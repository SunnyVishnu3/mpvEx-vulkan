package com.astrixforge.devicemasker.ui.screens.home

import androidx.lifecycle.SavedStateHandle
import app.cash.turbine.test
import com.astrixforge.devicemasker.MainDispatcherRule
import com.astrixforge.devicemasker.common.AppConfig
import com.astrixforge.devicemasker.common.SpoofGroup
import com.astrixforge.devicemasker.data.XposedScopeState
import com.astrixforge.devicemasker.data.models.InstalledApp
import com.astrixforge.devicemasker.testing.FakeAppScopeRepository
import com.astrixforge.devicemasker.testing.FakeSpoofRepository
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class HomeViewModelTest {

    @get:Rule val mainDispatcherRule = MainDispatcherRule()

    private fun homeViewModel(
        repository: FakeSpoofRepository,
        isXposedActiveFlow: MutableStateFlow<Boolean> = MutableStateFlow(false),
        xposedScopeStateFlow: MutableStateFlow<XposedScopeState> =
            MutableStateFlow(XposedScopeState.Disconnected),
    ): HomeViewModel =
        HomeViewModel(
            repository = repository,
            isXposedActiveFlow = isXposedActiveFlow,
            xposedScopeStateFlow = xposedScopeStateFlow,
            savedStateHandle = SavedStateHandle(),
        )

    @Test
    fun `module toggle updates state`() = runTest {
        val repository = FakeSpoofRepository()
        val viewModel = homeViewModel(repository)

        viewModel.state.test {
            assertFalse(awaitItem().isModuleEnabled)

            viewModel.setModuleEnabled(true)
            assertTrue(awaitItem().isModuleEnabled)
        }
    }

    @Test
    fun `group selection updates selected group and counts`() = runTest {
        val group = SpoofGroup.createNew(name = "TestGroup", isDefault = true)
        val repository =
            FakeSpoofRepository(
                initialGroups = listOf(group),
                initialAppConfigs =
                    mapOf(
                        "com.example.app" to
                            AppConfig(
                                packageName = "com.example.app",
                                groupId = group.id,
                                isEnabled = true,
                            )
                    ),
            )
        val viewModel = homeViewModel(repository)

        // Initial state
        assertEquals("TestGroup", viewModel.state.value.selectedGroup?.name)
        assertEquals(1, viewModel.state.value.enabledAppsCount)

        val group2 = SpoofGroup.createNew(name = "Other")
        repository.setGroups(listOf(group, group2))

        viewModel.selectGroup(group2.id)
        advanceUntilIdle()

        assertEquals("Other", viewModel.state.value.selectedGroup?.name)
        assertEquals(0, viewModel.state.value.enabledAppsCount)
    }

    @Test
    fun `xposed connection updates state`() = runTest {
        val xposedFlow = MutableStateFlow(false)
        val repository = FakeSpoofRepository()
        val viewModel = homeViewModel(repository, isXposedActiveFlow = xposedFlow)

        viewModel.state.test {
            assertFalse(awaitItem().isXposedActive)

            xposedFlow.value = true
            assertTrue(awaitItem().isXposedActive)
        }
    }

    @Test
    fun `enabledAppsCount reflects repository`() = runTest {
        val group = SpoofGroup.createNew(name = "G1", isDefault = true)
        val repository =
            FakeSpoofRepository(
                initialGroups = listOf(group),
                initialAppConfigs =
                    mapOf(
                        "a.b.c" to AppConfig(packageName = "a.b.c", groupId = group.id),
                        "d.e.f" to AppConfig(packageName = "d.e.f", groupId = group.id),
                    ),
            )
        val viewModel = homeViewModel(repository)

        viewModel.state.test { assertEquals(2, awaitItem().enabledAppsCount) }
    }

    @Test
    fun `loads installed apps so scoped apps appear without opening apps tab first`() = runTest {
        val appScopeRepository =
            FakeAppScopeRepository(
                scopedAppsLoadedFromSystem =
                    mapOf(
                        "com.scoped.app" to
                            InstalledApp("com.scoped.app", "Scoped App", isSystemApp = false)
                    )
            )
        val repository = FakeSpoofRepository(appScopeRepository = appScopeRepository)
        val scopeState =
            MutableStateFlow<XposedScopeState>(XposedScopeState.Connected(setOf("com.scoped.app")))

        val viewModel = homeViewModel(repository, xposedScopeStateFlow = scopeState)
        advanceUntilIdle()

        assertEquals(0, appScopeRepository.loadAppsCalls)
        assertEquals(1, appScopeRepository.loadScopedAppsCalls)
        assertEquals(false, appScopeRepository.lastForceRefresh)
        assertEquals(listOf("Scoped App"), viewModel.state.value.scopedApps.map { it.label })
    }

    @Test
    fun `home loads only scoped package metadata on init`() = runTest {
        val appScopeRepository =
            FakeAppScopeRepository(
                scopedAppsLoadedFromSystem =
                    mapOf(
                        "com.scoped.app" to
                            InstalledApp("com.scoped.app", "Scoped App", isSystemApp = false)
                    )
            )
        val repository = FakeSpoofRepository(appScopeRepository = appScopeRepository)
        val scopeState =
            MutableStateFlow<XposedScopeState>(
                XposedScopeState.Connected(setOf("android", "system", "com.scoped.app"))
            )

        val viewModel = homeViewModel(repository, xposedScopeStateFlow = scopeState)
        advanceUntilIdle()

        assertEquals(0, appScopeRepository.loadAppsCalls)
        assertEquals(setOf("com.scoped.app"), appScopeRepository.lastScopedPackages)
        assertEquals(listOf("Scoped App"), viewModel.state.value.scopedApps.map { it.label })
    }

    @Test
    fun `refresh scoped apps force refreshes scoped metadata`() = runTest {
        val appScopeRepository = FakeAppScopeRepository()
        val repository = FakeSpoofRepository(appScopeRepository = appScopeRepository)
        val viewModel =
            HomeViewModel(
                repository = repository,
                xposedScopeStateFlow =
                    MutableStateFlow(XposedScopeState.Connected(setOf("com.scoped.app"))),
                savedStateHandle = SavedStateHandle(),
            )
        advanceUntilIdle()

        viewModel.refreshScopedApps()
        advanceUntilIdle()

        assertEquals(0, appScopeRepository.loadAppsCalls)
        assertEquals(2, appScopeRepository.loadScopedAppsCalls)
        assertEquals(setOf("com.scoped.app"), appScopeRepository.lastScopedPackages)
        assertEquals(true, appScopeRepository.lastForceRefresh)
    }
}
