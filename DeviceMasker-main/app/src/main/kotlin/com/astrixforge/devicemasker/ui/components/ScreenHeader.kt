package com.astrixforge.devicemasker.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.astrixforge.devicemasker.ui.theme.DeviceMaskerTheme

/**
 * Consistent screen header for main navigation destinations.
 *
 * Displays a headlineMedium title with optional action buttons on the right. Used as the first item
 * in LazyColumn for main screens.
 *
 * @param title The screen title
 * @param modifier Optional modifier
 * @param actions Optional composable for action buttons (e.g., import/export)
 */
@Composable
fun ScreenHeader(
    title: String,
    modifier: Modifier = Modifier,
    actions: @Composable RowScope.() -> Unit = {},
) {
    Row(
        modifier = modifier.fillMaxWidth().padding(vertical = 8.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurface,
        )
        Row(horizontalArrangement = Arrangement.spacedBy(4.dp), content = actions)
    }
}

// ═══════════════════════════════════════════════════════════
// Previews
// ═══════════════════════════════════════════════════════════

@Preview(showBackground = true, backgroundColor = 0xFF121212)
@Composable
private fun ScreenHeaderPreview() {
    DeviceMaskerTheme { ScreenHeader(title = "Settings") }
}

@Preview(showBackground = true, backgroundColor = 0xFF121212)
@Composable
private fun ScreenHeaderWithActionsPreview() {
    DeviceMaskerTheme {
        ScreenHeader(
            title = "Groups",
            actions = { Text("Action", color = MaterialTheme.colorScheme.primary) },
        )
    }
}
