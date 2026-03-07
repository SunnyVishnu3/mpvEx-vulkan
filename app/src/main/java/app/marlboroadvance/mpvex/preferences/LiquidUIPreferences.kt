package app.marlboroadvance.mpvex.preferences

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.liquidUIDataStore by preferencesDataStore(name = "liquid_ui_prefs")

class LiquidUIPreferences(context: Context) {
    private val dataStore = context.liquidUIDataStore

    companion object {
        val LIQUID_UI_ENABLED = booleanPreferencesKey("liquid_ui_enabled")
        val LIQUID_BLUR_ENABLED = booleanPreferencesKey("liquid_blur_enabled")
        val LIQUID_LENS_ENABLED = booleanPreferencesKey("liquid_lens_enabled")
        val LIQUID_VIBRANCY_ENABLED = booleanPreferencesKey("liquid_vibrancy_enabled")
    }

    val liquidUIEnabledFlow: Flow<Boolean> = dataStore.data.map { prefs ->
        prefs[LIQUID_UI_ENABLED] ?: false
    }

    val liquidBlurEnabledFlow: Flow<Boolean> = dataStore.data.map { prefs ->
        prefs[LIQUID_BLUR_ENABLED] ?: true
    }

    val liquidLensEnabledFlow: Flow<Boolean> = dataStore.data.map { prefs ->
        prefs[LIQUID_LENS_ENABLED] ?: true
    }

    val liquidVibrancyEnabledFlow: Flow<Boolean> = dataStore.data.map { prefs ->
        prefs[LIQUID_VIBRANCY_ENABLED] ?: true
    }

    suspend fun setLiquidUIEnabled(enabled: Boolean) {
        dataStore.edit { it[LIQUID_UI_ENABLED] = enabled }
    }

    suspend fun setBlurEnabled(enabled: Boolean) {
        dataStore.edit { it[LIQUID_BLUR_ENABLED] = enabled }
    }

    suspend fun setLensEnabled(enabled: Boolean) {
        dataStore.edit { it[LIQUID_LENS_ENABLED] = enabled }
    }

    suspend fun setVibrancyEnabled(enabled: Boolean) {
        dataStore.edit { it[LIQUID_VIBRANCY_ENABLED] = enabled }
    }
}
