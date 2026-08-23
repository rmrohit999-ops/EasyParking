import { Body, Controller, Get, Param, Post } from '@nestjs/common';
import { ApiOperation, ApiTags } from '@nestjs/swagger';
import { Roles } from '../../common/decorators/roles.decorator';
import { Throttle } from '../../common/rate-limit/throttle.decorator';
import { CurrentUser, AuthenticatedUser } from '../../common/decorators/current-user.decorator';
import { PaymentsService } from './payments.service';
import { CreatePaymentOrderDto, RetryPaymentDto } from './dto/payments.dto';

/**
 * §7.7. `POST /payments/webhooks/:gateway` is deliberately NOT here — it's
 * public (gateway-signed, not user-authenticated) and needs the raw
 * request body for signature verification, so it lives in its own
 * WebhooksController with its own, much narrower guard configuration.
 */
@ApiTags('payments')
@Controller({ path: 'payments', version: '1' })
export class PaymentsController {
  constructor(private readonly paymentsService: PaymentsService) {}

  @Post('orders')
  @Roles('DRIVER')
  @Throttle({ limit: 10, windowSeconds: 60 })
  @ApiOperation({ summary: 'Create a payment order for a booking awaiting payment (idempotent)' })
  createOrder(@CurrentUser() user: AuthenticatedUser, @Body() dto: CreatePaymentOrderDto) {
    return this.paymentsService.createOrder(user.id, dto);
  }

  @Get(':paymentOrderId')
  @ApiOperation({ summary: 'Get one payment order (own, or ADMIN)' })
  getOne(@CurrentUser() user: AuthenticatedUser & { roles: string[] }, @Param('paymentOrderId') paymentOrderId: string) {
    return this.paymentsService.getPayment(user, paymentOrderId);
  }

  @Post(':paymentOrderId/retry')
  @Roles('DRIVER')
  @Throttle({ limit: 10, windowSeconds: 60 })
  @ApiOperation({ summary: 'Retry a FAILED payment order with a fresh gateway order (idempotent)' })
  retry(
    @CurrentUser() user: AuthenticatedUser,
    @Param('paymentOrderId') paymentOrderId: string,
    @Body() dto: RetryPaymentDto,
  ) {
    return this.paymentsService.retryPayment(user.id, paymentOrderId, dto);
  }
}
