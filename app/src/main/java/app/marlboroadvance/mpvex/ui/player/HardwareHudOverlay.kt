package app.marlboroadvance.mpvex.ui.player

import android.app.ActivityManager
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.BatteryManager
import android.os.SystemClock
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

    if (statisticsPage != 6) return

    val context = LocalContext.current
    var cpuUsage by remember { mutableStateOf("Calculating...") }
    var ramUsage by remember { mutableStateOf("0 MB / 0 MB") }
    var batteryTemp by remember { mutableStateOf("0.0°C") }
    var gpuDriver by remember { mutableStateOf("Checking...") }
    var activeEnvVars by remember { mutableStateOf("None") }

    LaunchedEffect(Unit) {
        var previousCpuTime = android.os.Process.getElapsedCpuTime()
        var previousUptime = SystemClock.elapsedRealtime()
        val numCores = Runtime.getRuntime().availableProcessors()

        while (true) {
            // 1. Read Env Variables
            val prefs = context.getSharedPreferences("gpu_driver_settings", Context.MODE_PRIVATE)
            val varsList = mutableListOf<String>()
            
            if (prefs.getBoolean("env_force_gmem", false)) varsList.add("TURNIP_FORCE_GMEM=1")
            if (prefs.getBoolean("env_disable_sync_fd", false)) varsList.add("TU_DEBUG=nosyncfd")
            if (prefs.getBoolean("env_mailbox_present", false)) varsList.add("MESA_VK_WSI_PRESENT_MODE=mailbox")
            if (prefs.getBoolean("env_debug_shaders", false)) varsList.add("IR3_SHADER_DEBUG=nouboopt")
            
            val customVarsJson = prefs.getString("env_custom_vars", "{}") ?: "{}"
            try {
                val json = org.json.JSONObject(customVarsJson)
                json.keys().forEach { key -> varsList.add("$key=${json.getString(key)}") }
            } catch (e: Exception) {}

            activeEnvVars = if (varsList.isNotEmpty()) varsList.joinToString("\n") else "None"
            
            // 2. Detect if Turnip is actively loaded in the app's memory! (Bulletproof method)
            var isTurnipActive = false
            try {
                // Check the app's own memory map to see if the custom library is hooked
                val mapsFile = File("/proc/self/maps")
                if (mapsFile.exists()) {
                    val mapsData = mapsFile.readText()
                    if (mapsData.contains("freedreno", ignoreCase = true) || 
                        mapsData.contains("vulkan.ad0", ignoreCase = true) || 
                        mapsData.contains("vulkan.purple", ignoreCase = true) ||
                        mapsData.contains("turnip", ignoreCase = true)) {
                        isTurnipActive = true
                    }
                }
            } catch (e: Exception) {}

            // Fallback: Check SharedPreferences blindly for any saved path containing a driver
            if (!isTurnipActive) {
                try {
                    val defaultPrefs = context.getSharedPreferences(context.packageName + "_preferences", Context.MODE_PRIVATE)
                    isTurnipActive = defaultPrefs.all.values.any { 
                        it is String && it.contains("/") && 
                        (it.contains("vulkan", ignoreCase = true) || it.contains("turnip", ignoreCase = true) || it.contains("driver", ignoreCase = true))
                    }
                } catch (e: Exception) {}
            }
            
            // Show Turnip if loaded in memory, saved in prefs, OR variables are forced
            gpuDriver = if (varsList.isNotEmpty() || isTurnipActive) "Mesa Freedreno (Turnip)" else "System Adreno"

            // 3. Read Battery Temp
            val intent = context.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
            val temp = intent?.getIntExtra(BatteryManager.EXTRA_TEMPERATURE, 0) ?: 0
            batteryTemp = "${temp / 10.0}°C"

            // 4. Read RAM Usage
            val activityManager = context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
            val memInfo = ActivityManager.MemoryInfo()
            activityManager.getMemoryInfo(memInfo)
            val totalRamMb = memInfo.totalMem / (1024 * 1024)
            val availRamMb = memInfo.availMem / (1024 * 1024)
            val usedRamMb = totalRamMb - availRamMb
            ramUsage = "$usedRamMb MB / $totalRamMb MB"

            // 5. Calculate App CPU Percentage
            val currentCpuTime = android.os.Process.getElapsedCpuTime()
            val currentUptime = SystemClock.elapsedRealtime()
            
            if (previousUptime > 0) {
                val cpuDelta = currentCpuTime - previousCpuTime
                val timeDelta = currentUptime - previousUptime
                val cpuPercent = (cpuDelta.toFloat() / timeDelta.toFloat()) * 100f / numCores
                cpuUsage = String.format("App Load: %.1f%%", cpuPercent.coerceIn(0f, 100f))
            }
            
            previousCpuTime = currentCpuTime
            previousUptime = currentUptime

            delay(1000) 
        }
    }

    Box(
        modifier = modifier.fillMaxSize().padding(16.dp)
    ) {
        Column(
            modifier = Modifier
                .background(Color(0x99000000))
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
                color = Color(0xFF4CAF50),
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
