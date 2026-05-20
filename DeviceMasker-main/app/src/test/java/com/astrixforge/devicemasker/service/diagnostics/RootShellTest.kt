package com.astrixforge.devicemasker.service.diagnostics

import kotlin.io.path.createTempDirectory
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RootShellTest {
    @Test
    fun `su unavailable returns root unavailable`() {
        val shell = RootShell(FakeExecutor(available = false))
        val dir = createTempDirectory("root").toFile()

        val result = shell.run(RootCommand("id"), dir)

        assertFalse(result.rootAvailable)
        assertEquals(RootCommandStatus.ROOT_UNAVAILABLE, result.status)
        assertTrue(dir.resolve("manifest.json").exists())
        assertTrue(dir.resolve("manifest.json").readText().contains("ROOT_UNAVAILABLE"))
    }

    @Test
    fun `captures exit code stderr and keeps capped stdout tail`() {
        val shell = RootShell(FakeExecutor(stdout = "abcdef", stderr = "warning", exitCode = 7))
        val dir = createTempDirectory("root").toFile()

        val result = shell.run(RootCommand("id", maxOutputBytes = 3), dir)

        assertEquals(7, result.exitCode)
        assertEquals("warning", result.stderrSummary)
        assertEquals("def", result.stdoutPath?.readText())
        assertEquals("warning", result.stderrPath?.readText())
        assertEquals(RootCommandStatus.EXITED, result.status)
        assertTrue(dir.resolve("manifest.json").readText().contains(""""exitCode":7"""))
        assertTrue(dir.resolve("manifest.json").readText().contains("warning"))
    }

    @Test
    fun `timeout returns timeout result`() {
        val shell = RootShell(FakeExecutor(timedOut = true))

        val result =
            shell.run(
                RootCommand("logcat", timeoutMillis = 1),
                createTempDirectory("root").toFile(),
            )

        assertTrue(result.timedOut)
        assertEquals(RootCommandStatus.TIMED_OUT, result.status)
        assertTrue(result.stderrSummary.isEmpty())
    }

    private class FakeExecutor(
        private val available: Boolean = true,
        private val stdout: String = "",
        private val stderr: String = "",
        private val exitCode: Int = 0,
        private val timedOut: Boolean = false,
    ) : RootCommandExecutor {
        override fun isRootAvailable(): Boolean = available

        override fun execute(command: String, timeoutMillis: Long): RootExecutionResult =
            RootExecutionResult(
                exitCode = exitCode,
                stdout = stdout,
                stderr = stderr,
                timedOut = timedOut,
                durationMillis = timeoutMillis,
            )
    }
}
