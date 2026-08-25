import { Controller, Get, Headers, HttpCode, HttpStatus, Post, ServiceUnavailableException, UnauthorizedException } from '@nestjs/common';
import { ApiOperation, ApiTags } from '@nestjs/swagger';
import { ConfigService } from '@nestjs/config';
import { timingSafeEqual } from 'crypto';
import { AppConfig } from '../../common/config/configuration';
import { Public } from '../../common/guards/jwt-auth.guard';
import { Roles } from '../../common/decorators/roles.decorator';
import { Throttle } from '../../common/rate-limit/throttle.decorator';
import { MapsQuotaService } from './maps-quota.service';

@ApiTags('admin-maps-quota')
@Controller({ path: 'admin/maps-quota', version: '1' })
export class MapsQuotaController {
  constructor(
    private readonly mapsQuotaService: MapsQuotaService,
    private readonly configService: ConfigService<AppConfig, true>,
  ) {}

  @Get()
  @Roles('ADMIN', 'SUPER_ADMIN')
  @ApiOperation({ summary: "Today's Google Maps API quota usage per SKU, for the admin dashboard's live usage bars" })
  getUsage() {
    return this.mapsQuotaService.getUsageSnapshot();
  }

  /**
   * Receiver for a Google Cloud Billing Budget alert, forwarded here by a
   * Cloud Function/Pub/Sub push subscription you configure per
   * docs/MAPS_QUOTA_RUNBOOK.md — trips the breaker globally regardless of
   * what our own per-SKU counters say, since actual Cloud Billing spend is
   * the ground truth. @Public(): this is an external system calling in,
   * not an authenticated ParkEase user, so it's verified by shared secret
   * instead of a bearer token — same reasoning as the payment gateway's
   * webhook endpoint.
   */
  @Post('budget-alert-webhook')
  @Public()
  @Throttle({ limit: 20, windowSeconds: 60 })
  @HttpCode(HttpStatus.OK)
  @ApiOperation({ summary: 'GCP Billing Budget alert webhook — trips the global Maps quota breaker. Verified by shared secret, not a user token.' })
  async receiveBudgetAlert(@Headers('x-webhook-secret') providedSecret?: string) {
    const configuredSecret = this.configService.get('maps', { infer: true }).budgetWebhookSecret;
    if (!configuredSecret) {
      throw new ServiceUnavailableException('Budget alert webhook is not configured on this server.');
    }
    if (!providedSecret || !this.secretsMatch(providedSecret, configuredSecret)) {
      throw new UnauthorizedException('Invalid webhook secret.');
    }

    await this.mapsQuotaService.tripGlobalBreaker('Google Cloud Billing budget alert threshold reached');
    return { received: true };
  }

  private secretsMatch(provided: string, configured: string): boolean {
    const providedBuf = Buffer.from(provided);
    const configuredBuf = Buffer.from(configured);
    if (providedBuf.length !== configuredBuf.length) return false;
    return timingSafeEqual(providedBuf, configuredBuf);
  }
}
