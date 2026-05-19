package com.astrixforge.devicemasker.common.diagnostics

import java.util.concurrent.atomic.AtomicInteger
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

val DiagnosticJson: Json = Json {
    encodeDefaults = true
    ignoreUnknownKeys = true
    prettyPrint = false
}

@Serializable
enum class DiagnosticSource {
    APP,
    XPOSED,
    SYSTEM_SERVER,
    LOGCAT,
    LSPOSED,
    ROOT,
    ADB,
}

@Serializable
enum class DiagnosticSeverity {
    VERBOSE,
    DEBUG,
    INFO,
    WARN,
    ERROR,
    FATAL,
}

@Serializable
enum class DiagnosticEventType {
    APP_START,
    APP_LOG,
    XPOSED_SERVICE_CONNECTED,
    XPOSED_SERVICE_DIED,
    REMOTE_PREFS_SYNC_STARTED,
    REMOTE_PREFS_SYNC_COMMITTED,
    REMOTE_PREFS_SYNC_FAILED,
    XPOSED_ENTRY_LOADED,
    REMOTE_PREFS_UNAVAILABLE,
    TARGET_PACKAGE_SELECTED,
    HOOK_REGISTRATION_STARTED,
    HOOK_REGISTERED,
    HOOK_FAILED,
    HOOK_SKIPPED,
    HOOK_DEOPTIMIZE_FAILED,
    SPOOF_RETURNED,
    SPOOF_PASSTHROUGH,
    DIAGNOSTICS_SERVICE_LOG,
    DIAGNOSTICS_SERVICE_CLEARED,
    ROOT_UNAVAILABLE,
    ROOT_COMMAND_STARTED,
    ROOT_COMMAND_COMPLETED,
    ROOT_COMMAND_FAILED,
    ROOT_LOGCAT_COLLECTED,
    ROOT_ANR_COLLECTED,
    ROOT_TOMBSTONE_COLLECTED,
    ROOT_DUMPSYS_COLLECTED,
    ROOT_GETPROP_COLLECTED,
    SUPPORT_BUNDLE_STARTED,
    SUPPORT_BUNDLE_CREATED,
    SUPPORT_BUNDLE_FAILED,
    SNAPSHOT_CREATED,
}

@Serializable
data class DiagnosticEvent(
    val eventId: String,
    val timestampWallMillis: Long,
    val timestampElapsedMillis: Long,
    val sessionId: String,
    val bootId: String,
    val source: DiagnosticSource,
    val severity: DiagnosticSeverity = DiagnosticSeverity.INFO,
    val eventType: DiagnosticEventType,
    val processName: String? = null,
    val packageName: String? = null,
    val pid: Int? = null,
    val tid: Int? = null,
    val threadName: String? = null,
    val hooker: String? = null,
    val method: String? = null,
    val spoofType: String? = null,
    val status: String? = null,
    val reason: String? = null,
    val configVersion: Long? = null,
    val prefsVersion: Long? = null,
    val moduleEnabled: Boolean = false,
    val appEnabled: Boolean = false,
    val message: String,
    val throwableClass: String? = null,
    val stacktrace: List<String> = emptyList(),
    val extras: Map<String, String> = emptyMap(),
) {
    companion object {
        private val nextSequence = AtomicInteger(0)

        fun nextEventId(timestampWallMillis: Long): String {
            val sequence =
                nextSequence.updateAndGet { current ->
                    if (current >= MAX_SEQUENCE) 1 else current + 1
                }
            return "evt_${timestampWallMillis}_${sequence.toString().padStart(SEQUENCE_WIDTH, '0')}"
        }

        private const val MAX_SEQUENCE = 999_999
        private const val SEQUENCE_WIDTH = 6
    }
}
