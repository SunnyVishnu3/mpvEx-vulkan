package com.astrixforge.devicemasker.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.astrixforge.devicemasker.data.models.ThemeMode
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/**
 * DataStore for UI/app settings only.
 *
 * In the Multi-Module architecture, this is separate from hook configuration. Hook config uses
 * JsonConfig via ConfigManager. This store only handles UI preferences like theme settings.
 */
private val Context.settingsDataStore: DataStore<Preferences> by
    preferencesDataStore(name = "settings")

class SettingsDataStore(private val context: Context) : ISettingsDataStore {

    private val dataStore: DataStore<Preferences>
        get() = context.settingsDataStore

    // Preference keys
    private object Keys {
        val THEME_MODE = intPreferencesKey("theme_mode")
        val AMOLED_MODE = booleanPreferencesKey("amoled_mode")
        val DYNAMIC_COLORS = booleanPreferencesKey("dynamic_colors")
    }

    private companion object {
        private const val THEME_SYSTEM = 0
        private const val THEME_LIGHT = 1
        private const val THEME_DARK = 2
    }

    // ═══════════════════════════════════════════════════════════
    // THEME SETTINGS
    // ═══════════════════════════════════════════════════════════

    /** Flow of theme mode preference. */
    override val themeMode: Flow<ThemeMode> =
        dataStore.data.map { prefs ->
            when (prefs[Keys.THEME_MODE]) {
                THEME_SYSTEM -> ThemeMode.SYSTEM
                THEME_LIGHT -> ThemeMode.LIGHT
                THEME_DARK -> ThemeMode.DARK
                else -> ThemeMode.SYSTEM
            }
        }

    /** Sets the theme mode. */
    override suspend fun setThemeMode(mode: ThemeMode) {
        dataStore.edit { prefs ->
            prefs[Keys.THEME_MODE] =
                when (mode) {
                    ThemeMode.SYSTEM -> THEME_SYSTEM
                    ThemeMode.LIGHT -> THEME_LIGHT
                    ThemeMode.DARK -> THEME_DARK
                }
        }
    }

    /** Flow of AMOLED dark mode preference. */
    override val amoledMode: Flow<Boolean> =
        dataStore.data.map { prefs -> prefs[Keys.AMOLED_MODE] ?: true }

    /** Sets AMOLED dark mode. */
    override suspend fun setAmoledMode(enabled: Boolean) {
        dataStore.edit { prefs -> prefs[Keys.AMOLED_MODE] = enabled }
    }

    /** Flow of dynamic colors preference. */
    override val dynamicColors: Flow<Boolean> =
        dataStore.data.map { prefs -> prefs[Keys.DYNAMIC_COLORS] ?: true }

    /** Sets dynamic colors. */
    override suspend fun setDynamicColors(enabled: Boolean) {
        dataStore.edit { prefs -> prefs[Keys.DYNAMIC_COLORS] = enabled }
    }
}
