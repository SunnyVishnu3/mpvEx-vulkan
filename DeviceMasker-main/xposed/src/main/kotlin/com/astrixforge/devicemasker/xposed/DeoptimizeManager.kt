package com.astrixforge.devicemasker.xposed

import android.util.Log
import io.github.libxposed.api.XposedInterface
import io.github.libxposed.api.error.XposedFrameworkError
import java.lang.reflect.Method

/**
 * Centralized ART deoptimization manager for libxposed API 101.
 *
 * ## Why deoptimize() is critical
 *
 * ART's JIT (Just-In-Time) and AOT (Ahead-Of-Time) compilers aggressively inline short methods. Any
 * method shorter than ~20 bytecodes — such as `TelephonyManager.getImei()` or `Build.getSerial()` —
 * may be inlined directly into the caller's compiled machine code. When that happens, your Xposed
 * hook on the method object is never invoked; the app reads the real value and our hook is silently
 * skipped.
 *
 * `xi.deoptimize(method)` tells ART: "do not optimize away calls to this method — always dispatch
 * through the method table." This forces ART to recompile the method in interpreter mode (or
 * de-optimized compiled mode), ensuring every call goes through our hook callback.
 *
 * ## Usage rule
 * - ALWAYS call deoptimize() AFTER xi.hook() — deoptimizing before hooking is a no-op.
 * - It is idempotent — calling it multiple times on the same method is safe.
 * - Cost is one-time at module load time (~10-50ms total for all methods). After that, all
 *   subsequent calls route through hooks with no additional overhead.
 *
 * ## When to deoptimize
 * - All TelephonyManager getters (getImei, getSubscriberId, etc.) — always JIT-compiled
 * - All Build.* static field accessors — inlined by startup code
 * - SubscriptionInfo getters — heavily used by banking apps
 * - WebView UA / UserAgent strings — read at WebView init time (early, often AOT)
 */
object DeoptimizeManager {

    private const val TAG = "DeoptimizeManager"

    /**
     * Deoptimizes a primary target method and optionally its known caller methods.
     *
     * Use this when you have prior knowledge of hot caller paths (e.g., from DexParser analysis or
     * known banking SDK patterns). Deoptimizing callers ensures inline-at-callsite cases are also
     * caught.
     *
     * @param xi The XposedInterface instance (from XposedModule or hook callback)
     * @param target The method to deoptimize (must be hooked first)
     * @param callers Optional known caller methods to also deoptimize
     */
    fun deoptimizeWithCallers(
        xi: XposedInterface,
        target: Method,
        callers: List<Method> = emptyList(),
    ) {
        val success =
            try {
                xi.deoptimize(target)
            } catch (e: XposedFrameworkError) {
                throw e
            } catch (_: Throwable) {
                false
            }
        if (!success) {
            Log.w(
                TAG,
                "Failed to deoptimize ${target.declaringClass.simpleName}.${target.name} " +
                    "— hook may be silently skipped on JIT-compiled paths",
            )
        }
        for (caller in callers) {
            try {
                xi.deoptimize(caller)
            } catch (e: XposedFrameworkError) {
                throw e
            } catch (t: Throwable) {
                Log.w(TAG, "Failed to deoptimize caller ${caller.name}: ${t.message}")
            }
        }
    }

    /**
     * Batch-deoptimizes all methods in a list. Useful after hooking an entire class's methods
     * (e.g., all TelephonyManager getters). Each deoptimize is individually guarded — one failure
     * does not prevent others.
     *
     * @param xi The XposedInterface instance
     * @param methods All methods to deoptimize
     */
    fun deoptimizeAll(xi: XposedInterface, methods: List<Method>) {
        for (method in methods) {
            try {
                xi.deoptimize(method)
            } catch (e: XposedFrameworkError) {
                throw e
            } catch (t: Throwable) {
                Log.w(
                    TAG,
                    "Failed to deoptimize ${method.declaringClass.simpleName}.${method.name}: ${t.message}",
                )
            }
        }
    }
}
