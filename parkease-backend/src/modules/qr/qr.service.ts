import { BadRequestException, ConflictException, ForbiddenException, Injectable, Logger, NotFoundException } from '@nestjs/common';
import { ConfigService } from '@nestjs/config';
import { AppRole, Booking, BookingActorSource, ParkingListing, ParkingSection, Prisma, Vehicle, VerificationOutcome } from '@prisma/client';
import { PrismaService } from '../../common/prisma/prisma.service';
import { AppConfig } from '../../common/config/configuration';
import { AuthenticatedUser } from '../../common/decorators/current-user.decorator';
import { BookingService } from '../booking/booking.service';
import { LedgerService } from '../ledger/ledger.service';
import { RefundsService } from '../refunds/refunds.service';
import { NotificationsService } from '../notifications/notifications.service';
import { computePayableBreakdown } from '../payments/pricing';
import { Money } from '../../common/money/money';
import { CashCollectDto, CheckInDto, CheckOutDto, ReportMismatchDto, ResolveMismatchDto, ScanQrDto } from './dto/qr.dto';
import { hashQrToken, parseQrToken, signQrToken, verifyQrTokenSignature } from './qr-token.util';

type BookingWithRelations = Booking & { parking: ParkingListing; section: ParkingSection; vehicle: Vehicle };

@Injectable()
export class QrService {
  private readonly logger = new Logger(QrService.name);

  constructor(
    private readonly prisma: PrismaService,
    private readonly configService: ConfigService<AppConfig, true>,
    private readonly bookingService: BookingService,
    private readonly ledgerService: LedgerService,
    private readonly refundsService: RefundsService,
    private readonly notificationsService: NotificationsService,
  ) {}

  // ---------------------------------------------------------------------
  // Driver: pass issuance/retrieval
  // ---------------------------------------------------------------------

  async getOrIssuePass(driverId: string, bookingId: string) {
    const booking = await this.prisma.booking.findUnique({ where: { id: bookingId } });
    if (!booking) throw new NotFoundException('Booking not found.');
    if (booking.driver_id !== driverId) throw new ForbiddenException('That booking does not belong to you.');
    if (!['CONFIRMED', 'DRIVER_ARRIVING'].includes(booking.status)) {
      throw new ConflictException(
        `A QR pass is only available while a booking is CONFIRMED or DRIVER_ARRIVING (this one is ${booking.status}).`,
      );
    }

    const secret = this.configService.get('qr', { infer: true }).signingSecret;
    let pass = await this.prisma.qrPass.findUnique({ where: { booking_id: bookingId } });

    if (pass && pass.status !== 'ACTIVE') {
      throw new ConflictException(`This booking's QR pass is ${pass.status} and cannot be re-issued.`);
    }

    if (!pass) {
      const issuedAt = new Date();
      // Open-ended (Instant) bookings have no end_time — the pass stays
      // valid for a generous fixed window rather than forever, matching
      // qr_passes.expires_at being NOT NULL in the Milestone 0 ERD.
      const expiresAt = booking.end_time ?? new Date(issuedAt.getTime() + 24 * 60 * 60 * 1000);
      const created = await this.prisma.qrPass.create({
        data: { booking_id: bookingId, payload_hash: '', issued_at: issuedAt, expires_at: expiresAt, status: 'ACTIVE' },
      });
      const token = signQrToken(created.id, created.issued_at.getTime(), secret);
      pass = await this.prisma.qrPass.update({ where: { id: created.id }, data: { payload_hash: hashQrToken(token) } });
      return toPassView(pass, token);
    }

    const token = signQrToken(pass.id, pass.issued_at.getTime(), secret);
    return toPassView(pass, token);
  }

  // ---------------------------------------------------------------------
  // Attendant/owner: scan, check-in, check-out
  // ---------------------------------------------------------------------

  async scan(actorUser: AuthenticatedUser & { roles: string[] }, dto: ScanQrDto) {
    const pass = await this.mustResolveVerifiedPass(dto.token);
    const booking = pass.booking as unknown as BookingWithRelations;
    const { actorRole } = await this.mustAuthorizeOperator(actorUser, booking);

    const outcome = this.evaluateOutcome(pass, booking);
    if (outcome !== 'OK') {
      await this.recordCheckEvent(pass, booking, actorUser, actorRole, 'CHECK_IN', outcome);
    }
    return {
      qrPassId: pass.id,
      bookingId: pass.booking_id,
      passStatus: pass.status,
      outcome,
      booking: toBookingOpsSummary(booking),
    };
  }

  async checkIn(actorUser: AuthenticatedUser & { roles: string[] }, bookingId: string, dto: CheckInDto) {
    const pass = await this.mustResolveVerifiedPass(dto.token, bookingId);
    const booking = pass.booking as unknown as BookingWithRelations;
    const { actorRole, source } = await this.mustAuthorizeOperator(actorUser, booking);

    const outcome = this.evaluateOutcome(pass, booking);
    if (outcome === 'ALREADY_USED') throw new ConflictException('This QR pass has already been used.');
    if (outcome === 'EXPIRED') throw new ConflictException('This QR pass has expired.');
    if (!['CONFIRMED', 'DRIVER_ARRIVING'].includes(booking.status)) {
      throw new ConflictException(`This booking is ${booking.status}, not ready for check-in.`);
    }

    // A presented-registration mismatch doesn't block check-in per the
    // literal Milestone 0 §8 state diagram — VEHICLE_MISMATCH is only
    // reachable from PARKING_ACTIVE — see CheckInDto's doc comment.
    const presentedMatches =
      !dto.presentedRegistrationNumber ||
      dto.presentedRegistrationNumber.trim().toUpperCase() === booking.vehicle.registration_number.trim().toUpperCase();
    const finalOutcome: VerificationOutcome = outcome !== 'OK' ? outcome : presentedMatches ? 'OK' : 'VEHICLE_NUMBER_MISMATCH';

    await this.recordCheckEvent(pass, booking, actorUser, actorRole, 'CHECK_IN', finalOutcome);
    await this.prisma.qrPass.update({ where: { id: pass.id }, data: { status: 'USED', used_at: new Date() } });

    await this.bookingService.applyBookingTransition(bookingId, booking.status, 'CHECKED_IN', {
      actorId: actorUser.id,
      actorRole,
      source,
      reason: 'QR check-in scan.',
      metadata: { qrPassId: pass.id, verificationOutcome: finalOutcome },
    });
    const activated = await this.bookingService.applyBookingTransition(bookingId, 'CHECKED_IN', 'PARKING_ACTIVE', {
      actorId: null,
      actorRole: null,
      source: 'SYSTEM_JOB',
      reason: 'System marks active on successful check-in.',
    });

    return { outcome: finalOutcome, booking: activated };
  }

  async checkOut(actorUser: AuthenticatedUser & { roles: string[] }, bookingId: string, dto: CheckOutDto) {
    const pass = await this.mustResolveVerifiedPass(dto.token, bookingId);
    const booking = pass.booking as unknown as BookingWithRelations;
    const { actorRole, source } = await this.mustAuthorizeOperator(actorUser, booking);

    if (booking.status !== 'PARKING_ACTIVE') {
      throw new ConflictException(`This booking is ${booking.status}, not ready for check-out.`);
    }

    await this.recordCheckEvent(pass, booking, actorUser, actorRole, 'CHECK_OUT', 'OK');
    await this.bookingService.applyBookingTransition(bookingId, 'PARKING_ACTIVE', 'CHECKED_OUT', {
      actorId: actorUser.id,
      actorRole,
      source,
      reason: 'QR check-out scan.',
      metadata: { qrPassId: pass.id },
    });
    const completed = await this.bookingService.applyBookingTransition(bookingId, 'CHECKED_OUT', 'COMPLETED', {
      actorId: null,
      actorRole: null,
      source: 'SYSTEM_JOB',
      reason: 'System finalizes ledger/earnings on checkout.',
    });

    // "System finalizes ledger/earnings" (Milestone 0 §8) — the owner's
    // earnings entry for this booking becomes AVAILABLE (settlement-
    // eligible, Milestone 9) now that the parking session is actually
    // over, matching the PENDING -> AVAILABLE transition LedgerService's
    // Milestone 7 doc comment already flagged as landing "after checkout."
    // A booking with no ledger entry yet (payment still mid-flight, or
    // never collected) is left alone rather than erroring — checkout
    // completing before payment settles is a real operational case, not
    // a bug, and Milestone 10's reconciliation-exceptions tooling is the
    // right place to surface that gap, not this call.
    await this.prisma.ownerEarningsLedgerEntry.updateMany({
      where: { booking_id: bookingId, status: 'PENDING' },
      data: { status: 'AVAILABLE' },
    });

    return { outcome: 'OK' as const, booking: completed };
  }

  // ---------------------------------------------------------------------
  // Mismatch reporting/resolution
  // ---------------------------------------------------------------------

  async reportMismatch(actorUser: AuthenticatedUser & { roles: string[] }, bookingId: string, dto: ReportMismatchDto) {
    const booking = await this.mustLoadBookingWithRelations(bookingId);
    const { actorRole, source } = await this.mustAuthorizeOperator(actorUser, booking);
    if (booking.status !== 'PARKING_ACTIVE') {
      throw new ConflictException(`Mismatches can only be reported while a booking is PARKING_ACTIVE (this one is ${booking.status}).`);
    }

    await this.prisma.vehicleMismatchEvent.create({
      data: {
        booking_id: bookingId,
        reported_by: actorUser.id,
        original_category: booking.vehicle_category,
        actual_category: dto.actualCategory ?? booking.vehicle_category,
        original_vehicle_id: booking.vehicle_id,
        actual_vehicle_registration: dto.actualVehicleRegistration,
      },
    });

    return this.bookingService.applyBookingTransition(bookingId, 'PARKING_ACTIVE', 'VEHICLE_MISMATCH', {
      actorId: actorUser.id,
      actorRole,
      source,
      reason: dto.note ?? 'Attendant reported a vehicle/category mismatch.',
    });
  }

  async resolveMismatch(actorUser: AuthenticatedUser & { roles: string[] }, bookingId: string, dto: ResolveMismatchDto) {
    const booking = await this.mustLoadBookingWithRelations(bookingId);
    if (booking.status !== 'VEHICLE_MISMATCH') {
      throw new ConflictException(`Only a VEHICLE_MISMATCH booking can be resolved (this one is ${booking.status}).`);
    }

    let actorRole: AppRole;
    let source: BookingActorSource;
    if (dto.resolution === 'ADMIN_OVERRIDE') {
      if (!actorUser.roles.includes('ADMIN')) throw new ForbiddenException('Only an admin can override a mismatch.');
      actorRole = 'ADMIN';
      source = 'ADMIN';
    } else {
      ({ actorRole, source } = await this.mustAuthorizeOperator(actorUser, booking));
    }

    const newStatus = dto.resolution === 'REJECTED_ENTRY' ? 'REJECTED' : 'PARKING_ACTIVE';
    const result = await this.bookingService.applyBookingTransition(bookingId, 'VEHICLE_MISMATCH', newStatus, {
      actorId: actorUser.id,
      actorRole,
      source,
      reason: dto.reason ?? `Mismatch resolved: ${dto.resolution}.`,
    });

    const openMismatch = await this.prisma.vehicleMismatchEvent.findFirst({
      where: { booking_id: bookingId, resolution: null },
      orderBy: { created_at: 'desc' },
    });
    if (openMismatch) {
      await this.prisma.vehicleMismatchEvent.update({ where: { id: openMismatch.id }, data: { resolution: dto.resolution } });
    }

    // Milestone 9: entry was refused after the driver had already paid and
    // been let in — never their fault, so REJECTED_ENTRY always refunds in
    // full. ADMIN_OVERRIDE returns the booking to PARKING_ACTIVE with
    // nothing to refund; RECATEGORIZED_WITH_PAYMENT_DIFF isn't a reachable
    // `dto.resolution` value at all (ResolveMismatchDto's Milestone 8 doc
    // comment already disclosed it's not implemented).
    if (dto.resolution === 'REJECTED_ENTRY') {
      await this.refundsService.refundForRejectedMismatch(bookingId, actorUser.id);
    }

    return result;
  }

  // ---------------------------------------------------------------------
  // Cash collection (§7.7 in the Milestone 0 spec, built here rather than
  // in PaymentsModule since it shares this module's attendant/owner
  // operational-authorization machinery — see AssignAttendantDto's doc
  // comment in parking.dto.ts for the same "no natural single home"
  // situation).
  // ---------------------------------------------------------------------

  async cashCollect(actorUser: AuthenticatedUser & { roles: string[] }, bookingId: string, dto: CashCollectDto) {
    const booking = await this.mustLoadBookingWithRelations(bookingId);
    const { actorRole, source } = await this.mustAuthorizeOperator(actorUser, booking);
    if (booking.status !== 'PENDING_PAYMENT') {
      throw new ConflictException(`This booking is ${booking.status}, not awaiting payment.`);
    }

    const inFlightOnlineOrder = await this.prisma.paymentOrder.findFirst({
      where: { booking_id: bookingId, status: { in: ['PENDING', 'PROCESSING', 'SUCCESSFUL'] } },
    });
    if (inFlightOnlineOrder) {
      throw new ConflictException('An online payment is already in progress or completed for this booking.');
    }

    const breakdown = await computePayableBreakdown(this.prisma, this.configService, booking.section, booking);

    await this.prisma.$transaction(async (tx) => {
      await tx.cashCollection.create({
        data: {
          booking_id: bookingId,
          collected_by: actorUser.id,
          amount: breakdown.driverTotalMinorUnits,
          confirmed_at: new Date(),
          confirmation_method: dto.confirmationMethod ?? 'CASH',
          audit_note: dto.auditNote,
        },
      });
      await this.ledgerService.recordSuccessfulPayment(tx, {
        booking: { ...booking, status: 'CONFIRMED' },
        ownerId: booking.parking.owner_id,
        vehicleType: booking.vehicle.vehicle_type,
        paymentMethod: 'CASH',
        gateway: null,
        gatewayTransactionId: null,
        breakdown,
        idempotencyKey: `cash:${bookingId}`,
      });
      await this.bookingService.markPaid(bookingId, actorUser.id, source, tx, actorRole);
    });

    // Distinct from the generic "Booking confirmed" notification
    // applyBookingTransition already fires for every CONFIRMED transition
    // (online success included) — this one specifically confirms the cash
    // amount was received, closing the loop the driver saw when they
    // picked "pay with cash" (BookingService.payCash).
    await this.notificationsService.send({
      userId: booking.driver_id,
      category: 'booking_status',
      type: 'CASH_PAYMENT_RECEIVED',
      title: 'Cash payment received',
      body: `Your cash payment of ${Money.of(breakdown.driverTotalMinorUnits, breakdown.currency).toDisplayString()} has been received and your booking is now paid.`,
    });

    return this.bookingService.getBooking(actorUser, bookingId);
  }

  // ---------------------------------------------------------------------
  // Shared helpers
  // ---------------------------------------------------------------------

  private async mustResolveVerifiedPass(token: string, expectedBookingId?: string) {
    const parsed = parseQrToken(token);
    if (!parsed) throw new BadRequestException('Invalid QR code.');

    const pass = await this.prisma.qrPass.findUnique({
      where: { id: parsed.qrPassId },
      include: { booking: { include: { parking: true, section: true, vehicle: true } } },
    });
    if (!pass) throw new NotFoundException('QR code not recognized.');
    if (expectedBookingId && pass.booking_id !== expectedBookingId) {
      throw new BadRequestException('QR code does not match this booking.');
    }

    const secret = this.configService.get('qr', { infer: true }).signingSecret;
    const signatureOk = verifyQrTokenSignature(token, pass.id, pass.issued_at.getTime(), secret);
    const hashOk = hashQrToken(token) === pass.payload_hash;
    if (!signatureOk || !hashOk) {
      // A forged or tampered token — worth its own log line distinct from
      // an ordinary "not found," per Milestone 0 §13 T9.
      this.logger.warn(`QR token failed verification for pass ${pass.id}.`);
      throw new ForbiddenException('Invalid QR code.');
    }
    return pass;
  }

  private async mustLoadBookingWithRelations(bookingId: string): Promise<BookingWithRelations> {
    const booking = await this.prisma.booking.findUnique({
      where: { id: bookingId },
      include: { parking: true, section: true, vehicle: true },
    });
    if (!booking) throw new NotFoundException('Booking not found.');
    return booking;
  }

  private evaluateOutcome(pass: { status: string; expires_at: Date }, booking: BookingWithRelations): VerificationOutcome {
    if (pass.status === 'USED' || pass.status === 'REVOKED') return 'ALREADY_USED';
    if (pass.status === 'EXPIRED' || pass.expires_at.getTime() < Date.now()) return 'EXPIRED';
    // Re-runs the same category/type compatibility check as booking
    // creation (Milestone 0 §5.6's is_bookable), against the section's
    // *current* configuration — catching the rare case where a section's
    // supported types changed after this booking was made.
    if (
      booking.vehicle_category !== booking.section.vehicle_category ||
      !booking.section.supported_vehicle_types.includes(booking.vehicle.vehicle_type)
    ) {
      return 'CATEGORY_MISMATCH';
    }
    return 'OK';
  }

  private async recordCheckEvent(
    pass: { id: string },
    booking: BookingWithRelations,
    actorUser: AuthenticatedUser,
    actorRole: AppRole,
    eventType: 'CHECK_IN' | 'CHECK_OUT',
    outcome: VerificationOutcome,
  ) {
    await this.prisma.checkEvent.create({
      data: {
        booking_id: booking.id,
        qr_pass_id: pass.id,
        event_type: eventType,
        // Nullable "if owner self-service" per the Milestone 0 §5.11 ERD —
        // only populated when an actual ATTENDANT performed the scan, not
        // when the owner operated their own listing directly or an admin
        // did it for support/investigation.
        attendant_id: actorRole === 'ATTENDANT' ? actorUser.id : null,
        parking_id: booking.parking_id,
        section_id: booking.section_id,
        verification_outcome: outcome,
      },
    });
  }

  /**
   * ADMIN always passes (support/investigation access, Milestone 0 §4's
   * capability matrix). Otherwise: an OWNER may operate their own listing
   * directly (small owner-operated lots, per the same matrix); an
   * ATTENDANT needs a live (non-revoked) AttendantAssignment for this
   * parking_id, and — when that assignment restricts categories — the
   * booking's vehicle_category must be one of them.
   */
  private async mustAuthorizeOperator(
    user: AuthenticatedUser & { roles: string[] },
    booking: BookingWithRelations,
  ): Promise<{ actorRole: AppRole; source: BookingActorSource }> {
    if (user.roles.includes('ADMIN')) return { actorRole: 'ADMIN', source: 'ADMIN' };

    if (user.roles.includes('OWNER')) {
      const ownerProfile = await this.prisma.ownerProfile.findUnique({ where: { user_id: user.id } });
      if (ownerProfile && ownerProfile.id === booking.parking.owner_id) {
        return { actorRole: 'OWNER', source: 'APP' };
      }
    }

    if (user.roles.includes('ATTENDANT')) {
      const attendantProfile = await this.prisma.attendantProfile.findUnique({ where: { user_id: user.id } });
      if (attendantProfile) {
        const assignment = await this.prisma.attendantAssignment.findFirst({
          where: { attendant_id: attendantProfile.id, parking_id: booking.parking_id, revoked_at: null },
        });
        if (assignment && (assignment.authorized_categories.length === 0 || assignment.authorized_categories.includes(booking.vehicle_category))) {
          return { actorRole: 'ATTENDANT', source: 'ATTENDANT_APP' };
        }
      }
    }

    throw new ForbiddenException('You are not authorized to operate on this parking listing.');
  }
}

function toPassView(pass: { id: string; booking_id: string; issued_at: Date; expires_at: Date; status: string }, token: string) {
  return {
    qrPassId: pass.id,
    bookingId: pass.booking_id,
    token,
    issuedAt: pass.issued_at,
    expiresAt: pass.expires_at,
    status: pass.status,
  };
}

function toBookingOpsSummary(b: BookingWithRelations) {
  return {
    id: b.id,
    status: b.status,
    vehicleCategory: b.vehicle_category,
    vehicleRegistration: b.vehicle.registration_number,
    sectionName: b.section.name,
    parkingId: b.parking_id,
  };
}
