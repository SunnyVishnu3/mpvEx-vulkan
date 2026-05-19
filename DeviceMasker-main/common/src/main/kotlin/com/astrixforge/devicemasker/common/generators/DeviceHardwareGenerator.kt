package com.astrixforge.devicemasker.common.generators

import com.astrixforge.devicemasker.common.DeviceProfilePreset
import com.astrixforge.devicemasker.common.models.DeviceHardwareConfig
import com.astrixforge.devicemasker.common.util.secureRandom

/**
 * Generates complete, correlated device hardware values.
 *
 * This ensures device hardware values are consistent:
 * - IMEI is always generated (all modern devices have IMEI)
 * - Serial number matches manufacturer pattern
 * - WiFi MAC may use manufacturer OUI
 * - All values align with the device profile
 *
 * Note: MEID has been removed as CDMA networks were deprecated in 2022.
 */
@Suppress("unused") // Methods used for device hardware spoofing
object DeviceHardwareGenerator {

    /**
     * Generates a complete device hardware config.
     *
     * @param deviceProfile The device profile to base hardware on
     * @return DeviceHardwareConfig with all correlated values
     */
    fun generate(deviceProfile: DeviceProfilePreset): DeviceHardwareConfig {
        return DeviceHardwareConfig(
            deviceProfile = deviceProfile,
            imei = IMEIGenerator.generateForPreset(deviceProfile),
            serial = SerialGenerator.generate(deviceProfile.manufacturer),
            wifiMAC = MACGenerator.generateWiFiMAC(deviceProfile.manufacturer),
            bluetoothMAC = MACGenerator.generateBluetoothMAC(), // Independent
        )
    }

    /**
     * Generates a device hardware config from a random preset.
     *
     * @return DeviceHardwareConfig with random device
     */
    fun generate(): DeviceHardwareConfig {
        val randomPreset = DeviceProfilePreset.PRESETS.secureRandom()
        return generate(randomPreset)
    }

    /**
     * Generates a device hardware config from a preset ID.
     *
     * @param presetId Device profile preset ID (e.g., "pixel_8_pro")
     * @return DeviceHardwareConfig or null if preset not found
     */
    fun generateFromPresetId(presetId: String): DeviceHardwareConfig? {
        val preset = DeviceProfilePreset.findById(presetId) ?: return null
        return generate(preset)
    }
}
