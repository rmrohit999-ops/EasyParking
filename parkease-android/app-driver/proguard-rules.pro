# ParkEase release R8/ProGuard rules.

# Keep line numbers for readable crash reports (Crashlytics).
-keepattributes SourceFile,LineNumberTable
-renamesourcefileattribute SourceFile

# Razorpay Checkout SDK — its own classes reference
# proguard.annotation.Keep/KeepClassMembers, a compile-time-only annotation
# library not present at runtime; R8 needs to be told not to warn about
# that, on top of Razorpay's own documented release-build rules (keep the
# SDK itself + payment callback methods).
-dontwarn proguard.annotation.**
-keep class proguard.annotation.** { *; }
-keepattributes *Annotation*
-dontwarn com.razorpay.**
-keep class com.razorpay.** { *; }
-optimizations !method/inlining/*
-keepclasseswithmembers class * {
  public void onPayment*(...);
}
