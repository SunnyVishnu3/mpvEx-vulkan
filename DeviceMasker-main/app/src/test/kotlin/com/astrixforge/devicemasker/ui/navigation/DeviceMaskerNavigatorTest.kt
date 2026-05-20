package com.astrixforge.devicemasker.ui.navigation

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DeviceMaskerNavigatorTest {
    @Test
    fun startsOnHome() {
        val state = DeviceMaskerNavigationState()

        assertEquals(NavDestination.Home, state.topLevelDestination)
        assertEquals(NavDestination.Home, state.currentDestination)
        assertEquals(listOf(NavDestination.Home), state.currentBackStack)
        assertFalse(state.isFocusScreen)
    }

    @Test
    fun topLevelNavigationSwitchesStacksWithoutClearingChildStack() {
        val state = DeviceMaskerNavigationState()
        val navigator = DeviceMaskerNavigator(state)

        navigator.navigateTopLevel(NavDestination.Groups)
        navigator.navigateToGroup("group-a")
        navigator.navigateTopLevel(NavDestination.Settings)
        navigator.navigateToDiagnostics()
        navigator.navigateTopLevel(NavDestination.Groups)

        assertEquals(NavDestination.Groups, state.topLevelDestination)
        assertEquals(NavDestination.GroupSpoofing("group-a"), state.currentDestination)
        assertEquals(
            listOf(NavDestination.Groups, NavDestination.GroupSpoofing("group-a")),
            state.currentBackStack,
        )
        assertTrue(state.isFocusScreen)
    }

    @Test
    fun navigateToLogsMonitorUsesSettingsStack() {
        val state = DeviceMaskerNavigationState()
        val navigator = DeviceMaskerNavigator(state)

        navigator.navigateToLogsMonitor()

        assertEquals(NavDestination.Settings, state.topLevelDestination)
        assertEquals(NavDestination.LogsMonitor, state.currentDestination)
        assertEquals(
            listOf(NavDestination.Settings, NavDestination.LogsMonitor),
            state.currentBackStack,
        )
        assertTrue(state.isFocusScreen)
    }

    @Test
    fun restoredTopLevelDestinationUsesRestoredStack() {
        val state =
            DeviceMaskerNavigationState(restoredTopLevelDestination = NavDestination.Settings)

        assertEquals(NavDestination.Settings, state.topLevelDestination)
        assertEquals(NavDestination.Settings, state.currentDestination)
        assertEquals(listOf(NavDestination.Settings), state.visibleBackStack)
    }

    @Test
    fun visibleBackStackCannotMutateNavigationState() {
        val state = DeviceMaskerNavigationState()

        val exposedStack = state.visibleBackStack

        assertFalse(exposedStack is MutableList<*>)
        assertEquals(listOf(NavDestination.Home), exposedStack)
        assertEquals(NavDestination.Home, state.currentDestination)
    }

    @Test
    fun backFromChildPopsToCurrentTopLevel() {
        val state = DeviceMaskerNavigationState()
        val navigator = DeviceMaskerNavigator(state)

        navigator.navigateTopLevel(NavDestination.Groups)
        navigator.navigateToGroup("group-a")

        assertFalse(navigator.goBack())

        assertEquals(NavDestination.Groups, state.currentDestination)
        assertEquals(listOf(NavDestination.Groups), state.currentBackStack)
        assertFalse(state.isFocusScreen)
    }

    @Test
    fun backFromNonHomeTopLevelReturnsHome() {
        val state = DeviceMaskerNavigationState()
        val navigator = DeviceMaskerNavigator(state)

        navigator.navigateTopLevel(NavDestination.Settings)

        assertFalse(navigator.goBack())

        assertEquals(NavDestination.Home, state.topLevelDestination)
        assertEquals(NavDestination.Home, state.currentDestination)
    }

    @Test
    fun backFromHomeRootRequestsActivityExit() {
        val state = DeviceMaskerNavigationState()
        val navigator = DeviceMaskerNavigator(state)

        assertTrue(navigator.goBack())
        assertEquals(NavDestination.Home, state.currentDestination)
    }

    @Test
    fun parsesGroupDeepLinkIntoSyntheticBackStack() {
        val deepLink = DeviceMaskerDeepLinks.parse("devicemasker://open/groups/group-a")

        assertEquals(
            DeviceMaskerDeepLink(
                topLevelDestination = NavDestination.Groups,
                backStack = listOf(NavDestination.Groups, NavDestination.GroupSpoofing("group-a")),
            ),
            deepLink,
        )
    }

    @Test
    fun parsesDiagnosticsDeepLinkIntoSyntheticBackStack() {
        val deepLink = DeviceMaskerDeepLinks.parse("devicemasker://open/diagnostics")

        assertEquals(
            DeviceMaskerDeepLink(
                topLevelDestination = NavDestination.Settings,
                backStack = listOf(NavDestination.Settings, NavDestination.Diagnostics),
            ),
            deepLink,
        )
    }

    @Test
    fun deepLinkNavigationReplacesTargetStack() {
        val state = DeviceMaskerNavigationState()
        val navigator = DeviceMaskerNavigator(state)

        navigator.navigateToGroup("old-group")
        navigator.navigateDeepLink(
            DeviceMaskerDeepLink(
                topLevelDestination = NavDestination.Groups,
                backStack = listOf(NavDestination.Groups, NavDestination.GroupSpoofing("new-group")),
            )
        )

        assertEquals(NavDestination.Groups, state.topLevelDestination)
        assertEquals(
            listOf(NavDestination.Groups, NavDestination.GroupSpoofing("new-group")),
            state.currentBackStack,
        )
    }
}
