import { BadRequestException, ConflictException, Injectable, NotFoundException } from '@nestjs/common';
import { PrismaService } from '../../common/prisma/prisma.service';
import { isValidIndianRegistrationNumber } from '../../common/validation/indian-vehicle-registration';
import { OwnershipResolver } from '../../common/guards/resource-ownership.guard';
import { AuthenticatedUser } from '../../common/decorators/current-user.decorator';
import { CreateVehicleDto, UpdateVehicleDto } from './dto/vehicle.dto';

@Injectable()
export class VehiclesService {
  constructor(private readonly prisma: PrismaService) {}

  async list(driverId: string) {
    const vehicles = await this.prisma.vehicle.findMany({
      where: { driver_id: driverId, status: 'ACTIVE' },
      orderBy: [{ is_default: 'desc' }, { created_at: 'desc' }],
    });
    return vehicles.map(toVehicleView);
  }

  async create(driverId: string, dto: CreateVehicleDto) {
    // Registration format validation happens here (not only via a regex
    // decorator) so the error message can be specific and category is
    // never inferred from the string itself — only from dto.category.
    if (!isValidIndianRegistrationNumber(dto.registrationNumber)) {
      throw new BadRequestException(
        "That doesn't look like a valid vehicle registration number. Please check and try again.",
      );
    }

    const existing = await this.prisma.vehicle.findFirst({
      where: { driver_id: driverId, registration_number: dto.registrationNumber, status: 'ACTIVE' },
    });
    if (existing) throw new ConflictException('You already have this vehicle registered.');

    const isFirstVehicle = (await this.prisma.vehicle.count({ where: { driver_id: driverId, status: 'ACTIVE' } })) === 0;

    const vehicle = await this.prisma.$transaction(async (tx) => {
      if (dto.setAsDefault || isFirstVehicle) {
        await tx.vehicle.updateMany({ where: { driver_id: driverId, is_default: true }, data: { is_default: false } });
      }
      return tx.vehicle.create({
        data: {
          driver_id: driverId,
          category: dto.category,
          vehicle_type: dto.vehicleType,
          size: dto.size,
          registration_number: dto.registrationNumber,
          make: dto.make,
          model: dto.model,
          is_default: dto.setAsDefault || isFirstVehicle,
        },
      });
    });

    return toVehicleView(vehicle);
  }

  async update(driverId: string, vehicleId: string, dto: UpdateVehicleDto) {
    await this.assertOwnedActive(driverId, vehicleId);
    const vehicle = await this.prisma.vehicle.update({
      where: { id: vehicleId },
      data: { vehicle_type: dto.vehicleType, size: dto.size, make: dto.make, model: dto.model },
    });
    return toVehicleView(vehicle);
  }

  async remove(driverId: string, vehicleId: string) {
    const vehicle = await this.assertOwnedActive(driverId, vehicleId);
    await this.prisma.$transaction(async (tx) => {
      await tx.vehicle.update({ where: { id: vehicleId }, data: { status: 'REMOVED', is_default: false } });
      if (vehicle.is_default) {
        const next = await tx.vehicle.findFirst({
          where: { driver_id: driverId, status: 'ACTIVE', id: { not: vehicleId } },
          orderBy: { created_at: 'desc' },
        });
        if (next) await tx.vehicle.update({ where: { id: next.id }, data: { is_default: true } });
      }
    });
  }

  async setDefault(driverId: string, vehicleId: string) {
    await this.assertOwnedActive(driverId, vehicleId);
    await this.prisma.$transaction([
      this.prisma.vehicle.updateMany({ where: { driver_id: driverId, is_default: true }, data: { is_default: false } }),
      this.prisma.vehicle.update({ where: { id: vehicleId }, data: { is_default: true } }),
    ]);
  }

  private async assertOwnedActive(driverId: string, vehicleId: string) {
    const vehicle = await this.prisma.vehicle.findFirst({
      where: { id: vehicleId, driver_id: driverId, status: 'ACTIVE' },
    });
    if (!vehicle) throw new NotFoundException('Vehicle not found.');
    return vehicle;
  }
}

function toVehicleView(v: {
  id: string;
  category: string;
  vehicle_type: string;
  size: string | null;
  registration_number: string;
  make: string | null;
  model: string | null;
  is_default: boolean;
}) {
  return {
    id: v.id,
    category: v.category,
    vehicleType: v.vehicle_type,
    size: v.size,
    registrationNumber: v.registration_number,
    make: v.make,
    model: v.model,
    isDefault: v.is_default,
  };
}

/** Registered under the 'VehicleOwnershipResolver' token for @CheckOwnership('VehicleOwnershipResolver', 'vehicleId'). */
@Injectable()
export class VehicleOwnershipResolver implements OwnershipResolver {
  constructor(private readonly prisma: PrismaService) {}

  async resolve(user: AuthenticatedUser & { roles: string[] }, resourceId: string): Promise<boolean> {
    const vehicle = await this.prisma.vehicle.findUnique({ where: { id: resourceId } });
    return vehicle?.driver_id === user.id;
  }
}
