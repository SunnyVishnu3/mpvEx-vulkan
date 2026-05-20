package com.astrixforge.devicemasker.common.models

import com.astrixforge.devicemasker.common.util.secureRandom as secureRandomSource
import com.astrixforge.devicemasker.common.util.secureRandom
import kotlinx.serialization.Serializable

/**
 * Correlated location values.
 *
 * Timezone, locale, and GPS coordinates are all derived from the same country. This ensures
 * consistency and prevents detection from location mismatches.
 */
@Serializable
data class LocationConfig(
    val country: String, // ISO 3166-1 alpha-2 code (e.g., "US", "JP")
    val timezone: String, // TZ database name (e.g., "America/New_York")
    val locale: String, // Language_Country (e.g., "en_US")
    val latitude: Double, // GPS latitude within country bounds
    val longitude: Double, // GPS longitude within country bounds
) {
    companion object {

        /**
         * GPS coordinate bounds by country (minLat, maxLat, minLon, maxLon). Major cities/populated
         * areas for realism.
         */
        private val COUNTRY_GPS_BOUNDS =
            mapOf(
                // USA - Continental 48 states focus
                "US" to
                    listOf(
                        // New York City area
                        GPSBounds(40.4774, 40.9176, -74.2591, -73.7004),
                        // Los Angeles area
                        GPSBounds(33.7037, 34.3373, -118.6682, -117.6462),
                        // Chicago area
                        GPSBounds(41.6445, 42.0230, -87.9401, -87.5244),
                        // Houston area
                        GPSBounds(29.5150, 30.1142, -95.7884, -95.0147),
                        // Phoenix area
                        GPSBounds(33.2903, 33.7490, -112.3241, -111.9261),
                        // San Francisco area
                        GPSBounds(37.6390, 37.9298, -122.5169, -122.3481),
                    ),
                // Canada - Major cities
                "CA" to
                    listOf(
                        GPSBounds(43.5810, 43.8555, -79.6393, -79.1158), // Toronto
                        GPSBounds(49.0495, 49.3167, -123.2247, -122.9851), // Vancouver
                        GPSBounds(45.4107, 45.5376, -73.7498, -73.4749), // Montreal
                    ),
                // UK - Major cities
                "GB" to
                    listOf(
                        GPSBounds(51.3841, 51.6723, -0.3514, 0.1484), // London
                        GPSBounds(53.3343, 53.5383, -2.3178, -2.1178), // Manchester
                        GPSBounds(52.3808, 52.5858, -2.0177, -1.8177), // Birmingham
                    ),
                // Germany - Major cities
                "DE" to
                    listOf(
                        GPSBounds(52.3382, 52.6755, 13.0884, 13.7611), // Berlin
                        GPSBounds(48.0616, 48.2481, 11.3607, 11.7229), // Munich
                        GPSBounds(50.0154, 50.2271, 8.4721, 8.8006), // Frankfurt
                    ),
                // France - Major cities
                "FR" to
                    listOf(
                        GPSBounds(48.8155, 48.9022, 2.2242, 2.4699), // Paris
                        GPSBounds(43.2304, 43.3436, 5.2822, 5.4867), // Marseille
                        GPSBounds(45.7089, 45.8109, 4.7723, 4.8983), // Lyon
                    ),
                // India - Major cities
                "IN" to
                    listOf(
                        GPSBounds(18.8928, 19.2705, 72.7758, 72.9866), // Mumbai
                        GPSBounds(28.4041, 28.8835, 76.8380, 77.3419), // Delhi
                        GPSBounds(12.8340, 13.1399, 77.4601, 77.7847), // Bangalore
                        GPSBounds(17.2876, 17.5545, 78.2705, 78.5932), // Hyderabad
                        GPSBounds(22.4507, 22.6296, 88.2635, 88.4501), // Kolkata
                        GPSBounds(13.0033, 13.1467, 80.1689, 80.3047), // Chennai
                    ),
                // China - Major cities
                "CN" to
                    listOf(
                        GPSBounds(39.7555, 40.0235, 116.1850, 116.5428), // Beijing
                        GPSBounds(30.9898, 31.3730, 121.1151, 121.8052), // Shanghai
                        GPSBounds(22.1533, 22.5640, 113.8367, 114.4095), // Shenzhen/Guangzhou
                    ),
                // Japan - Major cities
                "JP" to
                    listOf(
                        GPSBounds(35.5306, 35.8174, 139.4670, 139.9108), // Tokyo
                        GPSBounds(34.5665, 34.7760, 135.3868, 135.5967), // Osaka
                        GPSBounds(35.0920, 35.2359, 136.7781, 137.0188), // Nagoya
                    ),
                // Australia - Major cities
                "AU" to
                    listOf(
                        GPSBounds(-33.9991, -33.5781, 150.9204, 151.3430), // Sydney
                        GPSBounds(-37.9751, -37.6655, 144.7732, 145.1503), // Melbourne
                        GPSBounds(-27.6073, -27.2529, 152.8644, 153.1789), // Brisbane
                        GPSBounds(-32.0587, -31.7952, 115.7439, 116.0178), // Perth
                    ),
                // South Korea - Major cities
                "KR" to
                    listOf(
                        GPSBounds(37.4138, 37.7017, 126.7642, 127.1838), // Seoul
                        GPSBounds(35.0690, 35.2359, 128.9680, 129.1318), // Busan
                        GPSBounds(35.7988, 35.9413, 128.4930, 128.7321), // Daegu
                    ),
                // Brazil - Major cities
                "BR" to
                    listOf(
                        GPSBounds(-23.7494, -23.3569, -46.8755, -46.3654), // São Paulo
                        GPSBounds(-23.0749, -22.7469, -43.7958, -43.0987), // Rio de Janeiro
                        GPSBounds(-15.8697, -15.7285, -48.0216, -47.7937), // Brasília
                    ),
                // Russia - Major cities
                "RU" to
                    listOf(
                        GPSBounds(55.5699, 55.9116, 37.3193, 37.9451), // Moscow
                        GPSBounds(59.8372, 60.0925, 30.0789, 30.5593), // St. Petersburg
                        GPSBounds(54.9266, 55.1267, 82.8451, 83.1189), // Novosibirsk
                    ),
                // Mexico - Major cities
                "MX" to
                    listOf(
                        GPSBounds(19.2040, 19.5929, -99.3647, -98.9454), // Mexico City
                        GPSBounds(25.5697, 25.8140, -100.4687, -100.1965), // Monterrey
                        GPSBounds(20.5889, 20.7619, -103.4366, -103.2545), // Guadalajara
                    ),
                // Indonesia - Major cities
                "ID" to
                    listOf(
                        GPSBounds(-6.3702, -6.0873, 106.6829, 107.0200), // Jakarta
                        GPSBounds(-7.3444, -7.1949, 112.6242, 112.8333), // Surabaya
                        GPSBounds(-6.9776, -6.8646, 107.5371, 107.7254), // Bandung
                    ),
                // Saudi Arabia - Major cities
                "SA" to
                    listOf(
                        GPSBounds(24.5501, 24.8677, 46.5420, 46.8571), // Riyadh
                        GPSBounds(21.3458, 21.5785, 39.7231, 39.9825), // Jeddah
                        GPSBounds(21.3713, 21.4598, 39.7866, 39.8931), // Mecca
                    ),
                // UAE - Major cities
                "AE" to
                    listOf(
                        GPSBounds(25.0657, 25.3589, 55.0976, 55.4023), // Dubai
                        GPSBounds(24.3338, 24.5671, 54.2917, 54.7692), // Abu Dhabi
                        GPSBounds(25.3136, 25.4195, 55.3679, 55.4877), // Sharjah
                    ),
            )

        /** Timezone options by country. */
        private val COUNTRY_TIMEZONES =
            mapOf(
                // USA - Complete 6 time zone coverage
                "US" to
                    listOf(
                        "America/New_York", // Eastern (17 states + DC)
                        "America/Chicago", // Central (9 states fully)
                        "America/Denver", // Mountain (6 states fully)
                        "America/Los_Angeles", // Pacific (3 states fully)
                        "America/Phoenix", // Arizona (no DST)
                        "America/Anchorage", // Alaska
                        "Pacific/Honolulu", // Hawaii (no DST)
                    ),
                // Canada
                "CA" to
                    listOf(
                        "America/Toronto", // Eastern
                        "America/Vancouver", // Pacific
                        "America/Edmonton", // Mountain
                        "America/Winnipeg", // Central
                        "America/Halifax", // Atlantic
                    ),
                "GB" to listOf("Europe/London"),
                "DE" to listOf("Europe/Berlin"),
                "FR" to listOf("Europe/Paris"),
                "IN" to listOf("Asia/Kolkata"),
                "CN" to listOf("Asia/Shanghai", "Asia/Urumqi"),
                "JP" to listOf("Asia/Tokyo"),
                "AU" to
                    listOf(
                        "Australia/Sydney",
                        "Australia/Melbourne",
                        "Australia/Brisbane",
                        "Australia/Perth",
                        "Australia/Adelaide",
                    ),
                // NEW countries
                "KR" to listOf("Asia/Seoul"),
                "BR" to listOf("America/Sao_Paulo", "America/Brasilia", "America/Fortaleza"),
                "RU" to listOf("Europe/Moscow", "Asia/Novosibirsk", "Asia/Vladivostok"),
                "MX" to listOf("America/Mexico_City", "America/Tijuana", "America/Cancun"),
                "ID" to listOf("Asia/Jakarta", "Asia/Makassar", "Asia/Jayapura"),
                "SA" to listOf("Asia/Riyadh"),
                "AE" to listOf("Asia/Dubai"),
            )

        /** Locale options by country. */
        private val COUNTRY_LOCALES =
            mapOf(
                // USA - English with Spanish as second most common
                "US" to listOf("en_US", "es_US"),
                "CA" to listOf("en_CA", "fr_CA"),
                "GB" to listOf("en_GB"),
                "DE" to listOf("de_DE"),
                "FR" to listOf("fr_FR"),
                "IN" to listOf("en_IN", "hi_IN"),
                "CN" to listOf("zh_CN"),
                "JP" to listOf("ja_JP"),
                "AU" to listOf("en_AU"),
                // NEW countries
                "KR" to listOf("ko_KR"),
                "BR" to listOf("pt_BR"),
                "RU" to listOf("ru_RU"),
                "MX" to listOf("es_MX"),
                "ID" to listOf("id_ID"),
                "SA" to listOf("ar_SA"),
                "AE" to listOf("ar_AE", "en_AE"),
            )

        /**
         * Generate a location config with correlated GPS coordinates.
         *
         * @param country Optional country code. If null, selects randomly.
         * @return LocationConfig with matching timezone, locale, and GPS within country bounds
         */
        fun generate(country: String? = null): LocationConfig {
            val selectedCountry = country ?: COUNTRY_TIMEZONES.keys.secureRandom()
            val timezones = COUNTRY_TIMEZONES[selectedCountry] ?: listOf("UTC")
            val locales = COUNTRY_LOCALES[selectedCountry] ?: listOf("en_US")
            val gpsBounds = COUNTRY_GPS_BOUNDS[selectedCountry]?.secureRandom()

            // Generate coordinates within country bounds, or random if no bounds defined
            val (lat, lon) =
                if (gpsBounds != null) {
                    val latitude =
                        gpsBounds.minLat +
                            (gpsBounds.maxLat - gpsBounds.minLat) * secureRandomSource.nextDouble()
                    val longitude =
                        gpsBounds.minLon +
                            (gpsBounds.maxLon - gpsBounds.minLon) * secureRandomSource.nextDouble()
                    latitude to longitude
                } else {
                    // Fallback to random worldwide
                    (-MAX_LATITUDE + LATITUDE_SPAN * secureRandomSource.nextDouble()) to
                        (-MAX_LONGITUDE + LONGITUDE_SPAN * secureRandomSource.nextDouble())
                }

            return LocationConfig(
                country = selectedCountry,
                timezone = timezones.secureRandom(),
                locale = locales.secureRandom(),
                latitude = lat,
                longitude = lon,
            )
        }

        /**
         * Generate matching the SIM card's country.
         *
         * @param carrier The SIM card's carrier
         * @return LocationConfig from same country as carrier
         */
        fun generateForCarrier(carrier: Carrier): LocationConfig {
            return generate(carrier.countryIso)
        }

        /**
         * Get country code from a timezone ID.
         *
         * @param timezoneId The timezone ID (e.g., "America/New_York")
         * @return Country ISO code or null if not found
         */
        fun getCountryForTimezone(timezoneId: String): String? {
            return COUNTRY_TIMEZONES.entries.find { (_, timezones) -> timezoneId in timezones }?.key
        }

        /**
         * Get a locale for a given timezone.
         *
         * Finds the country for the timezone, then returns a locale for that country.
         *
         * @param timezoneId The timezone ID
         * @return Locale string (e.g., "en_US") or null if timezone not mapped
         */
        fun getLocaleForTimezone(timezoneId: String): String? {
            val country = getCountryForTimezone(timezoneId)
            return country?.let(COUNTRY_LOCALES::get)?.firstOrNull()
        }

        private const val MAX_LATITUDE = 90.0
        private const val LATITUDE_SPAN = 180.0
        private const val MAX_LONGITUDE = 180.0
        private const val LONGITUDE_SPAN = 360.0
    }
}

/** GPS bounding box for a region. */
data class GPSBounds(
    val minLat: Double,
    val maxLat: Double,
    val minLon: Double,
    val maxLon: Double,
)
