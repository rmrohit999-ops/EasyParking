import { Controller, Get, Param, Patch, Query, Body } from '@nestjs/common';
import { ApiOperation, ApiTags } from '@nestjs/swagger';
import { Roles } from '../../common/decorators/roles.decorator';
import { CheckOwnership } from '../../common/guards/resource-ownership.guard';
import { AvailabilityService } from './availability.service';
import { GetAvailabilityQueryDto, SetBlockedCountDto } from './dto/availability.dto';

@ApiTags('availability')
@Controller({ version: '1' })
export class AvailabilityController {
  constructor(private readonly availabilityService: AvailabilityService) {}

  @Get('parking/:listingId/availability')
  @ApiOperation({ summary: 'Live per-category availability for a listing' })
  getAvailability(@Param('listingId') listingId: string, @Query() query: GetAvailabilityQueryDto) {
    return this.availabilityService.getListingAvailability(listingId, query.category);
  }

  @Patch('owner/parking/:listingId/sections/:sectionId/availability')
  @Roles('OWNER')
  @CheckOwnership('ParkingListingOwnershipResolver', 'listingId')
  @ApiOperation({ summary: 'Manually block N spaces in a section (maintenance, private use, etc.)' })
  setBlockedCount(
    @Param('listingId') listingId: string,
    @Param('sectionId') sectionId: string,
    @Body() dto: SetBlockedCountDto,
  ) {
    return this.availabilityService.setBlockedCount(listingId, sectionId, dto.blockedCount);
  }
}
