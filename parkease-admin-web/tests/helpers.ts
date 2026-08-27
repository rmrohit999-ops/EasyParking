import { Page, expect } from "@playwright/test";

/**
 * A real ADMIN-role account created specifically for this test suite,
 * marked with the same reserved .test email domain used elsewhere in this
 * project for demo/test data (see prisma/seed/seed-demo-listings.ts) —
 * never a mocked login, always a real credential against the real backend.
 *
 * The password is deliberately NOT hardcoded here (this repo is public) —
 * set ADMIN_EMAIL/ADMIN_PASSWORD as real environment variables before
 * running the suite. See docs/ADMIN_WEB_TESTING.md for how to (re)create
 * this account.
 */
export const ADMIN_EMAIL = process.env.ADMIN_EMAIL ?? "qa_admin@parkease.test";
export const ADMIN_PASSWORD = (() => {
  const value = process.env.ADMIN_PASSWORD;
  if (!value) {
    throw new Error(
      "ADMIN_PASSWORD environment variable is required to run this suite — see tests/helpers.ts and docs/ADMIN_WEB_TESTING.md.",
    );
  }
  return value;
})();

/**
 * A separate, ordinary (no admin role) account — suspend/reinstate is
 * tested against this, not the admin account itself. The backend
 * correctly refuses to let a plain ADMIN suspend an admin-tier account
 * (including itself) — that's real, intentional authorization, confirmed
 * while writing this suite (see the test report for detail), not
 * something to route around.
 */
export const TARGET_USER_EMAIL = process.env.TARGET_USER_EMAIL ?? "qa_target_user@parkease.test";

export async function loginAsAdmin(page: Page) {
  await page.goto("/login");
  await page.getByLabel("Email").fill(ADMIN_EMAIL);
  await page.getByLabel("Password").fill(ADMIN_PASSWORD);
  await page.getByRole("button", { name: /sign in/i }).click();
  await expect(page.getByRole("heading", { name: "Platform overview" })).toBeVisible({ timeout: 15000 });
}
