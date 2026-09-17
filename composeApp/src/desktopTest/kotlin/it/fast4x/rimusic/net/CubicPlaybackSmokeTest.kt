package app.it.fast4x.rimusic.net

import it.fast4x.innertube.utils.NewPipeUtils
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import okhttp3.OkHttpClient
import kotlin.test.Test
import kotlin.test.assertTrue
import windows.FfmpegAudioController

/** Opt-in network smoke test for the complete desktop path (InnerTube -> buffer -> FFmpeg). */
class CubicPlaybackSmokeTest {
    @Test
    fun decodesAudioFromAResolvedStream() = runBlocking {
        if (System.getenv("CUBIC_NETWORK_SMOKE") != "1") return@runBlocking

        val client = OkHttpClient()
        NewPipeUtils.init { client }
        val tracks = listOf(
            "dQw4w9WgXcQ", // baseline
            "VsSR52-yIXA", // NF, James Arthur - SORRY
            "dOo8GUUPisQ", // FLETCHER - If You're Gonna Lie
            "s0TBXCAQC50", // WA AJABU - ONJULA PRAISE
        )
        tracks.forEach { videoId ->
            CubicPlayTorrioResolver.invalidate(videoId)
            val url = CubicPlayTorrioResolver.resolve(videoId, client)
            assertTrue(!url.isNullOrBlank(), "InnerTube did not resolve $videoId")

            val controller = FfmpegAudioController(client)
            try {
                controller.setExpectedDuration(600_000L)
                controller.load(url!!)
                controller.play()
                delay(4_000L)
                assertTrue(controller.state.value.timestamp > 1_000L, "FFmpeg did not advance $videoId")
            } finally {
                controller.dispose()
            }
        }
    }
}
