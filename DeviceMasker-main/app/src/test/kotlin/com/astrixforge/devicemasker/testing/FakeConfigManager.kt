package com.astrixforge.devicemasker.testing

import android.content.Context
import com.astrixforge.devicemasker.common.AppConfig
import com.astrixforge.devicemasker.common.JsonConfig
import com.astrixforge.devicemasker.common.SpoofGroup
import com.astrixforge.devicemasker.common.SpoofType
import com.astrixforge.devicemasker.common.addOrUpdateGroup
import com.astrixforge.devicemasker.common.getAllGroups
import com.astrixforge.devicemasker.common.regenerateAll
import com.astrixforge.devicemasker.common.setAppConfig
import com.astrixforge.devicemasker.common.setIdentifier
import com.astrixforge.devicemasker.common.withPersona
import com.astrixforge.devicemasker.service.IConfigManager
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** Fake [IConfigManager] that keeps all state in memory. */
class FakeConfigManager : IConfigManager {

    private val _config = MutableStateFlow(JsonConfig.createDefault())
    override val config: StateFlow<JsonConfig> = _config.asStateFlow()

    private val _isInitialized = MutableStateFlow(false)
    override val isInitialized: StateFlow<Boolean> = _isInitialized.asStateFlow()

    override fun init(context: Context) {
        _isInitialized.value = true
    }

    override fun saveConfig() {
        // no-op
    }

    override fun syncCurrentConfig() {
        // no-op
    }

    override fun isModuleEnabled(): Boolean = _config.value.isModuleEnabled

    override fun setModuleEnabled(enabled: Boolean) {
        _config.value = _config.value.copy(isModuleEnabled = enabled)
    }

    override fun getAllGroups(): List<SpoofGroup> = _config.value.getAllGroups()

    override fun getGroup(groupId: String): SpoofGroup? = _config.value.getGroup(groupId)

    override fun createGroup(name: String, copyFromGroupId: String?): SpoofGroup {
        val baseGroup = copyFromGroupId?.let { getGroup(it) }
        val newGroup =
            baseGroup?.copy(
                id = java.util.UUID.randomUUID().toString(),
                name = name,
                createdAt = System.currentTimeMillis(),
                updatedAt = System.currentTimeMillis(),
                assignedApps = emptySet(),
            ) ?: SpoofGroup.createNew(name = name, isDefault = false)

        _config.value = _config.value.addOrUpdateGroup(newGroup)
        return newGroup
    }

    override fun updateGroup(group: SpoofGroup) {
        _config.value =
            _config.value.addOrUpdateGroup(group.copy(updatedAt = System.currentTimeMillis()))
    }

    override fun setDefaultGroup(groupId: String) {
        if (_config.value.getGroup(groupId) == null) return

        _config.value =
            _config.value.copy(
                groups =
                    _config.value.groups.mapValues { (id, group) ->
                        group.copy(isDefault = id == groupId)
                    }
            )
    }

    override fun deleteGroup(groupId: String) {
        _config.value = _config.value.removeGroup(groupId)
    }

    override fun getGroupForApp(packageName: String): SpoofGroup? =
        _config.value.getGroupForApp(packageName)

    override fun setIdentifierValue(groupId: String, type: SpoofType, value: String?) {
        val group = getGroup(groupId) ?: return
        val identifier =
            group.getIdentifier(type)?.copy(value = value)
                ?: com.astrixforge.devicemasker.common.DeviceIdentifier.createDefault(type)
                    .copy(value = value)
        updateGroup(group.setIdentifier(identifier))
    }

    override fun setTypeEnabled(groupId: String, type: SpoofType, enabled: Boolean) {
        val group = getGroup(groupId) ?: return
        val identifier =
            group.getIdentifier(type)?.copy(isEnabled = enabled)
                ?: com.astrixforge.devicemasker.common.DeviceIdentifier.createDefault(type)
                    .copy(isEnabled = enabled)
        updateGroup(group.setIdentifier(identifier))
    }

    override fun regenerateAllValues(groupId: String) {
        val group = getGroup(groupId) ?: return
        updateGroup(group.regenerateAll())
    }

    override fun getPersonaSeed(group: SpoofGroup): String = group.resolvedPersonaSeed()

    override fun getPersonaVersion(group: SpoofGroup): Long = group.updatedAt

    override fun refreshPersonaLifecycle(group: SpoofGroup): SpoofGroup {
        return group.withPersona(
            seed = java.util.UUID.randomUUID().toString(),
            generatedAt = System.currentTimeMillis(),
        )
    }

    override fun getAppConfig(packageName: String): AppConfig? =
        _config.value.getAppConfig(packageName)

    override fun assignAppToGroup(packageName: String, groupId: String) {
        val groups =
            _config.value.groups.mapValues { (_, group) ->
                when {
                    group.id == groupId ->
                        group.copy(assignedApps = group.assignedApps + packageName)
                    packageName in group.assignedApps ->
                        group.copy(assignedApps = group.assignedApps - packageName)
                    else -> group
                }
            }
        val appConfig =
            getAppConfig(packageName)?.copy(groupId = groupId)
                ?: AppConfig(packageName = packageName, groupId = groupId)
        _config.value = _config.value.copy(groups = groups).setAppConfig(appConfig)
    }

    override fun unassignApp(packageName: String) {
        val groups =
            _config.value.groups.mapValues { (_, group) ->
                if (packageName in group.assignedApps) {
                    group.copy(assignedApps = group.assignedApps - packageName)
                } else {
                    group
                }
            }
        val appConfig =
            getAppConfig(packageName)?.copy(groupId = null)
                ?: AppConfig(packageName = packageName, groupId = null)
        _config.value = _config.value.copy(groups = groups).setAppConfig(appConfig)
    }

    override fun setAppEnabled(packageName: String, enabled: Boolean) {
        val appConfig =
            getAppConfig(packageName)?.copy(isEnabled = enabled)
                ?: AppConfig(packageName = packageName, isEnabled = enabled)
        _config.value = _config.value.setAppConfig(appConfig)
    }

    override fun setAppRiskyHooksEnabled(packageName: String, enabled: Boolean) {
        val appConfig =
            (getAppConfig(packageName) ?: AppConfig(packageName = packageName))
                .withRiskyHooksEnabled(enabled)
        _config.value = _config.value.setAppConfig(appConfig)
    }

    override fun setAppClassLookupHidingEnabled(packageName: String, enabled: Boolean) {
        val appConfig =
            (getAppConfig(packageName) ?: AppConfig(packageName = packageName))
                .withClassLookupHidingEnabled(enabled)
        _config.value = _config.value.setAppConfig(appConfig)
    }

    /** Resets state for tests. */
    fun reset() {
        _config.value = JsonConfig.createDefault()
        _isInitialized.value = false
    }
}
