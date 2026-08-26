import { BadRequestException, ForbiddenException, Inject, Injectable, NotFoundException } from '@nestjs/common';
import { ApprovalStatus, ListingStatus, VehicleCategory, VehicleType } from '@prisma/client';
import { PrismaService } from '../../common/prisma/prisma.service';
import { STORAGE_SERVICE, StorageService } from '../storage/storage.service';
import { SearchParkingQueryDto } from './dto/discovery.dto';

export interface SearchRow {
  listing_id: string;
  listing_name: string;
  parking_type: string;
  address_line: string;
  city: string;
  latitude: number;
  longitude: number;
  distance_meters: number;
  section_id: string;
  section_name: string;
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
  reserved_count: number;
  occupied_count: number;
  blocked_count: number;
}

@Injectable()
export class DiscoveryService {
  constructor(
    private readonly prisma: PrismaService,
    @Inject(STORAGE_SERVICE) private readonly storage: StorageService,
  ) {}

  /**
   * A search targets either a specific vehicle the driver owns (the normal
   * path — compatibility is checked against its real category/type) or an
   * explicit category for pre-vehicle browsing (Milestone 0 §7.4's "explicit
   * category for anonymous/pre-vehicle browsing"). Exactly one must be
   * given; requiring it here (rather than in the DTO) lets the error name
   * the actual problem instead of class-validator's generic wording.
   */
  private async resolveSearchProfile(
    driverId: string,
    dto: SearchParkingQueryDto,
  ): Promise<{ category: VehicleCategory; vehicleType?: VehicleType }> {
    if (dto.vehicleId) {
      const vehicle = await this.prisma.vehicle.findUnique({ where: { id: dto.vehicleId } });
      if (!vehicle || vehicle.status !== 'ACTIVE') throw new NotFoundException('Vehicle not found.');
      if (vehicle.driver_id !== driverId) throw new ForbiddenException('That vehicle does not belong to you.');
      return { category: vehicle.category, vehicleType: vehicle.vehicle_type };
    }
    if (dto.category) {
      return { category: dto.category };
    }
    throw new BadRequestException('Provide either vehicleId or category to search.');
  }

  async search(driverId: string, dto: SearchParkingQueryDto) {
    const { category, vehicleType } = await this.resolveSearchProfile(driverId, dto);
    const radiusMeters = dto.radiusMeters ?? 3000;
    const page = dto.page ?? 1;
    const pageSize = dto.pageSize ?? 20;

    // A raw query is unavoidable here — Prisma has no query-builder support
    // for PostGIS geography operators (parking_locations.geog is declared
    // Unsupported(...) in schema.prisma). Grouping section rows into
    // per-listing results with per-category availability counts happens in
    // application code below rather than SQL, so the shape matches
    // Milestone 0 §7's "never a single blended Available badge" requirement.
    //
    // NOTE (known MVP limit, revisit per Milestone 0 §8's Postgres-to-Redis
    // escalation plan under real load): pagination is applied to the
    // grouped listing list in application code after fetching up to 500
    // matching section rows, not pushed down as SQL LIMIT/OFFSET — pushing
    // it down would risk splitting one listing's sections across two pages.
    const rows = await this.prisma.$queryRawUnsafe<SearchRow[]>(
      `
      SELECT
        pl.id AS listing_id,
        pl.name AS listing_name,
        pl.parking_type AS parking_type,
        loc.address_line AS address_line,
        loc.city AS city,
        loc.latitude AS latitude,
        loc.longitude AS longitude,
        ST_Distance(loc.geog, ST_SetSRID(ST_MakePoint($1, $2), 4326)::geography) AS distance_meters,
        ps.id AS section_id,
        ps.name AS section_name,
        ps.vehicle_category AS vehicle_category,
        ps.supported_vehicle_types AS supported_vehicle_types,
        ps.capacity AS capacity,
        ps.currency AS currency,
        ps.hourly_rate_minor_units AS hourly_rate_minor_units,
        ps.is_covered AS is_covered,
        ps.has_security AS has_security,
        ps.has_cctv AS has_cctv,
        ps.has_ev_charging AS has_ev_charging,
        ps.instant_mode_enabled AS instant_mode_enabled,
        COALESCE(sa.reserved_count, 0) AS reserved_count,
        COALESCE(sa.occupied_count, 0) AS occupied_count,
        COALESCE(sa.blocked_count, 0) AS blocked_count
      FROM parking_sections ps
      JOIN parking_listings pl ON pl.id = ps.parking_id
      JOIN parking_locations loc ON loc.parking_id = pl.id
      LEFT JOIN section_availability sa ON sa.section_id = ps.id
      WHERE pl.approval_status = 'APPROVED'
        AND pl.status = 'ACTIVE'
        AND ps.approval_status = 'APPROVED'
        AND ps.status = 'ACTIVE'
        AND ps.vehicle_category = $3::"VehicleCategory"
        AND ($4::"VehicleType" IS NULL OR $4::"VehicleType" = ANY(ps.supported_vehicle_types))
        AND ($5::boolean IS NULL OR ps.instant_mode_enabled = $5)
        AND ($6::boolean IS NULL OR ps.is_covered = $6)
        AND ($7::boolean IS NULL OR ps.has_security = $7)
        AND ($8::boolean IS NULL OR ps.has_cctv = $8)
        AND ($9::boolean IS NULL OR ps.has_ev_charging = $9)
        AND ST_DWithin(loc.geog, ST_SetSRID(ST_MakePoint($1, $2), 4326)::geography, $10)
      ORDER BY distance_meters ASC
      LIMIT 500
      `,
      dto.lng,
      dto.lat,
      category,
      vehicleType ?? null,
      dto.instantOnly ?? null,
      dto.covered ?? null,
      dto.hasSecurity ?? null,
      dto.hasCctv ?? null,
      dto.hasEvCharging ?? null,
      radiusMeters,
    );

    const byListing = new Map<
      string,
      {
        id: string;
        name: string;
        parkingType: string;
        addressLine: string;
        city: string;
        latitude: number;
        longitude: number;
        distanceMeters: number;
        sections: ReturnType<typeof toSectionSearchView>[];
      }
    >();

    for (const row of rows) {
      const existing = byListing.get(row.listing_id);
      const section = toSectionSearchView(row);
      if (existing) {
        existing.sections.push(section);
        existing.distanceMeters = Math.min(existing.distanceMeters, row.distance_meters);
      } else {
        byListing.set(row.listing_id, {
          id: row.listing_id,
          name: row.listing_name,
          parkingType: row.parking_type,
          addressLine: row.address_line,
          city: row.city,
          latitude: row.latitude,
          longitude: row.longitude,
          distanceMeters: row.distance_meters,
          sections: [section],
        });
      }
    }

    const allListings = Array.from(byListing.values()).sort((a, b) => a.distanceMeters - b.distanceMeters);
    const start = (page - 1) * pageSize;
    const pageItems = allListings.slice(start, start + pageSize);

    // Only for the page actually being returned, never the full match set
    // — a presigned URL is generated per listing, so this stays bounded to
    // pageSize (default 20) regardless of how many listings matched.
    // Storage being unconfigured must never fail the whole search — it
    // already reports "unavailable" honestly everywhere else (uploads,
    // listing photos), so here it just means no primaryPhotoUrl, exactly
    // like a listing that genuinely has no photos yet.
    const photoUrlByListing = new Map<string, string>();
    if (this.storage.isConfigured && pageItems.length > 0) {
      const primaryPhotos = await this.prisma.parkingPhoto.findMany({
        where: { parking_id: { in: pageItems.map((l) => l.id) }, status: 'ACTIVE' },
        distinct: ['parking_id'],
        orderBy: { uploaded_at: 'asc' },
        select: { parking_id: true, storage_key: true },
      });
      await Promise.all(
        primaryPhotos.map(async (p) => {
          photoUrlByListing.set(p.parking_id, await this.storage.createReadUrl(p.storage_key));
        }),
      );
    }

    // Same bounded-to-page pattern as the photo lookup above — real
    // averages from the `reviews` table (Review model existed unused since
    // Milestone 0; this is the first thing to actually read it), never a
    // fabricated rating. A listing with zero ACTIVE reviews simply gets
    // averageRating: null / ratingCount: 0, distinguishable from "has a
    // real low rating" on the client.
    const ratingByListing = await this.fetchRatingSummaries(pageItems.map((l) => l.id));

    return {
      page,
      pageSize,
      totalListings: allListings.length,
      results: pageItems.map((item) => ({
        ...item,
        primaryPhotoUrl: photoUrlByListing.get(item.id) ?? null,
        averageRating: ratingByListing.get(item.id)?.average ?? null,
        ratingCount: ratingByListing.get(item.id)?.count ?? 0,
      })),
    };
  }

  /**
   * `ratings` is a per-review Json breakdown (`{overall, cleanliness,
   * security, location}` — see Review model); only `overall` is aggregated
   * into the listing-level star rating shown to drivers. Bounded to the
   * given listing ids so callers (search's page, one listing's detail) stay
   * cheap regardless of total review volume.
   */
  private async fetchRatingSummaries(listingIds: string[]): Promise<Map<string, { average: number; count: number }>> {
    const result = new Map<string, { average: number; count: number }>();
    if (listingIds.length === 0) return result;

    const rows = await this.prisma.$queryRawUnsafe<{ parking_id: string; avg_rating: number; rating_count: bigint }[]>(
      `
      SELECT parking_id, AVG((ratings->>'overall')::numeric) AS avg_rating, COUNT(*) AS rating_count
      FROM reviews
      WHERE parking_id = ANY($1::text[]) AND status = 'ACTIVE'
      GROUP BY parking_id
      `,
      listingIds,
    );
    rows.forEach((row) => {
      result.set(row.parking_id, { average: Math.round(Number(row.avg_rating) * 10) / 10, count: Number(row.rating_count) });
    });
    return result;
  }

  async getListingDetail(listingId: string) {
    const listing = await this.prisma.parkingListing.findFirst({
      where: { id: listingId, approval_status: ApprovalStatus.APPROVED },
      include: {
        location: true,
        sections: { where: { approval_status: ApprovalStatus.APPROVED }, include: { availability: true } },
        photos: { where: { status: 'ACTIVE' } },
      },
    });
    if (!listing) throw new NotFoundException('Parking listing not found.');
    const ratingSummary = (await this.fetchRatingSummaries([listing.id])).get(listing.id);

    return {
      id: listing.id,
      name: listing.name,
      parkingType: listing.parking_type,
      description: listing.description,
      status: listing.status,
      averageRating: ratingSummary?.average ?? null,
      ratingCount: ratingSummary?.count ?? 0,
      location: listing.location
        ? {
            latitude: listing.location.latitude,
            longitude: listing.location.longitude,
            addressLine: listing.location.address_line,
            city: listing.location.city,
            state: listing.location.state,
            postalCode: listing.location.postal_code,
            entranceNotes: listing.location.entrance_notes,
          }
        : null,
      sections: listing.sections.map((s) => ({
        id: s.id,
        name: s.name,
        vehicleCategory: s.vehicle_category,
        supportedVehicleTypes: s.supported_vehicle_types,
        currency: s.currency,
        hourlyRateMinorUnits: s.hourly_rate_minor_units,
        isCovered: s.is_covered,
        hasSecurity: s.has_security,
        hasCctv: s.has_cctv,
        hasEvCharging: s.has_ev_charging,
        instantModeEnabled: s.instant_mode_enabled,
        status: s.status,
        availableCount: computeAvailable(s.capacity, s.availability),
      })),
      photoCount: listing.photos.length,
    };
  }

  async listSections(listingId: string) {
    const listing = await this.prisma.parkingListing.findFirst({
      where: { id: listingId, approval_status: ApprovalStatus.APPROVED },
    });
    if (!listing) throw new NotFoundException('Parking listing not found.');

    const sections = await this.prisma.parkingSection.findMany({
      where: { parking_id: listingId, approval_status: ApprovalStatus.APPROVED },
      include: { availability: true },
    });
    return sections.map((s) => ({
      id: s.id,
      name: s.name,
      vehicleCategory: s.vehicle_category,
      status: s.status,
      availableCount: computeAvailable(s.capacity, s.availability),
    }));
  }

  /** Public — lets a driver read reviews before booking, same "no auth needed to browse" posture as getListingDetail. Driver identity is deliberately reduced to a first-name-only label, not full contact details. */
  async listReviews(listingId: string, page = 1, pageSize = 20) {
    const listing = await this.prisma.parkingListing.findFirst({ where: { id: listingId, approval_status: ApprovalStatus.APPROVED } });
    if (!listing) throw new NotFoundException('Parking listing not found.');

    const [reviews, total] = await Promise.all([
      this.prisma.review.findMany({
        where: { parking_id: listingId, status: 'ACTIVE' },
        orderBy: { created_at: 'desc' },
        skip: (page - 1) * pageSize,
        take: pageSize,
      }),
      this.prisma.review.count({ where: { parking_id: listingId, status: 'ACTIVE' } }),
    ]);

    // Review.driver_id has no declared Prisma relation (schema-only model,
    // never built on before this) — a bounded follow-up lookup, same
    // "no name field on User" gap booking.service.ts's toBookingView doc
    // comment already discloses; first segment of the email local part is
    // a reasonable, non-identifying-enough display label.
    const drivers = await this.prisma.user.findMany({
      where: { id: { in: reviews.map((r) => r.driver_id) } },
      select: { id: true, email: true },
    });
    const emailByDriverId = new Map(drivers.map((d) => [d.id, d.email]));

    return {
      page,
      pageSize,
      total,
      results: reviews.map((r) => ({
        id: r.id,
        ratings: r.ratings,
        comment: r.comment,
        createdAt: r.created_at,
        reviewerLabel: emailByDriverId.get(r.driver_id)?.split('@')[0] ?? 'ParkEase driver',
      })),
    };
  }

  // ---------------------------------------------------------------------
  // Favorites
  // ---------------------------------------------------------------------

  async addFavorite(driverId: string, listingId: string) {
    const listing = await this.prisma.parkingListing.findUnique({ where: { id: listingId } });
    if (!listing) throw new NotFoundException('Parking listing not found.');

    await this.prisma.favoriteListing.upsert({
      where: { driver_id_parking_id: { driver_id: driverId, parking_id: listingId } },
      create: { driver_id: driverId, parking_id: listingId },
      update: {},
    });
  }

  async removeFavorite(driverId: string, listingId: string) {
    await this.prisma.favoriteListing.deleteMany({ where: { driver_id: driverId, parking_id: listingId } });
  }

  async listFavorites(driverId: string) {
    const favorites = await this.prisma.favoriteListing.findMany({
      where: { driver_id: driverId },
      include: { parking: { include: { location: true } } },
      orderBy: { created_at: 'desc' },
    });
    return favorites
      .filter((f) => f.parking.approval_status === ApprovalStatus.APPROVED)
      .map((f) => ({
        id: f.parking.id,
        name: f.parking.name,
        parkingType: f.parking.parking_type,
        status: f.parking.status,
        city: f.parking.location?.city ?? null,
        favoritedAt: f.created_at,
      }));
  }
}

function toSectionSearchView(row: SearchRow) {
  const available = row.capacity - row.reserved_count - row.occupied_count - row.blocked_count;
  return {
    id: row.section_id,
    name: row.section_name,
    vehicleCategory: row.vehicle_category,
    supportedVehicleTypes: row.supported_vehicle_types,
    currency: row.currency,
    hourlyRateMinorUnits: row.hourly_rate_minor_units,
    isCovered: row.is_covered,
    hasSecurity: row.has_security,
    hasCctv: row.has_cctv,
    hasEvCharging: row.has_ev_charging,
    instantModeEnabled: row.instant_mode_enabled,
    availableCount: Math.max(0, available),
  };
}

function computeAvailable(capacity: number, availability: { reserved_count: number; occupied_count: number; blocked_count: number } | null) {
  if (!availability) return 0;
  return Math.max(0, capacity - availability.reserved_count - availability.occupied_count - availability.blocked_count);
}
