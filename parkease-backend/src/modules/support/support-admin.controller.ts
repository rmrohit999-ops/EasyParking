import { Body, Controller, Get, Param, Patch, Post, Query } from '@nestjs/common';
import { ApiOperation, ApiTags } from '@nestjs/swagger';
import { Roles } from '../../common/decorators/roles.decorator';
import { CurrentUser, AuthenticatedUser } from '../../common/decorators/current-user.decorator';
import { SupportService } from './support.service';
import { AddSupportMessageDto, AssignSupportTicketDto, ListSupportTicketsQueryDto, UpdateSupportTicketStatusDto } from './dto/support.dto';

@ApiTags('admin-support')
@Roles('ADMIN')
@Controller({ path: 'admin/support/tickets', version: '1' })
export class SupportAdminController {
  constructor(private readonly supportService: SupportService) {}

  @Get()
  @ApiOperation({ summary: 'List all support tickets, optionally filtered by status' })
  listAll(@Query() query: ListSupportTicketsQueryDto) {
    return this.supportService.listAll(query.status);
  }

  @Get(':ticketId')
  @ApiOperation({ summary: 'Get one ticket with its message thread' })
  getOne(@CurrentUser() admin: AuthenticatedUser & { roles: string[] }, @Param('ticketId') ticketId: string) {
    return this.supportService.getTicket(admin, ticketId);
  }

  @Post(':ticketId/messages')
  @ApiOperation({ summary: 'Reply to a ticket as support' })
  addMessage(
    @CurrentUser() admin: AuthenticatedUser & { roles: string[] },
    @Param('ticketId') ticketId: string,
    @Body() dto: AddSupportMessageDto,
  ) {
    return this.supportService.addMessage(admin, ticketId, dto);
  }

  @Post(':ticketId/assign')
  @ApiOperation({ summary: 'Assign a ticket to an agent' })
  assign(@CurrentUser() admin: AuthenticatedUser, @Param('ticketId') ticketId: string, @Body() dto: AssignSupportTicketDto) {
    return this.supportService.assign(admin.id, ticketId, dto);
  }

  @Patch(':ticketId/status')
  @ApiOperation({ summary: "Update a ticket's status" })
  updateStatus(@CurrentUser() admin: AuthenticatedUser, @Param('ticketId') ticketId: string, @Body() dto: UpdateSupportTicketStatusDto) {
    return this.supportService.updateStatus(admin.id, ticketId, dto);
  }
}
