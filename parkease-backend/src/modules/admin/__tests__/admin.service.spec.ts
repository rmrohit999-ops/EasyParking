import { ForbiddenException } from '@nestjs/common';
import { assertMayActOnAdminTier } from '../admin.service';

describe('assertMayActOnAdminTier', () => {
  it('allows a plain ADMIN to act on a DRIVER', () => {
    expect(() => assertMayActOnAdminTier(['ADMIN'], ['DRIVER'])).not.toThrow();
  });

  it('allows a plain ADMIN to act on an OWNER or ATTENDANT', () => {
    expect(() => assertMayActOnAdminTier(['ADMIN'], ['OWNER'])).not.toThrow();
    expect(() => assertMayActOnAdminTier(['ADMIN'], ['ATTENDANT'])).not.toThrow();
  });

  it('blocks a plain ADMIN from acting on another ADMIN', () => {
    expect(() => assertMayActOnAdminTier(['ADMIN'], ['ADMIN'])).toThrow(ForbiddenException);
  });

  it('blocks a plain ADMIN from acting on a SUPER_ADMIN', () => {
    expect(() => assertMayActOnAdminTier(['ADMIN'], ['SUPER_ADMIN'])).toThrow(ForbiddenException);
  });

  it('allows a SUPER_ADMIN to act on another ADMIN', () => {
    expect(() => assertMayActOnAdminTier(['ADMIN', 'SUPER_ADMIN'], ['ADMIN'])).not.toThrow();
  });

  it('allows a SUPER_ADMIN to act on another SUPER_ADMIN', () => {
    expect(() => assertMayActOnAdminTier(['ADMIN', 'SUPER_ADMIN'], ['SUPER_ADMIN'])).not.toThrow();
  });

  it('handles a target with multiple roles, one of which is admin-tier', () => {
    expect(() => assertMayActOnAdminTier(['ADMIN'], ['DRIVER', 'ADMIN'])).toThrow(ForbiddenException);
  });
});
