package com.astrixforge.devicemasker.ui.screens.groups

import androidx.lifecycle.SavedStateHandle
import app.cash.turbine.test
import com.astrixforge.devicemasker.MainDispatcherRule
import com.astrixforge.devicemasker.testing.FakeSpoofRepository
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class GroupsViewModelTest {

    @get:Rule val mainDispatcherRule = MainDispatcherRule()

    private fun groupsViewModel(repository: FakeSpoofRepository): GroupsViewModel =
        GroupsViewModel(repository, SavedStateHandle())

    @Test
    fun `create group adds to list`() = runTest {
        val repository = FakeSpoofRepository()
        val viewModel = groupsViewModel(repository)

        viewModel.state.test {
            assertTrue(awaitItem().groups.isEmpty())

            viewModel.createGroup("New Group", "desc")
            val state = awaitItem()
            assertEquals(1, state.groups.size)
            assertEquals("New Group", state.groups.first().name)
        }
    }

    @Test
    fun `delete group removes from list`() = runTest {
        val repository = FakeSpoofRepository()
        repository.createGroup("ToDelete", "")
        val viewModel = groupsViewModel(repository)

        viewModel.state.test {
            val before = awaitItem()
            assertEquals(1, before.groups.size)

            viewModel.deleteGroup(before.groups.first().id)
            val after = awaitItem()
            assertTrue(after.groups.isEmpty())
        }
    }

    @Test
    fun `export returns json data`() = runTest {
        val repository = FakeSpoofRepository()
        val viewModel = groupsViewModel(repository)

        var result: Result<String>? = null
        viewModel.exportGroups { result = it }

        val exportResult = result ?: error("Export result was not delivered")
        assertTrue(exportResult.isSuccess)
        assertTrue(exportResult.getOrThrow().contains("groups"))
    }

    @Test
    fun `import malformed json returns false`() = runTest {
        val repository = FakeSpoofRepository()
        val viewModel = groupsViewModel(repository)

        var result = true
        viewModel.importGroups("not json") { result = it }

        assertFalse(result)
    }
}
