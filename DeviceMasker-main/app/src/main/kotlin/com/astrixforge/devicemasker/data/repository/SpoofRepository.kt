package com.astrixforge.devicemasker.data.repository

import android.content.Context
import com.astrixforge.devicemasker.common.AppConfig
import com.astrixforge.devicemasker.common.CorrelationGroup
import com.astrixforge.devicemasker.common.DeviceProfilePreset
import com.astrixforge.devicemasker.common.SpoofGroup
import com.astrixforge.devicemasker.common.SpoofType
import com.astrixforge.devicemasker.common.generators.DeviceHardwareGenerator
import com.astrixforge.devicemasker.common.generators.ICCIDGenerator
import com.astrixforge.devicemasker.common.generators.IMSIGenerator
import com.astrixforge.devicemasker.common.generators.MACGenerator
import com.astrixforge.devicemasker.common.generators.PhoneNumberGenerator
import com.astrixforge.devicemasker.common.generators.SIMGenerator
import com.astrixforge.devicemasker.common.generators.UUIDGenerator
import com.astrixforge.devicemasker.common.getAllGroups
import com.astrixforge.devicemasker.common.isAppAssigned
import com.astrixforge.devicemasker.common.models.Carrier
import com.astrixforge.devicemasker.common.models.DeviceHardwareConfig
import com.astrixforge.devicemasker.common.models.LocationConfig
import com.astrixforge.devicemasker.common.models.SIMConfig
import com.astrixforge.devicemasker.common.util.secureRandom
import com.astrixforge.devicemasker.common.withEnabled
import com.astrixforge.devicemasker.common.withValue
import com.astrixforge.devicemasker.service.ConfigManager
import com.astrixforge.devicemasker.service.IConfigManager
import java.util.concurrent.atomic.AtomicReference
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.serialization.SerializationException
import timber.log.Timber

/**
 * Main repository combining all spoof-related data operations.
 *
 * Multi-Module Architecture: This repository now wraps configManager and provides the same API to
 * the UI while using JsonConfig as the backing store.
 *
 * @param context Application context (for legacy compatibility)
 * @param configManager Config manager instance (default [configManager] for production)
 * @param appScopeRepository App scope repository instance (default production impl)
 */
// Suppress: suspend modifiers are kept for API consistency and future-proofing.
// configManager may become async in the future (database, network sync).
// TooManyFunctions is suppressed on this compatibility facade only; contracts are split by
// workflow.
@Suppress("RedundantSuspendModifier", "TooManyFunctions")
class SpoofRepository
@JvmOverloads
constructor(
    context: Context,
    private val configManager: IConfigManager = ConfigManager,
    override val appScopeRepository: IAppScopeRepository = AppScopeRepository(context),
) : ISpoofRepository {

    // ═══════════════════════════════════════════════════════════
    // CORRELATION CACHING
    // ═══════════════════════════════════════════════════════════

    /**
     * Cached groups for correlated value generation.
     *
     * These ensure that values in the same correlation group always use the same underlying group,
     * preventing detection from mismatches.
     */
    private val cachedSIMConfig = AtomicReference<SIMConfig?>(null)
    private val cachedLocationConfig = AtomicReference<LocationConfig?>(null)
    private val cachedDeviceHardwareConfig = AtomicReference<DeviceHardwareConfig?>(null)

    // ═══════════════════════════════════════════════════════════
    // UI STATE FLOWS
    // ═══════════════════════════════════════════════════════════

    /** Flow of module enabled state. */
    override val moduleEnabled: Flow<Boolean> = configManager.config.map { it.isModuleEnabled }

    /** Flow of all groups. */
    override val groups: Flow<List<SpoofGroup>> = configManager.config.map { it.getAllGroups() }

    /** Flow of canonical per-app settings. */
    override val appConfigs: Flow<Map<String, AppConfig>> =
        configManager.config.map { it.appConfigs }

    /** Flow of the active group (default group). */
    override val activeGroup: Flow<SpoofGroup?> = configManager.config.map { it.getDefaultGroup() }

    /** Flow of enabled app count. */
    override val enabledAppCount: Flow<Int> =
        configManager.config.map { config ->
            config.appConfigs.values.count { appConfig ->
                appConfig.isEnabled &&
                    config.getGroupForApp(appConfig.packageName)?.isEnabled == true
            }
        }

    /** Combined UI state flow for dashboard. */
    data class DashboardState(
        val isModuleEnabled: Boolean,
        val activeGroup: SpoofGroup?,
        val enabledAppCount: Int,
        val groupCount: Int,
    )

    override val dashboardState: Flow<DashboardState> =
        combine(moduleEnabled, activeGroup, enabledAppCount, groups) {
            enabled,
            group,
            appCount,
            groupList ->
            DashboardState(
                isModuleEnabled = enabled,
                activeGroup = group,
                enabledAppCount = appCount,
                groupCount = groupList.size,
            )
        }

    // ═══════════════════════════════════════════════════════════
    // MODULE SETTINGS
    // ═══════════════════════════════════════════════════════════

    /** Enables or disables the module globally. */
    override suspend fun setModuleEnabled(enabled: Boolean) {
        configManager.setModuleEnabled(enabled)
    }

    /** Sets the active group by ID (makes it default). */
    override suspend fun setActiveGroup(groupId: String) {
        configManager.setDefaultGroup(groupId)
    }

    // ═══════════════════════════════════════════════════════════
    // VALUE GENERATION (WITH CORRELATION)
    // ═══════════════════════════════════════════════════════════

    /**
     * Generates a new random value for a spoof type.
     *
     * Values in the same correlation group will use the same underlying profile to ensure
     * consistency (e.g., IMSI and ICCID both use same carrier).
     */
    override fun generateValue(type: SpoofType): String {
        return when (type.correlationGroup) {
            CorrelationGroup.SIM_CARD -> generateSIMValue(type)
            CorrelationGroup.LOCATION -> generateLocationValue(type)
            CorrelationGroup.DEVICE_HARDWARE -> generateDeviceHardwareValue(type)
            CorrelationGroup.NONE -> generateIndependentValue(type)
        }
    }

    /**
     * Generates correlated SIM card values. All SIM values use the same carrier profile for
     * consistency.
     */
    private fun generateSIMValue(type: SpoofType): String {
        // Generate SIM config if not cached
        val simConfig =
            cachedSIMConfig.updateAndGet { current -> current ?: SIMGenerator.generate() }
                ?: error("SIM config generation failed")

        return when (type) {
            SpoofType.IMSI -> simConfig.imsi
            SpoofType.ICCID -> simConfig.iccid
            SpoofType.CARRIER_NAME -> simConfig.carrierName
            SpoofType.CARRIER_MCC_MNC -> simConfig.mccMnc
            SpoofType.PHONE_NUMBER -> simConfig.phoneNumber
            // NEW: Additional SIM values for comprehensive spoofing
            SpoofType.SIM_COUNTRY_ISO -> simConfig.simCountryIso
            SpoofType.NETWORK_COUNTRY_ISO -> simConfig.networkCountryIso
            SpoofType.SIM_OPERATOR_NAME -> simConfig.simOperatorName
            SpoofType.NETWORK_OPERATOR -> simConfig.networkOperator
            else -> throw IllegalArgumentException("Not a SIM value: $type")
        }
    }

    /**
     * Regenerates ONLY a specific SIM value while keeping the same carrier.
     *
     * This is used when the user wants to regenerate just the phone number, IMSI, or ICCID without
     * changing the carrier. This prevents the bug where regenerating phone number would show wrong
     * country code.
     *
     * @param type The specific SIM type to regenerate
     * @return The new value, using the SAME carrier as currently cached
     */
    override fun regenerateSIMValueOnly(type: SpoofType): String {
        // Get the current carrier from cache, or generate new config if no cache
        val currentCarrier = cachedSIMConfig.get()?.carrier ?: Carrier.nextSecureRandom()

        // Generate ONLY the specific value using the SAME carrier
        return when (type) {
            SpoofType.PHONE_NUMBER -> PhoneNumberGenerator.generate(currentCarrier)
            SpoofType.IMSI -> IMSIGenerator.generate(currentCarrier)
            SpoofType.ICCID -> ICCIDGenerator.generate(currentCarrier)
            // For carrier-derived values, always use the cached profile
            SpoofType.CARRIER_NAME -> currentCarrier.name
            SpoofType.CARRIER_MCC_MNC -> currentCarrier.mccMnc
            SpoofType.SIM_COUNTRY_ISO -> currentCarrier.countryIsoLower
            SpoofType.NETWORK_COUNTRY_ISO -> currentCarrier.countryIsoLower
            SpoofType.SIM_OPERATOR_NAME -> currentCarrier.name
            SpoofType.NETWORK_OPERATOR -> currentCarrier.mccMnc
            else -> throw IllegalArgumentException("Not a SIM value: $type")
        }
    }

    /** Generates correlated location values. Timezone and locale are from the same country. */
    private fun generateLocationValue(type: SpoofType): String {
        // Generate location config if not cached
        val locationConfig =
            cachedLocationConfig.updateAndGet { current -> current ?: LocationConfig.generate() }
                ?: error("Location config generation failed")

        return when (type) {
            SpoofType.TIMEZONE -> locationConfig.timezone
            SpoofType.LOCALE -> locationConfig.locale
            else -> throw IllegalArgumentException("Not a location value: $type")
        }
    }

    /**
     * Generates correlated device hardware values. IMEI, Serial, WiFi MAC match the device profile.
     *
     * Note: MEID removed - CDMA deprecated since 2022.
     */
    private fun generateDeviceHardwareValue(type: SpoofType): String {
        // Generate device hardware config if not cached
        val hardwareConfig =
            cachedDeviceHardwareConfig.updateAndGet { current ->
                current ?: DeviceHardwareGenerator.generate()
            } ?: error("Device hardware config generation failed")

        return when (type) {
            SpoofType.IMEI -> hardwareConfig.imei
            SpoofType.SERIAL -> hardwareConfig.serial
            SpoofType.WIFI_MAC -> hardwareConfig.wifiMAC
            else -> throw IllegalArgumentException("Not a device hardware value: $type")
        }
    }

    /** Generates independent values (no correlation needed). */
    private fun generateIndependentValue(type: SpoofType): String {
        return when (type) {
            // Network (independent)
            SpoofType.BLUETOOTH_MAC -> MACGenerator.generateBluetoothMAC()
            SpoofType.WIFI_SSID -> generateRealisticSSID()
            SpoofType.WIFI_BSSID -> MACGenerator.generate()

            // Advertising (all independent)
            SpoofType.ANDROID_ID -> UUIDGenerator.generateAndroidId()
            SpoofType.GSF_ID -> UUIDGenerator.generateGSFId()
            SpoofType.ADVERTISING_ID -> UUIDGenerator.generateAdvertisingId()
            SpoofType.MEDIA_DRM_ID -> UUIDGenerator.generateMediaDrmId()

            // System
            SpoofType.DEVICE_PROFILE ->
                com.astrixforge.devicemasker.common.DeviceProfilePreset.PRESETS.secureRandom().id

            // Location (independent coordinates)
            SpoofType.LOCATION_LATITUDE ->
                String.format(
                    java.util.Locale.US,
                    COORDINATE_FORMAT,
                    (-MAX_LATITUDE..MAX_LATITUDE).secureRandom(),
                )

            SpoofType.LOCATION_LONGITUDE ->
                String.format(
                    java.util.Locale.US,
                    COORDINATE_FORMAT,
                    (-MAX_LONGITUDE..MAX_LONGITUDE).secureRandom(),
                )

            else -> throw IllegalArgumentException("Unknown independent type: $type")
        }
    }

    /**
     * Clears cached correlation profiles. Call this to force generation of new correlated values.
     */
    override fun resetCorrelations() {
        cachedSIMConfig.set(null)
        cachedLocationConfig.set(null)
        cachedDeviceHardwareConfig.set(null)
    }

    /**
     * Resets a specific correlation group's cache. Call this before regenerating correlated values
     * to get fresh values.
     */
    override fun resetCorrelationGroup(group: CorrelationGroup) {
        when (group) {
            CorrelationGroup.SIM_CARD -> cachedSIMConfig.set(null)
            CorrelationGroup.LOCATION -> cachedLocationConfig.set(null)
            CorrelationGroup.DEVICE_HARDWARE -> cachedDeviceHardwareConfig.set(null)
            CorrelationGroup.NONE -> {
                /* No cache for independent values */
            }
        }
    }

    /**
     * Regenerates both timezone and locale values together.
     *
     * This ensures both come from the SAME location config (same country), avoiding mismatches like
     * "Asia/Kolkata" with "en_US".
     *
     * @param groupId Group to update
     */
    override suspend fun regenerateLocationValues(groupId: String) {
        val group = configManager.getGroup(groupId) ?: return

        // Reset cache to get fresh location config
        cachedLocationConfig.set(null)

        // Generate new location config
        val locationConfig = LocationConfig.generate()
        cachedLocationConfig.set(locationConfig)

        // Update both values from the SAME config
        var updatedGroup = group.withValue(SpoofType.TIMEZONE, locationConfig.timezone)
        updatedGroup = updatedGroup.withValue(SpoofType.LOCALE, locationConfig.locale)

        configManager.updateGroup(updatedGroup)
    }

    /**
     * Updates a group with SIM values from a specific carrier.
     *
     * This generates all SIM-related values (IMSI, ICCID, Phone, etc.) from the selected carrier
     * and updates the group.
     *
     * ALSO syncs Location values (timezone/locale) to match carrier's country. This prevents
     * detection from SIM/Location country mismatches.
     *
     * @param groupId Group to update
     * @param carrier The carrier to use for generation
     */
    override suspend fun updateGroupWithCarrier(groupId: String, carrier: Carrier) {
        val group = configManager.getGroup(groupId) ?: return

        // Generate SIM config from specific carrier
        val simConfig = SIMGenerator.generate(carrier)
        cachedSIMConfig.set(simConfig)

        // NEW: Also generate location matching carrier's country
        // This ensures timezone/locale/GPS match the SIM country
        val locationConfig = LocationConfig.generateForCarrier(carrier)
        cachedLocationConfig.set(locationConfig)

        // Update all SIM-related values in the group
        var updatedGroup = group.copy(selectedCarrierMccMnc = carrier.mccMnc)
        updatedGroup = updatedGroup.withValue(SpoofType.IMSI, simConfig.imsi)
        updatedGroup = updatedGroup.withValue(SpoofType.ICCID, simConfig.iccid)
        updatedGroup = updatedGroup.withValue(SpoofType.PHONE_NUMBER, simConfig.phoneNumber)
        updatedGroup = updatedGroup.withValue(SpoofType.CARRIER_NAME, simConfig.carrierName)
        updatedGroup = updatedGroup.withValue(SpoofType.CARRIER_MCC_MNC, simConfig.mccMnc)
        updatedGroup = updatedGroup.withValue(SpoofType.SIM_COUNTRY_ISO, simConfig.simCountryIso)
        updatedGroup =
            updatedGroup.withValue(SpoofType.NETWORK_COUNTRY_ISO, simConfig.networkCountryIso)
        updatedGroup =
            updatedGroup.withValue(SpoofType.SIM_OPERATOR_NAME, simConfig.simOperatorName)
        updatedGroup = updatedGroup.withValue(SpoofType.NETWORK_OPERATOR, simConfig.networkOperator)

        // Sync Location to carrier country (prevents detection)
        updatedGroup = updatedGroup.withValue(SpoofType.TIMEZONE, locationConfig.timezone)
        updatedGroup = updatedGroup.withValue(SpoofType.LOCALE, locationConfig.locale)

        // NEW: Sync GPS coordinates to carrier country
        updatedGroup =
            updatedGroup.withValue(
                SpoofType.LOCATION_LATITUDE,
                String.format(java.util.Locale.US, "%.6f", locationConfig.latitude),
            )
        updatedGroup =
            updatedGroup.withValue(
                SpoofType.LOCATION_LONGITUDE,
                String.format(java.util.Locale.US, "%.6f", locationConfig.longitude),
            )

        configManager.updateGroup(updatedGroup)
    }

    /**
     * Updates a group with hardware values matching the device profile.
     *
     * When user selects a Device Profile (e.g., "Pixel 8 Pro"), this ensures:
     * - IMEI uses appropriate TAC prefix
     * - Serial matches manufacturer pattern (e.g., FA6AB for Google)
     * - WiFi MAC may use manufacturer OUI
     *
     * @param groupId Group to update
     * @param presetId The device preset ID to use
     */
    override suspend fun updateGroupWithDeviceProfile(groupId: String, presetId: String) {
        val group = configManager.getGroup(groupId) ?: return
        val preset = DeviceProfilePreset.findById(presetId) ?: return

        // Generate hardware matching the device profile
        val hardwareConfig = DeviceHardwareGenerator.generate(preset)
        cachedDeviceHardwareConfig.set(hardwareConfig)

        var updatedGroup = group.withValue(SpoofType.DEVICE_PROFILE, presetId)
        updatedGroup = updatedGroup.withValue(SpoofType.IMEI, hardwareConfig.imei)
        updatedGroup = updatedGroup.withValue(SpoofType.SERIAL, hardwareConfig.serial)
        updatedGroup = updatedGroup.withValue(SpoofType.WIFI_MAC, hardwareConfig.wifiMAC)

        configManager.updateGroup(updatedGroup)
    }

    /**
     * Regenerates all spoof values in a group while preserving internal consistency between related
     * identifiers.
     */
    override suspend fun regenerateAllValues(groupId: String) {
        val group = configManager.getGroup(groupId) ?: return

        val carrier = Carrier.nextSecureRandom()
        val simConfig = SIMGenerator.generate(carrier)
        val locationConfig = LocationConfig.generateForCarrier(carrier)
        val deviceProfile = DeviceProfilePreset.PRESETS.secureRandom()
        val hardwareConfig = DeviceHardwareGenerator.generate(deviceProfile)

        cachedSIMConfig.set(simConfig)
        cachedLocationConfig.set(locationConfig)
        cachedDeviceHardwareConfig.set(hardwareConfig)

        var updatedGroup = group.copy(selectedCarrierMccMnc = carrier.mccMnc)
        updatedGroup = updatedGroup.withValue(SpoofType.IMSI, simConfig.imsi)
        updatedGroup = updatedGroup.withValue(SpoofType.ICCID, simConfig.iccid)
        updatedGroup = updatedGroup.withValue(SpoofType.PHONE_NUMBER, simConfig.phoneNumber)
        updatedGroup = updatedGroup.withValue(SpoofType.SIM_COUNTRY_ISO, simConfig.simCountryIso)
        updatedGroup =
            updatedGroup.withValue(SpoofType.NETWORK_COUNTRY_ISO, simConfig.networkCountryIso)
        updatedGroup = updatedGroup.withValue(SpoofType.CARRIER_NAME, simConfig.carrierName)
        updatedGroup = updatedGroup.withValue(SpoofType.CARRIER_MCC_MNC, simConfig.mccMnc)
        updatedGroup =
            updatedGroup.withValue(SpoofType.SIM_OPERATOR_NAME, simConfig.simOperatorName)
        updatedGroup = updatedGroup.withValue(SpoofType.NETWORK_OPERATOR, simConfig.networkOperator)

        updatedGroup = updatedGroup.withValue(SpoofType.TIMEZONE, locationConfig.timezone)
        updatedGroup = updatedGroup.withValue(SpoofType.LOCALE, locationConfig.locale)
        updatedGroup =
            updatedGroup.withValue(
                SpoofType.LOCATION_LATITUDE,
                String.format(java.util.Locale.US, "%.6f", locationConfig.latitude),
            )
        updatedGroup =
            updatedGroup.withValue(
                SpoofType.LOCATION_LONGITUDE,
                String.format(java.util.Locale.US, "%.6f", locationConfig.longitude),
            )

        updatedGroup = updatedGroup.withValue(SpoofType.DEVICE_PROFILE, deviceProfile.id)
        updatedGroup = updatedGroup.withValue(SpoofType.IMEI, hardwareConfig.imei)
        updatedGroup = updatedGroup.withValue(SpoofType.SERIAL, hardwareConfig.serial)
        updatedGroup = updatedGroup.withValue(SpoofType.WIFI_MAC, hardwareConfig.wifiMAC)
        updatedGroup = updatedGroup.withValue(SpoofType.BLUETOOTH_MAC, hardwareConfig.bluetoothMAC)
        updatedGroup = updatedGroup.withValue(SpoofType.WIFI_SSID, generateRealisticSSID())
        updatedGroup = updatedGroup.withValue(SpoofType.WIFI_BSSID, MACGenerator.generate())
        updatedGroup =
            updatedGroup.withValue(SpoofType.ANDROID_ID, UUIDGenerator.generateAndroidId())
        updatedGroup = updatedGroup.withValue(SpoofType.GSF_ID, UUIDGenerator.generateGSFId())
        updatedGroup =
            updatedGroup.withValue(SpoofType.ADVERTISING_ID, UUIDGenerator.generateAdvertisingId())
        updatedGroup =
            updatedGroup.withValue(SpoofType.MEDIA_DRM_ID, UUIDGenerator.generateMediaDrmId())

        configManager.updateGroup(updatedGroup)
    }

    /** Generates realistic WiFi SSID names. Uses common router brands and patterns. */
    private fun generateRealisticSSID(): String {
        val patterns =
            listOf(
                // US ISP Routers
                { "\"NETGEAR-${randomHex(4)}\"" },
                { "\"NETGEAR-5G-${randomHex(2)}\"" },
                { "\"xfinitywifi\"" },
                { "\"XFINITY\"" },
                { "\"ATT${randomHex(6)}\"" },
                { "\"ATTWiFi-${randomHex(4)}\"" },
                // Common router brands
                { "\"TP-LINK_${randomHex(4)}\"" },
                { "\"TP-Link_${randomHex(4)}_5G\"" },
                { "\"ASUS_${randomHex(4)}\"" },
                { "\"ASUS_RT-${randomHex(4)}\"" },
                { "\"Linksys${randomDigits(5)}\"" },
                { "\"dlink-${randomHex(4)}\"" },
                { "\"ORBI${randomDigits(2)}\"" },
                { "\"eero-${randomHex(4)}\"" },
                { "\"GoogleWifi-${randomHex(4)}\"" },
                { "\"ARRIS-${randomHex(4)}\"" },
                // Generic home networks
                { "\"Home_WiFi\"" },
                { "\"MyNetwork-5G\"" },
                { "\"WiFi-${randomDigits(4)}\"" },
                { "\"Guest_Network\"" },
                { "\"${randomFamilyName()}_WiFi\"" },
                { "\"${randomFamilyName()}_5G\"" },
            )
        return patterns.secureRandom()()
    }

    private fun randomHex(length: Int) = buildString {
        repeat(length) { append("0123456789ABCDEF".secureRandom()) }
    }

    private fun randomDigits(length: Int) = buildString {
        repeat(length) { append((MIN_DIGIT..MAX_DIGIT).secureRandom()) }
    }

    private fun randomFamilyName(): String {
        val names =
            listOf(
                "Smith",
                "Johnson",
                "Williams",
                "Brown",
                "Jones",
                "Miller",
                "Davis",
                "Wilson",
                "Moore",
                "Taylor",
            )
        return names.secureRandom()
    }

    // ═══════════════════════════════════════════════════════════
    // GROUP MANAGEMENT (For GroupsScreen)
    // ═══════════════════════════════════════════════════════════

    /** Gets all groups as a Flow. */
    override fun getAllGroups(): Flow<List<SpoofGroup>> = groups

    /** Creates a new group with generated spoof values. */
    override suspend fun createGroup(name: String, description: String) {
        // Check if this is the first group (to auto-set as default)
        val isFirstGroup = configManager.getAllGroups().isEmpty()

        // Create the group with generated values
        val newGroup = configManager.createGroup(name)

        // Add description and initialize with generated values
        // If first group, also set as default
        var updatedGroup =
            newGroup.copy(
                description = description,
                isDefault = isFirstGroup, // First group becomes default automatically
            )
        SpoofType.entries.forEach { type ->
            val value = generateValue(type)
            updatedGroup = updatedGroup.withValue(type, value)
        }
        configManager.updateGroup(updatedGroup)
    }

    /** Updates an existing group. */
    override suspend fun updateGroup(group: SpoofGroup) {
        configManager.updateGroup(group)
    }

    /** Deletes a group by ID. */
    override suspend fun deleteGroup(groupId: String) {
        configManager.deleteGroup(groupId)
    }

    /** Sets a group as the default. */
    override suspend fun setDefaultGroup(groupId: String) {
        setActiveGroup(groupId)
    }

    /** Sets whether a group is enabled (master switch for all its apps). */
    override suspend fun setGroupEnabled(groupId: String, enabled: Boolean) {
        val group = configManager.getGroup(groupId) ?: return
        val updatedGroup = group.withEnabled(enabled)
        configManager.updateGroup(updatedGroup)
    }

    // ═══════════════════════════════════════════════════════════
    // APP ASSIGNMENT (For ProfileDetailScreen)
    // ═══════════════════════════════════════════════════════════

    /** Adds an app to a group. */
    override suspend fun addAppToGroup(groupId: String, packageName: String) {
        configManager.assignAppToGroup(packageName, groupId)
    }

    /** Removes an app from a group. */
    override suspend fun removeAppFromGroup(groupId: String, packageName: String) {
        val appConfig = configManager.getAppConfig(packageName)
        val isAssignedByCanonicalConfig = appConfig?.groupId == groupId
        val isAssignedByLegacyGroup =
            configManager.getGroup(groupId)?.isAppAssigned(packageName) == true

        if (isAssignedByCanonicalConfig || isAssignedByLegacyGroup) {
            configManager.unassignApp(packageName)
        }
    }

    override suspend fun setAppEnabled(packageName: String, enabled: Boolean) {
        configManager.setAppEnabled(packageName, enabled)
    }

    override suspend fun setAppRiskyHooksEnabled(packageName: String, enabled: Boolean) {
        configManager.setAppRiskyHooksEnabled(packageName, enabled)
    }

    override suspend fun setAppClassLookupHidingEnabled(packageName: String, enabled: Boolean) {
        configManager.setAppClassLookupHidingEnabled(packageName, enabled)
    }

    // ═══════════════════════════════════════════════════════════
    // EXPORT / IMPORT
    // ═══════════════════════════════════════════════════════════

    /** Exports all groups as JSON string. */
    override suspend fun exportGroups(): String {
        return configManager.config.first().toJsonString()
    }

    /** Imports groups from JSON string. Returns true on success. */
    override suspend fun importGroups(jsonString: String): Boolean {
        return try {
            val config = com.astrixforge.devicemasker.common.JsonConfig.parse(jsonString)
            config.getAllGroups().forEach { group -> configManager.updateGroup(group) }
            true
        } catch (e: SerializationException) {
            Timber.tag(TAG).w(e, "Failed to import spoof groups")
            false
        } catch (e: IllegalArgumentException) {
            Timber.tag(TAG).w(e, "Failed to import spoof groups")
            false
        }
    }

    companion object {
        private const val TAG = "SpoofRepository"

        @Volatile private var INSTANCE: SpoofRepository? = null

        /** Gets the singleton instance. */
        fun getInstance(context: Context): SpoofRepository {
            return INSTANCE
                ?: synchronized(this) {
                    INSTANCE ?: SpoofRepository(context.applicationContext).also { INSTANCE = it }
                }
        }
    }
}

private const val COORDINATE_FORMAT = "%.6f"
private const val MAX_LATITUDE = 90.0
private const val MAX_LONGITUDE = 180.0
private const val MIN_DIGIT = 0
private const val MAX_DIGIT = 9
