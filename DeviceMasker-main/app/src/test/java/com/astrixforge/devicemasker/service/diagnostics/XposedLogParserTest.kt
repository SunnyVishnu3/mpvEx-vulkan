package com.astrixforge.devicemasker.service.diagnostics

import com.astrixforge.devicemasker.common.diagnostics.DiagnosticEventType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class XposedLogParserTest {
    @Test
    fun `parser extracts xposed lifecycle and spoof events`() {
        val events =
            XposedLogParser.parseLines(
                listOf(
                    "05-18 10:00:00.000  1  1 I DeviceMasker: XposedEntry loaded for process: com.mantle.verify",
                    "05-18 10:00:01.000  1  1 I DeviceMasker: Target package selected: com.mantle.verify",
                    "05-18 10:00:02.000  1  1 I DeviceMasker: All hooks registered for: com.mantle.verify",
                    "05-18 10:00:03.000  1  1 I DeviceMasker: Spoof event: ANDROID_ID",
                ),
                sessionId = "test",
            )

        assertEquals(4, events.size)
        assertEquals(
            listOf(
                DiagnosticEventType.XPOSED_ENTRY_LOADED,
                DiagnosticEventType.TARGET_PACKAGE_SELECTED,
                DiagnosticEventType.HOOK_REGISTERED,
                DiagnosticEventType.SPOOF_RETURNED,
            ),
            events.map { it.eventType },
        )
        assertTrue(events.all { it.extras.containsKey("rawLine") })
    }
}
