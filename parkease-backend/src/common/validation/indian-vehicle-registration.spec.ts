import { isValidIndianRegistrationNumber, normalizeRegistrationNumber } from './indian-vehicle-registration';

describe('Indian vehicle registration validation', () => {
  it('accepts a standard state-code plate with spaces/hyphens', () => {
    expect(isValidIndianRegistrationNumber('KA 01 AB 1234')).toBe(true);
    expect(isValidIndianRegistrationNumber('KA-01-AB-1234')).toBe(true);
  });

  it('accepts a plate with a 1-digit RTO code', () => {
    expect(isValidIndianRegistrationNumber('DL3CAF1234')).toBe(true);
  });

  it('accepts a Bharat-series (BH) plate', () => {
    expect(isValidIndianRegistrationNumber('24BH1234AB')).toBe(true);
  });

  it('rejects an obviously malformed string', () => {
    expect(isValidIndianRegistrationNumber('not-a-plate')).toBe(false);
    expect(isValidIndianRegistrationNumber('12345')).toBe(false);
  });

  it('normalizes case and separators consistently', () => {
    expect(normalizeRegistrationNumber('ka 01 ab 1234')).toBe('KA01AB1234');
  });
});
