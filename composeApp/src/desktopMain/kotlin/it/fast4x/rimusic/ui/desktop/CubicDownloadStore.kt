package app.it.fast4x.rimusic.ui.desktop

import app.it.fast4x.rimusic.net.CubicRangeTransfer
import database.entities.Song
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import java.io.File
import java.awt.Desktop
import java.nio.file.Files
import java.nio.file.StandardCopyOption

internal object CubicDownloadStore {
    val directory: File by lazy {
        File(System.getProperty("user.home"), "Music/Cubic Music/Downloads").apply { mkdirs() }
    }

    fun downloadedFiles(): List<File> = directory.listFiles()
        .orEmpty()
        .filter { it.isFile && it.extension.lowercase() != "part" }
        .sortedByDescending(File::lastModified)

    fun downloadedSongIds(): Set<String> = downloadedFiles()
        .mapNotNull { it.name.substringBeforeLast('.').takeIf(String::isNotBlank) }
        .toSet()

    fun localFile(songId: String): File? =
        directory.listFiles()
            .orEmpty()
            .firstOrNull { it.isFile && it.extension.lowercase() != "part" && it.name.substringBeforeLast('.') == safeId(songId) }

    fun reveal(songId: String): Boolean {
        val file = localFile(songId) ?: return false
        return reveal(file)
    }

    fun reveal(file: File): Boolean {
        if (!file.isFile) return false
        return runCatching {
            if (System.getProperty("os.name").contains("Windows", ignoreCase = true)) {
                ProcessBuilder("explorer.exe", "/select,${file.absolutePath}").start()
            } else {
                Desktop.getDesktop().open(file.parentFile ?: directory)
            }
        }.isSuccess
    }

    fun delete(file: File): Boolean = runCatching {
        file.canonicalFile.parentFile == directory.canonicalFile && file.isFile && file.delete()
    }.getOrDefault(false)

    fun clear() {
        directory.listFiles().orEmpty().filter(File::isFile).forEach(File::delete)
    }

    suspend fun download(
        client: OkHttpClient,
        song: Song,
        streamUrl: String,
        onProgress: (Float) -> Unit
    ): Result<File> = withContext(Dispatchers.IO) {
        runCatching {
            directory.mkdirs()
            val mime = streamUrl.toHttpUrlOrNull()?.queryParameter("mime").orEmpty()
            val extension = when {
                mime.contains("webm", ignoreCase = true) -> "webm"
                else -> "m4a"
            }
            val destination = File(directory, "${safeId(song.id)}.$extension")
            val partial = File(directory, "${safeId(song.id)}.part")
            partial.outputStream().buffered().use { output ->
                CubicRangeTransfer.copy(client, streamUrl, output, onProgress = { copied, total ->
                    if (total > 0) onProgress((copied.toFloat() / total).coerceIn(0f, 1f))
                })
            }
            runCatching {
                Files.move(partial.toPath(), destination.toPath(), StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE)
            }.recoverCatching {
                Files.move(partial.toPath(), destination.toPath(), StandardCopyOption.REPLACE_EXISTING)
            }.getOrThrow()
            onProgress(1f)
            destination
        }.onFailure { partialFile(song.id).delete() }
    }

    private fun partialFile(songId: String) = File(directory, "${safeId(songId)}.part")

    internal fun safeId(value: String): String = value.replace(Regex("[^A-Za-z0-9_-]"), "_")
}
