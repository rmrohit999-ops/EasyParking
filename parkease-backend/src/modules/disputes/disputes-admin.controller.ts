import { Body, Controller, Get, Param, Post, Query } from '@nestjs/common';
import { ApiOperation, ApiTags } from '@nestjs/swagger';
import { Roles } from '../../common/decorators/roles.decorator';
import { CurrentUser, AuthenticatedUser } from '../../common/decorators/current-user.decorator';
import { DisputesService } from './disputes.service';
import { AddDisputeEvidenceDto, ListDisputesQueryDto, ResolveDisputeDto } from './dto/disputes.dto';

@ApiTags('admin-disputes')
@Roles('ADMIN')
@Controller({ path: 'admin/disputes', version: '1' })
export class DisputesAdminController {
  constructor(private readonly disputesService: DisputesService) {}

  @Get()
  @ApiOperation({ summary: 'List disputes across all users, optionally filtered by status' })
  listAll(@Query() query: ListDisputesQueryDto) {
    return this.disputesService.listAll(query.status);
  }

  @Get(':disputeId')
  @ApiOperation({ summary: 'Get one dispute with all evidence, including admin-only' })
  getOne(@CurrentUser() admin: AuthenticatedUser & { roles: string[] }, @Param('disputeId') disputeId: string) {
    return this.disputesService.getDispute(admin, disputeId);
  }

  @Post(':disputeId/evidence')
  @ApiOperation({ summary: 'Add admin-only or shared evidence to a dispute' })
  addEvidence(
    @CurrentUser() admin: AuthenticatedUser & { roles: string[] },
    @Param('disputeId') disputeId: string,
    @Body() dto: AddDisputeEvidenceDto,
  ) {
    return this.disputesService.addEvidence(admin, disputeId, dto);
  }

  @Post(':disputeId/resolve')
  @ApiOperation({ summary: 'Resolve or reject a dispute, optionally issuing a refund' })
  resolve(@CurrentUser() admin: AuthenticatedUser, @Param('disputeId') disputeId: string, @Body() dto: ResolveDisputeDto) {
    return this.disputesService.resolveDispute(admin.id, disputeId, dto);
  }
}
