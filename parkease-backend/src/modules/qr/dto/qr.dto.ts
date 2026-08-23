import { IsEnum, IsIn, IsOptional, IsString, MaxLength, MinLength } from 'class-validator';
import { VehicleCategory } from '@prisma/client';

export class ScanQrDto {
  @IsString()
  @MinLength(3)
  token!: string;
}

export class CheckInDto {
  @IsString()
  @MinLength(3)
  token!: string;

  // What the attendant actually reads off the vehicle in front of them —
  // compared against the booked vehicle's registration_number as an
  // informational signal (Milestone 0 §5.6's "QR/check-in validation
  // re-runs is_bookable against the vehicle actually presented"). A
  // mismatch here does NOT block check-in per the literal state diagram
  // (VEHICLE_MISMATCH is only reachable from PARKING_ACTIVE) — it's
  // recorded on the check_events row so a later /mismatch report has
  // evidence, and the attendant is expected to call /mismatch themselves
  // once the vehicle is actually parked if the discrepancy is real.
  @IsOptional()
  @IsString()
  @MaxLength(20)
  presentedRegistrationNumber?: string;
}

export class CheckOutDto {
  @IsString()
  @MinLength(3)
  token!: string;
}

const MISMATCH_RESOLUTIONS = ['REJECTED_ENTRY', 'ADMIN_OVERRIDE'] as const;

export class ReportMismatchDto {
  @IsString()
  @MaxLength(20)
  actualVehicleRegistration!: string;

  @IsOptional()
  @IsEnum(VehicleCategory)
  actualCategory?: VehicleCategory;

  @IsOptional()
  @IsString()
  @MaxLength(500)
  note?: string;
}

/**
 * RECATEGORIZED_WITH_PAYMENT_DIFF (the third MismatchResolution value) is
 * deliberately not selectable here — resolving a mismatch that way means
 * collecting a real payment for the price difference, which needs its own
 * payment-order-for-a-diff-amount flow layered on Milestone 7's payments
 * module. That's a genuinely separate piece of work, scoped out of
 * Milestone 8 and disclosed rather than faked; for now a mismatch resolves
 * to either REJECTED_ENTRY (attendant/owner, no payment involved) or
 * ADMIN_OVERRIDE (admin discretion, audit-logged).
 */
export class ResolveMismatchDto {
  @IsIn(MISMATCH_RESOLUTIONS)
  resolution!: (typeof MISMATCH_RESOLUTIONS)[number];

  @IsOptional()
  @IsString()
  @MaxLength(500)
  reason?: string;
}

export class CashCollectDto {
  @IsOptional()
  @IsString()
  @MaxLength(200)
  confirmationMethod?: string;

  @IsOptional()
  @IsString()
  @MaxLength(500)
  auditNote?: string;
}
