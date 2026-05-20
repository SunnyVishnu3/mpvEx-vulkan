package com.astrixforge.devicemasker.xposed.hooker

import java.io.File
import org.junit.Assert.assertTrue
import org.junit.Test

class R8HookerAbiTest {

    private val repoRoot: File =
        generateSequence(File(System.getProperty("user.dir") ?: error("user.dir is not set"))) {
                file ->
                file.parentFile
            }
            .first { File(it, "settings.gradle.kts").isFile }

    @Test
    fun `runtime hookers pass stable hooker objects to libxposed`() {
        val hookerDir =
            File(repoRoot, "xposed/src/main/kotlin/com/astrixforge/devicemasker/xposed/hooker")
        val offenders =
            hookerDir
                .walkTopDown()
                .filter { it.isFile && it.extension == "kt" && it.name != "BaseSpoofHooker.kt" }
                .flatMap { file ->
                    file.readLines().mapIndexedNotNull { index, line ->
                        if (
                            line.contains(".intercept {") || line.trim().startsWith("intercept {")
                        ) {
                            "${file.relativeTo(repoRoot).invariantSeparatorsPath}:${index + 1}:${line.trim()}"
                        } else {
                            null
                        }
                    }
                }
                .toList()

        assertTrue(
            "libxposed runtime callbacks must use stableHooker, not direct Kotlin SAM lambdas:\n" +
                offenders.joinToString("\n"),
            offenders.isEmpty(),
        )
    }

    @Test
    fun `r8 rules keep stable hooker callback classes`() {
        val combinedRules =
            File(repoRoot, "app/proguard-rules.pro").readText() +
                "\n" +
                File(repoRoot, "xposed/consumer-rules.pro").readText()

        assertTrue(combinedRules.contains("io.github.libxposed.api.XposedInterface\$Hooker"))
        assertTrue(combinedRules.contains("com.astrixforge.devicemasker.xposed.hooker.callback.**"))
    }
}
