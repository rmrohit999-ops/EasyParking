/**
 * Normalizes and validates an Indian vehicle registration number.
 * Covers the standard state-code format (e.g. KA01AB1234, DL3CAF1234,
 * MH12AB1234) and the newer Bharat-series format (e.g. 24BH1234AB).
 * Never classifies a vehicle's CATEGORY from this string — registration
 * format has no reliable relationship to two-wheeler vs four-wheeler, so
 * category always comes from the driver's explicit category selection
 * (see Milestone 0 §"Do not classify a vehicle only from free text").
 */
const STANDARD_PATTERN = /^[A-Z]{2}[0-9]{1,2}[A-Z]{0,3}[0-9]{4}$/;
const BH_SERIES_PATTERN = /^[0-9]{2}BH[0-9]{4}[A-Z]{1,2}$/;

export function normalizeRegistrationNumber(raw: string): string {
  return raw.toUpperCase().replace(/[\s-]/g, '');
}

export function isValidIndianRegistrationNumber(raw: string): boolean {
  const normalized = normalizeRegistrationNumber(raw);
  return STANDARD_PATTERN.test(normalized) || BH_SERIES_PATTERN.test(normalized);
}
