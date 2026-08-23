import { Module } from '@nestjs/common';
import { AuditModule } from '../audit/audit.module';
import { FraudController } from './fraud.controller';
import { FraudService } from './fraud.service';

/**
 * FraudModule — real (Milestone 10: admin, fraud and support), filling in
 * the structural placeholder Milestone 1 left here.
 */
@Module({
  imports: [AuditModule],
  controllers: [FraudController],
  providers: [FraudService],
  exports: [FraudService],
})
export class FraudModule {}
