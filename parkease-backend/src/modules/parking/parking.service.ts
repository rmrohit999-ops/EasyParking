import { BadRequestException, ConflictException, Inject, Injectable, NotFoundException } from '@nestjs/common';
import { ApprovalStatus, ListingStatus, PhotoType, Prisma } from '@prisma/client';
import { PrismaService } from '../../common/prisma/prisma.service';
import { OwnershipResolver } from '../../common/guards/resource-ownership.guard';
import { AuthenticatedUser } from '../../common/decorators/current-user.decorator';
import { STORAGE_SERVICE, StorageService, buildStorageKey } from '../storage/storage.service';
import {
  AssignAttendantDto,
  CreateListingDto,
  CreatePhotoUploadUrlDto,
  CreateSectionDto,
  CreateSpaceDto,
  RegisterPhotoDto,
  UpdateListingDto,
  UpdateListingStatusDto,
  UpdateSectionDto,
  UpdateSectionStatusDto,
  UpdateSpaceDto,
  UpsertLocationDto,
} from './dto/parking.dto';

@Injectable()
export class ParkingService {
  constructor(
    private readonly prisma: PrismaService,
    @Inject(STORAGE_SERVICE) private readonly storage: StorageService,
  ) {}

  // ---------------------------------------------------------------------
  // Listings
  // ---------------------------------------------------------------------

  async createListing(userId: string, dto: CreateListingDto) {
    const ownerProfile = await this.requireOwnerProfile(userId);
    const listing = await this.prisma.parkingListing.create({
      data: {
        owner_id: ownerProfile.id,
        name: dto.name,
        parking_type: dto.parkingType,
        description: dto.description,
        // approval_status defaults PENDING, status defaults PAUSED — an
        // owner cannot go live until an admin approves (Milestone 0 §4/§6).
      },
    });
    return toListingSummary(listing);
  }

  async listMine(userId: string) {
    const ownerProfile = await this.requireOwnerProfile(userId);
    const listings = await this.prisma.parkingListing.findMany({
      where: { owner_id: ownerProfile.id },
      orderBy: { created_at: 'desc' },
    });
    return listings.map(toListingSummary);
  }

  async getOne(listingId: string) {
    const listing = await this.prisma.parkingListing.findUnique({
      where: { id: listingId },
      include: {
        location: true,
        sections: { orderBy: { created_at: 'asc' } },
        photos: { where: { status: 'ACTIVE' } },
      },
    });
    if (!listing) throw new NotFoundException('Parking listing not found.');
    return toListingDetail(listing);
  }

  async updateListing(listingId: string, dto: UpdateListingDto) {
    const listing = await this.prisma.parkingListing.update({
      where: { id: listingId },
      data: { name: dto.name, description: dto.description },
    });
    return toListingSummary(listing);
  }

  async updateListingStatus(listingId: string, dto: UpdateListingStatusDto) {
    const listing = await this.mustGetListing(listingId);
    if (dto.status === ListingStatus.ACTIVE && listing.approval_status !== ApprovalStatus.APPROVED) {
      throw new ConflictException(
        'This listing has not been approved yet. It can only go active after admin review.',
      );
    }
    const updated = await this.prisma.parkingListing.update({
      where: { id: listingId },
      data: { status: dto.status },
    });
    return toListingSummary(updated);
  }

  async upsertLocation(listingId: string, dto: UpsertLocationDto) {
    const location = await this.prisma.parkingLocation.upsert({
      where: { parking_id: listingId },
      create: {
        parking_id: listingId,
        latitude: dto.latitude,
        longitude: dto.longitude,
        address_line: dto.addressLine,
        city: dto.city,
        state: dto.state,
        postal_code: dto.postalCode,
        entrance_notes: dto.entranceNotes,
        location_accuracy_meters: dto.locationAccuracyMeters,
      },
      update: {
        latitude: dto.latitude,
        longitude: dto.longitude,
        address_line: dto.addressLine,
        city: dto.city,
        state: dto.state,
        postal_code: dto.postalCode,
        entrance_notes: dto.entranceNotes,
        location_accuracy_meters: dto.locationAccuracyMeters,
        last_updated_at: new Date(),
      },
    });
    // geog (PostGIS geography(Point,4326)) is kept in sync from lat/lng via
    // raw SQL since Prisma has no native geography type (Unsupported(...)
    // in schema.prisma) — required for the radius search Milestone 5 adds.
    await this.prisma.$executeRawUnsafe(
      `UPDATE parking_locations SET geog = ST_SetSRID(ST_MakePoint($1, $2), 4326)::geography WHERE parking_id = $3`,
      dto.longitude,
      dto.latitude,
      listingId,
    );
    return toLocationView(location);
  }

  async submitForApproval(listingId: string) {
    const listing = await this.mustGetListing(listingId);
    if (listing.approval_status === ApprovalStatus.APPROVED) {
      throw new ConflictException('This listing is already approved.');
    }

    const [location, sectionCount, listingPhotoCount] = await Promise.all([
      this.prisma.parkingLocation.findUnique({ where: { parking_id: listingId } }),
      this.prisma.parkingSection.count({ where: { parking_id: listingId } }),
      this.prisma.parkingPhoto.count({
        where: { parking_id: listingId, photo_type: PhotoType.LISTING, status: 'ACTIVE' },
      }),
    ]);

    const missing: string[] = [];
    if (!location) missing.push('a pinned location');
    if (sectionCount === 0) missing.push('at least one parking section');
    if (listingPhotoCount === 0) missing.push('at least one listing photo');
    if (missing.length > 0) {
      throw new BadRequestException(`Before submitting for review, this listing needs: ${missing.join(', ')}.`);
    }

    const updated = await this.prisma.parkingListing.update({
      where: { id: listingId },
      data: { approval_status: ApprovalStatus.PENDING },
    });
    return toListingSummary(updated);
  }

  // ---------------------------------------------------------------------
  // Sections
  // ---------------------------------------------------------------------

  async createSection(listingId: string, dto: CreateSectionDto) {
    const section = await this.prisma.$transaction(async (tx) => {
      const created = await tx.parkingSection.create({
        data: {
          parking_id: listingId,
          name: dto.name,
          vehicle_category: dto.vehicleCategory,
          supported_vehicle_types: dto.supportedVehicleTypes,
          capacity: dto.capacity,
          hourly_rate_minor_units: dto.hourlyRateMinorUnits,
          is_covered: dto.isCovered ?? false,
          has_security: dto.hasSecurity ?? false,
          has_cctv: dto.hasCctv ?? false,
          has_ev_charging: dto.hasEvCharging ?? false,
          instant_mode_enabled: dto.instantModeEnabled ?? false,
          operating_hours: dto.operatingHours as Prisma.InputJsonValue | undefined,
          location_notes: dto.locationNotes,
        },
      });
      // A section is meaningless for booking without its capacity counter —
      // created in the same transaction so the two rows never drift out of
      // existence relative to each other (Milestone 0 §8 availability model).
      await tx.sectionAvailability.create({
        data: { section_id: created.id, capacity: created.capacity },
      });
      return created;
    });
    return toSectionView(section);
  }

  async listSections(listingId: string) {
    const sections = await this.prisma.parkingSection.findMany({
      where: { parking_id: listingId },
      orderBy: { created_at: 'asc' },
    });
    return sections.map(toSectionView);
  }

  async updateSection(listingId: string, sectionId: string, dto: UpdateSectionDto) {
    const section = await this.mustGetSection(listingId, sectionId);

    if (dto.capacity !== undefined && dto.capacity !== section.capacity) {
      const availability = await this.prisma.sectionAvailability.findUnique({ where: { section_id: sectionId } });
      const committed = (availability?.reserved_count ?? 0) + (availability?.occupied_count ?? 0) + (availability?.blocked_count ?? 0);
      if (dto.capacity < committed) {
        throw new ConflictException(
          `Capacity can't be reduced below ${committed} — that many spaces are currently reserved, occupied, or blocked.`,
        );
      }
    }

    const updated = await this.prisma.$transaction(async (tx) => {
      const result = await tx.parkingSection.update({
        where: { id: sectionId },
        data: {
          name: dto.name,
          supported_vehicle_types: dto.supportedVehicleTypes,
          capacity: dto.capacity,
          hourly_rate_minor_units: dto.hourlyRateMinorUnits,
          is_covered: dto.isCovered,
          has_security: dto.hasSecurity,
          has_cctv: dto.hasCctv,
          has_ev_charging: dto.hasEvCharging,
          instant_mode_enabled: dto.instantModeEnabled,
          operating_hours: dto.operatingHours as Prisma.InputJsonValue | undefined,
          location_notes: dto.locationNotes,
        },
      });
      if (dto.capacity !== undefined && dto.capacity !== section.capacity) {
        await tx.sectionAvailability.update({ where: { section_id: sectionId }, data: { capacity: dto.capacity } });
      }
      return result;
    });
    return toSectionView(updated);
  }

  async updateSectionStatus(listingId: string, sectionId: string, dto: UpdateSectionStatusDto) {
    const section = await this.mustGetSection(listingId, sectionId);
    if (dto.status === ListingStatus.ACTIVE && section.approval_status !== ApprovalStatus.APPROVED) {
      throw new ConflictException(
        'This section has not been approved yet. It can only go active after admin review.',
      );
    }
    const updated = await this.prisma.parkingSection.update({ where: { id: sectionId }, data: { status: dto.status } });
    return toSectionView(updated);
  }

  async removeSection(listingId: string, sectionId: string) {
    await this.mustGetSection(listingId, sectionId);
    const [holds, bookings] = await Promise.all([
      this.prisma.bookingHold.count({ where: { section_id: sectionId } }),
      this.prisma.booking.count({ where: { section_id: sectionId } }),
    ]);
    if (holds > 0 || bookings > 0) {
      throw new ConflictException('This section has booking history and cannot be deleted. Pause it instead.');
    }
    await this.prisma.$transaction([
      this.prisma.sectionAvailability.deleteMany({ where: { section_id: sectionId } }),
      this.prisma.parkingSpace.deleteMany({ where: { section_id: sectionId } }),
      this.prisma.parkingSection.delete({ where: { id: sectionId } }),
    ]);
  }

  // ---------------------------------------------------------------------
  // Spaces (optional space-level tracking within a section)
  // ---------------------------------------------------------------------

  async createSpace(listingId: string, sectionId: string, dto: CreateSpaceDto) {
    await this.mustGetSection(listingId, sectionId);
    const existing = await this.prisma.parkingSpace.findUnique({
      where: { section_id_space_label: { section_id: sectionId, space_label: dto.spaceLabel } },
    });
    if (existing) throw new ConflictException(`A space labeled "${dto.spaceLabel}" already exists in this section.`);

    const space = await this.prisma.parkingSpace.create({
      data: {
        section_id: sectionId,
        space_label: dto.spaceLabel,
        size: dto.size,
        length_cm: dto.lengthCm,
        width_cm: dto.widthCm,
        height_clearance_cm: dto.heightClearanceCm,
        weight_limit_kg: dto.weightLimitKg,
        is_ev_capable: dto.isEvCapable ?? false,
        is_covered: dto.isCovered ?? false,
        is_accessible: dto.isAccessible ?? false,
      },
    });
    return toSpaceView(space);
  }

  async listSpaces(listingId: string, sectionId: string) {
    await this.mustGetSection(listingId, sectionId);
    const spaces = await this.prisma.parkingSpace.findMany({
      where: { section_id: sectionId },
      orderBy: { space_label: 'asc' },
    });
    return spaces.map(toSpaceView);
  }

  async updateSpace(listingId: string, sectionId: string, spaceId: string, dto: UpdateSpaceDto) {
    await this.mustGetSpace(listingId, sectionId, spaceId);
    const space = await this.prisma.parkingSpace.update({
      where: { id: spaceId },
      data: {
        size: dto.size,
        length_cm: dto.lengthCm,
        width_cm: dto.widthCm,
        height_clearance_cm: dto.heightClearanceCm,
        weight_limit_kg: dto.weightLimitKg,
        is_ev_capable: dto.isEvCapable,
        is_covered: dto.isCovered,
        is_accessible: dto.isAccessible,
        active: dto.active,
      },
    });
    return toSpaceView(space);
  }

  async removeSpace(listingId: string, sectionId: string, spaceId: string) {
    const space = await this.mustGetSpace(listingId, sectionId, spaceId);
    if (space.status !== 'AVAILABLE') {
      throw new ConflictException('This space is currently reserved, occupied, or blocked and cannot be removed.');
    }
    await this.prisma.parkingSpace.delete({ where: { id: spaceId } });
  }

  // ---------------------------------------------------------------------
  // Photos — presigned upload, then metadata registration
  // ---------------------------------------------------------------------

  async createPhotoUploadUrl(listingId: string, _userId: string, dto: CreatePhotoUploadUrlDto) {
    if (dto.photoType === PhotoType.SECTION) {
      if (!dto.sectionId) throw new BadRequestException('sectionId is required for a SECTION photo.');
      await this.mustGetSection(listingId, dto.sectionId);
    }
    const ext = dto.contentType === 'image/png' ? '.png' : dto.contentType === 'image/webp' ? '.webp' : '.jpg';
    const key = buildStorageKey(`parking/${listingId}/${dto.photoType.toLowerCase()}`, `photo${ext}`);
    const presigned = await this.storage.createUploadUrl(key, dto.contentType);
    return presigned;
  }

  async registerPhoto(listingId: string, userId: string, dto: RegisterPhotoDto) {
    if (dto.photoType === PhotoType.SECTION) {
      if (!dto.sectionId) throw new BadRequestException('sectionId is required for a SECTION photo.');
      await this.mustGetSection(listingId, dto.sectionId);
    }
    if (!dto.storageKey.startsWith(`parking/${listingId}/`)) {
      throw new BadRequestException('That upload does not belong to this listing.');
    }
    const photo = await this.prisma.parkingPhoto.create({
      data: {
        parking_id: listingId,
        section_id: dto.photoType === PhotoType.SECTION ? dto.sectionId : null,
        photo_type: dto.photoType,
        storage_key: dto.storageKey,
        uploaded_by: userId,
      },
    });
    return { id: photo.id, photoType: photo.photo_type, sectionId: photo.section_id, uploadedAt: photo.uploaded_at };
  }

  async listPhotos(listingId: string) {
    const photos = await this.prisma.parkingPhoto.findMany({
      where: { parking_id: listingId, status: 'ACTIVE' },
      orderBy: { uploaded_at: 'desc' },
    });
    return Promise.all(
      photos.map(async (p) => ({
        id: p.id,
        photoType: p.photo_type,
        sectionId: p.section_id,
        uploadedAt: p.uploaded_at,
        viewUrl: await this.storage.createReadUrl(p.storage_key),
      })),
    );
  }

  async removePhoto(listingId: string, photoId: string) {
    const photo = await this.prisma.parkingPhoto.findFirst({ where: { id: photoId, parking_id: listingId } });
    if (!photo) throw new NotFoundException('Photo not found.');
    await this.prisma.parkingPhoto.update({ where: { id: photoId }, data: { status: 'REMOVED' } });
    // Best-effort object deletion — the DB row is the source of truth for
    // "is this photo shown"; a failure here leaves an orphaned object in
    // the bucket, not a broken listing, so it's swallowed rather than
    // failing the request.
    try {
      await this.storage.deleteObject(photo.storage_key);
    } catch {
      /* orphaned object cleanup can be handled by a later housekeeping job */
    }
  }

  // ---------------------------------------------------------------------
  // Attendants (Milestone 8) — see AssignAttendantDto's doc comment for the
  // owner-hired employment model this implements.
  // ---------------------------------------------------------------------

  async assignAttendant(ownerId: string, listingId: string, dto: AssignAttendantDto) {
    const normalizedEmail = dto.attendantEmail.trim().toLowerCase();
    const user = await this.prisma.user.findUnique({ where: { email: normalizedEmail } });
    if (!user) {
      throw new NotFoundException('No ParkEase account found with that email. The attendant needs to sign up first.');
    }
    const ownerProfile = await this.prisma.ownerProfile.findUniqueOrThrow({ where: { user_id: ownerId } });

    const attendantProfile = await this.prisma.$transaction(async (tx) => {
      await tx.userRoleAssignment.upsert({
        where: { user_id_role: { user_id: user.id, role: 'ATTENDANT' } },
        update: { status: 'ACTIVE' },
        create: { user_id: user.id, role: 'ATTENDANT' },
      });
      return tx.attendantProfile.upsert({
        where: { user_id: user.id },
        // Re-assigning an attendant who already has a profile (e.g. from a
        // different owner previously) doesn't overwrite employer_owner_id —
        // one attendant account can hold assignments across multiple
        // owners' listings via separate attendant_assignments rows; only
        // the very first owner to invite them "employs" them in this field,
        // which is informational only (authorization is always checked via
        // attendant_assignments, never employer_owner_id).
        update: {},
        create: { user_id: user.id, employer_owner_id: ownerProfile.id, status: 'ACTIVE' },
      });
    });

    const existing = await this.prisma.attendantAssignment.findFirst({
      where: { attendant_id: attendantProfile.id, parking_id: listingId, revoked_at: null },
    });
    if (existing) {
      throw new ConflictException('This account is already an active attendant for this listing.');
    }

    const assignment = await this.prisma.attendantAssignment.create({
      data: {
        attendant_id: attendantProfile.id,
        parking_id: listingId,
        authorized_categories: dto.authorizedCategories,
        assigned_by: ownerId,
      },
    });
    return toAttendantAssignmentView(assignment, user);
  }

  async listAttendants(listingId: string) {
    const assignments = await this.prisma.attendantAssignment.findMany({
      where: { parking_id: listingId, revoked_at: null },
      include: { attendant: { include: { user: true } } },
      orderBy: { assigned_at: 'desc' },
    });
    return assignments.map((a) => toAttendantAssignmentView(a, a.attendant.user));
  }

  async revokeAttendant(listingId: string, assignmentId: string) {
    const assignment = await this.prisma.attendantAssignment.findFirst({
      where: { id: assignmentId, parking_id: listingId },
    });
    if (!assignment) throw new NotFoundException('Attendant assignment not found.');
    if (assignment.revoked_at) return; // already revoked — idempotent
    await this.prisma.attendantAssignment.update({ where: { id: assignmentId }, data: { revoked_at: new Date() } });
  }

  // ---------------------------------------------------------------------
  // Admin approval workflow
  // ---------------------------------------------------------------------

  async listPendingListings() {
    const listings = await this.prisma.parkingListing.findMany({
      where: { approval_status: { in: [ApprovalStatus.PENDING, ApprovalStatus.NEEDS_MORE_INFORMATION] } },
      include: { location: true, sections: true, photos: { where: { status: 'ACTIVE' } } },
      orderBy: { created_at: 'asc' },
    });
    return listings.map(toListingDetail);
  }

  async approveListing(adminId: string, listingId: string) {
    const listing = await this.mustGetListing(listingId);
    const updated = await this.prisma.parkingListing.update({
      where: { id: listingId },
      data: { approval_status: ApprovalStatus.APPROVED },
    });
    await this.writeAuditLog(adminId, 'PARKING_LISTING_APPROVED', 'ParkingListing', listingId, listing, updated);
    return toListingSummary(updated);
  }

  async rejectListing(adminId: string, listingId: string, reason: string) {
    const listing = await this.mustGetListing(listingId);
    const updated = await this.prisma.parkingListing.update({
      where: { id: listingId },
      data: { approval_status: ApprovalStatus.REJECTED },
    });
    await this.writeAuditLog(adminId, 'PARKING_LISTING_REJECTED', 'ParkingListing', listingId, listing, {
      ...updated,
      reason,
    });
    return toListingSummary(updated);
  }

  async requestMoreInfoListing(adminId: string, listingId: string, message: string) {
    const listing = await this.mustGetListing(listingId);
    const updated = await this.prisma.parkingListing.update({
      where: { id: listingId },
      data: { approval_status: ApprovalStatus.NEEDS_MORE_INFORMATION },
    });
    await this.writeAuditLog(adminId, 'PARKING_LISTING_NEEDS_MORE_INFO', 'ParkingListing', listingId, listing, {
      ...updated,
      message,
    });
    return toListingSummary(updated);
  }

  async listPendingSections() {
    const sections = await this.prisma.parkingSection.findMany({
      where: { approval_status: { in: [ApprovalStatus.PENDING, ApprovalStatus.NEEDS_MORE_INFORMATION] } },
      orderBy: { created_at: 'asc' },
    });
    return sections.map(toSectionView);
  }

  async approveSection(adminId: string, sectionId: string) {
    const section = await this.prisma.parkingSection.findUnique({ where: { id: sectionId } });
    if (!section) throw new NotFoundException('Section not found.');
    const updated = await this.prisma.parkingSection.update({
      where: { id: sectionId },
      data: { approval_status: ApprovalStatus.APPROVED },
    });
    await this.writeAuditLog(adminId, 'PARKING_SECTION_APPROVED', 'ParkingSection', sectionId, section, updated);
    return toSectionView(updated);
  }

  async rejectSection(adminId: string, sectionId: string, reason: string) {
    const section = await this.prisma.parkingSection.findUnique({ where: { id: sectionId } });
    if (!section) throw new NotFoundException('Section not found.');
    const updated = await this.prisma.parkingSection.update({
      where: { id: sectionId },
      data: { approval_status: ApprovalStatus.REJECTED },
    });
    await this.writeAuditLog(adminId, 'PARKING_SECTION_REJECTED', 'ParkingSection', sectionId, section, {
      ...updated,
      reason,
    });
    return toSectionView(updated);
  }

  // ---------------------------------------------------------------------
  // Internal helpers
  // ---------------------------------------------------------------------

  private async requireOwnerProfile(userId: string) {
    const ownerProfile = await this.prisma.ownerProfile.findUnique({ where: { user_id: userId } });
    if (!ownerProfile) {
      throw new BadRequestException('You need an owner profile before you can list parking. Please complete owner sign-up first.');
    }
    return ownerProfile;
  }

  private async mustGetListing(listingId: string) {
    const listing = await this.prisma.parkingListing.findUnique({ where: { id: listingId } });
    if (!listing) throw new NotFoundException('Parking listing not found.');
    return listing;
  }

  private async mustGetSection(listingId: string, sectionId: string) {
    const section = await this.prisma.parkingSection.findFirst({ where: { id: sectionId, parking_id: listingId } });
    if (!section) throw new NotFoundException('Parking section not found.');
    return section;
  }

  private async mustGetSpace(listingId: string, sectionId: string, spaceId: string) {
    await this.mustGetSection(listingId, sectionId);
    const space = await this.prisma.parkingSpace.findFirst({ where: { id: spaceId, section_id: sectionId } });
    if (!space) throw new NotFoundException('Parking space not found.');
    return space;
  }

  private async writeAuditLog(
    actorId: string,
    action: string,
    targetType: string,
    targetId: string,
    beforeState: unknown,
    afterState: unknown,
  ) {
    await this.prisma.auditLog.create({
      data: {
        actor_id: actorId,
        actor_role: 'ADMIN',
        action,
        target_type: targetType,
        target_id: targetId,
        before_state: beforeState as object,
        after_state: afterState as object,
      },
    });
  }
}

// ---------------------------------------------------------------------
// View mappers — snake_case DB rows to camelCase API shapes
// ---------------------------------------------------------------------

function toListingSummary(l: {
  id: string;
  name: string;
  parking_type: string;
  description: string | null;
  approval_status: string;
  status: string;
  timezone: string;
  created_at: Date;
  updated_at: Date;
}) {
  return {
    id: l.id,
    name: l.name,
    parkingType: l.parking_type,
    description: l.description,
    approvalStatus: l.approval_status,
    status: l.status,
    timezone: l.timezone,
    createdAt: l.created_at,
    updatedAt: l.updated_at,
  };
}

function toListingDetail(l: Parameters<typeof toListingSummary>[0] & {
  location: Parameters<typeof toLocationView>[0] | null;
  sections: Parameters<typeof toSectionView>[0][];
  photos: { id: string; photo_type: string; section_id: string | null }[];
}) {
  return {
    ...toListingSummary(l),
    location: l.location ? toLocationView(l.location) : null,
    sections: l.sections.map(toSectionView),
    photoCount: l.photos.length,
  };
}

function toLocationView(loc: {
  latitude: number;
  longitude: number;
  address_line: string;
  city: string;
  state: string;
  postal_code: string;
  entrance_notes: string | null;
  location_accuracy_meters: number | null;
}) {
  return {
    latitude: loc.latitude,
    longitude: loc.longitude,
    addressLine: loc.address_line,
    city: loc.city,
    state: loc.state,
    postalCode: loc.postal_code,
    entranceNotes: loc.entrance_notes,
    locationAccuracyMeters: loc.location_accuracy_meters,
  };
}

function toSectionView(s: {
  id: string;
  name: string;
  vehicle_category: string;
  supported_vehicle_types: string[];
  capacity: number;
  currency: string;
  hourly_rate_minor_units: number;
  is_covered: boolean;
  has_security: boolean;
  has_cctv: boolean;
  has_ev_charging: boolean;
  instant_mode_enabled: boolean;
  status: string;
  approval_status: string;
  location_notes: string | null;
}) {
  return {
    id: s.id,
    name: s.name,
    vehicleCategory: s.vehicle_category,
    supportedVehicleTypes: s.supported_vehicle_types,
    capacity: s.capacity,
    currency: s.currency,
    hourlyRateMinorUnits: s.hourly_rate_minor_units,
    isCovered: s.is_covered,
    hasSecurity: s.has_security,
    hasCctv: s.has_cctv,
    hasEvCharging: s.has_ev_charging,
    instantModeEnabled: s.instant_mode_enabled,
    status: s.status,
    approvalStatus: s.approval_status,
    locationNotes: s.location_notes,
  };
}

function toSpaceView(sp: {
  id: string;
  space_label: string;
  size: string | null;
  length_cm: number | null;
  width_cm: number | null;
  height_clearance_cm: number | null;
  weight_limit_kg: number | null;
  is_ev_capable: boolean;
  is_covered: boolean;
  is_accessible: boolean;
  status: string;
  active: boolean;
}) {
  return {
    id: sp.id,
    spaceLabel: sp.space_label,
    size: sp.size,
    lengthCm: sp.length_cm,
    widthCm: sp.width_cm,
    heightClearanceCm: sp.height_clearance_cm,
    weightLimitKg: sp.weight_limit_kg,
    isEvCapable: sp.is_ev_capable,
    isCovered: sp.is_covered,
    isAccessible: sp.is_accessible,
    status: sp.status,
    active: sp.active,
  };
}

function toAttendantAssignmentView(
  a: { id: string; parking_id: string; authorized_categories: string[]; assigned_at: Date; revoked_at: Date | null },
  user: { id: string; email: string | null; phone: string | null },
) {
  return {
    id: a.id,
    parkingId: a.parking_id,
    authorizedCategories: a.authorized_categories,
    assignedAt: a.assigned_at,
    revokedAt: a.revoked_at,
    attendant: { userId: user.id, email: user.email, phone: user.phone },
  };
}

// ---------------------------------------------------------------------
// Ownership resolvers — registered in ParkingModule under these string
// tokens for @CheckOwnership(...).
// ---------------------------------------------------------------------

@Injectable()
export class ParkingListingOwnershipResolver implements OwnershipResolver {
  constructor(private readonly prisma: PrismaService) {}

  async resolve(user: AuthenticatedUser & { roles: string[] }, resourceId: string): Promise<boolean> {
    const listing = await this.prisma.parkingListing.findUnique({
      where: { id: resourceId },
      include: { owner: true },
    });
    return listing?.owner.user_id === user.id;
  }
}
