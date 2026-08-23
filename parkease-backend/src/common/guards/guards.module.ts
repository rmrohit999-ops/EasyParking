import { Global, Module } from '@nestjs/common';
import { APP_GUARD } from '@nestjs/core';
import { AuthModule } from '../../modules/auth/auth.module';
import { ThrottleGuard } from '../rate-limit/throttle.guard';
import { JwtAuthGuard } from './jwt-auth.guard';
import { RolesGuard } from './roles.guard';
import { ResourceOwnershipGuard } from './resource-ownership.guard';

/**
 * Wires the four request-pipeline guards globally, in order:
 *   1. ThrottleGuard — cheap, no DB/auth dependency, rejects abuse early.
 *   2. JwtAuthGuard — authentication + fresh status/role lookup.
 *   3. RolesGuard — role check against @Roles() metadata.
 *   4. ResourceOwnershipGuard — resource-scoped ownership via @CheckOwnership().
 * Every protected endpoint gets all four automatically; @Public() opts an
 * endpoint out of #2 (and therefore #3/#4, which need req.user).
 */
@Global()
@Module({
  imports: [AuthModule],
  providers: [
    { provide: APP_GUARD, useClass: ThrottleGuard },
    { provide: APP_GUARD, useClass: JwtAuthGuard },
    { provide: APP_GUARD, useClass: RolesGuard },
    { provide: APP_GUARD, useClass: ResourceOwnershipGuard },
  ],
})
export class GuardsModule {}
