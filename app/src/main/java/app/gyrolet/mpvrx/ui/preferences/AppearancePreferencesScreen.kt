package app.gyrolet.mpvrx.ui.preferences

import app.gyrolet.mpvrx.ui.icons.Icon
import app.gyrolet.mpvrx.ui.icons.Icons

import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import app.gyrolet.mpvrx.R
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Button
import androidx.compose.material3.TextButton
import androidx.compose.ui.graphics.Color
import app.gyrolet.mpvrx.ui.components.LiquidToggle
import app.gyrolet.mpvrx.ui.preferences.components.AdaptiveSwitchPreference
import com.kyant.backdrop.backdrops.rememberLayerBackdrop
import app.gyrolet.mpvrx.domain.thumbnail.ThumbnailRepository
import app.gyrolet.mpvrx.preferences.AppearancePreferences
import app.gyrolet.mpvrx.preferences.BrowserPreferences
import app.gyrolet.mpvrx.preferences.GesturePreferences
import app.gyrolet.mpvrx.preferences.PlayerPreferences
import app.gyrolet.mpvrx.ui.player.ControlsAnimationStyle
import app.gyrolet.mpvrx.ui.player.NavigationAnimStyle
import app.gyrolet.mpvrx.ui.player.VideoOpenAnimation
import app.gyrolet.mpvrx.ui.player.controls.components.sheets.toFixed
import app.gyrolet.mpvrx.preferences.MultiChoiceSegmentedButton
import app.gyrolet.mpvrx.preferences.ThumbnailMode
import app.gyrolet.mpvrx.preferences.preference.collectAsState
import app.gyrolet.mpvrx.presentation.Screen
import app.gyrolet.mpvrx.presentation.components.ConfirmDialog
import app.gyrolet.mpvrx.ui.preferences.components.ThemePicker
import app.gyrolet.mpvrx.ui.theme.DarkMode
import app.gyrolet.mpvrx.ui.utils.LocalBackStack
import app.gyrolet.mpvrx.ui.utils.popSafely
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import me.zhanghai.compose.preference.ListPreference
import me.zhanghai.compose.preference.ProvidePreferenceLocals
import me.zhanghai.compose.preference.SliderPreference
import me.zhanghai.compose.preference.SwitchPreference
import org.koin.compose.koinInject
import kotlin.math.roundToInt

@Serializable
object AppearancePreferencesScreen : Screen {
    @OptIn(ExperimentalMaterial3Api::class)
    @Composable
    override fun Content() {
        val context = LocalContext.current
        val preferences = koinInject<AppearancePreferences>()
        val browserPreferences = koinInject<BrowserPreferences>()
        val gesturePreferences = koinInject<GesturePreferences>()
        val playerPreferences = koinInject<PlayerPreferences>()
        val thumbnailRepository = koinInject<ThumbnailRepository>()
        val backstack = LocalBackStack.current
        val scope = rememberCoroutineScope()
        val systemDarkTheme = isSystemInDarkTheme()

        val darkMode by preferences.darkMode.collectAsState()
        val appTheme by preferences.appTheme.collectAsState()
        var pendingThumbnailMode by remember { mutableStateOf<ThumbnailMode?>(null) }
        var isThemeSectionExpanded by rememberSaveable { mutableStateOf(true) }
        val storedThumbnailMode by browserPreferences.thumbnailMode.collectAsState()
        val thumbnailFramePosition by browserPreferences.thumbnailFramePosition.collectAsState()
        val thumbnailCacheClearedMessage = stringResource(R.string.pref_thumbnail_cache_cleared)

        val thumbnailMode = storedThumbnailMode

        // Determine if we're in dark mode for theme preview
        val isDarkMode = when (darkMode) {
            DarkMode.Dark -> true
            DarkMode.Light -> false
            DarkMode.System -> systemDarkTheme
        }

        if (pendingThumbnailMode != null) {
            ConfirmDialog(
                title = stringResource(R.string.pref_appearance_thumbnail_generation_change_title),
                subtitle = stringResource(R.string.pref_appearance_thumbnail_generation_change_summary),
                onConfirm = {
                    val selectedMode = pendingThumbnailMode
                    pendingThumbnailMode = null
                    if (selectedMode != null) {
                        browserPreferences.thumbnailMode.set(selectedMode)
                        scope.launch {
                            val result = withContext(Dispatchers.IO) {
                                runCatching { thumbnailRepository.clearThumbnailCache() }
                            }
                            result
                                .onSuccess {
                                    Toast
                                        .makeText(
                                            context,
                                            thumbnailCacheClearedMessage,
                                            Toast.LENGTH_SHORT,
                                        ).show()
                                }.onFailure { error ->
                                    Toast
                                        .makeText(
                                            context,
                                            context.resources.getString(
                                                R.string.pref_thumbnail_cache_clear_failed,
                                                error.message ?: "Unknown error",
                                            ),
                                            Toast.LENGTH_LONG,
                                        ).show()
                                }
                        }
                    }
                },
                onCancel = { pendingThumbnailMode = null },
            )
        }

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
                        IconButton(onClick = { backstack.popSafely() }) {
                            Icon(
                                Icons.Outlined.ArrowBack,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.secondary,
                            )
                        }
                    },
                )
            },
        ) { padding ->
            ProvidePreferenceLocals {
                LazyColumn(
                    modifier =
                    Modifier
                        .fillMaxSize()
                        .padding(padding),
                ) {
                    item {
                        PreferenceSectionHeader(title = stringResource(id = R.string.pref_appearance_category_theme))
                    }

                    item {
                        PreferenceCard {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { isThemeSectionExpanded = !isThemeSectionExpanded }
                                    .padding(horizontal = 20.dp, vertical = 12.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Column(
                                    modifier = Modifier.weight(1f),
                                    verticalArrangement = Arrangement.spacedBy(2.dp),
                                ) {
                                    Text(
                                        text = stringResource(id = R.string.pref_appearance_category_theme),
                                        style = MaterialTheme.typography.titleMedium,
                                        fontWeight = FontWeight.SemiBold,
                                    )
                                    Text(
                                        text = "${stringResource(darkMode.titleRes)} · ${stringResource(appTheme.titleRes)}",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.outline,
                                    )
                                }
                                Icon(
                                    imageVector = if (isThemeSectionExpanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                                    contentDescription = null,
                                    modifier = Modifier.size(20.dp),
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }

                            AnimatedVisibility(visible = isThemeSectionExpanded) {
                                Column {
                                    PreferenceDivider()

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

                                    AdaptiveSwitchPreference(
                                        value = amoledMode,
                                        onValueChange = { newValue ->
                                            preferences.amoledMode.set(newValue)
                                        },
                                        title = { Text(text = stringResource(id = R.string.pref_appearance_amoled_mode_title)) },
                                        summary = {
                                            Text(
                                                text = stringResource(id = R.string.pref_appearance_amoled_mode_summary),
                                                color = MaterialTheme.colorScheme.outline,
                                            )
                                        },
                                        enabled = darkMode != DarkMode.Light
                                    )

                                    PreferenceDivider()

                                    val useSystemFont by preferences.useSystemFont.collectAsState()
                                    AdaptiveSwitchPreference(
                                        value = useSystemFont,
                                        onValueChange = preferences.useSystemFont::set,
                                        title = { Text(text = stringResource(id = R.string.pref_appearance_system_font_title)) },
                                        summary = {
                                            Text(
                                                text = stringResource(id = R.string.pref_appearance_system_font_summary),
                                                color = MaterialTheme.colorScheme.outline,
                                            )
                                        },
                                    )
                                }
                            }
                        }
                    }

                    item {
                        PreferenceSectionHeader(title = stringResource(id = R.string.pref_appearance_category_file_browser))
                    }

                    item {
                        PreferenceCard {
                            val unlimitedNameLines by preferences.unlimitedNameLines.collectAsState()
                            AdaptiveSwitchPreference(
                                value = unlimitedNameLines,
                                onValueChange = { preferences.unlimitedNameLines.set(it) },
                                title = {
                                    Text(
                                        text = stringResource(id = R.string.pref_appearance_unlimited_name_lines_title),
                                    )
                                },
                                summary = {
                                    Text(
                                        text = stringResource(id = R.string.pref_appearance_unlimited_name_lines_summary),
                                        color = MaterialTheme.colorScheme.outline,
                                    )
                                }
                            )

                            PreferenceDivider()

                            val showUnplayedOldVideoLabel by preferences.showUnplayedOldVideoLabel.collectAsState()
                            AdaptiveSwitchPreference(
                                value = showUnplayedOldVideoLabel,
                                onValueChange = { preferences.showUnplayedOldVideoLabel.set(it) },
                                title = {
                                    Text(
                                        text = stringResource(id = R.string.pref_appearance_show_unplayed_old_video_label_title),
                                    )
                                },
                                summary = {
                                    Text(
                                        text = stringResource(id = R.string.pref_appearance_show_unplayed_old_video_label_summary),
                                        color = MaterialTheme.colorScheme.outline,
                                    )
                                }
                            )

                            PreferenceDivider()

                            val unplayedOldVideoDays by preferences.unplayedOldVideoDays.collectAsState()
                            SliderPreference(
                                value = unplayedOldVideoDays.toFloat(),
                                onValueChange = { preferences.unplayedOldVideoDays.set(it.roundToInt()) },
                                title = { Text(text = stringResource(id = R.string.pref_appearance_unplayed_old_video_days_title)) },
                                valueRange = 1f..30f,
                                summary = {
                                    Text(
                                        text = stringResource(
                                            id = R.string.pref_appearance_unplayed_old_video_days_summary,
                                            unplayedOldVideoDays,
                                        ),
                                        color = MaterialTheme.colorScheme.outline,
                                    )
                                },
                                onSliderValueChange = { preferences.unplayedOldVideoDays.set(it.roundToInt()) },
                                sliderValue = unplayedOldVideoDays.toFloat(),
                                enabled = showUnplayedOldVideoLabel
                            )

                            PreferenceDivider()

                            val autoScrollToLastPlayed by browserPreferences.autoScrollToLastPlayed.collectAsState()
                            AdaptiveSwitchPreference(
                                value = autoScrollToLastPlayed,
                                onValueChange = { browserPreferences.autoScrollToLastPlayed.set(it) },
                                title = {
                                    Text(text = stringResource(R.string.pref_appearance_auto_scroll_title))
                                },
                                summary = {
                                    Text(
                                        text = stringResource(R.string.pref_appearance_auto_scroll_summary),
                                        color = MaterialTheme.colorScheme.outline,
                                    )
                                }
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
                                summary = {
                                    Text(
                                        text = stringResource(
                                            id = R.string.pref_appearance_watched_threshold_summary,
                                            watchedThreshold,
                                        ),
                                        color = MaterialTheme.colorScheme.outline,
                                    )
                                },
                            )
                        }
                    }

                    item {
                        PreferenceSectionHeader(title = stringResource(id = R.string.pref_appearance_category_thumbnails))
                    }

                    item {
                        PreferenceCard {
                            val showVideoThumbnails by browserPreferences.showVideoThumbnails.collectAsState()
                            AdaptiveSwitchPreference(
                                value = showVideoThumbnails,
                                onValueChange = { browserPreferences.showVideoThumbnails.set(it) },
                                title = {
                                    Text(text = stringResource(id = R.string.pref_appearance_show_video_thumbnails_title))
                                },
                                summary = {
                                    Text(
                                        text = stringResource(id = R.string.pref_appearance_show_video_thumbnails_summary),
                                        color = MaterialTheme.colorScheme.outline,
                                    )
                                }
                            )

                            PreferenceDivider()

                            ListPreference(
                                value = thumbnailMode,
                                onValueChange = { newMode ->
                                    if (newMode != thumbnailMode) {
                                        pendingThumbnailMode = newMode
                                    }
                                },
                                values = ThumbnailMode.entries,
                                valueToText = { AnnotatedString(it.displayName) },
                                title = { Text(text = stringResource(id = R.string.pref_appearance_thumbnail_generation_title)) },
                                summary = {
                                    Text(
                                        text = when (thumbnailMode) {
                                            ThumbnailMode.FrameAtPosition ->
                                                "${thumbnailMode.displayName} (${thumbnailFramePosition.roundToInt()}%)"
                                            else -> thumbnailMode.displayName
                                        },
                                        color = MaterialTheme.colorScheme.outline,
                                    )
                                },
                                enabled = showVideoThumbnails,
                            )

                            if (thumbnailMode == ThumbnailMode.FrameAtPosition) {
                                PreferenceDivider()

                                SliderPreference(
                                    value = thumbnailFramePosition,
                                    onValueChange = { browserPreferences.thumbnailFramePosition.set(it) },
                                    sliderValue = thumbnailFramePosition,
                                    onSliderValueChange = { browserPreferences.thumbnailFramePosition.set(it) },
                                    title = {
                                        Text(text = stringResource(id = R.string.pref_appearance_thumbnail_position_title))
                                    },
                                    valueRange = 0f..100f,
                                    valueSteps = 99,
                                    summary = {
                                        Text(
                                            text = stringResource(
                                                id = R.string.pref_appearance_thumbnail_position_summary,
                                                thumbnailFramePosition.roundToInt(),
                                            ),
                                            color = MaterialTheme.colorScheme.outline,
                                        )
                                    },
                                    enabled = showVideoThumbnails,
                                )
                            }

                            PreferenceDivider()

                            val tapThumbnailToSelect by gesturePreferences.tapThumbnailToSelect.collectAsState()
                            AdaptiveSwitchPreference(
                                value = tapThumbnailToSelect,
                                onValueChange = { gesturePreferences.tapThumbnailToSelect.set(it) },
                                title = {
                                    Text(
                                        text = stringResource(id = R.string.pref_gesture_tap_thumbnail_to_select_title),
                                    )
                                },
                                summary = {
                                    Text(
                                        text = stringResource(id = R.string.pref_gesture_tap_thumbnail_to_select_summary),
                                        color = MaterialTheme.colorScheme.outline,
                                    )
                                },
                                enabled = showVideoThumbnails,
                            )

                            PreferenceDivider()

                            val showNetworkThumbnails by preferences.showNetworkThumbnails.collectAsState()
                            AdaptiveSwitchPreference(
                                value = showNetworkThumbnails,
                                onValueChange = { preferences.showNetworkThumbnails.set(it) },
                                title = {
                                    Text(
                                        text = stringResource(id = R.string.pref_appearance_show_network_thumbnails_title),
                                    )
                                },
                                summary = {
                                    Text(
                                        text = stringResource(id = R.string.pref_appearance_show_network_thumbnails_summary),
                                        color = MaterialTheme.colorScheme.outline,
                                    )
                                },
                                enabled = showVideoThumbnails,
                            )
                        }
                    }

                    item {
                        PreferenceSectionHeader(title = stringResource(id = R.string.pref_appearance_category_navigation))
                    }

                    item {
                        PreferenceCard {
                            val showHomeTab by preferences.showHomeTab.collectAsState()
                            val showRecentsTab by preferences.showRecentsTab.collectAsState()
                            val showPlaylistsTab by preferences.showPlaylistsTab.collectAsState()
                            val showNetworkTab by preferences.showNetworkTab.collectAsState()

                            AdaptiveSwitchPreference(
                                value = showHomeTab,
                                onValueChange = preferences.showHomeTab::set,
                                title = { Text(text = stringResource(id = R.string.pref_nav_home_title)) },
                                summary = {
                                    Text(
                                        text = stringResource(id = R.string.pref_nav_home_summary),
                                        color = MaterialTheme.colorScheme.outline,
                                    )
                                },
                            )

                            PreferenceDivider()

                            AdaptiveSwitchPreference(
                                value = showRecentsTab,
                                onValueChange = preferences.showRecentsTab::set,
                                title = { Text(text = stringResource(id = R.string.pref_nav_recents_title)) },
                                summary = {
                                    Text(
                                        text = stringResource(id = R.string.pref_nav_recents_summary),
                                        color = MaterialTheme.colorScheme.outline,
                                    )
                                },
                            )

                            PreferenceDivider()

                            AdaptiveSwitchPreference(
                                value = showPlaylistsTab,
                                onValueChange = preferences.showPlaylistsTab::set,
                                title = { Text(text = stringResource(id = R.string.pref_nav_playlists_title)) },
                                summary = {
                                    Text(
                                        text = stringResource(id = R.string.pref_nav_playlists_summary),
                                        color = MaterialTheme.colorScheme.outline,
                                    )
                                },
                            )

                            PreferenceDivider()

                            AdaptiveSwitchPreference(
                                value = showNetworkTab,
                                onValueChange = preferences.showNetworkTab::set,
                                title = { Text(text = stringResource(id = R.string.pref_nav_network_title)) },
                                summary = {
                                    Text(
                                        text = stringResource(id = R.string.pref_nav_network_summary),
                                        color = MaterialTheme.colorScheme.outline,
                                    )
                                },
                            )
                        }
                    }

                    // ── Animations ────────────────────────────────────────
                    item {
                        PreferenceSectionHeader(title = stringResource(R.string.pref_section_animations))
                    }

                    item {
                        PreferenceCard {
                            val controlsAnimStyle by playerPreferences.controlsAnimStyle.collectAsState()
                            ListPreference(
                                value = controlsAnimStyle,
                                onValueChange = playerPreferences.controlsAnimStyle::set,
                                values = ControlsAnimationStyle.entries,
                                valueToText = { AnnotatedString(it.displayName) },
                                title = { Text(stringResource(R.string.pref_anim_controls_style_title)) },
                                summary = { Text(controlsAnimStyle.displayName, color = MaterialTheme.colorScheme.outline) },
                            )

                            PreferenceDivider()

                            val videoOpenAnim by playerPreferences.videoOpenAnimation.collectAsState()
                            ListPreference(
                                value = videoOpenAnim,
                                onValueChange = playerPreferences.videoOpenAnimation::set,
                                values = VideoOpenAnimation.entries,
                                valueToText = { AnnotatedString(it.displayName) },
                                title = { Text(stringResource(R.string.pref_anim_video_open_title)) },
                                summary = { Text(videoOpenAnim.displayName, color = MaterialTheme.colorScheme.outline) },
                            )

                            PreferenceDivider()

                            val navAnimStyle by playerPreferences.navAnimStyle.collectAsState()
                            ListPreference(
                                value = navAnimStyle,
                                onValueChange = playerPreferences.navAnimStyle::set,
                                values = NavigationAnimStyle.entries,
                                valueToText = { AnnotatedString(it.displayName) },
                                title = { Text(stringResource(R.string.pref_anim_tab_nav_style_title)) },
                                summary = { Text(navAnimStyle.displayName, color = MaterialTheme.colorScheme.outline) },
                            )

                            PreferenceDivider()

                            val appNavStyle by playerPreferences.appNavStyle.collectAsState()
                            ListPreference(
                                value = appNavStyle,
                                onValueChange = playerPreferences.appNavStyle::set,
                                values = NavigationAnimStyle.entries,
                                valueToText = { AnnotatedString(it.displayName) },
                                title = { Text(stringResource(R.string.pref_anim_screen_nav_style_title)) },
                                summary = { Text(appNavStyle.displayName, color = MaterialTheme.colorScheme.outline) },
                            )

                            PreferenceDivider()

                            val enableLiquidGlass by preferences.enableLiquidGlass.collectAsState()
                            val liquidToggleColor by preferences.liquidToggleColor.collectAsState()
                            
                            AdaptiveSwitchPreference(
                                value = enableLiquidGlass,
                                onValueChange = { enabled ->
                                    if (enabled &&
                                        preferences.liquidButtonBlur.get() <= 0f &&
                                        preferences.liquidButtonLensRadius.get() <= 0f &&
                                        preferences.liquidButtonLensDepth.get() <= 0f
                                    ) {
                                        preferences.liquidButtonBlur.set(26f)
                                        preferences.liquidButtonLensRadius.set(42f)
                                        preferences.liquidButtonLensDepth.set(72f)
                                        preferences.liquidDialogBlur.set(32f)
                                        preferences.liquidDialogSaturation.set(1.3f)
                                        preferences.liquidDialogBrightness.set(0.08f)
                                        preferences.liquidDialogLensRadius.set(55f)
                                        preferences.liquidDialogLensDepth.set(85f)
                                        preferences.liquidDialogContainerAlpha.set(0.35f)
                                    }
                                    preferences.enableLiquidGlass.set(enabled)
                                },
                                title = { Text(text = stringResource(id = R.string.pref_anim_liquid_glass_title)) },
                                summary = {
                                    Text(
                                        text = stringResource(id = R.string.pref_anim_liquid_glass_summary),
                                        color = MaterialTheme.colorScheme.outline,
                                    )
                                }
                            )

                            PreferenceDivider()

                            var showLiquidDialogSettings by remember { mutableStateOf(false) }
                            if (enableLiquidGlass) {
                                PreferenceCard(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable { showLiquidDialogSettings = !showLiquidDialogSettings }
                                        .padding(horizontal = 20.dp, vertical = 12.dp)
                                ) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Column(modifier = Modifier.weight(1f)) {
                                            Text(
                                                text = "Liquid Dialog Parameters",
                                                style = MaterialTheme.typography.titleMedium,
                                                fontWeight = FontWeight.SemiBold,
                                            )
                                            Text(
                                                text = "Adjust blur, saturation, and lens effects for dialogs",
                                                style = MaterialTheme.typography.bodySmall,
                                                color = MaterialTheme.colorScheme.outline,
                                            )
                                        }
                                        Icon(
                                            imageVector = if (showLiquidDialogSettings) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                                            contentDescription = null,
                                            modifier = Modifier.size(20.dp),
                                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                        )
                                    }

                                    AnimatedVisibility(visible = showLiquidDialogSettings) {
                                        Column(modifier = Modifier.padding(top = 16.dp)) {
                                            var showPreview by remember { mutableStateOf(false) }

                                            if (showPreview) {
                                                ConfirmDialog(
                                                    title = "Liquid Dialog Preview",
                                                    subtitle = "This is how your dialogs and sheets will look with the current parameters.",
                                                    onConfirm = { showPreview = false },
                                                    onCancel = { showPreview = false }
                                                )
                                            }

                                            Button(
                                                onClick = { showPreview = true },
                                                modifier = Modifier.fillMaxWidth(),
                                                shape = MaterialTheme.shapes.extraLarge
                                            ) {
                                                Text("Show Live Preview")
                                            }

                                            Spacer(Modifier.height(16.dp))

                                            val liquidBlur by preferences.liquidDialogBlur.collectAsState()
                                            SliderPreference(
                                                value = liquidBlur,
                                                onValueChange = { preferences.liquidDialogBlur.set(it) },
                                                title = { Text("Blur Intensity") },
                                                valueRange = 0f..64f,
                                                sliderValue = liquidBlur,
                                                onSliderValueChange = { preferences.liquidDialogBlur.set(it) },
                                                summary = { Text("${liquidBlur.roundToInt()} dp") }
                                            )

                                            val liquidSaturation by preferences.liquidDialogSaturation.collectAsState()
                                            SliderPreference(
                                                value = liquidSaturation,
                                                onValueChange = { preferences.liquidDialogSaturation.set(it) },
                                                title = { Text("Saturation") },
                                                valueRange = 0f..3f,
                                                sliderValue = liquidSaturation,
                                                onSliderValueChange = { preferences.liquidDialogSaturation.set(it) },
                                                summary = { Text("%.2f".format(liquidSaturation)) }
                                            )

                                            val liquidBrightness by preferences.liquidDialogBrightness.collectAsState()
                                            SliderPreference(
                                                value = liquidBrightness,
                                                onValueChange = { preferences.liquidDialogBrightness.set(it) },
                                                title = { Text("Brightness Offset") },
                                                valueRange = -1f..1f,
                                                sliderValue = liquidBrightness,
                                                onSliderValueChange = { preferences.liquidDialogBrightness.set(it) },
                                                summary = { Text("%.2f".format(liquidBrightness)) }
                                            )

                                            val liquidLensRadius by preferences.liquidDialogLensRadius.collectAsState()
                                            SliderPreference(
                                                value = liquidLensRadius,
                                                onValueChange = { preferences.liquidDialogLensRadius.set(it) },
                                                title = { Text("Lens Radius") },
                                                valueRange = 0f..100f,
                                                sliderValue = liquidLensRadius,
                                                onSliderValueChange = { preferences.liquidDialogLensRadius.set(it) },
                                                summary = { Text("${liquidLensRadius.roundToInt()} dp") }
                                            )

                                            val liquidLensDepth by preferences.liquidDialogLensDepth.collectAsState()
                                            SliderPreference(
                                                value = liquidLensDepth,
                                                onValueChange = { preferences.liquidDialogLensDepth.set(it) },
                                                title = { Text("Lens Depth") },
                                                valueRange = 0f..200f,
                                                sliderValue = liquidLensDepth,
                                                onSliderValueChange = { preferences.liquidDialogLensDepth.set(it) },
                                                summary = { Text("${liquidLensDepth.roundToInt()} dp") }
                                            )

                                            val liquidAlpha by preferences.liquidDialogContainerAlpha.collectAsState()
                                            SliderPreference(
                                                value = liquidAlpha,
                                                onValueChange = { preferences.liquidDialogContainerAlpha.set(it) },
                                                title = { Text("Container Alpha") },
                                                valueRange = 0f..1f,
                                                sliderValue = liquidAlpha,
                                                onSliderValueChange = { preferences.liquidDialogContainerAlpha.set(it) },
                                                summary = { Text("%.2f".format(liquidAlpha)) }
                                            )

                                            TextButton(
                                                onClick = {
                                                    preferences.liquidDialogBlur.set(32f)
                                                    preferences.liquidDialogSaturation.set(1.3f)
                                                    preferences.liquidDialogBrightness.set(0.08f)
                                                    preferences.liquidDialogLensRadius.set(55f)
                                                    preferences.liquidDialogLensDepth.set(85f)
                                                    preferences.liquidDialogContainerAlpha.set(0.35f)
                                                },
                                                modifier = Modifier.align(Alignment.End)
                                            ) {
                                                Text(stringResource(R.string.generic_reset))
                                            }
                                        }
                                    }
                                }
                                PreferenceDivider()

                                var showLiquidButtonSettings by remember { mutableStateOf(false) }
                                PreferenceCard(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable { showLiquidButtonSettings = !showLiquidButtonSettings }
                                        .padding(horizontal = 20.dp, vertical = 12.dp)
                                ) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Column(modifier = Modifier.weight(1f)) {
                                            Text(
                                                text = "Liquid Button Parameters",
                                                style = MaterialTheme.typography.titleMedium,
                                                fontWeight = FontWeight.SemiBold,
                                            )
                                            Text(
                                                text = "Adjust blur and lens effects for liquid buttons",
                                                style = MaterialTheme.typography.bodySmall,
                                                color = MaterialTheme.colorScheme.outline,
                                            )
                                        }
                                        Icon(
                                            imageVector = if (showLiquidButtonSettings) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                                            contentDescription = null,
                                            modifier = Modifier.size(20.dp),
                                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                        )
                                    }

                                    AnimatedVisibility(visible = showLiquidButtonSettings) {
                                        Column(modifier = Modifier.padding(top = 16.dp)) {
                                            val liquidBlur by preferences.liquidButtonBlur.collectAsState()
                                            SliderPreference(
                                                value = liquidBlur,
                                                onValueChange = { preferences.liquidButtonBlur.set(it) },
                                                title = { Text("Blur Intensity") },
                                                valueRange = 0f..64f,
                                                sliderValue = liquidBlur,
                                                onSliderValueChange = { preferences.liquidButtonBlur.set(it) },
                                                summary = { Text("${liquidBlur.roundToInt()} dp") }
                                            )

                                            val liquidLensRadius by preferences.liquidButtonLensRadius.collectAsState()
                                            SliderPreference(
                                                value = liquidLensRadius,
                                                onValueChange = { preferences.liquidButtonLensRadius.set(it) },
                                                title = { Text("Lens Radius") },
                                                valueRange = 0f..100f,
                                                sliderValue = liquidLensRadius,
                                                onSliderValueChange = { preferences.liquidButtonLensRadius.set(it) },
                                                summary = { Text("${liquidLensRadius.roundToInt()} dp") }
                                            )

                                            val liquidLensDepth by preferences.liquidButtonLensDepth.collectAsState()
                                            SliderPreference(
                                                value = liquidLensDepth,
                                                onValueChange = { preferences.liquidButtonLensDepth.set(it) },
                                                title = { Text("Lens Depth") },
                                                valueRange = 0f..200f,
                                                sliderValue = liquidLensDepth,
                                                onSliderValueChange = { preferences.liquidButtonLensDepth.set(it) },
                                                summary = { Text("${liquidLensDepth.roundToInt()} dp") }
                                            )

                                            TextButton(
                                                onClick = {
                                                    preferences.liquidButtonBlur.set(26f)
                                                    preferences.liquidButtonLensRadius.set(42f)
                                                    preferences.liquidButtonLensDepth.set(72f)
                                                },
                                                modifier = Modifier.align(Alignment.End)
                                            ) {
                                                Text(stringResource(R.string.generic_reset))
                                            }
                                        }
                                    }
                                }
                                PreferenceDivider()
                            }

                            var showLiquidColorPicker by remember { mutableStateOf(false) }
                            
                            PreferenceCard(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { showLiquidColorPicker = !showLiquidColorPicker }
                                    .padding(horizontal = 20.dp, vertical = 12.dp)
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = stringResource(id = R.string.pref_anim_liquid_toggle_color_title),
                                            style = MaterialTheme.typography.titleMedium,
                                            fontWeight = FontWeight.SemiBold,
                                        )
                                        Text(
                                            text = stringResource(id = R.string.pref_anim_liquid_toggle_color_summary),
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.outline,
                                        )
                                    }
                                    Box(
                                        modifier = Modifier
                                            .size(32.dp)
                                            .background(
                                                color = Color(liquidToggleColor),
                                                shape = CircleShape
                                            )
                                    )
                                }
                                
                                AnimatedVisibility(visible = showLiquidColorPicker) {
                                    Column(modifier = Modifier.padding(top = 16.dp)) {
                                        // Preview of LiquidToggle
                                        val toggleBackdrop = rememberLayerBackdrop()
                                        var previewSelected by remember { mutableStateOf(true) }
                                        
                                        Box(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .padding(bottom = 16.dp),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            LiquidToggle(
                                                selected = { previewSelected },
                                                onSelect = { previewSelected = it },
                                                backdrop = toggleBackdrop,
                                                accentColor = Color(liquidToggleColor)
                                            )
                                        }

                                        LiquidColorPicker(
                                            color = liquidToggleColor,
                                            onColorChange = { preferences.liquidToggleColor.set(it) }
                                        )
                                        
                                        TextButton(
                                            onClick = { preferences.liquidToggleColor.set(0xFF000080.toInt()) },
                                            modifier = Modifier.align(Alignment.End)
                                        ) {
                                            Text(stringResource(R.string.generic_reset))
                                        }
                                    }
                                }
                            }

                            PreferenceDivider()

                            var showLiquidSeekbarColorPicker by remember { mutableStateOf(false) }
                            val liquidSeekbarColor by preferences.liquidSeekbarColor.collectAsState()

                            PreferenceCard(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { showLiquidSeekbarColorPicker = !showLiquidSeekbarColorPicker }
                                    .padding(horizontal = 20.dp, vertical = 12.dp)
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = stringResource(id = R.string.pref_anim_liquid_seekbar_color_title),
                                            style = MaterialTheme.typography.titleMedium,
                                            fontWeight = FontWeight.SemiBold,
                                        )
                                        Text(
                                            text = stringResource(id = R.string.pref_anim_liquid_seekbar_color_summary),
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.outline,
                                        )
                                    }
                                    Box(
                                        modifier = Modifier
                                            .size(32.dp)
                                            .background(
                                                color = Color(liquidSeekbarColor),
                                                shape = CircleShape
                                            )
                                    )
                                }

                                AnimatedVisibility(visible = showLiquidSeekbarColorPicker) {
                                    Column(modifier = Modifier.padding(top = 16.dp)) {
                                        LiquidColorPicker(
                                            color = liquidSeekbarColor,
                                            onColorChange = { preferences.liquidSeekbarColor.set(it) }
                                        )

                                        TextButton(
                                            onClick = { preferences.liquidSeekbarColor.set(0xFFFF4500.toInt()) },
                                            modifier = Modifier.align(Alignment.End)
                                        ) {
                                            Text(stringResource(R.string.generic_reset))
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun LiquidColorPicker(
    color: Int,
    onColorChange: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier) {
        val r = (color shr 16) and 0xFF
        val g = (color shr 8) and 0xFF
        val b = color and 0xFF
        val a = (color shr 24) and 0xFF

        fun updateColor(nr: Int = r, ng: Int = g, nb: Int = b, na: Int = a) {
            onColorChange((na shl 24) or (nr shl 16) or (ng shl 8) or nb)
        }

        SliderPreference(
            value = r.toFloat(),
            onValueChange = { updateColor(nr = it.roundToInt()) },
            title = { Text("Red") },
            valueRange = 0f..255f,
            sliderValue = r.toFloat(),
            onSliderValueChange = { updateColor(nr = it.roundToInt()) },
            summary = { Text(r.toString()) }
        )
        SliderPreference(
            value = g.toFloat(),
            onValueChange = { updateColor(ng = it.roundToInt()) },
            title = { Text("Green") },
            valueRange = 0f..255f,
            sliderValue = g.toFloat(),
            onSliderValueChange = { updateColor(ng = it.roundToInt()) },
            summary = { Text(g.toString()) }
        )
        SliderPreference(
            value = b.toFloat(),
            onValueChange = { updateColor(nb = it.roundToInt()) },
            title = { Text("Blue") },
            valueRange = 0f..255f,
            sliderValue = b.toFloat(),
            onSliderValueChange = { updateColor(nb = it.roundToInt()) },
            summary = { Text(b.toString()) }
        )
        SliderPreference(
            value = a.toFloat(),
            onValueChange = { updateColor(na = it.roundToInt()) },
            title = { Text("Alpha") },
            valueRange = 0f..255f,
            sliderValue = a.toFloat(),
            onSliderValueChange = { updateColor(na = it.roundToInt()) },
            summary = { Text(a.toString()) }
        )
    }
}
