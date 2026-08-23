plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.hilt)
    alias(libs.plugins.ksp)
    // Declared (resolved onto the classpath) but not applied here — see the
    // conditional `apply(plugin = ...)` call at the bottom of this file.
    alias(libs.plugins.google.services) apply false
}

// -----------------------------------------------------------------------------
// Milestone 13 (Play Store package): release signing.
//
// Read from env vars so the real keystore file and its passwords are NEVER
// committed — CI injects these from its own secret store (see
// docs/RELEASE_SIGNING.md for how to generate the keystore and where to put
// the secrets in GitHub Actions). All four must be present together for a
// real signing config to exist at all; if any is missing, `signingConfigs`
// simply has no "release" entry and `buildTypes.release.signingConfig`
// stays unset. AGP then produces an UNSIGNED release artifact rather than
// silently falling back to the debug key (a common, dangerous template
// shortcut this project deliberately avoids) — an unsigned APK/AAB fails
// loudly the moment anyone tries to install it or upload it to Play, which
// is the correct failure mode for "someone tried to build a release
// without the real keystore."
// -----------------------------------------------------------------------------
val releaseKeystorePath: String? = System.getenv("RELEASE_KEYSTORE_PATH")
val releaseKeystorePassword: String? = System.getenv("RELEASE_KEYSTORE_PASSWORD")
val releaseKeyAlias: String? = System.getenv("RELEASE_KEY_ALIAS")
val releaseKeyPassword: String? = System.getenv("RELEASE_KEY_PASSWORD")
val hasReleaseSigningConfig: Boolean =
    !releaseKeystorePath.isNullOrBlank() &&
        !releaseKeystorePassword.isNullOrBlank() &&
        !releaseKeyAlias.isNullOrBlank() &&
        !releaseKeyPassword.isNullOrBlank()

// Razorpay's Checkout "key_id" is meant to be public/client-embeddable (the
// secret half, key_secret, only ever lives on the backend — see
// razorpay-provider.service.ts). Still read from env rather than committed,
// same as everything else gateway-related: empty by default means Pay Now
// honestly reports payments as unavailable rather than opening a checkout
// sheet that can never succeed.
val razorpayKeyId: String = System.getenv("RAZORPAY_KEY_ID") ?: ""

android {
    namespace = "com.parkease.app"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.parkease.app"
        minSdk = 26
        targetSdk = 36
        versionCode = 1
        versionName = "0.1.0"

        buildConfigField("String", "RAZORPAY_KEY_ID", "\"$razorpayKeyId\"")

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    // ---------------------------------------------------------------------
    // Environment separation (Milestone 0 §16 / "Release Environment
    // Separation"): dev/staging point at their own backend + own
    // applicationIdSuffix so all three can be installed side by side on one
    // device; prod is what ships to Play.
    // ---------------------------------------------------------------------
    flavorDimensions += "environment"
    productFlavors {
        create("dev") {
            dimension = "environment"
            applicationIdSuffix = ".dev"
            versionNameSuffix = "-dev"
            // Local-machine override for on-device testing: the dev backend isn't deployed
            // anywhere reachable yet, so this points at the developer's own LAN IP running
            // `npm run start:dev` (see app/src/dev/res/xml/network_security_config.xml for
            // the matching cleartext allowance). Swap back to a real https://api-dev... URL
            // once a dev backend is actually deployed somewhere.
            buildConfigField("String", "API_BASE_URL", "\"http://192.168.1.5:3000/\"")
            resValue("string", "app_name", "ParkEase Dev")
        }
        create("staging") {
            dimension = "environment"
            applicationIdSuffix = ".staging"
            versionNameSuffix = "-staging"
            buildConfigField("String", "API_BASE_URL", "\"https://api-staging.parkease.app/\"")
            resValue("string", "app_name", "ParkEase Staging")
        }
        create("prod") {
            dimension = "environment"
            buildConfigField("String", "API_BASE_URL", "\"https://api.parkease.app/\"")
            resValue("string", "app_name", "ParkEase")
        }
    }

    signingConfigs {
        if (hasReleaseSigningConfig) {
            create("release") {
                storeFile = file(releaseKeystorePath!!)
                storePassword = releaseKeystorePassword
                keyAlias = releaseKeyAlias
                keyPassword = releaseKeyPassword
                // Play App Signing re-signs with Google's own key for
                // distribution anyway — this upload key only needs to
                // satisfy AGP's own signing-config validation, not carry
                // v1/v2/v3/v4 scheme flags beyond AGP's defaults.
            }
        }
    }

    buildTypes {
        debug {
            isMinifyEnabled = false
            isDebuggable = true
        }
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            if (hasReleaseSigningConfig) {
                signingConfig = signingConfigs.getByName("release")
            }
            // else: no signingConfig is assigned at all — see the
            // Milestone 13 doc comment above `hasReleaseSigningConfig` for
            // why that's the intentional, safe failure mode rather than a
            // gap to "fix" with a fallback.
        }
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlin {
        jvmToolchain(17)
    }

    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }
}

dependencies {
    implementation(project(":core:core-model"))
    implementation(project(":core:core-ui"))
    implementation(project(":core:core-network"))
    implementation(project(":core:core-database"))
    implementation(project(":core:core-datastore"))
    implementation(project(":core:core-location"))
    implementation(project(":core:core-analytics"))
    implementation(project(":feature:auth"))
    implementation(project(":feature:vehicles"))
    implementation(project(":feature:owner-parking"))
    implementation(project(":feature:driver-search"))
    implementation(project(":feature:booking"))
    implementation(project(":feature:attendant"))
    implementation(project(":feature:earnings"))
    implementation(project(":feature:notifications"))

    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.activity.compose)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.navigation.compose)

    implementation(libs.hilt.android)
    ksp(libs.hilt.compiler)
    implementation(libs.hilt.navigation.compose)

    implementation(platform(libs.firebase.bom))
    implementation(libs.firebase.messaging.ktx)
    implementation(libs.firebase.crashlytics.ktx)
    implementation(libs.firebase.analytics.ktx)

    implementation(libs.razorpay.checkout)

    debugImplementation(libs.androidx.compose.ui.tooling)
    debugImplementation(libs.androidx.compose.ui.test.manifest)

    testImplementation(libs.junit)
    testImplementation(libs.truth)
    testImplementation(libs.kotlinx.coroutines.test)
    androidTestImplementation(libs.androidx.test.ext.junit)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
}

// -----------------------------------------------------------------------------
// Milestone 12 (release hardening) fix: the Google Services Gradle plugin
// throws and fails EVERY task in this module (not just the ones that need
// it) the moment it's applied without a real app/google-services.json —
// and this sandboxed build environment, like any fresh checkout before
// someone has created a real Firebase project and downloaded that file,
// never has one. Applying the plugin conditionally means:
//   - a checkout without google-services.json still builds, lints, and
//     unit-tests cleanly (including CI, until a real Firebase project is
//     wired up for Milestone 13's Play Store release);
//   - PushTokenProvider/ParkEaseMessagingService (Milestone 11) still
//     compile and run either way — FirebaseMessaging.getInstance().token
//     just fails and PushTokenProvider.currentToken() returns null, which
//     every caller already treats as "skip push registration for now," per
//     that class's own doc comment, not as a crash;
//   - dropping in a real google-services.json (per Firebase Console ->
//     Project settings -> your Android app, matching applicationId per
//     flavor: com.parkease.app.dev / .staging / com.parkease.app) is the
//     only step needed to turn real push notifications on — no code change.
// -----------------------------------------------------------------------------
if (file("google-services.json").exists()) {
    apply(plugin = "com.google.gms.google-services")
} else {
    logger.warn(
        "[parkease] app/google-services.json not found — skipping the Google Services Gradle " +
            "plugin. The build, lint, and unit tests all still work; FCM push notifications " +
            "(Milestone 11) simply stay inert until a real Firebase project's config file is " +
            "added. See the comment above this block.",
    )
}
