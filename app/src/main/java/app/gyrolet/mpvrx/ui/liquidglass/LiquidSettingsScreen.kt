package app.gyrolet.mpvrx.ui.liquidglass

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AssistChip
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import app.gyrolet.mpvrx.R
import app.gyrolet.mpvrx.preferences.AppearancePreferences
import app.gyrolet.mpvrx.preferences.preference.collectAsState
import app.gyrolet.mpvrx.presentation.Screen
import app.gyrolet.mpvrx.ui.icons.AppIcon
import app.gyrolet.mpvrx.ui.icons.Icon
import app.gyrolet.mpvrx.ui.icons.Icons
import app.gyrolet.mpvrx.ui.preferences.PreferenceCard
import app.gyrolet.mpvrx.ui.preferences.PreferenceDivider
import app.gyrolet.mpvrx.ui.preferences.PreferenceSectionHeader
import app.gyrolet.mpvrx.ui.theme.LocalEmphasizedTypography
import app.gyrolet.mpvrx.ui.utils.LocalBackStack
import app.gyrolet.mpvrx.ui.utils.popSafely
import com.kyant.backdrop.backdrops.rememberLayerBackdrop
import kotlinx.serialization.Serializable
import me.zhanghai.compose.preference.ProvidePreferenceLocals
import org.koin.compose.koinInject
import kotlin.math.roundToInt

@Serializable
object LiquidSettingsScreen : Screen {
  @OptIn(ExperimentalMaterial3Api::class)
  @Composable
  override fun Content() {
    val preferences = koinInject<AppearancePreferences>()
    val backstack = LocalBackStack.current
    val emphasizedTypography = LocalEmphasizedTypography.current
    val screenBackdrop = rememberLayerBackdrop()
    val enableLiquidGlass by preferences.enableLiquidGlass.collectAsState()
    val liquidToggleColor by preferences.liquidToggleColor.collectAsState()
    val liquidSeekbarColor by preferences.liquidSeekbarColor.collectAsState()
    val liquidButtonBlur by preferences.liquidButtonBlur.collectAsState()
    val liquidButtonLensRadius by preferences.liquidButtonLensRadius.collectAsState()
    val liquidButtonLensDepth by preferences.liquidButtonLensDepth.collectAsState()
    val liquidButtonOpacity by preferences.liquidButtonOpacity.collectAsState()
    val liquidButtonTint by preferences.liquidButtonTint.collectAsState()
    val liquidDialogBlur by preferences.liquidDialogBlur.collectAsState()
    val liquidDialogSaturation by preferences.liquidDialogSaturation.collectAsState()
    val liquidDialogBrightness by preferences.liquidDialogBrightness.collectAsState()
    val liquidDialogLensRadius by preferences.liquidDialogLensRadius.collectAsState()
    val liquidDialogLensDepth by preferences.liquidDialogLensDepth.collectAsState()
    val liquidDialogContainerAlpha by preferences.liquidDialogContainerAlpha.collectAsState()
    val liquidDialogDarkText by preferences.liquidDialogDarkText.collectAsState()

    Scaffold(
      topBar = {
        TopAppBar(
          title = {
            Text(
              text = stringResource(R.string.pref_anim_liquid_glass_title),
              style = emphasizedTypography.headlineSmall,
            )
          },
          navigationIcon = {
            IconButton(onClick = { backstack.popSafely() }) {
              Icon(
                Icons.RoundedFilled.ArrowBack,
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
          modifier = Modifier
            .fillMaxSize()
            .padding(padding),
          contentPadding = PaddingValues(bottom = 28.dp),
        ) {
          item {
            PreferenceSectionHeader(title = "Liquid Glass")
          }

          item {
            PreferenceCard {
              AdaptiveSwitchPreference(
                value = enableLiquidGlass,
                onValueChange = { enabled ->
                  if (enabled) {
                    applyLiquidDefaults(preferences)
                  }
                  preferences.liquidGlassEnabled.set(enabled)
                },
                title = { Text(text = stringResource(id = R.string.pref_anim_liquid_glass_title)) },
                summary = {
                  Text(
                    text = stringResource(id = R.string.pref_anim_liquid_glass_summary),
                    color = MaterialTheme.colorScheme.outline,
                  )
                },
              )
            }
          }

          if (enableLiquidGlass) {
            item {
              PreferenceCard {
                LiquidPreviewPanel(
                  toggleColor = Color(liquidToggleColor),
                  seekbarColor = Color(liquidSeekbarColor),
                  buttonTint = Color(liquidButtonTint),
                  dialogAlpha = liquidDialogContainerAlpha,
                  backdrop = screenBackdrop,
                )
              }
            }

            item {
              PreferenceSectionHeader(title = "Accents")
            }

            item {
              PreferenceCard {
                ColorPreferenceBlock(
                  title = "Toggle accent",
                  summary = "Used by liquid switches and quick actions",
                  selectedColor = liquidToggleColor,
                  presets = LiquidTogglePresets,
                  defaultCustomColor = 0xFF536DFE.toInt(),
                  onColorChange = preferences.liquidToggleColor::set,
                )

                PreferenceDivider()

                ColorPreferenceBlock(
                  title = "Seekbar accent",
                  summary = "Used by the liquid player timeline",
                  selectedColor = liquidSeekbarColor,
                  presets = LiquidSeekbarPresets,
                  defaultCustomColor = 0xFFFF4500.toInt(),
                  onColorChange = preferences.liquidSeekbarColor::set,
                )
              }
            }

            item {
              PreferenceSectionHeader(title = "Buttons")
            }

            item {
              PreferenceCard {
                SliderPreferenceRow(
                  title = "Blur",
                  summary = "Backdrop diffusion for icon and pill buttons",
                  value = liquidButtonBlur,
                  valueRange = 0f..64f,
                  icon = Icons.RoundedFilled.BlurOn,
                  suffix = "dp",
                  onValueChange = preferences.liquidButtonBlur::set,
                )
                PreferenceDivider()
                SliderPreferenceRow(
                  title = "Lens radius",
                  summary = "Curvature size around button edges",
                  value = liquidButtonLensRadius,
                  valueRange = 0f..100f,
                  icon = Icons.RoundedFilled.AspectRatio,
                  suffix = "dp",
                  onValueChange = preferences.liquidButtonLensRadius::set,
                )
                PreferenceDivider()
                SliderPreferenceRow(
                  title = "Lens depth",
                  summary = "Refraction strength through the control surface",
                  value = liquidButtonLensDepth,
                  valueRange = 0f..200f,
                  icon = Icons.RoundedFilled.BlurOff,
                  suffix = "dp",
                  onValueChange = preferences.liquidButtonLensDepth::set,
                )
                PreferenceDivider()
                SliderPreferenceRow(
                  title = "Tint opacity",
                  summary = "How much white glass tint is layered on buttons",
                  value = liquidButtonOpacity,
                  valueRange = 0f..1f,
                  icon = Icons.RoundedFilled.Opacity,
                  onValueChange = preferences.liquidButtonOpacity::set,
                )
                PreferenceDivider()
                ColorPreferenceBlock(
                  title = "Button tint",
                  summary = "Base tint used over liquid buttons",
                  selectedColor = liquidButtonTint,
                  presets = LiquidButtonTintPresets,
                  defaultCustomColor = 0x26FFFFFF,
                  onColorChange = preferences.liquidButtonTint::set,
                )
                ResetRow(onClick = { resetButtonGlass(preferences) })
              }
            }

            item {
              PreferenceSectionHeader(title = "Dialogs And Sheets")
            }

            item {
              PreferenceCard {
                AdaptiveSwitchPreference(
                  value = liquidDialogDarkText,
                  onValueChange = preferences.liquidDialogDarkText::set,
                  title = { Text("Dark text on glass") },
                  summary = {
                    Text(
                      text = "Use black text when the glass layer sits over bright video frames",
                      color = MaterialTheme.colorScheme.outline,
                    )
                  },
                )
                PreferenceDivider()
                SliderPreferenceRow(
                  title = "Blur",
                  summary = "Backdrop diffusion behind dialogs and sheets",
                  value = liquidDialogBlur,
                  valueRange = 0f..64f,
                  icon = Icons.RoundedFilled.BlurOn,
                  suffix = "dp",
                  onValueChange = preferences.liquidDialogBlur::set,
                )
                PreferenceDivider()
                SliderPreferenceRow(
                  title = "Saturation",
                  summary = "Color intensity preserved through the glass",
                  value = liquidDialogSaturation,
                  valueRange = 0f..3f,
                  icon = Icons.RoundedFilled.AutoAwesome,
                  onValueChange = preferences.liquidDialogSaturation::set,
                )
                PreferenceDivider()
                SliderPreferenceRow(
                  title = "Brightness",
                  summary = "Exposure offset applied to the backdrop",
                  value = liquidDialogBrightness,
                  valueRange = -1f..1f,
                  icon = Icons.RoundedFilled.BrightnessMedium,
                  onValueChange = preferences.liquidDialogBrightness::set,
                )
                PreferenceDivider()
                SliderPreferenceRow(
                  title = "Lens radius",
                  summary = "Curvature size for dialog panels",
                  value = liquidDialogLensRadius,
                  valueRange = 0f..100f,
                  icon = Icons.RoundedFilled.AspectRatio,
                  suffix = "dp",
                  onValueChange = preferences.liquidDialogLensRadius::set,
                )
                PreferenceDivider()
                SliderPreferenceRow(
                  title = "Lens depth",
                  summary = "Refraction strength for dialogs and sheets",
                  value = liquidDialogLensDepth,
                  valueRange = 0f..200f,
                  icon = Icons.RoundedFilled.BlurOff,
                  suffix = "dp",
                  onValueChange = preferences.liquidDialogLensDepth::set,
                )
                PreferenceDivider()
                SliderPreferenceRow(
                  title = "Container alpha",
                  summary = "Opacity of the dialog backing plate",
                  value = liquidDialogContainerAlpha,
                  valueRange = 0f..1f,
                  icon = Icons.RoundedFilled.Opacity,
                  onValueChange = preferences.liquidDialogContainerAlpha::set,
                )
                ResetRow(onClick = { resetDialogGlass(preferences) })
              }
            }
          }
        }
      }
    }
  }
}

private data class LiquidColorPreset(
  val name: String,
  val color: Int,
)

private val LiquidTogglePresets = listOf(
  LiquidColorPreset("Indigo", 0xFF536DFE.toInt()),
  LiquidColorPreset("Teal", 0xFF00BFA5.toInt()),
  LiquidColorPreset("Amber", 0xFFFFAB00.toInt()),
  LiquidColorPreset("Rose", 0xFFFF4081.toInt()),
  LiquidColorPreset("Emerald", 0xFF00E676.toInt()),
  LiquidColorPreset("Navy", 0xFF000080.toInt()),
)

private val LiquidSeekbarPresets = listOf(
  LiquidColorPreset("Orange", 0xFFFF4500.toInt()),
  LiquidColorPreset("Ocean", 0xFF00B0FF.toInt()),
  LiquidColorPreset("Teal", 0xFF00BFA5.toInt()),
  LiquidColorPreset("Rose", 0xFFFF4081.toInt()),
  LiquidColorPreset("Emerald", 0xFF00E676.toInt()),
  LiquidColorPreset("Violet", 0xFF7C4DFF.toInt()),
)

private val LiquidButtonTintPresets = listOf(
  LiquidColorPreset("White", 0x26FFFFFF),
  LiquidColorPreset("Frost", 0x33D9F2FF),
  LiquidColorPreset("Smoke", 0x33202A33),
  LiquidColorPreset("Indigo", 0x33536DFE),
  LiquidColorPreset("Amber", 0x33FFAB00),
)

@Composable
private fun LiquidPreviewPanel(
  toggleColor: Color,
  seekbarColor: Color,
  buttonTint: Color,
  dialogAlpha: Float,
  backdrop: com.kyant.backdrop.Backdrop,
) {
  var previewSelected by remember { mutableStateOf(true) }

  Column(
    modifier = Modifier.padding(horizontal = 20.dp, vertical = 14.dp),
    verticalArrangement = Arrangement.spacedBy(18.dp),
  ) {
    Row(
      modifier = Modifier.fillMaxWidth(),
      horizontalArrangement = Arrangement.SpaceBetween,
      verticalAlignment = Alignment.CenterVertically,
    ) {
      Column(modifier = Modifier.weight(1f)) {
        Text(
          text = "Current feel",
          style = MaterialTheme.typography.titleMedium,
          fontWeight = FontWeight.SemiBold,
        )
        Text(
          text = "Buttons, toggles, seekbars, dialogs",
          style = MaterialTheme.typography.bodySmall,
          color = MaterialTheme.colorScheme.outline,
        )
      }
      AssistChip(
        onClick = {},
        enabled = false,
        label = { Text("Active") },
        leadingIcon = {
          ColorDot(color = toggleColor, size = 10.dp)
        },
      )
    }

    Surface(
      modifier = Modifier.fillMaxWidth(),
      shape = RoundedCornerShape(22.dp),
      color = MaterialTheme.colorScheme.surfaceContainerHigh,
    ) {
      Column(
        modifier = Modifier.padding(18.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
      ) {
        Row(
          modifier = Modifier.fillMaxWidth(),
          horizontalArrangement = Arrangement.SpaceBetween,
          verticalAlignment = Alignment.CenterVertically,
        ) {
          LiquidToggle(
            selected = { previewSelected },
            onSelect = { previewSelected = it },
            backdrop = backdrop,
            accentColor = toggleColor,
          )

          Surface(
            color = buttonTint.copy(alpha = buttonTint.alpha.coerceIn(0.12f, 0.5f)),
            shape = RoundedCornerShape(999.dp),
            tonalElevation = 0.dp,
          ) {
            Row(
              modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
              verticalAlignment = Alignment.CenterVertically,
            ) {
              Icon(
                imageVector = Icons.RoundedFilled.AutoAwesome,
                contentDescription = null,
                modifier = Modifier.size(16.dp),
                tint = MaterialTheme.colorScheme.onSurface,
              )
              Spacer(Modifier.width(8.dp))
              Text(
                text = "Glass",
                style = MaterialTheme.typography.labelLarge,
              )
            }
          }
        }

        Box(
          modifier = Modifier
            .fillMaxWidth()
            .height(8.dp)
            .background(
              color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.14f),
              shape = RoundedCornerShape(999.dp),
            ),
          contentAlignment = Alignment.CenterStart,
        ) {
          Box(
            modifier = Modifier
              .fillMaxWidth(0.62f)
              .height(8.dp)
              .background(
                brush = Brush.horizontalGradient(
                  listOf(seekbarColor, seekbarColor.copy(alpha = 0.52f)),
                ),
                shape = RoundedCornerShape(999.dp),
              ),
          )
        }

        Surface(
          modifier = Modifier.fillMaxWidth(),
          shape = RoundedCornerShape(18.dp),
          color = MaterialTheme.colorScheme.primary.copy(alpha = dialogAlpha.coerceIn(0.12f, 0.65f)),
        ) {
          Column(modifier = Modifier.padding(14.dp)) {
            Text(
              text = "Dialog glass",
              style = MaterialTheme.typography.titleSmall,
              color = MaterialTheme.colorScheme.onPrimary,
            )
            Text(
              text = "Alpha ${formatLiquidValue(dialogAlpha)}",
              style = MaterialTheme.typography.bodySmall,
              color = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.78f),
            )
          }
        }
      }
    }
  }
}

@Composable
private fun ColorPreferenceBlock(
  title: String,
  summary: String,
  selectedColor: Int,
  presets: List<LiquidColorPreset>,
  defaultCustomColor: Int,
  onColorChange: (Int) -> Unit,
) {
  val isPreset = presets.any { it.color == selectedColor }
  val showCustom = !isPreset

  Column(
    modifier = Modifier.padding(horizontal = 20.dp, vertical = 14.dp),
    verticalArrangement = Arrangement.spacedBy(14.dp),
  ) {
    Row(
      modifier = Modifier.fillMaxWidth(),
      horizontalArrangement = Arrangement.SpaceBetween,
      verticalAlignment = Alignment.CenterVertically,
    ) {
      Column(modifier = Modifier.weight(1f)) {
        Text(
          text = title,
          style = MaterialTheme.typography.titleMedium,
          fontWeight = FontWeight.SemiBold,
        )
        Text(
          text = summary,
          style = MaterialTheme.typography.bodySmall,
          color = MaterialTheme.colorScheme.outline,
        )
      }
      ColorDot(color = Color(selectedColor), size = 34.dp)
    }

    Row(
      modifier = Modifier
        .fillMaxWidth()
        .horizontalScroll(rememberScrollState()),
      horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
      presets.forEach { preset ->
        ColorChip(
          label = preset.name,
          color = Color(preset.color),
          selected = selectedColor == preset.color,
          onClick = { onColorChange(preset.color) },
        )
      }
      ColorChip(
        label = "Custom",
        color = Color(selectedColor),
        selected = showCustom,
        onClick = {
          if (!showCustom) {
            onColorChange(defaultCustomColor)
          }
        },
      )
    }

    AnimatedVisibility(visible = showCustom) {
      CompactColorSliders(
        color = selectedColor,
        onColorChange = onColorChange,
      )
    }
  }
}

@Composable
private fun ColorChip(
  label: String,
  color: Color,
  selected: Boolean,
  onClick: () -> Unit,
) {
  FilterChip(
    selected = selected,
    onClick = onClick,
    label = { Text(label) },
    leadingIcon = { ColorDot(color = color, size = 12.dp) },
  )
}

@Composable
private fun CompactColorSliders(
  color: Int,
  onColorChange: (Int) -> Unit,
) {
  val r = (color shr 16) and 0xFF
  val g = (color shr 8) and 0xFF
  val b = color and 0xFF
  val a = (color ushr 24) and 0xFF

  fun updateColor(nr: Int = r, ng: Int = g, nb: Int = b, na: Int = a) {
    onColorChange((na shl 24) or (nr shl 16) or (ng shl 8) or nb)
  }

  Column(
    modifier = Modifier
      .fillMaxWidth()
      .padding(top = 2.dp),
    verticalArrangement = Arrangement.spacedBy(6.dp),
  ) {
    ChannelSlider("Red", r, Color(0xFFFF453A)) { updateColor(nr = it) }
    ChannelSlider("Green", g, Color(0xFF30D158)) { updateColor(ng = it) }
    ChannelSlider("Blue", b, Color(0xFF0A84FF)) { updateColor(nb = it) }
    ChannelSlider("Alpha", a, MaterialTheme.colorScheme.primary) { updateColor(na = it) }
  }
}

@Composable
private fun ChannelSlider(
  label: String,
  value: Int,
  color: Color,
  onValueChange: (Int) -> Unit,
) {
  Row(
    modifier = Modifier.fillMaxWidth(),
    verticalAlignment = Alignment.CenterVertically,
  ) {
    Text(
      text = label,
      modifier = Modifier.width(56.dp),
      style = MaterialTheme.typography.labelMedium,
      color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
    Slider(
      value = value.toFloat(),
      onValueChange = { onValueChange(it.roundToInt().coerceIn(0, 255)) },
      valueRange = 0f..255f,
      modifier = Modifier.weight(1f),
      colors = SliderDefaults.colors(
        activeTrackColor = color,
        thumbColor = color,
      ),
    )
    Text(
      text = value.toString(),
      modifier = Modifier.width(36.dp),
      style = MaterialTheme.typography.labelSmall,
      color = MaterialTheme.colorScheme.outline,
    )
  }
}

@Composable
private fun SliderPreferenceRow(
  title: String,
  summary: String,
  value: Float,
  valueRange: ClosedFloatingPointRange<Float>,
  icon: AppIcon,
  onValueChange: (Float) -> Unit,
  suffix: String = "",
) {
  Column(
    modifier = Modifier.padding(horizontal = 20.dp, vertical = 14.dp),
    verticalArrangement = Arrangement.spacedBy(10.dp),
  ) {
    Row(
      modifier = Modifier.fillMaxWidth(),
      verticalAlignment = Alignment.CenterVertically,
    ) {
      Surface(
        modifier = Modifier.size(40.dp),
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.primary.copy(alpha = 0.12f),
      ) {
        Box(contentAlignment = Alignment.Center) {
          Icon(
            imageVector = icon,
            contentDescription = null,
            modifier = Modifier.size(21.dp),
            tint = MaterialTheme.colorScheme.primary,
          )
        }
      }

      Spacer(Modifier.width(14.dp))

      Column(modifier = Modifier.weight(1f)) {
        Row(
          modifier = Modifier.fillMaxWidth(),
          horizontalArrangement = Arrangement.SpaceBetween,
          verticalAlignment = Alignment.CenterVertically,
        ) {
          Text(
            text = title,
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.SemiBold,
          )
          Text(
            text = formatLiquidValue(value, suffix),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.primary,
            fontWeight = FontWeight.SemiBold,
          )
        }
        Text(
          text = summary,
          style = MaterialTheme.typography.bodySmall,
          color = MaterialTheme.colorScheme.outline,
        )
      }
    }

    Slider(
      value = value.coerceIn(valueRange.start, valueRange.endInclusive),
      onValueChange = onValueChange,
      valueRange = valueRange,
      modifier = Modifier.fillMaxWidth(),
    )
  }
}

@Composable
private fun ResetRow(
  onClick: () -> Unit,
) {
  Row(
    modifier = Modifier
      .fillMaxWidth()
      .padding(horizontal = 12.dp, vertical = 4.dp),
    horizontalArrangement = Arrangement.End,
  ) {
    TextButton(
      onClick = onClick,
      colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error),
    ) {
      Icon(
        imageVector = Icons.RoundedFilled.Restore,
        contentDescription = null,
        modifier = Modifier.size(16.dp),
      )
      Spacer(Modifier.width(6.dp))
      Text("Reset")
    }
  }
}

@Composable
private fun ColorDot(
  color: Color,
  size: androidx.compose.ui.unit.Dp,
) {
  Box(
    modifier = Modifier
      .size(size)
      .background(color = color, shape = CircleShape)
      .border(1.dp, MaterialTheme.colorScheme.outlineVariant, CircleShape),
  )
}

private fun formatLiquidValue(value: Float, suffix: String = ""): String {
  val rounded = if (valueRangeLooksFraction(value)) {
    "%.${2}f".format(value)
  } else {
    value.roundToInt().toString()
  }
  return if (suffix.isBlank()) rounded else "$rounded $suffix"
}

private fun valueRangeLooksFraction(value: Float): Boolean =
  value > -2f && value < 4f && value != value.roundToInt().toFloat()

internal fun applyLiquidDefaults(preferences: AppearancePreferences) {
  if (
    preferences.liquidButtonBlur.get() <= 0f &&
    preferences.liquidButtonLensRadius.get() <= 0f &&
    preferences.liquidButtonLensDepth.get() <= 0f
  ) {
    resetButtonGlass(preferences)
    resetDialogGlass(preferences)
  }
}

private fun resetButtonGlass(preferences: AppearancePreferences) {
  preferences.liquidButtonBlur.set(26f)
  preferences.liquidButtonLensRadius.set(42f)
  preferences.liquidButtonLensDepth.set(72f)
  preferences.liquidButtonOpacity.set(0.15f)
  preferences.liquidButtonTint.set(0x26FFFFFF)
}

private fun resetDialogGlass(preferences: AppearancePreferences) {
  preferences.liquidDialogBlur.set(32f)
  preferences.liquidDialogSaturation.set(1.3f)
  preferences.liquidDialogBrightness.set(0.08f)
  preferences.liquidDialogLensRadius.set(55f)
  preferences.liquidDialogLensDepth.set(85f)
  preferences.liquidDialogContainerAlpha.set(0.35f)
}
