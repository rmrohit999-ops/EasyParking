import { Injectable, ServiceUnavailableException } from '@nestjs/common';
import {
  CreateGatewayOrderParams,
  CreateGatewayRefundParams,
  GatewayOrderResult,
  GatewayRefundResult,
  PaymentProvider,
} from './payment-provider.interface';

/**
 * Selected instead of RazorpayProviderService when PAYMENT_PROVIDER isn't
 * set to a known gateway at all (as opposed to being set but missing
 * credentials, which RazorpayProviderService itself already handles via
 * isConfigured=false). Every method fails loudly and explicitly rather
 * than silently no-opping or pretending success — the same
 * "unavailable-state, not fake-success" rule as everywhere else in this
 * codebase (Milestone 0 §14).
 */
@Injectable()
export class NullPaymentProviderService implements PaymentProvider {
  readonly gatewayName = 'none';
  readonly isConfigured = false;

  async createOrder(_params: CreateGatewayOrderParams): Promise<GatewayOrderResult> {
    throw new ServiceUnavailableException('Online payments are temporarily unavailable. Please try again later.');
  }

  verifyWebhookSignature(_rawBody: Buffer, _signatureHeader: string | undefined): boolean {
    return false;
  }

  async createRefund(_params: CreateGatewayRefundParams): Promise<GatewayRefundResult> {
    throw new ServiceUnavailableException('Online payments are temporarily unavailable. Please try again later.');
  }
}
