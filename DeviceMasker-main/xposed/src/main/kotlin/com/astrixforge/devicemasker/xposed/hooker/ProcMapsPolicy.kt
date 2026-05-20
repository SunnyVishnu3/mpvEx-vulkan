package com.astrixforge.devicemasker.xposed.hooker

import android.content.SharedPreferences
import com.astrixforge.devicemasker.common.SharedPrefsKeys

internal data class ProcMapsPolicy(
    val javaLineRedactionEnabled: Boolean,
    val javaByteRedactionEnabled: Boolean,
    val javaNioRedactionEnabled: Boolean,
) {
    companion object {
        fun fromPrefs(prefs: SharedPreferences, packageName: String): ProcMapsPolicy {
            val risky =
                prefs.getBoolean(SharedPrefsKeys.getRiskyHooksEnabledKey(packageName), false)
            return ProcMapsPolicy(
                javaLineRedactionEnabled = true,
                javaByteRedactionEnabled =
                    risky &&
                        prefs.getBoolean(
                            SharedPrefsKeys.getJavaProcMapsByteRedactionEnabledKey(packageName),
                            false,
                        ),
                javaNioRedactionEnabled =
                    risky &&
                        prefs.getBoolean(
                            SharedPrefsKeys.getJavaProcMapsNioRedactionEnabledKey(packageName),
                            false,
                        ),
            )
        }
    }
}
