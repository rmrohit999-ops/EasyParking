import { ForbiddenException, Injectable, NotFoundException } from '@nestjs/common';
import { AppRole, SupportTicketStatus } from '@prisma/client';
import { PrismaService } from '../../common/prisma/prisma.service';
import { AuthenticatedUser } from '../../common/decorators/current-user.decorator';
import { AuditService } from '../audit/audit.service';
import { NotificationsService } from '../notifications/notifications.service';
import { AddSupportMessageDto, AssignSupportTicketDto, CreateSupportTicketDto, UpdateSupportTicketStatusDto } from './dto/support.dto';

/**
 * SupportModule — real (Milestone 10). A ticket + threaded-message model
 * exactly matching the ERD's `support_tickets`/`support_messages` shape —
 * every message is attributed to a real sender_id/sender_role, there is no
 * "system reply" that isn't actually written by someone.
 */
@Injectable()
export class SupportService {
  constructor(
    private readonly prisma: PrismaService,
    private readonly auditService: AuditService,
    private readonly notificationsService: NotificationsService,
  ) {}

  async createTicket(user: AuthenticatedUser & { roles: string[] }, dto: CreateSupportTicketDto) {
    const ticket = await this.prisma.supportTicket.create({
      data: {
        user_id: user.id,
        category: dto.category,
        subject: dto.subject,
        description: dto.description,
        related_booking_id: dto.relatedBookingId,
        status: 'OPEN',
      },
    });
    // The ticket's own `description` is the first message too, so a
    // ticket's message thread is always complete context on its own
    // rather than the opening complaint living only in a separate column.
    await this.prisma.supportMessage.create({
      data: {
        ticket_id: ticket.id,
        sender_id: user.id,
        sender_role: primaryRole(user.roles),
        message: dto.description,
      },
    });
    return this.getTicket(user, ticket.id);
  }

  async listMine(user: AuthenticatedUser) {
    const tickets = await this.prisma.supportTicket.findMany({ where: { user_id: user.id }, orderBy: { created_at: 'desc' } });
    return tickets.map(toTicketView);
  }

  async listAll(status?: SupportTicketStatus) {
    const tickets = await this.prisma.supportTicket.findMany({
      where: status ? { status } : {},
      orderBy: { created_at: 'desc' },
      take: 200,
    });
    return tickets.map(toTicketView);
  }

  async getTicket(user: AuthenticatedUser & { roles: string[] }, ticketId: string) {
    const ticket = await this.prisma.supportTicket.findUnique({
      where: { id: ticketId },
      include: { messages: { orderBy: { created_at: 'asc' } } },
    });
    if (!ticket) throw new NotFoundException('Support ticket not found.');
    if (ticket.user_id !== user.id && !user.roles.includes('ADMIN')) {
      throw new ForbiddenException('You do not have permission to view this ticket.');
    }
    return { ...toTicketView(ticket), messages: ticket.messages.map(toMessageView) };
  }

  async addMessage(user: AuthenticatedUser & { roles: string[] }, ticketId: string, dto: AddSupportMessageDto) {
    const ticket = await this.prisma.supportTicket.findUnique({ where: { id: ticketId } });
    if (!ticket) throw new NotFoundException('Support ticket not found.');
    const isAdmin = user.roles.includes('ADMIN');
    if (ticket.user_id !== user.id && !isAdmin) {
      throw new ForbiddenException('You do not have permission to reply to this ticket.');
    }
    if (ticket.status === 'CLOSED') throw new ForbiddenException('This ticket is closed. Ask support to reopen it.');

    await this.prisma.supportMessage.create({
      data: { ticket_id: ticketId, sender_id: user.id, sender_role: primaryRole(user.roles), message: dto.message },
    });

    // A user replying to a WAITING_FOR_USER ticket naturally reopens it for
    // the agent; an agent replying to an OPEN ticket naturally moves it
    // in progress — small, real state transitions, not clicks a human
    // would otherwise have to remember to also make.
    const nextStatus: SupportTicketStatus | undefined =
      !isAdmin && ticket.status === 'WAITING_FOR_USER' ? 'REOPENED' : isAdmin && ticket.status === 'OPEN' ? 'IN_PROGRESS' : undefined;
    if (nextStatus) {
      await this.prisma.supportTicket.update({ where: { id: ticketId }, data: { status: nextStatus } });
    }

    // Best-effort: let the ticket's owner know the moment support replies.
    // Not notified the other direction (agent on user reply) — agents work
    // from the admin ticket queue, not a personal notification inbox.
    if (isAdmin && ticket.user_id !== user.id) {
      await this.notificationsService.send({
        userId: ticket.user_id,
        category: 'support',
        type: 'SUPPORT_REPLY',
        title: 'New reply on your support ticket',
        body: dto.message.length > 140 ? `${dto.message.slice(0, 137)}...` : dto.message,
        deepLink: `parkease://support/tickets/${ticketId}`,
      });
    }

    return this.getTicket(user, ticketId);
  }

  async assign(adminId: string, ticketId: string, dto: AssignSupportTicketDto) {
    const existing = await this.prisma.supportTicket.findUnique({ where: { id: ticketId } });
    if (!existing) throw new NotFoundException('Support ticket not found.');
    const updated = await this.prisma.supportTicket.update({
      where: { id: ticketId },
      data: { assigned_to: dto.assignedTo, status: existing.status === 'OPEN' ? 'IN_PROGRESS' : existing.status },
    });
    await this.auditService.record({
      actorId: adminId,
      actorRole: 'ADMIN',
      action: 'ASSIGN_SUPPORT_TICKET',
      targetType: 'SupportTicket',
      targetId: ticketId,
      afterState: { assignedTo: dto.assignedTo },
    });
    return toTicketView(updated);
  }

  async updateStatus(adminId: string, ticketId: string, dto: UpdateSupportTicketStatusDto) {
    const existing = await this.prisma.supportTicket.findUnique({ where: { id: ticketId } });
    if (!existing) throw new NotFoundException('Support ticket not found.');
    const updated = await this.prisma.supportTicket.update({ where: { id: ticketId }, data: { status: dto.status } });
    await this.auditService.record({
      actorId: adminId,
      actorRole: 'ADMIN',
      action: 'UPDATE_SUPPORT_TICKET_STATUS',
      targetType: 'SupportTicket',
      targetId: ticketId,
      beforeState: { status: existing.status },
      afterState: { status: updated.status },
    });
    return toTicketView(updated);
  }
}

function primaryRole(roles: string[]): AppRole {
  // ADMIN > OWNER > ATTENDANT > DRIVER — whichever is "most privileged"
  // is attributed as the message sender's role when a user holds more
  // than one; matches how mustAuthorizeOperator picks a role elsewhere.
  if (roles.includes('ADMIN')) return 'ADMIN';
  if (roles.includes('OWNER')) return 'OWNER';
  if (roles.includes('ATTENDANT')) return 'ATTENDANT';
  return 'DRIVER';
}

function toTicketView(t: {
  id: string;
  user_id: string;
  category: string;
  subject: string;
  description: string;
  status: string;
  assigned_to: string | null;
  related_booking_id: string | null;
  created_at: Date;
  updated_at: Date;
}) {
  return {
    id: t.id,
    userId: t.user_id,
    category: t.category,
    subject: t.subject,
    description: t.description,
    status: t.status,
    assignedTo: t.assigned_to,
    relatedBookingId: t.related_booking_id,
    createdAt: t.created_at,
    updatedAt: t.updated_at,
  };
}

function toMessageView(m: { id: string; ticket_id: string; sender_id: string; sender_role: string; message: string; created_at: Date }) {
  return {
    id: m.id,
    ticketId: m.ticket_id,
    senderId: m.sender_id,
    senderRole: m.sender_role,
    message: m.message,
    createdAt: m.created_at,
  };
}
