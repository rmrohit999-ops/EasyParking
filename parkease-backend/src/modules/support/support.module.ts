import { Module } from '@nestjs/common';
import { AuditModule } from '../audit/audit.module';
import { NotificationsModule } from '../notifications/notifications.module';
import { SupportController } from './support.controller';
import { SupportAdminController } from './support-admin.controller';
import { SupportService } from './support.service';

/**
 * SupportModule — real (Milestone 10: admin, fraud and support), filling
 * in the structural placeholder Milestone 1 left here. NotificationsModule
 * (Milestone 11) was added so a user gets notified the moment an agent
 * replies to their ticket — see SupportService.addMessage.
 */
@Module({
  imports: [AuditModule, NotificationsModule],
  controllers: [SupportController, SupportAdminController],
  providers: [SupportService],
  exports: [SupportService],
})
export class SupportModule {}
