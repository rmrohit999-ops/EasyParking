import { Module } from '@nestjs/common';
import { BookingModule } from '../booking/booking.module';
import { PaymentsModule } from '../payments/payments.module';
import { NotificationsModule } from '../notifications/notifications.module';
import { RefundsController } from './refunds.controller';
import { RefundsService } from './refunds.service';

/**
 * RefundsModule — real (Milestone 9: earnings, refunds and settlements).
 * Imports BookingModule for BookingService (to drive the actual status
 * transition alongside the refund) and PaymentsModule for the
 * PAYMENT_PROVIDER_SERVICE token (to dispatch a gateway refund) — neither
 * of those modules imports this one, so the dependency graph stays a DAG
 * exactly like QrModule's relationship to BookingModule/LedgerModule in
 * Milestone 8.
 */
@Module({
  imports: [BookingModule, PaymentsModule, NotificationsModule],
  controllers: [RefundsController],
  providers: [RefundsService],
  exports: [RefundsService],
})
export class RefundsModule {}
