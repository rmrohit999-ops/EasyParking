import { Body, Controller, Get, Param, Post } from '@nestjs/common';
import { ApiOperation, ApiTags } from '@nestjs/swagger';
import { CurrentUser, AuthenticatedUser } from '../../common/decorators/current-user.decorator';
import { SupportService } from './support.service';
import { AddSupportMessageDto, CreateSupportTicketDto } from './dto/support.dto';

@ApiTags('support')
@Controller({ path: 'support/tickets', version: '1' })
export class SupportController {
  constructor(private readonly supportService: SupportService) {}

  @Post()
  @ApiOperation({ summary: 'Open a support ticket' })
  create(@CurrentUser() user: AuthenticatedUser & { roles: string[] }, @Body() dto: CreateSupportTicketDto) {
    return this.supportService.createTicket(user, dto);
  }

  @Get()
  @ApiOperation({ summary: 'List your own support tickets' })
  listMine(@CurrentUser() user: AuthenticatedUser) {
    return this.supportService.listMine(user);
  }

  @Get(':ticketId')
  @ApiOperation({ summary: 'Get one ticket with its message thread (ownership/admin scoped)' })
  getOne(@CurrentUser() user: AuthenticatedUser & { roles: string[] }, @Param('ticketId') ticketId: string) {
    return this.supportService.getTicket(user, ticketId);
  }

  @Post(':ticketId/messages')
  @ApiOperation({ summary: 'Reply to a ticket' })
  addMessage(
    @CurrentUser() user: AuthenticatedUser & { roles: string[] },
    @Param('ticketId') ticketId: string,
    @Body() dto: AddSupportMessageDto,
  ) {
    return this.supportService.addMessage(user, ticketId, dto);
  }
}
