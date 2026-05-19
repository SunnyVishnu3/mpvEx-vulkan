package com.astrixforge.devicemasker.common.diagnostics

import java.security.MessageDigest

enum class RedactionMode {
    REDACTED,
    UNREDACTED,
}

class DiagnosticRedactor(private val mode: RedactionMode) {
    fun redactValue(value: String): String {
        if (mode == RedactionMode.UNREDACTED) return value

        val trimmed = value.trim()
        return when {
            ICCID_REGEX.matches(trimmed) -> "[REDACTED_ICCID]"
            IMSI_REGEX.matches(trimmed) -> "[REDACTED_IMSI]"
            IMEI_REGEX.matches(trimmed) -> "[REDACTED_IMEI]"
            MAC_REGEX.matches(trimmed) -> "[REDACTED_MAC]"
            ANDROID_ID_REGEX.matches(trimmed) -> "[REDACTED_ANDROID_ID]"
            PHONE_REGEX.matches(trimmed) -> "[REDACTED_PHONE]"
            LOCATION_REGEX.matches(trimmed) -> "[REDACTED_LOCATION]"
            else -> value
        }
    }

    fun redactPackage(packageName: String): String {
        if (mode == RedactionMode.UNREDACTED) return packageName
        return "[PKG:${sha256Prefix(packageName)}]"
    }

    fun redactMessage(message: String): String {
        if (mode == RedactionMode.UNREDACTED) return message

        var redacted = message
        MESSAGE_PATTERNS.forEach { (regex, replacement) ->
            redacted = regex.replace(redacted, replacement)
        }
        return redacted
    }

    fun redactEvent(event: DiagnosticEvent): DiagnosticEvent {
        if (mode == RedactionMode.UNREDACTED) return event

        return event.copy(
            processName = event.processName?.let(::redactPackage),
            packageName = event.packageName?.let(::redactPackage),
            message = redactMessage(event.message),
            stacktrace = event.stacktrace.map(::redactMessage),
            extras = event.extras.mapValues { (_, value) -> redactValue(value) },
        )
    }

    private fun sha256Prefix(value: String): String {
        val bytes = MessageDigest.getInstance("SHA-256").digest(value.toByteArray(Charsets.UTF_8))
        return bytes.take(HASH_PREFIX_BYTES).joinToString(separator = "") { byte ->
            "%02x".format(byte)
        }
    }

    private companion object {
        private const val HASH_PREFIX_BYTES = 4

        private val ICCID_REGEX = Regex("""89\d{17,20}""")
        private val IMSI_REGEX = Regex("""31\d{13}""")
        private val IMEI_REGEX = Regex("""\d{15}""")
        private val MAC_REGEX = Regex("""(?i)(?:[0-9a-f]{2}:){5}[0-9a-f]{2}""")
        private val ANDROID_ID_REGEX = Regex("""(?i)[0-9a-f]{16}""")
        private val PHONE_REGEX = Regex("""\+?[1-9]\d{9,14}""")
        private val LOCATION_REGEX = Regex("""-?\d{1,3}\.\d+,\s*-?\d{1,3}\.\d+""")

        private val MESSAGE_PATTERNS =
            listOf(
                ICCID_REGEX to "[REDACTED_ICCID]",
                IMSI_REGEX to "[REDACTED_IMSI]",
                IMEI_REGEX to "[REDACTED_IMEI]",
                MAC_REGEX to "[REDACTED_MAC]",
                ANDROID_ID_REGEX to "[REDACTED_ANDROID_ID]",
                PHONE_REGEX to "[REDACTED_PHONE]",
                LOCATION_REGEX to "[REDACTED_LOCATION]",
            )
    }
}
