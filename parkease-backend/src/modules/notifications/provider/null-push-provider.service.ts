import { Injectable, ServiceUnavailableException } from '@nestjs/common';
import { SendPushParams, SendPushResult, PushProvider } from './push-provider.interface';

/** Selected when push isn't configured at all — mirrors NullPaymentProviderService's "fail loudly" rule. */
@Injectable()
export class NullPushProviderService implements PushProvider {
  readonly providerName = 'none';
  readonly isConfigured = false;

  async send(_params: SendPushParams): Promise<SendPushResult> {
    throw new ServiceUnavailableException('Push notifications are temporarily unavailable.');
  }
}
