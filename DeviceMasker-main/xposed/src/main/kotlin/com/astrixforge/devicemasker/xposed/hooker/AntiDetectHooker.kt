package com.astrixforge.devicemasker.xposed.hooker

import android.util.Log
import com.astrixforge.devicemasker.common.diagnostics.DiagnosticEventType
import com.astrixforge.devicemasker.xposed.DualLog
import com.astrixforge.devicemasker.xposed.diagnostics.XposedDiagnosticEventSink
import com.astrixforge.devicemasker.xposed.hooker.callback.stableHooker
import io.github.libxposed.api.XposedInterface
import io.github.libxposed.api.XposedInterface.ExceptionMode
import io.github.libxposed.api.error.XposedFrameworkError

/**
 * Anti-Detection Hooker — hides Xposed/LSPosed presence from apps.
 *
 * ⚠️ CRITICAL: This hooker MUST be loaded FIRST in [XposedEntry.onPackageReady]. If it loads after
 * spoof hooks, there is a brief window where detection is possible.
 *
 * ## Detection vectors covered
 * 1. Stack trace analysis — filters XposedBridge/LSPosed/YukiHookAPI frames from Thread/Throwable
 * 2. ClassLoader.loadClass() — prevents detection via direct classloader queries
 * 3. PackageManager — hides LSPosed Manager, Magisk, VirtualXposed from PM queries
 *
 * ## API 101 changes
 * - Extends nothing (no YukiBaseHooker)
 * - Static hook(cl, xi, prefs, pkg) factory
 * - Lambda-based interception via `xi.hook(m).intercept(stableHooker { chain -> ... })`
 * - throwToApp() replaced by throwing directly from intercept hook
 */
object AntiDetectHooker {

    private const val TAG = "AntiDetectHooker"

    // Class/library/package name fragments to suppress in detection vectors
    private val HIDDEN_CLASS_PATTERNS =
        arrayOf(
            "de.robv.android.xposed",
            "XposedBridge",
            "XposedHelpers",
            "XC_MethodHook",
            "io.github.lsposed",
            "LSPHooker",
            "LSPosed",
            "com.highcapable.yukihookapi",
            "YukiHookAPI",
            "YukiBaseHooker",
            "EdHooker",
            "EdXposed",
            "com.elderdrivers.riru",
            "rikka.ndk",
            "org.lsposed.lspd",
            "LSPosedService",
            "io.github.libxposed", // Hide libxposed itself too
        )

    private val SAFE_CLASS_PREFIXES =
        arrayOf(
            "android.",
            "androidx.",
            "com.android.",
            "com.google.",
            "dalvik.",
            "java.",
            "javax.",
            "kotlin.",
            "kotlinx.",
            "org.jetbrains.",
        )

    private val HIDDEN_PACKAGES =
        setOf(
            "de.robv.android.xposed.installer",
            "org.lsposed.manager",
            "io.github.lsposed.manager",
            "com.topjohnwu.magisk",
            "me.weishu.exp",
            "org.meowcat.edxposed.manager",
        )

    private val classLookupHookActive = ThreadLocal<Boolean>()

    fun hook(cl: ClassLoader, xi: XposedInterface, pkg: String) {
        DualLog.debug(TAG, "Loading anti-detection hooks for: $pkg")

        hookStackTraces(cl, xi)
        DualLog.debug(TAG, "Class lookup hiding disabled for target startup safety")
        XposedDiagnosticEventSink.log(
            Log.INFO,
            TAG,
            "Class lookup hiding skipped for target startup safety",
            eventType = DiagnosticEventType.HOOK_SKIPPED,
        )
        AntiDetectPackageManagerHooks.hook(cl, xi, HIDDEN_PACKAGES)

        DualLog.debug(TAG, "Anti-detection hooks registered for: $pkg")
    }

    // ─────────────────────────────────────────────────────────────
    // Stack trace hooks (Thread + Throwable)
    // ─────────────────────────────────────────────────────────────

    private fun hookStackTraces(cl: ClassLoader, xi: XposedInterface) {
        // Thread.getStackTrace() — reentrant guard
        try {
            val threadClass = cl.loadClass("java.lang.Thread")
            threadClass
                .getDeclaredMethod("getStackTrace")
                .also { it.isAccessible = true }
                .let { m ->
                    xi.hook(m)
                        .intercept(
                            stableHooker { chain ->
                                val result = chain.proceed()
                                @Suppress("UNCHECKED_CAST")
                                val stack =
                                    result as? Array<StackTraceElement>
                                        ?: return@stableHooker result
                                filterStackTrace(stack)
                            }
                        )
                    xi.deoptimize(m)
                }
        } catch (e: XposedFrameworkError) {
            throw e
        } catch (t: Throwable) {
            DualLog.warn(TAG, "Thread.getStackTrace() hook failed", t)
        }
        // Throwable.getStackTrace() — reentrant guard
        try {
            val throwableClass = cl.loadClass("java.lang.Throwable")
            throwableClass
                .getDeclaredMethod("getStackTrace")
                .also { it.isAccessible = true }
                .let { m ->
                    xi.hook(m)
                        .intercept(
                            stableHooker { chain ->
                                val result = chain.proceed()
                                @Suppress("UNCHECKED_CAST")
                                val stack =
                                    result as? Array<StackTraceElement>
                                        ?: return@stableHooker result
                                filterStackTrace(stack)
                            }
                        )
                    xi.deoptimize(m)
                }
        } catch (e: XposedFrameworkError) {
            throw e
        } catch (t: Throwable) {
            DualLog.warn(TAG, "Throwable.getStackTrace() hook failed", t)
        }
    }

    // ─────────────────────────────────────────────────────────────
    // ClassLoader.loadClass() hook — prevents xposed class enumeration
    // ─────────────────────────────────────────────────────────────

    private fun hookClassLoaderLoadClass(xi: XposedInterface) {
        try {
            val classLoaderClass = ClassLoader::class.java
            classLoaderClass.declaredMethods
                .filter {
                    it.name == "loadClass" &&
                        (it.parameterTypes.contentEquals(arrayOf(String::class.java)) ||
                            it.parameterTypes.contentEquals(
                                arrayOf(String::class.java, Boolean::class.javaPrimitiveType!!)
                            ))
                }
                .forEach { method ->
                    method.isAccessible = true
                    xi.hook(method)
                        .setExceptionMode(ExceptionMode.PASSTHROUGH)
                        .intercept(
                            stableHooker { chain ->
                                if (classLookupHookActive.get() == true)
                                    return@stableHooker chain.proceed()
                                classLookupHookActive.set(true)
                                try {
                                    val className = chain.args.firstOrNull() as? String
                                    if (className != null && shouldHideClass(className)) {
                                        throw ClassNotFoundException(className)
                                    }
                                    chain.proceed()
                                } finally {
                                    classLookupHookActive.set(false)
                                }
                            }
                        )
                    xi.deoptimize(method)
                }
        } catch (e: XposedFrameworkError) {
            throw e
        } catch (t: Throwable) {
            DualLog.warn(TAG, "ClassLoader.loadClass() hook failed", t)
        }
    }

    private fun hookClassForName(xi: XposedInterface) {
        try {
            val classClass = Class::class.java
            classClass.declaredMethods
                .filter {
                    it.name == "forName" &&
                        (it.parameterTypes.contentEquals(arrayOf(String::class.java)) ||
                            it.parameterTypes.contentEquals(
                                arrayOf(
                                    String::class.java,
                                    Boolean::class.javaPrimitiveType!!,
                                    ClassLoader::class.java,
                                )
                            ))
                }
                .forEach { method ->
                    method.isAccessible = true
                    xi.hook(method)
                        .setExceptionMode(ExceptionMode.PASSTHROUGH)
                        .intercept(
                            stableHooker { chain ->
                                if (classLookupHookActive.get() == true)
                                    return@stableHooker chain.proceed()
                                classLookupHookActive.set(true)
                                try {
                                    val className = chain.args.firstOrNull() as? String
                                    if (className != null && shouldHideClass(className)) {
                                        throw ClassNotFoundException(className)
                                    }
                                    chain.proceed()
                                } finally {
                                    classLookupHookActive.set(false)
                                }
                            }
                        )
                    xi.deoptimize(method)
                }
        } catch (e: XposedFrameworkError) {
            throw e
        } catch (t: Throwable) {
            DualLog.warn(TAG, "Class.forName() hook failed", t)
        }
    }

    // ─────────────────────────────────────────────────────────────
    // Shared state
    // ─────────────────────────────────────────────────────────────

    private val filteringFlag = ThreadLocal<Boolean>()

    internal fun filterStackTrace(stack: Array<StackTraceElement>): Array<StackTraceElement> {
        if (filteringFlag.get() == true) return stack
        filteringFlag.set(true)
        return try {
            var hiddenCount = 0
            for (frame in stack) {
                if (shouldHideStackFrame(frame.className)) hiddenCount++
            }
            if (hiddenCount == 0) return stack

            val filtered = arrayOfNulls<StackTraceElement>(stack.size - hiddenCount)
            var writeIndex = 0
            for (frame in stack) {
                if (!shouldHideStackFrame(frame.className)) {
                    filtered[writeIndex] = frame
                    writeIndex++
                }
            }
            @Suppress("UNCHECKED_CAST")
            filtered as Array<StackTraceElement>
        } finally {
            filteringFlag.set(false)
        }
    }

    private fun shouldHideStackFrame(className: String): Boolean {
        if (className.startsWith("com.astrixforge.devicemasker")) return false
        if (
            className.startsWith("android.") ||
                className.startsWith("java.") ||
                className.startsWith("kotlin.") ||
                className.startsWith("androidx.")
        )
            return false
        return shouldMatchHiddenPattern(className)
    }

    private fun shouldHideClass(className: String): Boolean {
        for (prefix in SAFE_CLASS_PREFIXES) {
            if (className.startsWith(prefix)) return false
        }
        for (pattern in HIDDEN_CLASS_PATTERNS) {
            if (containsIgnoreCase(className, pattern)) return true
        }
        return false
    }

    private fun shouldMatchHiddenPattern(value: String): Boolean {
        for (pattern in HIDDEN_CLASS_PATTERNS) {
            if (containsIgnoreCase(value, pattern)) return true
        }
        return false
    }

    internal fun shouldHideClassForTest(className: String): Boolean = shouldHideClass(className)

    private fun containsIgnoreCase(value: String, pattern: String): Boolean {
        val lastStart = value.length - pattern.length
        if (lastStart < 0) return false
        var index = 0
        while (index <= lastStart) {
            if (value.regionMatches(index, pattern, 0, pattern.length, ignoreCase = true)) {
                return true
            }
            index++
        }
        return false
    }
}
