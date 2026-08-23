import { IsBoolean, IsIn, IsOptional, IsString, MinLength } from 'class-validator';

export class RegisterDeviceDto {
  @IsString()
  @MinLength(10)
  fcmToken!: string;

  @IsOptional()
  @IsIn(['ANDROID', 'IOS'])
  platform?: string;
}

export class UnregisterDeviceDto {
  @IsString()
  @MinLength(10)
  fcmToken!: string;
}

export class UpdateNotificationPreferenceDto {
  @IsString()
  @MinLength(2)
  category!: string;

  @IsIn(['PUSH', 'SMS', 'EMAIL'])
  channel!: 'PUSH' | 'SMS' | 'EMAIL';

  @IsBoolean()
  enabled!: boolean;
}
