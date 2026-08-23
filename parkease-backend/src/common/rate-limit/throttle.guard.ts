import { CanActivate, ExecutionContext, HttpException, HttpStatus, Injectable, Logger, OnModuleDestroy, OnModuleInit } from '@nestjs/common';
import { Reflector } from '@nestjs/core';
import { ConfigService } from '@nestjs/config';
import Redis from 'ioredis';
import { Request } from 'express';
import { AppConfig } from '../config/configuration';
import { PrismaService } from '../prisma/prisma.service';
import { THROTTLE_KEY, ThrottleOptions } from './throttle.decorator';
import { AuthenticatedUser } from '../decorators/current-user.decorator';

/**
 * Fixed-window rate limiter backed by Redis (INCR + EXPIRE), keyed by
 * route + (authenticated user id, falling back to IP for anonymous
 * requests like login/OTP). Blocked requests are audit-logged per
 * Milestone 0's "audit logs for blocked requests" requirement.
 */
@Injectable()
export class ThrottleGuard implements CanActivate, OnModuleInit, OnModuleDestroy {
  private readonly logger = new Logger(ThrottleGuard.name);
  private client!: Redis;

  constructor(
    private readonly reflector: Reflector,
    private readonly configService: ConfigService<AppConfig, true>,
    private readonly prisma: PrismaService,
  ) {}

  onModuleInit() {
    const url = this.configService.get('redis', { infer: true }).url;
    this.client = new Redis(url, { lazyConnect: true, maxRetriesPerRequest: 1 });
    this.client.on('error', (err) => this.logger.warn(`Redis error in ThrottleGuard: ${err.message}`));
  }

  async onModuleDestroy() {
    await this.client?.quit().catch(() => undefined);
  }

  async canActivate(context: ExecutionContext): Promise<boolean> {
    const options = this.reflector.get<ThrottleOptions>(THROTTLE_KEY, context.getHandler());
    if (!options) return true;

    const request = context.switchToHttp().getRequest<Request & { user?: AuthenticatedUser }>();
    const identity = request.user?.id ?? request.ip ?? 'unknown';
    const route = `${request.method}:${request.route?.path ?? request.path}`;
    const key = `throttle:${route}:${identity}`;

    let count: number;
    try {
      if (this.client.status !== 'ready' && this.client.status !== 'connecting') {
        await this.client.connect();
      }
      count = await this.client.incr(key);
      if (count === 1) {
        await this.client.expire(key, options.windowSeconds);
      }
    } catch (err) {
      // Fail OPEN on Redis unavailability rather than locking every user
      // out of auth if the cache is down — logged so it's visible in
      // monitoring, matching "graceful degradation" over hard failure for
      // a non-critical-path dependency.
      this.logger.warn(`Rate limiting unavailable, allowing request: ${(err as Error).message}`);
      return true;
    }

    if (count > options.limit) {
      await this.prisma.auditLog
        .create({
          data: {
            actor_id: request.user?.id,
            action: 'RATE_LIMIT_BLOCKED',
            target_type: 'route',
            target_id: route,
            ip_address: request.ip,
            correlation_id: (request as Request & { correlationId?: string }).correlationId,
          },
        })
        .catch(() => undefined);
      throw new HttpException('Too many requests. Please try again shortly.', HttpStatus.TOO_MANY_REQUESTS);
    }

    return true;
  }
}
