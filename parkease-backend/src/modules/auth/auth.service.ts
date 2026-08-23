import {
  BadRequestException,
  ConflictException,
  Injectable,
  UnauthorizedException,
} from '@nestjs/common';
import { Request } from 'express';
import { randomBytes, createHash } from 'crypto';
import { PrismaService } from '../../common/prisma/prisma.service';
import { PasswordService } from './services/password.service';
import { TokenService } from './services/token.service';
import { OtpService } from './services/otp.service';
import { GoogleAuthService } from './services/google-auth.service';
import {
  ChangePasswordDto,
  ForgotPasswordDto,
  GoogleSignInDto,
  LoginDto,
  OtpRequestDto,
  OtpVerifyDto,
  RegisterDto,
  ResetPasswordDto,
} from './dto/auth.dto';

interface TokenPair {
  accessToken: string;
  refreshToken: string;
  expiresInSeconds: number;
}

@Injectable()
export class AuthService {
  constructor(
    private readonly prisma: PrismaService,
    private readonly passwordService: PasswordService,
    private readonly tokenService: TokenService,
    private readonly otpService: OtpService,
    private readonly googleAuthService: GoogleAuthService,
  ) {}

  // ---------------------------------------------------------------------
  // Registration / login
  // ---------------------------------------------------------------------

  async register(dto: RegisterDto, req: Request) {
    if (!dto.email && !dto.phone) {
      throw new BadRequestException('An email or phone number is required to register.');
    }
    if (dto.email) {
      const existing = await this.prisma.user.findUnique({ where: { email: dto.email } });
      if (existing) throw new ConflictException('An account with that email already exists.');
    }
    if (dto.phone) {
      const existing = await this.prisma.user.findUnique({ where: { phone: dto.phone } });
      if (existing) throw new ConflictException('An account with that phone number already exists.');
    }

    const passwordHash = dto.password ? await this.passwordService.hash(dto.password) : null;

    const user = await this.prisma.user.create({
      data: {
        email: dto.email,
        phone: dto.phone,
        password_hash: passwordHash,
        // Registration always grants DRIVER by default — becoming an OWNER
        // is an explicit later step (POST /users/me/roles/owner), never
        // implicit, so onboarding/verification requirements apply.
        roles: { create: { role: 'DRIVER' } },
      },
      include: { roles: true },
    });

    return this.issueSessionAndTokens(user.id, req);
  }

  async login(dto: LoginDto, req: Request): Promise<TokenPair> {
    if (!dto.email || !dto.password) {
      throw new UnauthorizedException('Incorrect email or password.');
    }
    const user = await this.prisma.user.findUnique({ where: { email: dto.email } });
    // Deliberately identical error for "no such user" and "wrong password"
    // so the endpoint doesn't leak account existence (Milestone 0 T1).
    if (!user || !user.password_hash || user.status !== 'ACTIVE') {
      throw new UnauthorizedException('Incorrect email or password.');
    }
    const valid = await this.passwordService.verify(user.password_hash, dto.password);
    if (!valid) throw new UnauthorizedException('Incorrect email or password.');

    return this.issueSessionAndTokens(user.id, req);
  }

  async requestOtp(dto: OtpRequestDto) {
    return this.otpService.requestOtp(dto.phone, dto.purpose);
  }

  async verifyOtpAndLogin(dto: OtpVerifyDto, req: Request): Promise<TokenPair> {
    await this.otpService.verifyOtp(dto.phone, dto.purpose, dto.code);

    let user = await this.prisma.user.findUnique({ where: { phone: dto.phone } });
    if (!user) {
      if (dto.purpose !== 'REGISTER' && dto.purpose !== 'LOGIN') {
        throw new UnauthorizedException('No account found for that phone number.');
      }
      user = await this.prisma.user.create({
        data: {
          phone: dto.phone,
          phone_verified_at: new Date(),
          roles: { create: { role: 'DRIVER' } },
        },
      });
    } else if (!user.phone_verified_at) {
      await this.prisma.user.update({ where: { id: user.id }, data: { phone_verified_at: new Date() } });
    }

    if (user.status !== 'ACTIVE') throw new UnauthorizedException('This account is not active.');
    return this.issueSessionAndTokens(user.id, req);
  }

  async googleSignIn(dto: GoogleSignInDto, req: Request): Promise<TokenPair> {
    const identity = await this.googleAuthService.verifyIdToken(dto.idToken);

    let user = await this.prisma.user.findUnique({ where: { google_subject_id: identity.googleSubjectId } });
    if (!user) {
      const byEmail = await this.prisma.user.findUnique({ where: { email: identity.email } });
      if (byEmail) {
        user = await this.prisma.user.update({
          where: { id: byEmail.id },
          data: { google_subject_id: identity.googleSubjectId },
        });
      } else {
        user = await this.prisma.user.create({
          data: {
            email: identity.email,
            email_verified_at: identity.emailVerified ? new Date() : null,
            google_subject_id: identity.googleSubjectId,
            profile_photo_url: identity.pictureUrl,
            roles: { create: { role: 'DRIVER' } },
          },
        });
      }
    }

    if (user.status !== 'ACTIVE') throw new UnauthorizedException('This account is not active.');
    return this.issueSessionAndTokens(user.id, req);
  }

  // ---------------------------------------------------------------------
  // Session lifecycle
  // ---------------------------------------------------------------------

  private async issueSessionAndTokens(userId: string, req: Request): Promise<TokenPair> {
    const refreshTtl = this.tokenService.refreshTtlSeconds();
    // Session row is created with a placeholder hash, then immediately
    // updated once we have the sessionId to embed in the refresh JWT —
    // avoids a chicken/egg between "need sessionId to sign token" and
    // "need the token to compute its hash."
    const session = await this.prisma.session.create({
      data: {
        user_id: userId,
        refresh_token_hash: '',
        device_id: req.header('x-device-id') ?? undefined,
        device_info: req.header('user-agent') ?? undefined,
        ip_address: req.ip,
        user_agent: req.header('user-agent') ?? undefined,
        expires_at: new Date(Date.now() + refreshTtl * 1000),
      },
    });

    const accessToken = this.tokenService.signAccessToken(userId, session.id);
    const refreshToken = this.tokenService.signRefreshToken(userId, session.id);
    await this.prisma.session.update({
      where: { id: session.id },
      data: { refresh_token_hash: hashToken(refreshToken) },
    });

    return { accessToken, refreshToken, expiresInSeconds: this.tokenService.accessTtlSeconds() };
  }

  async refresh(refreshToken: string): Promise<TokenPair> {
    const payload = this.tokenService.verifyRefreshToken(refreshToken);
    const session = await this.prisma.session.findUnique({ where: { id: payload.sessionId } });

    if (!session || session.revoked_at || session.expires_at.getTime() < Date.now()) {
      throw new UnauthorizedException('Your session has expired. Please sign in again.');
    }

    // Reuse/theft detection: the presented refresh token must match the
    // one currently on record. A mismatch means either an old (already-
    // rotated) token is being replayed, or the token was stolen — either
    // way, kill the session rather than silently accepting it.
    if (session.refresh_token_hash !== hashToken(refreshToken)) {
      await this.prisma.session.update({ where: { id: session.id }, data: { revoked_at: new Date() } });
      throw new UnauthorizedException('Your session is no longer valid. Please sign in again.');
    }

    const newRefreshToken = this.tokenService.signRefreshToken(payload.sub, session.id);
    const newAccessToken = this.tokenService.signAccessToken(payload.sub, session.id);
    await this.prisma.session.update({
      where: { id: session.id },
      data: {
        refresh_token_hash: hashToken(newRefreshToken),
        expires_at: new Date(Date.now() + this.tokenService.refreshTtlSeconds() * 1000),
      },
    });

    return {
      accessToken: newAccessToken,
      refreshToken: newRefreshToken,
      expiresInSeconds: this.tokenService.accessTtlSeconds(),
    };
  }

  async logout(sessionId: string): Promise<void> {
    await this.prisma.session.updateMany({
      where: { id: sessionId, revoked_at: null },
      data: { revoked_at: new Date() },
    });
  }

  async listSessions(userId: string) {
    const sessions = await this.prisma.session.findMany({
      where: { user_id: userId, revoked_at: null, expires_at: { gt: new Date() } },
      orderBy: { issued_at: 'desc' },
    });
    return sessions.map((s) => ({
      id: s.id,
      deviceInfo: s.device_info,
      ipAddress: s.ip_address,
      issuedAt: s.issued_at,
      expiresAt: s.expires_at,
    }));
  }

  async revokeSession(userId: string, sessionId: string): Promise<void> {
    await this.prisma.session.updateMany({
      where: { id: sessionId, user_id: userId, revoked_at: null },
      data: { revoked_at: new Date() },
    });
  }

  // ---------------------------------------------------------------------
  // Password management
  // ---------------------------------------------------------------------

  async forgotPassword(dto: ForgotPasswordDto): Promise<void> {
    const user = await this.prisma.user.findUnique({ where: { email: dto.email } });
    // Always behave identically whether or not the account exists, so the
    // endpoint can't be used to enumerate registered emails.
    if (!user) return;

    const resetToken = randomBytes(32).toString('hex');
    // Reuses the OTP challenge table as a generic short-lived-secret store
    // (purpose=RESET_PASSWORD, phone_or_email=email) rather than a second
    // near-identical table.
    await this.prisma.otpChallenge.create({
      data: {
        phone_or_email: dto.email,
        purpose: 'RESET_PASSWORD',
        code_hash: createHash('sha256').update(resetToken).digest('hex'),
        expires_at: new Date(Date.now() + 30 * 60 * 1000),
      },
    });

    // TODO: dispatch via the email provider boundary once EMAIL_PROVIDER is
    // configured (see src/common/config). Never logs the raw reset token.
  }

  async resetPassword(dto: ResetPasswordDto): Promise<void> {
    const tokenHash = createHash('sha256').update(dto.resetToken).digest('hex');
    const challenge = await this.prisma.otpChallenge.findFirst({
      where: { purpose: 'RESET_PASSWORD', code_hash: tokenHash, consumed_at: null },
    });
    if (!challenge || challenge.expires_at.getTime() < Date.now()) {
      throw new UnauthorizedException('That reset link is invalid or has expired.');
    }

    const user = await this.prisma.user.findUnique({ where: { email: challenge.phone_or_email } });
    if (!user) throw new UnauthorizedException('That reset link is invalid or has expired.');

    const newHash = await this.passwordService.hash(dto.newPassword);
    await this.prisma.$transaction([
      this.prisma.user.update({ where: { id: user.id }, data: { password_hash: newHash } }),
      this.prisma.otpChallenge.update({ where: { id: challenge.id }, data: { consumed_at: new Date() } }),
      // Resetting a password revokes every existing session — a credential
      // compromise scenario shouldn't leave old sessions alive.
      this.prisma.session.updateMany({ where: { user_id: user.id, revoked_at: null }, data: { revoked_at: new Date() } }),
    ]);
  }

  async changePassword(userId: string, dto: ChangePasswordDto): Promise<void> {
    const user = await this.prisma.user.findUnique({ where: { id: userId } });
    if (!user?.password_hash) throw new BadRequestException('This account does not use a password.');

    const valid = await this.passwordService.verify(user.password_hash, dto.currentPassword);
    if (!valid) throw new UnauthorizedException('Your current password is incorrect.');

    const newHash = await this.passwordService.hash(dto.newPassword);
    await this.prisma.user.update({ where: { id: userId }, data: { password_hash: newHash } });
  }

  // ---------------------------------------------------------------------
  // Account deletion
  // ---------------------------------------------------------------------

  /**
   * See Milestone 0 "Account Deletion and Data Deletion": personal data is
   * anonymized (not just soft-flagged), all sessions are revoked, and
   * records with a legal/financial retention requirement (transactions,
   * audit logs, disputes) are deliberately NOT deleted — they lose their
   * link to identifying user fields where the schema allows it, and the
   * user-facing account becomes fully inaccessible either way.
   */
  async deleteAccount(userId: string): Promise<void> {
    const anonymizedEmail = `deleted-${userId}@parkease.invalid`;
    await this.prisma.$transaction([
      this.prisma.user.update({
        where: { id: userId },
        data: {
          status: 'DELETED',
          email: anonymizedEmail,
          phone: null,
          password_hash: null,
          google_subject_id: null,
          profile_photo_url: null,
        },
      }),
      this.prisma.session.updateMany({ where: { user_id: userId, revoked_at: null }, data: { revoked_at: new Date() } }),
      this.prisma.notificationDevice.updateMany({ where: { user_id: userId, revoked_at: null }, data: { revoked_at: new Date() } }),
    ]);
  }
}

function hashToken(token: string): string {
  return createHash('sha256').update(token).digest('hex');
}
