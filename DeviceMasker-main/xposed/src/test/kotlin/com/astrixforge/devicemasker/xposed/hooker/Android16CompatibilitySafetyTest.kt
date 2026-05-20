package com.astrixforge.devicemasker.xposed.hooker

import java.io.File
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class Android16CompatibilitySafetyTest {
    private val repoRoot: File =
        generateSequence(File(System.getProperty("user.dir") ?: error("user.dir is not set"))) {
                it.parentFile
            }
            .first { File(it, "settings.gradle.kts").isFile }

    @Test
    fun `project does not add hidden api bypass libraries`() {
        val files =
            listOf("gradle/libs.versions.toml", "app/build.gradle.kts", "xposed/build.gradle.kts")
                .map { File(repoRoot, it).readText() }
                .joinToString("\n")

        assertFalse(files.contains("HiddenApiBypass", ignoreCase = true))
        assertFalse(files.contains("org.lsposed.hiddenapibypass", ignoreCase = true))
    }

    @Test
    fun `runtime hookers keep libxposed framework errors unhandled by generic catches`() {
        val xposedSources =
            File(repoRoot, "xposed/src/main/kotlin")
                .walkTopDown()
                .filter { it.isFile && it.extension == "kt" }
                .joinToString("\n") { it.readText() }

        assertTrue(xposedSources.contains("catch (e: XposedFrameworkError)"))
        assertTrue(xposedSources.contains("throw e"))
    }

    @Test
    fun `native and system server hook tracks are not enabled by default`() {
        val buildFiles =
            listOf("gradle/libs.versions.toml", "xposed/build.gradle.kts")
                .map { File(repoRoot, it).readText() }
                .joinToString("\n")

        listOf("bytehook", "shadowhook", "xhook", "Dobby", "DexKit", "frida").forEach { forbidden ->
            assertFalse(
                "Unexpected production dependency: $forbidden",
                buildFiles.contains(forbidden),
            )
        }
    }
}
