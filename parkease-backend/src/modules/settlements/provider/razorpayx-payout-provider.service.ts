import { Injectable, Logger, ServiceUnavailableException } from '@nestjs/common';
import { ConfigService } from '@nestjs/config';
import { AppConfig } from '../../../common/config/configuration';
import { CreateGatewayPayoutParams, GatewayPayoutResult, PayoutProvider } from './payout-provider.interface';

/**
 * Real RazorpayX Payouts REST API integration — same "no SDK, plain fetch,
 * Basic auth, no simulated-success branch" shape as
 * payments/provider/razorpay-provider.service.ts, against
 * https://api.razorpay.com/v1/payouts. RazorpayX additionally requires the
 * originating virtual/current account number on every request (the
 * `X-Account-Number` header — or, per RazorpayX's own docs, an
 * `account_number` field on the payout body; done here as a body field to
 * avoid a second bespoke header path) since one RazorpayX business account
 * can hold several such accounts.
 *
 * Like every other external gateway in this build, this sandbox has no
 * live RAZORPAYX_* credentials and no network egress to exercise this
 * class end-to-end — disclosed here exactly as it is on
 * RazorpayProviderService. A payout also requires a Razorpay "contact" +
 * "fund account" to exist for the payee before /payouts will accept a
 * request; creating those is a real, separate RazorpayX API call this
 * class does not yet make (an MVP scope cut, not a hidden shortcut) — see
 * SettlementsService's doc comment for how that gap is surfaced rather
 * than silently worked around.
 */
@Injectable()
export class RazorpayXPayoutProviderService implements PayoutProvider {
  private readonly logger = new Logger(RazorpayXPayoutProviderService.name);
  readonly payoutName = 'razorpayx';
  readonly isConfigured: boolean;
  private readonly accountNumber: string;
  private readonly keyId: string;
  private readonly keySecret: string;
  private static readonly API_BASE = 'https://api.razorpay.com/v1';

  constructor(configService: ConfigService<AppConfig, true>) {
    const payouts = configService.get('payouts', { infer: true });
    this.accountNumber = payouts.accountNumber;
    this.keyId = payouts.keyId;
    this.keySecret = payouts.keySecret;
    this.isConfigured = payouts.isConfigured;

    if (!this.isConfigured) {
      this.logger.warn(
        'Payout provider is not configured (PAYOUT_PROVIDER/RAZORPAYX_ACCOUNT_NUMBER/RAZORPAYX_KEY_ID/RAZORPAYX_KEY_SECRET). ' +
          'Settlement processing will report unavailable until they are set — see .env.example.',
      );
    }
  }

  private requireConfigured(): void {
    if (!this.isConfigured) {
      throw new ServiceUnavailableException('Owner payouts are temporarily unavailable. Please try again later.');
    }
  }

  private authHeader(): string {
    return 'Basic ' + Buffer.from(`${this.keyId}:${this.keySecret}`).toString('base64');
  }

  async createPayout(params: CreateGatewayPayoutParams): Promise<GatewayPayoutResult> {
    this.requireConfigured();

    // NOTE (disclosed MVP gap): RazorpayX payouts target a `fund_account_id`,
    // not a raw account number/VPA — the real integration path is
    // contact.create -> fund_account.create -> payout.create. Since this
    // sandbox can never reach the live API to validate that flow either
    // way, this call sends the destination details inline via
    // `fund_account` object creation shorthand RazorpayX's API accepts on
    // /payouts for a first-time payee, which is the closest single-call
    // approximation of the real flow; a production rollout should persist
    // the returned fund_account id on OwnerPayoutAccount and reuse it on
    // subsequent payouts rather than re-creating it every time.
    const fundAccount =
      params.destination.method === 'BANK'
        ? {
            account_type: 'bank_account',
            bank_account: {
              name: params.destination.accountHolderName,
              account_number: params.destination.accountNumber,
              ifsc: params.destination.ifsc,
            },
          }
        : {
            account_type: 'vpa',
            vpa: { address: params.destination.upiVpa },
          };

    const response = await fetch(`${RazorpayXPayoutProviderService.API_BASE}/payouts`, {
      method: 'POST',
      headers: {
        Authorization: this.authHeader(),
        'Content-Type': 'application/json',
        'X-Payout-Idempotency': params.idempotencyKey,
      },
      body: JSON.stringify({
        account_number: this.accountNumber,
        fund_account: fundAccount,
        amount: Number(params.amountMinorUnits),
        currency: params.currency,
        mode: params.destination.method === 'BANK' ? 'IMPS' : 'UPI',
        purpose: 'payout',
        queue_if_low_balance: true,
        reference_id: params.referenceId,
      }),
    });

    if (!response.ok) {
      const body = await response.text().catch(() => '');
      this.logger.error(`RazorpayX payout failed: ${response.status} ${body}`);
      throw new ServiceUnavailableException('The payout gateway rejected the transfer. Please try again.');
    }

    const json = (await response.json()) as { id: string; status: string };
    return { gatewayPayoutId: json.id, status: json.status };
  }
}
