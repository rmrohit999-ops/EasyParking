import { ConflictException, ForbiddenException, NotFoundException } from '@nestjs/common';
import { BookingService } from '../booking.service';

/**
 * Real coverage for the first actual read/write path against the Review
 * model — it existed unused in the schema since Milestone 0. Guards under
 * test: only the booking's own driver can review it, only once it's
 * actually COMPLETED (never a speculative pre-visit rating), and never
 * more than one review per booking.
 */
function buildFakePrisma(bookings: Record<string, any>) {
  const reviews: Record<string, any> = {};
  let reviewCounter = 0;

  const bookingDelegate = { findUnique: async ({ where }: any) => bookings[where.id] ?? null };
  const reviewDelegate = {
    findUnique: async ({ where }: any) => reviews[where.booking_id] ?? null,
    create: async ({ data }: any) => {
      const record = { id: `review-${++reviewCounter}`, status: 'ACTIVE', created_at: new Date(), ...data };
      reviews[data.booking_id] = record;
      return record;
    },
  };

  return { prisma: { booking: bookingDelegate, review: reviewDelegate } as any, reviews };
}

function buildService(prisma: any) {
  return new BookingService(
    prisma,
    { get: () => ({}) } as any,
    { scheduleExpiry: async () => undefined } as any,
    { send: async () => undefined } as any,
    { emitToUser: () => undefined, emitToListing: () => undefined } as any,
  );
}

describe('BookingService.submitReview', () => {
  it('rejects a review from someone other than the booking\'s own driver', async () => {
    const bookings = { 'booking-1': { id: 'booking-1', driver_id: 'driver-1', parking_id: 'parking-1', status: 'COMPLETED' } };
    const { prisma } = buildFakePrisma(bookings);
    const service = buildService(prisma);

    await expect(service.submitReview('driver-2', 'booking-1', { overall: 5 })).rejects.toThrow(ForbiddenException);
  });

  it('rejects a review for a booking that has not completed', async () => {
    const bookings = { 'booking-1': { id: 'booking-1', driver_id: 'driver-1', parking_id: 'parking-1', status: 'PARKING_ACTIVE' } };
    const { prisma } = buildFakePrisma(bookings);
    const service = buildService(prisma);

    await expect(service.submitReview('driver-1', 'booking-1', { overall: 5 })).rejects.toThrow(ConflictException);
  });

  it('rejects a booking that does not exist', async () => {
    const { prisma } = buildFakePrisma({});
    const service = buildService(prisma);

    await expect(service.submitReview('driver-1', 'missing', { overall: 5 })).rejects.toThrow(NotFoundException);
  });

  it('accepts a real review for a completed booking, storing the full ratings breakdown', async () => {
    const bookings = { 'booking-1': { id: 'booking-1', driver_id: 'driver-1', parking_id: 'parking-1', status: 'COMPLETED' } };
    const { prisma, reviews } = buildFakePrisma(bookings);
    const service = buildService(prisma);

    const result = await service.submitReview('driver-1', 'booking-1', { overall: 4, cleanliness: 5, comment: 'Great spot' });

    expect(result.bookingId).toBe('booking-1');
    expect(result.ratings).toEqual({ overall: 4, cleanliness: 5, security: null, location: null });
    expect(reviews['booking-1'].comment).toBe('Great spot');
  });

  it('refuses a second review for the same booking', async () => {
    const bookings = { 'booking-1': { id: 'booking-1', driver_id: 'driver-1', parking_id: 'parking-1', status: 'COMPLETED' } };
    const { prisma } = buildFakePrisma(bookings);
    const service = buildService(prisma);

    await service.submitReview('driver-1', 'booking-1', { overall: 4 });

    await expect(service.submitReview('driver-1', 'booking-1', { overall: 2 })).rejects.toThrow(ConflictException);
  });
});

describe('BookingService.getMyReview', () => {
  it('returns null when the caller never reviewed this booking', async () => {
    const { prisma } = buildFakePrisma({});
    const service = buildService(prisma);

    expect(await service.getMyReview('driver-1', 'booking-1')).toBeNull();
  });

  it('never returns another driver\'s review', async () => {
    const bookings = { 'booking-1': { id: 'booking-1', driver_id: 'driver-1', parking_id: 'parking-1', status: 'COMPLETED' } };
    const { prisma } = buildFakePrisma(bookings);
    const service = buildService(prisma);
    await service.submitReview('driver-1', 'booking-1', { overall: 4 });

    expect(await service.getMyReview('driver-2', 'booking-1')).toBeNull();
    expect(await service.getMyReview('driver-1', 'booking-1')).not.toBeNull();
  });
});
