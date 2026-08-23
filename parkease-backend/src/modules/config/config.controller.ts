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

@ApiTags('admin-config')
@Roles('ADMIN')
@Controller({ path: 'admin/config', version: '1' })
export class ConfigController {
  constructor(private readonly configService: ConfigService) {}

  @Post('commission-policies')
  @ApiOperation({ summary: 'Create a commission policy (supersedes, never edits in place)' })
  createCommissionPolicy(@CurrentUser() admin: AuthenticatedUser, @Body() dto: CreateCommissionPolicyDto) {
    return this.configService.createCommissionPolicy(admin.id, dto);
  }

  @Get('commission-policies')
  @ApiOperation({ summary: 'List commission policies, most recent first' })
  listCommissionPolicies() {
    return this.configService.listCommissionPolicies();
  }

  @Post('tax-policies')
  @ApiOperation({ summary: 'Create a tax policy (supersedes, never edits in place)' })
  createTaxPolicy(@CurrentUser() admin: AuthenticatedUser, @Body() dto: CreateTaxPolicyDto) {
    return this.configService.createTaxPolicy(admin.id, dto);
  }

  @Get('tax-policies')
  @ApiOperation({ summary: 'List tax policies, most recent first' })
  listTaxPolicies() {
    return this.configService.listTaxPolicies();
  }

  @Post('cancellation-policies')
  @ApiOperation({ summary: 'Create a cancellation refund-tier policy (supersedes, never edits in place)' })
  createCancellationPolicy(@CurrentUser() admin: AuthenticatedUser, @Body() dto: CreateCancellationPolicyDto) {
    return this.configService.createCancellationPolicy(admin.id, dto);
  }

  @Get('cancellation-policies')
  @ApiOperation({ summary: 'List cancellation policies, most recent first' })
  listCancellationPolicies() {
    return this.configService.listCancellationPolicies();
  }

  @Post('feature-flags')
  @ApiOperation({ summary: 'Create a feature flag' })
  createFeatureFlag(@CurrentUser() admin: AuthenticatedUser, @Body() dto: CreateFeatureFlagDto) {
    return this.configService.createFeatureFlag(admin.id, dto);
  }

  @Get('feature-flags')
  @ApiOperation({ summary: 'List feature flags' })
  listFeatureFlags() {
    return this.configService.listFeatureFlags();
  }

  @Patch('feature-flags/:key')
  @ApiOperation({ summary: 'Toggle/update a feature flag' })
  updateFeatureFlag(@CurrentUser() admin: AuthenticatedUser, @Param('key') key: string, @Body() dto: UpdateFeatureFlagDto) {
    return this.configService.updateFeatureFlag(admin.id, key, dto);
  }
}
