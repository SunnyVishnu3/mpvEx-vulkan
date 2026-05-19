package app.gyrolet.mpvrx.ui.preferences

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import app.gyrolet.mpvrx.domain.ytdl.YtdlManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.koin.compose.koinInject

@Composable
fun YtdlUpdateDialog(
  onDismiss: () -> Unit,
) {
  val ytdlManager = koinInject<YtdlManager>()
  val scope = rememberCoroutineScope()
  
  var logs by remember { mutableStateOf("") }
  var isDownloading by remember { mutableStateOf(false) }
  var error by remember { mutableStateOf<String?>(null) }
  var isFinished by remember { mutableStateOf(false) }

  AlertDialog(
    onDismissRequest = { if (!isDownloading) onDismiss() },
    title = { Text("YTDL Installer") },
    text = {
      Column(modifier = Modifier.padding(8.dp)) {
        if (isDownloading) {
          Text("Installing/Updating...")
          Spacer(modifier = Modifier.height(16.dp))
          LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
          Spacer(modifier = Modifier.height(16.dp))
        }
        Text(
          text = logs.ifEmpty { "Ready to install/update" },
          modifier = Modifier.height(200.dp).verticalScroll(rememberScrollState()),
          style = androidx.compose.ui.text.TextStyle(fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace)
        )
        error?.let {
          Spacer(modifier = Modifier.height(8.dp))
          Text("Error: $it", color = androidx.compose.ui.graphics.Color.Red)
        }
      }
    },
    confirmButton = {
      if (!isFinished && !isDownloading) {
        TextButton(onClick = {
          scope.launch {
            isDownloading = true
            logs = ""
            error = null
            
            val result = ytdlManager.runInstaller { line ->
                logs += "$line\n"
            }
            
            isDownloading = false
            if (result.isSuccess) {
              logs += "\nSuccessfully installed/updated!"
              isFinished = true
            } else {
              error = result.exceptionOrNull()?.message ?: "Unknown error"
              logs += "\nInstallation failed."
            }
          }
        }) {
          Text("Install/Update")
        }
      } else if (isFinished) {
        TextButton(onClick = onDismiss) {
          Text("Done")
        }
      }
    },
    dismissButton = {
      if (!isDownloading) {
        TextButton(onClick = onDismiss) {
          Text("Cancel")
        }
      }
    }
  )
}
