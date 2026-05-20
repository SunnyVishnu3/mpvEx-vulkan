package com.astrixforge.devicemasker.ui

import android.content.Context
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import com.astrixforge.devicemasker.R
import com.astrixforge.devicemasker.data.SettingsDataStore
import com.astrixforge.devicemasker.data.models.ThemeMode
import com.astrixforge.devicemasker.data.repository.SpoofRepository
import com.astrixforge.devicemasker.service.diagnostics.RootAccessState
import com.astrixforge.devicemasker.ui.navigation.DeviceMaskerNavigator
import com.astrixforge.devicemasker.ui.navigation.NavDestination
import com.astrixforge.devicemasker.ui.screens.diagnostics.DiagnosticsScreen
import com.astrixforge.devicemasker.ui.screens.groups.GroupsScreen
import com.astrixforge.devicemasker.ui.screens.groupspoofing.GroupSpoofingScreenContent
import com.astrixforge.devicemasker.ui.screens.home.HomeScreen
import com.astrixforge.devicemasker.ui.screens.logsmonitor.LogsMonitorScreen

@Composable
internal fun HomeDestinationEntry(repository: SpoofRepository, navigator: DeviceMaskerNavigator) {
    HomeScreen(
        repository = repository,
        onNavigateToSpoof = { navigator.navigateTopLevel(NavDestination.Groups) },
        onRegenerateAll = {},
        onNavigateToGroup = navigator::navigateToGroup,
    )
}

@Composable
internal fun SettingsDestinationEntry(
    context: Context,
    settingsStore: SettingsDataStore,
    themeMode: ThemeMode,
    amoledMode: Boolean,
    dynamicColors: Boolean,
    rootAccessState: RootAccessState,
    navigator: DeviceMaskerNavigator,
) {
    SettingsEntry(
        application = context.applicationContext as android.app.Application,
        settingsStore = settingsStore,
        themeMode = themeMode,
        amoledMode = amoledMode,
        dynamicColors = dynamicColors,
        rootAccessState = rootAccessState,
        navigator = navigator,
    )
}

@Composable
internal fun GroupSpoofingDestinationEntry(
    repository: SpoofRepository,
    groupId: String,
    onNavigateBack: () -> Unit,
) {
    GroupSpoofingScreenContent(
        repository = repository,
        groupId = groupId,
        onNavigateBack = onNavigateBack,
    )
}

@Composable
internal fun GroupsDestinationEntry(repository: SpoofRepository, navigator: DeviceMaskerNavigator) {
    GroupsScreen(
        repository = repository,
        onGroupClick = { group -> navigator.navigateToGroup(group.id) },
    )
}

@Composable
internal fun DiagnosticsDestinationEntry(
    context: Context,
    repository: SpoofRepository,
    onNavigateBack: () -> Unit,
) {
    DiagnosticsScreen(
        application = context.applicationContext as android.app.Application,
        repository = repository,
        onNavigateBack = onNavigateBack,
    )
}

@Composable
internal fun LogsMonitorDestinationEntry(context: Context, onNavigateBack: () -> Unit) {
    LogsMonitorScreen(
        application = context.applicationContext as android.app.Application,
        onNavigateBack = onNavigateBack,
    )
}

@Composable
internal fun GroupDetailPlaceholder(modifier: Modifier = Modifier) {
    Box(modifier = modifier.fillMaxSize().wrapContentSize()) {
        Text(
            text = stringResource(R.string.group_empty_title),
            style = MaterialTheme.typography.titleMedium,
        )
    }
}
