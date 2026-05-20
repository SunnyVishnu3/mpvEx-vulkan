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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.astrixforge.devicemasker.R
import com.astrixforge.devicemasker.common.SpoofGroup
import com.astrixforge.devicemasker.common.SpoofType
import com.astrixforge.devicemasker.ui.components.expressive.ExpressiveCard
import com.astrixforge.devicemasker.ui.components.expressive.ExpressiveOutlinedCard
import com.astrixforge.devicemasker.ui.components.expressive.ExpressiveSwitch
import com.astrixforge.devicemasker.ui.components.sheet.TimezonePickerSheet
import com.astrixforge.devicemasker.ui.screens.groupspoofing.items.IndependentSpoofItem

/**
 * Content layout for Location category - mirrors SIM Card design pattern exactly.
 *
 * UI Structure:
 * 1. Location Selection Card - "Choose Location" switch, Timezone picker, Locale display
 * 2. Latitude/Longitude - Each has its own Switch + Regenerate (fully independent)
 */
@Composable
fun LocationCategoryContent(
    group: SpoofGroup?,
    onToggle: (SpoofType, Boolean) -> Unit,
    onRegenerate: (SpoofType) -> Unit,
    @Suppress("UNUSED_PARAMETER") onRegenerateLocation: () -> Unit,
    modifier: Modifier = Modifier,
    timezoneSelected: (String) -> Unit = {},
) {
    var showTimezoneSheet by rememberSaveable { mutableStateOf(false) }
    val uiState = group.toLocationUiState()

    if (showTimezoneSheet) {
        TimezonePickerSheet(
            selectedTimezone = uiState.timezoneValue,
            timezoneSelected = { timezone ->
                timezoneSelected(timezone)
                showTimezoneSheet = false
            },
            onDismiss = { showTimezoneSheet = false },
        )
    }

    Column(modifier = modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        LocationSelectionCard(
            uiState = uiState,
            onToggle = onToggle,
            onTimezoneClick = { showTimezoneSheet = true },
        )
        LocationSpoofItem(
            type = SpoofType.LOCATION_LATITUDE,
            value = uiState.latValue,
            isEnabled = uiState.latEnabled,
            onToggle = onToggle,
            onRegenerate = onRegenerate,
        )
        LocationSpoofItem(
            type = SpoofType.LOCATION_LONGITUDE,
            value = uiState.longValue,
            isEnabled = uiState.longEnabled,
            onToggle = onToggle,
            onRegenerate = onRegenerate,
        )
    }
}

private data class LocationUiState(
    val timezoneEnabled: Boolean,
    val timezoneValue: String,
    val localeValue: String,
    val latEnabled: Boolean,
    val latValue: String,
    val longEnabled: Boolean,
    val longValue: String,
)

private fun SpoofGroup?.toLocationUiState(): LocationUiState =
    LocationUiState(
        timezoneEnabled = this?.isTypeEnabled(SpoofType.TIMEZONE) ?: false,
        timezoneValue = this?.getValue(SpoofType.TIMEZONE).orEmpty(),
        localeValue = this?.getValue(SpoofType.LOCALE).orEmpty(),
        latEnabled = this?.isTypeEnabled(SpoofType.LOCATION_LATITUDE) ?: false,
        latValue = this?.getValue(SpoofType.LOCATION_LATITUDE).orEmpty(),
        longEnabled = this?.isTypeEnabled(SpoofType.LOCATION_LONGITUDE) ?: false,
        longValue = this?.getValue(SpoofType.LOCATION_LONGITUDE).orEmpty(),
    )

@Composable
private fun LocationSelectionCard(
    uiState: LocationUiState,
    onToggle: (SpoofType, Boolean) -> Unit,
    onTimezoneClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    ExpressiveCard(
        onClick = { /* Selection action feedback */ },
        modifier = modifier.fillMaxWidth(),
        containerColor = MaterialTheme.colorScheme.surface,
        shape = MaterialTheme.shapes.medium,
        elevation = CardDefaults.elevatedCardElevation(defaultElevation = 0.dp),
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            LocationToggleHeader(
                checked = uiState.timezoneEnabled,
                onToggle = { enabled ->
                    onToggle(SpoofType.TIMEZONE, enabled)
                    onToggle(SpoofType.LOCALE, enabled)
                },
            )
            LocationDetails(uiState = uiState, onTimezoneClick = onTimezoneClick)
        }
    }
}

@Composable
private fun LocationToggleHeader(checked: Boolean, onToggle: (Boolean) -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = stringResource(id = R.string.label_choose_location),
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurface,
        )
        ExpressiveSwitch(checked = checked, onCheckedChange = onToggle)
    }
}

@Composable
private fun LocationDetails(uiState: LocationUiState, onTimezoneClick: () -> Unit) {
    AnimatedVisibility(
        visible = uiState.timezoneEnabled,
        enter = expandVertically(animationSpec = spring()) + fadeIn(),
        exit = shrinkVertically(animationSpec = spring()) + fadeOut(),
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
            TimezoneRow(value = uiState.timezoneValue, onTimezoneClick = onTimezoneClick)
            LocaleRow(value = uiState.localeValue)
        }
    }
}

@Composable
private fun TimezoneRow(value: String, onTimezoneClick: () -> Unit) {
    PickerLikeRow(label = stringResource(id = R.string.group_spoofing_timezone)) {
        ExpressiveOutlinedCard(
            onClick = onTimezoneClick,
            modifier = Modifier.width(200.dp),
            shape = RoundedCornerShape(12.dp),
        ) {
            PickerValueButton(value = value)
        }
    }
}

@Composable
private fun LocaleRow(value: String) {
    PickerLikeRow(label = stringResource(id = R.string.group_spoofing_locale)) {
        ExpressiveOutlinedCard(
            enabled = false,
            onClick = { /* No action - read only */ },
            modifier = Modifier.width(200.dp),
            shape = RoundedCornerShape(12.dp),
        ) {
            PickerValueButton(value = value, showArrow = false)
        }
    }
}

@Composable
private fun PickerLikeRow(label: String, content: @Composable () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(text = label, style = MaterialTheme.typography.bodyMedium)
        content()
    }
}

@Composable
private fun PickerValueButton(value: String, showArrow: Boolean = true) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
        horizontalArrangement = if (showArrow) Arrangement.SpaceBetween else Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = value.ifEmpty { stringResource(id = R.string.group_spoofing_not_set) },
            style = MaterialTheme.typography.bodyMedium,
            modifier = if (showArrow) Modifier.weight(1f) else Modifier,
        )
        if (showArrow) {
            Icon(
                imageVector = Icons.Filled.ChevronRight,
                contentDescription = null,
                modifier = Modifier.size(20.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun LocationSpoofItem(
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
