# Add project specific ProGuard rules here.
# You can control the set of applied configuration files using the
# proguardFiles setting in build.gradle.kts.

# AtomicXCore SDK
-keep class com.tencent.** { *; }

# SVGAPlayer
-keep class com.opensource.svgaplayer.** { *; }

# Coil
-dontwarn coil.**
