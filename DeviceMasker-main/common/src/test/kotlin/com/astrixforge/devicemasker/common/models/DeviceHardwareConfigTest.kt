package com.astrixforge.devicemasker.common.models

import com.astrixforge.devicemasker.common.DeviceProfilePreset
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import org.junit.Test

class DeviceHardwareConfigTest {

    @Test
    fun `isDualSIM is true when preset sim count is greater than one`() {
        val preset = DeviceProfilePreset.PRESETS.first { it.simCount > 1 }
        val config =
            DeviceHardwareConfig(
                deviceProfile = preset,
                imei = "353325091234567",
                serial = "SERIAL12345",
                wifiMAC = "02:11:22:33:44:55",
                bluetoothMAC = "02:66:77:88:99:AA",
            )

        assertTrue(config.isDualSIM)
    }

    @Test
    fun `isDualSIM is false when preset sim count is one`() {
        val preset = DeviceProfilePreset.PRESETS.first { it.simCount == 1 }
        val config =
            DeviceHardwareConfig(
                deviceProfile = preset,
                imei = "353325091234567",
                serial = "SERIAL12345",
                wifiMAC = "02:11:22:33:44:55",
                bluetoothMAC = "02:66:77:88:99:AA",
            )

        assertFalse(config.isDualSIM)
    }
}
