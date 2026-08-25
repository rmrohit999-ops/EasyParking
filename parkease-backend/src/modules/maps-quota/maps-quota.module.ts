import { Module } from '@nestjs/common';
import { NotificationsModule } from '../notifications/notifications.module';
import { MapsQuotaController } from './maps-quota.controller';
import { MapsQuotaService } from './maps-quota.service';

@Module({
  imports: [NotificationsModule],
  controllers: [MapsQuotaController],
  providers: [MapsQuotaService],
  exports: [MapsQuotaService],
})
export class MapsQuotaModule {}
