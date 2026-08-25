import { INestApplicationContext, Logger } from '@nestjs/common';
import { ConfigService } from '@nestjs/config';
import { IoAdapter } from '@nestjs/platform-socket.io';
import { createAdapter } from '@socket.io/redis-adapter';
import { ServerOptions } from 'socket.io';
import Redis from 'ioredis';
import { AppConfig } from '../config/configuration';

/**
 * Fans WebSocket events out across every backend instance via Redis pub/sub
 * — without this, `RealtimeGateway.emitToUser()` on instance A would never
 * reach a client whose socket happens to be connected to instance B, which
 * is otherwise inevitable once this deploys with more than one replica.
 * Two separate ioredis connections are required (one dedicated to
 * publishing, one dedicated to subscribing) — the same client can't do
 * both at once in the subscribe mode socket.io-redis-adapter needs.
 */
export class RedisIoAdapter extends IoAdapter {
  private readonly logger = new Logger(RedisIoAdapter.name);
  private adapterConstructor?: ReturnType<typeof createAdapter>;

  constructor(private readonly app: INestApplicationContext) {
    super(app);
  }

  async connectToRedis(): Promise<void> {
    const configService = this.app.get(ConfigService<AppConfig, true>);
    const url = configService.get('redis', { infer: true }).url;

    const pubClient = new Redis(url, { lazyConnect: true, maxRetriesPerRequest: 2 });
    const subClient = pubClient.duplicate();
    pubClient.on('error', (err) => this.logger.warn(`Redis pub client error: ${err.message}`));
    subClient.on('error', (err) => this.logger.warn(`Redis sub client error: ${err.message}`));

    await Promise.all([pubClient.connect(), subClient.connect()]);
    this.adapterConstructor = createAdapter(pubClient, subClient);
  }

  createIOServer(port: number, options?: ServerOptions): unknown {
    const server = super.createIOServer(port, options);
    if (this.adapterConstructor) {
      server.adapter(this.adapterConstructor);
    } else {
      this.logger.warn('Redis adapter not connected — falling back to single-instance in-memory Socket.IO adapter.');
    }
    return server;
  }
}
