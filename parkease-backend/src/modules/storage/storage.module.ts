import { Module } from '@nestjs/common';
import { S3StorageService, STORAGE_SERVICE } from './storage.service';

/**
 * StorageModule — real (Milestone 4). Provides the presigned-URL S3/MinIO
 * adapter under the STORAGE_SERVICE token so consuming modules (parking
 * photos here; owner KYC documents in a later milestone) depend on the
 * StorageService interface rather than a concrete provider, matching the
 * OwnershipResolver pattern used elsewhere in this codebase.
 */
@Module({
  providers: [{ provide: STORAGE_SERVICE, useClass: S3StorageService }],
  exports: [STORAGE_SERVICE],
})
export class StorageModule {}
