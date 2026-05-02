package app.marlboroadvance.mpvex.domain.browser

import androidx.compose.runtime.Immutable
import app.marlboroadvance.mpvex.domain.media.model.Video

/**
 * Represents an item in the filesystem browser (either a folder or a video file)
 */
// Perf: @Immutable lets Compose treat these as stable inputs and skip
// unnecessary row recompositions when the same instance is passed in.
@Immutable
sealed class FileSystemItem {
  abstract val name: String
  abstract val path: String
  abstract val lastModified: Long

  @Immutable
  data class Folder(
    override val name: String,
    override val path: String,
    override val lastModified: Long,
    val videoCount: Int = 0,
    val totalSize: Long = 0L,
    val totalDuration: Long = 0L,
    val hasSubfolders: Boolean = false,
  ) : FileSystemItem()

  @Immutable
  data class VideoFile(
    override val name: String,
    override val path: String,
    override val lastModified: Long,
    val video: Video,
  ) : FileSystemItem()
}

/**
 * Represents a path component in the breadcrumb navigation
 */
// Perf: stability hint for breadcrumb rendering.
@Immutable
data class PathComponent(
  val name: String,
  val fullPath: String,
)
