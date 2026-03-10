package app.marlboroadvance.mpvex.ui.preferences

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import app.marlboroadvance.mpvex.R
import app.marlboroadvance.mpvex.preferences.AppearancePreferences
import app.marlboroadvance.mpvex.preferences.BrowserPreferences
import app.marlboroadvance.mpvex.preferences.GesturePreferences
import app.marlboroadvance.mpvex.preferences.LiquidUIPreferences
import app.marlboroadvance.mpvex.preferences.LiquidTarget
import app.marlboroadvance.mpvex.preferences.MultiChoiceSegmentedButton
import app.marlboroadvance.mpvex.preferences.preference.collectAsState
import app.marlboroadvance.mpvex.presentation.Screen
import app.marlboroadvance.mpvex.ui.components.liquid.AdaptiveToggle
import app.marlboroadvance.mpvex.ui.components.liquid.LiquidSwitchPreference
import app.marlboroadvance.mpvex.ui.preferences.components.ThemePicker
import app.marlboroadvance.mpvex.ui.theme.DarkMode
import app.marlboroadvance.mpvex.ui.utils.LocalBackStack
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.launch
import kotlinx.serialization.Serializable
import me.zhanghai.compose.preference.ProvidePreferenceLocals
import me.zhanghai.compose.preference.SliderPreference
import org.koin.compose.koinInject
import kotlin.math.roundToInt
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState


@Serializable
object AppearancePreferencesScreen : Screen {
    @OptIn(ExperimentalMaterial3Api::class)
    @Composable
    override fun Content() {
        val preferences = koinInject<AppearancePreferences>()
        val context = LocalContext.current
        val liquidPreferences = remember { LiquidUIPreferences(context) }
        val scope = rememberCoroutineScope()
        val browserPreferences = koinInject<BrowserPreferences>()
        val gesturePreferences = koinInject<GesturePreferences>()
        val backstack = LocalBackStack.current
        val systemDarkTheme = isSystemInDarkTheme()

        val darkMode by preferences.darkMode.collectAsState()
        val appTheme by preferences.appTheme.collectAsState()

        val isDarkMode = when (darkMode) {
            DarkMode.Dark -> true
            DarkMode.Light -> false
            DarkMode.System -> systemDarkTheme
        }

        val isLiquidUIEnabled by liquidPreferences.liquidUIEnabledFlow.collectAsState(initial = false)
        val toggleColor by liquidPreferences.liquidToggleColorFlow.collectAsState(initial = 0xFF4CAF50)
        val sliderColor by liquidPreferences.liquidSliderColorFlow.collectAsState(initial = 0xFF2196F3)

        // --- THE ACTIVE TARGET STATE ---
        var selectedTarget by remember { mutableStateOf(LiquidTarget.NAV) }

        // --- SAFE DYNAMIC DATA LOADING (Wrapped in remember blocks) ---
        val blurRadius by remember(selectedTarget) { liquidPreferences.blurRadiusFlow(selectedTarget) }.collectAsState(initial = 0f)
        val refractionHeight by remember(selectedTarget) { liquidPreferences.refractionHeightFlow(selectedTarget) }.collectAsState(initial = 40f)
        val refractionAmount by remember(selectedTarget) { liquidPreferences.refractionAmountFlow(selectedTarget) }.collectAsState(initial = 23f)
        val tintAlpha by remember(selectedTarget) { liquidPreferences.tintAlphaFlow(selectedTarget) }.collectAsState(initial = 0.15f)
        val chromaticAberration by remember(selectedTarget) { liquidPreferences.chromaticAberrationFlow(selectedTarget) }.collectAsState(initial = false)
        val depthEffect by remember(selectedTarget) { liquidPreferences.depthEffectFlow(selectedTarget) }.collectAsState(initial = true)
        val vibrancyEnabled by remember(selectedTarget) { liquidPreferences.vibrancyEnabledFlow(selectedTarget) }.collectAsState(initial = true)

        Scaffold(
            topBar = {
                TopAppBar(
                    title = {
                        Text(
                            text = stringResource(R.string.pref_appearance_title),
                            style = MaterialTheme.typography.headlineSmall,
                            fontWeight = FontWeight.ExtraBold,
                            color = MaterialTheme.colorScheme.primary,
                        )
                    },
                    navigationIcon = {
                        IconButton(onClick = backstack::removeLastOrNull) {
                            Icon(Icons.AutoMirrored.Outlined.ArrowBack, contentDescription = null, tint = MaterialTheme.colorScheme.secondary)
                        }
                    },
                )
            },
        ) { padding ->
            ProvidePreferenceLocals {
                LazyColumn(modifier = Modifier.fillMaxSize().padding(padding)) {
                    item { PreferenceSectionHeader(title = stringResource(id = R.string.pref_appearance_category_theme)) }

                    item {
                        PreferenceCard {
                            Column(modifier = Modifier.padding(vertical = 8.dp)) {
                                MultiChoiceSegmentedButton(
                                    choices = DarkMode.entries.map { stringResource(it.titleRes) }.toImmutableList(),
                                    selectedIndices = persistentListOf(DarkMode.entries.indexOf(darkMode)),
                                    onClick = { preferences.darkMode.set(DarkMode.entries[it]) },
                                )
                            }

                            PreferenceDivider()

                            val amoledMode by preferences.amoledMode.collectAsState()

                            ThemePicker(
                                currentTheme = appTheme,
                                isDarkMode = isDarkMode,
                                onThemeSelected = { preferences.appTheme.set(it) },
                                modifier = Modifier.padding(vertical = 8.dp),
                            )

                            PreferenceDivider()

                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { scope.launch { liquidPreferences.setLiquidUIEnabled(!isLiquidUIEnabled) } }
                                    .padding(horizontal = 16.dp, vertical = 16.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(1f).padding(end = 16.dp)) {
                                    Text(text = "Enable Liquid Glass UI", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface)
                                    Text(text = "Transform controls with modern glass effects", color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodyMedium)
                                }
                                AdaptiveToggle(
                                    checked = isLiquidUIEnabled,
                                    onCheckedChange = { enabled -> scope.launch { liquidPreferences.setLiquidUIEnabled(enabled) } },
                                    preferences = liquidPreferences
                                )
                            }

                            AnimatedVisibility(visible = isLiquidUIEnabled) {
                                Column {
                                    Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
                                        Text("Liquid UI Colors", style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary, modifier = Modifier.padding(bottom = 8.dp))
                                        
                                        fun parseColorInput(input: String): Long? {
                                            return try {
                                                val formatted = if (input.matches(Regex("^[0-9A-Fa-f]{6,8}$"))) "#$input" else input
                                                val colorInt = android.graphics.Color.parseColor(formatted)
                                                colorInt.toLong() and 0xFFFFFFFFL
                                            } catch (e: Exception) { null }
                                        }

                                        var toggleInputText by remember(toggleColor) { mutableStateOf(String.format("#%06X", 0xFFFFFF and toggleColor.toInt())) }
                                        OutlinedTextField(
                                            value = toggleInputText,
                                            onValueChange = { newValue ->
                                                toggleInputText = newValue
                                                parseColorInput(newValue)?.let { colorLong -> scope.launch { liquidPreferences.setToggleColor(colorLong) } }
                                            },
                                            label = { Text("Toggle Color") },
                                            modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp),
                                            singleLine = true,
                                            leadingIcon = { Box(modifier = Modifier.size(24.dp).clip(CircleShape).background(Color(toggleColor))) }
                                        )

                                        var sliderInputText by remember(sliderColor) { mutableStateOf(String.format("#%06X", 0xFFFFFF and sliderColor.toInt())) }
                                        OutlinedTextField(
                                            value = sliderInputText,
                                            onValueChange = { newValue ->
                                                sliderInputText = newValue
                                                parseColorInput(newValue)?.let { colorLong -> scope.launch { liquidPreferences.setSliderColor(colorLong) } }
                                            },
                                            label = { Text("Slider Color") },
                                            modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp),
                                            singleLine = true,
                                            leadingIcon = { Box(modifier = Modifier.size(24.dp).clip(CircleShape).background(Color(sliderColor))) }
                                        )
                                    }

                                    PreferenceDivider()
                                    
                                                                        // --- THE NEW PREMIUM TARGET SELECTOR ---
                                    Column(modifier = Modifier.fillMaxWidth().padding(vertical = 12.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                                        Text("Select UI Layer to Tune:", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.outline, modifier = Modifier.padding(bottom = 8.dp))
                                        
                                        // The Row is now safely scrollable so it won't break its layout!
                                        Row(
                                            modifier = Modifier
                                                .horizontalScroll(rememberScrollState()) 
                                                .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f), CircleShape)
                                                .padding(4.dp),
                                            horizontalArrangement = Arrangement.Center
                                        ) {
                                            LiquidTarget.values().forEach { target ->
                                                val isSelected = selectedTarget == target
                                                Surface(
                                                    modifier = Modifier.clickable { selectedTarget = target }.padding(horizontal = 4.dp),
                                                    shape = CircleShape,
                                                    color = if (isSelected) MaterialTheme.colorScheme.primary else Color.Transparent,
                                                    contentColor = if (isSelected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant
                                                ) {
                                                    // maxLines = 1 completely prevents the "wrapping" bug
                                                    Text(
                                                        text = target.title, 
                                                        maxLines = 1, 
                                                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp), 
                                                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                                                    )
                                                }
                                            }
                                        }
                                    }


                                    // --- THE SLIDERS ---
                                    Text("${selectedTarget.title} Tuning", style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary, modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp))

                                    SliderPreference(
                                        value = blurRadius,
                                        onValueChange = { v -> scope.launch { liquidPreferences.setBlurRadius(selectedTarget, v) } },
                                        sliderValue = blurRadius,
                                        onSliderValueChange = { v -> scope.launch { liquidPreferences.setBlurRadius(selectedTarget, v) } },
                                        title = { Text("Blur Radius") },
                                        valueRange = 0f..64f,
                                        summary = { Text("${blurRadius.roundToInt()} px", color = MaterialTheme.colorScheme.outline) }
                                    )

                                    SliderPreference(
                                        value = refractionHeight,
                                        onValueChange = { v -> scope.launch { liquidPreferences.setRefractionHeight(selectedTarget, v) } },
                                        sliderValue = refractionHeight,
                                        onSliderValueChange = { v -> scope.launch { liquidPreferences.setRefractionHeight(selectedTarget, v) } },
                                        title = { Text("Refraction Height") },
                                        valueRange = 0f..100f,
                                        summary = { Text("${refractionHeight.roundToInt()} dp", color = MaterialTheme.colorScheme.outline) }
                                    )

                                    SliderPreference(
                                        value = refractionAmount,
                                        onValueChange = { v -> scope.launch { liquidPreferences.setRefractionAmount(selectedTarget, v) } },
                                        sliderValue = refractionAmount,
                                        onSliderValueChange = { v -> scope.launch { liquidPreferences.setRefractionAmount(selectedTarget, v) } },
                                        title = { Text("Refraction Amount") },
                                        valueRange = 0f..100f,
                                        summary = { Text("${refractionAmount.roundToInt()} dp", color = MaterialTheme.colorScheme.outline) }
                                    )

                                    SliderPreference(
                                        value = tintAlpha,
                                        onValueChange = { v -> scope.launch { liquidPreferences.setTintAlpha(selectedTarget, v) } },
                                        sliderValue = tintAlpha,
                                        onSliderValueChange = { v -> scope.launch { liquidPreferences.setTintAlpha(selectedTarget, v) } },
                                        title = { Text("Glass Opacity (Tint)") },
                                        valueRange = 0.0f..1.0f,
                                        summary = { Text("${(tintAlpha * 100).roundToInt()}%", color = MaterialTheme.colorScheme.outline) }
                                    )

                                    PreferenceDivider()

                                    LiquidSwitchPreference(
                                        value = depthEffect,
                                        onValueChange = { v -> scope.launch { liquidPreferences.setDepthEffect(selectedTarget, v) } },
                                        title = { Text("Depth Effect") },
                                        summary = { Text("Enables 3D lens depth calculation", color = MaterialTheme.colorScheme.outline) }
                                    )

                                    LiquidSwitchPreference(
                                        value = chromaticAberration,
                                        onValueChange = { v -> scope.launch { liquidPreferences.setChromaticAberration(selectedTarget, v) } },
                                        title = { Text("Chromatic Aberration") },
                                        summary = { Text("Enables RGB color-split on glass edges", color = MaterialTheme.colorScheme.outline) }
                                    )

                                    LiquidSwitchPreference(
                                        value = vibrancyEnabled,
                                        onValueChange = { v -> scope.launch { liquidPreferences.setVibrancyEnabled(selectedTarget, v) } },
                                        title = { Text("Vibrancy") },
                                        summary = { Text("Multiplies background saturation by 1.5x", color = MaterialTheme.colorScheme.outline) }
                                    )
                                }
                            }

                            PreferenceDivider()
                            
                            LiquidSwitchPreference(
                                value = amoledMode,
                                onValueChange = { newValue -> preferences.amoledMode.set(newValue) },
                                title = { Text(text = stringResource(id = R.string.pref_appearance_amoled_mode_title)) },
                                summary = { Text(text = stringResource(id = R.string.pref_appearance_amoled_mode_summary), color = MaterialTheme.colorScheme.outline) },
                                enabled = darkMode != DarkMode.Light
                            )
                        }
                    }

                    item { PreferenceSectionHeader(title = stringResource(id = R.string.pref_appearance_category_file_browser)) }

                    item {
                        PreferenceCard {
                            val unlimitedNameLines by preferences.unlimitedNameLines.collectAsState()
                            LiquidSwitchPreference(
                                value = unlimitedNameLines,
                                onValueChange = { preferences.unlimitedNameLines.set(it) },
                                title = { Text(text = stringResource(id = R.string.pref_appearance_unlimited_name_lines_title)) },
                                summary = { Text(text = stringResource(id = R.string.pref_appearance_unlimited_name_lines_summary), color = MaterialTheme.colorScheme.outline) }
                            )

                            PreferenceDivider()

                            val showUnplayedOldVideoLabel by preferences.showUnplayedOldVideoLabel.collectAsState()
                            LiquidSwitchPreference(
                                value = showUnplayedOldVideoLabel,
                                onValueChange = { preferences.showUnplayedOldVideoLabel.set(it) },
                                title = { Text(text = stringResource(id = R.string.pref_appearance_show_unplayed_old_video_label_title)) },
                                summary = { Text(text = stringResource(id = R.string.pref_appearance_show_unplayed_old_video_label_summary), color = MaterialTheme.colorScheme.outline) }
                            )

                            PreferenceDivider()

                            val unplayedOldVideoDays by preferences.unplayedOldVideoDays.collectAsState()
                            SliderPreference(
                                value = unplayedOldVideoDays.toFloat(),
                                onValueChange = { preferences.unplayedOldVideoDays.set(it.roundToInt()) },
                                sliderValue = unplayedOldVideoDays.toFloat(),
                                onSliderValueChange = { preferences.unplayedOldVideoDays.set(it.roundToInt()) },
                                title = { Text(text = stringResource(id = R.string.pref_appearance_unplayed_old_video_days_title)) },
                                valueRange = 1f..30f,
                                enabled = showUnplayedOldVideoLabel,
                                summary = { Text(text = stringResource(id = R.string.pref_appearance_unplayed_old_video_days_summary, unplayedOldVideoDays), color = MaterialTheme.colorScheme.outline) }
                            )

                            PreferenceDivider()

                            val autoScrollToLastPlayed by browserPreferences.autoScrollToLastPlayed.collectAsState()
                            LiquidSwitchPreference(
                                value = autoScrollToLastPlayed,
                                onValueChange = { browserPreferences.autoScrollToLastPlayed.set(it) },
                                title = { Text(text = stringResource(R.string.pref_appearance_auto_scroll_title)) },
                                summary = { Text(text = stringResource(R.string.pref_appearance_auto_scroll_summary), color = MaterialTheme.colorScheme.outline) }
                            )

                            PreferenceDivider()

                            val watchedThreshold by browserPreferences.watchedThreshold.collectAsState()
                            SliderPreference(
                                value = watchedThreshold.toFloat(),
                                onValueChange = { browserPreferences.watchedThreshold.set(it.roundToInt()) },
                                sliderValue = watchedThreshold.toFloat(),
                                onSliderValueChange = { browserPreferences.watchedThreshold.set(it.roundToInt()) },
                                title = { Text(text = stringResource(id = R.string.pref_appearance_watched_threshold_title)) },
                                valueRange = 50f..100f,
                                valueSteps = 9,
                                summary = { Text(text = stringResource(id = R.string.pref_appearance_watched_threshold_summary, watchedThreshold), color = MaterialTheme.colorScheme.outline) }
                            )

                            PreferenceDivider()

                            val tapThumbnailToSelect by gesturePreferences.tapThumbnailToSelect.collectAsState()
                            LiquidSwitchPreference(
                                value = tapThumbnailToSelect,
                                onValueChange = { gesturePreferences.tapThumbnailToSelect.set(it) },
                                title = { Text(text = stringResource(id = R.string.pref_gesture_tap_thumbnail_to_select_title)) },
                                summary = { Text(text = stringResource(id = R.string.pref_gesture_tap_thumbnail_to_select_summary), color = MaterialTheme.colorScheme.outline) }
                            )

                            PreferenceDivider()

                            val showNetworkThumbnails by preferences.showNetworkThumbnails.collectAsState()
                            LiquidSwitchPreference(
                                value = showNetworkThumbnails,
                                onValueChange = { preferences.showNetworkThumbnails.set(it) },
                                title = { Text(text = stringResource(id = R.string.pref_appearance_show_network_thumbnails_title)) },
                                summary = { Text(text = stringResource(id = R.string.pref_appearance_show_network_thumbnails_summary), color = MaterialTheme.colorScheme.outline) }
                            )
                        }
                    }
                }
            }
        }
    }
}
