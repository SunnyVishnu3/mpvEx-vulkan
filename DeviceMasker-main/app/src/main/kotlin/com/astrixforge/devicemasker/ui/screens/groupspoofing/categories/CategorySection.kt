package com.astrixforge.devicemasker.ui.screens.groupspoofing.categories

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.astrixforge.devicemasker.R
import com.astrixforge.devicemasker.common.DeviceProfilePreset
import com.astrixforge.devicemasker.common.SpoofGroup
import com.astrixforge.devicemasker.common.SpoofType
import com.astrixforge.devicemasker.common.models.Carrier
import com.astrixforge.devicemasker.ui.components.IconCircle
import com.astrixforge.devicemasker.ui.components.expressive.CompactExpressiveIconButton
import com.astrixforge.devicemasker.ui.components.expressive.ExpressiveCard
import com.astrixforge.devicemasker.ui.components.expressive.ExpressiveSwitch
import com.astrixforge.devicemasker.ui.components.expressive.animatedRoundedCornerShape
import com.astrixforge.devicemasker.ui.screens.groupspoofing.items.CorrelatedSpoofItem
import com.astrixforge.devicemasker.ui.screens.groupspoofing.items.IndependentSpoofItem
import com.astrixforge.devicemasker.ui.screens.groupspoofing.model.UIDisplayCategory
import com.astrixforge.devicemasker.ui.screens.groupspoofing.model.themeColor
import com.astrixforge.devicemasker.ui.theme.AppMotion

/** Category section for group spoof values - organized by correlation groups. */
@Composable
fun CategorySection(
    category: UIDisplayCategory,
    group: SpoofGroup?,
    isExpanded: Boolean,
    onToggleExpand: () -> Unit,
    onRegenerate: (SpoofType) -> Unit,
    onRegenerateCategory: () -> Unit,
    onRegenerateLocation: () -> Unit,
    onToggle: (SpoofType, Boolean) -> Unit,
    onCarrierChange: (Carrier) -> Unit,
    timezoneSelected: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val categoryColor = category.themeColor()
    val rotationAngle by
        animateFloatAsState(
            targetValue = if (isExpanded) 0f else 180f,
            animationSpec = AppMotion.FastSpring,
            label = "expandRotation",
        )

    val categoryShape = animatedRoundedCornerShape(targetRadius = if (isExpanded) 24.dp else 16.dp)
    val isCategoryEnabled = category.isEnabledFor(group)

    ExpressiveCard(
        onClick = { onToggleExpand() },
        modifier = modifier.animateContentSize(animationSpec = spring()),
        shape = categoryShape,
        containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
    ) {
        Column {
            CategoryHeader(
                category = category,
                categoryColor = categoryColor,
                isExpanded = isExpanded,
                rotationAngle = rotationAngle,
            )
            CategoryContent(
                category = category,
                group = group,
                isExpanded = isExpanded,
                isCategoryEnabled = isCategoryEnabled,
                categoryColor = categoryColor,
                onRegenerate = onRegenerate,
                onRegenerateCategory = onRegenerateCategory,
                onRegenerateLocation = onRegenerateLocation,
                onToggle = onToggle,
                onCarrierChange = onCarrierChange,
                timezoneSelected = timezoneSelected,
            )
        }
    }
}

@Composable
private fun CategoryHeader(
    category: UIDisplayCategory,
    categoryColor: Color,
    isExpanded: Boolean,
    rotationAngle: Float,
) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(16.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.weight(1f),
        ) {
            IconCircle(
                icon = category.icon,
                containerColor = categoryColor.copy(alpha = 0.15f),
                iconColor = categoryColor,
                iconSize = 22.dp,
            )
            Text(
                text = stringResource(id = category.titleRes),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
            )
        }

        Icon(
            imageVector = Icons.Filled.ExpandLess,
            contentDescription =
                if (isExpanded) {
                    stringResource(id = R.string.action_collapse)
                } else {
                    stringResource(id = R.string.action_expand)
                },
            modifier = Modifier.rotate(rotationAngle),
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun CategoryContent(
    category: UIDisplayCategory,
    group: SpoofGroup?,
    isExpanded: Boolean,
    isCategoryEnabled: Boolean,
    categoryColor: Color,
    onRegenerate: (SpoofType) -> Unit,
    onRegenerateCategory: () -> Unit,
    onRegenerateLocation: () -> Unit,
    onToggle: (SpoofType, Boolean) -> Unit,
    onCarrierChange: (Carrier) -> Unit,
    timezoneSelected: (String) -> Unit,
) {
    AnimatedVisibility(
        visible = isExpanded,
        enter = expandVertically(animationSpec = spring()) + fadeIn(),
        exit = shrinkVertically(animationSpec = spring()) + fadeOut(),
    ) {
        Column(
            modifier = Modifier.padding(start = 16.dp, end = 16.dp, bottom = 16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            CorrelatedCategoryActions(
                category = category,
                isCategoryEnabled = isCategoryEnabled,
                categoryColor = categoryColor,
                onRegenerateCategory = onRegenerateCategory,
                onToggle = onToggle,
            )
            CategoryItems(
                category = category,
                group = group,
                isCategoryEnabled = isCategoryEnabled,
                onRegenerate = onRegenerate,
                onRegenerateCategory = onRegenerateCategory,
                onRegenerateLocation = onRegenerateLocation,
                onToggle = onToggle,
                onCarrierChange = onCarrierChange,
                timezoneSelected = timezoneSelected,
            )
        }
    }
}

@Composable
private fun CorrelatedCategoryActions(
    category: UIDisplayCategory,
    isCategoryEnabled: Boolean,
    categoryColor: Color,
    onRegenerateCategory: () -> Unit,
    onToggle: (SpoofType, Boolean) -> Unit,
) {
    if (!category.isCorrelated) return

    Column {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            ExpressiveSwitch(
                checked = isCategoryEnabled,
                onCheckedChange = { enabled ->
                    category.types.forEach { type -> onToggle(type, enabled) }
                },
            )

            if (isCategoryEnabled) {
                CompactExpressiveIconButton(
                    onClick = onRegenerateCategory,
                    icon = Icons.Filled.Refresh,
                    contentDescription = stringResource(id = R.string.action_regenerate_all),
                    tint = categoryColor,
                )
            }
        }
        Spacer(modifier = Modifier.height(4.dp))
    }
}

@Composable
private fun CategoryItems(
    category: UIDisplayCategory,
    group: SpoofGroup?,
    isCategoryEnabled: Boolean,
    onRegenerate: (SpoofType) -> Unit,
    onRegenerateCategory: () -> Unit,
    onRegenerateLocation: () -> Unit,
    onToggle: (SpoofType, Boolean) -> Unit,
    onCarrierChange: (Carrier) -> Unit,
    timezoneSelected: (String) -> Unit,
) {
    when (category) {
        UIDisplayCategory.SIM_CARD ->
            SIMCardCategoryContent(
                group = group,
                onToggle = onToggle,
                onRegenerate = onRegenerate,
                onCarrierChange = onCarrierChange,
            )

        UIDisplayCategory.LOCATION ->
            LocationCategoryContent(
                group = group,
                onToggle = onToggle,
                onRegenerate = onRegenerate,
                onRegenerateLocation = onRegenerateLocation,
                timezoneSelected = timezoneSelected,
            )

        UIDisplayCategory.DEVICE_HARDWARE ->
            DeviceHardwareCategoryContent(
                group = group,
                onToggle = onToggle,
                onRegenerate = onRegenerate,
                onRegenerateCategory = onRegenerateCategory,
            )

        else ->
            StandardCategoryItems(
                category = category,
                group = group,
                isCategoryEnabled = isCategoryEnabled,
                onToggle = onToggle,
                onRegenerate = onRegenerate,
            )
    }
}

@Composable
private fun StandardCategoryItems(
    category: UIDisplayCategory,
    group: SpoofGroup?,
    isCategoryEnabled: Boolean,
    onToggle: (SpoofType, Boolean) -> Unit,
    onRegenerate: (SpoofType) -> Unit,
) {
    category.types.forEach { type ->
        StandardCategoryItem(
            category = category,
            group = group,
            type = type,
            isCategoryEnabled = isCategoryEnabled,
            onToggle = onToggle,
            onRegenerate = onRegenerate,
        )
    }
}

@Composable
private fun StandardCategoryItem(
    category: UIDisplayCategory,
    group: SpoofGroup?,
    type: SpoofType,
    isCategoryEnabled: Boolean,
    onToggle: (SpoofType, Boolean) -> Unit,
    onRegenerate: (SpoofType) -> Unit,
) {
    val isGroupEnabled = group?.isTypeEnabled(type) ?: false
    val displayValue = type.displayValue(group?.getValue(type).orEmpty())

    if (category.isCorrelated) {
        CorrelatedSpoofItem(
            type = type,
            value = displayValue,
            isEnabled = isCategoryEnabled,
            modifier = Modifier.fillMaxWidth(),
        )
    } else {
        IndependentSpoofItem(
            type = type,
            value = displayValue,
            isEnabled = isGroupEnabled,
            onToggle = { enabled -> onToggle(type, enabled) },
            onRegenerate = { onRegenerate(type) },
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

private fun UIDisplayCategory.isEnabledFor(group: SpoofGroup?): Boolean =
    isCorrelated && types.all { group?.isTypeEnabled(it) ?: false }

private fun SpoofType.displayValue(rawValue: String): String =
    if (this == SpoofType.DEVICE_PROFILE) {
        DeviceProfilePreset.findById(rawValue)?.name ?: rawValue
    } else {
        rawValue
    }
