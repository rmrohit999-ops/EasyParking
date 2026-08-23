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
 * RazorpayProviderService. A payout requires a Razorpay "contact" + "fund
 * account" to exist for the payee before /payouts will accept a request:
 * on an account's first payout (no existingFundAccountId passed in), this
 * class makes the real contact.create -> fund_account.create calls and
 * returns the new fund_account_id for SettlementsService to persist onto
 * OwnerPayoutAccount; every later payout to the same account reuses that
 * id directly instead of re-creating a contact/fund account each time.
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

  private async postJson<T>(path: string, body: unknown, idempotencyKey?: string): Promise<T> {
    const response = await fetch(`${RazorpayXPayoutProviderService.API_BASE}${path}`, {
      method: 'POST',
      headers: {
        Authorization: this.authHeader(),
        'Content-Type': 'application/json',
        ...(idempotencyKey ? { 'X-Payout-Idempotency': idempotencyKey } : {}),
      },
      body: JSON.stringify(body),
    });
    if (!response.ok) {
      const responseBody = await response.text().catch(() => '');
      this.logger.error(`RazorpayX ${path} failed: ${response.status} ${responseBody}`);
      throw new ServiceUnavailableException('The payout gateway rejected the request. Please try again.');
    }
    return response.json() as Promise<T>;
  }

  /** POST /contacts — a vendor being paid out, not a customer (Razorpay's own terminology for this direction of money movement). */
  private async createContact(accountHolderName: string, referenceId: string): Promise<string> {
    const contact = await this.postJson<{ id: string }>('/contacts', {
      name: accountHolderName,
      type: 'vendor',
      reference_id: referenceId,
    });
    return contact.id;
  }

  /** POST /fund_accounts — links a bank account or UPI VPA to a contact so /payouts can target it by fund_account_id. */
  private async createFundAccount(contactId: string, destination: CreateGatewayPayoutParams['destination']): Promise<string> {
    const fundAccount = await this.postJson<{ id: string }>('/fund_accounts', {
      contact_id: contactId,
      account_type: destination.method === 'BANK' ? 'bank_account' : 'vpa',
      ...(destination.method === 'BANK'
        ? { bank_account: { name: destination.accountHolderName, account_number: destination.accountNumber, ifsc: destination.ifsc } }
        : { vpa: { address: destination.upiVpa } }),
    });
    return fundAccount.id;
  }

  async createPayout(params: CreateGatewayPayoutParams): Promise<GatewayPayoutResult> {
    this.requireConfigured();

    let fundAccountId = params.existingFundAccountId;
    let newContactId: string | undefined;

    if (!fundAccountId) {
      newContactId = await this.createContact(params.destination.accountHolderName, params.referenceId);
      fundAccountId = await this.createFundAccount(newContactId, params.destination);
    }

    const json = await this.postJson<{ id: string; status: string }>(
      '/payouts',
      {
        account_number: this.accountNumber,
        fund_account_id: fundAccountId,
        amount: Number(params.amountMinorUnits),
        currency: params.currency,
        mode: params.destination.method === 'BANK' ? 'IMPS' : 'UPI',
        purpose: 'payout',
        queue_if_low_balance: true,
        reference_id: params.referenceId,
      },
      params.idempotencyKey,
    );

    return { gatewayPayoutId: json.id, status: json.status, fundAccountId, contactId: newContactId };
  }
}
