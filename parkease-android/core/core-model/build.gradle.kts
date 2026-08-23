// Pure-Kotlin JVM module — deliberately NOT an Android library. It holds
// enums/value types shared across every feature module (vehicle category,
// booking/payment/refund/settlement status, Money) that have zero Android
// framework dependency, so this module builds and unit-tests with just a
// JVM + Kotlin — no Android SDK required. This is the one Android-adjacent
// module validated with a real `gradle test` run in Milestone 1 (see the
// backend/Android README for what was and wasn't verified in the sandbox).
plugins {
    alias(libs.plugins.kotlin.jvm)
}

kotlin {
    jvmToolchain(17)
}

dependencies {
    testImplementation(libs.junit)
    testImplementation(libs.truth)
}
