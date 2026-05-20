package com.astrixforge.devicemasker.service

import android.content.Context
import android.net.Uri

interface ILogManager {
    suspend fun exportLogsToUri(context: Context, uri: Uri): LogExportResult

    suspend fun createShareableLogFile(context: Context): ShareableLogResult

    fun generateLogFileName(): String
}
