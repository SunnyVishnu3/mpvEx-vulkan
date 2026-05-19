package com.astrixforge.devicemasker.ui.screens.logsmonitor

import com.astrixforge.devicemasker.MainDispatcherRule
import com.astrixforge.devicemasker.service.logmonitor.ILogMonitorRepository
import com.astrixforge.devicemasker.service.logmonitor.LogCaptureStatus
import com.astrixforge.devicemasker.service.logmonitor.LogMonitorLevel
import com.astrixforge.devicemasker.service.logmonitor.LogMonitorRow
import com.astrixforge.devicemasker.service.logmonitor.LogMonitorSource
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

class LogsMonitorViewModelTest {
    @get:Rule val mainDispatcherRule = MainDispatcherRule()

    @Test
    fun `view model filters visible rows and delegates capture actions`() = runTest {
        val repository = FakeLogMonitorRepository()
        val viewModel = LogsMonitorViewModel(repository)
        val collectJob = launch { viewModel.state.collect {} }

        repository.rowsFlow.value =
            listOf(
                LogMonitorRow(
                    1,
                    1,
                    LogMonitorSource.APP,
                    LogMonitorLevel.INFO,
                    "app",
                    "one",
                    "one",
                ),
                LogMonitorRow(
                    2,
                    2,
                    LogMonitorSource.XPOSED,
                    LogMonitorLevel.ERROR,
                    "xposed",
                    "hook failed",
                    "hook failed",
                ),
            )
        viewModel.setSource(LogMonitorSource.XPOSED)
        viewModel.setMinLevel(LogMonitorLevel.WARN)
        viewModel.setQuery("hook")
        viewModel.startCapture()
        viewModel.stopCapture()
        advanceUntilIdle()

        assertEquals(1, viewModel.state.value.visibleRows.size)
        assertEquals("hook failed", viewModel.state.value.visibleRows.single().message)
        assertEquals(1, repository.startCount)
        assertEquals(1, repository.stopCount)
        collectJob.cancel()
    }

    private class FakeLogMonitorRepository : ILogMonitorRepository {
        val rowsFlow = MutableStateFlow<List<LogMonitorRow>>(emptyList())
        private val statusFlow = MutableStateFlow(LogCaptureStatus.IDLE)
        var startCount = 0
        var stopCount = 0

        override val rows: StateFlow<List<LogMonitorRow>> = rowsFlow
        override val status: StateFlow<LogCaptureStatus> = statusFlow

        override fun startCapture() {
            startCount++
            statusFlow.value = LogCaptureStatus.RUNNING
        }

        override fun stopCapture() {
            stopCount++
            statusFlow.value = LogCaptureStatus.IDLE
        }

        override fun clear() {
            rowsFlow.value = emptyList()
        }
    }
}
