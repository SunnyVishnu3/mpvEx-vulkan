package com.astrixforge.devicemasker.common

import com.astrixforge.devicemasker.common.util.Luhn

/** Utility functions shared between the app UI and xposed module. */
@Suppress("unused") // Utility functions for cross-module use
object Utils {
    private val MAC_REGEX = Regex("^([0-9A-Fa-f]{2}:){5}[0-9A-Fa-f]{2}$")
    private val UUID_REGEX =
        Regex("^[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}$")
    private const val DEFAULT_MASK = "••••••••"
    private const val MAX_MASK_LENGTH = 8
    private const val IMEI_LENGTH = 15

    /** Log levels for service logging. */
    object LogLevel {
        const val VERBOSE = 0
        const val DEBUG = 1
        const val INFO = 2
        const val WARN = 3
        const val ERROR = 4
    }

    /**
     * Masks a value for display (e.g., in UI). Shows first and last 2 characters, rest as
     * asterisks.
     *
     * @param value The value to mask
     * @param visibleChars Number of characters to show at start and end
     * @return Masked string or original if too short
     */
    fun maskValue(value: String?, visibleChars: Int = 2): String {
        return if (value.isNullOrBlank()) {
            DEFAULT_MASK
        } else {
            val visibleEdgeLength = visibleChars * 2
            if (value.length <= visibleEdgeLength) {
                value
            } else {
                val start = value.take(visibleChars)
                val end = value.takeLast(visibleChars)
                val maskLength = value.length - visibleEdgeLength
                val mask = "•".repeat(maskLength.coerceAtMost(MAX_MASK_LENGTH))
                "$start$mask$end"
            }
        }
    }

    /**
     * Validates an IMEI using Luhn checksum.
     *
     * @param imei The IMEI to validate (15 digits)
     * @return True if valid
     */
    fun isValidImei(imei: String): Boolean {
        val hasValidFormat = imei.length == IMEI_LENGTH && imei.all { it.isDigit() }
        return hasValidFormat && Luhn.isValid(imei)
    }

    /**
     * Validates a MAC address format.
     *
     * @param mac The MAC address (XX:XX:XX:XX:XX:XX)
     * @return True if valid format
     */
    fun isValidMac(mac: String): Boolean {
        return MAC_REGEX.matches(mac)
    }

    /**
     * Validates a UUID format.
     *
     * @param uuid The UUID string
     * @return True if valid format
     */
    fun isValidUuid(uuid: String): Boolean {
        return UUID_REGEX.matches(uuid)
    }

    /**
     * Formats a timestamp as a human-readable string.
     *
     * @param epochMillis Timestamp in epoch milliseconds
     * @return Formatted date/time string
     */
    fun formatTimestamp(epochMillis: Long): String {
        val date = java.util.Date(epochMillis)
        val format = java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss", java.util.Locale.US)
        return format.format(date)
    }
}
