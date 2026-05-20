package com.astrixforge.devicemasker.service.diagnostics

import com.astrixforge.devicemasker.common.diagnostics.DiagnosticEvent
import com.astrixforge.devicemasker.common.diagnostics.DiagnosticEventType
import com.astrixforge.devicemasker.common.diagnostics.DiagnosticJson
import com.astrixforge.devicemasker.common.diagnostics.DiagnosticSeverity
import com.astrixforge.devicemasker.common.diagnostics.DiagnosticSource
import com.astrixforge.devicemasker.common.diagnostics.RedactionMode
import java.util.zip.ZipFile
import kotlin.io.path.createTempDirectory
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SupportBundleBuilderTest {
    @Test
    fun `support bundle streams app and xposed events as jsonl`() {
        val outputDir = createTempDirectory("bundle").toFile()
        val appEvents = (1..1000).map { """{"message":"app-$it"}""" }
        val xposedEvents = (1..1000).map { """{"message":"xposed-$it"}""" }

        val bundle =
            SupportBundleBuilder(appEvents = appEvents, xposedEvents = xposedEvents)
                .build(outputDir, RedactionMode.UNREDACTED)

        ZipFile(bundle).use { zip ->
            val appText =
                zip.getInputStream(zip.getEntry("app/app_events.jsonl")).bufferedReader().readText()
            val xposedText =
                zip.getInputStream(zip.getEntry("xposed/xposed_events.jsonl"))
                    .bufferedReader()
                    .readText()
            assertTrue(appText.contains("""{"message":"app-1000"}"""))
            assertTrue(xposedText.contains("""{"message":"xposed-1000"}"""))
        }
    }

    @Test
    fun `root maximum zip contains expected support artifacts`() {
        val rootDir = createTempDirectory("root-artifacts").toFile()
        rootDir.resolve("logcat_main_system_crash.txt").writeText("imei=490154203237518")
        rootDir.resolve("command_manifest.jsonl").writeText("""{"status":"EXITED"}""")
        val outputDir = createTempDirectory("bundle").toFile()
        val bundle =
            SupportBundleBuilder(
                    appEvents = listOf("""{"message":"app"}"""),
                    xposedEvents = listOf("""{"message":"xposed"}"""),
                    snapshots =
                        mapOf(
                            "config_snapshot_redacted.json" to """{"imei":"[REDACTED_IMEI]"}""",
                            "remote_prefs_snapshot_redacted.json" to "{}",
                            "scope_snapshot.json" to "[]",
                            "hook_health.json" to "{}",
                            "summary.json" to "{}",
                        ),
                    rootArtifactsDir = rootDir,
                )
                .build(outputDir, RedactionMode.REDACTED)

        ZipFile(bundle).use { zip ->
            listOf(
                    "manifest.json",
                    "README_REPRO.md",
                    "app/app_events.jsonl",
                    "xposed/xposed_events.jsonl",
                    "config/config_snapshot_redacted.json",
                    "config/remote_prefs_snapshot_redacted.json",
                    "scope/scope_snapshot.json",
                    "root/logcat_main_system_crash.txt",
                    "root/command_manifest.jsonl",
                )
                .forEach { assertTrue("Missing $it", zip.getEntry(it) != null) }

            assertTrue(
                zip.getInputStream(zip.getEntry("manifest.json"))
                    .bufferedReader()
                    .readText()
                    .contains(""""mode":"MAXIMUM"""")
            )

            val combined =
                zip.entries().asSequence().joinToString("\n") { entry ->
                    zip.getInputStream(entry).bufferedReader().readText()
                }
            assertFalse(combined.contains("490154203237518"))
            assertTrue(combined.contains("[REDACTED_IMEI]"))
        }
    }

    @Test
    fun `support bundle copies binary artifacts byte for byte`() {
        val outputDir = createTempDirectory("bundle").toFile()
        val binary = createTempDirectory("binary").toFile().resolve("sample.tar.gz")
        val bytes = byteArrayOf(0x1f, 0x8b.toByte(), 0x08, 0x00, 0x7f, 0x00)
        binary.writeBytes(bytes)

        val bundle =
            SupportBundleBuilder(
                    appEvents = emptyList(),
                    xposedEvents = emptyList(),
                    supportArtifacts =
                        listOf(
                            SupportArtifact(
                                source = binary,
                                zipPath = "root/sample.tar.gz",
                                encoding = ArtifactEncoding.BINARY_RAW,
                            )
                        ),
                )
                .build(outputDir, RedactionMode.REDACTED)

        ZipFile(bundle).use { zip ->
            val copied = zip.getInputStream(zip.getEntry("root/sample.tar.gz")).readBytes()
            assertArrayEquals(bytes, copied)
        }
    }

    @Test
    fun `support bundle redacts only text artifacts marked for redaction`() {
        val outputDir = createTempDirectory("bundle").toFile()
        val text = createTempDirectory("text").toFile().resolve("logcat.txt")
        text.writeText("imei=490154203237518\nraw=visible\n")

        val bundle =
            SupportBundleBuilder(
                    appEvents = emptyList(),
                    xposedEvents = emptyList(),
                    supportArtifacts =
                        listOf(
                            SupportArtifact(
                                source = text,
                                zipPath = "root/logcat.txt",
                                encoding = ArtifactEncoding.TEXT_REDACTED,
                            )
                        ),
                )
                .build(outputDir, RedactionMode.REDACTED)

        ZipFile(bundle).use { zip ->
            val content =
                zip.getInputStream(zip.getEntry("root/logcat.txt")).bufferedReader().readText()
            assertFalse(content.contains("490154203237518"))
            assertTrue(content.contains("[REDACTED_IMEI]"))
            assertTrue(content.contains("raw=visible"))
        }
    }

    @Test
    fun `support bundle can contain parsed xposed events`() {
        val outputDir = createTempDirectory("bundle").toFile()
        val bundle =
            SupportBundleBuilder(
                    appEvents = emptyList(),
                    xposedEvents =
                        listOf("""{"message":"All hooks registered for: [PKG:abcd1234]"}"""),
                )
                .build(outputDir, RedactionMode.REDACTED)

        ZipFile(bundle).use { zip ->
            val content =
                zip.getInputStream(zip.getEntry("xposed/xposed_events.jsonl"))
                    .bufferedReader()
                    .readText()
            assertTrue(content.contains("All hooks registered"))
        }
    }

    @Test
    fun `redacted diagnostic jsonl remains parseable`() {
        val outputDir = createTempDirectory("bundle").toFile()
        val event =
            DiagnosticEvent(
                eventId = "evt_1779078112030_000001",
                timestampWallMillis = 1779078112030,
                timestampElapsedMillis = 74021,
                sessionId = "app-log",
                bootId = "unknown",
                source = DiagnosticSource.APP,
                severity = DiagnosticSeverity.INFO,
                eventType = DiagnosticEventType.APP_LOG,
                message = "imei=490154203237518",
                extras = mapOf("android_id" to "a1b2c3d4e5f60789"),
            )
        val genericJson = """{"timestampWallMillis":1779078112030,"message":"id=490154203237518"}"""

        val bundle =
            SupportBundleBuilder(
                    appEvents = listOf(DiagnosticJson.encodeToString(event), genericJson),
                    xposedEvents = emptyList(),
                )
                .build(outputDir, RedactionMode.REDACTED)

        ZipFile(bundle).use { zip ->
            val lines =
                zip.getInputStream(zip.getEntry("app/app_events.jsonl"))
                    .bufferedReader()
                    .readLines()
                    .filter { it.isNotBlank() }

            val redactedEvent = DiagnosticJson.decodeFromString<DiagnosticEvent>(lines.first())
            assertEquals(1779078112030, redactedEvent.timestampWallMillis)
            assertTrue(redactedEvent.message.contains("[REDACTED_IMEI]"))
            assertEquals("[REDACTED_ANDROID_ID]", redactedEvent.extras.getValue("android_id"))
            assertTrue(lines.drop(1).all { runCatching { it.toJsonElement() }.isSuccess })
        }
    }

    private fun String.toJsonElement() = kotlinx.serialization.json.Json.parseToJsonElement(this)
}
