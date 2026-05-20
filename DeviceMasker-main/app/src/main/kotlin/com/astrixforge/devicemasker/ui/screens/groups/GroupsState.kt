package com.astrixforge.devicemasker.ui.screens.groups

import androidx.compose.runtime.Immutable
import com.astrixforge.devicemasker.common.AppConfig
import com.astrixforge.devicemasker.data.models.SpoofGroup
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.ImmutableMap
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.persistentMapOf

/** UI state for the Groups list screen. */
@Immutable
data class GroupsState(
    val isLoading: Boolean = true,
    val isRefreshing: Boolean = false,
    val groups: ImmutableList<SpoofGroup> = persistentListOf(),
    val appConfigs: ImmutableMap<String, AppConfig> = persistentMapOf(),
)
