"use server";

import { redirect } from "next/navigation";
import { clearAccessTokenCookie } from "@/lib/session";

export async function logout(): Promise<void> {
  await clearAccessTokenCookie();
  redirect("/login");
}
