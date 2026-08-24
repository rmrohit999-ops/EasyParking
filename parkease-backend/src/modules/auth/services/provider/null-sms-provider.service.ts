import { Injectable, ServiceUnavailableException } from '@nestjs/common';
import { SmsProvider } from './sms-provider.interface';

/**
 * Selected instead of TwilioSmsProviderService when SMS_PROVIDER isn't set
 * to a known gateway — mirrors NullPaymentProviderService/
 * NullPayoutProviderService's "fail loudly, never fake success" rule
 * exactly. OtpService.requestOtp already refuses before reaching a
 * provider at all when unconfigured, so this is a defensive second layer,
 * not the primary gate.
 */
@Injectable()
export class NullSmsProviderService implements SmsProvider {
  readonly providerName = 'none';
  readonly isConfigured = false;

  async sendOtp(_phoneNumber: string, _code: string): Promise<void> {
    throw new ServiceUnavailableException('Phone verification is temporarily unavailable. Please use email login instead.');
  }
}
