package com.astrixforge.devicemasker.common.diagnostics

import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import org.junit.Test

class DiagnosticRedactorTest {
    @Test
    fun `redacts common device identifiers`() {
        val redactor = DiagnosticRedactor(RedactionMode.REDACTED)

        assertEquals("[REDACTED_IMEI]", redactor.redactValue("490154203237518"))
        assertEquals("[REDACTED_IMSI]", redactor.redactValue("310260123456789"))
        assertEquals("[REDACTED_ICCID]", redactor.redactValue("89014103211118510720"))
        assertEquals("[REDACTED_MAC]", redactor.redactValue("02:00:00:12:34:56"))
        assertEquals("[REDACTED_ANDROID_ID]", redactor.redactValue("a1b2c3d4e5f60789"))
        assertEquals("[REDACTED_PHONE]", redactor.redactValue("+14155552671"))
        assertEquals("[REDACTED_LOCATION]", redactor.redactValue("37.4219983,-122.084"))
    }

    @Test
    fun `redacts sensitive values inside messages`() {
        val redactor = DiagnosticRedactor(RedactionMode.REDACTED)

        val redacted =
            redactor.redactMessage(
                "imei=490154203237518 mac=02:00:00:12:34:56 androidId=a1b2c3d4e5f60789"
            )

        assertFalse(redacted.contains("490154203237518"))
        assertFalse(redacted.contains("02:00:00:12:34:56"))
        assertFalse(redacted.contains("a1b2c3d4e5f60789"))
        assertTrue(redacted.contains("[REDACTED_IMEI]"))
        assertTrue(redacted.contains("[REDACTED_MAC]"))
        assertTrue(redacted.contains("[REDACTED_ANDROID_ID]"))
    }

    @Test
    fun `hashes packages in redacted mode and preserves packages in unredacted mode`() {
        val redactor = DiagnosticRedactor(RedactionMode.REDACTED)
        val unredacted = DiagnosticRedactor(RedactionMode.UNREDACTED)

        assertEquals("[PKG:db02af9d]", redactor.redactPackage("com.bank.example"))
        assertEquals("com.bank.example", unredacted.redactPackage("com.bank.example"))
    }

    @Test
    fun `redacts event package message stacktrace and extras`() {
        val redactor = DiagnosticRedactor(RedactionMode.REDACTED)
        val event =
            DiagnosticEvent(
                eventId = "evt_1700000000000_000010",
                timestampWallMillis = 1_700_000_000_000,
                timestampElapsedMillis = 10,
                sessionId = "session-1",
                bootId = "boot-1",
                source = DiagnosticSource.APP,
                eventType = DiagnosticEventType.APP_LOG,
                packageName = "com.bank.example",
                processName = "com.bank.example",
                message = "Failed for 490154203237518",
                stacktrace = listOf("at com.bank.example.Main.fail(Main.kt:1): 02:00:00:12:34:56"),
                extras = mapOf("android_id" to "a1b2c3d4e5f60789"),
            )

        val redacted = redactor.redactEvent(event)

        assertEquals("[PKG:db02af9d]", redacted.packageName)
        assertEquals("[PKG:db02af9d]", redacted.processName)
        assertFalse(redacted.message.contains("490154203237518"))
        assertFalse(redacted.stacktrace.single().contains("02:00:00:12:34:56"))
        assertEquals("[REDACTED_ANDROID_ID]", redacted.extras.getValue("android_id"))
    }
}
