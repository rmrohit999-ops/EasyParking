import { Body, Controller, Param, Post } from '@nestjs/common';
import { ApiOperation, ApiTags } from '@nestjs/swagger';
import { Roles } from '../../common/decorators/roles.decorator';
import { CurrentUser, AuthenticatedUser } from '../../common/decorators/current-user.decorator';
import { QrService } from './qr.service';
import { CheckInDto, CheckOutDto, ReportMismatchDto, ResolveMismatchDto, ScanQrDto } from './dto/qr.dto';

/**
 * §7.9. `@Roles` here is deliberately broad (ATTENDANT/OWNER/ADMIN) — the
 * real authorization (this specific attendant is actually assigned to
 * this specific parking, or this specific owner actually owns it) is a
 * service-layer check in QrService.mustAuthorizeOperator, not something a
 * static role list can express (Milestone 0 §4's four-layer enforcement
 * pattern: role is necessary but not sufficient).
 */
@ApiTags('qr')
@Roles('ATTENDANT', 'OWNER', 'ADMIN')
@Controller({ path: 'attendant', version: '1' })
export class AttendantController {
  constructor(private readonly qrService: QrService) {}

  @Post('qr/scan')
  @ApiOperation({ summary: "Verify a booking's QR pass without changing its state (lookup before check-in/out)" })
  scan(@CurrentUser() user: AuthenticatedUser & { roles: string[] }, @Body() dto: ScanQrDto) {
    return this.qrService.scan(user, dto);
  }

  @Post('bookings/:bookingId/check-in')
  @ApiOperation({ summary: 'Check a vehicle in (CONFIRMED/DRIVER_ARRIVING -> CHECKED_IN -> PARKING_ACTIVE)' })
  checkIn(
    @CurrentUser() user: AuthenticatedUser & { roles: string[] },
    @Param('bookingId') bookingId: string,
    @Body() dto: CheckInDto,
  ) {
    return this.qrService.checkIn(user, bookingId, dto);
  }

  @Post('bookings/:bookingId/check-out')
  @ApiOperation({ summary: 'Check a vehicle out (PARKING_ACTIVE -> CHECKED_OUT -> COMPLETED)' })
  checkOut(
    @CurrentUser() user: AuthenticatedUser & { roles: string[] },
    @Param('bookingId') bookingId: string,
    @Body() dto: CheckOutDto,
  ) {
    return this.qrService.checkOut(user, bookingId, dto);
  }

  @Post('bookings/:bookingId/mismatch')
  @ApiOperation({ summary: 'Report a vehicle/category mismatch on a PARKING_ACTIVE booking' })
  reportMismatch(
    @CurrentUser() user: AuthenticatedUser & { roles: string[] },
    @Param('bookingId') bookingId: string,
    @Body() dto: ReportMismatchDto,
  ) {
    return this.qrService.reportMismatch(user, bookingId, dto);
  }

  @Post('bookings/:bookingId/mismatch/resolve')
  @ApiOperation({ summary: 'Resolve an open VEHICLE_MISMATCH (reject entry, or admin override back to active)' })
  resolveMismatch(
    @CurrentUser() user: AuthenticatedUser & { roles: string[] },
    @Param('bookingId') bookingId: string,
    @Body() dto: ResolveMismatchDto,
  ) {
    return this.qrService.resolveMismatch(user, bookingId, dto);
  }
}
