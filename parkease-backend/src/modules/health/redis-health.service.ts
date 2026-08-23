import { Injectable, Logger, OnModuleDestroy, OnModuleInit } from '@nestjs/common';
import { ConfigService } from '@nestjs/config';
import Redis from 'ioredis';
import { AppConfig } from '../../common/config/configuration';

@Injectable()
export class RedisHealthService implements OnModuleInit, OnModuleDestroy {
  private readonly logger = new Logger(RedisHealthService.name);
  private client!: Redis;

  constructor(private readonly configService: ConfigService<AppConfig, true>) {}

  onModuleInit() {
    const url = this.configService.get('redis', { infer: true }).url;
    this.client = new Redis(url, { lazyConnect: true, maxRetriesPerRequest: 1 });
    this.client.on('error', (err) => this.logger.warn(`Redis connection error: ${err.message}`));
  }

  async onModuleDestroy() {
    await this.client?.quit().catch(() => undefined);
  }

  async isHealthy(): Promise<boolean> {
    try {
      if (this.client.status !== 'ready' && this.client.status !== 'connecting') {
        await this.client.connect();
      }
      const pong = await this.client.ping();
      return pong === 'PONG';
    } catch {
      return false;
    }
  }
}
