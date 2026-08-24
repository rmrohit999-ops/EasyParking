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
// Feature modules are added one at a time as their milestone starts, per
// the folder structure in the Milestone 0 architecture doc:
//   - Milestone 2: feature:auth
//   - Milestone 3: feature:vehicles
//   - Milestone 4+: feature:driver-search, feature:owner-*, etc. (not yet added)
// -----------------------------------------------------------------------------
include(":app")
include(":core:core-model")
include(":core:core-ui")
include(":core:core-network")
include(":core:core-database")
include(":core:core-datastore")
include(":core:core-location")
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
