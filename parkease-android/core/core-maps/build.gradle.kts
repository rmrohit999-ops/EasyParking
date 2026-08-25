plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.hilt)
    alias(libs.plugins.ksp)
}

android {
    namespace = "com.parkease.core.maps"
    compileSdk = 36
    defaultConfig { minSdk = 26 }
    buildFeatures {
        compose = true
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlin { jvmToolchain(17) }
}

dependencies {
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.lifecycle.runtime.compose)

    // osmdroid: OpenStreetMap rendering — no API key, no billing, replaces
    // Google Maps Compose / play-services-maps entirely (see OsmMap.kt's
    // doc comment). `api`, not `implementation`: GeoPoint appears directly
    // in this module's own public signatures, so consumers need it on
    // their compile classpath too, exactly like play-services-maps' LatLng
    // did before.
    api(libs.osmdroid.android)

    // OSRM routing client for real road/path-following polylines — a
    // small, independent Retrofit/OkHttp stack, since OSRM is a public
    // third-party host, not our own authenticated backend (deliberately
    // not sharing core-network's authenticated client).
    implementation(libs.retrofit.core)
    implementation(libs.retrofit.converter.moshi)
    implementation(libs.moshi.kotlin)
    ksp(libs.moshi.kotlin.codegen)
    implementation(libs.okhttp.core)
    implementation(libs.kotlinx.coroutines.android)

    implementation(libs.hilt.android)
    ksp(libs.hilt.compiler)

    testImplementation(libs.junit)
    testImplementation(libs.truth)
    testImplementation(libs.kotlinx.coroutines.test)
}
