import { Controller, Get, Query } from '@nestjs/common';
import { ApiOperation, ApiTags } from '@nestjs/swagger';
import { Roles } from '../../common/decorators/roles.decorator';
import { CurrentUser, AuthenticatedUser } from '../../common/decorators/current-user.decorator';
import { EarningsService } from './earnings.service';
import { ListLedgerQueryDto } from './dto/settlements.dto';

@ApiTags('earnings')
@Controller({ version: '1' })
export class EarningsController {
  constructor(private readonly earningsService: EarningsService) {}

  @Get('owner/earnings/summary')
  @Roles('OWNER')
  @ApiOperation({ summary: 'Earnings total by status (PENDING/AVAILABLE/PROCESSING/SETTLED/FAILED/ADJUSTED/REVERSED)' })
  summary(@CurrentUser() owner: AuthenticatedUser) {
    return this.earningsService.summary(owner.id);
  }

  @Get('owner/earnings/ledger')
  @Roles('OWNER')
  @ApiOperation({ summary: 'List earnings ledger entries, optionally filtered by status' })
  ledger(@CurrentUser() owner: AuthenticatedUser, @Query() query: ListLedgerQueryDto) {
    return this.earningsService.ledger(owner.id, query.status);
  }
}
