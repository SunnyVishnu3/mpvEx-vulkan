package com.astrixforge.devicemasker.common.generators

import com.astrixforge.devicemasker.common.util.secureRandom
import java.security.SecureRandom

/**
 * MAC Address Generator for network spoofing.
 *
 * MAC Address Structure (48 bits / 6 octets):
 * - First 3 octets: OUI (Organizationally Unique Identifier) - Identifies manufacturer
 * - Last 3 octets: NIC (Network Interface Controller) - Device-specific
 *
 * Important Bits in First Octet:
 * - Bit 0 (LSB): 0 = Unicast, 1 = Multicast
 * - Bit 1: 0 = Globally Unique (OUI enforced), 1 = Locally Administered
 *
 * For spoofing, we typically want:
 * - Unicast addresses (bit 0 = 0)
 * - Locally administered (bit 1 = 1) OR use real OUIs
 */
object MACGenerator {
    private const val UNICAST_LOCAL_ADMIN_MASK = 0xFC
    private const val LOCAL_ADMIN_BIT = 0x02

    /**
     * Real OUI prefixes from major device manufacturers. These are 24-bit identifiers assigned by
     * IEEE.
     */
    private val MANUFACTURER_OUIS =
        mapOf(
            // Samsung
            "samsung" to
                listOf(
                    "00:16:32",
                    "00:21:19",
                    "00:24:54",
                    "00:26:37",
                    "00:E0:91",
                    "3C:5A:B4",
                    "78:AB:BB",
                ),
            // Apple
            "apple" to
                listOf(
                    "00:03:93",
                    "00:0A:27",
                    "00:0D:93",
                    "00:1D:4F",
                    "00:1E:C2",
                    "00:1F:5B",
                    "00:22:41",
                    "00:26:BB",
                ),
            // Google
            "google" to listOf("3C:5A:B4", "F4:F5:D8", "54:60:09", "94:EB:2C"),
            // Xiaomi
            "xiaomi" to
                listOf("04:CF:8C", "0C:1D:AF", "14:F6:5A", "18:59:36", "28:6C:07", "34:CE:00"),
            // Huawei
            "huawei" to
                listOf("00:1E:10", "00:25:68", "00:25:9E", "00:46:4B", "00:66:4B", "04:F9:38"),
            // Intel (common in laptops)
            "intel" to
                listOf("00:1C:C0", "00:1D:E0", "00:1E:67", "00:1F:3B", "00:22:FA", "00:24:D7"),
            // Qualcomm/Broadcom (common in phones)
            "qualcomm" to listOf("00:00:F0", "00:15:FF", "00:18:0A", "00:1A:6B", "00:1C:57"),
            // Realtek (common WiFi chips)
            "realtek" to listOf("00:1E:E3", "00:60:DE", "00:E0:4C", "18:02:AE", "34:29:12"),
            // Generic random
            "generic" to listOf("02:00:00", "06:00:00", "0A:00:00", "0E:00:00"),
        )

    /** Secure random instance for cryptographic-quality randomness. */
    private val secureRandom = SecureRandom()

    /**
     * Generates a random valid unicast MAC address. Uses locally administered bit for maximum
     * compatibility.
     *
     * @return A MAC address in XX:XX:XX:XX:XX:XX format
     */
    fun generate(): String {
        val octets = ByteArray(6).apply { secureRandom.nextBytes(this) }

        // Ensure unicast (clear bit 0) and locally administered (set bit 1)
        octets[0] = (octets[0].toInt() and UNICAST_LOCAL_ADMIN_MASK or LOCAL_ADMIN_BIT).toByte()

        return octets.joinToString(":") { "%02X".format(it) }
    }

    /**
     * Generates a MAC address with a real manufacturer OUI prefix. This makes the address appear to
     * be from a legitimate device.
     *
     * @param manufacturer The manufacturer name (samsung, apple, google, etc.)
     * @return A MAC address with the specified manufacturer's OUI
     */
    fun generateForManufacturer(manufacturer: String): String {
        val oui =
            MANUFACTURER_OUIS[manufacturer.lowercase()]?.secureRandom()
                ?: MANUFACTURER_OUIS["generic"]?.secureRandom()
                ?: return generate()

        // Generate the last 3 octets randomly
        val nicBytes = ByteArray(3).apply { secureRandom.nextBytes(this) }

        val nicPart = nicBytes.joinToString(":") { "%02X".format(it) }

        return "$oui:$nicPart"
    }

    /**
     * Generates a WiFi-typical MAC address (common for mobile devices).
     *
     * @return A MAC address typical for WiFi interfaces
     */
    fun generateWiFiMAC(): String {
        return generate()
    }

    /**
     * Generates a WiFi MAC address with optional manufacturer correlation.
     *
     * 50% chance of using manufacturer's OUI, 50% locally-administered. This creates realistic
     * variation (some devices use manufacturer OUI, others use locally-administered MACs).
     *
     * @param manufacturer Device manufacturer (optional)
     * @return A MAC address for WiFi interface
     */
    fun generateWiFiMAC(manufacturer: String?): String {
        return if (manufacturer != null && secureRandom.nextBoolean()) {
            // 50% chance: Use manufacturer OUI
            generateForManufacturer(manufacturer)
        } else {
            // 50% chance: Use locally-administered
            generate()
        }
    }

    /**
     * Generates a Bluetooth-typical MAC address. Note: Bluetooth MACs often use similar OUIs but
     * may have different conventions.
     *
     * @return A MAC address typical for Bluetooth interfaces
     */
    fun generateBluetoothMAC(): String {
        // Bluetooth typically uses same OUI as WiFi for integrated chips
        return generateWiFiMAC()
    }
}
