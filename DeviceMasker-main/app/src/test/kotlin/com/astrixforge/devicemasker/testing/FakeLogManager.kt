package com.astrixforge.devicemasker.testing

import android.content.Context
import android.net.Uri
import com.astrixforge.devicemasker.service.ILogManager
import com.astrixforge.devicemasker.service.LogExportResult
import com.astrixforge.devicemasker.service.ShareableLogResult

/** Fake [ILogManager] for Settings testing. */
class FakeLogManager : ILogManager {

    var exportResult: LogExportResult = LogExportResult.Success("/path/to/file.zip", 42)
    var shareableResult: ShareableLogResult = ShareableLogResult.Success(Uri.EMPTY, "file.zip", 42)
    var fileName: String = "devicemasker_support_20240101_000000.zip"
    var exportCallCount: Int = 0
        private set

    var shareCallCount: Int = 0
        private set

    var lastExportUri: Uri? = null
        private set

    override suspend fun exportLogsToUri(context: Context, uri: Uri): LogExportResult {
        exportCallCount += 1
        lastExportUri = uri
        return exportResult
    }

    override suspend fun createShareableLogFile(context: Context): ShareableLogResult {
        shareCallCount += 1
        return shareableResult
    }

    override fun generateLogFileName(): String = fileName
}
