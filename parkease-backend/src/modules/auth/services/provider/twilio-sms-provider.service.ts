import { Injectable, Logger, ServiceUnavailableException } from '@nestjs/common';
import { ConfigService } from '@nestjs/config';
import { AppConfig } from '../../../../common/config/configuration';
import { SmsProvider } from './sms-provider.interface';

/**
 * Real Twilio Messages API integration — same "no SDK, plain fetch, Basic
 * auth, no simulated-success branch" shape as
 * payments/provider/razorpay-provider.service.ts. Twilio's Auth Token is
 * the actual account secret (equivalent to Razorpay's key_secret) — never
 * logged, never returned to a client.
 */
@Injectable()
export class TwilioSmsProviderService implements SmsProvider {
  private readonly logger = new Logger(TwilioSmsProviderService.name);
  readonly providerName = 'twilio';
  readonly isConfigured: boolean;
  private readonly accountSid: string;
  private readonly authToken: string;
  private readonly fromNumber: string;

  constructor(configService: ConfigService<AppConfig, true>) {
    const otp = configService.get('otp', { infer: true });
    this.accountSid = otp.twilioAccountSid;
    this.authToken = otp.twilioAuthToken;
    this.fromNumber = otp.twilioFromNumber;
    this.isConfigured = otp.isConfigured;

    if (!this.isConfigured) {
      this.logger.warn(
        'Twilio is not fully configured (TWILIO_ACCOUNT_SID/TWILIO_AUTH_TOKEN/TWILIO_FROM_NUMBER). ' +
          'OTP requests will report unavailable until all three are set — see .env.example.',
      );
    }
  }

  private authHeader(): string {
    return 'Basic ' + Buffer.from(`${this.accountSid}:${this.authToken}`).toString('base64');
  }

  async sendOtp(phoneNumber: string, code: string): Promise<void> {
    if (!this.isConfigured) {
      throw new ServiceUnavailableException('Phone verification is temporarily unavailable. Please use email login instead.');
    }

    const body = new URLSearchParams({
      To: phoneNumber,
      From: this.fromNumber,
      Body: `Your ParkEase verification code is ${code}. It expires shortly — don't share it with anyone.`,
    });

    const response = await fetch(`https://api.twilio.com/2010-04-01/Accounts/${this.accountSid}/Messages.json`, {
      method: 'POST',
      headers: {
        Authorization: this.authHeader(),
        'Content-Type': 'application/x-www-form-urlencoded',
      },
      body,
    });

    if (!response.ok) {
      const responseBody = await response.text().catch(() => '');
      this.logger.error(`Twilio send failed: ${response.status} ${responseBody}`);
      throw new ServiceUnavailableException('We could not send that verification code. Please try again shortly.');
    }
  }
}
