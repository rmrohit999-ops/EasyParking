import { Body, Controller, Get, Param, Post } from '@nestjs/common';
import { ApiOperation, ApiTags } from '@nestjs/swagger';
import { Roles } from '../../common/decorators/roles.decorator';
import { CurrentUser, AuthenticatedUser } from '../../common/decorators/current-user.decorator';
import { QrService } from './qr.service';
import { CashCollectDto } from './dto/qr.dto';

/**
 * Lives under `/bookings` (matching §7.9 and §7.7's literal paths) rather
 * than `/qr`, even though QrService owns it — the URL namespace here is
 * booking-centric ("this booking's pass," "collect cash for this
 * booking"), not QR-module-centric.
 */
@ApiTags('qr')
@Controller({ path: 'bookings', version: '1' })
export class QrPassController {
  constructor(private readonly qrService: QrService) {}

  @Get(':bookingId/pass')
  @Roles('DRIVER')
  @ApiOperation({ summary: "Get (issuing if needed) the current driver's QR pass for a booking" })
  getPass(@CurrentUser() user: AuthenticatedUser, @Param('bookingId') bookingId: string) {
    return this.qrService.getOrIssuePass(user.id, bookingId);
  }

  @Post(':bookingId/cash-collect')
  @Roles('OWNER', 'ATTENDANT')
  @ApiOperation({ summary: 'Confirm cash payment was collected for a booking, moving it to CONFIRMED' })
  cashCollect(
    @CurrentUser() user: AuthenticatedUser & { roles: string[] },
    @Param('bookingId') bookingId: string,
    @Body() dto: CashCollectDto,
  ) {
    return this.qrService.cashCollect(user, bookingId, dto);
  }
}
