import { IsOptional, IsString, MaxLength, MinLength } from 'class-validator';

export class UpdateProfileDto {
  @IsOptional()
  @IsString()
  @MinLength(2)
  @MaxLength(120)
  fullName?: string;

  @IsOptional()
  @IsString()
  profilePhotoUrl?: string;
}

export class BecomeOwnerDto {
  @IsOptional()
  @IsString()
  @MaxLength(160)
  businessName?: string;
}
