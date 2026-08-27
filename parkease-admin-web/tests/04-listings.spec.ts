import { test, expect } from "@playwright/test";
import { loginAsAdmin } from "./helpers";

test.describe("Pending listings", () => {
  test("renders real backend data — either real pending listings or a genuine empty state", async ({ page }) => {
    await loginAsAdmin(page);
    await page.getByRole("link", { name: "Pending Listings" }).click();
    await expect(page.getByRole("heading", { name: "Pending listings" })).toBeVisible();

    // Whichever branch fires must be a real backend response, not a stuck
    // loading state or a client-side crash — assert one of the two real
    // outcomes is actually rendered within a reasonable time. ListingCard
    // renders a listing's name as a <p>, not a heading, so the real signal
    // for "at least one real card rendered" is its Approve/Reject actions.
    const emptyState = page.getByText("Nothing pending review right now.");
    const anyCard = page.getByRole("button", { name: "Approve" }).first();
    await expect(emptyState.or(anyCard)).toBeVisible({ timeout: 10000 });
  });
});
