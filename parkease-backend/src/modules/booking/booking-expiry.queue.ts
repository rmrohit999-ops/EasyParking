import { Injectable, Logger, OnModuleDestroy, OnModuleInit } from '@nestjs/common';
import { ConfigService } from '@nestjs/config';
import { Queue } from 'bullmq';
import IORedis from 'ioredis';
import { AppConfig } from '../../common/config/configuration';

export const BOOKING_HOLD_EXPIRY_QUEUE_NAME = 'booking-hold-expiry';

export interface ExpireHoldJobData {
  holdId: string;
}

/**
 * Producer side only — enqueues a delayed "expire this hold" job. Split
 * from the consumer (BookingExpiryProcessor) so BookingService can depend
 * on this without a circular dependency: BookingService -> Producer (to
 * schedule), Processor -> BookingService (to actually expire). Nothing
 * depends on Processor, so the graph has no cycle.
 *
 * This is a best-effort proactive layer only — see BookingExpiryProcessor
 * and BookingService.expireHoldIfDue for why a Redis/queue outage never
 * breaks correctness, only promptness.
 */
@Injectable()
export class BookingExpiryProducer implements OnModuleInit, OnModuleDestroy {
  private readonly logger = new Logger(BookingExpiryProducer.name);
  private queue: Queue<ExpireHoldJobData> | null = null;
  private connection: IORedis | null = null;

  constructor(private readonly configService: ConfigService<AppConfig, true>) {}

  onModuleInit() {
    try {
      const url = this.configService.get('redis', { infer: true }).url;
      this.connection = new IORedis(url, { maxRetriesPerRequest: null, lazyConnect: true });
      this.connection.on('error', (err) => this.logger.warn(`Redis error in BookingExpiryProducer: ${err.message}`));
      this.queue = new Queue<ExpireHoldJobData>(BOOKING_HOLD_EXPIRY_QUEUE_NAME, { connection: this.connection });
    } catch (err) {
      this.logger.warn(`BookingExpiryProducer unavailable, falling back to lazy expiry only: ${(err as Error).message}`);
    }
  }

  async scheduleExpiry(holdId: string, delayMs: number): Promise<void> {
    if (!this.queue) return; // degraded — the lazy check in BookingService remains authoritative
    try {
      await this.queue.add(
        'expire-hold',
        { holdId },
        { delay: Math.max(0, delayMs), jobId: holdId, removeOnComplete: true, removeOnFail: true },
      );
    } catch (err) {
      this.logger.warn(`Could not schedule proactive expiry for hold ${holdId}: ${(err as Error).message}`);
    }
  }

  async onModuleDestroy() {
    await this.queue?.close().catch(() => undefined);
    await this.connection?.quit().catch(() => undefined);
  }
}
