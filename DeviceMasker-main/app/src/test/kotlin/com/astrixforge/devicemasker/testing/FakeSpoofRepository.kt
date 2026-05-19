package com.astrixforge.devicemasker.testing

import com.astrixforge.devicemasker.common.AppConfig
import com.astrixforge.devicemasker.common.CorrelationGroup
import com.astrixforge.devicemasker.common.SpoofGroup
import com.astrixforge.devicemasker.common.SpoofType
import com.astrixforge.devicemasker.common.addApp
import com.astrixforge.devicemasker.common.models.Carrier
import com.astrixforge.devicemasker.common.removeApp
import com.astrixforge.devicemasker.common.withEnabled
import com.astrixforge.devicemasker.common.withValue
import com.astrixforge.devicemasker.data.repository.IAppScopeRepository
import com.astrixforge.devicemasker.data.repository.ISpoofRepository
import com.astrixforge.devicemasker.data.repository.SpoofRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update

/** Fake [ISpoofRepository] for ViewModel testing. */
class FakeSpoofRepository(
    initialGroups: List<SpoofGroup> = emptyList(),
    initialModuleEnabled: Boolean = false,
    initialAppConfigs: Map<String, AppConfig> = emptyMap(),
    override val appScopeRepository: IAppScopeRepository = FakeAppScopeRepository(),
) : ISpoofRepository {

    private val _moduleEnabled = MutableStateFlow(initialModuleEnabled)
    override val moduleEnabled: Flow<Boolean> = _moduleEnabled.asStateFlow()

    private val _groups = MutableStateFlow(initialGroups)
    override val groups: Flow<List<SpoofGroup>> = _groups.asStateFlow()

    private val _appConfigs = MutableStateFlow(initialAppConfigs)
    override val appConfigs: Flow<Map<String, AppConfig>> = _appConfigs.asStateFlow()

    override val activeGroup: Flow<SpoofGroup?> = _groups.map { list -> list.find { it.isDefault } }

    override val enabledAppCount: Flow<Int> =
        combine(_groups, _appConfigs) { groups, appConfigs ->
            appConfigs.values.count { appConfig ->
                appConfig.isEnabled &&
                    groups.any { group -> group.id == appConfig.groupId && group.isEnabled }
            }
        }

    override val dashboardState: Flow<SpoofRepository.DashboardState> =
        combine(moduleEnabled, activeGroup, enabledAppCount, groups) {
            enabled,
            group,
            appCount,
            groupList ->
            SpoofRepository.DashboardState(
                isModuleEnabled = enabled,
                activeGroup = group,
                enabledAppCount = appCount,
                groupCount = groupList.size,
            )
        }

    override suspend fun setModuleEnabled(enabled: Boolean) {
        _moduleEnabled.value = enabled
    }

    override suspend fun setActiveGroup(groupId: String) {
        _groups.update { list ->
            list.map { group ->
                when {
                    group.id == groupId -> group.copy(isDefault = true)
                    group.isDefault -> group.copy(isDefault = false)
                    else -> group
                }
            }
        }
    }

    override fun generateValue(type: SpoofType): String = "fake-${type.name.lowercase()}"

    override fun regenerateSIMValueOnly(type: SpoofType): String =
        "fake-sim-${type.name.lowercase()}"

    override fun resetCorrelations() {
        // no-op
    }

    override fun resetCorrelationGroup(group: CorrelationGroup) {
        // no-op
    }

    override suspend fun regenerateLocationValues(groupId: String) {
        val group = _groups.value.find { it.id == groupId } ?: return
        _groups.update { list ->
            list.map {
                if (it.id == groupId) {
                    it.withValue(SpoofType.TIMEZONE, "America/New_York")
                        .withValue(SpoofType.LOCALE, "en_US")
                } else it
            }
        }
    }

    override suspend fun updateGroupWithCarrier(groupId: String, carrier: Carrier) {
        val group = _groups.value.find { it.id == groupId } ?: return
        _groups.update { list ->
            list.map {
                if (it.id == groupId) {
                    it.withValue(SpoofType.CARRIER_NAME, carrier.name)
                        .withValue(SpoofType.CARRIER_MCC_MNC, carrier.mccMnc)
                        .withValue(SpoofType.SIM_COUNTRY_ISO, carrier.countryIsoLower)
                        .withValue(SpoofType.TIMEZONE, "America/New_York")
                        .withValue(SpoofType.LOCALE, "en_US")
                } else it
            }
        }
    }

    override suspend fun updateGroupWithDeviceProfile(groupId: String, presetId: String) {
        val group = _groups.value.find { it.id == groupId } ?: return
        _groups.update { list ->
            list.map {
                if (it.id == groupId) it.withValue(SpoofType.DEVICE_PROFILE, presetId) else it
            }
        }
    }

    override suspend fun regenerateAllValues(groupId: String) {
        val group = _groups.value.find { it.id == groupId } ?: return
        _groups.update { list ->
            list.map {
                if (it.id == groupId) {
                    SpoofType.entries.fold(it) { g, type -> g.withValue(type, generateValue(type)) }
                } else it
            }
        }
    }

    override fun getAllGroups(): Flow<List<SpoofGroup>> = groups

    override suspend fun createGroup(name: String, description: String) {
        val newGroup =
            SpoofGroup.createNew(name = name, isDefault = _groups.value.isEmpty())
                .copy(description = description)
        _groups.update { it + newGroup }
    }

    override suspend fun updateGroup(group: SpoofGroup) {
        _groups.update { list -> list.map { if (it.id == group.id) group else it } }
    }

    override suspend fun deleteGroup(groupId: String) {
        _groups.update { list -> list.filter { it.id != groupId } }
    }

    override suspend fun setDefaultGroup(groupId: String) {
        setActiveGroup(groupId)
    }

    override suspend fun setGroupEnabled(groupId: String, enabled: Boolean) {
        _groups.update { list ->
            list.map { if (it.id == groupId) it.withEnabled(enabled) else it }
        }
    }

    override suspend fun addAppToGroup(groupId: String, packageName: String) {
        _groups.update { list -> list.map { if (it.id == groupId) it.addApp(packageName) else it } }
        _appConfigs.update { configs ->
            configs +
                (packageName to
                    (configs[packageName]?.copy(groupId = groupId)
                        ?: AppConfig(packageName = packageName, groupId = groupId)))
        }
    }

    override suspend fun removeAppFromGroup(groupId: String, packageName: String) {
        _groups.update { list ->
            list.map { if (it.id == groupId) it.removeApp(packageName) else it }
        }
        _appConfigs.update { configs ->
            configs +
                (packageName to
                    (configs[packageName]?.copy(groupId = null)
                        ?: AppConfig(packageName = packageName, groupId = null)))
        }
    }

    override suspend fun setAppEnabled(packageName: String, enabled: Boolean) {
        _appConfigs.update { configs ->
            configs +
                (packageName to
                    (configs[packageName] ?: AppConfig(packageName = packageName)).copy(
                        isEnabled = enabled
                    ))
        }
    }

    override suspend fun setAppRiskyHooksEnabled(packageName: String, enabled: Boolean) {
        _appConfigs.update { configs ->
            configs +
                (packageName to
                    (configs[packageName] ?: AppConfig(packageName = packageName))
                        .withRiskyHooksEnabled(enabled))
        }
    }

    override suspend fun setAppClassLookupHidingEnabled(packageName: String, enabled: Boolean) {
        _appConfigs.update { configs ->
            configs +
                (packageName to
                    (configs[packageName] ?: AppConfig(packageName = packageName))
                        .withClassLookupHidingEnabled(enabled))
        }
    }

    override suspend fun exportGroups(): String {
        return "{ \"groups\": [] }"
    }

    override suspend fun importGroups(jsonString: String): Boolean {
        return try {
            com.astrixforge.devicemasker.common.JsonConfig.parse(jsonString)
            true
        } catch (_: Exception) {
            false
        }
    }

    fun setGroups(groups: List<SpoofGroup>) {
        _groups.value = groups
    }
}
