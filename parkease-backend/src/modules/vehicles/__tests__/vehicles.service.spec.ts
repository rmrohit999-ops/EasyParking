import { BadRequestException, ConflictException } from '@nestjs/common';
import { VehiclesService } from '../vehicles.service';
import { VehicleCategory, VehicleType } from '@prisma/client';

/**
 * Hand-rolled fake standing in for PrismaService — enough of the `vehicle`
 * delegate's surface for VehiclesService, plus a $transaction that just
 * runs the callback against `this` (mirrors Prisma's interactive
 * transaction API closely enough for this unit-level test).
 */
function buildFakePrisma() {
  const vehicles: any[] = [];
  let idCounter = 0;

  const vehicleDelegate = {
    findMany: async ({ where }: any) =>
      vehicles.filter((v) => v.driver_id === where.driver_id && v.status === where.status),
    findFirst: async ({ where }: any) =>
      vehicles.find(
        (v) =>
          (!where.driver_id || v.driver_id === where.driver_id) &&
          (!where.registration_number || v.registration_number === where.registration_number) &&
          (!where.status || v.status === where.status) &&
          (!where.id || v.id === where.id) &&
          (!where.id?.not || v.id !== where.id.not),
      ) ?? null,
    count: async ({ where }: any) =>
      vehicles.filter((v) => v.driver_id === where.driver_id && v.status === where.status).length,
    create: async ({ data }: any) => {
      const record = { id: `vehicle-${++idCounter}`, status: 'ACTIVE', ...data };
      vehicles.push(record);
      return record;
    },
    update: async ({ where, data }: any) => {
      const record = vehicles.find((v) => v.id === where.id);
      Object.assign(record, data);
      return record;
    },
    updateMany: async ({ where, data }: any) => {
      vehicles
        .filter((v) => v.driver_id === where.driver_id && (where.is_default === undefined || v.is_default === where.is_default))
        .forEach((v) => Object.assign(v, data));
    },
  };

  return {
    vehicle: vehicleDelegate,
    $transaction: async (arg: any) => {
      if (Array.isArray(arg)) return Promise.all(arg);
      return arg({ vehicle: vehicleDelegate });
    },
  } as any;
}

describe('VehiclesService', () => {
  it('sets the first vehicle a driver adds as their default automatically', async () => {
    const prisma = buildFakePrisma();
    const service = new VehiclesService(prisma);

    const vehicle = await service.create('driver-1', {
      category: VehicleCategory.FOUR_WHEELER,
      vehicleType: VehicleType.CAR,
      registrationNumber: 'KA01AB1234',
    } as any);

    expect(vehicle.isDefault).toBe(true);
  });

  it('adding a second vehicle does not disturb the first default unless setAsDefault is true', async () => {
    const prisma = buildFakePrisma();
    const service = new VehiclesService(prisma);

    await service.create('driver-1', {
      category: VehicleCategory.FOUR_WHEELER,
      vehicleType: VehicleType.CAR,
      registrationNumber: 'KA01AB1234',
    } as any);
    const second = await service.create('driver-1', {
      category: VehicleCategory.TWO_WHEELER,
      vehicleType: VehicleType.SCOOTER,
      registrationNumber: 'KA01AB5678',
    } as any);

    expect(second.isDefault).toBe(false);
    const list = await service.list('driver-1');
    expect(list.find((v) => v.registrationNumber === 'KA01AB1234')?.isDefault).toBe(true);
  });

  it('rejects a malformed registration number before touching the database', async () => {
    const prisma = buildFakePrisma();
    const service = new VehiclesService(prisma);

    await expect(
      service.create('driver-1', {
        category: VehicleCategory.FOUR_WHEELER,
        vehicleType: VehicleType.CAR,
        registrationNumber: 'not-a-plate',
      } as any),
    ).rejects.toThrow(BadRequestException);
  });

  it('rejects a duplicate active registration for the same driver', async () => {
    const prisma = buildFakePrisma();
    const service = new VehiclesService(prisma);
    await service.create('driver-1', {
      category: VehicleCategory.FOUR_WHEELER,
      vehicleType: VehicleType.CAR,
      registrationNumber: 'KA01AB1234',
    } as any);

    await expect(
      service.create('driver-1', {
        category: VehicleCategory.FOUR_WHEELER,
        vehicleType: VehicleType.CAR,
        registrationNumber: 'KA01AB1234',
      } as any),
    ).rejects.toThrow(ConflictException);
  });
});
