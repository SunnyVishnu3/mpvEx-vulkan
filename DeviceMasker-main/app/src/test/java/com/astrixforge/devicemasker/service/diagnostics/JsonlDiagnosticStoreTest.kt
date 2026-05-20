package com.astrixforge.devicemasker.service.diagnostics

import com.astrixforge.devicemasker.common.diagnostics.DiagnosticEvent
import com.astrixforge.devicemasker.common.diagnostics.DiagnosticEventType
import com.astrixforge.devicemasker.common.diagnostics.DiagnosticSource
import java.io.File
import kotlin.io.path.createTempDirectory
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class JsonlDiagnosticStoreTest {
    @Test
    fun `appending writes one diagnostic event per json line`() {
        val sessionDir = tempSessionDir()
        val store = JsonlDiagnosticStore(sessionDir = sessionDir, maxFileBytes = 4096)

        store.append(event("one"))
        store.append(event("two"))

        val lines = File(sessionDir, "app_events_000.jsonl").readLines()
        assertEquals(2, lines.size)
        assertEquals(listOf("one", "two"), store.readEvents().map { it.message })
    }

    @Test
    fun `corrupted lines are skipped and counted`() {
        val sessionDir = tempSessionDir()
        val store = JsonlDiagnosticStore(sessionDir = sessionDir, maxFileBytes = 4096)
        store.append(event("valid"))
        File(sessionDir, "app_events_000.jsonl").appendText("not-json\n")

        assertEquals(listOf("valid"), store.readEvents().map { it.message })
        assertEquals(1, store.stats().corruptedLines)
    }

    @Test
    fun `max file size triggers rotation`() {
        val sessionDir = tempSessionDir()
        val store = JsonlDiagnosticStore(sessionDir = sessionDir, maxFileBytes = 650)

        repeat(4) { store.append(event("message-$it".padEnd(80, 'x'))) }

        assertTrue(File(sessionDir, "app_events_000.jsonl").exists())
        assertTrue(File(sessionDir, "app_events_001.jsonl").exists())
        assertEquals(4, store.readEvents().size)
    }

    @Test
    fun `max sessions deletes oldest session folders`() {
        val root = createTempDirectory("dm-sessions").toFile()
        val ids = ArrayDeque((1..12).map { "session-$it" })
        val manager =
            DiagnosticSessionManager(
                filesDir = root,
                sessionIdProvider = { ids.removeFirst() },
                bootIdProvider = { "boot-1" },
                maxSessions = 10,
            )

        repeat(12) { manager.startSession() }

        val sessionNames =
            File(root, "logs/sessions").listFiles().orEmpty().map { it.name }.sorted()
        assertEquals(10, sessionNames.size)
        assertFalse("session_session-1" in sessionNames)
        assertFalse("session_session-2" in sessionNames)
        assertTrue("session_session-12" in sessionNames)
    }

    @Test
    fun `dropped event counts are tracked`() {
        val sessionDir = tempSessionDir()
        val store = JsonlDiagnosticStore(sessionDir = sessionDir, maxFileBytes = 128)

        store.append(event("too-large".padEnd(1000, 'x')))

        val stats = store.stats()
        assertEquals(1, stats.droppedEvents)
        assertEquals(0, stats.totalEvents)
    }

    private fun tempSessionDir(): File {
        val dir = createTempDirectory("dm-jsonl").toFile()
        return File(dir, "logs/sessions/session_test").also { it.mkdirs() }
    }

    private fun event(message: String): DiagnosticEvent =
        DiagnosticEvent(
            eventId = DiagnosticEvent.nextEventId(1_700_000_000_000),
            timestampWallMillis = 1_700_000_000_000,
            timestampElapsedMillis = 1,
            sessionId = "session-test",
            bootId = "boot-test",
            source = DiagnosticSource.APP,
            eventType = DiagnosticEventType.APP_LOG,
            message = message,
        )
}
