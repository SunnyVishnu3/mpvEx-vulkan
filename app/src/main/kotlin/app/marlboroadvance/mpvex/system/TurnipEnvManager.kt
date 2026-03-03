package app.marlboroadvance.mpvex.system

import android.content.Context
import android.content.SharedPreferences
import org.json.JSONObject

class TurnipEnvManager(context: Context) {
    private val prefs: SharedPreferences = context.getSharedPreferences("gpu_driver_settings", Context.MODE_PRIVATE)

    var forceGmem: Boolean
        get() = prefs.getBoolean("env_force_gmem", false)
        set(value) = prefs.edit().putBoolean("env_force_gmem", value).apply()

    var disableSyncFd: Boolean
        get() = prefs.getBoolean("env_disable_sync_fd", false)
        set(value) = prefs.edit().putBoolean("env_disable_sync_fd", value).apply()

    var mailboxPresent: Boolean
        get() = prefs.getBoolean("env_mailbox_present", false)
        set(value) = prefs.edit().putBoolean("env_mailbox_present", value).apply()

    var debugShaders: Boolean
        get() = prefs.getBoolean("env_debug_shaders", false)
        set(value) = prefs.edit().putBoolean("env_debug_shaders", value).apply()

    fun getCustomVars(): Map<String, String> {
        val jsonStr = prefs.getString("env_custom_vars", "{}") ?: "{}"
        val map = mutableMapOf<String, String>()
        try {
            val json = JSONObject(jsonStr)
            json.keys().forEach { key ->
                map[key] = json.getString(key)
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        return map
    }

    fun addCustomVar(key: String, value: String) {
        if (key.isBlank() || value.isBlank()) return
        val map = getCustomVars().toMutableMap()
        map[key.trim()] = value.trim()
        saveCustomVars(map)
    }

    fun removeCustomVar(key: String) {
        val map = getCustomVars().toMutableMap()
        map.remove(key)
        saveCustomVars(map)
    }

    private fun saveCustomVars(map: Map<String, String>) {
        val json = JSONObject()
        map.forEach { (k, v) -> json.put(k, v) }
        prefs.edit().putString("env_custom_vars", json.toString()).apply()
    }

    /**
     * Reads all active settings and safely injects them into the provided C++ JNI bridge function
     */
    fun applyToDriver(injector: (String, String) -> Boolean) {
        if (forceGmem) injector("TURNIP_FORCE_GMEM", "1")
        
        val tuDebugFlags = mutableListOf<String>()
        if (disableSyncFd) tuDebugFlags.add("nosyncfd")
        
        if (tuDebugFlags.isNotEmpty()) {
            injector("TU_DEBUG", tuDebugFlags.joinToString(","))
        }

        if (mailboxPresent) injector("MESA_VK_WSI_PRESENT_MODE", "mailbox")
        if (debugShaders) injector("IR3_SHADER_DEBUG", "nouboopt")

        // Inject all user-defined custom variables
        getCustomVars().forEach { (key, value) ->
            injector(key, value)
        }
    }
}

