package com.astrixforge.devicemasker.ui.screens.settings

import android.net.Uri
import app.cash.turbine.test
import com.astrixforge.devicemasker.MainDispatcherRule
import com.astrixforge.devicemasker.data.models.ThemeMode
import com.astrixforge.devicemasker.service.ShareableLogResult
import com.astrixforge.devicemasker.testing.FakeLogManager
import com.astrixforge.devicemasker.testing.FakeSettingsDataStore
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment

@RunWith(RobolectricTestRunner::class)
@OptIn(ExperimentalCoroutinesApi::class)
class SettingsViewModelTest {

    @get:Rule val mainDispatcherRule = MainDispatcherRule()

    private fun createViewModel(
        settingsStore: FakeSettingsDataStore = FakeSettingsDataStore(),
        logManager: FakeLogManager = FakeLogManager(),
    ): SettingsViewModel {
        val app = RuntimeEnvironment.getApplication()
        return SettingsViewModel(
            app,
            settingsStore,
            logManager,
            ioDispatcher = UnconfinedTestDispatcher(),
        )
    }

    @Test
    fun `theme change updates state`() = runTest {
        val viewModel = createViewModel()

        viewModel.state.test {
            assertEquals(ThemeMode.SYSTEM, awaitItem().themeMode)

            viewModel.setThemeMode(ThemeMode.DARK)
            assertEquals(ThemeMode.DARK, awaitItem().themeMode)
        }
    }

    @Test
    fun `export success updates state`() = runTest {
        val logManager = FakeLogManager()
        val viewModel = createViewModel(logManager = logManager)

        viewModel.exportLogsToUri(Uri.parse("content://test"))
        advanceUntilIdle()

        assertFalse(viewModel.state.value.isExportingLogs)
        assertTrue(viewModel.state.value.exportResult is ExportResult.Success)
    }

    @Test
    fun `export uses single log manager path without mode state`() = runTest {
        val logManager = FakeLogManager()
        val viewModel = createViewModel(logManager = logManager)
        val uri = Uri.parse("content://test/export.zip")

        viewModel.exportLogsToUri(uri)
        advanceUntilIdle()

        assertEquals(1, logManager.exportCallCount)
        assertEquals(uri, logManager.lastExportUri)
        assertFalse(viewModel.state.value.isExportingLogs)
        assertTrue(viewModel.state.value.exportResult is ExportResult.Success)
    }

    @Test
    fun `share uses single log manager path without mode state`() = runTest {
        val logManager = FakeLogManager()
        val viewModel = createViewModel(logManager = logManager)
        var result: ShareableLogResult? = null

        viewModel.createShareableLogs { shareResult -> result = shareResult }
        advanceUntilIdle()

        assertEquals(1, logManager.shareCallCount)
        assertTrue(result is ShareableLogResult.Success)
        assertFalse(viewModel.state.value.isExportingLogs)
    }

    @Test
    fun `export failure updates state`() = runTest {
        val logManager =
            FakeLogManager().apply {
                exportResult =
                    com.astrixforge.devicemasker.service.LogExportResult.Error("disk full")
            }
        val viewModel = createViewModel(logManager = logManager)

        viewModel.exportLogsToUri(Uri.parse("content://test"))
        advanceUntilIdle()

        assertFalse(viewModel.state.value.isExportingLogs)
        assertTrue(viewModel.state.value.exportResult is ExportResult.Error)
    }

    @Test
    fun `clear export result resets state`() = runTest {
        val logManager = FakeLogManager()
        val viewModel = createViewModel(logManager = logManager)

        viewModel.exportLogsToUri(Uri.parse("content://test"))
        advanceUntilIdle()
        assertTrue(viewModel.state.value.exportResult is ExportResult.Success)

        viewModel.clearExportResult()
        assertNull(viewModel.state.value.exportResult)
    }
}
