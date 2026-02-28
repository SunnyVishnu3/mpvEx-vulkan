package app.marlboroadvance.mpvex.system

import android.content.Context
import android.util.Log

object AdrenoTools {
    private const val TAG = "AdrenoTools"
    var isBridgeLoaded = false
        private set

    init {
        try {
            System.loadLibrary("adrenotools_bridge")
            isBridgeLoaded = true
            Log.i(TAG, "Native bridge loaded successfully")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to load adrenotools bridge", e)
        }
    }

    @JvmStatic
    private external fun nativeHookDriver(tmpLibDir: String, hookLibDir: String, customDriverDir: String): Boolean

    fun hookCustomDriver(context: Context, driverDir: String): Boolean {
        if (!isBridgeLoaded) return false
        
        // Adrenotools requires these exact system paths to hook the linker
        val tmpLibDir = context.cacheDir.absolutePath
        val hookLibDir = context.applicationInfo.nativeLibraryDir
        
        return nativeHookDriver(tmpLibDir, hookLibDir, driverDir)
    }
}
