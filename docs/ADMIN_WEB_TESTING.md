# Admin Web — Playwright End-to-End Testing

`parkease-admin-web/tests/` is a real, end-to-end Playwright suite that drives
a Chromium browser against a real deployed admin-web instance and its real
backend API — not a local mock, not a stubbed response. This is deliberate:
it's the same methodology that caught two real, shipped bugs earlier in this
project (a Next.js `"use server"` file that silently crashed because it
exported a non-function value, and a cookie-mutation call that failed in a
specific server-render context) — both invisible to a build-only check.

## One-time setup: the two QA accounts

The suite needs two real accounts that exist only for testing, never real
users. Both use the reserved `@parkease.test` email domain (an IANA-reserved
TLD, RFC 2606) — the same convention `prisma/seed/seed-demo-listings.ts` uses
for demo data, so these accounts are always trivially identifiable and never
confused with real production data.

```bash
cd parkease-backend
DATABASE_URL="<your DATABASE_URL>" node -e "
const { PrismaClient } = require('@prisma/client');
const argon2 = require('argon2');
const p = new PrismaClient();
(async () => {
  const password = 'choose-a-real-password-here';
  const hash = await argon2.hash(password, { type: argon2.argon2id });
  const user = await p.user.create({
    data: { email: 'qa_admin@parkease.test', password_hash: hash, status: 'ACTIVE', email_verified_at: new Date() },
  });
  await p.userRoleAssignment.create({ data: { user_id: user.id, role: 'ADMIN' } });
  console.log('Created qa_admin — password:', password);
  await p.\$disconnect();
})();
"
```

Repeat without the `userRoleAssignment` step for `qa_target_user@parkease.test`
— an ordinary account with no admin role, used as the suspend/reinstate
test's target (see below for why a second account is needed).

**Never hardcode either password in source** — this repository is public.
Export them as environment variables instead:

```bash
export ADMIN_EMAIL=qa_admin@parkease.test
export ADMIN_PASSWORD=<the real password you chose>
export TARGET_USER_EMAIL=qa_target_user@parkease.test   # optional, has a safe default
```

## Running the suite

```bash
cd parkease-admin-web
npm install
npx playwright install chromium   # one-time browser download
npx playwright test
```

Runs serially (`workers: 1`) against `https://admin-web-production-e697.up.railway.app`
by default — override with `ADMIN_WEB_BASE_URL` to point at a different
deployment. Serial execution is intentional, not a performance oversight: the
backend has a real Redis-backed progressive login-throttle guard on
`/auth/login`, and several parallel workers logging into the same QA account
within a few seconds will trip it — a real, correct security feature, not a
bug, but not a realistic thing for a test suite to do to itself.

## Why suspend/reinstate targets a separate account, not the admin itself

The backend correctly refuses to let a plain `ADMIN` suspend an admin-tier
account, including itself — confirmed directly while writing this suite (the
UI surfaced the real backend message: *"Only a super admin can suspend or
reinstate an admin account."*). That's real, intentional authorization, not
something to route around, so the test targets `qa_target_user@parkease.test`
instead — which is also the more realistic real-world scenario (an admin
moderating a regular user).

## What this does and doesn't cover

Covers: authentication and session-guarding, the platform-overview dashboard,
user search and a real suspend/reinstate mutation (verified persisted, not
just optimistically rendered), pending-listing review, the cash-payments
report, and the Maps API quota monitor.

Doesn't cover: the native Android apps (`app-driver`/`app-partner`) —
Playwright drives web browsers, not native apps. Their automated coverage is
the JUnit/Truth unit test suite run on every Gradle build; full on-device UI
automation (e.g. Espresso) is a separate, larger effort not set up here.

See `~/Desktop/ParkEase_AdminWeb_Playwright_Test_Report.pdf` (or regenerate
your own) for a full results writeup from the most recent run.
