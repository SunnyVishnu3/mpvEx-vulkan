package app.marlboroadvance.mpvex.database.entities

import androidx.compose.runtime.Immutable
import androidx.room.Entity
import androidx.room.PrimaryKey

// Perf: stability hint for Compose so list rows can skip recomposition
// when the same instance is supplied during scrolling/refresh.
@Immutable
@Entity
data class RecentlyPlayedEntity(
  @PrimaryKey(autoGenerate = true) val id: Int = 0,
  val filePath: String,
  val fileName: String,
  val videoTitle: String? = null,
  val duration: Long = 0,
  val fileSize: Long = 0,
  val width: Int = 0,
  val height: Int = 0,
  val timestamp: Long,
  val launchSource: String? = null,
  val playlistId: Int? = null,
)
