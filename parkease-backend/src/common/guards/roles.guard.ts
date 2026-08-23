import { CanActivate, ExecutionContext, ForbiddenException, Injectable } from '@nestjs/common';
import { Reflector } from '@nestjs/core';
import { Request } from 'express';
import { AppRole, ROLES_KEY } from '../decorators/roles.decorator';
import { AuthenticatedUser } from '../decorators/current-user.decorator';

/**
 * Enforces @Roles(...) declared on a handler/controller. Runs after
 * JwtAuthGuard (which populates req.user). An endpoint with no @Roles()
 * decorator is reachable by any authenticated user — mark endpoints that
 * need broader/narrower access explicitly rather than relying on this
 * default, per Milestone 0 §4 (every protected API verifies role).
 */
@Injectable()
export class RolesGuard implements CanActivate {
  constructor(private readonly reflector: Reflector) {}

  canActivate(context: ExecutionContext): boolean {
    const requiredRoles = this.reflector.getAllAndOverride<AppRole[]>(ROLES_KEY, [
      context.getHandler(),
      context.getClass(),
    ]);
    if (!requiredRoles || requiredRoles.length === 0) return true;

    const request = context.switchToHttp().getRequest<Request & { user?: AuthenticatedUser & { roles: string[] } }>();
    const userRoles = request.user?.roles ?? [];
    const hasRole = requiredRoles.some((r) => userRoles.includes(r));
    if (!hasRole) {
      throw new ForbiddenException('You do not have permission to perform this action.');
    }
    return true;
  }
}
