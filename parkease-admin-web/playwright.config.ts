import { defineConfig, devices } from "@playwright/test";

/**
 * Runs against the real, deployed production admin-web + backend — not a
 * local/mocked server. Same methodology already used earlier in this
 * project: a build-only check can't catch a mutation that silently fails
 * to persist, or a server-render crash from a bad "use server" export; a
 * real browser driving the real deployed app against the real backend can.
 */
export default defineConfig({
  testDir: "./tests",
  fullyParallel: false,
  // Forced serial: every test logs in as the same single QA admin account,
  // and the backend has a real Redis-backed progressive login throttle
  // guard on /auth/login (by design, to slow brute-force attempts). Running
  // workers in parallel means several tests' logins — including one
  // deliberately-wrong-password attempt from the invalid-credentials test
  // — land on that guard within the same short window, which then also
  // rejects the *correct* concurrent logins. That's the throttle guard
  // doing its job, not a product bug — but it makes concurrent workers an
  // unrealistic test setup for a suite that all shares one account.
  workers: 1,
  retries: 0,
  reporter: [["list"], ["json", { outputFile: "test-results/results.json" }]],
  use: {
    baseURL: process.env.ADMIN_WEB_BASE_URL ?? "https://admin-web-production-e697.up.railway.app",
    screenshot: "only-on-failure",
    trace: "retain-on-failure",
  },
  projects: [
    { name: "chromium", use: { ...devices["Desktop Chrome"] } },
  ],
});
