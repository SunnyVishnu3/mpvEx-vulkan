package com.astrixforge.devicemasker.ui.components.expressive

import androidx.compose.ui.graphics.vector.ImageVector

/**
 * Quick Action Button data class.
 *
 * @param label Button label text
 * @param icon Optional leading icon
 * @param onClick Click handler
 * @param enabled Whether the button is enabled
 */
data class QuickAction(
    val label: String,
    val icon: ImageVector? = null,
    val onClick: () -> Unit,
    val enabled: Boolean = true,
)
