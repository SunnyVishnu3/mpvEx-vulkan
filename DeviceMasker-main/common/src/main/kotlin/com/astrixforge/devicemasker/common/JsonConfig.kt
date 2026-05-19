package com.astrixforge.devicemasker.common

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

/**
 * Main configuration container for Device Masker.
 *
 * This is the root object that holds all module configuration, including:
 * - Module enabled state
 * - All spoof groups
 * - Per-app configurations
 *
 * The configuration is serialized to JSON for UI persistence. Runtime hook delivery is flattened
 * into RemotePreferences by the app-side config sync layer.
 *
 * @property version Configuration format version for migrations
 * @property isModuleEnabled Global module enable/disable switch
 * @property groups Map of group ID to SpoofGroup
 * @property appConfigs Map of package name to AppConfig
 */
@Suppress("unused") // API methods used across modules
@Serializable
data class JsonConfig(
    val version: Int = Constants.CONFIG_VERSION,
    val isModuleEnabled: Boolean = true,
    @SerialName("profiles") val groups: Map<String, SpoofGroup> = emptyMap(),
    val appConfigs: Map<String, AppConfig> = emptyMap(),
) {
    // ═══════════════════════════════════════════════════════════
    // GROUP MANAGEMENT
    // ═══════════════════════════════════════════════════════════

    /**
     * Gets a group by ID.
     *
     * @return The group or null if not found
     */
    fun getGroup(groupId: String): SpoofGroup? {
        return groups[groupId]
    }

    /**
     * Gets the default group (the one with isDefault = true).
     *
     * @return The default group or null if none is set
     */
    fun getDefaultGroup(): SpoofGroup? {
        return groups.values.find { it.isDefault }
    }

    /**
     * Adds or updates a group.
     *
     * @return Updated JsonConfig
     */
    fun withGroup(group: SpoofGroup): JsonConfig {
        return copy(groups = groups + (group.id to group))
    }

    /**
     * Removes a group by ID.
     *
     * @return Updated JsonConfig
     */
    fun removeGroup(groupId: String): JsonConfig {
        val newAppConfigs = appConfigs.filterValues { it.groupId != groupId }
        return copy(groups = groups - groupId, appConfigs = newAppConfigs)
    }

    // ═══════════════════════════════════════════════════════════
    // APP CONFIG MANAGEMENT
    // ═══════════════════════════════════════════════════════════

    /**
     * Gets app configuration for a package.
     *
     * @return The AppConfig or null if not configured
     */
    fun getAppConfig(packageName: String): AppConfig? {
        return appConfigs[packageName]
    }

    /**
     * Gets the group assigned to an app. Falls back to default group if no specific assignment.
     *
     * @return The assigned group, default group, or null
     */
    fun getGroupForApp(packageName: String): SpoofGroup? {
        val appConfig = appConfigs[packageName]
        return if (appConfig?.isEnabled == false) {
            null
        } else {
            appConfig?.groupId?.let(groups::get) ?: getDefaultGroup()
        }
    }

    /**
     * Gets the explicitly assigned group for a package. Runtime sync must not use default fallback.
     */
    fun getExplicitGroupForApp(packageName: String): SpoofGroup? {
        val appConfig = appConfigs[packageName]
        return appConfig?.groupId?.takeIf { appConfig.isEnabled }?.let(groups::get)
    }

    /**
     * Adds or updates an app configuration.
     *
     * @return Updated JsonConfig
     */
    fun withAppConfig(appConfig: AppConfig): JsonConfig {
        return copy(appConfigs = appConfigs + (appConfig.packageName to appConfig))
    }

    /**
     * Removes app configuration for a package.
     *
     * @return Updated JsonConfig
     */
    fun removeAppConfig(packageName: String): JsonConfig {
        return copy(appConfigs = appConfigs - packageName)
    }

    /**
     * Migrates legacy configs that only stored app scope in [SpoofGroup.assignedApps].
     *
     * Current runtime sync uses [appConfigs] as the canonical per-app scope table. If a loaded
     * development config predates that table, derive enabled app configs from group assignments.
     * Once appConfigs exist, they remain authoritative and stale group assignment sets are ignored.
     */
    fun withDerivedAppConfigsFromAssignedApps(): JsonConfig {
        if (appConfigs.isNotEmpty()) return this

        val derivedAppConfigs =
            groups.values
                .flatMap { group ->
                    group.assignedApps.map { packageName ->
                        packageName to AppConfig(packageName = packageName, groupId = group.id)
                    }
                }
                .toMap()

        return copy(appConfigs = derivedAppConfigs)
    }

    // ═══════════════════════════════════════════════════════════
    // SERIALIZATION
    // ═══════════════════════════════════════════════════════════

    /** Serializes this config to a JSON string. */
    fun toJsonString(): String {
        return jsonSerializer.encodeToString(this)
    }

    companion object {
        // JSON serializer for compact output
        private val jsonSerializer = Json {
            ignoreUnknownKeys = true
            encodeDefaults = true
        }

        /**
         * Parses a JSON string into a JsonConfig.
         *
         * @param json The JSON string to parse
         * @return Parsed JsonConfig
         * @throws Exception if parsing fails
         */
        fun parse(json: String): JsonConfig {
            return jsonSerializer.decodeFromString<JsonConfig>(json)
        }

        /** Creates a default configuration with no groups (fresh install). */
        fun createDefault(): JsonConfig {
            return JsonConfig() // Empty - user must create groups
        }
    }
}

private val prettyJsonSerializer = Json {
    ignoreUnknownKeys = true
    encodeDefaults = true
    prettyPrint = true
}

/** Gets all groups as a list. */
fun JsonConfig.getAllGroups(): List<SpoofGroup> = groups.values.toList()

/** Alias for [JsonConfig.withGroup] - adds or updates a group. */
fun JsonConfig.addOrUpdateGroup(group: SpoofGroup): JsonConfig = withGroup(group)

/** Alias for [JsonConfig.withAppConfig] - sets app configuration. */
fun JsonConfig.setAppConfig(appConfig: AppConfig): JsonConfig = withAppConfig(appConfig)

/** Serializes this config to a pretty-printed JSON string. */
fun JsonConfig.toPrettyJsonString(): String = prettyJsonSerializer.encodeToString(this)

/**
 * Parses a JSON string into a [JsonConfig], preserving parse failures for callers that need to
 * surface recovery or logging decisions.
 */
fun JsonConfig.Companion.parseCatching(json: String?): Result<JsonConfig> = runCatching {
    require(!json.isNullOrBlank()) { "Config JSON must not be blank" }
    parse(json)
}

/** Safely parses a JSON string, returning a default config on failure. */
fun JsonConfig.Companion.parseOrDefault(
    json: String?,
    onFailure: ((Throwable) -> Unit)? = null,
): JsonConfig =
    parseCatching(json).getOrElse { error ->
        onFailure?.invoke(error)
        createDefault()
    }
