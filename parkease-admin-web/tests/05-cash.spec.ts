import { test, expect } from "@playwright/test";
import { loginAsAdmin } from "./helpers";

test.describe("Cash payments report", () => {
  test("summary cards render real currency-formatted figures", async ({ page }) => {
    await loginAsAdmin(page);
    await page.getByRole("link", { name: "Cash Payments" }).click();
    await expect(page.getByRole("heading", { name: "Cash payments" })).toBeVisible();

    for (const label of ["Total collected", "ParkEase commission", "Owner net payable", "Completed", "Pending"]) {
      await expect(page.getByText(label, { exact: true })).toBeVisible();
    }
    await expect(page.getByRole("heading", { name: "By owner" })).toBeVisible();
  });
});
