package com.auramusic.app.ui.screens

import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.QueueMusic
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Clear
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.ContentPaste
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.DownloadDone
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.Link
import androidx.compose.material.icons.rounded.MoreHoriz
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import com.auramusic.app.ui.components.AuraAddToPlaylistModal
import com.auramusic.app.ui.components.AuraCreateOrImportModal
import com.auramusic.app.ui.components.AuraCreatePlaylistModal
import com.auramusic.app.ui.components.AuraPlaylistOptionsModal
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import androidx.compose.ui.window.Dialog
import coil.compose.AsyncImage
import com.auramusic.app.core.media.AuraPlayerManager
import com.auramusic.app.domain.model.AuraPlaylist
import com.auramusic.app.domain.model.AuraTrack
import com.auramusic.app.domain.model.ListeningStats
import com.auramusic.app.ui.components.AuraTrackOptionsModal
import com.auramusic.app.ui.theme.AuraCircleButtonBg
import com.auramusic.app.ui.theme.AuraNeonLime
import com.auramusic.app.ui.theme.AuraPillInactive
import com.auramusic.app.ui.theme.AuraPillInactiveText
import com.auramusic.app.ui.theme.AuraPlayGreen
import com.auramusic.app.ui.theme.SpotifyCard
import com.auramusic.app.ui.theme.SpotifyPitchBlack
import com.auramusic.app.ui.theme.TextPrimary
import com.auramusic.app.ui.theme.TextSecondary

@Composable
fun LibraryScreen(
    favorites: List<AuraTrack>,
    downloads: List<AuraTrack>,
    playlists: List<AuraPlaylist>,
    stats: ListeningStats,
    recentlyPlayed: List<AuraTrack> = emptyList(),
    downloadingIds: Set<String>,
    downloadProgressMap: Map<String, Float> = emptyMap(),
    currentPlayingId: String? = null,
    isPlaying: Boolean = false,
    spotifyImportStatus: String? = null,
    importedPlaylistSuccess: AuraPlaylist? = null,
    initialPlaylistId: String? = null,
    initialScrollToTrackId: String? = null,
    onClearScrollTarget: () -> Unit = {},
    onSetActiveUserPlaylist: (AuraPlaylist) -> Unit = {},
    openedFromHomeScreen: Boolean = false,
    onNavigateToHome: () -> Unit = {},
    onClearInitialPlaylist: () -> Unit = {},
    onImportSpotifyPlaylist: (String) -> Unit = {},
    onDismissImportSuccess: () -> Unit = {},
    onPlayTrack: (AuraTrack, List<AuraTrack>) -> Unit,
    onToggleFavorite: (AuraTrack) -> Unit,
    onDownloadTrack: (AuraTrack) -> Unit,
    onRemoveDownload: (AuraTrack) -> Unit = {},
    onUpdatePlaylistCover: (playlistId: String, newCoverUrl: String) -> Unit = { _, _ -> },
    onUpdatePlaylistTitle: (playlistId: String, newTitle: String) -> Unit = { _, _ -> },
    onDeletePlaylist: (playlistId: String) -> Unit = {},
    onRemoveTrackFromPlaylist: (playlistId: String, videoId: String) -> Unit = { _, _ -> },
    onCreatePlaylist: (name: String, description: String, coverUrl: String) -> Unit = { _, _, _ -> },
    onAddTrackToPlaylist: (playlistId: String, track: AuraTrack) -> Unit = { _, _ -> },
    onRemoveFromRecentlyPlayed: (AuraTrack) -> Unit = {},
    onOpenArtist: (String) -> Unit = {}
) {
    val context = LocalContext.current
    val uniqueRecent = remember(recentlyPlayed) {
        val seen = mutableSetOf<String>()
        recentlyPlayed.filter { track ->
            val key = com.auramusic.app.core.recommendations.LocalRecommendationEngine.songIdentityKey(track)
            seen.add(key)
        }
    }
    val musicFavorites = remember(favorites) {
        favorites.filterNot {
            it.isPodcast ||
            it.genre.equals("podcast", ignoreCase = true) ||
            it.album.contains("podcast", ignoreCase = true) ||
            it.musicVideoType == "PODCAST"
        }
    }
    var activeTab by rememberSaveable { mutableIntStateOf(0) }
    var selectedModalTrack by remember { mutableStateOf<AuraTrack?>(null) }
    var selectedPlaylistId by rememberSaveable { mutableStateOf<String?>(initialPlaylistId) }
    var selectedPlaylistForOptions by remember { mutableStateOf<AuraPlaylist?>(null) }
    var showCreateOrImportSheet by remember { mutableStateOf(false) }
    var showCreatePlaylistModal by remember { mutableStateOf(false) }
    var trackForAddToPlaylist by remember { mutableStateOf<AuraTrack?>(null) }
    var showRenameDialog by remember { mutableStateOf(false) }
    var renamePlaylistTarget by remember { mutableStateOf<AuraPlaylist?>(null) }
    var renameInput by remember { mutableStateOf("") }
    var showImportCard by remember { mutableStateOf(false) }
    var linkInput by remember { mutableStateOf("") }

    val mediaPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        val pl = selectedPlaylistForOptions
        if (uri != null && pl != null) {
            onUpdatePlaylistCover(pl.id, uri.toString())
            Toast.makeText(context, "Cover updated", Toast.LENGTH_SHORT).show()
        }
    }

    LaunchedEffect(initialPlaylistId) {
        if (initialPlaylistId != null) {
            selectedPlaylistId = initialPlaylistId
        }
    }

    // Auto-close import section upon successful import
    LaunchedEffect(spotifyImportStatus) {
        if (spotifyImportStatus?.startsWith("Imported", ignoreCase = true) == true) {
            kotlinx.coroutines.delay(1000)
            showImportCard = false
        }
    }

    LaunchedEffect(importedPlaylistSuccess) {
        if (importedPlaylistSuccess != null) {
            showImportCard = false
            onDismissImportSuccess()
        }
    }

    BackHandler(enabled = selectedPlaylistId != null) {
        selectedPlaylistId = null
        onClearInitialPlaylist()
        if (openedFromHomeScreen) {
            onNavigateToHome()
        }
    }

    // Track the active or last-displayed playlist so the slide-out exit animation retains its content
    val selectedPlaylist = playlists.firstOrNull { it.id == selectedPlaylistId }
    var lastSelectedPlaylist by remember { mutableStateOf<AuraPlaylist?>(null) }
    if (selectedPlaylist != null) {
        lastSelectedPlaylist = selectedPlaylist
    }
    val playlistSlideEasing = remember { CubicBezierEasing(0.23f, 1f, 0.32f, 1f) }

    val tabLabels = listOf("All", "Playlists", "Liked Songs", "Podcasts", "Downloaded")

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(SpotifyPitchBlack)
    ) {
    // 1. My Music / Library Playlists Screen (slides out to the left when a playlist is opened, slides back in when closed)
    AnimatedVisibility(
        visible = selectedPlaylist == null,
        enter = slideInHorizontally(
            initialOffsetX = { fullWidth -> -fullWidth },
            animationSpec = tween(durationMillis = 280, easing = playlistSlideEasing)
        ) + fadeIn(animationSpec = tween(durationMillis = 200, easing = playlistSlideEasing)),
        exit = slideOutHorizontally(
            targetOffsetX = { fullWidth -> -fullWidth },
            animationSpec = tween(durationMillis = 250, easing = playlistSlideEasing)
        ) + fadeOut(animationSpec = tween(durationMillis = 180, easing = playlistSlideEasing)),
        modifier = Modifier.fillMaxSize()
    ) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(SpotifyPitchBlack)
            .statusBarsPadding()
            .padding(top = 2.dp)
    ) {
        // Top Bar: "My Music" + Flat Options Button (no arrow back, no circle fill container)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "My Music",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                color = Color.White,
                fontSize = 20.sp
            )

            IconButton(
                onClick = {
                    if (showImportCard) {
                        showImportCard = false
                    } else {
                        showCreateOrImportSheet = true
                    }
                },
                modifier = Modifier.size(36.dp)
            ) {
                Icon(
                    imageVector = if (showImportCard) Icons.Rounded.Close else Icons.Rounded.Add,
                    contentDescription = if (showImportCard) "Close Import" else "Create or Import Playlist",
                    tint = Color.White,
                    modifier = Modifier.size(24.dp)
                )
            }
        }

        // Flat Designed Spotify Link & Playlist Import Section
        AnimatedVisibility(
            visible = showImportCard,
            enter = fadeIn(tween(200)) + expandVertically(
                animationSpec = tween(280, easing = CubicBezierEasing(0.23f, 1f, 0.32f, 1f))
            ),
            exit = fadeOut(tween(160)) + shrinkVertically(
                animationSpec = tween(240, easing = CubicBezierEasing(0.23f, 1f, 0.32f, 1f))
            )
        ) {
            val isImporting = spotifyImportStatus?.contains("…") == true

            Surface(
                shape = RoundedCornerShape(14.dp),
                color = Color(0xFF14161E),
                shadowElevation = 0.dp,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 6.dp)
            ) {
                Column(modifier = Modifier.padding(14.dp)) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Link,
                            contentDescription = null,
                            tint = AuraNeonLime,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "Import Spotify Playlist",
                            color = Color.White,
                            fontWeight = FontWeight.Bold,
                            fontSize = 14.sp
                        )
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    // Lower height link input field (no paste button, clean compact row)
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(40.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .background(Color(0xFF0D0E13))
                            .padding(horizontal = 12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        BasicTextField(
                            value = linkInput,
                            onValueChange = { linkInput = it },
                            singleLine = true,
                            textStyle = TextStyle(
                                color = TextPrimary,
                                fontSize = 12.5.sp
                            ),
                            modifier = Modifier.weight(1f),
                            decorationBox = { innerTextField ->
                                if (linkInput.isEmpty()) {
                                    Text(
                                        text = "Paste public Spotify playlist or album URL…",
                                        color = TextSecondary.copy(alpha = 0.7f),
                                        fontSize = 12.5.sp
                                    )
                                }
                                innerTextField()
                            }
                        )

                        if (linkInput.isNotBlank()) {
                            IconButton(
                                onClick = { linkInput = "" },
                                modifier = Modifier.size(24.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.Clear,
                                    contentDescription = "Clear",
                                    tint = TextSecondary,
                                    modifier = Modifier.size(14.dp)
                                )
                            }
                        }
                    }

                    // Status and Import button on the SAME row
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 10.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // Imported status or progress on the left
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .padding(end = 10.dp)
                        ) {
                            if (!spotifyImportStatus.isNullOrBlank()) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    if (isImporting) {
                                        CircularProgressIndicator(
                                            color = AuraNeonLime,
                                            strokeWidth = 2.dp,
                                            modifier = Modifier.size(13.dp)
                                        )
                                        Spacer(modifier = Modifier.width(6.dp))
                                    }
                                    Text(
                                        text = spotifyImportStatus,
                                        color = if (spotifyImportStatus.contains("Could not", true) || spotifyImportStatus.contains("Failed", true))
                                            Color(0xFFFF5252)
                                        else
                                            AuraNeonLime,
                                        fontSize = 11.5.sp,
                                        fontWeight = FontWeight.Medium,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                            }
                        }

                        // Compact "Import" button (no "Playlist" text)
                        Button(
                            onClick = {
                                if (linkInput.isNotBlank()) {
                                    onImportSpotifyPlaylist(linkInput.trim())
                                    linkInput = ""
                                }
                            },
                            enabled = linkInput.isNotBlank() && !isImporting,
                            shape = RoundedCornerShape(8.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = AuraNeonLime,
                                disabledContainerColor = Color(0xFF222530)
                            ),
                            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 6.dp),
                            modifier = Modifier.height(34.dp)
                        ) {
                            if (isImporting) {
                                CircularProgressIndicator(
                                    color = Color.Black,
                                    strokeWidth = 2.dp,
                                    modifier = Modifier.size(12.dp)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Importing…", color = Color.Black, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                            } else {
                                Icon(
                                    imageVector = Icons.Rounded.Download,
                                    contentDescription = null,
                                    tint = if (linkInput.isNotBlank()) Color.Black else TextSecondary,
                                    modifier = Modifier.size(14.dp)
                                )
                                Spacer(modifier = Modifier.width(5.dp))
                                Text(
                                    "Import",
                                    color = if (linkInput.isNotBlank()) Color.Black else TextSecondary,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 12.sp
                                )
                            }
                        }
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        // Filter Pills: All, Playlists, Liked Songs, Downloaded (Screen 3)
        LazyRow(
            contentPadding = PaddingValues(horizontal = 20.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            items(tabLabels.size) { idx ->
                val selected = activeTab == idx
                val label = tabLabels[idx]
                Surface(
                    shape = CircleShape,
                    color = if (selected) AuraNeonLime else AuraPillInactive,
                    modifier = Modifier
                        .clip(CircleShape)
                        .clickable { activeTab = idx }
                ) {
                    Text(
                        text = label,
                        color = if (selected) Color.Black else AuraPillInactiveText,
                        fontWeight = FontWeight.Bold,
                        fontSize = 11.5.sp,
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 4.dp)
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        when (activeTab) {
            0 -> {
                LazyColumn(contentPadding = PaddingValues(bottom = 140.dp)) {
                        item(key = "library_overview") {
                            Column(modifier = Modifier.padding(horizontal = 16.dp)) {
                                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                    LibraryOverviewTile(
                                        title = "Liked songs", subtitle = "${musicFavorites.size} songs",
                                        tracks = musicFavorites.take(3), modifier = Modifier.weight(1f).height(150.dp),
                                        onClick = { activeTab = 2 }
                                    )
                                    val mostPlayed = stats.topTracks.firstOrNull()
                                    LibraryOverviewTile(
                                        title = "Most played",
                                        subtitle = mostPlayed?.let { "${it.first.title} · ${it.second} plays" } ?: "No plays yet",
                                        tracks = stats.topTracks.map { it.first },
                                        modifier = Modifier.weight(1f).height(150.dp), onClick = { activeTab = 0 }
                                    )
                                }
                                Spacer(Modifier.height(10.dp))
                                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                    LibraryOverviewTile(
                                        title = "Downloaded", subtitle = "${downloads.size} songs",
                                        tracks = downloads.take(3), modifier = Modifier.weight(1f).height(124.dp),
                                        onClick = { activeTab = 4 }
                                    )
                                    LibraryOverviewTile(
                                        title = "Playlists", subtitle = "${playlists.size} playlists",
                                        tracks = playlists.mapNotNull { it.tracks.firstOrNull() }.take(3),
                                        modifier = Modifier.weight(1f).height(124.dp), onClick = { activeTab = 1 }
                                    )
                                }
                                Spacer(Modifier.height(18.dp))
                                Text("Recently added", color = Color.White, fontSize = 19.sp, fontWeight = FontWeight.Bold)
                                Spacer(Modifier.height(6.dp))
                            }
                        }
                        if (uniqueRecent.isEmpty()) {
                            item(key = "empty_tracks") {
                                EmptyVaultMessage("No recently played songs yet. Play tracks to see them here.")
                            }
                        } else items(uniqueRecent, key = { "recent_${it.videoId.ifBlank { it.id }}" }) { track ->
                            val isFav = favorites.any { it.videoId == track.videoId }
                            val isDown = downloads.any { it.videoId == track.videoId && it.isDownloaded }
                            val isDownProgress = downloadingIds.contains(track.videoId)
                            SpotifyTrackRowItem(
                                track = track,
                                isCurrent = currentPlayingId == track.videoId,
                                isFavorite = isFav,
                                isDownloaded = isDown,
                                isDownloading = isDownProgress,
                                downloadProgress = downloadProgressMap[track.videoId],
                                isPlaying = isPlaying,
                                onClick = { onPlayTrack(track, uniqueRecent) },
                                onToggleFavorite = { onToggleFavorite(track) },
                                onDownload = { onDownloadTrack(track) },
                                onOptionsClick = { selectedModalTrack = track }
                            )
                        }
                }
            }
            1 -> {
                if (playlists.isEmpty()) {
                    EmptyVaultMessage("No playlists created or imported yet.")
                } else {
                    LazyColumn(contentPadding = PaddingValues(bottom = 140.dp)) {
                        items(playlists, key = { it.id }) { playlist ->
                            PlaylistItemRow(
                                playlist = playlist,
                                onClick = { selectedPlaylistId = playlist.id },
                                onLongClick = { selectedPlaylistForOptions = playlist }
                            )
                        }
                    }
                }
            }
            2 -> {
                if (musicFavorites.isEmpty()) {
                    EmptyVaultMessage("No liked songs yet. Tap the heart on any song to save it here.")
                } else {
                    LazyColumn(contentPadding = PaddingValues(bottom = 140.dp)) {
                        items(musicFavorites, key = { it.videoId }) { track ->
                            SpotifyTrackRowItem(
                                track = track,
                                isCurrent = currentPlayingId == track.videoId,
                                isFavorite = true,
                                isDownloaded = downloads.any { it.videoId == track.videoId && it.isDownloaded },
                                isDownloading = downloadingIds.contains(track.videoId),
                                downloadProgress = downloadProgressMap[track.videoId],
                                isPlaying = isPlaying,
                                onClick = { onPlayTrack(track, musicFavorites) },
                                onToggleFavorite = { onToggleFavorite(track) },
                                onDownload = { onDownloadTrack(track) },
                                onOptionsClick = { selectedModalTrack = track }
                            )
                        }
                    }
                }
            }
            3 -> {
                // PODCASTS TAB: Liked podcast shows (playlists where isPodcast == true)
                // and liked podcast episodes (favorites where isPodcast == true or genre/album indicates podcast)
                val podcastShows = playlists.filter { it.isPodcast || it.id.startsWith("MPSP") || it.id.startsWith("MP_") || it.name.contains("podcast", ignoreCase = true) }
                val podcastEpisodes = favorites.filter {
                    it.isPodcast ||
                    it.album.contains("podcast", ignoreCase = true) ||
                    it.genre.equals("podcast", ignoreCase = true) ||
                    it.musicVideoType == "PODCAST"
                }

                if (podcastShows.isEmpty() && podcastEpisodes.isEmpty()) {
                    EmptyVaultMessage("No liked podcasts yet. Tap the heart on any podcast show or episode to save it here.")
                } else {
                    LazyColumn(contentPadding = PaddingValues(bottom = 140.dp)) {
                        if (podcastShows.isNotEmpty()) {
                            item {
                                Text(
                                    text = "Shows (${podcastShows.size})",
                                    color = Color.White,
                                    fontSize = 17.sp,
                                    fontWeight = FontWeight.Bold,
                                    modifier = Modifier.padding(horizontal = 20.dp, vertical = 10.dp)
                                )
                            }
                            items(podcastShows, key = { "pod_show_${it.id}" }) { playlist ->
                                PlaylistItemRow(
                                    playlist = playlist,
                                    onClick = { selectedPlaylistId = playlist.id },
                                    onLongClick = { selectedPlaylistForOptions = playlist }
                                )
                            }
                        }

                        if (podcastEpisodes.isNotEmpty()) {
                            item {
                                Text(
                                    text = "Liked Episodes (${podcastEpisodes.size})",
                                    color = Color.White,
                                    fontSize = 17.sp,
                                    fontWeight = FontWeight.Bold,
                                    modifier = Modifier.padding(horizontal = 20.dp, vertical = 10.dp)
                                )
                            }
                            items(podcastEpisodes, key = { "pod_ep_${it.videoId}" }) { track ->
                                SpotifyTrackRowItem(
                                    track = track,
                                    isCurrent = currentPlayingId == track.videoId,
                                    isFavorite = true,
                                    isDownloaded = downloads.any { it.videoId == track.videoId && it.isDownloaded },
                                    isDownloading = downloadingIds.contains(track.videoId),
                                    downloadProgress = downloadProgressMap[track.videoId],
                                    isPlaying = isPlaying,
                                    onClick = { onPlayTrack(track, podcastEpisodes) },
                                    onToggleFavorite = { onToggleFavorite(track) },
                                    onDownload = { onDownloadTrack(track) },
                                    onOptionsClick = { selectedModalTrack = track }
                                )
                            }
                        }
                    }
                }
            }
            4 -> {
                if (downloads.isEmpty()) {
                    EmptyVaultMessage("No downloaded songs yet. Tap download on any song for offline listening.")
                } else {
                    LazyColumn(contentPadding = PaddingValues(bottom = 140.dp)) {
                        items(downloads, key = { it.videoId }) { track ->
                            val isDown = track.isDownloaded
                            val isDownProgress = downloadingIds.contains(track.videoId)
                            SpotifyTrackRowItem(
                                track = track,
                                isCurrent = currentPlayingId == track.videoId,
                                isFavorite = favorites.any { it.videoId == track.videoId },
                                isDownloaded = isDown,
                                isDownloading = isDownProgress,
                                downloadProgress = downloadProgressMap[track.videoId],
                                isPlaying = isPlaying,
                                onClick = { onPlayTrack(track, downloads) },
                                onToggleFavorite = { onToggleFavorite(track) },
                                onDownload = { onDownloadTrack(track) },
                                onOptionsClick = { selectedModalTrack = track }
                            )
                        }
                    }
                }
            }
        }
    }
    }

    // 2. Slide-in / Slide-out PlaylistDetailsScreen overlay
    AnimatedVisibility(
        visible = selectedPlaylist != null,
        enter = slideInHorizontally(
            initialOffsetX = { it },
            animationSpec = tween(durationMillis = 280, easing = playlistSlideEasing)
        ) + fadeIn(animationSpec = tween(durationMillis = 200, easing = playlistSlideEasing)),
        exit = slideOutHorizontally(
            targetOffsetX = { it },
            animationSpec = tween(durationMillis = 250, easing = playlistSlideEasing)
        ) + fadeOut(animationSpec = tween(durationMillis = 180, easing = playlistSlideEasing)),
        modifier = Modifier
            .fillMaxSize()
            .zIndex(5f)
    ) {
        val displayPlaylist = selectedPlaylist ?: lastSelectedPlaylist
        if (displayPlaylist != null) {
            PlaylistDetailsScreen(
                playlist = displayPlaylist,
                favorites = favorites,
                downloads = downloads,
                downloadingIds = downloadingIds,
                downloadProgressMap = downloadProgressMap,
                currentPlayingId = currentPlayingId,
                isPlaying = isPlaying,
                initialScrollToTrackId = initialScrollToTrackId,
                onClearScrollTarget = onClearScrollTarget,
                onBack = {
                    selectedPlaylistId = null
                    onClearInitialPlaylist()
                    if (openedFromHomeScreen) {
                        onNavigateToHome()
                    }
                },
                onPlayTrack = { track, queue ->
                    onSetActiveUserPlaylist(displayPlaylist)
                    onPlayTrack(track, queue)
                },
                onToggleFavorite = onToggleFavorite,
                onDownloadTrack = onDownloadTrack,
                onRemoveDownload = onRemoveDownload,
                onRemoveTrackFromPlaylist = { vId -> onRemoveTrackFromPlaylist(displayPlaylist.id, vId) },
                onUpdateCover = { newCover -> onUpdatePlaylistCover(displayPlaylist.id, newCover) },
                onUpdateTitle = { newTitle -> onUpdatePlaylistTitle(displayPlaylist.id, newTitle) },
                onDeletePlaylist = {
                    onDeletePlaylist(displayPlaylist.id)
                    selectedPlaylistId = null
                    onClearInitialPlaylist()
                    if (openedFromHomeScreen) {
                        onNavigateToHome()
                    }
                },
                playlists = playlists,
                onAddTrackToPlaylist = onAddTrackToPlaylist,
                onCreatePlaylist = onCreatePlaylist,
                onOpenArtist = onOpenArtist
            )
        }
    }
    }

    selectedModalTrack?.let { modalTrack ->
        AuraTrackOptionsModal(
            track = modalTrack,
            isFavorite = favorites.any { it.videoId == modalTrack.videoId },
            onDismiss = { selectedModalTrack = null },
            onRemoveFromRecent = if (recentlyPlayed.any { it.videoId == modalTrack.videoId || it.id == modalTrack.id }) {
                {
                    onRemoveFromRecentlyPlayed(modalTrack)
                    selectedModalTrack = null
                }
            } else null,
            onPlayNext = {
                AuraPlayerManager.playNext(modalTrack)
                Toast.makeText(context, "Playing next: ${modalTrack.title}", Toast.LENGTH_SHORT).show()
                selectedModalTrack = null
            },
            onAddToQueue = {
                AuraPlayerManager.enqueueTrack(modalTrack)
                Toast.makeText(context, "Added to queue: ${modalTrack.title}", Toast.LENGTH_SHORT).show()
                selectedModalTrack = null
            },
            onToggleFavorite = {
                val wasFav = favorites.any { it.videoId == modalTrack.videoId }
                onToggleFavorite(modalTrack)
                val msg = if (wasFav) "Removed from Liked Songs" else "Added to Liked Songs"
                Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
                selectedModalTrack = null
            },
            onAddToPlaylist = {
                val t = modalTrack
                selectedModalTrack = null
                trackForAddToPlaylist = t
            },
            onGoToArtist = {
                selectedModalTrack = null
                onOpenArtist(modalTrack.artist)
            },
            isDownloaded = downloads.any { it.videoId == modalTrack.videoId && it.isDownloaded },
            onDownloadTrack = {
                onDownloadTrack(modalTrack)
                Toast.makeText(context, "Downloading ${modalTrack.title}…", Toast.LENGTH_SHORT).show()
                selectedModalTrack = null
            },
            onRemoveDownload = {
                onRemoveDownload(modalTrack)
                selectedModalTrack = null
            },
            onWatchVideo = {
                selectedModalTrack = null
                onPlayTrack(modalTrack, listOf(modalTrack))
                com.auramusic.app.core.media.AuraPlayerManager.setVideoMode(true)
            }
        )
    }

    selectedPlaylistForOptions?.let { pl ->
        AuraPlaylistOptionsModal(
            playlist = pl,
            onDismiss = { selectedPlaylistForOptions = null },
            onDownloadAll = {
                selectedPlaylistForOptions = null
                pl.tracks.forEach { onDownloadTrack(it) }
                Toast.makeText(context, "Downloading ${pl.tracks.size} songs for offline listening…", Toast.LENGTH_SHORT).show()
            },
            onChangeCover = {
                selectedPlaylistForOptions = null
                mediaPickerLauncher.launch("image/*")
            },
            onResetCover = if (pl.coverUrl.isNotBlank()) {
                {
                    selectedPlaylistForOptions = null
                    onUpdatePlaylistCover(pl.id, "")
                    Toast.makeText(context, "Cover reset to Spotify collage", Toast.LENGTH_SHORT).show()
                }
            } else null,
            onRename = {
                selectedPlaylistForOptions = null
                renamePlaylistTarget = pl
                renameInput = pl.name
                showRenameDialog = true
            },
            onDelete = {
                selectedPlaylistForOptions = null
                onDeletePlaylist(pl.id)
            }
        )
    }

    if (showRenameDialog && renamePlaylistTarget != null) {
        val target = renamePlaylistTarget!!
        AlertDialog(
            onDismissRequest = { showRenameDialog = false },
            containerColor = Color(0xFF1B1D26),
            title = { Text("Rename Playlist", color = Color.White, fontWeight = FontWeight.Bold) },
            text = {
                OutlinedTextField(
                    value = renameInput,
                    onValueChange = { renameInput = it },
                    singleLine = true,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = AuraNeonLime,
                        unfocusedBorderColor = Color.White.copy(alpha = 0.2f),
                        focusedTextColor = Color.White,
                        unfocusedTextColor = Color.White
                    )
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        val trimmed = renameInput.trim()
                        if (trimmed.isNotBlank()) {
                            onUpdatePlaylistTitle(target.id, trimmed)
                            Toast.makeText(context, "Renamed to '$trimmed'", Toast.LENGTH_SHORT).show()
                        }
                        showRenameDialog = false
                    }
                ) {
                    Text("Save", color = AuraNeonLime, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showRenameDialog = false }) {
                    Text("Cancel", color = Color.White.copy(alpha = 0.6f))
                }
            }
        )
    }

    // Modal 1: Choice between "Create a playlist" and "Import Playlist from Spotify"
    if (showCreateOrImportSheet) {
        AuraCreateOrImportModal(
            onDismiss = { showCreateOrImportSheet = false },
            onCreatePlaylist = {
                showCreateOrImportSheet = false
                showCreatePlaylistModal = true
            },
            onImportSpotify = {
                showCreateOrImportSheet = false
                showImportCard = true
            }
        )
    }

    // Modal 2: Create Playlist with Cover Image, Title, Description
    if (showCreatePlaylistModal) {
        AuraCreatePlaylistModal(
            onDismiss = { showCreatePlaylistModal = false },
            onCreate = { title, desc, coverUri ->
                showCreatePlaylistModal = false
                onCreatePlaylist(title, desc, coverUri)
                activeTab = 1
                Toast.makeText(context, "Created playlist: $title", Toast.LENGTH_SHORT).show()
            }
        )
    }

    // Modal 3: Add Song to Playlist
    trackForAddToPlaylist?.let { trackToAdd ->
        AuraAddToPlaylistModal(
            track = trackToAdd,
            playlists = playlists,
            onDismiss = { trackForAddToPlaylist = null },
            onSelectPlaylist = { targetPl ->
                onAddTrackToPlaylist(targetPl.id, trackToAdd)
                trackForAddToPlaylist = null
                Toast.makeText(context, "Added '${trackToAdd.title}' to '${targetPl.name}'", Toast.LENGTH_SHORT).show()
            },
            onCreateNewPlaylist = {
                trackForAddToPlaylist = null
                showCreatePlaylistModal = true
            }
        )
    }
}

@Composable
private fun LibraryOverviewTile(
    title: String,
    subtitle: String,
    tracks: List<AuraTrack>,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(20.dp))
            .background(Color(0xFF24272D))
            .clickable(onClick = onClick)
            .padding(14.dp)
    ) {
        if (tracks.isEmpty()) {
            EmptyArtworkFanStack(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(top = 2.dp, end = 2.dp)
            )
        } else if (tracks.size == 1) {
            AsyncImage(
                model = tracks.first().highResArtworkUrl,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(top = 2.dp, end = 2.dp)
                    .size(54.dp)
                    .clip(RoundedCornerShape(9.dp))
            )
        } else {
            Box(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(top = 2.dp, end = 2.dp)
                    .size(width = 82.dp, height = 60.dp)
            ) {
                tracks.take(3).forEachIndexed { index, track ->
                    AsyncImage(
                        model = track.highResArtworkUrl,
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier
                            .align(Alignment.Center)
                            .offset(x = ((index - 1) * 20).dp)
                            .rotate((index - 1) * 18f)
                            .zIndex(index.toFloat())
                            .size(width = 44.dp, height = 52.dp)
                            .clip(RoundedCornerShape(9.dp))
                    )
                }
            }
        }
        Column(modifier = Modifier.align(Alignment.BottomStart)) {
            Text(title, color = Color.White, fontSize = 15.sp, fontWeight = FontWeight.Bold)
            Text(subtitle, color = TextSecondary, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}

@Composable
private fun EmptyArtworkFanStack(modifier: Modifier = Modifier) {
    Canvas(modifier = modifier.size(width = 82.dp, height = 60.dp)) {
        val cardWidth = size.width * 0.54f
        val cardHeight = size.height * 0.86f
        val corner = CornerRadius(7.dp.toPx())
        val dashedStroke = Stroke(
            width = 1.dp.toPx(),
            pathEffect = PathEffect.dashPathEffect(floatArrayOf(4.dp.toPx(), 3.dp.toPx()))
        )

        for (index in 0..2) {
            val center = Offset(size.width * (0.25f + index * 0.25f), size.height * 0.50f)
            rotate(degrees = (index - 1) * 18f, pivot = center) {
                drawRoundRect(
                    color = Color.White.copy(alpha = 0.30f),
                    topLeft = center - Offset(cardWidth / 2f, cardHeight / 2f),
                    size = Size(cardWidth, cardHeight),
                    cornerRadius = corner,
                    style = dashedStroke
                )
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun PlaylistItemRow(
    playlist: AuraPlaylist,
    onClick: () -> Unit,
    onLongClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .combinedClickable(
                onClick = onClick,
                onLongClick = onLongClick
            )
            .padding(horizontal = 20.dp, vertical = 7.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        com.auramusic.app.ui.components.AuraPlaylistCover(
            coverUrl = playlist.coverUrl,
            tracks = playlist.tracks,
            modifier = Modifier.size(56.dp),
            shape = RoundedCornerShape(16.dp)
        )

        Spacer(modifier = Modifier.width(14.dp))

        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = playlist.name,
                style = MaterialTheme.typography.titleSmall,
                color = TextPrimary,
                maxLines = 1,
                overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = "Playlist • ${playlist.tracks.size} songs",
                style = MaterialTheme.typography.bodySmall,
                color = TextSecondary
            )
        }
    }
}

@Composable
private fun EmptyVaultMessage(message: String) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 32.dp, vertical = 64.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = message,
            style = MaterialTheme.typography.bodyMedium,
            color = TextSecondary
        )
    }
}


