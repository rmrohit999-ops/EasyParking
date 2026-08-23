# ParkEase

"Find Parking. Park Easy." — a smart parking marketplace for the Indian market.

This repository was built milestone-by-milestone (Milestones 0–13) per the project plan; see
[`ParkEase Architecture Blueprint.md`](./ParkEase%20Architecture%20Blueprint.md) (Milestone 0)
for the full system design, and each subdirectory's own README for module-level detail.

- `parkease-backend/` — NestJS + Prisma + PostgreSQL/PostGIS + Redis API: auth, vehicles,
  parking listings/sections, search, booking (hold → confirm, with race-safe capacity), QR
  check-in/out, payments (Razorpay), refunds, owner payout accounts + settlements, disputes,
  support, fraud detection, admin tools, notifications (in-app + FCM push), and an admin
  analytics/reports module.
- `parkease-android/` — Kotlin + Jetpack Compose, multi-module Gradle Android client covering
  the driver, parking owner, attendant, and admin-adjacent flows against that API, plus push
  notifications and production release signing.
- `.github/workflows/` — CI for both (lint, unit tests, dependency scanning; see
  `docs/RELEASE_SIGNING.md` for what's still needed to add an actual signed-release CI job).
- `docs/release/` — Play Store submission package: `PRIVACY_POLICY.md`, `TERMS_OF_SERVICE.md`,
  `DATA_SAFETY.md` (Play Console Data Safety form mapping), `STORE_LISTING.md` (listing copy +
  asset checklist), `REVIEWER_INSTRUCTIONS.md` (how a Play reviewer reaches every role).
- `docs/RELEASE_SIGNING.md` — how to generate and use the release keystore, locally and in CI.

**Current status: all 14 planned milestones (0–13) are built.** This was developed and
validated in a sandboxed environment with **zero network egress** — meaning `npm install`,
`prisma generate`, `docker`, and a real Gradle/Android build could never actually be run here.
Every backend and Android source file was validated by brace-balance checking and a filtered,
syntax-only `tsc --noEmit` pass (backend) rather than a real compile, and by structural review
against real, well-established framework patterns (Android). **Before this ships anywhere:**
1. Run a real `npm install && prisma generate && npm run build` and `npm test` in the backend,
   and a real `./gradlew build` for Android, in an environment with network access, and fix
   whatever a real toolchain surfaces that this validation approach couldn't catch (type errors
   against the generated Prisma client being the most likely category, given the literal-enum
   workarounds used throughout to sidestep that exact gap).
2. Work through the remaining Milestone 13 gaps called out honestly in `docs/release/` (no
   in-app account-deletion screen yet; no app icon/screenshots/feature graphic yet; several
   `[BRACKETED]` placeholders in the Privacy Policy/Terms need real legal-entity details and a
   counsel review).
3. Provision real (test-mode, then production) credentials for every "reports unavailable until
   configured" integration: SMS/OTP provider, email provider, Razorpay payments, RazorpayX
   payouts, Firebase (push/analytics/crashlytics), Google OAuth, object storage.

See each subproject's own README for module-level detail, and `docs/release/` for what's needed
to actually submit to the Play Store.
