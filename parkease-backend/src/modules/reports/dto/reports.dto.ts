import { IsIn, IsInt, IsISO8601, IsOptional, Max, Min } from 'class-validator';

const GRANULARITIES = ['day', 'week', 'month'] as const;

/**
 * Every report in this module accepts an optional [from, to] window over
 * ISO-8601 date/datetime strings. Omitting both defaults to the trailing 30
 * days (see ReportsService.resolveRange) rather than requiring the admin
 * dashboard to compute and pass a default itself.
 */
export class ReportDateRangeQueryDto {
  @IsOptional()
  @IsISO8601()
  from?: string;

  @IsOptional()
  @IsISO8601()
  to?: string;
}

export class RevenueTimeseriesQueryDto extends ReportDateRangeQueryDto {
  @IsOptional()
  @IsIn(GRANULARITIES)
  granularity?: (typeof GRANULARITIES)[number];
}

export class TopOwnersQueryDto extends ReportDateRangeQueryDto {
  @IsOptional()
  @IsInt()
  @Min(1)
  @Max(50)
  limit?: number;
}
