import { test, expect } from "@playwright/test";
import { loginAsAdmin } from "./helpers";

test.describe("Dashboard overview", () => {
  test("shows real, numeric platform stats — not placeholders", async ({ page }) => {
    await loginAsAdmin(page);

    const labels = ["Total users", "Suspended", "Pending listings", "Fraud alerts", "Support tickets", "Open disputes"];
    for (const label of labels) {
      await expect(page.getByText(label, { exact: true })).toBeVisible();
    }

    // Each stat card's number must be a real integer the backend returned,
    // not an empty/placeholder string — this is what would have caught a
    // broken apiFetch call or a field-name mismatch after a backend change.
    const cards = page.locator(".rounded-lg.border.border-slate-200.bg-white.p-4");
    const count = await cards.count();
    expect(count).toBeGreaterThanOrEqual(6);
    for (let i = 0; i < count; i++) {
      const text = (await cards.nth(i).locator("p").first().innerText()).trim();
      expect(text).toMatch(/^\d+$/);
    }
  });

  test("sidebar links to every admin section", async ({ page }) => {
    await loginAsAdmin(page);
    for (const label of ["Dashboard", "Users", "Pending Listings", "Cash Payments", "Maps API Quota"]) {
      await expect(page.getByRole("link", { name: label })).toBeVisible();
    }
  });
});
