import { Body, Controller, Get, Param, Post } from '@nestjs/common';
import { ApiOperation, ApiTags } from '@nestjs/swagger';
import { Roles } from '../../common/decorators/roles.decorator';
import { CurrentUser, AuthenticatedUser } from '../../common/decorators/current-user.decorator';
import { RefundsService } from './refunds.service';
import {
  AdminCancelBookingDto,
  AdminManualRefundDto,
  CancelBookingDto,
  ConfirmCashRefundDto,
  MarkParkingUnavailableDto,
} from './dto/refunds.dto';

/**
 * Milestone 9: owns every driver/owner/admin-facing endpoint that both
 * mutates a booking's status AND needs to compute/dispatch a refund as
 * part of the same request — see RefundsService's class doc comment for
 * why this supersedes the plain-transition `/bookings/:id/cancel` and
 * `/admin/bookings/:id/cancel` routes BookingModule registered through
 * Milestone 8 (those handlers were removed from BookingController/
 * AdminBookingController when this module took over the routes).
 */
@ApiTags('refunds')
@Controller({ version: '1' })
export class RefundsController {
  constructor(private readonly refundsService: RefundsService) {}

  @Post('bookings/:bookingId/cancel')
  @Roles('DRIVER')
  @ApiOperation({ summary: 'Cancel your own booking and refund per the applicable cancellation policy' })
  cancel(@CurrentUser() user: AuthenticatedUser, @Param('bookingId') bookingId: string, @Body() dto: CancelBookingDto) {
    return this.refundsService.cancelBookingAndRefund(user.id, bookingId, dto);
  }

  @Post('admin/bookings/:bookingId/cancel')
  @Roles('ADMIN')
  @ApiOperation({ summary: 'Force-cancel a booking (-> ADMIN_CANCELLED) and refund (default: in full)' })
  adminCancel(@CurrentUser() admin: AuthenticatedUser, @Param('bookingId') bookingId: string, @Body() dto: AdminCancelBookingDto) {
    return this.refundsService.adminCancelBookingAndRefund(admin.id, bookingId, dto);
  }

  @Post('owner/bookings/:bookingId/mark-unavailable')
  @Roles('OWNER')
  @ApiOperation({ summary: 'Mark a confirmed booking\'s parking as unavailable (-> PARKING_UNAVAILABLE) and refund in full' })
  markUnavailable(
    @CurrentUser() owner: AuthenticatedUser & { roles: string[] },
    @Param('bookingId') bookingId: string,
    @Body() dto: MarkParkingUnavailableDto,
  ) {
    return this.refundsService.markParkingUnavailableAndRefund(owner, bookingId, dto);
  }

  @Post('admin/bookings/:bookingId/manual-refund')
  @Roles('ADMIN')
  @ApiOperation({ summary: 'Issue a discretionary refund without changing the booking\'s status (disputes/support)' })
  manualRefund(@CurrentUser() admin: AuthenticatedUser, @Param('bookingId') bookingId: string, @Body() dto: AdminManualRefundDto) {
    return this.refundsService.adminManualRefund(admin.id, bookingId, dto);
  }

  @Get('bookings/:bookingId/refunds')
  @ApiOperation({ summary: "List a booking's refunds (role/ownership scoped)" })
  listForBooking(@CurrentUser() user: AuthenticatedUser & { roles: string[] }, @Param('bookingId') bookingId: string) {
    return this.refundsService.listRefundsForBooking(user, bookingId);
  }

  @Get('refunds/:refundId')
  @ApiOperation({ summary: 'Get one refund (role/ownership scoped)' })
  getOne(@CurrentUser() user: AuthenticatedUser & { roles: string[] }, @Param('refundId') refundId: string) {
    return this.refundsService.getRefund(user, refundId);
  }

  @Post('refunds/:refundId/confirm-cash')
  @Roles('OWNER', 'ADMIN')
  @ApiOperation({ summary: 'Confirm a cash refund was physically handed back to the driver' })
  confirmCash(
    @CurrentUser() user: AuthenticatedUser & { roles: string[] },
    @Param('refundId') refundId: string,
    @Body() dto: ConfirmCashRefundDto,
  ) {
    return this.refundsService.confirmCashRefund(user, refundId, dto);
  }
}
