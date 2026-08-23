import { Type } from 'class-transformer';
import { IsArray, IsBoolean, IsIn, IsInt, IsISO8601, IsNumber, IsObject, IsOptional, IsString, Min, MinLength, ValidateNested } from 'class-validator';

const POLICY_SCOPES = ['GLOBAL', 'PARKING', 'SECTION', 'VEHICLE_CATEGORY', 'OWNER', 'PROMOTION'] as const;

export class CreateCommissionPolicyDto {
  @IsIn(POLICY_SCOPES)
  scope!: (typeof POLICY_SCOPES)[number];

  @IsOptional()
  @IsString()
  scopeRefId?: string;

  @IsNumber()
  @Min(0)
  commissionPercent!: number;

  @IsOptional()
  @IsInt()
  @Min(0)
  fixedFeeMinorUnits?: number;

  @IsOptional()
  @IsInt()
  @Min(0)
  minCommissionMinorUnits?: number;

  @IsOptional()
  @IsISO8601()
  effectiveFrom?: string;
}

export class CreateTaxPolicyDto {
  @IsIn(POLICY_SCOPES)
  scope!: (typeof POLICY_SCOPES)[number];

  @IsOptional()
  @IsString()
  scopeRefId?: string;

  @IsOptional()
  @IsString()
  taxType?: string;

  @IsNumber()
  @Min(0)
  ratePercent!: number;

  @IsOptional()
  @IsBoolean()
  inclusive?: boolean;

  @IsOptional()
  @IsISO8601()
  effectiveFrom?: string;
}

class CancellationTierDto {
  @IsNumber()
  @Min(0)
  minHoursBeforeStart!: number;

  @IsNumber()
  @Min(0)
  refundPercent!: number;
}

export class CreateCancellationPolicyDto {
  @IsIn(POLICY_SCOPES)
  scope!: (typeof POLICY_SCOPES)[number];

  @IsOptional()
  @IsString()
  scopeRefId?: string;

  @IsArray()
  @ValidateNested({ each: true })
  @Type(() => CancellationTierDto)
  tiers!: CancellationTierDto[];

  @IsOptional()
  @IsISO8601()
  effectiveFrom?: string;
}

export class CreateFeatureFlagDto {
  @IsString()
  @MinLength(2)
  key!: string;

  @IsOptional()
  @IsString()
  description?: string;

  @IsOptional()
  @IsBoolean()
  enabledGlobally?: boolean;

  @IsOptional()
  @IsObject()
  rolloutRules?: Record<string, unknown>;
}

export class UpdateFeatureFlagDto {
  @IsOptional()
  @IsBoolean()
  enabledGlobally?: boolean;

  @IsOptional()
  @IsObject()
  rolloutRules?: Record<string, unknown>;

  @IsOptional()
  @IsString()
  description?: string;
}
