package app.gyrolet.mpvrx.domain.anicli.provider.moviebox

import com.google.gson.Gson
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.IOException
import java.util.concurrent.ConcurrentHashMap

class MovieBoxClient {

    private data class CacheEntry<T>(val value: T, val expiresAt: Long)

    private companion object {
        private val hosts = listOf(
            "https://api6.aoneroom.com", "https://api5.aoneroom.com",
            "https://api4.aoneroom.com", "https://api4sg.aoneroom.com",
            "https://api3.aoneroom.com", "https://api6sg.aoneroom.com",
            "https://api.inmoviebox.com",
        )
        private val retryStatusCodes = setOf(403, 407, 429, 500, 502, 503, 504)
        private const val userAgent = "com.community.oneroom/50020046 (Linux; U; Android 13; en_US; 23078RKD5C; Build/TQ2A.230405.003; Cronet/135.0.7012.3)"
        private const val clientInfo = "{\"package_name\":\"com.community.oneroom\",\"version_name\":\"3.0.03.0529.03\",\"version_code\":50020046,\"os\":\"android\",\"os_version\":\"13\",\"install_ch\":\"ps\",\"device_id\":\"1234567890abcdef1234567890abcdef\",\"install_store\":\"ps\",\"gaid\":\"11111111-1111-1111-1111-111111111111\",\"brand\":\"Redmi\",\"model\":\"23078RKD5C\",\"system_language\":\"en\",\"net\":\"NETWORK_WIFI\",\"region\":\"US\",\"timezone\":\"Asia/Kolkata\",\"sp_code\":\"40401\",\"X-Play-Mode\":\"2\"}"
        private const val homeTtlMs = 2 * 60 * 1000L
        private const val searchTtlMs = 60 * 1000L
        private const val subjectTtlMs = 10 * 60 * 1000L
        private const val seasonTtlMs = 10 * 60 * 1000L
        private const val resourceTtlMs = 60 * 1000L
        private const val captionsTtlMs = 60 * 1000L
    }

    private val gson = Gson()
    private val okHttpClient = OkHttpClient.Builder().followRedirects(true).followSslRedirects(true).build()
    private val hostMutex = Mutex()
    private var activeHost = hosts.first()
    private var runtimeToken: String? = null

    private val homeCache = ConcurrentHashMap<String, CacheEntry<JsonObject>>()
    private val searchCache = ConcurrentHashMap<String, CacheEntry<JsonObject>>()
    private val subjectCache = ConcurrentHashMap<String, CacheEntry<JsonObject>>()
    private val seasonCache = ConcurrentHashMap<String, CacheEntry<JsonObject>>()
    private val resourceCache = ConcurrentHashMap<String, CacheEntry<JsonObject>>()
    private val captionsCache = ConcurrentHashMap<String, CacheEntry<JsonObject>>()

    suspend fun getHome(page: Int = 1, tabId: Int = 0, version: String = ""): JsonObject {
        val cacheKey = "$page|$tabId|$version"
        return cached(homeCache, cacheKey, homeTtlMs) {
            requestJson("GET", "/wefeed-mobile-bff/tab-operating", linkedMapOf("page" to page, "tabId" to tabId, "version" to version))
        }
    }

    suspend fun search(keyword: String, page: Int, perPage: Int = 20): JsonObject {
        val cacheKey = "${keyword.lowercase()}|$page|$perPage"
        return cached(searchCache, cacheKey, searchTtlMs) {
            requestJson("POST", "/wefeed-mobile-bff/subject-api/search", body = gson.toJson(mapOf("keyword" to keyword, "page" to page, "perPage" to perPage, "subjectType" to 0)), contentType = "application/json; charset=utf-8")
        }
    }

    suspend fun getSubject(subjectId: String): JsonObject = cached(subjectCache, subjectId, subjectTtlMs) {
        requestJson("GET", "/wefeed-mobile-bff/subject-api/get", linkedMapOf("subjectId" to subjectId))
    }

    suspend fun getSeasonInfo(subjectId: String): JsonObject = cached(seasonCache, subjectId, seasonTtlMs) {
        requestJson("GET", "/wefeed-mobile-bff/subject-api/season-info", linkedMapOf("subjectId" to subjectId))
    }

    suspend fun getResourcePage(subjectId: String, resolution: Int, page: Int, perPage: Int = 20): JsonObject {
        val cacheKey = "$subjectId|$resolution|$page|$perPage"
        return cached(resourceCache, cacheKey, resourceTtlMs) {
            requestJson("GET", "/wefeed-mobile-bff/subject-api/resource", linkedMapOf("subjectId" to subjectId, "resolution" to resolution, "page" to page, "perPage" to perPage), includePlayMode = true)
        }
    }

    suspend fun getCaptions(subjectId: String, resourceId: String): JsonObject {
        val cacheKey = "$subjectId|$resourceId"
        return cached(captionsCache, cacheKey, captionsTtlMs) {
            requestJson("GET", "/wefeed-mobile-bff/subject-api/get-ext-captions", linkedMapOf("subjectId" to subjectId, "resourceId" to resourceId), includePlayMode = true)
        }
    }

    private suspend fun requestJson(method: String, path: String, queryParams: Map<String, Any?> = emptyMap(), body: String? = null, contentType: String = "application/json", includePlayMode: Boolean = false): JsonObject = withContext(Dispatchers.IO) {
        val orderedHosts = hostMutex.withLock { listOf(activeHost) + hosts.filterNot { it == activeHost } }
        var lastException: Exception? = null
        var lastResponseError: String? = null

        for (host in orderedHosts) {
            val url = buildUrl(host, path, queryParams)
            val request = Request.Builder()
                .url(url)
                .headers(okhttp3.Headers.headersOf(*MovieBoxSigning.buildSignedHeaders(
                    method = method, url = url, accept = "application/json", contentType = contentType,
                    body = body, timestampMs = System.currentTimeMillis(), authToken = runtimeToken,
                    clientInfo = clientInfo, userAgent = userAgent, includePlayMode = includePlayMode,
                ).flatMap { listOf(it.key, it.value) }.toTypedArray()))
                .method(method, if (method.equals("POST", ignoreCase = true)) (body ?: "").toRequestBody(contentType.toMediaType()) else null)
                .build()

            try {
                okHttpClient.newCall(request).execute().use { response ->
                    MovieBoxSigning.extractBearerToken(response.header("x-user"))?.let { runtimeToken = it }
                    val responseBody = response.body.string()
                    if (response.code in retryStatusCodes) { lastResponseError = "HTTP ${response.code}: ${response.message}"; return@use }
                    if (!response.isSuccessful) throw IOException("MovieBox request failed with ${response.code}: ${response.message} ${responseBody.take(240)}")
                    val root = JsonParser.parseString(responseBody).asJsonObject
                    val code = root.get("code")?.asInt ?: -1
                    if (code != 0) throw IOException("MovieBox API error $code: ${root.get("message")?.asString.orEmpty()}")
                    val data = root.getAsJsonObject("data") ?: throw IOException("MovieBox response did not contain a data object")
                    hostMutex.withLock { activeHost = host }
                    return@withContext data
                }
            } catch (exception: Exception) { lastException = exception }
        }
        throw lastException ?: IOException(lastResponseError ?: "MovieBox host pool exhausted for $path")
    }

    private fun buildUrl(host: String, path: String, queryParams: Map<String, Any?>): String {
        val builder = "$host$path".toHttpUrl().newBuilder()
        queryParams.forEach { (key, value) -> if (value != null) builder.addQueryParameter(key, value.toString()) }
        return builder.build().toString()
    }

    private suspend fun <T> cached(map: ConcurrentHashMap<String, CacheEntry<T>>, key: String, ttlMs: Long, block: suspend () -> T): T {
        val now = System.currentTimeMillis()
        map[key]?.takeIf { it.expiresAt > now }?.let { return it.value }
        val value = block()
        map[key] = CacheEntry(value = value, expiresAt = now + ttlMs)
        return value
    }
}
