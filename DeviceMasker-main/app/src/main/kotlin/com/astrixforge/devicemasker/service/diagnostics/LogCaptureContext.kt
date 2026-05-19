package com.astrixforge.devicemasker.service.diagnostics

import kotlinx.serialization.Serializable

@Serializable
data class LogCaptureContext(
    val selectedTargetPackage: String? = null,
    val includeAllScopedTargets: Boolean = true,
    val sinceMillis: Long? = null,
)
