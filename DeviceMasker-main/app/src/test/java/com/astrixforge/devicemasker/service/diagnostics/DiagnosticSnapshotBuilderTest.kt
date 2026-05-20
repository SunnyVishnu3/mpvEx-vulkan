package com.astrixforge.devicemasker.service.diagnostics

import com.astrixforge.devicemasker.common.diagnostics.RedactionMode
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DiagnosticSnapshotBuilderTest {
    @Test
    fun `redacted snapshots omit raw identifiers`() {
        val builder =
            DiagnosticSnapshotBuilder(
                metadata =
                    DiagnosticSnapshotMetadata(
                        appVersion = "1.0.0",
                        buildType = "debug",
                        androidSdk = 36,
                        androidRelease = "16",
                        device = "Google Pixel",
                        rootAvailable = true,
                        xposedFrameworkConnected = true,
                        moduleEnabled = true,
                        targetPackage = "com.bank.example",
                        scopePackages = listOf("android", "system", "com.bank.example"),
                        droppedLogCount = 2,
                    ),
                configJson = """{"imei":"490154203237518","package":"com.bank.example"}""",
                remotePrefs = mapOf("android_id" to "a1b2c3d4e5f60789"),
                hookHealthJson = """{"IMEI":1}""",
            )

        val snapshots = builder.build(RedactionMode.REDACTED)
        val combined = snapshots.values.joinToString("\n")

        assertTrue(
            snapshots.keys.containsAll(
                listOf(
                    "summary.json",
                    "config_snapshot_redacted.json",
                    "remote_prefs_snapshot_redacted.json",
                    "scope_snapshot.json",
                    "hook_health.json",
                )
            )
        )
        assertFalse(combined.contains("490154203237518"))
        assertFalse(combined.contains("a1b2c3d4e5f60789"))
        assertFalse(combined.contains("com.bank.example"))
        assertTrue(combined.contains("[REDACTED_IMEI]"))
        assertTrue(combined.contains("[REDACTED_ANDROID_ID]"))
        assertTrue(combined.contains("[PKG:db02af9d]"))
    }
}
