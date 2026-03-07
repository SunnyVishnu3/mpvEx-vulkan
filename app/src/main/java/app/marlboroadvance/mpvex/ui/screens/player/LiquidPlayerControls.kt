package app.marlboroadvance.mpvex.ui.screens.player

import android.os.Build
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.kyant.backdrop.backdrops.rememberLayerBackdrop
import com.kyant.backdrop.drawBackdrop
import com.kyant.backdrop.Backdrop
import app.marlboroadvance.mpvex.preferences.LiquidUIPreferences
import app.marlboroadvance.mpvex.ui.theme.LiquidUIEffects

@Composable
fun PlayerScreenWithLiquidUI(
    liquidUIPreferences: LiquidUIPreferences
) {
    // Collect the user's preference to toggle the UI on/off
    val liquidUIEnabled = liquidUIPreferences.liquidUIEnabledFlow.collectAsState(initial = false).value
    val backgroundColor = Color.Black
    
    // 1. Create the Backdrop State (CRITICAL)
    val backdrop = rememberLayerBackdrop {
        drawRect(backgroundColor)
        drawContent()
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(backgroundColor)
    ) {
        // Video content (Placeholder for the actual mpvEx video surface)
        Box(
            modifier = Modifier
                .fillMaxSize()
                .align(Alignment.Center),
            contentAlignment = Alignment.Center
        ) {
            Text("Video Player Surface", color = Color.White)
        }

        // 2. Apply the Controls
        if (liquidUIEnabled) {
            LiquidPlayerControls(
                backdrop = backdrop,
                modifier = Modifier.align(Alignment.BottomCenter)
            )
        } else {
            StandardPlayerControls(
                modifier = Modifier.align(Alignment.BottomCenter)
            )
        }
    }
}

@Composable
fun LiquidPlayerControls(
    backdrop: Backdrop?,
    modifier: Modifier = Modifier
) {
    if (backdrop == null) {
        StandardPlayerControls(modifier)
        return
    }

    Column(modifier = modifier.fillMaxWidth()) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(80.dp)
                .padding(16.dp)
                // 3. The Critical drawBackdrop Pattern
                .drawBackdrop(
                    backdrop = backdrop,
                    shape = { RoundedCornerShape(24.dp) },
                    effects = LiquidUIEffects.playerOverlayEffects(
                        enableBlur = true,
                        enableLens = Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU,
                        enableVibrancy = true
                    ),
                    onDrawSurface = { drawRect(LiquidUIEffects.glassSurfaceColor) }
                ),
            contentAlignment = Alignment.Center
        ) {
            // Add your playback icons (Play/Pause/Seek) here
            Text("Liquid Controls Active", color = Color.White)
        }
    }
}

@Composable
fun StandardPlayerControls(modifier: Modifier = Modifier) {
    // Fallback standard controls
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(80.dp)
            .padding(16.dp)
            .background(Color.DarkGray, RoundedCornerShape(24.dp)),
        contentAlignment = Alignment.Center
    ) {
        Text("Standard Controls", color = Color.White)
    }
}
