package app.marlboroadvance.mpvex.ui.preferences

import android.content.Context
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CloudDownload
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import app.marlboroadvance.mpvex.presentation.Screen
import app.marlboroadvance.mpvex.ui.utils.LocalBackStack
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import org.json.JSONArray
import java.io.File
import java.io.InputStream
import java.net.HttpURLConnection
import java.net.URL
import java.util.zip.ZipInputStream

// Data class to hold the specific driver variants from GitHub
data class GitHubAsset(val name: String, val downloadUrl: String)

@Serializable
object GpuDriverManagerScreen : Screen {
    @OptIn(ExperimentalMaterial3Api::class)
    @Composable
    override fun Content() {
        val context = LocalContext.current
        val backStack = LocalBackStack.current
        val scope = rememberCoroutineScope()
        
        val prefs = remember { context.getSharedPreferences("gpu_driver_settings", Context.MODE_PRIVATE) }
        var activeDriverDir by remember { mutableStateOf(prefs.getString("custom_driver_dir", null)) }
        var installedDrivers by remember { mutableStateOf<List<File>>(emptyList()) }
        
        // States for the Fetcher UI
        var isFetchingList by remember { mutableStateOf(false) }
        var isDownloadingAsset by remember { mutableStateOf(false) }
        var showAssetDialog by remember { mutableStateOf(false) }
        var availableAssets by remember { mutableStateOf<List<GitHubAsset>>(emptyList()) }
        var releaseTitle by remember { mutableStateOf("") }
        
        val driversDir = remember { File(context.filesDir, "gpu_drivers").apply { mkdirs() } }

        fun loadDrivers() {
            installedDrivers = driversDir.listFiles()?.filter { dir ->
                dir.isDirectory && dir.listFiles { file -> file.extension == "so" }?.isNotEmpty() == true
            }?.sortedBy { it.name } ?: emptyList()
        }

        LaunchedEffect(Unit) { loadDrivers() }

        fun extractDriverZip(inputStream: InputStream, zipName: String) {
            val targetDir = File(driversDir, zipName.removeSuffix(".zip"))
            targetDir.mkdirs()
            var foundDriver = false
            
            ZipInputStream(inputStream).use { zis ->
                var entry = zis.nextEntry
                while (entry != null) {
                    val fileName = File(entry.name).name
                    if (fileName.endsWith(".so")) {
                        val outFile = File(targetDir, fileName)
                        outFile.outputStream().use { fos -> zis.copyTo(fos) }
                        foundDriver = true
                    }
                    zis.closeEntry()
                    entry = zis.nextEntry
                }
            }
            if (foundDriver) {
                loadDrivers()
            } else {
                targetDir.deleteRecursively()
            }
        }

        val zipPicker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
            if (uri == null) return@rememberLauncherForActivityResult
            scope.launch(Dispatchers.IO) {
                runCatching {
                    val cursor = context.contentResolver.query(uri, null, null, null, null)
                    val zipName = cursor?.use { 
                        if (it.moveToFirst()) it.getString(it.getColumnIndexOrThrow("_display_name")) else "Local_Driver"
                    }?.removeSuffix(".zip") ?: "Local_Driver"

                    context.contentResolver.openInputStream(uri)?.use { extractDriverZip(it, zipName) }
                }
            }
        }

        // 1. Fetch the list of available drivers (Autotuner, GMEM, SYMEM, etc.)
        fun fetchAvailableDrivers() {
            if (isFetchingList) return
            isFetchingList = true
            scope.launch(Dispatchers.IO) {
                try {
                    val apiUrl = URL("https://api.github.com/repos/K11MCH1/AdrenoToolsDrivers/releases/latest")
                    val connection = apiUrl.openConnection() as HttpURLConnection
                    connection.setRequestProperty("Accept", "application/vnd.github.v3+json")
                    
                    val response = connection.inputStream.bufferedReader().readText()
                    val json = org.json.JSONObject(response)
                    
                    releaseTitle = json.getString("name")
                    val assetsArray = json.getJSONArray("assets")
                    val fetchedAssets = mutableListOf<GitHubAsset>()
                    
                    for (i in 0 until assetsArray.length()) {
                        val asset = assetsArray.getJSONObject(i)
                        val name = asset.getString("name")
                        if (name.endsWith(".zip")) {
                            fetchedAssets.add(GitHubAsset(name, asset.getString("browser_download_url")))
                        }
                    }

                    withContext(Dispatchers.Main) {
                        availableAssets = fetchedAssets
                        showAssetDialog = true
                    }
                } catch (e: Exception) {
                    withContext(Dispatchers.Main) { Toast.makeText(context, "Fetch failed: ${e.message}", Toast.LENGTH_SHORT).show() }
                    e.printStackTrace()
                } finally {
                    isFetchingList = false
                }
            }
        }

        // 2. Download the specific driver variant the user tapped
        fun downloadSpecificDriver(asset: GitHubAsset) {
            showAssetDialog = false
            isDownloadingAsset = true
            scope.launch(Dispatchers.IO) {
                try {
                    withContext(Dispatchers.Main) { Toast.makeText(context, "Downloading ${asset.name}...", Toast.LENGTH_SHORT).show() }
                    URL(asset.downloadUrl).openStream().use { extractDriverZip(it, asset.name) }
                    withContext(Dispatchers.Main) { Toast.makeText(context, "Install Complete!", Toast.LENGTH_SHORT).show() }
                } catch (e: Exception) {
                    withContext(Dispatchers.Main) { Toast.makeText(context, "Download failed.", Toast.LENGTH_SHORT).show() }
                    e.printStackTrace()
                } finally {
                    isDownloadingAsset = false
                }
            }
        }

        // 3. The Dialog UI showing the fetched drivers
        if (showAssetDialog) {
            AlertDialog(
                onDismissRequest = { showAssetDialog = false },
                title = { Text(text = "Available Drivers ($releaseTitle)", fontWeight = FontWeight.Bold) },
                text = {
                    LazyColumn(modifier = Modifier.fillMaxWidth()) {
                        items(availableAssets) { asset ->
                            Card(
                                modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp).clickable { downloadSpecificDriver(asset) },
                                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer)
                            ) {
                                Text(
                                    text = asset.name, 
                                    modifier = Modifier.padding(16.dp),
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = FontWeight.Medium
                                )
                            }
                        }
                    }
                },
                confirmButton = { TextButton(onClick = { showAssetDialog = false }) { Text("Cancel") } }
            )
        }

        Scaffold(
            topBar = {
                TopAppBar(
                    title = { Text("GPU Driver Manager") },
                    navigationIcon = {
                        IconButton(onClick = backStack::removeLastOrNull) {
                            Icon(Icons.AutoMirrored.Default.ArrowBack, contentDescription = "Back")
                        }
                    },
                    actions = {
                        IconButton(onClick = { fetchAvailableDrivers() }) {
                            if (isFetchingList || isDownloadingAsset) {
                                CircularProgressIndicator(modifier = Modifier.size(24.dp), strokeWidth = 2.dp)
                            } else {
                                Icon(Icons.Default.CloudDownload, contentDescription = "Fetch Drivers")
                            }
                        }
                    }
                )
            },
            floatingActionButton = {
                FloatingActionButton(onClick = { zipPicker.launch("application/zip") }) {
                    Icon(Icons.Default.Add, contentDescription = "Install Local ZIP")
                }
            }
        ) { padding ->
            LazyColumn(modifier = Modifier.fillMaxSize().padding(padding).padding(horizontal = 16.dp)) {
                item {
                    Text("Requires app restart to take effect.", color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(bottom = 16.dp))
                }

                item {
                    DriverCard(
                        title = "System GPU driver",
                        subtitle = "Qualcomm Default",
                        isSelected = activeDriverDir == null,
                        onClick = {
                            activeDriverDir = null
                            prefs.edit().remove("custom_driver_dir").apply()
                        }
                    )
                }

                items(installedDrivers) { driverFolder ->
                    DriverCard(
                        title = driverFolder.name,
                        subtitle = "Custom Turnip Driver",
                        isSelected = activeDriverDir == driverFolder.absolutePath,
                        onClick = {
                            activeDriverDir = driverFolder.absolutePath
                            prefs.edit().putString("custom_driver_dir", driverFolder.absolutePath).apply()
                        },
                        onDelete = {
                            if (activeDriverDir == driverFolder.absolutePath) {
                                activeDriverDir = null
                                prefs.edit().remove("custom_driver_dir").apply()
                            }
                            driverFolder.deleteRecursively()
                            loadDrivers()
                        }
                    )
                }
            }
        }
    }
}

@Composable
fun DriverCard(title: String, subtitle: String, isSelected: Boolean, onClick: () -> Unit, onDelete: (() -> Unit)? = null) {
    ElevatedCard(
        modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp).clickable(onClick = onClick),
        colors = CardDefaults.elevatedCardColors(
            containerColor = if (isSelected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surface
        )
    ) {
        Row(modifier = Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            RadioButton(selected = isSelected, onClick = onClick)
            Column(modifier = Modifier.weight(1f).padding(start = 16.dp)) {
                Text(text = title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                Text(text = subtitle, style = MaterialTheme.typography.bodyMedium)
            }
            if (onDelete != null) {
                IconButton(onClick = onDelete) { Icon(Icons.Default.Delete, contentDescription = "Delete", tint = MaterialTheme.colorScheme.error) }
            }
        }
    }
}
