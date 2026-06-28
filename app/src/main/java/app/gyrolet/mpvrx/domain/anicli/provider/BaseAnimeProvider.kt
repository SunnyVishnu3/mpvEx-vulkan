package app.gyrolet.mpvrx.domain.anicli.provider

import app.gyrolet.mpvrx.domain.anicli.AnimeSource

data class SearchParams(
    val query: String,
    val currentPage: Int = 1,
    val pageLimit: Int = 20,
    val sortBy: String = "relevance",
    val translationType: String = "sub",
)

data class EpisodeStreamsParams(
    val query: String,
    val animeId: String,
    val episode: String,
    val episodeId: String? = null,
    val translationType: String = "sub",
    val server: String? = null,
    val quality: String = "720",
)

data class AnimeParams(
    val id: String,
    val query: String,
    val translationType: String = "sub",
)

data class PageInfo(
    val total: Int? = null,
    val perPage: Int? = null,
    val currentPage: Int? = null,
)

data class AnimeEpisodes(
    val sub: List<String>,
    val dub: List<String> = emptyList(),
    val raw: List<String> = emptyList(),
)

data class SearchResult(
    val id: String,
    val title: String,
    val episodes: AnimeEpisodes,
    val otherTitles: List<String> = emptyList(),
    val mediaType: String? = null,
    val score: Float? = null,
    val status: String? = null,
    val season: String? = null,
    val poster: String? = null,
    val year: String? = null,
    val description: String? = null,
    val bannerImage: String? = null,
    val genres: List<String> = emptyList(),
)

data class SearchResults(
    val pageInfo: PageInfo,
    val results: List<SearchResult>,
)

data class AnimeEpisodeInfo(
    val id: String,
    val episode: String,
    val title: String? = null,
    val poster: String? = null,
    val duration: String? = null,
)

data class Anime(
    val id: String,
    val title: String,
    val episodes: AnimeEpisodes,
    val type: String? = null,
    val episodesInfo: List<AnimeEpisodeInfo>? = null,
    val poster: String? = null,
    val year: String? = null,
    val description: String? = null,
)

data class EpisodeStream(
    val link: String,
    val title: String? = null,
    val quality: String = "720",
    val translationType: String = "sub",
    val audioLanguage: String? = null,
    val referer: String? = null,
    val format: String? = null,
    val isHls: Boolean? = null,
    val isMp4: Boolean? = null,
    val requestHeaders: Map<String, String> = emptyMap(),
)

data class Subtitle(
    val url: String,
    val language: String? = null,
)

data class Server(
    val name: String,
    val links: List<EpisodeStream>,
    val episodeTitle: String? = null,
    val headers: Map<String, String> = emptyMap(),
    val subtitles: List<Subtitle> = emptyList(),
    val audio: List<String> = emptyList(),
)

abstract class BaseAnimeProvider {
    abstract val source: AnimeSource
    abstract val headers: Map<String, String>
    open val defaultReferer: String get() = headers["Referer"] ?: "https://google.com"
    open val defaultUserAgent: String
        get() = headers["User-Agent"]
            ?: "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/121.0.0.0 Safari/537.36"

    abstract suspend fun search(params: SearchParams): SearchResults?
    open suspend fun latest(params: SearchParams): SearchResults? = null
    abstract suspend fun get(params: AnimeParams): Anime?
    abstract suspend fun episodeStreams(params: EpisodeStreamsParams): List<Server>?
}
