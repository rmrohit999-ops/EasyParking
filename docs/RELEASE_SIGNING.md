# Release signing — ParkEase Android

Referenced from `parkease-android/app/build.gradle.kts`'s `hasReleaseSigningConfig` block. This
is the one document that explains how the four env vars it reads
(`RELEASE_KEYSTORE_PATH`, `RELEASE_KEYSTORE_PASSWORD`, `RELEASE_KEY_ALIAS`,
`RELEASE_KEY_PASSWORD`) get their real values, both for a developer building a release locally
and for CI.

## Why this exists

A release build (`assembleProdRelease` / `bundleProdRelease`) must be signed with a real upload
key before it can be installed outside of `debug`, or uploaded to Play Console. Committing a
keystore or its passwords to the repository would leak the signing key to anyone with read
access to the repo (including, forever, anyone who ever cloned it, via git history) — so neither
the keystore file nor its passwords are ever committed. `app/build.gradle.kts` reads them from
environment variables at build time instead; if they're absent, the release build produces an
**unsigned** artifact rather than silently falling back to the debug key — that's the intentional
fail-loud behavior (see the doc comment in `app/build.gradle.kts` right above
`hasReleaseSigningConfig`).

## 1. Generate the keystore (once, by whoever owns the release process)

```bash
keytool -genkeypair -v \
  -keystore parkease-release.keystore \
  -alias parkease-upload \
  -keyalg RSA -keysize 2048 -validity 10000
```

You'll be prompted for a keystore password, a key password (can be the same as the keystore
password), and identity details (organization name, etc. — these end up in the certificate, not
in the app itself, and are not user-facing).

**This keystore is the single most sensitive artifact in the release pipeline.** If it's lost,
you cannot publish an update to an existing Play Store listing under the same app — Play App
Signing (see §4) mitigates the worst case of this, but losing the *upload* key still means
re-establishing upload-key trust with Google, which involves a support process. Store it in a
password manager or secrets vault with restricted access — never in the repository, never in
plain chat/email.

## 2. Building a signed release locally

Set the four env vars in your shell (or a local `.envrc`/`.env` file that is itself
`.gitignore`d — never a committed file) before invoking Gradle:

```bash
export RELEASE_KEYSTORE_PATH=/absolute/path/to/parkease-release.keystore
export RELEASE_KEYSTORE_PASSWORD='...'
export RELEASE_KEY_ALIAS=parkease-upload
export RELEASE_KEY_PASSWORD='...'

cd parkease-android
./gradlew :app:bundleProdRelease   # produces the .aab for Play upload
# or: ./gradlew :app:assembleProdRelease   # produces a signed .apk
```

If any of the four vars is missing or blank, `hasReleaseSigningConfig` evaluates `false`, the
`release` signing config is never created, and the resulting `.aab`/`.apk` is unsigned — Gradle
will not fail the build itself, but attempting to install the APK or upload the AAB to Play
Console will fail with a signing error. That's the expected, safe behavior for "someone tried to
build a release without the real keystore" — treat it as a signal to check your env vars, not a
bug to route around.

## 3. Configuring CI (GitHub Actions)

Store the four values as **repository secrets** (Settings → Secrets and variables → Actions),
not as plain workflow env vars:

- `RELEASE_KEYSTORE_BASE64` — the keystore file, base64-encoded (`base64 -w0
  parkease-release.keystore`), since GitHub secrets are text, not files.
- `RELEASE_KEYSTORE_PASSWORD`
- `RELEASE_KEY_ALIAS`
- `RELEASE_KEY_PASSWORD`

In the release workflow job, decode the keystore to a temp path and export the four env vars the
Gradle build actually reads:

```yaml
      - name: Decode release keystore
        run: echo "${{ secrets.RELEASE_KEYSTORE_BASE64 }}" | base64 -d > "$RUNNER_TEMP/release.keystore"
      - name: Build signed release bundle
        env:
          RELEASE_KEYSTORE_PATH: ${{ runner.temp }}/release.keystore
          RELEASE_KEYSTORE_PASSWORD: ${{ secrets.RELEASE_KEYSTORE_PASSWORD }}
          RELEASE_KEY_ALIAS: ${{ secrets.RELEASE_KEY_ALIAS }}
          RELEASE_KEY_PASSWORD: ${{ secrets.RELEASE_KEY_PASSWORD }}
        working-directory: parkease-android
        run: ./gradlew :app:bundleProdRelease
```

**As of Milestone 13 this release workflow/job does not exist yet** — `android-ci.yml` only runs
lint/unit-tests/dependency-submission on every push, per Milestone 12. Adding a dedicated
`release.yml` (triggered on a version tag, running the steps above and then uploading the `.aab`
as a workflow artifact or directly to Play Console via a publishing action) is a real remaining
task — see the go-live checklist. Don't assume it exists just because this document describes
what it should do.

## 4. Play App Signing (recommended, not yet configured)

Google Play strongly recommends (and requires for new apps via Android App Bundle) enrolling in
**Play App Signing**: you upload your `.aab` signed with the *upload* key generated in §1, and
Google re-signs it with a separate *app signing* key that it manages and that never leaves
Google's infrastructure. This means losing the upload keystore is recoverable (Google can help
you rotate to a new upload key, since the app signing key — the one that matters for update
compatibility on users' devices — was never yours to lose). Enroll in Play App Signing the first
time you create the app's release in Play Console, under **Setup → App signing** — no code or
Gradle change is needed on ParkEase's side either way, since AGP always produces an upload-key-
signed artifact regardless of whether Play App Signing is enabled.

## 5. Verifying a build's signature

```bash
apksigner verify --print-certs app-prod-release.apk
# or, for the AAB, verify the SHA fingerprint against what Play Console shows under App signing
keytool -list -v -keystore parkease-release.keystore -alias parkease-upload
```

Compare the SHA-256 fingerprint to what's registered in Play Console (App integrity → App
signing key certificate, or Upload key certificate) before trusting a build.

---
*Maintainer note: this document describes the signing mechanism actually implemented in
`app/build.gradle.kts` as of Milestone 13. §3's CI workflow is prescriptive (what to add), not
descriptive (nothing like it exists in `.github/workflows/` yet) — that gap is called out
explicitly rather than implied to be done.*
