import { Module } from '@nestjs/common';
import { AuditController } from './audit.controller';
import { AuditService } from './audit.service';

/**
 * AuditModule — real (Milestone 10: admin, fraud and support). Exports
 * AuditService so AdminModule/FraudModule/SupportModule/DisputesModule can
 * all write to the same append-only log without importing each other.
 */
@Module({
  controllers: [AuditController],
  providers: [AuditService],
  exports: [AuditService],
})
export class AuditModule {}
