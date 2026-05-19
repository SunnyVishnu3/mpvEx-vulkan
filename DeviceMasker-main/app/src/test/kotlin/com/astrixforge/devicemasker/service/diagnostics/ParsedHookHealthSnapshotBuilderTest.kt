package com.astrixforge.devicemasker.service.diagnostics

import com.astrixforge.devicemasker.common.diagnostics.DiagnosticEvent
import com.astrixforge.devicemasker.common.diagnostics.DiagnosticEventType
import com.astrixforge.devicemasker.common.diagnostics.DiagnosticJson
import com.astrixforge.devicemasker.common.diagnostics.DiagnosticSeverity
import com.astrixforge.devicemasker.common.diagnostics.DiagnosticSource
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Test

class ParsedHookHealthSnapshotBuilderTest {
    @Test
    fun `buildJson summarizes parsed xposed events`() {
        val events =
            listOf(
                event(DiagnosticEventType.XPOSED_ENTRY_LOADED),
                event(DiagnosticEventType.TARGET_PACKAGE_SELECTED),
                event(DiagnosticEventType.HOOK_REGISTERED),
                event(DiagnosticEventType.HOOK_FAILED, DiagnosticSeverity.ERROR),
                event(DiagnosticEventType.SPOOF_RETURNED),
                event(DiagnosticEventType.SPOOF_RETURNED),
            )

        val snapshot =
            DiagnosticJson.parseToJsonElement(ParsedHookHealthSnapshotBuilder.buildJson(events))
                as JsonObject

        assertEquals(6, snapshot.getValue("totalEvents").jsonPrimitive.int)
        assertEquals(1, snapshot.getValue("xposedEntryLoaded").jsonPrimitive.int)
        assertEquals(1, snapshot.getValue("targetPackageSelected").jsonPrimitive.int)
        assertEquals(1, snapshot.getValue("hookRegistered").jsonPrimitive.int)
        assertEquals(1, snapshot.getValue("hookFailed").jsonPrimitive.int)
        assertEquals(2, snapshot.getValue("spoofReturned").jsonPrimitive.int)
        assertEquals(1, snapshot.getValue("errorEvents").jsonPrimitive.int)
        assertEquals("parsed_xposed_export", snapshot.getValue("source").jsonPrimitive.content)
    }

    private fun event(
        eventType: DiagnosticEventType,
        severity: DiagnosticSeverity = DiagnosticSeverity.INFO,
    ): DiagnosticEvent =
        DiagnosticEvent(
            eventId = DiagnosticEvent.nextEventId(timestampWallMillis = 1_779_078_112_030),
            timestampWallMillis = 1_779_078_112_030,
            timestampElapsedMillis = 1,
            sessionId = "test",
            bootId = "unknown",
            source = DiagnosticSource.XPOSED,
            severity = severity,
            eventType = eventType,
            message = eventType.name,
        )
}
