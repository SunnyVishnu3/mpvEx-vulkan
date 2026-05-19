package com.astrixforge.devicemasker.ui.screens.settings

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Save
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.astrixforge.devicemasker.R
import com.astrixforge.devicemasker.data.models.ThemeMode
import com.astrixforge.devicemasker.service.diagnostics.RootAccessState
import com.astrixforge.devicemasker.ui.components.AppModalBottomSheet
import com.astrixforge.devicemasker.ui.components.ScreenHeader
import com.astrixforge.devicemasker.ui.components.expressive.QuickAction
import com.astrixforge.devicemasker.ui.components.expressive.QuickActionRow
import com.astrixforge.devicemasker.ui.theme.DeviceMaskerTheme

/**
 * Settings screen for app preferences.
 *
 * Provides options for:
 * - Theme settings (theme mode, AMOLED dark mode, dynamic colors)
 * - Debug options (log export with save/share options)
 * - About information
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun SettingsScreen(
    modifier: Modifier = Modifier,
    themeMode: ThemeMode = ThemeMode.SYSTEM,
    amoledDarkMode: Boolean = true,
    dynamicColors: Boolean = true,
    isExportingLogs: Boolean = false,
    rootAccessState: RootAccessState = RootAccessState.UNKNOWN,
    exportResult: ExportResult? = null,
    onThemeModeChange: (ThemeMode) -> Unit = {},
    onAmoledDarkModeChange: (Boolean) -> Unit = {},
    onDynamicColorChange: (Boolean) -> Unit = {},
    onExportLogsToUri: (Uri) -> Unit = {},
    onShareLogs: () -> Unit = {},
    onClearExportResult: () -> Unit = {},
    onNavigateToDiagnostics: () -> Unit = {},
    onNavigateToLogsMonitor: () -> Unit = {},
    generateLogFileName: () -> String = { "devicemasker_logs.log" },
) {
    var showThemeModeDialog by rememberSaveable { mutableStateOf(false) }
    var showExportSheet by rememberSaveable { mutableStateOf(false) }
    val snackbarHostState = remember { SnackbarHostState() }
    val buildTypeValue = settingsBuildTypeValue()
    val moduleInfoValue = stringResource(R.string.settings_module_info_value)
    val isDarkModeActive = isDarkModeActive(themeMode)

    val exportLogsLauncher =
        rememberLauncherForActivityResult(
            ActivityResultContracts.CreateDocument("application/zip")
        ) { uri ->
            uri?.let { onExportLogsToUri(it) }
        }

    ExportResultSnackbarEffect(
        exportMessage = exportResultMessage(exportResult),
        snackbarHostState = snackbarHostState,
        onClearExportResult = onClearExportResult,
    )
    SettingsScreenFrame(
        themeMode = themeMode,
        amoledDarkMode = amoledDarkMode,
        dynamicColors = dynamicColors,
        isExportingLogs = isExportingLogs,
        rootAccessState = rootAccessState,
        isDarkModeActive = isDarkModeActive,
        buildTypeValue = buildTypeValue,
        moduleInfoValue = moduleInfoValue,
        snackbarHostState = snackbarHostState,
        onThemeModeClick = { showThemeModeDialog = true },
        onAmoledDarkModeChange = onAmoledDarkModeChange,
        onDynamicColorChange = onDynamicColorChange,
        onExportLogsClick = { if (!isExportingLogs) showExportSheet = true },
        onNavigateToDiagnostics = onNavigateToDiagnostics,
        onNavigateToLogsMonitor = onNavigateToLogsMonitor,
        modifier = modifier,
    )
    SettingsDialogsAndSheets(
        themeMode = themeMode,
        showThemeModeDialog = showThemeModeDialog,
        showExportSheet = showExportSheet,
        onThemeModeChange = onThemeModeChange,
        themeModeDialogChanged = { showThemeModeDialog = it },
        exportSheetChanged = { showExportSheet = it },
        exportLogsLauncher = exportLogsLauncher,
        generateLogFileName = generateLogFileName,
        onShareLogs = onShareLogs,
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
@Suppress("FunctionNaming")
internal fun ExportActionsBottomSheetContent(
    title: String,
    saveLabel: String,
    shareLabel: String,
    onSave: () -> Unit,
    onShare: () -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    AppModalBottomSheet(onDismiss = onDismiss, modifier = modifier, title = title) {
        QuickActionRow(
            primaryAction =
                QuickAction(
                    label = saveLabel,
                    icon = Icons.Outlined.Save,
                    onClick = {
                        onSave()
                        onDismiss()
                    },
                ),
            secondaryAction =
                QuickAction(
                    label = shareLabel,
                    icon = Icons.Outlined.Share,
                    onClick = {
                        onShare()
                        onDismiss()
                    },
                ),
        )
    }
}

@Composable
internal fun SettingsScreenContent(
    themeMode: ThemeMode,
    amoledDarkMode: Boolean,
    dynamicColors: Boolean,
    isExportingLogs: Boolean,
    rootAccessState: RootAccessState,
    isDarkModeActive: Boolean,
    buildTypeValue: String,
    moduleInfoValue: String,
    modifier: Modifier = Modifier,
    onThemeModeClick: () -> Unit = {},
    onAmoledDarkModeChange: (Boolean) -> Unit = {},
    onDynamicColorChange: (Boolean) -> Unit = {},
    onExportLogsClick: () -> Unit = {},
    onNavigateToDiagnostics: () -> Unit = {},
    onNavigateToLogsMonitor: () -> Unit = {},
) {
    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item(key = "header") { ScreenHeader(title = stringResource(id = R.string.settings_title)) }

        item(key = "appearance") {
            AppearanceSettingsSection(
                themeMode = themeMode,
                amoledDarkMode = amoledDarkMode,
                dynamicColors = dynamicColors,
                isDarkModeActive = isDarkModeActive,
                onThemeModeClick = onThemeModeClick,
                onAmoledDarkModeChange = onAmoledDarkModeChange,
                onDynamicColorChange = onDynamicColorChange,
            )
        }

        item(key = "debug") {
            DebugSettingsSection(
                rootAccessState = rootAccessState,
                isExportingLogs = isExportingLogs,
                onExportLogsClick = onExportLogsClick,
                onNavigateToDiagnostics = onNavigateToDiagnostics,
                onNavigateToLogsMonitor = onNavigateToLogsMonitor,
            )
        }

        item(key = "about") {
            AboutSettingsSection(buildTypeValue = buildTypeValue, moduleInfoValue = moduleInfoValue)
        }

        item(key = "bottom_spacing") { Spacer(modifier = Modifier.height(24.dp)) }
    }
}

@Preview(showBackground = true, backgroundColor = 0xFF000000)
@Composable
private fun SettingsScreenPreview() {
    DeviceMaskerTheme { SettingsScreen() }
}
