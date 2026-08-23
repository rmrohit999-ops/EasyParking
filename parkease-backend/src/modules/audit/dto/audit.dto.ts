import { IsOptional, IsString, IsUUID } from 'class-validator';

export class ListAuditLogsQueryDto {
  @IsOptional()
  @IsString()
  targetType?: string;

  @IsOptional()
  @IsUUID()
  targetId?: string;

  @IsOptional()
  @IsUUID()
  actorId?: string;
}
