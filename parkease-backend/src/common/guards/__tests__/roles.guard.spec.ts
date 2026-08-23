import { ExecutionContext, ForbiddenException } from '@nestjs/common';
import { Reflector } from '@nestjs/core';
import { RolesGuard } from '../roles.guard';

function buildContext(user: { roles: string[] } | undefined): ExecutionContext {
  return {
    switchToHttp: () => ({ getRequest: () => ({ user }) }),
    getHandler: () => ({}),
    getClass: () => ({}),
  } as unknown as ExecutionContext;
}

describe('RolesGuard', () => {
  it('allows any authenticated user when no @Roles() is declared', () => {
    const reflector = { getAllAndOverride: () => undefined } as unknown as Reflector;
    const guard = new RolesGuard(reflector);
    expect(guard.canActivate(buildContext({ roles: ['DRIVER'] }))).toBe(true);
  });

  it('allows a user with a matching required role', () => {
    const reflector = { getAllAndOverride: () => ['OWNER'] } as unknown as Reflector;
    const guard = new RolesGuard(reflector);
    expect(guard.canActivate(buildContext({ roles: ['DRIVER', 'OWNER'] }))).toBe(true);
  });

  it('rejects a user without any of the required roles', () => {
    const reflector = { getAllAndOverride: () => ['ADMIN'] } as unknown as Reflector;
    const guard = new RolesGuard(reflector);
    expect(() => guard.canActivate(buildContext({ roles: ['DRIVER'] }))).toThrow(ForbiddenException);
  });

  it('a driver cannot access an OWNER-only endpoint', () => {
    const reflector = { getAllAndOverride: () => ['OWNER'] } as unknown as Reflector;
    const guard = new RolesGuard(reflector);
    expect(() => guard.canActivate(buildContext({ roles: ['DRIVER'] }))).toThrow(ForbiddenException);
  });

  it('an attendant cannot access an ADMIN-only endpoint', () => {
    const reflector = { getAllAndOverride: () => ['ADMIN'] } as unknown as Reflector;
    const guard = new RolesGuard(reflector);
    expect(() => guard.canActivate(buildContext({ roles: ['ATTENDANT'] }))).toThrow(ForbiddenException);
  });

  it('a user holding multiple roles passes if any one matches', () => {
    const reflector = { getAllAndOverride: () => ['ADMIN', 'OWNER'] } as unknown as Reflector;
    const guard = new RolesGuard(reflector);
    expect(guard.canActivate(buildContext({ roles: ['DRIVER', 'OWNER'] }))).toBe(true);
  });
});
