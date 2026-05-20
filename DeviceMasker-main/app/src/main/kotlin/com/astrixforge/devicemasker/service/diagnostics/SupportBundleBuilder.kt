package com.astrixforge.devicemasker.service.diagnostics

import com.astrixforge.devicemasker.common.diagnostics.DiagnosticEvent
import com.astrixforge.devicemasker.common.diagnostics.DiagnosticJson
import com.astrixforge.devicemasker.common.diagnostics.DiagnosticRedactor
import com.astrixforge.devicemasker.common.diagnostics.RedactionMode
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString

class SupportBundleBuilder(
    private val appEvents: List<String>,
    private val xposedEvents: List<String>,
    private val snapshots: Map<String, String> = emptyMap(),
    private val rootArtifactsDir: File? = null,
    private val supportArtifacts: List<SupportArtifact> = emptyList(),
) {
    fun build(outputDir: File, redactionMode: RedactionMode): File {
        outputDir.mkdirs()
        val file = File(outputDir, "devicemasker_support_${timestamp()}.zip")
        val redactor = DiagnosticRedactor(redactionMode)
        ZipOutputStream(file.outputStream().buffered()).use { zip ->
            zip.writeText(
                "manifest.json",
                """{"mode":"MAXIMUM","redaction":"$redactionMode","createdAt":"${timestamp()}"}""",
            )
            zip.writeText(
                "README_REPRO.md",
                "Reproduce the issue, then attach this local support bundle.\n",
            )
            zip.writeJsonl("app/app_events.jsonl", appEvents, redactor)
            zip.writeJsonl("xposed/xposed_events.jsonl", xposedEvents, redactor)

            snapshots.forEach { (name, content) ->
                val prefix =
                    when {
                        name.startsWith("config") || name.startsWith("remote") -> "config"
                        name.startsWith("scope") -> "scope"
                        else -> "diagnostics"
                    }
                zip.writeText("$prefix/$name", content.redact(redactor))
            }

            rootArtifactsDir
                ?.walkTopDown()
                ?.filter { it.isFile }
                ?.forEach { artifact ->
                    val relative = artifact.relativeTo(rootArtifactsDir).invariantSeparatorsPath
                    zip.writeRedactedFile("root/$relative", artifact, redactor)
                }

            supportArtifacts.forEach { artifact -> zip.writeArtifact(artifact, redactor) }
            zip.writeSupportArtifactManifest(supportArtifacts)
        }
        return file
    }

    private fun ZipOutputStream.writeText(path: String, content: String) {
        putNextEntry(ZipEntry(path))
        write(content.toByteArray(Charsets.UTF_8))
        closeEntry()
    }

    private fun ZipOutputStream.writeJsonl(
        path: String,
        lines: List<String>,
        redactor: DiagnosticRedactor,
    ) {
        putNextEntry(ZipEntry(path))
        lines.forEach { line ->
            write(line.redactJsonl(redactor).toByteArray(Charsets.UTF_8))
            write('\n'.code)
        }
        closeEntry()
    }

    private fun ZipOutputStream.writeRedactedFile(
        path: String,
        file: File,
        redactor: DiagnosticRedactor,
    ) {
        putNextEntry(ZipEntry(path))
        file.bufferedReader(Charsets.UTF_8).useLines { lines ->
            lines.forEach { line ->
                write(line.redact(redactor).toByteArray(Charsets.UTF_8))
                write('\n'.code)
            }
        }
        closeEntry()
    }

    private fun ZipOutputStream.writeArtifact(
        artifact: SupportArtifact,
        redactor: DiagnosticRedactor,
    ) {
        when (artifact.encoding) {
            ArtifactEncoding.TEXT_REDACTED ->
                writeRedactedFile(artifact.zipPath, artifact.source, redactor)
            ArtifactEncoding.TEXT_RAW -> writeRawFile(artifact.zipPath, artifact.source)
            ArtifactEncoding.BINARY_RAW -> writeRawFile(artifact.zipPath, artifact.source)
        }
    }

    private fun ZipOutputStream.writeRawFile(path: String, file: File) {
        putNextEntry(ZipEntry(path))
        file.inputStream().buffered().use { input -> input.copyTo(this) }
        closeEntry()
    }

    private fun ZipOutputStream.writeSupportArtifactManifest(artifacts: List<SupportArtifact>) {
        if (artifacts.isEmpty()) return
        val manifest =
            artifacts.map { artifact ->
                SupportArtifactManifest(
                    path = artifact.zipPath,
                    sourcePath = artifact.source.absolutePath,
                    encoding = artifact.encoding,
                    byteCount = artifact.source.length(),
                )
            }
        writeText(
            "support_artifacts_manifest.json",
            kotlinx.serialization.json.Json.encodeToString(manifest),
        )
    }

    private fun String.redact(redactor: DiagnosticRedactor): String = redactor.redactMessage(this)

    private fun String.redactJsonl(redactor: DiagnosticRedactor): String =
        runCatching {
                val event = DiagnosticJson.decodeFromString<DiagnosticEvent>(this)
                DiagnosticJson.encodeToString(redactor.redactEvent(event))
            }
            .getOrElse { redact(redactor).quoteBareRedactionTokens() }

    private fun String.quoteBareRedactionTokens(): String =
        BARE_REDACTION_TOKEN_REGEX.replace(this) { match ->
            "${match.groupValues[1]}\"${match.groupValues[2]}\""
        }

    private fun timestamp(): String = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())

    private companion object {
        private val BARE_REDACTION_TOKEN_REGEX =
            Regex("""(:\s*)(\[(?:REDACTED|PKG)[A-Z0-9_:]*])(?=\s*[,}\]])""")
    }
}
