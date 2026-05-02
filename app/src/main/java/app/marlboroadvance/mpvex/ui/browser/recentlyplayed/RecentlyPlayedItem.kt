package app.marlboroadvance.mpvex.ui.browser.recentlyplayed

import androidx.compose.runtime.Immutable
import app.marlboroadvance.mpvex.database.entities.PlaylistEntity
import app.marlboroadvance.mpvex.domain.media.model.Video

// Perf: @Immutable on each variant so Compose can skip row recompositions
// when the same item instance is supplied during list updates.
@Immutable
sealed class RecentlyPlayedItem {
  abstract val timestamp: Long

  @Immutable
  data class VideoItem(
    val video: Video,
    override val timestamp: Long,
  ) : RecentlyPlayedItem()

  @Immutable
  data class PlaylistItem(
    val playlist: PlaylistEntity,
    val videoCount: Int,
    val mostRecentVideoPath: String,
    override val timestamp: Long,
  ) : RecentlyPlayedItem()
}
