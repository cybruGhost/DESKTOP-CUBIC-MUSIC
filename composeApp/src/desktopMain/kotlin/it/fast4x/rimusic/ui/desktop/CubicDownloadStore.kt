package app.it.fast4x.rimusic.ui.desktop

import app.it.fast4x.rimusic.net.CubicRangeTransfer
import database.entities.Song
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import java.io.File
import java.io.RandomAccessFile
import java.awt.Desktop
import java.nio.file.Files
import java.nio.file.StandardCopyOption

internal object CubicDownloadStore {
    private val mediaExtensions = setOf("m4a", "mp4", "webm", "ogg", "opus")
    val directory: File by lazy {
        File(System.getProperty("user.home"), "Music/Cubic Music/Downloads").apply { mkdirs() }
    }

    fun downloadedFiles(): List<File> = directory.listFiles()
        .orEmpty()
        .filter { it.isFile && it.extension.lowercase() in mediaExtensions && isMarkedComplete(it) }
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
                mime.contains("ogg", ignoreCase = true) || mime.contains("opus", ignoreCase = true) -> "ogg"
                mime.contains("video/", ignoreCase = true) || mime.contains("video/mp4", ignoreCase = true) -> "mp4"
                else -> "m4a"
            }
            val destination = File(directory, "${safeId(song.id)}.$extension")
            val partial = File(directory, "${safeId(song.id)}.part")
            val copied = partial.outputStream().buffered().use { output ->
                CubicRangeTransfer.copy(client, streamUrl, output, onProgress = { copied, total ->
                    if (total > 0) onProgress((copied.toFloat() / total).coerceIn(0f, 1f))
                })
            }
            check(copied > 0L) { "The download returned no media bytes." }
            runCatching {
                Files.move(partial.toPath(), destination.toPath(), StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE)
            }.recoverCatching {
                Files.move(partial.toPath(), destination.toPath(), StandardCopyOption.REPLACE_EXISTING)
            }.getOrThrow()
            check(isValidMediaFile(destination)) { "The downloaded file failed media validation." }
            completionMarker(destination).writeText("Cubic Music complete\n$copied\n")
            directory.listFiles().orEmpty()
                .filter { it.isFile && it.extension.lowercase() in mediaExtensions && it.nameWithoutExtension == safeId(song.id) && it != destination }
                .forEach { old -> old.delete(); completionMarker(old).delete() }
            onProgress(1f)
            destination
        }.onFailure { partialFile(song.id).delete() }
    }

    private fun partialFile(songId: String) = File(directory, "${safeId(songId)}.part")

    private fun completionMarker(file: File): File = File(file.parentFile, file.name + ".complete")

    private fun isMarkedComplete(file: File): Boolean =
        completionMarker(file).isFile && isValidMediaFile(file)

    /** Container-level validation prevents half responses and HTML/JSON error payloads from
     * appearing as playable downloads. */
    private fun isValidMediaFile(file: File): Boolean = runCatching {
        if (!file.isFile || file.length() < 32L) return@runCatching false
        RandomAccessFile(file, "r").use { input ->
            val header = ByteArray(12)
            input.readFully(header)
            val isMp4 = String(header, 4, 4, Charsets.US_ASCII) == "ftyp"
            if (!isMp4) return@use header[0] == 0x1A.toByte() && header[1] == 0x45.toByte() && header[2] == 0xDF.toByte() && header[3] == 0xA3.toByte()

            var offset = 0L
            var hasMdat = false
            var hasMovie = false
            val length = input.length()
            while (offset + 8L <= length) {
                input.seek(offset)
                val size32 = input.readInt().toLong() and 0xFFFF_FFFFL
                val typeBytes = ByteArray(4)
                input.readFully(typeBytes)
                val type = String(typeBytes, Charsets.US_ASCII)
                val headerSize: Long
                val boxSize: Long
                if (size32 == 1L) {
                    boxSize = input.readLong()
                    headerSize = 16L
                } else if (size32 == 0L) {
                    boxSize = length - offset
                    headerSize = 8L
                } else {
                    boxSize = size32
                    headerSize = 8L
                }
                if (boxSize < headerSize || offset + boxSize > length) return@use false
                if (type == "mdat") hasMdat = true
                if (type == "moov" || type == "moof") hasMovie = true
                offset += boxSize
                if (size32 == 0L) break
            }
            offset == length && hasMdat && hasMovie
        }
    }.getOrDefault(false)

    internal fun safeId(value: String): String = value.replace(Regex("[^A-Za-z0-9_-]"), "_")
}
