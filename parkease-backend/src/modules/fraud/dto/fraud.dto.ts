import { IsIn, IsOptional, IsString, MinLength } from 'class-validator';

const ALERT_STATUSES = ['OPEN', 'UNDER_REVIEW', 'DISMISSED', 'ACTION_TAKEN'] as const;

export class ListFraudAlertsQueryDto {
  @IsOptional()
  @IsIn(ALERT_STATUSES)
  status?: (typeof ALERT_STATUSES)[number];
}

export class ReviewFraudAlertDto {
  @IsIn(ALERT_STATUSES)
  status!: (typeof ALERT_STATUSES)[number];

  @IsOptional()
  @IsString()
  @MinLength(2)
  actionTaken?: string;
}
