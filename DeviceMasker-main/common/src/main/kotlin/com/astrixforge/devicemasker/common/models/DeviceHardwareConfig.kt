package com.astrixforge.devicemasker.common.models

import com.astrixforge.devicemasker.common.DeviceProfilePreset
import kotlinx.serialization.Serializable

/**
 * Correlated device hardware values.
 *
 * These values represent physical hardware characteristics that must be consistent:
 * - IMEI identifies the device on GSM/LTE networks (all modern phones use this)
 * - Serial number pattern matches the manufacturer
 * - WiFi MAC may use manufacturer's OUI (first 3 bytes)
 * - Bluetooth MAC is independent (different chip)
 *
 * Note: MEID has been removed as CDMA networks were deprecated/shut down in 2022. All modern
 * devices use IMEI only.
 */
@Suppress("unused") // Properties used for serialization and cross-module access
@Serializable
data class DeviceHardwareConfig(
    val deviceProfile: DeviceProfilePreset,
    val imei: String, // Primary IMEI (required for all modern devices)
    val serial: String, // Matches manufacturer's pattern
    val wifiMAC: String, // May use manufacturer's OUI
    val bluetoothMAC: String, // Independent from WiFi
) {
    init {
        // Validate IMEI is present and properly formatted
        require(imei.isNotBlank()) { "Device must have a valid IMEI" }
        require(imei.length == IMEI_LENGTH && imei.all { it.isDigit() }) {
            "IMEI must be 15 digits, got: $imei"
        }
    }

    /** Get the device manufacturer from the profile. */
    val manufacturer: String
        get() = deviceProfile.manufacturer

    /** Get the device model from the profile. */
    val model: String
        get() = deviceProfile.model

    /**
     * Check if this is a dual-SIM device.
     *
     * Note: Dual-SIM devices have 2 IMEIs, not IMEI + MEID. This is currently not implemented but
     * can be added later.
     */
    val isDualSIM: Boolean
        get() = deviceProfile.simCount > 1

    private companion object {
        private const val IMEI_LENGTH = 15
    }
}
