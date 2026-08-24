import { Module } from '@nestjs/common';
import { StorageModule } from '../storage/storage.module';
import { AuditModule } from '../audit/audit.module';
import { ParkingListingsController } from './parking.controller';
import { ParkingAdminController } from './parking-admin.controller';
import { ParkingService, ParkingListingOwnershipResolver } from './parking.service';

/**
 * ParkingModule — real (Milestone 4: parking, sections and locations).
 * Depends on StorageModule for presigned photo upload/view URLs, and
 * AuditModule since listing/section approval is now automatic on
 * submission rather than a manual admin gate — the audit trail is what
 * makes that safe to have done, not a human clicking approve each time.
 */
@Module({
  imports: [StorageModule, AuditModule],
  controllers: [ParkingListingsController, ParkingAdminController],
  providers: [
    ParkingService,
    { provide: 'ParkingListingOwnershipResolver', useClass: ParkingListingOwnershipResolver },
  ],
  exports: [ParkingService],
})
export class ParkingModule {}
