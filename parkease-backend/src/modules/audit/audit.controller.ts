import { Controller, Get, Query } from '@nestjs/common';
import { ApiOperation, ApiTags } from '@nestjs/swagger';
import { Roles } from '../../common/decorators/roles.decorator';
import { AuditService } from './audit.service';
import { ListAuditLogsQueryDto } from './dto/audit.dto';

@ApiTags('admin-audit')
@Roles('ADMIN')
@Controller({ path: 'admin/audit-logs', version: '1' })
export class AuditController {
  constructor(private readonly auditService: AuditService) {}

  @Get()
  @ApiOperation({ summary: 'Read the append-only admin audit log, optionally filtered' })
  list(@Query() query: ListAuditLogsQueryDto) {
    return this.auditService.list(query);
  }
}
