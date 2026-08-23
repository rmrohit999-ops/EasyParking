import { Injectable, ServiceUnavailableException, UnauthorizedException } from '@nestjs/common';
import { ConfigService } from '@nestjs/config';
import { OAuth2Client } from 'google-auth-library';
import { AppConfig } from '../../../common/config/configuration';

export interface GoogleIdentity {
  googleSubjectId: string;
  email: string;
  emailVerified: boolean;
  name?: string;
  pictureUrl?: string;
}

/**
 * Verifies a Google ID token server-side (never trusts a client-asserted
 * email/subject). Reports unavailable — doesn't fake success — when
 * GOOGLE_OAUTH_CLIENT_ID isn't configured, per Milestone 0's provider
 * abstraction requirement for external auth.
 */
@Injectable()
export class GoogleAuthService {
  private client: OAuth2Client | null = null;

  constructor(private readonly configService: ConfigService<AppConfig, true>) {
    const cfg = this.configService.get('auth', { infer: true }).google;
    if (cfg.isConfigured) {
      this.client = new OAuth2Client(cfg.clientId);
    }
  }

  isConfigured(): boolean {
    return this.client !== null;
  }

  async verifyIdToken(idToken: string): Promise<GoogleIdentity> {
    if (!this.client) {
      throw new ServiceUnavailableException('Sign in with Google is temporarily unavailable.');
    }
    const cfg = this.configService.get('auth', { infer: true }).google;
    const ticket = await this.client
      .verifyIdToken({ idToken, audience: cfg.clientId })
      .catch(() => {
        throw new UnauthorizedException('Could not verify that Google sign-in. Please try again.');
      });
    const payload = ticket.getPayload();
    if (!payload || !payload.sub || !payload.email) {
      throw new UnauthorizedException('Could not verify that Google sign-in. Please try again.');
    }
    return {
      googleSubjectId: payload.sub,
      email: payload.email,
      emailVerified: Boolean(payload.email_verified),
      name: payload.name,
      pictureUrl: payload.picture,
    };
  }
}
