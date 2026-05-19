package com.astrixforge.devicemasker.service

import android.content.Context
import com.astrixforge.devicemasker.common.AppConfig
import com.astrixforge.devicemasker.common.JsonConfig
import com.astrixforge.devicemasker.common.SpoofGroup
import com.astrixforge.devicemasker.common.SpoofType
import kotlinx.coroutines.flow.StateFlow

interface ConfigLifecycle {
    val config: StateFlow<JsonConfig>
    val isInitialized: StateFlow<Boolean>

    fun init(context: Context)

    fun saveConfig()

    fun syncCurrentConfig()
}

interface ConfigModuleSettings {
    fun isModuleEnabled(): Boolean

    fun setModuleEnabled(enabled: Boolean)
}

interface ConfigGroupStore {
    fun getAllGroups(): List<SpoofGroup>

    fun getGroup(groupId: String): SpoofGroup?

    fun createGroup(name: String, copyFromGroupId: String? = null): SpoofGroup

    fun updateGroup(group: SpoofGroup)

    fun setDefaultGroup(groupId: String)

    fun deleteGroup(groupId: String)

    fun getGroupForApp(packageName: String): SpoofGroup?
}

interface ConfigIdentifierStore {
    fun setIdentifierValue(groupId: String, type: SpoofType, value: String?)

    fun setTypeEnabled(groupId: String, type: SpoofType, enabled: Boolean)

    fun regenerateAllValues(groupId: String)

    fun getPersonaSeed(group: SpoofGroup): String

    fun getPersonaVersion(group: SpoofGroup): Long

    fun refreshPersonaLifecycle(group: SpoofGroup): SpoofGroup
}

interface ConfigAppStore {
    fun getAppConfig(packageName: String): AppConfig?

    fun assignAppToGroup(packageName: String, groupId: String)

    fun unassignApp(packageName: String)

    fun setAppEnabled(packageName: String, enabled: Boolean)

    fun setAppRiskyHooksEnabled(packageName: String, enabled: Boolean)

    fun setAppClassLookupHidingEnabled(packageName: String, enabled: Boolean)
}

interface IConfigManager :
    ConfigLifecycle, ConfigModuleSettings, ConfigGroupStore, ConfigIdentifierStore, ConfigAppStore
