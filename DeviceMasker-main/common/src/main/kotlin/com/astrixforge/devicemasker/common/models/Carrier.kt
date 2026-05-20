package com.astrixforge.devicemasker.common.models

import com.astrixforge.devicemasker.common.util.secureRandom
import kotlinx.serialization.Serializable

/**
 * Mobile carrier with MCC/MNC information.
 *
 * Used to ensure IMSI, ICCID, carrier name, and phone number all match.
 *
 * @property name Display name of the carrier
 * @property mccMnc Combined MCC + MNC code (e.g., "310260")
 * @property countryCode Phone country code without + (e.g., "91" for India)
 * @property countryIso ISO 3166-1 alpha-2 country code (e.g., "IN")
 * @property iccidIssuerCode Carrier-specific ICCID issuer code (2-4 digits)
 * @property region Optional region name for country-specific carriers
 */
@Suppress("unused") // Used for SIM spoofing correlation
@Serializable
data class Carrier(
    val name: String,
    val mccMnc: String,
    val countryCode: String,
    val countryIso: String,
    val iccidIssuerCode: String = "00", // Default issuer code
    val region: String? = null, // Optional region (e.g., "Delhi", "Karnataka")
) {
    /** Returns MCC (first 3 digits of mccMnc). */
    val mcc: String
        get() = mccMnc.take(3)

    /** Returns MNC (digits after MCC). */
    val mnc: String
        get() = mccMnc.drop(3)

    /** Returns display name with region if available. */
    val displayName: String
        get() = if (region != null) "$name ($region)" else name

    /** Returns country ISO in lowercase (for SIM_COUNTRY_ISO). */
    val countryIsoLower: String
        get() = countryIso.lowercase()

    companion object {
        /**
         * Database of real carriers worldwide.
         *
         * These are real MCC/MNC combinations from actual mobile networks. Source: ITU-T E.212
         * registry, mcc-mnc.com
         */
        val ALL_CARRIERS =
            listOf(
                // ═══════════════════════════════════════════════════════════
                // INDIA - Comprehensive coverage with regional MNCs
                // ═══════════════════════════════════════════════════════════

                // Airtel - Multiple regional MNCs
                Carrier("Airtel", "40410", "91", "IN", "10", "Delhi NCR"),
                Carrier("Airtel", "40445", "91", "IN", "45", "Karnataka"),
                Carrier("Airtel", "40449", "91", "IN", "49", "Andhra Pradesh"),
                Carrier("Airtel", "40440", "91", "IN", "40", "Chennai"),
                Carrier("Airtel", "40431", "91", "IN", "31", "Kolkata"),
                Carrier("Airtel", "40402", "91", "IN", "02", "Punjab"),
                Carrier("Airtel", "40403", "91", "IN", "03", "Himachal Pradesh"),
                Carrier("Airtel", "40416", "91", "IN", "16", "North East"),
                Carrier("Airtel", "40493", "91", "IN", "93", "Maharashtra"),
                Carrier("Airtel", "40490", "91", "IN", "90", "Mumbai"),
                Carrier("Airtel", "40492", "91", "IN", "92", "Gujarat"),
                Carrier("Airtel", "40494", "91", "IN", "94", "Tamil Nadu"),
                Carrier("Airtel", "40495", "91", "IN", "95", "Kerala"),
                Carrier("Airtel", "40496", "91", "IN", "96", "Haryana"),
                Carrier("Airtel", "40497", "91", "IN", "97", "UP West"),
                Carrier("Airtel", "40498", "91", "IN", "98", "UP East"),
                Carrier("Airtel", "40552", "91", "IN", "52", "Bihar & Jharkhand"),

                // Jio - Nationwide with different MNCs per circle
                Carrier("Jio", "405857", "91", "IN", "857", "Andhra Pradesh"),
                Carrier("Jio", "405858", "91", "IN", "858", "Assam"),
                Carrier("Jio", "405859", "91", "IN", "859", "Bihar & Jharkhand"),
                Carrier("Jio", "405860", "91", "IN", "860", "Chennai"),
                Carrier("Jio", "405861", "91", "IN", "861", "Delhi NCR"),
                Carrier("Jio", "405862", "91", "IN", "862", "Gujarat"),
                Carrier("Jio", "405863", "91", "IN", "863", "Haryana"),
                Carrier("Jio", "405864", "91", "IN", "864", "Himachal Pradesh"),
                Carrier("Jio", "405865", "91", "IN", "865", "Jammu & Kashmir"),
                Carrier("Jio", "405866", "91", "IN", "866", "Karnataka"),
                Carrier("Jio", "405867", "91", "IN", "867", "Kerala"),
                Carrier("Jio", "405868", "91", "IN", "868", "Kolkata"),
                Carrier("Jio", "405869", "91", "IN", "869", "MP & Chhattisgarh"),
                Carrier("Jio", "405870", "91", "IN", "870", "Maharashtra"),
                Carrier("Jio", "405871", "91", "IN", "871", "Mumbai"),
                Carrier("Jio", "405872", "91", "IN", "872", "North East"),
                Carrier("Jio", "405873", "91", "IN", "873", "Orissa"),
                Carrier("Jio", "405874", "91", "IN", "874", "Punjab"),

                // Vi (Vodafone-Idea) - Major circles
                Carrier("Vi", "40411", "91", "IN", "11", "Delhi NCR"),
                Carrier("Vi", "40420", "91", "IN", "20", "Mumbai"),
                Carrier("Vi", "40446", "91", "IN", "46", "Kerala"),
                Carrier("Vi", "40443", "91", "IN", "43", "Tamil Nadu"),
                Carrier("Vi", "40427", "91", "IN", "27", "Maharashtra"),
                Carrier("Vi", "40430", "91", "IN", "30", "Kolkata"),
                Carrier("Vi", "40405", "91", "IN", "05", "Gujarat"),
                Carrier("Vi", "40407", "91", "IN", "07", "Andhra Pradesh"),
                Carrier("Vi", "40424", "91", "IN", "24", "Gujarat"),
                Carrier("Vi", "40460", "91", "IN", "60", "Rajasthan"),
                Carrier("Vi", "40401", "91", "IN", "01", "Haryana"),
                Carrier("Vi", "40412", "91", "IN", "12", "Haryana"),
                Carrier("Vi", "40422", "91", "IN", "22", "Maharashtra"),

                // BSNL - Government carrier
                Carrier("BSNL", "40434", "91", "IN", "34", "Haryana"),
                Carrier("BSNL", "40438", "91", "IN", "38", "Assam"),
                Carrier("BSNL", "40451", "91", "IN", "51", "HP"),
                Carrier("BSNL", "40453", "91", "IN", "53", "Punjab"),
                Carrier("BSNL", "40454", "91", "IN", "54", "UP West"),
                Carrier("BSNL", "40455", "91", "IN", "55", "UP East"),
                Carrier("BSNL", "40456", "91", "IN", "56", "Gujarat"),
                Carrier("BSNL", "40457", "91", "IN", "57", "MP"),
                Carrier("BSNL", "40458", "91", "IN", "58", "Rajasthan"),
                Carrier("BSNL", "40459", "91", "IN", "59", "Kolkata"),
                Carrier("BSNL", "40462", "91", "IN", "62", "J&K"),
                Carrier("BSNL", "40471", "91", "IN", "71", "Karnataka"),
                Carrier("BSNL", "40472", "91", "IN", "72", "Kerala"),
                Carrier("BSNL", "40473", "91", "IN", "73", "AP"),
                Carrier("BSNL", "40476", "91", "IN", "76", "Orissa"),

                // ═══════════════════════════════════════════════════════════
                // UNITED STATES - COMPREHENSIVE CARRIER DATABASE
                // ═══════════════════════════════════════════════════════════

                // === T-MOBILE (Largest GSM network, merged with Sprint 2020) ===
                Carrier("T-Mobile", "310260", "1", "US", "26", "Nationwide"),
                Carrier("T-Mobile", "310160", "1", "US", "16", "Nationwide Alt"),
                Carrier("T-Mobile", "310200", "1", "US", "20", "Legacy"),
                Carrier("T-Mobile", "310210", "1", "US", "21", "Legacy"),
                Carrier("T-Mobile", "310220", "1", "US", "22", "Legacy"),
                Carrier("T-Mobile", "310230", "1", "US", "23", "Legacy"),
                Carrier("T-Mobile", "310240", "1", "US", "24", "Legacy"),
                Carrier("T-Mobile", "310250", "1", "US", "25", "Legacy"),
                Carrier("T-Mobile", "310270", "1", "US", "27", "Legacy"),
                Carrier("T-Mobile", "310310", "1", "US", "31", "Sprint Migration"),
                Carrier("T-Mobile", "311490", "1", "US", "490", "Sprint Migration"),

                // === VERIZON (Largest LTE/5G network) ===
                Carrier("Verizon", "311480", "1", "US", "48", "Primary"),
                Carrier("Verizon", "310004", "1", "US", "04", "LTE"),
                Carrier("Verizon", "310005", "1", "US", "05", "LTE Alt"),
                Carrier("Verizon", "310006", "1", "US", "06", "LTE Alt 2"),
                Carrier("Verizon", "310010", "1", "US", "10", "Legacy"),
                Carrier("Verizon", "310012", "1", "US", "12", "Legacy"),
                Carrier("Verizon", "310013", "1", "US", "13", "Extended"),
                Carrier("Verizon", "311270", "1", "US", "270", "5G"),
                Carrier("Verizon", "311271", "1", "US", "271", "5G Alt"),
                Carrier("Verizon", "311272", "1", "US", "272", "5G Alt 2"),
                Carrier("Verizon", "311273", "1", "US", "273", "5G Alt 3"),
                Carrier("Verizon", "311274", "1", "US", "274", "5G Alt 4"),
                Carrier("Verizon", "311275", "1", "US", "275", "5G Alt 5"),
                Carrier("Verizon", "311280", "1", "US", "280", "5G Nationwide"),

                // === AT&T (Second largest) ===
                Carrier("AT&T", "310410", "1", "US", "41", "Nationwide"),
                Carrier("AT&T", "310016", "1", "US", "016", "Legacy"),
                Carrier("AT&T", "310030", "1", "US", "030", "Legacy"),
                Carrier("AT&T", "310070", "1", "US", "070", "Legacy"),
                Carrier("AT&T", "310080", "1", "US", "080", "FirstNet"),
                Carrier("AT&T", "310090", "1", "US", "090", "Enterprise"),
                Carrier("AT&T", "310150", "1", "US", "150", "Cricket Legacy"),
                Carrier("AT&T", "310170", "1", "US", "170", "Legacy"),
                Carrier("AT&T", "310280", "1", "US", "280", "Legacy"),
                Carrier("AT&T", "310380", "1", "US", "380", "Legacy"),
                Carrier("AT&T", "310560", "1", "US", "560", "GoCricket"),
                Carrier("AT&T", "311180", "1", "US", "180", "FirstNet"),

                // === US CELLULAR (Regional) ===
                Carrier("US Cellular", "310066", "1", "US", "066", "Midwest"),
                Carrier("US Cellular", "311220", "1", "US", "220", "Alt"),

                // === SPRINT LEGACY (Now T-Mobile, but MNCs still valid) ===
                Carrier("Sprint", "310120", "1", "US", "12", "Legacy"),
                Carrier("Sprint", "311490", "1", "US", "490", "Legacy LTE"),
                Carrier("Sprint", "311870", "1", "US", "870", "Legacy LTE"),
                Carrier("Sprint", "311880", "1", "US", "880", "Legacy LTE"),
                Carrier("Sprint", "312530", "1", "US", "530", "Legacy"),

                // === MVNOs (Use host network MNC but distinct brands) ===
                Carrier("Cricket Wireless", "310150", "1", "US", "150", "AT&T MVNO"),
                Carrier("Metro by T-Mobile", "310260", "1", "US", "26", "T-Mobile MVNO"),
                Carrier("Boost Mobile", "311870", "1", "US", "870", "Dish/Sprint"),

                // === SMALLER REGIONAL CARRIERS ===
                Carrier("GCI", "310430", "1", "US", "430", "Alaska"),
                Carrier("C Spire", "310580", "1", "US", "580", "Mississippi"),
                Carrier("Cellcom", "310510", "1", "US", "510", "Wisconsin"),
                Carrier("Carolina West", "310130", "1", "US", "130", "North Carolina"),
                Carrier("Pioneer Cellular", "310360", "1", "US", "360", "Oklahoma"),
                Carrier("Appalachian Wireless", "312120", "1", "US", "120", "Kentucky"),
                Carrier("Union Wireless", "310020", "1", "US", "020", "Wyoming"),
                Carrier("Plateau Wireless", "310100", "1", "US", "100", "New Mexico"),

                // ═══════════════════════════════════════════════════════════
                // CANADA
                // ═══════════════════════════════════════════════════════════
                Carrier("Rogers", "302720", "1", "CA", "72"),
                Carrier("Telus", "302220", "1", "CA", "22"),
                Carrier("Bell", "302610", "1", "CA", "61"),

                // ═══════════════════════════════════════════════════════════
                // UNITED KINGDOM
                // ═══════════════════════════════════════════════════════════
                Carrier("Vodafone UK", "234150", "44", "GB", "15"),
                Carrier("EE", "234100", "44", "GB", "10"),
                Carrier("O2 UK", "234200", "44", "GB", "20"),
                Carrier("Three UK", "234303", "44", "GB", "33"),

                // ═══════════════════════════════════════════════════════════
                // GERMANY
                // ═══════════════════════════════════════════════════════════
                Carrier("T-Mobile DE", "262010", "49", "DE", "01"),
                Carrier("Vodafone DE", "262020", "49", "DE", "02"),
                Carrier("O2 DE", "262030", "49", "DE", "03"),

                // ═══════════════════════════════════════════════════════════
                // FRANCE
                // ═══════════════════════════════════════════════════════════
                Carrier("SFR", "208010", "33", "FR", "01"),
                Carrier("Orange FR", "208200", "33", "FR", "20"),
                Carrier("Bouygues", "208030", "33", "FR", "03"),

                // ═══════════════════════════════════════════════════════════
                // CHINA
                // ═══════════════════════════════════════════════════════════
                Carrier("China Mobile", "460000", "86", "CN", "00"),
                Carrier("China Unicom", "460010", "86", "CN", "01"),
                Carrier("China Telecom", "460030", "86", "CN", "03"),

                // ═══════════════════════════════════════════════════════════
                // JAPAN
                // ═══════════════════════════════════════════════════════════
                Carrier("NTT DoCoMo", "440100", "81", "JP", "10"),
                Carrier("SoftBank", "440200", "81", "JP", "20"),
                Carrier("KDDI", "440300", "81", "JP", "30"),

                // ═══════════════════════════════════════════════════════════
                // AUSTRALIA
                // ═══════════════════════════════════════════════════════════
                Carrier("Telstra", "505010", "61", "AU", "01"),
                Carrier("Optus", "505020", "61", "AU", "02"),
                Carrier("Vodafone AU", "505030", "61", "AU", "03"),

                // ═══════════════════════════════════════════════════════════
                // SOUTH KOREA
                // ═══════════════════════════════════════════════════════════
                Carrier("SK Telecom", "45005", "82", "KR", "05"),
                Carrier("KT", "45008", "82", "KR", "08"),
                Carrier("LG U+", "45006", "82", "KR", "06"),

                // ═══════════════════════════════════════════════════════════
                // BRAZIL
                // ═══════════════════════════════════════════════════════════
                Carrier("Vivo BR", "72406", "55", "BR", "06"),
                Carrier("Claro BR", "72405", "55", "BR", "05"),
                Carrier("Tim BR", "72404", "55", "BR", "04"),
                Carrier("Oi", "72431", "55", "BR", "31"),

                // ═══════════════════════════════════════════════════════════
                // RUSSIA
                // ═══════════════════════════════════════════════════════════
                Carrier("MTS RU", "25001", "7", "RU", "01"),
                Carrier("Beeline RU", "25099", "7", "RU", "99"),
                Carrier("MegaFon", "25002", "7", "RU", "02"),
                Carrier("Tele2 RU", "25020", "7", "RU", "20"),

                // ═══════════════════════════════════════════════════════════
                // MEXICO
                // ═══════════════════════════════════════════════════════════
                Carrier("Telcel", "33402", "52", "MX", "02"),
                Carrier("AT&T MX", "33401", "52", "MX", "01"),
                Carrier("Movistar MX", "33403", "52", "MX", "03"),

                // ═══════════════════════════════════════════════════════════
                // INDONESIA
                // ═══════════════════════════════════════════════════════════
                Carrier("Telkomsel", "51010", "62", "ID", "10"),
                Carrier("Indosat", "51021", "62", "ID", "21"),
                Carrier("XL Axiata", "51011", "62", "ID", "11"),
                Carrier("Tri ID", "51089", "62", "ID", "89"),

                // ═══════════════════════════════════════════════════════════
                // SAUDI ARABIA
                // ═══════════════════════════════════════════════════════════
                Carrier("STC", "42001", "966", "SA", "01"),
                Carrier("Mobily", "42003", "966", "SA", "03"),
                Carrier("Zain SA", "42004", "966", "SA", "04"),

                // ═══════════════════════════════════════════════════════════
                // UAE
                // ═══════════════════════════════════════════════════════════
                Carrier("Etisalat", "42402", "971", "AE", "02"),
                Carrier("Du", "42403", "971", "AE", "03"),
            )

        /** Find carrier by MCC/MNC code. */
        fun getByMccMnc(mccMnc: String): Carrier? {
            return ALL_CARRIERS.find { it.mccMnc == mccMnc }
        }

        /** Get all carriers for a country. */
        fun getByCountry(countryIso: String): List<Carrier> {
            return ALL_CARRIERS.filter { it.countryIso.equals(countryIso, ignoreCase = true) }
        }

        /** Get carriers by name (partial match). */
        fun getByName(name: String): List<Carrier> {
            return ALL_CARRIERS.filter { it.name.contains(name, ignoreCase = true) }
        }

        /** Get a random carrier. */
        fun nextSecureRandom(): Carrier {
            return ALL_CARRIERS.secureRandom()
        }

        /** Get a random carrier from a specific country. */
        fun nextSecureRandomFromCountry(countryIso: String): Carrier? {
            return getByCountry(countryIso).let { if (it.isEmpty()) null else it.secureRandom() }
        }

        /** Get India carriers only. */
        fun indiaCarriers(): List<Carrier> = getByCountry("IN")

        /** Get a random India carrier. */
        fun nextSecureRandomIndia(): Carrier = indiaCarriers().secureRandom()
    }
}
