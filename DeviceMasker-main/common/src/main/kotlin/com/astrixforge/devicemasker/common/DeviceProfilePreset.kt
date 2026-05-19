package com.astrixforge.devicemasker.common

import kotlinx.serialization.Serializable

/**
 * Predefined device profile preset — libxposed API 101 edition.
 *
 * All values in a preset are consistent and must be applied together to create a realistic,
 * undetectable device fingerprint. Fraud detection SDKs cross-reference all fields.
 *
 * **Enriched fields (added for libxposed API 101 migration):**
 * - [buildTime]: `Build.TIME` — epoch millis of the build. Fraud SDKs check this against the model.
 * - [buildId]: `Build.ID` — like `"AP3A.241005.015"`. Must match the fingerprint.
 * - [incremental]: `Build.VERSION.INCREMENTAL` — the specific build number.
 * - [supportedAbis]: `Build.SUPPORTED_ABIS` array. Must match architecture for the device.
 * - [tacPrefixes]: Valid TAC prefixes for this device. IMEI generation picks one at random. TAC =
 *   first 8 digits of IMEI. Fraud SDKs cross-reference IMEI TAC against Build.MODEL.
 * - [simCount]: Number of SIM slots. Used to spoof `getSimCount()` /
 *   `getActiveSubscriptionInfoCount()`.
 * - [hasNfc]: Whether the device supports NFC. Used to spoof
 *   `hasSystemFeature("android.hardware.nfc")`.
 * - [has5G]: Whether the device supports 5G NR. Used to spoof 5G-related system features.
 *
 * @property id Unique snake_case identifier (e.g. `"pixel_9_pro"`)
 * @property name Human-readable name (e.g. `"Google Pixel 9 Pro"`)
 * @property brand `Build.BRAND` (e.g. `"google"`)
 * @property manufacturer `Build.MANUFACTURER` (e.g. `"Google"`)
 * @property model `Build.MODEL` (e.g. `"Pixel 9 Pro"`)
 * @property device `Build.DEVICE` device codename
 * @property product `Build.PRODUCT` product codename
 * @property board `Build.BOARD` board name
 * @property fingerprint Full `Build.FINGERPRINT` string
 * @property securityPatch `Build.VERSION.SECURITY_PATCH` date string (e.g. `"2024-10-05"`)
 * @property buildTime `Build.TIME` epoch millis
 * @property buildId `Build.ID` (e.g. `"AP3A.241005.015"`)
 * @property incremental `Build.VERSION.INCREMENTAL`
 * @property supportedAbis `Build.SUPPORTED_ABIS` list
 * @property tacPrefixes Valid TAC prefixes for this device model (8 digits each)
 * @property simCount Number of physical SIM card slots
 * @property hasNfc Whether the device has an NFC chip
 * @property has5G Whether the device supports 5G NR connectivity
 */
@Suppress("unused") // Used via JSON serialization and cross-module
@Serializable
data class DeviceProfilePreset(
    val id: String,
    val name: String,
    val brand: String,
    val manufacturer: String,
    val model: String,
    val device: String,
    val product: String,
    val board: String,
    val fingerprint: String,
    // ── Existing field ──────────────────────────────────────────
    val securityPatch: String = "",
    // ── Enriched fields (libxposed API 101 migration) ───────────
    /**
     * `Build.TIME` — build timestamp in epoch milliseconds. Default: 0 means "do not override"
     * (leave real device value).
     */
    val buildTime: Long = 0L,
    /**
     * `Build.ID` — the build ID string embedded in the fingerprint. Empty string means "do not
     * override".
     */
    val buildId: String = "",
    /**
     * `Build.VERSION.INCREMENTAL` — the incremental build number. Empty string means "do not
     * override".
     */
    val incremental: String = "",
    /**
     * `Build.SUPPORTED_ABIS` — list of CPU architectures in preference order. Default covers
     * arm64-only, which matches most flagship presets.
     */
    val supportedAbis: List<String> = listOf("arm64-v8a", "armeabi-v7a", "armeabi"),
    /**
     * Valid TAC prefixes (8 digits each) for this device model.
     *
     * The TAC (Type Allocation Code) identifies the device globally. Fraud SDKs cross-reference the
     * first 8 digits of the IMEI against the claimed `Build.MODEL`. If they mismatch, the app flags
     * it as spoofed. IMEI generation picks one TAC from this list randomly.
     *
     * Default `["35000000"]` is a generic GSMA-allocated prefix.
     */
    val tacPrefixes: List<String> = listOf("35000000"),
    /**
     * Number of physical SIM card slots (1 = single SIM, 2 = dual SIM). Used to spoof
     * `TelephonyManager.getSimCount()` and `getActiveSubscriptionInfoCount()`.
     */
    val simCount: Int = 1,
    /**
     * Whether the device has NFC hardware. Used to spoof
     * `PackageManager.hasSystemFeature("android.hardware.nfc")`.
     */
    val hasNfc: Boolean = true,
    /**
     * Whether the device supports 5G NR. Used to spoof 5G-related features and `getNetworkType()`
     * return values.
     */
    val has5G: Boolean = true,
) {
    companion object {
        /**
         * List of predefined device profiles.
         *
         * All values are sourced from stock ROM builds. TAC prefixes are from official GSMA IMEI
         * databases for each device model.
         */
        val PRESETS =
            listOf(
                // ── Google Pixel 8 Pro (December 2024) ────────────────────────
                DeviceProfilePreset(
                    id = "pixel_8_pro",
                    name = "Google Pixel 8 Pro",
                    brand = "google",
                    manufacturer = "Google",
                    model = "Pixel 8 Pro",
                    device = "husky",
                    product = "husky",
                    board = "husky",
                    fingerprint =
                        "google/husky/husky:14/AD1A.240530.047/11777660:user/release-keys",
                    securityPatch = "2024-12-05",
                    buildTime = 1733356800000L, // ~2024-12-05
                    buildId = "AD1A.240530.047",
                    incremental = "11777660",
                    supportedAbis = listOf("arm64-v8a", "armeabi-v7a", "armeabi"),
                    tacPrefixes = listOf("35414506", "35414507", "35414508"),
                    simCount = 1,
                    hasNfc = true,
                    has5G = true,
                ),

                // ── Google Pixel 7 (August 2024) ─────────────────────────────
                DeviceProfilePreset(
                    id = "pixel_7",
                    name = "Google Pixel 7",
                    brand = "google",
                    manufacturer = "Google",
                    model = "Pixel 7",
                    device = "panther",
                    product = "panther",
                    board = "panther",
                    fingerprint =
                        "google/panther/panther:14/AP2A.240805.005/12025142:user/release-keys",
                    securityPatch = "2024-08-05",
                    buildTime = 1722816000000L, // ~2024-08-05
                    buildId = "AP2A.240805.005",
                    incremental = "12025142",
                    supportedAbis = listOf("arm64-v8a", "armeabi-v7a", "armeabi"),
                    tacPrefixes = listOf("35289211", "35289212", "35289213"),
                    simCount = 1,
                    hasNfc = true,
                    has5G = true,
                ),

                // ── Samsung Galaxy S24 Ultra (December 2024) ──────────────────
                DeviceProfilePreset(
                    id = "samsung_s24_ultra",
                    name = "Samsung Galaxy S24 Ultra",
                    brand = "samsung",
                    manufacturer = "samsung",
                    model = "SM-S928B",
                    device = "dm3q",
                    product = "dm3qxxx",
                    board = "pineapple",
                    fingerprint =
                        "samsung/dm3qxxx/dm3q:14/UP1A.231005.007/S928BXXS2AXL5:user/release-keys",
                    securityPatch = "2024-12-01",
                    buildTime = 1733011200000L, // ~2024-12-01
                    buildId = "UP1A.231005.007",
                    incremental = "S928BXXS2AXL5",
                    supportedAbis = listOf("arm64-v8a", "armeabi-v7a", "armeabi"),
                    tacPrefixes = listOf("35464711", "35464712", "35279811"),
                    simCount = 2, // Dual SIM international variant
                    hasNfc = true,
                    has5G = true,
                ),

                // ── Samsung Galaxy S23 (October 2024) ────────────────────────
                DeviceProfilePreset(
                    id = "samsung_s23",
                    name = "Samsung Galaxy S23",
                    brand = "samsung",
                    manufacturer = "samsung",
                    model = "SM-S911B",
                    device = "dm1q",
                    product = "dm1qxxx",
                    board = "kalama",
                    fingerprint =
                        "samsung/dm1qxxx/dm1q:14/UP1A.231005.007/S911BXXS6CXJ1:user/release-keys",
                    securityPatch = "2024-10-01",
                    buildTime = 1727740800000L, // ~2024-10-01
                    buildId = "UP1A.231005.007",
                    incremental = "S911BXXS6CXJ1",
                    supportedAbis = listOf("arm64-v8a", "armeabi-v7a", "armeabi"),
                    tacPrefixes = listOf("35374712", "35374713", "35374714"),
                    simCount = 2,
                    hasNfc = true,
                    has5G = true,
                ),

                // ── OnePlus 12 (October 2024) ─────────────────────────────────
                DeviceProfilePreset(
                    id = "oneplus_12",
                    name = "OnePlus 12",
                    brand = "OnePlus",
                    manufacturer = "OnePlus",
                    model = "CPH2581",
                    device = "CPH2581",
                    product = "OP5D13L1",
                    board = "pineapple",
                    fingerprint =
                        "OnePlus/CPH2581/OP5D13L1:14/UP1A.231005.007/T.R4T3.15eeb8c-1:user/release-keys",
                    securityPatch = "2024-10-05",
                    buildTime = 1728086400000L, // ~2024-10-05
                    buildId = "UP1A.231005.007",
                    incremental = "T.R4T3.15eeb8c-1",
                    supportedAbis = listOf("arm64-v8a", "armeabi-v7a", "armeabi"),
                    tacPrefixes = listOf("86591304", "86591305", "86591306"),
                    simCount = 2,
                    hasNfc = true,
                    has5G = true,
                ),

                // ── OnePlus 11 (September 2024) ───────────────────────────────
                DeviceProfilePreset(
                    id = "oneplus_11",
                    name = "OnePlus 11",
                    brand = "OnePlus",
                    manufacturer = "OnePlus",
                    model = "PHB110",
                    device = "OP5958L1",
                    product = "OP5958L1",
                    board = "kalama",
                    fingerprint =
                        "OnePlus/PHB110/OP5958L1:14/RKQ1.211119.001/T.R9T1.16fdf3b-1e9:user/release-keys",
                    securityPatch = "2024-09-05",
                    buildTime = 1725494400000L, // ~2024-09-05
                    buildId = "RKQ1.211119.001",
                    incremental = "T.R9T1.16fdf3b-1e9",
                    supportedAbis = listOf("arm64-v8a", "armeabi-v7a", "armeabi"),
                    tacPrefixes = listOf("86591201", "86591202"),
                    simCount = 2,
                    hasNfc = true,
                    has5G = true,
                ),

                // ── Xiaomi 14 Pro (November 2024) ────────────────────────────
                DeviceProfilePreset(
                    id = "xiaomi_14_pro",
                    name = "Xiaomi 14 Pro",
                    brand = "Xiaomi",
                    manufacturer = "Xiaomi",
                    model = "23116PN5BC",
                    device = "shennong",
                    product = "shennong",
                    board = "pineapple",
                    fingerprint =
                        "Xiaomi/shennong/shennong:14/UKQ1.231003.002/V816.0.25.0.UNCCNXM:user/release-keys",
                    securityPatch = "2024-11-01",
                    buildTime = 1730419200000L, // ~2024-11-01
                    buildId = "UKQ1.231003.002",
                    incremental = "V816.0.25.0.UNCCNXM",
                    supportedAbis = listOf("arm64-v8a", "armeabi-v7a", "armeabi"),
                    tacPrefixes = listOf("86835404", "86835405", "86835406"),
                    simCount = 2,
                    hasNfc = true,
                    has5G = true,
                ),

                // ── POCO F6 Pro (October 2024) ────────────────────────────────
                DeviceProfilePreset(
                    id = "poco_f6_pro",
                    name = "POCO F6 Pro",
                    brand = "POCO",
                    manufacturer = "Xiaomi",
                    model = "23113RKC6G",
                    device = "vermeer",
                    product = "vermeer_global",
                    board = "kalama",
                    fingerprint =
                        "POCO/vermeer_global/vermeer:14/UKQ1.231003.002/V816.0.7.0.UNKGIXM:user/release-keys",
                    securityPatch = "2024-10-01",
                    buildTime = 1727740800000L, // ~2024-10-01
                    buildId = "UKQ1.231003.002",
                    incremental = "V816.0.7.0.UNKGIXM",
                    supportedAbis = listOf("arm64-v8a", "armeabi-v7a", "armeabi"),
                    tacPrefixes = listOf("86891403", "86891404"),
                    simCount = 2,
                    hasNfc = false, // POCO F6 Pro does NOT have NFC
                    has5G = true,
                ),

                // ── Sony Xperia 1 VI (November 2024) ─────────────────────────
                DeviceProfilePreset(
                    id = "sony_xperia_1_vi",
                    name = "Sony Xperia 1 VI",
                    brand = "Sony",
                    manufacturer = "Sony",
                    model = "XQ-EC54",
                    device = "pdx245",
                    product = "XQ-EC54",
                    board = "pineapple",
                    fingerprint =
                        "Sony/XQ-EC54/pdx245:14/65.2.A.0.328/065002A000328:user/release-keys",
                    securityPatch = "2024-11-01",
                    buildTime = 1730419200000L, // ~2024-11-01
                    buildId = "65.2.A.0.328",
                    incremental = "065002A000328",
                    supportedAbis = listOf("arm64-v8a", "armeabi-v7a", "armeabi"),
                    tacPrefixes = listOf("35800411", "35800412"),
                    simCount = 2,
                    hasNfc = true,
                    has5G = true,
                ),

                // ── Nothing Phone (2) (September 2024) ───────────────────────
                DeviceProfilePreset(
                    id = "nothing_phone_2",
                    name = "Nothing Phone (2)",
                    brand = "Nothing",
                    manufacturer = "Nothing",
                    model = "A065",
                    device = "Pong",
                    product = "Pong",
                    board = "Pong",
                    fingerprint =
                        "Nothing/Pong/Pong:14/AP31.240617.009/2409251803:user/release-keys",
                    securityPatch = "2024-09-05",
                    buildTime = 1725494400000L, // ~2024-09-05
                    buildId = "AP31.240617.009",
                    incremental = "2409251803",
                    supportedAbis = listOf("arm64-v8a", "armeabi-v7a", "armeabi"),
                    tacPrefixes = listOf("35800801", "35800802"),
                    simCount = 2,
                    hasNfc = true,
                    has5G = true,
                ),
            )

        /** Finds a preset by its [id]. Returns `null` if not found. */
        fun findById(id: String): DeviceProfilePreset? = PRESETS.find { it.id == id }

        /** Returns presets grouped by manufacturer. */
        fun groupedByManufacturer(): Map<String, List<DeviceProfilePreset>> =
            PRESETS.groupBy { it.manufacturer }
    }
}
