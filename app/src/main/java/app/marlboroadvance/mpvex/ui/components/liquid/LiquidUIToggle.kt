package app.marlboroadvance.mpvex.ui.components.liquid

import android.os.Build
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.kyant.backdrop.backdrops.rememberLayerBackdrop
import com.kyant.backdrop.drawBackdrop
import app.marlboroadvance.mpvex.ui.theme.LiquidUIEffects
import app.marlboroadvance.mpvex.preferences.LiquidUIPreferences

@Composable
fun LiquidToggle(
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    checkedColor: Color = Color(0xFF4CAF50),  
    uncheckedColor: Color = Color(0xFFBDBDBD),  
    thumbColor: Color = Color.White,
    applyLiquidEffect: Boolean = true
) {
    if (!applyLiquidEffect) {
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            modifier = modifier,
            enabled = enabled
        )
        return
    }

    // Smoothly animate the tint color of the glass
    val targetColor = if (checked) checkedColor.copy(alpha = 0.6f) else uncheckedColor.copy(alpha = 0.2f)
    val animatedColor by animateColorAsState(targetValue = targetColor, label = "toggle_color")
    
    val thumbPadding by animateDpAsState(
        targetValue = if (checked) 24.dp else 2.dp,
        label = "toggle_thumb_position"
    )

    // Capture the screen behind the toggle perfectly
    val backdrop = rememberLayerBackdrop {
        drawContent()
    }

    Box(
        modifier = modifier
            .size(width = 52.dp, height = 32.dp)
            .drawBackdrop(
                backdrop = backdrop,
                shape = { RoundedCornerShape(16.dp) },
                effects = LiquidUIEffects.glassCardEffects(enableBlur = true),
                onDrawSurface = { drawRect(animatedColor) } // Tint the glass, don't paint a solid block!
            )
            .clickable(
                enabled = enabled,
                onClick = { onCheckedChange(!checked) },
                indication = null,  
                interactionSource = remember { MutableInteractionSource() }
            ),
        contentAlignment = Alignment.CenterStart
    ) {
        Box(
            modifier = Modifier
                .size(28.dp)
                .padding(start = thumbPadding)
                .background(
                    color = thumbColor,
                    shape = RoundedCornerShape(14.dp)
                )
        )
    }
}

@Composable
fun LiquidSwitch(
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    label: String? = null,
    checkedColor: Color = Color(0xFF4CAF50), 
    applyLiquidEffect: Boolean = true
) {
    if (label == null) {
        LiquidToggle(
            checked = checked,
            onCheckedChange = onCheckedChange,
            modifier = modifier,
            enabled = enabled,
            checkedColor = checkedColor,
            applyLiquidEffect = applyLiquidEffect
        )
        return
    }

    Row(
        modifier = modifier.padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = label,
            modifier = Modifier.weight(1f)
        )

        Spacer(modifier = Modifier.size(8.dp))

        LiquidToggle(
            checked = checked,
            onCheckedChange = onCheckedChange,
            enabled = enabled,
            checkedColor = checkedColor,
            applyLiquidEffect = applyLiquidEffect
        )
    }
}

@Composable
fun AdaptiveToggle(
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    preferences: LiquidUIPreferences,
    enabled: Boolean = true,
    label: String? = null
) {
    val isLiquidUIEnabled = preferences.liquidUIEnabledFlow.collectAsState(false).value
    val toggleColorLong = preferences.liquidToggleColorFlow.collectAsState(0xFF4CAF50).value
    val customColor = Color(toggleColorLong)

    if (label != null) {
        LiquidSwitch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            modifier = modifier,
            enabled = enabled,
            label = label,
            checkedColor = customColor,
            applyLiquidEffect = isLiquidUIEnabled
        )
    } else {
        LiquidToggle(
            checked = checked,
            onCheckedChange = onCheckedChange,
            modifier = modifier,
            enabled = enabled,
            checkedColor = customColor,
            applyLiquidEffect = isLiquidUIEnabled
        )
    }
}

@Composable
fun SimpleToggle(
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    isLiquidUI: Boolean = true,
    label: String? = null,
    checkedColor: Color = Color(0xFF4CAF50)
) {
    if (label != null) {
        LiquidSwitch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            modifier = modifier,
            enabled = enabled,
            label = label,
            checkedColor = checkedColor,
            applyLiquidEffect = isLiquidUI
        )
    } else {
        LiquidToggle(
            checked = checked,
            onCheckedChange = onCheckedChange,
            modifier = modifier,
            enabled = enabled,
            checkedColor = checkedColor,
            applyLiquidEffect = isLiquidUI
        )
    }
}
