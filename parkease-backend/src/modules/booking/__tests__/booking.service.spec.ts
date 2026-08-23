import { BadRequestException, ConflictException } from '@nestjs/common';
import { BookingService } from '../booking.service';

/**
 * Milestone 12 (release hardening): targeted tests for the two properties
 * Milestone 0's threat model calls out by name for booking hold creation —
 * vehicle/section category segregation (T-series "never let a two-wheeler
 * occupy a four-wheeler space") and race-safe capacity (never overbook a
 * section under concurrent requests). `createHold` is the one place both
 * are enforced, so both live in one spec file against it.
 *
 * The capacity guard in `createHold` is a single atomic
 * `UPDATE ... WHERE (capacity - reserved - occupied - blocked) >= 1`
 * statement specifically so Postgres's own row-level locking — not
 * application code — serializes concurrent writers (see the doc comment
 * on that block in booking.service.ts). A hand-rolled fake can't recreate
 * true concurrent execution, but it CAN faithfully model that statement's
 * compare-and-swap semantics and prove the guard's arithmetic itself never
 * lets a second reservation through once a section is full — which is the
 * actual invariant under test; true multi-connection race coverage belongs
 * in the integration suite against a real Postgres (test:integration,
 * already wired with a real `postgres` service in backend-ci.yml).
 */

function buildFakePrisma(section: any, vehicles: Record<string, any>) {
  const availability = {
    section_id: section.id,
    capacity: section.capacity,
    reserved_count: 0,
    occupied_count: 0,
    blocked_count: 0,
    version: 0,
  };
  const holds: any[] = [];
  let holdCounter = 0;

  const vehicleDelegate = {
    findUnique: async ({ where }: any) => vehicles[where.id] ?? null,
  };
  const sectionDelegate = {
    findUnique: async ({ where }: any) => (where.id === section.id ? section : null),
  };
  const holdDelegate = {
    create: async ({ data }: any) => {
      const record = { id: `hold-${++holdCounter}`, released_at: null, booking_id: null, expires_at: data.expires_at, ...data };
      holds.push(record);
      return record;
    },
  };

  const txHandle = {
    $executeRaw: async (strings: TemplateStringsArray, ...values: any[]) => {
      const text = strings.join('?');
      if (text.includes('reserved_count = reserved_count + 1')) {
        const sectionId = values[0];
        if (sectionId !== availability.section_id) return 0;
        const free = availability.capacity - availability.reserved_count - availability.occupied_count - availability.blocked_count;
        if (free < 1) return 0;
        availability.reserved_count += 1;
        availability.version += 1;
        return 1;
      }
      throw new Error(`Unhandled fake $executeRaw query in this spec: ${text}`);
    },
    bookingHold: holdDelegate,
  };

  return {
    prisma: {
      vehicle: vehicleDelegate,
      parkingSection: sectionDelegate,
      $transaction: async (arg: any) => arg(txHandle),
    } as any,
    availability,
    holds,
  };
}

function buildService(prisma: any) {
  const expiryProducer = { scheduleExpiry: async () => undefined } as any;
  const configService = { get: () => ({ bookingHoldTtlSeconds: 600 }) } as any;
  const notificationsService = { send: async () => undefined } as any;
  return new BookingService(prisma, configService, expiryProducer, notificationsService);
}

function twoWheelerSection(overrides: Partial<any> = {}) {
  return {
    id: 'section-2w',
    capacity: 1,
    vehicle_category: 'TWO_WHEELER',
    supported_vehicle_types: ['BIKE', 'SCOOTER'],
    approval_status: 'APPROVED',
    status: 'ACTIVE',
    parking: { approval_status: 'APPROVED', status: 'ACTIVE' },
    ...overrides,
  };
}

describe('BookingService.createHold — vehicle/section category segregation', () => {
  it('rejects a four-wheeler vehicle against a two-wheeler-only section', async () => {
    const section = twoWheelerSection();
    const vehicles = {
      'car-1': { id: 'car-1', driver_id: 'driver-1', status: 'ACTIVE', category: 'FOUR_WHEELER', vehicle_type: 'CAR' },
    };
    const { prisma, availability } = buildFakePrisma(section, vehicles);
    const service = buildService(prisma);

    await expect(service.createHold('driver-1', { sectionId: section.id, vehicleId: 'car-1' })).rejects.toThrow(BadRequestException);
    // The category check runs before the section-availability transaction
    // even starts — a rejected mismatch must never touch reserved_count.
    expect(availability.reserved_count).toBe(0);
  });

  it('rejects a vehicle type the section does not support even within the same category', async () => {
    const section = twoWheelerSection({ supported_vehicle_types: ['BIKE'] }); // no SCOOTER
    const vehicles = {
      'scooter-1': { id: 'scooter-1', driver_id: 'driver-1', status: 'ACTIVE', category: 'TWO_WHEELER', vehicle_type: 'SCOOTER' },
    };
    const { prisma, availability } = buildFakePrisma(section, vehicles);
    const service = buildService(prisma);

    await expect(service.createHold('driver-1', { sectionId: section.id, vehicleId: 'scooter-1' })).rejects.toThrow(BadRequestException);
    expect(availability.reserved_count).toBe(0);
  });

  it('allows a matching category and vehicle type through', async () => {
    const section = twoWheelerSection();
    const vehicles = {
      'bike-1': { id: 'bike-1', driver_id: 'driver-1', status: 'ACTIVE', category: 'TWO_WHEELER', vehicle_type: 'BIKE' },
    };
    const { prisma, availability } = buildFakePrisma(section, vehicles);
    const service = buildService(prisma);

    const hold = await service.createHold('driver-1', { sectionId: section.id, vehicleId: 'bike-1' });

    expect(hold.sectionId).toBe(section.id);
    expect(availability.reserved_count).toBe(1);
  });
});

describe('BookingService.createHold — race-safe capacity (never overbook)', () => {
  it('refuses a second hold once the section\'s single space is already reserved', async () => {
    const section = twoWheelerSection({ capacity: 1 });
    const vehicles = {
      'bike-1': { id: 'bike-1', driver_id: 'driver-1', status: 'ACTIVE', category: 'TWO_WHEELER', vehicle_type: 'BIKE' },
      'bike-2': { id: 'bike-2', driver_id: 'driver-2', status: 'ACTIVE', category: 'TWO_WHEELER', vehicle_type: 'BIKE' },
    };
    const { prisma, availability } = buildFakePrisma(section, vehicles);
    const service = buildService(prisma);

    // First request (e.g. the winner of a real-world race) succeeds.
    await service.createHold('driver-1', { sectionId: section.id, vehicleId: 'bike-1' });
    expect(availability.reserved_count).toBe(1);

    // A second request against the same now-full section — modeling the
    // loser of a concurrent race — must be refused cleanly, never silently
    // overbook past capacity.
    await expect(service.createHold('driver-2', { sectionId: section.id, vehicleId: 'bike-2' })).rejects.toThrow(ConflictException);
    expect(availability.reserved_count).toBe(1); // unchanged — no phantom second reservation
  });

  it('allows exactly up to capacity and no further', async () => {
    const section = twoWheelerSection({ capacity: 2 });
    const vehicles = {
      'bike-1': { id: 'bike-1', driver_id: 'driver-1', status: 'ACTIVE', category: 'TWO_WHEELER', vehicle_type: 'BIKE' },
      'bike-2': { id: 'bike-2', driver_id: 'driver-2', status: 'ACTIVE', category: 'TWO_WHEELER', vehicle_type: 'BIKE' },
      'bike-3': { id: 'bike-3', driver_id: 'driver-3', status: 'ACTIVE', category: 'TWO_WHEELER', vehicle_type: 'BIKE' },
    };
    const { prisma, availability } = buildFakePrisma(section, vehicles);
    const service = buildService(prisma);

    await service.createHold('driver-1', { sectionId: section.id, vehicleId: 'bike-1' });
    await service.createHold('driver-2', { sectionId: section.id, vehicleId: 'bike-2' });
    expect(availability.reserved_count).toBe(2);

    await expect(service.createHold('driver-3', { sectionId: section.id, vehicleId: 'bike-3' })).rejects.toThrow(ConflictException);
    expect(availability.reserved_count).toBe(2);
  });
});
