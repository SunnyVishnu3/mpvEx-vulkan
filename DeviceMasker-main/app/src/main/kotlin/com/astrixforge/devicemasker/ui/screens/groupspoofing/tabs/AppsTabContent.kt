package com.astrixforge.devicemasker.ui.screens.groupspoofing.tabs

import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.astrixforge.devicemasker.BuildConfig
import com.astrixforge.devicemasker.R
import com.astrixforge.devicemasker.data.models.InstalledApp
import com.astrixforge.devicemasker.ui.components.AppListItem
import com.astrixforge.devicemasker.ui.components.ClearFocusOnImeDismiss
import com.astrixforge.devicemasker.ui.components.EmptyState
import com.astrixforge.devicemasker.ui.components.expressive.ExpressiveLoadingIndicatorWithLabel
import com.astrixforge.devicemasker.ui.components.expressive.ExpressivePullToRefresh
import com.astrixforge.devicemasker.ui.screens.groupspoofing.AppRowModel
import com.astrixforge.devicemasker.ui.theme.DeviceMaskerTheme
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.debounce

@OptIn(FlowPreview::class)
@Composable
fun AppsTabContent(
    appRows: ImmutableList<AppRowModel>,
    hasInstalledApps: Boolean,
    assignedCount: Int,
    onAppToggle: (InstalledApp, Boolean) -> Unit,
    isRefreshing: Boolean,
    onRefresh: () -> Unit,
    modifier: Modifier = Modifier,
    initialScrollPosition: Int = 0,
    onScrollPositionChange: (Int) -> Unit = {},
) {
    val focusManager = LocalFocusManager.current
    var searchQuery by rememberSaveable { mutableStateOf("") }
    var debouncedQuery by remember { mutableStateOf("") }
    var showSystemApps by rememberSaveable { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        snapshotFlow { searchQuery }
            .debounce(SEARCH_DEBOUNCE_MILLIS)
            .collect { debouncedQuery = it }
    }

    val filteredRows =
        rememberFilteredRows(
            appRows = appRows,
            debouncedQuery = debouncedQuery,
            showSystemApps = showSystemApps,
        )

    Column(
        modifier =
            modifier.fillMaxSize().pointerInput(Unit) {
                detectTapGestures { focusManager.clearFocus() }
            }
    ) {
        AppsSearchHeader(
            state =
                AppsHeaderState(
                    searchQuery = searchQuery,
                    showSystemApps = showSystemApps,
                    filteredCount = filteredRows.size,
                    assignedCount = assignedCount,
                ),
            queryChanged = { searchQuery = it },
            showSystemAppsChanged = { showSystemApps = it },
        )
        ExpressivePullToRefresh(isRefreshing = isRefreshing, onRefresh = onRefresh) {
            AppsTabBody(
                hasInstalledApps = hasInstalledApps,
                filteredRows = filteredRows,
                initialScrollPosition = initialScrollPosition,
                onScrollPositionChange = onScrollPositionChange,
                onAppToggle = { app, checked ->
                    focusManager.clearFocus()
                    onAppToggle(app, checked)
                },
            )
        }
    }
}

private const val SEARCH_DEBOUNCE_MILLIS = 300L

@Immutable
private data class AppsHeaderState(
    val searchQuery: String,
    val showSystemApps: Boolean,
    val filteredCount: Int,
    val assignedCount: Int,
)

@Composable
private fun rememberFilteredRows(
    appRows: ImmutableList<AppRowModel>,
    debouncedQuery: String,
    showSystemApps: Boolean,
): ImmutableList<AppRowModel> {
    val query = debouncedQuery.lowercase()
    return remember(appRows, query, showSystemApps) {
        appRows
            .asSequence()
            .filter { it.matches(query, showSystemApps, BuildConfig.APPLICATION_ID) }
            .toList()
            .toImmutableList()
    }
}

@Composable
private fun AppsSearchHeader(
    state: AppsHeaderState,
    queryChanged: (String) -> Unit,
    showSystemAppsChanged: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    val focusManager = LocalFocusManager.current
    var isSearchFocused by remember { mutableStateOf(false) }

    ClearFocusOnImeDismiss(isFocused = isSearchFocused, clearFocus = { focusManager.clearFocus() })

    Column(
        modifier = modifier.fillMaxWidth().padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        OutlinedTextField(
            value = state.searchQuery,
            onValueChange = queryChanged,
            placeholder = { Text(stringResource(id = R.string.group_spoofing_search_hint)) },
            leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
            trailingIcon = {
                if (state.searchQuery.isNotEmpty()) {
                    IconButton(onClick = { queryChanged("") }) {
                        Icon(
                            imageVector = Icons.Filled.Close,
                            contentDescription =
                                stringResource(id = R.string.group_spoofing_clear_search),
                        )
                    }
                }
            },
            modifier =
                Modifier.fillMaxWidth().onFocusChanged { focusState ->
                    isSearchFocused = focusState.isFocused
                },
            singleLine = true,
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
            keyboardActions = KeyboardActions(onSearch = { focusManager.clearFocus() }),
            shape = RoundedCornerShape(12.dp),
        )
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text =
                    pluralStringResource(
                        id = R.plurals.group_spoofing_apps_assigned_stats,
                        count = state.filteredCount,
                        state.filteredCount,
                        state.assignedCount,
                    ),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            FilterChip(
                selected = state.showSystemApps,
                onClick = { showSystemAppsChanged(!state.showSystemApps) },
                label = { Text(stringResource(id = R.string.group_spoofing_show_system_apps)) },
            )
        }
    }
}

@Composable
private fun AppsTabBody(
    hasInstalledApps: Boolean,
    filteredRows: ImmutableList<AppRowModel>,
    initialScrollPosition: Int,
    onScrollPositionChange: (Int) -> Unit,
    onAppToggle: (InstalledApp, Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    when {
        !hasInstalledApps -> LoadingAppsState(modifier = modifier)
        filteredRows.isEmpty() -> EmptyAppsSearchState(modifier = modifier)
        else ->
            AppsList(
                rows = filteredRows,
                initialScrollPosition = initialScrollPosition,
                onScrollPositionChange = onScrollPositionChange,
                onAppToggle = onAppToggle,
                modifier = modifier,
            )
    }
}

@Composable
private fun LoadingAppsState(modifier: Modifier = Modifier) {
    Box(modifier = modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        ExpressiveLoadingIndicatorWithLabel(
            label = stringResource(id = R.string.group_spoofing_loading_apps)
        )
    }
}

@Composable
private fun EmptyAppsSearchState(modifier: Modifier = Modifier) {
    EmptyState(
        icon = Icons.Filled.Search,
        title = stringResource(id = R.string.group_spoofing_no_apps_found),
        subtitle = stringResource(id = R.string.group_spoofing_adjust_search),
        modifier = modifier,
    )
}

@Composable
private fun AppsList(
    rows: ImmutableList<AppRowModel>,
    initialScrollPosition: Int,
    onScrollPositionChange: (Int) -> Unit,
    onAppToggle: (InstalledApp, Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    val listState = rememberLazyListState(initialFirstVisibleItemIndex = initialScrollPosition)
    val currentOnScrollPositionChange by rememberUpdatedState(onScrollPositionChange)

    LaunchedEffect(listState) {
        snapshotFlow { listState.firstVisibleItemIndex }
            .collect { currentOnScrollPositionChange(it) }
    }

    LaunchedEffect(Unit) {
        // Restore scroll position after list is composed
        if (initialScrollPosition > 0) {
            listState.scrollToItem(initialScrollPosition)
        }
    }

    LazyColumn(
        state = listState,
        modifier = modifier,
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        items(items = rows, key = { it.app.packageName }, contentType = { "app_item" }) { row ->
            AppListItem(
                app = row.app,
                isAssigned = row.isAssignedToCurrentGroup,
                isAppEnabled = row.isAppEnabled,
                assignedToOtherGroupName = row.assignedToOtherGroupName,
                onToggle = { checked -> onAppToggle(row.app, checked) },
                modifier = Modifier.fillMaxWidth().animateItem(),
            )
        }
        item(key = "bottom_spacer", contentType = "spacer") {
            Spacer(modifier = Modifier.height(24.dp))
        }
    }
}

// ═══════════════════════════════════════════════════════════
// Previews
// ═══════════════════════════════════════════════════════════

@Preview(showBackground = true, backgroundColor = 0xFF121212)
@Composable
private fun AppsTabContentLoadingPreview() {
    DeviceMaskerTheme {
        AppsTabContent(
            appRows = persistentListOf(),
            hasInstalledApps = false,
            assignedCount = 0,
            onAppToggle = { _, _ -> },
            isRefreshing = false,
            onRefresh = {},
        )
    }
}

@Preview(showBackground = true, backgroundColor = 0xFF121212)
@Composable
private fun AppsTabContentPopulatedPreview() {
    DeviceMaskerTheme {
        val exampleApp =
            InstalledApp(
                "com.example.app1",
                "Example App",
                isSystemApp = false,
                versionName = "1.0",
            )
        val anotherApp =
            InstalledApp(
                "com.example.app2",
                "Another App",
                isSystemApp = false,
                versionName = "2.0",
            )
        val systemApp =
            InstalledApp(
                "com.example.system",
                "System App",
                isSystemApp = true,
                versionName = "1.0",
            )
        AppsTabContent(
            appRows =
                persistentListOf(
                    AppRowModel(
                        app = exampleApp,
                        normalizedLabel = exampleApp.label.lowercase(),
                        normalizedPackageName = exampleApp.packageName.lowercase(),
                        isAssignedToCurrentGroup = true,
                        isAppEnabled = true,
                        assignedToOtherGroupName = null,
                    ),
                    AppRowModel(
                        app = anotherApp,
                        normalizedLabel = anotherApp.label.lowercase(),
                        normalizedPackageName = anotherApp.packageName.lowercase(),
                        isAssignedToCurrentGroup = false,
                        isAppEnabled = true,
                        assignedToOtherGroupName = null,
                    ),
                    AppRowModel(
                        app = systemApp,
                        normalizedLabel = systemApp.label.lowercase(),
                        normalizedPackageName = systemApp.packageName.lowercase(),
                        isAssignedToCurrentGroup = false,
                        isAppEnabled = true,
                        assignedToOtherGroupName = null,
                    ),
                ),
            hasInstalledApps = true,
            assignedCount = 1,
            onAppToggle = { _, _ -> },
            isRefreshing = false,
            onRefresh = {},
        )
    }
}
