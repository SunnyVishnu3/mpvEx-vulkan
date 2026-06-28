package app.gyrolet.mpvrx.domain.anicli.provider.moviebox

import app.gyrolet.mpvrx.domain.anicli.AnimeSource
import app.gyrolet.mpvrx.domain.anicli.isEnglishSubtitle
import app.gyrolet.mpvrx.domain.anicli.provider.Anime
import app.gyrolet.mpvrx.domain.anicli.provider.AnimeEpisodes
import app.gyrolet.mpvrx.domain.anicli.provider.AnimeEpisodeInfo
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
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope

class MovieBoxAnimeProvider : BaseAnimeProvider() {

    private data class MovieBoxDub(val subjectId: String, val code: String, val name: String, val original: Boolean)
    private data class MovieBoxSeasonAvailability(val season: Int, val maxEpisode: Int, val resolutions: List<MovieBoxResolutionAvailability>)
    private data class MovieBoxResolutionAvailability(val resolution: Int, val episodeCount: Int)
    private data class MovieBoxResourceEntry(val subjectId: String, val title: String, val resourceId: String, val resourceLink: String, val resolution: Int, val season: Int, val episode: Int)
    private data class MovieBoxPager(val page: Int, val hasMore: Boolean, val nextPage: Int)
    private data class EpisodeAddress(val isMovie: Boolean, val season: Int, val episode: Int)

    override val source: AnimeSource = AnimeSource.MOVIEBOX
    override val headers: Map<String, String> = mapOf("User-Agent" to MOVIEBOX_USER_AGENT, "Referer" to MOVIEBOX_REFERER)
    override val defaultReferer: String = MOVIEBOX_REFERER
    override val defaultUserAgent: String = MOVIEBOX_USER_AGENT
    private val client = MovieBoxClient()

    override suspend fun latest(params: SearchParams): SearchResults {
        val results = client.getHome(page = params.currentPage, tabId = 1).toHomeResults()
            .distinctBy { it.id }.take(params.pageLimit)
        return SearchResults(pageInfo = PageInfo(currentPage = params.currentPage, perPage = params.pageLimit), results = results)
    }

    override suspend fun search(params: SearchParams): SearchResults {
        val results = if (params.query.trim().isBlank()) {
            client.getHome(page = params.currentPage).toHomeResults()
        } else {
            client.search(keyword = params.query.trim(), page = params.currentPage, perPage = params.pageLimit).toSearchResults()
        }.distinctBy { it.id }.take(params.pageLimit)
        return SearchResults(pageInfo = PageInfo(currentPage = params.currentPage, perPage = params.pageLimit), results = results)
    }

    override suspend fun get(params: AnimeParams): Anime {
        val subject = client.getSubject(params.id)
        val seasonInfo = runCatching { client.getSeasonInfo(params.id) }.getOrNull()
        val isMovie = subject.isMovieSubject(seasonInfo)
        val title = subject.string("title") ?: subject.string("name") ?: params.query
        val poster = subject.obj("cover")?.string("url") ?: subject.string("poster") ?: subject.string("cover")
        val year = subject.string("releaseDate")?.take(4)
        val episodesInfo = if (isMovie) {
            listOf(AnimeEpisodeInfo(id = MOVIE_EPISODE_ID, episode = MOVIE_EPISODE_LABEL, title = title, poster = poster))
        } else {
            seasonInfo?.toSeasonAvailabilities().orEmpty().flatMap { season ->
                (1..season.maxEpisode.coerceAtLeast(0)).map { episode ->
                    AnimeEpisodeInfo(id = buildEpisodeId(season.season, episode), episode = "S${season.season}E$episode", title = "S${season.season}E$episode", poster = poster)
                }
            }
        }
        return Anime(id = params.id, title = title, episodes = AnimeEpisodes(sub = episodesInfo.map { it.episode }, raw = episodesInfo.map { it.episode }), type = if (isMovie) "Movie" else "TV Show", episodesInfo = episodesInfo, poster = poster, year = year)
    }

    override suspend fun episodeStreams(params: EpisodeStreamsParams): List<Server> {
        val address = parseEpisodeAddress(params.episodeId, params.episode)
        return if (address.isMovie) getMovieServers(params.animeId, params.query)
        else getEpisodeServers(params.animeId, address.season, address.episode, params.query)
    }

    private suspend fun getMovieServers(subjectId: String, title: String): List<Server> = coroutineScope {
        val detail = client.getSubject(subjectId)
        val dubs = detail.toDubs(subjectId)
        dubs.map { dub -> async {
            val dubDetail = if (dub.subjectId == subjectId) detail else client.getSubject(dub.subjectId)
            dubDetail.toMovieResources(dub.subjectId).sortedByDescending { it.resolution }.mapNotNull { it.toServer(dub, title, 0, 0) }
        } }.awaitAll().flatten().distinctBy { it.links.firstOrNull()?.link ?: it.name }.sortedBy { it.movieBoxSortKey() }
    }

    private suspend fun getEpisodeServers(subjectId: String, seasonNumber: Int, episodeNumber: Int, title: String): List<Server> = coroutineScope {
        val detail = client.getSubject(subjectId)
        val dubs = detail.toDubs(subjectId)
        dubs.map { dub -> async {
            val seasonAvailabilities = client.getSeasonInfo(dub.subjectId).toSeasonAvailabilities()
            val targetSeason = seasonAvailabilities.firstOrNull { it.season == seasonNumber } ?: return@async emptyList()
            coroutineScope {
                targetSeason.resolutions.filter { it.episodeCount >= episodeNumber }.sortedByDescending { it.resolution }.map { availability -> async {
                    val resource = findEpisodeResource(dub.subjectId, availability.resolution, seasonNumber, episodeNumber, seasonAvailabilities) ?: return@async null
                    resource.toServer(dub, title, seasonNumber, episodeNumber)
                } }.awaitAll().filterNotNull()
            }
        } }.awaitAll().flatten().distinctBy { it.links.firstOrNull()?.link ?: it.name }.sortedBy { it.movieBoxSortKey() }
    }

    private suspend fun MovieBoxResourceEntry.toServer(dub: MovieBoxDub, title: String, season: Int, episode: Int): Server? {
        if (resourceLink.isBlank()) return null
        val subtitles = runCatching { loadSubtitles(subjectId, resourceId) }.getOrDefault(emptyList())
        return Server(
            name = "${dub.name} - ${resolution}p",
            links = listOf(EpisodeStream(
                link = resourceLink, title = "$title${if (season > 0 && episode > 0) " - S${season}E$episode" else ""}",
                quality = "${resolution}p", translationType = dub.code.ifBlank { dub.name },
                audioLanguage = dub.name, referer = MOVIEBOX_REFERER,
                format = when { resourceLink.contains(".m3u8", ignoreCase = true) -> "hls"; resourceLink.contains(".mp4", ignoreCase = true) -> "mp4"; else -> null },
                isHls = resourceLink.contains(".m3u8", ignoreCase = true), isMp4 = resourceLink.contains(".mp4", ignoreCase = true),
            )),
            headers = headers, subtitles = subtitles, audio = listOf(dub.name),
        )
    }

    private suspend fun findEpisodeResource(subjectId: String, resolution: Int, seasonNumber: Int, episodeNumber: Int, seasonAvailabilities: List<MovieBoxSeasonAvailability>? = null): MovieBoxResourceEntry? {
        val resolvedSeasons = seasonAvailabilities ?: client.getSeasonInfo(subjectId).toSeasonAvailabilities()
        val resolutionSeasons = resolvedSeasons.toSeasonIndexesForResolution(resolution)
        val targetSeason = resolutionSeasons.firstOrNull { it.season == seasonNumber } ?: return null
        if (targetSeason.episodes < episodeNumber) return null
        MovieBoxEpisodePager.buildRequestPlan(resolutionSeasons, seasonNumber, episodeNumber, 1, RESOURCE_PER_PAGE).requests.firstOrNull()?.page?.let { predictedPage ->
            client.getResourcePage(subjectId, resolution, predictedPage, RESOURCE_PER_PAGE).findEpisodeInResourcePage(subjectId, seasonNumber, episodeNumber)?.let { return it }
        }
        var page = 1
        while (true) {
            client.getResourcePage(subjectId, resolution, page, RESOURCE_PER_PAGE).findEpisodeInResourcePage(subjectId, seasonNumber, episodeNumber)?.let { return it }
            val pager = client.getResourcePage(subjectId, resolution, page, RESOURCE_PER_PAGE).toPager()
            if (!pager.hasMore || pager.nextPage <= page) break
            page = pager.nextPage
        }
        return null
    }

    private suspend fun loadSubtitles(subjectId: String, resourceId: String): List<Subtitle> =
        client.getCaptions(subjectId, resourceId).array("extCaptions").mapNotNull { caption ->
            val entry = caption.asObjectOrNull() ?: return@mapNotNull null
            val url = entry.string("url") ?: return@mapNotNull null
            val languageCode = entry.string("lan"); val label = entry.string("lanName") ?: languageCode ?: "Subtitle"
            if (!isEnglishSubtitle(languageCode = languageCode, label = label)) return@mapNotNull null
            Subtitle(url = url, language = label)
        }.distinctBy { it.url }

    private fun JsonObject.toHomeResults(): List<SearchResult> = array("items").mapNotNull { it.asObjectOrNull() }.flatMap { section ->
        when (section.string("type")) {
            "BANNER" -> section.array("banners", "banner").mapNotNull { it.asObjectOrNull()?.obj("subject")?.toSearchResult() }
            "SUBJECTS_MOVIE" -> section.array("subjects").mapNotNull { it.asObjectOrNull()?.toSearchResult() }
            "CUSTOM" -> section.obj("customData")?.array("items")?.mapNotNull { it.asObjectOrNull()?.obj("subject")?.toSearchResult() }.orEmpty()
            else -> listOfNotNull(section.obj("subject")?.toSearchResult() ?: section.toSearchResult())
        }
    }

    private fun JsonObject.toSearchResults(): List<SearchResult> =
        array("items", "subjects", "list", "results", "movies")
            .flatMap { it.asObjectOrNull()?.toSearchResultCandidates().orEmpty() }
            .ifEmpty { toSearchResultCandidates() }
            .distinctBy { it.id }

    private fun JsonObject.toSearchResult(): SearchResult? {
        val subjectType = int("subjectType") ?: int("type")
        if (subjectType != null && subjectType != SUBJECT_TYPE_MOVIE && subjectType != SUBJECT_TYPE_TV) return null
        val id = string("subjectId") ?: string("id") ?: return null
        val title = string("title") ?: string("name") ?: return null
        val poster = obj("cover")?.string("url") ?: string("poster") ?: string("cover")
        val banner = obj("stills")?.string("url") ?: obj("backdrop")?.string("url") ?: string("banner") ?: poster
        val releaseDate = string("releaseDate") ?: string("released")
        val isMovie = subjectType != SUBJECT_TYPE_TV
        val episodeLabels = if (isMovie) listOf(MOVIE_EPISODE_LABEL) else (1..(int("episodeCount") ?: int("maxEp") ?: 0).coerceAtLeast(0)).map { it.toString() }
        val genreString = string("genre")
        val parsedGenres = if (!genreString.isNullOrBlank()) {
            genreString.split(",").map { it.trim() }.filter { it.isNotBlank() }
        } else {
            array("genreList", "genres", "genre").mapNotNull { it.asObjectOrNull()?.string("name") ?: it.safeString() }
        }
        return SearchResult(id = id, title = title, episodes = AnimeEpisodes(sub = episodeLabels, raw = episodeLabels), mediaType = if (isMovie) "Movie" else "TV Show", score = float("imdbRatingValue") ?: float("rating"), poster = poster, year = releaseDate?.take(4), description = string("description") ?: string("overview"), bannerImage = banner, genres = parsedGenres)
    }

    private fun JsonObject.toSearchResultCandidates(): List<SearchResult> = buildList {
        obj("subject")?.toSearchResult()?.let(::add)
        obj("subjectInfo")?.toSearchResult()?.let(::add)
        obj("item")?.toSearchResult()?.let(::add)
        toSearchResult()?.let(::add)
    }

    private fun JsonObject.toDubs(fallbackSubjectId: String): List<MovieBoxDub> = array("dubs").mapNotNull { element ->
        val dub = element.asObjectOrNull() ?: return@mapNotNull null
        val subjectId = dub.string("subjectId") ?: return@mapNotNull null
        val rawName = dub.string("lanName") ?: dub.string("name") ?: ""
        val isOriginal = dub.bool("original") == true || rawName.startsWith("Original", ignoreCase = true)
        MovieBoxDub(subjectId = subjectId, code = dub.string("lanCode") ?: dub.string("code") ?: "", name = normalizeDubName(rawName, isOriginal), original = isOriginal)
    }.distinctBy { it.subjectId to it.code }.ifEmpty { listOf(MovieBoxDub(subjectId = fallbackSubjectId, code = "original", name = "Original", original = true)) }

    private fun JsonObject.toMovieResources(subjectId: String): List<MovieBoxResourceEntry> = array("resourceDetectors").mapNotNull { it.asObjectOrNull() }.flatMap { detector ->
        detector.array("resolutionList").mapNotNull { it.asObjectOrNull()?.toResourceEntry(subjectId) }
    }.distinctBy { it.resolution }

    private fun JsonObject.toSeasonAvailabilities(): List<MovieBoxSeasonAvailability> = array("seasons").mapNotNull { season ->
        val seasonObject = season.asObjectOrNull() ?: return@mapNotNull null
        val seasonNumber = seasonObject.int("se") ?: return@mapNotNull null
        val maxEpisode = seasonObject.int("maxEp") ?: 0
        MovieBoxSeasonAvailability(season = seasonNumber, maxEpisode = maxEpisode, resolutions = seasonObject.array("resolutions").mapNotNull {
            val r = it.asObjectOrNull() ?: return@mapNotNull null; MovieBoxResolutionAvailability(resolution = r.int("resolution") ?: return@mapNotNull null, episodeCount = r.int("epNum") ?: maxEpisode)
        })
    }.sortedBy { it.season }

    private fun JsonObject.toPager(): MovieBoxPager { val p = obj("pager"); val page = p?.int("page") ?: 1; return MovieBoxPager(page = page, hasMore = p?.bool("hasMore") == true, nextPage = p?.int("nextPage") ?: (page + 1)) }

    private fun JsonObject.findEpisodeInResourcePage(subjectId: String, seasonNumber: Int, episodeNumber: Int): MovieBoxResourceEntry? = array("list").mapNotNull { it.asObjectOrNull()?.toResourceEntry(subjectId) }.firstOrNull { it.season == seasonNumber && it.episode == episodeNumber }

    private fun JsonObject.toResourceEntry(subjectId: String): MovieBoxResourceEntry? {
        val resourceId = string("resourceId") ?: return null
        val resourceLink = string("resourceLink") ?: return null
        return MovieBoxResourceEntry(subjectId = subjectId, title = string("title").orEmpty(), resourceId = resourceId, resourceLink = resourceLink, resolution = int("resolution") ?: 0, season = int("se") ?: 0, episode = int("ep") ?: 0)
    }

    private fun List<MovieBoxSeasonAvailability>.toSeasonIndexesForResolution(resolution: Int): List<MovieBoxSeasonIndex> = mapNotNull { season ->
        val episodeCount = season.resolutions.firstOrNull { it.resolution == resolution }?.episodeCount ?: return@mapNotNull null
        MovieBoxSeasonIndex(season = season.season, episodes = episodeCount.coerceAtMost(season.maxEpisode.takeIf { it > 0 } ?: episodeCount))
    }

    private fun JsonObject.isMovieSubject(seasonInfo: JsonObject?): Boolean {
        val type = int("subjectType") ?: int("type")
        if (type == SUBJECT_TYPE_MOVIE) return true
        if (type == SUBJECT_TYPE_TV) return false
        return toMovieResources(string("subjectId").orEmpty()).isNotEmpty() || seasonInfo?.toSeasonAvailabilities().orEmpty().isEmpty()
    }

    private fun Server.movieBoxSortKey(): String {
        val resolution = links.firstOrNull()?.quality?.filter(Char::isDigit)?.toIntOrNull() ?: 0
        val dubName = audio.firstOrNull().orEmpty()
        val isOriginal = dubName.equals("Original", ignoreCase = true)
        return "${if (isOriginal) "0" else "1"}|${dubName.trim().lowercase()}|${(9999 - resolution.coerceAtLeast(0)).toString().padStart(4, '0')}"
    }

    private fun parseEpisodeAddress(episodeId: String?, episodeLabel: String): EpisodeAddress {
        if (episodeId == MOVIE_EPISODE_ID || episodeLabel.equals(MOVIE_EPISODE_LABEL, ignoreCase = true)) return EpisodeAddress(isMovie = true, season = 0, episode = 0)
        val idParts = episodeId.orEmpty().split(":")
        if (idParts.size == 3 && idParts[0] == "moviebox") return EpisodeAddress(isMovie = false, season = idParts[1].toIntOrNull() ?: 1, episode = idParts[2].toIntOrNull() ?: 1)
        val match = Regex("""S(\d+)E(\d+)""", RegexOption.IGNORE_CASE).find(episodeLabel)
        return EpisodeAddress(isMovie = false, season = match?.groupValues?.getOrNull(1)?.toIntOrNull() ?: 1, episode = match?.groupValues?.getOrNull(2)?.toIntOrNull() ?: episodeLabel.filter(Char::isDigit).toIntOrNull() ?: 1)
    }

    private fun buildEpisodeId(season: Int, episode: Int): String = "moviebox:$season:$episode"
    private fun normalizeDubName(rawName: String, original: Boolean): String = when { original -> "Original"; rawName.isBlank() -> "Dub"; else -> rawName }

    private fun JsonObject.array(vararg names: String): List<JsonElement> = names.firstNotNullOfOrNull { name -> get(name)?.takeIf { !it.isJsonNull && it.isJsonArray }?.asJsonArray?.toList() }.orEmpty()
    private fun JsonObject.obj(name: String): JsonObject? = get(name)?.takeIf { !it.isJsonNull && it.isJsonObject }?.asJsonObject
    private fun JsonObject.string(name: String): String? = get(name)?.safeString()?.takeIf { it.isNotBlank() }
    private fun JsonObject.int(name: String): Int? = runCatching { get(name)?.takeIf { !it.isJsonNull }?.asInt }.getOrNull()
    private fun JsonObject.float(name: String): Float? = runCatching { get(name)?.takeIf { !it.isJsonNull }?.asFloat }.getOrNull()
    private fun JsonObject.bool(name: String): Boolean? = runCatching { get(name)?.takeIf { !it.isJsonNull }?.asBoolean }.getOrNull()
    private fun JsonElement.asObjectOrNull(): JsonObject? = takeIf { !it.isJsonNull && it.isJsonObject }?.asJsonObject
    private fun JsonElement.safeString(): String? = runCatching { takeIf { !it.isJsonNull && it.isJsonPrimitive }?.asString }.getOrNull()

    private companion object {
        const val SUBJECT_TYPE_MOVIE = 1; const val SUBJECT_TYPE_TV = 2
        const val RESOURCE_PER_PAGE = 20; const val MOVIE_EPISODE_ID = "moviebox:movie"
        const val MOVIE_EPISODE_LABEL = "Movie"
        const val MOVIEBOX_REFERER = "https://moviebox.ph/"
        const val MOVIEBOX_USER_AGENT = "com.community.oneroom/50020046 (Linux; U; Android 13; en_US; 23078RKD5C; Build/TQ2A.230405.003; Cronet/135.0.7012.3)"
    }
}
