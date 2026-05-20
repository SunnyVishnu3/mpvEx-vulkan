package com.astrixforge.devicemasker.ui.navigation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshots.SnapshotStateList
import androidx.navigation3.runtime.rememberNavBackStack

val topLevelDestinations: List<NavDestination> =
    listOf(NavDestination.Home, NavDestination.Groups, NavDestination.Settings)

@Composable
fun rememberDeviceMaskerNavigationState(): DeviceMaskerNavigationState {
    val homeBackStack = rememberNavBackStack(NavDestination.Home)
    val groupsBackStack = rememberNavBackStack(NavDestination.Groups)
    val settingsBackStack = rememberNavBackStack(NavDestination.Settings)
    val topLevelDestinationState =
        rememberSaveable(stateSaver = TopLevelDestinationSaver) {
            mutableStateOf<NavDestination>(NavDestination.Home)
        }

    return remember(homeBackStack, groupsBackStack, settingsBackStack, topLevelDestinationState) {
        DeviceMaskerNavigationState(
            topLevelDestinationState = topLevelDestinationState,
            backStacks =
                linkedMapOf(
                    NavDestination.Home to homeBackStack.asDestinationStack(),
                    NavDestination.Groups to groupsBackStack.asDestinationStack(),
                    NavDestination.Settings to settingsBackStack.asDestinationStack(),
                ),
        )
    }
}

@Suppress("UNCHECKED_CAST")
private fun MutableList<*>.asDestinationStack(): MutableList<NavDestination> =
    this as MutableList<NavDestination>

class DeviceMaskerNavigationState(
    private val startDestination: NavDestination = NavDestination.Home,
    restoredTopLevelDestination: NavDestination = startDestination,
    private val topLevelDestinationState: MutableState<NavDestination> =
        mutableStateOf(restoredTopLevelDestination),
    private val backStacks: Map<NavDestination, MutableList<NavDestination>> =
        linkedMapOf(
            NavDestination.Home to mutableStateListOf(NavDestination.Home),
            NavDestination.Groups to mutableStateListOf(NavDestination.Groups),
            NavDestination.Settings to mutableStateListOf(NavDestination.Settings),
        ),
) {
    var topLevelDestination: NavDestination by topLevelDestinationState
        internal set

    val currentBackStack: List<NavDestination>
        get() = backStacks.getValue(topLevelDestination).toList()

    val currentDestination: NavDestination
        get() = backStacks.getValue(topLevelDestination).last()

    private val mutableVisibleBackStack =
        mutableStateListOf<NavDestination>().apply { addAll(currentBackStack) }

    val visibleBackStack: List<NavDestination>
        get() = mutableVisibleBackStack.toList()

    internal val navDisplayBackStack: SnapshotStateList<NavDestination>
        get() = mutableVisibleBackStack

    val isFocusScreen: Boolean
        get() =
            currentDestination is NavDestination.GroupSpoofing ||
                currentDestination == NavDestination.Diagnostics ||
                currentDestination == NavDestination.LogsMonitor

    internal fun push(destination: NavDestination) {
        backStacks.getValue(topLevelDestination).add(destination)
        syncVisibleBackStack()
    }

    internal fun switchTopLevel(destination: NavDestination) {
        require(destination in backStacks.keys) { "Not a top-level destination: $destination" }
        topLevelDestination = destination
        syncVisibleBackStack()
    }

    internal fun replaceStack(
        topLevelDestination: NavDestination,
        newBackStack: List<NavDestination>,
    ) {
        require(topLevelDestination in backStacks.keys) {
            "Not a top-level destination: $topLevelDestination"
        }
        require(newBackStack.firstOrNull() == topLevelDestination) {
            "Deep link stack must start with its top-level destination"
        }
        backStacks.getValue(topLevelDestination).apply {
            clear()
            addAll(newBackStack)
        }
        this.topLevelDestination = topLevelDestination
        syncVisibleBackStack()
    }

    internal fun popCurrentStack(): Boolean {
        val stack = backStacks.getValue(topLevelDestination)
        if (stack.size <= 1) return false
        stack.removeAt(stack.lastIndex)
        syncVisibleBackStack()
        return true
    }

    private fun syncVisibleBackStack() {
        mutableVisibleBackStack.clear()
        mutableVisibleBackStack.addAll(currentBackStack)
    }
}

private val TopLevelDestinationSaver: Saver<NavDestination, String> =
    Saver(
        save = { destination -> destination.topLevelSaveKey() },
        restore = { savedKey -> topLevelDestinationFromSaveKey(savedKey) },
    )

private fun NavDestination.topLevelSaveKey(): String =
    when (this) {
        NavDestination.Groups -> "groups"
        NavDestination.Settings -> "settings"
        else -> "home"
    }

private fun topLevelDestinationFromSaveKey(key: String): NavDestination =
    when (key) {
        "groups" -> NavDestination.Groups
        "settings" -> NavDestination.Settings
        else -> NavDestination.Home
    }

class DeviceMaskerNavigator(private val state: DeviceMaskerNavigationState) {
    fun navigateTopLevel(destination: NavDestination) {
        state.switchTopLevel(destination)
    }

    fun navigateToGroup(groupId: String) {
        state.switchTopLevel(NavDestination.Groups)
        val destination = NavDestination.GroupSpoofing(groupId)
        if (state.currentDestination != destination) {
            state.push(destination)
        }
    }

    fun navigateToDiagnostics() {
        state.switchTopLevel(NavDestination.Settings)
        if (state.currentDestination != NavDestination.Diagnostics) {
            state.push(NavDestination.Diagnostics)
        }
    }

    fun navigateToLogsMonitor() {
        state.switchTopLevel(NavDestination.Settings)
        if (state.currentDestination != NavDestination.LogsMonitor) {
            state.push(NavDestination.LogsMonitor)
        }
    }

    fun navigateDeepLink(deepLink: DeviceMaskerDeepLink) {
        state.replaceStack(
            topLevelDestination = deepLink.topLevelDestination,
            newBackStack = deepLink.backStack,
        )
    }

    /** @return true when the Activity should handle the back press by exiting. */
    fun goBack(): Boolean =
        when {
            state.popCurrentStack() -> false
            state.topLevelDestination != NavDestination.Home -> {
                state.switchTopLevel(NavDestination.Home)
                false
            }
            else -> true
        }
}
