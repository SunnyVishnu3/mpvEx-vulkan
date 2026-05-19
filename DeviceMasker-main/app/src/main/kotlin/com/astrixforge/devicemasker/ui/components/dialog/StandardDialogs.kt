package com.astrixforge.devicemasker.ui.components.dialog

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import com.astrixforge.devicemasker.R
import com.astrixforge.devicemasker.ui.theme.DeviceMaskerTheme

/**
 * Reusable confirmation dialog for destructive or important actions.
 *
 * @param title Dialog title
 * @param message Dialog message/body
 * @param confirmText Text for confirm button (defaults to "Confirm")
 * @param onConfirm Callback when confirm is clicked
 * @param onDismiss Callback when dialog is dismissed
 * @param isDestructive If true, shows error-colored confirm button
 */
@Composable
fun ConfirmationDialog(
    title: String,
    message: String,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
    confirmText: String = stringResource(id = R.string.action_confirm),
    dismissText: String = stringResource(id = R.string.action_cancel),
    isDestructive: Boolean = false,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        modifier = modifier,
        icon = {
            Icon(
                imageVector = if (isDestructive) Icons.Filled.Delete else Icons.Filled.Warning,
                contentDescription = null,
                tint =
                    if (isDestructive) {
                        MaterialTheme.colorScheme.error
                    } else {
                        MaterialTheme.colorScheme.primary
                    },
            )
        },
        title = { Text(text = title) },
        text = { Text(text = message) },
        confirmButton = {
            TextButton(onClick = onConfirm) {
                Text(
                    text = confirmText,
                    color =
                        if (isDestructive) {
                            MaterialTheme.colorScheme.error
                        } else {
                            MaterialTheme.colorScheme.primary
                        },
                )
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(text = dismissText) } },
    )
}

/**
 * Specialized delete confirmation dialog.
 *
 * @param itemName Name of the item being deleted (for display)
 * @param onConfirm Callback when delete is confirmed
 * @param onDismiss Callback when dialog is dismissed
 */
@Composable
fun DeleteConfirmationDialog(
    itemName: String,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    ConfirmationDialog(
        title = stringResource(id = R.string.dialog_delete_title),
        message = stringResource(id = R.string.dialog_delete_message, itemName),
        confirmText = stringResource(id = R.string.action_delete),
        onConfirm = onConfirm,
        onDismiss = onDismiss,
        modifier = modifier,
        isDestructive = true,
    )
}

// ═══════════════════════════════════════════════════════════
// Previews
// ═══════════════════════════════════════════════════════════

@Preview(showBackground = true, backgroundColor = 0xFF121212)
@Composable
private fun ConfirmationDialogPreview() {
    DeviceMaskerTheme {
        ConfirmationDialog(
            title = "Confirm Action",
            message = "Are you sure you want to proceed?",
            onConfirm = {},
            onDismiss = {},
        )
    }
}

@Preview(showBackground = true, backgroundColor = 0xFF121212)
@Composable
private fun DeleteDialogPreview() {
    DeviceMaskerTheme {
        DeleteConfirmationDialog(itemName = "Work Group", onConfirm = {}, onDismiss = {})
    }
}
