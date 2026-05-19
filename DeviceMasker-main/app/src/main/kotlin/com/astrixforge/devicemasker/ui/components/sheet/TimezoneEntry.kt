package com.astrixforge.devicemasker.ui.components.sheet

import java.util.Locale
import java.util.TimeZone

/** Data class representing a timezone entry for the picker. */
data class TimezoneEntry(
    val id: String,
    val displayName: String,
    val offset: String,
    val region: String,
) {
    companion object {
        /** All available timezones grouped and sorted. */
        val ALL: List<TimezoneEntry> by lazy {
            TimeZone.getAvailableIDs()
                .filter { it.contains("/") }
                .map { id ->
                    val tz = TimeZone.getTimeZone(id)
                    val offsetMinutes = tz.rawOffset / MILLIS_PER_MINUTE
                    val hours = offsetMinutes / MINUTES_PER_HOUR
                    val minutes = kotlin.math.abs(offsetMinutes % MINUTES_PER_HOUR)
                    val offsetStr =
                        String.format(
                            Locale.US,
                            "GMT%s%02d:%02d",
                            if (hours >= 0) "+" else "",
                            hours,
                            minutes,
                        )
                    val region = id.substringBefore("/")
                    val city = id.substringAfter("/").replace("_", " ")
                    TimezoneEntry(id = id, displayName = city, offset = offsetStr, region = region)
                }
                .sortedWith(compareBy({ it.region }, { it.displayName }))
        }

        /** Search timezones by query (matches id, display name, or offset). */
        fun search(query: String): List<TimezoneEntry> {
            if (query.isBlank()) return ALL
            val lowerQuery = query.lowercase()
            return ALL.filter { tz ->
                tz.id.lowercase().contains(lowerQuery) ||
                    tz.displayName.lowercase().contains(lowerQuery) ||
                    tz.offset.lowercase().contains(lowerQuery) ||
                    tz.region.lowercase().contains(lowerQuery)
            }
        }
    }
}

private const val MILLIS_PER_MINUTE = 60_000
private const val MINUTES_PER_HOUR = 60
