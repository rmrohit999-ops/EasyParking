"use client";

import { useActionState, useState } from "react";
import { approveListing, rejectListing, type ListingActionState } from "./actions";
import type { AdminPendingListingResponse } from "@/lib/types";

const initialActionState: ListingActionState = { error: null };

export function ListingCard({ listing }: { listing: AdminPendingListingResponse }) {
  const [approveState, approveAction, approvePending] = useActionState(approveListing, initialActionState);
  const [rejectState, rejectAction, rejectPending] = useActionState(rejectListing, initialActionState);
  const [showRejectForm, setShowRejectForm] = useState(false);

  return (
    <div className="rounded-lg border border-slate-200 bg-white p-4 shadow-sm">
      <div className="flex items-start justify-between gap-4">
        <div>
          <p className="font-semibold text-slate-900">{listing.name}</p>
          <p className="mt-0.5 text-xs text-slate-500">
            {listing.parkingType} · {listing.approvalStatus}
          </p>
        </div>
        {!showRejectForm && (
          <div className="flex shrink-0 gap-2">
            <form action={approveAction}>
              <input type="hidden" name="listingId" value={listing.id} />
              <button
                type="submit"
                disabled={approvePending}
                className="rounded-md bg-emerald-700 px-3 py-1.5 text-xs font-semibold text-white hover:bg-emerald-800 disabled:opacity-50"
              >
                {approvePending ? "…" : "Approve"}
              </button>
            </form>
            <button
              type="button"
              onClick={() => setShowRejectForm(true)}
              className="rounded-md border border-red-200 px-3 py-1.5 text-xs font-semibold text-red-700 hover:bg-red-50"
            >
              Reject
            </button>
          </div>
        )}
      </div>

      {showRejectForm && (
        <form action={rejectAction} className="mt-3 flex items-center gap-2">
          <input type="hidden" name="listingId" value={listing.id} />
          <input
            name="reason"
            placeholder="Reason for rejection"
            required
            className="flex-1 rounded border border-slate-300 px-2 py-1.5 text-xs"
          />
          <button type="submit" disabled={rejectPending} className="rounded-md bg-red-700 px-3 py-1.5 text-xs font-semibold text-white hover:bg-red-800 disabled:opacity-50">
            {rejectPending ? "…" : "Confirm reject"}
          </button>
          <button type="button" onClick={() => setShowRejectForm(false)} className="text-xs text-slate-400 hover:text-slate-600">
            Cancel
          </button>
        </form>
      )}

      {(approveState.error || rejectState.error) && (
        <p className="mt-2 text-xs text-red-600">{approveState.error ?? rejectState.error}</p>
      )}
    </div>
  );
}
