import { IsBoolean, IsEnum, IsOptional, IsString, MaxLength, MinLength } from 'class-validator';
import { Transform } from 'class-transformer';
import { VehicleCategory, VehicleType, VehicleSize } from '@prisma/client';
import { normalizeRegistrationNumber } from '../../../common/validation/indian-vehicle-registration';

// Reuses Prisma's generated enums directly (rather than a parallel DTO enum)
// so there is exactly one place these values are defined and no risk of the
// API layer and the database drifting apart. A driver may only ever submit
// TWO_WHEELER / FOUR_WHEELER / OTHER_SUPPORTED here — UNSUPPORTED_PENDING_REVIEW
// is a state the backend assigns (Milestone 10 admin review), never client-set.
const DRIVER_SELECTABLE_CATEGORIES = [
  VehicleCategory.TWO_WHEELER,
  VehicleCategory.FOUR_WHEELER,
  VehicleCategory.OTHER_SUPPORTED,
] as const;

export class CreateVehicleDto {
  @IsEnum(DRIVER_SELECTABLE_CATEGORIES)
  category!: (typeof DRIVER_SELECTABLE_CATEGORIES)[number];

  @IsEnum(VehicleType)
  vehicleType!: VehicleType;

  @IsOptional()
  @IsEnum(VehicleSize)
  size?: VehicleSize;

  @IsString()
  @Transform(({ value }) => normalizeRegistrationNumber(value))
  @MinLength(4)
  @MaxLength(15)
  registrationNumber!: string;

  @IsOptional()
  @IsString()
  @MaxLength(60)
  make?: string;

  @IsOptional()
  @IsString()
  @MaxLength(60)
  model?: string;

  @IsOptional()
  @IsBoolean()
  setAsDefault?: boolean;
}

export class UpdateVehicleDto {
  @IsOptional()
  @IsEnum(VehicleType)
  vehicleType?: VehicleType;

  @IsOptional()
  @IsEnum(VehicleSize)
  size?: VehicleSize;

  @IsOptional()
  @IsString()
  @MaxLength(60)
  make?: string;

  @IsOptional()
  @IsString()
  @MaxLength(60)
  model?: string;

  // Category is deliberately NOT editable after creation without going
  // through UNSUPPORTED_PENDING_REVIEW -> Admin review, per Milestone 0's
  // "protection against unauthorized vehicle-category changes" — see
  // the admin module (Milestone 10).
}
