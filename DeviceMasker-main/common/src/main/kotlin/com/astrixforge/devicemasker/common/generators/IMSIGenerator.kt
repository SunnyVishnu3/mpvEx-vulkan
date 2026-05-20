package com.astrixforge.devicemasker.common.generators

import com.astrixforge.devicemasker.common.models.Carrier
import java.security.SecureRandom

/**
 * IMSI (International Mobile Subscriber Identity) Generator.
 *
 * IMSI Format (15 digits):
 * - MCC (Mobile Country Code): 3 digits
 * - MNC (Mobile Network Code): 2-3 digits
 * - MSIN (Mobile Subscription Identification Number): 9-10 digits
 *
 * This generator uses realistic MCC/MNC combinations from major carriers worldwide. Can auto-select
 * MCC/MNC based on system timezone/locale for increased realism.
 */
object IMSIGenerator {

    /** Secure random instance for cryptographic-quality randomness. */
    private val secureRandom = SecureRandom()

    /**
     * Valid MCC+MNC combinations from major carriers worldwide. Format: "MCCMNC" (5-6 digits total)
     */
    private val VALID_MCC_MNC =
        listOf(
            // United States
            "310260", // T-Mobile US
            "311480", // Verizon US
            "310410", // AT&T US
            "310120", // Sprint
            "310004", // Verizon
            "310028", // AT&T Mobility

            // Canada
            "302720", // Rogers
            "302220", // Telus
            "302610", // Bell

            // United Kingdom
            "234150", // Vodafone UK
            "234100", // EE (Everything Everywhere)
            "234200", // O2 UK
            "234303", // Three UK

            // Germany
            "262010", // T-Mobile Germany
            "262020", // Vodafone Germany
            "262030", // O2 Germany

            // France
            "208010", // SFR
            "208200", // Orange France
            "208030", // F-Bouygues

            // India
            "404450", // Airtel
            "404400", // Vodafone India
            "404900", // Reliance Jio

            // China
            "460000", // China Mobile
            "460010", // China Unicom
            "460030", // China Telecom

            // Japan
            "440100", // NTT DoCoMo
            "440200", // SoftBank
            "440300", // KDDI

            // Australia
            "505010", // Telstra
            "505020", // Optus
            "505030", // Vodafone AU
        )

    /** Maps timezone to regional MCC/MNC lists for location-aware generation. */
    private val REGION_MCC_MNC =
        mapOf(
            // North America
            "America/New_York" to
                listOf("310260", "311480", "310410", "310120", "310004", "310028"),
            "America/Chicago" to listOf("310260", "311480", "310410", "310120", "310004", "310028"),
            "America/Denver" to listOf("310260", "311480", "310410", "310120", "310004", "310028"),
            "America/Los_Angeles" to
                listOf("310260", "311480", "310410", "310120", "310004", "310028"),
            "America/Toronto" to listOf("302720", "302220", "302610"),

            // Europe
            "Europe/London" to listOf("234150", "234100", "234200", "234303"),
            "Europe/Berlin" to listOf("262010", "262020", "262030"),
            "Europe/Paris" to listOf("208010", "208200", "208030"),

            // Asia
            "Asia/Kolkata" to listOf("404450", "404400", "404900"),
            "Asia/Shanghai" to listOf("460000", "460010", "460030"),
            "Asia/Tokyo" to listOf("440100", "440200", "440300"),

            // Oceania
            "Australia/Sydney" to listOf("505010", "505020", "505030"),
            "Australia/Melbourne" to listOf("505010", "505020", "505030"),
        )

    /** Maps locale country codes to regional MCC/MNC lists. */
    private val LOCALE_MCC_MNC =
        mapOf(
            "US" to listOf("310260", "311480", "310410", "310120"),
            "CA" to listOf("302720", "302220", "302610"),
            "GB" to listOf("234150", "234100", "234200", "234303"),
            "DE" to listOf("262010", "262020", "262030"),
            "FR" to listOf("208010", "208200", "208030"),
            "IN" to listOf("404450", "404400", "404900"),
            "CN" to listOf("460000", "460010", "460030"),
            "JP" to listOf("440100", "440200", "440300"),
            "AU" to listOf("505010", "505020", "505030"),
        )

    /**
     * Generates a valid 15-digit IMSI number with realistic MCC/MNC.
     *
     * @param preferLocalRegion If true, attempts to select MCC/MNC based on system timezone/locale
     * @return A valid IMSI string (15 digits)
     */
    fun generate(preferLocalRegion: Boolean = false): String {
        // Select MCC+MNC (location-aware if requested, random otherwise)
        val mccMnc =
            if (preferLocalRegion) {
                selectLocationAwareMccMnc() ?: selectRandomMccMnc()
            } else {
                selectRandomMccMnc()
            }

        // Calculate MSIN length to complete 15 digits
        val msinLength = 15 - mccMnc.length

        // Generate MSIN (remaining digits)
        val msin = buildString { repeat(msinLength) { append(secureRandom.nextInt(10)) } }

        return mccMnc + msin
    }

    /**
     * Generates a valid 15-digit IMSI for a specific carrier.
     *
     * This ensures the IMSI matches the carrier's MCC/MNC, which is critical for correlation with
     * ICCID, carrier name, and phone number.
     *
     * @param carrier The carrier to generate IMSI for
     * @return 15-digit IMSI starting with carrier's MCC/MNC
     */
    fun generate(carrier: Carrier): String {
        val mccMnc = carrier.mccMnc
        val msinLength = 15 - mccMnc.length

        // Generate random MSIN (Mobile Subscription Identification Number)
        val msin = buildString { repeat(msinLength) { append(secureRandom.nextInt(10)) } }

        return mccMnc + msin
    }

    /** Selects a random MCC/MNC from all available carriers. */
    private fun selectRandomMccMnc(): String {
        return VALID_MCC_MNC[secureRandom.nextInt(VALID_MCC_MNC.size)]
    }

    /**
     * Attempts to select MCC/MNC based on system timezone or locale. Returns null if no match found
     * (caller should fall back to random).
     */
    private fun selectLocationAwareMccMnc(): String? {
        val timeZone = java.util.TimeZone.getDefault().id
        val locale = java.util.Locale.getDefault()
        val regionalList = REGION_MCC_MNC[timeZone] ?: LOCALE_MCC_MNC[locale.country]
        return regionalList?.let { it[secureRandom.nextInt(it.size)] }
    }
}
