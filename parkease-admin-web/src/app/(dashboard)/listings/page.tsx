import { apiFetch } from "@/lib/api";
import type { AdminPendingListingResponse } from "@/lib/types";
import { ListingCard } from "./ListingCard";

export default async function ListingsPage() {
  const listings = await apiFetch<AdminPendingListingResponse[]>("/v1/admin/parking/listings/pending");

  return (
    <div>
      <h1 className="text-2xl font-bold text-slate-900">Pending listings</h1>
      <p className="mt-1 text-sm text-slate-500">Review and approve new parking listings before they become publicly bookable.</p>

      <div className="mt-6 space-y-3">
        {listings.map((listing) => (
          <ListingCard key={listing.id} listing={listing} />
        ))}
        {listings.length === 0 && (
          <p className="rounded-lg border border-dashed border-slate-300 bg-white p-8 text-center text-sm text-slate-400">
            Nothing pending review right now.
          </p>
        )}
      </div>
    </div>
  );
}
