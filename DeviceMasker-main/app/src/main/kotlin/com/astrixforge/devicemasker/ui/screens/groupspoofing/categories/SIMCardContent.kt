package com.astrixforge.devicemasker.ui.screens.groupspoofing.categories

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
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
import com.astrixforge.devicemasker.common.models.Carrier
import com.astrixforge.devicemasker.ui.components.expressive.ExpressiveCard
import com.astrixforge.devicemasker.ui.components.expressive.ExpressiveSwitch
import com.astrixforge.devicemasker.ui.components.sheet.CountryPickerSheet

/**
 * Content layout for SIM Card category with carrier-driven flow.
 *
 * UI Structure:
 * 1. Carrier Selection Card - Country picker + Carrier dropdown
 * 2. Locked Values Section - Read-only derived values (no switch/regenerate)
 * 3. Regeneratable Values Section - Phone, IMSI, ICCID with regenerate buttons
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SIMCardCategoryContent(
    group: SpoofGroup?,
    onToggle: (SpoofType, Boolean) -> Unit,
    onRegenerate: (SpoofType) -> Unit,
    modifier: Modifier = Modifier,
    onCarrierChange: (Carrier) -> Unit = {},
) {
    var showCountryPickerSheet by rememberSaveable { mutableStateOf(false) }

    val currentCarrierMccMnc = group?.selectedCarrierMccMnc
    val currentCarrier =
        remember(currentCarrierMccMnc) { currentCarrierMccMnc?.let { Carrier.getByMccMnc(it) } }

    var selectedCountryIso by
        remember(currentCarrier) { mutableStateOf(currentCarrier?.countryIso ?: "IN") }

    val carrierOptions =
        remember(selectedCountryIso) { CarrierOptions(Carrier.getByCountry(selectedCountryIso)) }
    val uiState = group.toSimCardUiState()

    if (showCountryPickerSheet) {
        CountryPickerSheet(
            selectedCountryIso = selectedCountryIso,
            countrySelected = { country ->
                selectedCountryIso = country.iso
                showCountryPickerSheet = false
                // Auto-select first carrier from new country
                val newCarriers = Carrier.getByCountry(country.iso)
                if (newCarriers.isNotEmpty()) {
                    onCarrierChange(newCarriers.first())
                }
            },
            onDismiss = { showCountryPickerSheet = false },
        )
    }

    Column(modifier = modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        SimCarrierCard(
            uiState = uiState,
            selectedCountryIso = selectedCountryIso,
            carrierOptions = carrierOptions,
            currentCarrier = currentCarrier,
            currentCarrierMccMnc = currentCarrierMccMnc,
            onToggle = onToggle,
            onCountryClick = { showCountryPickerSheet = true },
            onCarrierChange = onCarrierChange,
        )
        RegeneratableSimValues(uiState = uiState, onToggle = onToggle, onRegenerate = onRegenerate)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SimCarrierCard(
    uiState: SimCardUiState,
    selectedCountryIso: String,
    carrierOptions: CarrierOptions,
    currentCarrier: Carrier?,
    currentCarrierMccMnc: String?,
    onToggle: (SpoofType, Boolean) -> Unit,
    onCountryClick: () -> Unit,
    onCarrierChange: (Carrier) -> Unit,
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
            SimToggleHeader(isSimEnabled = uiState.isSimEnabled, onToggle = onToggle)
            if (uiState.isSimEnabled) {
                SimCarrierControls(
                    uiState = uiState,
                    selectedCountryIso = selectedCountryIso,
                    carrierOptions = carrierOptions,
                    currentCarrier = currentCarrier,
                    currentCarrierMccMnc = currentCarrierMccMnc,
                    onCountryClick = onCountryClick,
                    onCarrierChange = onCarrierChange,
                )
            }
        }
    }
}

@Composable
private fun SimToggleHeader(isSimEnabled: Boolean, onToggle: (SpoofType, Boolean) -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = stringResource(id = R.string.label_choose_sim),
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurface,
        )
        ExpressiveSwitch(
            checked = isSimEnabled,
            onCheckedChange = { enabled -> CarrierSpoofTypes.forEach { onToggle(it, enabled) } },
        )
    }
}
