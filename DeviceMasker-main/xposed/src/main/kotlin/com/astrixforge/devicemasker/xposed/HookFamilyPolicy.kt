package com.astrixforge.devicemasker.xposed

import android.content.SharedPreferences
import com.astrixforge.devicemasker.common.SharedPrefsKeys

internal data class HookFamilyPolicy(
    val antiDetectEnabled: Boolean,
    val deviceEnabled: Boolean,
    val subscriptionEnabled: Boolean,
    val networkEnabled: Boolean,
    val systemEnabled: Boolean,
    val systemFeatureEnabled: Boolean,
    val locationEnabled: Boolean,
    val sensorEnabled: Boolean,
    val advertisingEnabled: Boolean,
    val webViewEnabled: Boolean,
    val packageManagerEnabled: Boolean,
) {
    companion object {
        fun fromPrefs(prefs: SharedPreferences, packageName: String): HookFamilyPolicy {
            fun enabled(family: String): Boolean =
                prefs.getBoolean(SharedPrefsKeys.getHookFamilyEnabledKey(packageName, family), true)

            return HookFamilyPolicy(
                antiDetectEnabled = enabled("anti_detect"),
                deviceEnabled = enabled("device"),
                subscriptionEnabled = enabled("subscription"),
                networkEnabled = enabled("network"),
                systemEnabled = enabled("system"),
                systemFeatureEnabled = enabled("system_feature"),
                locationEnabled = enabled("location"),
                sensorEnabled = enabled("sensor"),
                advertisingEnabled = enabled("advertising"),
                webViewEnabled = enabled("webview"),
                packageManagerEnabled = enabled("package_manager"),
            )
        }
    }
}
