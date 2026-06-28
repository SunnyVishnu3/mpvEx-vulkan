package app.gyrolet.mpvrx.domain.anicli.provider.moviebox

import com.google.gson.JsonObject
import com.google.gson.JsonParser
import java.nio.charset.StandardCharsets
import java.util.Base64

enum class MovieBoxVideoKind { MOVIE, SERIES }

data class MovieBoxServerSelection(
    val title: String,
    val subjectId: String,
    val dubSubjectId: String,
    val videoType: MovieBoxVideoKind,
    val dubCode: String,
    val dubName: String,
    val resolution: Int,
    val resourceId: String,
    val season: Int,
    val episode: Int,
    val releaseDate: String? = null,
)

object MovieBoxServerCodec {
    fun encode(selection: MovieBoxServerSelection): String {
        val json = JsonObject().apply {
            addProperty("title", selection.title)
            addProperty("subjectId", selection.subjectId)
            addProperty("dubSubjectId", selection.dubSubjectId)
            addProperty("videoType", selection.videoType.name)
            addProperty("dubCode", selection.dubCode)
            addProperty("dubName", selection.dubName)
            addProperty("resolution", selection.resolution)
            addProperty("resourceId", selection.resourceId)
            addProperty("season", selection.season)
            addProperty("episode", selection.episode)
            addProperty("releaseDate", selection.releaseDate)
        }
        return Base64.getUrlEncoder().withoutPadding().encodeToString(json.toString().toByteArray(StandardCharsets.UTF_8))
    }

    fun decode(encoded: String): MovieBoxServerSelection {
        val decoded = String(Base64.getUrlDecoder().decode(encoded), StandardCharsets.UTF_8)
        val json = JsonParser.parseString(decoded).asJsonObject
        return MovieBoxServerSelection(
            title = json.get("title")?.asString.orEmpty(),
            subjectId = json.get("subjectId")?.asString.orEmpty(),
            dubSubjectId = json.get("dubSubjectId")?.asString.orEmpty(),
            videoType = MovieBoxVideoKind.valueOf(json.get("videoType")?.asString.orEmpty()),
            dubCode = json.get("dubCode")?.asString.orEmpty(),
            dubName = json.get("dubName")?.asString.orEmpty(),
            resolution = json.get("resolution")?.asInt ?: 0,
            resourceId = json.get("resourceId")?.asString.orEmpty(),
            season = json.get("season")?.asInt ?: 0,
            episode = json.get("episode")?.asInt ?: 0,
            releaseDate = json.get("releaseDate")?.takeIf { !it.isJsonNull }?.asString?.takeIf { it.isNotBlank() },
        )
    }
}

data class MovieBoxSeasonIndex(val season: Int, val episodes: Int)
data class MovieBoxEpisodePageRequest(val page: Int, val offset: Int, val limit: Int)
data class MovieBoxEpisodeRequestPlan(val requests: List<MovieBoxEpisodePageRequest>)

object MovieBoxEpisodePager {
    fun buildRequestPlan(seasons: List<MovieBoxSeasonIndex>, startSeason: Int, startEpisode: Int, limit: Int, perPage: Int): MovieBoxEpisodeRequestPlan {
        if (limit <= 0 || perPage <= 0) return MovieBoxEpisodeRequestPlan(emptyList())
        val absoluteIndex = buildAbsoluteEpisodeIndex(seasons, startSeason, startEpisode)
        var remaining = limit
        var globalOffset = absoluteIndex
        val requests = mutableListOf<MovieBoxEpisodePageRequest>()
        while (remaining > 0) {
            val page = (globalOffset / perPage) + 1
            val offsetInPage = globalOffset % perPage
            val requestLimit = minOf(remaining, perPage - offsetInPage)
            requests += MovieBoxEpisodePageRequest(page = page, offset = offsetInPage, limit = requestLimit)
            remaining -= requestLimit
            globalOffset += requestLimit
        }
        return MovieBoxEpisodeRequestPlan(requests)
    }

    private fun buildAbsoluteEpisodeIndex(seasons: List<MovieBoxSeasonIndex>, startSeason: Int, startEpisode: Int): Int =
        seasons.filter { it.season < startSeason }.sumOf { it.episodes } + (startEpisode - 1).coerceAtLeast(0)
}

data class MovieBoxServerPresentation(val dubName: String, val dubCode: String, val resolution: Int) {
    fun sortKey(): String {
        val isOriginal = dubName.equals("Original", ignoreCase = true)
        val dubBucket = if (isOriginal) "0" else "1"
        val normalizedDub = dubName.trim().lowercase().ifBlank { dubCode.trim().lowercase() }
        val invertedResolution = (9999 - resolution.coerceAtLeast(0)).toString().padStart(4, '0')
        return "$dubBucket|$normalizedDub|$invertedResolution"
    }
}
