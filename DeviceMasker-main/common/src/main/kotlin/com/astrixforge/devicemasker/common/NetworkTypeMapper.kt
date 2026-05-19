package com.astrixforge.devicemasker.common

/**
 * Maps carrier MCC/MNC codes to expected network type constants.
 *
 * Spoofing the carrier name/MCC/MNC without also spoofing the network type is detectable: fraud
 * SDKs cross-reference the carrier against the expected technology. For example, Jio India (404-40)
 * on a 2025 device should return `NETWORK_TYPE_NR` (5G), not HSPA+ (15).
 *
 * The mapping uses the first 3 digits of MCC/MNC as a broad carrier prefix rather than exact PLMN
 * matches, providing good coverage without an exhaustive database.
 *
 * ## Network type constants (from `TelephonyManager`):
 * ```
 * NETWORK_TYPE_LTE = 13
 * NETWORK_TYPE_NR  = 20
 * ```
 *
 * @see [spec: network-type-mapping/spec.md]
 */
object NetworkTypeMapper {

    // TelephonyManager.NETWORK_TYPE_LTE = 13 (avoids depending on android framework in :common)
    const val NETWORK_TYPE_LTE: Int = 13

    // TelephonyManager.NETWORK_TYPE_NR = 20
    const val NETWORK_TYPE_NR: Int = 20

    /**
     * Maps carrying MCC/MNC to the expected network type constant.
     *
     * Supports both compact format `"40440"` (5-digit) and separated format `"404"+"40"`.
     *
     * Returns [NETWORK_TYPE_NR] for carriers known to have nationwide 5G coverage in 2025+. Returns
     * [NETWORK_TYPE_LTE] as a safe default for all others.
     *
     * @param mccMnc Combined MCC+MNC string (5 or 6 digits, no separator), e.g. `"31026"`,
     *   `"40440"`
     * @return `NETWORK_TYPE_NR` (20) or `NETWORK_TYPE_LTE` (13)
     */
    fun getForMccMnc(mccMnc: String): Int {
        if (mccMnc.length < PLMN_PREFIX_LENGTH) return NETWORK_TYPE_LTE

        // Use first 5 digits for matching (covers both 5- and 6-digit PLMNs)
        val plmn = mccMnc.take(PLMN_PREFIX_LENGTH)
        val mcc = plmn.take(3)

        return if (mcc in NR_MCC_CODES || NR_PLMN_PREFIXES.any(plmn::startsWith)) {
            NETWORK_TYPE_NR
        } else {
            NETWORK_TYPE_LTE
        }
    }

    /** @return The network type string name — for logging / diagnostics. */
    fun getNetworkTypeName(networkType: Int): String =
        when (networkType) {
            NETWORK_TYPE_NR -> "NR (5G)"
            NETWORK_TYPE_LTE -> "LTE (4G)"
            else -> "UNKNOWN ($networkType)"
        }

    // ── US MCC ranges ─────────────────────────────────────────────────────────────────────────
    // MCC 310..316 are all allocated to the United States
    private const val PLMN_PREFIX_LENGTH = 5
    private val NR_MCC_CODES by lazy {
        (310..316).map { it.toString() }.toSet() + setOf("450", "440", "466", "454")
    }
    private val NR_PLMN_PREFIXES =
        setOf(
            "234",
            "404",
            "405",
            "460",
            "262",
            "208",
            "222",
            "214",
            "204",
            "424",
            "420",
            "525",
            "505",
        )
}
