package com.astrixforge.devicemasker.ui.screens.groups

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.astrixforge.devicemasker.common.AppConfig
import com.astrixforge.devicemasker.data.models.SpoofGroup
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.ImmutableMap

@Composable
internal fun GroupsScreenBody(
    groups: ImmutableList<SpoofGroup>,
    appConfigs: ImmutableMap<String, AppConfig>,
    isRefreshing: Boolean,
    snackbarHostState: SnackbarHostState,
    onGroupClick: (SpoofGroup) -> Unit,
    onCreateGroup: () -> Unit,
    onEditGroup: (SpoofGroup) -> Unit,
    onDeleteGroup: (SpoofGroup) -> Unit,
    onSetDefault: (SpoofGroup) -> Unit,
    onEnableChange: (SpoofGroup, Boolean) -> Unit,
    onExport: () -> Unit,
    onImport: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(modifier = modifier.fillMaxSize()) {
        GroupsScreenContent(
            groups = groups,
            appConfigs = appConfigs,
            isRefreshing = isRefreshing,
            onGroupClick = onGroupClick,
            onCreateGroup = onCreateGroup,
            onEditGroup = onEditGroup,
            onDeleteGroup = onDeleteGroup,
            onSetDefault = onSetDefault,
            onEnableChange = onEnableChange,
            onExport = onExport,
            onImport = onImport,
            modifier = Modifier.fillMaxSize(),
        )
        SnackbarHost(
            hostState = snackbarHostState,
            modifier = Modifier.align(Alignment.BottomCenter).padding(16.dp),
        )
    }
}
