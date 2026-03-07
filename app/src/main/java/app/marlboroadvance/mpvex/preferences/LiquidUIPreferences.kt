package app.marlboroadvance.mpvex.preferences

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
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
        
        // Custom Color Keys
        val LIQUID_TOGGLE_COLOR = longPreferencesKey("liquid_toggle_color")
        val LIQUID_SLIDER_COLOR = longPreferencesKey("liquid_slider_color")
    }

    val liquidUIEnabledFlow: Flow<Boolean> = dataStore.data.map { it[LIQUID_UI_ENABLED] ?: false }
    val liquidBlurEnabledFlow: Flow<Boolean> = dataStore.data.map { it[LIQUID_BLUR_ENABLED] ?: true }
    val liquidLensEnabledFlow: Flow<Boolean> = dataStore.data.map { it[LIQUID_LENS_ENABLED] ?: true }
    val liquidVibrancyEnabledFlow: Flow<Boolean> = dataStore.data.map { it[LIQUID_VIBRANCY_ENABLED] ?: true }
    
    // Default: Green (0xFF4CAF50) for toggles, Blue (0xFF2196F3) for sliders
    val liquidToggleColorFlow: Flow<Long> = dataStore.data.map { it[LIQUID_TOGGLE_COLOR] ?: 0xFF4CAF50 }
    val liquidSliderColorFlow: Flow<Long> = dataStore.data.map { it[LIQUID_SLIDER_COLOR] ?: 0xFF2196F3 }

    suspend fun setLiquidUIEnabled(enabled: Boolean) { dataStore.edit { it[LIQUID_UI_ENABLED] = enabled } }
    suspend fun setBlurEnabled(enabled: Boolean) { dataStore.edit { it[LIQUID_BLUR_ENABLED] = enabled } }
    suspend fun setLensEnabled(enabled: Boolean) { dataStore.edit { it[LIQUID_LENS_ENABLED] = enabled } }
    suspend fun setVibrancyEnabled(enabled: Boolean) { dataStore.edit { it[LIQUID_VIBRANCY_ENABLED] = enabled } }
    
    suspend fun setToggleColor(color: Long) { dataStore.edit { it[LIQUID_TOGGLE_COLOR] = color } }
    suspend fun setSliderColor(color: Long) { dataStore.edit { it[LIQUID_SLIDER_COLOR] = color } }
}
