package app.gyrolet.mpvrx.domain.anicli.provider.moviebox

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
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import java.io.IOException

class MovieBoxAnimeProvider : BaseAnimeProvider() {

    private data class SubjectRef(
        val subjectId: String,
        val detailPath: String,
        val subjectType: Int,
    )

    private data class Dub(
        val subjectId: String,
        val detailPath: String,
        val code: String,
        val name: String,
        val original: Boolean,
    )

    private data class EpisodeAddress(
        val isMovie: Boolean,
        val season: Int,
        val episode: Int,
    )

    override val source: AnimeSource = AnimeSource.MOVIEBOX
    override val headers: Map<String, String> = mediaHeaders(detailPath = null)
    override val defaultReferer: String = MOVIEBOX_ORIGIN
    override val defaultUserAgent: String = MOVIEBOX_USER_AGENT

    private val client = MovieBoxClient()

    override suspend fun latest(params: SearchParams): SearchResults {
        val page = params.currentPage.coerceAtLeast(1)
        val pageLimit = params.pageLimit.coerceAtLeast(1)
        val data = client.getTrending(page = page - 1, perPage = pageLimit)
        return data.toSearchResults(
            itemKeys = arrayOf("subjectList", "items"),
            requestedPage = page,
            pageLimit = pageLimit,
            pageOffset = 1,
        )
    }

    override suspend fun search(params: SearchParams): SearchResults {
        val query = params.query.trim()
        if (query.isBlank()) return latest(params)
        val page = params.currentPage.coerceAtLeast(1)
        val pageLimit = params.pageLimit.coerceAtLeast(1)
        return client.search(query, page, pageLimit).toSearchResults(
            itemKeys = arrayOf("items", "subjects", "results"),
            requestedPage = page,
            pageLimit = pageLimit,
        )
    }

    override suspend fun get(params: AnimeParams): Anime {
        val reference = resolveReference(params.id, params.query)
        val detail = client.getDetail(reference.detailPath)
        val subject = detail.obj("subject") ?: throw IOException("MovieBox detail response has no subject")
        val resolvedReference = subject.toSubjectRef() ?: reference
        val title = subject.string("title") ?: subject.string("name") ?: params.query
        val poster = subject.obj("cover")?.string("url") ?: subject.string("poster")
        val isMovie = resolvedReference.subjectType != SUBJECT_TYPE_TV
        val episodeInfo = if (isMovie) {
            listOf(
                AnimeEpisodeInfo(
                    id = MOVIE_EPISODE_ID,
                    episode = MOVIE_EPISODE_LABEL,
                    title = title,
                    poster = poster,
                )
            )
        } else {
            val resource = detail.obj("resource")
                ?: throw IOException("MovieBox TV detail response has no resource data")
            val seasonsElement = resource.get("seasons")
            if (seasonsElement == null || seasonsElement.isJsonNull || !seasonsElement.isJsonArray) {
                throw IOException("MovieBox TV detail response has no valid seasons array")
            }
            resource.array("seasons")
                .mapNotNull { it.asObjectOrNull() }
                .sortedBy { it.int("se") ?: Int.MAX_VALUE }
                .flatMap { season ->
                    val seasonNumber = season.int("se") ?: return@flatMap emptyList()
                    val episodeCount = season.int("maxEp")?.coerceAtLeast(0) ?: 0
                    (1..episodeCount).map { episode ->
                        AnimeEpisodeInfo(
                            id = buildEpisodeId(seasonNumber, episode),
                            episode = "S${seasonNumber}E$episode",
                            title = "S${seasonNumber}E$episode",
                            poster = poster,
                            season = seasonNumber,
                        )
                    }
                }
        }
        val episodeLabels = episodeInfo.map { it.episode }
        return Anime(
            id = resolvedReference.encode(),
            title = title,
            episodes = AnimeEpisodes(sub = episodeLabels, raw = episodeLabels),
            type = if (isMovie) "Movie" else "TV Show",
            episodesInfo = episodeInfo,
            poster = poster,
            year = subject.string("releaseDate")?.take(4),
            description = subject.string("description") ?: detail.obj("metadata")?.string("description"),
            status = subject.string("status") ?: subject.string("releaseStatus"),
            country = subject.string("countryName") ?: subject.string("country"),
        )
    }

    override suspend fun episodeStreams(params: EpisodeStreamsParams): List<Server> = coroutineScope {
        val reference = resolveReference(params.animeId, params.query)
        val detail = client.getDetail(reference.detailPath)
        val subject = detail.obj("subject") ?: throw IOException("MovieBox detail response has no subject")
        val resolvedReference = subject.toSubjectRef() ?: reference
        val address = parseEpisodeAddress(
            episodeId = params.episodeId,
            episodeLabel = params.episode,
            subjectType = resolvedReference.subjectType,
        )
        val season = if (address.isMovie) 0 else address.season
        val episode = if (address.isMovie) 0 else address.episode
        val title = subject.string("title") ?: params.query

        val allCaptions = runCatching {
            client.getDownload(
                subjectId = resolvedReference.subjectId,
                detailPath = resolvedReference.detailPath,
                season = season,
                episode = episode,
            ).toCaptions()
        }.getOrDefault(emptyList())

        val results = subject.toDubs(resolvedReference).map { dub ->
            async {
                runCatching {
                    loadResourceServers(dub, title, address, allCaptions)
                }.onFailure { if (it is CancellationException) throw it }
            }
        }.awaitAll()
        val servers = results.flatMap { it.getOrDefault(emptyList()) }
            .distinctBy { server -> server.links.firstOrNull()?.link ?: server.name }
            .sortedWith(
                compareBy<Server> { languagePriority(it.audio.firstOrNull()) }
                    .thenByDescending { it.links.firstOrNull()?.quality?.filter(Char::isDigit)?.toIntOrNull() ?: 0 }
                    .thenBy { it.audio.firstOrNull().orEmpty().lowercase() }
            )
        if (servers.isEmpty()) results.firstNotNullOfOrNull { it.exceptionOrNull() }?.let { throw it }
        servers
    }

    private suspend fun resolveReference(id: String, query: String): SubjectRef {
        decodeSubjectRef(id)?.let { return it }
        val subjectId = id.substringBefore('|').trim()
        if (query.isNotBlank()) {
            var page = 1
            while (page <= LEGACY_LOOKUP_MAX_PAGES) {
                val data = client.search(query.trim(), page = page, perPage = LEGACY_LOOKUP_LIMIT)
                data.array("items").firstNotNullOfOrNull { item ->
                    item.asObjectOrNull()?.toSubjectRef()?.takeIf { it.subjectId == subjectId }
                }
                ?.let { return it }
                val pager = data.obj("pager")
                if (pager?.bool("hasMore") != true) break
                val nextPage = pager.int("nextPage") ?: (page + 1)
                if (nextPage <= page) break
                page = nextPage
            }
        }
        return client.getTrending(page = 0, perPage = LEGACY_LOOKUP_LIMIT)
            .array("subjectList")
            .firstNotNullOfOrNull { item ->
                item.asObjectOrNull()?.toSubjectRef()?.takeIf { it.subjectId == subjectId }
            }
            ?: throw IOException("MovieBox could not resolve legacy subject id $subjectId")
    }

    private fun JsonObject.toSearchResults(
        itemKeys: Array<String>,
        requestedPage: Int,
        pageLimit: Int,
        pageOffset: Int = 0,
    ): SearchResults {
        val results = array(*itemKeys)
            .mapNotNull { it.asObjectOrNull()?.toSearchResult() }
            .filter { it.hasPlayableType }
            .distinctBy { it.result.id }
            .map { it.result }
        val pager = obj("pager")
        return SearchResults(
            pageInfo = PageInfo(
                total = pager?.int("totalCount"),
                perPage = pager?.int("perPage") ?: pageLimit,
                currentPage = requestedPage,
                hasMore = pager?.bool("hasMore") ?: (results.size >= pageLimit),
                nextPage = pager?.int("nextPage")?.plus(pageOffset),
            ),
            results = results,
        )
    }

    private data class ParsedSearchResult(val result: SearchResult, val hasPlayableType: Boolean)

    private fun JsonObject.toSearchResult(): ParsedSearchResult? {
        val reference = toSubjectRef() ?: return null
        val title = string("title") ?: string("name") ?: return null
        val isMovie = reference.subjectType != SUBJECT_TYPE_TV
        val displayTitle = if (isMovie) title else title.replace(SEASON_SUFFIX, "").trim()
        val poster = obj("cover")?.string("url") ?: string("poster")
        val banner = obj("stills")?.string("url") ?: obj("backdrop")?.string("url") ?: poster
        val genres = string("genre")
            ?.split(',')
            ?.map { it.trim() }
            ?.filter { it.isNotBlank() }
            .orEmpty()
        val episodeLabels = if (isMovie) listOf(MOVIE_EPISODE_LABEL) else emptyList()
        return ParsedSearchResult(
            result = SearchResult(
                id = reference.encode(),
                title = displayTitle,
                episodes = AnimeEpisodes(sub = episodeLabels, raw = episodeLabels),
                mediaType = if (isMovie) "Movie" else "TV Show",
                score = float("imdbRatingValue") ?: float("rating"),
                status = string("status") ?: string("releaseStatus"),
                poster = poster,
                year = string("releaseDate")?.take(4),
                description = string("description") ?: string("overview"),
                bannerImage = banner,
                genres = genres,
                country = string("countryName") ?: string("country"),
            ),
            hasPlayableType = reference.subjectType in PLAYABLE_SUBJECT_TYPES && bool("hasResource") != false,
        )
    }

    private fun JsonObject.toDubs(reference: SubjectRef): List<Dub> {
        val parsed = array("dubs").mapNotNull { element ->
            val dub = element.asObjectOrNull() ?: return@mapNotNull null
            val subjectId = dub.string("subjectId") ?: return@mapNotNull null
            val detailPath = dub.string("detailPath") ?: return@mapNotNull null
            val rawName = dub.string("lanName") ?: dub.string("name").orEmpty()
            val original = dub.bool("original") == true || rawName.startsWith("Original", ignoreCase = true)
            Dub(
                subjectId = subjectId,
                detailPath = detailPath,
                code = dub.string("lanCode") ?: dub.string("code").orEmpty(),
                name = displayLanguage(
                    code = dub.string("lanCode") ?: dub.string("code").orEmpty(),
                    rawName = rawName,
                    original = original,
                ),
                original = original,
            )
        }
        val current = parsed.firstOrNull { it.subjectId == reference.subjectId } ?: Dub(
            subjectId = reference.subjectId,
            detailPath = reference.detailPath,
            code = "original",
            name = "Original",
            original = true,
        )
        return (listOf(current) + parsed)
            .distinctBy { it.subjectId to it.detailPath }
            .sortedWith(compareBy<Dub> { if (it.original) 0 else 1 }.thenBy { it.name.lowercase() })
    }

    private suspend fun loadResourceServers(
        dub: Dub,
        title: String,
        address: EpisodeAddress,
        captions: List<Subtitle>,
    ): List<Server> {
        val resources = mutableListOf<JsonObject>()
        var page = 1
        while (page <= MAX_RESOURCE_PAGES) {
            val data = client.getResourcePage(dub.subjectId, page, RESOURCE_PAGE_SIZE)
            resources += data.array("list").mapNotNull { it.asObjectOrNull() }
                .filter { resource ->
                    resource.int("se") == address.season && resource.int("ep") == address.episode
                }
            val pager = data.obj("pager")
            if (pager?.bool("hasMore") != true) break
            val nextPage = pager.int("nextPage") ?: (page + 1)
            if (nextPage <= page) break
            page = nextPage
        }

        val episodeSuffix = if (address.isMovie) "" else " - S${address.season}E${address.episode}"
        return resources.mapNotNull { resource ->
            val url = resource.string("resourceLink") ?: return@mapNotNull null
            val resolution = resource.int("resolution") ?: return@mapNotNull null
            val requestHeaders = mapOf(
                "User-Agent" to MOVIEBOX_USER_AGENT,
                "Accept" to "*/*",
            )
            Server(
                name = "${dub.name} - ${resolution}p",
                links = listOf(
                    EpisodeStream(
                        link = url,
                        title = "$title$episodeSuffix",
                        quality = "${resolution}p",
                        translationType = dub.code.ifBlank { dub.name },
                        audioLanguage = dub.name,
                        referer = "",
                        format = "mp4",
                        isHls = false,
                        isMp4 = true,
                        requestHeaders = requestHeaders,
                    )
                ),
                headers = requestHeaders,
                subtitles = captions,
                audio = listOf(dub.name),
            )
        }.distinctBy { it.links.first().link }
            .sortedByDescending { it.links.first().quality.filter(Char::isDigit).toIntOrNull() ?: 0 }
    }

    private fun JsonObject.toCaptions(): List<Subtitle> =
        array("captions").mapNotNull { element ->
            val caption = element.asObjectOrNull() ?: return@mapNotNull null
            val url = caption.string("url") ?: return@mapNotNull null
            val languageCode = caption.string("lan")
            val label = caption.string("lanName") ?: languageCode ?: "Subtitle"
            Subtitle(url = url, language = label, languageCode = languageCode)
        }.distinctBy { it.url }

    private fun parseEpisodeAddress(
        episodeId: String?,
        episodeLabel: String,
        subjectType: Int,
    ): EpisodeAddress {
        if (subjectType != SUBJECT_TYPE_TV || episodeId == MOVIE_EPISODE_ID ||
            episodeLabel.equals(MOVIE_EPISODE_LABEL, ignoreCase = true)
        ) {
            return EpisodeAddress(isMovie = true, season = 0, episode = 0)
        }
        val idMatch = EPISODE_ID.matchEntire(episodeId.orEmpty())
        val labelMatch = EPISODE_LABEL.find(episodeLabel)
        val season = idMatch?.groupValues?.getOrNull(1)?.toIntOrNull()
            ?: labelMatch?.groupValues?.getOrNull(1)?.toIntOrNull()
            ?: 1
        val episode = idMatch?.groupValues?.getOrNull(2)?.toIntOrNull()
            ?: labelMatch?.groupValues?.getOrNull(2)?.toIntOrNull()
            ?: episodeLabel.filter(Char::isDigit).toIntOrNull()
            ?: 1
        return EpisodeAddress(isMovie = false, season = season.coerceAtLeast(1), episode = episode.coerceAtLeast(1))
    }

    private fun JsonObject.toSubjectRef(): SubjectRef? {
        val subjectId = string("subjectId") ?: string("id") ?: return null
        val detailPath = string("detailPath") ?: return null
        val subjectType = int("subjectType") ?: int("type") ?: return null
        return SubjectRef(subjectId, detailPath, subjectType)
    }

    private fun SubjectRef.encode(): String = "$subjectId|$detailPath|$subjectType"

    private fun decodeSubjectRef(value: String): SubjectRef? {
        val parts = value.split('|', limit = 3)
        if (parts.size != 3) return null
        val subjectId = parts[0].takeIf { it.isNotBlank() } ?: return null
        val detailPath = parts[1].takeIf { it.isNotBlank() } ?: return null
        val subjectType = parts[2].toIntOrNull() ?: return null
        return SubjectRef(subjectId, detailPath, subjectType)
    }

    private fun buildEpisodeId(season: Int, episode: Int): String = "moviebox:$season:$episode"

    private fun languagePriority(language: String?): Int = when (language?.trim()?.lowercase()) {
        "original" -> 0
        "english" -> 1
        "hindi" -> 2
        else -> 3
    }

    private fun displayLanguage(code: String, rawName: String, original: Boolean): String {
        if (original) return "Original"
        return when (code.trim().lowercase()) {
            "en", "eng" -> "English"
            "hi", "hin" -> "Hindi"
            else -> rawName
                .replace(LANGUAGE_VARIANT_SUFFIX, "")
                .trim()
                .ifBlank { code.trim() }
                .ifBlank { "Unknown" }
                .replaceFirstChar { if (it.isLowerCase()) it.titlecase() else it.toString() }
        }
    }

    private fun mediaHeaders(detailPath: String?): Map<String, String> = mapOf(
        "Accept" to "*/*",
        "User-Agent" to MOVIEBOX_USER_AGENT,
        "Origin" to MOVIEBOX_ORIGIN,
        "Referer" to detailPath?.let { "$MOVIEBOX_ORIGIN/movies/$it" }.orEmpty().ifBlank { MOVIEBOX_ORIGIN },
    )

    private fun JsonObject.array(vararg names: String): List<JsonElement> =
        names.firstNotNullOfOrNull { name ->
            get(name)?.takeIf { !it.isJsonNull && it.isJsonArray }?.asJsonArray?.toList()
        }.orEmpty()

    private fun JsonObject.obj(name: String): JsonObject? =
        get(name)?.takeIf { !it.isJsonNull && it.isJsonObject }?.asJsonObject

    private fun JsonObject.string(name: String): String? =
        runCatching { get(name)?.takeIf { !it.isJsonNull && it.isJsonPrimitive }?.asString }
            .getOrNull()
            ?.takeIf { it.isNotBlank() }

    private fun JsonObject.int(name: String): Int? =
        runCatching { get(name)?.takeIf { !it.isJsonNull }?.asInt }.getOrNull()

    private fun JsonObject.float(name: String): Float? =
        runCatching { get(name)?.takeIf { !it.isJsonNull }?.asFloat }.getOrNull()

    private fun JsonObject.bool(name: String): Boolean? =
        runCatching { get(name)?.takeIf { !it.isJsonNull }?.asBoolean }.getOrNull()

    private fun JsonElement.asObjectOrNull(): JsonObject? =
        takeIf { !it.isJsonNull && it.isJsonObject }?.asJsonObject

    private companion object {
        const val SUBJECT_TYPE_MOVIE = 1
        const val SUBJECT_TYPE_TV = 2
        const val SUBJECT_TYPE_ANIME = 7
        const val MOVIE_EPISODE_ID = "moviebox:movie"
        const val MOVIE_EPISODE_LABEL = "Movie"
        const val MOVIEBOX_ORIGIN = "https://h5.aoneroom.com"
        const val MOVIEBOX_USER_AGENT =
            "Mozilla/5.0 (Linux; Android 13; Pixel 7) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/126.0.0.0 Mobile Safari/537.36"
        const val LEGACY_LOOKUP_LIMIT = 24
        const val LEGACY_LOOKUP_MAX_PAGES = 5
        const val RESOURCE_PAGE_SIZE = 20
        const val MAX_RESOURCE_PAGES = 20
        val PLAYABLE_SUBJECT_TYPES = setOf(SUBJECT_TYPE_MOVIE, SUBJECT_TYPE_TV, SUBJECT_TYPE_ANIME)
        val SEASON_SUFFIX = Regex("""\s+S\d+(?:\s*-\s*S\d+)?\s*$""", RegexOption.IGNORE_CASE)
        val EPISODE_ID = Regex("""moviebox:(\d+):(\d+)""", RegexOption.IGNORE_CASE)
        val EPISODE_LABEL = Regex("""S(\d+)E(\d+)""", RegexOption.IGNORE_CASE)
        val LANGUAGE_VARIANT_SUFFIX = Regex("""\s+(?:dub|sub)\s*$""", RegexOption.IGNORE_CASE)
    }
}
