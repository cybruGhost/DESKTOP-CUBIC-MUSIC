package app.it.fast4x.rimusic.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.DpSize
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.compose.ui.window.WindowPlacement
import androidx.compose.ui.window.WindowPosition
import androidx.compose.ui.window.WindowState
import app.it.fast4x.rimusic.ui.desktop.CubicAlbumDetailPage
import app.it.fast4x.rimusic.net.CubicPlayTorrioResolver
import app.it.fast4x.rimusic.ui.desktop.CubicAlbumsPage
import app.it.fast4x.rimusic.ui.desktop.CubicArtistDetailPage
import app.it.fast4x.rimusic.ui.desktop.CubicArtistStore
import app.it.fast4x.rimusic.ui.desktop.CubicArtistsPage
import app.it.fast4x.rimusic.ui.desktop.CubicLiveSearchPage
import app.it.fast4x.rimusic.ui.desktop.CubicRichBrowsePage
import app.it.fast4x.rimusic.ui.desktop.CubicColors
import app.it.fast4x.rimusic.ui.desktop.CubicDesktopTheme
import app.it.fast4x.rimusic.ui.desktop.CubicDiscoveryData
import app.it.fast4x.rimusic.ui.desktop.CubicDiscoveryRepository
import app.it.fast4x.rimusic.ui.desktop.CubicDownloadStore
import app.it.fast4x.rimusic.ui.desktop.CubicDownloadsPage
import app.it.fast4x.rimusic.ui.desktop.CubicNewReleasesPage
import app.it.fast4x.rimusic.ui.desktop.CubicEmptyState
import app.it.fast4x.rimusic.ui.desktop.CubicExpandedPlayerDialogV2
import app.it.fast4x.rimusic.ui.desktop.CubicNowPlayingPanelV2
import app.it.fast4x.rimusic.ui.desktop.CubicMiniPlayer
import app.it.fast4x.rimusic.ui.desktop.CubicPlayerRailV2
import app.it.fast4x.rimusic.ui.desktop.CubicPlaylistsPage
import app.it.fast4x.rimusic.ui.desktop.CubicProfilePage
import app.it.fast4x.rimusic.ui.desktop.CubicRoutes
import app.it.fast4x.rimusic.ui.desktop.CubicSearchPage
import app.it.fast4x.rimusic.ui.desktop.CubicSettingsPage
import app.it.fast4x.rimusic.ui.desktop.CubicSidebar
import app.it.fast4x.rimusic.ui.desktop.CubicSongCollection
import app.it.fast4x.rimusic.ui.desktop.CubicSongsPage
import app.it.fast4x.rimusic.ui.desktop.CubicSongsDiscoveryPage
import app.it.fast4x.rimusic.ui.desktop.CubicUserPlaylistsPage
import app.it.fast4x.rimusic.ui.desktop.CubicTastePage
import app.it.fast4x.rimusic.ui.desktop.CubicPlaylistStore
import app.it.fast4x.rimusic.ui.desktop.CubicProfileStore
import app.it.fast4x.rimusic.ui.desktop.CubicTasteStore
import app.it.fast4x.rimusic.ui.desktop.CubicTopBar
import app.it.fast4x.rimusic.ui.screens.MoodScreen
import app.it.fast4x.rimusic.ui.screens.PlaylistScreen
import app.it.fast4x.rimusic.utils.asSong
import database.DB
import database.entities.Song
import database.entities.Album
import database.entities.SongAlbumMap
import database.entities.SongEntity
import it.fast4x.innertube.Innertube
import it.fast4x.innertube.models.bodies.NextBody
import it.fast4x.innertube.requests.player
import it.fast4x.innertube.requests.relatedPage
import it.fast4x.innertube.utils.NewPipeUtils
import it.fast4x.lrclib.LrcLib
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import okhttp3.OkHttpClient
import windows.FfmpegAudioController
import java.awt.Frame
import java.awt.GraphicsEnvironment
import java.awt.Window

@Composable
fun CubicDesktopAppV2(windowState: WindowState, hostWindow: Window) {
    val navController = rememberNavController()
    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = backStackEntry?.destination?.route ?: CubicRoutes.Browse
    val playbackHttpClient = remember { OkHttpClient() }
    val downloadHttpClient = remember { OkHttpClient() }
    val controller = remember(playbackHttpClient) { FfmpegAudioController(playbackHttpClient) }
    val playerState by controller.state.collectAsState()
    val database = remember { DB }
    val scope = rememberCoroutineScope()

    val librarySongs by remember(database) { database.songsByTitleAsc() }.collectAsState(initial = emptyList())
    val libraryAlbums by remember(database) { database.getAllAlbums() }.collectAsState(initial = emptyList())
    val sessionHistory = remember { mutableStateListOf<Song>() }
    val playbackQueue = remember { mutableStateListOf<Song>() }
    val downloadProgress = remember { mutableStateMapOf<String, Float>() }

    var currentSong by remember { mutableStateOf<Song?>(null) }
    var currentQueueIndex by remember { mutableIntStateOf(-1) }
    var activeStreamUrl by remember { mutableStateOf<String?>(null) }
    var playbackMessage by remember { mutableStateOf<String?>(null) }
    var isResolving by remember { mutableStateOf(false) }
    var showExpandedPlayer by remember { mutableStateOf(false) }
    var localPlaylists by remember { mutableStateOf(CubicPlaylistStore.names()) }
    var downloadedIds by remember { mutableStateOf(CubicDownloadStore.downloadedSongIds()) }
    var downloadedFiles by remember { mutableStateOf(CubicDownloadStore.downloadedFiles()) }
    var profileName by remember { mutableStateOf(CubicProfileStore.username()) }
    var totalPlays by remember { mutableIntStateOf(CubicProfileStore.totalPlays()) }
    var tasteRefresh by remember { mutableIntStateOf(0) }
    var keepSidebarExpanded by remember { mutableStateOf(CubicProfileStore.keepSidebarExpanded()) }
    var syncedLyrics by remember { mutableStateOf<String?>(null) }
    var plainLyrics by remember { mutableStateOf<String?>(null) }
    var lyricsLoading by remember { mutableStateOf(false) }
    var completionHandledFor by remember { mutableStateOf<String?>(null) }
    var playbackRetryCount by remember { mutableIntStateOf(0) }
    var playRequestGeneration by remember { mutableIntStateOf(0) }
    var pendingResumePosition by remember { mutableStateOf(0L) }

    var selectedMood by remember { mutableStateOf<Innertube.Mood.Item?>(null) }
    var selectedAlbumId by remember { mutableStateOf<String?>(null) }
    var isRadioExpanding by remember { mutableStateOf(false) }
    var miniMode by remember { mutableStateOf(false) }
    var selectedPlaylistId by remember { mutableStateOf<String?>(null) }
    var selectedArtistId by remember { mutableStateOf<String?>(null) }
    var searchQuery by remember { mutableStateOf("") }
    var activeSearchQuery by remember { mutableStateOf("") }

    var discovery by remember { mutableStateOf<CubicDiscoveryData?>(null) }
    var discoveryLoading by remember { mutableStateOf(true) }
    var discoveryError by remember { mutableStateOf<String?>(null) }
    var discoveryRefresh by remember { mutableIntStateOf(0) }

    DisposableEffect(Unit) {
        NewPipeUtils.init { playbackHttpClient }
        controller.onStreamFailure = { failedUrl, positionMs ->
            scope.launch {
                val song = currentSong
                if (song == null || activeStreamUrl != failedUrl || isResolving || playbackRetryCount >= 2 || !failedUrl.startsWith("http")) return@launch
                playbackRetryCount++
                CubicPlayTorrioResolver.markFailed(song.id, failedUrl)
                isResolving = true
                playbackMessage = "Refreshing stream..."
                activeStreamUrl = null
                val refreshed = resolveCubicDesktopPlaybackUrlV2(song.id, playbackHttpClient)
                if (refreshed != null) {
                    pendingResumePosition = positionMs
                    activeStreamUrl = refreshed
                    playbackMessage = "Playing"
                } else {
                    playbackMessage = "Playback failed - retry the track"
                }
                isResolving = false
            }
        }
        onDispose {
            controller.onStreamFailure = null
            controller.dispose()
        }
    }

    LaunchedEffect(librarySongs) {
        if (librarySongs.isNotEmpty() && sessionHistory.isEmpty()) {
            val byId = (librarySongs.map { it.song } + CubicTasteStore.songMetadata())
                .associateBy { it.id }
            sessionHistory.addAll(CubicTasteStore.ids().mapNotNull { byId[it] })
        }
    }

    // Older desktop builds stored only ids and the aggregate play counter.  Rehydrate
    // those ids once from InnerTube so My Taste can show the real title/artist/artwork
    // instead of an empty page after an upgrade.
    LaunchedEffect(Unit) {
        val known = CubicTasteStore.songMetadata().mapTo(mutableSetOf()) { it.id }
        CubicTasteStore.topPlayedIds(20).filterNot { it in known }.forEach { videoId ->
            val details = withTimeoutOrNull(5_000L) {
                withContext(Dispatchers.IO) { Innertube.player(videoId = videoId)?.getOrNull()?.videoDetails }
            } ?: return@forEach
            val title = details.title?.takeIf(String::isNotBlank) ?: return@forEach
            val seconds = details.lengthSeconds?.toLongOrNull()
            val duration = seconds?.let { "${it / 60}:${(it % 60).toString().padStart(2, '0')}" }
            val thumbnail = details.thumbnail?.thumbnails?.maxByOrNull { it.width ?: 0 }?.url
            val song = Song(videoId, title, details.author, duration, thumbnail)
            CubicTasteStore.saveMetadata(song)
            database.upsert(song)
            tasteRefresh++
        }
    }

    LaunchedEffect(Unit) {
        snapshotFlow { windowState.isMinimized }.distinctUntilChanged().collect { minimized ->
            if (minimized) {
                // Restore the native frame first; otherwise Windows can keep the old maximized bounds
                // while Compose only resizes the content inside that large frame.
                val frame = hostWindow as? Frame
                frame?.extendedState = Frame.NORMAL
                frame?.isResizable = false
                windowState.isMinimized = false
                windowState.placement = WindowPlacement.Floating
                // Keep the minimized player discreet: it is a floating control, not a second window.
                windowState.size = DpSize(420.dp, 92.dp)
                windowState.position = WindowPosition(Alignment.BottomEnd)
                delay(150)
                frame?.let {
                    val screen = it.graphicsConfiguration?.bounds
                        ?: GraphicsEnvironment.getLocalGraphicsEnvironment().maximumWindowBounds
                    val scale = it.graphicsConfiguration?.defaultTransform?.scaleX ?: 1.0
                    val width = (420 * scale).toInt()
                    val height = (92 * scale).toInt()
                    it.extendedState = Frame.NORMAL
                    it.setBounds(screen.x + screen.width - width - 18, screen.y + screen.height - height - 18, width, height)
                    it.validate()
                }
                hostWindow.isAlwaysOnTop = true
                miniMode = true
            }
        }
    }

    fun restoreFullPlayer() {
        miniMode = false
        hostWindow.isAlwaysOnTop = false
        (hostWindow as? Frame)?.isResizable = true
        windowState.placement = WindowPlacement.Maximized
        (hostWindow as? Frame)?.extendedState = Frame.MAXIMIZED_BOTH
    }

    LaunchedEffect(activeStreamUrl) {
        activeStreamUrl?.let { url ->
            val resumeAt = pendingResumePosition
            pendingResumePosition = 0L
            controller.loadAt(url, resumeAt, currentSong?.id)
            controller.play()
        }
    }

    LaunchedEffect(discoveryRefresh, currentSong?.id) {
        discoveryLoading = true
        discoveryError = null
        val personalSeeds = buildList {
            currentSong?.id?.let(::add)
            addAll(sessionHistory.map { it.id })
            addAll(CubicTasteStore.ids())
            addAll(librarySongs.filter { it.song.isLiked }.map { it.song.id })
        }.filter(String::isNotBlank).distinct()
        val seed = personalSeeds.getOrNull(discoveryRefresh.mod(personalSeeds.size.coerceAtLeast(1))) ?: "HZnNt9nnEhw"
        CubicDiscoveryRepository.load(force = discoveryRefresh > 0 || personalSeeds.isNotEmpty(), seedVideoId = seed)
            .onSuccess { discovery = it }
            .onFailure { discoveryError = it.message ?: "The live catalog is unavailable." }
        discoveryLoading = false
    }

    LaunchedEffect(currentSong?.id) {
        val song = currentSong
        syncedLyrics = null
        plainLyrics = null
        lyricsLoading = song != null
        if (song != null) {
            val artist = song.artistsText.orEmpty().substringBefore(',').trim()
            if (artist.isNotBlank()) {
                val matches = LrcLib.lyrics(artist, song.title)?.getOrNull().orEmpty()
                syncedLyrics = matches.firstNotNullOfOrNull { it.syncedLyrics?.takeIf(String::isNotBlank) }
                plainLyrics = matches.firstNotNullOfOrNull { it.plainLyrics?.takeIf(String::isNotBlank) }
            }
        }
        lyricsLoading = false
    }

    fun navigate(route: String) {
        if (route in CubicRoutes.primary) {
            searchQuery = ""
            activeSearchQuery = ""
        }
        navController.navigate(route) { launchSingleTop = true; restoreState = true }
    }

    fun openSearch() {
        searchQuery.trim().takeIf(String::isNotEmpty)?.let {
            activeSearchQuery = it
        }
    }

    LaunchedEffect(searchQuery) {
        val query = searchQuery.trim()
        if (query.length >= 2) {
            delay(250)
            activeSearchQuery = query
        }
    }

    fun startSong(song: Song, queueIndex: Int) {
        val targetSong = librarySongs.firstOrNull { it.song.id == song.id }?.song ?: song
        val requestGeneration = playRequestGeneration + 1
        playRequestGeneration = requestGeneration
        currentSong = targetSong
        activeStreamUrl = null
        isResolving = true
        controller.stop()
        controller.setExpectedDuration(targetSong.durationText.cubicDurationMillis())
        currentQueueIndex = queueIndex
        completionHandledFor = null
        playbackRetryCount = 0
        pendingResumePosition = 0L
        sessionHistory.removeAll { it.id == song.id }
        sessionHistory.add(0, targetSong)
        totalPlays = CubicProfileStore.recordPlay()
        scope.launch { database.upsert(targetSong) }
        scope.launch {
            CubicTasteStore.record(targetSong)
            if (requestGeneration != playRequestGeneration) return@launch
            playbackMessage = null
            val cached = CubicDownloadStore.localFile(song.id)
            val resolved = cached?.takeIf { it.exists() }?.toURI()?.toString()
                ?: resolveCubicDesktopPlaybackUrlV2(song.id, playbackHttpClient)
            if (requestGeneration != playRequestGeneration) return@launch
            if (resolved != null) {
                activeStreamUrl = resolved
                playbackMessage = if (cached != null) "Playing offline" else "Playing"
            } else {
                playbackMessage = "Stream unavailable Ã¢â‚¬â€ try another track"
            }
            isResolving = false
        }
    }

    fun playSong(song: Song) {
        playbackQueue.clear()
        playbackQueue.add(song)
        startSong(song, 0)
    }


    fun playSongsAsQueue(songs: List<Song>, startIndex: Int = 0) {
        val unique = songs.distinctBy { it.id }
        if (unique.isEmpty()) return
        playbackQueue.clear()
        playbackQueue.addAll(unique)
        val safeIndex = startIndex.coerceIn(playbackQueue.indices)
        startSong(playbackQueue[safeIndex], safeIndex)
    }
    fun playQueueIndex(index: Int) {
        playbackQueue.getOrNull(index)?.let { startSong(it, index) }
    }

    fun playPrevious() = playQueueIndex(currentQueueIndex - 1)
    fun playNext() = playQueueIndex(currentQueueIndex + 1)

    fun toggleFavorite() {
        val updated = currentSong?.toggleLike() ?: return
        currentSong = updated
        playbackQueue.indexOfFirst { it.id == updated.id }.takeIf { it >= 0 }?.let { playbackQueue[it] = updated }
        sessionHistory.indexOfFirst { it.id == updated.id }.takeIf { it >= 0 }?.let { sessionHistory[it] = updated }
        scope.launch { database.upsert(updated) }
    }

    fun addSongToPlaylist(song: Song, playlist: String) {
        CubicPlaylistStore.addSong(playlist, song.id)
        scope.launch { database.upsert(song) }
        playbackMessage = "Added to $playlist"
        localPlaylists = CubicPlaylistStore.names()
    }

    fun clearDesktopData() {
        controller.stop()
        currentSong = null
        activeStreamUrl = null
        isResolving = false
        playbackQueue.clear()
        sessionHistory.clear()
        currentQueueIndex = -1
        showExpandedPlayer = false
        syncedLyrics = null
        plainLyrics = null
        scope.launch {
            librarySongs.forEach { database.delete(it.song) }
        withContext(Dispatchers.IO) {
                CubicDownloadStore.clear()
                CubicTasteStore.clear()
                CubicArtistStore.clear()
                CubicProfileStore.clearListeningStats()
                CubicPlaylistStore.clear()
            }
            totalPlays = 0
            downloadedIds = emptySet()
            downloadedFiles = emptyList()
            localPlaylists = emptyList()
            playbackMessage = "Desktop data cleared"
        }
    }

    fun downloadSong(song: Song) {
        if (song.id in downloadedIds || downloadProgress.containsKey(song.id)) return
        downloadProgress[song.id] = 0f
        scope.launch {
            val streamUrl = resolveCubicDesktopDownloadUrlV2(song.id, downloadHttpClient)
            val result = if (streamUrl == null) Result.failure(IllegalStateException("No downloadable stream was returned."))
            else CubicDownloadStore.download(downloadHttpClient, song, streamUrl) { progress ->
                scope.launch { downloadProgress[song.id] = progress }
            }
            result.onSuccess {
                database.upsert(song)
                downloadedIds = downloadedIds + song.id
                downloadedFiles = CubicDownloadStore.downloadedFiles()
                playbackMessage = if (currentSong?.id == song.id) "Downloaded for offline playback" else playbackMessage
            }.onFailure {
                playbackMessage = "Download failed: ${it.message ?: "unknown error"}"
            }
            downloadProgress.remove(song.id)
        }
    }

    fun deleteDownload(file: java.io.File) {
        if (CubicDownloadStore.delete(file)) {
            downloadedFiles = CubicDownloadStore.downloadedFiles()
            downloadedIds = CubicDownloadStore.downloadedSongIds()
            playbackMessage = "Download removed"
        }
    }

    LaunchedEffect(currentSong?.id, currentQueueIndex, playbackQueue.size) {
        val seed = currentSong?.id
        val shouldExpand = seed != null && currentQueueIndex >= playbackQueue.lastIndex - 3 && !isRadioExpanding
        if (shouldExpand) {
            isRadioExpanding = true
            try {
                val additions = Innertube.relatedPage(NextBody(videoId = seed))?.getOrNull()?.songs.orEmpty()
                    .map { it.asSong }
                    .filter { candidate -> playbackQueue.none { it.id == candidate.id } }
                    .distinctBy { it.id }
                playbackQueue.addAll(additions)
            } finally {
                isRadioExpanding = false
            }
        }
    }

    LaunchedEffect(playerState.isPlaying, playerState.timestamp, playerState.duration, currentSong?.id, isResolving, activeStreamUrl) {
        val songId = currentSong?.id
        if (songId != null && !isResolving && activeStreamUrl != null && !playerState.isPlaying && playerState.duration > 0 &&
            playerState.timestamp >= playerState.duration - 900 && completionHandledFor != songId
        ) {
            completionHandledFor = songId
            // Let the radio request already in flight finish before deciding that the queue is empty.
            while (isRadioExpanding) delay(120)
            if (currentQueueIndex >= playbackQueue.lastIndex) {
                isRadioExpanding = true
                try {
                    val additions = Innertube.relatedPage(NextBody(videoId = songId))?.getOrNull()?.songs.orEmpty()
                        .map { it.asSong }
                        .filter { candidate -> playbackQueue.none { it.id == candidate.id } }
                        .distinctBy { it.id }
                    playbackQueue.addAll(additions)
                } finally {
                    isRadioExpanding = false
                }
            }
            if (currentSong?.id == songId && currentQueueIndex < playbackQueue.lastIndex) playNext()
            else if (currentSong?.id == songId) playbackMessage = "Radio is finding another track…"
        }

    }
    CubicDesktopTheme {
        Box(Modifier.fillMaxSize().background(CubicColors.Background)) {
            Column(
                Modifier.fillMaxSize().padding(
                    horizontal = if (miniMode) 4.dp else 18.dp,
                    vertical = if (miniMode) 4.dp else 16.dp
                )
            ) {
        if (miniMode) {
            CubicMiniPlayer(controller, currentSong, isResolving, currentQueueIndex in 0 until playbackQueue.lastIndex, ::playNext, ::restoreFullPlayer)
        } else {
                BoxWithConstraints(Modifier.weight(1f).fillMaxWidth().clip(RoundedCornerShape(28.dp)).background(CubicColors.Window)) {
                    val showNowPlaying = maxWidth >= 1220.dp
                    Row(Modifier.fillMaxSize()) {
                        CubicSidebar(currentRoute, ::navigate, keepSidebarExpanded)
                        Column(Modifier.weight(1f).fillMaxSize()) {
                            CubicTopBar(
                                currentRoute, currentRoute !in CubicRoutes.primary, searchQuery, librarySongs.isNotEmpty(),
                                                                onBack = { navController.popBackStack() }, onSearchQueryChange = { searchQuery = it }, onSearch = ::openSearch,
                                onNavigate = ::navigate, onShuffle = { librarySongs.randomOrNull()?.song?.let(::playSong) }
                            )
                            Box(Modifier.weight(1f).fillMaxWidth().background(CubicColors.Window)) {
                                val liveQuery = searchQuery.trim()
                                if (liveQuery.length >= 2) {
                                    CubicLiveSearchPage(
                                        query = liveQuery,
                                        onSongClick = ::playSong,
                                        onAlbumClick = { selectedAlbumId = it; searchQuery = ""; navigate(CubicRoutes.Album) },
                                        onArtistClick = { selectedArtistId = it; searchQuery = ""; navigate(CubicRoutes.Artist) },
                                        onPlaylistClick = { selectedPlaylistId = it; searchQuery = ""; navigate(CubicRoutes.Playlist) }
                                    )
                                } else NavHost(navController, CubicRoutes.Browse, Modifier.fillMaxSize()) {
                                    composable(CubicRoutes.Browse) {
                                        CubicRichBrowsePage(discovery, discoveryLoading, discoveryError, { discoveryRefresh++ }, ::playSong, { songs -> playSongsAsQueue(songs) },
                                            { selectedAlbumId = it; navigate(CubicRoutes.Album) },
                                            { selectedArtistId = it; navigate(CubicRoutes.Artist) },
                                            { selectedPlaylistId = it; navigate(CubicRoutes.Playlist) },
                                            { selectedMood = it; navigate(CubicRoutes.Mood) })
                                    }
                                    composable(CubicRoutes.Songs) { CubicSongsDiscoveryPage(discovery, discoveryLoading, discoveryError, librarySongs, { discoveryRefresh++ }, ::playSong, { selectedArtistId = it; navigate(CubicRoutes.Artist) }) { songs -> playSongsAsQueue(songs) } }
                                    composable(CubicRoutes.NewReleases) { CubicNewReleasesPage(discovery, discoveryLoading, discoveryError, { discoveryRefresh++ }) { selectedAlbumId = it; navigate(CubicRoutes.Album) } }
                                    composable(CubicRoutes.Recent) { CubicSongsPage(sessionHistory.map(::SongEntity), CubicSongCollection.Recent, currentSong?.id, ::playSong) }
                                    composable(CubicRoutes.Favorites) { CubicSongsPage(librarySongs, CubicSongCollection.Favorites, currentSong?.id, ::playSong) }
                                    composable(CubicRoutes.Taste) {
                                        CubicTastePage(
                                            librarySongs = librarySongs,
                                            refreshKey = tasteRefresh,
                                            currentSongId = currentSong?.id,
                                            onSongClick = ::playSong,
                                            onArtistClick = { selectedArtistId = it; navigate(CubicRoutes.Artist) }
                                        )
                                    }
                                    composable(CubicRoutes.Cached) {
                                        CubicDownloadsPage(
                                            files = downloadedFiles,
                                            librarySongs = librarySongs,
                                            currentSongId = currentSong?.id,
                                            onSongClick = ::playSong,
                                            onReveal = { CubicDownloadStore.reveal(it) },
                                            onDelete = ::deleteDownload
                                        )
                                    }
                                    composable(CubicRoutes.Albums) { CubicAlbumsPage(libraryAlbums) { selectedAlbumId = it; navigate(CubicRoutes.Album) } }
                                    composable(CubicRoutes.Artists) { CubicArtistsPage(discovery, discoveryLoading, discoveryError, { discoveryRefresh++ }) { selectedArtistId = it; navigate(CubicRoutes.Artist) } }
                                    composable(CubicRoutes.Playlists) {
                                        CubicUserPlaylistsPage(
                                            discovery, localPlaylists, librarySongs,
                                            onCreate = { localPlaylists = CubicPlaylistStore.create(it) },
                                            onSongClick = ::playSong
                                        ) { selectedPlaylistId = it; navigate(CubicRoutes.Playlist) }
                                    }
                                    composable(CubicRoutes.Search) { CubicLiveSearchPage(activeSearchQuery, ::playSong, { selectedAlbumId = it; navigate(CubicRoutes.Album) }, { selectedArtistId = it; navigate(CubicRoutes.Artist) }, { selectedPlaylistId = it; navigate(CubicRoutes.Playlist) }) }
                                    composable(CubicRoutes.Album) {
                                        CubicAlbumDetailPage(
                                            browseId = selectedAlbumId.orEmpty(),
                                            onAlbumSongClick = { songs, index -> playSongsAsQueue(songs, index) },
                                            onAlbumClick = { selectedAlbumId = it; navigate(CubicRoutes.Album) },
                                            onSaveAlbum = { album, songs ->
                                                scope.launch {
                                                    database.upsert(album)
                                                    songs.forEachIndexed { index, song ->
                                                        database.upsert(song)
                                                        database.upsert(SongAlbumMap(songId = song.id, albumId = album.id, position = index))
                                                    }
                                                    playbackMessage = "Added ${album.title.orEmpty()} to Albums"
                                                }
                                            }
                                        )
                                    }
                                    composable(CubicRoutes.Artist) { CubicArtistDetailPage(selectedArtistId.orEmpty(), ::playSong, { selectedAlbumId = it; navigate(CubicRoutes.Album) }, { selectedPlaylistId = it; navigate(CubicRoutes.Playlist) }) }
                                    composable(CubicRoutes.Playlist) { PlaylistScreen(selectedPlaylistId.orEmpty(), ::playSong, { selectedAlbumId = it; navigate(CubicRoutes.Album) }, { navController.popBackStack() }) }
                                    composable(CubicRoutes.Mood) {
                                        selectedMood?.let { mood -> MoodScreen(mood, { selectedAlbumId = it; navigate(CubicRoutes.Album) }, { selectedArtistId = it; navigate(CubicRoutes.Artist) }, { selectedPlaylistId = it; navigate(CubicRoutes.Playlist) }) }
                                            ?: CubicEmptyState("No mood selected", "Return to Browse and choose a mood.")
                                    }
                                    composable(CubicRoutes.Profile) {
                                        CubicProfilePage(profileName, librarySongs.size, downloadedIds.size, librarySongs.count { it.song.isLiked }, totalPlays) { navigate(CubicRoutes.Settings) }
                                    }
                                    composable(CubicRoutes.Settings) {
                                        CubicSettingsPage(
                                            downloadCount = downloadedIds.size,
                                            username = profileName,
                                            keepSidebarExpanded = keepSidebarExpanded,
                                            onUsernameSave = { value ->
                                                CubicProfileStore.setUsername(value)
                                                profileName = CubicProfileStore.username()
                                            },
                                            onKeepSidebarExpandedChange = { value ->
                                                CubicProfileStore.setKeepSidebarExpanded(value)
                                                keepSidebarExpanded = value
                                            },
                                            onClearData = ::clearDesktopData
                                        )
                                    }
                                }
                            }
                        }
                        if (showNowPlaying) {
                            CubicNowPlayingPanelV2(currentSong, isResolving, playbackMessage, playbackQueue, currentQueueIndex, syncedLyrics, plainLyrics, playerState.timestamp, lyricsLoading,
                                downloadedIds, downloadProgress, ::toggleFavorite, localPlaylists, ::playQueueIndex, ::addSongToPlaylist, ::downloadSong) { currentSong?.let { showExpandedPlayer = true } }
                        }
                    }
                }
                Spacer(Modifier.height(12.dp))
                Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                    CubicPlayerRailV2(controller, currentSong, isResolving, playbackMessage, currentQueueIndex > 0,
                        currentQueueIndex in 0 until playbackQueue.lastIndex, ::playPrevious, ::playNext,
                        { currentSong?.let { showExpandedPlayer = true } }, Modifier.fillMaxWidth(0.82f))
                }
            }
            val expandedSong = currentSong
            if (showExpandedPlayer && expandedSong != null) {
                CubicExpandedPlayerDialogV2(controller, expandedSong, isResolving, playbackMessage, playbackQueue, currentQueueIndex,
                    syncedLyrics, plainLyrics, playerState.timestamp, lyricsLoading, downloadedIds, downloadProgress, ::toggleFavorite, localPlaylists, ::playQueueIndex, ::addSongToPlaylist, ::downloadSong,
                    ::playPrevious, ::playNext) { showExpandedPlayer = false }
            }
        }
    }
        }
}

private suspend fun resolveCubicDesktopPlaybackUrlV2(videoId: String, httpClient: OkHttpClient): String? =
    CubicPlayTorrioResolver.resolve(videoId, httpClient)

private suspend fun resolveCubicDesktopDownloadUrlV2(videoId: String, httpClient: OkHttpClient): String? =
    CubicPlayTorrioResolver.resolveForDownload(videoId, httpClient)

private fun String?.cubicDurationMillis(): Long {
    val parts = this?.split(':')?.mapNotNull(String::toLongOrNull) ?: return 0L
    if (parts.isEmpty()) return 0L
    return parts.fold(0L) { total, part ->
        if (part < 0L || total > Long.MAX_VALUE / 60L) return 0L
        total * 60L + part
    } * 1000L
}

