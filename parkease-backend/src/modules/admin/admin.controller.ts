import { Body, Controller, Get, Param, Post, Query } from '@nestjs/common';
import { ApiOperation, ApiTags } from '@nestjs/swagger';
import { Roles } from '../../common/decorators/roles.decorator';
import { CurrentUser, AuthenticatedUser } from '../../common/decorators/current-user.decorator';
import { AdminService } from './admin.service';
import { ListUsersQueryDto, SuspendUserDto } from './dto/admin.dto';

@ApiTags('admin')
@Roles('ADMIN')
@Controller({ path: 'admin', version: '1' })
export class AdminController {
  constructor(private readonly adminService: AdminService) {}

  @Get('dashboard/summary')
  @ApiOperation({ summary: 'Real-time operational counts for the admin dashboard' })
  dashboardSummary() {
    return this.adminService.dashboardSummary();
  }

  @Get('users')
  @ApiOperation({ summary: 'Search/list users, optionally filtered by role/status/query' })
  listUsers(@Query() query: ListUsersQueryDto) {
    return this.adminService.listUsers(query);
  }

  @Get('users/:userId')
  @ApiOperation({ summary: 'Get one user with role/profile summary' })
  getUser(@Param('userId') userId: string) {
    return this.adminService.getUser(userId);
  }

  @Post('users/:userId/suspend')
  @ApiOperation({ summary: 'Suspend a user (blocks sign-in immediately, revokes all sessions). Suspending another admin account requires SUPER_ADMIN.' })
  suspendUser(@CurrentUser() admin: AuthenticatedUser, @Param('userId') userId: string, @Body() dto: SuspendUserDto) {
    return this.adminService.suspendUser(admin.id, admin.roles, userId, dto);
  }

  @Post('users/:userId/reinstate')
  @ApiOperation({ summary: 'Reinstate a suspended user. Reinstating another admin account requires SUPER_ADMIN.' })
  reinstateUser(@CurrentUser() admin: AuthenticatedUser, @Param('userId') userId: string) {
    return this.adminService.reinstateUser(admin.id, admin.roles, userId);
  }
}
