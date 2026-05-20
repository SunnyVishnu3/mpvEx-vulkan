package com.astrixforge.devicemasker.service

import java.io.File
import java.lang.reflect.Modifier
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ImplementationPlanHardeningTest {

    @Test
    fun `manifest disables app backup and uses adjust resize`() {
        val manifest = projectFile("app/src/main/AndroidManifest.xml").readText()

        assertTrue(manifest.contains("""android:allowBackup="false""""))
        assertTrue(manifest.contains("""android:windowSoftInputMode="adjustResize""""))
    }

    @Test
    fun `app build has minified ci release validation build type`() {
        val buildFile = projectFile("app/build.gradle.kts").readText()

        assertTrue(buildFile.contains("""create("ciRelease")"""))
        assertTrue(buildFile.contains("isMinifyEnabled = true"))
        assertTrue(buildFile.contains("metricsDestination"))
        assertTrue(buildFile.contains("reportsDestination"))
    }

    @Test
    fun `diagnostic store read paths are synchronized`() {
        val readEvents =
            com.astrixforge.devicemasker.service.diagnostics.JsonlDiagnosticStore::class
                .java
                .getDeclaredMethod("readEvents")
        val stats =
            com.astrixforge.devicemasker.service.diagnostics.JsonlDiagnosticStore::class
                .java
                .getDeclaredMethod("stats")

        assertTrue(Modifier.isSynchronized(readEvents.modifiers))
        assertTrue(Modifier.isSynchronized(stats.modifiers))
    }

    @Test
    fun `root support bundle streams artifacts instead of readText`() {
        val source =
            projectFile(
                    "app/src/main/kotlin/com/astrixforge/devicemasker/service/diagnostics/SupportBundleBuilder.kt"
                )
                .readText()

        assertFalse(source.contains("artifact.readText()"))
        assertTrue(source.contains("writeRedactedFile"))
        assertTrue(source.contains("useLines"))
    }

    private fun projectFile(path: String): File {
        var current = File("").absoluteFile
        while (current != current.parentFile) {
            val candidate = File(current, path)
            if (candidate.exists()) return candidate
            current = current.parentFile ?: break
        }
        return File(path)
    }
}
