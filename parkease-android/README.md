# ParkEase Android

Kotlin + Jetpack Compose + Material 3 + Hilt + Retrofit + Room. See
`../ParkEase Architecture Blueprint.md` (Milestone 0) for the full system design.

## Requirements

- Android Studio (Ladybug or newer) or the command-line SDK, with `compileSdk 36` /
  build-tools installed.
- JDK 17.

## Build

```bash
./gradlew :core:core-model:test   # pure-Kotlin module — no Android SDK required
./gradlew assembleDevDebug        # full app — requires the Android SDK
./gradlew testDevDebugUnitTest
```

## Push notifications (Firebase) setup

`app/build.gradle.kts` applies the Google Services Gradle plugin only if
`app/google-services.json` exists — a fresh checkout builds, lints, and unit-tests fine
without it, and `PushTokenProvider`/`ParkEaseMessagingService` (Milestone 11) degrade
safely (device registration just no-ops) rather than crashing. To turn on real FCM push:

1. Create a Firebase project and add an Android app for each flavor's applicationId you
   need (`com.parkease.app.dev`, `com.parkease.app.staging`, `com.parkease.app` for prod).
2. Download that project's `google-services.json` and place it at `app/google-services.json`
   (one file covers all `client` entries/applicationIds registered in that Firebase project).
3. Rebuild — the plugin picks it up automatically, no code changes needed.

Never commit a real `google-services.json` for a production Firebase project to a public
repo; it's safe to keep private/CI-secret the same way the release keystore is (Milestone 13).

## Module map (Milestone 1 state)

| Module | Type | Status |
|---|---|---|
| `app` | Android application | Scaffold: Hilt entry point, dev/staging/prod flavors, placeholder Compose screen |
| `core/core-model` | Pure Kotlin (JVM) | **Real & tested**: vehicle/booking/payment/settlement enums, `Money`, the client-side compatibility predicate mirroring the backend's `is_bookable` check |
| `core/core-ui` | Android library | Placeholder theme entry point; real design-system tokens land in Milestone 5 |
| `core/core-network` | Android library | Placeholder; real Retrofit/OkHttp client + auth interceptor lands in Milestone 2 |
| `core/core-database` | Android library | Placeholder; real Room entities/DAOs land per-feature starting Milestone 3 |
| `core/core-datastore` | Android library | Placeholder; real session DataStore lands in Milestone 2 |
| `core/core-location` | Android library | Placeholder; real FusedLocationProvider wrapper lands in Milestone 5 |
| `core/core-analytics` | Android library | Placeholder; real consent-gated analytics lands in Milestone 11 |

Feature modules (`feature/auth`, `feature/driver-search`, `feature/owner-*`, etc.) are
intentionally not created yet — each is added when its milestone starts, per the
Milestone 0 folder structure.

## What this milestone does NOT include

No screens, no networking, no local persistence, no location, no QR scanning — this is
project foundation only. The one piece of real, working logic is `core-model`, because
it's pure Kotlin and testable without the Android SDK.

## Known limitation — unverified in this environment

This project was scaffolded in a sandbox with no egress to Maven Central, Google's Maven
repository, or the Gradle Plugin Portal (and no Android SDK installed), so **no Gradle
build has actually been run** here — not even `:core:core-model:test`. Files were
validated the ways that don't require dependency resolution: directory/module wiring
cross-checked against `settings.gradle.kts`, TOML syntax of the version catalog, and
manual review of every `build.gradle.kts`. The first real build must happen on a
developer machine or in CI (see `.github/workflows/android-ci.yml`) with normal internet
access — treat this scaffold as unverified until that build passes, and report back
anything that doesn't compile so it can be fixed before Milestone 2 builds on top of it.
