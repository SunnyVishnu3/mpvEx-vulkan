package com.astrixforge.devicemasker.ui.screens.settings

import android.os.Build
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Article
import androidx.compose.material.icons.outlined.Code
import androidx.compose.material.icons.outlined.Contrast
import androidx.compose.material.icons.outlined.DarkMode
import androidx.compose.material.icons.outlined.FileDownload
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.LightMode
import androidx.compose.material.icons.outlined.Palette
import androidx.compose.material.icons.outlined.Shield
import androidx.compose.material.icons.outlined.Tune
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.astrixforge.devicemasker.BuildConfig
import com.astrixforge.devicemasker.R
import com.astrixforge.devicemasker.data.models.ThemeMode
import com.astrixforge.devicemasker.service.diagnostics.RootAccessState
import com.astrixforge.devicemasker.ui.components.SettingsClickableItem
import com.astrixforge.devicemasker.ui.components.SettingsClickableItemWithValue
import com.astrixforge.devicemasker.ui.components.SettingsInfoItem
import com.astrixforge.devicemasker.ui.components.SettingsSection
import com.astrixforge.devicemasker.ui.components.SettingsSwitchItem
import com.astrixforge.devicemasker.ui.components.expressive.CompactLoadingIndicator

@Composable
internal fun AppearanceSettingsSection(
    themeMode: ThemeMode,
    amoledDarkMode: Boolean,
    dynamicColors: Boolean,
    isDarkModeActive: Boolean,
    onThemeModeClick: () -> Unit,
    onAmoledDarkModeChange: (Boolean) -> Unit,
    onDynamicColorChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    SettingsSection(
        title = stringResource(id = R.string.settings_appearance),
        modifier = modifier,
    ) {
        SettingsClickableItemWithValue(
            icon = themeMode.icon(),
            title = stringResource(id = R.string.settings_theme_mode),
            description = stringResource(id = themeMode.displayNameRes),
            onClick = onThemeModeClick,
        )
        AnimatedVisibility(
            visible = isDarkModeActive,
            enter = expandVertically(),
            exit = shrinkVertically(),
        ) {
            AmoledModeSetting(
                amoledDarkMode = amoledDarkMode,
                onAmoledDarkModeChange = onAmoledDarkModeChange,
            )
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            Spacer(modifier = Modifier.height(8.dp))
            SettingsSwitchItem(
                icon = Icons.Outlined.Palette,
                title = stringResource(id = R.string.settings_dynamic_colors),
                description = stringResource(id = R.string.settings_dynamic_description),
                checked = dynamicColors,
                onCheckedChange = onDynamicColorChange,
            )
        }
    }
}

@Composable
private fun AmoledModeSetting(amoledDarkMode: Boolean, onAmoledDarkModeChange: (Boolean) -> Unit) {
    Column {
        Spacer(modifier = Modifier.height(8.dp))
        SettingsSwitchItem(
            icon = Icons.Outlined.Contrast,
            title = stringResource(id = R.string.settings_amoled_mode),
            description = stringResource(id = R.string.settings_amoled_description),
            checked = amoledDarkMode,
            onCheckedChange = onAmoledDarkModeChange,
        )
    }
}

@Composable
internal fun DebugSettingsSection(
    rootAccessState: RootAccessState,
    isExportingLogs: Boolean,
    onExportLogsClick: () -> Unit,
    onNavigateToDiagnostics: () -> Unit,
    onNavigateToLogsMonitor: () -> Unit,
    modifier: Modifier = Modifier,
) {
    SettingsSection(title = stringResource(id = R.string.settings_debug), modifier = modifier) {
        SettingsInfoItem(
            icon = Icons.Outlined.Shield,
            title = stringResource(id = R.string.root_access_status_title),
            value = stringResource(id = rootAccessState.labelRes()),
        )
        Spacer(modifier = Modifier.height(8.dp))
        SettingsClickableItem(
            icon = Icons.Outlined.FileDownload,
            title = stringResource(id = R.string.settings_export_logs),
            description =
                if (isExportingLogs) {
                    stringResource(id = R.string.settings_exporting)
                } else {
                    stringResource(id = R.string.settings_export_logs_description)
                },
            onClick = { if (!isExportingLogs) onExportLogsClick() },
            trailingContent =
                if (isExportingLogs)
                    ({ CompactLoadingIndicator(modifier = Modifier.padding(end = 8.dp)) })
                else null,
        )
        Spacer(modifier = Modifier.height(8.dp))
        SettingsClickableItem(
            icon = Icons.Outlined.Article,
            title = stringResource(id = R.string.settings_logs_monitor),
            description = stringResource(id = R.string.settings_logs_monitor_description),
            onClick = onNavigateToLogsMonitor,
        )
        Spacer(modifier = Modifier.height(8.dp))
        SettingsClickableItem(
            icon = Icons.Outlined.Shield,
            title = stringResource(id = R.string.settings_diagnostics),
            description = stringResource(id = R.string.settings_diagnostics_description),
            onClick = onNavigateToDiagnostics,
        )
    }
}

@Composable
internal fun AboutSettingsSection(
    buildTypeValue: String,
    moduleInfoValue: String,
    modifier: Modifier = Modifier,
) {
    SettingsSection(title = stringResource(id = R.string.settings_about), modifier = modifier) {
        SettingsInfoItem(
            icon = Icons.Outlined.Info,
            title = stringResource(id = R.string.settings_version),
            value = stringResource(id = R.string.settings_version_info, BuildConfig.VERSION_NAME),
        )
        Spacer(modifier = Modifier.height(8.dp))
        SettingsInfoItem(
            icon = Icons.Outlined.Code,
            title = stringResource(id = R.string.settings_build_type),
            value = buildTypeValue,
        )
        Spacer(modifier = Modifier.height(8.dp))
        SettingsInfoItem(
            icon = Icons.Outlined.Tune,
            title = stringResource(id = R.string.settings_module_info),
            value = moduleInfoValue,
        )
    }
}

private fun ThemeMode.icon() =
    when (this) {
        ThemeMode.DARK -> Icons.Outlined.DarkMode
        ThemeMode.LIGHT -> Icons.Outlined.LightMode
        ThemeMode.SYSTEM -> Icons.Outlined.Contrast
    }

internal fun RootAccessState.labelRes(): Int =
    when (this) {
        RootAccessState.UNKNOWN -> R.string.root_access_status_unknown
        RootAccessState.REQUESTING -> R.string.root_access_status_requesting
        RootAccessState.GRANTED -> R.string.root_access_status_granted
        RootAccessState.DENIED -> R.string.root_access_status_denied
        RootAccessState.UNAVAILABLE -> R.string.root_access_status_unavailable
    }
