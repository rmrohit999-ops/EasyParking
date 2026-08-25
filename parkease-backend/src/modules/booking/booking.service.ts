import { BadRequestException, ConflictException, ForbiddenException, GoneException, Injectable, Logger, NotFoundException } from '@nestjs/common';
import { ConfigService } from '@nestjs/config';
import { AppRole as PrismaAppRole, BookingActorSource, BookingStatus, BookingType, Prisma, VehicleCategory, VehicleType } from '@prisma/client';
import { PrismaService } from '../../common/prisma/prisma.service';
import { AppConfig } from '../../common/config/configuration';
import { AuthenticatedUser } from '../../common/decorators/current-user.decorator';
import { OCCUPIED_BOOKING_STATUSES, RESERVED_BOOKING_STATUSES, TERMINAL_BOOKING_STATUSES, isTransitionAllowed } from './booking-state-machine';
import { BookingExpiryProducer } from './booking-expiry.queue';
import { CancelBookingDto, ConfirmBookingDto, CreateHoldDto, CreateInstantBookingDto } from './dto/booking.dto';
import { NotificationsService } from '../notifications/notifications.service';
import { RealtimeGateway } from '../realtime/realtime.gateway';
import { computePayableBreakdown } from '../payments/pricing';
import { Money } from '../../common/money/money';

@Injectable()
export class BookingService {
  private readonly logger = new Logger(BookingService.name);

  constructor(
    private readonly prisma: PrismaService,
    private readonly configService: ConfigService<AppConfig, true>,
    private readonly expiryProducer: BookingExpiryProducer,
    private readonly notificationsService: NotificationsService,
    private readonly realtimeGateway: RealtimeGateway,
  ) {}

  // ---------------------------------------------------------------------
  // Holds
  // ---------------------------------------------------------------------

  async createHold(driverId: string, dto: CreateHoldDto) {
    const vehicle = await this.prisma.vehicle.findUnique({ where: { id: dto.vehicleId } });
    if (!vehicle || vehicle.status !== 'ACTIVE') throw new NotFoundException('Vehicle not found.');
    if (vehicle.driver_id !== driverId) throw new ForbiddenException('That vehicle does not belong to you.');

    const section = await this.prisma.parkingSection.findUnique({
      where: { id: dto.sectionId },
      include: { parking: true },
    });
    if (!section) throw new NotFoundException('Parking section not found.');
    assertBookable(section, section.parking);
    assertCompatible(vehicle.category, vehicle.vehicle_type, section.vehicle_category, section.supported_vehicle_types);

    const holdTtlSeconds = this.configService.get('business', { infer: true }).bookingHoldTtlSeconds;
    const expiresAt = new Date(Date.now() + holdTtlSeconds * 1000);

    const hold = await this.prisma.$transaction(async (tx) => {
      // Atomic, race-safe capacity reservation: the WHERE clause's
      // capacity-minus-committed arithmetic can't be expressed in Prisma's
      // typed query builder, so this is raw SQL — see Milestone 0 §8's
      // "serializes concurrent writers via Postgres row-locking." A
      // concurrent second reservation racing for the last space will get
      // 0 rows affected here and fail cleanly with 409, never overbook.
      const affected = await tx.$executeRaw`
        UPDATE section_availability
        SET reserved_count = reserved_count + 1, version = version + 1, updated_at = now()
        WHERE section_id = ${dto.sectionId}
          AND (capacity - reserved_count - occupied_count - blocked_count) >= 1
      `;
      if (affected === 0) {
        throw new ConflictException('No spaces available in this section right now.');
      }

      return tx.bookingHold.create({
        data: {
          section_id: dto.sectionId,
          vehicle_category: section.vehicle_category,
          driver_id: driverId,
          vehicle_id: dto.vehicleId,
          expires_at: expiresAt,
        },
      });
    });

    await this.expiryProducer.scheduleExpiry(hold.id, holdTtlSeconds * 1000);

    return {
      id: hold.id,
      sectionId: hold.section_id,
      vehicleId: hold.vehicle_id,
      expiresAt: hold.expires_at,
    };
  }

  /**
   * Called by the proactive expiry job (BookingExpiryProcessor) and,
   * lazily, by every read/write path that touches a hold (see
   * `mustGetLiveHold`) — either caller reaching an already-released or
   * already-booked hold is a safe no-op, which is what makes both paths
   * safe to run concurrently without their own locking.
   */
  async expireHoldIfDue(holdId: string): Promise<void> {
    const hold = await this.prisma.bookingHold.findUnique({ where: { id: holdId } });
    if (!hold || hold.released_at || hold.booking_id) return; // already resolved one way or another
    if (hold.expires_at.getTime() > Date.now()) return; // not due yet

    await this.prisma.$transaction([
      this.prisma.bookingHold.update({ where: { id: holdId }, data: { released_at: new Date() } }),
      this.prisma.$executeRaw`
        UPDATE section_availability
        SET reserved_count = GREATEST(reserved_count - 1, 0), version = version + 1, updated_at = now()
        WHERE section_id = ${hold.section_id}
      `,
    ]);
  }

  private async mustGetLiveHold(driverId: string, holdId: string) {
    await this.expireHoldIfDue(holdId);
    const hold = await this.prisma.bookingHold.findUnique({ where: { id: holdId } });
    if (!hold) throw new NotFoundException('Hold not found.');
    if (hold.driver_id !== driverId) throw new ForbiddenException('That hold does not belong to you.');
    if (hold.booking_id) throw new ConflictException('This hold has already been confirmed into a booking.');
    if (hold.released_at) throw new GoneException('This hold has expired. Please search again.');
    return hold;
  }

  // ---------------------------------------------------------------------
  // Booking creation
  // ---------------------------------------------------------------------

  async confirmBooking(driverId: string, dto: ConfirmBookingDto) {
    const hold = await this.mustGetLiveHold(driverId, dto.holdId);

    const startTime = new Date(dto.startTime);
    const endTime = new Date(dto.endTime);
    if (Number.isNaN(startTime.getTime()) || Number.isNaN(endTime.getTime()) || endTime <= startTime) {
      throw new BadRequestException('endTime must be after startTime.');
    }

    const section = await this.prisma.parkingSection.findUniqueOrThrow({ where: { id: hold.section_id } });
    const durationHours = Math.max(1, Math.ceil((endTime.getTime() - startTime.getTime()) / (60 * 60 * 1000)));
    const subtotalMinorUnits = section.hourly_rate_minor_units * durationHours;

    return this.createBookingFromHold(hold, {
      bookingType: BookingType.ADVANCE,
      startTime,
      endTime,
      priceSnapshot: {
        currency: section.currency,
        hourlyRateMinorUnits: section.hourly_rate_minor_units,
        durationHours,
        subtotalMinorUnits,
        // Commission/GST/discount computation is Milestone 7 (Payments) —
        // this snapshot is the base rate calculation only, not a final
        // payable amount.
        note: 'Subtotal only — tax, commission, and any discounts are computed at payment time (Milestone 7).',
      },
    });
  }

  async createInstantBooking(driverId: string, dto: CreateInstantBookingDto) {
    const section = await this.prisma.parkingSection.findUnique({
      where: { id: dto.sectionId },
      include: { parking: true },
    });
    if (!section) throw new NotFoundException('Parking section not found.');
    if (!section.instant_mode_enabled) {
      throw new BadRequestException('Instant Mode is not enabled for this section.');
    }

    const hold = await this.createHold(driverId, { sectionId: dto.sectionId, vehicleId: dto.vehicleId });
    const liveHold = await this.mustGetLiveHold(driverId, hold.id);

    return this.createBookingFromHold(liveHold, {
      bookingType: BookingType.INSTANT,
      startTime: new Date(),
      endTime: null,
      priceSnapshot: {
        currency: section.currency,
        hourlyRateMinorUnits: section.hourly_rate_minor_units,
        note: 'Instant Parking is open-ended — the payable amount is computed from actual duration at checkout (Milestone 8/9).',
      },
    });
  }

  private async createBookingFromHold(
    hold: { id: string; section_id: string; driver_id: string; vehicle_id: string; vehicle_category: VehicleCategory },
    args: { bookingType: BookingType; startTime: Date; endTime: Date | null; priceSnapshot: Record<string, unknown> },
  ) {
    const section = await this.prisma.parkingSection.findUniqueOrThrow({ where: { id: hold.section_id } });

    const booking = await this.prisma.$transaction(async (tx) => {
      const created = await tx.booking.create({
        data: {
          driver_id: hold.driver_id,
          vehicle_id: hold.vehicle_id,
          parking_id: section.parking_id,
          section_id: section.id,
          vehicle_category: hold.vehicle_category,
          booking_type: args.bookingType,
          status: BookingStatus.PENDING_PAYMENT,
          start_time: args.startTime,
          end_time: args.endTime,
          price_snapshot: args.priceSnapshot as Prisma.InputJsonValue,
          policy_snapshot: {
            note: 'Cancellation/commission/tax policy versioning is configured starting Milestone 9/10 — none are attached yet.',
          },
        },
      });
      await tx.bookingHold.update({ where: { id: hold.id }, data: { booking_id: created.id } });
      await tx.bookingEvent.create({
        data: {
          booking_id: created.id,
          actor_id: hold.driver_id,
          actor_role: 'DRIVER',
          source: 'APP',
          old_status: null,
          new_status: BookingStatus.PENDING_PAYMENT,
          reason: 'Hold confirmed into booking.',
        },
      });
      return created;
    });

    return toBookingView(booking);
  }

  // ---------------------------------------------------------------------
  // Reads
  // ---------------------------------------------------------------------

  async listBookings(user: AuthenticatedUser & { roles: string[] }) {
    await this.expireStaleBookings();

    if (user.roles.includes('ADMIN')) {
      const bookings = await this.prisma.booking.findMany({
        orderBy: { created_at: 'desc' },
        take: 200,
        include: BOOKING_VIEW_INCLUDE,
      });
      return bookings.map(toBookingView);
    }
    if (user.roles.includes('OWNER')) {
      const ownerProfile = await this.prisma.ownerProfile.findUnique({ where: { user_id: user.id } });
      if (!ownerProfile) return [];
      const bookings = await this.prisma.booking.findMany({
        where: { parking: { owner_id: ownerProfile.id } },
        orderBy: { created_at: 'desc' },
        take: 200,
        include: BOOKING_VIEW_INCLUDE,
      });
      return bookings.map(toBookingView);
    }
    // DRIVER (default) — ATTENDANT scoping via AttendantAssignment lands in
    // Milestone 8 alongside QR/check-in, the first feature that needs it.
    const bookings = await this.prisma.booking.findMany({
      where: { driver_id: user.id },
      orderBy: { created_at: 'desc' },
      take: 200,
      include: BOOKING_VIEW_INCLUDE,
    });
    return bookings.map(toBookingView);
  }

  async getBooking(user: AuthenticatedUser & { roles: string[] }, bookingId: string) {
    await this.expireHoldForBookingIfDue(bookingId);
    const booking = await this.prisma.booking.findUnique({
      where: { id: bookingId },
      include: BOOKING_VIEW_INCLUDE,
    });
    if (!booking) throw new NotFoundException('Booking not found.');

    const allowed = await this.canAccessBooking(user, booking);
    if (!allowed) throw new ForbiddenException('You do not have permission to view this booking.');

    return toBookingView(booking);
  }

  /**
   * The authoritative payable amount for a PENDING_PAYMENT booking — same
   * computePayableBreakdown() used by both the online-payment order and
   * cashCollect, so "what the customer is told to pay" and "what the
   * gateway/owner actually settles" can never drift apart. Exists so the
   * payment screen can show "Parking Fee / Total Payable" before the
   * driver has picked a payment method at all, not just after.
   */
  async getQuote(user: AuthenticatedUser & { roles: string[] }, bookingId: string) {
    const booking = await this.prisma.booking.findUnique({
      where: { id: bookingId },
      include: { parking: true, section: true },
    });
    if (!booking) throw new NotFoundException('Booking not found.');
    const allowed = await this.canAccessBooking(user, booking);
    if (!allowed) throw new ForbiddenException('You do not have permission to view this booking.');

    const breakdown = await computePayableBreakdown(this.prisma, this.configService, booking.section, booking);
    return {
      bookingId,
      currency: breakdown.currency,
      parkingAmountMinorUnits: breakdown.parkingAmountMinorUnits.toString(),
      taxAmountMinorUnits: breakdown.taxAmountMinorUnits.toString(),
      totalPayableMinorUnits: breakdown.driverTotalMinorUnits.toString(),
    };
  }

  /**
   * Driver explicitly picks "pay with cash" on the payment screen — records
   * the intent (so the owner's booking list can show "Cash Payment Pending"
   * rather than an ambiguous PENDING_PAYMENT with no method) and notifies
   * both sides immediately, per the same "owner should know now, not just
   * when the driver shows up" requirement the cash-collect flow already
   * assumes. Does NOT transition booking status — PENDING_PAYMENT is
   * correct until the owner/attendant actually confirms cash in hand via
   * QrService.cashCollect, which is the only thing that moves it to
   * CONFIRMED (this stays purely a notification/display signal, so it
   * can never race or conflict with the online-payment path).
   */
  async payCash(driverId: string, bookingId: string) {
    const booking = await this.prisma.booking.findUnique({
      where: { id: bookingId },
      include: { parking: true, section: true },
    });
    if (!booking) throw new NotFoundException('Booking not found.');
    if (booking.driver_id !== driverId) throw new ForbiddenException('That booking does not belong to you.');
    if (booking.status !== 'PENDING_PAYMENT') {
      throw new ConflictException(`This booking is ${booking.status}, not awaiting payment.`);
    }

    const breakdown = await computePayableBreakdown(this.prisma, this.configService, booking.section, booking);

    await this.prisma.booking.update({ where: { id: bookingId }, data: { intended_payment_method: 'CASH' } });

    await this.notificationsService.send({
      userId: booking.driver_id,
      category: 'booking_status',
      type: 'CASH_PAYMENT_PENDING',
      title: 'Cash payment pending',
      body: `Please pay ${Money.of(breakdown.driverTotalMinorUnits, breakdown.currency).toDisplayString()} in cash to the parking owner.`,
    });

    const ownerProfile = await this.prisma.ownerProfile.findUnique({ where: { id: booking.parking.owner_id } });
    if (ownerProfile) {
      await this.notificationsService.send({
        userId: ownerProfile.user_id,
        category: 'booking_status',
        type: 'CASH_PAYMENT_PENDING_OWNER',
        title: 'Cash payment pending',
        body: `Cash payment of ${Money.of(breakdown.driverTotalMinorUnits, breakdown.currency).toDisplayString()} is pending for Booking #${bookingId.slice(0, 8)}.`,
      });
    }

    const updated = await this.prisma.booking.findUniqueOrThrow({ where: { id: bookingId } });
    return toBookingView(updated);
  }

  private async canAccessBooking(
    user: AuthenticatedUser & { roles: string[] },
    booking: { driver_id: string; parking: { owner_id: string } },
  ): Promise<boolean> {
    if (user.roles.includes('ADMIN')) return true;
    if (booking.driver_id === user.id) return true;
    if (user.roles.includes('OWNER')) {
      const ownerProfile = await this.prisma.ownerProfile.findUnique({ where: { user_id: user.id } });
      if (ownerProfile && ownerProfile.id === booking.parking.owner_id) return true;
    }
    return false;
  }

  // ---------------------------------------------------------------------
  // Cancellation
  // ---------------------------------------------------------------------

  async cancelBooking(driverId: string, bookingId: string, dto: CancelBookingDto) {
    const booking = await this.prisma.booking.findUnique({ where: { id: bookingId } });
    if (!booking) throw new NotFoundException('Booking not found.');
    if (booking.driver_id !== driverId) throw new ForbiddenException('You do not have permission to cancel this booking.');

    return this.applyBookingTransition(bookingId, booking.status, 'CANCELLED', {
      actorId: driverId,
      actorRole: 'DRIVER',
      source: 'APP',
      reason: dto.reason ?? 'Cancelled by driver.',
    });
  }

  async adminCancelBooking(adminId: string, bookingId: string, reason: string) {
    const booking = await this.prisma.booking.findUnique({ where: { id: bookingId } });
    if (!booking) throw new NotFoundException('Booking not found.');

    return this.applyBookingTransition(bookingId, booking.status, 'ADMIN_CANCELLED', {
      actorId: adminId,
      actorRole: 'ADMIN',
      source: 'ADMIN',
      reason,
    });
  }

  /**
   * Called by Milestone 7's PaymentsService before creating a payment
   * order: applies the same lazy-expiry check used everywhere else in this
   * module (so a payment order can never be created against a booking
   * whose hold TTL has already silently lapsed), then enforces ownership
   * and status. Returns the raw Prisma row (not the trimmed view) because
   * PaymentsService needs section_id/parking_id/price_snapshot to compute
   * the payable breakdown.
   */
  async getPayableBooking(driverId: string, bookingId: string) {
    await this.expireHoldForBookingIfDue(bookingId);
    const booking = await this.prisma.booking.findUnique({
      where: { id: bookingId },
      include: { section: true },
    });
    if (!booking) throw new NotFoundException('Booking not found.');
    if (booking.driver_id !== driverId) throw new ForbiddenException('That booking does not belong to you.');
    if (booking.status !== 'PENDING_PAYMENT') {
      throw new ConflictException(`This booking is ${booking.status}, not awaiting payment.`);
    }
    return booking;
  }

  /**
   * Called by Milestone 7's payment webhook handler once a payment is
   * verified SUCCESSFUL, and by Milestone 8's cash-collect flow once an
   * attendant/owner confirms cash in hand. Intentionally not exposed by
   * any controller directly — there is no way for a driver to reach
   * CONFIRMED without one of these two real, verified paths, matching the
   * "no simulated payment success" rule.
   *
   * Accepts an optional `externalTx`: both callers need the
   * PaymentOrder/CashCollection update, the ledger's Transaction/
   * OwnerEarningsLedgerEntry rows, and this booking transition to commit
   * atomically together — passing an already-open
   * `Prisma.TransactionClient` through here (rather than this method
   * opening a second, independent transaction) is what makes that a single
   * all-or-nothing unit instead of separately-committed writes that could
   * disagree if the process died in between.
   *
   * `source`/`actorRole` default to the webhook path's values (a system
   * actor, no human actorRole) since that was this method's only caller
   * before Milestone 8; the cash-collect path passes the real human actor.
   */
  async markPaid(
    bookingId: string,
    actorId: string | null,
    source: BookingActorSource = 'WEBHOOK',
    externalTx?: Prisma.TransactionClient,
    actorRole: PrismaAppRole | null = null,
  ) {
    const client = externalTx ?? this.prisma;
    const booking = await client.booking.findUnique({ where: { id: bookingId } });
    if (!booking) throw new NotFoundException('Booking not found.');
    return this.applyBookingTransition(
      bookingId,
      booking.status,
      'CONFIRMED',
      {
        actorId,
        actorRole,
        source,
        reason: 'Payment verified successful.',
      },
      externalTx,
    );
  }

  /**
   * The single choke point for every status change (Milestone 0 §8): (a)
   * checks the transition is in the explicit allow-list, (b) uses a
   * conditional UPDATE keyed on the row's current status as the optimistic
   * lock — no separate `version` column needed, a duplicate call that
   * arrives after the first already moved the status is simply a no-op
   * (0 rows match `status = expectedCurrentStatus` the second time), (c)
   * releases section capacity atomically when the new status leaves the
   * "reserved" set, and (d) writes a booking_events row in the same
   * transaction.
   *
   * `externalTx`, when supplied (Milestone 7's markPaid path), makes this
   * run against a transaction the caller already opened instead of opening
   * its own — see markPaid's doc comment.
   */
  async applyBookingTransition(
    bookingId: string,
    expectedCurrentStatus: BookingStatus,
    newStatus: BookingStatus,
    actor: { actorId: string | null; actorRole: string | null; source: string; reason?: string; metadata?: Record<string, unknown> },
    externalTx?: Prisma.TransactionClient,
  ) {
    if (TERMINAL_BOOKING_STATUSES.has(expectedCurrentStatus)) {
      throw new ConflictException(`This booking is already ${expectedCurrentStatus} and cannot be changed further.`);
    }
    if (!isTransitionAllowed(expectedCurrentStatus, newStatus)) {
      throw new ConflictException(`Cannot move a booking from ${expectedCurrentStatus} to ${newStatus}.`);
    }

    // Milestone 0 §12: reserved_count = held/confirmed-but-not-yet-present;
    // occupied_count = physically checked in; a unit is in at most one of
    // the two at a time, so the only two capacity events that matter are
    // "did this transition leave the reserved bucket" and "did it leave or
    // enter the occupied bucket" — computed as -1/0/+1 deltas rather than a
    // single releasesCapacity boolean (which Milestone 6 only needed
    // because nothing reached the occupied bucket yet). Nothing in the
    // allow-list ever transitions *into* RESERVED_BOOKING_STATUSES from
    // outside it, so reservedDelta is always 0 or -1 in practice.
    const reservedDelta =
      (RESERVED_BOOKING_STATUSES.has(expectedCurrentStatus) ? -1 : 0) + (RESERVED_BOOKING_STATUSES.has(newStatus) ? 1 : 0);
    const occupiedDelta =
      (OCCUPIED_BOOKING_STATUSES.has(expectedCurrentStatus) ? -1 : 0) + (OCCUPIED_BOOKING_STATUSES.has(newStatus) ? 1 : 0);

    const run = async (tx: Prisma.TransactionClient) => {
      const result = await tx.booking.updateMany({
        where: { id: bookingId, status: expectedCurrentStatus },
        data: { status: newStatus },
      });
      if (result.count === 0) {
        throw new ConflictException('This booking was already updated by another request. Please refresh and try again.');
      }

      if (reservedDelta !== 0 || occupiedDelta !== 0) {
        const booking = await tx.booking.findUniqueOrThrow({ where: { id: bookingId } });
        await tx.$executeRaw`
          UPDATE section_availability
          SET reserved_count = GREATEST(reserved_count + ${reservedDelta}, 0),
              occupied_count = GREATEST(occupied_count + ${occupiedDelta}, 0),
              version = version + 1, updated_at = now()
          WHERE section_id = ${booking.section_id}
        `;
      }

      await tx.bookingEvent.create({
        data: {
          booking_id: bookingId,
          actor_id: actor.actorId,
          actor_role: actor.actorRole as PrismaAppRole | null,
          source: actor.source as BookingActorSource,
          old_status: expectedCurrentStatus,
          new_status: newStatus,
          reason: actor.reason,
          metadata: actor.metadata as Prisma.InputJsonValue | undefined,
        },
      });

      return toBookingView(await tx.booking.findUniqueOrThrow({ where: { id: bookingId } }));
    };

    const view = await (externalTx ? run(externalTx) : this.prisma.$transaction(run));

    // Best-effort, outside the transaction and never awaited into a
    // failure path — NotificationsService.send() already swallows its own
    // errors (Milestone 11's doc comment on that class), but the booking
    // transition itself must commit and return regardless of whether a
    // notification goes out at all.
    void this.notifyOnTransition(view.driverId, newStatus).catch(() => undefined);

    // Same best-effort rule as the notification above. Only fires when
    // capacity actually moved (not every status change does — e.g.
    // PARKING_ACTIVE/CHECKED_OUT don't touch reserved/occupied counts) so
    // an owner/attendant dashboard watching this listing doesn't get
    // spurious "availability changed" events for transitions that didn't
    // change any number it's showing.
    if (reservedDelta !== 0 || occupiedDelta !== 0) {
      try {
        this.realtimeGateway.emitToListing(view.parkingId, 'availability_changed', {
          sectionId: view.sectionId,
          bookingId: view.id,
          newStatus,
        });
      } catch (err) {
        this.logger.warn(`Realtime listing emit failed for parking ${view.parkingId}: ${(err as Error).message}`);
      }
    }

    return view;
  }

  /**
   * A single, generic hook off `applyBookingTransition` — the one choke
   * point every status change already passes through (Milestone 0 §8) —
   * rather than each of the dozen call sites across booking/qr/refunds
   * remembering to notify separately. Only the statuses a driver actually
   * benefits from hearing about proactively are covered; PARKING_ACTIVE
   * and CHECKED_OUT are intentionally silent (the driver is standing right
   * there for both).
   */
  private async notifyOnTransition(driverId: string, newStatus: BookingStatus): Promise<void> {
    const copy: Partial<Record<BookingStatus, { title: string; body: string }>> = {
      CONFIRMED: { title: 'Booking confirmed', body: 'Your parking booking is confirmed. Your entry pass is ready.' },
      CHECKED_IN: { title: "You're checked in", body: 'Enjoy your stay — remember to check out when you leave.' },
      COMPLETED: { title: 'Booking completed', body: 'Thanks for parking with ParkEase!' },
      CANCELLED: { title: 'Booking cancelled', body: 'Your booking has been cancelled.' },
      ADMIN_CANCELLED: { title: 'Booking cancelled', body: 'Your booking was cancelled by ParkEase support.' },
      PARKING_UNAVAILABLE: { title: 'Booking cancelled', body: 'This parking became unavailable — you have been refunded in full.' },
      EXPIRED: { title: 'Booking expired', body: 'Your booking expired before payment was completed.' },
      NO_SHOW: { title: 'Marked as no-show', body: "You didn't check in for your booking in time." },
      VEHICLE_MISMATCH: { title: 'Vehicle mismatch reported', body: 'A mismatch was reported for your parked vehicle.' },
      REJECTED: { title: 'Entry rejected', body: 'Entry was rejected for your booking — you have been refunded.' },
    };
    const message = copy[newStatus];
    if (!message) return;

    await this.notificationsService.send({
      userId: driverId,
      category: 'booking_status',
      type: `BOOKING_${newStatus}`,
      title: message.title,
      body: message.body,
    });
  }

  // ---------------------------------------------------------------------
  // Expiry sweeps (lazy — the correctness guarantee; BookingExpiryProducer
  // is only a promptness optimization on top of this)
  // ---------------------------------------------------------------------

  private async expireHoldForBookingIfDue(bookingId: string) {
    const hold = await this.prisma.bookingHold.findUnique({ where: { booking_id: bookingId } });
    // Nothing to do — INSTANT/unconfirmed-hold bookings aside, most
    // bookings keep their hold row linked but it's already "used" (not
    // expirable) once booking_id is set; expireHoldIfDue is a no-op then.
    if (hold) await this.expireHoldIfDue(hold.id);
    await this.expireBookingIfDue(bookingId);
  }

  private async expireBookingIfDue(bookingId: string) {
    const booking = await this.prisma.booking.findUnique({ where: { id: bookingId } });
    if (!booking || booking.status !== 'PENDING_PAYMENT') return;
    // A PENDING_PAYMENT booking expires once its originating hold's TTL
    // has passed and no payment has landed — there is no separate
    // booking-level TTL field, the hold's expires_at is the single source
    // of truth for "how long can this sit unpaid."
    const hold = await this.prisma.bookingHold.findUnique({ where: { booking_id: bookingId } });
    if (!hold || hold.expires_at.getTime() > Date.now()) return;
    await this.applyBookingTransition(bookingId, 'PENDING_PAYMENT', 'EXPIRED', {
      actorId: null,
      actorRole: null,
      source: 'SYSTEM_JOB',
      reason: 'Payment window elapsed.',
    }).catch((err) => this.logger.warn(`Failed to auto-expire booking ${bookingId}: ${(err as Error).message}`));
  }

  private async expireStaleBookings() {
    const stale = await this.prisma.booking.findMany({
      where: { status: 'PENDING_PAYMENT' },
      include: { hold: true },
      take: 100,
    });
    for (const booking of stale) {
      if (booking.hold && booking.hold.expires_at.getTime() <= Date.now()) {
        await this.expireBookingIfDue(booking.id);
      }
    }
  }
}

// ---------------------------------------------------------------------
// Compatibility + view helpers
// ---------------------------------------------------------------------

function assertBookable(
  section: { approval_status: string; status: string },
  listing: { approval_status: string; status: string },
) {
  if (listing.approval_status !== 'APPROVED' || listing.status !== 'ACTIVE') {
    throw new ConflictException('This parking listing is not currently accepting bookings.');
  }
  if (section.approval_status !== 'APPROVED' || section.status !== 'ACTIVE') {
    throw new ConflictException('This section is not currently accepting bookings.');
  }
}

/**
 * Server-authoritative mirror of the Android client's checkCompatibility()
 * (core-model/VehicleCompatibility.kt) — category and vehicle type only;
 * there is no section-level max-vehicle-size field in the current ERD (a
 * known Milestone 0 gap, tracked but not blocking booking correctness for
 * the two dimensions that are modeled).
 */
function assertCompatible(
  vehicleCategory: VehicleCategory,
  vehicleType: VehicleType,
  sectionCategory: VehicleCategory,
  supportedTypes: VehicleType[],
) {
  if (vehicleCategory !== sectionCategory) {
    throw new BadRequestException('This vehicle is not compatible with this section (category mismatch).');
  }
  if (!supportedTypes.includes(vehicleType)) {
    throw new BadRequestException('This vehicle type is not supported by this section.');
  }
}

/**
 * Shared enrichment for toBookingView's optional display fields — used by
 * every listBookings branch and getBooking so the owner's booking list
 * (customer contact, vehicle plate, cash-collection status) never needs a
 * second round trip. cash_collections is a to-many relation but a booking
 * only ever has 0 or 1 (cashCollect's PENDING_PAYMENT-only guard makes a
 * second one impossible), so toBookingView just reads index 0.
 */
const BOOKING_VIEW_INCLUDE = {
  vehicle: { select: { registration_number: true } },
  driver: { select: { email: true, phone: true } },
  parking: { select: { name: true, owner_id: true } },
  cash_collections: { select: { amount: true, confirmed_at: true } },
} satisfies Prisma.BookingInclude;

function toBookingView(b: {
  id: string;
  driver_id: string;
  vehicle_id: string;
  parking_id: string;
  section_id: string;
  vehicle_category: string;
  booking_type: string;
  status: string;
  intended_payment_method?: string | null;
  start_time: Date | null;
  end_time: Date | null;
  actual_check_in_at: Date | null;
  actual_check_out_at: Date | null;
  price_snapshot: unknown;
  created_at: Date;
  // Optional — only populated when the caller's query actually included
  // these relations (listBookings' OWNER branch and getBooking do; the
  // lighter fetches in payCash/markPaid don't need to). Never required,
  // so every existing call site keeps compiling unchanged.
  vehicle?: { registration_number: string } | null;
  // User has no name field (registration's fullName is collected but not
  // persisted — a separate pre-existing gap, not this feature's to fix) —
  // email/phone is the same "who is this" fallback the admin users list
  // already uses.
  driver?: { phone: string | null; email: string | null } | null;
  parking?: { name: string } | null;
  cash_collections?: { amount: bigint; confirmed_at: Date | null }[];
}) {
  const cashCollection = b.cash_collections?.[0];
  return {
    id: b.id,
    driverId: b.driver_id,
    vehicleId: b.vehicle_id,
    parkingId: b.parking_id,
    sectionId: b.section_id,
    vehicleCategory: b.vehicle_category,
    bookingType: b.booking_type,
    status: b.status,
    intendedPaymentMethod: b.intended_payment_method ?? null,
    startTime: b.start_time,
    endTime: b.end_time,
    actualCheckInAt: b.actual_check_in_at,
    actualCheckOutAt: b.actual_check_out_at,
    priceSnapshot: b.price_snapshot,
    createdAt: b.created_at,
    vehicleRegistrationNumber: b.vehicle?.registration_number ?? null,
    driverContact: b.driver?.email ?? b.driver?.phone ?? null,
    parkingName: b.parking?.name ?? null,
    cashAmountMinorUnits: cashCollection ? cashCollection.amount.toString() : null,
    cashConfirmedAt: cashCollection?.confirmed_at ?? null,
  };
}
