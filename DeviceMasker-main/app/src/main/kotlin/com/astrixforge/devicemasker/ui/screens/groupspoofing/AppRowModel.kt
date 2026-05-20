package com.astrixforge.devicemasker.ui.screens.groupspoofing

import androidx.compose.runtime.Immutable
import com.astrixforge.devicemasker.data.models.InstalledApp

@Immutable
data class AppRowModel(
    val app: InstalledApp,
    val normalizedLabel: String,
    val normalizedPackageName: String,
    val isAssignedToCurrentGroup: Boolean,
    val isAppEnabled: Boolean,
    val assignedToOtherGroupName: String?,
) {
    fun matches(query: String, showSystemApps: Boolean, selfPackageName: String): Boolean =
        app.packageName != selfPackageName &&
            (showSystemApps || !app.isSystemApp) &&
            (query.isEmpty() ||
                normalizedLabel.contains(query) ||
                normalizedPackageName.contains(query))
}
