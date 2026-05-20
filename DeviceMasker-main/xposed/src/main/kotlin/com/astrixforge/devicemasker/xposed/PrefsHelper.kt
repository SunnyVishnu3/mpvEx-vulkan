package com.astrixforge.devicemasker.xposed

import android.content.SharedPreferences
import com.astrixforge.devicemasker.common.DevicePersona
import com.astrixforge.devicemasker.common.SharedPrefsKeys
import com.astrixforge.devicemasker.common.SpoofType

/**
 * Thread-safe preference reading for libxposed API 101 RemotePreferences.
 *
 * ## What changed from the YukiHookAPI (XSharedPreferences) version
 *
 * Old: Took `YukiHookPrefsBridge` from `prefs` property in YukiHookAPI's `PackageParam`. New: Takes
 * a standard `SharedPreferences` obtained via `XposedInterface.getRemotePreferences(PREFS_GROUP)`.
 *
 * Key advantages of RemotePreferences over XSharedPreferences:
 * - **Live**: reflects the latest values written by the UI app — no target app restart needed
 * - **Safe**: read-only in hooked processes (enforced by libxposed)
 * - **No reload()**: XSharedPreferences required manual reload() calls; RemotePreferences does not
 *
 * ## Key format (unchanged)
 *
 * The `SharedPrefsKeys` class in `:common` remains the single source of truth for all key names. No
 * key format changes are needed — only the delivery mechanism changed.
 *
 * ## Thread safety
 *
 * RemotePreferences returned by libxposed are backed by a snapshot that is safe to read on any
 * thread. Each read reflects the latest committed values from the module app process.
 */
object PrefsHelper {

    fun getStoredSpoofValue(
        prefs: SharedPreferences,
        packageName: String,
        type: SpoofType,
    ): String? {
        val typeEnabled =
            prefs.getBoolean(SharedPrefsKeys.getSpoofEnabledKey(packageName, type), false)
        if (!typeEnabled) return null

        val stored = prefs.getString(SharedPrefsKeys.getSpoofValueKey(packageName, type), null)
        return stored?.takeIf { it.isNotBlank() } ?: getPersonaSpoofValue(prefs, packageName, type)
    }

    /**
     * Gets the stored spoof value for a type, or invokes [fallback] when no configured value
     * exists.
     *
     * Hook callbacks should prefer [getStoredSpoofValue] and return the original method result when
     * it returns `null`. This compatibility wrapper is retained only for non-callback helpers.
     */
    fun getSpoofValue(
        prefs: SharedPreferences,
        packageName: String,
        type: SpoofType,
        fallback: () -> String,
    ): String {
        return getStoredSpoofValue(prefs, packageName, type) ?: fallback()
    }

    /**
     * Checks if a specific spoof type is explicitly enabled for a package.
     *
     * Returns false if the key is absent (spoofing types default to disabled — opt-in model).
     *
     * @param prefs RemotePreferences from XposedEntry.getRemotePreferences()
     * @param packageName Target app package name
     * @param type The SpoofType to check
     */
    fun isSpoofTypeEnabled(
        prefs: SharedPreferences,
        packageName: String,
        type: SpoofType,
    ): Boolean = prefs.getBoolean(SharedPrefsKeys.getSpoofEnabledKey(packageName, type), false)

    fun areRiskyHooksEnabled(prefs: SharedPreferences, packageName: String): Boolean =
        prefs.getBoolean(SharedPrefsKeys.getRiskyHooksEnabledKey(packageName), false)

    fun isClassLookupHidingEnabled(prefs: SharedPreferences, packageName: String): Boolean =
        areRiskyHooksEnabled(prefs, packageName) &&
            prefs.getBoolean(SharedPrefsKeys.getClassLookupHidingEnabledKey(packageName), false)

    private fun getPersonaSpoofValue(
        prefs: SharedPreferences,
        packageName: String,
        type: SpoofType,
    ): String? {
        val personaJson = prefs.getString(SharedPrefsKeys.getPersonaBlobKey(packageName), null)
        val persona = DevicePersona.parseOrNull(personaJson) ?: return null
        if (persona.packageName != packageName) return null
        return persona.getValue(type)?.takeIf { it.isNotBlank() }
    }
}
