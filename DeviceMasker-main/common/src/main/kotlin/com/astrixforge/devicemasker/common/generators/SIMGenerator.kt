package com.astrixforge.devicemasker.common.generators

import com.astrixforge.devicemasker.common.models.Carrier
import com.astrixforge.devicemasker.common.models.SIMConfig
import com.astrixforge.devicemasker.common.util.secureRandom

/**
 * Generates complete, correlated SIM card values.
 *
 * This ensures all SIM-related values (IMSI, ICCID, carrier, phone number, country ISO, operator
 * name, network operator) are consistent and match the same carrier.
 *
 * This is CRITICAL for avoiding detection - in the real world, all these values come from the same
 * physical SIM card and MUST correlate.
 */
@Suppress("unused") // Methods used for SIM spoofing
object SIMGenerator {

    /**
     * Generates a complete SIM config with all correlated values.
     *
     * All derived fields (simCountryIso, networkCountryIso, simOperatorName, networkOperator) are
     * automatically populated from the carrier data.
     *
     * @param carrier Optional specific carrier. If null, selects randomly.
     * @return SIMConfig with all values matching the carrier
     */
    fun generate(carrier: Carrier? = null): SIMConfig {
        // Select carrier (random if not specified)
        val selectedCarrier = carrier ?: Carrier.nextSecureRandom()

        // Generate all values using the SAME carrier
        // SIMConfig.create() automatically populates derived fields
        return SIMConfig.create(
            carrier = selectedCarrier,
            imsi = IMSIGenerator.generate(selectedCarrier),
            iccid = ICCIDGenerator.generate(selectedCarrier),
            phoneNumber = PhoneNumberGenerator.generate(selectedCarrier),
        )
    }

    /**
     * Generates a SIM config for a specific country.
     *
     * @param countryIso ISO 3166-1 alpha-2 country code (e.g., "US", "IN")
     * @return SIMConfig from a carrier in that country
     */
    fun generateForCountry(countryIso: String): SIMConfig {
        val carriers = Carrier.getByCountry(countryIso)
        require(carriers.isNotEmpty()) { "No carriers found for country: $countryIso" }

        return generate(carriers.secureRandom())
    }

    /**
     * Generates a SIM config for India. Convenience method for the most common use case.
     *
     * @return SIMConfig from a random India carrier (Airtel, Jio, Vi, BSNL)
     */
    fun generateForIndia(): SIMConfig {
        return generate(Carrier.nextSecureRandomIndia())
    }

    /**
     * Generates a SIM config for a specific carrier by name.
     *
     * @param carrierName Name of carrier (e.g., "Airtel", "Jio")
     * @return SIMConfig for the carrier, or null if not found
     */
    fun generateForCarrier(carrierName: String): SIMConfig? {
        val carriers = Carrier.getByName(carrierName)
        if (carriers.isEmpty()) return null
        return generate(carriers.secureRandom())
    }
}
