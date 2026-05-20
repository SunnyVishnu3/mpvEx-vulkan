package com.astrixforge.devicemasker.ui.screens.diagnostics

import androidx.compose.runtime.Immutable
import com.astrixforge.devicemasker.diagnostics.AntiDetectionTest
import com.astrixforge.devicemasker.diagnostics.DiagnosticResult
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf

/** UI state for the Diagnostics screen. */
@Immutable
data class DiagnosticsState(
    val isLoading: Boolean = true,
    val isRefreshing: Boolean = false,
    val isXposedActive: Boolean = false,
    val diagnosticResults: ImmutableList<DiagnosticResult> = persistentListOf(),
    val antiDetectionResults: ImmutableList<AntiDetectionTest> = persistentListOf(),
    val diagnosticsErrorMessage: String? = null,
    val reproCaptureState: ReproCaptureState = ReproCaptureState.IDLE,
)

enum class ReproCaptureState {
    IDLE,
    CAPTURING,
    STOPPING,
    EXPORT_READY,
    ERROR,
}
