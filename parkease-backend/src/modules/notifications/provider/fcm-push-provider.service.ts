import { Injectable, Logger, ServiceUnavailableException } from '@nestjs/common';
import { ConfigService } from '@nestjs/config';
import { createSign } from 'crypto';
import { AppConfig } from '../../../common/config/configuration';
import { SendPushParams, SendPushResult, PushProvider } from './push-provider.interface';

interface ServiceAccount {
  project_id: string;
  client_email: string;
  private_key: string;
}

/**
 * Real Firebase Cloud Messaging HTTP v1 integration — no firebase-admin
 * SDK dependency, just Node's built-in `crypto` (to sign the OAuth2
 * service-account JWT) and `fetch` (to exchange it for an access token and
 * then call FCM), matching every other gateway integration in this
 * codebase's "plain HTTP client against the provider's own documented API,
 * no SDK, no simulated-success branch" shape (RazorpayProviderService,
 * RazorpayXPayoutProviderService).
 *
 * The OAuth2 flow: sign a JWT (`{iss, scope, aud, iat, exp}`) with the
 * service account's RSA private key (RS256), exchange it at Google's token
 * endpoint for a short-lived access token (cached until ~1 minute before
 * expiry), then call `POST https://fcm.googleapis.com/v1/projects/{id}/
 * messages:send` with that token. This sandbox has no live
 * FCM_SERVICE_ACCOUNT_JSON and no network egress to exercise it end-to-end
 * — disclosed exactly as every other external gateway in this build is.
 */
@Injectable()
export class FcmPushProviderService implements PushProvider {
  private readonly logger = new Logger(FcmPushProviderService.name);
  readonly providerName = 'fcm';
  readonly isConfigured: boolean;
  private serviceAccount: ServiceAccount | null = null;
  private cachedToken: { token: string; expiresAtMs: number } | null = null;

  constructor(configService: ConfigService<AppConfig, true>) {
    const push = configService.get('push', { infer: true });
    this.isConfigured = push.isConfigured;
    if (this.isConfigured) {
      try {
        this.serviceAccount = JSON.parse(push.fcmServiceAccountJson) as ServiceAccount;
      } catch {
        this.logger.error('FCM_SERVICE_ACCOUNT_JSON is not valid JSON — push notifications will report unavailable.');
        this.serviceAccount = null;
      }
    }
    if (!this.isConfigured || !this.serviceAccount) {
      this.logger.warn(
        'Push provider is not configured (FCM_PROJECT_ID/FCM_SERVICE_ACCOUNT_JSON). ' +
          'Push sends will report unavailable until they are set — see .env.example.',
      );
    }
  }

  private requireConfigured(): ServiceAccount {
    if (!this.isConfigured || !this.serviceAccount) {
      throw new ServiceUnavailableException('Push notifications are temporarily unavailable.');
    }
    return this.serviceAccount;
  }

  private base64url(input: Buffer | string): string {
    return Buffer.from(input).toString('base64').replace(/\+/g, '-').replace(/\//g, '_').replace(/=+$/, '');
  }

  private async getAccessToken(account: ServiceAccount): Promise<string> {
    const now = Date.now();
    if (this.cachedToken && this.cachedToken.expiresAtMs - 60_000 > now) {
      return this.cachedToken.token;
    }

    const iat = Math.floor(now / 1000);
    const exp = iat + 3600;
    const header = this.base64url(JSON.stringify({ alg: 'RS256', typ: 'JWT' }));
    const claims = this.base64url(
      JSON.stringify({
        iss: account.client_email,
        scope: 'https://www.googleapis.com/auth/firebase.messaging',
        aud: 'https://oauth2.googleapis.com/token',
        iat,
        exp,
      }),
    );
    const unsigned = `${header}.${claims}`;
    const signer = createSign('RSA-SHA256');
    signer.update(unsigned);
    signer.end();
    const signature = this.base64url(signer.sign(account.private_key));
    const jwt = `${unsigned}.${signature}`;

    const response = await fetch('https://oauth2.googleapis.com/token', {
      method: 'POST',
      headers: { 'Content-Type': 'application/x-www-form-urlencoded' },
      body: new URLSearchParams({
        grant_type: 'urn:ietf:params:oauth:grant-type:jwt-bearer',
        assertion: jwt,
      }).toString(),
    });
    if (!response.ok) {
      const body = await response.text().catch(() => '');
      this.logger.error(`FCM OAuth2 token exchange failed: ${response.status} ${body}`);
      throw new ServiceUnavailableException('Could not authenticate with the push provider. Please try again later.');
    }
    const json = (await response.json()) as { access_token: string; expires_in: number };
    this.cachedToken = { token: json.access_token, expiresAtMs: now + json.expires_in * 1000 };
    return json.access_token;
  }

  async send(params: SendPushParams): Promise<SendPushResult> {
    const account = this.requireConfigured();
    const accessToken = await this.getAccessToken(account);

    const response = await fetch(`https://fcm.googleapis.com/v1/projects/${account.project_id}/messages:send`, {
      method: 'POST',
      headers: { Authorization: `Bearer ${accessToken}`, 'Content-Type': 'application/json' },
      body: JSON.stringify({
        message: {
          token: params.fcmToken,
          notification: { title: params.title, body: params.body },
          data: { ...(params.data ?? {}), ...(params.deepLink ? { deepLink: params.deepLink } : {}) },
        },
      }),
    });
    if (!response.ok) {
      const body = await response.text().catch(() => '');
      this.logger.error(`FCM send failed: ${response.status} ${body}`);
      throw new ServiceUnavailableException('The push provider rejected the notification.');
    }
    const json = (await response.json()) as { name: string };
    return { messageId: json.name };
  }
}
