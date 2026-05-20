package com.astrixforge.devicemasker.service.diagnostics

import com.astrixforge.devicemasker.common.diagnostics.DiagnosticEvent
import com.astrixforge.devicemasker.common.diagnostics.DiagnosticEventType
import com.astrixforge.devicemasker.common.diagnostics.DiagnosticSeverity
import com.astrixforge.devicemasker.common.diagnostics.DiagnosticSource

object XposedLogParser {
    fun parseLines(lines: Sequence<String>, sessionId: String): List<DiagnosticEvent> =
        lines.mapNotNull { line -> parseLine(line, sessionId) }.toList()

    fun parseLines(lines: Iterable<String>, sessionId: String): List<DiagnosticEvent> =
        parseLines(lines.asSequence(), sessionId)

    private fun parseLine(line: String, sessionId: String): DiagnosticEvent? {
        val eventType = line.toEventType() ?: return null
        val now = System.currentTimeMillis()
        return DiagnosticEvent(
            eventId = DiagnosticEvent.nextEventId(now),
            timestampWallMillis = now,
            timestampElapsedMillis = System.nanoTime() / NANOS_PER_MILLI,
            sessionId = sessionId,
            bootId = "unknown",
            source = DiagnosticSource.XPOSED,
            severity = line.toSeverity(),
            eventType = eventType,
            hooker = "DeviceMasker",
            message = line.substringAfter("DeviceMasker:", line).trim(),
            extras = mapOf("rawLine" to line),
        )
    }

    private fun String.toEventType(): DiagnosticEventType? =
        when {
            "XposedEntry loaded" in this -> DiagnosticEventType.XPOSED_ENTRY_LOADED
            "Target package selected:" in this -> DiagnosticEventType.TARGET_PACKAGE_SELECTED
            "All hooks registered for:" in this -> DiagnosticEventType.HOOK_REGISTERED
            "Spoof event:" in this -> DiagnosticEventType.SPOOF_RETURNED
            "Hook registration failed" in this -> DiagnosticEventType.HOOK_FAILED
            else -> null
        }

    private fun String.toSeverity(): DiagnosticSeverity =
        when {
            " F " in this -> DiagnosticSeverity.FATAL
            " E " in this -> DiagnosticSeverity.ERROR
            " W " in this -> DiagnosticSeverity.WARN
            " I " in this -> DiagnosticSeverity.INFO
            " V " in this -> DiagnosticSeverity.VERBOSE
            else -> DiagnosticSeverity.DEBUG
        }

    private const val NANOS_PER_MILLI = 1_000_000
}
