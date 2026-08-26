import { IsISO8601, IsInt, IsOptional, IsString, IsUUID, Max, MaxLength, Min, MinLength } from 'class-validator';

export class CreateHoldDto {
  @IsUUID()
  sectionId!: string;

  @IsUUID()
  vehicleId!: string;
}

export class ConfirmBookingDto {
  @IsUUID()
  holdId!: string;

  @IsISO8601()
  startTime!: string;

  @IsISO8601()
  endTime!: string;
}

export class CreateInstantBookingDto {
  @IsUUID()
  sectionId!: string;

  @IsUUID()
  vehicleId!: string;
}

export class CancelBookingDto {
  @IsOptional()
  @IsString()
  @MinLength(1)
  @MaxLength(500)
  reason?: string;
}

export class AdminCancelBookingDto {
  @IsString()
  @MinLength(3)
  @MaxLength(500)
  reason!: string;
}

export class SubmitReviewDto {
  @IsInt()
  @Min(1)
  @Max(5)
  overall!: number;

  @IsOptional()
  @IsInt()
  @Min(1)
  @Max(5)
  cleanliness?: number;

  @IsOptional()
  @IsInt()
  @Min(1)
  @Max(5)
  security?: number;

  @IsOptional()
  @IsInt()
  @Min(1)
  @Max(5)
  location?: number;

  @IsOptional()
  @IsString()
  @MaxLength(1000)
  comment?: string;
}
