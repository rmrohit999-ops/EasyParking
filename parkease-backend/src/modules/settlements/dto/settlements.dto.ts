import { IsIn, IsOptional, IsString, Length, MaxLength, MinLength, ValidateIf } from 'class-validator';

export class CreatePayoutAccountDto {
  @IsIn(['BANK', 'UPI'])
  method!: 'BANK' | 'UPI';

  @IsString()
  @MinLength(2)
  @MaxLength(140)
  accountHolderName!: string;

  @ValidateIf((o) => o.method === 'BANK')
  @IsString()
  @MinLength(6)
  @MaxLength(34)
  accountNumber?: string;

  @ValidateIf((o) => o.method === 'BANK')
  @IsString()
  @Length(11, 11)
  ifsc?: string;

  @ValidateIf((o) => o.method === 'UPI')
  @IsString()
  @MinLength(3)
  @MaxLength(100)
  upiVpa?: string;
}

export class VerifyPayoutAccountDto {
  @IsIn(['VERIFIED', 'REJECTED'])
  verificationStatus!: 'VERIFIED' | 'REJECTED';
}

export class ListSettlementsQueryDto {
  @IsOptional()
  @IsIn(['PENDING', 'PROCESSING', 'SETTLED', 'FAILED', 'REVERSED'])
  status?: 'PENDING' | 'PROCESSING' | 'SETTLED' | 'FAILED' | 'REVERSED';
}

export class ListLedgerQueryDto {
  @IsOptional()
  @IsIn(['PENDING', 'AVAILABLE', 'PROCESSING', 'SETTLED', 'FAILED', 'ADJUSTED', 'REVERSED'])
  status?: 'PENDING' | 'AVAILABLE' | 'PROCESSING' | 'SETTLED' | 'FAILED' | 'ADJUSTED' | 'REVERSED';
}
