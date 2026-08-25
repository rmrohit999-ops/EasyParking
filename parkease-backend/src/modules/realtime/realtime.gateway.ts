import { Logger } from '@nestjs/common';
import {
  ConnectedSocket,
  MessageBody,
  OnGatewayConnection,
  OnGatewayDisconnect,
  SubscribeMessage,
  WebSocketGateway,
  WebSocketServer,
} from '@nestjs/websockets';
import { Server, Socket } from 'socket.io';
import { PrismaService } from '../../common/prisma/prisma.service';
import { TokenService } from '../auth/services/token.service';

/** Pulled out of the handshake object itself (not the whole Socket) purely so this is unit-testable without a real socket.io connection. */
export function extractHandshakeToken(handshake: { auth?: Record<string, unknown>; headers?: Record<string, unknown> }): string | null {
  const fromAuth = handshake.auth?.token;
  if (typeof fromAuth === 'string' && fromAuth.length > 0) return fromAuth;

  const authHeader = handshake.headers?.authorization;
  if (typeof authHeader === 'string' && authHeader.startsWith('Bearer ')) {
    return authHeader.slice('Bearer '.length);
  }
  return null;
}

/**
 * Real-time push layer (Milestone: real-time sync). One namespace, JWT-
 * authenticated on connect exactly like JwtAuthGuard authenticates a REST
 * request — same TokenService, same "re-check status in the DB, don't
 * just trust the token" rule, because a socket connection can outlive a
 * suspension that happened after it was opened.
 *
 * Two room kinds:
 *  - `user:{userId}` — joined automatically on connect. Every event that
 *    already triggers NotificationsService.send() (booking transitions,
 *    QR check-in/out, disputes, settlements, support, refunds) also lands
 *    here in real time — see NotificationsService.send()'s new emit call.
 *  - `listing:{listingId}` — joined on explicit request via
 *    `subscribe:listing`, authorized per-request (the listing's owner, an
 *    attendant currently assigned to it, or an admin/super_admin) since
 *    unlike a user's own room this isn't implied by "who is this token
 *    for." Used for live occupancy/availability changes — see
 *    BookingService's central transition method.
 */
@WebSocketGateway({
  namespace: '/realtime',
  cors: { origin: true, credentials: true },
})
export class RealtimeGateway implements OnGatewayConnection, OnGatewayDisconnect {
  private readonly logger = new Logger(RealtimeGateway.name);

  @WebSocketServer()
  server!: Server;

  constructor(
    private readonly tokenService: TokenService,
    private readonly prisma: PrismaService,
  ) {}

  async handleConnection(client: Socket): Promise<void> {
    try {
      const token = extractHandshakeToken(client.handshake);
      if (!token) throw new Error('no token provided');

      const payload = this.tokenService.verifyAccessToken(token);
      const user = await this.prisma.user.findUnique({ where: { id: payload.sub } });
      if (!user || user.status !== 'ACTIVE') throw new Error('account not active');

      client.data.userId = user.id;
      await client.join(`user:${user.id}`);
    } catch (err) {
      this.logger.warn(`Rejected realtime connection ${client.id}: ${(err as Error).message}`);
      client.disconnect(true);
    }
  }

  handleDisconnect(): void {
    // No explicit cleanup needed — socket.io removes a disconnected socket
    // from every room it was in automatically.
  }

  @SubscribeMessage('subscribe:listing')
  async onSubscribeListing(
    @ConnectedSocket() client: Socket,
    @MessageBody() data: { listingId?: string },
  ): Promise<{ ok: boolean; error?: string }> {
    const listingId = data?.listingId;
    const userId = client.data.userId as string | undefined;
    if (!userId || !listingId) return { ok: false, error: 'Missing listingId.' };

    const allowed = await this.canWatchListing(userId, listingId);
    if (!allowed) return { ok: false, error: 'Not authorized for this listing.' };

    await client.join(`listing:${listingId}`);
    return { ok: true };
  }

  private async canWatchListing(userId: string, listingId: string): Promise<boolean> {
    const roles = await this.prisma.userRoleAssignment.findMany({ where: { user_id: userId, status: 'ACTIVE' } });
    if (roles.some((r) => r.role === 'ADMIN' || r.role === 'SUPER_ADMIN')) return true;

    const listing = await this.prisma.parkingListing.findUnique({
      where: { id: listingId },
      select: { owner: { select: { user_id: true } } },
    });
    if (listing?.owner.user_id === userId) return true;

    const assignment = await this.prisma.attendantAssignment.findFirst({
      where: { parking_id: listingId, revoked_at: null, attendant: { user_id: userId } },
    });
    return Boolean(assignment);
  }

  /** Delivered to whichever of the user's own devices/tabs are connected right now — booking status, QR events, disputes, settlements, support, refunds. */
  emitToUser(userId: string, event: string, payload: unknown): void {
    this.server?.to(`user:${userId}`).emit(event, payload);
  }

  /** Delivered to whoever is currently watching this specific listing (owner/attendant/admin dashboards) — live occupancy changes. */
  emitToListing(listingId: string, event: string, payload: unknown): void {
    this.server?.to(`listing:${listingId}`).emit(event, payload);
  }
}
