package com.astrixforge.devicemasker.data.repository

import com.astrixforge.devicemasker.common.AppConfig
import com.astrixforge.devicemasker.common.CorrelationGroup
import com.astrixforge.devicemasker.common.SpoofGroup
import com.astrixforge.devicemasker.common.SpoofType
import com.astrixforge.devicemasker.common.models.Carrier
import kotlinx.coroutines.flow.Flow

interface SpoofStateRepository {
    val appScopeRepository: IAppScopeRepository
    val moduleEnabled: Flow<Boolean>
    val groups: Flow<List<SpoofGroup>>
    val appConfigs: Flow<Map<String, AppConfig>>
    val activeGroup: Flow<SpoofGroup?>
    val enabledAppCount: Flow<Int>
    val dashboardState: Flow<SpoofRepository.DashboardState>

    suspend fun setModuleEnabled(enabled: Boolean)

    suspend fun setActiveGroup(groupId: String)
}

interface SpoofValueRepository {
    fun generateValue(type: SpoofType): String

    fun regenerateSIMValueOnly(type: SpoofType): String

    fun resetCorrelations()

    fun resetCorrelationGroup(group: CorrelationGroup)

    suspend fun regenerateLocationValues(groupId: String)

    suspend fun updateGroupWithCarrier(groupId: String, carrier: Carrier)

    suspend fun updateGroupWithDeviceProfile(groupId: String, presetId: String)

    suspend fun regenerateAllValues(groupId: String)
}

interface SpoofGroupRepository {
    fun getAllGroups(): Flow<List<SpoofGroup>>

    suspend fun createGroup(name: String, description: String = "")

    suspend fun updateGroup(group: SpoofGroup)

    suspend fun deleteGroup(groupId: String)

    suspend fun setDefaultGroup(groupId: String)

    suspend fun setGroupEnabled(groupId: String, enabled: Boolean)
}

interface SpoofAppAssignmentRepository {
    suspend fun addAppToGroup(groupId: String, packageName: String)

    suspend fun removeAppFromGroup(groupId: String, packageName: String)

    suspend fun setAppEnabled(packageName: String, enabled: Boolean)

    suspend fun setAppRiskyHooksEnabled(packageName: String, enabled: Boolean)

    suspend fun setAppClassLookupHidingEnabled(packageName: String, enabled: Boolean)
}

interface SpoofImportExportRepository {
    suspend fun exportGroups(): String

    suspend fun importGroups(jsonString: String): Boolean
}

interface ISpoofRepository :
    SpoofStateRepository,
    SpoofValueRepository,
    SpoofGroupRepository,
    SpoofAppAssignmentRepository,
    SpoofImportExportRepository
