import { test, expect } from "@playwright/test";
import { loginAsAdmin } from "./helpers";

test.describe("Maps API quota", () => {
  test("shows a real live status badge (ACTIVE or capped) from the backend", async ({ page }) => {
    await loginAsAdmin(page);
    await page.getByRole("link", { name: "Maps API Quota" }).click();
    await expect(page.getByRole("heading", { name: "Maps API quota" })).toBeVisible();

    const active = page.getByText("ACTIVE", { exact: true });
    const capped = page.getByText("80% CAP REACHED — INTENT FALLBACK ACTIVE");
    await expect(active.or(capped)).toBeVisible({ timeout: 10000 });
    await expect(page.getByText(/Daily usage —/)).toBeVisible();
  });
});
