package com.astrixforge.devicemasker.ui.screens.groupspoofing

import androidx.compose.runtime.Immutable
import com.astrixforge.devicemasker.common.AppConfig
import com.astrixforge.devicemasker.common.SpoofGroup
import com.astrixforge.devicemasker.data.models.InstalledApp
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.ImmutableMap
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.persistentMapOf

/** UI state for the Group Spoofing screen. */
@Immutable
data class GroupSpoofingState(
    val isLoading: Boolean = true,
    val isAppsRefreshing: Boolean = false,
    val group: SpoofGroup? = null,
    val groups: ImmutableList<SpoofGroup> = persistentListOf(),
    val appConfigs: ImmutableMap<String, AppConfig> = persistentMapOf(),
    val installedApps: ImmutableList<InstalledApp> = persistentListOf(),
    val appRows: ImmutableList<AppRowModel> = persistentListOf(),
    val selectedTab: Int = 0,
    val spoofTabScrollPosition: Int = 0,
    val appsTabScrollPosition: Int = 0,
)
