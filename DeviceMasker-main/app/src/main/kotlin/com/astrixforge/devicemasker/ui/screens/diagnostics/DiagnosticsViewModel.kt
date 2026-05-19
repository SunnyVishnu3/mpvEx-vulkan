package com.astrixforge.devicemasker.ui.screens.diagnostics

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.viewModelScope
import com.astrixforge.devicemasker.data.XposedPrefs
import com.astrixforge.devicemasker.data.repository.ISpoofRepository
import com.astrixforge.devicemasker.service.DefaultDiagnosticsProvider
import com.astrixforge.devicemasker.service.IDiagnosticsProvider
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import timber.log.Timber

/**
 * ViewModel for the Diagnostics screen.
 *
 * Config delivery is exclusively via RemotePreferences. Target-process hook proof comes from
 * LSPosed/logcat evidence, not a custom diagnostics Binder bridge.
 */
class DiagnosticsViewModel(
    application: Application,
    private val repository: ISpoofRepository,
    isXposedActiveFlow: StateFlow<Boolean> = XposedPrefs.isServiceConnected,
    private val diagnosticsProvider: IDiagnosticsProvider = DefaultDiagnosticsProvider(application),
    @Suppress("unused") private val savedStateHandle: SavedStateHandle,
) : AndroidViewModel(application) {

    private val _state = MutableStateFlow(DiagnosticsState())
    val state: StateFlow<DiagnosticsState> = _state.asStateFlow()

    init {
        _state.update { it.copy(isXposedActive = isXposedActiveFlow.value) }

        viewModelScope.launch {
            isXposedActiveFlow.collect { connected ->
                _state.update { it.copy(isXposedActive = connected) }
            }
        }

        runDiagnostics()
    }

    /** Pull-to-refresh: re-run all diagnostics and reload service data. */
    fun refresh() {
        _state.update { it.copy(isRefreshing = true) }
        viewModelScope.launch {
            delay(MIN_REFRESH_DURATION_MILLIS)
            runDiagnosticsInternal()
        }
    }

    private fun runDiagnostics() {
        viewModelScope.launch { runDiagnosticsInternal() }
    }

    private suspend fun runDiagnosticsInternal() {
        runCatching {
                val diagnosticResults = diagnosticsProvider.runDiagnosticTests(repository)
                val antiDetectionResults = diagnosticsProvider.runAntiDetectionTests()

                _state.update {
                    it.copy(
                        diagnosticResults = diagnosticResults.toImmutableList(),
                        antiDetectionResults = antiDetectionResults.toImmutableList(),
                        diagnosticsErrorMessage = null,
                        isLoading = false,
                        isRefreshing = false,
                    )
                }
            }
            .onFailure { error ->
                Timber.tag(TAG).e(error, "Diagnostics run failed")
                _state.update {
                    it.copy(
                        diagnosticsErrorMessage =
                            error.message ?: "Diagnostics failed without a message",
                        isLoading = false,
                        isRefreshing = false,
                    )
                }
            }
    }

    private companion object {
        private const val TAG = "DiagnosticsViewModel"
        private const val MIN_REFRESH_DURATION_MILLIS = 400L
    }
}
