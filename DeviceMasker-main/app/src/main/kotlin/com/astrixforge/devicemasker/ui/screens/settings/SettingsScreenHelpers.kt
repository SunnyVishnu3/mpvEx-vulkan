package com.astrixforge.devicemasker.ui.screens.settings

import androidx.activity.compose.ManagedActivityResultLauncher
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import com.astrixforge.devicemasker.BuildConfig
import com.astrixforge.devicemasker.R
import com.astrixforge.devicemasker.data.models.ThemeMode
import com.astrixforge.devicemasker.service.diagnostics.RootAccessState
import com.astrixforge.devicemasker.ui.components.dialog.ThemeModeDialog

@Composable
internal fun settingsBuildTypeValue(): String =
    stringResource(
        if (BuildConfig.DEBUG) {
            R.string.settings_build_type_debug
        } else {
            R.string.settings_build_type_release
        }
    )

@Composable
internal fun isDarkModeActive(themeMode: ThemeMode): Boolean {
    val isSystemDark = isSystemInDarkTheme()
    return when (themeMode) {
        ThemeMode.SYSTEM -> isSystemDark
        ThemeMode.DARK -> true
        ThemeMode.LIGHT -> false
    }
}

@Composable
internal fun exportResultMessage(exportResult: ExportResult?): String? =
    when (exportResult) {
        is ExportResult.Success ->
            pluralStringResource(
                R.plurals.settings_export_logs_success,
                exportResult.lineCount,
                exportResult.lineCount,
                exportResult.filePath,
            )
        is ExportResult.Error ->
            stringResource(R.string.settings_export_logs_error, exportResult.message)
        null -> null
    }

@Composable
internal fun ExportResultSnackbarEffect(
    exportMessage: String?,
    snackbarHostState: SnackbarHostState,
    onClearExportResult: () -> Unit,
) {
    val currentOnClearExportResult = rememberUpdatedState(onClearExportResult)
    LaunchedEffect(exportMessage) {
        exportMessage?.let {
            snackbarHostState.showSnackbar(it)
            currentOnClearExportResult.value()
        }
    }
}

@Composable
internal fun SettingsScreenFrame(
    themeMode: ThemeMode,
    amoledDarkMode: Boolean,
    dynamicColors: Boolean,
    isExportingLogs: Boolean,
    rootAccessState: RootAccessState,
    isDarkModeActive: Boolean,
    buildTypeValue: String,
    moduleInfoValue: String,
    snackbarHostState: SnackbarHostState,
    onThemeModeClick: () -> Unit,
    onAmoledDarkModeChange: (Boolean) -> Unit,
    onDynamicColorChange: (Boolean) -> Unit,
    onExportLogsClick: () -> Unit,
    onNavigateToDiagnostics: () -> Unit,
    onNavigateToLogsMonitor: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(modifier = modifier) {
        SettingsScreenContent(
            themeMode = themeMode,
            amoledDarkMode = amoledDarkMode,
            dynamicColors = dynamicColors,
            isExportingLogs = isExportingLogs,
            rootAccessState = rootAccessState,
            isDarkModeActive = isDarkModeActive,
            buildTypeValue = buildTypeValue,
            moduleInfoValue = moduleInfoValue,
            onThemeModeClick = onThemeModeClick,
            onAmoledDarkModeChange = onAmoledDarkModeChange,
            onDynamicColorChange = onDynamicColorChange,
            onExportLogsClick = onExportLogsClick,
            onNavigateToDiagnostics = onNavigateToDiagnostics,
            onNavigateToLogsMonitor = onNavigateToLogsMonitor,
        )
        SnackbarHost(
            hostState = snackbarHostState,
            modifier = Modifier.align(Alignment.BottomCenter),
        )
    }
}

@Composable
internal fun SettingsDialogsAndSheets(
    themeMode: ThemeMode,
    showThemeModeDialog: Boolean,
    showExportSheet: Boolean,
    onThemeModeChange: (ThemeMode) -> Unit,
    themeModeDialogChanged: (Boolean) -> Unit,
    exportSheetChanged: (Boolean) -> Unit,
    exportLogsLauncher: ManagedActivityResultLauncher<String, android.net.Uri?>,
    generateLogFileName: () -> String,
    onShareLogs: () -> Unit,
) {
    if (showThemeModeDialog) {
        ThemeModeDialog(
            currentMode = themeMode,
            onModeSelect = { mode ->
                onThemeModeChange(mode)
                themeModeDialogChanged(false)
            },
            onDismiss = { themeModeDialogChanged(false) },
        )
    }
    if (showExportSheet) {
        ExportActionsBottomSheetContent(
            title = stringResource(R.string.settings_export_sheet_title),
            saveLabel = stringResource(R.string.settings_export_save),
            shareLabel = stringResource(R.string.settings_export_share),
            onSave = { exportLogsLauncher.launch(generateLogFileName()) },
            onShare = onShareLogs,
            onDismiss = { exportSheetChanged(false) },
        )
    }
}
