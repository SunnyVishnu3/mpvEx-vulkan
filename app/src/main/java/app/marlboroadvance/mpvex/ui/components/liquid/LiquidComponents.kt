package app.marlboroadvance.mpvex.ui.components.liquid

import androidx.compose.foundation.background
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.dp
import com.kyant.backdrop.Backdrop
import app.marlboroadvance.mpvex.ui.theme.LiquidEffectConfig
import app.marlboroadvance.mpvex.ui.theme.LiquidEffectType
import app.marlboroadvance.mpvex.ui.theme.applyLiquidEffects

@Composable
fun LiquidGlassContainer(
    modifier: Modifier = Modifier,
    shape: Shape = RoundedCornerShape(16.dp),
    backdrop: Backdrop?,
    effectConfig: LiquidEffectConfig = LiquidEffectConfig(LiquidEffectType.PLAYER_OVERLAY),
    content: @Composable () -> Unit
) {
    if (backdrop == null) {
        androidx.compose.material3.Surface(
            modifier = modifier.background(Color.White.copy(alpha = 0.1f), shape),
            shape = shape,
            color = Color.White.copy(alpha = 0.1f)
        ) {
            content()
        }
    } else {
        androidx.compose.material3.Surface(
            modifier = modifier.applyLiquidEffects(backdrop, shape, effectConfig),
            shape = shape,
            color = Color.Transparent
        ) {
            content()
        }
    }
}

@Composable
fun LiquidGlassButton(
    modifier: Modifier = Modifier,
    backdrop: Backdrop?,
    effectConfig: LiquidEffectConfig = LiquidEffectConfig(LiquidEffectType.BUTTON),
    isLensButton: Boolean = false,
    onClick: () -> Unit,
    content: @Composable () -> Unit
) {
    androidx.compose.material3.Button(
        onClick = onClick,
        modifier = if (backdrop != null) {
            modifier.applyLiquidEffects(
                backdrop = backdrop,
                shape = if (isLensButton) CircleShape else RoundedCornerShape(12.dp),
                config = effectConfig
            )
        } else modifier,
        colors = androidx.compose.material3.ButtonDefaults.buttonColors(
            containerColor = Color.Transparent
        )
    ) {
        content()
    }
}

@Composable
fun LiquidGlassBottomSheet(
    modifier: Modifier = Modifier,
    backdrop: Backdrop?,
    onDismiss: () -> Unit,
    content: @Composable () -> Unit
) {
    if (backdrop == null) {
        androidx.compose.material3.BasicAlertDialog(
            onDismissRequest = onDismiss,
            modifier = modifier
        ) {
            content()
        }
    } else {
        androidx.compose.material3.BasicAlertDialog(
            onDismissRequest = onDismiss,
            modifier = modifier.applyLiquidEffects(
                backdrop = backdrop,
                shape = RoundedCornerShape(24.dp),
                config = LiquidEffectConfig(LiquidEffectType.DIALOG)
            )
        ) {
            content()
        }
    }
}

@Composable
fun LiquidGlassCard(
    modifier: Modifier = Modifier,
    backdrop: Backdrop?,
    onClick: () -> Unit = {},
    content: @Composable () -> Unit
) {
    androidx.compose.material3.Card(
        modifier = if (backdrop != null) {
            modifier.applyLiquidEffects(
                backdrop = backdrop,
                shape = RoundedCornerShape(12.dp),
                config = LiquidEffectConfig(LiquidEffectType.CARD)
            )
        } else modifier,
        shape = RoundedCornerShape(12.dp),
        onClick = onClick
    ) {
        content()
    }
}

