package app.it.fast4x.rimusic.ui.desktop

import database.entities.Song
import java.nio.charset.StandardCharsets
import java.util.Base64
import java.util.prefs.Preferences

internal object CubicTasteStore {
    private val node = Preferences.userRoot().node("CubicMusic/Desktop")

    fun ids(): List<String> = node.get("tasteHistory", "")
        .lineSequence().map(String::trim).filter(String::isNotBlank).distinct().take(100).toList()

    fun record(song: Song) {
        val songId = song.id
        val updated = listOf(songId) + ids().filterNot { it == songId }
        node.put("tasteHistory", updated.take(100).joinToString("\n"))
        val counts = playCounts().toMutableMap()
        counts[songId] = (counts[songId] ?: 0) + 1
        node.put("playCounts", counts.entries.sortedByDescending { it.value }.take(200).joinToString("\n") { "${it.key}=${it.value}" })
        saveMetadata(song)
    }

    /** Saves display metadata for a previously recorded id without changing its play count. */
    fun saveMetadata(song: Song) {
        val metadata = songMetadata().associateBy { it.id }.toMutableMap()
        metadata[song.id] = song
        node.put(
            "tasteSongs",
            metadata.values.take(200).joinToString("\n") { stored ->
                listOf(stored.id, stored.title, stored.artistsText.orEmpty(), stored.durationText.orEmpty(), stored.thumbnailUrl.orEmpty())
                    .joinToString("|") { it.encodeBase64() }
            }
        )
    }

    /** Metadata is persisted with the taste ids so the taste page still has real songs after a restart,
     * even when the database has not finished rehydrating its library rows yet. */
    fun songMetadata(): List<Song> = node.get("tasteSongs", "")
        .lineSequence()
        .mapNotNull { line ->
            val fields = line.split('|').map { it.decodeBase64() }
            val id = fields.getOrNull(0)?.takeIf(String::isNotBlank) ?: return@mapNotNull null
            Song(
                id = id,
                title = fields.getOrNull(1).orEmpty().ifBlank { id },
                artistsText = fields.getOrNull(2).orEmpty().ifBlank { null },
                durationText = fields.getOrNull(3).orEmpty().ifBlank { null },
                thumbnailUrl = fields.getOrNull(4).orEmpty().ifBlank { null }
            )
        }
        .distinctBy { it.id }
        .toList()

    fun playCounts(): Map<String, Int> = node.get("playCounts", "")
        .lineSequence()
        .mapNotNull { line ->
            val parts = line.split('=', limit = 2)
            parts.getOrNull(0)?.trim()?.takeIf(String::isNotBlank)?.let { id -> id to (parts.getOrNull(1)?.toIntOrNull() ?: 0) }
        }
        .toMap()

    fun topPlayedIds(limit: Int = 50): List<String> = playCounts()
        .entries.sortedByDescending { it.value }.take(limit).map { it.key }

    fun clear() {
        node.remove("tasteHistory")
        node.remove("playCounts")
        node.remove("tasteSongs")
    }
}

internal data class CubicFollowedArtist(
    val id: String,
    val name: String,
    val thumbnailUrl: String?
)

internal object CubicArtistStore {
    private val node = Preferences.userRoot().node("CubicMusic/Desktop/Artists")

    fun followed(): List<CubicFollowedArtist> = node.get("followed", "")
        .lineSequence()
        .mapNotNull { line ->
            val fields = line.split('|')
            val id = fields.getOrNull(0)?.decodeBase64()?.takeIf(String::isNotBlank) ?: return@mapNotNull null
            val name = fields.getOrNull(1)?.decodeBase64().orEmpty().ifBlank { id }
            val thumbnail = fields.getOrNull(2)?.decodeBase64()?.takeIf(String::isNotBlank)
            CubicFollowedArtist(id, name, thumbnail)
        }
        .distinctBy { it.id }
        .toList()

    fun isFollowed(id: String): Boolean = followed().any { it.id == id }

    fun toggle(id: String, name: String?, thumbnailUrl: String?): Boolean {
        val current = followed().toMutableList()
        val index = current.indexOfFirst { it.id == id }
        if (index >= 0) current.removeAt(index) else current.add(0, CubicFollowedArtist(id, name.orEmpty().ifBlank { id }, thumbnailUrl))
        node.put("followed", current.joinToString("\n") { artist ->
            listOf(artist.id, artist.name, artist.thumbnailUrl.orEmpty()).joinToString("|") { it.encodeBase64() }
        })
        return index < 0
    }

    fun clear() = node.remove("followed")
}

private fun String.encodeBase64(): String = Base64.getUrlEncoder().withoutPadding()
    .encodeToString(toByteArray(StandardCharsets.UTF_8))

private fun String.decodeBase64(): String = runCatching {
    String(Base64.getUrlDecoder().decode(this), StandardCharsets.UTF_8)
}.getOrDefault("")

internal object CubicProfileStore {
    private val node = Preferences.userRoot().node("CubicMusic/Desktop/Profile")

    fun username(): String = node.get("username", "").trim()

    fun setUsername(value: String) {
        val cleaned = value.trim().replace(Regex("\\s+"), " ")
        if (cleaned.isBlank()) node.remove("username") else node.put("username", cleaned)
    }

    fun totalPlays(): Int = node.getInt("totalPlays", 0).coerceAtLeast(0)

    fun recordPlay(): Int {
        val updated = totalPlays() + 1
        node.putInt("totalPlays", updated)
        node.putLong("lastPlayedAt", System.currentTimeMillis())
        return updated
    }

    fun keepSidebarExpanded(): Boolean =
        if (!node.getBoolean("sidebarPreferenceSet", false)) false
        else node.getBoolean("keepSidebarExpanded", false)

    fun setKeepSidebarExpanded(value: Boolean) {
        node.putBoolean("keepSidebarExpanded", value)
        node.putBoolean("sidebarPreferenceSet", true)
    }

    fun clearListeningStats() {
        node.remove("totalPlays")
        node.remove("lastPlayedAt")
    }
}

internal object CubicPlaylistStore {
    private val node = Preferences.userRoot().node("CubicMusic/Desktop/Playlists")

    fun names(): List<String> = node.get("names", "")
        .lineSequence().map(String::trim).filter(String::isNotBlank).distinct().toList()

    fun create(rawName: String): List<String> {
        val name = rawName.trim().replace(Regex("\\s+"), " ")
        if (name.isBlank()) return names()
        val updated = (names() + name).distinctBy(String::lowercase)
        node.put("names", updated.joinToString("\n"))
        return updated
    }

    fun addSong(playlist: String, songId: String) {
        if (playlist !in names()) return
        val key = songKey(playlist)
        val updated = listOf(songId) + songIds(playlist).filterNot { it == songId }
        node.put(key, updated.joinToString("\n"))
    }

    fun songIds(playlist: String): List<String> = node.get(songKey(playlist), "")
        .lineSequence().map(String::trim).filter(String::isNotBlank).distinct().toList()

    fun clear() {
        names().forEach { node.remove(songKey(it)) }
        node.remove("names")
    }

    private fun songKey(name: String): String = "songs." + Base64.getUrlEncoder().withoutPadding()
        .encodeToString(name.toByteArray(StandardCharsets.UTF_8))
}
