package com.astrixforge.devicemasker.ui.screens.home

import com.astrixforge.devicemasker.common.AppConfig
import com.astrixforge.devicemasker.data.XposedScopeState
import com.astrixforge.devicemasker.data.models.InstalledApp
import com.astrixforge.devicemasker.data.models.SpoofGroup
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.toImmutableList

fun buildHomeScopedApps(
    scopeState: XposedScopeState,
    scopedAppMetadata: Map<String, InstalledApp>,
    appConfigs: Map<String, AppConfig>,
    groups: List<SpoofGroup>,
): ImmutableList<HomeScopedApp> {
    val scopePackages =
        when (scopeState) {
            is XposedScopeState.Connected -> scopeState.packages
            XposedScopeState.Disconnected,
            is XposedScopeState.Error -> emptySet()
        }
    val groupsById = groups.associateBy { it.id }

    return scopePackages
        .asSequence()
        .filterNot { it in DEFAULT_LSPOSED_SCOPE_PACKAGES }
        .mapNotNull { packageName ->
            val installedApp = scopedAppMetadata[packageName] ?: return@mapNotNull null
            val appConfig = appConfigs[packageName]
            val group = appConfig?.groupId?.let(groupsById::get)
            HomeScopedApp(
                packageName = packageName,
                label = installedApp.label,
                groupName = group?.name,
                isGloballyEnabled = appConfig?.isEnabled != false,
                status = appConfig.toHomeScopedAppStatus(group),
            )
        }
        .sortedWith(compareBy<HomeScopedApp> { it.label.lowercase() }.thenBy { it.packageName })
        .toList()
        .toImmutableList()
}

private fun AppConfig?.toHomeScopedAppStatus(group: SpoofGroup?): HomeScopedAppStatus =
    when {
        this != null && !isEnabled -> HomeScopedAppStatus.DisabledByApp
        this == null || group == null -> HomeScopedAppStatus.NotConfigured
        !group.isEnabled -> HomeScopedAppStatus.DisabledByGroup
        else -> HomeScopedAppStatus.Enabled
    }

private val DEFAULT_LSPOSED_SCOPE_PACKAGES = setOf("android", "system")
