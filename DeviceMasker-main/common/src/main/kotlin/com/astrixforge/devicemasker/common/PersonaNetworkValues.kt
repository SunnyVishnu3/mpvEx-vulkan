package com.astrixforge.devicemasker.common

import com.astrixforge.devicemasker.common.models.Carrier
import com.astrixforge.devicemasker.common.models.GPSBounds
import com.astrixforge.devicemasker.common.models.LocationConfig
import java.util.Locale

internal fun generateSsid(rootSeed: String, carrier: Carrier, location: LocationConfig): String {
    val countryPatterns =
        when (carrier.countryIso.uppercase(Locale.US)) {
            "US" ->
                listOf(
                    ssidWithHexPrefix(rootSeed, "NETGEAR-", "ssid-us-netgear"),
                    "\"ATT-${deterministicHex(rootSeed, "ssid-us-att", MEDIUM_HEX_BYTES).uppercase(Locale.US)}\"",
                    "\"${carrier.name}_5G\"",
                )
            "IN" ->
                listOf(
                    ssidWithDigitsPrefix(
                        rootSeed = rootSeed,
                        prefix = "${carrier.name}Fiber-",
                        label = "ssid-in-fiber",
                    ),
                    "\"Airtel_Xstream_${deterministicDigits(rootSeed, "ssid-in-airtel", MEDIUM_HEX_BYTES)}\"",
                    ssidWithHexPrefix(rootSeed, "JioFiber-", "ssid-in-jio"),
                )
            "AU" ->
                listOf(
                    "\"Telstra-${deterministicDigits(rootSeed, "ssid-au-telstra", MIN_DISTINCT_TRACKING_IDS)}\"",
                    "\"Home-${location.timezone.substringAfter('/').take(MIN_DISTINCT_TRACKING_IDS)}\"",
                    ssidWithHexPrefix(rootSeed, "TP-Link_", "ssid-au-tplink"),
                )
            else ->
                listOf(
                    ssidWithDigitsPrefix(
                        rootSeed = rootSeed,
                        prefix = "${carrier.name}_",
                        label = "ssid-carrier",
                    ),
                    ssidWithHexPrefix(rootSeed, "Home_WiFi_", "ssid-home"),
                    "\"Guest_${carrier.countryIso.uppercase(Locale.US)}\"",
                )
        }
    return pickFrom(rootSeed, "ssid-pattern:${carrier.mccMnc}", countryPatterns)
}

internal fun generateBssid(rootSeed: String, carrier: Carrier): String {
    val bytes = digestBytes(rootSeed, "bssid:${carrier.mccMnc}")
    val mac = bytes.copyOfRange(0, MAC_BYTES)
    mac[0] = (mac[0].toInt() and LOCAL_MAC_MASK).toByte()
    return mac.joinToString(":") { "%02X".format(it) }
}

internal fun countryTimezones(countryIso: String): List<String> =
    COUNTRY_TIMEZONES[countryIso] ?: listOf("UTC")

internal fun countryLocales(countryIso: String): List<String> =
    COUNTRY_LOCALES[countryIso] ?: listOf("en_US")

internal fun gpsBoundsFor(countryIso: String): List<GPSBounds> =
    COUNTRY_GPS_BOUNDS[countryIso] ?: listOf(DEFAULT_GPS_BOUNDS)

private fun ssidWithHexPrefix(rootSeed: String, prefix: String, label: String): String =
    "\"$prefix${deterministicHex(rootSeed, label, SHORT_HEX_BYTES).uppercase(Locale.US)}\""

private fun ssidWithDigitsPrefix(rootSeed: String, prefix: String, label: String): String =
    "\"$prefix${deterministicDigits(rootSeed, label, MIN_DISTINCT_TRACKING_IDS)}\""

private val COUNTRY_TIMEZONES =
    mapOf(
        "US" to listOf("America/New_York", "America/Chicago", "America/Los_Angeles"),
        "CA" to listOf("America/Toronto", "America/Vancouver"),
        "GB" to listOf("Europe/London"),
        "DE" to listOf("Europe/Berlin"),
        "FR" to listOf("Europe/Paris"),
        "IN" to listOf("Asia/Kolkata"),
        "CN" to listOf("Asia/Shanghai"),
        "JP" to listOf("Asia/Tokyo"),
        "AU" to listOf("Australia/Sydney", "Australia/Perth"),
        "KR" to listOf("Asia/Seoul"),
        "BR" to listOf("America/Sao_Paulo"),
        "RU" to listOf("Europe/Moscow", "Asia/Novosibirsk"),
        "MX" to listOf("America/Mexico_City"),
        "ID" to listOf("Asia/Jakarta"),
        "SA" to listOf("Asia/Riyadh"),
        "AE" to listOf("Asia/Dubai"),
    )

private val COUNTRY_LOCALES =
    mapOf(
        "US" to listOf("en_US", "es_US"),
        "CA" to listOf("en_CA", "fr_CA"),
        "GB" to listOf("en_GB"),
        "DE" to listOf("de_DE"),
        "FR" to listOf("fr_FR"),
        "IN" to listOf("en_IN", "hi_IN"),
        "CN" to listOf("zh_CN"),
        "JP" to listOf("ja_JP"),
        "AU" to listOf("en_AU"),
        "KR" to listOf("ko_KR"),
        "BR" to listOf("pt_BR"),
        "RU" to listOf("ru_RU"),
        "MX" to listOf("es_MX"),
        "ID" to listOf("id_ID"),
        "SA" to listOf("ar_SA"),
        "AE" to listOf("ar_AE", "en_AE"),
    )

private val COUNTRY_GPS_BOUNDS =
    mapOf(
        "US" to
            listOf(
                GPSBounds(40.4774, 40.9176, -74.2591, -73.7004),
                GPSBounds(33.7037, 34.3373, -118.6682, -117.6462),
            ),
        "CA" to listOf(GPSBounds(43.5810, 43.8555, -79.6393, -79.1158)),
        "GB" to listOf(GPSBounds(51.3841, 51.6723, -0.3514, 0.1484)),
        "DE" to listOf(GPSBounds(52.3382, 52.6755, 13.0884, 13.7611)),
        "FR" to listOf(GPSBounds(48.8155, 48.9022, 2.2242, 2.4699)),
        "IN" to
            listOf(
                GPSBounds(18.8928, 19.2705, 72.7758, 72.9866),
                GPSBounds(28.4041, 28.8835, 76.8380, 77.3419),
            ),
        "CN" to listOf(GPSBounds(39.7555, 40.0235, 116.1850, 116.5428)),
        "JP" to listOf(GPSBounds(35.5306, 35.8174, 139.4670, 139.9108)),
        "AU" to listOf(GPSBounds(-33.9991, -33.5781, 150.9204, 151.3430)),
        "KR" to listOf(GPSBounds(37.4138, 37.7017, 126.7642, 127.1838)),
        "BR" to listOf(GPSBounds(-23.7494, -23.3569, -46.8755, -46.3654)),
        "RU" to listOf(GPSBounds(55.5699, 55.9116, 37.3193, 37.9451)),
        "MX" to listOf(GPSBounds(19.2040, 19.5929, -99.3647, -98.9454)),
        "ID" to listOf(GPSBounds(-6.3702, -6.0873, 106.6829, 107.0200)),
        "SA" to listOf(GPSBounds(24.5501, 24.8677, 46.5420, 46.8571)),
        "AE" to listOf(GPSBounds(25.0657, 25.3589, 55.0976, 55.4023)),
    )

private val DEFAULT_GPS_BOUNDS = GPSBounds(-33.8688, -33.7, 151.0, 151.3)
