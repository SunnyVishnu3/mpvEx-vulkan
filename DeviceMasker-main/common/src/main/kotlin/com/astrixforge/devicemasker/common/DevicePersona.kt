package com.astrixforge.devicemasker.common

import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

/**
 * Coherent device persona snapshot published per package for xposed-side runtime consumption.
 *
 * This sits alongside legacy flat per-type keys during migration and becomes the preferred runtime
 * contract for hookers that need cross-surface consistency.
 */
@Serializable
data class DevicePersona(
    val schemaVersion: Int = 1,
    val groupId: String,
    val packageName: String,
    val rootSeed: String,
    val version: Long,
    val generatedAt: Long,
    val deviceProfileId: String,
    val hardware: HardwarePersona,
    val subscriptions: List<SubscriptionPersona>,
    val location: LocationPersona,
    val networkEnvironment: NetworkEnvironmentPersona,
    val tracking: TrackingPersona,
    val browser: BrowserPersona,
) {
    fun getValue(type: SpoofType): String? = PERSONA_VALUE_READERS[type]?.invoke(this)

    fun toJsonString(): String = jsonSerializer.encodeToString(this)

    companion object {
        private val jsonSerializer = Json {
            ignoreUnknownKeys = true
            encodeDefaults = true
        }

        fun parse(json: String): DevicePersona = jsonSerializer.decodeFromString(json)

        fun parseOrNull(json: String?): DevicePersona? =
            runCatching {
                    if (json.isNullOrBlank()) {
                        null
                    } else {
                        parse(json)
                    }
                }
                .getOrNull()
    }
}

private val PERSONA_VALUE_READERS: Map<SpoofType, DevicePersona.() -> String?> =
    mapOf(
        SpoofType.IMEI to { hardware.primaryImei },
        SpoofType.IMSI to { subscriptions.firstOrNull()?.imsi },
        SpoofType.SERIAL to { hardware.serial },
        SpoofType.ICCID to { subscriptions.firstOrNull()?.iccid },
        SpoofType.PHONE_NUMBER to { subscriptions.firstOrNull()?.phoneNumber },
        SpoofType.SIM_COUNTRY_ISO to { subscriptions.firstOrNull()?.simCountryIso },
        SpoofType.NETWORK_COUNTRY_ISO to { subscriptions.firstOrNull()?.networkCountryIso },
        SpoofType.SIM_OPERATOR_NAME to { subscriptions.firstOrNull()?.simOperatorName },
        SpoofType.NETWORK_OPERATOR to { subscriptions.firstOrNull()?.networkOperator },
        SpoofType.WIFI_MAC to { hardware.wifiMac },
        SpoofType.BLUETOOTH_MAC to { hardware.bluetoothMac },
        SpoofType.WIFI_SSID to { networkEnvironment.ssid },
        SpoofType.WIFI_BSSID to { networkEnvironment.bssid },
        SpoofType.CARRIER_NAME to { subscriptions.firstOrNull()?.carrierName },
        SpoofType.CARRIER_MCC_MNC to { subscriptions.firstOrNull()?.carrierMccMnc },
        SpoofType.ANDROID_ID to { tracking.androidId },
        SpoofType.GSF_ID to { tracking.gsfId },
        SpoofType.ADVERTISING_ID to { tracking.advertisingId },
        SpoofType.MEDIA_DRM_ID to { tracking.mediaDrmId },
        SpoofType.DEVICE_PROFILE to { deviceProfileId },
        SpoofType.LOCATION_LATITUDE to { formatCoordinate(location.latitude) },
        SpoofType.LOCATION_LONGITUDE to { formatCoordinate(location.longitude) },
        SpoofType.TIMEZONE to { location.timezone },
        SpoofType.LOCALE to { location.locale },
    )

private fun formatCoordinate(value: Double): String =
    String.format(java.util.Locale.US, "%.6f", value)

@Serializable
data class HardwarePersona(
    val primaryImei: String,
    val secondaryImei: String? = null,
    val serial: String,
    val wifiMac: String,
    val bluetoothMac: String,
)

@Serializable
data class SubscriptionPersona(
    val slotIndex: Int,
    val isActive: Boolean = true,
    val carrierName: String,
    val carrierMccMnc: String,
    val simOperatorName: String,
    val networkOperator: String,
    val simCountryIso: String,
    val networkCountryIso: String,
    val phoneNumber: String,
    val imsi: String,
    val iccid: String,
)

@Serializable
data class LocationPersona(
    val countryIso: String,
    val region: String? = null,
    val timezone: String,
    val locale: String,
    val latitude: Double,
    val longitude: Double,
)

@Serializable
data class NetworkEnvironmentPersona(
    val ssid: String,
    val bssid: String,
    val networkType: Int,
    val isRoaming: Boolean = false,
)

@Serializable
data class TrackingPersona(
    val androidId: String,
    val gsfId: String,
    val advertisingId: String,
    val mediaDrmId: String,
)

@Serializable data class BrowserPersona(val userAgentModel: String, val androidRelease: String)

@Serializable
data class PersonaValidationResult(val isValid: Boolean, val issues: List<String> = emptyList())
