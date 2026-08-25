"use client";

import { useActionState, useState } from "react";
import { reinstateUser, suspendUser, type UserActionState } from "./actions";
import type { AdminUserSummaryResponse } from "@/lib/types";

const initialActionState: UserActionState = { error: null };

export function UserRow({ user }: { user: AdminUserSummaryResponse }) {
  const [suspendState, suspendAction, suspendPending] = useActionState(suspendUser, initialActionState);
  const [reinstateState, reinstateAction, reinstatePending] = useActionState(reinstateUser, initialActionState);
  const [showSuspendForm, setShowSuspendForm] = useState(false);
  // Not pre-disabled for admin-tier targets here — this client doesn't
  // know whether the signed-in admin is a plain ADMIN or a SUPER_ADMIN,
  // and guessing wrong would wrongly block a legitimate super-admin
  // action. The backend's real check (only SUPER_ADMIN may act on an
  // admin-tier account) is authoritative; its 403 message surfaces below
  // exactly as returned, same as everywhere else in this app.

  return (
    <tr className="border-b border-slate-100 last:border-0">
      <td className="px-4 py-3 text-sm text-slate-900">{user.email ?? user.phone ?? "—"}</td>
      <td className="px-4 py-3 text-sm text-slate-600">{user.roles.join(", ") || "—"}</td>
      <td className="px-4 py-3">
        <span
          className={`inline-flex rounded-full px-2 py-0.5 text-xs font-medium ${
            user.status === "ACTIVE" ? "bg-emerald-50 text-emerald-700" : "bg-red-50 text-red-700"
          }`}
        >
          {user.status}
        </span>
      </td>
      <td className="px-4 py-3 text-right text-sm">
        {user.status === "SUSPENDED" ? (
          <form action={reinstateAction} className="inline">
            <input type="hidden" name="userId" value={user.id} />
            <button type="submit" disabled={reinstatePending} className="font-medium text-emerald-700 hover:underline disabled:opacity-50">
              {reinstatePending ? "Reinstating…" : "Reinstate"}
            </button>
          </form>
        ) : showSuspendForm ? (
          <form action={suspendAction} className="flex items-center justify-end gap-2">
            <input type="hidden" name="userId" value={user.id} />
            <input
              name="reason"
              placeholder="Reason"
              required
              className="w-40 rounded border border-slate-300 px-2 py-1 text-xs"
            />
            <button type="submit" disabled={suspendPending} className="font-medium text-red-700 hover:underline disabled:opacity-50">
              {suspendPending ? "…" : "Confirm"}
            </button>
            <button type="button" onClick={() => setShowSuspendForm(false)} className="text-slate-400 hover:text-slate-600">
              Cancel
            </button>
          </form>
        ) : (
          <button
            type="button"
            onClick={() => setShowSuspendForm(true)}
            className="font-medium text-red-700 hover:underline"
          >
            Suspend
          </button>
        )}
      </td>
      {(suspendState.error || reinstateState.error) && (
        <td colSpan={4} className="px-4 pb-2 text-xs text-red-600">
          {suspendState.error ?? reinstateState.error}
        </td>
      )}
    </tr>
  );
}
