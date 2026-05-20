package com.astrixforge.devicemasker.ui.screens.home

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.outlined.Apps
import androidx.compose.material.icons.outlined.Fingerprint
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.compose.dropUnlessResumed
import androidx.lifecycle.viewmodel.compose.viewModel
import com.astrixforge.devicemasker.R
import com.astrixforge.devicemasker.common.AppConfig
import com.astrixforge.devicemasker.data.models.SpoofGroup
import com.astrixforge.devicemasker.data.repository.ISpoofRepository
import com.astrixforge.devicemasker.ui.components.StatCard
import com.astrixforge.devicemasker.ui.components.expressive.ExpressiveCard
import com.astrixforge.devicemasker.ui.components.expressive.ExpressiveLoadingIndicator
import com.astrixforge.devicemasker.ui.components.expressive.ExpressivePullToRefresh
import com.astrixforge.devicemasker.ui.components.expressive.HeroStatusIndicator
import com.astrixforge.devicemasker.ui.components.expressive.QuickAction
import com.astrixforge.devicemasker.ui.components.expressive.QuickActionGroup
import com.astrixforge.devicemasker.ui.components.expressive.StatusIndicator
import com.astrixforge.devicemasker.ui.components.expressive.animatedRoundedCornerShape
import com.astrixforge.devicemasker.ui.navigation.homeViewModelFactory
import com.astrixforge.devicemasker.ui.theme.AppMotion
import com.astrixforge.devicemasker.ui.theme.DeviceMaskerTheme
import com.astrixforge.devicemasker.ui.theme.statusActive
import com.astrixforge.devicemasker.ui.theme.statusInactive
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.ImmutableMap
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.persistentMapOf

/**
 * Home screen displaying module status and quick stats.
 *
 * Uses MVVM pattern with HomeViewModel for state management.
 *
 * Shows:
 * - Module active/inactive status with animated indicator
 * - Group dropdown selector
 * - Current group summary with protected apps count
 * - Quick stats (protected apps, masked identifiers)
 * - Quick actions
 *
 * @param viewModel The HomeViewModel for state and actions
 * @param onNavigateToSpoof Callback to navigate to spoof screen
 * @param onRegenerateAll Callback to regenerate all values
 * @param onNavigateToGroup Callback to navigate to group spoofing screen with selected group ID
 * @param modifier Optional modifier
 */
@Composable
fun HomeScreen(
    repository: ISpoofRepository,
    onNavigateToSpoof: () -> Unit,
    onRegenerateAll: () -> Unit,
    modifier: Modifier = Modifier,
    onNavigateToGroup: ((String) -> Unit)? = null,
    viewModel: HomeViewModel =
        viewModel(factory = remember(repository) { homeViewModelFactory(repository) }),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val navigateToSpoofWhenResumed = dropUnlessResumed {
        val selectedGroup = state.selectedGroup
        if (onNavigateToGroup != null && selectedGroup != null) {
            onNavigateToGroup(selectedGroup.id)
        } else {
            onNavigateToSpoof()
        }
    }

    HomeScreenContent(
        isXposedActive = state.isXposedActive,
        isModuleEnabled = state.isModuleEnabled,
        groups = state.groups,
        appConfigs = state.appConfigs,
        selectedGroup = state.selectedGroup,
        groupSelected = { group -> viewModel.selectGroup(group.id) },
        enabledAppsCount = state.enabledAppsCount,
        maskedIdentifiersCount = state.maskedIdentifiersCount,
        isScopedAppsRefreshing = state.isScopedAppsRefreshing,
        scopedApps = state.scopedApps,
        onScopedAppEnabledChange = viewModel::setAppEnabled,
        onScopedAppsRefresh = viewModel::refreshScopedApps,
        onNavigateToSpoof = navigateToSpoofWhenResumed,
        onRegenerateAll = { viewModel.regenerateAll { onRegenerateAll() } },
        isLoading = state.isLoading,
        modifier = modifier,
    )
}

/** Stateless content for HomeScreen - enables preview and testing. */
@Composable
fun HomeScreenContent(
    isXposedActive: Boolean,
    isModuleEnabled: Boolean,
    groups: ImmutableList<SpoofGroup>,
    appConfigs: ImmutableMap<String, AppConfig>,
    selectedGroup: SpoofGroup?,
    groupSelected: (SpoofGroup) -> Unit,
    enabledAppsCount: Int,
    maskedIdentifiersCount: Int,
    onNavigateToSpoof: () -> Unit,
    onRegenerateAll: () -> Unit,
    modifier: Modifier = Modifier,
    isScopedAppsRefreshing: Boolean = false,
    scopedApps: ImmutableList<HomeScopedApp> = persistentListOf(),
    onScopedAppEnabledChange: (String, Boolean) -> Unit = { _, _ -> },
    onScopedAppsRefresh: () -> Unit = {},
    isLoading: Boolean = false,
) {
    ExpressivePullToRefresh(
        isRefreshing = isScopedAppsRefreshing,
        onRefresh = onScopedAppsRefresh,
        modifier = modifier,
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            if (isLoading) {
                ExpressiveLoadingIndicator(modifier = Modifier.align(Alignment.Center))
            } else {
                Column(
                    modifier =
                        Modifier.fillMaxSize()
                            .verticalScroll(rememberScrollState())
                            .padding(horizontal = 16.dp, vertical = 8.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    StatusCard(
                        isXposedActive = isXposedActive,
                        isModuleEnabled = isModuleEnabled,
                        modifier = Modifier.fillMaxWidth(),
                    )

                    Spacer(modifier = Modifier.height(20.dp))

                    QuickStatsRow(
                        enabledAppsCount = enabledAppsCount,
                        maskedIdentifiersCount = maskedIdentifiersCount,
                        modifier = Modifier.fillMaxWidth(),
                    )

                    Spacer(modifier = Modifier.height(20.dp))

                    GroupSelectorCard(
                        groups = groups,
                        appConfigs = appConfigs,
                        selectedGroup = selectedGroup,
                        groupSelected = groupSelected,
                        onClick = onNavigateToSpoof,
                        modifier = Modifier.fillMaxWidth(),
                    )

                    Spacer(modifier = Modifier.height(20.dp))

                    QuickActionsSection(
                        onNavigateToSpoof = onNavigateToSpoof,
                        onRegenerateAll = onRegenerateAll,
                        modifier = Modifier.fillMaxWidth(),
                    )

                    Spacer(modifier = Modifier.height(4.dp))

                    HomeScopedAppsSection(
                        scopedApps = scopedApps,
                        onAppEnabledChange = { app, enabled ->
                            onScopedAppEnabledChange(app.packageName, enabled)
                        },
                        modifier = Modifier.fillMaxWidth(),
                    )

                    Spacer(modifier = Modifier.height(24.dp))
                }
            }
        }
    }
}

@Composable
private fun QuickStatsRow(
    enabledAppsCount: Int,
    maskedIdentifiersCount: Int,
    modifier: Modifier = Modifier,
) {
    Row(modifier = modifier, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        StatCard(
            icon = Icons.Outlined.Apps,
            value = enabledAppsCount.toString(),
            label = stringResource(id = R.string.home_protected_apps_label),
            modifier = Modifier.weight(1f),
        )
        StatCard(
            icon = Icons.Outlined.Fingerprint,
            value = maskedIdentifiersCount.toString(),
            label = stringResource(id = R.string.home_masked_ids_label),
            modifier = Modifier.weight(1f),
        )
    }
}

/** Hero status card showing module activation status. */
@Composable
private fun StatusCard(
    isXposedActive: Boolean,
    isModuleEnabled: Boolean,
    modifier: Modifier = Modifier,
) {
    val statusColor by
        animateColorAsState(
            targetValue =
                if (isXposedActive && isModuleEnabled) {
                    MaterialTheme.colorScheme.statusActive
                } else {
                    MaterialTheme.colorScheme.statusInactive
                },
            animationSpec = AppMotion.Effect.Color,
            label = "statusColor",
        )

    val scale by
        animateFloatAsState(
            targetValue = if (isXposedActive && isModuleEnabled) 1f else 0.95f,
            animationSpec =
                if (AppMotion.shouldReduceMotion()) {
                    AppMotion.ReducedAlpha
                } else {
                    AppMotion.Spatial.Expressive
                },
            label = "cardScale",
        )

    val statusShape =
        animatedRoundedCornerShape(
            targetRadius = if (isXposedActive && isModuleEnabled) 28.dp else 16.dp,
            label = "statusCardMorph",
        )

    Card(
        modifier =
            modifier.graphicsLayer {
                scaleX = scale
                scaleY = scale
            },
        colors =
            CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
        shape = statusShape,
    ) {
        Box(
            modifier =
                Modifier.fillMaxWidth()
                    .background(
                        Brush.verticalGradient(
                            colors = listOf(statusColor.copy(alpha = 0.15f), Color.Transparent)
                        )
                    )
                    .padding(24.dp)
        ) {
            StatusCardContent(
                isXposedActive = isXposedActive,
                isModuleEnabled = isModuleEnabled,
                statusColor = statusColor,
            )
        }
    }
}

@Composable
private fun StatusCardContent(
    isXposedActive: Boolean,
    isModuleEnabled: Boolean,
    statusColor: Color,
) {
    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
        StatusIcon(isActive = isXposedActive && isModuleEnabled, statusColor = statusColor)
        Spacer(modifier = Modifier.height(16.dp))
        Text(
            text = stringResource(id = R.string.app_name),
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurface,
        )
        Spacer(modifier = Modifier.height(8.dp))
        StatusBadge(
            isXposedActive = isXposedActive,
            isModuleEnabled = isModuleEnabled,
            statusColor = statusColor,
        )
        if (!isXposedActive) {
            Spacer(modifier = Modifier.height(12.dp))
            ModuleEnableInstruction()
        }
    }
}

@Composable
private fun StatusIcon(isActive: Boolean, @Suppress("UNUSED_PARAMETER") statusColor: Color) {
    HeroStatusIndicator(isActive = isActive, icon = Icons.Filled.Shield)
}

@Composable
private fun StatusBadge(isXposedActive: Boolean, isModuleEnabled: Boolean, statusColor: Color) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center,
    ) {
        StatusIndicator(color = statusColor, size = 12.dp)
        Spacer(modifier = Modifier.width(8.dp))
        Text(
            text = statusText(isXposedActive = isXposedActive, isModuleEnabled = isModuleEnabled),
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun ModuleEnableInstruction() {
    OutlinedCard(
        colors =
            CardDefaults.outlinedCardColors(
                containerColor = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.3f)
            )
    ) {
        Text(
            text = stringResource(id = R.string.home_enable_instruction),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.error,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
        )
    }
}

@Composable
private fun statusText(isXposedActive: Boolean, isModuleEnabled: Boolean): String =
    when {
        !isXposedActive -> stringResource(id = R.string.home_module_not_injected)
        isModuleEnabled -> stringResource(id = R.string.home_protection_active)
        else -> stringResource(id = R.string.home_protection_disabled)
    }

/** Group selector card with bottom sheet for group selection. */
@Composable
private fun GroupSelectorCard(
    groups: ImmutableList<SpoofGroup>,
    appConfigs: ImmutableMap<String, AppConfig>,
    selectedGroup: SpoofGroup?,
    groupSelected: (SpoofGroup) -> Unit,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var showBottomSheet by rememberSaveable { mutableStateOf(false) }

    ExpressiveCard(
        onClick = { showBottomSheet = true },
        modifier = modifier,
        shape = MaterialTheme.shapes.large,
        containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
        elevation = CardDefaults.elevatedCardElevation(defaultElevation = 0.dp),
    ) {
        GroupSelectorHeader(
            selectedGroup = selectedGroup,
            rotationAngle = 0f,
            onViewGroup = onClick,
        )
    }

    if (showBottomSheet) {
        GroupSelectorBottomSheet(
            groups = groups,
            appConfigs = appConfigs,
            selectedGroup = selectedGroup,
            groupSelected = groupSelected,
            dismiss = { showBottomSheet = false },
        )
    }
}

/** Quick actions section. */
@Composable
private fun QuickActionsSection(
    onNavigateToSpoof: () -> Unit,
    onRegenerateAll: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(
            text = stringResource(id = R.string.home_quick_actions),
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurface,
        )

        // Material 3 Expressive Button Group
        QuickActionGroup(
            actions =
                persistentListOf(
                    QuickAction(
                        label = stringResource(id = R.string.home_action_configure),
                        icon = Icons.Outlined.Fingerprint,
                        onClick = onNavigateToSpoof,
                    ),
                    QuickAction(
                        label = stringResource(id = R.string.action_regenerate),
                        icon = Icons.Filled.Refresh,
                        onClick = onRegenerateAll,
                    ),
                )
        )
    }
}

@Preview(showBackground = true, backgroundColor = 0xFF000000)
@Composable
private fun HomeScreenContentPreview() {
    DeviceMaskerTheme {
        HomeScreenContent(
            isXposedActive = true,
            isModuleEnabled = true,
            groups =
                persistentListOf(
                    SpoofGroup.createDefaultGroup(),
                    SpoofGroup.createNew("Work Group"),
                    SpoofGroup.createNew("Gaming"),
                ),
            appConfigs = persistentMapOf(),
            selectedGroup = SpoofGroup.createDefaultGroup(),
            groupSelected = {},
            enabledAppsCount = 12,
            maskedIdentifiersCount = 24,
            onNavigateToSpoof = {},
            onRegenerateAll = {},
        )
    }
}

@Preview(showBackground = true, backgroundColor = 0xFF000000)
@Composable
private fun HomeScreenInactivePreview() {
    DeviceMaskerTheme {
        HomeScreenContent(
            isXposedActive = false,
            isModuleEnabled = false,
            groups = persistentListOf(),
            appConfigs = persistentMapOf(),
            selectedGroup = null,
            groupSelected = {},
            enabledAppsCount = 0,
            maskedIdentifiersCount = 0,
            onNavigateToSpoof = {},
            onRegenerateAll = {},
        )
    }
}
