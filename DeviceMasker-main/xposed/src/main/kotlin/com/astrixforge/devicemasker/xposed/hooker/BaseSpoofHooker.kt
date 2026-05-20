package com.astrixforge.devicemasker.xposed.hooker

import com.astrixforge.devicemasker.common.DeviceProfilePreset
import com.astrixforge.devicemasker.common.SpoofType
import com.astrixforge.devicemasker.xposed.DualLog
import com.astrixforge.devicemasker.xposed.HookConfigSnapshot
import com.astrixforge.devicemasker.xposed.XposedEntry
import com.astrixforge.devicemasker.xposed.diagnostics.XposedDiagnosticEventSink
import io.github.libxposed.api.error.XposedFrameworkError
import java.lang.reflect.Method

/**
 * Base class for all Device Masker spoof hookers — libxposed API 101 edition.
 *
 * ## Design contract
 *
 * Each hooker is a stateless Kotlin `object` that extends this class. The lifecycle is:
 * 1. XposedEntry.onPackageLoaded() calls `Hooker.hook(cl, xi, pkg, snapshot)`
 * 2. `hook()` calls [safeHook] for each method it wants to intercept
 * 3. Inside each [safeHook] block: resolve the [Method], call `xi.hook().intercept(stableHooker {
 *    ... })`, then `xi.deoptimize()`
 * 4. The stable hooker callback handles the actual callback via `chain.proceed()` + return value.
 *
 * ## Why no instance state
 *
 * libxposed API 101 creates a NEW XposedModule instance per target process. Hooker objects are
 * registered once per process load and their callbacks fire on whatever thread the target app uses.
 * Shared state must stay process-stable. `XposedEntry` registers hooks only for the first package
 * loaded in a process to avoid later package loads corrupting process-global state.
 *
 * ## Hook safety pattern (API 101)
 *
 * Every single method hook must be in its own [safeHook] block. One failed method lookup (e.g.,
 * `getImei(int)` absent on some OEM firmware) must NOT prevent other hooks from registering.
 *
 * ```kotlin
 * object DeviceHooker : BaseSpoofHooker("DeviceHooker") {
 *     fun hook(cl: ClassLoader, xi: XposedInterface, pkg: String, snapshot: HookConfigSnapshot) {
 *         val tmClass = cl.loadClassOrNull("android.telephony.TelephonyManager") ?: return
 *         // Each method in its own safeHook — one failure cannot cascade
 *         safeHook("getImei()") {
 *             tmClass.methodOrNull("getImei")?.let { m ->
 *                 xi.hook(m).intercept(
 *                     stableHooker { chain ->
 *                     val original = chain.proceed()
 *                     getConfiguredSpoofValue(snapshot, SpoofType.IMEI) ?: original
 *                     },
 *                 )
 *                 xi.deoptimize(m)  // bypass ART inlining — CRITICAL for guaranteed delivery
 *             }
 *         }
 *     }
 * }
 * ```
 */
abstract class BaseSpoofHooker(protected val tag: String) {

    /**
     * Wraps a single hook registration in a try-catch.
     *
     * If the block throws for any reason (class not found, method not found, hook engine error),
     * the error is logged via XposedEntry.instance.log() and execution continues with the next
     * hook. This ensures one missing method on an OEM firmware never silently skips ALL subsequent
     * hooks.
     *
     * @param methodName Human-readable name for the method (used in error logs only)
     * @param block The hook registration code: resolve method → xi.hook() → xi.deoptimize()
     */
    protected fun safeHook(methodName: String, block: () -> Unit) {
        try {
            XposedDiagnosticEventSink.hookHealth.recordRegistrationAttempt(tag, methodName)
            block()
            XposedDiagnosticEventSink.hookHealth.recordRegistrationSuccess(tag, methodName)
        } catch (e: XposedFrameworkError) {
            throw e
        } catch (t: Throwable) {
            XposedDiagnosticEventSink.hookHealth.recordRegistrationFailure(
                tag,
                methodName,
                t.javaClass.simpleName,
            )
            val message = "safeHook($methodName) failed: ${t.javaClass.simpleName}: ${t.message}"
            DualLog.warn(tag, message, t)
            runCatching { XposedEntry.instance.log(android.util.Log.WARN, tag, message, null) }
        }
    }

    /**
     * Loads a class from this ClassLoader, returning null if not found. Safe for use in any process
     * — isolated renderers, webview sandboxes, etc. may not have all Android classes in their
     * ClassLoader.
     */
    protected fun ClassLoader.loadClassOrNull(name: String): Class<*>? =
        try {
            loadClass(name)
        } catch (_: ClassNotFoundException) {
            null
        }

    /**
     * Looks up a declared method by name and exact parameter types, returning null if missing.
     * Automatically makes the method accessible for reflection. Safe for OEM firmware variations
     * (e.g., `getImei(int)` may be absent on some devices).
     */
    protected fun Class<*>.methodOrNull(name: String, vararg params: Class<*>): Method? =
        try {
            getDeclaredMethod(name, *params).also { it.isAccessible = true }
        } catch (_: NoSuchMethodException) {
            null
        }

    /**
     * Gets a spoof value from the package-local config snapshot for a given type.
     *
     * @param type The SpoofType being spoofed
     * @param fallback Generator called if no value is configured in prefs
     */
    protected fun getSpoofValue(
        snapshot: HookConfigSnapshot,
        type: SpoofType,
        fallback: () -> String,
    ): String = snapshot.getValue(type) ?: fallback()

    protected fun getConfiguredSpoofValue(snapshot: HookConfigSnapshot, type: SpoofType): String? =
        snapshot.getValue(type)

    protected fun getConfiguredDeviceProfilePreset(
        snapshot: HookConfigSnapshot
    ): DeviceProfilePreset? = snapshot.getDeviceProfilePreset()

    protected fun isSpoofTypeEnabled(snapshot: HookConfigSnapshot, type: SpoofType): Boolean =
        snapshot.isEnabled(type)

    /** Records a successful spoof event in hook-side metrics and LSPosed/logcat. */
    protected fun reportSpoofEvent(pkg: String, type: SpoofType) {
        runCatching { XposedEntry.instance.reportSpoofEvent(pkg, type.name) }
    }
}
