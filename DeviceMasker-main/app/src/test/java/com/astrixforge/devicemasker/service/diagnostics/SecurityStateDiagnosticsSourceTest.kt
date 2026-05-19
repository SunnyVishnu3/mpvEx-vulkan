package com.astrixforge.devicemasker.service.diagnostics

import java.io.File
import org.junit.Assert.assertTrue
import org.junit.Test

class SecurityStateDiagnosticsSourceTest {

    private val repoRoot: File =
        generateSequence(File(System.getProperty("user.dir") ?: error("user.dir is not set"))) {
                file ->
                file.parentFile
            }
            .first { File(it, "settings.gradle.kts").isFile }

    @Test
    fun `advanced protection and identity check are diagnostics only`() {
        val diagnostics =
            File(
                    repoRoot,
                    "app/src/main/kotlin/com/astrixforge/devicemasker/service/diagnostics/SecurityStateDiagnostics.kt",
                )
                .readText()
        val logManager =
            File(repoRoot, "app/src/main/kotlin/com/astrixforge/devicemasker/service/LogManager.kt")
                .readText()

        assertTrue(diagnostics.contains("AdvancedProtectionManager"))
        assertTrue(diagnostics.contains("isAdvancedProtectionEnabled"))
        assertTrue(diagnostics.contains("IDENTITY_CHECK"))
        assertTrue(diagnostics.contains("security_state_snapshot.json"))
        assertTrue(logManager.contains("SecurityStateDiagnostics.snapshotFile(context)"))
    }
}
