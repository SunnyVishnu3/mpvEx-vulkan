package app.gyrolet.mpvrx.domain.anicli.provider.encdec

import app.gyrolet.mpvrx.domain.anicli.AnimeSource
import app.gyrolet.mpvrx.domain.anicli.provider.Anime
import app.gyrolet.mpvrx.domain.anicli.provider.AnimeEpisodeInfo
import app.gyrolet.mpvrx.domain.anicli.provider.AnimeEpisodes
import app.gyrolet.mpvrx.domain.anicli.provider.AnimeParams
import app.gyrolet.mpvrx.domain.anicli.provider.BaseAnimeProvider
import app.gyrolet.mpvrx.domain.anicli.provider.EpisodeStream
import app.gyrolet.mpvrx.domain.anicli.provider.EpisodeStreamsParams
import app.gyrolet.mpvrx.domain.anicli.provider.PageInfo
import app.gyrolet.mpvrx.domain.anicli.provider.SearchParams
import app.gyrolet.mpvrx.domain.anicli.provider.SearchResult
import app.gyrolet.mpvrx.domain.anicli.provider.SearchResults
import app.gyrolet.mpvrx.domain.anicli.provider.Server
import app.gyrolet.mpvrx.domain.anicli.provider.Subtitle
import com.google.gson.JsonElement
import com.google.gson.JsonObject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext
import java.io.IOException

class EncDecAnimeProvider : BaseAnimeProvider() {

    private data class MediaRef(
        val tmdbId: String,
        val imdbId: String?,
        val type: String,
        val year: String?,
    )

    private val client = EncDecClient()

    override val source = AnimeSource.ENCDEC
    override val headers = mapOf(
        "Accept" to "*/*",
        "User-Agent" to USER_AGENT,
    )
    override val defaultReferer = "https://enc-dec.app/"

    override suspend fun search(params: SearchParams): SearchResults = withContext(Dispatchers.IO) {
        val query = params.query.trim()
        val page = params.currentPage.coerceAtLeast(1)
        val pageSize = params.pageLimit.coerceAtLeast(1)
        if (query.isBlank()) return@withContext SearchResults(PageInfo(currentPage = page, hasMore = false), emptyList())

        val allResults = client.searchFlix(query).mapNotNull { entry -> entry.toSearchResult() }
        val start = ((page - 1) * pageSize).coerceAtMost(allResults.size)
        val end = (start + pageSize).coerceAtMost(allResults.size)
        SearchResults(
            pageInfo = PageInfo(
                total = allResults.size,
                perPage = pageSize,
                currentPage = page,
                hasMore = end < allResults.size,
                nextPage = (page + 1).takeIf { end < allResults.size },
            ),
            results = allResults.subList(start, end),
        )
    }

    override suspend fun get(params: AnimeParams): Anime = withContext(Dispatchers.IO) {
        val reference = decodeRef(params.id)
        val entry = client.findFlix(reference.tmdbId, reference.imdbId, reference.type)
            ?: throw IOException("EncDec database has no details for ${params.query}")
        val info = entry.obj("info") ?: throw IOException("EncDec database returned no media info")
        val title = info.string("title_en") ?: info.string("title") ?: params.query
        val episodes = entry.toEpisodes(reference.type, title)
        val labels = episodes.map { it.episode }
        Anime(
            id = reference.encode(),
            title = title,
            episodes = AnimeEpisodes(sub = labels, raw = labels),
            type = if (reference.type == "movie") "Movie" else "TV Show",
            episodesInfo = episodes,
            year = info.string("year"),
        )
    }

    override suspend fun episodeStreams(params: EpisodeStreamsParams): List<Server> = coroutineScope {
        val reference = decodeRef(params.animeId)
        val (season, episode) = if (reference.type == "movie") 0 to 0 else parseEpisode(params)
        val title = params.query.ifBlank { "Stream" }
        val jobs = listOf(
            async(Dispatchers.IO) {
                resolving { client.vidlink(reference.tmdbId, reference.type, season, episode) }
            },
            async(Dispatchers.IO) {
                resolving {
                    client.videasy(
                        reference.tmdbId,
                        reference.imdbId,
                        title,
                        reference.type,
                        reference.year,
                        season,
                        episode,
                    )
                }
            },
            async(Dispatchers.IO) {
                resolving { client.vidfast(reference.tmdbId, reference.type, season, episode) }
            },
            async(Dispatchers.IO) {
                resolving {
                    client.lordflix(
                        reference.tmdbId,
                        reference.imdbId,
                        title,
                        reference.type,
                        reference.year,
                        season,
                        episode,
                    )
                }
            },
        )
        val streams = jobs.awaitAll().flatten()
            .distinctBy { it.url }
            .sortedWith(
                compareBy<EncDecClient.ResolvedStream> { providerPriority(it.provider) }
                    .thenByDescending { qualityNumber(it.quality) }
            )
        if (streams.isEmpty()) throw IOException("No streams found from EncDec providers")
        streams.map { it.toServer(title) }
    }

    private inline fun resolving(block: () -> List<EncDecClient.ResolvedStream>): List<EncDecClient.ResolvedStream> =
        try {
            block()
        } catch (exception: CancellationException) {
            throw exception
        } catch (_: Exception) {
            emptyList()
        }

    private fun JsonObject.toSearchResult(): SearchResult? {
        val info = obj("info") ?: return null
        val tmdbId = info.string("tmdb_id") ?: return null
        val title = info.string("title_en") ?: info.string("title") ?: return null
        val type = info.string("type")?.lowercase().let { if (it == "movie") "movie" else "tv" }
        val reference = MediaRef(tmdbId, info.string("imdb_id"), type, info.string("year"))
        val labels = toEpisodes(type, title).map { it.episode }
        return SearchResult(
            id = reference.encode(),
            title = title,
            episodes = AnimeEpisodes(sub = labels, raw = labels),
            mediaType = if (type == "movie") "Movie" else "TV Show",
            year = reference.year,
        )
    }

    private fun JsonObject.toEpisodes(type: String, title: String): List<AnimeEpisodeInfo> {
        if (type == "movie") {
            return listOf(AnimeEpisodeInfo(id = MOVIE_EPISODE_ID, episode = "Movie", title = title))
        }
        val episodes = obj("episodes") ?: return emptyList()
        return episodes.entrySet().mapNotNull season@{ (seasonKey, seasonValue) ->
            val seasonNumber = seasonKey.toIntOrNull() ?: return@season null
            seasonValue.asObjectOrNull()?.entrySet()?.mapNotNull episode@{ (episodeKey, episodeValue) ->
                val episodeNumber = episodeKey.toIntOrNull() ?: return@episode null
                AnimeEpisodeInfo(
                    id = "encdec:$seasonNumber:$episodeNumber",
                    episode = "S${seasonNumber}E$episodeNumber",
                    title = episodeValue.asObjectOrNull()?.string("title") ?: "Episode $episodeNumber",
                    season = seasonNumber,
                )
            }.orEmpty()
        }.flatten().sortedWith(compareBy<AnimeEpisodeInfo> { it.season }.thenBy { episodeNumber(it.episode) })
    }

    private fun parseEpisode(params: EpisodeStreamsParams): Pair<Int, Int> {
        val encoded = EPISODE_ID.matchEntire(params.episodeId.orEmpty())
        val label = EPISODE_LABEL.find(params.episode)
        val season = encoded?.groupValues?.getOrNull(1)?.toIntOrNull()
            ?: label?.groupValues?.getOrNull(1)?.toIntOrNull()
            ?: 1
        val episode = encoded?.groupValues?.getOrNull(2)?.toIntOrNull()
            ?: label?.groupValues?.getOrNull(2)?.toIntOrNull()
            ?: 1
        return season.coerceAtLeast(1) to episode.coerceAtLeast(1)
    }

    private fun EncDecClient.ResolvedStream.toServer(title: String): Server {
        val requestHeaders = mapOf(
            "User-Agent" to USER_AGENT,
            "Referer" to "$referer/",
            "Origin" to referer,
        )
        val isHls = url.substringBefore('?').endsWith(".m3u8", ignoreCase = true)
        val isMp4 = url.substringBefore('?').endsWith(".mp4", ignoreCase = true)
        return Server(
            name = "$provider - $quality",
            links = listOf(
                EpisodeStream(
                    link = url,
                    title = title,
                    quality = quality,
                    translationType = "sub",
                    audioLanguage = "English",
                    referer = "$referer/",
                    format = when {
                        isHls -> "hls"
                        isMp4 -> "mp4"
                        else -> null
                    },
                    isHls = isHls,
                    isMp4 = isMp4,
                    requestHeaders = requestHeaders,
                )
            ),
            headers = requestHeaders,
            subtitles = subtitles.map { Subtitle(it.url, it.language, it.languageCode) },
            audio = listOf("English"),
        )
    }

    private fun MediaRef.encode(): String = listOf(tmdbId, imdbId.orEmpty(), type, year.orEmpty()).joinToString("|")

    private fun decodeRef(value: String): MediaRef {
        val parts = value.split('|', limit = 4)
        val tmdbId = parts.getOrNull(0)?.takeIf { it.isNotBlank() }
            ?: throw IOException("Invalid EncDec media ID")
        return MediaRef(
            tmdbId = tmdbId,
            imdbId = parts.getOrNull(1)?.takeIf { it.isNotBlank() },
            type = parts.getOrNull(2)?.takeIf { it == "movie" || it == "tv" } ?: "tv",
            year = parts.getOrNull(3)?.takeIf { it.isNotBlank() },
        )
    }

    private fun providerPriority(provider: String): Int = when {
        provider.startsWith("Vidlink") -> 0
        provider.startsWith("Videasy") -> 1
        provider.startsWith("Vidfast") -> 2
        provider.startsWith("Lordflix") -> 3
        else -> 4
    }

    private fun qualityNumber(value: String): Int = value.filter(Char::isDigit).toIntOrNull() ?: 0
    private fun episodeNumber(value: String): Int = EPISODE_LABEL.find(value)?.groupValues?.getOrNull(2)?.toIntOrNull() ?: 0
    private fun JsonElement.asObjectOrNull(): JsonObject? = takeIf { isJsonObject }?.asJsonObject
    private fun JsonObject.obj(name: String): JsonObject? = get(name)?.asObjectOrNull()
    private fun JsonObject.string(name: String): String? =
        runCatching { get(name)?.takeIf { it.isJsonPrimitive }?.asString }.getOrNull()?.takeIf { it.isNotBlank() }

    private companion object {
        const val MOVIE_EPISODE_ID = "encdec:movie"
        const val USER_AGENT =
            "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/137.0.0.0 Safari/537.36"
        val EPISODE_ID = Regex("""encdec:(\d+):(\d+)""", RegexOption.IGNORE_CASE)
        val EPISODE_LABEL = Regex("""S(\d+)E(\d+)""", RegexOption.IGNORE_CASE)
    }
}
