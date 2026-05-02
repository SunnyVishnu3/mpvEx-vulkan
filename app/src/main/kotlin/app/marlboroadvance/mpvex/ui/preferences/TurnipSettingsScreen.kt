package app.marlboroadvance.mpvex.ui.preferences

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
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
import app.marlboroadvance.mpvex.system.TurnipEnvManager
import app.marlboroadvance.mpvex.ui.utils.LocalBackStack
import kotlinx.serialization.Serializable

@Serializable
object TurnipSettingsScreen : Screen {
    @OptIn(ExperimentalMaterial3Api::class)
    @Composable
    override fun Content() {
        val context = LocalContext.current
        val backStack = LocalBackStack.current
        val envManager = remember { TurnipEnvManager(context) }
        
        // Settings States
        var forceGmem by remember { mutableStateOf(envManager.forceGmem) }
        var disableSyncFd by remember { mutableStateOf(envManager.disableSyncFd) }
        var mailboxPresent by remember { mutableStateOf(envManager.mailboxPresent) }
        var debugShaders by remember { mutableStateOf(envManager.debugShaders) }

        // Custom Variables State
        var customVars by remember { mutableStateOf(envManager.getCustomVars()) }
        var showAddVarDialog by remember { mutableStateOf(false) }

        Scaffold(
            topBar = {
                TopAppBar(
                    title = { Text("Turnip Settings") },
                    navigationIcon = {
                        IconButton(onClick = backStack::removeLastOrNull) {
                            Icon(Icons.AutoMirrored.Default.ArrowBack, contentDescription = "Back")
                        }
                    }
                )
            },
            floatingActionButton = {
                FloatingActionButton(onClick = { showAddVarDialog = true }) {
                    Icon(Icons.Default.Add, contentDescription = "Add Custom Variable")
                }
            }
        ) { padding ->
            LazyColumn(modifier = Modifier.fillMaxSize().padding(padding).padding(horizontal = 16.dp)) {
                item {
                    Text(
                        "Configure Mesa Freedreno environment variables. Requires an app restart to apply.", 
                        color = MaterialTheme.colorScheme.error, 
                        modifier = Modifier.padding(bottom = 16.dp)
                    )
                }

                item { Text("Common Optimizations", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary, modifier = Modifier.padding(vertical = 8.dp)) }

                item {
                    SwitchPreferenceRow(
                        title = "Force GMEM",
                        subtitle = "Forces Adreno tile-based rendering (TURNIP_FORCE_GMEM=1)",
                        checked = forceGmem,
                        onCheckedChange = { forceGmem = it; envManager.forceGmem = it }
                    )
                    HorizontalDivider()
                }
                item {
                    SwitchPreferenceRow(
                        title = "Disable Sync FD",
                        subtitle = "Fixes screen flickering on modern Android OS (TU_DEBUG=nosyncfd)",
                        checked = disableSyncFd,
                        onCheckedChange = { disableSyncFd = it; envManager.disableSyncFd = it }
                    )
                    HorizontalDivider()
                }
                item {
                    SwitchPreferenceRow(
                        title = "Mailbox Presentation",
                        subtitle = "Forces uncapped frame submission to SurfaceFlinger",
                        checked = mailboxPresent,
                        onCheckedChange = { mailboxPresent = it; envManager.mailboxPresent = it }
                    )
                    HorizontalDivider()
                }
                item {
                    SwitchPreferenceRow(
                        title = "IR3 Shader Debug",
                        subtitle = "Logs compiler variables (IR3_SHADER_DEBUG=nouboopt)",
                        checked = debugShaders,
                        onCheckedChange = { debugShaders = it; envManager.debugShaders = it }
                    )
                }

                item { 
                    Spacer(modifier = Modifier.height(24.dp))
                    Text("Custom Variables", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary, modifier = Modifier.padding(vertical = 8.dp)) 
                }

                customVars.forEach { (key, value) ->
                    item {
                        Card(
                            modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
                        ) {
                            Row(modifier = Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(text = key, fontWeight = FontWeight.Bold)
                                    Text(text = value, style = MaterialTheme.typography.bodyMedium)
                                }
                                IconButton(onClick = {
                                    envManager.removeCustomVar(key)
                                    customVars = envManager.getCustomVars()
                                }) {
                                    Icon(Icons.Default.Delete, contentDescription = "Delete", tint = MaterialTheme.colorScheme.error)
                                }
                            }
                        }
                    }
                }
            }
        }

        if (showAddVarDialog) {
            var varName by remember { mutableStateOf("") }
            var varValue by remember { mutableStateOf("") }
            AlertDialog(
                onDismissRequest = { showAddVarDialog = false },
                title = { Text("Add Custom Variable") },
                text = {
                    Column {
                        OutlinedTextField(value = varName, onValueChange = { varName = it }, label = { Text("Variable Name (e.g. TU_DEBUG)") }, singleLine = true)
                        Spacer(modifier = Modifier.height(8.dp))
                        OutlinedTextField(value = varValue, onValueChange = { varValue = it }, label = { Text("Value (e.g. sysmem)") }, singleLine = true)
                    }
                },
                confirmButton = {
                    TextButton(onClick = {
                        envManager.addCustomVar(varName, varValue)
                        customVars = envManager.getCustomVars()
                        showAddVarDialog = false
                    }) { Text("Save") }
                },
                dismissButton = { TextButton(onClick = { showAddVarDialog = false }) { Text("Cancel") } }
            )
        }
    }
}

@Composable
fun SwitchPreferenceRow(title: String, subtitle: String, checked: Boolean, onCheckedChange: (Boolean) -> Unit) {
    Row(modifier = Modifier.fillMaxWidth().padding(vertical = 12.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
        Column(modifier = Modifier.weight(1f).padding(end = 16.dp)) {
            Text(text = title, style = MaterialTheme.typography.titleMedium)
            Text(text = subtitle, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Switch(checked = checked, onCheckedChange = onCheckedChange)
    }
}

