import * as Joi from 'joi';

/**
 * Fail-fast environment validation.
 *
 * This is what makes the "unavailable-state behavior" pattern from the
 * Milestone 0 architecture doc possible: variables that gate an entire
 * *feature* (payments, SMS OTP, maps, push) are allowed to be empty, but
 * variables the *process itself* cannot run without (DB, Redis, port,
 * JWT signing secrets) are required. A missing required var crashes boot
 * with a clear message instead of the app limping along and failing
 * confusingly on the first request.
 */
export const envValidationSchema = Joi.object({
  NODE_ENV: Joi.string().valid('development', 'staging', 'production', 'test').default('development'),
  PORT: Joi.number().port().default(3000),
  APP_BASE_URL: Joi.string().uri().required(),
  CORS_ALLOWED_ORIGINS: Joi.string().allow('').default(''),
  LOG_LEVEL: Joi.string().valid('fatal', 'error', 'warn', 'info', 'debug', 'trace').default('info'),
  CORRELATION_ID_HEADER: Joi.string().default('x-correlation-id'),

  DATABASE_URL: Joi.string().uri({ scheme: ['postgresql', 'postgres'] }).required(),
  DATABASE_POOL_MAX: Joi.number().integer().min(1).default(10),
  REDIS_URL: Joi.string().uri({ scheme: ['redis', 'rediss'] }).required(),

  // Required for the process to start (session signing), even though no
  // auth endpoints are implemented until Milestone 2.
  JWT_ACCESS_SECRET: Joi.string().min(16).required(),
  JWT_ACCESS_TTL_SECONDS: Joi.number().integer().positive().default(900),
  JWT_REFRESH_SECRET: Joi.string().min(16).required(),
  JWT_REFRESH_TTL_SECONDS: Joi.number().integer().positive().default(2592000),
  PASSWORD_HASH_ALGO: Joi.string().valid('argon2id').default('argon2id'),
  GOOGLE_OAUTH_CLIENT_ID: Joi.string().allow('').default(''),
  GOOGLE_OAUTH_CLIENT_SECRET: Joi.string().allow('').default(''),

  // Feature-gating: optional. Absence disables the feature, never fakes it.
  SMS_PROVIDER: Joi.string().allow('').default(''),
  SMS_PROVIDER_API_KEY: Joi.string().allow('').default(''),
  SMS_SENDER_ID: Joi.string().allow('').default(''),
  OTP_LENGTH: Joi.number().integer().min(4).max(8).default(6),
  OTP_TTL_SECONDS: Joi.number().integer().positive().default(300),
  OTP_MAX_ATTEMPTS: Joi.number().integer().positive().default(5),
  OTP_RESEND_COOLDOWN_SECONDS: Joi.number().integer().positive().default(30),

  EMAIL_PROVIDER: Joi.string().allow('').default(''),
  EMAIL_PROVIDER_API_KEY: Joi.string().allow('').default(''),
  EMAIL_FROM_ADDRESS: Joi.string().allow('').default(''),

  PAYMENT_PROVIDER: Joi.string().allow('').default(''),
  PAYMENT_KEY_ID: Joi.string().allow('').default(''),
  PAYMENT_KEY_SECRET: Joi.string().allow('').default(''),
  PAYMENT_WEBHOOK_SECRET: Joi.string().allow('').default(''),
  PAYMENT_ENV: Joi.string().valid('test', 'live').default('test'),

  MAPS_API_KEY_SERVER: Joi.string().allow('').default(''),

  FCM_PROJECT_ID: Joi.string().allow('').default(''),
  FCM_SERVICE_ACCOUNT_JSON: Joi.string().allow('').default(''),

  STORAGE_PROVIDER: Joi.string().valid('s3', 'gcs', 'minio').default('minio'),
  STORAGE_ENDPOINT: Joi.string().allow('').default(''),
  STORAGE_BUCKET_PUBLIC: Joi.string().required(),
  STORAGE_BUCKET_PRIVATE: Joi.string().required(),
  STORAGE_ACCESS_KEY_ID: Joi.string().allow('').default(''),
  STORAGE_SECRET_ACCESS_KEY: Joi.string().allow('').default(''),
  STORAGE_REGION: Joi.string().default('us-east-1'),
  STORAGE_SIGNED_URL_TTL_SECONDS: Joi.number().integer().positive().default(600),

  SENTRY_DSN_BACKEND: Joi.string().allow('').default(''),

  DEFAULT_COMMISSION_PERCENT: Joi.number().min(0).max(100).default(10),
  DEFAULT_GST_PERCENT: Joi.number().min(0).max(100).default(18),
  BOOKING_HOLD_TTL_SECONDS: Joi.number().integer().positive().default(600),
  INSTANT_PARKING_OWNER_RESPONSE_TIMEOUT_SECONDS: Joi.number().integer().positive().default(90),
  SUPPORT_CONTACT_EMAIL: Joi.string().allow('').default(''),
  SUPPORT_CONTACT_PHONE: Joi.string().allow('').default(''),
  PRIVACY_POLICY_URL: Joi.string().allow('').default(''),
  TERMS_URL: Joi.string().allow('').default(''),
});
