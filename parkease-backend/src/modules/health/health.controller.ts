import { Controller, Get, HttpStatus, Res } from '@nestjs/common';
import { ApiOperation, ApiTags } from '@nestjs/swagger';
import { Response } from 'express';
import { PrismaService } from '../../common/prisma/prisma.service';
import { Public } from '../../common/guards/jwt-auth.guard';
import { RedisHealthService } from './redis-health.service';

interface HealthCheckResult {
  status: 'ok' | 'degraded';
  checks: {
    database: 'ok' | 'error';
    redis: 'ok' | 'error';
  };
  timestamp: string;
}

/**
 * Real liveness/readiness probe. Deliberately unauthenticated (used by
 * load balancers / uptime monitors) and deliberately not a hardcoded 200 —
 * it round-trips both Postgres and Redis so a broken dependency is visible
 * before it causes confusing failures elsewhere.
 */
@ApiTags('health')
@Controller('health')
export class HealthController {
  constructor(
    private readonly prisma: PrismaService,
    private readonly redisHealth: RedisHealthService,
  ) {}

  @Public()
  @Get()
  @ApiOperation({ summary: 'Liveness/readiness probe with real DB and Redis connectivity checks' })
  async check(@Res() res: Response) {
    const [dbOk, redisOk] = await Promise.all([this.prisma.isHealthy(), this.redisHealth.isHealthy()]);

    const result: HealthCheckResult = {
      status: dbOk && redisOk ? 'ok' : 'degraded',
      checks: {
        database: dbOk ? 'ok' : 'error',
        redis: redisOk ? 'ok' : 'error',
      },
      timestamp: new Date().toISOString(),
    };

    res.status(result.status === 'ok' ? HttpStatus.OK : HttpStatus.SERVICE_UNAVAILABLE).json(result);
  }
}
