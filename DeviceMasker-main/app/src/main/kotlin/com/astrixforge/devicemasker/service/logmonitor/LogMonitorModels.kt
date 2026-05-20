package com.astrixforge.devicemasker.service.logmonitor

import androidx.compose.runtime.Immutable

enum class LogMonitorLevel {
    VERBOSE,
    DEBUG,
    INFO,
    WARN,
    ERROR,
    FATAL,
}

enum class LogMonitorSource {
    ALL,
    APP,
    XPOSED,
    LSPOSED,
    CRASH,
    LOGCAT,
}

enum class LogCaptureStatus {
    IDLE,
    STARTING,
    RUNNING,
    STOPPING,
    ERROR,
}

@Immutable
data class LogMonitorRow(
    val id: Long,
    val timestampMillis: Long,
    val source: LogMonitorSource,
    val level: LogMonitorLevel,
    val tag: String,
    val message: String,
    val rawLine: String,
)

@Immutable
data class LogMonitorFilter(
    val source: LogMonitorSource = LogMonitorSource.ALL,
    val minLevel: LogMonitorLevel = LogMonitorLevel.DEBUG,
    val query: String = "",
)
