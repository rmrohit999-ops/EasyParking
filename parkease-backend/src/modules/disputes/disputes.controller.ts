import { Body, Controller, Get, Param, Post } from '@nestjs/common';
import { ApiOperation, ApiTags } from '@nestjs/swagger';
import { CurrentUser, AuthenticatedUser } from '../../common/decorators/current-user.decorator';
import { DisputesService } from './disputes.service';
import { AddDisputeEvidenceDto, FileDisputeDto } from './dto/disputes.dto';

@ApiTags('disputes')
@Controller({ path: 'disputes', version: '1' })
export class DisputesController {
  constructor(private readonly disputesService: DisputesService) {}

  @Post()
  @ApiOperation({ summary: 'File a dispute over a booking' })
  file(@CurrentUser() user: AuthenticatedUser & { roles: string[] }, @Body() dto: FileDisputeDto) {
    return this.disputesService.fileDispute(user, dto);
  }

  @Get()
  @ApiOperation({ summary: 'List disputes you filed' })
  listMine(@CurrentUser() user: AuthenticatedUser) {
    return this.disputesService.listMine(user);
  }

  @Get(':disputeId')
  @ApiOperation({ summary: 'Get one dispute with its (visible-to-you) evidence' })
  getOne(@CurrentUser() user: AuthenticatedUser & { roles: string[] }, @Param('disputeId') disputeId: string) {
    return this.disputesService.getDispute(user, disputeId);
  }

  @Post(':disputeId/evidence')
  @ApiOperation({ summary: 'Add evidence to a dispute' })
  addEvidence(
    @CurrentUser() user: AuthenticatedUser & { roles: string[] },
    @Param('disputeId') disputeId: string,
    @Body() dto: AddDisputeEvidenceDto,
  ) {
    return this.disputesService.addEvidence(user, disputeId, dto);
  }
}
