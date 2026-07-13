package app.gyrolet.mpvrx.domain.anicli.provider.moviebox

import com.google.gson.Gson
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import okhttp3.Call
import okhttp3.Callback
import okhttp3.Cookie
import okhttp3.Dns
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.IOException
import java.net.Inet4Address
import java.net.URLDecoder
import java.nio.charset.StandardCharsets
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import kotlin.coroutines.resumeWithException

internal class MovieBoxClient {

    private data class CacheEntry<T>(val value: T, val expiresAt: Long)
    private class AuthenticationException(message: String) : IOException(message)
    private class RateLimitException(retryAfter: String?) : IOException(
        "MovieBox rate limit reached${retryAfter?.let { "; retry after $it seconds" }.orEmpty()}"
    )

    private companion object {
        const val V2_HOST = "https://h5-api.aoneroom.com"
        const val V1_HOST = "https://h5.aoneroom.com"
        const val USER_AGENT =
            "Mozilla/5.0 (Linux; Android 13; Pixel 7) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/126.0.0.0 Mobile Safari/537.36"
        const val V2_HOME_PATH = "/wefeed-h5api-bff/home"
        const val V2_SEARCH_PATH = "/wefeed-h5api-bff/subject/search"
        const val V2_DETAIL_PATH = "/wefeed-h5api-bff/detail"
        const val V1_APP_PATH = "/wefeed-h5-bff/app/get-latest-app-pkgs"
        const val V1_TRENDING_PATH = "/wefeed-h5-bff/web/subject/trending"
        const val V1_DOWNLOAD_PATH = "/wefeed-h5-bff/web/subject/download"
        const val SEARCH_TTL_MS = 60_000L
        const val TRENDING_TTL_MS = 2 * 60_000L
        const val DETAIL_TTL_MS = 10 * 60_000L
        const val DOWNLOAD_TTL_MS = 60_000L
        const val MAX_CACHE_ENTRIES = 128

        const val MOBILE_HOME_PATH = "/wefeed-mobile-bff/tab-operating"
        const val MOBILE_RESOURCE_PATH = "/wefeed-mobile-bff/subject-api/resource"
        const val RESOURCE_TTL_MS = 60_000L
        private const val MOBILE_USER_AGENT =
            "com.community.oneroom/50020046 (Linux; U; Android 13; en_US; 23078RKD5C; Build/TQ2A.230405.003; Cronet/135.0.7012.3)"
        private val mobileHosts = listOf(
            "https://api6.aoneroom.com", "https://api5.aoneroom.com",
            "https://api4.aoneroom.com", "https://api4sg.aoneroom.com",
            "https://api3.aoneroom.com", "https://api.inmoviebox.com",
        )
        private val retryStatusCodes = setOf(403, 407, 500, 502, 503, 504)
    }

    private val gson = Gson()
    private val okHttpClient = OkHttpClient.Builder()
        .dns { hostname -> Dns.SYSTEM.lookup(hostname).sortedByDescending { it is Inet4Address } }
        .followRedirects(true)
        .followSslRedirects(true)
        .build()
    private val searchCacheMutex = Mutex()
    private val trendingCacheMutex = Mutex()
    private val detailCacheMutex = Mutex()
    private val downloadCacheMutex = Mutex()
    private val v1AuthMutex = Mutex()
    private val v2AuthMutex = Mutex()
    private val searchCache = ConcurrentHashMap<String, CacheEntry<JsonObject>>()
    private val trendingCache = ConcurrentHashMap<String, CacheEntry<JsonObject>>()
    private val detailCache = ConcurrentHashMap<String, CacheEntry<JsonObject>>()
    private val downloadCache = ConcurrentHashMap<String, CacheEntry<JsonObject>>()

    @Volatile private var accountCookie: String? = null
    @Volatile private var bearerToken: String? = null

    private val mobileDeviceId = UUID.randomUUID().toString()
    private val mobileClientInfo = gson.toJson(
        mapOf(
            "package_name" to "com.community.oneroom",
            "version_name" to "3.0.03.0529.03",
            "version_code" to 50020046,
            "os" to "android",
            "os_version" to "13",
            "install_ch" to "ps",
            "device_id" to mobileDeviceId,
            "install_store" to "ps",
            "gaid" to mobileDeviceId,
            "brand" to "Redmi",
            "model" to "23078RKD5C",
            "system_language" to "en",
            "net" to "NETWORK_WIFI",
            "region" to "US",
            "timezone" to "Asia/Kolkata",
            "sp_code" to "40401",
        )
    )
    private val mobileHostMutex = Mutex()
    private val mobileAuthMutex = Mutex()
    private val resourceCacheMutex = Mutex()
    private val resourceCache = ConcurrentHashMap<String, CacheEntry<JsonObject>>()

    @Volatile private var activeMobileHost = mobileHosts.first()
    @Volatile private var mobileRuntimeToken: String? = null

    suspend fun getTrending(page: Int, perPage: Int): JsonObject {
        val normalizedPage = page.coerceAtLeast(0)
        val normalizedPerPage = perPage.coerceAtLeast(1)
        val cacheKey = "$normalizedPage|$normalizedPerPage"
        return cached(trendingCache, trendingCacheMutex, cacheKey, TRENDING_TTL_MS) {
            requestV1(
                method = "GET",
                path = V1_TRENDING_PATH,
                query = mapOf("page" to normalizedPage, "perPage" to normalizedPerPage),
            )
        }
    }

    suspend fun search(keyword: String, page: Int, perPage: Int): JsonObject {
        val normalizedPage = page.coerceAtLeast(1)
        val normalizedPerPage = perPage.coerceAtLeast(1)
        val cacheKey = "${keyword.trim().lowercase()}|$normalizedPage|$normalizedPerPage"
        return cached(searchCache, searchCacheMutex, cacheKey, SEARCH_TTL_MS) {
            requestV2(
                method = "POST",
                path = V2_SEARCH_PATH,
                body = gson.toJson(
                    mapOf(
                        "keyword" to keyword.trim(),
                        "page" to normalizedPage,
                        "perPage" to normalizedPerPage,
                        "subjectType" to 0,
                    )
                ),
            )
        }
    }

    suspend fun getDetail(detailPath: String): JsonObject =
        cached(detailCache, detailCacheMutex, detailPath, DETAIL_TTL_MS) {
            requestV2(
                method = "GET",
                path = V2_DETAIL_PATH,
                query = mapOf("detailPath" to detailPath),
            )
        }

    suspend fun getDownload(
        subjectId: String,
        detailPath: String,
        season: Int,
        episode: Int,
    ): JsonObject {
        val cacheKey = "$subjectId|$detailPath|$season|$episode"
        return cached(downloadCache, downloadCacheMutex, cacheKey, DOWNLOAD_TTL_MS) {
            requestV1(
                method = "GET",
                path = V1_DOWNLOAD_PATH,
                query = mapOf("subjectId" to subjectId, "se" to season, "ep" to episode),
                referer = "$V1_HOST/movies/$detailPath",
            )
        }
    }

    suspend fun getResourcePage(subjectId: String, page: Int, perPage: Int): JsonObject {
        val normalizedPage = page.coerceAtLeast(1)
        val normalizedPerPage = perPage.coerceAtLeast(1)
        val cacheKey = "$subjectId|$normalizedPage|$normalizedPerPage"
        return cached(resourceCache, resourceCacheMutex, cacheKey, RESOURCE_TTL_MS) {
            requestMobile(
                method = "GET",
                path = MOBILE_RESOURCE_PATH,
                query = linkedMapOf(
                    "subjectId" to subjectId,
                    "resolution" to 0,
                    "page" to normalizedPage,
                    "perPage" to normalizedPerPage,
                ),
            )
        }
    }

    private suspend fun requestMobile(
        method: String,
        path: String,
        query: Map<String, Any?> = emptyMap(),
    ): JsonObject {
        val attemptedToken = ensureMobileToken()
        try {
            return executeMobileRequest(method, path, query, attemptedToken).data
        } catch (_: AuthenticationException) {
            mobileAuthMutex.withLock {
                if (mobileRuntimeToken == attemptedToken) mobileRuntimeToken = null
            }
            return executeMobileRequest(method, path, query, ensureMobileToken()).data
        }
    }

    private suspend fun ensureMobileToken(): String = mobileAuthMutex.withLock {
        mobileRuntimeToken?.takeIf { it.isNotBlank() }?.let { return@withLock it }
        val result = executeMobileRequest(
            method = "GET",
            path = MOBILE_HOME_PATH,
            query = linkedMapOf("page" to 1, "tabId" to 0, "version" to ""),
            authToken = null,
            includePlayMode = false,
        )
        result.issuedToken
            ?.takeIf { it.isNotBlank() }
            ?.also { mobileRuntimeToken = it }
            ?: throw IOException("MovieBox mobile authentication did not issue a token")
    }

    private data class MobileResult(val data: JsonObject, val issuedToken: String?)

    private suspend fun executeMobileRequest(
        method: String,
        path: String,
        query: Map<String, Any?>,
        authToken: String?,
        includePlayMode: Boolean = true,
    ): MobileResult {
        val orderedHosts = mobileHostMutex.withLock {
            listOf(activeMobileHost) + mobileHosts.filterNot { it == activeMobileHost }
        }
        var lastException: Exception? = null

        for (host in orderedHosts) {
            try {
                val url = buildUrl(host, path, query)
                val timestampMs = System.currentTimeMillis()
                val signedHeaders = MovieBoxSigning.buildSignedHeaders(
                    method = method,
                    url = url,
                    body = null,
                    timestampMs = timestampMs,
                    authToken = authToken,
                    clientInfo = mobileClientInfo,
                    userAgent = MOBILE_USER_AGENT,
                    includePlayMode = includePlayMode,
                )
                val requestBuilder = Request.Builder().url(url)
                signedHeaders.forEach { (key, value) -> requestBuilder.header(key, value) }
                requestBuilder.method(method, null)

                okHttpClient.newCall(requestBuilder.build()).awaitResponse().use { response ->
                    val issuedToken = MovieBoxSigning.extractBearerToken(response.header("x-user"))
                    if (response.code == 429) throw RateLimitException(response.header("Retry-After"))
                    if (response.code == 401 || response.code == 441) {
                        throw AuthenticationException("MovieBox mobile authentication expired with HTTP ${response.code}")
                    }
                    if (response.code in retryStatusCodes) return@use
                    val responseBody = response.body.string()
                    if (!response.isSuccessful) {
                        throw IOException("MovieBox mobile request failed with HTTP ${response.code}: ${responseBody.take(240)}")
                    }
                    val root = JsonParser.parseString(responseBody).asJsonObject
                    val code = root.get("code")?.takeIf { !it.isJsonNull }?.asInt ?: -1
                    if (code != 0) {
                        val msg = root.get("message")?.takeIf { !it.isJsonNull }?.asString.orEmpty()
                        if (code == 401 || code == 441 || msg.contains("token", ignoreCase = true) ||
                            msg.contains("auth", ignoreCase = true)
                        ) {
                            throw AuthenticationException("MovieBox mobile authentication failed: $code $msg")
                        }
                        throw IOException("MovieBox mobile API error $code: $msg")
                    }
                    val data = root.getAsJsonObject("data")
                        ?: throw IOException("MovieBox mobile response did not contain a data object")
                    issuedToken?.let { mobileRuntimeToken = it }
                    mobileHostMutex.withLock { activeMobileHost = host }
                    return MobileResult(data, issuedToken)
                }
            } catch (exception: RateLimitException) {
                throw exception
            } catch (exception: AuthenticationException) {
                throw exception
            } catch (exception: Exception) {
                lastException = exception
            }
        }
        throw lastException ?: IOException("MovieBox mobile hosts exhausted for $path")
    }

    private suspend fun requestV2(
        method: String,
        path: String,
        query: Map<String, Any?> = emptyMap(),
        body: String? = null,
    ): JsonObject {
        val attemptedToken = ensureV2Token()
        try {
            return executeApiRequest(V2_HOST, method, path, query, body, bearerToken = attemptedToken)
        } catch (_: AuthenticationException) {
            v2AuthMutex.withLock {
                if (bearerToken == attemptedToken) bearerToken = null
            }
            val refreshedToken = ensureV2Token()
            return executeApiRequest(V2_HOST, method, path, query, body, bearerToken = refreshedToken)
        }
    }

    private suspend fun requestV1(
        method: String,
        path: String,
        query: Map<String, Any?> = emptyMap(),
        body: String? = null,
        referer: String = "$V1_HOST/",
    ): JsonObject {
        val attemptedCookie = ensureV1Cookie()
        try {
            return executeApiRequest(V1_HOST, method, path, query, body, account = attemptedCookie, referer = referer)
        } catch (_: AuthenticationException) {
            v1AuthMutex.withLock {
                if (accountCookie == attemptedCookie) accountCookie = null
            }
            val refreshedCookie = ensureV1Cookie()
            return executeApiRequest(V1_HOST, method, path, query, body, account = refreshedCookie, referer = referer)
        }
    }

    private suspend fun ensureV2Token(): String = v2AuthMutex.withLock {
        bearerToken?.takeIf { it.isNotBlank() }?.let { return@withLock it }
        val url = buildUrl(V2_HOST, V2_HOME_PATH, mapOf("host" to "h5.aoneroom.com"))
        val request = Request.Builder()
            .url(url)
            .header("Accept", "application/json")
            .header("Referer", "$V1_HOST/")
            .header("User-Agent", USER_AGENT)
            .build()
        okHttpClient.newCall(request).awaitResponse().use { response ->
            if (response.code == 429) throw RateLimitException(response.header("Retry-After"))
            if (!response.isSuccessful) throw IOException("MovieBox authentication failed with HTTP ${response.code}")
            val tokenFromUserHeader = response.header("x-user")?.let { header ->
                runCatching { JsonParser.parseString(header).asJsonObject.get("token")?.asString }.getOrNull()
            }
            val tokenFromCookie = response.headers.values("Set-Cookie")
                .mapNotNull { Cookie.parse(V2_HOST.toHttpUrl(), it) }
                .firstOrNull { it.name == "token" }
                ?.value
            normalizeToken(tokenFromUserHeader ?: tokenFromCookie).also { bearerToken = it }
        }
    }

    private suspend fun ensureV1Cookie(): String = v1AuthMutex.withLock {
        accountCookie?.takeIf { it.isNotBlank() }?.let { return@withLock it }
        val url = buildUrl(V1_HOST, V1_APP_PATH, mapOf("app_name" to "moviebox"))
        val request = Request.Builder()
            .url(url)
            .header("Accept", "application/json")
            .header("Referer", "$V1_HOST/")
            .header("User-Agent", USER_AGENT)
            .build()
        okHttpClient.newCall(request).awaitResponse().use { response ->
            if (response.code == 429) throw RateLimitException(response.header("Retry-After"))
            if (!response.isSuccessful) throw IOException("MovieBox cookie request failed with HTTP ${response.code}")
            response.headers.values("Set-Cookie")
                .mapNotNull { Cookie.parse(V1_HOST.toHttpUrl(), it) }
                .firstOrNull { it.name == "account" }
                ?.value
                ?.takeIf { it.isNotBlank() }
                ?.also { accountCookie = it }
                ?: throw IOException("MovieBox did not issue an account cookie")
        }
    }

    private suspend fun executeApiRequest(
        host: String,
        method: String,
        path: String,
        query: Map<String, Any?>,
        body: String?,
        bearerToken: String? = null,
        account: String? = null,
        referer: String = "$V1_HOST/",
    ): JsonObject {
        val url = buildUrl(host, path, query)
        val requestBuilder = Request.Builder()
            .url(url)
            .header("Accept", "application/json")
            .header("Accept-Language", "en-US,en;q=0.9")
            .header("Referer", referer)
            .header("User-Agent", USER_AGENT)
        bearerToken?.let {
            requestBuilder.header("Authorization", "Bearer $it")
            requestBuilder.header("Cookie", "token=$it")
            requestBuilder.header("Origin", V1_HOST)
        }
        account?.let { requestBuilder.header("Cookie", "account=$it") }
        val requestBody = body?.toRequestBody("application/json; charset=utf-8".toMediaType())
        requestBuilder.method(method, if (method.equals("POST", ignoreCase = true)) requestBody else null)

        okHttpClient.newCall(requestBuilder.build()).awaitResponse().use { response ->
            val responseBody = response.body.string()
            if (response.code == 401 || response.code == 403) {
                throw AuthenticationException("MovieBox authentication expired with HTTP ${response.code}")
            }
            if (response.code == 429) throw RateLimitException(response.header("Retry-After"))
            if (!response.isSuccessful) {
                throw IOException("MovieBox request failed with HTTP ${response.code}: ${responseBody.take(240)}")
            }
            val root = runCatching { JsonParser.parseString(responseBody).asJsonObject }
                .getOrElse { throw IOException("MovieBox returned invalid JSON", it) }
            val code = runCatching { root.get("code")?.takeIf { !it.isJsonNull }?.asInt }.getOrNull() ?: -1
            val message = runCatching { root.get("message")?.takeIf { !it.isJsonNull }?.asString }.getOrNull().orEmpty()
            if (code != 0) {
                if (code == 401 || message.contains("token", ignoreCase = true) ||
                    message.contains("auth", ignoreCase = true) ||
                    message.contains("unauthorized", ignoreCase = true)
                ) {
                    throw AuthenticationException("MovieBox authentication failed: $code $message")
                }
                throw IOException("MovieBox API error $code: $message")
            }
            return root.getAsJsonObject("data")
                ?: throw IOException("MovieBox response did not contain a data object")
        }
    }

    private fun buildUrl(host: String, path: String, query: Map<String, Any?>): String {
        val builder = "$host$path".toHttpUrl().newBuilder()
        query.forEach { (key, value) -> if (value != null) builder.addQueryParameter(key, value.toString()) }
        return builder.build().toString()
    }

    private suspend fun <T> cached(
        map: ConcurrentHashMap<String, CacheEntry<T>>,
        mutex: Mutex,
        key: String,
        ttlMs: Long,
        block: suspend () -> T,
    ): T {
        map[key]?.takeIf { it.expiresAt > System.currentTimeMillis() }?.let { return it.value }
        return mutex.withLock {
            if (map.size >= MAX_CACHE_ENTRIES) {
                val now = System.currentTimeMillis()
                map.entries.filter { it.value.expiresAt <= now }.forEach { map.remove(it.key, it.value) }
                if (map.size >= MAX_CACHE_ENTRIES) map.keys.firstOrNull()?.let(map::remove)
            }
            map[key]?.takeIf { it.expiresAt > System.currentTimeMillis() }?.value
                ?: block().also { value ->
                    map[key] = CacheEntry(value, System.currentTimeMillis() + ttlMs)
                }
        }
    }

    private fun normalizeToken(rawToken: String?): String {
        val token = rawToken
            ?.let { URLDecoder.decode(it, StandardCharsets.UTF_8.name()) }
            ?.trim()
            ?.removeSurrounding("\"")
            ?.takeIf { it.isNotBlank() }
            ?: throw IOException("MovieBox did not issue a V2 token")
        if (token.count { it == '.' } != 2) throw IOException("MovieBox issued an invalid V2 token")
        return token
    }

    private suspend fun Call.awaitResponse(): Response = suspendCancellableCoroutine { continuation ->
        continuation.invokeOnCancellation { cancel() }
        enqueue(object : Callback {
            override fun onFailure(call: Call, exception: IOException) {
                continuation.resumeWithException(exception)
            }

            override fun onResponse(call: Call, response: Response) {
                continuation.resume(response) { response.close() }
            }
        })
    }
}
