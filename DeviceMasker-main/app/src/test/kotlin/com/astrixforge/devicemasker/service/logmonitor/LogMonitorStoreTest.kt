package com.astrixforge.devicemasker.service.logmonitor

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class LogMonitorStoreTest {
    @get:Rule val temp = TemporaryFolder()

    @Test
    fun `store keeps newest bounded rows`() {
        val store = LogMonitorStore(File(temp.root, "monitor.jsonl"), maxRows = 2)

        store.appendRawLine("05-18 10:00:00.000  1  1 D DeviceMasker: first")
        store.appendRawLine("05-18 10:00:01.000  1  1 I DeviceMasker: second")
        store.appendRawLine("05-18 10:00:02.000  1  1 W DeviceMasker: third")

        assertEquals(listOf("second", "third"), store.rows.value.map { it.message })
    }

    @Test
    fun `store filters source level and query`() {
        val store = LogMonitorStore(File(temp.root, "monitor.jsonl"), maxRows = 10)

        store.appendRawLine("05-18 10:00:00.000  1  1 I DeviceMasker: app line")
        store.appendRawLine("05-18 10:00:01.000  1  1 E AndroidRuntime: FATAL EXCEPTION")
        store.appendRawLine(
            "05-18 10:00:02.000  1  1 I DeviceMasker: All hooks registered for: app"
        )

        val rows =
            store.visibleRows(
                LogMonitorFilter(
                    source = LogMonitorSource.XPOSED,
                    minLevel = LogMonitorLevel.INFO,
                    query = "hooks",
                )
            )

        assertEquals(1, rows.size)
        assertTrue(rows.single().message.contains("All hooks registered"))
    }
}
