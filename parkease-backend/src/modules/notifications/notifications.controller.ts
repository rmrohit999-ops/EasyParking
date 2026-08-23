import { Body, Controller, Get, Param, Post, Query } from '@nestjs/common';
import { ApiOperation, ApiTags } from '@nestjs/swagger';
import { CurrentUser, AuthenticatedUser } from '../../common/decorators/current-user.decorator';
import { NotificationsService } from './notifications.service';
import { RegisterDeviceDto, UnregisterDeviceDto, UpdateNotificationPreferenceDto } from './dto/notifications.dto';

@ApiTags('notifications')
@Controller({ version: '1' })
export class NotificationsController {
  constructor(private readonly notificationsService: NotificationsService) {}

  @Post('notifications/devices')
  @ApiOperation({ summary: 'Register this device for push notifications' })
  registerDevice(@CurrentUser() user: AuthenticatedUser, @Body() dto: RegisterDeviceDto) {
    return this.notificationsService.registerDevice(user.id, dto.fcmToken, dto.platform);
  }

  @Post('notifications/devices/unregister')
  @ApiOperation({ summary: 'Stop sending push notifications to this device (e.g. on sign-out)' })
  unregisterDevice(@CurrentUser() user: AuthenticatedUser, @Body() dto: UnregisterDeviceDto) {
    return this.notificationsService.unregisterDevice(user.id, dto.fcmToken);
  }

  @Get('notifications')
  @ApiOperation({ summary: 'List your in-app notification inbox' })
  listMine(@CurrentUser() user: AuthenticatedUser, @Query('unreadOnly') unreadOnly?: string) {
    return this.notificationsService.listMine(user.id, unreadOnly === 'true');
  }

  @Post('notifications/:notificationId/read')
  @ApiOperation({ summary: 'Mark one notification read' })
  markRead(@CurrentUser() user: AuthenticatedUser, @Param('notificationId') notificationId: string) {
    return this.notificationsService.markRead(user.id, notificationId);
  }

  @Post('notifications/read-all')
  @ApiOperation({ summary: 'Mark every notification read' })
  markAllRead(@CurrentUser() user: AuthenticatedUser) {
    return this.notificationsService.markAllRead(user.id);
  }

  @Get('notifications/preferences')
  @ApiOperation({ summary: 'Get your notification channel preferences' })
  getPreferences(@CurrentUser() user: AuthenticatedUser) {
    return this.notificationsService.getPreferences(user.id);
  }

  @Post('notifications/preferences')
  @ApiOperation({ summary: 'Enable/disable a notification category on a channel' })
  updatePreference(@CurrentUser() user: AuthenticatedUser, @Body() dto: UpdateNotificationPreferenceDto) {
    return this.notificationsService.updatePreference(user.id, dto);
  }
}
