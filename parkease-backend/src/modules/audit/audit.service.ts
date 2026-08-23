import { Injectable } from '@nestjs/common';
import { AppRole, Prisma } from '@prisma/client';
import { PrismaService } from '../../common/prisma/prisma.service';

/**
 * AuditModule — real (Milestone 10). `audit_logs` is append-only (the
 * Prisma schema's own doc comment: "No update/delete API is ever exposed
 * for this table") — this service is the only writer, and it only ever
 * inserts. Every admin-mutating action elsewhere in this milestone (user
 * suspend/reinstate, dispute resolution, fraud alert review, config
 * changes) calls `record()` in the same request, so the log is a
 * byproduct of the action happening, not something a human has to
 * remember to file separately.
 */
@Injectable()
export class AuditService {
  constructor(private readonly prisma: PrismaService) {}

  async record(params: {
    actorId: string | null;
    actorRole: AppRole | null;
    action: string;
    targetType: string;
    targetId: string;
    beforeState?: Record<string, unknown> | null;
    afterState?: Record<string, unknown> | null;
    ipAddress?: string | null;
    correlationId?: string | null;
  }) {
    return this.prisma.auditLog.create({
      data: {
        actor_id: params.actorId,
        actor_role: params.actorRole,
        action: params.action,
        target_type: params.targetType,
        target_id: params.targetId,
        before_state: (params.beforeState ?? undefined) as Prisma.InputJsonValue | undefined,
        after_state: (params.afterState ?? undefined) as Prisma.InputJsonValue | undefined,
        ip_address: params.ipAddress ?? null,
        correlation_id: params.correlationId ?? null,
      },
    });
  }

  async list(filter: { targetType?: string; targetId?: string; actorId?: string }) {
    return this.prisma.auditLog.findMany({
      where: {
        target_type: filter.targetType,
        target_id: filter.targetId,
        actor_id: filter.actorId,
      },
      orderBy: { created_at: 'desc' },
      take: 200,
    });
  }
}
