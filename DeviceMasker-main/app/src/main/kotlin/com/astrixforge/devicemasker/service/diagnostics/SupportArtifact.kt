package com.astrixforge.devicemasker.service.diagnostics

import java.io.File
import kotlinx.serialization.Serializable

enum class ArtifactEncoding {
    TEXT_REDACTED,
    TEXT_RAW,
    BINARY_RAW,
}

data class SupportArtifact(val source: File, val zipPath: String, val encoding: ArtifactEncoding)

@Serializable
data class SupportArtifactManifest(
    val path: String,
    val sourcePath: String,
    val encoding: ArtifactEncoding,
    val byteCount: Long,
)
