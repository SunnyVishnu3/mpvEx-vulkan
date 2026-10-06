package app.gyrolet.mpvrx.ui.liquidglass

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import app.gyrolet.mpvrx.preferences.AppearancePreferences
import app.gyrolet.mpvrx.preferences.preference.collectAsState
import com.kyant.backdrop.backdrops.rememberLayerBackdrop
import me.zhanghai.compose.preference.SwitchPreference
import org.koin.compose.koinInject

@Composable
fun AdaptiveSwitchPreference(
    value: Boolean,
    onValueChange: (Boolean) -> Unit,
    title: @Composable () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    icon: (@Composable () -> Unit)? = null,
    summary: (@Composable () -> Unit)? = null,
) {
    val preferences = koinInject<AppearancePreferences>()
    val enableLiquidGlass by preferences.enableLiquidGlass.collectAsState()
    val liquidToggleColor by preferences.liquidToggleColor.collectAsState()

    if (enableLiquidGlass) {
        LiquidAdaptiveSwitchPreference(
            value = value,
            onValueChange = onValueChange,
            title = title,
            modifier = modifier,
            enabled = enabled,
            icon = icon,
            summary = summary,
            accentColor = Color(liquidToggleColor)
        )
    } else {
        SwitchPreference(
            value = value,
            onValueChange = onValueChange,
            title = title,
            modifier = modifier,
            enabled = enabled,
            icon = icon,
            summary = summary
        )
    }
}

@Composable
fun LiquidAdaptiveSwitchPreference(
    value: Boolean,
    onValueChange: (Boolean) -> Unit,
    title: @Composable () -> Unit,
    modifier: Modifier = Modifier,
    icon: (@Composable () -> Unit)? = null,
    summary: (@Composable () -> Unit)? = null,
    accentColor: Color = MaterialTheme.colorScheme.primary,
    enabled: Boolean = true,
) {
    val backdrop = rememberLayerBackdrop()

    ListItem(
        headlineContent = title,
        supportingContent = summary,
        leadingContent = icon,
        trailingContent = {
            Box(contentAlignment = Alignment.Center) {
                LiquidToggle(
                    selected = { value },
                    onSelect = onValueChange,
                    backdrop = backdrop,
                    accentColor = accentColor
                )
            }
        },
        modifier = modifier
            .fillMaxWidth()
            .clickable(enabled = enabled) { onValueChange(!value) },
        colors = ListItemDefaults.colors(containerColor = Color.Transparent)
    )
}
