"use server";

import { revalidatePath } from "next/cache";
import { apiFetch, ApiError } from "@/lib/api";

export interface UserActionState {
  error: string | null;
}

// A "use server" file may only export async functions — no plain values,
// not even a constant object (Next.js throws "A 'use server' file can
// only export async functions, found object" at module-evaluation time,
// which silently breaks every action in the file, not just the bad
// export). The initial useActionState value lives in the client
// component that actually calls useActionState instead.

export async function suspendUser(_prevState: UserActionState, formData: FormData): Promise<UserActionState> {
  const userId = String(formData.get("userId") ?? "");
  const reason = String(formData.get("reason") ?? "").trim();
  if (!reason) {
    return { error: "A reason is required to suspend an account." };
  }
  try {
    await apiFetch(`/v1/admin/users/${userId}/suspend`, {
      method: "POST",
      body: JSON.stringify({ reason }),
    });
  } catch (err) {
    return { error: err instanceof ApiError ? err.message : "Something went wrong. Please try again." };
  }
  revalidatePath("/users");
  return { error: null };
}

export async function reinstateUser(_prevState: UserActionState, formData: FormData): Promise<UserActionState> {
  const userId = String(formData.get("userId") ?? "");
  try {
    await apiFetch(`/v1/admin/users/${userId}/reinstate`, { method: "POST" });
  } catch (err) {
    return { error: err instanceof ApiError ? err.message : "Something went wrong. Please try again." };
  }
  revalidatePath("/users");
  return { error: null };
}
