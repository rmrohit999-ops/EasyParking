/**
 * Gateway-agnostic boundary for Milestone 7 (Payments). The rest of the
 * payments module (PaymentsService, WebhooksController) is written only
 * against this interface — never against a concrete gateway SDK — so
 * swapping providers doesn't ripple into booking/ledger logic, matching
 * Milestone 0 §3's "module boundaries as interfaces" principle. The
 * Milestone 0 blueprint's Open Question #1 flagged a Razorpay-shaped
 * interface (order + webhook + refund) as the default assumption for an
 * India-marketplace fit — RazorpayProvider below implements it; a
 * different gateway is a second implementation of the same interface, not
 * a rewrite of this module.
 */
export interface CreateGatewayOrderParams {
  amountMinorUnits: bigint;
  currency: string;
  /** Our own PaymentOrder.id — passed as the gateway "receipt" so a support agent can cross-reference without a DB lookup. */
  receiptId: string;
  /** Cross-referenced back against the booking on webhook receipt (T7 mitigation, Milestone 0 §13). */
  notes: Record<string, string>;
}

export interface GatewayOrderResult {
  gatewayOrderId: string;
}

export interface CreateGatewayRefundParams {
  gatewayPaymentId: string;
  amountMinorUnits: bigint;
  idempotencyKey: string;
}

export interface GatewayRefundResult {
  gatewayRefundId: string;
}

export interface PaymentProvider {
  readonly gatewayName: string;
  readonly isConfigured: boolean;

  createOrder(params: CreateGatewayOrderParams): Promise<GatewayOrderResult>;

  /**
   * HMAC-verifies a webhook delivery against the raw (unparsed) request
   * body — verification must run on the exact bytes the gateway signed,
   * which is why WebhooksController captures `req.rawBody` rather than the
   * JSON-parsed body (Milestone 0 §13, T7).
   */
  verifyWebhookSignature(rawBody: Buffer, signatureHeader: string | undefined): boolean;

  createRefund(params: CreateGatewayRefundParams): Promise<GatewayRefundResult>;
}

// Named _SERVICE to avoid any confusion with the PAYMENT_PROVIDER env var
// (which selects *which* implementation this DI token resolves to).
export const PAYMENT_PROVIDER_SERVICE = 'PAYMENT_PROVIDER_SERVICE';
