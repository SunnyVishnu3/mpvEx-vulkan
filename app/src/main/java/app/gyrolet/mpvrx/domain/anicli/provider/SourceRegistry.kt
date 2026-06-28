package app.gyrolet.mpvrx.domain.anicli.provider

import app.gyrolet.mpvrx.domain.anicli.AnimeSource

class SourceRegistry(
    providers: List<BaseAnimeProvider>,
) {
    private val map: Map<AnimeSource, BaseAnimeProvider> = providers.associateBy { it.source }

    fun get(source: AnimeSource): BaseAnimeProvider =
        map[source] ?: error("No BaseAnimeProvider registered for source: $source")

    fun getOrNull(source: AnimeSource): BaseAnimeProvider? = map[source]

    fun allProviders(): List<BaseAnimeProvider> = map.values.toList()

    fun defaultRefererFor(source: AnimeSource): String =
        map[source]?.defaultReferer ?: "https://google.com"

    fun defaultUserAgentFor(source: AnimeSource): String =
        map[source]?.defaultUserAgent
            ?: "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/121.0.0.0 Safari/537.36"

    companion object {
        private val SOURCE_DISPLAY_ORDER = listOf(AnimeSource.MOVIEBOX)

        fun orderedSources(sources: Iterable<AnimeSource>): List<AnimeSource> {
            val orderIndex = SOURCE_DISPLAY_ORDER.withIndex().associate { it.value to it.index }
            return sources.distinct().sortedBy { orderIndex[it] ?: Int.MAX_VALUE }
        }
    }
}
