package com.astrixforge.devicemasker.ui.navigation

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.dropUnlessResumed
import com.astrixforge.devicemasker.R
import com.astrixforge.devicemasker.ui.theme.AppMotion
import com.astrixforge.devicemasker.ui.theme.ElevationTokens

/**
 * Bottom navigation bar for DeviceMasker.
 *
 * Uses Material 3 NavigationBar with animated icons and labels. Supports reduced-motion aware
 * transitions for smooth but accessible feedback.
 *
 * @param currentDestination Current active top-level destination
 * @param onNavigate Callback when user selects a route
 * @param modifier Optional modifier
 */
@Composable
fun BottomNavBar(
    currentDestination: NavDestination,
    onNavigate: (NavDestination) -> Unit,
    modifier: Modifier = Modifier,
) {
    NavigationBar(
        modifier = modifier,
        containerColor = MaterialTheme.colorScheme.surface,
        contentColor = MaterialTheme.colorScheme.onSurface,
        tonalElevation = ElevationTokens.Level0,
    ) {
        bottomNavItems.forEach { item ->
            BottomNavItem(
                item = item,
                isSelected = currentDestination == item.destination,
                onClick = dropUnlessResumed { onNavigate(item.destination) },
            )
        }
    }
}

/** Individual navigation bar item. */
@Composable
private fun RowScope.BottomNavItem(item: NavItem, isSelected: Boolean, onClick: () -> Unit) {
    val scale by
        animateFloatAsState(
            targetValue = if (isSelected && !AppMotion.shouldReduceMotion()) 1.05f else 1f,
            animationSpec = AppMotion.spatial(AppMotion.Spatial.Standard, AppMotion.ReducedAlpha),
            label = "iconScale_${item.destination}",
        )

    NavigationBarItem(
        selected = isSelected,
        onClick = onClick,
        icon = {
            AnimatedNavIcon(
                item = item,
                isSelected = isSelected,
                modifier =
                    Modifier.graphicsLayer {
                        scaleX = scale
                        scaleY = scale
                    },
            )
        },
        label = {
            Text(text = stringResource(item.labelRes), style = MaterialTheme.typography.labelMedium)
        },
        colors =
            NavigationBarItemDefaults.colors(
                selectedIconColor = MaterialTheme.colorScheme.onSecondaryContainer,
                selectedTextColor = MaterialTheme.colorScheme.secondary,
                indicatorColor = MaterialTheme.colorScheme.secondaryContainer,
                unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
                unselectedTextColor = MaterialTheme.colorScheme.onSurfaceVariant,
            ),
    )
}

/** Animated icon that transitions between selected and unselected states. */
@Composable
private fun AnimatedNavIcon(item: NavItem, isSelected: Boolean, modifier: Modifier = Modifier) {
    val iconColor by
        animateColorAsState(
            targetValue =
                if (isSelected) {
                    MaterialTheme.colorScheme.onSecondaryContainer
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant
                },
            animationSpec = AppMotion.Effect.Color,
            label = "iconColor_${item.destination}",
        )

    Icon(
        imageVector = if (isSelected) item.selectedIcon else item.unselectedIcon,
        contentDescription = stringResource(navContentDescriptionRes(item.destination, isSelected)),
        modifier = modifier.size(24.dp),
        tint = iconColor,
    )
}

private fun navContentDescriptionRes(destination: NavDestination, isSelected: Boolean): Int =
    when (destination) {
        NavDestination.Home -> {
            if (isSelected) R.string.bottom_nav_home else R.string.bottom_nav_open_home
        }
        NavDestination.Groups -> {
            if (isSelected) R.string.bottom_nav_groups else R.string.bottom_nav_open_groups
        }
        else -> {
            if (isSelected) R.string.bottom_nav_settings else R.string.bottom_nav_open_settings
        }
    }
