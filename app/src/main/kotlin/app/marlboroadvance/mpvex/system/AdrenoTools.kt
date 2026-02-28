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
        } catch (e: UnsatisfiedLinkError) {
            // CRITICAL FIX: This prevents crashes on 32-bit (armeabi-v7a) or x86 devices
            // where the 64-bit libadrenotools library isn't compiled.
            isBridgeLoaded = false
            Log.w(TAG, "Adrenotools not supported on this architecture", e)
        } catch (e: Exception) {
            isBridgeLoaded = false
            Log.e(TAG, "Failed to load adrenotools bridge", e)
        }
    }

    @JvmStatic
    private external fun nativeHookDriver(tmpLibDir: String, hookLibDir: String, customDriverDir: String): Boolean

    fun hookCustomDriver(context: Context, driverDir: String): Boolean {
        if (!isBridgeLoaded) return false
        
        val tmpLibDir = context.cacheDir.absolutePath
        val hookLibDir = context.applicationInfo.nativeLibraryDir
        
        return nativeHookDriver(tmpLibDir, hookLibDir, driverDir)
    }
}
