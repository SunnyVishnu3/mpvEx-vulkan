package com.astrixforge.devicemasker.ui.screens.home

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Android
import androidx.compose.material.icons.outlined.Apps
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.astrixforge.devicemasker.R
import com.astrixforge.devicemasker.ui.components.AppIconCache
import com.astrixforge.devicemasker.ui.components.CachedAppIcon
import com.astrixforge.devicemasker.ui.components.EmptyState
import com.astrixforge.devicemasker.ui.components.expressive.ExpressiveCard
import com.astrixforge.devicemasker.ui.components.expressive.ExpressiveSwitch
import com.astrixforge.devicemasker.ui.components.expressive.SectionHeader
import com.astrixforge.devicemasker.ui.components.rememberAppIconCache
import com.astrixforge.devicemasker.ui.theme.AppMotion
import kotlinx.collections.immutable.ImmutableList

/** Section showing apps currently selected in LSPosed scope. */
@Composable
fun HomeScopedAppsSection(
    scopedApps: ImmutableList<HomeScopedApp>,
    onAppEnabledChange: (HomeScopedApp, Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    var isExpanded by rememberSaveable { mutableStateOf(true) }
    val iconCache = rememberAppIconCache()
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(6.dp)) {
        SectionHeader(
            title = stringResource(R.string.home_scoped_apps),
            isExpanded = isExpanded,
            onExpandChange = { isExpanded = it },
            verticalPadding = 2.dp,
        )
        AnimatedVisibility(
            visible = isExpanded,
            enter = expandVertically(animationSpec = AppMotion.ReducedIntSize),
            exit = shrinkVertically(animationSpec = AppMotion.ReducedIntSize),
        ) {
            if (scopedApps.isEmpty()) {
                EmptyState(
                    icon = Icons.Outlined.Apps,
                    title = stringResource(R.string.home_scoped_apps_empty),
                    subtitle = stringResource(R.string.home_scoped_apps_empty_subtitle),
                )
            } else {
                LazyColumn(
                    modifier = Modifier.heightIn(max = SCOPED_APPS_MAX_HEIGHT),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    items(
                        items = scopedApps,
                        key = { it.packageName },
                        contentType = { "home_scoped_app" },
                    ) { app ->
                        HomeScopedAppCard(
                            app = app,
                            onAppEnabledChange = onAppEnabledChange,
                            iconCache = iconCache,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun HomeScopedAppCard(
    app: HomeScopedApp,
    onAppEnabledChange: (HomeScopedApp, Boolean) -> Unit,
    iconCache: AppIconCache,
    modifier: Modifier = Modifier,
) {
    val enabledState = stringResource(R.string.group_app_selected_state)
    val disabledState = stringResource(R.string.group_app_unselected_state)

    ExpressiveCard(
        modifier =
            modifier
                .fillMaxWidth()
                .graphicsLayer { alpha = homeScopedAppCardAlpha(app.isGloballyEnabled) }
                .semantics {
                    role = Role.Switch
                    stateDescription = if (app.isGloballyEnabled) enabledState else disabledState
                },
        onClick = { onAppEnabledChange(app, !app.isGloballyEnabled) },
        shape = MaterialTheme.shapes.small,
        containerColor = MaterialTheme.colorScheme.surface,
        elevation = CardDefaults.elevatedCardElevation(defaultElevation = 0.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(12.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            CachedAppIcon(
                packageName = app.packageName,
                label = app.label,
                iconCache = iconCache,
                fallback = { iconModifier -> HomeScopedAppIconFallback(modifier = iconModifier) },
            )
            HomeScopedAppDetails(app = app, modifier = Modifier.weight(1f))
            ExpressiveSwitch(
                checked = app.isGloballyEnabled,
                onCheckedChange = { enabled -> onAppEnabledChange(app, enabled) },
            )
        }
    }
}

@Composable
private fun HomeScopedAppDetails(app: HomeScopedApp, modifier: Modifier = Modifier) {
    Column(modifier = modifier) {
        Text(
            text = app.label,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Medium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Text(
            text = app.statusSubtitle(),
            style = MaterialTheme.typography.bodySmall,
            color =
                if (app.status == HomeScopedAppStatus.Enabled) {
                    MaterialTheme.colorScheme.onSurfaceVariant
                } else {
                    MaterialTheme.colorScheme.error
                },
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun HomeScopedApp.statusSubtitle(): String =
    when (status) {
        HomeScopedAppStatus.Enabled -> groupName ?: packageName
        HomeScopedAppStatus.DisabledByApp -> stringResource(R.string.home_scoped_app_disabled)
        HomeScopedAppStatus.DisabledByGroup ->
            stringResource(R.string.home_scoped_app_group_disabled, groupName.orEmpty())
        HomeScopedAppStatus.NotConfigured -> stringResource(R.string.home_scoped_app_not_configured)
    }

@Composable
private fun HomeScopedAppIconFallback(modifier: Modifier = Modifier) {
    Box(
        modifier =
            modifier
                .size(40.dp)
                .clip(RoundedCornerShape(8.dp))
                .background(MaterialTheme.colorScheme.secondaryContainer),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = Icons.Outlined.Android,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSecondaryContainer,
            modifier = Modifier.size(24.dp),
        )
    }
}

internal fun homeScopedAppCardAlpha(isGloballyEnabled: Boolean): Float =
    if (isGloballyEnabled) ENABLED_SCOPED_APP_CARD_ALPHA else DISABLED_SCOPED_APP_CARD_ALPHA

private const val ENABLED_SCOPED_APP_CARD_ALPHA = 1f
private const val DISABLED_SCOPED_APP_CARD_ALPHA = 0.62f
private val SCOPED_APPS_MAX_HEIGHT = 420.dp
