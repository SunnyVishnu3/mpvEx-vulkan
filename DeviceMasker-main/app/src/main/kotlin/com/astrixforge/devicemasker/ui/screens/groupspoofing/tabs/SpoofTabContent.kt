package com.astrixforge.devicemasker.ui.screens.groupspoofing.tabs

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.astrixforge.devicemasker.R
import com.astrixforge.devicemasker.common.SpoofGroup
import com.astrixforge.devicemasker.common.SpoofType
import com.astrixforge.devicemasker.common.models.Carrier
import com.astrixforge.devicemasker.ui.components.expressive.ExpressiveCard
import com.astrixforge.devicemasker.ui.screens.groupspoofing.categories.CategorySection
import com.astrixforge.devicemasker.ui.screens.groupspoofing.model.UIDisplayCategory
import com.astrixforge.devicemasker.ui.theme.DeviceMaskerTheme

/**
 * Spoof Values tab content for group spoofing screen.
 *
 * Shows all spoof categories (SIM, Device, Network, Advertising, Location) organized as expandable
 * sections.
 */
@Suppress("LongMethod")
@Composable
fun SpoofTabContent(
    group: SpoofGroup?,
    onRegenerate: (SpoofType) -> Unit,
    onRegenerateCategory: (UIDisplayCategory) -> Unit,
    onRegenerateLocation: () -> Unit,
    onToggle: (SpoofType, Boolean) -> Unit,
    onCarrierChange: (Carrier) -> Unit,
    timezoneSelected: (String) -> Unit,
    modifier: Modifier = Modifier,
    initialScrollPosition: Int = 0,
    onScrollPositionChange: (Int) -> Unit = {},
) {
    var expandedCategories by rememberSaveable(group?.id) { mutableStateOf(emptyList<String>()) }
    val listState = rememberLazyListState(initialFirstVisibleItemIndex = initialScrollPosition)
    val currentOnScrollPositionChange by rememberUpdatedState(onScrollPositionChange)

    LaunchedEffect(listState) {
        snapshotFlow { listState.firstVisibleItemIndex }
            .collect { currentOnScrollPositionChange(it) }
    }

    LaunchedEffect(Unit) {
        if (initialScrollPosition > 0) {
            listState.scrollToItem(initialScrollPosition)
        }
    }

    LazyColumn(
        state = listState,
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        // Header info
        item {
            if (group != null) {
                ExpressiveCard(
                    onClick = { /* Header info click */ },
                    modifier = Modifier.fillMaxWidth(),
                    shape = MaterialTheme.shapes.medium,
                    containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
                    elevation = CardDefaults.elevatedCardElevation(defaultElevation = 0.dp),
                ) {
                    Row(
                        modifier = Modifier.padding(12.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            text = stringResource(id = R.string.group_spoofing_desc),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        }

        // Categories - organized by correlation groups
        UIDisplayCategory.entries.forEach { category ->
            item(key = "spoof_${category.name}") {
                val isExpanded = expandedCategories.contains(category.name)

                CategorySection(
                    category = category,
                    group = group,
                    isExpanded = isExpanded,
                    onToggleExpand = {
                        expandedCategories =
                            if (isExpanded) {
                                expandedCategories - category.name
                            } else {
                                expandedCategories + category.name
                            }
                    },
                    onRegenerate = onRegenerate,
                    onRegenerateCategory = { onRegenerateCategory(category) },
                    onRegenerateLocation = onRegenerateLocation,
                    onToggle = onToggle,
                    onCarrierChange = onCarrierChange,
                    timezoneSelected = timezoneSelected,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }

        item { Spacer(modifier = Modifier.height(24.dp)) }
    }
}

// ═══════════════════════════════════════════════════════════
// Previews
// ═══════════════════════════════════════════════════════════

@Preview(showBackground = true, backgroundColor = 0xFF121212)
@Composable
private fun SpoofTabContentEmptyPreview() {
    DeviceMaskerTheme {
        SpoofTabContent(
            group = null,
            onRegenerate = {},
            onRegenerateCategory = {},
            onRegenerateLocation = {},
            onToggle = { _, _ -> },
            onCarrierChange = {},
            timezoneSelected = {},
        )
    }
}

@Preview(showBackground = true, backgroundColor = 0xFF121212)
@Composable
private fun SpoofTabContentPopulatedPreview() {
    DeviceMaskerTheme {
        SpoofTabContent(
            group = SpoofGroup.createNew("Preview Group"),
            onRegenerate = {},
            onRegenerateCategory = {},
            onRegenerateLocation = {},
            onToggle = { _, _ -> },
            onCarrierChange = {},
            timezoneSelected = {},
        )
    }
}
