import { Module } from '@nestjs/common';
import { AuditModule } from '../audit/audit.module';
import { RefundsModule } from '../refunds/refunds.module';
import { NotificationsModule } from '../notifications/notifications.module';
import { DisputesController } from './disputes.controller';
import { DisputesAdminController } from './disputes-admin.controller';
import { DisputesService } from './disputes.service';

/**
 * DisputesModule — real (Milestone 10: admin, fraud and support). Imports
 * RefundsModule (Milestone 9) so a resolved dispute can trigger a real
 * discretionary refund via RefundsService.adminManualRefund — see
 * DisputesService's class doc comment. NotificationsModule (Milestone 11)
 * was added so the filer learns their dispute was resolved without having
 * to keep checking back.
 */
@Module({
  imports: [AuditModule, RefundsModule, NotificationsModule],
  controllers: [DisputesController, DisputesAdminController],
  providers: [DisputesService],
  exports: [DisputesService],
})
export class DisputesModule {}
