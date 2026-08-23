import { PasswordService } from '../password.service';

describe('PasswordService', () => {
  const service = new PasswordService();

  it('hashes a password and verifies the correct plaintext', async () => {
    const hash = await service.hash('correct horse battery staple');
    expect(hash).not.toContain('correct horse battery staple');
    expect(await service.verify(hash, 'correct horse battery staple')).toBe(true);
  });

  it('rejects an incorrect password against a valid hash', async () => {
    const hash = await service.hash('correct horse battery staple');
    expect(await service.verify(hash, 'wrong password')).toBe(false);
  });

  it('never throws on a malformed hash — returns false instead', async () => {
    await expect(service.verify('not-a-real-argon2-hash', 'anything')).resolves.toBe(false);
  });

  it('produces a different hash each time (random salt)', async () => {
    const [a, b] = await Promise.all([service.hash('same-password'), service.hash('same-password')]);
    expect(a).not.toEqual(b);
  });
});
