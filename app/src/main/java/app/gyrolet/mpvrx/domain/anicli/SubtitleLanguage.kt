package app.gyrolet.mpvrx.domain.anicli

import java.util.Locale

fun isEnglishSubtitleLanguage(value: String?): Boolean {
    val normalized = value?.trim()?.lowercase(Locale.ROOT) ?: return false
    if (normalized.isBlank()) return false
    val dashed = normalized.replace('_', '-')
    if (dashed == "en" || dashed == "eng" || dashed.startsWith("en-") || dashed.startsWith("eng-")) return true
    if (dashed.contains("english")) return true
    val tokens = dashed.split(Regex("[^a-z0-9]+")).filter { it.isNotBlank() }
    return tokens.any { it == "en" || it == "eng" || it == "english" }
}

fun isEnglishSubtitle(languageCode: String?, label: String?): Boolean =
    isEnglishSubtitleLanguage(languageCode) || isEnglishSubtitleLanguage(label)

fun AniCliSubtitleTrack.isEnglishSubtitle(): Boolean =
    isEnglishSubtitle(languageCode = languageCode, label = label)

fun Iterable<AniCliSubtitleTrack>.onlyEnglishSubtitles(): List<AniCliSubtitleTrack> =
    filter { it.isEnglishSubtitle() }.distinctBy { it.url }
