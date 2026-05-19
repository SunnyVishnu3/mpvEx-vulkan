package com.astrixforge.devicemasker.service.diagnostics

import kotlin.io.path.createTempDirectory
import org.junit.Assert.assertTrue
import org.junit.Test

class LsposedLogCopyCollectorTest {
    @Test
    fun `collector attempts only known LSPosed log paths`() {
        val executor = RecordingExecutor()
        val collector = LsposedLogCopyCollector(RootShell(executor))
        val outputDir = createTempDirectory("lsposed-copy").toFile()

        collector.collect(outputDir)

        assertTrue(executor.commands.any { it.contains("/data/adb/lspd/log") })
        assertTrue(executor.commands.any { it.contains("/data/adb/lspd/log.old") })
        assertTrue(executor.commands.any { it.contains("[ -d /data/adb/lspd/log ]") })
        assertTrue(executor.commands.any { it.contains("for f in /data/adb/lspd/log/*") })
        assertTrue(outputDir.resolve("lsposed_copy_manifest.jsonl").exists())
        assertTrue(outputDir.resolve("lsposed_log.txt").exists())
        assertTrue(outputDir.resolve("lsposed_log_old.txt").exists())
        assertTrue(
            outputDir.resolve("lsposed_log.txt").readText().contains("DeviceMasker LSPosed log")
        )
    }

    private class RecordingExecutor : RootCommandExecutor {
        val commands = mutableListOf<String>()

        override fun isRootAvailable(): Boolean = true

        override fun execute(command: String, timeoutMillis: Long): RootExecutionResult {
            commands += command
            return RootExecutionResult(0, "DeviceMasker LSPosed log", "", false, 1)
        }
    }
}
