import { Injectable, UnauthorizedException } from '@nestjs/common';
import { ConfigService } from '@nestjs/config';
import * as jwt from 'jsonwebtoken';
import { AppConfig } from '../../../common/config/configuration';

export interface AccessTokenPayload {
  sub: string; // user id
  sessionId: string;
  type: 'access';
}

export interface RefreshTokenPayload {
  sub: string;
  sessionId: string;
  type: 'refresh';
}

// Both secrets are plain strings (HMAC), never an RSA/EC key — pinning
// `algorithm`/`algorithms` explicitly on every sign/verify call (Milestone
// 12 security review) is defense-in-depth against JWT "algorithm
// confusion" (CWE-347: a token crafted with `alg: none` or an asymmetric
// algorithm accepted by a verifier that forgot to restrict algorithms).
// jsonwebtoken defaults to HS256 for a string secret and would already
// reject a mismatched alg in current versions, but relying on a library
// default for this specific check is exactly the kind of implicit trust a
// security review exists to remove.
const JWT_ALGORITHM = 'HS256' as const;

/**
 * Signs/verifies short-lived access tokens and longer-lived, rotating
 * refresh tokens. Access tokens are stateless (verified by signature +
 * expiry + a fresh user/role lookup in JwtAuthGuard); refresh tokens are
 * additionally checked against the Session row's stored hash so a stolen-
 * and-reused refresh token is detectable (see AuthService.refresh).
 */
@Injectable()
export class TokenService {
  constructor(private readonly configService: ConfigService<AppConfig, true>) {}

  signAccessToken(userId: string, sessionId: string): string {
    const cfg = this.configService.get('auth', { infer: true });
    return jwt.sign({ sub: userId, sessionId, type: 'access' } as AccessTokenPayload, cfg.accessSecret, {
      expiresIn: cfg.accessTtlSeconds,
      algorithm: JWT_ALGORITHM,
    });
  }

  signRefreshToken(userId: string, sessionId: string): string {
    const cfg = this.configService.get('auth', { infer: true });
    return jwt.sign({ sub: userId, sessionId, type: 'refresh' } as RefreshTokenPayload, cfg.refreshSecret, {
      expiresIn: cfg.refreshTtlSeconds,
      algorithm: JWT_ALGORITHM,
    });
  }

  verifyAccessToken(token: string): AccessTokenPayload {
    const cfg = this.configService.get('auth', { infer: true });
    try {
      const payload = jwt.verify(token, cfg.accessSecret, { algorithms: [JWT_ALGORITHM] }) as AccessTokenPayload;
      if (payload.type !== 'access') throw new Error('wrong token type');
      return payload;
    } catch {
      throw new UnauthorizedException('Your session has expired. Please sign in again.');
    }
  }

  verifyRefreshToken(token: string): RefreshTokenPayload {
    const cfg = this.configService.get('auth', { infer: true });
    try {
      const payload = jwt.verify(token, cfg.refreshSecret, { algorithms: [JWT_ALGORITHM] }) as RefreshTokenPayload;
      if (payload.type !== 'refresh') throw new Error('wrong token type');
      return payload;
    } catch {
      throw new UnauthorizedException('Your session has expired. Please sign in again.');
    }
  }

  accessTtlSeconds(): number {
    return this.configService.get('auth', { infer: true }).accessTtlSeconds;
  }

  refreshTtlSeconds(): number {
    return this.configService.get('auth', { infer: true }).refreshTtlSeconds;
  }
}
