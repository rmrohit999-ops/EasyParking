import { Module } from '@nestjs/common';
import { AuditModule } from '../audit/audit.module';
import { AdminController } from './admin.controller';
import { AdminService } from './admin.service';

/**
 * AdminModule — real (Milestone 10: admin, fraud and support), filling in
 * the structural placeholder Milestone 1 left here. Listing/section
 * approval (parking-admin.controller.ts) stayed in ParkingModule since
 * Milestone 4 — this module only ever grows the parts of "admin" that
 * genuinely span the whole platform: user management and the dashboard
 * summary. Fraud/support/disputes/config are their own sibling modules
 * (same milestone) rather than folded in here, matching the one-concern-
 * per-module boundary the rest of this codebase already keeps.
 */
@Module({
  imports: [AuditModule],
  controllers: [AdminController],
  providers: [AdminService],
  exports: [AdminService],
})
export class AdminModule {}
