package com.astrixforge.devicemasker.ui.navigation

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Groups
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.outlined.Groups
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.navigation3.runtime.NavKey
import com.astrixforge.devicemasker.R
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
sealed interface NavDestination : NavKey {
    @Serializable @SerialName("home") data object Home : NavDestination

    @Serializable @SerialName("groups") data object Groups : NavDestination

    @Serializable @SerialName("settings") data object Settings : NavDestination

    @Serializable @SerialName("diagnostics") data object Diagnostics : NavDestination

    @Serializable @SerialName("logs_monitor") data object LogsMonitor : NavDestination

    @Serializable
    @SerialName("group_spoofing")
    data class GroupSpoofing(val groupId: String) : NavDestination
}

/** Navigation item data class for bottom navigation. */
data class NavItem(
    val destination: NavDestination,
    val labelRes: Int,
    val selectedIcon: ImageVector,
    val unselectedIcon: ImageVector,
)

/**
 * Bottom navigation items (3 tabs for group-centric workflow). Order: Home → Groups → Settings
 *
 * Note: Global Spoof tab removed - each group now works independently with its own enable/disable
 * controls.
 */
val bottomNavItems: List<NavItem> =
    listOf(
        NavItem(
            destination = NavDestination.Home,
            labelRes = R.string.bottom_nav_home,
            selectedIcon = Icons.Filled.Home,
            unselectedIcon = Icons.Outlined.Home,
        ),
        NavItem(
            destination = NavDestination.Groups,
            labelRes = R.string.bottom_nav_groups,
            selectedIcon = Icons.Filled.Groups,
            unselectedIcon = Icons.Outlined.Groups,
        ),
        NavItem(
            destination = NavDestination.Settings,
            labelRes = R.string.bottom_nav_settings,
            selectedIcon = Icons.Filled.Settings,
            unselectedIcon = Icons.Outlined.Settings,
        ),
    )
