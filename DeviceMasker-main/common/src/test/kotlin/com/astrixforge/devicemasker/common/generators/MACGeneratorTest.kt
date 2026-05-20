package com.astrixforge.devicemasker.common.generators

import com.astrixforge.devicemasker.common.Utils
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import org.junit.Test

/**
 * Unit tests for MACGenerator.
 *
 * Tests validate:
 * 1. MAC format (XX:XX:XX:XX:XX:XX)
 * 2. Unicast bit (LSB of first octet = 0)
 * 3. Locally administered bit for generic MACs
 * 4. Manufacturer-specific OUI prefixes
 */
class MACGeneratorTest {

    // Regex pattern for valid MAC address
    private val macPattern = Regex("^([0-9A-F]{2}:){5}[0-9A-F]{2}$")

    @Test
    fun `generate returns valid MAC format`() {
        repeat(100) {
            val mac = MACGenerator.generate()
            assertTrue(
                macPattern.matches(mac),
                "MAC should match XX:XX:XX:XX:XX:XX format, got: $mac",
            )
        }
    }

    @Test
    fun `generate returns 6 octets separated by colons`() {
        repeat(100) {
            val mac = MACGenerator.generate()
            val octets = mac.split(":")
            assertEquals(6, octets.size, "MAC should have 6 octets")

            octets.forEach { octet ->
                assertEquals(2, octet.length, "Each octet should be 2 hex chars")
                assertTrue(
                    octet.all { it in "0123456789ABCDEF" },
                    "Octet should be uppercase hex: $octet",
                )
            }
        }
    }

    @Test
    fun `generate returns unicast address`() {
        repeat(100) {
            val mac = MACGenerator.generate()
            val firstOctet = mac.split(":")[0].toInt(16)

            // Unicast: LSB of first octet = 0
            val isUnicast = (firstOctet and 0x01) == 0
            assertTrue(isUnicast, "MAC should be unicast (LSB=0), got: $mac")
        }
    }

    @Test
    fun `generate returns locally administered address`() {
        repeat(100) {
            val mac = MACGenerator.generate()
            val firstOctet = mac.split(":")[0].toInt(16)

            // Locally administered: bit 1 = 1
            val isLocallyAdministered = (firstOctet and 0x02) != 0
            assertTrue(
                isLocallyAdministered,
                "Generic MAC should be locally administered (bit 1=1), got: $mac",
            )
        }
    }

    @Test
    fun `generateForManufacturer returns valid MAC`() {
        val manufacturers = listOf("samsung", "apple", "google", "xiaomi", "huawei", "unknown")

        manufacturers.forEach { manufacturer ->
            repeat(20) {
                val mac = MACGenerator.generateForManufacturer(manufacturer)
                assertTrue(
                    macPattern.matches(mac),
                    "MAC for $manufacturer should be valid format: $mac",
                )

                // Should still be unicast
                val firstOctet = mac.split(":")[0].toInt(16)
                val isUnicast = (firstOctet and 0x01) == 0
                assertTrue(isUnicast, "MAC for $manufacturer should be unicast: $mac")
            }
        }
    }

    @Test
    fun `generateWiFiMAC returns valid MAC`() {
        repeat(100) {
            val mac = MACGenerator.generateWiFiMAC()
            assertTrue(macPattern.matches(mac), "WiFi MAC should be valid format: $mac")
        }
    }

    @Test
    fun `generateBluetoothMAC returns valid MAC`() {
        repeat(100) {
            val mac = MACGenerator.generateBluetoothMAC()
            assertTrue(macPattern.matches(mac), "Bluetooth MAC should be valid format: $mac")
        }
    }

    @Test
    fun `generated MACs are unique`() {
        val macs = (1..1000).map { MACGenerator.generate() }.toSet()

        // With 48-bit random space, 1000 unique MACs should be guaranteed
        assertTrue(macs.size >= 990, "Expected nearly 1000 unique MACs, got ${macs.size}")
    }

    @Test
    fun `Utils validates generated MACs`() {
        repeat(100) {
            val mac = MACGenerator.generate()
            assertTrue(Utils.isValidMac(mac), "Utils.isValidMac should accept generated MAC: $mac")
        }
    }
}
