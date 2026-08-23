import { Controller, Delete, Get, HttpCode, HttpStatus, Param, Post } from '@nestjs/common';
import { ApiOperation, ApiTags } from '@nestjs/swagger';
import { Roles } from '../../common/decorators/roles.decorator';
import { CurrentUser, AuthenticatedUser } from '../../common/decorators/current-user.decorator';
import { DiscoveryService } from './discovery.service';

@ApiTags('favorites')
@Roles('DRIVER')
@Controller({ path: 'favorites', version: '1' })
export class FavoritesController {
  constructor(private readonly discoveryService: DiscoveryService) {}

  @Get()
  @ApiOperation({ summary: "List the current driver's favorited listings" })
  list(@CurrentUser() user: AuthenticatedUser) {
    return this.discoveryService.listFavorites(user.id);
  }

  @Post(':listingId')
  @HttpCode(HttpStatus.NO_CONTENT)
  @ApiOperation({ summary: 'Favorite a listing (idempotent)' })
  add(@CurrentUser() user: AuthenticatedUser, @Param('listingId') listingId: string) {
    return this.discoveryService.addFavorite(user.id, listingId);
  }

  @Delete(':listingId')
  @HttpCode(HttpStatus.NO_CONTENT)
  @ApiOperation({ summary: 'Unfavorite a listing (idempotent)' })
  remove(@CurrentUser() user: AuthenticatedUser, @Param('listingId') listingId: string) {
    return this.discoveryService.removeFavorite(user.id, listingId);
  }
}
