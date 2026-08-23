import { Injectable, Logger } from '@nestjs/common';
import { Booking, PaymentMethod, Prisma, VehicleType } from '@prisma/client';
import { PrismaService } from '../../common/prisma/prisma.service';
import { PriceBreakdown } from '../payments/pricing';

/**
 * LedgerModule's real logic, filled in here in Milestone 7 exactly as its
 * Milestone 1 placeholder comment promised ("Real providers/controllers/
 * business logic land in Milestone 7 — Payments"). Owns the one place a
 * `transactions` row (the immutable ledger, Milestone 0 §5.8) gets
 * created: the moment a payment (online, via webhook) or a cash collection
 * is confirmed. Everything downstream — owner earnings dashboards,
 * settlements/payouts (Milestone 9), refunds/adjustments (Milestone 9) —
 * reads from what this method writes; it never edits a transaction row in
 * place, only appends.
 */
@Injectable()
export class LedgerService {
  private readonly logger = new Logger(LedgerService.name);

  constructor(private readonly prisma: PrismaService) {}

  /**
   * Runs inside the same Prisma transaction as the PaymentOrder ->
   * SUCCESSFUL update and the booking's PENDING_PAYMENT -> CONFIRMED
   * transition (called from PaymentsService.handleWebhook), so a
   * transactions row can never exist without the payment/booking state it
   * describes actually having landed, and vice versa.
   */
  async recordSuccessfulPayment(
    tx: Prisma.TransactionClient,
    params: {
      booking: Pick<Booking, 'id' | 'driver_id' | 'vehicle_id' | 'parking_id' | 'section_id' | 'vehicle_category' | 'status'>;
      ownerId: string;
      vehicleType: VehicleType;
      paymentMethod: PaymentMethod;
      gateway: string | null;
      gatewayTransactionId: string | null;
      breakdown: PriceBreakdown;
      idempotencyKey: string;
    },
  ) {
    const transaction = await tx.transaction.create({
      data: {
        booking_id: params.booking.id,
        driver_id: params.booking.driver_id,
        owner_id: params.ownerId,
        parking_id: params.booking.parking_id,
        section_id: params.booking.section_id,
        vehicle_id: params.booking.vehicle_id,
        vehicle_category: params.booking.vehicle_category,
        vehicle_type: params.vehicleType,
        parking_amount: params.breakdown.parkingAmountMinorUnits,
        platform_fee: params.breakdown.platformFeeMinorUnits,
        commission_amount: params.breakdown.commissionAmountMinorUnits,
        tax_amount: params.breakdown.taxAmountMinorUnits,
        driver_total: params.breakdown.driverTotalMinorUnits,
        owner_gross: params.breakdown.ownerGrossMinorUnits,
        owner_net: params.breakdown.ownerNetMinorUnits,
        payment_method: params.paymentMethod,
        gateway: params.gateway,
        gateway_transaction_id: params.gatewayTransactionId,
        payment_status: 'SUCCESSFUL',
        booking_status_at_time: params.booking.status,
        currency: params.breakdown.currency,
        idempotency_key: params.idempotencyKey,
      },
    });

    // Owner earnings ledger entry starts PENDING (not yet withdrawable) —
    // Milestone 9 defines the PENDING -> AVAILABLE transition (e.g. after
    // checkout/a hold period) and the settlement/payout flow that consumes
    // AVAILABLE entries. Creating the entry here, at the moment money is
    // actually confirmed received, is what makes "every rupee owed to an
    // owner traces back to a real successful transaction" structural
    // rather than a report generated after the fact.
    await tx.ownerEarningsLedgerEntry.create({
      data: {
        owner_id: params.ownerId,
        parking_id: params.booking.parking_id,
        section_id: params.booking.section_id,
        vehicle_category: params.booking.vehicle_category,
        booking_id: params.booking.id,
        transaction_id: transaction.id,
        amount: params.breakdown.ownerNetMinorUnits,
        status: 'PENDING',
      },
    });

    this.logger.log(`Recorded transaction ${transaction.id} for booking ${params.booking.id} (${params.paymentMethod}).`);
    return transaction;
  }
}
