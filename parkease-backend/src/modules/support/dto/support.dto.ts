import { IsIn, IsOptional, IsString, IsUUID, MaxLength, MinLength } from 'class-validator';

const TICKET_STATUSES = ['OPEN', 'IN_PROGRESS', 'WAITING_FOR_USER', 'RESOLVED', 'CLOSED', 'REOPENED'] as const;

export class CreateSupportTicketDto {
  @IsString()
  @MinLength(2)
  @MaxLength(80)
  category!: string;

  @IsString()
  @MinLength(3)
  @MaxLength(200)
  subject!: string;

  @IsString()
  @MinLength(1)
  @MaxLength(4000)
  description!: string;

  @IsOptional()
  @IsUUID()
  relatedBookingId?: string;
}

export class AddSupportMessageDto {
  @IsString()
  @MinLength(1)
  @MaxLength(4000)
  message!: string;
}

export class UpdateSupportTicketStatusDto {
  @IsIn(TICKET_STATUSES)
  status!: (typeof TICKET_STATUSES)[number];
}

export class AssignSupportTicketDto {
  @IsUUID()
  assignedTo!: string;
}

export class ListSupportTicketsQueryDto {
  @IsOptional()
  @IsIn(TICKET_STATUSES)
  status?: (typeof TICKET_STATUSES)[number];
}
