import { test, expect } from "@playwright/test";
import { loginAsAdmin, ADMIN_EMAIL, TARGET_USER_EMAIL } from "./helpers";

test.describe("Users", () => {
  test("search finds the real QA account by email", async ({ page }) => {
    await loginAsAdmin(page);
    await page.getByRole("link", { name: "Users" }).click();
    await expect(page.getByRole("heading", { name: "Users" })).toBeVisible();

    await page.getByPlaceholder("Search by email or phone").fill(ADMIN_EMAIL);
    await page.getByPlaceholder("Search by email or phone").press("Enter");
    await expect(page.getByRole("cell", { name: ADMIN_EMAIL })).toBeVisible({ timeout: 10000 });
  });

  /**
   * A real mutation test, against the real backend — the same methodology
   * that caught two real bugs earlier in this project (a "use server" file
   * that silently crashed, and a cookie-mutation call that failed in a
   * specific render context). Runs against a disposable, ordinary target
   * account (not the admin's own account — see helpers.ts for why: a plain
   * ADMIN is correctly forbidden from suspending an admin-tier account,
   * including itself), and always ends by reinstating it.
   */
  test("suspend then reinstate a real target user", async ({ page }) => {
    await loginAsAdmin(page);
    await page.getByRole("link", { name: "Users" }).click();
    await page.getByPlaceholder("Search by email or phone").fill(TARGET_USER_EMAIL);
    await page.getByPlaceholder("Search by email or phone").press("Enter");

    const row = page.locator("tr", { hasText: TARGET_USER_EMAIL });
    await expect(row).toBeVisible({ timeout: 10000 });

    await row.getByRole("button", { name: "Suspend" }).click();
    await row.getByPlaceholder("Reason").fill("Playwright QA regression test — auto-reinstated at end of test.");
    await row.getByRole("button", { name: "Confirm" }).click();

    // The real assertion: the mutation actually persisted server-side (a
    // fresh row from the next page render, not just optimistic client state).
    await expect(row.getByText("SUSPENDED", { exact: true })).toBeVisible({ timeout: 10000 });

    await row.getByRole("button", { name: "Reinstate" }).click();
    await expect(row.getByText("ACTIVE", { exact: true })).toBeVisible({ timeout: 10000 });
  });
});
