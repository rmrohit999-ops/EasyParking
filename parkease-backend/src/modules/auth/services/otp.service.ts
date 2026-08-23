import {
  HttpException,
  HttpStatus,
  Injectable,
  Logger,
  ServiceUnavailableException,
  UnauthorizedException,
} from '@nestjs/common';
import { ConfigService } from '@nestjs/config';
import { randomInt } from 'crypto';
import * as argon2 from 'argon2';
import { PrismaService } from '../../../common/prisma/prisma.service';
import { AppConfig } from '../../../common/config/configuration';

class TooManyRequestsException extends HttpException {
  constructor(message: string) {
    super(message, HttpStatus.TOO_MANY_REQUESTS);
  }
}

export type OtpPurpose = 'LOGIN' | 'REGISTER' | 'RESET_PASSWORD';

/**
 * Real OTP lifecycle: generates a cryptographically random code, stores
 * only its hash, enforces resend cooldown + max verify attempts, and
 * refuses to issue an OTP at all when no SMS provider is configured
 * (Milestone 0: "never use fake OTPs ... unavailable-state behavior when
 * credentials are missing"). There is no dev bypass code path here — if
 * SMS isn't configured, phone-OTP auth is unavailable, full stop.
 */
@Injectable()
export class OtpService {
  private readonly logger = new Logger(OtpService.name);

  constructor(
    private readonly prisma: PrismaService,
    private readonly configService: ConfigService<AppConfig, true>,
  ) {}

  isConfigured(): boolean {
    return this.configService.get('otp', { infer: true }).isConfigured;
  }

  async requestOtp(phoneOrEmail: string, purpose: OtpPurpose, requestIp?: string): Promise<{ expiresInSeconds: number }> {
    const cfg = this.configService.get('otp', { infer: true });
    if (!cfg.isConfigured) {
      throw new ServiceUnavailableException(
        'Phone verification is temporarily unavailable. Please use email login instead.',
      );
    }

    const recent = await this.prisma.otpChallenge.findFirst({
      where: { phone_or_email: phoneOrEmail, purpose },
      orderBy: { created_at: 'desc' },
    });
    if (recent && Date.now() - recent.created_at.getTime() < cfg.resendCooldownSeconds * 1000) {
      throw new TooManyRequestsException('Please wait before requesting another code.');
    }

    const code = generateNumericOtp(cfg.length);
    const codeHash = await argon2.hash(code);
    const expiresAt = new Date(Date.now() + cfg.ttlSeconds * 1000);

    await this.prisma.otpChallenge.create({
      data: { phone_or_email: phoneOrEmail, purpose, code_hash: codeHash, expires_at: expiresAt, request_ip: requestIp },
    });

    await this.sendViaProvider(phoneOrEmail, code);
    return { expiresInSeconds: cfg.ttlSeconds };
  }

  async verifyOtp(phoneOrEmail: string, purpose: OtpPurpose, code: string): Promise<void> {
    const cfg = this.configService.get('otp', { infer: true });
    const challenge = await this.prisma.otpChallenge.findFirst({
      where: { phone_or_email: phoneOrEmail, purpose, consumed_at: null },
      orderBy: { created_at: 'desc' },
    });

    if (!challenge) throw new UnauthorizedException('That code is invalid or has expired. Please request a new one.');
    if (challenge.expires_at.getTime() < Date.now()) {
      throw new UnauthorizedException('That code has expired. Please request a new one.');
    }
    if (challenge.attempt_count >= cfg.maxAttempts) {
      throw new TooManyRequestsException('Too many incorrect attempts. Please request a new code.');
    }

    const valid = await argon2.verify(challenge.code_hash, code).catch(() => false);
    if (!valid) {
      await this.prisma.otpChallenge.update({
        where: { id: challenge.id },
        data: { attempt_count: { increment: 1 } },
      });
      throw new UnauthorizedException('That code is incorrect. Please try again.');
    }

    await this.prisma.otpChallenge.update({ where: { id: challenge.id }, data: { consumed_at: new Date() } });
  }

  /**
   * Real provider boundary. With no SMS_PROVIDER configured, requestOtp()
   * above already refuses before reaching here — this method exists so a
   * concrete provider (MSG91/Twilio/etc.) has a single integration point
   * once credentials are supplied, per the Milestone 0 environment spec.
   */
  private async sendViaProvider(phoneOrEmail: string, code: string): Promise<void> {
    const cfg = this.configService.get('otp', { infer: true });
    this.logger.log(`Dispatching OTP via ${cfg.smsProvider} to ${maskContact(phoneOrEmail)}`);
    // TODO(Milestone 7-adjacent infra work): wire the real provider SDK call
    // here (e.g. MSG91/Twilio REST call using cfg.smsApiKey/cfg.smsSenderId).
    // Deliberately not implemented against a specific vendor in this
    // foundation pass — see Milestone 0 §18 open question on SMS provider
    // choice. Never logs or returns the OTP code itself.
    void code;
  }
}

function generateNumericOtp(length: number): string {
  const digits = Array.from({ length }, () => randomInt(0, 10));
  return digits.join('');
}

function maskContact(value: string): string {
  if (value.includes('@')) {
    const [user, domain] = value.split('@');
    return `${user.slice(0, 2)}***@${domain}`;
  }
  return value.length > 4 ? `${'*'.repeat(value.length - 4)}${value.slice(-4)}` : '***';
}
