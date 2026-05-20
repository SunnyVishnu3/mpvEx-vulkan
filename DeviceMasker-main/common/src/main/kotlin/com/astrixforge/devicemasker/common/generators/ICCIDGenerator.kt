package com.astrixforge.devicemasker.common.generators

import com.astrixforge.devicemasker.common.models.Carrier
import com.astrixforge.devicemasker.common.util.Luhn
import java.security.SecureRandom
import java.util.Locale

/**
 * ICCID (Integrated Circuit Card Identifier) Generator.
 *
 * ICCID Format (19-20 digits with Luhn checksum):
 * - Major Industry Identifier: 2 digits (89 for telecom)
 * - Country Code: 1-3 digits
 * - Issuer Identifier: 1-4 digits
 * - Account Identification: 6-12 digits
 * - Check Digit: 1 digit (Luhn algorithm)
 *
 * Standard format: 89 (telecom) + CC + Issuer + Account + Luhn
 */
object ICCIDGenerator {
    private const val TELECOM_MII = "89"
    private const val TWO_DIGIT_BOUND = 100
    private const val ACCOUNT_LENGTH = 12
    private const val BASE_ICCID_LENGTH = 18
    private const val DECIMAL_RADIX = 10

    /** Secure random instance for cryptographic-quality randomness. */
    private val secureRandom = SecureRandom()

    /**
     * Generates a valid ICCID with Luhn checksum. Typical format: 89 + country (2) + issuer (2) +
     * account (12) + check (1) = 19 digits
     *
     * @return A valid ICCID string (19 digits)
     */
    fun generate(): String {
        // Major Industry Identifier for telecom
        val mii = TELECOM_MII

        // Country code (2 digits) - using common values
        val countryCode = String.format(Locale.US, "%02d", secureRandom.nextInt(TWO_DIGIT_BOUND))

        // Issuer identifier (2 digits)
        val issuer = String.format(Locale.US, "%02d", secureRandom.nextInt(TWO_DIGIT_BOUND))

        // Account identification (12 digits)
        val account = buildString {
            repeat(ACCOUNT_LENGTH) { append(secureRandom.nextInt(DECIMAL_RADIX)) }
        }

        // Base ICCID without check digit (18 digits)
        val base = mii + countryCode + issuer + account

        return Luhn.appendCheckDigit(base)
    }

    /**
     * Country code mapping for ICCID generation. Maps ISO 3166-1 alpha-2 country codes to ICCID
     * country codes.
     */
    private val COUNTRY_TO_ICCID_CODE =
        mapOf(
            "US" to "01", // United States
            "CA" to "01", // Canada (same as US for telecom)
            "GB" to "44", // United Kingdom
            "DE" to "49", // Germany
            "FR" to "33", // France
            "IN" to "91", // India
            "CN" to "86", // China
            "JP" to "81", // Japan
            "AU" to "61", // Australia
        )

    /**
     * Generates a valid ICCID for a specific carrier.
     *
     * The ICCID format is: 89 + country_code + issuer_code + serial + luhn_checksum
     *
     * This uses carrier-specific data for maximum realism:
     * - Country code from carrier's country ISO
     * - Issuer code from carrier's iccidIssuerCode field
     *
     * @param carrier The carrier to generate ICCID for
     * @return A valid ICCID string (19 digits) with matching country/issuer codes
     */
    fun generate(carrier: Carrier): String {
        // Major Industry Identifier for telecom (always 89)
        val mii = TELECOM_MII

        // Country code matching carrier's country (e.g., "91" for India)
        val countryCode =
            COUNTRY_TO_ICCID_CODE[carrier.countryIso]
                ?: carrier.countryCode.padStart(2, '0') // Fallback to phone country code

        // Carrier-specific issuer code (from carrier data, not random!)
        val issuer = carrier.iccidIssuerCode.padStart(2, '0')

        // Calculate remaining digits needed for 18 total (before check digit)
        // ICCID = MII(2) + CC(2-3) + Issuer(2-4) + Serial(variable) + Check(1) = 19-20 digits
        val accountLength = BASE_ICCID_LENGTH - mii.length - countryCode.length - issuer.length
        require(accountLength > 0) { "Invalid ICCID component lengths" }

        val account = buildString {
            repeat(accountLength) { append(secureRandom.nextInt(DECIMAL_RADIX)) }
        }

        // Base ICCID without check digit
        val base = mii + countryCode + issuer + account

        return Luhn.appendCheckDigit(base)
    }
}
