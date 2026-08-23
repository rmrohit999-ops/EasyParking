import { Module } from '@nestjs/common';
import { ConfigService } from '@nestjs/config';
import { AppConfig } from '../../common/config/configuration';
import { NotificationsController } from './notifications.controller';
import { NotificationsService } from './notifications.service';
import { PUSH_PROVIDER_SERVICE } from './provider/push-provider.interface';
import { FcmPushProviderService } from './provider/fcm-push-provider.service';
import { NullPushProviderService } from './provider/null-push-provider.service';

/**
 * NotificationsModule — real (Milestone 11: notifications and analytics),
 * filling in the structural placeholder Milestone 1 left here. Selects
 * FcmPushProviderService when push is configured, NullPushProviderService
 * otherwise — mirroring PaymentsModule/SettlementsModule's provider-
 * selection factory exactly. Exported so any other module can inject
 * NotificationsService to fire a real notification at its own lifecycle
 * events (booking transitions, refunds, settlements, support replies,
 * dispute resolutions — see each of those modules' own wiring).
 */
@Module({
  controllers: [NotificationsController],
  providers: [
    NotificationsService,
    FcmPushProviderService,
    NullPushProviderService,
    {
      provide: PUSH_PROVIDER_SERVICE,
      inject: [ConfigService, FcmPushProviderService, NullPushProviderService],
      useFactory: (configService: ConfigService<AppConfig, true>, fcm: FcmPushProviderService, nullProvider: NullPushProviderService) =>
        configService.get('push', { infer: true }).isConfigured ? fcm : nullProvider,
    },
  ],
  exports: [NotificationsService],
})
export class NotificationsModule {}
