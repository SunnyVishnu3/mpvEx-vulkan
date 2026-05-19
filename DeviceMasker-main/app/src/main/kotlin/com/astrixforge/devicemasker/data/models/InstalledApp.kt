package com.astrixforge.devicemasker.data.models

data class InstalledApp(
    val packageName: String,
    val label: String,
    val isSystemApp: Boolean = false,
    val versionName: String = "",
)
