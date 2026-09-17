package app.it.fast4x.rimusic.net

import it.fast4x.innertube.Innertube
import it.fast4x.innertube.models.PlayerResponse
import it.fast4x.innertube.requests.player
import it.fast4x.innertube.utils.NewPipeUtils
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import okhttp3.Call
import okhttp3.Callback
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import java.io.IOException
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit
import kotlin.coroutines.resume

/**
 * Desktop adaptation of PlayTorrio's direct YouTube resolver.
 *
 * It keeps catalog IDs on InnerTube, refreshes visitor data from the watch page, ranks
 * audio-only formats, probes alternate CDN nodes and caches only until shortly before the
 * signed URL expires. Playback bytes still flow through [CubicPlaybackBuffer].
 */
internal object CubicPlayTorrioResolver {
    private const val CLIENT_BACKOFF_MS = 10 * 60 * 1000L
    private const val VISITOR_TTL_MS = 3 * 60 * 60 * 1000L
    private const val URL_EXPIRY_SAFETY_MS = 2 * 60 * 1000L
    private const val DEFAULT_URL_TTL_MS = 20 * 60 * 1000L
    private const val CPN_ALPHABET = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789-_"

    private val streamCache = ConcurrentHashMap<String, CachedStream>()
    private val failedClientsUntil = ConcurrentHashMap<String, Long>()
    private val resolveLocks = ConcurrentHashMap<String, Mutex>()

    @Volatile private var visitorCache: CachedVisitor? = null

    suspend fun resolve(videoId: String, httpClient: OkHttpClient): String? {
        cached(videoId)?.let { return it }
        val lock = resolveLocks.computeIfAbsent(videoId) { Mutex() }
        return lock.withLock {
            cached(videoId)?.let { return@withLock it }
            resolveFresh(videoId, httpClient)?.also { url ->
                streamCache[videoId] = CachedStream(url, expiryFor(url))
            }
        }
    }

    fun markFailed(videoId: String, url: String) {
        streamCache.remove(videoId)
        val clientName = url.toHttpUrlOrNull()?.queryParameter("c").orEmpty()
        if (clientName.isNotBlank()) {
            failedClientsUntil[clientKey(videoId, clientName)] =
                System.currentTimeMillis() + CLIENT_BACKOFF_MS
        }
    }

    fun invalidate(videoId: String) {
        streamCache.remove(videoId)
    }

    private fun cached(videoId: String): String? {
        val cached = streamCache[videoId] ?: return null
        return if (System.currentTimeMillis() < cached.expiresAtMs) cached.url
        else null.also { streamCache.remove(videoId, cached) }
    }

    private suspend fun resolveFresh(videoId: String, httpClient: OkHttpClient): String? =
        withContext(Dispatchers.IO) {
            val now = System.currentTimeMillis()
            val visitorData = refreshedVisitorData(videoId, httpClient)
            val primaryClients = cubicDesktopPlaybackClients.filterNot { it.clientName == "IOS" }
            val clients = primaryClients.filter { client ->
                (failedClientsUntil[clientKey(videoId, client.clientName)] ?: 0L) <= now
            }.ifEmpty {
                failedClientsUntil.keys.removeIf { it.startsWith("$videoId:") }
                primaryClients
            }

            var signatureTimestamp: Int? = null
            var signatureTimestampLoaded = false
            for (client in clients) {
                if (client.loginRequired && Innertube.cookie.isNullOrBlank()) continue
                if (client.useSignatureTimestamp && !signatureTimestampLoaded) {
                    signatureTimestamp = NewPipeUtils.getSignatureTimestamp(videoId).getOrNull()
                    signatureTimestampLoaded = true
                }

                val context = client.toContext(
                    Innertube.locale,
                    visitorData,
                    Innertube.dataSyncId.takeIf { !Innertube.cookie.isNullOrBlank() }
                )
                val response = runCatching {
                    withTimeout(8_000L) {
                        Innertube.player(
                            videoId = videoId,
                            context = context,
                            signatureTimestamp = signatureTimestamp
                        )?.getOrNull()
                    }
                }.getOrNull() ?: continue

                if (response.playabilityStatus?.status != "OK") {
                    val reason = response.playabilityStatus?.reason.orEmpty()
                    System.err.println("Cubic PlayTorrio ${client.clientName}: ${response.playabilityStatus?.status} $reason")
                    if (reason.contains("bot", ignoreCase = true) ||
                        response.playabilityStatus?.status == "LOGIN_REQUIRED"
                    ) {
                        failedClientsUntil[clientKey(videoId, client.clientName)] = now + CLIENT_BACKOFF_MS
                    }
                    continue
                }

                for (format in orderedFormats(response.streamingData)) {
                    val rawUrl = NewPipeUtils.getStreamUrl(format, videoId)
                        .getOrNull()
                        ?.takeIf(String::isNotBlank)
                        ?: continue
                    val identified = attachCubicPlaybackIdentity(rawUrl, client, playbackCpn()) ?: continue
                    resolveReachableCdn(identified, httpClient)?.let { reachable ->
                        System.err.println("Cubic playback source: PlayTorrio InnerTube ${client.clientName}")
                        return@withContext reachable
                    }
                }
            }
            null
        }

    private suspend fun refreshedVisitorData(videoId: String, client: OkHttpClient): String {
        visitorCache?.takeIf { System.currentTimeMillis() < it.expiresAtMs }?.let { return it.value }
        val fallback = Innertube.visitorData
            .takeUnless { it.isBlank() || it == "null" }
            ?: Innertube.DEFAULT_VISITOR_DATA
        val value = runCatching {
            withContext(Dispatchers.IO) {
                val request = Request.Builder()
                    .url("https://www.youtube.com/watch?v=$videoId&hl=en")
                    .header("User-Agent", cubicDesktopPlaybackClients.first().userAgent)
                    .header("Accept-Language", "en-US,en;q=0.9")
                    .get()
                    .build()
                client.newCall(request).execute().use { response ->
                    if (!response.isSuccessful) return@use null
                    VISITOR_PATTERN.find(response.body.string())
                        ?.groupValues
                        ?.getOrNull(1)
                        ?.replace("\\u0026", "&")
                        ?.takeIf(String::isNotBlank)
                }
            }
        }.getOrNull() ?: fallback
        visitorCache = CachedVisitor(value, System.currentTimeMillis() + VISITOR_TTL_MS)
        return value
    }

    private suspend fun resolveReachableCdn(url: String, client: OkHttpClient): String? = coroutineScope {
        val candidates = cdnCandidates(url)
        val results = Channel<String?>(candidates.size)
        val jobs = candidates.map { candidate ->
            launch(Dispatchers.IO) {
                try {
                    results.send(candidate.takeIf { isReachable(client, candidate) })
                } catch (_: CancellationException) {
                    // A faster CDN node won the race.
                }
            }
        }
        repeat(candidates.size) {
            results.receive()?.let { reachable ->
                jobs.forEach { it.cancel() }
                results.close()
                return@coroutineScope reachable
            }
        }
        results.close()
        null
    }

    private suspend fun isReachable(client: OkHttpClient, url: String): Boolean =
        suspendCancellableCoroutine { continuation ->
            val profile = cubicPlaybackHeaders(url)
            val request = Request.Builder()
                .url(url)
                .header("Range", "bytes=0-0")
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
            val call = client.newCall(request)
            call.timeout().timeout(2_500L, TimeUnit.MILLISECONDS)
            continuation.invokeOnCancellation { call.cancel() }
            call.enqueue(object : Callback {
                override fun onFailure(call: Call, error: IOException) {
                    if (continuation.isActive) continuation.resume(false)
                }

                override fun onResponse(call: Call, response: Response) {
                    response.use {
                        val contentType = it.header("Content-Type").orEmpty()
                        val usableType = contentType.isBlank() ||
                            contentType.startsWith("audio/", ignoreCase = true) ||
                            contentType.startsWith("application/octet-stream", ignoreCase = true) ||
                            contentType.contains("audio", ignoreCase = true)
                        if (continuation.isActive) {
                            continuation.resume((it.code == 200 || it.code == 206) && usableType)
                        }
                    }
                }
            })
        }

    private fun cdnCandidates(url: String): List<String> {
        val parsed = url.toHttpUrlOrNull() ?: return listOf(url)
        if (!parsed.host.endsWith("googlevideo.com")) return listOf(url)
        val nodes = parsed.queryParameter("mn")
            ?.split(',')
            ?.map(String::trim)
            ?.filter(String::isNotBlank)
            .orEmpty()
        if (nodes.isEmpty()) return listOf(url)

        return buildList {
            add(url)
            nodes.forEachIndexed { index, node ->
                val host = parsed.host
                    .replace(Regex("^rr\\d+---"), "rr${index + 1}---")
                    .replace(Regex("sn-[a-z0-9-]+"), node)
                if (host != parsed.host) {
                    add(parsed.newBuilder().host(host).build().toString())
                }
            }
        }.distinct().take(3)
    }

    private fun orderedFormats(streamingData: PlayerResponse.StreamingData?): List<PlayerResponse.StreamingData.Format> {
        val preferredItags = listOf(141, 140, 251, 250, 249, 139, 171, 774)
        return (streamingData?.adaptiveFormats.orEmpty() + streamingData?.formats.orEmpty())
            .asSequence()
            .filter { format ->
                format.isAudio &&
                    (!format.url.isNullOrBlank() || !format.signatureCipher.isNullOrBlank())
            }
            .distinctBy { it.itagValue ?: it.mimeType + it.url.orEmpty() + it.signatureCipher.orEmpty() }
            .sortedWith(
                compareBy<PlayerResponse.StreamingData.Format> {
                    preferredItags.indexOf(it.itagValue).takeIf { index -> index >= 0 } ?: Int.MAX_VALUE
                }.thenByDescending { it.bitrateValue ?: 0 }
            )
            .toList()
    }

    private fun expiryFor(url: String): Long {
        val now = System.currentTimeMillis()
        val signedExpiry = url.toHttpUrlOrNull()
            ?.queryParameter("expire")
            ?.toLongOrNull()
            ?.times(1000L)
            ?.minus(URL_EXPIRY_SAFETY_MS)
        return signedExpiry?.takeIf { it > now + 60_000L }
            ?: now + DEFAULT_URL_TTL_MS
    }

    private fun playbackCpn(): String = buildString(16) {
        repeat(16) { append(CPN_ALPHABET.random()) }
    }

    private fun clientKey(videoId: String, clientName: String) = "$videoId:$clientName"

    private data class CachedStream(val url: String, val expiresAtMs: Long)
    private data class CachedVisitor(val value: String, val expiresAtMs: Long)

    private val VISITOR_PATTERN = Regex("\\\"VISITOR_DATA\\\":\\\"([^\\\"]+)\\\"")
}
