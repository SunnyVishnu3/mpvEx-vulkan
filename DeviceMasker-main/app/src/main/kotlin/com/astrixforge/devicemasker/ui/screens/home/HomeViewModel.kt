package com.astrixforge.devicemasker.ui.screens.home

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.astrixforge.devicemasker.common.AppConfig
import com.astrixforge.devicemasker.common.enabledCount
import com.astrixforge.devicemasker.data.XposedPrefs
import com.astrixforge.devicemasker.data.XposedScopeState
import com.astrixforge.devicemasker.data.models.InstalledApp
import com.astrixforge.devicemasker.data.models.SpoofGroup
import com.astrixforge.devicemasker.data.repository.ISpoofRepository
import kotlinx.collections.immutable.toImmutableList
import kotlinx.collections.immutable.toImmutableMap
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * ViewModel for the Home screen.
 *
 * Manages the UI state by collecting from repository flows and provides action methods for UI
 * events.
 *
 * @param repository The [ISpoofRepository] for data access
 * @param isXposedActiveFlow Flow that emits Xposed service connection state
 */
class HomeViewModel(
    private val repository: ISpoofRepository,
    private val isXposedActiveFlow: StateFlow<Boolean> = XposedPrefs.isServiceConnected,
    private val xposedScopeStateFlow: StateFlow<XposedScopeState> = XposedPrefs.scopedPackages,
    @Suppress("unused") private val savedStateHandle: SavedStateHandle,
) : ViewModel() {

    private val _state = MutableStateFlow(HomeState())
    val state: StateFlow<HomeState> = _state.asStateFlow()
    private val isScopedAppsRefreshing = MutableStateFlow(false)

    init {
        viewModelScope.launch {
            xposedScopeStateFlow.collect { scopeState ->
                repository.appScopeRepository.loadScopedApps(
                    packageNames = scopeState.packageNamesOrEmpty(),
                    forceRefresh = false,
                )
            }
        }
        viewModelScope.launch {
            combine(
                    isXposedActiveFlow,
                    repository.moduleEnabled,
                    isScopedAppsRefreshing,
                    combine(
                        repository.groups,
                        repository.activeGroup,
                        repository.appConfigs,
                        repository.appScopeRepository.scopedAppMetadata,
                        xposedScopeStateFlow,
                    ) { groups, activeGroup, appConfigs, scopedMetadata, xposedScopeState ->
                        GroupFlows(
                            groups = groups,
                            activeGroup = activeGroup,
                            appConfigs = appConfigs,
                            scopedMetadata = scopedMetadata,
                            xposedScopeState = xposedScopeState,
                        )
                    },
                ) { connected, moduleEnabled, scopedAppsRefreshing, inner ->
                    val selectedGroup =
                        inner.activeGroup
                            ?: inner.groups.find { it.isDefault }
                            ?: inner.groups.firstOrNull()
                    HomeState(
                        isXposedActive = connected,
                        isModuleEnabled = moduleEnabled,
                        groups = inner.groups.toImmutableList(),
                        appConfigs = inner.appConfigs.toImmutableMap(),
                        selectedGroup = selectedGroup,
                        maskedIdentifiersCount = selectedGroup?.enabledCount() ?: 0,
                        enabledAppsCount =
                            if (selectedGroup?.isEnabled == true) {
                                inner.appConfigs.countAssignedToGroup(selectedGroup.id)
                            } else 0,
                        scopedApps =
                            buildHomeScopedApps(
                                scopeState = inner.xposedScopeState,
                                scopedAppMetadata = inner.scopedMetadata,
                                appConfigs = inner.appConfigs,
                                groups = inner.groups,
                            ),
                        isScopedAppsRefreshing = scopedAppsRefreshing,
                        isLoading = false,
                    )
                }
                .collect { homeState -> _state.value = homeState }
        }
    }

    /** Toggle module enabled/disabled state. */
    fun setModuleEnabled(enabled: Boolean) {
        viewModelScope.launch { repository.setModuleEnabled(enabled) }
    }

    /** Select a group from the dropdown. */
    fun selectGroup(groupId: String) {
        viewModelScope.launch {
            repository.setActiveGroup(groupId)
            _state.update { currentState ->
                val group = currentState.groups.find { it.id == groupId }
                currentState.copy(
                    selectedGroup = group,
                    enabledAppsCount =
                        if (group?.isEnabled == true) {
                            currentState.appConfigs.countAssignedToGroup(group.id)
                        } else 0,
                    maskedIdentifiersCount = group?.enabledCount() ?: 0,
                )
            }
        }
    }

    fun setAppEnabled(packageName: String, enabled: Boolean) {
        viewModelScope.launch { repository.setAppEnabled(packageName, enabled) }
    }

    fun refreshScopedApps() {
        viewModelScope.launch {
            isScopedAppsRefreshing.value = true
            try {
                repository.appScopeRepository.loadScopedApps(
                    packageNames = xposedScopeStateFlow.value.packageNamesOrEmpty(),
                    forceRefresh = true,
                )
                XposedPrefs.refreshScope()
            } finally {
                isScopedAppsRefreshing.value = false
            }
        }
    }

    /**
     * Regenerate all values for the selected group.
     *
     * @param onComplete Callback invoked after regeneration starts
     */
    fun regenerateAll(onComplete: () -> Unit = {}) {
        viewModelScope.launch {
            state.value.selectedGroup?.let { group ->
                repository.setActiveGroup(group.id)
                repository.regenerateAllValues(group.id)
            }
            onComplete()
        }
    }
}

private fun Map<String, AppConfig>.countAssignedToGroup(groupId: String): Int =
    values.count { it.groupId == groupId && it.isEnabled }

private data class GroupFlows(
    val groups: List<SpoofGroup>,
    val activeGroup: SpoofGroup?,
    val appConfigs: Map<String, AppConfig>,
    val scopedMetadata: Map<String, InstalledApp>,
    val xposedScopeState: XposedScopeState,
)

private fun XposedScopeState.packageNamesOrEmpty(): Set<String> =
    when (this) {
        is XposedScopeState.Connected -> packages
        XposedScopeState.Disconnected,
        is XposedScopeState.Error -> emptySet()
    }
