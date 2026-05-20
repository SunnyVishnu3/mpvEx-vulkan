package com.astrixforge.devicemasker.service

import com.astrixforge.devicemasker.common.diagnostics.DiagnosticEventType
import java.io.File
import kotlinx.coroutines.test.StandardTestDispatcher
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import timber.log.Timber

class AppLogStoreTest {

    @get:Rule val temp = TemporaryFolder()

    @Test
    fun `app log store persists sanitized entries`() {
        val store = AppLogStore(File(temp.root, "structured.log"), maxEntries = 10)

        store.append(
            AppLogEntry(
                timestampMillis = 1_700_000_000_000,
                level = "I",
                source = "app",
                tag = "Config\tManager",
                message = "Loaded\nconfig",
            )
        )

        val entries = store.readEntries()

        assertEquals(1, entries.size)
        assertEquals("Config Manager", entries.single().tag)
        assertEquals("Loaded config", entries.single().message)
        assertEquals(DiagnosticEventType.APP_LOG, store.readDiagnosticEvents().single().eventType)
    }

    @Test
    fun `app log store trims oldest entries`() {
        val store = AppLogStore(File(temp.root, "structured.log"), maxEntries = 2)

        store.append(AppLogEntry(1, "D", "app", "One", "first"))
        store.append(AppLogEntry(2, "D", "app", "Two", "second"))
        store.append(AppLogEntry(3, "D", "app", "Three", "third"))

        val entries = store.readEntries()

        assertEquals(listOf("second", "third"), entries.map { it.message })
    }

    @Test
    fun `persistent app log tree writes structured exception events`() {
        val store = AppLogStore(File(temp.root, "structured.log"), maxEntries = 10)
        val tree = PersistentAppLogTree(store)

        Timber.uprootAll()
        Timber.plant(tree)
        Timber.tag("ConfigManager")
            .e(IllegalStateException("bad state"), "Config failed for 490154203237518")
        Timber.uprootAll()

        val event = store.readDiagnosticEvents().single()
        assertEquals(DiagnosticEventType.APP_LOG, event.eventType)
        assertEquals("IllegalStateException", event.throwableClass)
        assertTrue(event.stacktrace.any { it.contains("IllegalStateException") })
        assertFalse(event.message.contains("490154203237518"))
        assertTrue(event.message.contains("[REDACTED_IMEI]"))
    }

    @Test
    fun `log file formatter emits clean minimal file with real entries`() {
        val content =
            LogFileFormatter.build(
                appEntries =
                    listOf(
                        AppLogEntry(
                            timestampMillis = 1_700_000_000_000,
                            level = "I",
                            source = "app",
                            tag = "ConfigManager",
                            message = "Config loaded",
                        )
                    ),
                serviceLogs = listOf("[13:40:04.441] I/DMService: Hooks registered for app"),
                diagnosticsStatus = "connected",
                exportedAtMillis = 1_700_000_100_000,
            )

        assertTrue(content.contains("Device Masker Logs"))
        assertTrue(content.contains("diagnostics=connected"))
        assertTrue(content.contains("app_entries=1"))
        assertTrue(content.contains("service_entries=1"))
        assertTrue(content.contains("ConfigManager Config loaded"))
        assertTrue(content.contains("[xposed]"))
        assertFalse(content.contains("════════"))
        assertFalse(content.contains("Possible reasons:"))
    }

    @Test
    fun `app log store tracks dropped queue events`() {
        val store =
            AppLogStore(
                file = File(temp.root, "structured.log"),
                maxEntries = 10,
                queueCapacity = 0,
                dispatcher = StandardTestDispatcher(),
            )

        store.append(AppLogEntry(1, "I", "app", "ConfigManager", "queued"))

        assertEquals(1, store.queueDroppedEventCount())
    }
}
