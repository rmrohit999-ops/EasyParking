import { CanActivate, ExecutionContext, ForbiddenException, Injectable, SetMetadata } from '@nestjs/common';
import { Reflector } from '@nestjs/core';
import { ModuleRef } from '@nestjs/core';
import { Request } from 'express';
import { AuthenticatedUser } from '../decorators/current-user.decorator';

/**
 * A resolver answers "does this user own/administer this resource, in a
 * status that permits this action?" for one resource type. Concrete
 * resolvers are registered by each domain module as it's built:
 *   - Milestone 3: VehicleOwnershipResolver (vehicle belongs to driver)
 *   - Milestone 4: ParkingOwnershipResolver (listing belongs to owner)
 *   - Milestone 6/8: AttendantAssignmentResolver (attendant assigned to parking)
 * ADMIN always passes — admin authorization for administrative actions is
 * enforced separately by @Roles('ADMIN') on the same endpoint, not by
 * bypassing ownership silently for every role.
 */
export interface OwnershipResolver {
  resolve(user: AuthenticatedUser & { roles: string[] }, resourceId: string): Promise<boolean>;
}

export const OWNERSHIP_RESOLVER_KEY = 'ownershipResolver';
export const OWNERSHIP_PARAM_KEY = 'ownershipParam';

/**
 * @CheckOwnership('VehicleOwnershipResolver', 'vehicleId') — resolverToken
 * must be registered as a provider (and exported) by the owning module, and
 * looked up here via ModuleRef so this guard stays generic across all
 * resource types instead of one guard per module.
 */
export const CheckOwnership = (resolverToken: string, param: string) => {
  return (target: object, key?: string | symbol, descriptor?: PropertyDescriptor) => {
    SetMetadata(OWNERSHIP_RESOLVER_KEY, resolverToken)(target, key as string, descriptor as PropertyDescriptor);
    SetMetadata(OWNERSHIP_PARAM_KEY, param)(target, key as string, descriptor as PropertyDescriptor);
  };
};

@Injectable()
export class ResourceOwnershipGuard implements CanActivate {
  constructor(
    private readonly reflector: Reflector,
    private readonly moduleRef: ModuleRef,
  ) {}

  async canActivate(context: ExecutionContext): Promise<boolean> {
    const resolverToken = this.reflector.get<string>(OWNERSHIP_RESOLVER_KEY, context.getHandler());
    if (!resolverToken) return true; // no ownership check declared on this endpoint

    const paramName = this.reflector.get<string>(OWNERSHIP_PARAM_KEY, context.getHandler());
    const request = context.switchToHttp().getRequest<Request & { user?: AuthenticatedUser & { roles: string[] } }>();
    const user = request.user;
    if (!user) throw new ForbiddenException('You do not have permission to perform this action.');
    if (user.roles.includes('ADMIN')) return true;

    const resourceId = request.params[paramName];
    if (!resourceId) throw new ForbiddenException('You do not have permission to perform this action.');

    const resolver = this.moduleRef.get<OwnershipResolver>(resolverToken, { strict: false });
    const owns = await resolver.resolve(user, resourceId);
    if (!owns) throw new ForbiddenException('You do not have permission to perform this action.');
    return true;
  }
}
