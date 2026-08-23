import { Module } from '@nestjs/common';
import { LedgerService } from './ledger.service';

/**
 * Filled in Milestone 7 (Payments) as its Milestone 1 placeholder promised.
 * Exports LedgerService so PaymentsModule (and, later, QrModule at
 * checkout for cash/final-amount reconciliation, and SettlementsModule in
 * Milestone 9) can record ledger entries without owning the Transaction/
 * OwnerEarningsLedgerEntry creation logic themselves.
 */
@Module({
  providers: [LedgerService],
  exports: [LedgerService],
})
export class LedgerModule {}
