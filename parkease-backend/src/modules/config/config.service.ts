import { Injectable, NotFoundException } from '@nestjs/common';
import { PolicyScope, Prisma } from '@prisma/client';
import { PrismaService } from '../../common/prisma/prisma.service';
import { AuditService } from '../audit/audit.service';
import {
  CreateCancellationPolicyDto,
  CreateCommissionPolicyDto,
  CreateFeatureFlagDto,
  CreateTaxPolicyDto,
  UpdateFeatureFlagDto,
} from './dto/config.dto';

/**
 * Admin CRUD over the pricing/refund policy tables `pricing.ts`
 * (Milestone 7) and `refunds.service.ts` (Milestone 9) already read from —
 * this is Milestone 10's missing other half: those services could only
 * ever fall back to env defaults because nothing could create a policy
 * row until now. Every policy type here is create-only + supersede, never
 * update-in-place: creating a new row with a later `effectiveFrom` and
 * leaving the old one's `effectiveTo` open (or explicitly closing it) is
 * how a policy changes over time without losing the history of what rate
 * applied when a given transaction/refund was computed — the same
 * versioned-policy shape the ERD's own `effective_from`/`effective_to`
 * columns already imply.
 */
@Injectable()
export class ConfigService {
  constructor(
    private readonly prisma: PrismaService,
    private readonly auditService: AuditService,
  ) {}

  // ---------------------------------------------------------------------
  // Commission policy
  // ---------------------------------------------------------------------

  async createCommissionPolicy(adminId: string, dto: CreateCommissionPolicyDto) {
    const created = await this.prisma.commissionPolicy.create({
      data: {
        scope: dto.scope as PolicyScope,
        scope_ref_id: dto.scopeRefId,
        commission_percent: dto.commissionPercent,
        fixed_fee: BigInt(dto.fixedFeeMinorUnits ?? 0),
        min_commission: BigInt(dto.minCommissionMinorUnits ?? 0),
        effective_from: dto.effectiveFrom ? new Date(dto.effectiveFrom) : new Date(),
        created_by: adminId,
      },
    });
    await this.auditService.record({
      actorId: adminId,
      actorRole: 'ADMIN',
      action: 'CREATE_COMMISSION_POLICY',
      targetType: 'CommissionPolicy',
      targetId: created.id,
      afterState: toJson(created),
    });
    return toCommissionPolicyView(created);
  }

  async listCommissionPolicies() {
    const policies = await this.prisma.commissionPolicy.findMany({ orderBy: { effective_from: 'desc' }, take: 100 });
    return policies.map(toCommissionPolicyView);
  }

  // ---------------------------------------------------------------------
  // Tax policy
  // ---------------------------------------------------------------------

  async createTaxPolicy(adminId: string, dto: CreateTaxPolicyDto) {
    const created = await this.prisma.taxPolicy.create({
      data: {
        scope: dto.scope as PolicyScope,
        scope_ref_id: dto.scopeRefId,
        tax_type: dto.taxType ?? 'GST',
        rate_percent: dto.ratePercent,
        inclusive: dto.inclusive ?? false,
        effective_from: dto.effectiveFrom ? new Date(dto.effectiveFrom) : new Date(),
        created_by: adminId,
      },
    });
    await this.auditService.record({
      actorId: adminId,
      actorRole: 'ADMIN',
      action: 'CREATE_TAX_POLICY',
      targetType: 'TaxPolicy',
      targetId: created.id,
      afterState: toJson(created),
    });
    return toTaxPolicyView(created);
  }

  async listTaxPolicies() {
    const policies = await this.prisma.taxPolicy.findMany({ orderBy: { effective_from: 'desc' }, take: 100 });
    return policies.map(toTaxPolicyView);
  }

  // ---------------------------------------------------------------------
  // Cancellation policy — `rules` is the `{ tiers: [...] }` shape
  // refunds.service.ts's `extractCancellationTiers` already parses.
  // ---------------------------------------------------------------------

  async createCancellationPolicy(adminId: string, dto: CreateCancellationPolicyDto) {
    const created = await this.prisma.cancellationPolicy.create({
      data: {
        scope: dto.scope as PolicyScope,
        scope_ref_id: dto.scopeRefId,
        rules: { tiers: dto.tiers } as unknown as Prisma.InputJsonValue,
        effective_from: dto.effectiveFrom ? new Date(dto.effectiveFrom) : new Date(),
        created_by: adminId,
      },
    });
    await this.auditService.record({
      actorId: adminId,
      actorRole: 'ADMIN',
      action: 'CREATE_CANCELLATION_POLICY',
      targetType: 'CancellationPolicy',
      targetId: created.id,
      afterState: toJson(created),
    });
    return toCancellationPolicyView(created);
  }

  async listCancellationPolicies() {
    const policies = await this.prisma.cancellationPolicy.findMany({ orderBy: { effective_from: 'desc' }, take: 100 });
    return policies.map(toCancellationPolicyView);
  }

  // ---------------------------------------------------------------------
  // Feature flags
  // ---------------------------------------------------------------------

  async createFeatureFlag(adminId: string, dto: CreateFeatureFlagDto) {
    const created = await this.prisma.featureFlag.create({
      data: {
        key: dto.key,
        description: dto.description,
        enabled_globally: dto.enabledGlobally ?? false,
        rollout_rules: (dto.rolloutRules ?? undefined) as Prisma.InputJsonValue | undefined,
      },
    });
    await this.auditService.record({
      actorId: adminId,
      actorRole: 'ADMIN',
      action: 'CREATE_FEATURE_FLAG',
      targetType: 'FeatureFlag',
      targetId: created.id,
      afterState: toJson(created),
    });
    return toFeatureFlagView(created);
  }

  async listFeatureFlags() {
    const flags = await this.prisma.featureFlag.findMany({ orderBy: { key: 'asc' } });
    return flags.map(toFeatureFlagView);
  }

  async updateFeatureFlag(adminId: string, key: string, dto: UpdateFeatureFlagDto) {
    const existing = await this.prisma.featureFlag.findUnique({ where: { key } });
    if (!existing) throw new NotFoundException('Feature flag not found.');

    const updated = await this.prisma.featureFlag.update({
      where: { key },
      data: {
        enabled_globally: dto.enabledGlobally ?? undefined,
        rollout_rules: (dto.rolloutRules ?? undefined) as Prisma.InputJsonValue | undefined,
        description: dto.description ?? undefined,
      },
    });
    await this.auditService.record({
      actorId: adminId,
      actorRole: 'ADMIN',
      action: 'UPDATE_FEATURE_FLAG',
      targetType: 'FeatureFlag',
      targetId: updated.id,
      beforeState: toJson(existing),
      afterState: toJson(updated),
    });
    return toFeatureFlagView(updated);
  }

  /**
   * Read-side used by any module that wants to gate a feature — a plain
   * boolean check today (percentage/service-area rollout targeting reads
   * `rolloutRules` but no caller in this build evaluates it yet against a
   * specific user/request context; disclosed as a real column with no
   * consumer wired to its full targeting logic yet, same shape as
   * CommissionPolicy's non-GLOBAL scopes in pricing.ts).
   */
  async isFeatureEnabled(key: string): Promise<boolean> {
    const flag = await this.prisma.featureFlag.findUnique({ where: { key } });
    return flag?.enabled_globally ?? false;
  }
}

function toJson(value: unknown): Record<string, unknown> {
  return JSON.parse(JSON.stringify(value, (_key, v) => (typeof v === 'bigint' ? v.toString() : v)));
}

function toCommissionPolicyView(p: {
  id: string;
  scope: string;
  scope_ref_id: string | null;
  commission_percent: number;
  fixed_fee: bigint;
  min_commission: bigint;
  effective_from: Date;
  effective_to: Date | null;
}) {
  return {
    id: p.id,
    scope: p.scope,
    scopeRefId: p.scope_ref_id,
    commissionPercent: p.commission_percent,
    fixedFeeMinorUnits: p.fixed_fee.toString(),
    minCommissionMinorUnits: p.min_commission.toString(),
    effectiveFrom: p.effective_from,
    effectiveTo: p.effective_to,
  };
}

function toTaxPolicyView(p: {
  id: string;
  scope: string;
  scope_ref_id: string | null;
  tax_type: string;
  rate_percent: number;
  inclusive: boolean;
  effective_from: Date;
  effective_to: Date | null;
}) {
  return {
    id: p.id,
    scope: p.scope,
    scopeRefId: p.scope_ref_id,
    taxType: p.tax_type,
    ratePercent: p.rate_percent,
    inclusive: p.inclusive,
    effectiveFrom: p.effective_from,
    effectiveTo: p.effective_to,
  };
}

function toCancellationPolicyView(p: { id: string; scope: string; scope_ref_id: string | null; rules: Prisma.JsonValue; effective_from: Date; effective_to: Date | null }) {
  return {
    id: p.id,
    scope: p.scope,
    scopeRefId: p.scope_ref_id,
    rules: p.rules,
    effectiveFrom: p.effective_from,
    effectiveTo: p.effective_to,
  };
}

function toFeatureFlagView(f: { id: string; key: string; description: string | null; enabled_globally: boolean; rollout_rules: Prisma.JsonValue; updated_at: Date }) {
  return {
    id: f.id,
    key: f.key,
    description: f.description,
    enabledGlobally: f.enabled_globally,
    rolloutRules: f.rollout_rules,
    updatedAt: f.updated_at,
  };
}
