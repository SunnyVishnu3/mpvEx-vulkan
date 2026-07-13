package app.gyrolet.mpvrx.domain.anicli.provider.encdec

import com.google.gson.Gson
import com.google.gson.JsonArray
import com.google.gson.JsonElement
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.IOException
import java.nio.charset.StandardCharsets
import java.security.MessageDigest
import java.util.Base64

internal class EncDecClient {

    data class ResolvedStream(
        val provider: String,
        val url: String,
        val quality: String = "Auto",
        val referer: String,
        val subtitles: List<ResolvedSubtitle> = emptyList(),
    )

    data class ResolvedSubtitle(
        val url: String,
        val language: String,
        val languageCode: String? = null,
    )

    private val gson = Gson()
    private val client = OkHttpClient.Builder()
        .followRedirects(true)
        .followSslRedirects(true)
        .build()

    fun searchFlix(query: String): List<JsonObject> {
        val url = "$FLIX_DB/search".toHttpUrl().newBuilder()
            .addQueryParameter("query", query)
            .build()
        return parseJson(request(url.toString())).asArrayOrNull()
            ?.mapNotNull { it.asObjectOrNull() }
            .orEmpty()
    }

    fun findFlix(tmdbId: String, imdbId: String?, type: String): JsonObject? {
        val byTmdb = "$FLIX_DB/find".toHttpUrl().newBuilder()
            .addQueryParameter("tmdb_id", tmdbId)
            .addQueryParameter("type", type)
            .build()
        parseJson(request(byTmdb.toString())).asArrayOrNull()
            ?.firstOrNull()?.asObjectOrNull()?.let { return it }
        if (imdbId.isNullOrBlank()) return null
        val byImdb = "$FLIX_DB/find".toHttpUrl().newBuilder()
            .addQueryParameter("imdb_id", imdbId)
            .addQueryParameter("type", type)
            .build()
        return parseJson(request(byImdb.toString())).asArrayOrNull()
            ?.firstOrNull()?.asObjectOrNull()
    }

    fun vidlink(tmdbId: String, type: String, season: Int, episode: Int): List<ResolvedStream> {
        val encrypted = encDecGet("enc-vidlink", "text" to tmdbId).asStringOrNull()
            ?: throw IOException("EncDec vidlink encryption returned no string")
        val mediaPath = if (type == "movie") {
            "movie/$encrypted"
        } else {
            "tv/$encrypted/$season/$episode"
        }
        val data = parseJson(
            request(
                url = "$VIDLINK_API/$mediaPath",
                headers = sourceHeaders(VIDLINK_ORIGIN),
            )
        ).asObjectOrNull() ?: throw IOException("Vidlink returned invalid JSON")
        val stream = data.obj("stream") ?: return emptyList()
        val subtitles = stream.array("captions").mapNotNull caption@{ caption ->
            val item = caption.asObjectOrNull() ?: return@caption null
            val url = item.string("url") ?: return@caption null
            ResolvedSubtitle(
                url = url,
                language = item.string("language") ?: item.string("label") ?: "Subtitle",
                languageCode = item.string("languageCode") ?: item.string("lang"),
            )
        }
        return stream.obj("qualities")?.entrySet().orEmpty().mapNotNull quality@{ (quality, value) ->
            val item = value.asObjectOrNull() ?: return@quality null
            val url = item.string("url") ?: return@quality null
            ResolvedStream("Vidlink", url, qualityLabel(quality), VIDLINK_ORIGIN, subtitles)
        }
    }

    fun videasy(
        tmdbId: String,
        imdbId: String?,
        title: String,
        type: String,
        year: String?,
        season: Int,
        episode: Int,
    ): List<ResolvedStream> {
        val headers = sourceHeaders(VIDEASY_ORIGIN)
        val seedUrl = "$WINGS_API/seed".toHttpUrl().newBuilder()
            .addQueryParameter("mediaId", tmdbId)
            .build()
        val seed = parseJson(request(seedUrl.toString(), headers)).asObjectOrNull()?.string("seed")
            ?: throw IOException("Videasy returned no seed")
        val encodedTitle = encodeComponent(encodeComponent(title))
        val routes = if (type == "movie") VIDEASY_MOVIE_ROUTES else VIDEASY_TV_ROUTES

        return routes.flatMap { (name, route) ->
            runCatching {
                val query = buildList {
                    add("title=$encodedTitle")
                    add("mediaType=$type")
                    add("year=${encodeComponent(year.orEmpty())}")
                    if (type != "movie") {
                        add("episodeId=$episode")
                        add("seasonId=$season")
                    }
                    add("tmdbId=${encodeComponent(tmdbId)}")
                    add("imdbId=${encodeComponent(imdbId.orEmpty())}")
                    add("enc=2")
                    add("seed=${encodeComponent(seed)}")
                }.joinToString("&")
                val encrypted = request("$WINGS_API/$route/sources-with-title?$query", headers)
                val result = encDecPost(
                    "dec-videasy",
                    mapOf("text" to encrypted, "id" to tmdbId, "seed" to seed),
                ).asObjectOrNull() ?: return@runCatching emptyList()
                val subtitles = result.array("subtitles").mapNotNull subtitle@{ subtitle ->
                    val item = subtitle.asObjectOrNull() ?: return@subtitle null
                    val url = item.string("url") ?: return@subtitle null
                    ResolvedSubtitle(
                        url = url,
                        language = item.string("language") ?: item.string("lang") ?: "Subtitle",
                        languageCode = item.string("lang"),
                    )
                }
                result.array("sources").mapNotNull source@{ source ->
                    val item = source.asObjectOrNull() ?: return@source null
                    val url = item.string("url") ?: return@source null
                    ResolvedStream(
                        provider = "Videasy $name",
                        url = url,
                        quality = qualityLabel(item.string("quality")),
                        referer = VIDEASY_ORIGIN,
                        subtitles = subtitles,
                    )
                }
            }.getOrDefault(emptyList())
        }.distinctBy { it.url }
    }

    fun vidfast(tmdbId: String, type: String, season: Int, episode: Int): List<ResolvedStream> {
        val pageUrl = if (type == "movie") {
            "$VIDFAST_ORIGIN/movie/$tmdbId"
        } else {
            "$VIDFAST_ORIGIN/tv/$tmdbId/$season/$episode"
        }
        val page = request(pageUrl, sourceHeaders(VIDFAST_ORIGIN))
        val text = VIDFAST_TEXT.find(page)?.groupValues?.getOrNull(1)
            ?: throw IOException("Vidfast page did not expose server data")
        val parts = encDecGet("enc-vidfast", "text" to text).asObjectOrNull()
            ?: throw IOException("EncDec vidfast encryption returned no object")
        val serversUrl = parts.string("servers") ?: throw IOException("Vidfast returned no servers URL")
        val streamUrl = parts.string("stream") ?: throw IOException("Vidfast returned no stream URL")
        val token = parts.string("token") ?: throw IOException("Vidfast returned no CSRF token")
        val headers = sourceHeaders(VIDFAST_ORIGIN) + mapOf(
            "X-Requested-With" to "XMLHttpRequest",
            "X-CSRF-Token" to token,
        )
        val encryptedServers = request(serversUrl, headers, method = "POST", body = "")
        val servers = encDecPost("dec-vidfast", mapOf("text" to encryptedServers)).asArrayOrNull()
            ?: throw IOException("EncDec vidfast servers result was not an array")

        return servers.mapNotNull server@{ serverElement ->
            val server = serverElement.asObjectOrNull() ?: return@server null
            val data = server.string("data") ?: return@server null
            runCatching {
                val encryptedStream = request(
                    "$streamUrl/${encodePathSegment(data)}",
                    headers,
                    method = "POST",
                    body = "",
                )
                val result = encDecPost("dec-vidfast", mapOf("text" to encryptedStream)).asObjectOrNull()
                    ?: return@runCatching null
                val url = result.string("url") ?: return@runCatching null
                val subtitles = result.array("tracks").mapNotNull track@{ track ->
                    val item = track.asObjectOrNull() ?: return@track null
                    val trackUrl = item.string("url") ?: item.string("file") ?: return@track null
                    ResolvedSubtitle(
                        url = trackUrl,
                        language = item.string("label") ?: item.string("language") ?: "Subtitle",
                        languageCode = item.string("lang"),
                    )
                }
                ResolvedStream(
                    provider = "Vidfast ${server.string("name").orEmpty()}".trim(),
                    url = url,
                    referer = VIDFAST_ORIGIN,
                    subtitles = subtitles,
                )
            }.getOrNull()
        }.distinctBy { it.url }
    }

    fun lordflix(
        tmdbId: String,
        imdbId: String?,
        title: String,
        type: String,
        year: String?,
        season: Int,
        episode: Int,
    ): List<ResolvedStream> {
        val headers = sourceHeaders(LORDFLIX_ORIGIN)
        val serverData = parseJson(request("$LORDFLIX_API/servers", headers))
        val servers = when {
            serverData.isJsonArray -> serverData.asJsonArray.mapNotNull { it.asStringOrNull() }
            serverData.isJsonObject -> serverData.asJsonObject.array("servers").mapNotNull { item ->
                item.asStringOrNull() ?: item.asObjectOrNull()?.string("name")
            }
            else -> emptyList()
        }
        return servers.flatMap { server ->
            runCatching {
                val catalogUrl = LORDFLIX_API.toHttpUrl().newBuilder()
                    .addQueryParameter("title", title)
                    .addQueryParameter("type", if (type == "movie") "movie" else "series")
                    .addQueryParameter("year", year.orEmpty())
                    .addQueryParameter("imdb", imdbId.orEmpty())
                    .addQueryParameter("tmdb", tmdbId)
                    .addQueryParameter("server", server)
                    .apply {
                        if (type != "movie") {
                            addQueryParameter("season", season.toString())
                            addQueryParameter("episode", episode.toString())
                        }
                    }
                    .build()
                val requestData = encDecGet("enc-lordflix", "url" to catalogUrl.toString()).asObjectOrNull()
                    ?: return@runCatching emptyList()
                val requestUrl = requestData.string("url") ?: return@runCatching emptyList()
                val attest = solveLordflixChallenge(headers)
                val encrypted = request(requestUrl, headers + ("x-attest" to attest))
                val result = encDecPost("dec-lordflix", mapOf("text" to encrypted)).asObjectOrNull()
                    ?: return@runCatching emptyList()
                result.array("stream").mapNotNull stream@{ stream ->
                    val item = stream.asObjectOrNull() ?: return@stream null
                    val url = item.string("playlist") ?: item.string("url") ?: return@stream null
                    val subtitles = item.array("captions").mapNotNull caption@{ caption ->
                        val sub = caption.asObjectOrNull() ?: return@caption null
                        val subUrl = sub.string("url") ?: return@caption null
                        ResolvedSubtitle(
                            url = subUrl,
                            language = sub.string("language") ?: sub.string("label") ?: "Subtitle",
                            languageCode = sub.string("lang"),
                        )
                    }
                    ResolvedStream("Lordflix $server", url, referer = LORDFLIX_ORIGIN, subtitles = subtitles)
                }
            }.getOrDefault(emptyList())
        }.distinctBy { it.url }
    }

    private fun solveLordflixChallenge(headers: Map<String, String>): String {
        val challenge = parseJson(request("$LORDFLIX_API/challenge", headers)).asObjectOrNull()
            ?: throw IOException("Lordflix returned an invalid challenge")
        val expected = challenge.string("challenge") ?: throw IOException("Lordflix challenge has no hash")
        val salt = challenge.string("salt") ?: throw IOException("Lordflix challenge has no salt")
        val maximum = challenge.int("maxnumber") ?: throw IOException("Lordflix challenge has no limit")
        val digest = MessageDigest.getInstance("SHA-256")
        val number = (0..maximum).firstOrNull { candidate ->
            val hash = digest.digest("$salt$candidate".toByteArray(StandardCharsets.UTF_8)).toHex()
            hash.equals(expected, ignoreCase = true)
        } ?: throw IOException("Lordflix challenge could not be solved")
        val payload = JsonObject().apply {
            addProperty("algorithm", challenge.string("algorithm") ?: "SHA-256")
            addProperty("challenge", expected)
            addProperty("number", number)
            addProperty("salt", salt)
            addProperty("signature", challenge.string("signature").orEmpty())
        }
        return Base64.getEncoder().encodeToString(gson.toJson(payload).toByteArray(StandardCharsets.UTF_8))
    }

    private fun encDecGet(path: String, vararg query: Pair<String, String>): JsonElement {
        val url = "$ENC_DEC_API/$path".toHttpUrl().newBuilder().apply {
            query.forEach { (key, value) -> addQueryParameter(key, value) }
        }.build()
        return validateEnvelope(parseJson(request(url.toString())), path)
    }

    private fun encDecPost(path: String, body: Map<String, String>): JsonElement =
        validateEnvelope(
            parseJson(
                request(
                    url = "$ENC_DEC_API/$path",
                    method = "POST",
                    body = gson.toJson(body),
                    headers = mapOf("Content-Type" to "application/json"),
                )
            ),
            path,
        )

    private fun validateEnvelope(element: JsonElement, path: String): JsonElement {
        val root = element.asObjectOrNull() ?: throw IOException("EncDec $path returned invalid JSON")
        val status = root.int("status") ?: -1
        if (status != 200) {
            throw IOException("EncDec $path failed: ${root.string("error") ?: "status $status"}")
        }
        return root.get("result")?.takeUnless { it.isJsonNull }
            ?: throw IOException("EncDec $path returned no result")
    }

    private fun request(
        url: String,
        headers: Map<String, String> = emptyMap(),
        method: String = "GET",
        body: String? = null,
    ): String {
        val builder = Request.Builder().url(url).header("User-Agent", USER_AGENT)
        headers.forEach { (key, value) -> builder.header(key, value) }
        val requestBody = body?.toRequestBody(headers["Content-Type"]?.toMediaType())
        builder.method(method, requestBody)
        client.newCall(builder.build()).execute().use { response ->
            val responseBody = response.body.string()
            if (!response.isSuccessful) {
                throw IOException("HTTP ${response.code} for $url: ${responseBody.take(240)}")
            }
            return responseBody
        }
    }

    private fun sourceHeaders(origin: String): Map<String, String> = mapOf(
        "Accept" to "*/*",
        "Origin" to origin,
        "Referer" to "$origin/",
    )

    private fun parseJson(value: String): JsonElement = runCatching { JsonParser.parseString(value) }
        .getOrElse { throw IOException("Remote service returned invalid JSON", it) }

    private fun qualityLabel(value: String?): String = value
        ?.trim()
        ?.takeIf { it.isNotBlank() }
        ?.let { if (it.any(Char::isDigit) && !it.endsWith("p", ignoreCase = true)) "${it}p" else it }
        ?: "Auto"

    private fun encodeComponent(value: String): String = java.net.URLEncoder.encode(value, "UTF-8")
        .replace("+", "%20")

    private fun encodePathSegment(value: String): String = value.toHttpUrlPathSegment()

    private fun String.toHttpUrlPathSegment(): String = "https://placeholder.invalid/".toHttpUrl()
        .newBuilder().addPathSegment(this).build().encodedPathSegments.last()

    private fun ByteArray.toHex(): String = joinToString("") { "%02x".format(it.toInt() and 0xff) }

    private fun JsonElement.asObjectOrNull(): JsonObject? = takeIf { isJsonObject }?.asJsonObject
    private fun JsonElement.asArrayOrNull(): JsonArray? = takeIf { isJsonArray }?.asJsonArray
    private fun JsonElement.asStringOrNull(): String? =
        runCatching { takeIf { isJsonPrimitive }?.asString }.getOrNull()?.takeIf { it.isNotBlank() }
    private fun JsonObject.obj(name: String): JsonObject? = get(name)?.asObjectOrNull()
    private fun JsonObject.array(name: String): List<JsonElement> = get(name)?.asArrayOrNull()?.toList().orEmpty()
    private fun JsonObject.string(name: String): String? = get(name)?.asStringOrNull()
    private fun JsonObject.int(name: String): Int? = runCatching { get(name)?.asInt }.getOrNull()

    private companion object {
        const val ENC_DEC_API = "https://enc-dec.app/api"
        const val FLIX_DB = "https://enc-dec.app/db/flix"
        const val VIDLINK_API = "https://vidlink.pro/api/b"
        const val VIDLINK_ORIGIN = "https://vidlink.pro"
        const val VIDEASY_ORIGIN = "https://player.videasy.to"
        const val WINGS_API = "https://api.wingsdatabase.com"
        const val VIDFAST_ORIGIN = "https://vidfast.vc"
        const val LORDFLIX_API = "https://hongkong.lordflix.club"
        const val LORDFLIX_ORIGIN = "https://lordflix.org"
        const val USER_AGENT =
            "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/137.0.0.0 Safari/537.36"
        val VIDFAST_TEXT = Regex("""\\"en\\":\\"(.*?)\\"""")
        val VIDEASY_MOVIE_ROUTES = listOf(
            "Jett" to "jett",
            "Yoru" to "cdn",
            "Tejo" to "tejo",
            "Neon" to "neon2",
            "Sage" to "ym",
            "Cypher" to "downloader2",
            "Breach" to "m4uhd",
            "Vyse" to "hdmovie",
        )
        val VIDEASY_TV_ROUTES = VIDEASY_MOVIE_ROUTES
    }
}
