# ParkEase release R8/ProGuard rules.

# Keep line numbers for readable crash reports (Crashlytics).
-keepattributes SourceFile,LineNumberTable
-renamesourcefileattribute SourceFile

# feature:booking is compiled into this app (for OwnerBookingsScreen), and
# it references the Razorpay Checkout SDK even though this app never opens
# a real checkout — R8 still needs these rules to shrink cleanly rather
# than warn/fail on encountering those classes.
-dontwarn proguard.annotation.**
-keep class proguard.annotation.** { *; }
-keepattributes *Annotation*
-dontwarn com.razorpay.**
-keep class com.razorpay.** { *; }
-optimizations !method/inlining/*
-keepclasseswithmembers class * {
  public void onPayment*(...);
}
