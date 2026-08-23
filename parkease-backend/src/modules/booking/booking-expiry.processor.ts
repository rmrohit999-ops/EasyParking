import { Injectable, Logger, OnModuleDestroy, OnModuleInit } from '@nestjs/common';
import { ConfigService } from '@nestjs/config';
import { Job, Worker } from 'bullmq';
import IORedis from 'ioredis';
import { AppConfig } from '../../common/config/configuration';
import { BOOKING_HOLD_EXPIRY_QUEUE_NAME, ExpireHoldJobData } from './booking-expiry.queue';
import { BookingService } from './booking.service';

/**
 * Consumer side of the proactive hold-expiry queue. Depends on
 * BookingService (to call the same `expireHoldIfDue` the lazy read path
 * uses — one implementation, two triggers); nothing depends on this class,
 * so pairing it with BookingExpiryProducer (which BookingService depends
 * on to schedule jobs) never creates a circular provider graph.
 */
@Injectable()
export class BookingExpiryProcessor implements OnModuleInit, OnModuleDestroy {
  private readonly logger = new Logger(BookingExpiryProcessor.name);
  private worker: Worker<ExpireHoldJobData> | null = null;
  private connection: IORedis | null = null;

  constructor(
    private readonly configService: ConfigService<AppConfig, true>,
    private readonly bookingService: BookingService,
  ) {}

  onModuleInit() {
    try {
      const url = this.configService.get('redis', { infer: true }).url;
      this.connection = new IORedis(url, { maxRetriesPerRequest: null, lazyConnect: true });
      this.connection.on('error', (err) => this.logger.warn(`Redis error in BookingExpiryProcessor: ${err.message}`));

      this.worker = new Worker<ExpireHoldJobData>(
        BOOKING_HOLD_EXPIRY_QUEUE_NAME,
        async (job: Job<ExpireHoldJobData>) => this.bookingService.expireHoldIfDue(job.data.holdId),
        { connection: this.connection },
      );
      this.worker.on('failed', (job, err) => {
        this.logger.warn(`Hold expiry job failed for ${job?.data?.holdId}: ${err.message}`);
      });
    } catch (err) {
      this.logger.warn(`BookingExpiryProcessor unavailable — expired holds rely on lazy checks only: ${(err as Error).message}`);
    }
  }

  async onModuleDestroy() {
    await this.worker?.close().catch(() => undefined);
    await this.connection?.quit().catch(() => undefined);
  }
}
