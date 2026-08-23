import { Controller, Get, Query } from '@nestjs/common';
import { ApiOperation, ApiTags } from '@nestjs/swagger';
import { Roles } from '../../common/decorators/roles.decorator';
import { ReportsService } from './reports.service';
import { ReportDateRangeQueryDto, RevenueTimeseriesQueryDto, TopOwnersQueryDto } from './dto/reports.dto';

@ApiTags('admin-reports')
@Roles('ADMIN')
@Controller({ path: 'admin/reports', version: '1' })
export class ReportsController {
  constructor(private readonly reportsService: ReportsService) {}

  @Get('bookings-summary')
  @ApiOperation({ summary: 'Booking counts by status and revenue totals for a date range (defaults to the trailing 30 days)' })
  bookingsSummary(@Query() query: ReportDateRangeQueryDto) {
    return this.reportsService.bookingsSummary(query);
  }

  @Get('revenue-timeseries')
  @ApiOperation({ summary: 'Revenue bucketed by day/week/month for a date range' })
  revenueTimeseries(@Query() query: RevenueTimeseriesQueryDto) {
    return this.reportsService.revenueTimeseries(query);
  }

  @Get('top-owners')
  @ApiOperation({ summary: 'Top owners by net earnings for a date range' })
  topOwners(@Query() query: TopOwnersQueryDto) {
    return this.reportsService.topOwners(query);
  }

  @Get('utilization-snapshot')
  @ApiOperation({ summary: 'Current occupancy snapshot across active, approved sections (point-in-time only — see ReportsService doc comment)' })
  utilizationSnapshot() {
    return this.reportsService.utilizationSnapshot();
  }
}
