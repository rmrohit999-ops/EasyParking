import { Body, Controller, Get, Param, Patch, Post } from '@nestjs/common';
import { ApiOperation, ApiTags } from '@nestjs/swagger';
import { Roles } from '../../common/decorators/roles.decorator';
import { CurrentUser, AuthenticatedUser } from '../../common/decorators/current-user.decorator';
import { ConfigService } from './config.service';
import {
  CreateCancellationPolicyDto,
  CreateCommissionPolicyDto,
  CreateFeatureFlagDto,
  CreateTaxPolicyDto,
  UpdateFeatureFlagDto,
} from './dto/config.dto';

// Reads (GET) stay available to any ADMIN — the spec's own permission
// matrix only lists "configure commissions, fees and taxes" under
// SUPER_ADMIN, not plain ADMIN, so every write here is SUPER_ADMIN-only,
// overriding the controller-level @Roles('ADMIN','SUPER_ADMIN') per route.
@ApiTags('admin-config')
@Roles('ADMIN', 'SUPER_ADMIN')
@Controller({ path: 'admin/config', version: '1' })
export class ConfigController {
  constructor(private readonly configService: ConfigService) {}

  @Post('commission-policies')
  @Roles('SUPER_ADMIN')
  @ApiOperation({ summary: 'Create a commission policy (supersedes, never edits in place) — SUPER_ADMIN only' })
  createCommissionPolicy(@CurrentUser() admin: AuthenticatedUser, @Body() dto: CreateCommissionPolicyDto) {
    return this.configService.createCommissionPolicy(admin.id, dto);
  }

  @Get('commission-policies')
  @ApiOperation({ summary: 'List commission policies, most recent first' })
  listCommissionPolicies() {
    return this.configService.listCommissionPolicies();
  }

  @Post('tax-policies')
  @Roles('SUPER_ADMIN')
  @ApiOperation({ summary: 'Create a tax policy (supersedes, never edits in place) — SUPER_ADMIN only' })
  createTaxPolicy(@CurrentUser() admin: AuthenticatedUser, @Body() dto: CreateTaxPolicyDto) {
    return this.configService.createTaxPolicy(admin.id, dto);
  }

  @Get('tax-policies')
  @ApiOperation({ summary: 'List tax policies, most recent first' })
  listTaxPolicies() {
    return this.configService.listTaxPolicies();
  }

  @Post('cancellation-policies')
  @Roles('SUPER_ADMIN')
  @ApiOperation({ summary: 'Create a cancellation refund-tier policy (supersedes, never edits in place) — SUPER_ADMIN only' })
  createCancellationPolicy(@CurrentUser() admin: AuthenticatedUser, @Body() dto: CreateCancellationPolicyDto) {
    return this.configService.createCancellationPolicy(admin.id, dto);
  }

  @Get('cancellation-policies')
  @ApiOperation({ summary: 'List cancellation policies, most recent first' })
  listCancellationPolicies() {
    return this.configService.listCancellationPolicies();
  }

  @Post('feature-flags')
  @Roles('SUPER_ADMIN')
  @ApiOperation({ summary: 'Create a feature flag — SUPER_ADMIN only' })
  createFeatureFlag(@CurrentUser() admin: AuthenticatedUser, @Body() dto: CreateFeatureFlagDto) {
    return this.configService.createFeatureFlag(admin.id, dto);
  }

  @Get('feature-flags')
  @ApiOperation({ summary: 'List feature flags' })
  listFeatureFlags() {
    return this.configService.listFeatureFlags();
  }

  @Patch('feature-flags/:key')
  @Roles('SUPER_ADMIN')
  @ApiOperation({ summary: 'Toggle/update a feature flag — SUPER_ADMIN only' })
  updateFeatureFlag(@CurrentUser() admin: AuthenticatedUser, @Param('key') key: string, @Body() dto: UpdateFeatureFlagDto) {
    return this.configService.updateFeatureFlag(admin.id, key, dto);
  }
}
