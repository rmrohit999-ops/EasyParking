"use server";

import { redirect } from "next/navigation";
import { API_BASE_URL } from "@/lib/config";
import { setAccessTokenCookie } from "@/lib/session";
import type { LoginResponse, UserProfileResponse } from "@/lib/types";

export interface LoginState {
  error: string | null;
}

/**
 * Reuses the exact same /v1/auth/login and /v1/users/me endpoints every
 * mobile app calls — no separate admin auth surface. The admin-tier check
 * here is a login-time UX nicety (a clear "this account doesn't have
 * admin access" message instead of a confusing empty dashboard) — it is
 * NOT the real authorization boundary. Every page and mutation past this
 * point calls the backend again, which re-checks roles from the database
 * on every single request (JwtAuthGuard/RolesGuard) regardless of what
 * this action decided at login time.
 */
export async function login(_prevState: LoginState, formData: FormData): Promise<LoginState> {
  const email = String(formData.get("email") ?? "").trim();
  const password = String(formData.get("password") ?? "");
  if (!email || !password) {
    return { error: "Enter your email and password." };
  }

  const loginResponse = await fetch(`${API_BASE_URL}/v1/auth/login`, {
    method: "POST",
    headers: { "Content-Type": "application/json" },
    body: JSON.stringify({ email, password }),
    cache: "no-store",
  });

  if (!loginResponse.ok) {
    return { error: "Incorrect email or password." };
  }

  const { accessToken } = (await loginResponse.json()) as LoginResponse;

  const meResponse = await fetch(`${API_BASE_URL}/v1/users/me`, {
    headers: { Authorization: `Bearer ${accessToken}` },
    cache: "no-store",
  });
  if (!meResponse.ok) {
    return { error: "Something went wrong. Please try again." };
  }
  const me = (await meResponse.json()) as UserProfileResponse;
  if (!me.roles.includes("ADMIN") && !me.roles.includes("SUPER_ADMIN")) {
    return { error: "This account doesn't have admin access." };
  }

  await setAccessTokenCookie(accessToken);
  redirect("/");
}
