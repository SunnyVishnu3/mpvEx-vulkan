package com.astrixforge.devicemasker.ui

import android.content.Context
import androidx.compose.animation.ContentTransform
import androidx.compose.animation.core.spring
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.NavigationRailItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.adaptive.navigation3.ListDetailSceneStrategy
import androidx.compose.material3.adaptive.navigation3.rememberListDetailSceneStrategy
import androidx.compose.material3.windowsizeclass.WindowSizeClass
import androidx.compose.material3.windowsizeclass.WindowWidthSizeClass
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.dropUnlessResumed
import androidx.navigation3.runtime.NavEntryDecorator
import androidx.navigation3.runtime.entryProvider
import androidx.navigation3.scene.SceneStrategy
import androidx.navigation3.ui.NavDisplay
import com.astrixforge.devicemasker.data.SettingsDataStore
import com.astrixforge.devicemasker.data.models.ThemeMode
import com.astrixforge.devicemasker.data.repository.SpoofRepository
import com.astrixforge.devicemasker.service.diagnostics.RootAccessState
import com.astrixforge.devicemasker.ui.navigation.BottomNavBar
import com.astrixforge.devicemasker.ui.navigation.DeviceMaskerDeepLinks
import com.astrixforge.devicemasker.ui.navigation.DeviceMaskerNavigationState
import com.astrixforge.devicemasker.ui.navigation.DeviceMaskerNavigator
import com.astrixforge.devicemasker.ui.navigation.NavDestination
import com.astrixforge.devicemasker.ui.navigation.bottomNavItems
import com.astrixforge.devicemasker.ui.theme.AppMotion
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf

@Composable
internal fun rememberMainSceneStrategies(
    windowSizeClass: WindowSizeClass
): ImmutableList<SceneStrategy<NavDestination>> {
    val listDetailSceneStrategy: SceneStrategy<NavDestination>? =
        if (windowSizeClass.widthSizeClass == WindowWidthSizeClass.Compact) {
            null
        } else {
            rememberListDetailSceneStrategy<NavDestination>()
        }
    return listDetailSceneStrategy?.let { persistentListOf(it) } ?: persistentListOf()
}

@Composable
internal fun HandleMainDeepLinkIntent(
    deepLinkIntent: android.content.Intent?,
    navigator: DeviceMaskerNavigator,
    deepLinkIntentHandled: () -> Unit,
) {
    val currentOnDeepLinkIntentHandled by rememberUpdatedState(deepLinkIntentHandled)

    LaunchedEffect(deepLinkIntent) {
        val intent = deepLinkIntent ?: return@LaunchedEffect
        if (intent.action == android.content.Intent.ACTION_VIEW) {
            DeviceMaskerDeepLinks.parse(intent.dataString)?.let(navigator::navigateDeepLink)
        }
        currentOnDeepLinkIntentHandled()
    }
}

@Composable
internal fun MainNavigationScaffold(
    navigationState: DeviceMaskerNavigationState,
    navigator: DeviceMaskerNavigator,
    showBottomBar: Boolean,
    showNavRail: Boolean,
    modifier: Modifier = Modifier,
    content: @Composable (Modifier) -> Unit,
) {
    Scaffold(
        modifier = modifier.fillMaxSize(),
        containerColor = MaterialTheme.colorScheme.background,
        bottomBar = {
            if (showBottomBar) {
                BottomNavBar(
                    currentDestination = navigationState.topLevelDestination,
                    onNavigate = { destination -> navigator.navigateTopLevel(destination) },
                )
            }
        },
    ) { innerPadding ->
        Row(modifier = Modifier.fillMaxSize()) {
            if (showNavRail) {
                NavRail(
                    currentDestination = navigationState.topLevelDestination,
                    onNavigate = { destination -> navigator.navigateTopLevel(destination) },
                )
            }
            content(
                if (showNavRail) {
                    Modifier.padding(innerPadding).weight(1f)
                } else {
                    Modifier.padding(innerPadding)
                }
            )
        }
    }
}

@Composable
internal fun DeviceMaskerNavDisplay(
    navigationState: DeviceMaskerNavigationState,
    navigator: DeviceMaskerNavigator,
    repository: SpoofRepository,
    settingsStore: SettingsDataStore,
    themeMode: ThemeMode,
    amoledMode: Boolean,
    dynamicColors: Boolean,
    rootAccessState: RootAccessState,
    context: Context,
    navigationBackHandler: () -> Unit,
    entryDecorators: ImmutableList<NavEntryDecorator<NavDestination>>,
    sceneStrategies: ImmutableList<SceneStrategy<NavDestination>>,
    reduceMotion: Boolean,
    modifier: Modifier = Modifier,
) {
    NavDisplay(
        backStack = navigationState.navDisplayBackStack,
        modifier = modifier,
        onBack = navigationBackHandler,
        entryDecorators = entryDecorators,
        sceneStrategies = sceneStrategies,
        transitionSpec = { navForwardTransform(reduceMotion) },
        popTransitionSpec = { navBackTransform(reduceMotion) },
        predictivePopTransitionSpec = { navBackTransform(reduceMotion) },
        entryProvider =
            entryProvider {
                entry<NavDestination.Home> {
                    HomeDestinationEntry(repository = repository, navigator = navigator)
                }

                entry<NavDestination.Settings> {
                    SettingsDestinationEntry(
                        context = context,
                        settingsStore = settingsStore,
                        themeMode = themeMode,
                        amoledMode = amoledMode,
                        dynamicColors = dynamicColors,
                        rootAccessState = rootAccessState,
                        navigator = navigator,
                    )
                }

                entry<NavDestination.GroupSpoofing>(
                    metadata = ListDetailSceneStrategy.detailPane()
                ) { destination ->
                    GroupSpoofingDestinationEntry(
                        repository = repository,
                        groupId = destination.groupId,
                        onNavigateBack = navigationBackHandler,
                    )
                }

                entry<NavDestination.Groups>(
                    metadata =
                        ListDetailSceneStrategy.listPane(
                            detailPlaceholder = { GroupDetailPlaceholder() }
                        )
                ) {
                    GroupsDestinationEntry(repository = repository, navigator = navigator)
                }

                entry<NavDestination.Diagnostics> {
                    DiagnosticsDestinationEntry(
                        context = context,
                        repository = repository,
                        onNavigateBack = navigationBackHandler,
                    )
                }

                entry<NavDestination.LogsMonitor> {
                    LogsMonitorDestinationEntry(
                        context = context,
                        onNavigateBack = navigationBackHandler,
                    )
                }
            },
    )
}

@Composable
private fun NavRail(
    currentDestination: NavDestination,
    onNavigate: (NavDestination) -> Unit,
    modifier: Modifier = Modifier,
) {
    NavigationRail(
        modifier = modifier.fillMaxHeight(),
        containerColor = MaterialTheme.colorScheme.surface,
        contentColor = MaterialTheme.colorScheme.onSurface,
    ) {
        bottomNavItems.forEach { item ->
            val isSelected = currentDestination == item.destination
            val navigateToItem = dropUnlessResumed { onNavigate(item.destination) }
            NavigationRailItem(
                selected = isSelected,
                onClick = navigateToItem,
                icon = {
                    Icon(
                        imageVector = if (isSelected) item.selectedIcon else item.unselectedIcon,
                        contentDescription = stringResource(item.labelRes),
                        modifier = Modifier.size(24.dp),
                    )
                },
                label = {
                    Text(
                        text = stringResource(item.labelRes),
                        style = MaterialTheme.typography.labelMedium,
                    )
                },
                colors =
                    NavigationRailItemDefaults.colors(
                        selectedIconColor = MaterialTheme.colorScheme.onSecondaryContainer,
                        selectedTextColor = MaterialTheme.colorScheme.secondary,
                        indicatorColor = MaterialTheme.colorScheme.secondaryContainer,
                        unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
                        unselectedTextColor = MaterialTheme.colorScheme.onSurfaceVariant,
                    ),
            )
        }
    }
}

private fun navForwardTransform(reduceMotion: Boolean): ContentTransform =
    if (reduceMotion) {
        fadeIn(animationSpec = spring()) togetherWith fadeOut(animationSpec = spring())
    } else {
        fadeIn(animationSpec = spring()) +
            slideInHorizontally(
                initialOffsetX = { it / 5 },
                animationSpec = AppMotion.ReducedOffset,
            ) togetherWith
            fadeOut(animationSpec = spring()) +
                slideOutHorizontally(
                    targetOffsetX = { -it / 5 },
                    animationSpec = AppMotion.ReducedOffset,
                )
    }

private fun navBackTransform(reduceMotion: Boolean): ContentTransform =
    if (reduceMotion) {
        fadeIn(animationSpec = spring()) togetherWith fadeOut(animationSpec = spring())
    } else {
        fadeIn(animationSpec = spring()) +
            slideInHorizontally(
                initialOffsetX = { -it / 5 },
                animationSpec = AppMotion.ReducedOffset,
            ) togetherWith
            fadeOut(animationSpec = spring()) +
                slideOutHorizontally(
                    targetOffsetX = { it / 5 },
                    animationSpec = AppMotion.ReducedOffset,
                )
    }
