package app.it.fast4x.rimusic.net

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.OkHttpClient
import okhttp3.Request
import java.util.concurrent.TimeUnit

/**
 * Last-resort desktop source for videos YouTube marks LOGIN_REQUIRED/age-restricted.
 * The normal InnerTube and PoToken paths always run first; this resolver is only used
 * when they return no playable media URL.
 */
internal object CubicConvertYtMp3Resolver {
    private const val AUTH_ENDPOINT = "https://epsilon.epsiloncloud.org/api/v1/auth"
    private const val INIT_ENDPOINT = "https://epsilon.epsiloncloud.org/api/v1/init"
    private const val USER_AGENT =
        "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 " +
            "(KHTML, like Gecko) Chrome/114.0.0.0 Safari/537.36"
    private val json = Json { ignoreUnknownKeys = true }

    suspend fun resolve(videoId: String, baseClient: OkHttpClient): String? = withContext(Dispatchers.IO) {
        runCatching {
            withTimeout(18_000L) {
                val client = baseClient.newBuilder()
                    .connectTimeout(6, TimeUnit.SECONDS)
                    .readTimeout(8, TimeUnit.SECONDS)
                    .callTimeout(10, TimeUnit.SECONDS)
                    .build()
                val headers = mapOf(
                    "User-Agent" to USER_AGENT,
                    "Accept" to "application/json, text/javascript, */*; q=0.01",
                    "Origin" to "https://convertytmp3.org",
                    "Referer" to "https://convertytmp3.org/"
                )
                val key = getJson(client, "$AUTH_ENDPOINT?_=\${System.currentTimeMillis()}", headers)
                    ?.jsonObject?.get("key")?.jsonPrimitive?.contentOrNull
                    ?.takeIf(String::isNotBlank)
                    ?: return@withTimeout null
                val authHeaders = headers + ("Authorization" to "Bearer $key")
                val convertBase = getJson(client, "$INIT_ENDPOINT?_=\${System.currentTimeMillis()}", authHeaders)
                    ?.jsonObject?.get("convertURL")?.jsonPrimitive?.contentOrNull
                    ?.takeIf(String::isNotBlank)
                    ?: return@withTimeout null

                var convertUrl = "${convertBase}&v=$videoId&f=mp3&_=${System.currentTimeMillis()}"
                var result = getJson(client, convertUrl, authHeaders)
                repeat(5) {
                    val redirect = result?.jsonObject?.get("redirect")?.jsonPrimitive?.intOrNull
                    val redirectUrl = result?.jsonObject?.get("redirectURL")?.jsonPrimitive?.contentOrNull
                    if (redirect == 1 && !redirectUrl.isNullOrBlank()) {
                        convertUrl = redirectUrl
                        result = getJson(client, convertUrl, authHeaders)
                    } else return@repeat
                }
                var downloadUrl = result?.jsonObject?.get("downloadURL")?.jsonPrimitive?.contentOrNull
                val progressUrl = result?.jsonObject?.get("progressURL")?.jsonPrimitive?.contentOrNull
                if (downloadUrl.isNullOrBlank() && !progressUrl.isNullOrBlank()) {
                    repeat(10) {
                        delay(1_200L)
                        val progress = getJson(client, progressUrl, authHeaders)?.jsonObject ?: return@repeat
                        val status = progress["status"]?.jsonPrimitive?.intOrNull ?: return@repeat
                        if (status < 0) return@repeat
                        if (status == 3) {
                            downloadUrl = progress["downloadURL"]?.jsonPrimitive?.contentOrNull
                            return@repeat
                        }
                    }
                }
                downloadUrl?.takeIf(String::isNotBlank)
            }
        }.onFailure { error ->
            System.err.println("Cubic convert fallback failed: ${error.message}")
        }.getOrNull()
    }

    private fun getJson(client: OkHttpClient, url: String, headers: Map<String, String>) =
        runCatching {
            val request = Request.Builder().url(url).apply {
                headers.forEach { (name, value) -> header(name, value) }
            }.get().build()
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) return@use null
                response.body.string().takeIf(String::isNotBlank)?.let(json::parseToJsonElement)
            }
        }.getOrNull()
}
