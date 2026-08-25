import { Module } from '@nestjs/common';
import { BookingController } from './booking.controller';
import { BookingService } from './booking.service';
import { BookingExpiryProducer } from './booking-expiry.queue';
import { BookingExpiryProcessor } from './booking-expiry.processor';
import { NotificationsModule } from '../notifications/notifications.module';
import { RealtimeModule } from '../realtime/realtime.module';

/**
 * BookingModule — real (Milestone 6: availability and booking). The
 * check-in/check-out/mismatch transitions already exist in the state
 * machine (booking-state-machine.ts) and in `applyBookingTransition`, but
 * are only reachable via BookingService.markPaid and Milestone 8's QR &
 * parking operations endpoints — nothing in this module lets a driver
 * fake their way to CONFIRMED. `admin-booking.controller.ts` (the old
 * ADMIN-initiated cancel route) was removed in Milestone 9 — that route
 * now lives in RefundsModule alongside the refund it always needs to
 * trigger; BookingService.adminCancelBooking is unchanged and still does
 * the actual transition, RefundsService just calls it directly instead of
 * a controller in this module exposing it. NotificationsModule
 * (Milestone 11) was added so applyBookingTransition can fire a real
 * best-effort notification at the one choke point every booking status
 * change already passes through — see BookingService.notifyOnTransition.
 */
@Module({
  imports: [NotificationsModule, RealtimeModule],
  controllers: [BookingController],
  providers: [BookingService, BookingExpiryProducer, BookingExpiryProcessor],
  exports: [BookingService],
})
export class BookingModule {}
