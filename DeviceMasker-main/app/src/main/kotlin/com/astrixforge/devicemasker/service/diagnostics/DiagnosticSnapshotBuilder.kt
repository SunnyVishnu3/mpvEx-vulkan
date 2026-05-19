package com.astrixforge.devicemasker.service.diagnostics

import com.astrixforge.devicemasker.common.diagnostics.DiagnosticRedactor
import com.astrixforge.devicemasker.common.diagnostics.RedactionMode
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

@Serializable
data class DiagnosticSnapshotMetadata(
    val appVersion: String,
    val buildType: String,
    val androidSdk: Int,
    val androidRelease: String,
    val device: String,
    val rootAvailable: Boolean,
    val xposedFrameworkConnected: Boolean,
    val moduleEnabled: Boolean,
    val targetPackage: String?,
    val scopePackages: List<String>,
    val droppedLogCount: Int,
)

class DiagnosticSnapshotBuilder(
    private val metadata: DiagnosticSnapshotMetadata,
    private val configJson: String,
    private val remotePrefs: Map<String, String>,
    private val hookHealthJson: String,
) {
    private val json = Json { prettyPrint = true }

    fun build(mode: RedactionMode): Map<String, String> {
        val redactor = DiagnosticRedactor(mode)
        val redactedMetadata =
            metadata.copy(
                targetPackage = metadata.targetPackage?.let(redactor::redactPackage),
                scopePackages =
                    metadata.scopePackages.map { pkg ->
                        if (pkg == "android" || pkg == "system") pkg
                        else redactor.redactPackage(pkg)
                    },
            )
        return mapOf(
            "summary.json" to json.encodeToString(redactedMetadata),
            "config_snapshot_redacted.json" to
                redactPackages(redactor.redactMessage(configJson), redactor),
            "remote_prefs_snapshot_redacted.json" to
                json.encodeToString(
                    remotePrefs.mapValues { (_, value) -> redactor.redactValue(value) }
                ),
            "scope_snapshot.json" to json.encodeToString(redactedMetadata.scopePackages),
            "hook_health.json" to hookHealthJson,
        )
    }

    private fun redactPackages(value: String, redactor: DiagnosticRedactor): String {
        var redacted = value
        metadata.targetPackage?.let { pkg ->
            redacted = redacted.replace(pkg, redactor.redactPackage(pkg))
        }
        metadata.scopePackages.forEach { pkg ->
            if (pkg != "android" && pkg != "system") {
                redacted = redacted.replace(pkg, redactor.redactPackage(pkg))
            }
        }
        return redacted
    }
}
