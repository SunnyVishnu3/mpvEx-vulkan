package com.astrixforge.devicemasker.ui.screens.home

import androidx.compose.runtime.Immutable

@Immutable
data class HomeScopedApp(
    val packageName: String,
    val label: String,
    val groupName: String?,
    val isGloballyEnabled: Boolean,
    val status: HomeScopedAppStatus,
)
