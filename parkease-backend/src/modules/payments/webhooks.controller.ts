import { Controller, Headers, HttpCode, Param, Post, RawBodyRequest, Req } from '@nestjs/common';
import { ApiExcludeController } from '@nestjs/swagger';
import { Request } from 'express';
import { Public } from '../../common/guards/jwt-auth.guard';
import { Throttle } from '../../common/rate-limit/throttle.decorator';
import { PaymentsService } from './payments.service';

/**
 * `POST /payments/webhooks/:gateway` — the one payments endpoint a real
 * user is never authenticated for (§7.7: "Public (signature-verified),
 * idempotent"). Trust comes entirely from
 * PaymentProvider.verifyWebhookSignature on the *raw* bytes (see main.ts's
 * `rawBody: true`), never from JWT/role/ownership — @Public() opts this
 * route out of JwtAuthGuard, and it has no @Roles()/@CheckOwnership()
 * because there is no authenticated caller to check.
 *
 * Still throttled (a generous per-gateway ceiling, not per-user — there is
 * no user) so a misbehaving or hostile flood of POSTs can't peg the
 * process, matching the ThrottleGuard's documented fail-open posture for
 * every other endpoint in this codebase.
 */
@ApiExcludeController()
@Controller({ path: 'payments/webhooks', version: '1' })
export class WebhooksController {
  constructor(private readonly paymentsService: PaymentsService) {}

  @Post(':gateway')
  @Public()
  @HttpCode(200)
  @Throttle({ limit: 300, windowSeconds: 60 })
  async handle(
    @Param('gateway') gateway: string,
    @Headers('x-razorpay-signature') razorpaySignature: string | undefined,
    @Req() req: RawBodyRequest<Request>,
  ) {
    const rawBody = req.rawBody;
    if (!rawBody) {
      // main.ts's rawBody:true should always populate this; a missing
      // rawBody is a server misconfiguration, not a client error, but we
      // still can't verify a signature over nothing.
      return { ok: false, reason: 'raw_body_unavailable' };
    }
    return this.paymentsService.handleWebhook(gateway, rawBody, razorpaySignature);
  }
}
