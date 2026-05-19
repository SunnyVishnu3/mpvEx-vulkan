package com.astrixforge.devicemasker.common

import kotlinx.serialization.Serializable

/**
 * Categories for grouping spoof types in the UI.
 *
 * Each category represents a logical grouping of device identifiers that share similar
 * characteristics or purposes.
 */
@Serializable
enum class SpoofCategory(val displayName: String) {
    DEVICE("Device"),
    NETWORK("Network"),
    ADVERTISING("Advertising"),
    SYSTEM("System"),
    LOCATION("Location"),
}

/**
 * Correlation groups for values that must be generated together.
 *
 * Values in the same group are interdependent and must maintain consistency. For example, IMSI and
 * CARRIER_MCC_MNC must use the same MCC/MNC value.
 */
@Serializable
enum class CorrelationGroup {
    /** No correlation - value is fully independent */
    NONE,

    /** SIM card identifiers - IMSI, ICCID, CARRIER must all match */
    SIM_CARD,

    /** Location settings - TIMEZONE, LOCALE should correlate */
    LOCATION,

    /** Device hardware - IMEI/MEID, Serial, MAC should match device */
    DEVICE_HARDWARE,
}

/**
 * Enumeration of all spoofable device identifiers.
 *
 * Each type represents a specific identifier that can be intercepted and replaced with a spoofed
 * value. Types are organized by [SpoofCategory].
 */
@Suppress("unused") // Enum values used in :xposed hookers
@Serializable
enum class SpoofType(
    val displayName: String,
    val category: SpoofCategory,
    val correlationGroup: CorrelationGroup = CorrelationGroup.NONE,
) {
    // ═══════════════════════════════════════════════════════════
    // DEVICE IDENTIFIERS
    // ═══════════════════════════════════════════════════════════

    /**
     * International Mobile Equipment Identity - Primary device identifier. 15 digits with Luhn
     * checksum validation.
     */
    IMEI(
        displayName = "IMEI",
        category = SpoofCategory.DEVICE,
        correlationGroup = CorrelationGroup.DEVICE_HARDWARE,
    ),

    /**
     * International Mobile Subscriber Identity - SIM card identifier. 15 digits identifying the
     * subscriber.
     */
    IMSI(
        displayName = "IMSI",
        category = SpoofCategory.DEVICE,
        correlationGroup = CorrelationGroup.SIM_CARD,
    ),

    /** Device serial number. Alphanumeric string, format varies by manufacturer. */
    SERIAL(
        displayName = "Serial Number",
        category = SpoofCategory.DEVICE,
        correlationGroup = CorrelationGroup.DEVICE_HARDWARE,
    ),

    /**
     * Integrated Circuit Card Identifier - SIM card serial. 19-20 digits identifying the SIM card.
     */
    ICCID(
        displayName = "ICCID",
        category = SpoofCategory.DEVICE,
        correlationGroup = CorrelationGroup.SIM_CARD,
    ),

    /** Phone number associated with the SIM. */
    PHONE_NUMBER(
        displayName = "Phone Number",
        category = SpoofCategory.DEVICE,
        correlationGroup = CorrelationGroup.SIM_CARD,
    ),

    /**
     * SIM card issuing country ISO code. Returned by TelephonyManager.getSimCountryIso(). Format:
     * lowercase 2-letter ISO code (e.g., "in" for India, "us" for USA)
     */
    SIM_COUNTRY_ISO(
        displayName = "SIM Country",
        category = SpoofCategory.DEVICE,
        correlationGroup = CorrelationGroup.SIM_CARD,
    ),

    /**
     * Current network country ISO code. Returned by TelephonyManager.getNetworkCountryIso().
     * Usually same as SIM country unless roaming. Format: lowercase 2-letter ISO code
     */
    NETWORK_COUNTRY_ISO(
        displayName = "Network Country",
        category = SpoofCategory.NETWORK,
        correlationGroup = CorrelationGroup.SIM_CARD,
    ),

    /**
     * SIM operator display name. Returned by TelephonyManager.getSimOperatorName(). This is the
     * carrier name as stored on the SIM card.
     */
    SIM_OPERATOR_NAME(
        displayName = "SIM Operator",
        category = SpoofCategory.DEVICE,
        correlationGroup = CorrelationGroup.SIM_CARD,
    ),

    /**
     * Network operator code (MCC+MNC as string). Returned by TelephonyManager.getNetworkOperator().
     * Format: 5-6 digit string (e.g., "40410" for Airtel Delhi)
     */
    NETWORK_OPERATOR(
        displayName = "Network Operator",
        category = SpoofCategory.NETWORK,
        correlationGroup = CorrelationGroup.SIM_CARD,
    ),

    // ═══════════════════════════════════════════════════════════
    // NETWORK IDENTIFIERS
    // ═══════════════════════════════════════════════════════════

    /** WiFi MAC address. 48-bit address in XX:XX:XX:XX:XX:XX format. */
    WIFI_MAC(
        displayName = "WiFi MAC",
        category = SpoofCategory.NETWORK,
        correlationGroup = CorrelationGroup.DEVICE_HARDWARE,
    ),

    /** Bluetooth MAC address. 48-bit address in XX:XX:XX:XX:XX:XX format. */
    BLUETOOTH_MAC(displayName = "Bluetooth MAC", category = SpoofCategory.NETWORK),

    /** Connected WiFi network name. */
    WIFI_SSID(displayName = "WiFi SSID", category = SpoofCategory.NETWORK),

    /** Connected WiFi access point MAC. */
    WIFI_BSSID(displayName = "WiFi BSSID", category = SpoofCategory.NETWORK),

    /** Carrier/operator name. */
    CARRIER_NAME(
        displayName = "Carrier Name",
        category = SpoofCategory.NETWORK,
        correlationGroup = CorrelationGroup.SIM_CARD,
    ),

    /** Mobile Country Code + Mobile Network Code. */
    CARRIER_MCC_MNC(
        displayName = "MCC/MNC",
        category = SpoofCategory.NETWORK,
        correlationGroup = CorrelationGroup.SIM_CARD,
    ),

    // ═══════════════════════════════════════════════════════════
    // ADVERTISING & TRACKING IDENTIFIERS
    // ═══════════════════════════════════════════════════════════

    /** Settings.Secure.ANDROID_ID - Per-app identifier. 16 hex characters. */
    ANDROID_ID(displayName = "Android ID", category = SpoofCategory.ADVERTISING),

    /** Google Services Framework ID. 16 hex characters, used by Google Play. */
    GSF_ID(displayName = "GSF ID", category = SpoofCategory.ADVERTISING),

    /** Google Advertising ID. UUID format, used for ad tracking. */
    ADVERTISING_ID(displayName = "Advertising ID", category = SpoofCategory.ADVERTISING),

    /** Media DRM Widevine device ID. 64 hex characters. */
    MEDIA_DRM_ID(displayName = "Media DRM ID", category = SpoofCategory.ADVERTISING),

    // ═══════════════════════════════════════════════════════════
    // SYSTEM / DEVICE PROFILE
    // ═══════════════════════════════════════════════════════════

    /**
     * Unified device profile - Sets all Build.* properties consistently.
     *
     * Instead of spoofing individual Build fields (which can cause detection), this applies a
     * complete device profile with matching:
     * - Build.FINGERPRINT
     * - Build.MODEL
     * - Build.MANUFACTURER
     * - Build.BRAND
     * - Build.DEVICE
     * - Build.PRODUCT
     * - Build.BOARD
     *
     * Value format: DeviceProfilePreset ID (e.g., "pixel_8_pro", "samsung_s24_ultra")
     *
     * @see DeviceProfilePreset for available presets
     */
    DEVICE_PROFILE(displayName = "Device Profile", category = SpoofCategory.SYSTEM),

    // ═══════════════════════════════════════════════════════════
    // LOCATION IDENTIFIERS
    // ═══════════════════════════════════════════════════════════

    /** GPS latitude coordinate. */
    LOCATION_LATITUDE(displayName = "Latitude", category = SpoofCategory.LOCATION),

    /** GPS longitude coordinate. */
    LOCATION_LONGITUDE(displayName = "Longitude", category = SpoofCategory.LOCATION),

    /** Device timezone. */
    TIMEZONE(
        displayName = "Timezone",
        category = SpoofCategory.LOCATION,
        correlationGroup = CorrelationGroup.LOCATION,
    ),

    /** Device locale/language setting. */
    LOCALE(
        displayName = "Locale",
        category = SpoofCategory.LOCATION,
        correlationGroup = CorrelationGroup.LOCATION,
    );

    companion object {
        /** Returns all spoof types for a given category. */
        fun byCategory(category: SpoofCategory): List<SpoofType> {
            return entries.filter { it.category == category }
        }

        /** Finds a SpoofType by its name, returns null if not found. */
        fun fromName(name: String): SpoofType? {
            return entries.find { it.name == name }
        }
    }
}
