package com.astrixforge.devicemasker.ui.screens.settings

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.astrixforge.devicemasker.data.ISettingsDataStore
import com.astrixforge.devicemasker.data.models.ThemeMode
import com.astrixforge.devicemasker.service.ILogManager
import com.astrixforge.devicemasker.service.LogExportResult
import com.astrixforge.devicemasker.service.LogManager
import com.astrixforge.devicemasker.service.ShareableLogResult
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * ViewModel for the Settings screen.
 *
 * Manages theme settings and log export functionality.
 *
 * @param application Application for context access
 * @param settingsStore The [ISettingsDataStore] for persistence
 * @param logManager The [ILogManager] for log operations
 * @param ioDispatcher Dispatcher for IO operations (overridable in tests)
 */
class SettingsViewModel(
    application: Application,
    private val settingsStore: ISettingsDataStore,
    private val logManager: ILogManager = LogManager,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
) : AndroidViewModel(application) {

    private val _state = MutableStateFlow(SettingsState())
    val state: StateFlow<SettingsState> = _state.asStateFlow()

    init {
        // Collect theme mode
        viewModelScope.launch {
            settingsStore.themeMode.collect { mode -> _state.update { it.copy(themeMode = mode) } }
        }

        // Collect AMOLED mode
        viewModelScope.launch {
            settingsStore.amoledMode.collect { enabled ->
                _state.update { it.copy(amoledMode = enabled) }
            }
        }

        // Collect dynamic colors
        viewModelScope.launch {
            settingsStore.dynamicColors.collect { enabled ->
                _state.update { it.copy(dynamicColors = enabled) }
            }
        }
    }

    fun setThemeMode(mode: ThemeMode) {
        viewModelScope.launch { settingsStore.setThemeMode(mode) }
    }

    fun setAmoledMode(enabled: Boolean) {
        viewModelScope.launch { settingsStore.setAmoledMode(enabled) }
    }

    fun setDynamicColors(enabled: Boolean) {
        viewModelScope.launch { settingsStore.setDynamicColors(enabled) }
    }

    // ═══════════════════════════════════════════════════════════
    // EXPORT LOGS
    // ═══════════════════════════════════════════════════════════

    /** Exports app-owned structured logs and available diagnostics logs to a custom URI. */
    fun exportLogsToUri(uri: Uri) {
        viewModelScope.launch {
            _state.update { it.copy(isExportingLogs = true, exportResult = null) }

            val result =
                withContext(ioDispatcher) {
                    logManager.exportLogsToUri(getApplication(), uri).toExportResult()
                }

            _state.update { it.copy(isExportingLogs = false, exportResult = result) }
        }
    }

    /**
     * Creates a shareable log file and returns the result. The caller should use the URI to launch
     * a share intent.
     */
    fun createShareableLogs(onResult: (ShareableLogResult) -> Unit) {
        viewModelScope.launch {
            _state.update { it.copy(isExportingLogs = true, exportResult = null) }

            val result =
                withContext(ioDispatcher) { logManager.createShareableLogFile(getApplication()) }

            _state.update { it.copy(isExportingLogs = false) }
            onResult(result)
        }
    }

    // ═══════════════════════════════════════════════════════════
    // UTILITY FUNCTIONS
    // ═══════════════════════════════════════════════════════════

    fun clearExportResult() {
        _state.update { it.copy(exportResult = null) }
    }

    /** Generate filename for Export Logs file picker */
    fun generateLogFileName(): String = logManager.generateLogFileName()

    private fun LogExportResult.toExportResult(): ExportResult {
        return when (this) {
            is LogExportResult.Success -> ExportResult.Success(filePath, lineCount)
            is LogExportResult.Error -> ExportResult.Error(message)
        }
    }
}
