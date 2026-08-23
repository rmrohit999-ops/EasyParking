import { IsIn, IsInt, IsOptional, IsString, Max, MaxLength, Min, MinLength } from 'class-validator';

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

  /**
   * Optional discretionary refund percentage (0-100) applied to the
   * driver's total instead of the default full refund an admin
   * force-cancel implies. Omitted -> 100 (full refund), matching "the
   * platform, not the driver, is cancelling" being the default assumption
   * for an admin-initiated cancel.
   */
  @IsOptional()
  @IsInt()
  @Min(0)
  @Max(100)
  refundPercent?: number;
}

export class MarkParkingUnavailableDto {
  @IsString()
  @MinLength(3)
  @MaxLength(500)
  reason!: string;
}

export class ConfirmCashRefundDto {
  @IsOptional()
  @IsString()
  @MaxLength(500)
  auditNote?: string;
}

export class RejectRefundDto {
  @IsString()
  @MinLength(3)
  @MaxLength(500)
  reason!: string;
}

export const REFUND_REASON_CODES = ['CATEGORY_MISMATCH', 'PARKING_UNAVAILABLE', 'ADMIN_APPROVED', 'CANCELLATION_POLICY', 'OTHER'] as const;

export class AdminManualRefundDto {
  @IsIn(REFUND_REASON_CODES)
  reasonCode!: (typeof REFUND_REASON_CODES)[number];

  @IsInt()
  @Min(0)
  @Max(100)
  refundPercent!: number;

  @IsString()
  @MinLength(3)
  @MaxLength(500)
  reason!: string;
}
