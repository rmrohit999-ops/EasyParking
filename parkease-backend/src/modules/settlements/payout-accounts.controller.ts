import { Body, Controller, Delete, Get, Param, Patch, Post } from '@nestjs/common';
import { ApiOperation, ApiTags } from '@nestjs/swagger';
import { Roles } from '../../common/decorators/roles.decorator';
import { CurrentUser, AuthenticatedUser } from '../../common/decorators/current-user.decorator';
import { PayoutAccountsService } from './payout-accounts.service';
import { CreatePayoutAccountDto, VerifyPayoutAccountDto } from './dto/settlements.dto';

@ApiTags('payout-accounts')
@Controller({ version: '1' })
export class PayoutAccountsController {
  constructor(private readonly payoutAccountsService: PayoutAccountsService) {}

  @Post('owner/payout-accounts')
  @Roles('OWNER')
  @ApiOperation({ summary: 'Add a bank/UPI payout destination (unverified until an admin confirms it)' })
  create(@CurrentUser() owner: AuthenticatedUser, @Body() dto: CreatePayoutAccountDto) {
    return this.payoutAccountsService.create(owner.id, dto);
  }

  @Get('owner/payout-accounts')
  @Roles('OWNER')
  @ApiOperation({ summary: 'List your payout accounts' })
  list(@CurrentUser() owner: AuthenticatedUser) {
    return this.payoutAccountsService.list(owner.id);
  }

  @Post('owner/payout-accounts/:accountId/primary')
  @Roles('OWNER')
  @ApiOperation({ summary: 'Make this your primary payout account (used for future settlements)' })
  setPrimary(@CurrentUser() owner: AuthenticatedUser, @Param('accountId') accountId: string) {
    return this.payoutAccountsService.setPrimary(owner.id, accountId);
  }

  @Delete('owner/payout-accounts/:accountId')
  @Roles('OWNER')
  @ApiOperation({ summary: 'Remove a payout account' })
  remove(@CurrentUser() owner: AuthenticatedUser, @Param('accountId') accountId: string) {
    return this.payoutAccountsService.remove(owner.id, accountId);
  }

  @Patch('admin/payout-accounts/:accountId/verify')
  @Roles('ADMIN')
  @ApiOperation({ summary: "Set a payout account's verification status" })
  verify(@Param('accountId') accountId: string, @Body() dto: VerifyPayoutAccountDto) {
    return this.payoutAccountsService.adminVerify(accountId, dto);
  }
}
