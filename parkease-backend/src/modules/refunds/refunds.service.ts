import { ConflictException, ForbiddenException, Inject, Injectable, Logger, NotFoundException } from '@nestjs/common';
import { ConfigService } from '@nestjs/config';
import { BookingType, Prisma, RefundReasonCode, RefundType, TransactionPaymentStatus } from '@prisma/client';
import { PrismaService } from '../../common/prisma/prisma.service';
import { AppConfig } from '../../common/config/configuration';
import { AuthenticatedUser } from '../../common/decorators/current-user.decorator';
import { Money } from '../../common/money/money';
import { BookingService } from '../booking/booking.service';
import { NotificationsService } from '../notifications/notifications.service';
import { AuditService } from '../audit/audit.service';
import { PAYMENT_PROVIDER_SERVICE, PaymentProvider } from '../payments/provider/payment-provider.interface';
import {
  AdminCancelBookingDto,
  AdminManualRefundDto,
  CancelBookingDto,
  ConfirmCashRefundDto,
  MarkParkingUnavailableDto,
} from './dto/refunds.dto';

/**
 * Milestone 9 (Earnings, refunds and settlements). Owns every path that
 * both changes a booking's status AND requires computing/dispatching a
 * refund as one user-facing action — driver self-cancel, admin
 * force-cancel, an owner marking a confirmed booking's parking
 * unavailable, and (called from QrModule) a mismatch resolved as
 * REJECTED_ENTRY. This is why the driver/admin-facing `/bookings/:id/cancel`
 * routes live here rather than in BookingModule: the same "route ownership
 * follows whichever module needs the cross-module orchestration" pattern
 * QrModule already established in Milestone 8 for check-in/check-out/
 * cash-collect (which also transition a booking but need machinery
 * BookingModule itself doesn't own).
 *
 * `refundForBooking` is the single choke point for every refund this
 * codebase ever issues — it never trusts a caller-supplied amount, always
 * recomputing from the booking's own successful Transaction and the
 * applicable cancellation policy/reason code, exactly mirroring
 * `computePayableBreakdown`'s "server is the only source of truth for
 * money" rule from Milestone 7.
 */
@Injectable()
export class RefundsService {
  private readonly logger = new Logger(RefundsService.name);

  constructor(
    private readonly prisma: PrismaService,
    private readonly configService: ConfigService<AppConfig, true>,
    private readonly bookingService: BookingService,
    private readonly notificationsService: NotificationsService,
    private readonly auditService: AuditService,
    @Inject(PAYMENT_PROVIDER_SERVICE) private readonly paymentProvider: PaymentProvider,
  ) {}

  // ---------------------------------------------------------------------
  // Orchestrated cancel-and-refund entry points
  // ---------------------------------------------------------------------

  async cancelBookingAndRefund(driverId: string, bookingId: string, dto: CancelBookingDto) {
    await this.bookingService.cancelBooking(driverId, bookingId, dto);
    await this.refundForBooking(bookingId, 'CANCELLATION_POLICY', driverId);
    return this.bookingService.getBooking({ id: driverId, roles: ['DRIVER'] }, bookingId);
  }

  async adminCancelBookingAndRefund(adminId: string, bookingId: string, dto: AdminCancelBookingDto) {
    await this.bookingService.adminCancelBooking(adminId, bookingId, dto.reason);
    await this.refundForBooking(bookingId, 'ADMIN_APPROVED', adminId, {
      overridePercent: dto.refundPercent ?? 100,
      approvedBy: adminId,
    });
    return this.bookingService.getBooking({ id: adminId, roles: ['ADMIN'] }, bookingId);
  }

  /**
   * Owner-initiated: the section became unavailable after a driver already
   * paid to reserve it (e.g. a physical obstruction, an emergency
   * closure) — never the driver's fault, always a full refund. Only
   * reachable from CONFIRMED, matching the state machine's
   * `CONFIRMED -> PARKING_UNAVAILABLE` edge (Milestone 0 §8): once a
   * driver has actually checked in, this is no longer the right tool —
   * that scenario is a mismatch/dispute instead.
   */
  async markParkingUnavailableAndRefund(ownerUser: AuthenticatedUser, bookingId: string, dto: MarkParkingUnavailableDto) {
    const booking = await this.prisma.booking.findUnique({ where: { id: bookingId }, include: { parking: true } });
    if (!booking) throw new NotFoundException('Booking not found.');

    const ownerProfile = await this.prisma.ownerProfile.findUnique({ where: { user_id: ownerUser.id } });
    if (!ownerProfile || ownerProfile.id !== booking.parking.owner_id) {
      throw new ForbiddenException('You do not have permission to modify this booking.');
    }

    await this.bookingService.applyBookingTransition(bookingId, booking.status, 'PARKING_UNAVAILABLE', {
      actorId: ownerUser.id,
      actorRole: 'OWNER',
      source: 'APP',
      reason: dto.reason,
    });
    await this.refundForBooking(bookingId, 'PARKING_UNAVAILABLE', ownerUser.id, { overridePercent: 100 });
    return this.bookingService.getBooking({ id: ownerUser.id, roles: ['OWNER'] }, bookingId);
  }

  /**
   * Called by QrModule's resolveMismatch (REJECTED_ENTRY resolution) after
   * it has already transitioned the booking to REJECTED — the driver was
   * let in, a category/registration mismatch was found, and entry was
   * ultimately refused, so the money comes back in full. RefundsModule
   * never imports QrModule (only the reverse), keeping the dependency
   * graph a DAG.
   */
  async refundForRejectedMismatch(bookingId: string, actorId: string) {
    return this.refundForBooking(bookingId, 'CATEGORY_MISMATCH', actorId, { overridePercent: 100 });
  }

  /**
   * Admin discretionary refund that does NOT change the booking's status —
   * for a dispute resolved in the driver's favour on a booking that has
   * already gone COMPLETED, or any other case a human reviewer decides
   * warrants money back without re-litigating the booking's lifecycle.
   * Milestone 10 (Disputes) is the natural caller once its resolution flow
   * exists; exposed here now as a real, admin-only, fully-audited action
   * rather than left unbuilt.
   */
  async adminManualRefund(adminId: string, bookingId: string, dto: AdminManualRefundDto) {
    const refund = await this.refundForBooking(bookingId, dto.reasonCode, adminId, {
      overridePercent: dto.refundPercent,
      approvedBy: adminId,
      note: dto.reason,
    });
    return refund ? toRefundView(refund) : null;
  }

  // ---------------------------------------------------------------------
  // Core refund computation + dispatch
  // ---------------------------------------------------------------------

  private async refundForBooking(
    bookingId: string,
    reasonCode: RefundReasonCode,
    initiatedBy: string | null,
    opts: { overridePercent?: number; approvedBy?: string; note?: string } = {},
  ) {
    const booking = await this.prisma.booking.findUniqueOrThrow({ where: { id: bookingId } });

    // The most recent transaction that has (or had) money actually
    // captured — SUCCESSFUL or already-PARTIALLY_REFUNDED, so a second
    // partial refund on the same booking still finds it. A FAILED/PENDING
    // transaction never held money, so there's nothing to send back.
    const transaction = await this.prisma.transaction.findFirst({
      where: { booking_id: bookingId, payment_status: { in: ['SUCCESSFUL', 'PARTIALLY_REFUNDED'] } },
      orderBy: { created_at: 'desc' },
    });
    if (!transaction) {
      this.logger.log(`No captured payment found for booking ${bookingId} — nothing to refund.`);
      return null;
    }

    const existingRefunds = await this.prisma.refund.findMany({
      where: { transaction_id: transaction.id, status: { in: ['PENDING', 'PROCESSING', 'COMPLETED'] } },
    });
    const alreadyRefunded = existingRefunds.reduce((sum, r) => sum + r.amount, 0n);
    if (alreadyRefunded >= transaction.driver_total) {
      this.logger.log(`Booking ${bookingId} is already fully refunded — no further refund issued.`);
      return existingRefunds[existingRefunds.length - 1] ?? null;
    }

    const { amountMinorUnits, refundType, percentApplied } = await this.computeRefundAmount(
      transaction,
      booking,
      reasonCode,
      alreadyRefunded,
      opts.overridePercent,
    );

    const refund = await this.prisma.refund.create({
      data: {
        transaction_id: transaction.id,
        booking_id: bookingId,
        refund_type: refundType,
        reason_code: reasonCode,
        amount: amountMinorUnits,
        status: 'PENDING',
        initiated_by: initiatedBy,
        approved_by: opts.approvedBy ?? null,
      },
    });

    // Single choke point for every refund path (self-cancel, admin-cancel,
    // parking-unavailable, mismatch-rejected, admin-manual) — one call here
    // covers all of them rather than repeating it at each call site.
    await this.auditService.record({
      actorId: initiatedBy,
      actorRole: opts.approvedBy ? 'ADMIN' : null,
      action: 'ISSUE_REFUND',
      targetType: 'Refund',
      targetId: refund.id,
      afterState: { bookingId, reasonCode, amountMinorUnits: amountMinorUnits.toString(), refundType, note: opts.note },
    });

    if (refundType === 'NONE' || amountMinorUnits === 0n) {
      this.logger.log(
        `Booking ${bookingId} refund computed as ₹0 (${percentApplied}% tier, reason=${reasonCode}) — recorded, no gateway call needed.`,
      );
      return this.prisma.refund.update({ where: { id: refund.id }, data: { status: 'COMPLETED', completed_at: new Date() } });
    }

    await this.prisma.transactionAdjustment.create({
      data: {
        transaction_id: transaction.id,
        adjustment_type: 'REFUND',
        amount: amountMinorUnits,
        reason: opts.note ?? `${reasonCode}: ${percentApplied}% of driver total refunded.`,
        created_by: initiatedBy,
      },
    });

    if (transaction.payment_method === 'CASH') {
      // No gateway to call for cash — stays PENDING until a human
      // physically hands the cash back and confirms via
      // POST /refunds/:id/confirm-cash (confirmCashRefund below). Marking
      // this COMPLETED automatically here would be exactly the kind of
      // fake success this codebase's "no fakes" rule forbids for money
      // movement — real cash requires a real, logged human confirmation.
      await this.applyLedgerReversal(bookingId, refundType);
      await this.updateTransactionRefundStatus(transaction.id, alreadyRefunded + amountMinorUnits);
      this.logger.log(`Cash refund ${refund.id} for booking ${bookingId} recorded PENDING — awaiting manual confirmation.`);
      await this.notifyRefund(booking.driver_id, amountMinorUnits, transaction.currency, 'pending cash refund');
      return refund;
    }

    if (!transaction.gateway_transaction_id) {
      this.logger.error(`Transaction ${transaction.id} for booking ${bookingId} has no gateway_transaction_id — cannot dispatch an online refund.`);
      return this.prisma.refund.update({ where: { id: refund.id }, data: { status: 'FAILED' } });
    }

    try {
      const gatewayResult = await this.paymentProvider.createRefund({
        gatewayPaymentId: transaction.gateway_transaction_id,
        amountMinorUnits,
        idempotencyKey: refund.id,
      });
      const completed = await this.prisma.refund.update({
        where: { id: refund.id },
        data: { status: 'COMPLETED', gateway_refund_id: gatewayResult.gatewayRefundId, completed_at: new Date() },
      });
      await this.applyLedgerReversal(bookingId, refundType);
      await this.updateTransactionRefundStatus(transaction.id, alreadyRefunded + amountMinorUnits);
      await this.notifyRefund(booking.driver_id, amountMinorUnits, transaction.currency, 'refund');
      return completed;
    } catch (err) {
      this.logger.error(`Gateway refund failed for booking ${bookingId}: ${(err as Error).message}`);
      // Deliberately not rethrown: the booking's own status transition was
      // already committed by the caller before this method ever ran, and
      // a failed refund dispatch must not appear to undo that. A FAILED
      // refund is a real, visible, admin-actionable record instead —
      // Milestone 10's admin tools are the natural place for a "retry
      // failed refund" action, not silently rolling back state here.
      return this.prisma.refund.update({ where: { id: refund.id }, data: { status: 'FAILED' } });
    }
  }

  /**
   * Resolves a refund percentage for CANCELLATION_POLICY the same way
   * pricing.ts resolves commission/tax: a real CancellationPolicy row
   * (GLOBAL scope; PARKING/SECTION/OWNER-scoped overrides are real ERD
   * columns but their resolution precedence is Milestone 10's admin-tools
   * scope, not this one) always wins when present, with its `rules` JSON
   * expected to hold `{ tiers: [{ minHoursBeforeStart, refundPercent }] }`
   * sorted by whichever tier's `minHoursBeforeStart` the booking still
   * clears. With no policy row, the env-configured two-tier default
   * (business.cancellationFullRefundHoursBeforeStart/
   * cancellationPartialRefundPercent) applies instead — pricing is never
   * silently zero just because nobody created a policy row.
   */
  private async resolveCancellationPercent(booking: { booking_type: BookingType; start_time: Date | null; created_at: Date }): Promise<number> {
    const business = this.configService.get('business', { infer: true });
    const now = new Date();

    if (booking.booking_type === 'INSTANT') {
      const secondsSinceStart = (now.getTime() - booking.created_at.getTime()) / 1000;
      return secondsSinceStart <= business.instantCancellationGraceSeconds ? 100 : 0;
    }

    const startTime = booking.start_time ?? booking.created_at;
    const hoursUntilStart = (startTime.getTime() - now.getTime()) / 3_600_000;

    const policy = await this.prisma.cancellationPolicy.findFirst({
      where: {
        scope: 'GLOBAL',
        effective_from: { lte: now },
        OR: [{ effective_to: null }, { effective_to: { gt: now } }],
      },
      orderBy: { effective_from: 'desc' },
    });
    const tiers = policy ? extractCancellationTiers(policy.rules) : [];
    if (tiers.length > 0) {
      const sorted = [...tiers].sort((a, b) => b.minHoursBeforeStart - a.minHoursBeforeStart);
      const match = sorted.find((tier) => hoursUntilStart >= tier.minHoursBeforeStart);
      return match ? match.refundPercent : 0;
    }

    if (hoursUntilStart >= business.cancellationFullRefundHoursBeforeStart) return 100;
    if (hoursUntilStart >= 0) return business.cancellationPartialRefundPercent;
    return 0;
  }

  private async computeRefundAmount(
    transaction: { driver_total: bigint; currency: string },
    booking: { booking_type: BookingType; start_time: Date | null; created_at: Date },
    reasonCode: RefundReasonCode,
    alreadyRefundedMinorUnits: bigint,
    overridePercent?: number,
  ): Promise<{ amountMinorUnits: bigint; refundType: RefundType; percentApplied: number }> {
    let percent: number;
    if (overridePercent !== undefined) {
      percent = overridePercent;
    } else if (reasonCode === 'PARKING_UNAVAILABLE' || reasonCode === 'CATEGORY_MISMATCH' || reasonCode === 'ADMIN_APPROVED') {
      percent = 100;
    } else if (reasonCode === 'CANCELLATION_POLICY') {
      percent = await this.resolveCancellationPercent(booking);
    } else {
      // OTHER — never auto-computed; a real amount requires an explicit
      // overridePercent from a human reviewer (adminManualRefund).
      percent = 0;
    }

    const remaining = Money.of(transaction.driver_total, transaction.currency).subtract(
      Money.of(alreadyRefundedMinorUnits, transaction.currency),
    );
    let amount = Money.of(transaction.driver_total, transaction.currency).percentage(percent);
    if (amount.compareTo(remaining) > 0) amount = remaining;
    if (amount.isNegative()) amount = Money.zero(transaction.currency);

    const refundType: RefundType = amount.isZero() ? 'NONE' : amount.equals(remaining) ? 'FULL' : 'PARTIAL';
    return { amountMinorUnits: amount.minorUnits, refundType, percentApplied: percent };
  }

  /**
   * Excludes the refunded booking's owner-earnings ledger entry from ever
   * being picked up by a settlement payout. FULL -> REVERSED (this booking
   * earned the owner nothing). PARTIAL -> ADJUSTED, a distinct status for
   * admin visibility, but functionally identical for settlement eligibility
   * (SettlementsService only ever selects `status: 'AVAILABLE'` entries) —
   * disclosed simplification: this does not compute or pay out the
   * net-of-refund remainder automatically, it only guarantees the entry
   * can never be paid out as if the refund never happened. Precise partial
   * clawback accounting is Milestone 10 territory.
   */
  private async applyLedgerReversal(bookingId: string, refundType: RefundType) {
    const entry = await this.prisma.ownerEarningsLedgerEntry.findFirst({ where: { booking_id: bookingId } });
    if (!entry) return;
    if (entry.status !== 'PENDING' && entry.status !== 'AVAILABLE') {
      this.logger.warn(
        `Owner earnings ledger entry ${entry.id} for booking ${bookingId} is already ${entry.status} — ` +
          `a refund cannot automatically reverse it; needs a manual settlement-level clawback (Milestone 10).`,
      );
      return;
    }
    await this.prisma.ownerEarningsLedgerEntry.update({
      where: { id: entry.id },
      data: { status: refundType === 'FULL' ? 'REVERSED' : 'ADJUSTED' },
    });
  }

  private async notifyRefund(driverId: string, amountMinorUnits: bigint, currency: string, kind: 'refund' | 'pending cash refund') {
    const amount = Money.of(amountMinorUnits, currency).toDisplayString();
    await this.notificationsService.send({
      userId: driverId,
      category: 'refund',
      type: 'REFUND_ISSUED',
      title: kind === 'refund' ? 'Refund issued' : 'Refund on its way',
      body:
        kind === 'refund'
          ? `${amount} has been refunded to your original payment method.`
          : `${amount} will be refunded in cash at the parking location.`,
    });
  }

  private async updateTransactionRefundStatus(transactionId: string, totalRefundedMinorUnits: bigint) {
    const transaction = await this.prisma.transaction.findUniqueOrThrow({ where: { id: transactionId } });
    const newStatus: TransactionPaymentStatus = totalRefundedMinorUnits >= transaction.driver_total ? 'REFUNDED' : 'PARTIALLY_REFUNDED';
    await this.prisma.transaction.update({ where: { id: transactionId }, data: { payment_status: newStatus, refund_status: newStatus } });
  }

  // ---------------------------------------------------------------------
  // Cash refund manual confirmation
  // ---------------------------------------------------------------------

  /**
   * Only ADMIN or the OWNER of the booking's parking listing may confirm a
   * cash refund was physically handed back — narrower than QrService's
   * attendant-inclusive authorization, deliberately: an attendant
   * confirming their own cash handback with no second party is a weaker
   * control for money leaving the platform's books than for money
   * entering them, so this MVP scopes attendant cash-refund confirmation
   * out rather than wave it through, disclosed here rather than silently
   * matched to cashCollect's broader authorization.
   */
  async confirmCashRefund(actorUser: AuthenticatedUser & { roles: string[] }, refundId: string, dto: ConfirmCashRefundDto) {
    const refund = await this.prisma.refund.findUnique({
      where: { id: refundId },
      include: { transaction: true, booking: { include: { parking: true } } },
    });
    if (!refund) throw new NotFoundException('Refund not found.');
    if (refund.transaction.payment_method !== 'CASH') {
      throw new ConflictException('This refund was not for a cash payment.');
    }
    if (refund.status !== 'PENDING') {
      throw new ConflictException(`This refund is already ${refund.status}.`);
    }

    if (!actorUser.roles.includes('ADMIN')) {
      const ownerProfile = await this.prisma.ownerProfile.findUnique({ where: { user_id: actorUser.id } });
      if (!ownerProfile || ownerProfile.id !== refund.booking.parking.owner_id) {
        throw new ForbiddenException('You do not have permission to confirm this refund.');
      }
    }

    const updated = await this.prisma.refund.update({
      where: { id: refundId },
      data: { status: 'COMPLETED', completed_at: new Date(), approved_by: actorUser.id },
    });
    await this.auditService.record({
      actorId: actorUser.id,
      actorRole: actorUser.roles.includes('ADMIN') ? 'ADMIN' : 'OWNER',
      action: 'CONFIRM_CASH_REFUND',
      targetType: 'Refund',
      targetId: refundId,
      afterState: { note: dto.auditNote },
    });
    return toRefundView(updated);
  }

  // ---------------------------------------------------------------------
  // Reads
  // ---------------------------------------------------------------------

  async listRefundsForBooking(user: AuthenticatedUser & { roles: string[] }, bookingId: string) {
    const booking = await this.prisma.booking.findUnique({ where: { id: bookingId }, include: { parking: true } });
    if (!booking) throw new NotFoundException('Booking not found.');
    await this.assertCanAccessBooking(user, booking);
    const refunds = await this.prisma.refund.findMany({ where: { booking_id: bookingId }, orderBy: { created_at: 'desc' } });
    return refunds.map(toRefundView);
  }

  async getRefund(user: AuthenticatedUser & { roles: string[] }, refundId: string) {
    const refund = await this.prisma.refund.findUnique({ where: { id: refundId }, include: { booking: { include: { parking: true } } } });
    if (!refund) throw new NotFoundException('Refund not found.');
    await this.assertCanAccessBooking(user, refund.booking);
    return toRefundView(refund);
  }

  private async assertCanAccessBooking(user: AuthenticatedUser & { roles: string[] }, booking: { driver_id: string; parking: { owner_id: string } }) {
    if (user.roles.includes('ADMIN')) return;
    if (booking.driver_id === user.id) return;
    if (user.roles.includes('OWNER')) {
      const ownerProfile = await this.prisma.ownerProfile.findUnique({ where: { user_id: user.id } });
      if (ownerProfile && ownerProfile.id === booking.parking.owner_id) return;
    }
    throw new ForbiddenException('You do not have permission to view this refund.');
  }
}

/**
 * Every controller-exposed Refund read/write goes through this — `amount`
 * is a Prisma `BigInt` column, which `JSON.stringify` cannot serialize on
 * its own (throws `TypeError: Do not know how to serialize a BigInt`), so
 * every raw Prisma `Refund` row is mapped to a plain-string-amount view
 * before it ever reaches a controller return, mirroring `toOrderView`/
 * `toSettlementView`'s identical bigint-to-string convention elsewhere in
 * this codebase.
 */
function toRefundView(refund: {
  id: string;
  transaction_id: string;
  booking_id: string;
  refund_type: string;
  reason_code: string;
  amount: bigint;
  status: string;
  gateway_refund_id: string | null;
  initiated_by: string | null;
  approved_by: string | null;
  created_at: Date;
  completed_at: Date | null;
}) {
  return {
    id: refund.id,
    transactionId: refund.transaction_id,
    bookingId: refund.booking_id,
    refundType: refund.refund_type,
    reasonCode: refund.reason_code,
    amountMinorUnits: refund.amount.toString(),
    status: refund.status,
    gatewayRefundId: refund.gateway_refund_id,
    initiatedBy: refund.initiated_by,
    approvedBy: refund.approved_by,
    createdAt: refund.created_at,
    completedAt: refund.completed_at,
  };
}

function extractCancellationTiers(rules: Prisma.JsonValue): Array<{ minHoursBeforeStart: number; refundPercent: number }> {
  if (!rules || typeof rules !== 'object' || Array.isArray(rules)) return [];
  const tiers = (rules as Record<string, unknown>).tiers;
  if (!Array.isArray(tiers)) return [];
  return tiers
    .filter(
      (t): t is { minHoursBeforeStart: number; refundPercent: number } =>
        typeof t === 'object' && t !== null && typeof (t as Record<string, unknown>).minHoursBeforeStart === 'number' && typeof (t as Record<string, unknown>).refundPercent === 'number',
    )
    .map((t) => ({ minHoursBeforeStart: t.minHoursBeforeStart, refundPercent: t.refundPercent }));
}
