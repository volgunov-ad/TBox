# Add project specific ProGuard rules here.
# You can control the set of applied configuration files using the
# proguardFiles setting in build.gradle.
#
# For more details, see
#   http://developer.android.com/guide/developing/tools/proguard.html

# If your project uses WebView with JS, uncomment the following
# and specify the fully qualified class name to the JavaScript interface
# class:
#-keepclassmembers class fqcn.of.javascript.interface.for.webview {
#   public *;
#}

# Uncomment this to preserve the line number information for
# debugging stack traces.
#-keepattributes SourceFile,LineNumberTable

# If you keep the line number information, uncomment this to
# hide the original source file name.
#-renamesourcefileattribute SourceFile

# mbCAN JNI glue resolves Java symbols by exact names/signatures.
# Keep the full vendor package unobfuscated in release builds.
-keep class com.mengbo.** { *; }
-keepnames class com.mengbo.**

# Keep enum constants used via reflection/valueOf in vendor code.
-keepclassmembers enum com.mengbo.** {
    public static **[] values();
    public static ** valueOf(java.lang.String);
}

-keep class com.yandex.** { *; }
-dontwarn com.yandex.**

# app_process loads this class by name from the app APK (shell uid).
-keep class vad.dashing.tbox.hotspot.HuSoftApMain {
    public static void main(java.lang.String[]);
}

# Expert raw Get/Set and HuCanMarkLog resolve names via Class.getDeclaredFields.
# Kotlin `const val` is inlined then dropped by R8 even under `-keep class { *; }`.
# The id objects use `@JvmField val` plus this keep so the static ints survive.
-keep class vad.dashing.tbox.mbcan.MbCanKnownVehiclePropertyId {
    public static <fields>;
}
-keep class vad.dashing.tbox.mbcan.MbCanKnownAudioPropertyId {
    public static <fields>;
}