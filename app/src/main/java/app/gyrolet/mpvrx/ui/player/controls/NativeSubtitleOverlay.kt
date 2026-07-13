package app.gyrolet.mpvrx.ui.player.controls

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.gyrolet.mpvrx.preferences.SubtitleJustification
import app.gyrolet.mpvrx.preferences.SubtitleRenderMode
import app.gyrolet.mpvrx.preferences.SubtitlesPreferences
import app.gyrolet.mpvrx.ui.player.PlayerViewModel
import app.gyrolet.mpvrx.ui.player.controls.components.panels.SubtitlesBorderStyle
import org.koin.compose.koinInject

@Composable
fun NativeSubtitleOverlay(
    viewModel: PlayerViewModel,
    modifier: Modifier = Modifier
) {
    val subtitlesPreferences = koinInject<SubtitlesPreferences>()
    val renderMode by viewModel.subtitleRenderMode.collectAsState()
    val subtitleText by viewModel.nativeSubtitleText.collectAsState()

    // Only render if mode is NATIVE and there is actual text to show
    if (renderMode != SubtitleRenderMode.NATIVE || subtitleText.isBlank()) {
        return
    }

    // Read preferences reactively
    val fontSizePrefs = subtitlesPreferences.fontSize.get()
    val bold = subtitlesPreferences.bold.get()
    val italic = subtitlesPreferences.italic.get()
    val textColor = Color(subtitlesPreferences.textColor.get())
    val borderStyleEnum = subtitlesPreferences.borderStyle.get()
    val borderColor = Color(subtitlesPreferences.borderColor.get())
    val shadowColor = Color(subtitlesPreferences.shadowColor.get())
    val borderSize = subtitlesPreferences.borderSize.get()
    val shadowOffset = subtitlesPreferences.shadowOffset.get()
    val justification = subtitlesPreferences.justification.get()
    val subPos = subtitlesPreferences.subPos.get()

    // Map Preferences to Compose Properties
    val fontWeight = if (bold) FontWeight.Bold else FontWeight.Normal
    val fontStyle = if (italic) FontStyle.Italic else FontStyle.Normal
    
    val textAlign = when (justification) {
        SubtitleJustification.Left -> TextAlign.Left
        SubtitleJustification.Right -> TextAlign.Right
        SubtitleJustification.Center -> TextAlign.Center
        SubtitleJustification.Auto -> TextAlign.Center
    }

    // Convert MPV sub-pos (0-100) to bottom padding (100 is bottom, 0 is top)
    val bottomPaddingPercent = (100 - subPos) / 100f
    
    // Scale font size down slightly since Compose sp tends to render larger than MPV's internal scale
    val scaledFontSize = (fontSizePrefs * 0.6f).sp

    Box(
        modifier = modifier
            .fillMaxSize()
            .padding(bottom = (bottomPaddingPercent * 100).dp, start = 16.dp, end = 16.dp),
        contentAlignment = Alignment.BottomCenter
    ) {
        // Base Style
        val baseStyle = TextStyle(
            fontSize = scaledFontSize,
            fontWeight = fontWeight,
            fontStyle = fontStyle,
            textAlign = textAlign
        )

        // Render based on border style
        when (borderStyleEnum) {
            SubtitlesBorderStyle.OutlineAndShadow -> {
                // Outline pass
                if (borderSize > 0) {
                    Text(
                        text = subtitleText,
                        style = baseStyle.copy(
                            color = borderColor,
                            drawStyle = Stroke(
                                miter = 10f,
                                width = borderSize.toFloat() * 2f,
                                join = StrokeJoin.Round
                            )
                        )
                    )
                }
                // Text pass with shadow
                Text(
                    text = subtitleText,
                    style = baseStyle.copy(
                        color = textColor,
                        shadow = if (shadowOffset > 0) Shadow(
                            color = shadowColor,
                            blurRadius = shadowOffset.toFloat() * 1.5f
                        ) else null
                    )
                )
            }
            SubtitlesBorderStyle.OpaqueBox,
            SubtitlesBorderStyle.BackgroundBox -> {
                // Background Box handled by TextStyle background
                Text(
                    text = subtitleText,
                    style = baseStyle.copy(
                        color = textColor,
                        background = Color(subtitlesPreferences.backgroundColor.get())
                    )
                )
            }
        }
    }
}
