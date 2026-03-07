package app.marlboroadvance.mpvex.ui.screens.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Card
import androidx.compose.material3.Divider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import app.marlboroadvance.mpvex.preferences.LiquidUIPreferences
import app.marlboroadvance.mpvex.ui.components.liquid.AdaptiveToggle
import kotlinx.coroutines.launch

/**
 * Liquid UI Settings Screen
 * 
 * Shows toggles for:
 * - Master Liquid UI Enable/Disable
 * - Individual Effect Settings (Blur, Lens, Vibrancy)
 * - Preview of effects in real-time
 */
@Composable
fun LiquidUISettingsScreen(
    preferences: LiquidUIPreferences,
    modifier: Modifier = Modifier
) {
    val scope = rememberCoroutineScope()
    
    // Collect all preference states
    val liquidUIEnabled = preferences.liquidUIEnabledFlow.collectAsState(false).value
    val blurEnabled = preferences.liquidBlurEnabledFlow.collectAsState(true).value
    val lensEnabled = preferences.liquidLensEnabledFlow.collectAsState(true).value
    val vibrancyEnabled = preferences.liquidVibrancyEnabledFlow.collectAsState(true).value

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp)
    ) {
        // ============================================================
        // SECTION 1: MASTER TOGGLE
        // ============================================================
        
        Text(
            text = "Liquid Glass UI",
            style = MaterialTheme.typography.headlineSmall,
            modifier = Modifier.padding(bottom = 8.dp)
        )
        
        Text(
            text = "Enable beautiful liquid glass effects throughout the app",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(bottom = 16.dp)
        )

        // Master toggle - Use standard switch for clarity on settings
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "Enable Liquid UI",
                style = MaterialTheme.typography.bodyLarge
            )
            
            // Use standard switch for the master toggle (meta-UI)
             AdaptiveToggle(
                checked = liquidUIEnabled,
                onCheckedChange = { enabled ->
                    scope.launch { preferences.setLiquidUIEnabled(enabled) }
                },
                preferences = preferences
            )
        }

        // ============================================================
        // SECTION 1.5: CUSTOM COLORS (DYNAMIC INPUT)
        // ============================================================
        
        AnimatedVisibility(visible = liquidUIEnabled) {
            Column {
                Divider(modifier = Modifier.padding(vertical = 16.dp))
                
                Text(
                    text = "Custom Colors",
                    style = MaterialTheme.typography.headlineSmall,
                    modifier = Modifier.padding(bottom = 8.dp)
                )
                
                Text(
                    text = "Type a hex code (e.g. #FF5733) or a basic color name (e.g. red, blue, cyan)",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(bottom = 16.dp)
                )

                val toggleColor = preferences.liquidToggleColorFlow.collectAsState(0xFF4CAF50).value
                val sliderColor = preferences.liquidSliderColorFlow.collectAsState(0xFF2196F3).value
                
                // Smart parser that reads names ("red") or hex codes ("#FF0000" or "FF0000")
                fun parseColorInput(input: String): Long? {
                    return try {
                        val formatted = if (input.matches(Regex("^[0-9A-Fa-f]{6,8}$"))) "#$input" else input
                        val colorInt = android.graphics.Color.parseColor(formatted)
                        colorInt.toLong() and 0xFFFFFFFFL
                    } catch (e: Exception) { null }
                }

                // --- TOGGLE COLOR INPUT ---
                var toggleInputText by remember(toggleColor) { 
                    mutableStateOf(String.format("#%06X", 0xFFFFFF and toggleColor.toInt())) 
                }
                
                androidx.compose.material3.OutlinedTextField(
                    value = toggleInputText,
                    onValueChange = { newValue ->
                        toggleInputText = newValue
                        // Only save to database if the color is valid!
                        parseColorInput(newValue)?.let { colorLong ->
                            scope.launch { preferences.setToggleColor(colorLong) }
                        }
                    },
                    label = { Text("Toggle Color") },
                    placeholder = { Text("e.g. #FF5733 or blue") },
                    modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp),
                    singleLine = true,
                    leadingIcon = {
                        Box(
                            modifier = Modifier
                                .size(24.dp)
                                .clip(androidx.compose.foundation.shape.CircleShape)
                                .background(androidx.compose.ui.graphics.Color(toggleColor))
                        )
                    }
                )

                // --- SLIDER COLOR INPUT ---
                var sliderInputText by remember(sliderColor) { 
                    mutableStateOf(String.format("#%06X", 0xFFFFFF and sliderColor.toInt())) 
                }
                
                androidx.compose.material3.OutlinedTextField(
                    value = sliderInputText,
                    onValueChange = { newValue ->
                        sliderInputText = newValue
                        // Only save to database if the color is valid!
                        parseColorInput(newValue)?.let { colorLong ->
                            scope.launch { preferences.setSliderColor(colorLong) }
                        }
                    },
                    label = { Text("Slider Color") },
                    placeholder = { Text("e.g. #00FF00 or cyan") },
                    modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp),
                    singleLine = true,
                    leadingIcon = {
                        Box(
                            modifier = Modifier
                                .size(24.dp)
                                .clip(androidx.compose.foundation.shape.CircleShape)
                                .background(androidx.compose.ui.graphics.Color(sliderColor))
                        )
                    }
                )
            }
        }
                    scope.launch {
                        preferences.setLiquidUIEnabled(enabled)
                    }
                }
            )
        }

        // ============================================================
        // SECTION 2: EFFECT TOGGLES
        // ============================================================
        
        Divider(modifier = Modifier.padding(vertical = 16.dp))
        
        Text(
            text = "Effect Settings",
            style = MaterialTheme.typography.headlineSmall,
            modifier = Modifier.padding(bottom = 8.dp)
        )
        
        Text(
            text = "Customize which effects are applied (requires Liquid UI enabled)",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(bottom = 16.dp)
        )

        // Blur Effect Toggle - CAN be liquid to show the effect
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "Blur Effect",
                    style = MaterialTheme.typography.bodyLarge
                )
                Text(
                    text = "Blurs the background behind UI elements",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            
            AdaptiveToggle(
                checked = blurEnabled,
                onCheckedChange = { enabled ->
                    scope.launch {
                        preferences.setBlurEnabled(enabled)
                    }
                },
                preferences = preferences,
                enabled = liquidUIEnabled
            )
        }

        // Lens Effect Toggle
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "Lens Effect",
                    style = MaterialTheme.typography.bodyLarge
                )
                Text(
                    text = "Adds refraction lens effect (Android 13+ only)",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            
            AdaptiveToggle(
                checked = lensEnabled,
                onCheckedChange = { enabled ->
                    scope.launch {
                        preferences.setLensEnabled(enabled)
                    }
                },
                preferences = preferences,
                enabled = liquidUIEnabled && android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU
            )
        }

        // Vibrancy Effect Toggle
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "Vibrancy Effect",
                    style = MaterialTheme.typography.bodyLarge
                )
                Text(
                    text = "Enhances color vibrancy of background content",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            
            AdaptiveToggle(
                checked = vibrancyEnabled,
                onCheckedChange = { enabled ->
                    scope.launch {
                        preferences.setVibrancyEnabled(enabled)
                    }
                },
                preferences = preferences,
                enabled = liquidUIEnabled
            )
        }

        // ============================================================
        // SECTION 3: INFORMATION
        // ============================================================
        
        Divider(modifier = Modifier.padding(vertical = 16.dp))
        
        Text(
            text = "Information",
            style = MaterialTheme.typography.headlineSmall,
            modifier = Modifier.padding(bottom = 8.dp)
        )

        InfoCard(
            title = "Performance Note",
            description = "Liquid glass effects work best on Android 12+. " +
                "Lens effects require Android 13+. Effects are optimized " +
                "to have minimal performance impact."
        )

        InfoCard(
            title = "Supported Screens",
            description = "Liquid UI is applied to: Player controls, " +
                "Browser cards, Dialogs, Buttons, Toggles, Bottom bars, " +
                "and more throughout the app."
        )

        InfoCard(
            title = "Compatibility",
            description = "All features work with your device. Some effects " +
                "may be automatically disabled on older Android versions."
        )
    }
}

/**
 * Info Card for displaying information
 */
@Composable
private fun InfoCard(
    title: String,
    description: String,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp)
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleSmall
            )
            Text(
                text = description,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 4.dp)
            )
        }
    }
}
