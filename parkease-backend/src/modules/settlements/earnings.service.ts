import { ForbiddenException, Injectable } from '@nestjs/common';
import { EarningsStatus } from '@prisma/client';
import { PrismaService } from '../../common/prisma/prisma.service';

// Transcribed directly from the Prisma schema's `EarningsStatus` enum
// (Milestone 0 ERD §5.10) rather than `Object.values(EarningsStatus)` at
// runtime — this file is validated with tsc against an ungenerated Prisma
// client in this sandbox (no network egress for `prisma generate`, see
// every other module's disclosed limitation), where the imported enum
// object's members aren't reliably typed for `Object.values()` to index
// back into a Record with. A literal tuple sidesteps that entirely and is
// exactly as correct once the client *is* generated.
const ALL_EARNINGS_STATUSES: readonly EarningsStatus[] = [
  'PENDING',
  'AVAILABLE',
  'PROCESSING',
  'SETTLED',
  'FAILED',
  'ADJUSTED',
  'REVERSED',
] as const;

/**
 * Owner-facing read side of the earnings ledger (Milestone 9). Every row
 * this reads was written once, at the moment a payment was actually
 * confirmed (LedgerService.recordSuccessfulPayment, Milestone 7) or
 * reversed by a refund (RefundsService.applyLedgerReversal, Milestone 9) —
 * this service never computes or estimates an amount itself.
 */
@Injectable()
export class EarningsService {
  constructor(private readonly prisma: PrismaService) {}

  async summary(ownerUserId: string) {
    const ownerProfile = await this.requireOwnerProfile(ownerUserId);
    const grouped = await this.prisma.ownerEarningsLedgerEntry.groupBy({
      by: ['status'],
      where: { owner_id: ownerProfile.id },
      _sum: { amount: true },
      _count: { _all: true },
    });
    const byStatus = Object.fromEntries(
      grouped.map((g) => [g.status, { amountMinorUnits: (g._sum.amount ?? 0n).toString(), count: g._count._all }]),
    ) as Record<string, { amountMinorUnits: string; count: number }>;

    // Ensure every status key is present even at zero, so a client doesn't
    // need to special-case "never had an entry in this status."
    for (const status of ALL_EARNINGS_STATUSES) {
      if (!byStatus[status]) byStatus[status] = { amountMinorUnits: '0', count: 0 };
    }

    return { ownerId: ownerProfile.id, byStatus };
  }

  async ledger(ownerUserId: string, status?: EarningsStatus) {
    const ownerProfile = await this.requireOwnerProfile(ownerUserId);
    const entries = await this.prisma.ownerEarningsLedgerEntry.findMany({
      where: { owner_id: ownerProfile.id, ...(status ? { status } : {}) },
      orderBy: { created_at: 'desc' },
      take: 200,
    });
    return entries.map((e) => ({
      id: e.id,
      bookingId: e.booking_id,
      parkingId: e.parking_id,
      sectionId: e.section_id,
      vehicleCategory: e.vehicle_category,
      amountMinorUnits: e.amount.toString(),
      status: e.status,
      createdAt: e.created_at,
    }));
  }

  private async requireOwnerProfile(userId: string) {
    const ownerProfile = await this.prisma.ownerProfile.findUnique({ where: { user_id: userId } });
    if (!ownerProfile) throw new ForbiddenException('You do not have an owner profile.');
    return ownerProfile;
  }
}
