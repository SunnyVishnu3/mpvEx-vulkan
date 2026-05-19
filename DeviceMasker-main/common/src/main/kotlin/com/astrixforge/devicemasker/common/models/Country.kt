package com.astrixforge.devicemasker.common.models

import kotlinx.serialization.Serializable

/**
 * Country data for the country picker.
 *
 * @property iso ISO 3166-1 alpha-2 code (e.g., "IN", "US")
 * @property name Display name (e.g., "India", "United States")
 * @property emoji Flag emoji (e.g., "🇮🇳", "🇺🇸")
 * @property phoneCode Phone prefix without + (e.g., "91", "1")
 */
@Serializable
@Suppress("unused") // displayName is API for UI use
data class Country(val iso: String, val name: String, val emoji: String, val phoneCode: String) {
    /** Display string with emoji and name. */
    val displayName: String
        get() = "$emoji $name"

    companion object {
        /** All supported countries. Only includes countries that have carriers in our database. */
        val ALL: List<Country> =
            listOf(
                    // Asia
                    Country("IN", "India", "🇮🇳", "91"),
                    Country("CN", "China", "🇨🇳", "86"),
                    Country("JP", "Japan", "🇯🇵", "81"),
                    Country("KR", "South Korea", "🇰🇷", "82"),
                    Country("ID", "Indonesia", "🇮🇩", "62"),

                    // Middle East
                    Country("SA", "Saudi Arabia", "🇸🇦", "966"),
                    Country("AE", "UAE", "🇦🇪", "971"),

                    // North America
                    Country("US", "United States", "🇺🇸", "1"),
                    Country("CA", "Canada", "🇨🇦", "1"),
                    Country("MX", "Mexico", "🇲🇽", "52"),

                    // South America
                    Country("BR", "Brazil", "🇧🇷", "55"),

                    // Europe
                    Country("GB", "United Kingdom", "🇬🇧", "44"),
                    Country("DE", "Germany", "🇩🇪", "49"),
                    Country("FR", "France", "🇫🇷", "33"),
                    Country("RU", "Russia", "🇷🇺", "7"),

                    // Oceania
                    Country("AU", "Australia", "🇦🇺", "61"),
                )
                .sortedBy { it.name }

        /** Get country by ISO code. */
        fun getByIso(iso: String): Country? = ALL.find { it.iso.equals(iso, ignoreCase = true) }

        /** Search countries by name or code. */
        fun search(query: String): List<Country> {
            if (query.isBlank()) return ALL
            val lowerQuery = query.lowercase()
            return ALL.filter {
                it.name.lowercase().contains(lowerQuery) || it.iso.lowercase().contains(lowerQuery)
            }
        }
    }
}
