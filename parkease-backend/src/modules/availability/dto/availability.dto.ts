import { IsEnum, IsInt, IsOptional, Min } from 'class-validator';
import { Type } from 'class-transformer';
import { VehicleCategory } from '@prisma/client';

export class GetAvailabilityQueryDto {
  @IsOptional()
  @IsEnum(VehicleCategory)
  category?: VehicleCategory;
}

export class SetBlockedCountDto {
  @Type(() => Number)
  @IsInt()
  @Min(0)
  blockedCount!: number;
}
