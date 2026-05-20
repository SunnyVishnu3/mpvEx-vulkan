package com.astrixforge.devicemasker.service.diagnostics

import kotlin.io.path.createTempDirectory
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RootLogCollectorTest {
    @Test
    fun `collector writes expected maximum diagnostic artifacts`() {
        val executor = RecordingExecutor()
        val collector = RootLogCollector(RootShell(executor))
        val outputDir = createTempDirectory("collector").toFile()

        collector.collect(outputDir, targetPackage = "com.mantle.verify")

        listOf(
                "logcat_main_system_crash.txt",
                "logcat_all_buffers.txt",
                "logcat_filtered_devicemasker_lsposed.txt",
                "anr/list.txt",
                "anr/anr_traces.txt",
                "tombstones/list.txt",
                "tombstones/tombstones.txt",
                "dumpsys_package_module.txt",
                "dumpsys_package_target.txt",
                "dumpsys_activity_processes.txt",
                "getprop_redacted.txt",
            )
            .forEach { relativePath -> assertTrue(outputDir.resolve(relativePath).exists()) }

        assertTrue(
            executor.commands.any {
                it.contains("logcat -d -v threadtime -b main,system,crash,events")
            }
        )
        assertTrue(executor.commands.any { it.contains("logcat -d -v threadtime -b all") })
        assertTrue(executor.commands.any { it.contains("dumpsys package com.mantle.verify") })
        assertTrue(
            outputDir.resolve("capture_context.json").readText().contains("com.mantle.verify")
        )
        assertTrue(outputDir.resolve("command_manifest.jsonl").exists())
        assertTrue(outputDir.resolve("command_manifest.jsonl").readText().contains("rootAvailable"))
        assertTrue(outputDir.resolve("logcat_all_buffers.txt.manifest.json").exists())
    }

    @Test
    fun `collector skips target commands when package is blank or invalid`() {
        val executor = RecordingExecutor()
        val collector = RootLogCollector(RootShell(executor))
        val outputDir = createTempDirectory("collector").toFile()

        collector.collect(outputDir, targetPackage = "com.example.bad; rm -rf /")

        assertFalse(executor.commands.any { it.contains("com.example.bad") })
        assertFalse(executor.commands.any { it.contains("com.mantle.verify") })
        assertFalse(outputDir.resolve("dumpsys_package_target.txt").exists())
        assertTrue(
            executor.commands.any {
                it.contains(
                    "DeviceMasker|LSPosed|lspd|XposedEntry|All hooks registered|Spoof event"
                )
            }
        )
    }

    @Test
    fun `collector filtered logcat is generic when target package is absent`() {
        val executor = RecordingExecutor()
        val collector = RootLogCollector(RootShell(executor))
        val outputDir = createTempDirectory("collector").toFile()

        collector.collect(outputDir, targetPackage = null)

        val filteredCommand =
            executor.commands.single { it.contains("logcat -d -t 2000 -v threadtime -b all") }
        assertTrue(filteredCommand.contains("DeviceMasker"))
        assertTrue(filteredCommand.contains("LSPosed"))
        assertTrue(filteredCommand.contains("XposedEntry"))
        assertTrue(filteredCommand.contains("All hooks registered"))
        assertTrue(filteredCommand.contains("Spoof event"))
        assertFalse(filteredCommand.contains("com.mantle.verify"))
    }

    @Test
    fun `collector explains empty command output in artifact and manifest`() {
        val executor = EmptyOutputExecutor()
        val collector = RootLogCollector(RootShell(executor))
        val outputDir = createTempDirectory("collector").toFile()

        collector.collect(
            outputDir = outputDir,
            context = LogCaptureContext(selectedTargetPackage = null),
        )

        val artifact = outputDir.resolve("logcat_filtered_devicemasker_lsposed.txt").readText()
        val manifest =
            outputDir.resolve("logcat_filtered_devicemasker_lsposed.txt.manifest.json").readText()
        assertTrue(artifact.contains("# empty:"))
        assertTrue(manifest.contains("stdoutBytes"))
        assertTrue(manifest.contains("stderrBytes"))
    }

    private class RecordingExecutor : RootCommandExecutor {
        val commands = mutableListOf<String>()

        override fun isRootAvailable(): Boolean = true

        override fun execute(command: String, timeoutMillis: Long): RootExecutionResult {
            commands += command
            return RootExecutionResult(0, "output for $command", "", false, 1)
        }
    }

    private class EmptyOutputExecutor : RootCommandExecutor {
        override fun isRootAvailable(): Boolean = true

        override fun execute(command: String, timeoutMillis: Long): RootExecutionResult =
            RootExecutionResult(
                exitCode = 1,
                stdout = "",
                stderr = "no matching lines",
                timedOut = false,
                durationMillis = 1,
            )
    }
}
