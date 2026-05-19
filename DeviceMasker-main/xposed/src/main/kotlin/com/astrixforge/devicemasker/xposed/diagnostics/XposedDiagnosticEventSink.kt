package com.astrixforge.devicemasker.xposed.diagnostics

import android.util.Log
import com.astrixforge.devicemasker.common.diagnostics.DiagnosticEvent
import com.astrixforge.devicemasker.common.diagnostics.DiagnosticEventType
import com.astrixforge.devicemasker.common.diagnostics.DiagnosticJson
import com.astrixforge.devicemasker.common.diagnostics.DiagnosticSeverity
import com.astrixforge.devicemasker.common.diagnostics.DiagnosticSource
import com.astrixforge.devicemasker.xposed.XposedEntry

object XposedDiagnosticEventSink {
    val hookHealth: HookHealthRegistry = HookHealthRegistry()

    fun log(
        priority: Int,
        tag: String,
        message: String,
        throwable: Throwable? = null,
        eventType: DiagnosticEventType = DiagnosticEventType.DIAGNOSTICS_SERVICE_LOG,
    ) {
        runCatching {
            when (priority) {
                Log.ERROR -> Log.e(tag, message, throwable)
                Log.WARN -> Log.w(tag, message, throwable)
                Log.INFO -> Log.i(tag, message)
                Log.VERBOSE -> Log.v(tag, message)
                else -> Log.d(tag, message)
            }
        }

        val event = buildEvent(priority, tag, message, throwable, eventType)
        val encoded =
            runCatching { DiagnosticJson.encodeToString(DiagnosticEvent.serializer(), event) }
                .getOrElse { message }

        runCatching { XposedEntry.instance.log(priority, tag, message, throwable) }
        runCatching { XposedEntry.instance.reportLog(tag, encoded, priority) }
    }

    private fun buildEvent(
        priority: Int,
        tag: String,
        message: String,
        throwable: Throwable?,
        eventType: DiagnosticEventType,
    ): DiagnosticEvent {
        val now = System.currentTimeMillis()
        return DiagnosticEvent(
            eventId = DiagnosticEvent.nextEventId(now),
            timestampWallMillis = now,
            timestampElapsedMillis = System.nanoTime() / 1_000_000,
            sessionId = "xposed",
            bootId = "unknown",
            source = DiagnosticSource.XPOSED,
            severity = priority.toSeverity(),
            eventType = eventType,
            threadName = Thread.currentThread().name,
            hooker = tag,
            message = message,
            throwableClass = throwable?.javaClass?.simpleName,
            stacktrace = throwable?.stackTraceToString()?.lines().orEmpty(),
        )
    }

    private fun Int.toSeverity(): DiagnosticSeverity =
        when (this) {
            Log.ERROR -> DiagnosticSeverity.ERROR
            Log.WARN -> DiagnosticSeverity.WARN
            Log.INFO -> DiagnosticSeverity.INFO
            Log.VERBOSE -> DiagnosticSeverity.VERBOSE
            else -> DiagnosticSeverity.DEBUG
        }
}
