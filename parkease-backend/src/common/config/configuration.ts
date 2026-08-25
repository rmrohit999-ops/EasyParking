/**
 * Groups validated env vars into a typed, structured config object rather
 * than reading process.env ad hoc throughout the codebase. Every later
 * module (payments, notifications, storage) reads its config from here and
 * decides its own "not configured" branch (see AppConfig.isPaymentsConfigured
 * etc.) instead of the raw env var leaking into business logic.
 */
export interface AppConfig {
  env: 'development' | 'staging' | 'production' | 'test';
  port: number;
  appBaseUrl: string;
  corsAllowedOrigins: string[];
  logLevel: string;
  correlationIdHeader: string;
  enableApiDocs: boolean;

  database: { url: string; poolMax: number };
  redis: { url: string };

  auth: {
    accessSecret: string;
    accessTtlSeconds: number;
    refreshSecret: string;
    refreshTtlSeconds: number;
    passwordHashAlgo: string;
    google: { clientId: string; clientSecret: string; isConfigured: boolean };
    superAdminEmail: string;
  };

  otp: {
    smsProvider: string;
    smsApiKey: string;
    smsSenderId: string;
    twilioAccountSid: string;
    twilioAuthToken: string;
    twilioFromNumber: string;
    length: number;
    ttlSeconds: number;
    maxAttempts: number;
    resendCooldownSeconds: number;
    isConfigured: boolean;
  };

  email: { provider: string; apiKey: string; fromAddress: string; isConfigured: boolean };

  payments: {
    provider: string;
    keyId: string;
    keySecret: string;
    webhookSecret: string;
    env: 'test' | 'live';
    isConfigured: boolean;
  };

  maps: { serverApiKey: string; isConfigured: boolean };

  push: { fcmProjectId: string; fcmServiceAccountJson: string; isConfigured: boolean };

  storage: {
    provider: 'S3' | 'GCS' | 'MINIO';
    endpoint: string;
    bucketPublic: string;
    bucketPrivate: string;
    accessKeyId: string;
    secretAccessKey: string;
    region: string;
    signedUrlTtlSeconds: number;
  };

  observability: { sentryDsnBackend: string };

  // Milestone 8: signs the token encoded into a booking's QR pass (an HMAC
  // over the qr_passes row id + issued_at, never the raw booking data —
  // Milestone 0 §5.11's "payload_hash" design). Deliberately its own
  // secret rather than reusing JWT_ACCESS_SECRET: a compromise of one
  // should not let an attacker forge the other.
  qr: { signingSecret: string };

  business: {
    defaultCommissionPercent: number;
    defaultGstPercent: number;
    bookingHoldTtlSeconds: number;
    instantParkingOwnerResponseTimeoutSeconds: number;
    // Milestone 7: Instant Parking bookings have no end_time at booking
    // creation (open-ended — the real amount is only known at checkout in
    // Milestone 8/9), but the booking state machine still requires a real
    // successful payment to leave PENDING_PAYMENT. This is the minimum
    // billable duration charged up front (a pre-authorization-style
    // minimum), with any additional amount owed on actual duration
    // reconciled at checkout — see payments/pricing.ts for the disclosed
    // design decision this implements.
    instantMinimumChargeHours: number;
    // Milestone 9: driver-initiated cancellation refund tiers
    // (RefundReasonCode.CANCELLATION_POLICY in refunds.service.ts). Only a
    // simple two-tier GLOBAL default is env-configured here — a real
    // CancellationPolicy row (scoped GLOBAL/PARKING/SECTION/OWNER, per the
    // Milestone 0 ERD) always wins when one exists; this is the same
    // "policy row wins, env value is the floor" pattern pricing.ts already
    // established for commission/tax.
    cancellationFullRefundHoursBeforeStart: number;
    cancellationPartialRefundPercent: number;
    // Instant Parking has no meaningful "hours before start" (start_time is
    // effectively now) — instead a short grace window after confirmation
    // during which a driver can cancel a mis-tap for a full refund; past it,
    // the pre-authorized minimum charge is treated as earned.
    instantCancellationGraceSeconds: number;
    supportContactEmail: string;
    supportContactPhone: string;
    privacyPolicyUrl: string;
    termsUrl: string;
  };

  // Milestone 9: owner payout accounts store bank account numbers / UPI
  // VPAs at rest — encrypted with this key (AES-256-GCM, see
  // common/crypto/field-encryption.ts) rather than in cleartext, even
  // though this is a sandbox project with no real bank integration. Unset
  // in dev -> payout-account creation reports unavailable rather than
  // silently storing cleartext, matching every other "no fakes, no silent
  // insecurity" provider gate in this codebase.
  payoutEncryption: { key: string; isConfigured: boolean };

  // Milestone 9: settlements/payouts to owners. A distinct gateway
  // integration from `payments` above (RazorpayX Payouts is a separate
  // product/API from Razorpay Payments, with its own account number and
  // credentials) even when both happen to be Razorpay in production —
  // modeled as its own PayoutProvider interface (settlements/provider/)
  // for the same reason PaymentProvider is its own interface.
  payouts: {
    provider: string;
    accountNumber: string;
    keyId: string;
    keySecret: string;
    isConfigured: boolean;
  };
}

export default (): AppConfig => ({
  env: (process.env.NODE_ENV as AppConfig['env']) ?? 'development',
  port: parseInt(process.env.PORT ?? '3000', 10),
  appBaseUrl: process.env.APP_BASE_URL ?? '',
  corsAllowedOrigins: (process.env.CORS_ALLOWED_ORIGINS ?? '')
    .split(',')
    .map((s) => s.trim())
    .filter(Boolean),
  logLevel: process.env.LOG_LEVEL ?? 'info',
  correlationIdHeader: process.env.CORRELATION_ID_HEADER ?? 'x-correlation-id',
  // Milestone 12 (release hardening): the interactive Swagger UI at /docs
  // enumerates every endpoint, DTO shape, and auth requirement — useful for
  // the team during development/staging, but unnecessary public
  // reconnaissance surface once this is a real production API. Defaults to
  // on outside production and off in production; ENABLE_API_DOCS is an
  // explicit opt-in override either way (e.g. a production ops team that
  // still wants it, reachable only with their own network-level access
  // control in front of it).
  enableApiDocs:
    process.env.ENABLE_API_DOCS !== undefined
      ? process.env.ENABLE_API_DOCS === 'true'
      : (process.env.NODE_ENV ?? 'development') !== 'production',

  database: {
    url: process.env.DATABASE_URL ?? '',
    poolMax: parseInt(process.env.DATABASE_POOL_MAX ?? '10', 10),
  },
  redis: { url: process.env.REDIS_URL ?? '' },

  auth: {
    accessSecret: process.env.JWT_ACCESS_SECRET ?? '',
    accessTtlSeconds: parseInt(process.env.JWT_ACCESS_TTL_SECONDS ?? '900', 10),
    refreshSecret: process.env.JWT_REFRESH_SECRET ?? '',
    refreshTtlSeconds: parseInt(process.env.JWT_REFRESH_TTL_SECONDS ?? '2592000', 10),
    passwordHashAlgo: process.env.PASSWORD_HASH_ALGO ?? 'argon2id',
    google: {
      clientId: process.env.GOOGLE_OAUTH_CLIENT_ID ?? '',
      clientSecret: process.env.GOOGLE_OAUTH_CLIENT_SECRET ?? '',
      isConfigured: Boolean(process.env.GOOGLE_OAUTH_CLIENT_ID && process.env.GOOGLE_OAUTH_CLIENT_SECRET),
    },
    superAdminEmail: (process.env.SUPER_ADMIN_EMAIL ?? '').trim().toLowerCase(),
  },

  otp: {
    smsProvider: process.env.SMS_PROVIDER ?? '',
    smsApiKey: process.env.SMS_PROVIDER_API_KEY ?? '',
    smsSenderId: process.env.SMS_SENDER_ID ?? '',
    twilioAccountSid: process.env.TWILIO_ACCOUNT_SID ?? '',
    twilioAuthToken: process.env.TWILIO_AUTH_TOKEN ?? '',
    twilioFromNumber: process.env.TWILIO_FROM_NUMBER ?? '',
    length: parseInt(process.env.OTP_LENGTH ?? '6', 10),
    ttlSeconds: parseInt(process.env.OTP_TTL_SECONDS ?? '300', 10),
    maxAttempts: parseInt(process.env.OTP_MAX_ATTEMPTS ?? '5', 10),
    resendCooldownSeconds: parseInt(process.env.OTP_RESEND_COOLDOWN_SECONDS ?? '30', 10),
    isConfigured:
      process.env.SMS_PROVIDER === 'twilio'
        ? Boolean(process.env.TWILIO_ACCOUNT_SID && process.env.TWILIO_AUTH_TOKEN && process.env.TWILIO_FROM_NUMBER)
        : Boolean(process.env.SMS_PROVIDER && process.env.SMS_PROVIDER_API_KEY),
  },

  email: {
    provider: process.env.EMAIL_PROVIDER ?? '',
    apiKey: process.env.EMAIL_PROVIDER_API_KEY ?? '',
    fromAddress: process.env.EMAIL_FROM_ADDRESS ?? '',
    isConfigured: Boolean(process.env.EMAIL_PROVIDER && process.env.EMAIL_PROVIDER_API_KEY),
  },

  payments: {
    provider: process.env.PAYMENT_PROVIDER ?? '',
    keyId: process.env.PAYMENT_KEY_ID ?? '',
    keySecret: process.env.PAYMENT_KEY_SECRET ?? '',
    webhookSecret: process.env.PAYMENT_WEBHOOK_SECRET ?? '',
    env: (process.env.PAYMENT_ENV as 'test' | 'live') ?? 'test',
    isConfigured: Boolean(
      process.env.PAYMENT_PROVIDER && process.env.PAYMENT_KEY_ID && process.env.PAYMENT_KEY_SECRET,
    ),
  },

  maps: {
    serverApiKey: process.env.MAPS_API_KEY_SERVER ?? '',
    isConfigured: Boolean(process.env.MAPS_API_KEY_SERVER),
  },

  push: {
    fcmProjectId: process.env.FCM_PROJECT_ID ?? '',
    fcmServiceAccountJson: process.env.FCM_SERVICE_ACCOUNT_JSON ?? '',
    isConfigured: Boolean(process.env.FCM_PROJECT_ID && process.env.FCM_SERVICE_ACCOUNT_JSON),
  },

  storage: {
    provider: (process.env.STORAGE_PROVIDER ?? 'minio').toUpperCase() as AppConfig['storage']['provider'],
    endpoint: process.env.STORAGE_ENDPOINT ?? '',
    bucketPublic: process.env.STORAGE_BUCKET_PUBLIC ?? '',
    bucketPrivate: process.env.STORAGE_BUCKET_PRIVATE ?? '',
    accessKeyId: process.env.STORAGE_ACCESS_KEY_ID ?? '',
    secretAccessKey: process.env.STORAGE_SECRET_ACCESS_KEY ?? '',
    region: process.env.STORAGE_REGION ?? 'us-east-1',
    signedUrlTtlSeconds: parseInt(process.env.STORAGE_SIGNED_URL_TTL_SECONDS ?? '600', 10),
  },

  observability: { sentryDsnBackend: process.env.SENTRY_DSN_BACKEND ?? '' },

  qr: { signingSecret: process.env.QR_SIGNING_SECRET ?? 'dev-only-change-me-qr' },

  business: {
    defaultCommissionPercent: parseFloat(process.env.DEFAULT_COMMISSION_PERCENT ?? '10'),
    defaultGstPercent: parseFloat(process.env.DEFAULT_GST_PERCENT ?? '18'),
    bookingHoldTtlSeconds: parseInt(process.env.BOOKING_HOLD_TTL_SECONDS ?? '600', 10),
    instantParkingOwnerResponseTimeoutSeconds: parseInt(
      process.env.INSTANT_PARKING_OWNER_RESPONSE_TIMEOUT_SECONDS ?? '90',
      10,
    ),
    instantMinimumChargeHours: parseInt(process.env.INSTANT_MINIMUM_CHARGE_HOURS ?? '1', 10),
    cancellationFullRefundHoursBeforeStart: parseInt(process.env.CANCELLATION_FULL_REFUND_HOURS_BEFORE_START ?? '2', 10),
    cancellationPartialRefundPercent: parseFloat(process.env.CANCELLATION_PARTIAL_REFUND_PERCENT ?? '50'),
    instantCancellationGraceSeconds: parseInt(process.env.INSTANT_CANCELLATION_GRACE_SECONDS ?? '120', 10),
    supportContactEmail: process.env.SUPPORT_CONTACT_EMAIL ?? '',
    supportContactPhone: process.env.SUPPORT_CONTACT_PHONE ?? '',
    privacyPolicyUrl: process.env.PRIVACY_POLICY_URL ?? '',
    termsUrl: process.env.TERMS_URL ?? '',
  },

  payoutEncryption: {
    key: process.env.PAYOUT_ENCRYPTION_KEY ?? '',
    isConfigured: Boolean(process.env.PAYOUT_ENCRYPTION_KEY),
  },

  payouts: {
    provider: process.env.PAYOUT_PROVIDER ?? '',
    accountNumber: process.env.RAZORPAYX_ACCOUNT_NUMBER ?? '',
    keyId: process.env.RAZORPAYX_KEY_ID ?? '',
    keySecret: process.env.RAZORPAYX_KEY_SECRET ?? '',
    isConfigured: Boolean(
      process.env.PAYOUT_PROVIDER && process.env.RAZORPAYX_ACCOUNT_NUMBER && process.env.RAZORPAYX_KEY_ID && process.env.RAZORPAYX_KEY_SECRET,
    ),
  },
});
