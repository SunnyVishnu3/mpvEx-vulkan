package com.astrixforge.devicemasker.ui.screens.logsmonitor

import android.app.Application
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.astrixforge.devicemasker.service.logmonitor.ILogMonitorRepository
import com.astrixforge.devicemasker.service.logmonitor.LogMonitorFilter
import com.astrixforge.devicemasker.service.logmonitor.LogMonitorLevel
import com.astrixforge.devicemasker.service.logmonitor.LogMonitorRepository
import com.astrixforge.devicemasker.service.logmonitor.LogMonitorRow
import com.astrixforge.devicemasker.service.logmonitor.LogMonitorSource
import kotlinx.collections.immutable.toPersistentList
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update

class LogsMonitorViewModel(private val repository: ILogMonitorRepository) : ViewModel() {
    constructor(application: Application) : this(LogMonitorRepository(application))

    private val filter = MutableStateFlow(LogMonitorFilter())

    val state: StateFlow<LogsMonitorState> =
        combine(repository.rows, repository.status, filter) { rows, status, currentFilter ->
                LogsMonitorState(
                    status = status,
                    filter = currentFilter,
                    visibleRows = rows.filter(currentFilter).toPersistentList(),
                )
            }
            .stateIn(
                scope = viewModelScope,
                started = SharingStarted.WhileSubscribed(5_000),
                initialValue = LogsMonitorState(),
            )

    fun startCapture() {
        repository.startCapture()
    }

    fun stopCapture() {
        repository.stopCapture()
    }

    fun clear() {
        repository.clear()
    }

    fun setSource(source: LogMonitorSource) {
        filter.update { current -> current.copy(source = source) }
    }

    fun setMinLevel(level: LogMonitorLevel) {
        filter.update { current -> current.copy(minLevel = level) }
    }

    fun setQuery(query: String) {
        filter.update { current -> current.copy(query = query) }
    }

    private fun List<LogMonitorRow>.filter(filter: LogMonitorFilter): List<LogMonitorRow> {
        val normalizedQuery = filter.query.trim()
        return filter { row ->
            row.level.ordinal >= filter.minLevel.ordinal &&
                (filter.source == LogMonitorSource.ALL || row.source == filter.source) &&
                (normalizedQuery.isEmpty() ||
                    row.rawLine.contains(normalizedQuery, ignoreCase = true))
        }
    }
}
