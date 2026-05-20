package com.astrixforge.devicemasker.common.generators

import com.astrixforge.devicemasker.common.DeviceProfilePreset
import com.astrixforge.devicemasker.common.util.Luhn
import java.security.SecureRandom

/**
 * IMEI Generator with Luhn checksum validation and TAC-aware preset support.
 *
 * IMEI Structure (15 digits):
 * - TAC (Type Allocation Code): 8 digits — identifies device type globally. **Fraud detection SDKs
 *   cross-reference the TAC against `Build.MODEL`.** A mismatch immediately flags the device as
 *   spoofed. Use [generateForPreset] when a device profile is set.
 * - Serial Number (SNR): 6 digits — random per-device identifier
 * - Check Digit: 1 digit — Luhn checksum for ITU-T E.212 validation
 */
object IMEIGenerator {
    private const val TAC_LENGTH = 8
    private const val SERIAL_LENGTH = 6
    private const val PARTIAL_IMEI_LENGTH = 14

    /** Secure random instance for cryptographic-quality randomness. */
    private val secureRandom = SecureRandom()

    /**
     * Realistic TAC prefixes from major manufacturers. These are 8-digit prefixes that identify the
     * device type.
     *
     * Format: First 2 digits = Reporting Body Identifier Next 6 digits = Manufacturer/Model code
     */
    private val TAC_PREFIXES =
        listOf(
            // ═══════════════════════════════════════════════════════════
            // Samsung Galaxy (S23, S24, S25, A series, Z Fold/Flip)
            // ═══════════════════════════════════════════════════════════
            "35332509",
            "35391810",
            "35405607",
            "35421910",
            "35512025",
            "35640125",
            "35478525", // S24/S25 Ultra
            "35692024",
            "35703024", // Galaxy Z Fold/Flip 6
            "35284110",
            "35357615", // Galaxy A series

            // ═══════════════════════════════════════════════════════════
            // Apple iPhone (14, 15, 16 series)
            // ═══════════════════════════════════════════════════════════
            "35332410",
            "35391105",
            "35420108",
            "35925006",
            "35393024",
            "35484916",
            "35548516", // iPhone 15
            "35769024",
            "35821024",
            "35892025", // iPhone 16/16 Pro

            // ═══════════════════════════════════════════════════════════
            // Google Pixel (7, 8, 9 series)
            // ═══════════════════════════════════════════════════════════
            "35826010",
            "35331510",
            "35380110",
            "35888110",
            "35123410",
            "35923011", // Pixel 8/8 Pro
            "35945024",
            "35967024",
            "35989025", // Pixel 9/9 Pro

            // ═══════════════════════════════════════════════════════════
            // Xiaomi/Redmi (14, 15 series)
            // ═══════════════════════════════════════════════════════════
            "86783403",
            "86076203",
            "86893003",
            "86945024",
            "86967024", // Xiaomi 14/15 Ultra
            "86912024",
            "86934024", // Redmi Note 13/14

            // ═══════════════════════════════════════════════════════════
            // OnePlus (12, 13)
            // ═══════════════════════════════════════════════════════════
            "86831803",
            "86809403",
            "86468503",
            "86899603",
            "86912503", // OnePlus 12/13

            // ═══════════════════════════════════════════════════════════
            // Nothing Phone (2, 2a, 3)
            // ═══════════════════════════════════════════════════════════
            "86454403",
            "86389203",
            "86923024",
            "86945024", // Nothing Phone 2a/3

            // ═══════════════════════════════════════════════════════════
            // Other brands (Huawei, Oppo, Vivo, Realme, Motorola, Sony)
            // ═══════════════════════════════════════════════════════════
            // Huawei
            "86156403",
            "86180603",
            "86445403",
            // Oppo
            "86768803",
            "86429203",
            "86720203",
            // Vivo / iQOO
            "86566203",
            "86780203",
            "86608003",
            "86934024",
            "86956024", // iQOO 12/13
            // Realme
            "86725403",
            "86892103",
            "86934124", // Realme 12/13
            // Motorola
            "35154711",
            "35185108",
            "35186609",
            // Sony Xperia
            "35618715",
            "35846806",
            "35874108",

            // ═══════════════════════════════════════════════════════════
            // Generic TACs (fallback)
            // ═══════════════════════════════════════════════════════════
            "01234567",
            "45123478",
            "35000000",
        )

    /**
     * Generates a valid 15-digit IMEI number with Luhn checksum.
     *
     * @param manufacturer Optional manufacturer name to filter TAC prefixes
     * @return A valid IMEI string (15 digits)
     */
    fun generate(manufacturer: String? = null): String {
        val filteredTacs = filterTacPrefixes(manufacturer)

        // Fallback to all TACs if filtered list is empty
        val finalTacs = filteredTacs.ifEmpty { TAC_PREFIXES }

        // Select a random TAC prefix from the eligible ones
        val tac = finalTacs[secureRandom.nextInt(finalTacs.size)]

        // Generate 6 random digits for the serial number
        val serial = buildString { repeat(SERIAL_LENGTH) { append(secureRandom.nextInt(10)) } }

        // Combine TAC and serial (14 digits without check digit)
        val imeiWithoutCheck = tac + serial

        return appendValidatedCheckDigit(imeiWithoutCheck)
    }

    private fun filterTacPrefixes(manufacturer: String?): List<String> {
        val matcher =
            manufacturer?.lowercase()?.let(::manufacturerTacMatcher) ?: return TAC_PREFIXES
        return TAC_PREFIXES.filter(matcher)
    }

    private fun manufacturerTacMatcher(manufacturer: String): (String) -> Boolean =
        when {
            manufacturer.contains("samsung") ->
                startsWithAny("3533", "3539", "354", "355", "356", "357")
            manufacturer.contains("apple") || manufacturer.contains("iphone") -> ::isAppleTac
            manufacturer.contains("google") || manufacturer.contains("pixel") ->
                startsWithAny("3582", "35331", "3538", "3588", "3512", "3592", "359")
            manufacturer.contains("xiaomi") ||
                manufacturer.contains("redmi") ||
                manufacturer.contains("poco") -> startsWithAny("867834", "860762", "86")
            manufacturer.contains("oneplus") ->
                startsWithAny("868318", "868094", "864685", "868996", "869125")
            else -> { _ -> true }
        }

    private fun startsWithAny(vararg prefixes: String): (String) -> Boolean = { tac ->
        prefixes.any(tac::startsWith)
    }

    private fun isAppleTac(tac: String): Boolean =
        tac.startsWith("35") && !tac.startsWith("3533") && !tac.startsWith("3539")

    /**
     * Generates a IMEI correlated to the given [DeviceProfilePreset].
     *
     * Uses one of the preset's [DeviceProfilePreset.tacPrefixes] as the TAC, ensuring the generated
     * IMEI matches what would be expected for the claimed device model.
     *
     * This closes the TAC-mismatch detection gap documented in the spec.
     *
     * @param preset The active device profile preset
     * @return A valid 15-digit IMEI starting with one of the preset's TAC prefixes
     */
    fun generateForPreset(preset: DeviceProfilePreset): String {
        if (preset.tacPrefixes.isEmpty()) {
            return generate(preset.manufacturer)
        }
        val tac = preset.tacPrefixes[secureRandom.nextInt(preset.tacPrefixes.size)]
        return generateWithTac(tac)
    }

    /**
     * Generates a valid IMEI from an explicit 8-digit TAC prefix.
     *
     * @param tac 8-digit Type Allocation Code string
     * @return Valid 15-digit IMEI
     * @throws IllegalArgumentException if [tac] is not exactly 8 digits
     */
    fun generateWithTac(tac: String): String {
        require(tac.length == TAC_LENGTH && tac.all { it.isDigit() }) {
            "TAC must be exactly 8 decimal digits, got: '$tac'"
        }
        val serial = buildString { repeat(SERIAL_LENGTH) { append(secureRandom.nextInt(10)) } }
        val partial = tac + serial
        return appendValidatedCheckDigit(partial)
    }

    /**
     * Calculates the Luhn check digit for a 14-digit partial IMEI.
     *
     * @param partial The 14-digit IMEI without check digit
     * @return The single check digit (0-9)
     */
    private fun appendValidatedCheckDigit(partial: String): String {
        require(partial.length == PARTIAL_IMEI_LENGTH) { "Partial IMEI must be 14 digits" }
        require(partial.all { it.isDigit() }) { "Partial IMEI must contain only digits" }
        return Luhn.appendCheckDigit(partial)
    }
}
