package com.astrixforge.devicemasker.data

import android.annotation.SuppressLint
import android.content.Context
import com.astrixforge.devicemasker.common.JsonConfig
import com.astrixforge.devicemasker.common.SharedPrefsKeys
import com.astrixforge.devicemasker.common.SpoofType
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import timber.log.Timber

/**
 * Syncs [JsonConfig] to [XposedPrefs] / `getRemotePreferences()` for live cross-process delivery.
 *
 * The UI stores configuration as a group-based [JsonConfig] (JSON file). Hooks read flat per-app
 * [SharedPreferences] keys via `getRemotePreferences()`. This object bridges the gap.
 *
 * ## Sync Flow (libxposed API 101):
 * ```
 * UI Change → ConfigManager → JsonConfig → ConfigSync.syncFromConfig()
 *                                                ↓
 *                                         XposedPrefs.getPrefs()?.edit()
 *                                                ↓
 *                                      XposedService.getRemotePreferences()
 *                                                ↓
 *                                         LSPosed (live — no app restart needed)
 *                                                ↓
 *                                         getRemotePreferences() in hooks
 * ```
 *
 * **Null-safety:** If the module is not active (LSPosed not running), [XposedPrefs.getPrefs]
 * returns `null` and all writes are silently no-op'd. Config is still persisted locally via
 * [ConfigManager]'s JSON file and will sync on next module activation.
 *
 * Call [syncFromConfig] whenever the config changes in the UI.
 */
object ConfigSync {

    private const val TAG = "ConfigSync"

    data class Snapshot(
        val booleans: Map<String, Boolean>,
        val strings: Map<String, String>,
        val stringSets: Map<String, Set<String>>,
        val longs: Map<String, Long>,
        val removeKeys: Set<String>,
        val currentApps: Set<String>,
        val removedApps: Set<String>,
    )

    /**
     * Syncs everything from [config] to [XposedPrefs] (RemotePreferences).
     *
     * Context parameter retained for API compatibility, but no longer used for prefs I/O.
     *
     * @param context Unused after migration — retained for call-site compatibility
     * @param config The [JsonConfig] to sync
     */
    fun syncFromConfig(@Suppress("UNUSED_PARAMETER") context: Context, config: JsonConfig) {
        syncFromConfig(config, XposedPrefs.getPrefs())
    }

    @SuppressLint("ApplySharedPref", "UseKtx")
    internal fun syncFromConfig(config: JsonConfig, prefs: android.content.SharedPreferences?) {
        if (prefs == null) {
            Timber.tag(TAG).d("XposedService not connected — config will sync on next activation")
            return
        }

        Timber.tag(TAG).d("Syncing config to RemotePreferences...")
        val previousEnabledApps =
            prefs.getStringSet(SharedPrefsKeys.KEY_ENABLED_APPS, emptySet()) ?: emptySet()
        val snapshot = buildSnapshot(config, previousEnabledApps)

        val committed =
            prefs
                .edit()
                .apply {
                    snapshot.removeKeys.forEach { remove(it) }
                    snapshot.booleans.forEach { (key, value) -> putBoolean(key, value) }
                    snapshot.strings.forEach { (key, value) -> putString(key, value) }
                    snapshot.stringSets.forEach { (key, value) -> putStringSet(key, value) }
                    snapshot.longs.forEach { (key, value) -> putLong(key, value) }
                }
                .commit()
        if (!committed) {
            Timber.tag(TAG).w("RemotePreferences commit failed during full sync")
            return
        }
        Timber.tag(TAG).d("Config synced to RemotePreferences — live delivery active")
    }

    /**
     * Quick sync for a single package.
     *
     * @param context Unused after migration — retained for call-site compatibility
     * @param config The [JsonConfig] to sync from
     * @param packageName The package to sync
     */
    fun syncApp(
        @Suppress("UNUSED_PARAMETER") context: Context,
        config: JsonConfig,
        packageName: String,
    ) {
        syncAppToPrefs(config, packageName, XposedPrefs.getPrefs())
    }

    fun syncPackages(
        @Suppress("UNUSED_PARAMETER") context: Context,
        config: JsonConfig,
        packageNames: Set<String>,
    ) {
        syncPackages(config, packageNames, XposedPrefs.getPrefs())
    }

    @SuppressLint("ApplySharedPref", "UseKtx")
    internal fun syncPackages(
        config: JsonConfig,
        packageNames: Set<String>,
        prefs: android.content.SharedPreferences?,
    ) {
        if (prefs == null) {
            Timber.tag(TAG)
                .d("XposedService not connected — scoped config will sync on next activation")
            return
        }

        val committed =
            prefs
                .edit()
                .apply {
                    putStringSet(
                        SharedPrefsKeys.KEY_ENABLED_APPS,
                        config.appConfigs.keys.toSortedSet(),
                    )
                    putLong(SharedPrefsKeys.KEY_CONFIG_VERSION, System.currentTimeMillis())
                    packageNames.toSortedSet().forEach { packageName ->
                        val state = config.syncStateFor(packageName)
                        if (state == null) {
                            removePackageSyncKeys(packageName)
                            putAppDisabled(packageName)
                        } else {
                            putAppSyncState(state)
                        }
                    }
                }
                .commit()
        if (!committed) {
            Timber.tag(TAG).w("RemotePreferences commit failed during scoped sync")
        }
    }

    suspend fun syncAppAsync(context: Context, config: JsonConfig, packageName: String) {
        withContext(Dispatchers.IO) { syncApp(context, config, packageName) }
    }

    /**
     * Clears all stored spoof keys for a package from [XposedPrefs].
     *
     * @param context Unused after migration — retained for call-site compatibility
     * @param packageName The package to clear
     */
    fun clearApp(@Suppress("UNUSED_PARAMETER") context: Context, packageName: String) {
        clearAppFromPrefs(packageName, XposedPrefs.getPrefs())
    }

    suspend fun clearAppAsync(context: Context, packageName: String) {
        withContext(Dispatchers.IO) { clearApp(context, packageName) }
    }

    fun buildSnapshot(config: JsonConfig, previousEnabledApps: Set<String>): Snapshot {
        val booleans = linkedMapOf<String, Boolean>()
        val strings = linkedMapOf<String, String>()
        val stringSets = linkedMapOf<String, Set<String>>()
        val longs = linkedMapOf<String, Long>()
        val removeKeys = linkedSetOf<String>()
        val currentApps = config.appConfigs.keys.toSortedSet()
        val removedApps = previousEnabledApps - currentApps

        booleans[SharedPrefsKeys.KEY_MODULE_ENABLED] = config.isModuleEnabled
        stringSets[SharedPrefsKeys.KEY_ENABLED_APPS] = currentApps
        longs[SharedPrefsKeys.KEY_CONFIG_VERSION] = System.currentTimeMillis()

        for (packageName in removedApps) {
            removeKeys += keysForSyncedPackage(packageName)
        }

        for (packageName in config.appConfigs.keys.toSortedSet()) {
            val state = config.syncStateFor(packageName)
            booleans[SharedPrefsKeys.getAppEnabledKey(packageName)] = state?.appEnabled == true
            booleans[SharedPrefsKeys.getRiskyHooksEnabledKey(packageName)] =
                state?.riskyHooksEnabled == true
            booleans[SharedPrefsKeys.getClassLookupHidingEnabledKey(packageName)] =
                state?.classLookupHidingEnabled == true
            hookFamilyNames.forEach { family ->
                booleans[SharedPrefsKeys.getHookFamilyEnabledKey(packageName, family)] =
                    state?.isHookFamilyEnabled(family) == true
            }
            booleans[SharedPrefsKeys.getJavaProcMapsByteRedactionEnabledKey(packageName)] = false
            booleans[SharedPrefsKeys.getJavaProcMapsNioRedactionEnabledKey(packageName)] = false
            if (state?.persona != null) {
                strings[SharedPrefsKeys.getPersonaBlobKey(packageName)] =
                    state.persona.toJsonString()
                longs[SharedPrefsKeys.getPersonaVersionKey(packageName)] = state.persona.version
            } else {
                removeKeys += SharedPrefsKeys.getPersonaBlobKey(packageName)
                removeKeys += SharedPrefsKeys.getPersonaVersionKey(packageName)
            }
            state?.spoofTypes.orEmpty().forEach { typeState ->
                val valueKey = SharedPrefsKeys.getSpoofValueKey(packageName, typeState.type)
                booleans[SharedPrefsKeys.getSpoofEnabledKey(packageName, typeState.type)] =
                    typeState.enabled
                if (typeState.value != null) strings[valueKey] = typeState.value
                else removeKeys += valueKey
            }
        }

        return Snapshot(
            booleans = booleans,
            strings = strings,
            stringSets = stringSets,
            longs = longs,
            removeKeys = removeKeys,
            currentApps = currentApps,
            removedApps = removedApps,
        )
    }
}

internal suspend fun syncAppAsync(
    config: JsonConfig,
    packageName: String,
    prefs: android.content.SharedPreferences?,
) {
    withContext(Dispatchers.IO) { syncAppToPrefs(config, packageName, prefs) }
}

@SuppressLint("ApplySharedPref", "UseKtx")
internal fun syncAppToPrefs(
    config: JsonConfig,
    packageName: String,
    prefs: android.content.SharedPreferences?,
) {
    val state = config.syncStateFor(packageName)
    when {
        prefs == null -> Timber.tag(CONFIG_SYNC_TAG).d(notConnectedMessage("syncApp", packageName))
        state == null -> {
            prefs
                .edit()
                .apply {
                    removePackageSyncKeys(packageName)
                    putAppDisabled(packageName)
                }
                .commit()
            Timber.tag(CONFIG_SYNC_TAG).d("App $packageName removed from spoofing (no group)")
        }
        else -> commitAppSync(packageName, state, prefs)
    }
}

internal suspend fun clearAppAsync(packageName: String, prefs: android.content.SharedPreferences?) {
    withContext(Dispatchers.IO) { clearAppFromPrefs(packageName, prefs) }
}

@SuppressLint("ApplySharedPref", "UseKtx")
internal fun clearAppFromPrefs(packageName: String, prefs: android.content.SharedPreferences?) {
    if (prefs == null) {
        Timber.tag(CONFIG_SYNC_TAG).d(notConnectedMessage("clearApp", packageName))
        return
    }

    val committed = prefs.edit().apply { removePackageSyncKeys(packageName) }.commit()
    if (!committed) {
        Timber.tag(CONFIG_SYNC_TAG)
            .w("RemotePreferences commit failed during clearApp for $packageName")
        return
    }
    Timber.tag(CONFIG_SYNC_TAG).d("App $packageName cleared from RemotePreferences")
}

private fun android.content.SharedPreferences.Editor.removePackageSyncKeys(packageName: String) {
    remove(SharedPrefsKeys.getAppEnabledKey(packageName))
    remove(SharedPrefsKeys.getRiskyHooksEnabledKey(packageName))
    remove(SharedPrefsKeys.getClassLookupHidingEnabledKey(packageName))
    hookFamilyNames.forEach { family ->
        remove(SharedPrefsKeys.getHookFamilyEnabledKey(packageName, family))
    }
    remove(SharedPrefsKeys.getJavaProcMapsByteRedactionEnabledKey(packageName))
    remove(SharedPrefsKeys.getJavaProcMapsNioRedactionEnabledKey(packageName))
    remove(SharedPrefsKeys.getPersonaBlobKey(packageName))
    remove(SharedPrefsKeys.getPersonaVersionKey(packageName))

    for (type in SpoofType.entries) {
        remove(SharedPrefsKeys.getSpoofEnabledKey(packageName, type))
        remove(SharedPrefsKeys.getSpoofValueKey(packageName, type))
    }
}

@SuppressLint("ApplySharedPref", "UseKtx")
private fun commitAppSync(
    packageName: String,
    state: AppSyncState,
    prefs: android.content.SharedPreferences,
) {
    val committed = prefs.edit().apply { putAppSyncState(state) }.commit()
    if (committed) {
        Timber.tag(CONFIG_SYNC_TAG).d("App $packageName synced to RemotePreferences")
    } else {
        Timber.tag(CONFIG_SYNC_TAG)
            .w("RemotePreferences commit failed during syncApp for $packageName")
    }
}

private fun notConnectedMessage(operation: String, packageName: String): String =
    "XposedService not connected — skipping $operation for $packageName"

private const val CONFIG_SYNC_TAG = "ConfigSync"
