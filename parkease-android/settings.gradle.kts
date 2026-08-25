pluginManagement {
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
    }
}

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
    }
}

rootProject.name = "ParkEase"

// -----------------------------------------------------------------------------
// Module list.
//
// `core-model` is a pure-Kotlin JVM module (no Android Gradle Plugin), so it
// can be built and unit-tested without an Android SDK — it holds the
// vehicle/booking/payment enums shared across the app.
//
// Two app modules, not one: app-driver (drivers) and app-partner (owners +
// attendants), each a real installable APK with its own applicationId and
// Play Store listing. The original single :app module (Welcome-screen
// role-picker serving all three roles from one APK) is retired — its
// source is left on disk under app/ for reference/rollback, but it's no
// longer included in the build. Both new app modules share every
// core-*/feature:* module below; only the thin app-level shell (Hilt
// entry point, root NavHost, manifest, signing) is duplicated per app.
// -----------------------------------------------------------------------------
include(":app-driver")
include(":app-partner")
include(":core:core-model")
include(":core:core-ui")
include(":core:core-network")
include(":core:core-database")
include(":core:core-datastore")
include(":core:core-location")
include(":core:core-maps")
include(":core:core-analytics")
include(":feature:auth")
include(":feature:vehicles")
include(":feature:owner-parking")
include(":feature:driver-search")
include(":feature:booking")
include(":feature:attendant")
include(":feature:earnings")
include(":feature:notifications")
include(":feature:admin")
