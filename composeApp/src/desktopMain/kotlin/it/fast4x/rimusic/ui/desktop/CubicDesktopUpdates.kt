package app.it.fast4x.rimusic.ui.desktop

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.util.concurrent.TimeUnit

internal sealed interface CubicDesktopUpdateStatus {
    data object Checking : CubicDesktopUpdateStatus
    data object NoRelease : CubicDesktopUpdateStatus
    data class UpToDate(val latest: String) : CubicDesktopUpdateStatus
    data class Available(val latest: String) : CubicDesktopUpdateStatus
    data class UnknownVersion(val latest: String) : CubicDesktopUpdateStatus
    data class Error(val message: String) : CubicDesktopUpdateStatus
}

internal object CubicDesktopUpdates {
    const val releasesUrl = "https://github.com/cybruGhost/DESKTOP-CUBIC-MUSIC/releases"
    private const val latestUrl = "$releasesUrl/latest"
    private const val tagPath = "/cybruGhost/DESKTOP-CUBIC-MUSIC/releases/tag/"
    val installedVersion: String = System.getProperty("cubic.desktop.version")
        ?.takeIf(String::isNotBlank) ?: "3.0.1"

    private val client = OkHttpClient.Builder()
        .connectTimeout(5, TimeUnit.SECONDS)
        .readTimeout(5, TimeUnit.SECONDS)
        .callTimeout(10, TimeUnit.SECONDS)
        .build()

    suspend fun check(): CubicDesktopUpdateStatus = withContext(Dispatchers.IO) {
        try {
            val request = Request.Builder()
                .url(latestUrl)
                .header("Accept", "text/html")
                .header("User-Agent", "CubicMusic-Desktop/$installedVersion")
                .get()
                .build()
            client.newCall(request).execute().use { response ->
                if (response.code == 404) return@withContext CubicDesktopUpdateStatus.NoRelease
                if (!response.isSuccessful) {
                    return@withContext CubicDesktopUpdateStatus.Error("GitHub returned HTTP ${response.code}.")
                }
                val resolvedUrl = response.request.url
                val tag = resolvedUrl.takeIf {
                    it.host == "github.com" && it.encodedPath.startsWith(tagPath)
                }?.pathSegments?.lastOrNull()
                    ?.takeIf(String::isNotBlank)
                    ?: return@withContext CubicDesktopUpdateStatus.NoRelease
                val comparison = compareVersions(tag, installedVersion)
                when {
                    comparison == null -> CubicDesktopUpdateStatus.UnknownVersion(tag)
                    comparison > 0 -> CubicDesktopUpdateStatus.Available(tag)
                    else -> CubicDesktopUpdateStatus.UpToDate(tag)
                }
            }
        } catch (error: CancellationException) {
            throw error
        } catch (error: Exception) {
            CubicDesktopUpdateStatus.Error("Couldn't check for updates. Check your connection and try again.")
        }
    }

    /** Returns null for non-numeric release tags, so they cannot trigger false update notices. */
    internal fun compareVersions(latest: String, installed: String): Int? {
        fun parts(value: String): List<Int>? {
            val match = Regex("^[vV]?(\\d+)\\.(\\d+)\\.(\\d+)(?:[-+].*)?$")
                .matchEntire(value) ?: return null
            return match.groupValues.drop(1).map { it.toIntOrNull() ?: return null }
        }
        val left = parts(latest) ?: return null
        val right = parts(installed) ?: return null
        for (index in 0..2) {
            val result = left[index].compareTo(right[index])
            if (result != 0) return result
        }
        return 0
    }
}
