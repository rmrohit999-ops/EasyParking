import { extractHandshakeToken } from '../realtime.gateway';

describe('extractHandshakeToken', () => {
  it('reads the token from handshake.auth.token (the standard socket.io-client pattern)', () => {
    expect(extractHandshakeToken({ auth: { token: 'abc123' } })).toBe('abc123');
  });

  it('falls back to a Bearer authorization header', () => {
    expect(extractHandshakeToken({ headers: { authorization: 'Bearer xyz789' } })).toBe('xyz789');
  });

  it('prefers handshake.auth.token over the header when both are present', () => {
    expect(extractHandshakeToken({ auth: { token: 'from-auth' }, headers: { authorization: 'Bearer from-header' } })).toBe('from-auth');
  });

  it('returns null when neither is present', () => {
    expect(extractHandshakeToken({})).toBeNull();
  });

  it('returns null for a non-Bearer authorization header', () => {
    expect(extractHandshakeToken({ headers: { authorization: 'Basic abc123' } })).toBeNull();
  });

  it('returns null for an empty auth.token string', () => {
    expect(extractHandshakeToken({ auth: { token: '' } })).toBeNull();
  });

  it('returns null when auth.token is not a string', () => {
    expect(extractHandshakeToken({ auth: { token: 12345 } })).toBeNull();
  });
});
