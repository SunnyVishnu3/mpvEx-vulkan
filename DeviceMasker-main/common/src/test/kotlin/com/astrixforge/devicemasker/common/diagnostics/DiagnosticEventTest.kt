package com.astrixforge.devicemasker.common.diagnostics

import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue
import org.junit.Test

class DiagnosticEventTest {
    @Test
    fun `event serializes with stable event code and context`() {
        val event =
            DiagnosticEvent(
                eventId = "evt_1700000000000_000001",
                timestampWallMillis = 1_700_000_000_000,
                timestampElapsedMillis = 42,
                sessionId = "session-1",
                bootId = "boot-1",
                source = DiagnosticSource.APP,
                severity = DiagnosticSeverity.INFO,
                eventType = DiagnosticEventType.REMOTE_PREFS_SYNC_COMMITTED,
                processName = "com.astrixforge.devicemasker",
                packageName = "com.astrixforge.devicemasker",
                pid = 123,
                tid = 456,
                threadName = "main",
                hooker = null,
                method = null,
                spoofType = null,
                status = "success",
                reason = null,
                configVersion = 100,
                prefsVersion = 100,
                moduleEnabled = true,
                appEnabled = true,
                message = "RemotePreferences sync committed",
                throwableClass = null,
                stacktrace = emptyList(),
                extras = mapOf("changed_keys" to "12"),
            )

        val json = DiagnosticJson.encodeToString(DiagnosticEvent.serializer(), event)
        val decoded = DiagnosticJson.decodeFromString(DiagnosticEvent.serializer(), json)

        assertEquals(event, decoded)
    }

    @Test
    fun `enum names are stable for exported logs`() {
        assertEquals("APP", DiagnosticSource.APP.name)
        assertEquals("XPOSED", DiagnosticSource.XPOSED.name)
        assertEquals("ROOT", DiagnosticSource.ROOT.name)
        assertEquals("INFO", DiagnosticSeverity.INFO.name)
        assertEquals("ERROR", DiagnosticSeverity.ERROR.name)
        assertEquals("APP_START", DiagnosticEventType.APP_START.name)
        assertEquals("HOOK_REGISTERED", DiagnosticEventType.HOOK_REGISTERED.name)
        assertEquals("ROOT_LOGCAT_COLLECTED", DiagnosticEventType.ROOT_LOGCAT_COLLECTED.name)
    }

    @Test
    fun `defaults are safe for optional diagnostic fields`() {
        val event =
            DiagnosticEvent(
                eventId = "evt_1700000000000_000002",
                timestampWallMillis = 1_700_000_000_000,
                timestampElapsedMillis = 100,
                sessionId = "session-1",
                bootId = "boot-1",
                source = DiagnosticSource.APP,
                eventType = DiagnosticEventType.APP_START,
                message = "App started",
            )

        assertEquals(DiagnosticSeverity.INFO, event.severity)
        assertEquals(null, event.packageName)
        assertEquals(null, event.throwableClass)
        assertEquals(emptyList(), event.stacktrace)
        assertEquals(emptyMap(), event.extras)
        assertEquals(false, event.moduleEnabled)
        assertEquals(false, event.appEnabled)
    }

    @Test
    fun `generated event ids are unique and sortable`() {
        val first = DiagnosticEvent.nextEventId(1_700_000_000_000)
        val second = DiagnosticEvent.nextEventId(1_700_000_000_000)

        assertNotEquals(first, second)
        assertTrue(first.matches(Regex("""evt_1700000000000_\d{6}""")))
        assertTrue(second > first)
    }
}
