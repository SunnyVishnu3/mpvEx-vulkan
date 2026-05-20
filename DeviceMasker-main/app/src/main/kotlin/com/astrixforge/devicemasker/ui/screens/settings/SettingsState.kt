package com.astrixforge.devicemasker.ui.screens.settings

import androidx.compose.runtime.Immutable
import com.astrixforge.devicemasker.data.models.ThemeMode

/** UI state for the Settings screen. */
@Immutable
data class SettingsState(
    val themeMode: ThemeMode = ThemeMode.SYSTEM,
    val amoledMode: Boolean = true,
    val dynamicColors: Boolean = true,
    val isExportingLogs: Boolean = false,
    val redactionChoice: RedactionChoice = RedactionChoice.REDACTED,
    val exportResult: ExportResult? = null,
)

enum class RedactionChoice {
    REDACTED,
    UNREDACTED_REQUIRES_CONFIRMATION,
}

/** Result of a log export operation. */
@Immutable
sealed class ExportResult {
    data class Success(val filePath: String, val lineCount: Int) : ExportResult()

    data class Error(val message: String) : ExportResult()
}
