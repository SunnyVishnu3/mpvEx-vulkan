package com.astrixforge.devicemasker.service

import com.astrixforge.devicemasker.data.repository.ISpoofRepository
import com.astrixforge.devicemasker.diagnostics.AntiDetectionTest
import com.astrixforge.devicemasker.diagnostics.DiagnosticResult

interface IDiagnosticsProvider {
    suspend fun runDiagnosticTests(repository: ISpoofRepository): List<DiagnosticResult>

    fun runAntiDetectionTests(): List<AntiDetectionTest>
}
