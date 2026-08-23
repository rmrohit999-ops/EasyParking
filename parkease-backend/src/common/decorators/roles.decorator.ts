import { SetMetadata } from '@nestjs/common';

export type AppRole = 'DRIVER' | 'OWNER' | 'ATTENDANT' | 'ADMIN';

export const ROLES_KEY = 'roles';

/**
 * Declares which roles may call an endpoint. This decorator only *declares*
 * intent — RolesGuard (Milestone 2) is what actually enforces it against the
 * authenticated user's roles. Declared now, ahead of real auth, so module
 * skeletons created in this milestone already show the shape controllers
 * will use once Milestone 2 lands.
 */
export const Roles = (...roles: AppRole[]) => SetMetadata(ROLES_KEY, roles);
