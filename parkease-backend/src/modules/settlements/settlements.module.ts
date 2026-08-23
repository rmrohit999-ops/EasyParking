import { Module } from '@nestjs/common';
import { ConfigService } from '@nestjs/config';
import { AppConfig } from '../../common/config/configuration';
import { NotificationsModule } from '../notifications/notifications.module';
import { PayoutAccountsController } from './payout-accounts.controller';
import { PayoutAccountsService } from './payout-accounts.service';
import { EarningsController } from './earnings.controller';
import { EarningsService } from './earnings.service';
import { SettlementsController } from './settlements.controller';
import { SettlementsService } from './settlements.service';
import { PAYOUT_PROVIDER_SERVICE } from './provider/payout-provider.interface';
import { RazorpayXPayoutProviderService } from './provider/razorpayx-payout-provider.service';
import { NullPayoutProviderService } from './provider/null-payout-provider.service';

/**
 * SettlementsModule — real (Milestone 9: earnings, refunds and
 * settlements), filling in the structural placeholder Milestone 1 left
 * here. Selects the concrete PayoutProvider once at bootstrap based on
 * `PAYOUT_PROVIDER` (RazorpayXPayoutProviderService for `razorpayx`;
 * NullPayoutProviderService otherwise), mirroring PaymentsModule's
 * provider-selection factory exactly.
 */
@Module({
  imports: [NotificationsModule],
  controllers: [PayoutAccountsController, EarningsController, SettlementsController],
  providers: [
    PayoutAccountsService,
    EarningsService,
    SettlementsService,
    RazorpayXPayoutProviderService,
    NullPayoutProviderService,
    {
      provide: PAYOUT_PROVIDER_SERVICE,
      inject: [ConfigService, RazorpayXPayoutProviderService, NullPayoutProviderService],
      useFactory: (
        configService: ConfigService<AppConfig, true>,
        razorpayx: RazorpayXPayoutProviderService,
        nullProvider: NullPayoutProviderService,
      ) => (configService.get('payouts', { infer: true }).provider === 'razorpayx' ? razorpayx : nullProvider),
    },
  ],
  exports: [SettlementsService, EarningsService],
})
export class SettlementsModule {}
