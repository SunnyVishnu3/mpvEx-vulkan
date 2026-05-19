package com.astrixforge.devicemasker.ui.navigation

import java.net.URI

data class DeviceMaskerDeepLink(
    val topLevelDestination: NavDestination,
    val backStack: List<NavDestination>,
)

object DeviceMaskerDeepLinks {
    const val SCHEME = "devicemasker"
    const val HOST = "open"

    fun parse(uriString: String?): DeviceMaskerDeepLink? {
        val uri = parseValidUri(uriString) ?: return null
        val segments = uri.pathSegments()

        return when (segments.firstOrNull()) {
            null,
            "home" ->
                DeviceMaskerDeepLink(
                    topLevelDestination = NavDestination.Home,
                    backStack = listOf(NavDestination.Home),
                )
            "groups" -> groupsDeepLink(segments)
            "settings" ->
                DeviceMaskerDeepLink(
                    topLevelDestination = NavDestination.Settings,
                    backStack = listOf(NavDestination.Settings),
                )
            "diagnostics" ->
                DeviceMaskerDeepLink(
                    topLevelDestination = NavDestination.Settings,
                    backStack = listOf(NavDestination.Settings, NavDestination.Diagnostics),
                )
            else -> null
        }
    }

    private fun parseValidUri(uriString: String?): URI? =
        uriString
            ?.takeIf { it.isNotBlank() }
            ?.let { runCatching { URI(it) }.getOrNull() }
            ?.takeIf { it.scheme.equals(SCHEME, ignoreCase = true) }
            ?.takeIf { it.host.equals(HOST, ignoreCase = true) }

    private fun URI.pathSegments(): List<String> =
        path?.trim('/')?.split('/')?.filter { it.isNotBlank() } ?: emptyList()

    private fun groupsDeepLink(segments: List<String>): DeviceMaskerDeepLink? =
        when (segments.size) {
            1 ->
                DeviceMaskerDeepLink(
                    topLevelDestination = NavDestination.Groups,
                    backStack = listOf(NavDestination.Groups),
                )
            2 ->
                DeviceMaskerDeepLink(
                    topLevelDestination = NavDestination.Groups,
                    backStack =
                        listOf(NavDestination.Groups, NavDestination.GroupSpoofing(segments[1])),
                )
            else -> null
        }
}
