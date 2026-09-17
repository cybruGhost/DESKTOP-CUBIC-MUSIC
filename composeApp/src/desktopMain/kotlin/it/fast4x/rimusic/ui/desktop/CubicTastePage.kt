package app.it.fast4x.rimusic.ui.desktop

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.FavoriteBorder
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import database.entities.Song
import database.entities.SongEntity

private enum class CubicTasteTab(val label: String) {
    Collective("Collective"),
    FollowedArtists("Followed artists"),
    Top50("Top 50")
}

@Composable
internal fun CubicTastePage(
    librarySongs: List<SongEntity>,
    refreshKey: Int = 0,
    currentSongId: String?,
    onSongClick: (Song) -> Unit,
    onArtistClick: (String) -> Unit
) {
    var selectedTab by remember { mutableStateOf(CubicTasteTab.Collective) }
    var followedArtists by remember { mutableStateOf(CubicArtistStore.followed()) }
    val playCounts = CubicTasteStore.playCounts()
    val storedSongs = remember(librarySongs, refreshKey) { CubicTasteStore.songMetadata() }
    val songsById = remember(librarySongs, storedSongs) {
        (storedSongs + librarySongs.map { it.song }).associateBy { it.id }
    }
    val topSongs = CubicTasteStore.topPlayedIds(50).mapNotNull { songsById[it] }
    val state = rememberLazyListState()
    val scope = rememberCoroutineScope()

    LazyColumn(
        state = state,
        modifier = Modifier.fillMaxSize().padding(horizontal = 28.dp).cubicKeyboardScroll(state, scope)
    ) {
        item {
            Row(
                Modifier.fillMaxWidth().padding(top = 22.dp, bottom = 17.dp),
                verticalAlignment = Alignment.Bottom,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(5.dp)) {
                    Text("My taste", color = CubicColors.Text, fontSize = 27.sp, fontWeight = FontWeight.Bold)
                    Text("Your listening, your artists, your rotation", color = CubicColors.TextSecondary, fontSize = 12.sp)
                }
                Text("${followedArtists.size} followed", color = CubicColors.TextMuted, fontSize = 11.sp)
            }
        }
        item {
            Row(
                Modifier.fillMaxWidth().clip(RoundedCornerShape(15.dp)).background(CubicColors.PanelRaised).padding(4.dp),
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                CubicTasteTab.values().forEach { tab ->
                    val selected = selectedTab == tab
                    Text(
                        tab.label,
                        color = if (selected) CubicColors.Background else CubicColors.TextSecondary,
                        fontSize = 11.sp,
                        fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
                        modifier = Modifier.weight(1f).clip(RoundedCornerShape(11.dp))
                            .background(if (selected) CubicColors.Accent else androidx.compose.ui.graphics.Color.Transparent)
                            .clickable { selectedTab = tab }
                            .padding(horizontal = 10.dp, vertical = 11.dp)
                    )
                }
            }
            Spacer(Modifier.height(22.dp))
        }

        when (selectedTab) {
            CubicTasteTab.Collective -> {
                if (followedArtists.isNotEmpty()) {
                    item { CubicSectionTitle("Followed artists", "Artists you chose to keep close"); Spacer(Modifier.height(10.dp)) }
                    items(followedArtists.take(12), key = { "taste-artist-${it.id}" }) { artist ->
                        TasteArtistRow(artist, onArtistClick) { artistToRemove ->
                            CubicArtistStore.toggle(artistToRemove.id, artistToRemove.name, artistToRemove.thumbnailUrl)
                            followedArtists = followedArtists.filterNot { it.id == artistToRemove.id }
                        }
                    }
                }
                if (topSongs.isNotEmpty()) {
                    item { Spacer(Modifier.height(24.dp)); CubicSectionTitle("Your top songs", "Ranked by plays on this desktop"); Spacer(Modifier.height(10.dp)) }
                    itemsIndexed(topSongs.take(10), key = { _, song -> "taste-song-${song.id}" }) { index, song ->
                        TasteSongRow(index + 1, song, playCounts[song.id] ?: 0, song.id == currentSongId) { onSongClick(song) }
                    }
                }
                if (followedArtists.isEmpty() && topSongs.isEmpty()) item {
                    CubicEmptyState("Your taste is still forming", "Follow artists or play a few songs to build this page.")
                }
            }
            CubicTasteTab.FollowedArtists -> {
                if (followedArtists.isEmpty()) item { CubicEmptyState("No followed artists yet", "Tap the heart on any artist to keep them here.") }
                else items(followedArtists, key = { "followed-${it.id}" }) { artist ->
                    TasteArtistRow(artist, onArtistClick) { artistToRemove ->
                        CubicArtistStore.toggle(artistToRemove.id, artistToRemove.name, artistToRemove.thumbnailUrl)
                        followedArtists = followedArtists.filterNot { it.id == artistToRemove.id }
                    }
                }
            }
            CubicTasteTab.Top50 -> {
                if (topSongs.isEmpty()) item { CubicEmptyState("No play counts yet", "Play songs on this desktop and your Top 50 will grow here.") }
                else itemsIndexed(topSongs, key = { _, song -> "top50-${song.id}" }) { index, song ->
                    TasteSongRow(index + 1, song, playCounts[song.id] ?: 0, song.id == currentSongId) { onSongClick(song) }
                }
            }
        }
        item { Spacer(Modifier.height(34.dp)) }
    }
}

@Composable
private fun TasteArtistRow(
    artist: CubicFollowedArtist,
    onArtistClick: (String) -> Unit,
    onUnfollow: (CubicFollowedArtist) -> Unit
) {
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).clickable { onArtistClick(artist.id) }.padding(horizontal = 10.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        CubicArtwork(artist.thumbnailUrl, Modifier.size(48.dp), 24.dp)
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
            Text(artist.name, color = CubicColors.Text, fontSize = 13.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text("Followed artist", color = CubicColors.TextSecondary, fontSize = 11.sp)
        }
        IconButton(onClick = { onUnfollow(artist) }, modifier = Modifier.size(34.dp)) {
            Icon(Icons.Rounded.Favorite, "Unfollow ${artist.name}", tint = CubicColors.Accent, modifier = Modifier.size(18.dp))
        }
    }
}

@Composable
private fun TasteSongRow(index: Int, song: Song, plays: Int, isPlaying: Boolean, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp))
            .background(if (isPlaying) CubicColors.AccentSoft else androidx.compose.ui.graphics.Color.Transparent)
            .clickable(onClick = onClick).padding(horizontal = 10.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text(index.toString().padStart(2, '0'), color = CubicColors.TextMuted, fontSize = 10.sp, modifier = Modifier.width(24.dp))
        CubicArtwork(song.thumbnailUrl, Modifier.size(46.dp), 10.dp)
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
            Text(song.title, color = if (isPlaying) CubicColors.Accent else CubicColors.Text, fontSize = 12.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(song.artistsText.orEmpty(), color = CubicColors.TextSecondary, fontSize = 10.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        Text("$plays plays", color = CubicColors.TextMuted, fontSize = 10.sp)
        Icon(Icons.Rounded.PlayArrow, "Play ${song.title}", tint = CubicColors.Accent, modifier = Modifier.size(18.dp))
    }
}
