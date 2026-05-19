package com.astrixforge.devicemasker.service.diagnostics

import com.astrixforge.devicemasker.common.diagnostics.DiagnosticEvent
import com.astrixforge.devicemasker.common.diagnostics.DiagnosticEventType
import com.astrixforge.devicemasker.common.diagnostics.DiagnosticJson
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString

@Serializable
data class ParsedHookHealthSnapshot(
    val source: String,
    val totalEvents: Int,
    val xposedEntryLoaded: Int,
    val targetPackageSelected: Int,
    val hookRegistered: Int,
    val hookFailed: Int,
    val hookSkipped: Int,
    val spoofReturned: Int,
    val spoofPassthrough: Int,
    val errorEvents: Int,
)

object ParsedHookHealthSnapshotBuilder {
    fun buildJson(events: List<DiagnosticEvent>): String =
        DiagnosticJson.encodeToString(
            ParsedHookHealthSnapshot(
                source = "parsed_xposed_export",
                totalEvents = events.size,
                xposedEntryLoaded = events.countType(DiagnosticEventType.XPOSED_ENTRY_LOADED),
                targetPackageSelected =
                    events.countType(DiagnosticEventType.TARGET_PACKAGE_SELECTED),
                hookRegistered = events.countType(DiagnosticEventType.HOOK_REGISTERED),
                hookFailed = events.countType(DiagnosticEventType.HOOK_FAILED),
                hookSkipped = events.countType(DiagnosticEventType.HOOK_SKIPPED),
                spoofReturned = events.countType(DiagnosticEventType.SPOOF_RETURNED),
                spoofPassthrough = events.countType(DiagnosticEventType.SPOOF_PASSTHROUGH),
                errorEvents =
                    events.count { it.severity.name == "ERROR" || it.severity.name == "FATAL" },
            )
        )

    private fun List<DiagnosticEvent>.countType(type: DiagnosticEventType): Int = count {
        it.eventType == type
    }
}
