import { CanActivate, ExecutionContext, Injectable, SetMetadata, UnauthorizedException } from '@nestjs/common';
import { ConfigService } from '@nestjs/config';
import { Reflector } from '@nestjs/core';
import { Request } from 'express';
import { AppConfig } from '../config/configuration';
import { PrismaService } from '../prisma/prisma.service';
import { TokenService } from '../../modules/auth/services/token.service';

export const IS_PUBLIC_KEY = 'isPublic';
/** Marks an endpoint as not requiring authentication (e.g. /health, webhooks). */
export const Public = () => SetMetadata(IS_PUBLIC_KEY, true);

/**
 * Pure decision of which roles are missing to reflect SUPER_ADMIN
 * elevation for this email, given the currently-persisted roles. Returns
 * an empty array when nothing needs to change (no super admin configured,
 * email doesn't match, or both roles already present) — the caller only
 * needs to write to the DB when this returns something non-empty.
 * Exported standalone so the matching/case-insensitivity/idempotency logic
 * is unit-testable without a database or NestJS test module.
 */
export function missingSuperAdminRoles(
  email: string | null | undefined,
  superAdminEmail: string,
  currentRoles: string[],
): Array<'ADMIN' | 'SUPER_ADMIN'> {
  if (!superAdminEmail || email?.trim().toLowerCase() !== superAdminEmail) {
    return [];
  }
  const missing: Array<'ADMIN' | 'SUPER_ADMIN'> = [];
  if (!currentRoles.includes('ADMIN')) missing.push('ADMIN');
  if (!currentRoles.includes('SUPER_ADMIN')) missing.push('SUPER_ADMIN');
  return missing;
}

/**
 * Verifies the access token AND re-checks the user's current status/roles
 * against the database on every request (not just the token's embedded
 * claims) — this is what makes an account suspension or role revocation
 * take effect immediately rather than waiting for the access token to
 * expire. Attaches `req.user = { id, roles }` for RolesGuard and
 * @CurrentUser() to consume.
 */
@Injectable()
export class JwtAuthGuard implements CanActivate {
  constructor(
    private readonly tokenService: TokenService,
    private readonly prisma: PrismaService,
    private readonly reflector: Reflector,
    private readonly configService: ConfigService<AppConfig, true>,
  ) {}

  async canActivate(context: ExecutionContext): Promise<boolean> {
    const isPublic = this.reflector.getAllAndOverride<boolean>(IS_PUBLIC_KEY, [
      context.getHandler(),
      context.getClass(),
    ]);
    if (isPublic) return true;

    const request = context.switchToHttp().getRequest<Request>();
    const authHeader = request.headers.authorization;
    if (!authHeader?.startsWith('Bearer ')) {
      throw new UnauthorizedException('Please sign in to continue.');
    }

    const token = authHeader.slice('Bearer '.length);
    const payload = this.tokenService.verifyAccessToken(token);

    const user = await this.prisma.user.findUnique({
      where: { id: payload.sub },
      include: { roles: { where: { status: 'ACTIVE' } } },
    });

    if (!user || user.status !== 'ACTIVE') {
      throw new UnauthorizedException('Please sign in to continue.');
    }

    let roles: string[] = user.roles.map((r) => r.role);
    roles = await this.applySuperAdminElevation(user.id, user.email, roles);

    (request as Request & { user?: unknown }).user = {
      id: user.id,
      roles,
      sessionId: payload.sessionId,
    };
    return true;
  }

  /**
   * SUPER_ADMIN is never granted through the normal role-assignment UI —
   * it's derived purely from SUPER_ADMIN_EMAIL matching this user's email,
   * re-checked fresh on every request just like every other role here. If
   * the DB row is missing (first login after configuring the env var, or
   * someone manually removed it), it's re-created here so the grant is
   * effectively un-revocable by anything short of changing the env var —
   * a plain ADMIN has no path to strip it. Fires only for the one matching
   * account; zero DB writes for everyone else since missingSuperAdminRoles
   * short-circuits on the email comparison.
   */
  private async applySuperAdminElevation(userId: string, email: string | null, roles: string[]): Promise<string[]> {
    const superAdminEmail = this.configService.get('auth', { infer: true }).superAdminEmail;
    const missing = missingSuperAdminRoles(email, superAdminEmail, roles);
    if (missing.length === 0) return roles;

    await this.prisma.$transaction(
      missing.map((role) =>
        this.prisma.userRoleAssignment.upsert({
          where: { user_id_role: { user_id: userId, role } },
          create: { user_id: userId, role, status: 'ACTIVE' },
          update: { status: 'ACTIVE' },
        }),
      ),
    );

    return [...roles, ...missing];
  }
}
