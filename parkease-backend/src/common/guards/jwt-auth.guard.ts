import { CanActivate, ExecutionContext, Injectable, SetMetadata, UnauthorizedException } from '@nestjs/common';
import { Reflector } from '@nestjs/core';
import { Request } from 'express';
import { PrismaService } from '../prisma/prisma.service';
import { TokenService } from '../../modules/auth/services/token.service';

export const IS_PUBLIC_KEY = 'isPublic';
/** Marks an endpoint as not requiring authentication (e.g. /health, webhooks). */
export const Public = () => SetMetadata(IS_PUBLIC_KEY, true);

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

    (request as Request & { user?: unknown }).user = {
      id: user.id,
      roles: user.roles.map((r) => r.role),
      sessionId: payload.sessionId,
    };
    return true;
  }
}
