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
import org.json.JSONObject
import java.io.File
import java.io.InputStream
import java.net.HttpURLConnection
import java.net.URL
import java.util.zip.ZipInputStream

// Data classes for GitHub integration and Local UI states
data class GitHubAsset(val displayName: String, val fileName: String, val downloadUrl: String)
data class DriverItem(val folder: File, val displayName: String, val displaySubtitle: String)

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
        var installedDrivers by remember { mutableStateOf<List<DriverItem>>(emptyList()) }
        
        // States for the Fetcher UI
        var isFetchingList by remember { mutableStateOf(false) }
        var isDownloadingAsset by remember { mutableStateOf(false) }
        var showAssetDialog by remember { mutableStateOf(false) }
        var availableAssets by remember { mutableStateOf<List<GitHubAsset>>(emptyList()) }
        
        val driversDir = remember { File(context.filesDir, "gpu_drivers").apply { mkdirs() } }

        fun loadDrivers() {
            val currentDrivers = mutableListOf<DriverItem>()
            val folders = driversDir.listFiles()?.filter { it.isDirectory } ?: emptyList()
            
            for (folder in folders) {
                val hasSoFile = folder.listFiles { file -> file.extension == "so" }?.isNotEmpty() == true
                if (hasSoFile) {
                    val metaFile = File(folder, "meta.json")
                    var dName = folder.name
                    var dSubtitle = "Custom Turnip Driver"
                    
                    // JSON Metadata Extractor
                    if (metaFile.exists()) {
                        try {
                            val jsonText = metaFile.readText()
                            val json = JSONObject(jsonText)
                            dName = json.optString("name", folder.name)
                            
                            val version = json.optString("driverVersion", "")
                            val vendor = json.optString("vendor", "Custom")
                            val api = json.optString("api", "Vulkan")
                            dSubtitle = "$vendor | $api | v$version"
                        } catch (e: Exception) {
                            e.printStackTrace()
                        }
                    }
                    currentDrivers.add(DriverItem(folder, dName, dSubtitle))
                }
            }
            installedDrivers = currentDrivers.sortedBy { it.displayName }
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
                    // Keep both .so files and meta.json!
                    if (fileName.endsWith(".so") || fileName.equals("meta.json", ignoreCase = true)) {
                        val outFile = File(targetDir, fileName)
                        outFile.outputStream().use { fos -> zis.copyTo(fos) }
                        if (fileName.endsWith(".so")) foundDriver = true
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

        // Fetch the full list of available drivers across recent releases
        fun fetchAvailableDrivers() {
            if (isFetchingList) return
            isFetchingList = true
            scope.launch(Dispatchers.IO) {
                try {
                    // Changed to general /releases endpoint to fetch the entire history
                    val apiUrl = URL("https://api.github.com/repos/K11MCH1/AdrenoToolsDrivers/releases")
                    val connection = apiUrl.openConnection() as HttpURLConnection
                    connection.setRequestProperty("Accept", "application/vnd.github.v3+json")
                    
                    val response = connection.inputStream.bufferedReader().readText()
                    val jsonArray = JSONArray(response)
                    val fetchedAssets = mutableListOf<GitHubAsset>()
                    
                    // Loop through the recent releases (limit to 10 to avoid massive dialogs)
                    val limit = if (jsonArray.length() > 10) 10 else jsonArray.length()
                    for (i in 0 until limit) {
                        val release = jsonArray.getJSONObject(i)
                        val releaseName = release.optString("name", "Release")
                        val assetsArray = release.getJSONArray("assets")
                        
                        // Loop through all zips attached to this specific release
                        for (j in 0 until assetsArray.length()) {
                            val asset = assetsArray.getJSONObject(j)
                            val fileName = asset.getString("name")
                            if (fileName.endsWith(".zip")) {
                                fetchedAssets.add(
                                    GitHubAsset(
                                        displayName = "$releaseName\n$fileName",
                                        fileName = fileName,
                                        downloadUrl = asset.getString("browser_download_url")
                                    )
                                )
                            }
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

        // Download specific tapped variant
        fun downloadSpecificDriver(asset: GitHubAsset) {
            showAssetDialog = false
            isDownloadingAsset = true
            scope.launch(Dispatchers.IO) {
                try {
                    withContext(Dispatchers.Main) { Toast.makeText(context, "Downloading ${asset.fileName}...", Toast.LENGTH_SHORT).show() }
                    URL(asset.downloadUrl).openStream().use { extractDriverZip(it, asset.fileName) }
                    withContext(Dispatchers.Main) { Toast.makeText(context, "Install Complete!", Toast.LENGTH_SHORT).show() }
                } catch (e: Exception) {
                    withContext(Dispatchers.Main) { Toast.makeText(context, "Download failed.", Toast.LENGTH_SHORT).show() }
                    e.printStackTrace()
                } finally {
                    isDownloadingAsset = false
                }
            }
        }

        // The Dialog UI showing fetched drivers
        if (showAssetDialog) {
            AlertDialog(
                onDismissRequest = { showAssetDialog = false },
                title = { Text(text = "Available GitHub Drivers", fontWeight = FontWeight.Bold) },
                text = {
                    LazyColumn(modifier = Modifier.fillMaxWidth()) {
                        items(availableAssets) { asset ->
                            Card(
                                modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp).clickable { downloadSpecificDriver(asset) },
                                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer)
                            ) {
                                Text(
                                    text = asset.displayName, 
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

                items(installedDrivers) { driverItem ->
                    DriverCard(
                        title = driverItem.displayName,
                        subtitle = driverItem.displaySubtitle,
                        isSelected = activeDriverDir == driverItem.folder.absolutePath,
                        onClick = {
                            activeDriverDir = driverItem.folder.absolutePath
                            prefs.edit().putString("custom_driver_dir", driverItem.folder.absolutePath).apply()
                        },
                        onDelete = {
                            if (activeDriverDir == driverItem.folder.absolutePath) {
                                activeDriverDir = null
                                prefs.edit().remove("custom_driver_dir").apply()
                            }
                            driverItem.folder.deleteRecursively()
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
