package app.marlboroadvance.mpvex.system

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
    private external fun nativeHookDriver(driverDir: String): Boolean

    fun hookCustomDriver(driverDir: String): Boolean {
        if (!isBridgeLoaded) return false
        return nativeHookDriver(driverDir)
    }
}

