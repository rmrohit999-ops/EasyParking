# build-logic

Reserved for shared Gradle convention plugins (e.g. `parkease.android.library`,
`parkease.android.feature`) that would replace the repeated `compileSdk`/`minSdk`/
`jvmToolchain` blocks currently duplicated across each module's `build.gradle.kts`.

Deliberately left unimplemented in Milestone 1: writing and wiring an untested
`buildSrc`/included-build convention plugin without the ability to run Gradle against a
real Android SDK + Maven Central in this environment (see the Milestone 1 report) is a
good way to ship broken build logic. The duplication today is small (7 modules) and
explicit; this module set is the first candidate for extraction once Milestone 5 or 6
adds enough feature modules that the duplication becomes a real maintenance cost, and
once it can be validated with a real build.
