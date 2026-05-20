package com.astrixforge.devicemasker.common.generators

import java.security.SecureRandom

/**
 * Serial Number Generator for device spoofing.
 *
 * Device serial numbers vary by manufacturer and follow specific patterns:
 * - Samsung: R + 2 digits + year letter + 8 digits (e.g., "R58M12345678")
 * - Google Pixel: 16 characters hex-like (e.g., "FA6AB0301534ABCD")
 * - Xiaomi: Format varies, typically 12-16 alphanumeric
 * - Generic: 8-16 alphanumeric characters
 *
 * All patterns based on real device serial number formats for maximum realism.
 */
object SerialGenerator {

    /** Secure random instance for cryptographic-quality randomness. */
    private val secureRandom = SecureRandom()

    /** Characters used in generic serial generation (excludes easily confused I, O, Q). */
    private const val ALPHANUMERIC_CHARS = "0123456789ABCDEFGHJKLMNPRSTUVWXYZ"

    /** Hex characters for Pixel-style serials. */
    private const val HEX_CHARS = "0123456789ABCDEF"
    private const val PIXEL_SERIAL_LENGTH = 16

    /**
     * Generates a realistic device serial number using a random manufacturer pattern.
     *
     * @return A realistic serial number string
     */
    fun generate(): String {
        val patterns =
            listOf(
                ::generateSamsungSerial,
                ::generatePixelSerial,
                ::generateXiaomiSerial,
                ::generateGenericSerial,
            )
        return patterns[secureRandom.nextInt(patterns.size)]()
    }

    /**
     * Generates a realistic serial number for a specific manufacturer.
     *
     * @param manufacturer The device manufacturer (e.g., "Samsung", "Google", "Xiaomi")
     * @return A serial number matching the manufacturer's pattern
     */
    fun generate(manufacturer: String): String {
        return when (manufacturer.lowercase()) {
            "samsung" -> generateSamsungSerial()
            "google" -> generatePixelSerial()
            "xiaomi",
            "redmi",
            "poco",
            "mi" -> generateXiaomiSerial()
            "oneplus" -> generateGenericSerial() // Can add OnePlus pattern later
            "oppo",
            "vivo",
            "realme" -> generateGenericSerial()
            else -> generateGenericSerial()
        }
    }

    /**
     * Generates a Samsung-style serial number. Format: R + 2 digits + year letter + 8 digits
     * Example: "R58M12345678"
     */
    private fun generateSamsungSerial(): String {
        val prefix = "R${secureRandom.nextInt(100).toString().padStart(2, '0')}"

        // Year indicator (A-Z excluding I, O, Q for clarity)
        val yearLetters = "ABCDEFGHJKLMNPRSTUVWXYZ"
        val yearIndicator = yearLetters[secureRandom.nextInt(yearLetters.length)]

        // 8-digit serial portion
        val serial = buildString { repeat(8) { append(secureRandom.nextInt(10)) } }

        return prefix + yearIndicator + serial
    }

    /**
     * Generates a Google Pixel-style serial number. Format: 16 hex-like characters (uppercase)
     * Example: "FA6AB0301534ABCD"
     */
    private fun generatePixelSerial(): String {
        return buildString {
            repeat(PIXEL_SERIAL_LENGTH) {
                append(HEX_CHARS[secureRandom.nextInt(HEX_CHARS.length)])
            }
        }
    }

    /**
     * Generates a Xiaomi-style serial number. Format: Typically 12-16 mixed alphanumeric Example:
     * "ABC123DEF456GHIJ"
     */
    private fun generateXiaomiSerial(): String {
        val length = 12 + secureRandom.nextInt(5) // 12-16 characters
        return buildString {
            repeat(length) {
                append(ALPHANUMERIC_CHARS[secureRandom.nextInt(ALPHANUMERIC_CHARS.length)])
            }
        }
    }

    /**
     * Generates a generic serial number for other manufacturers. Format: 10-14 alphanumeric
     * characters Example: "ABC123XYZ789"
     */
    private fun generateGenericSerial(): String {
        val length = 10 + secureRandom.nextInt(5) // 10-14 characters
        return buildString {
            repeat(length) {
                append(ALPHANUMERIC_CHARS[secureRandom.nextInt(ALPHANUMERIC_CHARS.length)])
            }
        }
    }
}
