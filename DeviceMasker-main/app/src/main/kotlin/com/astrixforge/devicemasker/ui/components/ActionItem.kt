package com.astrixforge.devicemasker.ui.components

import androidx.compose.ui.graphics.vector.ImageVector

/**
 * Data class representing an action item in the bottom sheet.
 *
 * @param icon Icon to display for the action
 * @param title Primary title text
 * @param description Optional subtitle/description text
 * @param onClick Callback when the action is selected
 */
data class ActionItem(
    val icon: ImageVector,
    val title: String,
    val description: String? = null,
    val enabled: Boolean = true,
    val onClick: () -> Unit,
)
