package com.astrixforge.devicemasker.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.astrixforge.devicemasker.R
import com.astrixforge.devicemasker.data.models.DeviceIdentifier
import com.astrixforge.devicemasker.data.models.SpoofType
import com.astrixforge.devicemasker.ui.components.expressive.CompactExpressiveIconButton
import com.astrixforge.devicemasker.ui.components.expressive.ExpressiveCard
import com.astrixforge.devicemasker.ui.components.expressive.ExpressiveIconButton
import com.astrixforge.devicemasker.ui.components.expressive.ExpressiveSwitch
import com.astrixforge.devicemasker.ui.theme.DeviceMaskerTheme

/**
 * Card displaying a single spoof value with controls.
 *
 * Shows the identifier label, current value, and action buttons for regenerating and editing the
 * value.
 *
 * @param identifier The device identifier to display
 * @param onRegenerate Callback to regenerate the value
 * @param onEdit Callback to edit the value
 * @param onToggle Callback to enable/disable this spoof
 * @param modifier Optional modifier
 * @param showActions Whether to show action buttons
 * @param maskValue Whether to mask the displayed value
 */
@Composable
fun SpoofValueCard(
    identifier: DeviceIdentifier,
    onRegenerate: () -> Unit,
    onEdit: (String) -> Unit,
    onToggle: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    showActions: Boolean = true,
    maskValue: Boolean = false,
) {
    ExpressiveCard(
        onClick = { /* Primary card action if any */ },
        modifier = modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.medium,
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            SpoofValueHeader(identifier = identifier, toggleRequested = onToggle)
            Spacer(modifier = Modifier.height(12.dp))
            SpoofValueDisplay(identifier = identifier, maskValue = maskValue)
            if (showActions) {
                SpoofValueActions(
                    value = identifier.value,
                    regenerateRequested = onRegenerate,
                    editRequested = onEdit,
                )
            }
        }
    }
}

@Composable
private fun SpoofValueHeader(identifier: DeviceIdentifier, toggleRequested: (Boolean) -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = identifier.type.displayName,
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Text(
                text = identifier.type.category.name,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        ExpressiveSwitch(checked = identifier.isEnabled, onCheckedChange = toggleRequested)
    }
}

@Composable
private fun SpoofValueDisplay(identifier: DeviceIdentifier, maskValue: Boolean) {
    Box(
        modifier =
            Modifier.fillMaxWidth()
                .background(
                    color = MaterialTheme.colorScheme.surfaceContainerHigh,
                    shape = MaterialTheme.shapes.small,
                )
                .padding(12.dp)
    ) {
        Text(
            text = displayValue(identifier, maskValue),
            style = MaterialTheme.typography.bodyMedium.copy(fontFamily = FontFamily.Monospace),
            color =
                if (identifier.value != null) {
                    MaterialTheme.colorScheme.onSurface
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant
                },
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

private fun displayValue(identifier: DeviceIdentifier, maskValue: Boolean): String =
    if (maskValue) {
        maskValueString(identifier.type, identifier.value)
    } else {
        identifier.value ?: "Not set"
    }

@Composable
private fun SpoofValueActions(
    value: String?,
    regenerateRequested: () -> Unit,
    editRequested: (String) -> Unit,
) {
    Column {
        Spacer(modifier = Modifier.height(8.dp))
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
            CompactExpressiveIconButton(
                onClick = regenerateRequested,
                icon = Icons.Default.Refresh,
                contentDescription = stringResource(id = R.string.action_regenerate),
                tint = MaterialTheme.colorScheme.primary,
            )
            Spacer(modifier = Modifier.width(4.dp))
            CompactExpressiveIconButton(
                onClick = { editRequested(value ?: "") },
                icon = Icons.Default.Edit,
                contentDescription = stringResource(id = R.string.action_edit_item),
            )
        }
    }
}

/** Compact version of SpoofValueCard for dense lists. */
@Composable
fun CompactSpoofValueCard(
    label: String,
    value: String?,
    onRegenerate: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    ExpressiveCard(
        onClick = { /* Compact item click */ },
        modifier = modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.medium,
        containerColor =
            Color.Transparent, // Let the parent container's color show through or use a subtle
        // surface
    ) {
        CompactSpoofValueContent(
            label = label,
            value = value,
            regenerateRequested = onRegenerate,
            enabled = enabled,
        )
    }
}

@Composable
private fun CompactSpoofValueContent(
    label: String,
    value: String?,
    regenerateRequested: () -> Unit,
    enabled: Boolean,
) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        CompactSpoofValueText(label = label, value = value, modifier = Modifier.weight(1f))
        ExpressiveIconButton(
            onClick = regenerateRequested,
            icon = Icons.Default.Refresh,
            contentDescription = stringResource(id = R.string.action_regenerate),
            enabled = enabled,
            tint = MaterialTheme.colorScheme.primary,
        )
    }
}

@Composable
private fun CompactSpoofValueText(label: String, value: String?, modifier: Modifier = Modifier) {
    Column(modifier = modifier) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface,
        )
        Text(
            text = value ?: "Not set",
            style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
            color =
                if (value != null) {
                    MaterialTheme.colorScheme.primary
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant
                },
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

/** Masks a value for display based on type. */
private fun maskValueString(type: SpoofType, value: String?): String {
    if (value == null) return "Not set"

    return when (type) {
        SpoofType.IMEI,
        SpoofType.IMSI -> {
            if (value.length > LONG_ID_VISIBLE_CHARS) {
                value.take(LONG_ID_SIDE_CHARS) + MASK + value.takeLast(LONG_ID_SIDE_CHARS)
            } else {
                MASK
            }
        }

        SpoofType.SERIAL -> {
            if (value.length > SHORT_ID_VISIBLE_CHARS) {
                MASK + value.takeLast(SHORT_ID_VISIBLE_CHARS)
            } else {
                MASK
            }
        }

        SpoofType.ANDROID_ID,
        SpoofType.GSF_ID,
        SpoofType.ADVERTISING_ID -> {
            if (value.length > HEX_ID_VISIBLE_CHARS) {
                value.take(HEX_ID_SIDE_CHARS) + MASK + value.takeLast(HEX_ID_SIDE_CHARS)
            } else {
                MASK
            }
        }

        SpoofType.WIFI_MAC,
        SpoofType.BLUETOOTH_MAC -> {
            val parts = value.split(":")
            if (parts.size == MAC_ADDRESS_PARTS) {
                "${parts[0]}:${parts[1]}:**:**:**:${parts[MAC_LAST_PART_INDEX]}"
            } else {
                "**:**:**:**:**:**"
            }
        }

        else -> value
    }
}

private const val MASK = "***"
private const val LONG_ID_VISIBLE_CHARS = 6
private const val LONG_ID_SIDE_CHARS = 3
private const val SHORT_ID_VISIBLE_CHARS = 4
private const val HEX_ID_VISIBLE_CHARS = 8
private const val HEX_ID_SIDE_CHARS = 4
private const val MAC_ADDRESS_PARTS = 6
private const val MAC_LAST_PART_INDEX = 5

// ═══════════════════════════════════════════════════════════
// Previews
// ═══════════════════════════════════════════════════════════

@Preview(showBackground = true, backgroundColor = 0xFF121212)
@Composable
private fun SpoofValueCardPreview() {
    DeviceMaskerTheme {
        SpoofValueCard(
            identifier =
                DeviceIdentifier(
                    type = SpoofType.IMEI,
                    value = "358673091234567",
                    isEnabled = true,
                ),
            onRegenerate = {},
            onEdit = {},
            onToggle = {},
        )
    }
}

@Preview(showBackground = true, backgroundColor = 0xFF121212)
@Composable
private fun SpoofValueCardMaskedPreview() {
    DeviceMaskerTheme {
        SpoofValueCard(
            identifier =
                DeviceIdentifier(
                    type = SpoofType.WIFI_MAC,
                    value = "A4:83:E7:12:34:56",
                    isEnabled = true,
                ),
            onRegenerate = {},
            onEdit = {},
            onToggle = {},
            maskValue = true,
        )
    }
}

@Preview(showBackground = true, backgroundColor = 0xFF121212)
@Composable
private fun CompactSpoofValueCardPreview() {
    DeviceMaskerTheme {
        CompactSpoofValueCard(label = "IMEI", value = "358673091234567", onRegenerate = {})
    }
}
