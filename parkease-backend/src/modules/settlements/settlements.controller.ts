import { Controller, Get, Param, Post, Query } from '@nestjs/common';
import { ApiOperation, ApiTags } from '@nestjs/swagger';
import { Roles } from '../../common/decorators/roles.decorator';
import { CurrentUser, AuthenticatedUser } from '../../common/decorators/current-user.decorator';
import { SettlementsService } from './settlements.service';
import { ListSettlementsQueryDto } from './dto/settlements.dto';

@ApiTags('settlements')
@Controller({ version: '1' })
export class SettlementsController {
  constructor(private readonly settlementsService: SettlementsService) {}

  @Post('owner/settlements')
  @Roles('OWNER')
  @ApiOperation({ summary: 'Request a payout of every currently AVAILABLE earning to your primary payout account' })
  request(@CurrentUser() owner: AuthenticatedUser) {
    return this.settlementsService.requestSettlement(owner.id);
  }

  @Get('owner/settlements')
  @Roles('OWNER')
  @ApiOperation({ summary: 'List your settlement requests' })
  listOwn(@CurrentUser() owner: AuthenticatedUser) {
    return this.settlementsService.listOwnSettlements(owner.id);
  }

  @Get('admin/settlements')
  @Roles('ADMIN')
  @ApiOperation({ summary: 'List settlements across all owners, optionally filtered by status' })
  listAll(@Query() query: ListSettlementsQueryDto) {
    return this.settlementsService.listAllSettlements(query.status);
  }

  @Post('admin/settlements/:settlementId/process')
  @Roles('ADMIN')
  @ApiOperation({ summary: 'Dispatch the real payout for a PENDING settlement' })
  process(@Param('settlementId') settlementId: string) {
    return this.settlementsService.processSettlement(settlementId);
  }
}
