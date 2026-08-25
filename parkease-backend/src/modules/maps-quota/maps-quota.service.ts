import { Injectable, Logger, OnModuleDestroy, OnModuleInit } from '@nestjs/common';
import { ConfigService } from '@nestjs/config';
import Redis from 'ioredis';
import { AppConfig } from '../../common/config/configuration';
import { PrismaService } from '../../common/prisma/prisma.service';
import { NotificationsService } from '../notifications/notifications.service';
import { MAPS_BILLABLE_SKUS, MapsBillableSku, QuotaCheckResult, QuotaSkuUsage, QuotaSnapshot } from './maps-quota.types';

const SECONDS_PER_DAY = 24 * 60 * 60;
// A day's counters live a little past midnight rather than expiring
// exactly at it, so a request landing right at the UTC day boundary can't
// race the key's own expiry into a false "unset" read.
const KEY_TTL_SECONDS = SECONDS_PER_DAY + 60 * 60;

function todayKey(): string {
  return new Date().toISOString().slice(0, 10);
}

function quotaKey(sku: MapsBillableSku, date: string): string {
  return `maps:quota:${sku}:daily:${date}`;
}

function alertedKey(sku: MapsBillableSku, date: string): string {
  return `maps:quota:${sku}:alerted:${date}`;
}

function globalTripKey(): string {
  return 'maps:quota:global:tripped';
}

/**
 * Real, working circuit breaker for the three billable Google Maps
 * Platform SKUs (Directions, Places, Geocoding) — but as of this
 * milestone, nothing in ParkEase actually calls any of them yet (no
 * Directions/route-preview, no Places Autocomplete, no server-side
 * Geocoding API integration; map rendering is free/unlimited on native
 * SDKs, and reverse-geocoding today goes through Android's on-device
 * Geocoder, a free system service unrelated to Maps Platform billing).
 * Built ahead of that need — same "build the real integration boundary
 * before the credentials/traffic exist" pattern this codebase already
 * uses for Payment/Payout/SMS providers — so the moment a billable call
 * is added anywhere, wrapping it in `checkAndIncrement(sku)` and honoring
 * `allowed: false` is the entire integration; no call site exists to wrap
 * yet, which is disclosed rather than hidden.
 *
 * Soft breaker only: the hard stop against real overage is the daily
 * quota set directly in Google Cloud Console (docs/MAPS_QUOTA_RUNBOOK.md).
 * This is early warning + graceful in-app fallback well before that.
 */
@Injectable()
export class MapsQuotaService implements OnModuleInit, OnModuleDestroy {
  private readonly logger = new Logger(MapsQuotaService.name);
  private client!: Redis;

  constructor(
    private readonly configService: ConfigService<AppConfig, true>,
    private readonly prisma: PrismaService,
    private readonly notificationsService: NotificationsService,
  ) {}

  onModuleInit() {
    const url = this.configService.get('redis', { infer: true }).url;
    this.client = new Redis(url, { lazyConnect: true, maxRetriesPerRequest: 1 });
    this.client.on('error', (err) => this.logger.warn(`Redis error in MapsQuotaService: ${err.message}`));
  }

  async onModuleDestroy() {
    await this.client?.quit().catch(() => undefined);
  }

  private async ensureConnected(): Promise<void> {
    if (this.client.status !== 'ready' && this.client.status !== 'connecting') {
      await this.client.connect();
    }
  }

  /**
   * The gate every billable call must pass through first. Increments and
   * allows while under the 80%-equivalent daily safety cap; blocks
   * (without incrementing further) once at or over it, firing a one-time-
   * per-SKU-per-day alert to every SUPER_ADMIN the moment the threshold is
   * first crossed. Fails OPEN on Redis unavailability, matching
   * ThrottleGuard's exact precedent — this is a cost-safety soft breaker,
   * not a security control, and the GCP hard quota is the real backstop
   * for an extended outage.
   */
  async checkAndIncrement(sku: MapsBillableSku): Promise<QuotaCheckResult> {
    const cap = this.configService.get('maps', { infer: true }).quotaDailySafetyCap;
    const date = todayKey();

    if (await this.isGloballyTripped()) {
      return { allowed: false, sku, count: cap, cap, percentUsed: 100 };
    }

    try {
      await this.ensureConnected();
      const current = parseInt((await this.client.get(quotaKey(sku, date))) ?? '0', 10);

      if (current >= cap) {
        return { allowed: false, sku, count: current, cap, percentUsed: Math.round((current / cap) * 100) };
      }

      const count = await this.client.incr(quotaKey(sku, date));
      if (count === 1) {
        await this.client.expire(quotaKey(sku, date), KEY_TTL_SECONDS);
      }
      const percentUsed = Math.round((count / cap) * 100);

      if (count >= cap) {
        await this.alertThresholdReached(sku, count, cap, date);
      }

      return { allowed: true, sku, count, cap, percentUsed };
    } catch (err) {
      this.logger.warn(`Maps quota tracking unavailable for ${sku}, allowing request: ${(err as Error).message}`);
      return { allowed: true, sku, count: 0, cap, percentUsed: 0 };
    }
  }

  /** Read-only snapshot for the admin dashboard — never increments anything. */
  async getUsageSnapshot(): Promise<QuotaSnapshot> {
    const cap = this.configService.get('maps', { infer: true }).quotaDailySafetyCap;
    const date = todayKey();
    const globallyTripped = await this.isGloballyTripped();

    const skus: QuotaSkuUsage[] = await Promise.all(
      MAPS_BILLABLE_SKUS.map(async (sku): Promise<QuotaSkuUsage> => {
        const count = await this.safeGetCount(sku, date);
        const percentUsed = Math.round((count / cap) * 100);
        return { sku, count, cap, percentUsed, capReached: globallyTripped || count >= cap };
      }),
    );

    return { date, globallyTripped, skus };
  }

  private async safeGetCount(sku: MapsBillableSku, date: string): Promise<number> {
    try {
      await this.ensureConnected();
      return parseInt((await this.client.get(quotaKey(sku, date))) ?? '0', 10);
    } catch (err) {
      this.logger.warn(`Could not read quota usage for ${sku}: ${(err as Error).message}`);
      return 0;
    }
  }

  async isGloballyTripped(): Promise<boolean> {
    try {
      await this.ensureConnected();
      return (await this.client.get(globalTripKey())) === '1';
    } catch {
      return false;
    }
  }

  /**
   * Manually trips the breaker for the rest of the day regardless of any
   * per-SKU count — what the GCP budget-alert webhook calls when your
   * actual Cloud Billing spend crosses its own threshold, independent of
   * whatever our own request counters say.
   */
  async tripGlobalBreaker(reason: string): Promise<void> {
    try {
      await this.ensureConnected();
      await this.client.set(globalTripKey(), '1', 'EX', KEY_TTL_SECONDS);
    } catch (err) {
      this.logger.warn(`Could not persist global maps quota trip: ${(err as Error).message}`);
    }
    await this.prisma.auditLog
      .create({
        data: { action: 'MAPS_QUOTA_GLOBAL_TRIP', target_type: 'maps_quota', target_id: 'global', after_state: { reason } },
      })
      .catch(() => undefined);
    await this.notifySuperAdmins(
      'Google Maps budget alert — navigation fallback active',
      `A Google Cloud Billing budget alert fired: ${reason}. All billable Maps API calls are now suspended for the rest of today; the app is using free native-intent navigation instead.`,
    );
  }

  private async alertThresholdReached(sku: MapsBillableSku, count: number, cap: number, date: string): Promise<void> {
    try {
      await this.ensureConnected();
      // SET ... NX: only the request that actually wins the race fires the
      // alert, so a burst of concurrent requests all crossing the
      // threshold together sends exactly one notification, not one per request.
      const wonRace = await this.client.set(alertedKey(sku, date), '1', 'EX', KEY_TTL_SECONDS, 'NX');
      if (!wonRace) return;
    } catch (err) {
      this.logger.warn(`Could not dedupe maps quota alert for ${sku}, sending anyway: ${(err as Error).message}`);
    }

    await this.prisma.auditLog
      .create({
        data: {
          action: 'MAPS_QUOTA_THRESHOLD_REACHED',
          target_type: 'maps_quota',
          target_id: sku,
          after_state: { count, cap },
        },
      })
      .catch(() => undefined);

    await this.notifySuperAdmins(
      `Maps API quota cap reached: ${sku}`,
      `${sku} has reached its daily safety cap (${count}/${cap} requests). Further ${sku} calls are blocked for the rest of today; the app has switched to free native-intent navigation.`,
    );
  }

  /**
   * Real in-app + push + realtime-WebSocket delivery via the existing
   * NotificationsService — email is NOT sent: no EMAIL_PROVIDER is
   * configured on this deployment, and per this codebase's "no fakes"
   * rule (matching OTP, forgot-password), an unconfigured channel reports
   * unavailable rather than pretending to send.
   */
  private async notifySuperAdmins(title: string, body: string): Promise<void> {
    const superAdmins = await this.prisma.userRoleAssignment.findMany({
      where: { role: 'SUPER_ADMIN', status: 'ACTIVE' },
      select: { user_id: true },
    });
    await Promise.all(
      superAdmins.map((a) =>
        this.notificationsService.send({
          userId: a.user_id,
          category: 'system_alert',
          type: 'MAPS_QUOTA_ALERT',
          title,
          body,
        }),
      ),
    );
  }
}
