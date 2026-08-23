import {
  IsEmail,
  IsIn,
  IsOptional,
  IsPhoneNumber,
  IsString,
  Matches,
  MaxLength,
  MinLength,
} from 'class-validator';

export class OtpRequestDto {
  @IsPhoneNumber('IN')
  phone!: string;

  @IsIn(['LOGIN', 'REGISTER', 'RESET_PASSWORD'])
  purpose!: 'LOGIN' | 'REGISTER' | 'RESET_PASSWORD';
}

export class OtpVerifyDto {
  @IsPhoneNumber('IN')
  phone!: string;

  @IsIn(['LOGIN', 'REGISTER', 'RESET_PASSWORD'])
  purpose!: 'LOGIN' | 'REGISTER' | 'RESET_PASSWORD';

  @IsString()
  @Matches(/^\d{4,8}$/)
  code!: string;
}

export class RegisterDto {
  @IsString()
  @MinLength(2)
  @MaxLength(120)
  fullName!: string;

  @IsOptional()
  @IsEmail()
  email?: string;

  @IsOptional()
  @IsPhoneNumber('IN')
  phone?: string;

  // Required unless registering purely via a verified OTP or Google identity.
  @IsOptional()
  @IsString()
  @MinLength(8)
  @MaxLength(128)
  password?: string;
}

export class LoginDto {
  @IsOptional()
  @IsEmail()
  email?: string;

  @IsOptional()
  @IsString()
  @MinLength(8)
  password?: string;
}

export class GoogleSignInDto {
  @IsString()
  idToken!: string;
}

export class RefreshDto {
  @IsString()
  refreshToken!: string;
}

export class ForgotPasswordDto {
  @IsEmail()
  email!: string;
}

export class ResetPasswordDto {
  @IsString()
  resetToken!: string;

  @IsString()
  @MinLength(8)
  @MaxLength(128)
  newPassword!: string;
}

export class ChangePasswordDto {
  @IsString()
  currentPassword!: string;

  @IsString()
  @MinLength(8)
  @MaxLength(128)
  newPassword!: string;
}
