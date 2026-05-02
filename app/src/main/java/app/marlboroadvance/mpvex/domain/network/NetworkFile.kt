package app.marlboroadvance.mpvex.domain.network

import androidx.compose.runtime.Immutable

/**
 * Represents a file or directory on a network share
 */
// Perf: stability hint so list rows aren't recomposed when same instance
// is re-supplied during scrolling/filtering.
@Immutable
data class NetworkFile(
  val name: String,
  val path: String,
  val size: Long,
  val isDirectory: Boolean,
  val lastModified: Long = 0,
  val mimeType: String? = null,
)
