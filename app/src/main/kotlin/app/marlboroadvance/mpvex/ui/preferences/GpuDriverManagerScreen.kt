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
import java.io.File
import java.util.zip.ZipInputStream

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
        
        val driversDir = remember { File(context.filesDir, "gpu_drivers").apply { mkdirs() } }

        fun loadDrivers() {
            installedDrivers = driversDir.listFiles()?.filter { it.isDirectory } ?: emptyList()
        }

        LaunchedEffect(Unit) { loadDrivers() }

        val zipPicker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
            if (uri == null) return@rememberLauncherForActivityResult
            scope.launch(Dispatchers.IO) {
                runCatching {
                    val cursor = context.contentResolver.query(uri, null, null, null, null)
                    val zipName = cursor?.use { 
                        if (it.moveToFirst()) it.getString(it.getColumnIndexOrThrow("_display_name")) else "Custom_Driver"
                    }?.removeSuffix(".zip") ?: "Custom_Driver"

                    val targetDir = File(driversDir, zipName)
                    targetDir.mkdirs()

                    var foundDriver = false
                    context.contentResolver.openInputStream(uri)?.use { inputStream ->
                        ZipInputStream(inputStream).use { zis ->
                            var entry = zis.nextEntry
                            while (entry != null) {
                                if (entry.name.endsWith("libvulkan_freedreno.so")) {
                                    val outFile = File(targetDir, "libvulkan_freedreno.so")
                                    outFile.outputStream().use { fos -> zis.copyTo(fos) }
                                    foundDriver = true
                                }
                                zis.closeEntry()
                                entry = zis.nextEntry
                            }
                        }
                    }

                    withContext(Dispatchers.Main) {
                        if (foundDriver) {
                            Toast.makeText(context, "Driver imported!", Toast.LENGTH_SHORT).show()
                            loadDrivers()
                        } else {
                            targetDir.deleteRecursively()
                            Toast.makeText(context, "No libvulkan_freedreno.so found in ZIP", Toast.LENGTH_LONG).show()
                        }
                    }
                }
            }
        }

        Scaffold(
            topBar = {
                TopAppBar(
                    title = { Text("GPU Driver Manager") },
                    navigationIcon = {
                        IconButton(onClick = backStack::removeLastOrNull) {
                            Icon(Icons.AutoMirrored.Default.ArrowBack, contentDescription = "Back")
                        }
                    }
                )
            },
            floatingActionButton = {
                FloatingActionButton(onClick = { zipPicker.launch("application/zip") }) {
                    Icon(Icons.Default.Add, contentDescription = "Install Driver")
                }
            }
        ) { padding ->
            LazyColumn(modifier = Modifier.fillMaxSize().padding(padding).padding(horizontal = 16.dp)) {
                item {
                    Text("Requires app restart to take effect.", color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(bottom = 16.dp))
                }

                // System Driver Option
                item {
                    DriverCard(
                        title = "System GPU driver",
                        subtitle = "Qualcomm (Default)",
                        isSelected = activeDriverDir == null,
                        onClick = {
                            activeDriverDir = null
                            prefs.edit().remove("custom_driver_dir").apply()
                        }
                    )
                }

                // Installed Turnip Drivers
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
        Row(
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            RadioButton(selected = isSelected, onClick = onClick)
            Column(modifier = Modifier.weight(1f).padding(start = 16.dp)) {
                Text(text = title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                Text(text = subtitle, style = MaterialTheme.typography.bodyMedium)
            }
            if (onDelete != null) {
                IconButton(onClick = onDelete) {
                    Icon(Icons.Default.Delete, contentDescription = "Delete", tint = MaterialTheme.colorScheme.error)
                }
            }
        }
    }
}

