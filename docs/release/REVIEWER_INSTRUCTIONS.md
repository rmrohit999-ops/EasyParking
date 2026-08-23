# Play Console reviewer instructions — ParkEase

**Purpose:** give the Play review team (and internal QA before submission) a real, working way
to reach every role-gated flow in the app — driver, parking owner, attendant, and admin. This
document is written for the "App access" section of Play Console's App content questionnaire,
where you paste instructions for how a reviewer can log in and test restricted functionality.

**Honesty note up front:** ParkEase deliberately has **no fake/demo data and no simulated
success paths** (see the seed script's own doc comment, `prisma/seed/seed.ts`) — every account
below must be created for real, through the app's real registration flow, against a real
deployed backend with real (test-mode, where applicable) provider credentials configured. There
is no "reviewer mode" that bypasses payments, OTP, or role checks. The steps below are exactly
what a real user (or your QA team) does.

## 0. Prerequisites the environment being reviewed must have configured

Before a reviewer can exercise every flow, the backend environment must have (in **test mode**
where the provider supports it — never point a review build at production/live payment keys):

- `PAYMENT_PROVIDER` / `PAYMENT_KEY_ID` / `PAYMENT_KEY_SECRET` / `PAYMENT_WEBHOOK_SECRET` — set
  to a Razorpay **test-mode** account so bookings can actually be paid for. Razorpay's test mode
  provides card/UPI numbers that always succeed, specifically for this kind of review flow —
  see Razorpay's own test-card documentation. Without this, `POST /v1/payments/orders` reports
  "unavailable" rather than faking success (Milestone 7's explicit design), and a reviewer can
  create a booking hold but never pay for it.
- `PAYOUT_PROVIDER` / `RAZORPAYX_*` — set to RazorpayX test mode if the reviewer needs to see an
  owner settlement actually pay out; otherwise settlements will report "unavailable" the same
  way.
- `PAYOUT_ENCRYPTION_KEY` — must be set (`openssl rand -base64 32`) or owner payout-account
  creation reports unavailable.
- `SMS_PROVIDER` — **optional.** If unset, phone/OTP login and registration report "unavailable"
  by design (no fake OTP is ever accepted) — use email+password registration/login instead (see
  below), which works with no external provider at all.
- The Android build must point `API_BASE_URL` (via the `prod`/`staging`/`dev` product flavor) at
  this reviewer backend, and, if you want push notifications testable, a real
  `app/google-services.json` must be present (optional — the app builds and runs fully without
  it; push notifications simply stay inert, per `app/build.gradle.kts`'s own doc comment).

## 1. Driver account (self-serve, no special setup)

Every account starts as a driver — this is the default and needs no admin action.

1. Open the app → **Register**.
2. Provide full name, email, and a password (8+ characters). (Phone+OTP registration is also
   available if `SMS_PROVIDER` is configured per §0; email+password is the simpler path for
   review.)
3. You're now signed in as a driver: add a vehicle (Vehicles tab), search for parking, create a
   booking, and pay for it (requires §0's payment config; use Razorpay's published test card/UPI
   details at checkout — never real payment details).
4. QR pass for the booking appears under the booking detail screen once payment succeeds — this
   is what an attendant scans at check-in (§3).

## 2. Parking owner account

Owner access is self-serve from any existing (driver) account — no admin action needed.

1. Sign in as a driver account created per §1.
2. Call `POST /v1/users/me/roles/owner` with an (optional) `{"businessName": "..."}` body — the
   app's owner-onboarding screen (Owner tab → "List your parking") does this for you; via the
   API directly (e.g. through Swagger at `/docs`, if `ENABLE_API_DOCS=true` on the reviewer
   backend, or via `curl -X POST .../v1/users/me/roles/owner -H "Authorization: Bearer <token>"`)
   works identically.
3. You're now an owner on the same account (roles are additive, not exclusive) — create a
   parking listing and at least one section (choose vehicle category/type so it's bookable by
   the driver flow in §1), and set pricing.
4. Owner earnings/settlement screens (Earnings tab) become populated once a driver books and
   pays for a section you own, and — if §0's payout config is set — once a settlement run
   actually pays out.

## 3. Attendant account

An attendant must be **invited by an existing parking owner** to a specific listing — this is
intentional (attendants get scoped access to one owner's location, not a global role), so it
needs two accounts: an owner (§2) and a separate driver account (§1) that will become the
attendant.

1. As the owner (§2), call `POST /v1/parking/:listingId/attendants` with body
   `{"attendantEmail": "<the other account's email>", "authorizedCategories": ["TWO_WHEELER"]}`
   (or `["FOUR_WHEELER"]`/both, matching what that listing accepts). The app's listing management
   screen (Owner tab → listing → "Attendants") does this for you if you'd rather not call the API
   directly.
2. Sign in on the invited account — it now has attendant access scoped to that listing. Use the
   Attendant tab's QR scanner to check in/out a booking created in §1 against that listing.

## 4. Admin account

**There is no self-serve or in-app path to become an admin, by design** — admin is the platform
operator role, and granting it must never be reachable from inside the product surface reviewers
or end users touch. For the reviewer backend, provision exactly one admin account directly
against the database before submitting for review:

1. Register a normal account by email+password per §1 (or via the API) and note its user id
   (`GET /v1/users/me` returns it, or read it from the `users` table by email).
2. Grant the ADMIN role directly, e.g. via `psql` against the reviewer database:
   ```sql
   INSERT INTO user_roles (id, user_id, role, status, granted_at)
   VALUES (gen_random_uuid(), '<the user id from step 1>', 'ADMIN', 'ACTIVE', now());
   ```
   (or the equivalent one-off Prisma script: `prisma.userRoleAssignment.create({ data: { user_id, role: 'ADMIN' } })`).
3. Sign in on that account — it now has access to the Admin tab (moderation, disputes, support,
   reports/analytics, fraud review, config flags) in addition to whatever driver/owner/attendant
   access it already had.

## 5. What a reviewer can and cannot fully exercise

- **Fully working, no caveats:** registration/login (email+password), vehicle management,
  listing creation, booking + QR check-in/out, role assignment (owner/attendant), in-app
  notification inbox, support tickets, reviews.
- **Requires real (test-mode) provider credentials per §0 to fully exercise:** payments, owner
  payouts/settlements, OTP-based phone login, push notifications.
- **Known gap, disclosed:** there is no in-app "Delete my account" button yet, even though the
  backend endpoint (`DELETE /v1/auth/account`) works — see DATA_SAFETY.md. If Play review
  specifically checks for an in-app account-deletion path, this will need to ship before
  submission; don't claim it exists in the Play Console questionnaire until the UI is built.

## 6. Example reviewer login credentials (fill in before submitting)

Provide Play Console with one already-created account per role so the review team doesn't have
to perform §1-§4 themselves:

| Role | Email | Password | Notes |
|---|---|---|---|
| Driver | [driver-reviewer@parkease.app] | [PASSWORD] | |
| Owner | [owner-reviewer@parkease.app] | [PASSWORD] | Has at least one listing with a bookable section |
| Attendant | [attendant-reviewer@parkease.app] | [PASSWORD] | Scoped to the owner-reviewer's listing above |
| Admin | [admin-reviewer@parkease.app] | [PASSWORD] | Granted per §4 |

---
*Maintainer note: written from the actual registration/role-assignment code paths (auth.service.ts,
users.controller.ts §becomeOwner, parking.controller.ts §assignAttendant) rather than an assumed
"there's probably a demo mode" — there isn't one, intentionally. Before submitting to Play,
actually walk through §1-§4 end-to-end against the real reviewer backend and fill in §6's table
with accounts that already exist, so the review team never has to.*
