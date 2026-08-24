import { Body, Controller, Get, Param, Post } from '@nestjs/common';
import { ApiOperation, ApiTags } from '@nestjs/swagger';
import { Roles } from '../../common/decorators/roles.decorator';
import { Throttle } from '../../common/rate-limit/throttle.decorator';
import { CurrentUser, AuthenticatedUser } from '../../common/decorators/current-user.decorator';
import { BookingService } from './booking.service';
import { ConfirmBookingDto, CreateHoldDto, CreateInstantBookingDto } from './dto/booking.dto';

/**
 * Cancellation (`/bookings/:id/cancel`, `/admin/bookings/:id/cancel`) moved
 * to RefundsModule in Milestone 9 — cancelling a paid booking always needs
 * a refund computed and dispatched as part of the same request, and
 * RefundsModule is what owns that cross-module orchestration (the same
 * "route ownership follows whichever module needs the extra machinery"
 * pattern QrModule already established for check-in/check-out/
 * cash-collect in Milestone 8). BookingService.cancelBooking/
 * adminCancelBooking still exist and do the actual state transition —
 * RefundsService calls them directly — this controller just no longer
 * exposes them on their own. No class-level @Roles() here since GET
 * endpoints are open to any authenticated role (scoped inside
 * BookingService), while mutating endpoints below declare their own
 * @Roles('DRIVER').
 */
@ApiTags('bookings')
@Controller({ path: 'bookings', version: '1' })
export class BookingController {
  constructor(private readonly bookingService: BookingService) {}

  @Post('holds')
  @Roles('DRIVER')
  @Throttle({ limit: 20, windowSeconds: 60 })
  @ApiOperation({ summary: 'Reserve capacity in a section for a specific vehicle, briefly, ahead of payment' })
  createHold(@CurrentUser() user: AuthenticatedUser, @Body() dto: CreateHoldDto) {
    return this.bookingService.createHold(user.id, dto);
  }

  @Post()
  @Roles('DRIVER')
  @ApiOperation({ summary: 'Confirm a hold into an advance booking (PENDING_PAYMENT)' })
  confirmBooking(@CurrentUser() user: AuthenticatedUser, @Body() dto: ConfirmBookingDto) {
    return this.bookingService.confirmBooking(user.id, dto);
  }

  @Post('instant')
  @Roles('DRIVER')
  @Throttle({ limit: 20, windowSeconds: 60 })
  @ApiOperation({ summary: 'Create + immediately confirm an Instant Mode booking (PENDING_PAYMENT)' })
  createInstantBooking(@CurrentUser() user: AuthenticatedUser, @Body() dto: CreateInstantBookingDto) {
    return this.bookingService.createInstantBooking(user.id, dto);
  }

  @Get()
  @ApiOperation({ summary: 'List bookings scoped to the caller: own (DRIVER), own parking (OWNER), or all (ADMIN)' })
  list(@CurrentUser() user: AuthenticatedUser & { roles: string[] }) {
    return this.bookingService.listBookings(user);
  }

  @Get(':bookingId')
  @ApiOperation({ summary: 'Get one booking (role+ownership scoped)' })
  getOne(@CurrentUser() user: AuthenticatedUser & { roles: string[] }, @Param('bookingId') bookingId: string) {
    return this.bookingService.getBooking(user, bookingId);
  }

  @Get(':bookingId/quote')
  @ApiOperation({ summary: 'The authoritative payable amount for a PENDING_PAYMENT booking (fee/tax/total) — same computation the gateway order and cash-collect both use' })
  getQuote(@CurrentUser() user: AuthenticatedUser & { roles: string[] }, @Param('bookingId') bookingId: string) {
    return this.bookingService.getQuote(user, bookingId);
  }

  @Post(':bookingId/pay-cash')
  @Roles('DRIVER')
  @Throttle({ limit: 10, windowSeconds: 60 })
  @ApiOperation({ summary: 'Driver picks "pay with cash" — records intent and notifies both driver and owner; does not itself change booking status (see QrService.cashCollect for the actual confirmation)' })
  payCash(@CurrentUser() user: AuthenticatedUser, @Param('bookingId') bookingId: string) {
    return this.bookingService.payCash(user.id, bookingId);
  }
}
