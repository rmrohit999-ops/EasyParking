"use server";

import { revalidatePath } from "next/cache";
import { apiFetch, ApiError } from "@/lib/api";

export interface ListingActionState {
  error: string | null;
}

// See users/actions.ts's comment on why no runtime value export belongs
// in a "use server" file — same fix here.

export async function approveListing(_prevState: ListingActionState, formData: FormData): Promise<ListingActionState> {
  const listingId = String(formData.get("listingId") ?? "");
  try {
    await apiFetch(`/v1/admin/parking/listings/${listingId}/approve`, { method: "POST" });
  } catch (err) {
    return { error: err instanceof ApiError ? err.message : "Something went wrong. Please try again." };
  }
  revalidatePath("/listings");
  return { error: null };
}

export async function rejectListing(_prevState: ListingActionState, formData: FormData): Promise<ListingActionState> {
  const listingId = String(formData.get("listingId") ?? "");
  const reason = String(formData.get("reason") ?? "").trim();
  if (!reason) {
    return { error: "A reason is required to reject a listing." };
  }
  try {
    await apiFetch(`/v1/admin/parking/listings/${listingId}/reject`, {
      method: "POST",
      body: JSON.stringify({ reason }),
    });
  } catch (err) {
    return { error: err instanceof ApiError ? err.message : "Something went wrong. Please try again." };
  }
  revalidatePath("/listings");
  return { error: null };
}
