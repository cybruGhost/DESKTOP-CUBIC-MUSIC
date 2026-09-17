package app.it.fast4x.rimusic.net

import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.OutputStream
import java.io.RandomAccessFile
import java.security.MessageDigest
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.Executors
import kotlin.math.min

/**
 * Downloads ahead of FFmpeg into a session file. The decoder consumes the growing file while the
 * network worker continues to fetch the whole track, so range retries never restart playback.
 */
internal object CubicPlaybackBuffer {
    private const val CHUNK_LENGTH = 512L * 1024L
    private const val STARTUP_BUFFER = 768L * 1024L
    private const val RETRIES_PER_CHUNK = 3
    private val contentRangePattern = Regex("bytes\\s+(\\d+)-(\\d+)/(\\d+|\\*)", RegexOption.IGNORE_CASE)
    private val sessions = ConcurrentHashMap<String, BufferSession>()
    private val workers = Executors.newFixedThreadPool(2) { task ->
        Thread(task, "cubic-playback-buffer").apply { isDaemon = true }
    }
    private val cacheDirectory = File(System.getProperty("java.io.tmpdir"), "cubic-music-playback").apply {
        mkdirs()
        listFiles()?.filter { System.currentTimeMillis() - it.lastModified() > 24 * 60 * 60 * 1000L }
            ?.forEach(File::delete)
    }

    fun copy(
        client: OkHttpClient,
        url: String,
        cacheKey: String?,
        output: OutputStream,
        shouldContinue: () -> Boolean,
        onProgress: (copied: Long, total: Long) -> Unit = { _, _ -> }
    ): Long {
        val formatKey = url.toHttpUrlOrNull()?.queryParameter("itag").orEmpty()
        val key = stableKey("${cacheKey ?: url}:$formatKey")
        sessions.values.filter { it.key != key }.forEach(BufferSession::cancelIfIncomplete)
        val session = sessions.compute(key) { _, existing ->
            existing?.takeIf { it.reusable } ?: BufferSession(key, File(cacheDirectory, "$key.media"))
        }!!
        session.start(client, url)
        return session.copyTo(output, shouldContinue, onProgress)
    }

    private fun stableKey(value: String): String = MessageDigest.getInstance("SHA-256")
        .digest(value.toByteArray())
        .take(16)
        .joinToString("") { "%02x".format(it) }

    private class BufferSession(val key: String, private val file: File) {
        private val monitor = Object()
        @Volatile private var started = false
        @Volatile var cancelled = false
            private set
        val reusable: Boolean get() = !failed && !cancelled
        @Volatile private var complete = false
        @Volatile var failed = false
            private set
        @Volatile private var failure: Throwable? = null
        @Volatile private var downloaded = file.takeIf(File::exists)?.length() ?: 0L
        @Volatile private var total = -1L

        fun start(client: OkHttpClient, url: String) = synchronized(monitor) {
            if (complete || started) return@synchronized
            cancelled = false
            failed = false
            failure = null
            total = url.toHttpUrlOrNull()?.queryParameter("clen")?.toLongOrNull()?.takeIf { it > 0L } ?: -1L
            if (total > 0L && downloaded >= total) {
                downloaded = total
                complete = true
                monitor.notifyAll()
                return@synchronized
            }
            started = true
            workers.submit { download(client, url) }
        }

        fun cancelIfIncomplete() = synchronized(monitor) {
            if (!complete) cancelled = true
            monitor.notifyAll()
        }

        private fun download(client: OkHttpClient, url: String) {
            try {
                file.parentFile?.mkdirs()
                RandomAccessFile(file, "rw").use { sink ->
                    sink.seek(downloaded)
                    while (!cancelled && (total < 0L || downloaded < total)) {
                        val start = downloaded
                        val end = if (total > 0L) min(start + CHUNK_LENGTH - 1L, total - 1L)
                        else start + CHUNK_LENGTH - 1L
                        var lastError: Throwable? = null
                        var chunkComplete = false
                        repeat(RETRIES_PER_CHUNK) { attempt ->
                            if (cancelled || chunkComplete) return@repeat
                            runCatching { downloadChunk(client, url, start, end, sink) }
                                .onSuccess { chunkComplete = true }
                                .onFailure { error ->
                                    lastError = error
                                    if (attempt + 1 < RETRIES_PER_CHUNK) Thread.sleep(250L * (attempt + 1L))
                                }
                        }
                        if (!chunkComplete) throw lastError ?: IllegalStateException("Buffered stream chunk failed")
                    }
                }
                synchronized(monitor) {
                    complete = !cancelled && (total < 0L || downloaded >= total)
                    started = false
                    monitor.notifyAll()
                }
            } catch (error: Throwable) {
                synchronized(monitor) {
                    started = false
                    if (!cancelled) {
                        failed = true
                        failure = error
                    }
                    monitor.notifyAll()
                }
            }
        }

        private fun downloadChunk(
            client: OkHttpClient,
            url: String,
            start: Long,
            end: Long,
            sink: RandomAccessFile
        ) {
            val profile = cubicPlaybackHeaders(url)
            val request = Request.Builder()
                .url(url)
                .header("Range", "bytes=$start-$end")
                .header("User-Agent", profile.userAgent)
                .header("Accept", "*/*")
                .header("Accept-Encoding", "identity")
                .header("Accept-Language", "en-US,en;q=0.9")
                .apply {
                    profile.origin?.let { header("Origin", it) }
                    profile.referer?.let { header("Referer", it) }
                }
                .get()
                .build()
            client.newCall(request).execute().use { response ->
                check(response.isSuccessful) { "Buffered stream HTTP ${response.code} at $start-$end" }
                response.header("Content-Range")?.let(contentRangePattern::find)?.let { match ->
                    val returnedStart = match.groupValues[1].toLong()
                    check(returnedStart == start) { "Buffered stream returned byte $returnedStart instead of $start" }
                    match.groupValues[3].takeUnless { it == "*" }?.toLongOrNull()?.let { total = it }
                }
                if (total < 0L && response.code == 200) total = response.body.contentLength()
                val bytes = ByteArray(64 * 1024)
                response.body.byteStream().use { input ->
                    while (!cancelled) {
                        val count = input.read(bytes)
                        if (count <= 0) break
                        sink.write(bytes, 0, count)
                        synchronized(monitor) {
                            downloaded += count
                            monitor.notifyAll()
                        }
                    }
                }
            }
        }

        fun copyTo(
            output: OutputStream,
            shouldContinue: () -> Boolean,
            onProgress: (copied: Long, total: Long) -> Unit
        ): Long {
            waitForStartup(shouldContinue)
            var copied = 0L
            RandomAccessFile(file, "r").use { source ->
                val bytes = ByteArray(64 * 1024)
                while (shouldContinue()) {
                    val available = downloaded - copied
                    if (available <= 0L) {
                        if (complete) break
                        failure?.let { throw it }
                        synchronized(monitor) { monitor.wait(200L) }
                        continue
                    }
                    val count = source.read(bytes, 0, min(bytes.size.toLong(), available).toInt())
                    if (count <= 0) {
                        synchronized(monitor) { monitor.wait(50L) }
                        continue
                    }
                    output.write(bytes, 0, count)
                    copied += count
                    onProgress(copied, total)
                }
            }
            output.flush()
            return copied
        }

        private fun waitForStartup(shouldContinue: () -> Boolean) {
            while (shouldContinue()) {
                if (downloaded >= STARTUP_BUFFER || complete || failure != null) break
                synchronized(monitor) { monitor.wait(100L) }
            }
            failure?.takeIf { downloaded == 0L }?.let { throw it }
        }
    }
}
