plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.hilt)
    alias(libs.plugins.ksp)
    alias(libs.plugins.google.services) apply false
}

// Release signing — see docs/RELEASE_SIGNING.md. Separate from app-partner's:
// two different Play Store listings need two different upload keys.
val releaseKeystorePath: String? = System.getenv("DRIVER_RELEASE_KEYSTORE_PATH")
val releaseKeystorePassword: String? = System.getenv("DRIVER_RELEASE_KEYSTORE_PASSWORD")
val releaseKeyAlias: String? = System.getenv("DRIVER_RELEASE_KEY_ALIAS")
val releaseKeyPassword: String? = System.getenv("DRIVER_RELEASE_KEY_PASSWORD")
val hasReleaseSigningConfig: Boolean =
    !releaseKeystorePath.isNullOrBlank() &&
        !releaseKeystorePassword.isNullOrBlank() &&
        !releaseKeyAlias.isNullOrBlank() &&
        !releaseKeyPassword.isNullOrBlank()

val razorpayKeyId: String = System.getenv("RAZORPAY_KEY_ID") ?: ""

android {
    namespace = "com.parkease.driver"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.parkease.driver"
        minSdk = 26
        targetSdk = 36
        versionCode = 1
        versionName = "0.1.0"

        buildConfigField("String", "RAZORPAY_KEY_ID", "\"$razorpayKeyId\"")

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    flavorDimensions += "environment"
    productFlavors {
        create("dev") {
            dimension = "environment"
            applicationIdSuffix = ".dev"
            versionNameSuffix = "-dev"
            buildConfigField("String", "API_BASE_URL", "\"https://easyparking-production.up.railway.app/\"")
            resValue("string", "app_name", "ParkEase Driver Dev")
        }
        create("staging") {
            dimension = "environment"
            applicationIdSuffix = ".staging"
            versionNameSuffix = "-staging"
            buildConfigField("String", "API_BASE_URL", "\"https://api-staging.parkease.app/\"")
            resValue("string", "app_name", "ParkEase Driver Staging")
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
    kotlin { jvmToolchain(17) }

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
    implementation(project(":core:core-maps"))
    implementation(project(":core:core-analytics"))
    implementation(project(":feature:auth"))
    implementation(project(":feature:vehicles"))
    implementation(project(":feature:driver-search"))
    implementation(project(":feature:booking"))
    implementation(project(":feature:notifications"))
    // Admin quick-switch (spec: rohitrreddy@gmail.com gets an optional
    // admin view from either app) — real access is still gated server-side
    // by AdminHomeScreen's own dashboardSummary() call, exactly as before.
    implementation(project(":feature:admin"))

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
    implementation(libs.androidx.compose.material.icons.extended)
    implementation(libs.androidx.navigation.compose)
    implementation(libs.retrofit.core)

    implementation(libs.hilt.android)
    ksp(libs.hilt.compiler)
    implementation(libs.hilt.navigation.compose)

    implementation(platform(libs.firebase.bom))
    implementation(libs.firebase.messaging.ktx)
    implementation(libs.firebase.crashlytics.ktx)
    implementation(libs.firebase.analytics.ktx)

    implementation(libs.razorpay.checkout)
    implementation(libs.coil.compose)

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

// See app-partner/build.gradle.kts's matching block — same "conditional
// plugin apply" reasoning. Each app needs its OWN Firebase project
// registration (google-services.json is keyed to applicationId), so this
// file's is independent of app-partner's.
if (file("google-services.json").exists()) {
    apply(plugin = "com.google.gms.google-services")
} else {
    logger.warn(
        "[parkease] app-driver/google-services.json not found — skipping the Google Services " +
            "Gradle plugin. FCM push notifications stay inert until a real Firebase project's " +
            "config file is added for com.parkease.driver.",
    )
}
