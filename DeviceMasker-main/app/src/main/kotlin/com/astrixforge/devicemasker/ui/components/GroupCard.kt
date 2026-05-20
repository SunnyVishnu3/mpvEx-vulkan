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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Groups
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.astrixforge.devicemasker.R
import com.astrixforge.devicemasker.common.assignedAppCount
import com.astrixforge.devicemasker.common.summary
import com.astrixforge.devicemasker.data.models.SpoofGroup
import com.astrixforge.devicemasker.ui.components.expressive.CompactExpressiveIconButton
import com.astrixforge.devicemasker.ui.components.expressive.ExpressiveCard
import com.astrixforge.devicemasker.ui.components.expressive.ExpressiveSwitch
import com.astrixforge.devicemasker.ui.components.expressive.animatedRoundedCornerShape
import com.astrixforge.devicemasker.ui.theme.DeviceMaskerTheme
import com.astrixforge.devicemasker.ui.theme.statusActive
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Card displaying a spoof group in a list.
 *
 * Shows group name, summary, creation date, app count, enable/disable switch, and action buttons.
 * Highlights the default group with a star indicator.
 *
 * @param group The group to display
 * @param isEnabled Whether spoofing is enabled for this group
 * @param appCount Number of apps assigned to this group
 * @param onClick Callback when the card is clicked
 * @param onEdit Callback when edit is requested
 * @param onDelete Callback when delete is requested
 * @param onSetDefault Callback to set as default group
 * @param onEnableChange Callback when enable/disable switch is toggled
 * @param modifier Optional modifier
 */
@Composable
fun GroupCard(
    group: SpoofGroup,
    onClick: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
    onSetDefault: () -> Unit,
    modifier: Modifier = Modifier,
    isEnabled: Boolean = group.isEnabled,
    appCount: Int = group.assignedAppCount(),
    onEnableChange: (Boolean) -> Unit = {},
) {
    val contentAlpha = if (isEnabled) 1f else 0.5f
    val groupEnabledState = stringResource(R.string.group_card_enabled_state)
    val groupDisabledState = stringResource(R.string.group_card_disabled_state)

    val cardShape =
        animatedRoundedCornerShape(
            targetRadius = if (isEnabled) 24.dp else 16.dp,
            label = "groupCardMorph",
        )

    ExpressiveCard(
        onClick = onClick,
        modifier =
            modifier.fillMaxWidth().semantics {
                stateDescription = if (isEnabled) groupEnabledState else groupDisabledState
            },
        shape = cardShape,
        containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
    ) {
        Column(modifier = Modifier.padding(16.dp).alpha(contentAlpha)) {
            GroupCardHeader(group = group, isEnabled = isEnabled, onEnableChange = onEnableChange)
            Spacer(modifier = Modifier.height(12.dp))
            GroupCardFooter(
                group = group,
                appCount = appCount,
                onSetDefault = onSetDefault,
                onEdit = onEdit,
                onDelete = onDelete,
            )
        }
    }
}

@Composable
private fun GroupCardHeader(
    group: SpoofGroup,
    isEnabled: Boolean,
    onEnableChange: (Boolean) -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
            GroupIcon()
            Spacer(modifier = Modifier.width(12.dp))
            GroupTitleBlock(group)
        }

        ExpressiveSwitch(checked = isEnabled, onCheckedChange = onEnableChange)
    }
}

@Composable
private fun GroupIcon() {
    Box(
        modifier =
            Modifier.size(40.dp)
                .background(
                    color = MaterialTheme.colorScheme.primary.copy(alpha = 0.15f),
                    shape = CircleShape,
                ),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = Icons.Default.Groups,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(24.dp),
        )
    }
}

@Composable
private fun GroupTitleBlock(group: SpoofGroup) {
    Column {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = group.name,
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )

            if (group.isDefault) {
                Spacer(modifier = Modifier.width(8.dp))
                DefaultBadge()
            }
        }

        if (group.description.isNotBlank()) {
            Text(
                text = group.description,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
private fun GroupCardFooter(
    group: SpoofGroup,
    appCount: Int,
    onSetDefault: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
) {
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        GroupStats(group = group, appCount = appCount)
        GroupActions(
            isDefault = group.isDefault,
            onSetDefault = onSetDefault,
            onEdit = onEdit,
            onDelete = onDelete,
        )
    }
}

@Composable
private fun GroupStats(group: SpoofGroup, appCount: Int) {
    Column {
        Text(
            text =
                pluralStringResource(
                    id = R.plurals.group_card_apps_count,
                    count = appCount,
                    appCount,
                ),
            style = MaterialTheme.typography.bodySmall,
            color =
                if (appCount > 0) {
                    MaterialTheme.colorScheme.primary
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant
                },
        )
        val dateFormatter = remember { SimpleDateFormat("MMM d, yyyy", Locale.getDefault()) }
        Text(
            text =
                stringResource(
                    R.string.group_card_created,
                    dateFormatter.format(Date(group.createdAt)),
                ),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
        )
    }
}

@Composable
private fun GroupActions(
    isDefault: Boolean,
    onSetDefault: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
) {
    Row {
        if (!isDefault) {
            CompactExpressiveIconButton(
                onClick = onSetDefault,
                icon = Icons.Default.Star,
                contentDescription = stringResource(R.string.group_card_set_default),
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        CompactExpressiveIconButton(
            onClick = onEdit,
            icon = Icons.Default.Edit,
            contentDescription = stringResource(R.string.group_card_edit),
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        if (!isDefault) {
            CompactExpressiveIconButton(
                onClick = onDelete,
                icon = Icons.Default.Delete,
                contentDescription = stringResource(R.string.group_card_delete),
                tint = MaterialTheme.colorScheme.error,
            )
        }
    }
}

/** Badge indicating the default group. */
@Composable
private fun DefaultBadge(modifier: Modifier = Modifier) {
    Box(
        modifier =
            modifier
                .background(
                    color = MaterialTheme.colorScheme.primary,
                    shape = MaterialTheme.shapes.small,
                )
                .padding(horizontal = 6.dp, vertical = 2.dp)
    ) {
        Text(
            text = stringResource(R.string.group_card_default),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onPrimary,
        )
    }
}

/**
 * Compact group card for selection lists. Uses ExpressiveCard for spring-animated selection
 * feedback.
 */
@Composable
fun CompactGroupCard(
    group: SpoofGroup,
    isSelected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val compactShape =
        animatedRoundedCornerShape(
            targetRadius = if (isSelected) 16.dp else 12.dp,
            label = "compactGroupCardMorph",
        )

    ExpressiveCard(
        onClick = onClick,
        modifier = modifier.fillMaxWidth(),
        isSelected = isSelected,
        shape = compactShape,
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                imageVector = Icons.Default.Groups,
                contentDescription = null,
                tint =
                    if (isSelected) {
                        MaterialTheme.colorScheme.primary
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    },
                modifier = Modifier.size(24.dp),
            )

            Spacer(modifier = Modifier.width(12.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = group.name,
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Text(
                    text = group.summary(),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            if (group.isDefault) {
                DefaultBadge()
            }

            if (isSelected) {
                Spacer(modifier = Modifier.width(8.dp))
                Icon(
                    imageVector = Icons.Default.Check,
                    contentDescription = stringResource(R.string.group_card_selected),
                    tint = MaterialTheme.colorScheme.statusActive,
                    modifier = Modifier.size(20.dp),
                )
            }
        }
    }
}

/** Formats a timestamp for display. */
private fun formatDate(timestamp: Long): String {
    val formatter = SimpleDateFormat("MMM d, yyyy", Locale.getDefault())
    return formatter.format(Date(timestamp))
}

// ═══════════════════════════════════════════════════════════
// Previews
// ═══════════════════════════════════════════════════════════

@Preview(showBackground = true, backgroundColor = 0xFF121212)
@Composable
private fun GroupCardDefaultPreview() {
    DeviceMaskerTheme {
        GroupCard(
            group = SpoofGroup.createDefaultGroup(),
            onClick = {},
            onEdit = {},
            onDelete = {},
            onSetDefault = {},
        )
    }
}

@Preview(showBackground = true, backgroundColor = 0xFF121212)
@Composable
private fun GroupCardCustomPreview() {
    DeviceMaskerTheme {
        GroupCard(
            group = SpoofGroup.createNew("Samsung Galaxy S24"),
            onClick = {},
            onEdit = {},
            onDelete = {},
            onSetDefault = {},
        )
    }
}

@Preview(showBackground = true, backgroundColor = 0xFF121212)
@Composable
private fun CompactGroupCardPreview() {
    DeviceMaskerTheme {
        CompactGroupCard(group = SpoofGroup.createDefaultGroup(), isSelected = true, onClick = {})
    }
}
