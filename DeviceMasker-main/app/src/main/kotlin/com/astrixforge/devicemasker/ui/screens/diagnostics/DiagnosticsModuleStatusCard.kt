package com.astrixforge.devicemasker.ui.screens.diagnostics

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.astrixforge.devicemasker.R
import com.astrixforge.devicemasker.ui.components.expressive.ExpressiveCard
import com.astrixforge.devicemasker.ui.theme.statusActive
import com.astrixforge.devicemasker.ui.theme.statusInactive
import com.astrixforge.devicemasker.ui.theme.statusOnActive
import com.astrixforge.devicemasker.ui.theme.statusOnInactive

@Composable
internal fun ModuleStatusCard(isXposedActive: Boolean, modifier: Modifier = Modifier) {
    val statusColor =
        if (isXposedActive) MaterialTheme.colorScheme.statusActive
        else MaterialTheme.colorScheme.statusInactive
    val onStatusColor =
        if (isXposedActive) MaterialTheme.colorScheme.statusOnActive
        else MaterialTheme.colorScheme.statusOnInactive

    ExpressiveCard(
        onClick = { /* Status card click */ },
        modifier = modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.large,
        containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
        elevation = CardDefaults.elevatedCardElevation(defaultElevation = 0.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            ModuleStatusIcon(statusColor = statusColor, onStatusColor = onStatusColor)
            Spacer(modifier = Modifier.width(16.dp))
            ModuleStatusText(isXposedActive = isXposedActive, statusColor = statusColor)
        }
    }
}

@Composable
private fun ModuleStatusIcon(statusColor: Color, onStatusColor: Color) {
    Box(
        modifier = Modifier.size(48.dp).background(color = statusColor, shape = CircleShape),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = Icons.Default.Shield,
            contentDescription = null,
            tint = onStatusColor,
            modifier = Modifier.size(28.dp),
        )
    }
}

@Composable
private fun ModuleStatusText(isXposedActive: Boolean, statusColor: Color) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Text(
            text = moduleStatusTitle(isXposedActive),
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
            color = statusColor,
        )
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = moduleStatusDescription(isXposedActive),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun moduleStatusTitle(isXposedActive: Boolean): String =
    if (isXposedActive) {
        stringResource(id = R.string.module_active)
    } else {
        stringResource(id = R.string.module_inactive)
    }

@Composable
private fun moduleStatusDescription(isXposedActive: Boolean): String =
    if (isXposedActive) {
        stringResource(id = R.string.diagnostics_module_active_desc)
    } else {
        stringResource(id = R.string.diagnostics_module_inactive_desc)
    }
