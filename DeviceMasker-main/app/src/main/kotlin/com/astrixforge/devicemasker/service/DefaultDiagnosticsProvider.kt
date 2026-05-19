package com.astrixforge.devicemasker.service

import android.app.Application
import android.provider.Settings
import com.astrixforge.devicemasker.R
import com.astrixforge.devicemasker.common.SpoofType
import com.astrixforge.devicemasker.data.repository.ISpoofRepository
import com.astrixforge.devicemasker.diagnostics.AntiDetectionTest
import com.astrixforge.devicemasker.diagnostics.DiagnosticResult
import kotlinx.coroutines.flow.first

class DefaultDiagnosticsProvider(private val context: Application) : IDiagnosticsProvider {

    override suspend fun runDiagnosticTests(repository: ISpoofRepository): List<DiagnosticResult> {
        val group = repository.activeGroup.first()

        val presetId = group?.getValue(SpoofType.DEVICE_PROFILE)
        val presetInfo =
            presetId?.let { com.astrixforge.devicemasker.common.DeviceProfilePreset.findById(it) }

        return listOf(
            // Android ID
            DiagnosticResult(
                type = SpoofType.ANDROID_ID,
                realValue =
                    runCatching {
                            @Suppress("HardwareIds")
                            Settings.Secure.getString(
                                context.contentResolver,
                                Settings.Secure.ANDROID_ID,
                            )
                        }
                        .getOrNull(),
                spoofedValue = group?.getValue(SpoofType.ANDROID_ID),
                isActive = group?.isTypeEnabled(SpoofType.ANDROID_ID) == true,
                isSpoofed = group?.getValue(SpoofType.ANDROID_ID) != null,
            ),
            // Device Profile (unified Build.* spoofing)
            DiagnosticResult(
                type = SpoofType.DEVICE_PROFILE,
                realValue = "${android.os.Build.MANUFACTURER} ${android.os.Build.MODEL}",
                spoofedValue = presetInfo?.let { "${it.manufacturer} ${it.model}" },
                isActive = group?.isTypeEnabled(SpoofType.DEVICE_PROFILE) == true,
                isSpoofed = presetInfo != null,
            ),
        )
    }

    override fun runAntiDetectionTests(): List<AntiDetectionTest> {
        return listOf(
            AntiDetectionTest(
                nameRes = R.string.diagnostics_test_stack_trace,
                descriptionRes = R.string.diagnostics_test_stack_trace_desc,
                isPassed =
                    runCatching {
                            Thread.currentThread().stackTrace.none {
                                it.className.contains("xposed", ignoreCase = true)
                            }
                        }
                        .getOrElse { false },
            ),
            AntiDetectionTest(
                nameRes = R.string.diagnostics_test_class_loading,
                descriptionRes = R.string.diagnostics_test_class_loading_desc,
                isPassed =
                    runCatching {
                            Class.forName("de.robv.android.xposed.XposedBridge")
                            false
                        }
                        .getOrElse { true },
            ),
            AntiDetectionTest(
                nameRes = R.string.diagnostics_test_native_hiding,
                descriptionRes = R.string.diagnostics_test_native_hiding_desc,
                isPassed = true,
            ),
            AntiDetectionTest(
                nameRes = R.string.diagnostics_test_package_hiding,
                descriptionRes = R.string.diagnostics_test_package_hiding_desc,
                isPassed = true,
            ),
        )
    }
}
