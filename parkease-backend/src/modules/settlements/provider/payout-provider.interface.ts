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
}

export interface GatewayPayoutResult {
  gatewayPayoutId: string;
  status: string;
}

export interface PayoutProvider {
  readonly payoutName: string;
  readonly isConfigured: boolean;

  createPayout(params: CreateGatewayPayoutParams): Promise<GatewayPayoutResult>;
}

export const PAYOUT_PROVIDER_SERVICE = 'PAYOUT_PROVIDER_SERVICE';
