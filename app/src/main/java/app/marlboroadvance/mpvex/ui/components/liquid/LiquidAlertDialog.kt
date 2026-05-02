package app.marlboroadvance.mpvex.ui.components.liquid

import androidx.compose.foundation.background
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

@Composable
fun LiquidAlertDialog(
    onDismissRequest: () -> Unit,
    confirmButton: @Composable () -> Unit,
    modifier: Modifier = Modifier,
    dismissButton: @Composable (() -> Unit)? = null,
    icon: @Composable (() -> Unit)? = null,
    title: @Composable (() -> Unit)? = null,
    text: @Composable (() -> Unit)? = null,
) {
    val backdrop = LocalLiquidBackdrop.current

    // CHANGED: dialog scrim was previously `Color.White.copy(alpha = 0.15f)`. That flat 15%-opaque white was the
    // root cause of the dialog text bleeding into menu/background text — and it didn't adapt to dark theme.
    // New scrim: theme color (`surfaceContainerHigh`) at 0.85 alpha. Adapts to light/dark automatically and is
    // opaque enough that text behind the dialog can't compete with text inside it.
    val scrimColor = MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.85f)

    AlertDialog(
        onDismissRequest = onDismissRequest,
        confirmButton = confirmButton,
        dismissButton = dismissButton,
        icon = icon,
        title = title,
        text = text,
        modifier = if (backdrop != null) {
            modifier.background(
                color = scrimColor,
                shape = MaterialTheme.shapes.extraLarge
            )
        } else modifier,
        containerColor = if (backdrop != null) Color.Transparent else MaterialTheme.colorScheme.surfaceContainerHigh,
        tonalElevation = if (backdrop != null) 0.dp else 6.dp,
        shape = MaterialTheme.shapes.extraLarge
    )
}
