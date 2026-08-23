import { Module } from '@nestjs/common';
import { AuditModule } from '../audit/audit.module';
import { ConfigController } from './config.controller';
import { ConfigService } from './config.service';

/**
 * ConfigModule — real (Milestone 10: admin, fraud and support —
 * commission/tax/cancellation/feature-flag config), filling in the
 * structural placeholder Milestone 1 left here.
 */
@Module({
  imports: [AuditModule],
  controllers: [ConfigController],
  providers: [ConfigService],
  exports: [ConfigService],
})
export class ConfigModule {}
