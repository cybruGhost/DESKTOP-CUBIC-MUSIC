package app.it.fast4x.rimusic.net

import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import java.io.OutputStream
import kotlin.math.min

/** Uses Android's 512 KiB bounded transfer and client-identity request headers. */
internal object CubicRangeTransfer {
    private const val CHUNK_LENGTH = 512L * 1024L
    private val contentRangePattern = Regex("bytes\\s+(\\d+)-(\\d+)/(\\d+|\\*)", RegexOption.IGNORE_CASE)

    fun validate(client: OkHttpClient, url: String): String? = runCatching {
        val headers = cubicPlaybackHeaders(url)
        val ranges = buildList {
            add("bytes=0-${CHUNK_LENGTH - 1L}")
            add("bytes=1048576-1048577")
        }.distinct()
        ranges.forEach { range ->
            val request = Request.Builder()
                .url(url)
                .header("Range", range)
                .applyPlaybackHeaders(headers)
                .get()
                .build()
            client.newCall(request).execute().use { response ->
                val contentType = response.header("Content-Type").orEmpty()
                val usableType = contentType.isBlank() ||
                    contentType.startsWith("audio/", ignoreCase = true) ||
                    contentType.startsWith("application/octet-stream", ignoreCase = true) ||
                    contentType.contains("audio", ignoreCase = true)
                check((response.code in 200..399 || response.code == 416) && usableType) {
                    "Stream validation failed with HTTP ${response.code} for $range"
                }
            }
        }
        url
    }.onFailure { error ->
        System.err.println("Cubic stream validation rejected ${url.toHttpUrlOrNull()?.queryParameter("c").orEmpty()}: ${error.message}")
    }.getOrNull()

    fun copy(
        client: OkHttpClient,
        url: String,
        output: OutputStream,
        shouldContinue: () -> Boolean = { true },
        onProgress: (copied: Long, total: Long) -> Unit = { _, _ -> }
    ): Long {
        var copied = 0L
        var total = url.toHttpUrlOrNull()
            ?.queryParameter("clen")
            ?.toLongOrNull()
            ?.takeIf { it > 0L }
            ?: -1L
        val buffer = ByteArray(64 * 1024)
        val headers = cubicPlaybackHeaders(url)

        while (shouldContinue() && (total < 0L || copied < total)) {
            val end = if (total > 0L) min(copied + CHUNK_LENGTH - 1L, total - 1L)
            else copied + CHUNK_LENGTH - 1L
            val request = Request.Builder()
                .url(url)
                .header("Range", "bytes=$copied-$end")
                .applyPlaybackHeaders(headers)
                .get()
                .build()

            var emptyChunk = false
            client.newCall(request).execute().use { response ->
                check(response.isSuccessful) {
                    "Download request failed with HTTP ${response.code} at bytes $copied-$end"
                }
                val contentType = response.header("Content-Type").orEmpty()
                check(isMediaContentType(contentType)) {
                    "Download server returned non-media content (${contentType.ifBlank { "unknown" }})."
                }
                val body = response.body
                val parsedRange = response.header("Content-Range")?.let(contentRangePattern::find)
                if (response.code == 206) {
                    check(parsedRange != null) { "The ranged response did not include Content-Range." }
                    val returnedStart = parsedRange!!.groupValues[1].toLong()
                    val returnedEnd = parsedRange.groupValues[2].toLong()
                    check(returnedStart == copied) { "The stream returned an unexpected starting byte." }
                    check(returnedEnd <= end) { "The stream returned bytes beyond the requested range." }
                    parsedRange.groupValues[3].takeUnless { it == "*" }?.toLongOrNull()?.let { total = it }
                } else {
                    // A server may ignore Range for the very first request, but accepting a
                    // full 200 response after offset zero would append an error/full file to
                    // an otherwise valid download.
                    check(copied == 0L) { "The download server ignored Range after byte $copied." }
                    if (total < 0L) total = body.contentLength()
                }

                val expectedChunk = if (response.code == 206 && parsedRange != null) {
                    parsedRange.groupValues[2].toLong() - parsedRange.groupValues[1].toLong() + 1L
                } else null
                var chunkCopied = 0L
                body.byteStream().use { input ->
                    while (shouldContinue()) {
                        val count = input.read(buffer)
                        if (count <= 0) break
                        if (expectedChunk != null) {
                            check(chunkCopied + count <= expectedChunk) { "The stream returned too many bytes for its range." }
                        }
                        output.write(buffer, 0, count)
                        copied += count
                        chunkCopied += count
                        onProgress(copied, total)
                    }
                }
                if (expectedChunk != null) {
                    check(chunkCopied == expectedChunk) { "The ranged response ended before its declared end byte." }
                }
                emptyChunk = chunkCopied == 0L
                if (response.code == 200) total = copied
            }
            if (emptyChunk) break
        }
        output.flush()
        check(total <= 0L || copied == total) { "Download ended at $copied bytes; expected $total." }
        return copied
    }

    private fun isMediaContentType(value: String): Boolean {
        if (value.isBlank()) return true
        val type = value.substringBefore(';').trim().lowercase()
        return type.startsWith("audio/") || type.startsWith("video/") ||
            type == "application/octet-stream" || type == "application/mp4"
    }

    private fun Request.Builder.applyPlaybackHeaders(headers: CubicPlaybackHeaders): Request.Builder = apply {
        header("User-Agent", headers.userAgent)
        header("Accept", "*/*")
        header("Accept-Encoding", "identity")
        header("Accept-Language", "en-US,en;q=0.9")
        headers.origin?.let { header("Origin", it) }
        headers.referer?.let { header("Referer", it) }
    }
}
