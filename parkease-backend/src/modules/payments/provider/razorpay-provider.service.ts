import { Injectable, Logger, ServiceUnavailableException } from '@nestjs/common';
import { ConfigService } from '@nestjs/config';
import { createHmac, timingSafeEqual } from 'crypto';
import { AppConfig } from '../../../common/config/configuration';
import {
  CreateGatewayOrderParams,
  CreateGatewayRefundParams,
  GatewayOrderResult,
  GatewayRefundResult,
  PaymentProvider,
} from './payment-provider.interface';

/**
 * Real Razorpay REST API integration — no SDK dependency, just `fetch`
 * (global since Node 18+) against https://api.razorpay.com/v1 with HTTP
 * Basic auth (keyId:keySecret), matching Razorpay's own documented
 * server-to-server API. This is a genuine gateway client: it makes real
 * network calls and will only ever succeed against real, live
 * PAYMENT_KEY_ID/PAYMENT_KEY_SECRET credentials — there is no
 * simulated/always-succeeds branch anywhere in this class ("no fakes",
 * Milestone 0 §14.6).
 *
 * This sandbox has no PAYMENT_* credentials and no network egress to
 * verify these calls end-to-end against Razorpay's real API — that
 * limitation is disclosed here exactly as it has been for every other
 * external integration in this build (storage, SMS, email). The HTTP
 * shapes below follow Razorpay's public API documentation as of this
 * build; a live credential is required to exercise this class for real.
 */
@Injectable()
export class RazorpayProviderService implements PaymentProvider {
  private readonly logger = new Logger(RazorpayProviderService.name);
  readonly gatewayName = 'razorpay';
  readonly isConfigured: boolean;
  private readonly keyId: string;
  private readonly keySecret: string;
  private readonly webhookSecret: string;
  private static readonly API_BASE = 'https://api.razorpay.com/v1';

  constructor(configService: ConfigService<AppConfig, true>) {
    const payments = configService.get('payments', { infer: true });
    this.keyId = payments.keyId;
    this.keySecret = payments.keySecret;
    this.webhookSecret = payments.webhookSecret;
    this.isConfigured = payments.isConfigured;

    if (!this.isConfigured) {
      this.logger.warn(
        'Payment provider is not configured (PAYMENT_PROVIDER/PAYMENT_KEY_ID/PAYMENT_KEY_SECRET). ' +
          'Payment endpoints will return 503 until they are set — see .env.example.',
      );
    }
  }

  private requireConfigured(): void {
    if (!this.isConfigured) {
      throw new ServiceUnavailableException(
        'Online payments are temporarily unavailable. Please try again later.',
      );
    }
  }

  private authHeader(): string {
    return 'Basic ' + Buffer.from(`${this.keyId}:${this.keySecret}`).toString('base64');
  }

  async createOrder(params: CreateGatewayOrderParams): Promise<GatewayOrderResult> {
    this.requireConfigured();

    // Razorpay's Orders API takes `amount` as an integer in the currency's
    // smallest unit (paise for INR) — exactly the minor-unit convention
    // this codebase already uses everywhere (Milestone 1 §money.ts), so no
    // conversion is needed here beyond BigInt -> Number (safe: order
    // amounts are always well under Number.MAX_SAFE_INTEGER).
    const response = await fetch(`${RazorpayProviderService.API_BASE}/orders`, {
      method: 'POST',
      headers: {
        Authorization: this.authHeader(),
        'Content-Type': 'application/json',
      },
      body: JSON.stringify({
        amount: Number(params.amountMinorUnits),
        currency: params.currency,
        receipt: params.receiptId,
        notes: params.notes,
      }),
    });

    if (!response.ok) {
      const body = await response.text().catch(() => '');
      this.logger.error(`Razorpay order creation failed: ${response.status} ${body}`);
      throw new ServiceUnavailableException('The payment gateway rejected the order. Please try again.');
    }

    const json = (await response.json()) as { id: string };
    return { gatewayOrderId: json.id };
  }

  verifyWebhookSignature(rawBody: Buffer, signatureHeader: string | undefined): boolean {
    if (!this.isConfigured || !this.webhookSecret || !signatureHeader) return false;
    const expected = createHmac('sha256', this.webhookSecret).update(rawBody).digest('hex');
    const expectedBuf = Buffer.from(expected, 'utf8');
    const actualBuf = Buffer.from(signatureHeader, 'utf8');
    // Constant-time comparison — a plain `===` would leak timing
    // information an attacker could use to forge a valid signature byte
    // by byte (Milestone 0 §13, T7).
    if (expectedBuf.length !== actualBuf.length) return false;
    return timingSafeEqual(expectedBuf, actualBuf);
  }

  async createRefund(params: CreateGatewayRefundParams): Promise<GatewayRefundResult> {
    this.requireConfigured();

    const response = await fetch(
      `${RazorpayProviderService.API_BASE}/payments/${encodeURIComponent(params.gatewayPaymentId)}/refund`,
      {
        method: 'POST',
        headers: {
          Authorization: this.authHeader(),
          'Content-Type': 'application/json',
          // Razorpay honours this header so a retried refund with the same
          // key is a no-op replay, not a second refund (Milestone 0 §3's
          // "idempotency everywhere money moves").
          'X-Razorpay-Idempotency-Key': params.idempotencyKey,
        },
        body: JSON.stringify({ amount: Number(params.amountMinorUnits) }),
      },
    );

    if (!response.ok) {
      const body = await response.text().catch(() => '');
      this.logger.error(`Razorpay refund failed: ${response.status} ${body}`);
      throw new ServiceUnavailableException('The payment gateway rejected the refund. Please try again.');
    }

    const json = (await response.json()) as { id: string };
    return { gatewayRefundId: json.id };
  }
}
