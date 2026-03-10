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

    AlertDialog(
        onDismissRequest = onDismissRequest,
        confirmButton = confirmButton,
        dismissButton = dismissButton,
        icon = icon,
        title = title,
        text = text,
        // The Magic Sauce: If Liquid is enabled, it strips the grey background and makes it a transparent glass pane!
        modifier = if (backdrop != null) {
            modifier.background(
                color = Color.White.copy(alpha = 0.15f), 
                shape = MaterialTheme.shapes.extraLarge
            )
        } else modifier,
        containerColor = if (backdrop != null) Color.Transparent else MaterialTheme.colorScheme.surfaceContainerHigh,
        tonalElevation = if (backdrop != null) 0.dp else 6.dp,
        shape = MaterialTheme.shapes.extraLarge
    )
}
