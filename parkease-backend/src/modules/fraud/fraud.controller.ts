import { Body, Controller, Get, Param, Patch, Post, Query } from '@nestjs/common';
import { ApiOperation, ApiTags } from '@nestjs/swagger';
import { Roles } from '../../common/decorators/roles.decorator';
import { CurrentUser, AuthenticatedUser } from '../../common/decorators/current-user.decorator';
import { FraudService } from './fraud.service';
import { ListFraudAlertsQueryDto, ReviewFraudAlertDto } from './dto/fraud.dto';

@ApiTags('admin-fraud')
@Roles('ADMIN')
@Controller({ path: 'admin/fraud', version: '1' })
export class FraudController {
  constructor(private readonly fraudService: FraudService) {}

  @Post('scan/user/:userId')
  @ApiOperation({ summary: "Compute a fresh behavioral risk signal for a user's booking history" })
  scanUser(@Param('userId') userId: string) {
    return this.fraudService.scanUser(userId);
  }

  @Post('scan/booking/:bookingId')
  @ApiOperation({ summary: 'Compute a mismatch-pattern risk signal for a single booking' })
  scanBooking(@Param('bookingId') bookingId: string) {
    return this.fraudService.scanBooking(bookingId);
  }

  @Post('scan/listing/:listingId')
  @ApiOperation({ summary: 'Not implemented in this build — see FraudService.scanListing' })
  scanListing(@Param('listingId') listingId: string) {
    return this.fraudService.scanListing(listingId);
  }

  @Get('alerts')
  @ApiOperation({ summary: 'List fraud alerts, optionally filtered by status' })
  listAlerts(@Query() query: ListFraudAlertsQueryDto) {
    return this.fraudService.listAlerts(query.status);
  }

  @Patch('alerts/:alertId')
  @ApiOperation({ summary: 'Review a fraud alert (dismiss, mark under review, or record action taken)' })
  reviewAlert(@CurrentUser() admin: AuthenticatedUser, @Param('alertId') alertId: string, @Body() dto: ReviewFraudAlertDto) {
    return this.fraudService.reviewAlert(admin.id, alertId, dto);
  }
}
