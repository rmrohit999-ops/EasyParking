import { Transform, Type } from 'class-transformer';
import { IsBoolean, IsEnum, IsInt, IsLatitude, IsLongitude, IsOptional, IsUUID, Max, Min } from 'class-validator';
import { VehicleCategory } from '@prisma/client';

// Same driver-selectable subset used at vehicle-creation and
// section-creation time — a search can never target
// UNSUPPORTED_PENDING_REVIEW, matching Milestone 0 §6.
const SEARCHABLE_CATEGORIES = [VehicleCategory.TWO_WHEELER, VehicleCategory.FOUR_WHEELER, VehicleCategory.OTHER_SUPPORTED] as const;

// class-transformer's @Type(() => Boolean) does a plain `Boolean(value)`
// coercion, which turns the query string "false" into `true` (any
// non-empty string is truthy) — a classic footgun for boolean query
// params. This explicit transform parses "true"/"false" text correctly
// and passes real booleans through unchanged.
function toQueryBoolean({ value }: { value: unknown }): unknown {
  if (typeof value === 'boolean') return value;
  if (value === 'true') return true;
  if (value === 'false') return false;
  return value;
}

export class SearchParkingQueryDto {
  @Type(() => Number)
  @IsLatitude()
  lat!: number;

  @Type(() => Number)
  @IsLongitude()
  lng!: number;

  @IsOptional()
  @Type(() => Number)
  @IsInt()
  @Min(100)
  @Max(20000)
  radiusMeters?: number = 3000;

  // Either vehicleId (search compatible against a real, owned vehicle) or
  // category (pre-vehicle / anonymous-style browsing) must be given — see
  // DiscoveryService.resolveSearchProfile. Both optional at the DTO level
  // so the service can produce one clear error message instead of
  // class-validator's generic "one of" wording.
  @IsOptional()
  @IsUUID()
  vehicleId?: string;

  @IsOptional()
  @IsEnum(SEARCHABLE_CATEGORIES)
  category?: (typeof SEARCHABLE_CATEGORIES)[number];

  @IsOptional()
  @Transform(toQueryBoolean)
  @IsBoolean()
  instantOnly?: boolean;

  @IsOptional()
  @Transform(toQueryBoolean)
  @IsBoolean()
  covered?: boolean;

  @IsOptional()
  @Transform(toQueryBoolean)
  @IsBoolean()
  hasSecurity?: boolean;

  @IsOptional()
  @Transform(toQueryBoolean)
  @IsBoolean()
  hasCctv?: boolean;

  @IsOptional()
  @Transform(toQueryBoolean)
  @IsBoolean()
  hasEvCharging?: boolean;

  @IsOptional()
  @Type(() => Number)
  @IsInt()
  @Min(1)
  page?: number = 1;

  @IsOptional()
  @Type(() => Number)
  @IsInt()
  @Min(1)
  @Max(50)
  pageSize?: number = 20;
}
