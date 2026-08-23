# ParkEase go-live checklist

Everything that stands between "all 14 milestones built" and "actually live for real users."
Grouped by category, in roughly the order you'd tackle them. Every item here is either something
this build environment genuinely could not do (no network egress — see below) or a gap this
project's own honesty discipline surfaced and deliberately did not paper over.

## 0. Why this checklist exists — the one thing to understand first

This entire codebase was written in a sandboxed environment with **zero network egress**: no
`npm install`, no `prisma generate`, no `docker`, no real `./gradlew build`, nothing that touches
a package registry or a real device/emulator. Every file was hand-validated with brace-balance
checking and, for the backend, a filtered syntax-only `tsc --noEmit` pass — not a real compile,
and never a real test run. That means **the single highest-priority go-live item, above every
other item on this list, is: run the real toolchains and fix what they find.** Everything below
assumes that's step one.

## 1. Make it actually build and pass its own tests

- [ ] Backend: `npm install && npx prisma generate && npm run build` in `parkease-backend/`.
      Expect Prisma-client type errors to be the most likely failure category — several patterns
      in this codebase (literal-enum tuples instead of `Object.values(EnumBinding)`,
      `toXView()` bigint-to-string serialization) exist specifically to work around type errors
      seen against an *ungenerated* client; once the real generated client exists, some of these
      workarounds may turn out to be unnecessary (harmless either way) or may reveal a real
      mismatch (needs a fix).
- [ ] Backend: `npm test` — run every `*.spec.ts` for real (8 spec files exist as of Milestone 12:
      vehicles, booking, payments, token/auth, roles guard, and others — see
      `find parkease-backend/src -name "*.spec.ts"`). The hand-rolled fake-Prisma-delegate tests
      were written carefully against the real Prisma API shape, but they've never actually
      executed.
- [ ] Backend: `npm run lint` and fix anything ESLint catches that `tsc --noEmit` wouldn't (unused
      vars in some configs, import ordering, etc.).
- [ ] Android: `./gradlew build` (or at minimum `./gradlew :app:assembleDevDebug lint
      testDevDebugUnitTest`) in `parkease-android/`. Expect the first real build to surface
      Gradle DSL issues that structural review can't — implicit receiver resolution inside
      nested lambdas (`signingConfigs`, `productFlavors`) is exactly the kind of thing that reads
      correctly but can still fail to compile.
- [ ] Android: actually install and run the app on a real device or emulator, for all four roles
      (see `docs/release/REVIEWER_INSTRUCTIONS.md` for how to reach each one) — this is the first
      point at which real Compose UI bugs, navigation bugs, and Hilt DI graph errors would surface.
      Pay particular attention to `RootNavHost`'s reactive login-state navigation (Milestone 11
      fix) and the conditional `google-services` plugin path (Milestone 12 fix) — both were fixed
      based on code review, not by observing an actual broken UI.

## 2. Third-party service configuration

Every one of these currently makes its feature "report unavailable" rather than fake success —
that's intentional (see each `.env.example` comment), but it means the feature has never been
exercised end-to-end. Configure test-mode credentials, exercise the flow, then swap to
production credentials before public launch.

- [ ] **Razorpay** (`PAYMENT_PROVIDER`/`PAYMENT_KEY_ID`/`PAYMENT_KEY_SECRET`/
      `PAYMENT_WEBHOOK_SECRET`) — test mode first; verify a real webhook round-trip (order create
      → pay with a Razorpay test card → webhook received → booking confirmed), not just the
      order-creation call.
- [ ] **RazorpayX** (`PAYOUT_PROVIDER`/`RAZORPAYX_*`) — verify a real settlement run actually
      dispatches a payout in test mode.
- [ ] **`PAYOUT_ENCRYPTION_KEY`** — generate with `openssl rand -base64 32` and store in your
      actual secret manager; rotate-and-migrate this key is a real operational procedure to have
      ready before owners start adding payout accounts, since existing encrypted values would
      need re-encryption under a new key.
- [ ] **SMS/OTP provider** (`SMS_PROVIDER`, etc.) — needed for phone+OTP login/registration;
      until set, only email+password auth works.
- [ ] **Email provider** (`EMAIL_PROVIDER`, etc.) — needed for password-reset emails and any
      email-based notifications.
- [ ] **Firebase** — create a real Firebase project, download `app/google-services.json` (per
      flavor: `com.parkease.app.dev`/`.staging`/`com.parkease.app`), and set
      `FCM_PROJECT_ID`/`FCM_SERVICE_ACCOUNT_JSON` on the backend. Until then push notifications
      are inert (in-app notification inbox still works fully without this).
- [ ] **Google OAuth** (`GOOGLE_OAUTH_CLIENT_ID`/`GOOGLE_OAUTH_CLIENT_SECRET`) — needed for
      "Sign in with Google."
- [ ] **Object storage** (`STORAGE_*`) — MinIO values in `.env.example` are dev-only; point at
      real S3-compatible storage with real credentials for staging/prod.
- [ ] **Maps** (`MAPS_API_KEY_SERVER`) — needed for any server-side geocoding/maps calls.
- [ ] **Sentry** (`SENTRY_DSN_BACKEND`) — currently unset; decide whether to wire it up before
      launch so production errors are actually visible somewhere.
- [ ] Firebase **Crashlytics/Analytics** — dependencies are present but deliberately not
      initialized (see `ParkEaseApplication`'s doc comment). Decide if/when to turn them on; if
      you do, update `docs/release/DATA_SAFETY.md` and `docs/release/PRIVACY_POLICY.md` in the
      *same change* — shipping active crash/analytics collection without updating those two
      documents is a Play Store policy violation, not just a documentation nicety.

## 3. Security

- [ ] Rotate every secret in `.env.example` to real, unique, secret-manager-issued values before
      any real deployment — `JWT_ACCESS_SECRET`, `JWT_REFRESH_SECRET`, `QR_SIGNING_SECRET`, and
      `PAYOUT_ENCRYPTION_KEY` all currently default to placeholder dev values.
- [ ] Set `CORS_ALLOWED_ORIGINS` explicitly for staging/production (Milestone 12 closed the
      insecure fallback, but an *unset* value in a non-development env now means CORS is fully
      closed — confirm that's actually what you want for any browser-based admin panel you may
      build later, and set the real allowed origins).
- [ ] Confirm `ENABLE_API_DOCS` is unset or `false` in production (defaults to off in production
      already, per Milestone 12 — verify the deployed env var doesn't override that).
- [ ] Run a real `npm audit --audit-level=high` (already wired into CI per
      `backend-ci.yml`) and a real Android dependency-submission scan (wired into
      `android-ci.yml` per Milestone 12) against actual resolved dependency trees — CI has never
      actually executed in this sandbox, so these have never actually run.
- [ ] Consider a professional security review / pen test before handling real payment flows at
      scale, especially around the booking-hold race-safety guarantees and the webhook signature
      verification path — both are covered by unit tests here, but neither has been load-tested
      or adversarially tested against a real deployment.
- [ ] Replace the manual-SQL admin-provisioning process (`docs/release/REVIEWER_INSTRUCTIONS.md`
      §4) with a proper, audited admin-bootstrap procedure for production use — direct database
      writes are fine for a one-off reviewer account, not as an ongoing operational practice.

## 4. Legal and compliance

- [ ] Fill in every `[BRACKETED]` placeholder in `docs/release/PRIVACY_POLICY.md` and
      `docs/release/TERMS_OF_SERVICE.md` (legal entity name, registered address, contact emails,
      retention periods, governing law/jurisdiction, grievance officer details).
- [ ] Have both documents reviewed by counsel qualified in your actual jurisdiction(s) — they
      were written from an accurate review of what the app actually does, but "accurate" and
      "legally sufficient" are different bars.
- [ ] Host both at stable HTTPS URLs; put those URLs into `PRIVACY_POLICY_URL`/`TERMS_URL` in the
      backend env config, into Play Console's listing fields, and eventually into an in-app
      Settings/Legal screen (none exists yet — see §6).
- [ ] Complete the real Play Console **Data Safety** form using `docs/release/DATA_SAFETY.md` as
      the source mapping — that document flags two items that must be re-answered the moment
      they change: Crashlytics/Analytics activation, and the account-deletion mechanism.
- [ ] Complete the real Play Console **Content rating** questionnaire using
      `docs/release/STORE_LISTING.md` §2 as a starting point — answer the live questionnaire
      directly, don't just copy the guidance verbatim.

## 5. Store listing readiness

- [ ] Design a real app icon (512×512, replaces the AGP default template launcher icon still in
      `app/src/main/res/mipmap-*`).
- [ ] Produce a feature graphic (1024×500) and 2–8 real phone screenshots from an actual running
      build — see `docs/release/STORE_LISTING.md` §4 for the suggested shot list and captions.
- [ ] Decide on tablet screenshots — the app's responsiveness on tablet form factors has not been
      evaluated at all.
- [ ] Fill in `docs/release/STORE_LISTING.md`'s contact fields (support email/phone/website).
- [ ] Generate the release keystore and provision CI secrets per `docs/RELEASE_SIGNING.md`, then
      actually build and verify one signed release artifact end-to-end.
- [ ] Add the release-build CI job described in `docs/RELEASE_SIGNING.md` §3 — it doesn't exist
      yet; only lint/unit-test/dependency-scan jobs currently run in `android-ci.yml`.

## 6. Real product gaps (found during review, not yet closed)

- [ ] **No in-app "Delete my account" screen.** The backend endpoint (`DELETE /v1/auth/account`)
      is fully implemented; no Android UI calls it. This is very likely required before Play
      Store production submission under Google's Account Deletion policy — treat as a blocker,
      not a nice-to-have.
- [ ] **Push-notification deep links aren't consumed.** `ParkEaseMessagingService` attaches an
      `EXTRA_DEEP_LINK` intent extra when a notification is tapped, but `MainActivity` doesn't
      resolve it to a NavController route yet (documented gap, `MainActivity.kt`). Tapping a
      push notification currently just opens the app to wherever `isLoggedIn` routes it, not to
      the specific booking/dispute/ticket it's about.
- [ ] No in-app Settings/Legal screen linking the hosted Privacy Policy/Terms — currently only
      the backend config and the Play listing know those URLs.
- [ ] `DriverProfile.licence_number` is a schema column with zero real code usage anywhere —
      either wire it into the driver profile UI/validation or remove it from the schema; leaving
      unused PII-adjacent schema fields around is exactly the kind of thing that causes Data
      Safety form drift later.
- [ ] Tablet/large-screen layout has not been evaluated (see §5).

## 7. Operational readiness

- [ ] Real staging environment: deployed backend + real Postgres/PostGIS + Redis, not just
      `docker-compose` locally.
- [ ] Database backup/restore procedure and tested recovery — nothing in this repo covers backup
      strategy.
- [ ] Monitoring/alerting beyond application logs — decide what "someone gets paged" means for
      this system (payment webhook failures and settlement failures are the two most likely
      candidates for real financial impact if silently broken).
- [ ] Load-test the booking-hold capacity path (`UPDATE ... WHERE (capacity - reserved -
      occupied - blocked) >= 1`) under real concurrent load — it's proven correct by a targeted
      unit test against the exact SQL shape, but that's not the same as observing it under
      production-scale contention.

---
*This checklist is the honest difference between "the code for all 14 milestones exists and is
structurally sound" and "this is live for real users handling real money." Work through it in
order — §1 (real build/test) will very likely surface issues that make parts of §2–§7 easier or
harder than they look from here, so don't skip ahead to store-listing polish before the code has
actually been compiled and run for the first time.*
