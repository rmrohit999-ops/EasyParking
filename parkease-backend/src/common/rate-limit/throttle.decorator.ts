import { SetMetadata } from '@nestjs/common';

export interface ThrottleOptions {
  limit: number;
  windowSeconds: number;
}

export const THROTTLE_KEY = 'throttle';

/**
 * Per-endpoint rate limit, enforced by ThrottleGuard (Redis-backed fixed
 * window). Applied to every endpoint the Milestone 0 threat model flags:
 * OTP request/verify, login, password reset, booking/payment creation,
 * refund requests, QR scans, uploads, support/review submission, admin APIs.
 */
export const Throttle = (options: ThrottleOptions) => SetMetadata(THROTTLE_KEY, options);
