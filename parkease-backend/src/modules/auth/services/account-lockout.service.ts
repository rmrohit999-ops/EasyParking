import { HttpException, HttpStatus, Injectable, Logger, OnModuleDestroy, OnModuleInit } from '@nestjs/common';
import { ConfigService } from '@nestjs/config';
import Redis from 'ioredis';
import { AppConfig } from '../../../common/config/configuration';

const MAX_FAILED_ATTEMPTS = 10;
const LOCKOUT_WINDOW_SECONDS = 15 * 60;

/**
 * Per-account login lockout, complementing (not replacing) ThrottleGuard's
 * per-IP rate limit on POST /auth/login. The IP throttle alone is
 * bypassable by rotating source IPs; this counts failed attempts against
 * the target account itself regardless of where they came from. Same
 * lazy-connect/fail-open Redis pattern as ThrottleGuard and
 * RedisHealthService — a cache outage degrades to "no lockout" rather than
 * locking every user out of login.
 */
@Injectable()
export class AccountLockoutService implements OnModuleInit, OnModuleDestroy {
  private readonly logger = new Logger(AccountLockoutService.name);
  private client!: Redis;

  constructor(private readonly configService: ConfigService<AppConfig, true>) {}

  onModuleInit() {
    const url = this.configService.get('redis', { infer: true }).url;
    this.client = new Redis(url, { lazyConnect: true, maxRetriesPerRequest: 1 });
    this.client.on('error', (err) => this.logger.warn(`Redis error in AccountLockoutService: ${err.message}`));
  }

  async onModuleDestroy() {
    await this.client?.quit().catch(() => undefined);
  }

  private key(identifier: string): string {
    return `login-lockout:${identifier}`;
  }

  private async ensureConnected(): Promise<boolean> {
    try {
      if (this.client.status !== 'ready' && this.client.status !== 'connecting') {
        await this.client.connect();
      }
      return true;
    } catch (err) {
      this.logger.warn(`Account lockout check unavailable: ${(err as Error).message}`);
      return false;
    }
  }

  /** Throws 429 if the account has too many recent failed attempts. */
  async assertNotLocked(identifier: string): Promise<void> {
    if (!(await this.ensureConnected())) return;
    const count = await this.client.get(this.key(identifier)).catch(() => null);
    if (count && Number(count) >= MAX_FAILED_ATTEMPTS) {
      throw new HttpException(
        'Too many failed sign-in attempts for this account. Please try again in a few minutes or reset your password.',
        HttpStatus.TOO_MANY_REQUESTS,
      );
    }
  }

  async recordFailedAttempt(identifier: string): Promise<void> {
    if (!(await this.ensureConnected())) return;
    const key = this.key(identifier);
    const count = await this.client.incr(key).catch(() => null);
    if (count === 1) {
      await this.client.expire(key, LOCKOUT_WINDOW_SECONDS).catch(() => undefined);
    }
  }

  async clearFailedAttempts(identifier: string): Promise<void> {
    if (!(await this.ensureConnected())) return;
    await this.client.del(this.key(identifier)).catch(() => undefined);
  }
}
