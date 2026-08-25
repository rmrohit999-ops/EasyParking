import { apiFetch } from "@/lib/api";
import type { MapsQuotaSnapshotResponse } from "@/lib/types";

export default async function MapsQuotaPage() {
  const snapshot = await apiFetch<MapsQuotaSnapshotResponse>("/v1/admin/maps-quota");
  const capped = snapshot.globallyTripped || snapshot.skus.some((s) => s.capReached);

  return (
    <div>
      <h1 className="text-2xl font-bold text-slate-900">Maps API quota</h1>
      <p className="mt-1 text-sm text-slate-500">
        Live daily usage for the billable Google Maps Platform SKUs ParkEase is built to guard.
      </p>

      <div className="mt-6 inline-flex items-center gap-2 rounded-lg border border-slate-200 bg-white px-4 py-3 shadow-sm">
        <span className={`h-3 w-3 rounded-full ${capped ? "bg-red-600" : "bg-emerald-600"}`} />
        <span className={`font-semibold ${capped ? "text-red-700" : "text-emerald-700"}`}>
          {capped ? "80% CAP REACHED — INTENT FALLBACK ACTIVE" : "ACTIVE"}
        </span>
      </div>

      <h2 className="mt-8 text-sm font-semibold uppercase tracking-wide text-slate-500">Daily usage — {snapshot.date}</h2>
      <div className="mt-3 space-y-4">
        {snapshot.skus.map((sku) => {
          const effectivelyCapped = sku.capReached || snapshot.globallyTripped;
          return (
            <div key={sku.sku} className="rounded-lg border border-slate-200 bg-white p-4 shadow-sm">
              <div className="flex items-center justify-between">
                <p className="font-medium capitalize text-slate-900">{sku.sku}</p>
                <p className="text-sm text-slate-600">
                  {sku.count} / {sku.cap} ({sku.percentUsed}%)
                </p>
              </div>
              <div className="mt-2 h-2 w-full overflow-hidden rounded-full bg-slate-100">
                <div
                  className={`h-full rounded-full ${effectivelyCapped ? "bg-red-600" : "bg-emerald-600"}`}
                  style={{ width: `${Math.min(sku.percentUsed, 100)}%` }}
                />
              </div>
              {effectivelyCapped && (
                <p className="mt-2 text-xs text-red-600">
                  Blocked for the rest of today — falling back to free native-intent navigation.
                </p>
              )}
            </div>
          );
        })}
      </div>

      <p className="mt-8 max-w-2xl text-xs text-slate-400">
        No billable Maps API calls exist in ParkEase yet — these bars stay at 0% until Directions,
        Places, or server-side Geocoding features are built. Map rendering and on-device address
        lookup are free and aren&apos;t tracked here. See docs/MAPS_QUOTA_RUNBOOK.md for the Google
        Cloud Console hard-quota setup.
      </p>
    </div>
  );
}
