package com.astrixforge.devicemasker.data.repository

import com.astrixforge.devicemasker.data.models.InstalledApp
import kotlinx.coroutines.flow.StateFlow

interface IAppScopeRepository {
    val installedApps: StateFlow<List<InstalledApp>>
    val scopedAppMetadata: StateFlow<Map<String, InstalledApp>>
    val isLoading: StateFlow<Boolean>

    suspend fun loadApps(forceRefresh: Boolean = false)

    suspend fun loadScopedApps(packageNames: Set<String>, forceRefresh: Boolean = false)

    suspend fun getInstalledApps(
        includeSystem: Boolean = false,
        refreshCache: Boolean = false,
    ): List<InstalledApp>

    fun invalidateCache()
}
