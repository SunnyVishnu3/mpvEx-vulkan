package com.astrixforge.devicemasker.data.models

import com.astrixforge.devicemasker.R

/** Theme mode options persisted by SettingsDataStore and rendered by the UI. */
enum class ThemeMode(val displayNameRes: Int) {
    SYSTEM(R.string.settings_theme_system),
    LIGHT(R.string.settings_theme_light),
    DARK(R.string.settings_theme_dark),
}
