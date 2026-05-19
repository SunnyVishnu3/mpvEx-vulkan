package com.astrixforge.devicemasker.service.diagnostics

import android.content.Context
import java.io.File

object RootCaptureStore {
    private const val ROOT_CAPTURE_DIR = "logs/root-capture"
    private const val LATEST_DIR = "latest"

    fun latestCaptureDir(context: Context): File =
        File(context.filesDir, "$ROOT_CAPTURE_DIR/$LATEST_DIR")

    fun prepareFreshCaptureDir(context: Context, trigger: String): File {
        val dir = latestCaptureDir(context)
        dir.deleteRecursively()
        dir.mkdirs()
        writeManifest(
            dir = dir,
            trigger = trigger,
            status = "STARTED",
            message = "Root capture started",
        )
        return dir
    }

    fun prepareExportArtifacts(context: Context, outputDir: File): File {
        val rootArtifactsDir = File(outputDir, "root_artifacts")
        rootArtifactsDir.deleteRecursively()
        rootArtifactsDir.mkdirs()

        val latest = latestCaptureDir(context)
        if (latest.exists()) {
            latest.copyRecursively(File(rootArtifactsDir, "latest_capture"), overwrite = true)
        }
        return rootArtifactsDir
    }

    fun writeManifest(dir: File, trigger: String, status: String, message: String) {
        dir.mkdirs()
        dir.resolve("root_capture_manifest.json")
            .writeText(
                buildManifestJson(
                    trigger = trigger,
                    status = status,
                    message = message,
                    timestampMillis = System.currentTimeMillis(),
                ),
                Charsets.UTF_8,
            )
    }

    private fun buildManifestJson(
        trigger: String,
        status: String,
        message: String,
        timestampMillis: Long,
    ): String = buildString {
        append("""{"trigger":"${trigger.jsonEscape()}","status":"${status.jsonEscape()}"""")
        append(""","message":"${message.jsonEscape()}","timestampMillis":$timestampMillis}""")
    }

    private fun String.jsonEscape(): String =
        replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", "\\n")
}
