import { ConflictException, ForbiddenException, Inject, Injectable, Logger, NotFoundException, ServiceUnavailableException } from '@nestjs/common';
import { ConfigService } from '@nestjs/config';
import { SettlementStatus } from '@prisma/client';
import { PrismaService } from '../../common/prisma/prisma.service';
import { AppConfig } from '../../common/config/configuration';
import { decryptField } from '../../common/crypto/field-encryption';
import { CreateGatewayPayoutParams, PAYOUT_PROVIDER_SERVICE, PayoutProvider } from './provider/payout-provider.interface';
import { NotificationsService } from '../notifications/notifications.service';
import { AuditService } from '../audit/audit.service';
import { Money } from '../../common/money/money';

/**
 * Owner payout requests and admin settlement processing (Milestone 9).
 * `requestSettlement` and `processSettlement` are the two halves of the
 * PENDING -> PROCESSING/SETTLED/FAILED settlement lifecycle the Milestone
 * 0 ERD's `SettlementStatus` enum describes; together with
 * `OwnerEarningsLedgerEntry`'s PENDING -> AVAILABLE (QrService, Milestone
 * 8 checkout) -> PROCESSING (here, on request) -> SETTLED/AVAILABLE (here,
 * on process success/failure) transitions, every rupee an owner is ever
 * paid traces back through an unbroken, append-only chain to the specific
 * successful Transaction it came from.
 */
@Injectable()
export class SettlementsService {
  private readonly logger = new Logger(SettlementsService.name);

  constructor(
    private readonly prisma: PrismaService,
    private readonly configService: ConfigService<AppConfig, true>,
    @Inject(PAYOUT_PROVIDER_SERVICE) private readonly payoutProvider: PayoutProvider,
    private readonly notificationsService: NotificationsService,
    private readonly auditService: AuditService,
  ) {}

  // ---------------------------------------------------------------------
  // Owner: request a settlement of everything currently AVAILABLE
  // ---------------------------------------------------------------------

  async requestSettlement(ownerUserId: string) {
    const ownerProfile = await this.requireOwnerProfile(ownerUserId);

    const primaryAccount = await this.prisma.ownerPayoutAccount.findFirst({
      where: { owner_id: ownerProfile.id, is_primary: true },
    });
    if (!primaryAccount) throw new ConflictException('Add and select a primary payout account before requesting a settlement.');
    if (primaryAccount.verification_status !== 'VERIFIED') {
      throw new ConflictException('Your primary payout account has not been verified yet.');
    }

    const eligibleEntries = await this.prisma.ownerEarningsLedgerEntry.findMany({
      where: { owner_id: ownerProfile.id, status: 'AVAILABLE' },
    });
    if (eligibleEntries.length === 0) throw new ConflictException('No available earnings to settle right now.');

    const totalMinorUnits = eligibleEntries.reduce((sum, e) => sum + e.amount, 0n);

    const settlement = await this.prisma.$transaction(async (tx) => {
      const created = await tx.settlement.create({
        data: {
          owner_id: ownerProfile.id,
          requested_amount: totalMinorUnits,
          status: 'PENDING',
          payout_account_id: primaryAccount.id,
        },
      });
      await tx.ownerEarningsLedgerEntry.updateMany({
        where: { id: { in: eligibleEntries.map((e) => e.id) } },
        data: { status: 'PROCESSING' },
      });
      await tx.settlementItem.createMany({
        data: eligibleEntries.map((e) => ({ settlement_id: created.id, owner_earnings_ledger_id: e.id })),
      });
      return created;
    });

    return toSettlementView(settlement);
  }

  async listOwnSettlements(ownerUserId: string) {
    const ownerProfile = await this.requireOwnerProfile(ownerUserId);
    const settlements = await this.prisma.settlement.findMany({
      where: { owner_id: ownerProfile.id },
      orderBy: { requested_at: 'desc' },
    });
    return settlements.map(toSettlementView);
  }

  // ---------------------------------------------------------------------
  // Admin: list + process
  // ---------------------------------------------------------------------

  async listAllSettlements(status?: SettlementStatus) {
    const settlements = await this.prisma.settlement.findMany({
      where: status ? { status } : {},
      orderBy: { requested_at: 'desc' },
      take: 200,
    });
    return settlements.map(toSettlementView);
  }

  /**
   * Dispatches the real payout call. On success, every ledger entry this
   * settlement covers moves PROCESSING -> SETTLED; on failure (gateway
   * rejection, network error, an unconfigured provider) the settlement
   * moves to FAILED and every entry it covered moves back PROCESSING ->
   * AVAILABLE so it is picked up by the owner's *next* settlement request
   * rather than stuck in limbo — no money is ever silently lost or
   * silently marked paid without a real gateway confirmation.
   */
  async processSettlement(settlementId: string) {
    const settlement = await this.prisma.settlement.findUnique({
      where: { id: settlementId },
      include: { payout_account: true },
    });
    if (!settlement) throw new NotFoundException('Settlement not found.');
    if (settlement.status !== 'PENDING') throw new ConflictException(`This settlement is already ${settlement.status}.`);

    await this.prisma.settlement.update({ where: { id: settlementId }, data: { status: 'PROCESSING' } });

    const encryption = this.configService.get('payoutEncryption', { infer: true });
    if (!encryption.isConfigured) {
      await this.releaseBackToAvailable(settlementId, 'FAILED');
      await this.notifySettlementOutcome(settlement.owner_id, 'FAILED', settlement.requested_amount);
      throw new ServiceUnavailableException('Payout decryption is not configured. Please try again later.');
    }

    let destination: CreateGatewayPayoutParams['destination'];
    try {
      destination =
        settlement.payout_account.method === 'BANK'
          ? {
              method: 'BANK',
              accountNumber: decryptField(settlement.payout_account.bank_account_number_encrypted!, encryption.key),
              ifsc: settlement.payout_account.ifsc!,
              accountHolderName: settlement.payout_account.account_holder_name,
            }
          : {
              method: 'UPI',
              upiVpa: decryptField(settlement.payout_account.upi_vpa_encrypted!, encryption.key),
              accountHolderName: settlement.payout_account.account_holder_name,
            };
    } catch (err) {
      this.logger.error(`Could not decrypt payout destination for settlement ${settlementId}: ${(err as Error).message}`);
      await this.releaseBackToAvailable(settlementId, 'FAILED');
      await this.notifySettlementOutcome(settlement.owner_id, 'FAILED', settlement.requested_amount);
      throw new ServiceUnavailableException('The payout destination could not be read. Please try again later.');
    }

    try {
      const result = await this.payoutProvider.createPayout({
        amountMinorUnits: settlement.requested_amount,
        currency: 'INR',
        destination,
        referenceId: settlement.id,
        idempotencyKey: settlement.id,
        existingFundAccountId: settlement.payout_account.razorpayx_fund_account_id ?? undefined,
      });

      const items = await this.prisma.settlementItem.findMany({ where: { settlement_id: settlementId } });
      await this.prisma.$transaction(async (tx) => {
        await tx.settlement.update({
          where: { id: settlementId },
          data: { status: 'SETTLED', gateway_payout_id: result.gatewayPayoutId, processed_at: new Date() },
        });
        await tx.ownerEarningsLedgerEntry.updateMany({
          where: { id: { in: items.map((i) => i.owner_earnings_ledger_id) } },
          data: { status: 'SETTLED' },
        });
        // Only set on the account's first payout (see RazorpayXPayoutProviderService) — persisted here so every later payout reuses it instead of re-creating a contact/fund account.
        if (!settlement.payout_account.razorpayx_fund_account_id) {
          await tx.ownerPayoutAccount.update({
            where: { id: settlement.payout_account_id },
            data: { razorpayx_fund_account_id: result.fundAccountId, razorpayx_contact_id: result.contactId },
          });
        }
      });
      await this.notifySettlementOutcome(settlement.owner_id, 'SETTLED', settlement.requested_amount);
      await this.auditService.record({
        actorId: null,
        actorRole: null,
        action: 'DISPATCH_PAYOUT',
        targetType: 'Settlement',
        targetId: settlementId,
        afterState: { status: 'SETTLED', amountMinorUnits: settlement.requested_amount.toString(), gatewayPayoutId: result.gatewayPayoutId },
      });
    } catch (err) {
      this.logger.error(`Payout dispatch failed for settlement ${settlementId}: ${(err as Error).message}`);
      await this.releaseBackToAvailable(settlementId, 'FAILED');
      await this.notifySettlementOutcome(settlement.owner_id, 'FAILED', settlement.requested_amount);
      await this.auditService.record({
        actorId: null,
        actorRole: null,
        action: 'DISPATCH_PAYOUT',
        targetType: 'Settlement',
        targetId: settlementId,
        afterState: { status: 'FAILED', amountMinorUnits: settlement.requested_amount.toString(), error: (err as Error).message },
      });
      throw err;
    }

    return toSettlementView(await this.prisma.settlement.findUniqueOrThrow({ where: { id: settlementId } }));
  }

  private async releaseBackToAvailable(settlementId: string, finalStatus: SettlementStatus) {
    const items = await this.prisma.settlementItem.findMany({ where: { settlement_id: settlementId } });
    await this.prisma.$transaction(async (tx) => {
      await tx.settlement.update({ where: { id: settlementId }, data: { status: finalStatus } });
      await tx.ownerEarningsLedgerEntry.updateMany({
        where: { id: { in: items.map((i) => i.owner_earnings_ledger_id) } },
        data: { status: 'AVAILABLE' },
      });
    });
  }

  /**
   * Best-effort owner notification on settlement outcome (Milestone 11).
   * `Settlement.owner_id` references `OwnerProfile.id`, not `User.id`
   * directly, so this resolves the profile -> user hop first — mirrors
   * every other cross-entity notify helper in this codebase (e.g.
   * RefundsService.notifyRefund resolving Booking -> driver_id). Never
   * allowed to block or fail settlement processing: NotificationsService
   * itself already swallows push failures, and a missing/deleted owner
   * profile here just skips the notify rather than throwing.
   */
  private async notifySettlementOutcome(ownerProfileId: string, outcome: 'SETTLED' | 'FAILED', amountMinorUnits: bigint) {
    const ownerProfile = await this.prisma.ownerProfile.findUnique({ where: { id: ownerProfileId } });
    if (!ownerProfile) return;

    const amount = Money.of(amountMinorUnits, 'INR').toDisplayString();
    const isSettled = outcome === 'SETTLED';

    await this.notificationsService.send({
      userId: ownerProfile.user_id,
      category: 'settlement',
      type: `SETTLEMENT_${outcome}`,
      title: isSettled ? 'Payout sent' : 'Payout failed',
      body: isSettled
        ? `${amount} has been sent to your payout account.`
        : `We couldn't process your ${amount} payout. It's back in your available balance — please check your payout account and try again.`,
    });
  }

  private async requireOwnerProfile(userId: string) {
    const ownerProfile = await this.prisma.ownerProfile.findUnique({ where: { user_id: userId } });
    if (!ownerProfile) throw new ForbiddenException('You do not have an owner profile.');
    return ownerProfile;
  }
}

function toSettlementView(settlement: {
  id: string;
  owner_id: string;
  requested_amount: bigint;
  status: string;
  payout_account_id: string;
  gateway_payout_id: string | null;
  requested_at: Date;
  processed_at: Date | null;
}) {
  return {
    id: settlement.id,
    ownerId: settlement.owner_id,
    requestedAmountMinorUnits: settlement.requested_amount.toString(),
    status: settlement.status,
    payoutAccountId: settlement.payout_account_id,
    gatewayPayoutId: settlement.gateway_payout_id,
    requestedAt: settlement.requested_at,
    processedAt: settlement.processed_at,
  };
}
