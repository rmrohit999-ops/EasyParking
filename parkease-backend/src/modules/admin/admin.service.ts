import { ConflictException, ForbiddenException, Injectable, NotFoundException } from '@nestjs/common';
import { AppRole, UserStatus } from '@prisma/client';
import { PrismaService } from '../../common/prisma/prisma.service';
import { AuditService } from '../audit/audit.service';
import { ListUsersQueryDto, SuspendUserDto } from './dto/admin.dto';

/** Roles an ADMIN may never suspend/reinstate — only SUPER_ADMIN can act on another admin account. */
const ADMIN_TIER_ROLES: AppRole[] = ['ADMIN', 'SUPER_ADMIN'];

/**
 * A plain ADMIN may suspend/reinstate drivers, owners and attendants, but
 * never another admin-tier account — only SUPER_ADMIN can. Exported
 * standalone (pure — no Prisma/NestJS) so this decision is unit-testable
 * without mocking the database.
 */
export function assertMayActOnAdminTier(callerRoles: string[], targetRoles: AppRole[]): void {
  const targetIsAdminTier = targetRoles.some((r) => ADMIN_TIER_ROLES.includes(r));
  if (targetIsAdminTier && !callerRoles.includes('SUPER_ADMIN')) {
    throw new ForbiddenException('Only a super admin can suspend or reinstate an admin account.');
  }
}

/**
 * AdminModule — real (Milestone 10). User management: search, detail,
 * suspend, reinstate. A suspension takes effect immediately, not on next
 * token refresh — JwtAuthGuard re-checks `user.status === 'ACTIVE'`
 * against the database on every single request (Milestone 2), so setting
 * SUSPENDED here is enough on its own; revoking every live session on top
 * of that closes the narrower window between "status flips" and "guard
 * next checks it" for anyone mid-request, and stops a refresh-token grant
 * already issued from minting a fresh access token later.
 */
@Injectable()
export class AdminService {
  constructor(
    private readonly prisma: PrismaService,
    private readonly auditService: AuditService,
  ) {}

  async listUsers(query: ListUsersQueryDto) {
    const users = await this.prisma.user.findMany({
      where: {
        status: query.status as UserStatus | undefined,
        roles: query.role ? { some: { role: query.role as AppRole, status: 'ACTIVE' } } : undefined,
        OR: query.q
          ? [{ email: { contains: query.q, mode: 'insensitive' } }, { phone: { contains: query.q } }]
          : undefined,
      },
      include: { roles: { where: { status: 'ACTIVE' } } },
      orderBy: { created_at: 'desc' },
      take: 200,
    });
    return users.map(toUserSummaryView);
  }

  async getUser(userId: string) {
    const user = await this.prisma.user.findUnique({
      where: { id: userId },
      include: {
        roles: true,
        driver_profile: true,
        owner_profile: true,
        attendant_profile: true,
      },
    });
    if (!user) throw new NotFoundException('User not found.');
    return toUserDetailView(user);
  }

  async suspendUser(adminId: string, callerRoles: string[], userId: string, dto: SuspendUserDto) {
    const user = await this.prisma.user.findUnique({ where: { id: userId }, include: { roles: { where: { status: 'ACTIVE' } } } });
    if (!user) throw new NotFoundException('User not found.');
    if (user.status === 'SUSPENDED') throw new ConflictException('This user is already suspended.');
    assertMayActOnAdminTier(callerRoles, user.roles.map((r) => r.role));

    await this.prisma.$transaction([
      this.prisma.user.update({ where: { id: userId }, data: { status: 'SUSPENDED' } }),
      this.prisma.session.updateMany({ where: { user_id: userId, revoked_at: null }, data: { revoked_at: new Date() } }),
    ]);

    await this.auditService.record({
      actorId: adminId,
      actorRole: 'ADMIN',
      action: 'SUSPEND_USER',
      targetType: 'User',
      targetId: userId,
      beforeState: { status: user.status },
      afterState: { status: 'SUSPENDED', reason: dto.reason },
    });

    return this.getUser(userId);
  }

  async reinstateUser(adminId: string, callerRoles: string[], userId: string) {
    const user = await this.prisma.user.findUnique({ where: { id: userId }, include: { roles: { where: { status: 'ACTIVE' } } } });
    if (!user) throw new NotFoundException('User not found.');
    if (user.status !== 'SUSPENDED') throw new ConflictException('This user is not currently suspended.');
    assertMayActOnAdminTier(callerRoles, user.roles.map((r) => r.role));

    await this.prisma.user.update({ where: { id: userId }, data: { status: 'ACTIVE' } });
    await this.auditService.record({
      actorId: adminId,
      actorRole: 'ADMIN',
      action: 'REINSTATE_USER',
      targetType: 'User',
      targetId: userId,
      beforeState: { status: 'SUSPENDED' },
      afterState: { status: 'ACTIVE' },
    });

    return this.getUser(userId);
  }

  /** A small operational snapshot — real counts, not mocked KPIs. */
  async dashboardSummary() {
    const [totalUsers, suspendedUsers, pendingListings, openFraudAlerts, openSupportTickets, openDisputes] = await Promise.all([
      this.prisma.user.count(),
      this.prisma.user.count({ where: { status: 'SUSPENDED' } }),
      this.prisma.parkingListing.count({ where: { approval_status: 'PENDING' } }),
      this.prisma.fraudAlert.count({ where: { status: 'OPEN' } }),
      this.prisma.supportTicket.count({ where: { status: { in: ['OPEN', 'REOPENED'] } } }),
      this.prisma.dispute.count({ where: { status: { in: ['OPEN', 'UNDER_REVIEW'] } } }),
    ]);
    return { totalUsers, suspendedUsers, pendingListings, openFraudAlerts, openSupportTickets, openDisputes };
  }
}

function toUserSummaryView(u: {
  id: string;
  phone: string | null;
  email: string | null;
  status: string;
  created_at: Date;
  roles: { role: string }[];
}) {
  return {
    id: u.id,
    phone: u.phone,
    email: u.email,
    status: u.status,
    roles: u.roles.map((r) => r.role),
    createdAt: u.created_at,
  };
}

function toUserDetailView(u: {
  id: string;
  phone: string | null;
  email: string | null;
  status: string;
  created_at: Date;
  roles: { role: string; status: string }[];
  driver_profile: unknown;
  owner_profile: unknown;
  attendant_profile: unknown;
}) {
  return {
    id: u.id,
    phone: u.phone,
    email: u.email,
    status: u.status,
    roles: u.roles.map((r) => ({ role: r.role, status: r.status })),
    hasDriverProfile: u.driver_profile != null,
    hasOwnerProfile: u.owner_profile != null,
    hasAttendantProfile: u.attendant_profile != null,
    createdAt: u.created_at,
  };
}
