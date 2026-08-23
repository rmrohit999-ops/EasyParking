/**
 * Gateway-agnostic boundary for Milestone 9 (Earnings, refunds and
 * settlements) — mirrors payments/provider/payment-provider.interface.ts's
 * design exactly, but for the opposite money direction: platform -> owner
 * bank account/UPI VPA, rather than driver -> platform. Kept as its own
 * interface (not folded into PaymentProvider) because a payout is a
 * distinct product even on gateways that also process payments — RazorpayX
 * Payouts is a separate API/account from Razorpay Payments — so
 * SettlementsService is written only against this interface.
 */
export interface CreateGatewayPayoutParams {
  amountMinorUnits: bigint;
  currency: string;
  /** Bank: account number; UPI: VPA — already decrypted by the caller. */
  destination: { method: 'BANK' | 'UPI'; accountNumber?: string; ifsc?: string; upiVpa?: string; accountHolderName: string };
  /** Our own Settlement.id, passed through so a support agent can cross-reference without a DB lookup. */
  referenceId: string;
  idempotencyKey: string;
  /**
   * OwnerPayoutAccount.razorpayx_fund_account_id from a prior payout to
   * this same account, if one exists — lets the provider skip re-creating
   * a contact/fund account on every payout. Undefined on a payout account's
   * first-ever payout.
   */
  existingFundAccountId?: string;
}

export interface GatewayPayoutResult {
  gatewayPayoutId: string;
  status: string;
  /** The fund_account_id used for this payout (freshly created, or the reused existingFundAccountId) — caller persists it onto OwnerPayoutAccount for reuse. */
  fundAccountId: string;
  /** Only set when a new contact was created this call (first payout to this account). */
  contactId?: string;
}

export interface PayoutProvider {
  readonly payoutName: string;
  readonly isConfigured: boolean;

  createPayout(params: CreateGatewayPayoutParams): Promise<GatewayPayoutResult>;
}

export const PAYOUT_PROVIDER_SERVICE = 'PAYOUT_PROVIDER_SERVICE';
