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

    // EDEN FORK INJECTOR
    @JvmStatic
    private external fun nativeSetEnv(name: String, value: String): Boolean

    fun hookCustomDriver(context: Context, driverDir: String): Boolean {
        if (!isBridgeLoaded) {
            Log.e(TAG, "Bridge not loaded; cannot hook driver")
            return false
        }

        Log.i(TAG, "hookCustomDriver() called with driverDir=$driverDir")

        val dir = File(driverDir)
        if (!dir.exists() || !dir.isDirectory) {
            Log.e(TAG, "driverDir does not exist or is not a directory: $driverDir")
            return false
        }

        val driverFile = dir.listFiles { file ->
            file.extension == "so" && file.name.contains("vulkan", ignoreCase = true)
        }?.firstOrNull() ?: dir.listFiles { file -> file.extension == "so" }?.firstOrNull()

        if (driverFile == null) {
            Log.e(TAG, "No valid .so driver file found in $driverDir")
            return false
        }

        val driverName = driverFile.name
        Log.i(TAG, "Found dynamic driver library: $driverName at ${driverFile.absolutePath}")

        runCatching {
            dir.setExecutable(true, false)
            dir.setReadable(true, false)
            driverFile.setExecutable(true, false)
            driverFile.setReadable(true, false)
        }.onFailure { Log.w(TAG, "Failed to set permissions on driver dir/file", it) }

        // Inject env vars BEFORE loading the driver — Turnip reads these at ICD init.
        val envManager = TurnipEnvManager(context)
        envManager.applyToDriver { key, value ->
            val ok = nativeSetEnv(key, value)
            Log.i(TAG, "setenv $key=$value -> $ok")
            ok
        }

        // Wipe stale artifacts from previous (possibly failed) hook attempts.
        // adrenotools refuses to re-populate the tmp dir if remnants exist.
        val tmpDir = context.getDir("vulkan_tmp", Context.MODE_PRIVATE)
        runCatching {
            tmpDir.listFiles()?.forEach { it.delete() }
        }.onFailure { Log.w(TAG, "Could not clear vulkan_tmp", it) }

        val tmpLibDir = tmpDir.absolutePath
        val hookLibDir = context.applicationInfo.nativeLibraryDir

        val baitFile = File(hookLibDir, "libvulkan_freedreno.so")
        Log.i(TAG, "Bait library at ${baitFile.absolutePath} - Exists: ${baitFile.exists()}")
        if (!baitFile.exists()) {
            Log.e(TAG, "Bait .so missing! adrenotools cannot bypass linker namespace without it.")
        }

        val result = nativeHookDriver(tmpLibDir, hookLibDir, driverDir, driverName)
        Log.i(TAG, "nativeHookDriver returned $result")
        return result
    }
}
