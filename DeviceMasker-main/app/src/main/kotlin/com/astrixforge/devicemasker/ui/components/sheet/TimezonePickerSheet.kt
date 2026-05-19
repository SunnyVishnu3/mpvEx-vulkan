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
import com.astrixforge.devicemasker.ui.components.AppModalBottomSheet
import com.astrixforge.devicemasker.ui.components.ClearFocusOnImeDismiss

@Immutable private data class TimezoneOptions(val items: List<TimezoneEntry>)

/**
 * Searchable modal sheet for selecting a timezone.
 *
 * @param selectedTimezone Currently selected timezone ID (e.g., "America/New_York")
 * @param timezoneSelected Called when a timezone is selected
 * @param onDismiss Called when sheet is dismissed
 */
@Composable
fun TimezonePickerSheet(
    selectedTimezone: String?,
    timezoneSelected: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    var searchQuery by rememberSaveable { mutableStateOf("") }
    val selectedState = stringResource(R.string.picker_selected_state)
    val notSelectedState = stringResource(R.string.picker_not_selected_state)

    AppModalBottomSheet(
        onDismiss = onDismiss,
        title = stringResource(R.string.content_timezone_picker),
    ) {
        TimezonePickerContent(
            searchQuery = searchQuery,
            queryChanged = { searchQuery = it },
            selectedTimezone = selectedTimezone,
            selectedState = selectedState,
            notSelectedState = notSelectedState,
            timezoneSelected = timezoneSelected,
        )
    }
}

@Composable
private fun TimezonePickerContent(
    searchQuery: String,
    queryChanged: (String) -> Unit,
    selectedTimezone: String?,
    selectedState: String,
    notSelectedState: String,
    timezoneSelected: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val timezones = remember(searchQuery) { TimezoneOptions(TimezoneEntry.search(searchQuery)) }

    Column(modifier = modifier) {
        TimezoneSearchField(searchQuery = searchQuery, queryChanged = queryChanged)
        Spacer(modifier = Modifier.height(12.dp))
        TimezoneList(
            timezones = timezones,
            selectedTimezone = selectedTimezone,
            selectedState = selectedState,
            notSelectedState = notSelectedState,
            timezoneSelected = timezoneSelected,
        )
    }
}

@Composable
private fun TimezoneSearchField(
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
        placeholder = { Text(stringResource(R.string.picker_search_timezone_hint)) },
        leadingIcon = { Icon(imageVector = Icons.Filled.Search, contentDescription = null) },
        singleLine = true,
    )
}

@Composable
private fun TimezoneList(
    timezones: TimezoneOptions,
    selectedTimezone: String?,
    selectedState: String,
    notSelectedState: String,
    timezoneSelected: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    LazyColumn(
        modifier = modifier.fillMaxWidth().heightIn(max = 460.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        items(timezones.items, key = { it.id }) { timezone ->
            val isSelected = timezone.id == selectedTimezone
            TimezoneRow(
                timezone = timezone,
                isSelected = isSelected,
                stateDescription = if (isSelected) selectedState else notSelectedState,
                timezoneSelected = timezoneSelected,
            )

            if (timezone != timezones.items.lastOrNull()) {
                TimezoneDivider()
            }
        }
    }
}

@Composable
private fun TimezoneRow(
    timezone: TimezoneEntry,
    isSelected: Boolean,
    stateDescription: String,
    timezoneSelected: (String) -> Unit,
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
                .clickable { timezoneSelected(timezone.id) }
                .padding(vertical = 12.dp, horizontal = 8.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        TimezoneLabel(timezone = timezone, isSelected = isSelected, modifier = Modifier.weight(1f))
        SelectedIcon(isSelected = isSelected)
    }
}

@Composable
private fun TimezoneLabel(
    timezone: TimezoneEntry,
    isSelected: Boolean,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier) {
        Text(
            text = timezone.displayName,
            style = MaterialTheme.typography.bodyLarge,
            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
        )
        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = timezone.offset,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.primary,
            )
            Text(
                text = "•",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.clearAndSetSemantics {},
            )
            Text(
                text = timezone.region,
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
private fun TimezoneDivider(modifier: Modifier = Modifier) {
    HorizontalDivider(
        modifier = modifier.padding(horizontal = 8.dp),
        color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f),
    )
}
