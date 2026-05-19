# =============================================================================
# Device Masker — Consumer ProGuard Rules (:xposed module)
# libxposed API 101 edition — YukiHookAPI rules REMOVED.
#
# These rules are merged into :app's R8 run by AGP when :xposed is consumed
# as a library. They protect classes that are loaded via libxposed reflection
# or Android's ContentProvider discovery.
# =============================================================================

# =============================================================================
# LIBXPOSED API 101 — Hook entry point and hooker infrastructure
# =============================================================================

# XposedModule subclass — entry class declared in META-INF/xposed/java_init.list.
# LSPosed instantiates this class by name via ClassLoader.loadClass().
# API 101: XposedModule uses a no-arg constructor.
-keep class com.astrixforge.devicemasker.xposed.XposedEntry { *; }
-keepclassmembers class com.astrixforge.devicemasker.xposed.XposedEntry {
    public <init>();
    public *;
    static *;
}

# XposedModule base class — kept for subclassing
-keep class io.github.libxposed.api.XposedModule { *; }

# Hooker callback ABI — libxposed calls XposedInterface.Hooker.intercept(Chain)
# from target processes. Device Masker uses named StableHooker subclasses instead
# of Kotlin SAM lambdas so R8 cannot strip or rewrite the runtime callback ABI.
-keep interface io.github.libxposed.api.XposedInterface$Hooker { *; }
-keep class * implements io.github.libxposed.api.XposedInterface$Hooker { *; }
-keep class com.astrixforge.devicemasker.xposed.hooker.callback.** { *; }

# Chain, HookBuilder, HookHandle — used inside lambda bodies and returned by API
-keep interface io.github.libxposed.api.XposedInterface$Chain { *; }
-keep interface io.github.libxposed.api.XposedInterface$HookBuilder { *; }
-keep interface io.github.libxposed.api.XposedInterface$HookHandle { *; }

# Preserve META-INF/xposed/ file contents — LSPosed reads java_init.list by class name
-adaptresourcefilecontents META-INF/xposed/java_init.list

# =============================================================================
# HOOKERS — All hookers and their lambda interceptors
# =============================================================================
-keep class com.astrixforge.devicemasker.xposed.hooker.** { *; }
-keepclassmembers class com.astrixforge.devicemasker.xposed.hooker.** {
    public *;
    protected *;
    static *;
}

# =============================================================================
# UTILS — Logging, metrics, and prefs utils (xposed root package)
# =============================================================================
-keep class com.astrixforge.devicemasker.xposed.PrefsHelper { *; }
-keep class com.astrixforge.devicemasker.xposed.DualLog { *; }
-keep class com.astrixforge.devicemasker.xposed.HookMetrics { *; }
-keep class com.astrixforge.devicemasker.xposed.PrefsKeys { *; }

# =============================================================================
# LIBXPOSED SERVICE — ModulePreferences for writing RemotePreferences from :app
# =============================================================================
-keep class io.github.libxposed.service.** { *; }
-keep interface io.github.libxposed.service.** { *; }
-dontwarn io.github.libxposed.**

# =============================================================================
# SUPPRESS LEGACY XPOSED WARNINGS
# The libxposed API jar stubs may reference old de.robv classes for ABI compat.
# =============================================================================
-dontwarn de.robv.android.xposed.**
-dontwarn com.highcapable.**
