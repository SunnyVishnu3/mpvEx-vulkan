package app.gyrolet.mpvrx.ui.browser.networkstreaming.proxy

import fi.iki.elonen.NanoHTTPD
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response as OkHttpResponse
import java.io.FilterInputStream
import java.io.InputStream
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

class HttpStreamingProxy private constructor() : NanoHTTPD("127.0.0.1", 0) {

    private data class Stream(
        val url: String,
        val headers: Map<String, String>,
        val registeredAt: Long = System.currentTimeMillis(),
    )

    private val client = OkHttpClient.Builder()
        .followRedirects(true)
        .followSslRedirects(true)
        .build()
    private val streams = ConcurrentHashMap<String, Stream>()

    fun register(url: String, headers: Map<String, String>): String {
        if (streams.size >= MAX_STREAMS) {
            streams.entries.minByOrNull { it.value.registeredAt }?.key?.let(streams::remove)
        }
        val id = UUID.randomUUID().toString()
        streams[id] = Stream(url, headers.filterValues { it.isNotBlank() })
        return "http://127.0.0.1:$listeningPort/$id.mp4"
    }

    override fun serve(session: IHTTPSession): Response {
        val id = session.uri.removePrefix("/").substringBefore('.').takeIf { it.isNotBlank() }
            ?: return notFound()
        val stream = streams[id] ?: return notFound()
        val request = Request.Builder().url(stream.url).apply {
            stream.headers.forEach { (name, value) -> header(name, value) }
            session.headers["range"]?.takeIf { it.isNotBlank() }?.let { header("Range", it) }
            if (session.method == Method.HEAD) head()
        }.build()

        val upstream = runCatching { client.newCall(request).execute() }
            .getOrElse { return newFixedLengthResponse(Response.Status.INTERNAL_ERROR, MIME_PLAINTEXT, it.message.orEmpty()) }
        if (upstream.code !in setOf(200, 206)) {
            val status = when (upstream.code) {
                403 -> Response.Status.FORBIDDEN
                404 -> Response.Status.NOT_FOUND
                416 -> Response.Status.RANGE_NOT_SATISFIABLE
                else -> Response.Status.INTERNAL_ERROR
            }
            upstream.close()
            return newFixedLengthResponse(status, MIME_PLAINTEXT, "Upstream HTTP ${upstream.code}")
        }

        val body = upstream.body
        val contentLength = body.contentLength()
        val mimeType = upstream.header("Content-Type")?.substringBefore(';') ?: "video/mp4"
        val status = if (upstream.code == 206) Response.Status.PARTIAL_CONTENT else Response.Status.OK
        val response = if (session.method == Method.HEAD) {
            upstream.close()
            newFixedLengthResponse(status, mimeType, "")
        } else if (contentLength >= 0L) {
            newFixedLengthResponse(status, mimeType, ClosingInputStream(body.byteStream(), upstream), contentLength)
        } else {
            newChunkedResponse(status, mimeType, ClosingInputStream(body.byteStream(), upstream))
        }
        copyHeader(upstream, response, "Accept-Ranges")
        copyHeader(upstream, response, "Content-Range")
        copyHeader(upstream, response, "Content-Length")
        return response
    }

    private fun copyHeader(upstream: OkHttpResponse, response: Response, name: String) {
        upstream.header(name)?.takeIf { it.isNotBlank() }?.let { response.addHeader(name, it) }
    }

    private fun notFound(): Response =
        newFixedLengthResponse(Response.Status.NOT_FOUND, MIME_PLAINTEXT, "Stream not found")

    private class ClosingInputStream(
        input: InputStream,
        private val response: OkHttpResponse,
    ) : FilterInputStream(input) {
        override fun close() {
            try {
                super.close()
            } finally {
                response.close()
            }
        }
    }

    companion object {
        private const val MAX_STREAMS = 32

        val instance: HttpStreamingProxy by lazy {
            HttpStreamingProxy().apply { start(SOCKET_READ_TIMEOUT, false) }
        }
    }
}
