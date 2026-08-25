/**
 * The three billable Google Maps Platform SKUs this circuit breaker is
 * built to guard. None are actually called anywhere in ParkEase yet — see
 * MapsQuotaService's own doc comment for why this exists ahead of that.
 * Deliberately does NOT include map rendering (free/unlimited on native
 * Android SDKs) or Android's on-device Geocoder (a free system service,
 * not a Google Maps Platform SKU at all).
 */
export type MapsBillableSku = 'directions' | 'places' | 'geocoding';

export const MAPS_BILLABLE_SKUS: readonly MapsBillableSku[] = ['directions', 'places', 'geocoding'];

export interface QuotaCheckResult {
  allowed: boolean;
  sku: MapsBillableSku;
  count: number;
  cap: number;
  percentUsed: number;
}

export interface QuotaSkuUsage {
  sku: MapsBillableSku;
  count: number;
  cap: number;
  percentUsed: number;
  capReached: boolean;
}

export interface QuotaSnapshot {
  date: string;
  globallyTripped: boolean;
  skus: QuotaSkuUsage[];
}
