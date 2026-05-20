package com.astrixforge.devicemasker.service.logmonitor

import java.io.File
import java.util.Locale
import java.util.concurrent.atomic.AtomicLong
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

class LogMonitorStore(private val sessionFile: File, private val maxRows: Int = DEFAULT_MAX_ROWS) {
    private val nextId = AtomicLong(0)
    private val rowsState = MutableStateFlow<List<LogMonitorRow>>(emptyList())
    private val statusState = MutableStateFlow(LogCaptureStatus.IDLE)

    val rows: StateFlow<List<LogMonitorRow>> = rowsState.asStateFlow()
    val status: StateFlow<LogCaptureStatus> = statusState.asStateFlow()

    fun setStatus(status: LogCaptureStatus) {
        statusState.value = status
    }

    fun appendRawLine(line: String, timestampMillis: Long = System.currentTimeMillis()) {
        val row = line.toRow(id = nextId.incrementAndGet(), timestampMillis = timestampMillis)
        sessionFile.parentFile?.mkdirs()
        sessionFile.appendText(row.toJsonLine() + "\n", Charsets.UTF_8)
        rowsState.update { rows -> (rows + row).takeLast(maxRows) }
    }

    fun clear() {
        rowsState.value = emptyList()
        sessionFile.delete()
    }

    fun visibleRows(filter: LogMonitorFilter): List<LogMonitorRow> {
        val normalizedQuery = filter.query.trim().lowercase(Locale.US)
        return rowsState.value.filter { row ->
            row.level.ordinal >= filter.minLevel.ordinal &&
                (filter.source == LogMonitorSource.ALL || row.source == filter.source) &&
                (normalizedQuery.isEmpty() ||
                    row.rawLine.lowercase(Locale.US).contains(normalizedQuery))
        }
    }

    private fun String.toRow(id: Long, timestampMillis: Long): LogMonitorRow {
        val parsed = parseThreadtimeLine(this)
        return LogMonitorRow(
            id = id,
            timestampMillis = timestampMillis,
            source = detectSource(this),
            level = parsed.level,
            tag = parsed.tag,
            message = parsed.message,
            rawLine = this,
        )
    }

    private fun parseThreadtimeLine(line: String): ParsedLine {
        val match = THREADTIME_REGEX.matchEntire(line.trim())
        if (match == null) {
            return ParsedLine(level = detectLevel(line), tag = "logcat", message = line)
        }
        return ParsedLine(
            level = match.groupValues[1].toMonitorLevel(),
            tag = match.groupValues[2].trim(),
            message = match.groupValues[3].trim(),
        )
    }

    private fun detectSource(line: String): LogMonitorSource =
        when {
            "AndroidRuntime" in line || "FATAL EXCEPTION" in line -> LogMonitorSource.CRASH
            "XposedEntry" in line || "All hooks registered" in line || "Spoof event:" in line ->
                LogMonitorSource.XPOSED
            "LSPosed" in line || "lspd" in line -> LogMonitorSource.LSPOSED
            "DeviceMasker" in line -> LogMonitorSource.APP
            else -> LogMonitorSource.LOGCAT
        }

    private fun detectLevel(line: String): LogMonitorLevel =
        when {
            " F " in line -> LogMonitorLevel.FATAL
            " E " in line -> LogMonitorLevel.ERROR
            " W " in line -> LogMonitorLevel.WARN
            " I " in line -> LogMonitorLevel.INFO
            " V " in line -> LogMonitorLevel.VERBOSE
            else -> LogMonitorLevel.DEBUG
        }

    private fun String.toMonitorLevel(): LogMonitorLevel =
        when (uppercase(Locale.US)) {
            "F" -> LogMonitorLevel.FATAL
            "E" -> LogMonitorLevel.ERROR
            "W" -> LogMonitorLevel.WARN
            "I" -> LogMonitorLevel.INFO
            "V" -> LogMonitorLevel.VERBOSE
            else -> LogMonitorLevel.DEBUG
        }

    private fun LogMonitorRow.toJsonLine(): String =
        """{"id":$id,"timestampMillis":$timestampMillis,"source":"$source","level":"$level","tag":"${tag.jsonEscape()}","message":"${message.jsonEscape()}","rawLine":"${rawLine.jsonEscape()}"}"""

    private fun String.jsonEscape(): String =
        replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", "\\n")

    private data class ParsedLine(val level: LogMonitorLevel, val tag: String, val message: String)

    private companion object {
        private const val DEFAULT_MAX_ROWS = 1_000
        private val THREADTIME_REGEX =
            Regex(
                """\d\d-\d\d\s+\d\d:\d\d:\d\d\.\d{3}\s+\d+\s+\d+\s+([VDIWEF])\s+([^:]+):\s?(.*)"""
            )
    }
}
