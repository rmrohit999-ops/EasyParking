import { IsIn, IsOptional, IsString, MaxLength, MinLength } from 'class-validator';

export class ListUsersQueryDto {
  @IsOptional()
  @IsIn(['DRIVER', 'OWNER', 'ATTENDANT', 'ADMIN'])
  role?: 'DRIVER' | 'OWNER' | 'ATTENDANT' | 'ADMIN';

  @IsOptional()
  @IsIn(['ACTIVE', 'SUSPENDED', 'DELETED'])
  status?: 'ACTIVE' | 'SUSPENDED' | 'DELETED';

  @IsOptional()
  @IsString()
  @MaxLength(200)
  q?: string;
}

export class SuspendUserDto {
  @IsString()
  @MinLength(3)
  @MaxLength(500)
  reason!: string;
}
