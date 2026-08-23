import { IsIn, IsInt, IsOptional, IsString, IsUUID, Max, MaxLength, Min, MinLength } from 'class-validator';

const DISPUTE_STATUSES = ['OPEN', 'UNDER_REVIEW', 'RESOLVED', 'REJECTED'] as const;
const EVIDENCE_TYPES = ['PHOTO', 'CHECK_EVENT', 'QR_SCAN', 'PAYMENT_REF', 'LOCATION_RECORD', 'MESSAGE'] as const;

export class FileDisputeDto {
  @IsUUID()
  bookingId!: string;

  @IsString()
  @MinLength(2)
  @MaxLength(80)
  disputeType!: string;

  /**
   * The Dispute model has no free-text column of its own (Milestone 0
   * ERD) — the filer's explanation is recorded as the first piece of
   * evidence (evidence_type=MESSAGE, the explanation text in
   * `reference_id`) rather than inventing a schema column, and is always
   * visible to both parties (PARTIES visibility) since it's the filer's
   * own statement of what happened.
   */
  @IsString()
  @MinLength(3)
  @MaxLength(2000)
  explanation!: string;
}

export class AddDisputeEvidenceDto {
  @IsIn(EVIDENCE_TYPES)
  evidenceType!: (typeof EVIDENCE_TYPES)[number];

  @IsOptional()
  @IsString()
  @MaxLength(2000)
  referenceId?: string;

  @IsOptional()
  @IsString()
  storageKey?: string;

  @IsOptional()
  @IsIn(['ADMIN_ONLY', 'PARTIES'])
  visibility?: 'ADMIN_ONLY' | 'PARTIES';
}

export class ListDisputesQueryDto {
  @IsOptional()
  @IsIn(DISPUTE_STATUSES)
  status?: (typeof DISPUTE_STATUSES)[number];
}

export class ResolveDisputeDto {
  @IsIn(['RESOLVED', 'REJECTED'])
  status!: 'RESOLVED' | 'REJECTED';

  @IsString()
  @MinLength(2)
  @MaxLength(1000)
  resolution!: string;

  /** Only meaningful when status is RESOLVED and a refund is warranted; omit for no refund. */
  @IsOptional()
  @IsInt()
  @Min(0)
  @Max(100)
  refundPercent?: number;
}
