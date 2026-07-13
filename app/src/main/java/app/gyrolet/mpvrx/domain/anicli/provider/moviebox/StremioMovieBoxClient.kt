package app.gyrolet.mpvrx.domain.anicli.provider.moviebox

import app.gyrolet.mpvrx.domain.anicli.provider.EpisodeStream
import app.gyrolet.mpvrx.domain.anicli.provider.Server
import com.google.gson.JsonElement
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.IOException

internal class StremioMovieBoxClient {
    private val client = OkHttpClient.Builder()
        .followRedirects(true)
        .followSslRedirects(true)
        .build()

    suspend fun getStreams(
        manifestUrl: String,
        title: String,
        year: String?,
        type: String,
        season: Int,
        episode: Int,
    ): List<Server> = withContext(Dispatchers.IO) {
        val manifest = normalizeManifestUrl(manifestUrl)
        validateManifest(manifest, type)
        val imdbId = resolveImdbId(title, year, type) ?: return@withContext emptyList()
        val videoId = if (type == "series") "$imdbId:$season:$episode" else imdbId
        val response = requestJson(buildStreamUrl(manifest, type, videoId))

        response.array("streams").mapNotNull { element ->
            val stream = element.asObjectOrNull() ?: return@mapNotNull null
            val url = stream.string("url") ?: return@mapNotNull null
            val name = stream.string("name") ?: "MovieBox"
            val description = stream.string("title") ?: name
            val requestHeaders = stream.obj("behaviorHints")
                ?.obj("proxyHeaders")
                ?.obj("request")
                ?.entrySet()
                ?.mapNotNull { (key, value) -> value.safeString()?.let { key to it } }
                ?.toMap()
                .orEmpty()
            val quality = QUALITY_REGEX.find(description)?.groupValues?.getOrNull(1)?.let { "${it}p" } ?: "Auto"
            val audio = AUDIO_REGEX.find(description)?.groupValues?.getOrNull(1)?.trim()?.takeIf { it.isNotBlank() }
            Server(
                name = description.lineSequence().firstOrNull()?.takeIf { it.isNotBlank() } ?: name,
                links = listOf(
                    EpisodeStream(
                        link = url,
                        title = description,
                        quality = quality,
                        translationType = audio ?: "Stremio",
                        audioLanguage = audio,
                        referer = requestHeaders["Referer"],
                        format = when {
                            url.contains(".m3u8", ignoreCase = true) -> "hls"
                            url.contains(".mp4", ignoreCase = true) -> "mp4"
                            else -> null
                        },
                        isHls = url.contains(".m3u8", ignoreCase = true),
                        isMp4 = url.contains(".mp4", ignoreCase = true),
                        requestHeaders = requestHeaders,
                    )
                ),
                headers = requestHeaders,
                audio = listOfNotNull(audio),
            )
        }.distinctBy { it.links.first().link }
    }

    private fun normalizeManifestUrl(value: String): HttpUrl {
        val trimmed = value.trim()
        val candidate = when {
            trimmed.endsWith("manifest.json", ignoreCase = true) -> trimmed
            trimmed.endsWith('/') -> "${trimmed}manifest.json"
            else -> "$trimmed/manifest.json"
        }
        return candidate.toHttpUrlOrNull()
            ?: throw IOException("Enter a valid HTTP(S) Stremio manifest URL")
    }

    private fun validateManifest(url: HttpUrl, type: String) {
        val manifest = requestJson(url)
        val resources = manifest.array("resources").mapNotNull { resource ->
            resource.safeString() ?: resource.asObjectOrNull()?.string("name")
        }
        val types = manifest.array("types").mapNotNull { it.safeString() }
        if ("stream" !in resources || type !in types) {
            throw IOException("The Stremio manifest does not provide $type streams")
        }
    }

    private fun resolveImdbId(title: String, year: String?, type: String): String? {
        val url = CINEMETA_BASE.newBuilder()
            .addPathSegment("catalog")
            .addPathSegment(type)
            .addPathSegment("top")
            .addPathSegment("search=$title.json")
            .build()
        val metas = requestJson(url).array("metas").mapNotNull { it.asObjectOrNull() }
        val normalizedTitle = title.normalizedTitle()
        return metas
            .sortedWith(
                compareByDescending<JsonObject> { it.string("name")?.normalizedTitle() == normalizedTitle }
                    .thenByDescending { candidate -> year != null && candidate.string("releaseInfo")?.contains(year) == true }
            )
            .firstNotNullOfOrNull { it.string("imdb_id") ?: it.string("id")?.takeIf(IMDB_ID_REGEX::matches) }
    }

    private fun buildStreamUrl(manifestUrl: HttpUrl, type: String, id: String): HttpUrl {
        val builder = manifestUrl.newBuilder().query(null).fragment(null)
        if (builder.build().pathSegments.lastOrNull().equals("manifest.json", ignoreCase = true)) {
            builder.removePathSegment(builder.build().pathSize - 1)
        }
        return builder
            .addPathSegment("stream")
            .addPathSegment(type)
            .addPathSegment("$id.json")
            .build()
    }

    private fun requestJson(url: HttpUrl): JsonObject {
        val request = Request.Builder().url(url).header("Accept", "application/json").build()
        client.newCall(request).execute().use { response ->
            val body = response.body.string()
            if (!response.isSuccessful) {
                throw IOException("Stremio addon request failed with HTTP ${response.code}")
            }
            return runCatching { JsonParser.parseString(body).asJsonObject }
                .getOrElse { throw IOException("Stremio addon returned invalid JSON", it) }
        }
    }

    private fun JsonObject.array(name: String): List<JsonElement> =
        get(name)?.takeIf { it.isJsonArray }?.asJsonArray?.toList().orEmpty()

    private fun JsonObject.obj(name: String): JsonObject? =
        get(name)?.takeIf { it.isJsonObject }?.asJsonObject

    private fun JsonObject.string(name: String): String? = get(name)?.safeString()?.takeIf { it.isNotBlank() }
    private fun JsonElement.asObjectOrNull(): JsonObject? = takeIf { isJsonObject }?.asJsonObject
    private fun JsonElement.safeString(): String? = runCatching { takeIf { isJsonPrimitive }?.asString }.getOrNull()
    private fun String.normalizedTitle(): String = lowercase().filter(Char::isLetterOrDigit)

    private companion object {
        val CINEMETA_BASE = "https://v3-cinemeta.strem.io/".toHttpUrlOrNull()!!
        val IMDB_ID_REGEX = Regex("tt\\d{7,9}", RegexOption.IGNORE_CASE)
        val QUALITY_REGEX = Regex("(\\d{3,4})p", RegexOption.IGNORE_CASE)
        val AUDIO_REGEX = Regex("Audio\\s*:?\\s*([^|\\n]+)", RegexOption.IGNORE_CASE)
    }
}
