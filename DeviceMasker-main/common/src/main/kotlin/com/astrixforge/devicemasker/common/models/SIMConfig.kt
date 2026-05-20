package com.astrixforge.devicemasker.common.models

import kotlinx.serialization.Serializable

/**
 * Correlated SIM card values.
 *
 * All values represent data from the SAME physical SIM card and must be consistent. In the real
 * world:
 * - IMSI contains the carrier's MCC/MNC
 * - ICCID contains a country code matching the carrier
 * - Phone number uses the country's calling code
 * - Carrier name matches the MCC/MNC in IMSI
 * - Country ISO codes all match
 *
 * Detection systems can easily spot mismatches between these values.
 */
@Suppress("unused") // Properties used for serialization and cross-module access
@Serializable
data class SIMConfig(
    val carrier: Carrier,
    val imsi: String, // 15 digits, starts with carrier.mccMnc
    val iccid: String, // 19-20 digits, country code matches carrier
    val phoneNumber: String, // +{countryCode}{number}

    // NEW: Additional fields for comprehensive spoofing
    val simCountryIso: String, // Lowercase country code (e.g., "in")
    val networkCountryIso: String, // Usually same as simCountryIso (non-roaming)
    val simOperatorName: String, // Carrier display name for SIM
    val networkOperator: String, // MCC+MNC as string (e.g., "40410")
) {
    /** Convenience property for carrier MCC/MNC. */
    val mccMnc: String
        get() = carrier.mccMnc

    /** Convenience property for carrier name. */
    val carrierName: String
        get() = carrier.name

    /** Convenience property for MCC. */
    val mcc: String
        get() = carrier.mcc

    /** Convenience property for MNC. */
    val mnc: String
        get() = carrier.mnc

    init {
        // Validate IMSI starts with correct MCC/MNC
        require(imsi.startsWith(carrier.mccMnc)) {
            "IMSI must start with carrier MCC/MNC ${carrier.mccMnc}, got: $imsi"
        }

        // Validate phone number matches country code
        require(phoneNumber.startsWith("+${carrier.countryCode}")) {
            "Phone number must start with +${carrier.countryCode}, got: $phoneNumber"
        }

        // Validate country ISO matches carrier
        require(simCountryIso.equals(carrier.countryIso, ignoreCase = true)) {
            "SIM country ISO must match carrier country ${carrier.countryIso}, got: $simCountryIso"
        }

        // Validate network operator matches carrier
        require(networkOperator == carrier.mccMnc) {
            "Network operator must match carrier MCC/MNC ${carrier.mccMnc}, got: $networkOperator"
        }
    }

    companion object {
        /**
         * Creates a SIMConfig with all derived fields populated automatically. This is the
         * recommended way to create a SIMConfig to ensure consistency.
         */
        fun create(carrier: Carrier, imsi: String, iccid: String, phoneNumber: String): SIMConfig =
            SIMConfig(
                carrier = carrier,
                imsi = imsi,
                iccid = iccid,
                phoneNumber = phoneNumber,
                simCountryIso = carrier.countryIsoLower,
                networkCountryIso = carrier.countryIsoLower, // Same as SIM (non-roaming)
                simOperatorName = carrier.name,
                networkOperator = carrier.mccMnc,
            )
    }
}
