import { Module } from '@nestjs/common';
import { AuthModule } from '../auth/auth.module';
import { RealtimeGateway } from './realtime.gateway';

/**
 * Leaf module — no dependency on Notifications/Booking/etc, so both of
 * those can import this without a circular-module error. PrismaService
 * comes from the @Global() PrismaModule (no explicit import needed);
 * TokenService comes from AuthModule, which already exports it for the
 * exact same "authenticate like the REST guard does" reason.
 */
@Module({
  imports: [AuthModule],
  providers: [RealtimeGateway],
  exports: [RealtimeGateway],
})
export class RealtimeModule {}
