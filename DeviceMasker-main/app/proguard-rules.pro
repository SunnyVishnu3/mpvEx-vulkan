# =============================================================================
# Device Masker — R8 / ProGuard Rules (app module)
# libxposed API 101 edition — YukiHookAPI / KavaRef rules REMOVED.
#
# Applied by R8 during assembleRelease.
# Base: proguard-android-optimize.txt (set in app/build.gradle.kts).
# Consumer rules from :common and :xposed are merged automatically by AGP.
# =============================================================================

# =============================================================================
# DEBUGGING — Keep source file & line numbers for deobfuscated crash reports
# =============================================================================
-keepattributes SourceFile,LineNumberTable
-renamesourcefileattribute SourceFile
-keepattributes Signature
-keepattributes Exceptions
-keepattributes InnerClasses

# Annotations are required by kotlinx.serialization, Compose, and libxposed
-keepattributes *Annotation*
-keepattributes RuntimeVisibleAnnotations
-keepattributes RuntimeInvisibleAnnotations
-keepattributes AnnotationDefault

# =============================================================================
# LIBXPOSED SERVICE — RemotePreferences / ContentProvider in :app process
# Used by XposedPrefs.init() and XposedPrefs.getPrefs() at runtime.
# =============================================================================
-keep class io.github.libxposed.service.** { *; }
-keep interface io.github.libxposed.service.** { *; }
-keepclassmembers class io.github.libxposed.service.** { *; }
-dontwarn io.github.libxposed.**

# XposedProvider is declared in AndroidManifest.xml
-keep class io.github.libxposed.service.XposedProvider { *; }

# =============================================================================
# LIBXPOSED API — Hook interface hierarchy and named callback classes.
# Device Masker uses explicit StableHooker subclasses for release R8 stability.
# =============================================================================
-keep interface io.github.libxposed.api.XposedInterface$Hooker { *; }
-keep class * implements io.github.libxposed.api.XposedInterface$Hooker { *; }
-keep class com.astrixforge.devicemasker.xposed.hooker.callback.** { *; }
-keep class com.astrixforge.devicemasker.xposed.hooker.** { *; }
-keep interface io.github.libxposed.api.XposedInterface$Chain { *; }
-keep interface io.github.libxposed.api.XposedInterface$HookBuilder { *; }
-keep interface io.github.libxposed.api.XposedInterface$HookHandle { *; }

# =============================================================================
# DEVICE MASKER — Application class (referenced in AndroidManifest)
# =============================================================================
-keep class com.astrixforge.devicemasker.DeviceMaskerApp { *; }

# =============================================================================
# DEVICE MASKER — Config service layer
# ConfigManager (app-side) manages StateFlow config, handles JSON file + RemotePreferences sync.
# =============================================================================
-keep class com.astrixforge.devicemasker.service.ConfigManager { *; }

# XposedPrefs wraps RemotePreferences write path; keep for ConfigSync calls
-keep class com.astrixforge.devicemasker.data.XposedPrefs { *; }
-keepclassmembers class com.astrixforge.devicemasker.data.XposedPrefs {
    public static *;
}

# ConfigSync flattens JsonConfig into per-app RemotePreferences keys
-keep class com.astrixforge.devicemasker.data.ConfigSync { *; }
-keepclassmembers class com.astrixforge.devicemasker.data.ConfigSync {
    public static *;
}

# =============================================================================
# KOTLINX SERIALIZATION — Compiler-generated serializers
# =============================================================================
-dontnote kotlinx.serialization.AnnotationsKt
-dontnote kotlinx.serialization.SerializationKt

-keepclassmembers @kotlinx.serialization.Serializable class ** {
    *** Companion;
    *** INSTANCE;
    kotlinx.serialization.KSerializer serializer(...);
}
-keepclasseswithmembers class ** {
    kotlinx.serialization.KSerializer serializer(...);
}
-keep class **$$serializer { *; }

# Keep Kotlin object singletons (SharedPrefsKeys, generators, ConfigSync, etc.)
-keepclassmembers class com.astrixforge.devicemasker.** {
    public static final *** INSTANCE;
}

# =============================================================================
# KOTLIN COROUTINES
# =============================================================================
-keepnames class kotlinx.coroutines.internal.MainDispatcherFactory {}
-keepnames class kotlinx.coroutines.CoroutineExceptionHandler {}
-keepnames class kotlinx.coroutines.android.AndroidExceptionPreHandler {}
-keepnames class kotlinx.coroutines.android.AndroidDispatcherFactory {}
-keepclassmembers class kotlinx.coroutines.** {
    volatile <fields>;
}
-keepclassmembers class kotlin.coroutines.SafeContinuation {
    volatile <fields>;
}
-dontwarn kotlinx.coroutines.**

# =============================================================================
# JETPACK COMPOSE
# =============================================================================
-dontwarn androidx.compose.**
-keepclassmembers class androidx.compose.runtime.** {
    *;
}

# =============================================================================
# NAVIGATION COMPOSE
# =============================================================================
-keepnames class * extends androidx.navigation.NavArgs

# =============================================================================
# DATASTORE
# =============================================================================
-dontwarn androidx.datastore.**

# =============================================================================
# HIDDEN API BYPASS — org.lsposed.hiddenapibypass
# =============================================================================
-keep class org.lsposed.hiddenapibypass.** { *; }
-dontwarn org.lsposed.hiddenapibypass.**

# =============================================================================
# TIMBER LOGGING — Strip verbose/debug/info calls in release builds
# -assumenosideeffects tells R8 these calls have no observable side effects.
# =============================================================================
-assumenosideeffects class timber.log.Timber {
    public static void v(java.lang.String, java.lang.Object[]);
    public static void v(java.lang.Throwable, java.lang.String, java.lang.Object[]);
    public static void d(java.lang.String, java.lang.Object[]);
    public static void d(java.lang.Throwable, java.lang.String, java.lang.Object[]);
    public static void i(java.lang.String, java.lang.Object[]);
    public static void i(java.lang.Throwable, java.lang.String, java.lang.Object[]);
}
-dontwarn org.jetbrains.annotations.**

# =============================================================================
# COIL 3 — App icon loading
# =============================================================================
-dontwarn io.coil3.**
-keep class io.coil3.** { *; }

# =============================================================================
# KOTLIN STANDARD LIBRARY
# =============================================================================
-dontwarn kotlin.**
-dontwarn kotlin.reflect.**
-keep class kotlin.Metadata { *; }
-keepclassmembers class kotlin.Metadata {
    public <methods>;
}
-keepclassmembers class kotlin.jvm.functions.** { *; }

# =============================================================================
# GENERAL ANDROID — Fundamental classes referenced by AndroidManifest
# =============================================================================
-keep public class * extends android.app.Activity
-keep public class * extends android.app.Service
-keep public class * extends android.content.ContentProvider
-keep public class * extends android.content.BroadcastReceiver

-keepclassmembers class * implements android.os.Parcelable {
    public static final ** CREATOR;
}

-keepclassmembers enum * {
    public static **[] values();
    public static ** valueOf(java.lang.String);
}

# =============================================================================
# SUPPRESS LEGACY XPOSED WARNINGS
# libxposed API jar stubs may transitively reference legacy classes.
# =============================================================================
-dontwarn de.robv.android.xposed.**
-dontwarn io.github.lsposed.**
-dontnote java.lang.reflect.AnnotatedType
-dontnote java.lang.reflect.AnnotatedElement
-dontnote java.lang.invoke.MethodHandles$Lookup
