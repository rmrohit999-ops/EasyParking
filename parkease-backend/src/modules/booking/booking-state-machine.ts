import { BookingStatus } from '@prisma/client';

/**
 * Explicit allow-list transcribed directly from Milestone 0 §8's state
 * diagram. Anything not listed here is refused — `applyBookingTransition`
 * (booking.service.ts) never falls through to an implicit "anything goes."
 */
export const BOOKING_TRANSITIONS: Record<BookingStatus, BookingStatus[]> = {
  PENDING_PAYMENT: ['CONFIRMED', 'EXPIRED', 'CANCELLED'],
  CONFIRMED: ['DRIVER_ARRIVING', 'CHECKED_IN', 'CANCELLED', 'NO_SHOW', 'ADMIN_CANCELLED', 'PARKING_UNAVAILABLE'],
  DRIVER_ARRIVING: ['CHECKED_IN'],
  CHECKED_IN: ['PARKING_ACTIVE'],
  PARKING_ACTIVE: ['VEHICLE_MISMATCH', 'CHECKED_OUT'],
  VEHICLE_MISMATCH: ['PARKING_ACTIVE', 'REJECTED'],
  CHECKED_OUT: ['COMPLETED'],
  COMPLETED: [],
  REJECTED: [],
  EXPIRED: [],
  CANCELLED: [],
  NO_SHOW: [],
  ADMIN_CANCELLED: [],
  PARKING_UNAVAILABLE: [],
};

export const TERMINAL_BOOKING_STATUSES: ReadonlySet<BookingStatus> = new Set([
  'COMPLETED',
  'REJECTED',
  'EXPIRED',
  'CANCELLED',
  'NO_SHOW',
  'ADMIN_CANCELLED',
  'PARKING_UNAVAILABLE',
] satisfies BookingStatus[]);

/**
 * Statuses where the booking still holds section capacity (i.e. counted in
 * `section_availability.reserved_count` or `.occupied_count`) — used to
 * decide whether a transition needs to release capacity atomically
 * alongside the status change, per Milestone 0 §8's "capacity release
 * happens atomically alongside every transition that leaves a section."
 */
export const RESERVED_BOOKING_STATUSES: ReadonlySet<BookingStatus> = new Set([
  'PENDING_PAYMENT',
  'CONFIRMED',
  'DRIVER_ARRIVING',
] satisfies BookingStatus[]);

export const OCCUPIED_BOOKING_STATUSES: ReadonlySet<BookingStatus> = new Set([
  'CHECKED_IN',
  'PARKING_ACTIVE',
  'VEHICLE_MISMATCH',
] satisfies BookingStatus[]);

export function isTransitionAllowed(from: BookingStatus, to: BookingStatus): boolean {
  return BOOKING_TRANSITIONS[from]?.includes(to) ?? false;
}
