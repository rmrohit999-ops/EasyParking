import "server-only";
import { redirect } from "next/navigation";
import { API_BASE_URL } from "./config";
import { clearAccessTokenCookie, getAccessToken } from "./session";

export class ApiError extends Error {
  constructor(
    public status: number,
    message: string,
  ) {
    super(message);
  }
}

/**
 * The one place every admin screen's data fetch and mutation goes
 * through — same "authenticated, server-authoritative" contract the
 * mobile apps' JwtAuthGuard already enforces; this is just a browser
 * client of the same REST API, not a separate backend surface. A 401
 * means the short-lived access token expired (see session.ts's doc
 * comment on why there's no silent refresh in v1) — clears the dead
 * cookie and sends the admin back to /login rather than surfacing a raw
 * error.
 */
export async function apiFetch<T>(path: string, init?: RequestInit): Promise<T> {
  const token = await getAccessToken();
  if (!token) {
    redirect("/login");
  }

  const response = await fetch(`${API_BASE_URL}${path}`, {
    ...init,
    cache: "no-store",
    headers: {
      ...init?.headers,
      Authorization: `Bearer ${token}`,
      "Content-Type": "application/json",
    },
  });

  if (response.status === 401) {
    // Cookie mutation is only allowed from a Server Action/Route Handler,
    // not from a plain Server Component render — and this can legitimately
    // fire from a component render (a normal page load with a stale
    // token) as well as from the RSC re-render Next.js runs after a
    // Server Action to reflect its result (e.g. an admin action that,
    // incidentally, invalidates their own session — this can't be ruled
    // out here). Best-effort: try to clear the dead cookie, but a context
    // that forbids it must never crash the response — redirecting to
    // /login still happens either way, and login's own flow overwrites
    // whatever cookie is left.
    await clearAccessTokenCookie().catch(() => undefined);
    redirect("/login");
  }

  if (!response.ok) {
    const body = await response.json().catch(() => null);
    const message = body?.error?.message ?? `Request failed (${response.status}).`;
    throw new ApiError(response.status, message);
  }

  if (response.status === 204) {
    return undefined as T;
  }

  return response.json() as Promise<T>;
}
