import { ConflictException, Injectable, NotFoundException } from '@nestjs/common';
import { ApprovalStatus, VehicleCategory } from '@prisma/client';
import { PrismaService } from '../../common/prisma/prisma.service';

@Injectable()
export class AvailabilityService {
  constructor(private readonly prisma: PrismaService) {}

  /**
   * Per-category aggregated availability for a public/authenticated read —
   * "never a single blended Available badge" (Milestone 0 §7): a listing
   * with both a two-wheeler and a four-wheeler section reports each
   * category's numbers separately, summed across however many sections of
   * that category the listing has.
   */
  async getListingAvailability(listingId: string, category?: VehicleCategory) {
    const listing = await this.prisma.parkingListing.findFirst({
      where: { id: listingId, approval_status: ApprovalStatus.APPROVED },
    });
    if (!listing) throw new NotFoundException('Parking listing not found.');

    const sections = await this.prisma.parkingSection.findMany({
      where: {
        parking_id: listingId,
        approval_status: ApprovalStatus.APPROVED,
        ...(category ? { vehicle_category: category } : {}),
      },
      include: { availability: true },
    });

    const byCategory = new Map<VehicleCategory, { capacity: number; reserved: number; occupied: number; blocked: number }>();
    for (const section of sections) {
      const bucket = byCategory.get(section.vehicle_category) ?? { capacity: 0, reserved: 0, occupied: 0, blocked: 0 };
      bucket.capacity += section.capacity;
      bucket.reserved += section.availability?.reserved_count ?? 0;
      bucket.occupied += section.availability?.occupied_count ?? 0;
      bucket.blocked += section.availability?.blocked_count ?? 0;
      byCategory.set(section.vehicle_category, bucket);
    }

    return Array.from(byCategory.entries()).map(([cat, b]) => ({
      category: cat,
      capacity: b.capacity,
      available: Math.max(0, b.capacity - b.reserved - b.occupied - b.blocked),
    }));
  }

  /**
   * Owner manually blocks N spaces in a section (maintenance, private use,
   * etc.) — distinct from `capacity` (the section's total size, edited via
   * ParkingService.updateSection): this only changes how many of that
   * fixed capacity are currently unavailable to book. Guarded the same
   * atomic way as hold creation — can't block more than what isn't already
   * reserved/occupied.
   */
  async setBlockedCount(listingId: string, sectionId: string, blockedCount: number) {
    const section = await this.prisma.parkingSection.findFirst({ where: { id: sectionId, parking_id: listingId } });
    if (!section) throw new NotFoundException('Parking section not found.');

    const affected = await this.prisma.$executeRaw`
      UPDATE section_availability
      SET blocked_count = ${blockedCount}, version = version + 1, updated_at = now()
      WHERE section_id = ${sectionId}
        AND (reserved_count + occupied_count + ${blockedCount}) <= capacity
    `;
    if (affected === 0) {
      throw new ConflictException(
        "That would block more spaces than are currently free — reduce the count or wait for reserved/occupied spaces to clear.",
      );
    }

    const availability = await this.prisma.sectionAvailability.findUniqueOrThrow({ where: { section_id: sectionId } });
    return {
      sectionId,
      capacity: availability.capacity,
      reservedCount: availability.reserved_count,
      occupiedCount: availability.occupied_count,
      blockedCount: availability.blocked_count,
      availableCount: Math.max(
        0,
        availability.capacity - availability.reserved_count - availability.occupied_count - availability.blocked_count,
      ),
    };
  }
}
