/**
 * Gateway-agnostic boundary for OTP SMS dispatch — mirrors
 * payments/provider/payment-provider.interface.ts's design exactly.
 * OtpService is written only against this interface, never against a
 * concrete vendor SDK, so swapping providers (Twilio/MSG91/etc.) doesn't
 * ripple into the OTP challenge lifecycle.
 */
export interface SmsProvider {
  readonly providerName: string;
  readonly isConfigured: boolean;

  /** Sends a one-time code to a phone number. Never logs the code itself. */
  sendOtp(phoneNumber: string, code: string): Promise<void>;
}

export const SMS_PROVIDER_SERVICE = 'SMS_PROVIDER_SERVICE';
