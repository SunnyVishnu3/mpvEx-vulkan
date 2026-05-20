package com.astrixforge.devicemasker.ui.screens.groupspoofing.categories

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.spring
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.astrixforge.devicemasker.R
import com.astrixforge.devicemasker.common.DeviceProfilePreset
import com.astrixforge.devicemasker.common.SpoofGroup
import com.astrixforge.devicemasker.common.SpoofType
import com.astrixforge.devicemasker.ui.components.expressive.CompactExpressiveIconButton
import com.astrixforge.devicemasker.ui.components.expressive.ExpressiveCard
import com.astrixforge.devicemasker.ui.components.expressive.ExpressiveSwitch
import com.astrixforge.devicemasker.ui.screens.groupspoofing.items.IndependentSpoofItem
import com.astrixforge.devicemasker.ui.screens.groupspoofing.items.ReadOnlyValueRow

/**
 * Content layout for Device Hardware category.
 *
 * All 3 items are fully independent with their own Switch + Regenerate:
 * - Device Profile (shows preset name)
 * - IMEI
 * - Serial
 */
@Composable
fun DeviceHardwareCategoryContent(
    group: SpoofGroup?,
    onToggle: (SpoofType, Boolean) -> Unit,
    onRegenerate: (SpoofType) -> Unit,
    modifier: Modifier = Modifier,
    @Suppress("UNUSED_PARAMETER") onRegenerateCategory: () -> Unit = {},
) {
    val uiState = group.toDeviceHardwareUiState()

    Column(modifier = modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        DeviceProfileCard(uiState = uiState, onToggle = onToggle, onRegenerate = onRegenerate)
        HardwareSpoofItem(
            type = SpoofType.IMEI,
            value = uiState.imeiValue,
            isEnabled = uiState.imeiEnabled,
            onToggle = onToggle,
            onRegenerate = onRegenerate,
        )
        HardwareSpoofItem(
            type = SpoofType.SERIAL,
            value = uiState.serialValue,
            isEnabled = uiState.serialEnabled,
            onToggle = onToggle,
            onRegenerate = onRegenerate,
        )
    }
}

private data class DeviceHardwareUiState(
    val deviceProfileEnabled: Boolean,
    val deviceProfileDisplayValue: String,
    val currentPreset: DeviceProfilePreset?,
    val imeiEnabled: Boolean,
    val imeiValue: String,
    val serialEnabled: Boolean,
    val serialValue: String,
)

private fun SpoofGroup?.toDeviceHardwareUiState(): DeviceHardwareUiState {
    val rawProfile = this?.getValue(SpoofType.DEVICE_PROFILE).orEmpty()
    val preset = DeviceProfilePreset.findById(rawProfile)
    return DeviceHardwareUiState(
        deviceProfileEnabled = this?.isTypeEnabled(SpoofType.DEVICE_PROFILE) ?: false,
        deviceProfileDisplayValue = preset?.name ?: rawProfile,
        currentPreset = preset,
        imeiEnabled = this?.isTypeEnabled(SpoofType.IMEI) ?: false,
        imeiValue = this?.getValue(SpoofType.IMEI).orEmpty(),
        serialEnabled = this?.isTypeEnabled(SpoofType.SERIAL) ?: false,
        serialValue = this?.getValue(SpoofType.SERIAL).orEmpty(),
    )
}

@Composable
private fun DeviceProfileCard(
    uiState: DeviceHardwareUiState,
    onToggle: (SpoofType, Boolean) -> Unit,
    onRegenerate: (SpoofType) -> Unit,
    modifier: Modifier = Modifier,
) {
    ExpressiveCard(
        onClick = { /* Card click feedback */ },
        modifier = modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.medium,
        containerColor = MaterialTheme.colorScheme.surface,
        elevation = CardDefaults.elevatedCardElevation(defaultElevation = 0.dp),
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            DeviceProfileHeader(
                checked = uiState.deviceProfileEnabled,
                onToggle = { onToggle(SpoofType.DEVICE_PROFILE, it) },
            )
            if (uiState.deviceProfileEnabled) {
                DeviceProfileValueRow(
                    value = uiState.deviceProfileDisplayValue,
                    onRegenerate = { onRegenerate(SpoofType.DEVICE_PROFILE) },
                )
                DeviceProfileDetails(preset = uiState.currentPreset)
            }
        }
    }
}

@Composable
private fun DeviceProfileHeader(checked: Boolean, onToggle: (Boolean) -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = SpoofType.DEVICE_PROFILE.displayName,
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.Medium,
            modifier = Modifier.weight(1f),
        )
        ExpressiveSwitch(checked = checked, onCheckedChange = onToggle)
    }
}

@Composable
private fun DeviceProfileValueRow(value: String, onRegenerate: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = value.ifEmpty { "Not set" },
            style = MaterialTheme.typography.bodySmall,
            fontFamily = FontFamily.Monospace,
            color = MaterialTheme.colorScheme.primary,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
        CompactExpressiveIconButton(
            onClick = onRegenerate,
            icon = Icons.Filled.Refresh,
            contentDescription = stringResource(id = R.string.action_regenerate),
            tint = MaterialTheme.colorScheme.primary,
        )
    }
}

@Composable
private fun DeviceProfileDetails(preset: DeviceProfilePreset?) {
    AnimatedVisibility(
        visible = preset != null,
        enter = expandVertically(animationSpec = spring()) + fadeIn(),
        exit = shrinkVertically(animationSpec = spring()) + fadeOut(),
    ) {
        preset?.let { DeviceProfileDetailsRows(preset = it) }
    }
}

@Composable
private fun DeviceProfileDetailsRows(preset: DeviceProfilePreset) {
    Column(
        modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        ReadOnlyValueRow(
            label = stringResource(id = R.string.group_spoofing_manufacturer),
            value = preset.manufacturer,
        )
        ReadOnlyValueRow(
            label = stringResource(id = R.string.group_spoofing_brand),
            value = preset.brand,
        )
        ReadOnlyValueRow(
            label = stringResource(id = R.string.group_spoofing_model),
            value = preset.model,
        )
        ReadOnlyValueRow(
            label = stringResource(id = R.string.group_spoofing_device),
            value = preset.device,
        )
        ReadOnlyValueRow(
            label = stringResource(id = R.string.group_spoofing_product),
            value = preset.product,
        )
        ReadOnlyValueRow(
            label = stringResource(id = R.string.group_spoofing_board),
            value = preset.board,
        )
        ReadOnlyValueRow(
            label = stringResource(id = R.string.group_spoofing_fingerprint),
            value = preset.fingerprint,
        )
        if (preset.securityPatch.isNotBlank()) {
            ReadOnlyValueRow(
                label = stringResource(id = R.string.group_spoofing_security_patch),
                value = preset.securityPatch,
            )
        }
    }
}

@Composable
private fun HardwareSpoofItem(
    type: SpoofType,
    value: String,
    isEnabled: Boolean,
    onToggle: (SpoofType, Boolean) -> Unit,
    onRegenerate: (SpoofType) -> Unit,
) {
    IndependentSpoofItem(
        type = type,
        value = value,
        isEnabled = isEnabled,
        onToggle = { enabled -> onToggle(type, enabled) },
        onRegenerate = { onRegenerate(type) },
        modifier = Modifier.fillMaxWidth(),
    )
}
