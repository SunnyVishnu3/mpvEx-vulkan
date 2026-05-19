package com.astrixforge.devicemasker.ui.screens.groupspoofing

import androidx.lifecycle.SavedStateHandle
import app.cash.turbine.test
import com.astrixforge.devicemasker.MainDispatcherRule
import com.astrixforge.devicemasker.common.AppConfig
import com.astrixforge.devicemasker.common.SpoofGroup
import com.astrixforge.devicemasker.common.SpoofType
import com.astrixforge.devicemasker.common.assignedAppCount
import com.astrixforge.devicemasker.common.models.Carrier
import com.astrixforge.devicemasker.data.models.InstalledApp
import com.astrixforge.devicemasker.testing.FakeAppScopeRepository
import com.astrixforge.devicemasker.testing.FakeSpoofRepository
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class GroupSpoofingViewModelTest {

    @get:Rule val mainDispatcherRule = MainDispatcherRule()

    private fun groupSpoofingViewModel(
        repository: FakeSpoofRepository,
        groupId: String,
    ): GroupSpoofingViewModel = GroupSpoofingViewModel(repository, groupId, SavedStateHandle())

    @Test
    fun `tab switching updates selected tab`() = runTest {
        val group = SpoofGroup.createNew(name = "G1")
        val repository = FakeSpoofRepository(initialGroups = listOf(group))
        val viewModel = groupSpoofingViewModel(repository, group.id)

        viewModel.state.test {
            assertEquals(0, awaitItem().selectedTab)

            viewModel.setSelectedTab(2)
            assertEquals(2, awaitItem().selectedTab)
        }
    }

    @Test
    fun `value regeneration updates group`() = runTest {
        val group = SpoofGroup.createNew(name = "G1")
        val repository = FakeSpoofRepository(initialGroups = listOf(group))
        val viewModel = groupSpoofingViewModel(repository, group.id)

        viewModel.state.test {
            val before = awaitItem()
            assertEquals(null, before.group?.getValue(SpoofType.ANDROID_ID))

            viewModel.regenerateValue(SpoofType.ANDROID_ID)
            val after = awaitItem()
            assertTrue(after.group?.getValue(SpoofType.ANDROID_ID)?.startsWith("fake-") == true)
        }
    }

    @Test
    fun `carrier update updates group values`() = runTest {
        val group = SpoofGroup.createNew(name = "G1")
        val repository = FakeSpoofRepository(initialGroups = listOf(group))
        val viewModel = groupSpoofingViewModel(repository, group.id)

        viewModel.state.test {
            awaitItem()

            val carrier = Carrier.nextSecureRandom()
            viewModel.updateCarrier(carrier)
            val after = awaitItem()
            assertEquals(carrier.name, after.group?.getValue(SpoofType.CARRIER_NAME))
            assertEquals(carrier.mccMnc, after.group?.getValue(SpoofType.CARRIER_MCC_MNC))
        }
    }

    @Test
    fun `app assignment updates group`() = runTest {
        val group = SpoofGroup.createNew(name = "G1")
        val repository = FakeSpoofRepository(initialGroups = listOf(group))
        val viewModel = groupSpoofingViewModel(repository, group.id)

        viewModel.state.test {
            val before = awaitItem()
            assertEquals(0, before.group?.assignedAppCount())

            viewModel.addAppToGroup("com.example.app")
            val after = awaitItem()
            assertEquals(1, after.group?.assignedAppCount())
        }
    }

    @Test
    fun `app configs expose canonical assignment even when legacy group apps are stale`() =
        runTest {
            val group =
                SpoofGroup.createNew(name = "G1").copy(assignedApps = setOf("com.stale.app"))
            val repository =
                FakeSpoofRepository(
                    initialGroups = listOf(group),
                    initialAppConfigs =
                        mapOf(
                            "com.current.app" to
                                AppConfig(
                                    packageName = "com.current.app",
                                    groupId = group.id,
                                    isEnabled = true,
                                )
                        ),
                )
            val viewModel = groupSpoofingViewModel(repository, group.id)

            viewModel.state.test {
                val state = awaitItem()

                assertEquals(group.id, state.appConfigs["com.current.app"]?.groupId)
                assertEquals(null, state.appConfigs["com.stale.app"])
            }
        }

    @OptIn(ExperimentalCoroutinesApi::class)
    @Test
    fun `app rows are normalized sorted and annotated outside composition`() = runTest {
        val currentGroup = SpoofGroup.createNew(name = "Current")
        val otherGroup = SpoofGroup.createNew(name = "Other")
        val assignedApp =
            InstalledApp("com.example.zulu", "Zulu", isSystemApp = false, versionName = "1")
        val otherAssignedApp =
            InstalledApp("com.example.alpha", "Alpha", isSystemApp = false, versionName = "1")
        val unassignedApp =
            InstalledApp("com.example.beta", "Beta", isSystemApp = false, versionName = "1")
        val appScopeRepository =
            FakeAppScopeRepository(
                initialApps = listOf(otherAssignedApp, unassignedApp, assignedApp)
            )
        val repository =
            FakeSpoofRepository(
                initialGroups = listOf(currentGroup, otherGroup),
                initialAppConfigs =
                    mapOf(
                        assignedApp.packageName to
                            AppConfig(
                                packageName = assignedApp.packageName,
                                groupId = currentGroup.id,
                                isEnabled = true,
                            ),
                        otherAssignedApp.packageName to
                            AppConfig(
                                packageName = otherAssignedApp.packageName,
                                groupId = otherGroup.id,
                                isEnabled = false,
                            ),
                    ),
                appScopeRepository = appScopeRepository,
            )

        val viewModel = groupSpoofingViewModel(repository, currentGroup.id)
        advanceUntilIdle()

        val rows = viewModel.state.value.appRows
        assertEquals(
            listOf(
                assignedApp.packageName,
                otherAssignedApp.packageName,
                unassignedApp.packageName,
            ),
            rows.map { it.app.packageName },
        )
        assertTrue(rows[0].isAssignedToCurrentGroup)
        assertEquals("alpha", rows[1].normalizedLabel)
        assertEquals(false, rows[1].isAppEnabled)
        assertEquals(otherGroup.name, rows[1].assignedToOtherGroupName)
    }
}
