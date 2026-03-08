package app.marlboroadvance.mpvex.preferences

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.floatPreferencesKey
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
        
        // Kyant Backdrop Effect Keys
        val LIQUID_BLUR_RADIUS = floatPreferencesKey("liquid_blur_radius")
        val LIQUID_REFRACTION_HEIGHT = floatPreferencesKey("liquid_refraction_height")
        val LIQUID_REFRACTION_AMOUNT = floatPreferencesKey("liquid_refraction_amount")
        val LIQUID_CHROMATIC_ABERRATION = booleanPreferencesKey("liquid_chromatic_aberration")
        val LIQUID_DEPTH_EFFECT = booleanPreferencesKey("liquid_depth_effect")
        val LIQUID_VIBRANCY_ENABLED = booleanPreferencesKey("liquid_vibrancy_enabled")
        
        // Color & Opacity Keys
        val LIQUID_TOGGLE_COLOR = longPreferencesKey("liquid_toggle_color")
        val LIQUID_SLIDER_COLOR = longPreferencesKey("liquid_slider_color")
        val LIQUID_TINT_ALPHA = floatPreferencesKey("liquid_tint_alpha")

        // Legacy Keys (Kept to prevent LiquidUISettingsScreen from crashing)
        val LIQUID_BLUR_ENABLED = booleanPreferencesKey("liquid_blur_enabled")
        val LIQUID_LENS_ENABLED = booleanPreferencesKey("liquid_lens_enabled")
    }

    // --- STATE FLOWS ---
    val liquidUIEnabledFlow: Flow<Boolean> = dataStore.data.map { it[LIQUID_UI_ENABLED] ?: false }
    
    val liquidBlurRadiusFlow: Flow<Float> = dataStore.data.map { it[LIQUID_BLUR_RADIUS] ?: 0f }
    val liquidRefractionHeightFlow: Flow<Float> = dataStore.data.map { it[LIQUID_REFRACTION_HEIGHT] ?: 40f }
    val liquidRefractionAmountFlow: Flow<Float> = dataStore.data.map { it[LIQUID_REFRACTION_AMOUNT] ?: 23f }
    val liquidChromaticAberrationFlow: Flow<Boolean> = dataStore.data.map { it[LIQUID_CHROMATIC_ABERRATION] ?: false }
    val liquidDepthEffectFlow: Flow<Boolean> = dataStore.data.map { it[LIQUID_DEPTH_EFFECT] ?: true }
    val liquidVibrancyEnabledFlow: Flow<Boolean> = dataStore.data.map { it[LIQUID_VIBRANCY_ENABLED] ?: true }
    val liquidTintAlphaFlow: Flow<Float> = dataStore.data.map { it[LIQUID_TINT_ALPHA] ?: 0.15f }
    
    val liquidToggleColorFlow: Flow<Long> = dataStore.data.map { it[LIQUID_TOGGLE_COLOR] ?: 0xFF4CAF50 }
    val liquidSliderColorFlow: Flow<Long> = dataStore.data.map { it[LIQUID_SLIDER_COLOR] ?: 0xFF2196F3 }

    // Legacy Flows
    val liquidBlurEnabledFlow: Flow<Boolean> = dataStore.data.map { it[LIQUID_BLUR_ENABLED] ?: true }
    val liquidLensEnabledFlow: Flow<Boolean> = dataStore.data.map { it[LIQUID_LENS_ENABLED] ?: true }

    // --- SETTERS ---
    suspend fun setLiquidUIEnabled(enabled: Boolean) { dataStore.edit { it[LIQUID_UI_ENABLED] = enabled } }
    suspend fun setBlurRadius(value: Float) { dataStore.edit { it[LIQUID_BLUR_RADIUS] = value } }
    suspend fun setRefractionHeight(value: Float) { dataStore.edit { it[LIQUID_REFRACTION_HEIGHT] = value } }
    suspend fun setRefractionAmount(value: Float) { dataStore.edit { it[LIQUID_REFRACTION_AMOUNT] = value } }
    suspend fun setChromaticAberration(enabled: Boolean) { dataStore.edit { it[LIQUID_CHROMATIC_ABERRATION] = enabled } }
    suspend fun setDepthEffect(enabled: Boolean) { dataStore.edit { it[LIQUID_DEPTH_EFFECT] = enabled } }
    suspend fun setVibrancyEnabled(enabled: Boolean) { dataStore.edit { it[LIQUID_VIBRANCY_ENABLED] = enabled } }
    suspend fun setTintAlpha(value: Float) { dataStore.edit { it[LIQUID_TINT_ALPHA] = value } }
    suspend fun setToggleColor(color: Long) { dataStore.edit { it[LIQUID_TOGGLE_COLOR] = color } }
    suspend fun setSliderColor(color: Long) { dataStore.edit { it[LIQUID_SLIDER_COLOR] = color } }
    
    // Legacy Setters
    suspend fun setBlurEnabled(enabled: Boolean) { dataStore.edit { it[LIQUID_BLUR_ENABLED] = enabled } }
    suspend fun setLensEnabled(enabled: Boolean) { dataStore.edit { it[LIQUID_LENS_ENABLED] = enabled } }
}
