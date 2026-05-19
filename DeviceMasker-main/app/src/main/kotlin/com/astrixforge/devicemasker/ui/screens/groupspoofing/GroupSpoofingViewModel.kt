package com.astrixforge.devicemasker.ui.screens.groupspoofing

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.astrixforge.devicemasker.common.AppConfig
import com.astrixforge.devicemasker.common.CorrelationGroup
import com.astrixforge.devicemasker.common.SpoofGroup
import com.astrixforge.devicemasker.common.SpoofType
import com.astrixforge.devicemasker.common.models.Carrier
import com.astrixforge.devicemasker.common.models.LocationConfig
import com.astrixforge.devicemasker.common.withValue
import com.astrixforge.devicemasker.data.models.DeviceIdentifier
import com.astrixforge.devicemasker.data.models.InstalledApp
import com.astrixforge.devicemasker.data.repository.ISpoofRepository
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.toImmutableList
import kotlinx.collections.immutable.toImmutableMap
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * ViewModel for the Group Spoofing screen.
 *
 * Manages group spoofing state and spoof value operations.
 *
 * @param repository The [ISpoofRepository] for data access
 * @param groupId The ID of the group to display
 */
@Suppress("TooManyFunctions")
class GroupSpoofingViewModel(
    private val repository: ISpoofRepository,
    private val groupId: String,
    private val savedStateHandle: SavedStateHandle,
) : ViewModel() {

    private val _state =
        MutableStateFlow(
            GroupSpoofingState(
                selectedTab = savedStateHandle[KEY_SELECTED_TAB] ?: 0,
                spoofTabScrollPosition = savedStateHandle[KEY_SPOOF_TAB_SCROLL] ?: 0,
                appsTabScrollPosition = savedStateHandle[KEY_APPS_TAB_SCROLL] ?: 0,
            )
        )
    val state: StateFlow<GroupSpoofingState> = _state.asStateFlow()

    init {
        viewModelScope.launch {
            repository.groups.collect { groups ->
                val group = groups.find { it.id == groupId }
                _state.update { current ->
                    current
                        .copy(groups = groups.toImmutableList(), group = group, isLoading = false)
                        .withRebuiltAppRows()
                }
            }
        }

        refreshApps(forceRefresh = false)

        viewModelScope.launch {
            repository.appScopeRepository.isLoading.collect { isLoading ->
                _state.update { it.copy(isAppsRefreshing = isLoading) }
            }
        }

        viewModelScope.launch {
            repository.appScopeRepository.installedApps.collect { apps ->
                _state.update { current ->
                    current.copy(installedApps = apps.toImmutableList()).withRebuiltAppRows()
                }
            }
        }

        viewModelScope.launch {
            repository.appConfigs.collect { appConfigs ->
                _state.update { current ->
                    current.copy(appConfigs = appConfigs.toImmutableMap()).withRebuiltAppRows()
                }
            }
        }
    }

    fun setSelectedTab(tab: Int) {
        savedStateHandle[KEY_SELECTED_TAB] = tab
        _state.update { current ->
            if (current.selectedTab == tab) current else current.copy(selectedTab = tab)
        }
    }

    fun regenerateValue(type: SpoofType) {
        val group = state.value.group ?: return
        viewModelScope.launch {
            if (type == SpoofType.DEVICE_PROFILE) {
                val nextPresetId = repository.generateValue(SpoofType.DEVICE_PROFILE)
                repository.updateGroupWithDeviceProfile(group.id, nextPresetId)
                return@launch
            }

            if (type.correlationGroup == CorrelationGroup.DEVICE_HARDWARE) {
                val presetId =
                    group.getValue(SpoofType.DEVICE_PROFILE)
                        ?: repository.generateValue(SpoofType.DEVICE_PROFILE)
                repository.updateGroupWithDeviceProfile(group.id, presetId)
                return@launch
            }

            val correlationGroup = type.correlationGroup
            val newValue =
                if (correlationGroup == CorrelationGroup.SIM_CARD) {
                    repository.regenerateSIMValueOnly(type)
                } else {
                    if (correlationGroup != CorrelationGroup.NONE) {
                        repository.resetCorrelationGroup(correlationGroup)
                    }
                    repository.generateValue(type)
                }

            val updated = group.withValue(type, newValue)
            repository.updateGroup(updated)
        }
    }

    fun regenerateCategory(types: List<SpoofType>, isCorrelated: Boolean) {
        val group = state.value.group ?: return
        viewModelScope.launch {
            if (types.contains(SpoofType.DEVICE_PROFILE)) {
                val nextPresetId = repository.generateValue(SpoofType.DEVICE_PROFILE)
                repository.updateGroupWithDeviceProfile(group.id, nextPresetId)
                return@launch
            }

            // Reset the cache for this correlation group first
            if (isCorrelated) {
                val correlationGroup = types.firstOrNull()?.correlationGroup
                if (correlationGroup != null) {
                    repository.resetCorrelationGroup(correlationGroup)
                }
            }

            // Now regenerate all types in this category
            var updatedGroup = group
            types.forEach { type ->
                val newValue = repository.generateValue(type)
                updatedGroup = updatedGroup.withValue(type, newValue)
            }
            repository.updateGroup(updatedGroup)
        }
    }

    fun toggleSpoofType(type: SpoofType, enabled: Boolean) {
        val group = state.value.group ?: return
        viewModelScope.launch {
            val identifier = group.getIdentifier(type) ?: DeviceIdentifier.createDefault(type)
            val updated = group.withIdentifier(identifier.copy(isEnabled = enabled))
            repository.updateGroup(updated)
        }
    }

    fun regenerateLocation() {
        val group = state.value.group ?: return
        viewModelScope.launch { repository.regenerateLocationValues(group.id) }
    }

    fun updateCarrier(carrier: Carrier) {
        val group = state.value.group ?: return
        viewModelScope.launch { repository.updateGroupWithCarrier(group.id, carrier) }
    }

    fun updateTimezone(timezoneId: String) {
        val group = state.value.group ?: return
        viewModelScope.launch {
            // Update timezone value
            var updated = group.withValue(SpoofType.TIMEZONE, timezoneId)

            // Sync locale with timezone - get matching locale for the timezone's country
            val locale = LocationConfig.getLocaleForTimezone(timezoneId)
            if (locale != null) {
                updated = updated.withValue(SpoofType.LOCALE, locale)
            }

            repository.updateGroup(updated)
        }
    }

    fun addAppToGroup(packageName: String) {
        viewModelScope.launch { repository.addAppToGroup(groupId, packageName) }
    }

    fun removeAppFromGroup(packageName: String) {
        viewModelScope.launch { repository.removeAppFromGroup(groupId, packageName) }
    }

    fun refreshApps() {
        refreshApps(forceRefresh = true)
    }

    private fun refreshApps(forceRefresh: Boolean) {
        viewModelScope.launch {
            runCatching { repository.appScopeRepository.loadApps(forceRefresh) }
        }
    }

    private fun GroupSpoofingState.withRebuiltAppRows(): GroupSpoofingState =
        copy(appRows = buildAppRows(installedApps, group, groups, appConfigs))

    private fun buildAppRows(
        installedApps: List<InstalledApp>,
        currentGroup: SpoofGroup?,
        allGroups: List<SpoofGroup>,
        appConfigs: Map<String, AppConfig>,
    ): ImmutableList<AppRowModel> {
        val groupsById = allGroups.associateBy { it.id }
        return installedApps
            .map { app ->
                val appConfig = appConfigs[app.packageName]
                val assignedGroupId = appConfig?.groupId
                AppRowModel(
                    app = app,
                    normalizedLabel = app.label.lowercase(),
                    normalizedPackageName = app.packageName.lowercase(),
                    isAssignedToCurrentGroup =
                        currentGroup != null && assignedGroupId == currentGroup.id,
                    isAppEnabled = appConfig?.isEnabled != false,
                    assignedToOtherGroupName =
                        assignedGroupId
                            ?.takeIf { it != currentGroup?.id }
                            ?.let { groupsById[it]?.name },
                )
            }
            .sortedWith(
                compareByDescending<AppRowModel> { it.isAssignedToCurrentGroup }
                    .thenBy { it.normalizedLabel }
            )
            .toImmutableList()
    }

    fun setSpoofTabScrollPosition(position: Int) {
        savedStateHandle[KEY_SPOOF_TAB_SCROLL] = position
        _state.update { it.copy(spoofTabScrollPosition = position) }
    }

    fun setAppsTabScrollPosition(position: Int) {
        savedStateHandle[KEY_APPS_TAB_SCROLL] = position
        _state.update { it.copy(appsTabScrollPosition = position) }
    }

    private companion object {
        const val KEY_SELECTED_TAB = "selectedTab"
        const val KEY_SPOOF_TAB_SCROLL = "spoofTabScroll"
        const val KEY_APPS_TAB_SCROLL = "appsTabScroll"
    }
}
