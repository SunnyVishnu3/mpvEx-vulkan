package app.marlboroadvance.mpvex.ui.components.liquid

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import app.marlboroadvance.mpvex.preferences.LiquidUIPreferences

@Composable
fun LiquidSwitchPreference(
    value: Boolean,
    onValueChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    icon: @Composable (() -> Unit)? = null,
    title: @Composable () -> Unit,
    summary: @Composable (() -> Unit)? = null
) {
    val context = LocalContext.current
    val preferences = remember { LiquidUIPreferences(context) }

    Row(
        modifier = modifier
            .fillMaxWidth()
            .clickable(enabled = enabled) { onValueChange(!value) }
            .padding(horizontal = 16.dp, vertical = 16.dp)
            .alpha(if (enabled) 1f else 0.5f),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (icon != null) {
            Box(modifier = Modifier.padding(end = 16.dp)) { icon() }
        }
        Column(modifier = Modifier.weight(1f).padding(end = 16.dp)) {
            title()
            if (summary != null) summary()
        }
        AdaptiveToggle(
            checked = value,
            onCheckedChange = onValueChange,
            preferences = preferences,
            enabled = enabled
        )
    }
}
