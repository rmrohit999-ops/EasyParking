import { Body, Controller, Get, Param, Post } from '@nestjs/common';
import { ApiOperation, ApiTags } from '@nestjs/swagger';
import { Roles } from '../../common/decorators/roles.decorator';
import { CurrentUser, AuthenticatedUser } from '../../common/decorators/current-user.decorator';
import { ParkingService } from './parking.service';
import { AdminRejectDto, AdminRequestMoreInfoDto } from './dto/parking.dto';

/**
 * Approval-workflow slice of admin functionality, built in Milestone 4
 * alongside listing/section creation since a listing is meaningless without
 * a reviewer able to approve it. The full admin dashboard (fraud signals,
 * user management, config, disputes) is Milestone 10 — this controller
 * only ever grows the review queue it already owns, not into those areas.
 */
@ApiTags('admin-parking')
@Roles('ADMIN')
@Controller({ path: 'admin/parking', version: '1' })
export class ParkingAdminController {
  constructor(private readonly parkingService: ParkingService) {}

  @Get('listings/pending')
  @ApiOperation({ summary: 'List listings awaiting or needing more info for approval' })
  listPendingListings() {
    return this.parkingService.listPendingListings();
  }

  @Post('listings/:listingId/approve')
  @ApiOperation({ summary: 'Approve a listing' })
  approveListing(@CurrentUser() admin: AuthenticatedUser, @Param('listingId') listingId: string) {
    return this.parkingService.approveListing(admin.id, listingId);
  }

  @Post('listings/:listingId/reject')
  @ApiOperation({ summary: 'Reject a listing with a reason' })
  rejectListing(
    @CurrentUser() admin: AuthenticatedUser,
    @Param('listingId') listingId: string,
    @Body() dto: AdminRejectDto,
  ) {
    return this.parkingService.rejectListing(admin.id, listingId, dto.reason);
  }

  @Post('listings/:listingId/request-more-info')
  @ApiOperation({ summary: 'Ask the owner for more information before approving' })
  requestMoreInfo(
    @CurrentUser() admin: AuthenticatedUser,
    @Param('listingId') listingId: string,
    @Body() dto: AdminRequestMoreInfoDto,
  ) {
    return this.parkingService.requestMoreInfoListing(admin.id, listingId, dto.message);
  }

  @Get('sections/pending')
  @ApiOperation({ summary: 'List sections awaiting or needing more info for approval' })
  listPendingSections() {
    return this.parkingService.listPendingSections();
  }

  @Post('sections/:sectionId/approve')
  @ApiOperation({ summary: 'Approve a section' })
  approveSection(@CurrentUser() admin: AuthenticatedUser, @Param('sectionId') sectionId: string) {
    return this.parkingService.approveSection(admin.id, sectionId);
  }

  @Post('sections/:sectionId/reject')
  @ApiOperation({ summary: 'Reject a section with a reason' })
  rejectSection(
    @CurrentUser() admin: AuthenticatedUser,
    @Param('sectionId') sectionId: string,
    @Body() dto: AdminRejectDto,
  ) {
    return this.parkingService.rejectSection(admin.id, sectionId, dto.reason);
  }
}
