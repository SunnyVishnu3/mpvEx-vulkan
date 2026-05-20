package com.astrixforge.devicemasker.ui.screens.groups

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Groups
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.outlined.FileDownload
import androidx.compose.material.icons.outlined.FileUpload
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FloatingActionButtonMenu
import androidx.compose.material3.FloatingActionButtonMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.ToggleFloatingActionButton
import androidx.compose.material3.ToggleFloatingActionButtonDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.compose.dropUnlessResumed
import androidx.lifecycle.viewmodel.compose.viewModel
import com.astrixforge.devicemasker.R
import com.astrixforge.devicemasker.common.AppConfig
import com.astrixforge.devicemasker.data.models.SpoofGroup
import com.astrixforge.devicemasker.data.repository.ISpoofRepository
import com.astrixforge.devicemasker.ui.components.EmptyState
import com.astrixforge.devicemasker.ui.components.GroupCard
import com.astrixforge.devicemasker.ui.components.ScreenHeader
import com.astrixforge.devicemasker.ui.components.expressive.ExpressivePullToRefresh
import com.astrixforge.devicemasker.ui.navigation.groupsViewModelFactory
import com.astrixforge.devicemasker.ui.theme.DeviceMaskerTheme
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.ImmutableMap
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.persistentMapOf

/**
 * Screen for managing spoof groups.
 *
 * Features:
 * - List of saved groups
 * - Create new group via FAB
 * - Edit group details
 * - Delete groups (except default)
 * - Set group as default
 * - Export/Import groups as JSON
 *
 * @param viewModel The GroupsViewModel for state management
 * @param onGroupClick Callback when a group is clicked
 * @param modifier Optional modifier
 */
@Composable
fun GroupsScreen(
    repository: ISpoofRepository,
    onGroupClick: (SpoofGroup) -> Unit,
    modifier: Modifier = Modifier,
    viewModel: GroupsViewModel =
        viewModel(factory = remember(repository) { groupsViewModelFactory(repository) }),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }

    var showCreateDialog by rememberSaveable { mutableStateOf(false) }
    var editGroupId by rememberSaveable { mutableStateOf<String?>(null) }
    var deleteGroupId by rememberSaveable { mutableStateOf<String?>(null) }
    val fileMessages = rememberGroupsFileMessages()
    val exportLauncher =
        rememberGroupsExportLauncher(context, scope, viewModel, snackbarHostState, fileMessages)
    val importLauncher =
        rememberGroupsImportLauncher(context, scope, viewModel, snackbarHostState, fileMessages)

    val timestampFormatter = remember { SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US) }
    GroupsScreenBody(
        groups = state.groups,
        appConfigs = state.appConfigs,
        isRefreshing = state.isRefreshing,
        snackbarHostState = snackbarHostState,
        onGroupClick = onGroupClick,
        onCreateGroup = { showCreateDialog = true },
        onEditGroup = { editGroupId = it.id },
        onDeleteGroup = { deleteGroupId = it.id },
        onSetDefault = { group -> viewModel.setDefaultGroup(group.id) },
        onEnableChange = { group, enabled -> viewModel.setGroupEnabled(group.id, enabled) },
        onExport = {
            val timestamp = timestampFormatter.format(Date())
            exportLauncher.launch("devicemasker_groups_$timestamp.json")
        },
        onImport = { importLauncher.launch("application/json") },
        modifier = modifier,
    )

    GroupsDialogs(
        groups = state.groups,
        showCreateDialog = showCreateDialog,
        editGroupId = editGroupId,
        deleteGroupId = deleteGroupId,
        createDialogChanged = { showCreateDialog = it },
        editGroupChanged = { editGroupId = it },
        deleteGroupChanged = { deleteGroupId = it },
        createGroup = viewModel::createGroup,
        updateGroup = viewModel::updateGroup,
        deleteGroup = viewModel::deleteGroup,
    )
}

/** Stateless content for GroupsScreen. */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun GroupsScreenContent(
    groups: ImmutableList<SpoofGroup>,
    appConfigs: ImmutableMap<String, AppConfig>,
    isRefreshing: Boolean,
    onGroupClick: (SpoofGroup) -> Unit,
    onCreateGroup: () -> Unit,
    onEditGroup: (SpoofGroup) -> Unit,
    onDeleteGroup: (SpoofGroup) -> Unit,
    onSetDefault: (SpoofGroup) -> Unit,
    modifier: Modifier = Modifier,
    onEnableChange: (SpoofGroup, Boolean) -> Unit = { _, _ -> },
    onExport: () -> Unit = {},
    onImport: () -> Unit = {},
) {
    // Track scroll position for FAB animation
    val listState = rememberLazyListState()
    var fabMenuExpanded by rememberSaveable { mutableStateOf(false) }

    Box(modifier = modifier.fillMaxSize()) {
        ExpressivePullToRefresh(isRefreshing = isRefreshing, onRefresh = {}) {
            GroupsList(
                groups = groups,
                appConfigs = appConfigs,
                onGroupClick = onGroupClick,
                onEditGroup = onEditGroup,
                onDeleteGroup = onDeleteGroup,
                onSetDefault = onSetDefault,
                onEnableChange = onEnableChange,
                onExport = onExport,
                onImport = onImport,
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                listState = listState,
            )
        }

        GroupsFabMenu(
            expanded = fabMenuExpanded,
            expandedChanged = { fabMenuExpanded = it },
            onCreateGroup = onCreateGroup,
            onImport = onImport,
            onExport = onExport,
            modifier = Modifier.align(Alignment.BottomEnd).padding(16.dp),
        )
    }
}

@Composable
private fun GroupsHeader(onExport: () -> Unit, onImport: () -> Unit) {
    var showMenu by remember { mutableStateOf(false) }

    ScreenHeader(
        title = stringResource(id = R.string.group_screen_title),
        actions = {
            Box {
                IconButton(onClick = { showMenu = true }) {
                    Icon(
                        imageVector = Icons.Default.MoreVert,
                        contentDescription = stringResource(id = R.string.action_more_options),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                GroupsOverflowMenu(
                    expanded = showMenu,
                    onDismiss = { showMenu = false },
                    onExport = onExport,
                    onImport = onImport,
                )
            }
        },
    )
}

@Composable
private fun GroupsOverflowMenu(
    expanded: Boolean,
    onDismiss: () -> Unit,
    onExport: () -> Unit,
    onImport: () -> Unit,
) {
    DropdownMenu(expanded = expanded, onDismissRequest = onDismiss) {
        DropdownMenuItem(
            text = { Text(stringResource(id = R.string.group_import_groups)) },
            onClick = {
                onDismiss()
                onImport()
            },
            leadingIcon = {
                Icon(imageVector = Icons.Outlined.FileDownload, contentDescription = null)
            },
        )
        DropdownMenuItem(
            text = { Text(stringResource(id = R.string.group_export_groups)) },
            onClick = {
                onDismiss()
                onExport()
            },
            leadingIcon = {
                Icon(imageVector = Icons.Outlined.FileUpload, contentDescription = null)
            },
        )
    }
}

@Composable
private fun GroupsList(
    groups: ImmutableList<SpoofGroup>,
    appConfigs: ImmutableMap<String, AppConfig>,
    onGroupClick: (SpoofGroup) -> Unit,
    onEditGroup: (SpoofGroup) -> Unit,
    onDeleteGroup: (SpoofGroup) -> Unit,
    onSetDefault: (SpoofGroup) -> Unit,
    onEnableChange: (SpoofGroup, Boolean) -> Unit,
    onExport: () -> Unit,
    onImport: () -> Unit,
    listState: androidx.compose.foundation.lazy.LazyListState,
    modifier: Modifier = Modifier,
    contentPadding: PaddingValues = PaddingValues(),
) {
    LazyColumn(
        state = listState,
        modifier = modifier,
        contentPadding = contentPadding,
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item { GroupsHeader(onExport = onExport, onImport = onImport) }
        if (groups.isEmpty()) {
            item { GroupsEmptyState() }
        } else {
            items(items = groups, key = { it.id }) { group ->
                GroupCard(
                    group = group,
                    isEnabled = group.isEnabled,
                    appCount = appConfigs.countAssignedToGroup(group.id),
                    onClick = dropUnlessResumed { onGroupClick(group) },
                    onEdit = { onEditGroup(group) },
                    onDelete = { onDeleteGroup(group) },
                    onSetDefault = { onSetDefault(group) },
                    onEnableChange = { enabled -> onEnableChange(group, enabled) },
                )
            }
            item { Spacer(modifier = Modifier.height(80.dp)) }
        }
    }
}

@Composable
private fun GroupsEmptyState() {
    EmptyState(
        icon = Icons.Default.Groups,
        title = stringResource(id = R.string.group_list_empty),
        subtitle = stringResource(id = R.string.group_create_new),
    )
}

private fun Map<String, AppConfig>.countAssignedToGroup(groupId: String): Int =
    values.count { it.groupId == groupId && it.isEnabled }

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun GroupsFabMenu(
    expanded: Boolean,
    expandedChanged: (Boolean) -> Unit,
    onCreateGroup: () -> Unit,
    onImport: () -> Unit,
    onExport: () -> Unit,
    modifier: Modifier = Modifier,
) {
    FloatingActionButtonMenu(
        expanded = expanded,
        modifier = modifier,
        button = { GroupsFabToggle(expanded = expanded, expandedChanged = expandedChanged) },
    ) {
        FloatingActionButtonMenuItem(
            onClick = {
                expandedChanged(false)
                onCreateGroup()
            },
            text = { Text(stringResource(id = R.string.group_new)) },
            icon = { Icon(imageVector = Icons.Default.Add, contentDescription = null) },
        )
        FloatingActionButtonMenuItem(
            onClick = {
                expandedChanged(false)
                onImport()
            },
            text = { Text(stringResource(id = R.string.group_import_groups)) },
            icon = { Icon(imageVector = Icons.Outlined.FileDownload, contentDescription = null) },
        )
        FloatingActionButtonMenuItem(
            onClick = {
                expandedChanged(false)
                onExport()
            },
            text = { Text(stringResource(id = R.string.group_export_groups)) },
            icon = { Icon(imageVector = Icons.Outlined.FileUpload, contentDescription = null) },
        )
    }
}

@Composable
private fun GroupsFabToggle(expanded: Boolean, expandedChanged: (Boolean) -> Unit) {
    ToggleFloatingActionButton(
        checked = expanded,
        onCheckedChange = expandedChanged,
        contentAlignment = Alignment.Center,
    ) {
        val icon = if (checkedProgress > 0.5f) Icons.Default.Close else Icons.Default.Add
        Icon(
            imageVector = icon,
            contentDescription = stringResource(id = R.string.group_new),
            modifier =
                with(ToggleFloatingActionButtonDefaults) {
                    Modifier.animateIcon({ checkedProgress })
                },
        )
    }
}

// ═══════════════════════════════════════════════════════════
// Previews
// ═══════════════════════════════════════════════════════════

@Preview(showBackground = true, backgroundColor = 0xFF121212)
@Composable
private fun GroupsScreenContentPreview() {
    DeviceMaskerTheme {
        GroupsScreenContent(
            groups =
                persistentListOf(
                    SpoofGroup.createDefaultGroup(),
                    SpoofGroup.createNew("Samsung Galaxy S24"),
                    SpoofGroup.createNew("Pixel 9 Pro"),
                ),
            appConfigs = persistentMapOf(),
            isRefreshing = false,
            onGroupClick = {},
            onCreateGroup = {},
            onEditGroup = {},
            onDeleteGroup = {},
            onSetDefault = {},
        )
    }
}

@Preview(showBackground = true, backgroundColor = 0xFF121212)
@Composable
private fun GroupsScreenEmptyPreview() {
    DeviceMaskerTheme {
        GroupsScreenContent(
            groups = persistentListOf(),
            appConfigs = persistentMapOf(),
            isRefreshing = false,
            onGroupClick = {},
            onCreateGroup = {},
            onEditGroup = {},
            onDeleteGroup = {},
            onSetDefault = {},
        )
    }
}
