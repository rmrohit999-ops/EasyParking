import { ForbiddenException, Injectable, NotFoundException } from '@nestjs/common';
import { DisputeStatus } from '@prisma/client';
import { PrismaService } from '../../common/prisma/prisma.service';
import { AuthenticatedUser } from '../../common/decorators/current-user.decorator';
import { AuditService } from '../audit/audit.service';
import { RefundsService } from '../refunds/refunds.service';
import { NotificationsService } from '../notifications/notifications.service';
import { AddDisputeEvidenceDto, FileDisputeDto, ResolveDisputeDto } from './dto/disputes.dto';

/**
 * DisputesModule — real (Milestone 10). `resolveDispute` is the first
 * caller of RefundsService.adminManualRefund (Milestone 9) — the two
 * milestones' features click together here: a resolved-in-the-driver's-
 * favour dispute is exactly the "admin discretionary refund without
 * changing the booking's status" case that method's doc comment already
 * anticipated. DisputesModule imports RefundsModule; RefundsModule never
 * imports this one, so the dependency graph stays a DAG.
 */
@Injectable()
export class DisputesService {
  constructor(
    private readonly prisma: PrismaService,
    private readonly auditService: AuditService,
    private readonly refundsService: RefundsService,
    private readonly notificationsService: NotificationsService,
  ) {}

  async fileDispute(user: AuthenticatedUser & { roles: string[] }, dto: FileDisputeDto) {
    const booking = await this.prisma.booking.findUnique({ where: { id: dto.bookingId }, include: { parking: true } });
    if (!booking) throw new NotFoundException('Booking not found.');
    await this.assertCanAccessBooking(user, booking);

    const dispute = await this.prisma.dispute.create({
      data: { booking_id: dto.bookingId, filed_by: user.id, dispute_type: dto.disputeType, status: 'OPEN' },
    });
    await this.prisma.disputeEvidence.create({
      data: {
        dispute_id: dispute.id,
        evidence_type: 'MESSAGE',
        reference_id: dto.explanation,
        added_by: user.id,
        visibility: 'PARTIES',
      },
    });
    return this.getDispute(user, dispute.id);
  }

  async addEvidence(user: AuthenticatedUser & { roles: string[] }, disputeId: string, dto: AddDisputeEvidenceDto) {
    const dispute = await this.prisma.dispute.findUnique({ where: { id: disputeId }, include: { booking: { include: { parking: true } } } });
    if (!dispute) throw new NotFoundException('Dispute not found.');
    const isAdmin = user.roles.includes('ADMIN');
    if (!isAdmin) await this.assertCanAccessBooking(user, dispute.booking);
    if (dispute.status === 'RESOLVED' || dispute.status === 'REJECTED') {
      throw new ForbiddenException('This dispute is already closed.');
    }

    await this.prisma.disputeEvidence.create({
      data: {
        dispute_id: disputeId,
        evidence_type: dto.evidenceType,
        reference_id: dto.referenceId,
        storage_key: dto.storageKey,
        added_by: user.id,
        visibility: isAdmin ? (dto.visibility ?? 'ADMIN_ONLY') : 'PARTIES',
      },
    });
    if (dispute.status === 'OPEN') {
      await this.prisma.dispute.update({ where: { id: disputeId }, data: { status: 'UNDER_REVIEW' } });
    }
    return this.getDispute(user, disputeId);
  }

  async listMine(user: AuthenticatedUser) {
    const disputes = await this.prisma.dispute.findMany({ where: { filed_by: user.id }, orderBy: { created_at: 'desc' } });
    return disputes.map(toDisputeView);
  }

  async listAll(status?: DisputeStatus) {
    const disputes = await this.prisma.dispute.findMany({ where: status ? { status } : {}, orderBy: { created_at: 'desc' }, take: 200 });
    return disputes.map(toDisputeView);
  }

  async getDispute(user: AuthenticatedUser & { roles: string[] }, disputeId: string) {
    const dispute = await this.prisma.dispute.findUnique({
      where: { id: disputeId },
      include: { booking: { include: { parking: true } }, evidence: { orderBy: { added_at: 'asc' } } },
    });
    if (!dispute) throw new NotFoundException('Dispute not found.');
    const isAdmin = user.roles.includes('ADMIN');
    if (!isAdmin) await this.assertCanAccessBooking(user, dispute.booking);

    // ADMIN_ONLY evidence is never shown to the filer/owner — it exists
    // for exactly that reason (Milestone 0 ERD's EvidenceVisibility enum).
    const evidence = dispute.evidence.filter((e) => isAdmin || e.visibility === 'PARTIES');
    return { ...toDisputeView(dispute), evidence: evidence.map(toEvidenceView) };
  }

  async resolveDispute(adminId: string, disputeId: string, dto: ResolveDisputeDto) {
    const dispute = await this.prisma.dispute.findUnique({ where: { id: disputeId } });
    if (!dispute) throw new NotFoundException('Dispute not found.');
    if (dispute.status === 'RESOLVED' || dispute.status === 'REJECTED') {
      throw new ForbiddenException(`This dispute is already ${dispute.status}.`);
    }

    const updated = await this.prisma.dispute.update({
      where: { id: disputeId },
      data: { status: dto.status, resolution: dto.resolution, resolved_by: adminId, resolved_at: new Date() },
    });

    if (dto.status === 'RESOLVED' && dto.refundPercent && dto.refundPercent > 0) {
      await this.refundsService.adminManualRefund(adminId, dispute.booking_id, {
        reasonCode: 'ADMIN_APPROVED',
        refundPercent: dto.refundPercent,
        reason: `Dispute ${disputeId} resolved: ${dto.resolution}`,
      });
    }

    await this.auditService.record({
      actorId: adminId,
      actorRole: 'ADMIN',
      action: 'RESOLVE_DISPUTE',
      targetType: 'Dispute',
      targetId: disputeId,
      beforeState: { status: dispute.status },
      afterState: { status: updated.status, resolution: updated.resolution, refundPercent: dto.refundPercent ?? 0 },
    });

    // Best-effort (Milestone 11): the filer learns the outcome even when
    // no refund was issued (e.g. status: 'REJECTED', or RESOLVED with a
    // 0% refund) — RefundsService.adminManualRefund above already sends
    // its own "Refund issued" notification when a refund does happen, so
    // this is deliberately about the dispute's own status, not the money.
    await this.notificationsService.send({
      userId: dispute.filed_by,
      category: 'dispute',
      type: `DISPUTE_${updated.status}`,
      title: updated.status === 'RESOLVED' ? 'Your dispute has been resolved' : 'Your dispute was reviewed',
      body: updated.resolution ?? `Status: ${updated.status}.`,
      deepLink: `parkease://disputes/${disputeId}`,
    });

    return toDisputeView(updated);
  }

  private async assertCanAccessBooking(user: AuthenticatedUser & { roles: string[] }, booking: { driver_id: string; parking: { owner_id: string } }) {
    if (user.roles.includes('ADMIN')) return;
    if (booking.driver_id === user.id) return;
    if (user.roles.includes('OWNER')) {
      const ownerProfile = await this.prisma.ownerProfile.findUnique({ where: { user_id: user.id } });
      if (ownerProfile && ownerProfile.id === booking.parking.owner_id) return;
    }
    throw new ForbiddenException('You do not have permission to access this dispute.');
  }
}

function toDisputeView(d: {
  id: string;
  booking_id: string;
  filed_by: string;
  dispute_type: string;
  status: string;
  resolution: string | null;
  resolved_by: string | null;
  created_at: Date;
  resolved_at: Date | null;
}) {
  return {
    id: d.id,
    bookingId: d.booking_id,
    filedBy: d.filed_by,
    disputeType: d.dispute_type,
    status: d.status,
    resolution: d.resolution,
    resolvedBy: d.resolved_by,
    createdAt: d.created_at,
    resolvedAt: d.resolved_at,
  };
}

function toEvidenceView(e: {
  id: string;
  dispute_id: string;
  evidence_type: string;
  storage_key: string | null;
  reference_id: string | null;
  added_by: string | null;
  added_at: Date;
  visibility: string;
}) {
  return {
    id: e.id,
    disputeId: e.dispute_id,
    evidenceType: e.evidence_type,
    storageKey: e.storage_key,
    referenceId: e.reference_id,
    addedBy: e.added_by,
    addedAt: e.added_at,
    visibility: e.visibility,
  };
}
