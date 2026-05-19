package com.astrixforge.devicemasker.service

import android.content.Context
import android.net.Uri
import androidx.core.content.FileProvider
import com.astrixforge.devicemasker.DeviceMaskerApp
import com.astrixforge.devicemasker.common.diagnostics.DiagnosticEvent
import com.astrixforge.devicemasker.common.diagnostics.DiagnosticJson
import com.astrixforge.devicemasker.common.diagnostics.RedactionMode
import com.astrixforge.devicemasker.service.diagnostics.DefaultSupportSnapshotProvider
import com.astrixforge.devicemasker.service.diagnostics.LogCaptureContext
import com.astrixforge.devicemasker.service.diagnostics.LsposedLogCopyCollector
import com.astrixforge.devicemasker.service.diagnostics.ParsedHookHealthSnapshotBuilder
import com.astrixforge.devicemasker.service.diagnostics.RootAccessManager
import com.astrixforge.devicemasker.service.diagnostics.RootCaptureStore
import com.astrixforge.devicemasker.service.diagnostics.RootLogCollector
import com.astrixforge.devicemasker.service.diagnostics.SecurityStateDiagnostics
import com.astrixforge.devicemasker.service.diagnostics.SupportBundleBuilder
import com.astrixforge.devicemasker.service.diagnostics.XposedLogParser
import java.io.File
import java.io.IOException
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Log manager for rootless structured export.
 *
 * The app cannot read global logcat without privileged access. Exports combine app-owned persistent
 * logs with optional root-captured logcat artifacts when root is available.
 */
object LogManager : ILogManager {

    private const val LOG_FILE_PREFIX = "devicemasker_support_"
    private const val LOG_FILE_EXTENSION = ".zip"

    override suspend fun exportLogsToUri(context: Context, uri: Uri): LogExportResult =
        withContext(Dispatchers.IO) {
            try {
                val bundle = buildSupportBundle(context)

                val outputStream = context.contentResolver.openOutputStream(uri)
                if (outputStream == null) {
                    LogExportResult.Error("Failed to open file for writing")
                } else {
                    outputStream.use { output ->
                        bundle.inputStream().use { input -> input.copyTo(output) }
                    }

                    LogExportResult.Success(
                        uri.lastPathSegment ?: bundle.name,
                        bundle.length().toInt(),
                    )
                }
            } catch (e: IOException) {
                LogExportResult.Error("Export failed: ${e.message}")
            } catch (e: SecurityException) {
                LogExportResult.Error("Export failed: ${e.message}")
            } catch (e: IllegalStateException) {
                LogExportResult.Error("Export failed: ${e.message}")
            }
        }

    override suspend fun createShareableLogFile(context: Context): ShareableLogResult =
        withContext(Dispatchers.IO) {
            try {
                val logsDir = File(context.cacheDir, "logs")
                if (!logsDir.exists()) {
                    logsDir.mkdirs()
                }

                val fileName = generateLogFileName()
                val bundle = buildSupportBundle(context, logsDir)
                val logFile = File(logsDir, fileName)
                if (bundle.name != logFile.name) {
                    bundle.copyTo(logFile, overwrite = true)
                }

                val uri =
                    FileProvider.getUriForFile(
                        context,
                        "${context.packageName}.fileprovider",
                        logFile,
                    )

                ShareableLogResult.Success(uri, fileName, logFile.length().toInt())
            } catch (e: IOException) {
                ShareableLogResult.Error("Failed to create shareable log: ${e.message}")
            } catch (e: SecurityException) {
                ShareableLogResult.Error("Failed to create shareable log: ${e.message}")
            } catch (e: IllegalStateException) {
                ShareableLogResult.Error("Failed to create shareable log: ${e.message}")
            }
        }

    private suspend fun buildSupportBundle(
        context: Context,
        outputDir: File = File(context.cacheDir, "logs"),
    ): File {
        val appEvents = AppLogStore.from(context).readDiagnosticEvents()
        val rootArtifactsDir =
            RootCaptureStore.prepareExportArtifacts(context, outputDir).also { rootDir ->
                if (RootAccessManager.hasGrantedRoot()) {
                    RootLogCollector()
                        .collect(
                            outputDir = File(rootDir, "export_snapshot"),
                            context = LogCaptureContext(selectedTargetPackage = null),
                        )
                    LsposedLogCopyCollector().collect(File(rootDir, "lsposed"))
                } else {
                    val unavailableMessage =
                        "Root access is not currently granted; export used app logs and " +
                            "captured root manifests only."
                    RootCaptureStore.writeManifest(
                        dir = rootDir,
                        trigger = "export",
                        status = "ROOT_UNAVAILABLE",
                        message = unavailableMessage,
                    )
                }
            }
        val parsedXposedEvents =
            rootArtifactsDir
                .walkTopDown()
                .filter { it.isFile && it.extension == "txt" }
                .flatMap { file ->
                    XposedLogParser.parseLines(file.readLines(Charsets.UTF_8), sessionId = "export")
                }
                .toList()
        val snapshots =
            DefaultSupportSnapshotProvider(context)
                .buildSnapshots()
                .plus(SecurityStateDiagnostics.snapshotFile(context))
                .plus(
                    "hook_health.json" to
                        ParsedHookHealthSnapshotBuilder.buildJson(parsedXposedEvents)
                )

        return SupportBundleBuilder(
                appEvents =
                    appEvents.map { event ->
                        DiagnosticJson.encodeToString(DiagnosticEvent.serializer(), event)
                    },
                xposedEvents =
                    parsedXposedEvents.map { event ->
                        DiagnosticJson.encodeToString(DiagnosticEvent.serializer(), event)
                    },
                snapshots = snapshots,
                rootArtifactsDir = rootArtifactsDir,
            )
            .build(outputDir, RedactionMode.REDACTED)
    }

    suspend fun getLogCount(): Int =
        withContext(Dispatchers.IO) {
            try {
                AppLogStore.from(DeviceMaskerApp.getInstance()).readEntries().size
            } catch (_: IOException) {
                0
            } catch (_: IllegalStateException) {
                0
            }
        }

    override fun generateLogFileName(): String {
        val timestamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())
        return "$LOG_FILE_PREFIX$timestamp$LOG_FILE_EXTENSION"
    }
}

sealed class LogExportResult {
    data class Success(val filePath: String, val lineCount: Int) : LogExportResult()

    data class Error(val message: String) : LogExportResult()
}

sealed class ShareableLogResult {
    data class Success(val uri: Uri, val fileName: String, val lineCount: Int) :
        ShareableLogResult()

    data class Error(val message: String) : ShareableLogResult()
}
