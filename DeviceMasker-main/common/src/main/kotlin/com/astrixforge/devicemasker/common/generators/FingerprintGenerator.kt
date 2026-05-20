package com.astrixforge.devicemasker.common.generators

import com.astrixforge.devicemasker.common.util.nextInt
import com.astrixforge.devicemasker.common.util.secureRandom

/**
 * Build Fingerprint Generator for system property spoofing.
 *
 * Build Fingerprint Format: brand/product/device:sdk_version/build_id/incremental:type/tags
 *
 * Example: google/redfin/redfin:11/RQ3A.210805.001.A1/7474174:user/release-keys
 *
 * Components:
 * - brand: Device manufacturer (google, samsung, xiaomi)
 * - product: Product code name
 * - device: Device code name (often same as product)
 * - sdk_version: Android version number (11, 12, 13, 14, etc.)
 * - build_id: Build identifier (e.g., RQ3A.210805.001.A1)
 * - incremental: Build number
 * - type: Build type (user, userdebug)
 * - tags: Build tags (release-keys, test-keys)
 */
@Suppress("unused") // Used for device profile spoofing
object FingerprintGenerator {
    private const val MIN_INCREMENTAL = 1_000_000
    private const val INCREMENTAL_BOUND = 9_000_000

    /**
     * Database of realistic device configurations. Each entry contains brand, product, device, and
     * typical build info.
     */
    private data class DeviceConfig(
        val brand: String,
        val product: String,
        val device: String,
        val manufacturer: String,
        val model: String,
        val buildIdPrefixes: List<String>,
    )

    private val DEVICE_DATABASE =
        listOf(
            // Google Pixel devices
            DeviceConfig(
                "google",
                "redfin",
                "redfin",
                "Google",
                "Pixel 5",
                listOf("SP1A", "TQ3A", "UQ1A"),
            ),
            DeviceConfig(
                "google",
                "oriole",
                "oriole",
                "Google",
                "Pixel 6",
                listOf("SQ3A", "TQ3A", "UQ1A"),
            ),
            DeviceConfig(
                "google",
                "raven",
                "raven",
                "Google",
                "Pixel 6 Pro",
                listOf("SQ3A", "TQ3A", "UQ1A"),
            ),
            DeviceConfig(
                "google",
                "panther",
                "panther",
                "Google",
                "Pixel 7",
                listOf("TQ3A", "UQ1A", "AP1A"),
            ),
            DeviceConfig(
                "google",
                "cheetah",
                "cheetah",
                "Google",
                "Pixel 7 Pro",
                listOf("TQ3A", "UQ1A", "AP1A"),
            ),
            DeviceConfig(
                "google",
                "lynx",
                "lynx",
                "Google",
                "Pixel 7a",
                listOf("TQ3A", "UQ1A", "AP1A"),
            ),
            DeviceConfig(
                "google",
                "husky",
                "husky",
                "Google",
                "Pixel 8 Pro",
                listOf("UQ1A", "AP1A", "AP2A"),
            ),
            DeviceConfig(
                "google",
                "shiba",
                "shiba",
                "Google",
                "Pixel 8",
                listOf("UQ1A", "AP1A", "AP2A"),
            ),
            DeviceConfig("google", "caiman", "caiman", "Google", "Pixel 9", listOf("AP3A", "BP1A")),
            DeviceConfig(
                "google",
                "tokay",
                "tokay",
                "Google",
                "Pixel 9 Pro",
                listOf("AP3A", "BP1A"),
            ),

            // Samsung Galaxy devices
            DeviceConfig(
                "samsung",
                "dm1q",
                "dm1q",
                "samsung",
                "SM-S911B",
                listOf("SP1A", "TP1A", "UP1A"),
            ),
            DeviceConfig(
                "samsung",
                "dm2q",
                "dm2q",
                "samsung",
                "SM-S916B",
                listOf("SP1A", "TP1A", "UP1A"),
            ),
            DeviceConfig(
                "samsung",
                "dm3q",
                "dm3q",
                "samsung",
                "SM-S918B",
                listOf("SP1A", "TP1A", "UP1A"),
            ),
            DeviceConfig("samsung", "e1q", "e1q", "samsung", "SM-S921B", listOf("UP1A", "AP1A")),
            DeviceConfig("samsung", "e2q", "e2q", "samsung", "SM-S926B", listOf("UP1A", "AP1A")),
            DeviceConfig("samsung", "e3q", "e3q", "samsung", "SM-S928B", listOf("UP1A", "AP1A")),

            // Xiaomi devices
            DeviceConfig(
                "Xiaomi",
                "venus",
                "venus",
                "Xiaomi",
                "M2011K2G",
                listOf("RKQ1", "SKQ1", "TKQ1"),
            ),
            DeviceConfig(
                "Xiaomi",
                "cepheus",
                "cepheus",
                "Xiaomi",
                "MI 9",
                listOf("PKQ1", "QKQ1", "RKQ1"),
            ),
            DeviceConfig(
                "Redmi",
                "alioth",
                "alioth",
                "Xiaomi",
                "M2012K11AG",
                listOf("RKQ1", "SKQ1"),
            ),

            // OnePlus devices
            DeviceConfig(
                "OnePlus",
                "NE2210",
                "NE2210",
                "OnePlus",
                "NE2210",
                listOf("SKQ1", "TKQ1"),
            ),
            DeviceConfig(
                "OnePlus",
                "CPH2423",
                "CPH2423",
                "OnePlus",
                "CPH2423",
                listOf("TP1A", "UP1A"),
            ),

            // Generic/fallback
            DeviceConfig(
                "Android",
                "generic",
                "generic",
                "Generic",
                "Android Device",
                listOf("UQ1A", "TQ3A"),
            ),
        )

    /** Generates a random build fingerprint. */
    fun generate(): String {
        val device = DEVICE_DATABASE.secureRandom()
        return generateForDevice(device, "14")
    }

    /** Generates a fingerprint for a specific device configuration. */
    @Suppress("SameParameterValue") // Parameter for future extensibility
    private fun generateForDevice(device: DeviceConfig, androidVersion: String): String {
        val buildIdPrefix = device.buildIdPrefixes.secureRandom()
        val buildId = generateBuildId(buildIdPrefix)
        val incremental = generateIncremental()

        return listOf(
                "${device.brand}/${device.product}/${device.device}:$androidVersion",
                buildId,
                "$incremental:user/release-keys",
            )
            .joinToString(separator = "/")
    }

    /**
     * Generates a realistic build ID.
     *
     * Format: PREFIX.YYMMDD.NUM (e.g., TQ3A.230901.001)
     */
    private fun generateBuildId(prefix: String): String {
        val year = nextInt(3) + 23 // Range 23-25
        val month = nextInt(12) + 1 // Range 1-12
        val day = nextInt(28) + 1 // Range 1-28
        val build = nextInt(9) + 1 // Range 1-9

        return "%s.%02d%02d%02d.%03d".format(prefix, year, month, day, build)
    }

    /** Generates a realistic incremental build number. */
    private fun generateIncremental(): String {
        return (nextInt(INCREMENTAL_BOUND) + MIN_INCREMENTAL).toString()
    }

    /**
     * Generates individual Build.* property values.
     *
     * @return A map of Build property names to values
     */
    fun generateBuildProperties(manufacturer: String? = null): Map<String, String> {
        val device =
            if (manufacturer != null) {
                DEVICE_DATABASE.filter {
                        it.brand.equals(manufacturer, ignoreCase = true) ||
                            it.manufacturer.equals(manufacturer, ignoreCase = true)
                    }
                    .let { if (it.isEmpty()) null else it.secureRandom() }
                    ?: DEVICE_DATABASE.secureRandom()
            } else {
                DEVICE_DATABASE.secureRandom()
            }

        val buildId = generateBuildId(device.buildIdPrefixes.secureRandom())
        val incremental = generateIncremental()
        val fingerprint =
            "${device.brand}/${device.product}/${device.device}:14/$buildId/$incremental:user/release-keys"

        return mapOf(
            "BRAND" to device.brand,
            "DEVICE" to device.device,
            "PRODUCT" to device.product,
            "MODEL" to device.model,
            "MANUFACTURER" to device.manufacturer,
            "FINGERPRINT" to fingerprint,
            "ID" to buildId,
            "DISPLAY" to "$buildId $incremental",
            "INCREMENTAL" to incremental,
            "TYPE" to "user",
            "TAGS" to "release-keys",
            "BOARD" to device.device.lowercase(),
            "HARDWARE" to device.device.lowercase(),
            "BOOTLOADER" to "unknown",
            "RADIO" to "unknown",
            "HOST" to "build.android.google.com",
        )
    }
}
