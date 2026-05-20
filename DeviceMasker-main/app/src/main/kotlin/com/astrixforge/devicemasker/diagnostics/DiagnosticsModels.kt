package com.astrixforge.devicemasker.diagnostics

import androidx.compose.runtime.Immutable
import com.astrixforge.devicemasker.common.SpoofType

/** Data class representing a diagnostic result. */
@Immutable
data class DiagnosticResult(
    val type: SpoofType,
    val realValue: String?,
    val spoofedValue: String?,
    val isActive: Boolean,
    val isSpoofed: Boolean,
) {
    val status: DiagnosticStatus
        get() =
            when {
                !isActive -> DiagnosticStatus.INACTIVE
                isSpoofed -> DiagnosticStatus.SUCCESS
                else -> DiagnosticStatus.WARNING
            }
}

enum class DiagnosticStatus {
    SUCCESS,
    WARNING,
    INACTIVE,
}

/** Anti-detection test result. */
@Immutable
data class AntiDetectionTest(val nameRes: Int, val descriptionRes: Int, val isPassed: Boolean)
