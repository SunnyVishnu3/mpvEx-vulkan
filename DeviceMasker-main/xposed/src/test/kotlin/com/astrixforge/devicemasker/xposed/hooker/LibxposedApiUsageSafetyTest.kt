package com.astrixforge.devicemasker.xposed.hooker

import java.io.File
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LibxposedApiUsageSafetyTest {
    private val repoRoot: File =
        generateSequence(File(System.getProperty("user.dir") ?: error("user.dir is not set"))) {
                it.parentFile
            }
            .first { File(it, "settings.gradle.kts").isFile }

    @Test
    fun `libxposed api stays compile only`() {
        val buildFile = File(repoRoot, "xposed/build.gradle.kts").readText()

        assertTrue(buildFile.contains("compileOnly"))
        assertFalse(
            Regex("""implementation\s*\([^)]*libxposed[^)]*api""").containsMatchIn(buildFile)
        )
    }

    @Test
    fun `module metadata targets libxposed api 101`() {
        val metadataDir = File(repoRoot, "xposed/src/main/resources/META-INF/xposed")
        val moduleProp = File(metadataDir, "module.prop").readText()
        val javaInit = File(metadataDir, "java_init.list").readText()

        assertTrue(moduleProp.contains("minApiVersion=101"))
        assertTrue(moduleProp.contains("targetApiVersion=101"))
        assertTrue(javaInit.contains("com.astrixforge.devicemasker.xposed.XposedEntry"))
    }

    @Test
    fun `legacy xposed bridge api stays absent`() {
        val sources = executableSourceLines()

        listOf(
                "import de.robv.android.xposed",
                "XC_MethodHook(",
                ".beforeHookedMethod",
                ".afterHookedMethod",
            )
            .forEach { forbidden ->
                assertFalse("Forbidden legacy API found: $forbidden", sources.contains(forbidden))
            }
    }

    @Test
    fun `runtime hookers do not use direct intercept lambdas`() {
        val hookerSources =
            File(repoRoot, "xposed/src/main/kotlin/com/astrixforge/devicemasker/xposed/hooker")
                .walkTopDown()
                .filter { it.isFile && it.extension == "kt" && it.name != "StableHooker.kt" }
                .joinToString("\n") { it.readText() }

        assertFalse(
            Regex("""\.intercept\s*\{""")
                .containsMatchIn(
                    hookerSources
                        .lineSequence()
                        .filterNot {
                            it.trimStart().startsWith("*") || it.trimStart().startsWith("//")
                        }
                        .joinToString("\n")
                )
        )
    }

    private fun executableSourceLines(): String =
        File(repoRoot, "xposed/src/main/kotlin")
            .walkTopDown()
            .filter { it.isFile && it.extension == "kt" }
            .flatMap { it.readLines() }
            .filterNot { line ->
                val trimmed = line.trimStart()
                trimmed.startsWith("*") || trimmed.startsWith("//") || trimmed.startsWith("\"")
            }
            .joinToString("\n")
}
