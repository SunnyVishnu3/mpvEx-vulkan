package com.astrixforge.devicemasker.service.diagnostics

import android.content.Context
import android.os.Build
import com.astrixforge.devicemasker.BuildConfig
import com.astrixforge.devicemasker.DeviceMaskerApp
import com.astrixforge.devicemasker.common.diagnostics.RedactionMode
import com.astrixforge.devicemasker.data.XposedPrefs
import com.astrixforge.devicemasker.data.XposedScopeState
import com.astrixforge.devicemasker.service.AppLogStore
import com.astrixforge.devicemasker.service.ConfigManager

interface SupportSnapshotProvider {
    fun buildSnapshots(): Map<String, String>
}

class DefaultSupportSnapshotProvider(private val context: Context) : SupportSnapshotProvider {
    override fun buildSnapshots(): Map<String, String> {
        val scopePackages = XposedPrefs.scopedPackages.value.connectedPackages()
        val metadata =
            DiagnosticSnapshotMetadata(
                appVersion = appVersionName(),
                buildType = BuildConfig.BUILD_TYPE,
                androidSdk = Build.VERSION.SDK_INT,
                androidRelease = Build.VERSION.RELEASE ?: "unknown",
                device = "${Build.MANUFACTURER} ${Build.MODEL}",
                rootAvailable = RootAccessManager.hasGrantedRoot(),
                xposedFrameworkConnected = XposedPrefs.isConnected(),
                moduleEnabled = XposedPrefs.isConnected(),
                targetPackage = null,
                scopePackages = scopePackages,
                droppedLogCount = droppedLogCount(),
            )
        return DiagnosticSnapshotBuilder(
                metadata = metadata,
                configJson = ConfigManager.config.value.toJsonString(),
                remotePrefs =
                    XposedPrefs.getPrefs()?.all?.mapValues { it.value.toString() }.orEmpty(),
                hookHealthJson = "{}",
            )
            .build(RedactionMode.REDACTED)
    }

    private fun appVersionName(): String =
        context.packageManager.getPackageInfo(context.packageName, 0).versionName ?: "unknown"

    private fun droppedLogCount(): Int =
        runCatching { DeviceMaskerApp.appLogStore.queueDroppedEventCount() }
            .getOrElse { AppLogStore.from(context).queueDroppedEventCount() }
}

private fun XposedScopeState.connectedPackages(): List<String> =
    when (this) {
        is XposedScopeState.Connected -> packages.sorted()
        XposedScopeState.Disconnected -> emptyList()
        is XposedScopeState.Error -> emptyList()
    }
