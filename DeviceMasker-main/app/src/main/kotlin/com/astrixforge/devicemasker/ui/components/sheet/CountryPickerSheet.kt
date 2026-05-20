package com.astrixforge.devicemasker.ui.components.sheet

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.astrixforge.devicemasker.R
import com.astrixforge.devicemasker.common.models.Country
import com.astrixforge.devicemasker.ui.components.AppModalBottomSheet
import com.astrixforge.devicemasker.ui.components.ClearFocusOnImeDismiss

@Immutable private data class CountryOptions(val items: List<Country>)

/**
 * Searchable modal sheet for selecting a country.
 *
 * @param selectedCountryIso Currently selected country ISO code
 * @param countrySelected Called when a country is selected
 * @param onDismiss Called when sheet is dismissed
 */
@Composable
fun CountryPickerSheet(
    selectedCountryIso: String?,
    countrySelected: (Country) -> Unit,
    onDismiss: () -> Unit,
) {
    var searchQuery by rememberSaveable { mutableStateOf("") }
    val selectedState = stringResource(R.string.picker_selected_state)
    val notSelectedState = stringResource(R.string.picker_not_selected_state)

    AppModalBottomSheet(
        onDismiss = onDismiss,
        title = stringResource(R.string.content_country_picker),
    ) {
        CountryPickerContent(
            searchQuery = searchQuery,
            queryChanged = { searchQuery = it },
            selectedCountryIso = selectedCountryIso,
            selectedState = selectedState,
            notSelectedState = notSelectedState,
            countrySelected = countrySelected,
        )
    }
}

@Composable
private fun CountryPickerContent(
    searchQuery: String,
    queryChanged: (String) -> Unit,
    selectedCountryIso: String?,
    selectedState: String,
    notSelectedState: String,
    countrySelected: (Country) -> Unit,
    modifier: Modifier = Modifier,
) {
    val countries = remember(searchQuery) { CountryOptions(Country.search(searchQuery)) }

    Column(modifier = modifier) {
        CountrySearchField(searchQuery = searchQuery, queryChanged = queryChanged)
        Spacer(modifier = Modifier.height(12.dp))
        CountryList(
            countries = countries,
            selectedCountryIso = selectedCountryIso,
            selectedState = selectedState,
            notSelectedState = notSelectedState,
            countrySelected = countrySelected,
        )
    }
}

@Composable
private fun CountrySearchField(
    searchQuery: String,
    queryChanged: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val focusManager = LocalFocusManager.current
    var isSearchFocused by remember { mutableStateOf(false) }

    ClearFocusOnImeDismiss(isFocused = isSearchFocused, clearFocus = { focusManager.clearFocus() })

    OutlinedTextField(
        value = searchQuery,
        onValueChange = queryChanged,
        modifier =
            modifier.fillMaxWidth().onFocusChanged { focusState ->
                isSearchFocused = focusState.isFocused
            },
        placeholder = { Text(stringResource(R.string.picker_search_country_hint)) },
        leadingIcon = { Icon(imageVector = Icons.Filled.Search, contentDescription = null) },
        singleLine = true,
    )
}

@Composable
private fun CountryList(
    countries: CountryOptions,
    selectedCountryIso: String?,
    selectedState: String,
    notSelectedState: String,
    countrySelected: (Country) -> Unit,
    modifier: Modifier = Modifier,
) {
    LazyColumn(
        modifier = modifier.fillMaxWidth().heightIn(max = 420.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        items(countries.items, key = { it.iso }) { country ->
            val isSelected = country.iso == selectedCountryIso
            CountryRow(
                country = country,
                isSelected = isSelected,
                stateDescription = if (isSelected) selectedState else notSelectedState,
                countrySelected = countrySelected,
            )

            if (country != countries.items.last()) {
                CountryDivider()
            }
        }
    }
}

@Composable
private fun CountryRow(
    country: Country,
    isSelected: Boolean,
    stateDescription: String,
    countrySelected: (Country) -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier =
            modifier
                .fillMaxWidth()
                .semantics {
                    role = Role.Button
                    this.stateDescription = stateDescription
                }
                .clickable { countrySelected(country) }
                .padding(vertical = 12.dp, horizontal = 8.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        CountryLabel(country = country, isSelected = isSelected)
        SelectedIcon(isSelected = isSelected)
    }
}

@Composable
private fun CountryLabel(country: Country, isSelected: Boolean, modifier: Modifier = Modifier) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier,
    ) {
        Text(text = country.emoji, style = MaterialTheme.typography.titleLarge)
        Column {
            Text(
                text = country.name,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
            )
            Text(
                text = "+${country.phoneCode}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun SelectedIcon(isSelected: Boolean, modifier: Modifier = Modifier) {
    if (isSelected) {
        Icon(
            imageVector = Icons.Filled.Check,
            contentDescription = null,
            modifier = modifier.clearAndSetSemantics {},
            tint = MaterialTheme.colorScheme.primary,
        )
    }
}

@Composable
private fun CountryDivider(modifier: Modifier = Modifier) {
    HorizontalDivider(
        modifier = modifier.padding(horizontal = 8.dp),
        color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f),
    )
}
