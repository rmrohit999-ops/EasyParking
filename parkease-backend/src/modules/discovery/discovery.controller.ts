import { Controller, DefaultValuePipe, Get, Param, ParseIntPipe, Query } from '@nestjs/common';
import { ApiOperation, ApiTags } from '@nestjs/swagger';
import { Roles } from '../../common/decorators/roles.decorator';
import { CurrentUser, AuthenticatedUser } from '../../common/decorators/current-user.decorator';
import { DiscoveryService } from './discovery.service';
import { SearchParkingQueryDto } from './dto/discovery.dto';

@ApiTags('discovery')
@Controller({ version: '1' })
export class DiscoveryController {
  constructor(private readonly discoveryService: DiscoveryService) {}

  @Get('search/parking')
  @Roles('DRIVER')
  @ApiOperation({ summary: 'Search approved, active parking near a point, filtered to a compatible vehicle/category' })
  search(@CurrentUser() user: AuthenticatedUser, @Query() query: SearchParkingQueryDto) {
    return this.discoveryService.search(user.id, query);
  }

  @Get('parking/:listingId')
  @ApiOperation({ summary: 'Public detail view of an approved listing' })
  getListing(@Param('listingId') listingId: string) {
    return this.discoveryService.getListingDetail(listingId);
  }

  @Get('parking/:listingId/sections')
  @ApiOperation({ summary: 'Approved sections for a listing, with live per-category availability' })
  listSections(@Param('listingId') listingId: string) {
    return this.discoveryService.listSections(listingId);
  }

  @Get('parking/:listingId/reviews')
  @ApiOperation({ summary: 'Real driver reviews for a listing — lets a driver read reviews before booking' })
  listReviews(
    @Param('listingId') listingId: string,
    @Query('page', new DefaultValuePipe(1), ParseIntPipe) page: number,
    @Query('pageSize', new DefaultValuePipe(20), ParseIntPipe) pageSize: number,
  ) {
    return this.discoveryService.listReviews(listingId, page, pageSize);
  }
}
