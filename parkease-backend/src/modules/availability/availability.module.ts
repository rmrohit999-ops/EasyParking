import { Module } from '@nestjs/common';
import { AvailabilityController } from './availability.controller';
import { AvailabilityService } from './availability.service';

/**
 * AvailabilityModule — real (Milestone 6). Pause/resume and Instant Mode
 * toggling already exist as ParkingModule endpoints from Milestone 4
 * (PATCH .../sections/:sectionId/status and the instantModeEnabled field
 * on PATCH .../sections/:sectionId) — this module only adds the two
 * capabilities that weren't covered there: the public per-category
 * availability read, and an owner's manual blocked-space adjustment.
 */
@Module({
  controllers: [AvailabilityController],
  providers: [AvailabilityService],
  exports: [AvailabilityService],
})
export class AvailabilityModule {}
