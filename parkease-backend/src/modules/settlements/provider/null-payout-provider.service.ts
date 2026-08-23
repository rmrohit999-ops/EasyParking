import { Injectable, ServiceUnavailableException } from '@nestjs/common';
import { CreateGatewayPayoutParams, GatewayPayoutResult, PayoutProvider } from './payout-provider.interface';

/**
 * Selected instead of RazorpayXPayoutProviderService when PAYOUT_PROVIDER
 * isn't set to a known gateway — mirrors NullPaymentProviderService's
 * "fail loudly, never fake success" rule exactly.
 */
@Injectable()
export class NullPayoutProviderService implements PayoutProvider {
  readonly payoutName = 'none';
  readonly isConfigured = false;

  async createPayout(_params: CreateGatewayPayoutParams): Promise<GatewayPayoutResult> {
    throw new ServiceUnavailableException('Owner payouts are temporarily unavailable. Please try again later.');
  }
}
