# ParkEase release R8/ProGuard rules.
# Populated incrementally as each milestone adds libraries with reflection
# needs (Moshi/Retrofit models, Room entities, Hilt). Kept intentionally
# short in Milestone 1 — an empty/near-empty file here is correct, not an
# oversight, since no networked/reflected models exist yet.

# Keep line numbers for readable crash reports (Crashlytics).
-keepattributes SourceFile,LineNumberTable
-renamesourcefileattribute SourceFile
