import { Module } from '@nestjs/common';
import { ConfigService } from '@nestjs/config';
import { AppConfig } from '../../common/config/configuration';
import { BookingModule } from '../booking/booking.module';
import { LedgerModule } from '../ledger/ledger.module';
import { PaymentsController } from './payments.controller';
import { WebhooksController } from './webhooks.controller';
import { PaymentsService } from './payments.service';
import { PAYMENT_PROVIDER_SERVICE } from './provider/payment-provider.interface';
import { RazorpayProviderService } from './provider/razorpay-provider.service';
import { NullPaymentProviderService } from './provider/null-payment-provider.service';

/**
 * PaymentsModule — real (Milestone 7). Selects the concrete
 * PaymentProvider once at bootstrap based on `PAYMENT_PROVIDER`
 * (RazorpayProviderService for `razorpay`; NullPaymentProviderService for
 * anything else, including unset — the "no gateway configured" branch that
 * makes every payment endpoint report 503 rather than fake success). This
 * mirrors StorageModule's provider-selection pattern (Milestone 4)
 * exactly, and is the reason PaymentsService is written only against the
 * PaymentProvider interface, never against RazorpayProviderService
 * directly.
 */
@Module({
  imports: [BookingModule, LedgerModule],
  controllers: [PaymentsController, WebhooksController],
  providers: [
    PaymentsService,
    RazorpayProviderService,
    NullPaymentProviderService,
    {
      provide: PAYMENT_PROVIDER_SERVICE,
      inject: [ConfigService, RazorpayProviderService, NullPaymentProviderService],
      useFactory: (
        configService: ConfigService<AppConfig, true>,
        razorpay: RazorpayProviderService,
        nullProvider: NullPaymentProviderService,
      ) => (configService.get('payments', { infer: true }).provider === 'razorpay' ? razorpay : nullProvider),
    },
  ],
  // PAYMENT_PROVIDER_SERVICE is also exported (Milestone 9) so
  // RefundsModule can dispatch a gateway refund against the same provider
  // an online payment was originally captured through, without
  // PaymentsModule needing to know refunds exist as a concept.
  exports: [PaymentsService, PAYMENT_PROVIDER_SERVICE],
})
export class PaymentsModule {}
