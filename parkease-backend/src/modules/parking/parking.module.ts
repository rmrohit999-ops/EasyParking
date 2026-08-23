import { Module } from '@nestjs/common';
import { StorageModule } from '../storage/storage.module';
import { ParkingListingsController } from './parking.controller';
import { ParkingAdminController } from './parking-admin.controller';
import { ParkingService, ParkingListingOwnershipResolver } from './parking.service';

/**
 * ParkingModule — real (Milestone 4: parking, sections and locations).
 * Depends on StorageModule for presigned photo upload/view URLs.
 */
@Module({
  imports: [StorageModule],
  controllers: [ParkingListingsController, ParkingAdminController],
  providers: [
    ParkingService,
    { provide: 'ParkingListingOwnershipResolver', useClass: ParkingListingOwnershipResolver },
  ],
  exports: [ParkingService],
})
export class ParkingModule {}
