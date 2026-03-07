package app.marlboroadvance.mpvex.ui.screens.settings

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Card
import androidx.compose.material3.Divider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import app.marlboroadvance.mpvex.preferences.LiquidUIPreferences
import app.marlboroadvance.mpvex.ui.components.liquid.AdaptiveToggle
import kotlinx.coroutines.launch

@Composable
fun LiquidUISettingsScreen(
    preferences: LiquidUIPreferences,
    modifier: Modifier = Modifier
) {
    val scope = rememberCoroutineScope()
    
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
            
            AdaptiveToggle(
                checked = liquidUIEnabled,
                onCheckedChange = { enabled ->
                    scope.launch { preferences.setLiquidUIEnabled(enabled) }
                },
                preferences = preferences
            )
        }

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
                
                fun parseColorInput(input: String): Long? {
                    return try {
                        val formatted = if (input.matches(Regex("^[0-9A-Fa-f]{6,8}$"))) "#$input" else input
                        val colorInt = android.graphics.Color.parseColor(formatted)
                        colorInt.toLong() and 0xFFFFFFFFL
                    } catch (e: Exception) { null }
                }

                var toggleInputText by remember(toggleColor) { 
                    mutableStateOf(String.format("#%06X", 0xFFFFFF and toggleColor.toInt())) 
                }
                
                OutlinedTextField(
                    value = toggleInputText,
                    onValueChange = { newValue ->
                        toggleInputText = newValue
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
                                .clip(CircleShape)
                                .background(Color(toggleColor))
                        )
                    }
                )

                var sliderInputText by remember(sliderColor) { 
                    mutableStateOf(String.format("#%06X", 0xFFFFFF and sliderColor.toInt())) 
                }
                
                OutlinedTextField(
                    value = sliderInputText,
                    onValueChange = { newValue ->
                        sliderInputText = newValue
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
                                .clip(CircleShape)
                                .background(Color(sliderColor))
                        )
                    }
                )
            }
        }

        Divider(modifier = Modifier.padding(vertical = 16.dp))
        
        Text(
            text = "Effect Settings",
            style = MaterialTheme.typography.headlineSmall,
            modifier = Modifier.padding(bottom = 8.dp)
        )
        
        Text(
            text = "Customize which effects are applied",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(bottom = 16.dp)
        )

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
                    scope.launch { preferences.setBlurEnabled(enabled) }
                },
                preferences = preferences,
                enabled = liquidUIEnabled
            )
        }

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
                    text = "Adds refraction lens effect (Android 13+)",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            
            AdaptiveToggle(
                checked = lensEnabled,
                onCheckedChange = { enabled ->
                    scope.launch { preferences.setLensEnabled(enabled) }
                },
                preferences = preferences,
                enabled = liquidUIEnabled && android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU
            )
        }

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
                    scope.launch { preferences.setVibrancyEnabled(enabled) }
                },
                preferences = preferences,
                enabled = liquidUIEnabled
            )
        }

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
    }
}

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
