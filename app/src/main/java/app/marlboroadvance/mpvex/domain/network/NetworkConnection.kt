package app.marlboroadvance.mpvex.domain.network

import androidx.compose.runtime.Immutable
import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Represents a network connection configuration
 */
// Perf: stability hint for Compose to skip recomposing rows when same
// instance is supplied (lists of saved connections).
@Immutable
@Entity(tableName = "network_connections")
data class NetworkConnection(
  @PrimaryKey(autoGenerate = true)
  val id: Long = 0,
  val name: String,
  val protocol: NetworkProtocol,
  val host: String,
  val port: Int,
  val username: String = "",
  val password: String = "",
  val path: String = "/",
  val isAnonymous: Boolean = false,
  val lastConnected: Long = 0,
  val autoConnect: Boolean = false,
  val useHttps: Boolean = false,  // For WebDAV: use HTTPS instead of HTTP
)

/**
 * Supported network protocols
 */
enum class NetworkProtocol(val displayName: String, val defaultPort: Int) {
  SMB("SMB", 445),
  FTP("FTP", 21),
  WEBDAV("WebDAV", 80),
}

/**
 * Runtime status of a network connection
 */
@Immutable
data class ConnectionStatus(
  val connectionId: Long,
  val isConnected: Boolean = false,
  val isConnecting: Boolean = false,
  val error: String? = null,
)
