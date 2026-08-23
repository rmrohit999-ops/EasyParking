import { createParamDecorator, ExecutionContext } from '@nestjs/common';

export interface AuthenticatedUser {
  id: string;
  roles: string[];
}

/**
 * Placeholder shape for the authenticated-user param decorator. Returns
 * undefined until JwtAuthGuard (Milestone 2) populates req.user — declared
 * now so controller signatures written in this milestone don't need to
 * change shape later.
 */
export const CurrentUser = createParamDecorator((_data: unknown, ctx: ExecutionContext): AuthenticatedUser | undefined => {
  const request = ctx.switchToHttp().getRequest();
  return request.user;
});
