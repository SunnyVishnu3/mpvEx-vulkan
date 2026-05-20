package com.astrixforge.devicemasker.common.generators

import com.astrixforge.devicemasker.common.models.Carrier
import com.astrixforge.devicemasker.common.util.secureRandom
import java.security.SecureRandom

/**
 * Phone Number Generator for SIM card spoofing.
 *
 * Generates phone numbers matching the carrier's country, ensuring consistency with IMSI and ICCID.
 *
 * For USA: Uses realistic area codes from major states. Format: +{countryCode}{number}
 */
object PhoneNumberGenerator {

    private val secureRandom = SecureRandom()

    /**
     * US area codes by state for realistic generation. Selected high-population area codes from
     * major states.
     */
    private val US_AREA_CODES =
        listOf(
            // California (most population)
            "213",
            "310",
            "323",
            "408",
            "415",
            "510",
            "619",
            "626",
            "650",
            "714",
            "818",
            "909",
            "949",
            // New York
            "212",
            "315",
            "347",
            "516",
            "518",
            "585",
            "607",
            "631",
            "646",
            "716",
            "718",
            "845",
            "914",
            "917",
            "929",
            // Texas
            "210",
            "214",
            "254",
            "281",
            "361",
            "409",
            "512",
            "682",
            "713",
            "817",
            "830",
            "903",
            "915",
            "972",
            // Florida
            "239",
            "305",
            "321",
            "352",
            "386",
            "407",
            "561",
            "727",
            "754",
            "786",
            "813",
            "850",
            "863",
            "904",
            "941",
            "954",
            // Illinois
            "217",
            "309",
            "312",
            "630",
            "708",
            "773",
            "815",
            "847",
            // Pennsylvania
            "215",
            "267",
            "412",
            "484",
            "570",
            "610",
            "717",
            "724",
            "814",
            // Ohio
            "216",
            "234",
            "330",
            "419",
            "440",
            "513",
            "567",
            "614",
            "740",
            "937",
            // Georgia
            "229",
            "404",
            "470",
            "478",
            "678",
            "706",
            "762",
            "770",
            "912",
            // Michigan
            "231",
            "248",
            "269",
            "313",
            "517",
            "586",
            "616",
            "734",
            "810",
            "989",
            // New Jersey
            "201",
            "551",
            "609",
            "732",
            "848",
            "856",
            "862",
            "908",
            "973",
            // Washington
            "206",
            "253",
            "360",
            "425",
            "509",
            // Arizona
            "480",
            "520",
            "602",
            "623",
            "928",
            // Colorado
            "303",
            "719",
            "720",
            "970",
            // Massachusetts
            "339",
            "351",
            "413",
            "508",
            "617",
            "774",
            "781",
            "857",
            "978",
        )

    /** Digit counts for phone numbers by country code. Excludes country code itself. */
    private val COUNTRY_PHONE_LENGTH =
        mapOf(
            "1" to 10, // US/Canada: +1 XXX XXX XXXX
            "44" to 10, // UK: +44 XXXX XXXXXX
            "49" to 11, // Germany: +49 XXX XXXXXXX
            "33" to 9, // France: +33 X XX XX XX XX
            "91" to 10, // India: +91 XXXXX XXXXX
            "86" to 11, // China: +86 XXX XXXX XXXX
            "81" to 10, // Japan: +81 XX XXXX XXXX
            "61" to 9, // Australia: +61 XXX XXX XXX
        )

    /**
     * Generates a random phone number.
     *
     * @return Phone number with random country code
     */
    fun generate(): String {
        val countryCode = COUNTRY_PHONE_LENGTH.keys.secureRandom()

        return if (countryCode == "1") {
            // US/Canada: use realistic area code
            generateUSPhoneNumber()
        } else {
            val digits = COUNTRY_PHONE_LENGTH[countryCode] ?: 10
            val number = buildString { repeat(digits) { append(secureRandom.nextInt(10)) } }
            "+$countryCode$number"
        }
    }

    /**
     * Generates a phone number for a specific carrier.
     *
     * The phone number will match the carrier's country code, ensuring consistency with IMSI and
     * ICCID.
     *
     * For US carriers, uses realistic area codes.
     *
     * @param carrier The carrier to generate phone number for
     * @return Phone number with matching country code
     */
    fun generate(carrier: Carrier): String {
        val countryCode = carrier.countryCode
        return if (countryCode == "1") {
            when (carrier.countryIso) {
                "US" -> generateUSPhoneNumber() // USA: Use realistic area code
                "CA" -> generateCanadianPhoneNumber() // Canada: Use Canadian area codes
                else -> generateGenericNumber(countryCode) // Other NANP regions
            }
        } else {
            generateGenericNumber(countryCode)
        }
    }

    /** Generates a generic phone number for a given country code. */
    private fun generateGenericNumber(countryCode: String): String {
        val digits = COUNTRY_PHONE_LENGTH[countryCode] ?: 10
        val number = buildString { repeat(digits) { append(secureRandom.nextInt(10)) } }
        return "+$countryCode$number"
    }

    /**
     * Generates a realistic US phone number. Format: +1 (areaCode) XXX-XXXX
     *
     * Uses real area codes from major US states.
     */
    private fun generateUSPhoneNumber(): String {
        val areaCode = US_AREA_CODES.secureRandom()

        // Exchange code (can't start with 0 or 1 per NANP rules)
        val exchange = buildString {
            append((2..9).secureRandom())
            repeat(2) { append(secureRandom.nextInt(10)) }
        }

        // Subscriber number (4 digits)
        val subscriber = buildString { repeat(4) { append(secureRandom.nextInt(10)) } }

        return "+1$areaCode$exchange$subscriber"
    }

    /** Generates a Canadian phone number. Uses common Canadian area codes. */
    private fun generateCanadianPhoneNumber(): String {
        val canadianAreaCodes =
            listOf(
                // Ontario
                "416",
                "647",
                "437",
                "905",
                "289",
                "365",
                "613",
                "343",
                "519",
                "226",
                // Quebec
                "514",
                "438",
                "450",
                "579",
                "418",
                "581",
                "819",
                "873",
                // British Columbia
                "604",
                "778",
                "236",
                "250",
                // Alberta
                "403",
                "587",
                "780",
                "825",
                // Other provinces
                "204",
                "431", // Manitoba
                "306",
                "639", // Saskatchewan
                "902",
                "782", // Nova Scotia/PEI
                "506", // New Brunswick
                "709", // Newfoundland
            )

        val areaCode = canadianAreaCodes.secureRandom()

        // Exchange code (can't start with 0 or 1)
        val exchange = buildString {
            append((2..9).secureRandom())
            repeat(2) { append(secureRandom.nextInt(10)) }
        }

        // Subscriber number (4 digits)
        val subscriber = buildString { repeat(4) { append(secureRandom.nextInt(10)) } }

        return "+1$areaCode$exchange$subscriber"
    }
}
