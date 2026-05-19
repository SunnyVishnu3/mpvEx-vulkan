package com.astrixforge.devicemasker.xposed

import android.util.Log
import com.astrixforge.devicemasker.common.diagnostics.DiagnosticEventType
import com.astrixforge.devicemasker.xposed.diagnostics.XposedDiagnosticEventSink

/**
 * Dual Logger — logs to Android logcat and mirrors structured entries to the module logger.
 *
 * ## Migration note (libxposed API 101)
 *
 * Previously used `YLog` from YukiHookAPI which routed to LSPosed Manager's log screen. With
 * libxposed API 101, we route to the standard `android.util.Log` instead. LSPosed captures module
 * process logcat output and displays it in its log screen, so all logs remain visible in LSPosed
 * Manager as before.
 *
 * Structured logs are mirrored to the module logger so LSPosed/logcat remains the authoritative
 * target-process evidence source.
 *
 * Usage:
 * ```kotlin
 * DualLog.debug("MyHooker", "Hook applied successfully")
 * DualLog.info("MyHooker", "Started hooks for: com.example.app")
 * DualLog.error("MyHooker", "Failed to hook", exception)
 * ```
 */
@Suppress("unused") // Logging utility — all methods are API surface
object DualLog {

    // ═══════════════════════════════════════════════════════════
    // LOGGING METHODS
    // ═══════════════════════════════════════════════════════════

    /** Debug level log — for detailed debugging info. */
    fun debug(tag: String, message: String) {
        report(tag, message, Log.DEBUG)
    }

    /** Info level log — for important events. */
    fun info(tag: String, message: String) {
        report(tag, message, Log.INFO)
    }

    /** Warning level log — for recoverable issues. */
    fun warn(tag: String, message: String) {
        report(tag, message, Log.WARN)
    }

    /** Warning level log with exception. */
    fun warn(tag: String, message: String, throwable: Throwable) {
        val fullMessage = "$message: ${throwable.message}"
        report(tag, fullMessage, Log.WARN, throwable)
    }

    /** Error level log — for failures. */
    fun error(tag: String, message: String) {
        report(tag, message, Log.ERROR)
    }

    /** Error level log with exception. */
    fun error(tag: String, message: String, throwable: Throwable) {
        report(tag, "$message: ${throwable.message}", Log.ERROR, throwable)
    }

    // ═══════════════════════════════════════════════════════════
    // PRIVATE HELPERS
    // ═══════════════════════════════════════════════════════════

    private fun report(tag: String, message: String, priority: Int, throwable: Throwable? = null) {
        XposedDiagnosticEventSink.log(
            priority = priority,
            tag = tag,
            message = message,
            throwable = throwable,
            eventType = DiagnosticEventType.DIAGNOSTICS_SERVICE_LOG,
        )
    }
}

/**
 * Hook Metrics — Tracks hook registration success/failure rates.
 *
 * Used by DiagnosticsViewModel to display hook health information.
 */
object HookMetrics {
    fun recordSuccess(hookerName: String, methodName: String) {
        XposedDiagnosticEventSink.hookHealth.recordRegistrationSuccess(hookerName, methodName)
    }

    fun recordFailure(hookerName: String, methodName: String) {
        XposedDiagnosticEventSink.hookHealth.recordRegistrationFailure(
            hookerName,
            methodName,
            "unknown",
        )
    }

    fun getSuccessCount(): Int =
        XposedDiagnosticEventSink.hookHealth.snapshot().registrationSuccesses.toInt()

    fun getFailureCount(): Int =
        XposedDiagnosticEventSink.hookHealth.snapshot().registrationFailures.toInt()

    fun getSummary(): String = buildString {
        val snapshot = XposedDiagnosticEventSink.hookHealth.snapshot()
        appendLine("=== Hook Metrics ===")
        appendLine("Success: ${snapshot.registrationSuccesses} calls")
        appendLine("Failures: ${snapshot.registrationFailures} calls")
        val failures = snapshot.methods.filterValues { it.failures > 0 }
        if (failures.isNotEmpty()) {
            appendLine("Failed methods:")
            failures.forEach { (method, health) -> appendLine("  - $method: ${health.failures}") }
        }
    }

    fun dumpToLog() {
        DualLog.info("HookMetrics", getSummary())
    }
}
