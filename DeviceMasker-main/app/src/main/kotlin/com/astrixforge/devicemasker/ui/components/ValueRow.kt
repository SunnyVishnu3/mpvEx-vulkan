package com.astrixforge.devicemasker.ui.components

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.astrixforge.devicemasker.ui.theme.DeviceMaskerTheme

/**
 * Displays a label and value in a vertical column layout.
 *
 * Used for displaying key-value pairs in diagnostics, details screens, etc.
 *
 * @param label The label text displayed above the value
 * @param value The value text to display
 * @param modifier Optional modifier
 * @param valueStyle Text style for the value (defaults to bodyMedium)
 * @param maxLines Maximum lines for value text
 */
@Composable
fun LabeledValue(
    label: String,
    value: String,
    modifier: Modifier = Modifier,
    valueStyle: TextStyle = MaterialTheme.typography.bodyMedium,
    maxLines: Int = 2,
) {
    Column(modifier = modifier) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(modifier = Modifier.height(2.dp))
        Text(
            text = value,
            style = valueStyle,
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = maxLines,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

/**
 * Displays a label and value in a horizontal row layout.
 *
 * The label takes fixed space while value fills remaining width.
 *
 * @param label The label text
 * @param value The value text
 * @param modifier Optional modifier
 */
@Composable
fun ValueRow(label: String, value: String, modifier: Modifier = Modifier) {
    Row(modifier = modifier.fillMaxWidth()) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.weight(LABEL_WEIGHT),
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.weight(VALUE_WEIGHT),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

private const val LABEL_WEIGHT = 0.4f
private const val VALUE_WEIGHT = 0.6f

// ═══════════════════════════════════════════════════════════
// Previews
// ═══════════════════════════════════════════════════════════

@Preview(showBackground = true, backgroundColor = 0xFF121212)
@Composable
private fun LabeledValuePreview() {
    DeviceMaskerTheme { LabeledValue(label = "IMEI", value = "358673912845672") }
}

@Preview(showBackground = true, backgroundColor = 0xFF121212)
@Composable
private fun ValueRowPreview() {
    DeviceMaskerTheme { ValueRow(label = "Serial Number", value = "AB1234567890") }
}
