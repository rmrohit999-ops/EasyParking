import { missingSuperAdminRoles } from '../jwt-auth.guard';

describe('missingSuperAdminRoles', () => {
  it('returns nothing when SUPER_ADMIN_EMAIL is unset', () => {
    expect(missingSuperAdminRoles('someone@example.com', '', [])).toEqual([]);
  });

  it('returns nothing when the email does not match', () => {
    expect(missingSuperAdminRoles('someone@example.com', 'super@parkease.app', [])).toEqual([]);
  });

  it('is case-insensitive and trims whitespace on the account email', () => {
    expect(missingSuperAdminRoles(' Super@ParkEase.App ', 'super@parkease.app', [])).toEqual(['ADMIN', 'SUPER_ADMIN']);
  });

  it('returns nothing for a null email (e.g. phone-only account)', () => {
    expect(missingSuperAdminRoles(null, 'super@parkease.app', [])).toEqual([]);
  });

  it('grants both ADMIN and SUPER_ADMIN when the account has neither yet', () => {
    expect(missingSuperAdminRoles('super@parkease.app', 'super@parkease.app', ['DRIVER'])).toEqual(['ADMIN', 'SUPER_ADMIN']);
  });

  it('grants only the missing role when ADMIN is already present', () => {
    expect(missingSuperAdminRoles('super@parkease.app', 'super@parkease.app', ['ADMIN'])).toEqual(['SUPER_ADMIN']);
  });

  it('is a no-op once both roles are already present (idempotent, no DB write needed)', () => {
    expect(missingSuperAdminRoles('super@parkease.app', 'super@parkease.app', ['ADMIN', 'SUPER_ADMIN'])).toEqual([]);
  });
});
