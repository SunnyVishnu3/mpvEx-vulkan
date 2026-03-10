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

// --- THE TARGET ENUM ---
// Adding CARD here automatically generates the entire UI tab in AppearancePreferencesScreen!
enum class LiquidTarget(val id: String, val title: String) {
    NAV("nav", "Navigation"),
    BUTTON("btn", "Buttons"),
    DIALOG("dlg", "Dialogs"),
    CARD("card", "Browser Cards") 
}

class LiquidUIPreferences(context: Context) {
    private val dataStore = context.liquidUIDataStore

    companion object {
        val LIQUID_UI_ENABLED = booleanPreferencesKey("liquid_ui_enabled")
        val LIQUID_TOGGLE_COLOR = longPreferencesKey("liquid_toggle_color")
        val LIQUID_SLIDER_COLOR = longPreferencesKey("liquid_slider_color")
        
        // Legacy Keys to prevent crashes in other screens
        val LIQUID_BLUR_ENABLED = booleanPreferencesKey("liquid_blur_enabled")
        val LIQUID_LENS_ENABLED = booleanPreferencesKey("liquid_lens_enabled")
        val LIQUID_VIBRANCY_ENABLED_LEGACY = booleanPreferencesKey("liquid_vibrancy_enabled_legacy")
    }

    // Master Toggles
    val liquidUIEnabledFlow: Flow<Boolean> = dataStore.data.map { it[LIQUID_UI_ENABLED] ?: false }
    val liquidToggleColorFlow: Flow<Long> = dataStore.data.map { it[LIQUID_TOGGLE_COLOR] ?: 0xFF4CAF50 }
    val liquidSliderColorFlow: Flow<Long> = dataStore.data.map { it[LIQUID_SLIDER_COLOR] ?: 0xFF2196F3 }

    suspend fun setLiquidUIEnabled(enabled: Boolean) { dataStore.edit { it[LIQUID_UI_ENABLED] = enabled } }
    suspend fun setToggleColor(color: Long) { dataStore.edit { it[LIQUID_TOGGLE_COLOR] = color } }
    suspend fun setSliderColor(color: Long) { dataStore.edit { it[LIQUID_SLIDER_COLOR] = color } }

    // --- DYNAMIC TARGET FLOWS ---
    fun blurRadiusFlow(target: LiquidTarget): Flow<Float> = dataStore.data.map { it[floatPreferencesKey("${target.id}_blur")] ?: 0f }
    fun refractionHeightFlow(target: LiquidTarget): Flow<Float> = dataStore.data.map { it[floatPreferencesKey("${target.id}_height")] ?: 40f }
    fun refractionAmountFlow(target: LiquidTarget): Flow<Float> = dataStore.data.map { it[floatPreferencesKey("${target.id}_amount")] ?: 23f }
    fun tintAlphaFlow(target: LiquidTarget): Flow<Float> = dataStore.data.map { it[floatPreferencesKey("${target.id}_alpha")] ?: 0.15f }
    
    fun chromaticAberrationFlow(target: LiquidTarget): Flow<Boolean> = dataStore.data.map { it[booleanPreferencesKey("${target.id}_chromatic")] ?: false }
    fun depthEffectFlow(target: LiquidTarget): Flow<Boolean> = dataStore.data.map { it[booleanPreferencesKey("${target.id}_depth")] ?: true }
    fun vibrancyEnabledFlow(target: LiquidTarget): Flow<Boolean> = dataStore.data.map { it[booleanPreferencesKey("${target.id}_vibrancy")] ?: true }

    // --- DYNAMIC TARGET SETTERS ---
    suspend fun setBlurRadius(target: LiquidTarget, value: Float) { dataStore.edit { it[floatPreferencesKey("${target.id}_blur")] = value } }
    suspend fun setRefractionHeight(target: LiquidTarget, value: Float) { dataStore.edit { it[floatPreferencesKey("${target.id}_height")] = value } }
    suspend fun setRefractionAmount(target: LiquidTarget, value: Float) { dataStore.edit { it[floatPreferencesKey("${target.id}_amount")] = value } }
    suspend fun setTintAlpha(target: LiquidTarget, value: Float) { dataStore.edit { it[floatPreferencesKey("${target.id}_alpha")] = value } }
    
    suspend fun setChromaticAberration(target: LiquidTarget, enabled: Boolean) { dataStore.edit { it[booleanPreferencesKey("${target.id}_chromatic")] = enabled } }
    suspend fun setDepthEffect(target: LiquidTarget, enabled: Boolean) { dataStore.edit { it[booleanPreferencesKey("${target.id}_depth")] = enabled } }
    suspend fun setVibrancyEnabled(target: LiquidTarget, enabled: Boolean) { dataStore.edit { it[booleanPreferencesKey("${target.id}_vibrancy")] = enabled } }

    // --- LEGACY FALLBACKS (Keeps LiquidUISettingsScreen.kt from crashing!) ---
    val liquidBlurEnabledFlow: Flow<Boolean> = dataStore.data.map { it[LIQUID_BLUR_ENABLED] ?: true }
    val liquidLensEnabledFlow: Flow<Boolean> = dataStore.data.map { it[LIQUID_LENS_ENABLED] ?: true }
    val liquidVibrancyEnabledFlow: Flow<Boolean> = dataStore.data.map { it[LIQUID_VIBRANCY_ENABLED_LEGACY] ?: true }

    suspend fun setBlurEnabled(enabled: Boolean) { dataStore.edit { it[LIQUID_BLUR_ENABLED] = enabled } }
    suspend fun setLensEnabled(enabled: Boolean) { dataStore.edit { it[LIQUID_LENS_ENABLED] = enabled } }
    
    // This perfectly satisfies the old screen while leaving the new engine untouched!
    suspend fun setVibrancyEnabled(enabled: Boolean) { dataStore.edit { it[LIQUID_VIBRANCY_ENABLED_LEGACY] = enabled } }
}
