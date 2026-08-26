/**
 * Creates 5 real, structurally-valid demo parking listings, GPS-scattered
 * within a radius of a real geocoded center point, for demoing the Instant
 * Parking Discovery feature. Deliberately separate from prisma/seed/seed.ts
 * (which refuses to run outside development, per this project's own "never
 * seed fake production data" rule) — THIS script is explicitly meant to be
 * run against production for a live demo, at the requesting owner's own
 * discretion, which is why it requires an explicit --yes flag rather than
 * running silently.
 *
 * Every "member" in the original ask maps onto real ParkEase entities: a
 * User has no persistent lat/lng column at all (a driver's location is only
 * ever sent live with a search request — see AddressGeocoder's doc
 * comments in the Android app), so the only entity that structurally stores
 * coordinates is ParkingLocation. Each demo "member" is therefore a real
 * User+OwnerProfile+ParkingListing+ParkingLocation+ParkingSection chain,
 * approved/active immediately so it shows up in a real Instant Parking
 * search right away.
 *
 * Test-data marking: every demo User's email ends in @parkease.test — a
 * TLD reserved by IANA (RFC 2606) specifically so it can never collide with
 * a real domain. cleanup-demo-listings.ts filters on exactly this. No
 * schema change, no is_test_account column.
 *
 * Geocoding is real, not guessed: the center point and each scattered
 * point's address are resolved via OSM's free public Nominatim API — same
 * "no API key, no billing" family as the osmdroid/OSRM integrations
 * already used elsewhere in this app, not a hardcoded/approximated
 * coordinate.
 *
 * Usage:
 *   DATABASE_URL="<target db>" npx ts-node prisma/seed/seed-demo-listings.ts \
 *     --place "Kani Nilam, Keeranatham, Coimbatore" --yes
 */
import { PrismaClient, ParkingType, VehicleCategory, VehicleType } from '@prisma/client';
import * as argon2 from 'argon2';

const prisma = new PrismaClient();

const NOMINATIM_USER_AGENT = 'ParkEase-DemoSeedScript/1.0';
const DEMO_COUNT = 5;
const RADIUS_METERS = 5000;
const EARTH_RADIUS_METERS = 6_371_000;

interface GeoPoint {
  latitude: number;
  longitude: number;
}

function parseArgs(): { place: string; confirmed: boolean } {
  const args = process.argv.slice(2);
  const placeIndex = args.indexOf('--place');
  const place = placeIndex >= 0 ? args[placeIndex + 1] : null;
  const confirmed = args.includes('--yes');
  if (!place) {
    throw new Error('Usage: ts-node seed-demo-listings.ts --place "<address to center on>" --yes');
  }
  return { place, confirmed };
}

async function sleep(ms: number) {
  return new Promise((resolve) => setTimeout(resolve, ms));
}

/** Real forward geocoding via Nominatim (OSM) — no API key, matches osmdroid/OSRM's free-tier pattern already used elsewhere in this project. */
async function geocode(query: string): Promise<{ point: GeoPoint; displayName: string }> {
  const url = `https://nominatim.openstreetmap.org/search?format=json&limit=1&q=${encodeURIComponent(query)}`;
  const response = await fetch(url, { headers: { 'User-Agent': NOMINATIM_USER_AGENT } });
  if (!response.ok)
    throw new Error(`Nominatim geocode failed for "${query}": HTTP ${response.status}`);
  const results = (await response.json()) as { lat: string; lon: string; display_name: string }[];
  if (results.length === 0)
    throw new Error(`Nominatim found no results for "${query}" — try a more specific place name.`);
  return {
    point: { latitude: parseFloat(results[0].lat), longitude: parseFloat(results[0].lon) },
    displayName: results[0].display_name,
  };
}

/** Real reverse geocoding for each scattered point's demo address — not a fabricated address string. */
async function reverseGeocode(
  point: GeoPoint,
): Promise<{ addressLine: string; city: string; state: string; postalCode: string }> {
  const url = `https://nominatim.openstreetmap.org/reverse?format=json&lat=${point.latitude}&lon=${point.longitude}`;
  const response = await fetch(url, { headers: { 'User-Agent': NOMINATIM_USER_AGENT } });
  if (!response.ok) {
    return {
      addressLine: `${point.latitude.toFixed(5)}, ${point.longitude.toFixed(5)}`,
      city: '',
      state: '',
      postalCode: '',
    };
  }
  const result = (await response.json()) as {
    display_name?: string;
    address?: Record<string, string>;
  };
  const addr = result.address ?? {};
  return {
    addressLine:
      result.display_name ?? `${point.latitude.toFixed(5)}, ${point.longitude.toFixed(5)}`,
    city: addr.city ?? addr.town ?? addr.suburb ?? addr.village ?? '',
    state: addr.state ?? '',
    postalCode: addr.postcode ?? '',
  };
}

/**
 * A uniformly-distributed random point within radiusMeters of center, using
 * real destination-point geodesic math (bearing + angular distance), not
 * naive lat/lng addition — which would distort real distances away from
 * the equator and bunch points unevenly near the poles of the sample
 * circle. distance = radius * sqrt(u) (not radius * u) so points are
 * uniform over the disk's AREA, not biased toward the center.
 */
function randomPointWithinRadius(center: GeoPoint, radiusMeters: number): GeoPoint {
  const distance = radiusMeters * Math.sqrt(Math.random());
  const bearing = 2 * Math.PI * Math.random();
  const angularDistance = distance / EARTH_RADIUS_METERS;

  const lat1 = (center.latitude * Math.PI) / 180;
  const lon1 = (center.longitude * Math.PI) / 180;

  const lat2 = Math.asin(
    Math.sin(lat1) * Math.cos(angularDistance) +
      Math.cos(lat1) * Math.sin(angularDistance) * Math.cos(bearing),
  );
  const lon2 =
    lon1 +
    Math.atan2(
      Math.sin(bearing) * Math.sin(angularDistance) * Math.cos(lat1),
      Math.cos(angularDistance) - Math.sin(lat1) * Math.sin(lat2),
    );

  return { latitude: (lat2 * 180) / Math.PI, longitude: (lon2 * 180) / Math.PI };
}

function randomPassword(): string {
  return 'Demo-' + Math.random().toString(36).slice(2, 10) + '!1';
}

async function main() {
  const { place, confirmed } = parseArgs();

  const dbHost = new URL(process.env.DATABASE_URL ?? '').host || '(unknown — DATABASE_URL not set)';
  console.log(`This will write ${DEMO_COUNT} real rows to: ${dbHost}`);
  if (!confirmed) {
    console.log(
      "Refusing to run without --yes (this is a safety check, not a real permission system) — re-run with --yes once you've confirmed the target above is correct.",
    );
    process.exit(1);
  }

  console.log(`Geocoding center point: "${place}"…`);
  const { point: center, displayName } = await geocode(place);
  console.log(
    `Resolved to ${center.latitude.toFixed(6)}, ${center.longitude.toFixed(6)} (${displayName})`,
  );

  const credentials: { email: string; password: string }[] = [];

  for (let i = 1; i <= DEMO_COUNT; i++) {
    const point = randomPointWithinRadius(center, RADIUS_METERS);
    await sleep(1100); // Nominatim's usage policy caps at ~1 request/second.
    const address = await reverseGeocode(point);

    const email = `test_member_${i}@parkease.test`;
    const password = randomPassword();
    const passwordHash = await argon2.hash(password, { type: argon2.argon2id });
    const phone = `+9190000000${String(i).padStart(2, '0')}`;
    const category = i % 2 === 0 ? VehicleCategory.TWO_WHEELER : VehicleCategory.FOUR_WHEELER;
    const vehicleTypes =
      category === VehicleCategory.TWO_WHEELER
        ? [VehicleType.BIKE, VehicleType.SCOOTER]
        : [VehicleType.CAR, VehicleType.SUV];

    const user = await prisma.user.create({
      data: { email, phone, password_hash: passwordHash, email_verified_at: new Date() },
    });
    await prisma.userRoleAssignment.create({ data: { user_id: user.id, role: 'OWNER' } });
    const ownerProfile = await prisma.ownerProfile.create({
      data: { user_id: user.id, business_name: `Demo Owner ${i}` },
    });
    const listing = await prisma.parkingListing.create({
      data: {
        owner_id: ownerProfile.id,
        name: `[DEMO] Test Parking ${i} — Keeranatham`,
        parking_type: ParkingType.INDIVIDUAL,
        description: `SEED DEMO DATA — safe to delete. Created by seed-demo-listings.ts for a live demo around "${place}".`,
        approval_status: 'APPROVED',
        status: 'ACTIVE',
      },
    });
    await prisma.parkingLocation.create({
      data: {
        parking_id: listing.id,
        latitude: point.latitude,
        longitude: point.longitude,
        address_line: address.addressLine,
        city: address.city,
        state: address.state,
        postal_code: address.postalCode,
        entrance_notes: 'Demo listing — main entrance.',
      },
    });
    // PostGIS's geog column has no Prisma-native type (Unsupported(...) in
    // schema.prisma) — synced via the same raw SQL parking.service.ts's
    // real upsertLocation() uses, otherwise ST_DWithin search will never
    // find this listing at all.
    await prisma.$executeRawUnsafe(
      `UPDATE parking_locations SET geog = ST_SetSRID(ST_MakePoint($1, $2), 4326)::geography WHERE parking_id = $3`,
      point.longitude,
      point.latitude,
      listing.id,
    );
    await prisma.parkingSection.create({
      data: {
        parking_id: listing.id,
        name: `Demo Section ${i}`,
        vehicle_category: category,
        supported_vehicle_types: vehicleTypes,
        capacity: 5,
        currency: 'INR',
        hourly_rate_minor_units: 2000,
        instant_mode_enabled: true,
        approval_status: 'APPROVED',
        status: 'ACTIVE',
      },
    });

    credentials.push({ email, password });
    console.log(
      `  ${i}. ${listing.name} @ ${point.latitude.toFixed(6)}, ${point.longitude.toFixed(6)} — ${address.city || address.addressLine}`,
    );
  }

  console.log(
    '\nDone. Login credentials for these demo owner accounts (shown once, not stored anywhere):',
  );
  credentials.forEach((c) => console.log(`  ${c.email} / ${c.password}`));
  console.log(
    '\nRun cleanup-demo-listings.ts (same DATABASE_URL, --yes) to remove all of this afterward.',
  );
}

main()
  .catch((e) => {
    console.error(e);
    process.exit(1);
  })
  .finally(async () => {
    await prisma.$disconnect();
  });
