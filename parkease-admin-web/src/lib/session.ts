import "server-only";
import { cookies } from "next/headers";
import { redirect } from "next/navigation";

const ACCESS_TOKEN_COOKIE = "pe_access_token";
// A little under the backend's actual JWT_ACCESS_TTL_SECONDS (900s) so the
// cookie never outlives the token it holds and implies a session that's
// already dead.
const ACCESS_TOKEN_MAX_AGE_SECONDS = 840;

/**
 * v1 deliberately does not store the refresh token or attempt silent
 * refresh — only the short-lived access token is kept, as an httpOnly
 * cookie never readable from client JS. When it expires (~14 minutes),
 * apiFetch's 401 handling clears the cookie and sends the admin back to
 * /login. This is a real, if modest, UX cost for an internal tool used in
 * short bursts; wiring middleware-based silent refresh is the natural v2.
 */
export async function setAccessTokenCookie(token: string): Promise<void> {
  const store = await cookies();
  store.set(ACCESS_TOKEN_COOKIE, token, {
    httpOnly: true,
    secure: process.env.NODE_ENV === "production",
    sameSite: "lax",
    path: "/",
    maxAge: ACCESS_TOKEN_MAX_AGE_SECONDS,
  });
}

export async function clearAccessTokenCookie(): Promise<void> {
  const store = await cookies();
  store.delete(ACCESS_TOKEN_COOKIE);
}

export async function getAccessToken(): Promise<string | null> {
  const store = await cookies();
  return store.get(ACCESS_TOKEN_COOKIE)?.value ?? null;
}

/** For a Server Component that must not render at all without a session — redirects rather than returning null. */
export async function requireAccessToken(): Promise<string> {
  const token = await getAccessToken();
  if (!token) {
    redirect("/login");
  }
  return token;
}
