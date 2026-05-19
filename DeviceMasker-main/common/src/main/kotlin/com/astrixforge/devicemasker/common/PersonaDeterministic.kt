package com.astrixforge.devicemasker.common

import java.security.MessageDigest
import java.util.UUID

internal fun deterministicCoordinate(
    rootSeed: String,
    label: String,
    min: Double,
    max: Double,
): Double {
    if (max <= min) return min
    val ratio =
        digestBytes(rootSeed, label)
            .take(DIGEST_LONG_BYTES)
            .fold(0L) { acc, byte ->
                (acc shl DIGEST_LONG_BYTES) or (byte.toLong() and BYTE_MASK_LONG)
            }
            .toDouble() / ULong.MAX_VALUE.toLong().toDouble()
    return min + (max - min) * ratio.coerceIn(0.0, 1.0)
}

internal fun <T> pickFrom(rootSeed: String, label: String, options: List<T>): T {
    require(options.isNotEmpty()) { "Options must not be empty" }
    val index = deterministicInt(rootSeed, label, options.size)
    return options[index]
}

internal fun deterministicInt(rootSeed: String, label: String, bound: Int): Int {
    if (bound <= 1) return 0
    val value =
        digestBytes(rootSeed, label).take(MIN_DISTINCT_TRACKING_IDS).fold(0) { acc, byte ->
            (acc shl DIGEST_LONG_BYTES) or (byte.toInt() and BYTE_MASK)
        }
    return (value and Int.MAX_VALUE) % bound
}

internal fun deterministicDigits(rootSeed: String, label: String, count: Int): String =
    buildString {
        val bytes = digestBytes(rootSeed, label)
        repeat(count) { index -> append((bytes[index].toInt() and BYTE_MASK) % DECIMAL_RADIX) }
    }

internal fun deterministicHex(rootSeed: String, label: String, byteCount: Int): String =
    digestBytes(rootSeed, label).copyOf(byteCount).joinToString("") { "%02x".format(it) }

internal fun deterministicUuid(rootSeed: String, label: String): UUID {
    val bytes = digestBytes(rootSeed, label).copyOf(UUID_BYTES)
    bytes[UUID_VERSION_INDEX] =
        ((bytes[UUID_VERSION_INDEX].toInt() and UUID_VERSION_CLEAR_MASK) or UUID_VERSION_4_BITS)
            .toByte()
    bytes[UUID_VARIANT_INDEX] =
        ((bytes[UUID_VARIANT_INDEX].toInt() and UUID_VARIANT_CLEAR_MASK) or UUID_VARIANT_BITS)
            .toByte()
    var mostSigBits = 0L
    var leastSigBits = 0L
    for (index in 0 until DIGEST_LONG_BYTES) {
        mostSigBits =
            (mostSigBits shl DIGEST_LONG_BYTES) or (bytes[index].toLong() and BYTE_MASK_LONG)
    }
    for (index in DIGEST_LONG_BYTES until UUID_BYTES) {
        leastSigBits =
            (leastSigBits shl DIGEST_LONG_BYTES) or (bytes[index].toLong() and BYTE_MASK_LONG)
    }
    return UUID(mostSigBits, leastSigBits)
}

internal fun digestBytes(rootSeed: String, label: String): ByteArray =
    MessageDigest.getInstance("SHA-256").digest("$rootSeed|$label".encodeToByteArray())
