package com.astrixforge.devicemasker.data

import com.astrixforge.devicemasker.data.models.ThemeMode
import kotlinx.coroutines.flow.Flow

interface ISettingsDataStore {
    val themeMode: Flow<ThemeMode>
    val amoledMode: Flow<Boolean>
    val dynamicColors: Flow<Boolean>

    suspend fun setThemeMode(mode: ThemeMode)

    suspend fun setAmoledMode(enabled: Boolean)

    suspend fun setDynamicColors(enabled: Boolean)
}
