import java.util.Properties

plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
}

// Same local.properties -> BuildConfig pattern used for local dev secrets
// elsewhere in this project (sdk.dir etc.), with a CI/secret-manager env
// var as the fallback so staging/prod builds don't need a committed file.
// Blank key -> mapsConfigured() below returns false and every consumer
// screen falls back to its existing non-map UI — never a broken/blank map.
val localProperties = Properties().apply {
    val file = rootProject.file("local.properties")
    if (file.exists()) file.inputStream().use { load(it) }
}
val mapsApiKey: String = (localProperties.getProperty("MAPS_API_KEY") ?: System.getenv("MAPS_API_KEY_ANDROID") ?: "").trim()

android {
    namespace = "com.parkease.core.maps"
    compileSdk = 36
    defaultConfig {
        minSdk = 26
        buildConfigField("String", "MAPS_API_KEY", "\"$mapsApiKey\"")
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
}

dependencies {
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.material3)
    implementation(libs.maps.compose)
    // api, not implementation: LatLng is part of LocationPickerMap's own
    // public signature (the defaultCenter parameter), so consumers need it
    // on their compile classpath too, not just core-maps' own.
    api(libs.play.services.maps)

    testImplementation(libs.junit)
    testImplementation(libs.truth)
}
