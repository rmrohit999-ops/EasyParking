import { Module } from '@nestjs/common';
import { ReportsController } from './reports.controller';
import { ReportsService } from './reports.service';

/**
 * ReportsModule — real (Milestone 11: notifications and analytics), filling
 * in the structural placeholder Milestone 1 left here. Pure read-side
 * aggregate reporting over tables other modules already write to — no new
 * write paths, no separate analytics store. See ReportsService's doc
 * comment for the one disclosed coverage gap (utilization is a live
 * snapshot only, not a time series).
 */
@Module({
  controllers: [ReportsController],
  providers: [ReportsService],
})
export class ReportsModule {}
