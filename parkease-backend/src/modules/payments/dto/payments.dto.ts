import { IsString, IsUUID, MaxLength, MinLength } from 'class-validator';

export class CreatePaymentOrderDto {
  @IsUUID()
  bookingId!: string;

  /**
   * Client-generated idempotency key (Milestone 0 §3: "idempotency
   * everywhere money moves"). A retried request with the same key replays
   * the original PaymentOrder instead of creating a second one — this is
   * what makes a driver's flaky-network double-tap safe.
   */
  @IsString()
  @MinLength(8)
  @MaxLength(128)
  idempotencyKey!: string;
}

export class RetryPaymentDto {
  @IsString()
  @MinLength(8)
  @MaxLength(128)
  idempotencyKey!: string;
}
