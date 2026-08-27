import { test, expect } from "@playwright/test";
import { loginAsAdmin, ADMIN_EMAIL, ADMIN_PASSWORD } from "./helpers";

test.describe("Authentication", () => {
  test("unauthenticated visit to a dashboard route redirects to /login", async ({ page }) => {
    await page.goto("/users");
    await expect(page).toHaveURL(/\/login/);
    await expect(page.getByRole("heading", { name: "ParkEase Admin" })).toBeVisible();
  });

  test("invalid credentials show a real error, not a silent failure", async ({ page }) => {
    await page.goto("/login");
    await page.getByLabel("Email").fill(ADMIN_EMAIL);
    await page.getByLabel("Password").fill("definitely-the-wrong-password");
    await page.getByRole("button", { name: /sign in/i }).click();
    await expect(page.getByRole("alert")).toBeVisible({ timeout: 10000 });
    await expect(page).toHaveURL(/\/login/);
  });

  test("valid credentials sign in and land on the dashboard", async ({ page }) => {
    await loginAsAdmin(page);
    await expect(page).toHaveURL("/");
  });

  test("signing out returns to /login and re-guards dashboard routes", async ({ page }) => {
    await loginAsAdmin(page);
    await page.getByRole("button", { name: /sign out/i }).click();
    await expect(page).toHaveURL(/\/login/, { timeout: 10000 });

    // The session is really gone server-side, not just hidden client-side —
    // going back to a dashboard route must redirect again, not show stale content.
    await page.goto("/users");
    await expect(page).toHaveURL(/\/login/);
  });
});
