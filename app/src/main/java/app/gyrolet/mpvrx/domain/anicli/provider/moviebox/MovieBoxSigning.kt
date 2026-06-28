package app.gyrolet.mpvrx.domain.anicli.provider.moviebox

import com.google.gson.JsonParser
import java.net.URI
import java.net.URLDecoder
import java.nio.charset.StandardCharsets
import java.security.MessageDigest
import java.util.Base64
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec

object MovieBoxSigning {

    private const val secretKeyDefault = "76iRl07s0xSN9jqmEWAt79EBJZulIQIsV64FZr2O"
    private const val signatureBodyMaxBytes = 102_400

    fun buildCanonicalString(
        method: String,
        accept: String?,
        contentType: String?,
        url: String,
        body: String?,
        timestampMs: Long,
    ): String {
        val uri = URI(url)
        val path = uri.rawPath.orEmpty()
        val query = buildSortedQuery(uri.rawQuery)
        val canonicalUrl = if (query.isBlank()) path else "$path?$query"
        val bodyBytes = body?.toByteArray(StandardCharsets.UTF_8)
        val bodyLength = bodyBytes?.size?.toString().orEmpty()
        val bodyHash = bodyBytes
            ?.copyOfRange(0, minOf(bodyBytes.size, signatureBodyMaxBytes))
            ?.let(::md5Hex).orEmpty()
        return buildString {
            append(method.uppercase())
            append('\n'); append(accept.orEmpty()); append('\n')
            append(contentType.orEmpty()); append('\n')
            append(bodyLength); append('\n')
            append(timestampMs); append('\n')
            append(bodyHash); append('\n')
            append(canonicalUrl)
        }
    }

    fun generateXClientToken(timestampMs: Long): String {
        val timestamp = timestampMs.toString()
        return "$timestamp,${md5Hex(timestamp.reversed().toByteArray(StandardCharsets.UTF_8))}"
    }

    fun generateXTrSignature(
        method: String,
        accept: String?,
        contentType: String?,
        url: String,
        body: String?,
        timestampMs: Long,
    ): String {
        val canonical = buildCanonicalString(method, accept, contentType, url, body, timestampMs)
        val secret = Base64.getDecoder().decode(padBase64(secretKeyDefault))
        val mac = Mac.getInstance("HmacMD5")
        mac.init(SecretKeySpec(secret, "HmacMD5"))
        val signature = Base64.getEncoder().encodeToString(mac.doFinal(canonical.toByteArray(StandardCharsets.UTF_8)))
        return "$timestampMs|2|$signature"
    }

    fun buildSignedHeaders(
        method: String,
        url: String,
        accept: String = "application/json",
        contentType: String = "application/json",
        body: String? = null,
        timestampMs: Long,
        authToken: String? = null,
        clientInfo: String,
        userAgent: String,
        includePlayMode: Boolean = false,
    ): Map<String, String> = buildMap {
        put("User-Agent", userAgent)
        put("Accept", accept)
        put("Content-Type", contentType)
        put("Connection", "keep-alive")
        put("X-Client-Token", generateXClientToken(timestampMs))
        put("x-tr-signature", generateXTrSignature(method, accept, contentType, url, body, timestampMs))
        put("X-Client-Info", clientInfo)
        put("X-Client-Status", "0")
        if (!authToken.isNullOrBlank()) put("Authorization", "Bearer $authToken")
        if (includePlayMode) put("X-Play-Mode", "2")
    }

    fun extractBearerToken(xUserHeader: String?): String? {
        if (xUserHeader.isNullOrBlank()) return null
        return runCatching {
            JsonParser.parseString(xUserHeader).asJsonObject
                .get("token")?.takeIf { !it.isJsonNull }?.asString?.takeIf { it.isNotBlank() }
        }.getOrNull()
    }

    private fun buildSortedQuery(rawQuery: String?): String {
        if (rawQuery.isNullOrBlank()) return ""
        return rawQuery.split("&").filter { it.isNotBlank() }.map { pair ->
            val index = pair.indexOf('=')
            val rawKey = if (index >= 0) pair.substring(0, index) else pair
            val rawValue = if (index >= 0) pair.substring(index + 1) else ""
            val key = URLDecoder.decode(rawKey, StandardCharsets.UTF_8.name())
            val value = URLDecoder.decode(rawValue, StandardCharsets.UTF_8.name())
            key to value
        }.sortedWith(compareBy<Pair<String, String>> { it.first }.thenBy { it.second })
            .joinToString("&") { (key, value) -> "$key=$value" }
    }

    private fun md5Hex(data: ByteArray): String {
        val digest = MessageDigest.getInstance("MD5").digest(data)
        return buildString(digest.size * 2) { digest.forEach { byte -> append("%02x".format(byte.toInt() and 0xff)) } }
    }

    private fun padBase64(value: String): String = value + "=".repeat((4 - value.length % 4) % 4)
}
