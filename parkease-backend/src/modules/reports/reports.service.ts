import { Injectable } from '@nestjs/common';
import { BookingStatus, VehicleCategory } from '@prisma/client';
import { PrismaService } from '../../common/prisma/prisma.service';
import { ReportDateRangeQueryDto, RevenueTimeseriesQueryDto, TopOwnersQueryDto } from './dto/reports.dto';

// Transcribed directly from the Prisma schema's `BookingStatus`/
// `VehicleCategory` enums rather than `Object.values(...)` at runtime — see
// EarningsService's identical fix (Milestone 9) for why: this file is
// validated with tsc against an ungenerated Prisma client in this sandbox
// (no network egress for `prisma generate`), where `Object.values()` on an
// imported enum binding doesn't reliably type-check as an index type. A
// literal tuple sidesteps that and is exactly as correct once the client
// *is* generated.
const ALL_BOOKING_STATUSES: readonly BookingStatus[] = [
  'PENDING_PAYMENT',
  'CONFIRMED',
  'DRIVER_ARRIVING',
  'CHECKED_IN',
  'PARKING_ACTIVE',
  'CHECKED_OUT',
  'COMPLETED',
  'REJECTED',
  'EXPIRED',
  'CANCELLED',
  'NO_SHOW',
  'VEHICLE_MISMATCH',
  'PARKING_UNAVAILABLE',
  'ADMIN_CANCELLED',
] as const;

const ALL_VEHICLE_CATEGORIES: readonly VehicleCategory[] = [
  'TWO_WHEELER',
  'FOUR_WHEELER',
  'OTHER_SUPPORTED',
  'UNSUPPORTED_PENDING_REVIEW',
] as const;

const DEFAULT_WINDOW_MS = 30 * 24 * 60 * 60 * 1000; // 30 days

interface RevenueBucketRow {
  bucket: Date;
  transaction_count: bigint | number | string;
  driver_total: bigint | number | string | null;
  commission_amount: bigint | number | string | null;
  tax_amount: bigint | number | string | null;
  owner_net: bigint | number | string | null;
}

/**
 * ReportsService — real (Milestone 11: notifications and analytics), filling
 * in the structural placeholder Milestone 1 left here. Pure read-side
 * aggregation over tables other milestones already write to
 * (Booking/Transaction/OwnerEarningsLedgerEntry/SectionAvailability) — no
 * separate analytics warehouse, no ETL, no numbers computed anywhere other
 * than here from the same source-of-truth rows the rest of the app trusts.
 *
 * Disclosed limitation: `utilizationSnapshot` can only ever report *current*
 * occupancy. `SectionAvailability` (Milestone 0 ERD §5.6) stores live
 * reserved/occupied/blocked counters that are overwritten in place on every
 * booking/check-in/check-out — there is no history table recording what
 * those counters were at a past point in time, so "occupancy last Tuesday
 * at 6pm" genuinely cannot be answered without a new time-series table this
 * milestone does not add. Revenue and booking-status reporting don't have
 * this limitation because Transaction/Booking rows are append-only and
 * timestamped, never overwritten.
 */
@Injectable()
export class ReportsService {
  constructor(private readonly prisma: PrismaService) {}

  async bookingsSummary(query: ReportDateRangeQueryDto) {
    const { from, to } = this.resolveRange(query);

    const grouped = await this.prisma.booking.groupBy({
      by: ['status'],
      where: { created_at: { gte: from, lte: to } },
      _count: { _all: true },
    });

    const byStatus: Record<string, number> = {};
    for (const status of ALL_BOOKING_STATUSES) byStatus[status] = 0;
    for (const g of grouped) byStatus[g.status] = g._count._all;
    const totalBookings = grouped.reduce((sum, g) => sum + g._count._all, 0);

    const revenue = await this.prisma.transaction.aggregate({
      where: { payment_status: 'SUCCESSFUL', created_at: { gte: from, lte: to } },
      _sum: { driver_total: true, commission_amount: true, tax_amount: true, owner_net: true, platform_fee: true },
      _count: { _all: true },
    });

    return {
      from,
      to,
      totalBookings,
      byStatus,
      successfulTransactions: revenue._count._all,
      currency: 'INR',
      grossDriverRevenueMinorUnits: (revenue._sum.driver_total ?? 0n).toString(),
      platformCommissionMinorUnits: (revenue._sum.commission_amount ?? 0n).toString(),
      taxCollectedMinorUnits: (revenue._sum.tax_amount ?? 0n).toString(),
      platformFeeMinorUnits: (revenue._sum.platform_fee ?? 0n).toString(),
      ownerNetPayableMinorUnits: (revenue._sum.owner_net ?? 0n).toString(),
    };
  }

  /**
   * A raw query is used here (rather than Prisma's groupBy, which has no
   * date-bucketing primitive) purely for `date_trunc` — the granularity unit
   * and both bounds are still passed as bound parameters ($1/$2/$3), never
   * string-interpolated, so this carries no more injection risk than any
   * parameterized query elsewhere in the codebase (see DiscoveryService's
   * PostGIS raw query, Milestone 5, for the same pattern).
   */
  async revenueTimeseries(query: RevenueTimeseriesQueryDto) {
    const { from, to } = this.resolveRange(query);
    const granularity = query.granularity ?? 'day';

    const rows = await this.prisma.$queryRawUnsafe<RevenueBucketRow[]>(
      `
      SELECT
        date_trunc($1, created_at) AS bucket,
        COUNT(*) AS transaction_count,
        SUM(driver_total) AS driver_total,
        SUM(commission_amount) AS commission_amount,
        SUM(tax_amount) AS tax_amount,
        SUM(owner_net) AS owner_net
      FROM transactions
      WHERE payment_status = 'SUCCESSFUL' AND created_at >= $2 AND created_at <= $3
      GROUP BY bucket
      ORDER BY bucket ASC
      `,
      granularity,
      from,
      to,
    );

    return {
      from,
      to,
      granularity,
      currency: 'INR',
      points: rows.map((r) => ({
        bucket: r.bucket,
        transactionCount: toSafeNumber(r.transaction_count),
        grossDriverRevenueMinorUnits: toAmountString(r.driver_total),
        platformCommissionMinorUnits: toAmountString(r.commission_amount),
        taxCollectedMinorUnits: toAmountString(r.tax_amount),
        ownerNetPayableMinorUnits: toAmountString(r.owner_net),
      })),
    };
  }

  async topOwners(query: TopOwnersQueryDto) {
    const { from, to } = this.resolveRange(query);
    const limit = query.limit ?? 10;

    const grouped = await this.prisma.transaction.groupBy({
      by: ['owner_id'],
      where: { payment_status: 'SUCCESSFUL', created_at: { gte: from, lte: to } },
      _sum: { owner_net: true },
      _count: { _all: true },
      orderBy: { _sum: { owner_net: 'desc' } },
      take: limit,
    });

    const owners = await this.prisma.ownerProfile.findMany({
      where: { id: { in: grouped.map((g) => g.owner_id) } },
      include: { user: { select: { phone: true, email: true } } },
    });
    const ownerById = new Map(owners.map((o) => [o.id, o]));

    return {
      from,
      to,
      currency: 'INR',
      owners: grouped.map((g) => {
        const owner = ownerById.get(g.owner_id);
        return {
          ownerId: g.owner_id,
          businessName: owner?.business_name ?? null,
          phone: owner?.user.phone ?? null,
          email: owner?.user.email ?? null,
          transactionCount: g._count._all,
          netEarningsMinorUnits: (g._sum.owner_net ?? 0n).toString(),
        };
      }),
    };
  }

  /**
   * Section 8's admin cash-monitoring view: total cash collected, broken
   * down by owner, plus pending-vs-completed counts. "Completed" reads
   * from Transaction (payment_method=CASH, payment_status=SUCCESSFUL —
   * only ever written by QrService.cashCollect, so this is exactly the
   * set of confirmed cash collections). "Pending" reads from Booking
   * (intended_payment_method=CASH, status=PENDING_PAYMENT — set by
   * BookingService.payCash the moment a driver picks cash, before an
   * owner has confirmed anything) — the two tables never overlap, since a
   * booking's intended_payment_method is only ever a same-row signal, not
   * itself proof of payment.
   */
  async cashSummary(query: ReportDateRangeQueryDto) {
    const { from, to } = this.resolveRange(query);

    const [completedAgg, pendingCount, byOwner] = await Promise.all([
      this.prisma.transaction.aggregate({
        where: { payment_method: 'CASH', payment_status: 'SUCCESSFUL', created_at: { gte: from, lte: to } },
        _sum: { driver_total: true, commission_amount: true, owner_net: true },
        _count: { _all: true },
      }),
      this.prisma.booking.count({
        where: { intended_payment_method: 'CASH', status: 'PENDING_PAYMENT', created_at: { gte: from, lte: to } },
      }),
      this.prisma.transaction.groupBy({
        by: ['owner_id'],
        where: { payment_method: 'CASH', payment_status: 'SUCCESSFUL', created_at: { gte: from, lte: to } },
        _sum: { driver_total: true, commission_amount: true, owner_net: true },
        _count: { _all: true },
        orderBy: { _sum: { driver_total: 'desc' } },
        take: 50,
      }),
    ]);

    const owners = await this.prisma.ownerProfile.findMany({
      where: { id: { in: byOwner.map((g) => g.owner_id) } },
      include: { user: { select: { phone: true, email: true } } },
    });
    const ownerById = new Map(owners.map((o) => [o.id, o]));

    return {
      from,
      to,
      currency: 'INR',
      totalCashCollectedMinorUnits: (completedAgg._sum.driver_total ?? 0n).toString(),
      totalCommissionMinorUnits: (completedAgg._sum.commission_amount ?? 0n).toString(),
      totalOwnerNetMinorUnits: (completedAgg._sum.owner_net ?? 0n).toString(),
      completedCount: completedAgg._count._all,
      pendingCount,
      byOwner: byOwner.map((g) => {
        const owner = ownerById.get(g.owner_id);
        return {
          ownerId: g.owner_id,
          businessName: owner?.business_name ?? null,
          phone: owner?.user.phone ?? null,
          email: owner?.user.email ?? null,
          transactionCount: g._count._all,
          totalCashCollectedMinorUnits: (g._sum.driver_total ?? 0n).toString(),
          commissionMinorUnits: (g._sum.commission_amount ?? 0n).toString(),
          netEarningsMinorUnits: (g._sum.owner_net ?? 0n).toString(),
        };
      }),
    };
  }

  async utilizationSnapshot() {
    const sections = await this.prisma.parkingSection.findMany({
      where: { status: 'ACTIVE', approval_status: 'APPROVED' },
      select: {
        parking_id: true,
        vehicle_category: true,
        parking: { select: { name: true } },
        availability: { select: { capacity: true, reserved_count: true, occupied_count: true, blocked_count: true } },
      },
    });

    let totalCapacity = 0;
    let totalUsed = 0;
    const byCategory = new Map<VehicleCategory, { capacity: number; used: number }>(
      ALL_VEHICLE_CATEGORIES.map((c) => [c, { capacity: 0, used: 0 }]),
    );
    const byListing = new Map<string, { listingId: string; listingName: string; capacity: number; used: number }>();

    for (const section of sections) {
      const capacity = section.availability?.capacity ?? 0;
      const used = (section.availability?.reserved_count ?? 0) + (section.availability?.occupied_count ?? 0) + (section.availability?.blocked_count ?? 0);

      totalCapacity += capacity;
      totalUsed += used;

      const cat = byCategory.get(section.vehicle_category)!;
      cat.capacity += capacity;
      cat.used += used;

      const listing = byListing.get(section.parking_id) ?? {
        listingId: section.parking_id,
        listingName: section.parking.name,
        capacity: 0,
        used: 0,
      };
      listing.capacity += capacity;
      listing.used += used;
      byListing.set(section.parking_id, listing);
    }

    return {
      asOf: new Date(),
      overall: { capacity: totalCapacity, used: totalUsed, occupancyPercent: occupancyPercent(totalUsed, totalCapacity) },
      byVehicleCategory: Array.from(byCategory.entries()).map(([category, v]) => ({
        category,
        capacity: v.capacity,
        used: v.used,
        occupancyPercent: occupancyPercent(v.used, v.capacity),
      })),
      byListing: Array.from(byListing.values())
        .map((v) => ({ ...v, occupancyPercent: occupancyPercent(v.used, v.capacity) }))
        .sort((a, b) => b.occupancyPercent - a.occupancyPercent)
        .slice(0, 100),
    };
  }

  private resolveRange(query: ReportDateRangeQueryDto): { from: Date; to: Date } {
    const to = query.to ? new Date(query.to) : new Date();
    const from = query.from ? new Date(query.from) : new Date(to.getTime() - DEFAULT_WINDOW_MS);
    return { from, to };
  }
}

function occupancyPercent(used: number, capacity: number): number {
  return capacity > 0 ? Math.round((used / capacity) * 1000) / 10 : 0;
}

/**
 * Raw SUM()/COUNT() results from Postgres come back through the driver as
 * bigint, string, or number depending on the exact aggregate/column type —
 * never assume which. Both helpers normalize defensively rather than
 * trusting one representation.
 */
function toAmountString(value: bigint | number | string | null): string {
  if (value === null || value === undefined) return '0';
  return typeof value === 'bigint' ? value.toString() : String(value);
}

function toSafeNumber(value: bigint | number | string | null): number {
  if (value === null || value === undefined) return 0;
  return typeof value === 'bigint' ? Number(value) : Number(value);
}
