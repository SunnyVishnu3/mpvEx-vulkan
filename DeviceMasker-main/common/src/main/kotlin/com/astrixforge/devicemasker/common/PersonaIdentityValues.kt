package com.astrixforge.devicemasker.common

import com.astrixforge.devicemasker.common.models.Carrier
import com.astrixforge.devicemasker.common.util.Luhn
import java.util.Locale

internal fun deterministicImei(
    rootSeed: String,
    label: String,
    preset: DeviceProfilePreset,
): String {
    val tac =
        if (preset.tacPrefixes.isNotEmpty()) {
            pickFrom(rootSeed, "$label:tac", preset.tacPrefixes)
        } else {
            "35000000"
        }
    val serial = deterministicDigits(rootSeed, "$label:serial", IMEI_SERIAL_LENGTH)
    return Luhn.appendCheckDigit(tac + serial)
}

internal fun deterministicImsi(rootSeed: String, label: String, carrier: Carrier): String {
    val remaining = (IMSI_LENGTH - carrier.mccMnc.length).coerceAtLeast(1)
    return carrier.mccMnc + deterministicDigits(rootSeed, label, remaining)
}

internal fun deterministicIccid(rootSeed: String, label: String, carrier: Carrier): String {
    val prefix = "89${carrier.countryCode}${carrier.iccidIssuerCode}"
    val baseLength = (ICCID_LENGTH - 1).coerceAtLeast(prefix.length + 1)
    val serialLength = (baseLength - prefix.length).coerceAtLeast(1)
    return Luhn.appendCheckDigit(prefix + deterministicDigits(rootSeed, label, serialLength))
}

internal fun deterministicPhoneNumber(rootSeed: String, label: String, carrier: Carrier): String {
    val nationalLength =
        COUNTRY_PHONE_LENGTH[carrier.countryIso.uppercase(Locale.US)] ?: DEFAULT_PHONE_LENGTH
    return "+${carrier.countryCode}${deterministicDigits(rootSeed, label, nationalLength)}"
}

internal fun deterministicMac(rootSeed: String, label: String, manufacturer: String): String {
    val ouis =
        MANUFACTURER_OUIS[manufacturer.lowercase(Locale.US)]
            ?: MANUFACTURER_OUIS["generic"].orEmpty()
    val prefix = pickFrom(rootSeed, "$label:oui", ouis).split(':')
    val bytes = digestBytes(rootSeed, label).copyOf(3)
    val suffix = bytes.joinToString(":") { "%02X".format(it) }
    return (prefix + suffix.split(':')).joinToString(":")
}

internal fun deterministicSerial(rootSeed: String, label: String, manufacturer: String): String =
    when (manufacturer.lowercase(Locale.US)) {
        "samsung" ->
            "R${deterministicDigits(rootSeed, "$label:prefix", 2)}" +
                "ABCDEFGHJKLMNPRSTUVWXYZ"[
                    deterministicInt(rootSeed, "$label:year", SAMSUNG_YEAR_LETTERS_LENGTH)] +
                deterministicDigits(rootSeed, "$label:body", SAMSUNG_BODY_LENGTH)
        "google" ->
            deterministicHex(rootSeed, "$label:pixel", PIXEL_SERIAL_BYTES).uppercase(Locale.US)
        "xiaomi",
        "redmi",
        "poco",
        "mi" -> deterministicAlphaNumeric(rootSeed, "$label:xiaomi", XIAOMI_SERIAL_LENGTH)
        else -> deterministicAlphaNumeric(rootSeed, "$label:generic", GENERIC_SERIAL_LENGTH)
    }

private fun deterministicAlphaNumeric(rootSeed: String, label: String, count: Int): String {
    val alphabet = "0123456789ABCDEFGHJKLMNPRSTUVWXYZ"
    val bytes = digestBytes(rootSeed, label)
    return buildString {
        repeat(count) { index ->
            append(alphabet[(bytes[index].toInt() and BYTE_MASK) % alphabet.length])
        }
    }
}

internal fun parseAndroidRelease(fingerprint: String): String =
    fingerprint.substringAfter(':', DEFAULT_ANDROID_RELEASE).substringBefore('/')

private val MANUFACTURER_OUIS =
    mapOf(
        "samsung" to listOf("00:16:32", "78:AB:BB"),
        "google" to listOf("3C:5A:B4", "94:EB:2C"),
        "xiaomi" to listOf("04:CF:8C", "34:CE:00"),
        "oneplus" to listOf("02:00:00", "06:00:00"),
        "sony" to listOf("00:1A:11", "3C:2E:F9"),
        "nothing" to listOf("02:00:00", "0A:00:00"),
        "generic" to listOf("02:00:00", "06:00:00", "0A:00:00"),
    )

private val COUNTRY_PHONE_LENGTH =
    mapOf(
        "US" to 10,
        "CA" to 10,
        "GB" to 10,
        "DE" to 10,
        "FR" to 9,
        "IN" to 10,
        "CN" to 11,
        "JP" to 10,
        "AU" to 9,
        "KR" to 10,
        "BR" to 11,
        "RU" to 10,
        "MX" to 10,
        "ID" to 10,
        "SA" to 9,
        "AE" to 9,
    )
