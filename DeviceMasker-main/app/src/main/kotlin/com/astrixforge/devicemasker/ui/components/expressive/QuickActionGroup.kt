package com.astrixforge.devicemasker.ui.components.expressive

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.outlined.Fingerprint
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ButtonGroup
import androidx.compose.material3.ButtonGroupDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.astrixforge.devicemasker.ui.theme.DeviceMaskerTheme
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf

/**
 * Material 3 Expressive ButtonGroup for quick actions.
 *
 * @param actions List of quick actions to display
 * @param modifier Modifier for the button group
 */
@Composable
fun QuickActionGroup(actions: ImmutableList<QuickAction>, modifier: Modifier = Modifier) {
    ButtonGroup(
        overflowIndicator = { state -> ButtonGroupDefaults.OverflowIndicator(state) },
        modifier = modifier.fillMaxWidth(),
    ) {
        actions.forEach { action ->
            clickableItem(
                onClick = action.onClick,
                label = action.label,
                icon =
                    action.icon?.let { icon ->
                        {
                            Icon(
                                imageVector = icon,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp),
                            )
                        }
                    },
                weight = 1f,
                enabled = action.enabled,
            )
        }
    }
}

/**
 * Simple two-button quick action row (fallback for non-experimental use).
 *
 * Uses FilledTonalButton for consistency when ButtonGroup is not available.
 *
 * @param primaryAction Primary action (left button)
 * @param secondaryAction Secondary action (right button)
 * @param modifier Modifier for the row
 */
@Composable
fun QuickActionRow(
    primaryAction: QuickAction,
    secondaryAction: QuickAction,
    modifier: Modifier = Modifier,
) {
    ButtonGroup(
        overflowIndicator = { state -> ButtonGroupDefaults.OverflowIndicator(state) },
        modifier = modifier.fillMaxWidth(),
    ) {
        listOf(primaryAction, secondaryAction).forEach { action ->
            clickableItem(
                onClick = action.onClick,
                label = action.label,
                icon = action.icon?.let { icon -> { Icon(icon, null) } },
                weight = 1f,
                enabled = action.enabled,
            )
        }
    }
}

/**
 * Single-select button group for mode selection (Stable fallback).
 *
 * @param options List of option labels
 * @param selectedIndex Currently selected index
 * @param onSelectionChange Callback when selection changes
 * @param modifier Modifier for the button group
 */
@Composable
fun SelectionButtonGroup(
    options: ImmutableList<String>,
    selectedIndex: Int,
    onSelectionChange: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    androidx.compose.foundation.layout.Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        options.forEachIndexed { index, option ->
            val isSelected = selectedIndex == index
            if (isSelected) {
                FilledTonalButton(
                    onClick = { onSelectionChange(index) },
                    modifier = Modifier.weight(1f),
                    contentPadding = ButtonDefaults.TextButtonContentPadding,
                ) {
                    Text(text = option, style = MaterialTheme.typography.labelLarge)
                }
            } else {
                OutlinedButton(
                    onClick = { onSelectionChange(index) },
                    modifier = Modifier.weight(1f),
                    contentPadding = ButtonDefaults.TextButtonContentPadding,
                ) {
                    Text(text = option, style = MaterialTheme.typography.labelLarge)
                }
            }
        }
    }
}

// ═══════════════════════════════════════════════════════════
// Previews
// ═══════════════════════════════════════════════════════════

@Preview(showBackground = true, backgroundColor = 0xFF121212)
@Composable
private fun QuickActionGroupPreview() {
    DeviceMaskerTheme {
        Box(modifier = Modifier.padding(16.dp)) {
            QuickActionGroup(
                actions =
                    persistentListOf(
                        QuickAction(
                            label = "Configure",
                            icon = Icons.Outlined.Fingerprint,
                            onClick = {},
                        ),
                        QuickAction(label = "Regenerate", icon = Icons.Filled.Refresh, onClick = {}),
                    )
            )
        }
    }
}

@Preview(showBackground = true, backgroundColor = 0xFF121212)
@Composable
private fun QuickActionRowPreview() {
    DeviceMaskerTheme {
        Box(modifier = Modifier.padding(16.dp)) {
            QuickActionRow(
                primaryAction =
                    QuickAction(
                        label = "Configure",
                        icon = Icons.Outlined.Fingerprint,
                        onClick = {},
                    ),
                secondaryAction =
                    QuickAction(label = "Regenerate", icon = Icons.Filled.Refresh, onClick = {}),
            )
        }
    }
}

@Preview(showBackground = true, backgroundColor = 0xFF121212)
@Composable
private fun SelectionButtonGroupPreview() {
    DeviceMaskerTheme {
        var selectedIndex by remember { mutableIntStateOf(0) }
        Box(modifier = Modifier.padding(16.dp)) {
            SelectionButtonGroup(
                options = persistentListOf("Day", "Week", "Month"),
                selectedIndex = selectedIndex,
                onSelectionChange = { selectedIndex = it },
            )
        }
    }
}
