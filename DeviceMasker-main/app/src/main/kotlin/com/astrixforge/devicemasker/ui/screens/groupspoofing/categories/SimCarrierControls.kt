package com.astrixforge.devicemasker.ui.screens.groupspoofing.categories

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.astrixforge.devicemasker.R
import com.astrixforge.devicemasker.common.SpoofType
import com.astrixforge.devicemasker.common.models.Carrier
import com.astrixforge.devicemasker.common.models.Country
import com.astrixforge.devicemasker.ui.components.expressive.ExpressiveOutlinedCard
import com.astrixforge.devicemasker.ui.screens.groupspoofing.items.IndependentSpoofItem
import com.astrixforge.devicemasker.ui.screens.groupspoofing.items.ReadOnlyValueRow

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun SimCarrierControls(
    uiState: SimCardUiState,
    selectedCountryIso: String,
    carrierOptions: CarrierOptions,
    currentCarrier: Carrier?,
    currentCarrierMccMnc: String?,
    onCountryClick: () -> Unit,
    onCarrierChange: (Carrier) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(16.dp)) {
        CountrySelectorRow(selectedCountryIso = selectedCountryIso, onCountryClick = onCountryClick)
        CarrierSelectorRow(
            carrierOptions = carrierOptions,
            currentCarrier = currentCarrier,
            currentCarrierMccMnc = currentCarrierMccMnc,
            onCarrierChange = onCarrierChange,
        )
        CarrierInfoRows(uiState = uiState)
    }
}

@Composable
private fun CountrySelectorRow(selectedCountryIso: String, onCountryClick: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = stringResource(id = R.string.group_spoofing_country),
            style = MaterialTheme.typography.bodyMedium,
        )
        CountrySelectorButton(
            selectedCountryIso = selectedCountryIso,
            onCountryClick = onCountryClick,
        )
    }
}

@Composable
private fun CountrySelectorButton(selectedCountryIso: String, onCountryClick: () -> Unit) {
    val selectedCountry = Country.getByIso(selectedCountryIso)
    val selectedCountryLabel =
        "${selectedCountry?.emoji ?: "🌍"} " + (selectedCountry?.name ?: selectedCountryIso)

    ExpressiveOutlinedCard(
        onClick = onCountryClick,
        modifier = Modifier.width(200.dp),
        shape = RoundedCornerShape(12.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = selectedCountryLabel,
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.weight(1f),
            )
            Icon(
                imageVector = Icons.Filled.ChevronRight,
                contentDescription = null,
                modifier = Modifier.size(20.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun CarrierSelectorRow(
    carrierOptions: CarrierOptions,
    currentCarrier: Carrier?,
    currentCarrierMccMnc: String?,
    onCarrierChange: (Carrier) -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = stringResource(id = R.string.group_spoofing_carrier),
            style = MaterialTheme.typography.bodyMedium,
        )
        CarrierDropdown(
            carrierOptions = carrierOptions,
            currentCarrier = currentCarrier,
            currentCarrierMccMnc = currentCarrierMccMnc,
            onCarrierChange = onCarrierChange,
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun CarrierDropdown(
    carrierOptions: CarrierOptions,
    currentCarrier: Carrier?,
    currentCarrierMccMnc: String?,
    onCarrierChange: (Carrier) -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }

    ExposedDropdownMenuBox(
        expanded = expanded,
        onExpandedChange = { expanded = it },
        modifier = Modifier.width(200.dp),
    ) {
        CarrierDropdownButton(
            currentCarrier = currentCarrier,
            expanded = expanded,
            onExpand = { expanded = true },
            modifier = Modifier.menuAnchor().fillMaxWidth(),
        )
        ExposedDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            carrierOptions.items.forEach { carrier ->
                CarrierDropdownItem(
                    carrier = carrier,
                    isSelected = carrier.mccMnc == currentCarrierMccMnc,
                    onCarrierSelect = {
                        onCarrierChange(carrier)
                        expanded = false
                    },
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun CarrierDropdownButton(
    currentCarrier: Carrier?,
    expanded: Boolean,
    onExpand: () -> Unit,
    modifier: Modifier = Modifier,
) {
    ExpressiveOutlinedCard(
        onClick = onExpand,
        modifier = modifier,
        shape = RoundedCornerShape(12.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = currentCarrier?.displayName ?: "Select carrier",
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.weight(1f),
            )
            ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded)
        }
    }
}

@Composable
private fun CarrierDropdownItem(
    carrier: Carrier,
    isSelected: Boolean,
    onCarrierSelect: () -> Unit,
) {
    DropdownMenuItem(
        text = { Text(carrier.displayName) },
        onClick = onCarrierSelect,
        leadingIcon = if (isSelected) ({ Icon(Icons.Filled.Check, null) }) else null,
    )
}

@Composable
private fun CarrierInfoRows(uiState: SimCardUiState) {
    Column(
        modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        ReadOnlyValueRow(label = "SIM Country", value = uiState.simCountryValue)
        ReadOnlyValueRow(label = "Network Country", value = uiState.networkCountryValue)
        ReadOnlyValueRow(label = "MCC/MNC", value = uiState.mccMncValue)
        ReadOnlyValueRow(label = "Carrier Name", value = uiState.carrierNameValue)
        ReadOnlyValueRow(label = "SIM Operator", value = uiState.simOperatorValue)
        ReadOnlyValueRow(label = "Network Operator", value = uiState.networkOperatorValue)
    }
}

@Composable
internal fun RegeneratableSimValues(
    uiState: SimCardUiState,
    onToggle: (SpoofType, Boolean) -> Unit,
    onRegenerate: (SpoofType) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        SimSpoofItem(
            type = SpoofType.PHONE_NUMBER,
            value = uiState.phoneValue,
            isEnabled = uiState.phoneEnabled,
            onToggle = onToggle,
            onRegenerate = onRegenerate,
        )
        SimSpoofItem(
            type = SpoofType.IMSI,
            value = uiState.imsiValue,
            isEnabled = uiState.imsiEnabled,
            onToggle = onToggle,
            onRegenerate = onRegenerate,
        )
        SimSpoofItem(
            type = SpoofType.ICCID,
            value = uiState.iccidValue,
            isEnabled = uiState.iccidEnabled,
            onToggle = onToggle,
            onRegenerate = onRegenerate,
        )
    }
}

@Composable
private fun SimSpoofItem(
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
