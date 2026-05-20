package com.astrixforge.devicemasker.testing

import com.astrixforge.devicemasker.data.ISettingsDataStore
import com.astrixforge.devicemasker.data.models.ThemeMode
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** Fake [ISettingsDataStore] for Settings testing. */
class FakeSettingsDataStore(
    initialTheme: ThemeMode = ThemeMode.SYSTEM,
    initialAmoled: Boolean = true,
    initialDynamic: Boolean = true,
) : ISettingsDataStore {

    private val _themeMode = MutableStateFlow(initialTheme)
    override val themeMode: StateFlow<ThemeMode> = _themeMode.asStateFlow()

    private val _amoledMode = MutableStateFlow(initialAmoled)
    override val amoledMode: StateFlow<Boolean> = _amoledMode.asStateFlow()

    private val _dynamicColors = MutableStateFlow(initialDynamic)
    override val dynamicColors: StateFlow<Boolean> = _dynamicColors.asStateFlow()

    override suspend fun setThemeMode(mode: ThemeMode) {
        _themeMode.value = mode
    }

    override suspend fun setAmoledMode(enabled: Boolean) {
        _amoledMode.value = enabled
    }

    override suspend fun setDynamicColors(enabled: Boolean) {
        _dynamicColors.value = enabled
    }
}
