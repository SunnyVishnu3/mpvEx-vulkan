package app.marlboroadvance.mpvex.system

import android.content.Context
import android.util.Log
import java.io.File

object AdrenoTools {
    private const val TAG = "AdrenoTools"
    var isBridgeLoaded = false
        private set

    init {
        try {
            System.loadLibrary("adrenotools_bridge")
            // Force Android to unpack the bait file into the trusted lib directory!
            runCatching { System.loadLibrary("vulkan_freedreno") }
            isBridgeLoaded = true
            Log.i(TAG, "Native bridge loaded successfully")
        } catch (e: UnsatisfiedLinkError) {
            isBridgeLoaded = false
            Log.w(TAG, "Adrenotools not supported on this architecture", e)
        } catch (e: Exception) {
            isBridgeLoaded = false
            Log.e(TAG, "Failed to load adrenotools bridge", e)
        }
    }

    @JvmStatic
    private external fun nativeHookDriver(
        tmpLibDir: String, 
        hookLibDir: String, 
        customDriverDir: String,
        driverName: String
    ): Boolean

    fun hookCustomDriver(context: Context, driverDir: String): Boolean {
        if (!isBridgeLoaded) return false
        
        val dir = File(driverDir)
        if (!dir.exists() || !dir.isDirectory) return false
        
        val driverFile = dir.listFiles { file -> 
            file.extension == "so" && file.name.contains("vulkan", ignoreCase = true)
        }?.firstOrNull() ?: dir.listFiles { file -> file.extension == "so" }?.firstOrNull()

        if (driverFile == null) {
            Log.e(TAG, "No valid .so driver file found in $driverDir")
            return false
        }

        val driverName = driverFile.name
        Log.i(TAG, "Found dynamic driver library: $driverName")
        
        runCatching {
            dir.setExecutable(true, false)
            dir.setReadable(true, false)
            driverFile.setExecutable(true, false)
            driverFile.setReadable(true, false)
        }

        val tmpDir = context.getDir("vulkan_tmp", Context.MODE_PRIVATE)
        val tmpLibDir = tmpDir.absolutePath
        val hookLibDir = context.applicationInfo.nativeLibraryDir
        
        val baitFile = File(hookLibDir, "libvulkan_freedreno.so")
        Log.i(TAG, "Checking Kotlin for bait library at: ${baitFile.absolutePath} - Exists: ${baitFile.exists()}")

        return nativeHookDriver(tmpLibDir, hookLibDir, driverDir, driverName)
    }
}
