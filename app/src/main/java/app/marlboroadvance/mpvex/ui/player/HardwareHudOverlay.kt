package app.marlboroadvance.mpvex.ui.player

import android.app.ActivityManager
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.BatteryManager
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.marlboroadvance.mpvex.preferences.AdvancedPreferences
import app.marlboroadvance.mpvex.preferences.preference.collectAsState
import kotlinx.coroutines.delay
import org.koin.compose.koinInject
import java.io.File

@Composable
fun HardwareHudOverlay(modifier: Modifier = Modifier) {
    val advancedPreferences = koinInject<AdvancedPreferences>()
    val statisticsPage by advancedPreferences.enabledStatisticsPage.collectAsState()

    // Only draw the overlay if the user specifically selected Page 6
    if (statisticsPage != 6) return

    val context = LocalContext.current
    var cpuUsage by remember { mutableStateOf("0%") }
    var ramUsage by remember { mutableStateOf("0 MB / 0 MB") }
    var batteryTemp by remember { mutableStateOf("0.0°C") }
    var gpuDriver by remember { mutableStateOf("System Default") }
    var activeEnvVars by remember { mutableStateOf("None") }

    // Poll Hardware Data Every 1 Second
    LaunchedEffect(Unit) {
        // 1. Get GPU Driver & Environment Variables
        val prefs = context.getSharedPreferences("gpu_driver_settings", Context.MODE_PRIVATE)
        
        // Build the list of active Turnip environment variables
        val varsList = mutableListOf<String>()
        if (prefs.getBoolean("env_force_gmem", false)) varsList.add("TURNIP_FORCE_GMEM=1")
        if (prefs.getBoolean("env_disable_sync_fd", false)) varsList.add("TU_DEBUG=nosyncfd")
        if (prefs.getBoolean("env_mailbox_present", false)) varsList.add("MESA_VK_WSI_PRESENT_MODE=mailbox")
        if (prefs.getBoolean("env_debug_shaders", false)) varsList.add("IR3_SHADER_DEBUG=nouboopt")
        
        val customVarsJson = prefs.getString("env_custom_vars", "{}") ?: "{}"
        try {
            val json = org.json.JSONObject(customVarsJson)
            json.keys().forEach { key ->
                varsList.add("$key=${json.getString(key)}")
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }

        activeEnvVars = if (varsList.isNotEmpty()) varsList.joinToString("\n") else "None"
        gpuDriver = if (varsList.isNotEmpty()) "Mesa Freedreno (Turnip)" else "System Adreno"

        while (true) {
            // 2. Read Battery Temp
            val intent = context.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
            val temp = intent?.getIntExtra(BatteryManager.EXTRA_TEMPERATURE, 0) ?: 0
            batteryTemp = "${temp / 10.0}°C"

            // 3. Read RAM Usage
            val activityManager = context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
            val memInfo = ActivityManager.MemoryInfo()
            activityManager.getMemoryInfo(memInfo)
            val totalRamMb = memInfo.totalMem / (1024 * 1024)
            val availRamMb = memInfo.availMem / (1024 * 1024)
            val usedRamMb = totalRamMb - availRamMb
            ramUsage = "$usedRamMb MB / $totalRamMb MB"

            // 4. Estimate CPU Usage (Reading /proc/stat)
            try {
                val statFile = File("/proc/stat")
                if (statFile.exists()) {
                    val line = statFile.readLines().firstOrNull()
                    if (line != null) {
                        val tokens = line.split("\\s+".toRegex()).drop(1).mapNotNull { it.toLongOrNull() }
                        if (tokens.size >= 4) {
                            // Quick active validation
                            cpuUsage = "Active"
                        }
                    }
                }
            } catch (e: Exception) {
                cpuUsage = "Restricted" // Android 10+ restricts /proc/stat
            }

            delay(1000) // Update every second
        }
    }

    // Draw the actual UI overlay (Styled like MPV native stats)
    Box(
        modifier = modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        Column(
            modifier = Modifier
                .background(Color(0x99000000)) // Semi-transparent black background
                .padding(12.dp)
        ) {
            Text(
                text = "=== Hardware HUD ===",
                color = Color.White,
                fontWeight = FontWeight.Bold,
                fontFamily = FontFamily.Monospace,
                fontSize = 14.sp
            )
            Spacer(modifier = Modifier.height(8.dp))
            
            HudRow("GPU Driver", gpuDriver)
            HudRow("Memory (RAM)", ramUsage)
            HudRow("Battery Temp", batteryTemp)
            HudRow("CPU Status", cpuUsage)

            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = "--- Active Env Variables ---",
                color = Color.LightGray,
                fontFamily = FontFamily.Monospace,
                fontSize = 12.sp
            )
            Text(
                text = activeEnvVars,
                color = Color(0xFF4CAF50), // A nice hacker-green for the variables
                fontFamily = FontFamily.Monospace,
                fontSize = 12.sp,
                modifier = Modifier.padding(top = 4.dp, start = 8.dp)
            )
        }
    }
}

@Composable
fun HudRow(label: String, value: String) {
    Row(
        modifier = Modifier.padding(vertical = 2.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Text(text = "$label:", color = Color.Gray, fontFamily = FontFamily.Monospace, fontSize = 12.sp)
        Text(text = value, color = Color.White, fontFamily = FontFamily.Monospace, fontSize = 12.sp)
    }
}
