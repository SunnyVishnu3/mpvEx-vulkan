package com.astrixforge.devicemasker

import java.io.File
import org.junit.Assert.assertTrue
import org.junit.Test

class ReleaseBuildSafetyTest {

    @Test
    fun `release build keeps minification enabled with stable hooker guardrails`() {
        val buildFile = projectFile("app/build.gradle.kts").readText()
        val stableHookerFile =
            projectFile(
                    "xposed/src/main/kotlin/com/astrixforge/devicemasker/xposed/hooker/callback/StableHooker.kt"
                )
                .readText()
        val r8GuardTest =
            projectFile(
                    "xposed/src/test/kotlin/com/astrixforge/devicemasker/xposed/hooker/R8HookerAbiTest.kt"
                )
                .readText()

        assertTrue(
            "Release R8 minification must stay enabled; StableHooker is the validated callback path.",
            Regex("release\\s*\\{[\\s\\S]*isMinifyEnabled\\s*=\\s*true").containsMatchIn(buildFile),
        )
        assertTrue(
            "Resource shrinking should stay enabled with release minification.",
            Regex("release\\s*\\{[\\s\\S]*isShrinkResources\\s*=\\s*true")
                .containsMatchIn(buildFile),
        )
        assertTrue(
            "StableHooker must keep libxposed runtime callbacks as concrete Hooker implementations.",
            stableHookerFile.contains("XposedInterface.Hooker"),
        )
        assertTrue(
            "R8HookerAbiTest must prevent direct runtime intercept lambdas from returning.",
            r8GuardTest.contains("direct Kotlin SAM lambdas"),
        )
    }

    @Test
    fun `xposed target process does not use ServiceManager diagnostics lookup`() {
        val entryFile =
            projectFile("xposed/src/main/kotlin/com/astrixforge/devicemasker/xposed/XposedEntry.kt")
                .readText()

        assertTrue(
            "Target-process diagnostics must not use ServiceManager; SELinux denies this on user builds.",
            !entryFile.contains("Class.forName(\"android.os.ServiceManager\")"),
        )
    }

    @Test
    fun `xposed hooks do not mutate immutable libxposed chain args`() {
        val xposedSources =
            projectFile("xposed/src/main/kotlin")
                .walkTopDown()
                .filter { it.isFile && it.extension == "kt" }
                .joinToString("\n") { it.readText() }

        assertTrue(
            "libxposed Chain.getArgs() returns an immutable list; use chain.proceed(Object[]) to change arguments.",
            !xposedSources.contains("chain.args["),
        )
    }

    @Test
    fun `hook registration catches rethrow xposed framework errors`() {
        val guardedFiles =
            listOf(
                "xposed/src/main/kotlin/com/astrixforge/devicemasker/xposed/XposedEntry.kt",
                "xposed/src/main/kotlin/com/astrixforge/devicemasker/xposed/hooker/BaseSpoofHooker.kt",
                "xposed/src/main/kotlin/com/astrixforge/devicemasker/xposed/hooker/AntiDetectHooker.kt",
            )

        guardedFiles.forEach { path ->
            val text = projectFile(path).readText()
            assertTrue(
                "$path must rethrow XposedFrameworkError before generic Throwable fallback.",
                text.contains("catch (e: XposedFrameworkError)") &&
                    text.contains("catch (t: Throwable)"),
            )
        }
    }

    @Test
    fun `anti detect class lookup hooks are reentrant safe`() {
        val hookerFile =
            projectFile(
                    "xposed/src/main/kotlin/com/astrixforge/devicemasker/xposed/hooker/AntiDetectHooker.kt"
                )
                .readText()

        assertTrue(
            "Class lookup hooks must guard reentry; otherwise target startup can ANR " +
                "while hook registration loads classes.",
            hookerFile.contains("classLookupHookActive"),
        )
        assertTrue(
            "Class lookup hooks must reset their guard in finally blocks.",
            hookerFile.contains("classLookupHookActive.set(false)"),
        )
        assertTrue(
            "Global class lookup hooks must stay disabled until they are proven target-startup safe.",
            !hookerFile.contains("hookClassForName(cl, xi)") &&
                !hookerFile.contains("hookClassLoaderLoadClass(cl, xi)"),
        )
        assertTrue(
            "Bootstrap classes should be resolved directly instead of through the target " +
                "ClassLoader after class-loading hooks are active.",
            hookerFile.contains("ClassLoader::class.java") &&
                hookerFile.contains("Class::class.java"),
        )
        assertTrue(
            "The class lookup hook must avoid Kotlin collection helpers in the intercepted class-loading path.",
            !hookerFile.substringAfter("private fun shouldHideClass").contains(".any"),
        )
    }

    @Test
    fun `android 16 compatibility safety scripts and dependencies stay conservative`() {
        val appBuild = projectFile("app/build.gradle.kts").readText()
        val xposedBuild = projectFile("xposed/build.gradle.kts").readText()
        val catalog = projectFile("gradle/libs.versions.toml").readText()

        assertTrue(projectFile("scripts/verify-16kb-page-support.ps1").isFile)
        assertTrue(projectFile("scripts/collect-a16-crash-evidence.ps1").isFile)
        assertTrue(!catalog.contains("hiddenapibypass", ignoreCase = true))
        assertTrue(!appBuild.contains("hiddenapibypass", ignoreCase = true))
        assertTrue(!xposedBuild.contains("hiddenapibypass", ignoreCase = true))
    }

    private fun projectFile(path: String): File {
        val normalized = path.replace("/", File.separator)
        return generateSequence(File("").absoluteFile) { it.parentFile }
            .map { File(it, normalized) }
            .first { it.exists() }
    }
}
