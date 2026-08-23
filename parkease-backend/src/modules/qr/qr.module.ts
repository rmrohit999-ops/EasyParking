import { Module } from '@nestjs/common';
import { BookingModule } from '../booking/booking.module';
import { LedgerModule } from '../ledger/ledger.module';
import { RefundsModule } from '../refunds/refunds.module';
import { QrPassController } from './qr-pass.controller';
import { AttendantController } from './attendant.controller';
import { QrService } from './qr.service';

/**
 * QrModule — real (Milestone 8, extended in Milestone 9). Depends on
 * BookingModule (state transitions) and LedgerModule (cash-collection
 * ledger recording) the same way PaymentsModule does — QrService is
 * written only against BookingService/LedgerService's public methods,
 * never Prisma writes to bookings/section_availability directly, keeping
 * applyBookingTransition the one true choke point for status changes and
 * capacity math. RefundsModule was added in Milestone 9 so a mismatch
 * resolved as REJECTED_ENTRY can trigger its full refund in the same
 * request — RefundsModule imports BookingModule too but never imports
 * QrModule, so this stays a DAG.
 */
@Module({
  imports: [BookingModule, LedgerModule, RefundsModule],
  controllers: [QrPassController, AttendantController],
  providers: [QrService],
  exports: [QrService],
})
export class QrModule {}
