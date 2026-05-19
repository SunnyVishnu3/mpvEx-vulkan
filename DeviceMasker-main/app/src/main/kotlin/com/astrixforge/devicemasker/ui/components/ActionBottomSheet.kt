package com.astrixforge.devicemasker.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Save
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SheetState
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.astrixforge.devicemasker.R
import com.astrixforge.devicemasker.ui.theme.DeviceMaskerTheme
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf

/**
 * A reusable sheet that displays a list of action items.
 *
 * Follows Material 3 design patterns with expressive styling. Each action item displays an icon,
 * title, and optional description.
 *
 * @param title Optional header title for the bottom sheet
 * @param actions List of actions to display
 * @param onDismiss Callback when the sheet is dismissed
 * @param sheetState The state of the modal bottom sheet
 * @param modifier Optional modifier
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ActionBottomSheet(
    actions: ImmutableList<ActionItem>,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
    title: String? = null,
    sheetState: SheetState = rememberModalBottomSheetState(),
) {
    AppModalBottomSheet(
        onDismiss = onDismiss,
        sheetState = sheetState,
        modifier = modifier,
        title = title,
    ) {
        actions.forEach { action ->
            ActionItemRow(
                icon = action.icon,
                title = action.title,
                description = action.description,
                enabled = action.enabled,
                onClick = {
                    action.onClick()
                    onDismiss()
                },
            )
        }
    }
}

/** A single row in the action bottom sheet. */
@Composable
private fun ActionItemRow(
    icon: ImageVector,
    title: String,
    description: String?,
    enabled: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier =
            modifier
                .fillMaxWidth()
                .semantics(mergeDescendants = true) {}
                .clickable(enabled = enabled, onClick = onClick)
                .padding(vertical = 14.dp, horizontal = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint =
                if (enabled) MaterialTheme.colorScheme.primary
                else MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(modifier = Modifier.width(16.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.Medium,
                color =
                    if (enabled) MaterialTheme.colorScheme.onSurface
                    else MaterialTheme.colorScheme.onSurfaceVariant,
            )
            if (description != null) {
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = description,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

// ═══════════════════════════════════════════════════════════
// Previews
// ═══════════════════════════════════════════════════════════

@OptIn(ExperimentalMaterial3Api::class)
@Preview(showBackground = true, backgroundColor = 0xFF121212)
@Composable
private fun ActionBottomSheetPreview() {
    DeviceMaskerTheme {
        ActionBottomSheet(
            title = stringResource(R.string.settings_export_sheet_title),
            actions =
                persistentListOf(
                    ActionItem(
                        icon = Icons.Outlined.Save,
                        title = stringResource(R.string.settings_export_save),
                        description = stringResource(R.string.settings_export_save_desc),
                        onClick = {},
                    ),
                    ActionItem(
                        icon = Icons.Outlined.Share,
                        title = stringResource(R.string.settings_export_share),
                        description = stringResource(R.string.settings_export_share_desc),
                        onClick = {},
                    ),
                ),
            onDismiss = {},
        )
    }
}
