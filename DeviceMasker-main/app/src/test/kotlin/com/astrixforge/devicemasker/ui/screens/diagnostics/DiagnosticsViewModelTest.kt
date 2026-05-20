package com.astrixforge.devicemasker.ui.screens.diagnostics

import androidx.lifecycle.SavedStateHandle
import app.cash.turbine.test
import com.astrixforge.devicemasker.MainDispatcherRule
import com.astrixforge.devicemasker.data.repository.ISpoofRepository
import com.astrixforge.devicemasker.diagnostics.AntiDetectionTest
import com.astrixforge.devicemasker.diagnostics.DiagnosticResult
import com.astrixforge.devicemasker.service.IDiagnosticsProvider
import com.astrixforge.devicemasker.testing.FakeSpoofRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment

@RunWith(RobolectricTestRunner::class)
class DiagnosticsViewModelTest {

    @get:Rule val mainDispatcherRule = MainDispatcherRule()

    private fun createViewModel(
        repository: FakeSpoofRepository = FakeSpoofRepository(),
        isXposedActiveFlow: MutableStateFlow<Boolean> = MutableStateFlow(false),
        diagnosticsProvider: IDiagnosticsProvider? = null,
    ): DiagnosticsViewModel {
        val app = RuntimeEnvironment.getApplication()
        return if (diagnosticsProvider == null) {
            DiagnosticsViewModel(
                app,
                repository,
                isXposedActiveFlow,
                savedStateHandle = SavedStateHandle(),
            )
        } else {
            DiagnosticsViewModel(
                app,
                repository,
                isXposedActiveFlow,
                diagnosticsProvider,
                savedStateHandle = SavedStateHandle(),
            )
        }
    }

    @Test
    fun `refresh toggles isRefreshing`() = runTest {
        val viewModel = createViewModel()

        viewModel.state.test {
            val initial = awaitItem()
            assertFalse(initial.isRefreshing)

            viewModel.refresh()
            val refreshing = awaitItem()
            assertTrue(refreshing.isRefreshing)

            val done = awaitItem()
            assertFalse(done.isRefreshing)
        }
    }

    @Test
    fun `anti-detection tests are populated`() = runTest {
        val viewModel = createViewModel()

        viewModel.state.test {
            val state = awaitItem()
            // antiDetectionResults are populated in init via runDiagnostics
            assertTrue(state.antiDetectionResults.isNotEmpty())
            // stack trace test should pass in test environment
            val stackTraceTest =
                state.antiDetectionResults.find {
                    it.nameRes == com.astrixforge.devicemasker.R.string.diagnostics_test_stack_trace
                }
            assertTrue(stackTraceTest?.isPassed == true)
        }
    }

    @Test
    fun `xposed active flow updates state`() = runTest {
        val xposedFlow = MutableStateFlow(false)
        val viewModel = createViewModel(isXposedActiveFlow = xposedFlow)

        viewModel.state.test {
            assertFalse(awaitItem().isXposedActive)

            xposedFlow.value = true
            assertTrue(awaitItem().isXposedActive)
        }
    }

    @Test
    fun `diagnostics failure clears loading and records error`() = runTest {
        val viewModel = createViewModel(diagnosticsProvider = ThrowingDiagnosticsProvider())

        viewModel.state.test {
            val state = awaitItem()
            assertFalse(state.isLoading)
            assertTrue(state.diagnosticsErrorMessage?.contains("diagnostics exploded") == true)
        }
    }
}

private class ThrowingDiagnosticsProvider : IDiagnosticsProvider {
    override suspend fun runDiagnosticTests(repository: ISpoofRepository): List<DiagnosticResult> {
        error("diagnostics exploded")
    }

    override fun runAntiDetectionTests(): List<AntiDetectionTest> = emptyList()
}
