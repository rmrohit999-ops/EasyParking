/**
 * Gateway-agnostic boundary for Milestone 11 (Notifications and
 * analytics) — same shape/purpose as PaymentProvider (Milestone 7) and
 * PayoutProvider (Milestone 9): NotificationsService is written only
 * against this interface, never against Firebase's SDK/API directly.
 */
export interface SendPushParams {
  fcmToken: string;
  title: string;
  body: string;
  deepLink?: string;
  data?: Record<string, string>;
}

export interface SendPushResult {
  messageId: string;
}

export interface PushProvider {
  readonly providerName: string;
  readonly isConfigured: boolean;

  send(params: SendPushParams): Promise<SendPushResult>;
}

export const PUSH_PROVIDER_SERVICE = 'PUSH_PROVIDER_SERVICE';
