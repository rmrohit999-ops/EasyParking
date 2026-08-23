import { BadRequestException, ConflictException, ForbiddenException, Inject, Injectable, Logger, NotFoundException } from '@nestjs/common';
import { ConfigService } from '@nestjs/config';
import { Prisma } from '@prisma/client';
import { PrismaService } from '../../common/prisma/prisma.service';
import { AppConfig } from '../../common/config/configuration';
import { BookingService } from '../booking/booking.service';
import { LedgerService } from '../ledger/ledger.service';
import { PAYMENT_PROVIDER_SERVICE, PaymentProvider } from './provider/payment-provider.interface';
import { computePayableBreakdown } from './pricing';
import { CreatePaymentOrderDto, RetryPaymentDto } from './dto/payments.dto';

@Injectable()
export class PaymentsService {
  private readonly logger = new Logger(PaymentsService.name);

  constructor(
    private readonly prisma: PrismaService,
    private readonly configService: ConfigService<AppConfig, true>,
    private readonly bookingService: BookingService,
    private readonly ledgerService: LedgerService,
    @Inject(PAYMENT_PROVIDER_SERVICE) private readonly provider: PaymentProvider,
  ) {}

  // ---------------------------------------------------------------------
  // Order creation
  // ---------------------------------------------------------------------

  async createOrder(driverId: string, dto: CreatePaymentOrderDto) {
    const replay = await this.prisma.paymentOrder.findUnique({ where: { idempotency_key: dto.idempotencyKey } });
    if (replay) {
      if (replay.driver_id !== driverId) {
        // A reused idempotency key belonging to someone else is a client
        // bug (or an attempted key-guessing probe), never a legitimate
        // replay — refuse rather than leaking another driver's order.
        throw new ForbiddenException('This idempotency key is not available.');
      }
      return toOrderView(replay);
    }

    const booking = await this.bookingService.getPayableBooking(driverId, dto.bookingId);

    const inFlight = await this.prisma.paymentOrder.findFirst({
      where: { booking_id: booking.id, status: { in: ['PENDING', 'PROCESSING'] } },
    });
    if (inFlight) {
      throw new ConflictException(
        'A payment is already in progress for this booking. Retry that payment instead of starting a new one.',
      );
    }

    return this.dispatchOrder(booking, driverId, dto.idempotencyKey);
  }

  async retryPayment(driverId: string, paymentOrderId: string, dto: RetryPaymentDto) {
    const previous = await this.prisma.paymentOrder.findUnique({ where: { id: paymentOrderId } });
    if (!previous) throw new NotFoundException('Payment order not found.');
    if (previous.driver_id !== driverId) throw new ForbiddenException('That payment does not belong to you.');
    if (previous.status !== 'FAILED') {
      throw new ConflictException(`Only a FAILED payment can be retried (this one is ${previous.status}).`);
    }

    const replay = await this.prisma.paymentOrder.findUnique({ where: { idempotency_key: dto.idempotencyKey } });
    if (replay) {
      if (replay.driver_id !== driverId) throw new ForbiddenException('This idempotency key is not available.');
      return toOrderView(replay);
    }

    // "FAILED --> PENDING: driver retries (new order, same booking hold if
    // still valid)" (Milestone 0 §9) — getPayableBooking re-validates the
    // booking is still PENDING_PAYMENT and not expired rather than trusting
    // that it still is just because the old order once existed.
    const booking = await this.bookingService.getPayableBooking(driverId, previous.booking_id);
    return this.dispatchOrder(booking, driverId, dto.idempotencyKey);
  }

  private async dispatchOrder(
    booking: Prisma.BookingGetPayload<{ include: { section: true } }>,
    driverId: string,
    idempotencyKey: string,
  ) {
    const breakdown = await computePayableBreakdown(this.prisma, this.configService, booking.section, booking);

    let order = await this.prisma.paymentOrder.create({
      data: {
        booking_id: booking.id,
        driver_id: driverId,
        gateway: this.provider.gatewayName,
        amount: breakdown.driverTotalMinorUnits,
        currency: breakdown.currency,
        status: 'PENDING',
        idempotency_key: idempotencyKey,
      },
    });

    try {
      const gatewayOrder = await this.provider.createOrder({
        amountMinorUnits: breakdown.driverTotalMinorUnits,
        currency: breakdown.currency,
        receiptId: order.id,
        notes: { bookingId: booking.id, driverId },
      });
      order = await this.prisma.paymentOrder.update({
        where: { id: order.id },
        data: { status: 'PROCESSING', gateway_order_id: gatewayOrder.gatewayOrderId },
      });
    } catch (err) {
      order = await this.prisma.paymentOrder.update({ where: { id: order.id }, data: { status: 'FAILED' } });
      this.logger.warn(`Payment order ${order.id} failed at gateway dispatch: ${(err as Error).message}`);
      throw err;
    }

    return toOrderView(order);
  }

  // ---------------------------------------------------------------------
  // Reads
  // ---------------------------------------------------------------------

  async getPayment(user: { id: string; roles: string[] }, paymentOrderId: string) {
    const order = await this.prisma.paymentOrder.findUnique({ where: { id: paymentOrderId } });
    if (!order) throw new NotFoundException('Payment order not found.');
    if (order.driver_id !== user.id && !user.roles.includes('ADMIN')) {
      throw new ForbiddenException('You do not have permission to view this payment.');
    }
    return toOrderView(order);
  }

  // ---------------------------------------------------------------------
  // Webhook
  // ---------------------------------------------------------------------

  /**
   * Verifies, dedupes, cross-checks, and processes a gateway webhook
   * delivery. Always resolves (never throws past a bad/unrecognized event)
   * so the gateway gets a 200 ack and doesn't retry-storm us for events we
   * intentionally ignore — WebhooksController is responsible for mapping a
   * failed *signature* check specifically to a 4xx (the one case that
   * should NOT be silently acked, since a bad signature could be a forgery
   * attempt worth alerting on, per Milestone 0 §13 T7).
   */
  async handleWebhook(gatewayName: string, rawBody: Buffer, signatureHeader: string | undefined): Promise<{ ok: boolean; reason?: string }> {
    if (gatewayName !== this.provider.gatewayName) {
      return { ok: false, reason: 'unknown_gateway' };
    }
    if (!this.provider.verifyWebhookSignature(rawBody, signatureHeader)) {
      throw new BadRequestException('Invalid webhook signature.');
    }

    let payload: RazorpayWebhookPayload;
    try {
      payload = JSON.parse(rawBody.toString('utf8')) as RazorpayWebhookPayload;
    } catch {
      return { ok: false, reason: 'invalid_json' };
    }

    const entity = payload?.payload?.payment?.entity;
    if (!entity) return { ok: false, reason: 'no_payment_entity' };

    const gatewayEventId = payload.id ?? `${payload.event}:${entity.id}`;
    try {
      await this.prisma.paymentGatewayEvent.create({
        data: {
          gateway: gatewayName,
          event_type: payload.event ?? 'unknown',
          gateway_event_id: gatewayEventId,
          raw_payload: payload as unknown as Prisma.InputJsonValue,
          signature_verified: true,
          processed_at: new Date(),
        },
      });
    } catch (err) {
      if ((err as { code?: string }).code === 'P2002') {
        // Replay of an already-processed delivery (T8, Milestone 0 §13) —
        // a no-op, not an error. The gateway retries webhooks aggressively
        // on anything but a prompt 2xx, so this path is expected to be hit
        // routinely, not exceptional.
        this.logger.log(`Duplicate webhook delivery ignored: ${gatewayName}/${gatewayEventId}`);
        return { ok: true, reason: 'duplicate' };
      }
      throw err;
    }

    const order = await this.prisma.paymentOrder.findFirst({ where: { gateway_order_id: entity.order_id } });
    if (!order) {
      this.logger.warn(`Webhook for unknown gateway_order_id=${entity.order_id} (gateway=${gatewayName}).`);
      return { ok: false, reason: 'order_not_found' };
    }

    const isCaptured = payload.event === 'payment.captured' || entity.status === 'captured';
    const isFailed = payload.event === 'payment.failed' || entity.status === 'failed';

    if (String(entity.amount) !== order.amount.toString() || entity.currency !== order.currency) {
      // T7 (Milestone 0 §13): a forged or tampered webhook claiming a
      // different amount than what we quoted the driver must never be
      // allowed to mark a payment successful, however the signature check
      // above resolved.
      this.logger.error(
        `Webhook amount/currency mismatch for order ${order.id}: expected ${order.amount.toString()} ${order.currency}, ` +
          `got ${entity.amount} ${entity.currency}. Refusing to mark successful.`,
      );
      await this.prisma.paymentOrder.updateMany({
        where: { id: order.id, status: { in: ['PENDING', 'PROCESSING'] } },
        data: { status: 'FAILED' },
      });
      return { ok: false, reason: 'amount_mismatch' };
    }

    if (isCaptured) {
      await this.markOrderSuccessful(order.id, entity.id);
      return { ok: true };
    }
    if (isFailed) {
      await this.prisma.paymentOrder.updateMany({
        where: { id: order.id, status: { in: ['PENDING', 'PROCESSING'] } },
        data: { status: 'FAILED' },
      });
      return { ok: true };
    }
    return { ok: true, reason: 'ignored_event' };
  }

  private async markOrderSuccessful(orderId: string, gatewayPaymentId: string) {
    await this.prisma.$transaction(async (tx) => {
      const result = await tx.paymentOrder.updateMany({
        where: { id: orderId, status: { in: ['PENDING', 'PROCESSING'] } },
        data: { status: 'SUCCESSFUL' },
      });
      if (result.count === 0) {
        // Already SUCCESSFUL from an earlier delivery of a differently-IDed
        // event for the same payment — idempotent no-op.
        return;
      }

      const order = await tx.paymentOrder.findUniqueOrThrow({ where: { id: orderId } });
      const booking = await tx.booking.findUniqueOrThrow({
        where: { id: order.booking_id },
        include: { section: true, parking: true, vehicle: true },
      });

      const breakdown = await computePayableBreakdown(this.prisma, this.configService, booking.section, booking);

      // booking.status is still PENDING_PAYMENT at this read (markPaid,
      // which flips it to CONFIRMED, runs after this) — the ledger's
      // booking_status_at_time is meant to snapshot the status this
      // transaction actually resulted in, so it's overridden here rather
      // than read verbatim off the pre-transition row.
      await this.ledgerService.recordSuccessfulPayment(tx, {
        booking: { ...booking, status: 'CONFIRMED' },
        ownerId: booking.parking.owner_id,
        vehicleType: booking.vehicle.vehicle_type,
        paymentMethod: 'ONLINE',
        gateway: this.provider.gatewayName,
        gatewayTransactionId: gatewayPaymentId,
        breakdown,
        idempotencyKey: `txn:${order.id}`,
      });

      await this.bookingService.markPaid(booking.id, null, 'WEBHOOK', tx);
    });
  }
}

interface RazorpayWebhookPayload {
  id?: string;
  event?: string;
  payload?: {
    payment?: {
      entity?: {
        id: string;
        order_id: string;
        status: string;
        amount: number;
        currency: string;
      };
    };
  };
}

function toOrderView(order: {
  id: string;
  booking_id: string;
  gateway: string;
  gateway_order_id: string | null;
  amount: bigint;
  currency: string;
  status: string;
  created_at: Date;
}) {
  return {
    id: order.id,
    bookingId: order.booking_id,
    gateway: order.gateway,
    gatewayOrderId: order.gateway_order_id,
    amountMinorUnits: order.amount.toString(),
    currency: order.currency,
    status: order.status,
    createdAt: order.created_at,
  };
}
