import { Inject, Injectable, Logger } from '@nestjs/common';
import { NotificationChannel } from '@prisma/client';
import { PrismaService } from '../../common/prisma/prisma.service';
import { RealtimeGateway } from '../realtime/realtime.gateway';
import { PUSH_PROVIDER_SERVICE, PushProvider } from './provider/push-provider.interface';
import { UpdateNotificationPreferenceDto } from './dto/notifications.dto';

export interface SendNotificationParams {
  userId: string;
  category: string;
  type: string;
  title: string;
  body: string;
  deepLink?: string;
  data?: Record<string, unknown>;
}

/**
 * NotificationsModule — real (Milestone 11). Two independent layers, both
 * always attempted: (1) an in-app `Notification` inbox row, written
 * unconditionally so nothing is ever lost even when push fails/is
 * unconfigured/is disabled by preference; (2) a best-effort push send via
 * `PushProvider`, gated by `NotificationPreference` and never allowed to
 * throw back to the caller — a booking confirming, a refund completing,
 * etc. must never fail *because* a push notification failed to send. This
 * mirrors how RefundsService's gateway-refund failure never rolls back the
 * booking transition that triggered it (Milestone 9).
 */
@Injectable()
export class NotificationsService {
  private readonly logger = new Logger(NotificationsService.name);

  constructor(
    private readonly prisma: PrismaService,
    @Inject(PUSH_PROVIDER_SERVICE) private readonly pushProvider: PushProvider,
    private readonly realtimeGateway: RealtimeGateway,
  ) {}

  // ---------------------------------------------------------------------
  // Devices
  // ---------------------------------------------------------------------

  async registerDevice(userId: string, fcmToken: string, platform = 'ANDROID') {
    return this.prisma.notificationDevice.upsert({
      where: { fcm_token: fcmToken },
      update: { user_id: userId, platform, last_seen_at: new Date(), revoked_at: null },
      create: { user_id: userId, fcm_token: fcmToken, platform },
    });
  }

  async unregisterDevice(userId: string, fcmToken: string) {
    await this.prisma.notificationDevice.updateMany({
      where: { fcm_token: fcmToken, user_id: userId },
      data: { revoked_at: new Date() },
    });
    return { fcmToken, revoked: true };
  }

  // ---------------------------------------------------------------------
  // Send (called from other modules' lifecycle events)
  // ---------------------------------------------------------------------

  async send(params: SendNotificationParams): Promise<void> {
    await this.prisma.notification.create({
      data: {
        user_id: params.userId,
        type: params.type,
        title: params.title,
        body: params.body,
        deep_link: params.deepLink,
        data: (params.data ?? undefined) as never,
      },
    });

    // Best-effort, same "never let this fail the caller" rule as the push
    // send below — a client with no live socket connection just doesn't
    // get this particular event and falls back to push/the inbox, exactly
    // like it always has.
    try {
      this.realtimeGateway.emitToUser(params.userId, 'notification', {
        category: params.category,
        type: params.type,
        title: params.title,
        body: params.body,
        deepLink: params.deepLink,
        data: params.data,
      });
    } catch (err) {
      this.logger.warn(`Realtime emit failed for user ${params.userId}, category ${params.category}: ${(err as Error).message}`);
    }

    try {
      const pushEnabled = await this.isChannelEnabled(params.userId, params.category, 'PUSH');
      if (!pushEnabled) return;

      const devices = await this.prisma.notificationDevice.findMany({
        where: { user_id: params.userId, revoked_at: null },
      });
      if (devices.length === 0) return;

      await Promise.all(
        devices.map((device) =>
          this.pushProvider
            .send({ fcmToken: device.fcm_token, title: params.title, body: params.body, deepLink: params.deepLink })
            .catch((err) => {
              this.logger.warn(`Push send failed for device ${device.id} (user ${params.userId}): ${(err as Error).message}`);
            }),
        ),
      );
    } catch (err) {
      // Never let a notification-side failure propagate to the caller —
      // see this class's doc comment.
      this.logger.warn(`Notification dispatch failed for user ${params.userId}, category ${params.category}: ${(err as Error).message}`);
    }
  }

  private async isChannelEnabled(userId: string, category: string, channel: NotificationChannel): Promise<boolean> {
    const pref = await this.prisma.notificationPreference.findUnique({
      where: { user_id_category_channel: { user_id: userId, category, channel } },
    });
    // No row -> default enabled, matching the schema's `enabled Boolean
    // @default(true)` — a user only ever appears here once they've
    // explicitly turned something off.
    return pref?.enabled ?? true;
  }

  // ---------------------------------------------------------------------
  // Inbox
  // ---------------------------------------------------------------------

  async listMine(userId: string, unreadOnly: boolean) {
    const notifications = await this.prisma.notification.findMany({
      where: { user_id: userId, ...(unreadOnly ? { read_at: null } : {}) },
      orderBy: { created_at: 'desc' },
      take: 100,
    });
    return notifications.map((n) => ({
      id: n.id,
      type: n.type,
      title: n.title,
      body: n.body,
      deepLink: n.deep_link,
      data: n.data,
      readAt: n.read_at,
      createdAt: n.created_at,
    }));
  }

  async markRead(userId: string, notificationId: string) {
    await this.prisma.notification.updateMany({
      where: { id: notificationId, user_id: userId, read_at: null },
      data: { read_at: new Date() },
    });
    return { id: notificationId, read: true };
  }

  async markAllRead(userId: string) {
    const result = await this.prisma.notification.updateMany({
      where: { user_id: userId, read_at: null },
      data: { read_at: new Date() },
    });
    return { markedRead: result.count };
  }

  // ---------------------------------------------------------------------
  // Preferences
  // ---------------------------------------------------------------------

  async getPreferences(userId: string) {
    const prefs = await this.prisma.notificationPreference.findMany({ where: { user_id: userId } });
    return prefs.map((p) => ({ category: p.category, channel: p.channel, enabled: p.enabled }));
  }

  async updatePreference(userId: string, dto: UpdateNotificationPreferenceDto) {
    const pref = await this.prisma.notificationPreference.upsert({
      where: { user_id_category_channel: { user_id: userId, category: dto.category, channel: dto.channel } },
      update: { enabled: dto.enabled },
      create: { user_id: userId, category: dto.category, channel: dto.channel, enabled: dto.enabled },
    });
    return { category: pref.category, channel: pref.channel, enabled: pref.enabled };
  }
}
