import { Booking, ParkingSection, PolicyScope } from '@prisma/client';
import { PrismaService } from '../../common/prisma/prisma.service';
import { AppConfig } from '../../common/config/configuration';
import { ConfigService } from '@nestjs/config';
import { Money } from '../../common/money/money';

export interface PriceBreakdown {
  currency: string;
  parkingAmountMinorUnits: bigint;
  taxAmountMinorUnits: bigint;
  commissionAmountMinorUnits: bigint;
  platformFeeMinorUnits: bigint;
  driverTotalMinorUnits: bigint;
  ownerGrossMinorUnits: bigint;
  ownerNetMinorUnits: bigint;
  commissionPercentApplied: number;
  taxPercentApplied: number;
}

/**
 * Commission/tax resolution is deliberately minimal in Milestone 7: only
 * the GLOBAL scope is looked up (PARKING/SECTION/VEHICLE_CATEGORY/OWNER/
 * PROMOTION-scoped overrides are real columns per the Milestone 0 ERD but
 * their resolution precedence/admin CRUD UI is explicitly Milestone 10's
 * scope — see config.module.ts's placeholder comment). When no GLOBAL
 * policy row exists yet (a fresh install with an empty commission_policies
 * table), the DEFAULT_COMMISSION_PERCENT/DEFAULT_GST_PERCENT env-configured
 * floor from business config is used instead, so pricing is never silently
 * zero because nobody's created a policy row yet.
 */
async function resolveCommissionRate(
  prisma: PrismaService,
  fallbackPercent: number,
): Promise<{ percent: number; fixedFeeMinorUnits: bigint; minCommissionMinorUnits: bigint }> {
  const now = new Date();
  const policy = await prisma.commissionPolicy.findFirst({
    where: {
      scope: PolicyScope.GLOBAL,
      effective_from: { lte: now },
      OR: [{ effective_to: null }, { effective_to: { gt: now } }],
    },
    orderBy: { effective_from: 'desc' },
  });
  if (!policy) return { percent: fallbackPercent, fixedFeeMinorUnits: 0n, minCommissionMinorUnits: 0n };
  return {
    percent: policy.commission_percent,
    fixedFeeMinorUnits: policy.fixed_fee,
    minCommissionMinorUnits: policy.min_commission,
  };
}

async function resolveTaxRate(
  prisma: PrismaService,
  fallbackPercent: number,
): Promise<{ percent: number; inclusive: boolean }> {
  const now = new Date();
  const policy = await prisma.taxPolicy.findFirst({
    where: {
      scope: PolicyScope.GLOBAL,
      effective_from: { lte: now },
      OR: [{ effective_to: null }, { effective_to: { gt: now } }],
    },
    orderBy: { effective_from: 'desc' },
  });
  if (!policy) return { percent: fallbackPercent, inclusive: false };
  return { percent: policy.rate_percent, inclusive: policy.inclusive };
}

/**
 * The single deterministic function used both when a PaymentOrder is
 * created (to know the amount to charge) and when its webhook reports
 * SUCCESSFUL (to know the amount to record in the ledger). Kept pure
 * (same inputs -> same outputs) rather than persisting the breakdown
 * somewhere between the two calls — the one disclosed edge case this
 * doesn't handle is a commission/tax policy change landing in the few
 * minutes between order creation and webhook delivery, which would apply
 * the newer policy to the ledger row than what the driver was quoted;
 * flagged here rather than hidden, and acceptable for an MVP given
 * policies are expected to change rarely, not per-minute.
 *
 * "Platform fee" (a driver-facing convenience fee distinct from the
 * owner-side commission cut) is a real `transactions.platform_fee` column
 * per the Milestone 0 ERD, but no policy type for configuring one exists
 * yet — it is always 0 in this milestone, disclosed rather than guessed.
 */
export async function computePayableBreakdown(
  prisma: PrismaService,
  configService: ConfigService<AppConfig, true>,
  section: ParkingSection,
  booking: Pick<Booking, 'booking_type' | 'price_snapshot'>,
): Promise<PriceBreakdown> {
  const business = configService.get('business', { infer: true });

  const parkingAmountMinorUnits =
    booking.booking_type === 'ADVANCE'
      ? BigInt(extractSubtotal(booking.price_snapshot))
      : BigInt(section.hourly_rate_minor_units) * BigInt(Math.max(1, business.instantMinimumChargeHours));

  const [{ percent: commissionPercent, fixedFeeMinorUnits, minCommissionMinorUnits }, { percent: taxPercent, inclusive }] =
    await Promise.all([
      resolveCommissionRate(prisma, business.defaultCommissionPercent),
      resolveTaxRate(prisma, business.defaultGstPercent),
    ]);

  const parkingAmount = Money.of(parkingAmountMinorUnits, section.currency);
  const taxAmount = parkingAmount.percentage(taxPercent);
  let commissionAmount = parkingAmount.percentage(commissionPercent).add(Money.of(fixedFeeMinorUnits, section.currency));
  const minCommission = Money.of(minCommissionMinorUnits, section.currency);
  if (commissionAmount.compareTo(minCommission) < 0) commissionAmount = minCommission;

  const platformFee = Money.zero(section.currency);
  // Inclusive tax is already inside parkingAmount (carved out for
  // reporting, not added on top); exclusive tax is charged in addition.
  const driverTotal = inclusive ? parkingAmount.add(platformFee) : parkingAmount.add(taxAmount).add(platformFee);
  const ownerGross = parkingAmount;
  const ownerNet = ownerGross.subtract(commissionAmount);

  return {
    currency: section.currency,
    parkingAmountMinorUnits: parkingAmount.minorUnits,
    taxAmountMinorUnits: taxAmount.minorUnits,
    commissionAmountMinorUnits: commissionAmount.minorUnits,
    platformFeeMinorUnits: platformFee.minorUnits,
    driverTotalMinorUnits: driverTotal.minorUnits,
    ownerGrossMinorUnits: ownerGross.minorUnits,
    ownerNetMinorUnits: ownerNet.minorUnits,
    commissionPercentApplied: commissionPercent,
    taxPercentApplied: taxPercent,
  };
}

function extractSubtotal(priceSnapshot: unknown): number {
  if (
    priceSnapshot &&
    typeof priceSnapshot === 'object' &&
    'subtotalMinorUnits' in priceSnapshot &&
    typeof (priceSnapshot as Record<string, unknown>).subtotalMinorUnits === 'number'
  ) {
    return (priceSnapshot as Record<string, number>).subtotalMinorUnits;
  }
  // Defensive fallback — should be unreachable since booking.service.ts
  // always populates subtotalMinorUnits for ADVANCE bookings at confirm
  // time, but price_snapshot is a Json column with no DB-level schema
  // guarantee, so this is a deliberate fail-safe rather than a crash.
  throw new Error('Booking price_snapshot is missing subtotalMinorUnits for an ADVANCE booking.');
}
