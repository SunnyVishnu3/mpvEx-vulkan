package com.astrixforge.devicemasker.xposed

import com.astrixforge.devicemasker.common.SharedPrefsKeys
import com.astrixforge.devicemasker.common.SpoofType

/**
 * Preference key helpers for the :xposed module.
 *
 * Delegates entirely to SharedPrefsKeys in :common, which remains the SINGLE SOURCE OF TRUTH for
 * all key names. This class exists only as a convenience alias within :xposed.
 *
 * ## Migration note (libxposed API 101)
 *
 * Previously used YukiHookAPI's `PrefsData` wrapper objects, which are specific to the
 * `YukiHookPrefsBridge`. With libxposed API 101 and RemotePreferences, we use standard
 * `SharedPreferences` keys (plain strings). `PrefsData` wrappers are no longer needed.
 *
 * Key format is UNCHANGED — only the delivery mechanism changed (RemotePreferences vs
 * XSharedPreferences). Both sides still use SharedPrefsKeys from :common.
 */
object PrefsKeys {

    // Global settings key strings (used directly with SharedPreferences.getBoolean/getString)
    const val MODULE_ENABLED = SharedPrefsKeys.KEY_MODULE_ENABLED
    const val DEBUG_ENABLED = SharedPrefsKeys.KEY_DEBUG_ENABLED

    // Per-app key generators — delegate to SharedPrefsKeys
    fun getSpoofValueKey(packageName: String, type: SpoofType): String =
        SharedPrefsKeys.getSpoofValueKey(packageName, type)

    fun getSpoofEnabledKey(packageName: String, type: SpoofType): String =
        SharedPrefsKeys.getSpoofEnabledKey(packageName, type)

    fun getAppEnabledKey(packageName: String): String =
        SharedPrefsKeys.getAppEnabledKey(packageName)

    fun getDeviceProfileKey(packageName: String): String =
        SharedPrefsKeys.getDeviceProfileKey(packageName)

    fun getHookFamilyEnabledKey(packageName: String, family: String): String =
        SharedPrefsKeys.getHookFamilyEnabledKey(packageName, family)
}
