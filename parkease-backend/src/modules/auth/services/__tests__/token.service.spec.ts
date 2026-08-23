import { ConfigService } from '@nestjs/config';
import { UnauthorizedException } from '@nestjs/common';
import * as jwt from 'jsonwebtoken';
import { TokenService } from '../token.service';
import { AppConfig } from '../../../../common/config/configuration';

function buildConfigService(overrides: Partial<AppConfig['auth']> = {}): ConfigService<AppConfig, true> {
  const auth: AppConfig['auth'] = {
    accessSecret: 'test-access-secret-not-real',
    accessTtlSeconds: 1, // short TTL to exercise expiry in tests
    refreshSecret: 'test-refresh-secret-not-real',
    refreshTtlSeconds: 3600,
    passwordHashAlgo: 'argon2id',
    google: { clientId: '', clientSecret: '', isConfigured: false },
    ...overrides,
  };
  return { get: (key: string) => (key === 'auth' ? auth : undefined) } as unknown as ConfigService<AppConfig, true>;
}

describe('TokenService', () => {
  it('signs and verifies a matching access token', () => {
    const service = new TokenService(buildConfigService());
    const token = service.signAccessToken('user-1', 'session-1');
    const payload = service.verifyAccessToken(token);
    expect(payload.sub).toBe('user-1');
    expect(payload.sessionId).toBe('session-1');
    expect(payload.type).toBe('access');
  });

  it('rejects a refresh token presented as an access token', () => {
    const service = new TokenService(buildConfigService());
    const refreshToken = service.signRefreshToken('user-1', 'session-1');
    expect(() => service.verifyAccessToken(refreshToken)).toThrow(UnauthorizedException);
  });

  it('rejects a token signed with a different secret', () => {
    const serviceA = new TokenService(buildConfigService({ accessSecret: 'secret-a-16-chars-min' }));
    const serviceB = new TokenService(buildConfigService({ accessSecret: 'secret-b-16-chars-min' }));
    const token = serviceA.signAccessToken('user-1', 'session-1');
    expect(() => serviceB.verifyAccessToken(token)).toThrow(UnauthorizedException);
  });

  it('rejects an expired access token', async () => {
    const service = new TokenService(buildConfigService({ accessTtlSeconds: 1 }));
    const token = service.signAccessToken('user-1', 'session-1');
    await new Promise((resolve) => setTimeout(resolve, 1100));
    expect(() => service.verifyAccessToken(token)).toThrow(UnauthorizedException);
  });

  // Milestone 12 (release hardening) security review: verify() now pins
  // `algorithms: ['HS256']` explicitly rather than trusting the library
  // default — this is the regression test proving that fix actually does
  // something, by forging the exact token shape it exists to reject: one
  // that declares `alg: "none"` and carries no signature at all. Without
  // the explicit algorithms allowlist, jsonwebtoken (depending on version/
  // configuration) could otherwise be tricked into accepting this as a
  // validly "signed" token for any payload an attacker chooses.
  it('rejects a forged token that declares alg:none and has no signature', () => {
    const service = new TokenService(buildConfigService());
    const forged = jwt.sign({ sub: 'attacker', sessionId: 'session-1', type: 'access' }, null, { algorithm: 'none' });
    expect(() => service.verifyAccessToken(forged)).toThrow(UnauthorizedException);
  });
});
