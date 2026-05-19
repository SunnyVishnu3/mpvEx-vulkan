package com.astrixforge.devicemasker.xposed

import android.content.SharedPreferences
import android.util.Log
import com.astrixforge.devicemasker.common.SharedPrefsKeys
import com.astrixforge.devicemasker.xposed.diagnostics.XposedDiagnosticEventSink
import com.astrixforge.devicemasker.xposed.hooker.AdvertisingHooker
import com.astrixforge.devicemasker.xposed.hooker.AntiDetectHooker
import com.astrixforge.devicemasker.xposed.hooker.DeviceHooker
import com.astrixforge.devicemasker.xposed.hooker.LocationHooker
import com.astrixforge.devicemasker.xposed.hooker.NetworkHooker
import com.astrixforge.devicemasker.xposed.hooker.PackageManagerHooker
import com.astrixforge.devicemasker.xposed.hooker.ProcMapsHooker
import com.astrixforge.devicemasker.xposed.hooker.SensorHooker
import com.astrixforge.devicemasker.xposed.hooker.SubscriptionHooker
import com.astrixforge.devicemasker.xposed.hooker.SystemFeatureHooker
import com.astrixforge.devicemasker.xposed.hooker.SystemHooker
import com.astrixforge.devicemasker.xposed.hooker.WebViewHooker
import io.github.libxposed.api.XposedModule
import io.github.libxposed.api.XposedModuleInterface.ModuleLoadedParam
import io.github.libxposed.api.XposedModuleInterface.PackageReadyParam
import io.github.libxposed.api.XposedModuleInterface.SystemServerStartingParam
import io.github.libxposed.api.error.XposedFrameworkError
import java.util.concurrent.ConcurrentHashMap

/**
 * Device Masker entry point for libxposed API 101.
 *
 * Each target process gets a NEW instance of this class — LSPosed reads
 * META-INF/xposed/java_init.list, instantiates this class via reflection, then calls
 * onPackageReady() or onSystemServerStarting() as appropriate.
 *
 * Architecture decisions:
 * - `instance` singleton gives hookers access to the XposedModule log API
 * - RemotePreferences retrieved via `getRemotePreferences(PREFS_GROUP)` — live, no restart needed
 * - Each hooker is a stateless `object` with a `hook(cl, xi, prefs, pkg)` factory method
 * - All hook registrations wrapped in `hookSafely()` — one failed hooker never crashes others
 *
 * Config delivery path (post-migration): UI → ConfigSync → RemotePreferences → LSPosed DB →
 * getRemotePreferences() (in hooks)
 *
 * Diagnostics path: hooks log to LSPosed/logcat. Root Maximum support bundles can collect that
 * evidence through the app's root log capture path.
 */
class XposedEntry : XposedModule() {

    companion object {
        const val TAG = "DeviceMasker"
        const val SELF_PACKAGE = "com.astrixforge.devicemasker"

        /**
         * Preferences group name matching the name written by XposedPrefs.kt in :app. Must match
         * exactly — this is the bridge between what the UI writes and what hooks read.
         */
        const val PREFS_GROUP = "device_masker_config"

        /**
         * Packages to skip entirely for app hooks. These are system-critical processes that must
         * never be intercepted. Note: "android" is skipped separately in onPackageReady() since it
         * is handled by onSystemServerStarting().
         */
        private val SKIP_PACKAGES =
            setOf(
                SELF_PACKAGE, // Own module app — never hook ourselves
                "com.android.systemui", // SystemUI — visual glitch risk
                "com.android.phone", // Phone/Dialer — call quality risk
                "com.google.android.gms", // GMS — integrity check interference risk
            )

        /** Singleton reference to this module instance. Set once per process in the init block. */
        @Volatile
        lateinit var instance: XposedEntry
            private set

        private val hookedClassLoaders = ConcurrentHashMap.newKeySet<Int>()

        internal fun isPackageCurrentlyEnabledForHooks(
            packageName: String,
            prefs: SharedPreferences,
        ): Boolean {
            if (!prefs.getBoolean(SharedPrefsKeys.KEY_MODULE_ENABLED, false)) return false
            val enabledApps =
                prefs.getStringSet(SharedPrefsKeys.KEY_ENABLED_APPS, emptySet()) ?: emptySet()
            return packageName in enabledApps &&
                prefs.getBoolean(SharedPrefsKeys.getAppEnabledKey(packageName), false)
        }
    }

    private var processName: String = ""

    override fun onModuleLoaded(param: ModuleLoadedParam) {
        super.onModuleLoaded(param)
        instance = this
        processName = param.processName
        log(Log.INFO, TAG, "XposedEntry loaded for process: ${param.processName}", null)
        XposedDiagnosticEventSink.log(
            Log.INFO,
            TAG,
            "XposedEntry loaded for process: ${param.processName}",
            eventType =
                com.astrixforge.devicemasker.common.diagnostics.DiagnosticEventType
                    .XPOSED_ENTRY_LOADED,
        )
    }

    override fun onSystemServerStarting(param: SystemServerStartingParam) {
        log(Log.INFO, TAG, "System server loaded for Device Masker", null)
    }

    /**
     * Called when a target package's ClassLoader is ready. Hooks set here intercept identifier
     * reads by the app while avoiding PackageLoadedParam.defaultClassLoader, which is only
     * available on Android 10+.
     *
     * @param param Contains packageName, classLoader, and process metadata.
     */
    override fun onPackageReady(param: PackageReadyParam) {
        val pkg = param.packageName
        if (shouldSkipLoadedPackage(pkg)) return

        val prefs = remotePreferencesOrNull(pkg) ?: return
        val hookPackage = enabledHookPackageOrNull(pkg, prefs) ?: return
        XposedDiagnosticEventSink.log(
            Log.INFO,
            TAG,
            "Target package selected: $hookPackage",
            eventType =
                com.astrixforge.devicemasker.common.diagnostics.DiagnosticEventType
                    .TARGET_PACKAGE_SELECTED,
        )

        val cl = classLoaderToHookOrNull(param.classLoader, hookPackage) ?: return

        // ═══════════════════════════════════════════════════════════
        // HOOK ORDER IS CRITICAL — do not reorder
        // ═══════════════════════════════════════════════════════════
        // 1. AntiDetect MUST load first — detection apps check for Xposed at launch.
        //    If spoofing hooks run before AntiDetect, Xposed evidence is visible in that window.
        // 2. SubscriptionHooker alongside DeviceHooker — both hook telephony paths.
        //    Apps cross-check TelephonyManager and SubscriptionManager results.
        // 3. Remaining hookers can run in any order.
        // ═══════════════════════════════════════════════════════════
        val policy = HookFamilyPolicy.fromPrefs(prefs, hookPackage)
        val snapshot = HookConfigSnapshot.fromPrefs(prefs, hookPackage)
        hookSafely(hookPackage, "AntiDetectHooker", policy.antiDetectEnabled) {
            AntiDetectHooker.hook(cl, this, hookPackage)
            ProcMapsHooker.hook(cl, this, prefs, hookPackage)
        }
        hookSafely(hookPackage, "DeviceHooker", policy.deviceEnabled) {
            DeviceHooker.hook(cl, this, hookPackage, snapshot)
        }
        hookSafely(hookPackage, "SubscriptionHooker", policy.subscriptionEnabled) {
            SubscriptionHooker.hook(cl, this, hookPackage, snapshot)
        }
        hookSafely(hookPackage, "NetworkHooker", policy.networkEnabled) {
            NetworkHooker.hook(cl, this, hookPackage, snapshot)
        }
        hookSafely(hookPackage, "SystemHooker", policy.systemEnabled) {
            SystemHooker.hook(cl, this, hookPackage, snapshot)
        }
        hookSafely(hookPackage, "SystemFeatureHooker", policy.systemFeatureEnabled) {
            SystemFeatureHooker.hook(cl, this, hookPackage, snapshot)
        }
        hookSafely(hookPackage, "LocationHooker", policy.locationEnabled) {
            LocationHooker.hook(cl, this, hookPackage, snapshot)
        }
        hookSafely(hookPackage, "SensorHooker", policy.sensorEnabled) {
            SensorHooker.hook(cl, this, hookPackage, snapshot)
        }
        hookSafely(hookPackage, "AdvertisingHooker", policy.advertisingEnabled) {
            AdvertisingHooker.hook(cl, this, hookPackage, snapshot)
        }
        hookSafely(hookPackage, "WebViewHooker", policy.webViewEnabled) {
            WebViewHooker.hook(cl, this, hookPackage, snapshot)
        }
        hookSafely(hookPackage, "PackageManagerHooker", policy.packageManagerEnabled) {
            PackageManagerHooker.hook(cl, this, hookPackage)
        }

        log(Log.INFO, TAG, "All hooks registered for: $hookPackage", null)
        XposedDiagnosticEventSink.log(
            Log.INFO,
            TAG,
            "All hooks registered for: $hookPackage",
            eventType =
                com.astrixforge.devicemasker.common.diagnostics.DiagnosticEventType.HOOK_REGISTERED,
        )
    }

    private fun shouldSkipLoadedPackage(pkg: String): Boolean =
        pkg == "android" || pkg in SKIP_PACKAGES

    private fun remotePreferencesOrNull(pkg: String): SharedPreferences? =
        try {
            getRemotePreferences(PREFS_GROUP)
        } catch (e: XposedFrameworkError) {
            throw e
        } catch (t: Throwable) {
            XposedDiagnosticEventSink.log(
                Log.WARN,
                TAG,
                "RemotePreferences unavailable for $pkg",
                t,
                com.astrixforge.devicemasker.common.diagnostics.DiagnosticEventType
                    .REMOTE_PREFS_UNAVAILABLE,
            )
            log(
                Log.WARN,
                TAG,
                "RemotePreferences unavailable for $pkg — skipping hooks: ${t.message}",
                t,
            )
            null
        }

    private fun enabledHookPackageOrNull(loadedPackage: String, prefs: SharedPreferences): String? {
        return selectHookPackage(loadedPackage, prefs)?.takeIf {
            isPackageCurrentlyEnabledForHooks(it, prefs)
        }
    }

    private fun classLoaderToHookOrNull(cl: ClassLoader, hookPackage: String): ClassLoader? {
        val classLoaderKey = System.identityHashCode(cl)
        if (hookedClassLoaders.add(classLoaderKey)) return cl

        log(Log.DEBUG, TAG, "Hooks already registered for classloader of $hookPackage", null)
        return null
    }

    private fun selectHookPackage(
        loadedPackage: String,
        prefs: android.content.SharedPreferences,
    ): String? {
        val processBasePackage = processName.substringBefore(':').takeIf { it.isNotBlank() }
        val candidates = listOfNotNull(loadedPackage, processBasePackage).distinct()
        return candidates.firstOrNull { candidate ->
            candidate !in SKIP_PACKAGES && isPackageCurrentlyEnabledForHooks(candidate, prefs)
        }
    }

    /**
     * Wraps a hooker registration block in try-catch so that one failed hooker cannot prevent
     * subsequent hookers from registering.
     *
     * If the block throws (e.g., class not found, method signature mismatch on this OEM), the error
     * is logged and execution continues with the next hooker.
     */
    private fun hookSafely(pkg: String, name: String, enabled: Boolean = true, block: () -> Unit) {
        if (!enabled) {
            log(Log.INFO, TAG, "$name disabled by hook-family policy for: $pkg", null)
            XposedDiagnosticEventSink.log(
                Log.INFO,
                TAG,
                "$name disabled by hook-family policy for: $pkg",
                eventType =
                    com.astrixforge.devicemasker.common.diagnostics.DiagnosticEventType.HOOK_SKIPPED,
            )
            return
        }
        try {
            XposedDiagnosticEventSink.hookHealth.recordRegistrationAttempt(name, "hook")
            block()
            XposedDiagnosticEventSink.hookHealth.recordRegistrationSuccess(name, "hook")
        } catch (e: XposedFrameworkError) {
            throw e
        } catch (t: Throwable) {
            XposedDiagnosticEventSink.hookHealth.recordRegistrationFailure(
                name,
                "hook",
                t.javaClass.simpleName,
            )
            XposedDiagnosticEventSink.log(
                Log.ERROR,
                TAG,
                "[$name] Hook registration failed for $pkg: ${t.javaClass.simpleName}: ${t.message}",
                t,
                com.astrixforge.devicemasker.common.diagnostics.DiagnosticEventType.HOOK_FAILED,
            )
            log(
                Log.ERROR,
                TAG,
                "[$name] Hook registration failed for $pkg: ${t.javaClass.simpleName}: ${t.message}",
                t,
            )
        }
    }

    fun reportSpoofEvent(pkg: String, spoofTypeName: String) {
        val result = XposedDiagnosticEventSink.hookHealth.recordSpoofEvent(pkg, spoofTypeName)
        if (result.shouldLog) {
            log(Log.DEBUG, TAG, "Spoof event: $pkg/$spoofTypeName", null)
        }
    }

    fun reportLog(tag: String, message: String, level: Int) {
        log(level, tag, message, null)
    }
}
