package app.it.fast4x.rimusic.net

import it.fast4x.innertube.utils.NewPipeUtils
import kotlinx.coroutines.runBlocking
import okhttp3.OkHttpClient
import java.io.OutputStream
import kotlin.test.Test
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class CubicPlayTorrioResolverTest {
    @Test
    fun resolvesAndReadsSeveralChunksFromFreshTracks() = runBlocking {
        if (System.getenv("CUBIC_NETWORK_SMOKE") != "1") return@runBlocking

        val client = OkHttpClient()
        NewPipeUtils.init { client }
        // Real-world tracks that previously exposed the desktop resolver regression.
        val tracks = listOf(
            "dQw4w9WgXcQ", // baseline
            "VsSR52-yIXA", // NF, James Arthur - SORRY
            "dOo8GUUPisQ", // FLETCHER - If You're Gonna Lie
            "s0TBXCAQC50", // WA AJABU - ONJULA PRAISE
        )
        val minimumContinuousBytes = 2L * 1024L * 1024L

        tracks.forEach { videoId ->
            CubicPlayTorrioResolver.invalidate(videoId)
            val url = assertNotNull(CubicPlayTorrioResolver.resolve(videoId, client), videoId)
            System.err.println("Cubic smoke track $videoId resolved")
            var received = 0L
            val sink = object : OutputStream() {
                override fun write(value: Int) {
                    received++
                }

                override fun write(bytes: ByteArray, offset: Int, length: Int) {
                    received += length
                }
            }
            CubicRangeTransfer.copy(
                client = client,
                url = url,
                output = sink,
                shouldContinue = { true }
            )
            assertTrue(received >= minimumContinuousBytes, "$videoId stopped after $received bytes")
        }
    }
}
