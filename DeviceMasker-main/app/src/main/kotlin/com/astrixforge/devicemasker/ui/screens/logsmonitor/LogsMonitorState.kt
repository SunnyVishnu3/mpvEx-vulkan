package com.astrixforge.devicemasker.ui.screens.logsmonitor

import androidx.compose.runtime.Immutable
import com.astrixforge.devicemasker.service.logmonitor.LogCaptureStatus
import com.astrixforge.devicemasker.service.logmonitor.LogMonitorFilter
import com.astrixforge.devicemasker.service.logmonitor.LogMonitorRow
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf

@Immutable
data class LogsMonitorState(
    val status: LogCaptureStatus = LogCaptureStatus.IDLE,
    val filter: LogMonitorFilter = LogMonitorFilter(),
    val visibleRows: ImmutableList<LogMonitorRow> = persistentListOf(),
)
