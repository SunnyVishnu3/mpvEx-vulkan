package com.astrixforge.devicemasker.service

import android.content.Context
import android.util.AtomicFile
import com.astrixforge.devicemasker.common.AppConfig
import com.astrixforge.devicemasker.common.DeviceIdentifier
import com.astrixforge.devicemasker.common.JsonConfig
import com.astrixforge.devicemasker.common.SpoofGroup
import com.astrixforge.devicemasker.common.SpoofType
import com.astrixforge.devicemasker.common.addOrUpdateGroup
import com.astrixforge.devicemasker.common.regenerateAll
import com.astrixforge.devicemasker.common.setAppConfig
import com.astrixforge.devicemasker.common.setIdentifier
import com.astrixforge.devicemasker.common.withPersona
import com.astrixforge.devicemasker.data.ConfigSync
import java.io.File
import java.io.IOException
import java.util.UUID
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerializationException
import timber.log.Timber

/**
 * Config Manager — manages application configuration (libxposed API 101 edition).
 *
 * Responsibilities:
 * 1. Load/save configuration from/to local JSON file (filesDir/config.json)
 * 2. Sync configuration to RemotePreferences via [ConfigSync] for live cross-process delivery
 * 3. Provide [StateFlow] for UI reactivity
 * 4. CRUD operations for groups and app configs
 *
 * Config save path (post-migration):
 * - UI → ConfigManager → local file + ConfigSync → RemotePreferences (live to hooks)
 *
 * No AIDL service calls: config delivery uses [XposedPrefs]/RemotePreferences exclusively.
 */
// Compatibility facade: callers still depend on the unified config API while the contract is split.
@Suppress("TooManyFunctions")
object ConfigManager : IConfigManager {

    private const val TAG = "ConfigManager"
    private const val CONFIG_FILE_NAME = "config.json"

    private var configFile: AtomicFile? = null
    private var appContext: Context? = null
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    // In-memory configuration
    private val _config = MutableStateFlow(JsonConfig.createDefault())
    override val config: StateFlow<JsonConfig> = _config.asStateFlow()

    // Initialization state
    private val _isInitialized = MutableStateFlow(false)
    override val isInitialized: StateFlow<Boolean> = _isInitialized.asStateFlow()
    private val initStarted = AtomicBoolean(false)
    private val initGeneration = AtomicInteger(0)
    private val saveMutex = Mutex()

    private sealed interface SyncHint {
        data object Full : SyncHint

        data class Packages(val packageNames: Set<String>) : SyncHint
    }

    /**
     * Initializes the ConfigManager with application context. Must be called in
     * Application.onCreate() or MainActivity.
     */
    override fun init(context: Context) {
        if (!initStarted.compareAndSet(false, true)) {
            Timber.tag(TAG).d("Already initialized")
            return
        }

        val applicationContext = context.applicationContext
        val localConfigFile = AtomicFile(File(applicationContext.filesDir, CONFIG_FILE_NAME))
        appContext = applicationContext
        configFile = localConfigFile
        Timber.tag(TAG).d("Config file: ${localConfigFile.baseFile.absolutePath}")

        val generation = initGeneration.incrementAndGet()
        scope.launch {
            loadConfig(generation)
            if (initGeneration.get() == generation) {
                _isInitialized.value = true
            }
        }
    }

    /**
     * Loads configuration from local file. Falls back to service config if local doesn't exist and
     * service is connected.
     */
    private suspend fun loadConfig(generation: Int) {
        withContext(Dispatchers.IO) {
            try {
                // Try loading from local file first
                val localConfigFile = requireConfigFile()
                if (localConfigFile.baseFile.exists()) {
                    val json = String(localConfigFile.readFully())
                    val loadedConfig =
                        JsonConfig.parse(json).withDerivedAppConfigsFromAssignedApps()
                    _config.value = loadedConfig
                    ConfigSync.syncFromConfig(requireAppContext(), loadedConfig)
                    Timber.tag(TAG).i("Config loaded from local file")
                    return@withContext
                }

                // Use default config
                val defaultConfig = JsonConfig.createDefault()
                _config.value = defaultConfig
                saveConfigInternal(defaultConfig, generation)
                Timber.tag(TAG).i("Created default config")
            } catch (e: IOException) {
                recoverFromLoadFailure(e, generation)
            } catch (e: SerializationException) {
                recoverFromLoadFailure(e, generation)
            } catch (e: IllegalArgumentException) {
                recoverFromLoadFailure(e, generation)
            } catch (e: IllegalStateException) {
                recoverFromLoadFailure(e, generation)
            }
        }
    }

    private suspend fun recoverFromLoadFailure(error: Throwable, generation: Int) {
        Timber.tag(TAG).e(error, "Failed to load config")
        val defaultConfig = JsonConfig.createDefault()
        preserveCorruptedConfig()
        _config.value = defaultConfig
        saveConfigInternal(defaultConfig, generation)
    }

    private fun requireConfigFile(): AtomicFile =
        checkNotNull(configFile) { "ConfigManager not initialized. Call init() first." }

    private fun requireAppContext(): Context =
        checkNotNull(appContext) { "ConfigManager not initialized. Call init() first." }

    private inline fun writeConfigFile(config: JsonConfig, writeFailed: (Throwable) -> Nothing) {
        val localConfigFile = requireConfigFile()
        val stream = localConfigFile.startWrite()
        try {
            stream.write(config.toJsonString().toByteArray())
            localConfigFile.finishWrite(stream)
        } catch (error: IOException) {
            localConfigFile.failWrite(stream)
            writeFailed(error)
        } catch (error: SerializationException) {
            localConfigFile.failWrite(stream)
            writeFailed(error)
        } catch (error: IllegalArgumentException) {
            localConfigFile.failWrite(stream)
            writeFailed(error)
        } catch (error: IllegalStateException) {
            localConfigFile.failWrite(stream)
            writeFailed(error)
        }
    }

    private fun logSaveFailure(error: Throwable) {
        Timber.tag(TAG).e(error, "Failed to save config")
    }

    private suspend fun persistAndSyncConfig(config: JsonConfig, syncHint: SyncHint) {
        try {
            // 1. Persist raw JSON locally as a backup and for UI reload on next launch
            writeConfigFile(config) { throw it }
            Timber.tag(TAG).d("Config saved to local file")

            // 2. Flatten per-app keys into RemotePreferences (live delivery to hooks)
            when (syncHint) {
                SyncHint.Full -> ConfigSync.syncFromConfig(requireAppContext(), config)
                is SyncHint.Packages ->
                    ConfigSync.syncPackages(requireAppContext(), config, syncHint.packageNames)
            }
            Timber.tag(TAG).d("Config synced to RemotePreferences")
        } catch (e: IOException) {
            logSaveFailure(e)
        } catch (e: SerializationException) {
            logSaveFailure(e)
        } catch (e: IllegalArgumentException) {
            logSaveFailure(e)
        } catch (e: IllegalStateException) {
            logSaveFailure(e)
        }
    }

    private fun preserveCorruptedConfig() {
        val localConfigFile = requireConfigFile()
        if (!localConfigFile.baseFile.exists()) return
        val backup =
            File(
                localConfigFile.baseFile.parentFile,
                "$CONFIG_FILE_NAME.corrupted.${System.currentTimeMillis()}",
            )
        val preserved =
            runCatching {
                    localConfigFile.baseFile.copyTo(backup, overwrite = true)
                    localConfigFile.baseFile.delete()
                }
                .isSuccess
        if (preserved) {
            Timber.tag(TAG).w("Corrupted config backed up to ${backup.name}")
        } else {
            Timber.tag(TAG).w("Failed to preserve corrupted config before recovery")
        }
    }

    /** Saves the current configuration to local file and syncs to service. */
    override fun saveConfig() {
        saveConfig(SyncHint.Full)
    }

    private fun saveConfig(syncHint: SyncHint) {
        val generation = initGeneration.get()
        val snapshot = _config.value
        scope.launch { saveConfigInternal(snapshot, generation, syncHint) }
    }

    override fun syncCurrentConfig() {
        val context = appContext ?: return
        scope.launch { ConfigSync.syncFromConfig(context, _config.value) }
    }

    /**
     * Internal save method.
     *
     * Write path (post-migration):
     * 1. Write JSON to local file (backup / UI reload)
     * 2. Sync flattened per-app keys to RemotePreferences via [ConfigSync] → LSPosed delivers these
     *    live to hooks via `getRemotePreferences()`
     *
     * No AIDL service write — config delivery is exclusively via RemotePreferences.
     */
    private suspend fun saveConfigInternal(
        config: JsonConfig,
        generation: Int,
        syncHint: SyncHint = SyncHint.Full,
    ) {
        withContext(Dispatchers.IO) {
            saveMutex.withLock {
                if (initGeneration.get() != generation) return@withLock
                persistAndSyncConfig(config, syncHint)
            }
        }
    }

    /** Updates the configuration and saves. */
    private fun updateConfig(
        syncHint: SyncHint = SyncHint.Full,
        transform: (JsonConfig) -> JsonConfig,
    ) {
        _config.update(transform)
        saveConfig(syncHint)
    }

    // ═══════════════════════════════════════════════════════════
    // Module Settings
    // ═══════════════════════════════════════════════════════════

    /** Gets whether the module is enabled. */
    override fun isModuleEnabled(): Boolean = _config.value.isModuleEnabled

    /** Sets whether the module is enabled. */
    override fun setModuleEnabled(enabled: Boolean) {
        updateConfig { it.copy(isModuleEnabled = enabled) }
    }

    // ═══════════════════════════════════════════════════════════
    // Group Management
    // ═══════════════════════════════════════════════════════════

    /** Gets all groups. */
    override fun getAllGroups(): List<SpoofGroup> = _config.value.groups.values.toList()

    /** Gets a group by ID. */
    override fun getGroup(groupId: String): SpoofGroup? = _config.value.getGroup(groupId)

    /** Creates a new group. */
    override fun createGroup(name: String, copyFromGroupId: String?): SpoofGroup {
        val baseGroup = copyFromGroupId?.let { getGroup(it) }
        val newGroup =
            baseGroup?.copy(
                id = java.util.UUID.randomUUID().toString(),
                name = name,
                createdAt = System.currentTimeMillis(),
                updatedAt = System.currentTimeMillis(),
                assignedApps = emptySet(), // Don't copy app assignments
            ) ?: SpoofGroup.createNew(name = name, isDefault = false)

        updateConfig { it.addOrUpdateGroup(newGroup) }
        return newGroup
    }

    /** Updates an existing group. */
    override fun updateGroup(group: SpoofGroup) {
        val updatedGroup = group.copy(updatedAt = System.currentTimeMillis())
        updateConfig(syncHint = SyncHint.Packages(packagesAssignedToGroup(group.id))) {
            it.addOrUpdateGroup(updatedGroup)
        }
    }

    /** Sets the default group through one config transform. */
    override fun setDefaultGroup(groupId: String) {
        updateConfig { config ->
            if (config.getGroup(groupId) == null) return@updateConfig config

            val updatedAt = System.currentTimeMillis()
            config.copy(
                groups =
                    config.groups.mapValues { (id, group) ->
                        group.copy(
                            isDefault = id == groupId,
                            updatedAt =
                                if (id == groupId || group.isDefault) {
                                    updatedAt
                                } else {
                                    group.updatedAt
                                },
                        )
                    }
            )
        }
    }

    /** Deletes a group. */
    override fun deleteGroup(groupId: String) {
        updateConfig { it.removeGroup(groupId) }
    }

    /** Gets the group for a specific app. */
    override fun getGroupForApp(packageName: String): SpoofGroup? {
        return _config.value.getGroupForApp(packageName)
    }

    // ═══════════════════════════════════════════════════════════
    // Group Identifier Management
    // ═══════════════════════════════════════════════════════════

    /** Sets an identifier value in a group. */
    override fun setIdentifierValue(groupId: String, type: SpoofType, value: String?) {
        val group = getGroup(groupId) ?: return
        val identifier =
            group.getIdentifier(type)?.copy(value = value)
                ?: DeviceIdentifier.createDefault(type).copy(value = value)
        val updatedGroup = group.setIdentifier(identifier)
        updateGroup(updatedGroup)
    }

    /** Sets whether a spoof type is enabled in a group. */
    override fun setTypeEnabled(groupId: String, type: SpoofType, enabled: Boolean) {
        val group = getGroup(groupId) ?: return
        val identifier =
            group.getIdentifier(type)?.copy(isEnabled = enabled)
                ?: DeviceIdentifier.createDefault(type).copy(isEnabled = enabled)
        val updatedGroup = group.setIdentifier(identifier)
        updateGroup(updatedGroup)
    }

    /** Regenerates all values in a group. */
    override fun regenerateAllValues(groupId: String) {
        val group = getGroup(groupId) ?: return
        val regenerated = group.regenerateAll()
        updateGroup(regenerated)
    }

    /** Returns the resolved persona seed for a group. */
    override fun getPersonaSeed(group: SpoofGroup): String = group.resolvedPersonaSeed()

    /** Returns the version used to identify the current generated persona state. */
    override fun getPersonaVersion(group: SpoofGroup): Long = group.updatedAt

    /** Rotates persona metadata while preserving group identity and assignments. */
    override fun refreshPersonaLifecycle(group: SpoofGroup): SpoofGroup {
        return group.withPersona(
            seed = UUID.randomUUID().toString(),
            generatedAt = System.currentTimeMillis(),
        )
    }

    // ═══════════════════════════════════════════════════════════
    // App Config Management
    // ═══════════════════════════════════════════════════════════

    /** Gets app config for a package. */
    override fun getAppConfig(packageName: String): AppConfig? =
        _config.value.getAppConfig(packageName)

    /** Assigns an app to a group. */
    override fun assignAppToGroup(packageName: String, groupId: String) {
        updateConfig(syncHint = SyncHint.Packages(setOf(packageName))) { config ->
            if (config.getGroup(groupId) == null) return@updateConfig config

            val groups =
                config.groups.mapValues { (_, group) ->
                    when {
                        group.id == groupId ->
                            group.copy(assignedApps = group.assignedApps + packageName)
                        packageName in group.assignedApps ->
                            group.copy(assignedApps = group.assignedApps - packageName)
                        else -> group
                    }
                }
            val appConfig =
                config.getAppConfig(packageName)?.copy(groupId = groupId)
                    ?: AppConfig(packageName = packageName, groupId = groupId)
            config.copy(groups = groups).setAppConfig(appConfig)
        }
    }

    /** Unassigns an app from its group. */
    override fun unassignApp(packageName: String) {
        updateConfig(syncHint = SyncHint.Packages(setOf(packageName))) { config ->
            val groups =
                config.groups.mapValues { (_, group) ->
                    if (packageName in group.assignedApps) {
                        group.copy(assignedApps = group.assignedApps - packageName)
                    } else {
                        group
                    }
                }
            val currentAppConfig = config.getAppConfig(packageName)
            val configWithoutGroup =
                currentAppConfig?.copy(groupId = null)
                    ?: AppConfig(packageName = packageName, groupId = null)
            config.copy(groups = groups).setAppConfig(configWithoutGroup)
        }
    }

    /** Sets whether spoofing is enabled for an app. */
    override fun setAppEnabled(packageName: String, enabled: Boolean) {
        val appConfig =
            getAppConfig(packageName)?.copy(isEnabled = enabled)
                ?: AppConfig(packageName = packageName, isEnabled = enabled)
        updateConfig(syncHint = SyncHint.Packages(setOf(packageName))) {
            it.setAppConfig(appConfig)
        }
    }

    override fun setAppRiskyHooksEnabled(packageName: String, enabled: Boolean) {
        val appConfig =
            (getAppConfig(packageName) ?: AppConfig(packageName = packageName))
                .withRiskyHooksEnabled(enabled)
        updateConfig(syncHint = SyncHint.Packages(setOf(packageName))) {
            it.setAppConfig(appConfig)
        }
    }

    override fun setAppClassLookupHidingEnabled(packageName: String, enabled: Boolean) {
        val appConfig =
            (getAppConfig(packageName) ?: AppConfig(packageName = packageName))
                .withClassLookupHidingEnabled(enabled)
        updateConfig(syncHint = SyncHint.Packages(setOf(packageName))) {
            it.setAppConfig(appConfig)
        }
    }

    private fun packagesAssignedToGroup(groupId: String): Set<String> =
        _config.value.appConfigs.values
            .asSequence()
            .filter { it.groupId == groupId }
            .map { it.packageName }
            .toSet()

    /** Resets initialization state for tests. */
    internal fun resetForTests() {
        initStarted.set(false)
        initGeneration.incrementAndGet()
        _isInitialized.value = false
        _config.value = JsonConfig.createDefault()
    }
}
