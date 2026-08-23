import { BadRequestException, Injectable, NotFoundException } from '@nestjs/common';
import { FraudAlertStatus, FraudSubjectType, Prisma } from '@prisma/client';
import { PrismaService } from '../../common/prisma/prisma.service';
import { AuditService } from '../audit/audit.service';
import { ReviewFraudAlertDto } from './dto/fraud.dto';

const ALERT_THRESHOLD_SCORE = 50;

/**
 * Real signal computation from data this codebase actually has, not a
 * placeholder scorer — every number in `explanation` traces back to a
 * genuine query, matching the Prisma schema's own comment on
 * `FraudSignal.explanation`: "must be human-readable, never a black box".
 * USER and BOOKING subjects are implemented; LISTING/REVIEW scans need
 * photo-hash-similarity and rating-manipulation heuristics this milestone
 * doesn't build — `scanListing`/`scanReview` say so explicitly rather than
 * silently returning an empty/zero signal that would look like "checked,
 * found nothing."
 */
@Injectable()
export class FraudService {
  constructor(
    private readonly prisma: PrismaService,
    private readonly auditService: AuditService,
  ) {}

  // ---------------------------------------------------------------------
  // Scans
  // ---------------------------------------------------------------------

  async scanUser(userId: string) {
    const user = await this.prisma.user.findUnique({ where: { id: userId } });
    if (!user) throw new NotFoundException('User not found.');

    const bookings = await this.prisma.booking.findMany({
      where: { driver_id: userId },
      select: { status: true, created_at: true },
    });
    const total = bookings.length;
    const noShowCount = bookings.filter((b) => b.status === 'NO_SHOW').length;
    const cancelledCount = bookings.filter((b) => b.status === 'CANCELLED').length;
    const rejectedCount = bookings.filter((b) => b.status === 'REJECTED').length;
    const dayAgo = new Date(Date.now() - 24 * 60 * 60 * 1000);
    const recentBookingCount = bookings.filter((b) => b.created_at >= dayAgo).length;

    const noShowRate = total > 0 ? noShowCount / total : 0;
    const cancelRate = total > 0 ? cancelledCount / total : 0;
    const rejectedRate = total > 0 ? rejectedCount / total : 0;
    const rapidBookingFlag = recentBookingCount > 5;

    // Rates only count once there's enough history to be meaningful (>=3
    // bookings) — a single cancelled first booking should never look like
    // a 100% cancellation rate.
    const enoughHistory = total >= 3;
    const score = Math.min(
      100,
      Math.round(
        (enoughHistory ? noShowRate * 40 + cancelRate * 20 + rejectedRate * 30 : 0) + (rapidBookingFlag ? 20 : 0),
      ),
    );

    const explanation = {
      totalBookings: total,
      noShowCount,
      noShowRate: round2(noShowRate),
      cancelledCount,
      cancelRate: round2(cancelRate),
      rejectedCount,
      rejectedRate: round2(rejectedRate),
      recentBookingCount24h: recentBookingCount,
      rapidBookingFlag,
      enoughHistoryForRates: enoughHistory,
    };

    return this.recordSignal('USER', userId, 'behavioral_score', score, explanation);
  }

  async scanBooking(bookingId: string) {
    const booking = await this.prisma.booking.findUnique({ where: { id: bookingId } });
    if (!booking) throw new NotFoundException('Booking not found.');

    const mismatchEvents = await this.prisma.vehicleMismatchEvent.count({ where: { booking_id: bookingId } });
    const failedRefunds = await this.prisma.refund.count({ where: { booking_id: bookingId, status: 'FAILED' } });
    const checkEventMismatches = await this.prisma.checkEvent.count({
      where: { booking_id: bookingId, verification_outcome: { in: ['CATEGORY_MISMATCH', 'VEHICLE_NUMBER_MISMATCH'] } },
    });

    const score = Math.min(100, mismatchEvents * 30 + checkEventMismatches * 20 + failedRefunds * 15);
    const explanation = { mismatchEvents, checkEventMismatches, failedRefunds };

    return this.recordSignal('BOOKING', bookingId, 'mismatch_pattern_score', score, explanation);
  }

  async scanListing(_listingId: string): Promise<never> {
    throw new BadRequestException(
      'Listing fraud scanning (photo-hash similarity, duplicate-listing detection) is not implemented in this build — ' +
        'PhotoHash rows are recorded at photo upload (Milestone 4) but nothing yet compares them across listings.',
    );
  }

  async scanReview(_reviewId: string): Promise<never> {
    throw new BadRequestException(
      'Review fraud scanning (rating-manipulation detection) is not implemented in this build.',
    );
  }

  private async recordSignal(
    subjectType: FraudSubjectType,
    subjectId: string,
    signalType: string,
    score: number,
    explanation: Record<string, unknown>,
  ) {
    const signal = await this.prisma.fraudSignal.create({
      data: {
        subject_type: subjectType,
        subject_id: subjectId,
        signal_type: signalType,
        score,
        explanation: explanation as Prisma.InputJsonValue,
      },
    });

    let alert = null;
    if (score >= ALERT_THRESHOLD_SCORE) {
      const existingOpenAlert = await this.prisma.fraudAlert.findFirst({
        where: {
          status: { in: ['OPEN', 'UNDER_REVIEW'] },
          fraud_signal: { subject_type: subjectType, subject_id: subjectId },
        },
      });
      if (!existingOpenAlert) {
        alert = await this.prisma.fraudAlert.create({ data: { fraud_signal_id: signal.id, status: 'OPEN' } });
      } else {
        alert = existingOpenAlert;
      }
    }

    return { signal: toSignalView(signal), alert: alert ? toAlertView(alert) : null };
  }

  // ---------------------------------------------------------------------
  // Alerts
  // ---------------------------------------------------------------------

  async listAlerts(status?: FraudAlertStatus) {
    const alerts = await this.prisma.fraudAlert.findMany({
      where: status ? { status } : {},
      include: { fraud_signal: true },
      orderBy: { fraud_signal: { computed_at: 'desc' } },
      take: 200,
    });
    return alerts.map((a) => ({ ...toAlertView(a), signal: toSignalView(a.fraud_signal) }));
  }

  async reviewAlert(adminId: string, alertId: string, dto: ReviewFraudAlertDto) {
    const existing = await this.prisma.fraudAlert.findUnique({ where: { id: alertId } });
    if (!existing) throw new NotFoundException('Fraud alert not found.');

    const updated = await this.prisma.fraudAlert.update({
      where: { id: alertId },
      data: { status: dto.status, reviewed_by: adminId, reviewed_at: new Date(), action_taken: dto.actionTaken },
    });
    await this.auditService.record({
      actorId: adminId,
      actorRole: 'ADMIN',
      action: 'REVIEW_FRAUD_ALERT',
      targetType: 'FraudAlert',
      targetId: alertId,
      beforeState: { status: existing.status },
      afterState: { status: updated.status, actionTaken: updated.action_taken },
    });
    return toAlertView(updated);
  }
}

function round2(n: number): number {
  return Math.round(n * 100) / 100;
}

function toSignalView(s: { id: string; subject_type: string; subject_id: string; signal_type: string; score: number; explanation: Prisma.JsonValue; computed_at: Date }) {
  return {
    id: s.id,
    subjectType: s.subject_type,
    subjectId: s.subject_id,
    signalType: s.signal_type,
    score: s.score,
    explanation: s.explanation,
    computedAt: s.computed_at,
  };
}

function toAlertView(a: { id: string; fraud_signal_id: string; status: string; reviewed_by: string | null; reviewed_at: Date | null; action_taken: string | null }) {
  return {
    id: a.id,
    fraudSignalId: a.fraud_signal_id,
    status: a.status,
    reviewedBy: a.reviewed_by,
    reviewedAt: a.reviewed_at,
    actionTaken: a.action_taken,
  };
}
