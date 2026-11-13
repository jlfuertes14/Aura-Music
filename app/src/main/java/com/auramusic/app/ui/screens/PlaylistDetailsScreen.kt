package com.auramusic.app.ui.screens

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsTopHeight
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.AddCircleOutline
import androidx.compose.material.icons.rounded.ArrowCircleDown
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.Image
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.RestartAlt
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material.icons.rounded.Shuffle
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import com.auramusic.app.domain.model.AuraPalette
import com.auramusic.app.ui.components.AuraPlaylistCover
import com.auramusic.app.ui.components.AuraAddToPlaylistModal
import com.auramusic.app.ui.components.AuraCreatePlaylistModal
import com.auramusic.app.ui.components.ForceWhiteSystemBarsEffect
import com.auramusic.app.ui.components.LazyColumnScrollbar
import com.auramusic.app.ui.components.TrackOptionRow
import com.auramusic.app.ui.theme.extractAuraPaletteFromUrl
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import coil.compose.AsyncImage
import com.auramusic.app.core.media.AuraPlayerManager
import com.auramusic.app.domain.model.AuraPlaylist
import com.auramusic.app.domain.model.AuraTrack
import com.auramusic.app.ui.components.AuraPlaylistCover
import com.auramusic.app.ui.components.AuraPlaylistOptionsModal
import com.auramusic.app.ui.components.AuraTrackOptionsModal
import com.auramusic.app.ui.theme.AuraNeonLime
import com.auramusic.app.ui.theme.AuraPlayGreen
import com.auramusic.app.ui.theme.SpotifyCard
import com.auramusic.app.ui.theme.SpotifyPitchBlack
import com.auramusic.app.ui.theme.TextPrimary
import com.auramusic.app.ui.theme.TextSecondary
import java.io.File
import kotlin.math.roundToInt
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

enum class PlaylistSortOption(val displayName: String) {
    DEFAULT("Sort"),
    TITLE("Title"),
    ARTIST("Artist"),
    DURATION("Duration")
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PlaylistDetailsScreen(
    playlist: AuraPlaylist,
    favorites: List<AuraTrack>,
    downloads: List<AuraTrack>,
    downloadingIds: Set<String>,
    downloadProgressMap: Map<String, Float> = emptyMap(),
    currentPlayingId: String?,
    isPlaying: Boolean,
    onBack: () -> Unit,
    onPlayTrack: (AuraTrack, List<AuraTrack>) -> Unit,
    onToggleFavorite: (AuraTrack) -> Unit,
    onDownloadTrack: (AuraTrack) -> Unit,
    onDownloadAll: (List<AuraTrack>) -> Unit = { tracks -> tracks.forEach(onDownloadTrack) },
    isDownloadPaused: Boolean = false,
    onPauseDownloads: () -> Unit = {},
    onResumeDownloads: () -> Unit = {},
    onCancelDownloads: () -> Unit = {},
    onRemoveDownload: ((AuraTrack) -> Unit)? = null,
    onRemoveTrackFromPlaylist: ((videoId: String) -> Unit)? = null,
    onUpdateCover: (newCoverUrl: String) -> Unit,
    onUpdateTitle: (newTitle: String) -> Unit,
    onDeletePlaylist: () -> Unit,
    playlists: List<AuraPlaylist> = emptyList(),
    initialScrollToTrackId: String? = null,
    onClearScrollTarget: () -> Unit = {},
    onAddTrackToPlaylist: (playlistId: String, track: AuraTrack) -> Unit = { _, _ -> },
    onCreatePlaylist: (name: String, description: String, coverUrl: String) -> Unit = { _, _, _ -> },
    onOpenArtist: (String) -> Unit = {}
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    var selectedModalTrack by remember { mutableStateOf<AuraTrack?>(null) }
    var trackForAddToPlaylist by remember { mutableStateOf<AuraTrack?>(null) }
    var showCreatePlaylistModal by remember { mutableStateOf(false) }
    var showEditTitleDialog by remember { mutableStateOf(false) }
    var showMenu by remember { mutableStateOf(false) }
    var newTitleInput by remember { mutableStateOf(playlist.name) }

    // Media Picker Launcher for selecting an image from the user's files/gallery
    val mediaPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        if (uri != null) {
            coroutineScope.launch(Dispatchers.IO) {
                val localPath = savePlaylistCoverLocally(context, playlist.id, uri)
                withContext(Dispatchers.Main) {
                    onUpdateCover(localPath)
                    Toast.makeText(context, "Playlist cover updated!", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    // Storage/Media Permission Launcher prompting user to allow files
    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted) {
            mediaPickerLauncher.launch("image/*")
        } else {
            Toast.makeText(context, "Storage permission is required to choose a custom cover", Toast.LENGTH_SHORT).show()
        }
    }

    val onPickCoverImage: () -> Unit = {
        val permission = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            Manifest.permission.READ_MEDIA_IMAGES
        } else {
            Manifest.permission.READ_EXTERNAL_STORAGE
        }
        val hasPermission = ContextCompat.checkSelfPermission(context, permission) == PackageManager.PERMISSION_GRANTED
        if (hasPermission) {
            mediaPickerLauncher.launch("image/*")
        } else {
            permissionLauncher.launch(permission)
        }
    }

    var playlistPalette by remember { mutableStateOf<AuraPalette?>(null) }
    LaunchedEffect(playlist.coverUrl, playlist.tracks) {
        val firstTrackArt = playlist.tracks.firstOrNull()?.let { t ->
            com.auramusic.app.core.media.AuraPlayerManager.getOfflineArtworkUri(context, t.videoId)
                ?: t.highResArtworkUrl
        } ?: ""
        val artUrl = playlist.coverUrl.ifBlank { firstTrackArt }
        if (artUrl.isNotBlank()) {
            try {
                playlistPalette = extractAuraPaletteFromUrl(artUrl, context)
            } catch (_: Exception) {}
        }
    }
    val dominantColor = playlistPalette?.backgroundTop ?: Color(0xFF1E3264)

    var searchInPlaylistQuery by remember { mutableStateOf("") }
    var sortOption by remember { mutableStateOf(PlaylistSortOption.DEFAULT) }

    val processedTracks = remember(playlist.tracks, searchInPlaylistQuery, sortOption) {
        val filtered = if (searchInPlaylistQuery.isBlank()) {
            playlist.tracks
        } else {
            playlist.tracks.filter {
                it.title.contains(searchInPlaylistQuery, ignoreCase = true) ||
                it.artist.contains(searchInPlaylistQuery, ignoreCase = true)
            }
        }
        when (sortOption) {
            PlaylistSortOption.DEFAULT -> filtered
            PlaylistSortOption.TITLE -> filtered.sortedBy { it.title.lowercase() }
            PlaylistSortOption.ARTIST -> filtered.sortedBy { it.artist.lowercase() }
            PlaylistSortOption.DURATION -> filtered.sortedByDescending { it.durationSeconds }
        }
    }

    val density = LocalDensity.current
    val statusBarTopPadding = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
    var pinnedHeaderBottomY by remember { mutableFloatStateOf(0f) }
    var belowCoverTitleBottomY by remember { mutableFloatStateOf(Float.MAX_VALUE) }

    val listState = rememberLazyListState()

    // Auto-scroll to currently playing track when navigated from full-screen now playing
    LaunchedEffect(initialScrollToTrackId, processedTracks) {
        if (!initialScrollToTrackId.isNullOrBlank() && processedTracks.isNotEmpty()) {
            val trackIdx = processedTracks.indexOfFirst {
                it.videoId == initialScrollToTrackId || it.id == initialScrollToTrackId
            }
            if (trackIdx >= 0) {
                // Header is item 0, tracks start at index 1
                val targetIndex = trackIdx + 1
                val pinnedHeaderOffsetPx = with(density) { (statusBarTopPadding + 48.dp).roundToPx() }
                val offsetPx = if (pinnedHeaderBottomY > 0f) pinnedHeaderBottomY.roundToInt() else pinnedHeaderOffsetPx
                // Wait for the playlist screen slide-in transition (280ms) to finish and settle first,
                // then gracefully scroll down to the song location
                kotlinx.coroutines.delay(340)
                listState.animateScrollToItem(index = targetIndex, scrollOffset = -offsetPx)
                onClearScrollTarget()
            }
        }
    }

    var isSearchRevealed by remember { mutableStateOf(false) }

    val nestedScrollConnection = remember {
        object : NestedScrollConnection {
            override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset {
                // If scrolling down into tracks (swiping finger up, available.y < -12f), hide search bar if query is empty
                if (available.y < -12f && searchInPlaylistQuery.isEmpty() && isSearchRevealed) {
                    isSearchRevealed = false
                }
                return Offset.Zero
            }

            override fun onPostScroll(
                consumed: Offset,
                available: Offset,
                source: NestedScrollSource
            ): Offset {
                // If user pulls down while at top of list, reveal search bar
                if (available.y > 10f && listState.firstVisibleItemIndex == 0 && listState.firstVisibleItemScrollOffset == 0) {
                    isSearchRevealed = true
                }
                return Offset.Zero
            }
        }
    }

    // Shrink progress: DO NOT shrink immediately upon scroll.
    // Wait until the invisible top header reaches the collage image.
    // Phase 1 (0.0 -> 0.55): Cover image shrinks FIRST while staying 100% opaque.
    // Phase 2 (0.55 -> 1.0): Cover image ghosts/fades out AFTER shrinking.
    val shrinkStartOffset = if (isSearchRevealed) 240f else 110f
    val shrinkEndOffset = if (isSearchRevealed) 920f else 760f

    val shrinkFraction by remember(isSearchRevealed) {
        derivedStateOf {
            if (listState.firstVisibleItemIndex > 0) {
                1f
            } else {
                val offset = listState.firstVisibleItemScrollOffset.toFloat()
                ((offset - shrinkStartOffset) / (shrinkEndOffset - shrinkStartOffset)).coerceIn(0f, 1f)
            }
        }
    }

    val headerBackgroundAlpha by remember {
        derivedStateOf {
            if (listState.firstVisibleItemIndex > 0) {
                1f
            } else {
                ((shrinkFraction - 0.45f) / 0.35f).coerceIn(0f, 1f)
            }
        }
    }
    // Centered playlist title on the pinned header ONLY appears AFTER the playlist title below the cover image is covered by the header
    val headerTitleAlpha by remember {
        derivedStateOf {
            if (listState.firstVisibleItemIndex > 0) {
                1f
            } else if (pinnedHeaderBottomY > 0f && belowCoverTitleBottomY < Float.MAX_VALUE) {
                val overlapPx = pinnedHeaderBottomY - belowCoverTitleBottomY
                (overlapPx / 36f).coerceIn(0f, 1f)
            } else {
                ((shrinkFraction - 0.85f) / 0.15f).coerceIn(0f, 1f)
            }
        }
    }
    val threeDotAlpha by remember {
        derivedStateOf {
            (1f - headerBackgroundAlpha).coerceIn(0f, 1f)
        }
    }

    val headerSolidGradient = remember(dominantColor) {
        Brush.verticalGradient(
            colors = listOf(
                dominantColor,                                         // Top (status bar)
                dominantColor,                                         // Middle (toolbar title)
                lerp(dominantColor, Color(0xFF1C1D24), 0.18f),         // Slightly darkened
                lerp(dominantColor, Color(0xFF14151B), 0.30f)          // Slightly dark / grayed out at bottom (NOT gradient to black)
            )
        )
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(SpotifyPitchBlack)
    ) {
        // 1. Scrollable Content: background color lives inside the header item up to the globe/time row so it scrolls away with the header and is never sticky
        LazyColumn(
            state = listState,
            modifier = Modifier
                .fillMaxSize()
                .nestedScroll(nestedScrollConnection),
            contentPadding = PaddingValues(top = 0.dp, bottom = 128.dp)
        ) {
            // Header: Search & Sort + Shrinking Collage + Title + Metadata + Actions
            item(key = "spotify_playlist_header") {
                Column(
                    modifier = Modifier.fillMaxWidth()
                ) {
                    // Colored Top Section: from Android status bar down to the Globe icon (tracks count) & time text row
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(
                                Brush.verticalGradient(
                                    colorStops = arrayOf(
                                        0.0f to dominantColor,
                                        0.45f to dominantColor,
                                        0.82f to lerp(dominantColor, SpotifyPitchBlack, 0.65f),
                                        1.0f to SpotifyPitchBlack
                                    )
                                )
                            )
                            .padding(
                                top = statusBarTopPadding + 52.dp,
                                start = 20.dp,
                                end = 20.dp,
                                bottom = 6.dp
                            )
                    ) {
                    // Search & Sort row: revealed ONLY on pull-down drag (not initially displayed)
                    AnimatedVisibility(
                        visible = isSearchRevealed || searchInPlaylistQuery.isNotEmpty(),
                        enter = expandVertically(
                            animationSpec = tween(240, easing = CubicBezierEasing(0.23f, 1f, 0.32f, 1f))
                        ) + fadeIn(animationSpec = tween(180)),
                        exit = shrinkVertically(
                            animationSpec = tween(200, easing = CubicBezierEasing(0.23f, 1f, 0.32f, 1f))
                        ) + fadeOut(animationSpec = tween(150))
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(bottom = 12.dp)
                                .graphicsLayer {
                                    alpha = (1f - (shrinkFraction * 1.5f)).coerceIn(0f, 1f)
                                },
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(
                                modifier = Modifier
                                    .weight(1f)
                                    .height(38.dp)
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(Color(0xFF404352))
                                    .padding(horizontal = 12.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.Search,
                                    contentDescription = null,
                                    tint = Color(0xFFB0B4C0),
                                    modifier = Modifier.size(18.dp)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                BasicTextField(
                                    value = searchInPlaylistQuery,
                                    onValueChange = { searchInPlaylistQuery = it },
                                    singleLine = true,
                                    textStyle = TextStyle(color = Color.White, fontSize = 13.sp),
                                    cursorBrush = SolidColor(AuraPlayGreen),
                                    modifier = Modifier.weight(1f),
                                    decorationBox = { inner ->
                                        if (searchInPlaylistQuery.isEmpty()) {
                                            Text(
                                                text = "Find in playlist",
                                                color = Color(0xFFB0B4C0),
                                                fontSize = 13.sp
                                            )
                                        }
                                        inner()
                                    }
                                )
                                if (searchInPlaylistQuery.isNotEmpty()) {
                                    IconButton(
                                        onClick = { searchInPlaylistQuery = "" },
                                        modifier = Modifier.size(24.dp)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Rounded.Close,
                                            contentDescription = "Clear",
                                            tint = Color(0xFFB0B4C0),
                                            modifier = Modifier.size(16.dp)
                                        )
                                    }
                                }
                            }

                            Spacer(modifier = Modifier.width(10.dp))

                            Surface(
                                shape = RoundedCornerShape(8.dp),
                                color = Color(0xFF404352),
                                modifier = Modifier
                                    .height(38.dp)
                                    .clip(RoundedCornerShape(8.dp))
                                    .clickable {
                                        sortOption = when (sortOption) {
                                            PlaylistSortOption.DEFAULT -> PlaylistSortOption.TITLE
                                            PlaylistSortOption.TITLE -> PlaylistSortOption.ARTIST
                                            PlaylistSortOption.ARTIST -> PlaylistSortOption.DURATION
                                            PlaylistSortOption.DURATION -> PlaylistSortOption.DEFAULT
                                        }
                                        val label = when (sortOption) {
                                            PlaylistSortOption.DEFAULT -> "Default Order"
                                            PlaylistSortOption.TITLE -> "Sorted by Title"
                                            PlaylistSortOption.ARTIST -> "Sorted by Artist"
                                            PlaylistSortOption.DURATION -> "Sorted by Duration"
                                        }
                                        Toast.makeText(context, label, Toast.LENGTH_SHORT).show()
                                    }
                            ) {
                                Box(
                                    modifier = Modifier.padding(horizontal = 14.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        text = sortOption.displayName,
                                        color = Color(0xFFD0D4E0),
                                        fontSize = 12.5.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            }
                        }
                    }

                    // Centered Shrinking & Ghost/Transparent Collage Cover (Larger ~280dp)
                    // Shrinks FIRST (0.0 -> 0.55) while fully opaque, then ghosts/fades out (0.55 -> 0.92)
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = if (isSearchRevealed) 10.dp else 16.dp)
                            .graphicsLayer {
                                val scalePhase = (shrinkFraction / 0.65f).coerceIn(0f, 1f)
                                val coverScale = (1f - (scalePhase * 0.48f)).coerceIn(0.52f, 1f)
                                val ghostAlpha = if (shrinkFraction < 0.50f) {
                                    1f
                                } else {
                                    (1f - ((shrinkFraction - 0.50f) / 0.38f)).coerceIn(0f, 1f)
                                }
                                scaleX = coverScale
                                scaleY = coverScale
                                alpha = ghostAlpha
                                translationY = shrinkFraction * 45f
                            },
                        contentAlignment = Alignment.Center
                    ) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth(0.72f)
                                .aspectRatio(1f)
                                .sizeIn(maxWidth = 285.dp, maxHeight = 285.dp)
                                .clip(RoundedCornerShape(8.dp))
                                .background(Color(0xFF1E2028))
                                .clickable { onPickCoverImage() },
                            contentAlignment = Alignment.Center
                        ) {
                            AuraPlaylistCover(
                                coverUrl = playlist.coverUrl,
                                tracks = playlist.tracks,
                                modifier = Modifier.fillMaxSize(),
                                shape = RoundedCornerShape(8.dp)
                            )
                        }
                    }

                    // Playlist Title (tracked so pinned header title only appears after this title is covered by the header)
                    Text(
                        text = playlist.name,
                        color = Color.White,
                        fontSize = 22.sp,
                        fontWeight = FontWeight.Bold,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.onGloballyPositioned { coords ->
                            belowCoverTitleBottomY = coords.positionInRoot().y + coords.size.height
                        }
                    )

                    // Description if present (filter out any legacy "Imported from Spotify" description)
                    val cleanDesc = playlist.description.replace(Regex("""(?i)^Imported from Spotify.*"""), "").trim()
                    if (cleanDesc.isNotBlank()) {
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = cleanDesc,
                            color = Color.White.copy(alpha = 0.70f),
                            fontSize = 12.5.sp,
                            lineHeight = 17.sp,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis
                        )
                    }

                    // Metadata Row (Globe + tracks + duration)
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.padding(top = 6.dp)
                    ) {
                        Canvas(modifier = Modifier.size(13.dp)) {
                            val stroke = Stroke(width = 1.2.dp.toPx())
                            drawCircle(color = Color.White.copy(alpha = 0.60f), style = stroke)
                            drawLine(
                                color = Color.White.copy(alpha = 0.60f),
                                start = Offset(0f, size.height / 2),
                                end = Offset(size.width, size.height / 2),
                                strokeWidth = 1.2.dp.toPx()
                            )
                            drawOval(
                                color = Color.White.copy(alpha = 0.60f),
                                size = Size(size.width * 0.5f, size.height),
                                topLeft = Offset(size.width * 0.25f, 0f),
                                style = stroke
                            )
                        }
                        Spacer(modifier = Modifier.width(6.dp))
                        val totalSec = playlist.tracks.sumOf { it.durationSeconds.toLong() }
                        val hours = totalSec / 3600
                        val minutes = (totalSec % 3600) / 60
                        val durText = if (hours > 0) "${hours}h ${minutes}min" else "${minutes}min"
                        Text(
                            text = "${playlist.tracks.size} tracks • $durText",
                            color = Color.White.copy(alpha = 0.65f),
                            fontSize = 12.sp
                        )
                    }
                    } // End of Colored Top Section (stops at Globe icon & time text row)

                    // Action buttons row (Add, Download, Share, 3-dots, Shuffle, Big Play) on pure black background
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(SpotifyPitchBlack)
                            .padding(horizontal = 20.dp)
                            .padding(top = 10.dp, bottom = 12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // Download All -> Pause / Resume + Cancel while this playlist is downloading
                        val isBatchDownloading = playlist.tracks.any { downloadingIds.contains(it.videoId) }
                        if (isBatchDownloading) {
                            IconButton(
                                onClick = { if (isDownloadPaused) onResumeDownloads() else onPauseDownloads() },
                                modifier = Modifier.size(44.dp)
                            ) {
                                Icon(
                                    imageVector = if (isDownloadPaused) Icons.Rounded.PlayArrow else Icons.Rounded.Pause,
                                    contentDescription = if (isDownloadPaused) "Resume downloads" else "Pause downloads",
                                    tint = AuraNeonLime,
                                    modifier = Modifier.size(24.dp)
                                )
                            }
                            IconButton(
                                onClick = onCancelDownloads,
                                modifier = Modifier.size(44.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.Close,
                                    contentDescription = "Cancel downloads",
                                    tint = Color.White.copy(alpha = 0.85f),
                                    modifier = Modifier.size(22.dp)
                                )
                            }
                        } else {
                            IconButton(
                                onClick = { onDownloadAll(playlist.tracks) },
                                modifier = Modifier.size(38.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.Download,
                                    contentDescription = "Download All",
                                    tint = Color.White.copy(alpha = 0.85f),
                                    modifier = Modifier.size(22.dp)
                                )
                            }
                        }

                        // Share
                        IconButton(
                            onClick = {
                                val shareIntent = Intent(Intent.ACTION_SEND).apply {
                                    type = "text/plain"
                                    putExtra(Intent.EXTRA_TEXT, "Listen to playlist '${playlist.name}' (${playlist.tracks.size} songs) on Aura Music")
                                }
                                context.startActivity(Intent.createChooser(shareIntent, "Share Playlist"))
                            },
                            modifier = Modifier.size(38.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.Share,
                                contentDescription = "Share",
                                tint = Color.White.copy(alpha = 0.85f),
                                modifier = Modifier.size(20.dp)
                            )
                        }

                        // Options
                        IconButton(
                            onClick = { showMenu = true },
                            modifier = Modifier.size(38.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.MoreVert,
                                contentDescription = "Options",
                                tint = Color.White.copy(alpha = 0.85f),
                                modifier = Modifier.size(22.dp)
                            )
                        }

                        // Shuffle
                        IconButton(
                            onClick = {
                                val contextualTracks = processedTracks.map { it.copy(album = playlist.name) }
                                val shuffled = contextualTracks.shuffled()
                                shuffled.firstOrNull()?.let { first -> onPlayTrack(first, shuffled) }
                            },
                            modifier = Modifier.size(38.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.Shuffle,
                                contentDescription = "Shuffle",
                                tint = Color.White.copy(alpha = 0.85f),
                                modifier = Modifier.size(22.dp)
                            )
                        }

                        Spacer(modifier = Modifier.weight(1f))

                        // Big Spotify Green Play Button
                        Box(
                            modifier = Modifier
                                .size(52.dp)
                                .clip(CircleShape)
                                .background(AuraPlayGreen)
                                .clickable {
                                    val contextualTracks = processedTracks.map { it.copy(album = playlist.name) }
                                    contextualTracks.firstOrNull()?.let { first ->
                                        onPlayTrack(first, contextualTracks)
                                    }
                                },
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.PlayArrow,
                                contentDescription = "Play All",
                                tint = Color.Black,
                                modifier = Modifier.size(34.dp)
                            )
                        }
                    }
                }
            }

            // Track list items
            itemsIndexed(
                items = processedTracks,
                key = { index, track -> "playlist_track_${index}_${track.videoId.ifBlank { track.id }}" }
            ) { _, track ->
                val isCurrent = currentPlayingId == track.videoId || (currentPlayingId != null && currentPlayingId == track.id)
                SpotifyTrackRowItem(
                    track = track,
                    isCurrent = isCurrent,
                    isPlaying = isPlaying,
                    isFavorite = favorites.any { it.videoId == track.videoId },
                    isDownloaded = downloads.any { it.videoId == track.videoId && it.isDownloaded },
                    isDownloading = downloadingIds.contains(track.videoId),
                    downloadProgress = downloadProgressMap[track.videoId],
                    onClick = {
                        val contextualTracks = processedTracks.map { it.copy(album = playlist.name) }
                        onPlayTrack(track.copy(album = playlist.name), contextualTracks)
                    },
                    onToggleFavorite = { onToggleFavorite(track) },
                    onDownload = { onDownloadTrack(track) },
                    onOptionsClick = { selectedModalTrack = track }
                )
            }

            // Bottom blank space equivalent to ~2 song tiles so mini player never covers the last item
            item(key = "playlist_bottom_blank_space") {
                Spacer(modifier = Modifier.height(16.dp))
            }
        }

        // Fast scrollbar on the right edge
        LazyColumnScrollbar(
            state = listState,
            modifier = Modifier.align(Alignment.CenterEnd),
            onlyWhenTrackScrolling = true
        )

        // Pinned Top Bar covering Android Status Bar (battery, time, wifi) + Toolbar
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .align(Alignment.TopCenter)
                .onGloballyPositioned { coords ->
                    pinnedHeaderBottomY = coords.positionInRoot().y + coords.size.height
                }
                .then(
                    if (headerBackgroundAlpha > 0.001f) {
                        Modifier.background(headerSolidGradient, alpha = headerBackgroundAlpha)
                    } else {
                        Modifier
                    }
                )
        ) {
            // Android Status Bar Area (battery, time, wifi) included in header background color
            Spacer(modifier = Modifier.windowInsetsTopHeight(WindowInsets.statusBars))

            // Row 1: Dedicated Navigation Row with Back button & 3-dots button (dark circle backgrounds per reference)
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp)
                    .padding(horizontal = 14.dp),
                contentAlignment = Alignment.Center
            ) {
                // Centered Title appearing as collage shrinks
                Text(
                    text = playlist.name,
                    color = Color.White,
                    fontSize = 17.sp,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    textAlign = TextAlign.Center,
                    modifier = Modifier
                        .padding(horizontal = 48.dp)
                        .graphicsLayer { alpha = headerTitleAlpha }
                )

                // Back button - Flat, no circle fill, larger icon size
                IconButton(
                    onClick = onBack,
                    modifier = Modifier
                        .align(Alignment.CenterStart)
                        .size(44.dp)
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Rounded.ArrowBack,
                        contentDescription = "Back",
                        tint = Color.White,
                        modifier = Modifier.size(26.dp)
                    )
                }

                // 3-dots Options button - Flat, no circle fill, larger icon size
                if (threeDotAlpha > 0.01f) {
                    IconButton(
                        onClick = { showMenu = true },
                        modifier = Modifier
                            .align(Alignment.CenterEnd)
                            .size(44.dp)
                            .graphicsLayer { alpha = threeDotAlpha }
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.MoreVert,
                            contentDescription = "Playlist Options",
                            tint = Color.White,
                            modifier = Modifier.size(26.dp)
                        )
                    }
                }
            }
        }
    }

    // Dialog to rename playlist
    if (showEditTitleDialog) {
        AlertDialog(
            onDismissRequest = { showEditTitleDialog = false },
            containerColor = SpotifyCard,
            title = {
                Text("Rename Playlist", color = TextPrimary, fontWeight = FontWeight.Bold)
            },
            text = {
                OutlinedTextField(
                    value = newTitleInput,
                    onValueChange = { newTitleInput = it },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = AuraPlayGreen,
                        unfocusedBorderColor = TextSecondary.copy(alpha = 0.3f),
                        focusedTextColor = TextPrimary,
                        unfocusedTextColor = TextPrimary
                    )
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        if (newTitleInput.isNotBlank()) {
                            onUpdateTitle(newTitleInput.trim())
                        }
                        showEditTitleDialog = false
                    }
                ) {
                    Text("Save", color = AuraPlayGreen, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showEditTitleDialog = false }) {
                    Text("Cancel", color = TextSecondary)
                }
            }
        )
    }

    if (showMenu) {
        AuraPlaylistOptionsModal(
            playlist = playlist,
            onDismiss = { showMenu = false },
            onFindInPlaylist = {
                showMenu = false
                isSearchRevealed = true
            },
            onDownloadAll = {
                showMenu = false
                onDownloadAll(playlist.tracks)
            },
            onChangeCover = {
                showMenu = false
                mediaPickerLauncher.launch("image/*")
            },
            onResetCover = if (playlist.coverUrl.isNotBlank()) {
                {
                    showMenu = false
                    onUpdateCover("")
                    Toast.makeText(context, "Cover reset to Spotify collage", Toast.LENGTH_SHORT).show()
                }
            } else null,
            onRename = {
                showMenu = false
                newTitleInput = playlist.name
                showEditTitleDialog = true
            },
            onDelete = {
                showMenu = false
                onDeletePlaylist()
            }
        )
    }

    selectedModalTrack?.let { modalTrack ->
        AuraTrackOptionsModal(
            track = modalTrack,
            isFavorite = favorites.any { it.videoId == modalTrack.videoId },
            onDismiss = { selectedModalTrack = null },
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
            onRemoveDownload = onRemoveDownload?.let { cb ->
                {
                    cb(modalTrack)
                    selectedModalTrack = null
                }
            },
            onRemoveFromPlaylist = onRemoveTrackFromPlaylist?.let { cb ->
                {
                    cb(modalTrack.videoId)
                    selectedModalTrack = null
                }
            },
            onWatchVideo = {
                selectedModalTrack = null
                val contextualTracks = playlist.tracks.map { it.copy(album = playlist.name) }
                onPlayTrack(modalTrack.copy(album = playlist.name), contextualTracks)
                com.auramusic.app.core.media.AuraPlayerManager.setVideoMode(true)
            }
        )
    }

    // Modal: Add Song to Playlist
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

    // Modal: Create Playlist on the fly
    if (showCreatePlaylistModal) {
        AuraCreatePlaylistModal(
            onDismiss = { showCreatePlaylistModal = false },
            onCreate = { title, desc, coverUri ->
                showCreatePlaylistModal = false
                onCreatePlaylist(title, desc, coverUri)
                Toast.makeText(context, "Created playlist: $title", Toast.LENGTH_SHORT).show()
            }
        )
    }
}

/**
 * Persists picked image content URI into private app storage for stable playlist cover display.
 */
private fun savePlaylistCoverLocally(context: Context, playlistId: String, uri: Uri): String {
    return try {
        val dir = File(context.filesDir, "playlist_covers").apply { mkdirs() }
        val destFile = File(dir, "cover_${playlistId}_${System.currentTimeMillis()}.jpg")
        context.contentResolver.openInputStream(uri)?.use { input ->
            destFile.outputStream().use { output ->
                input.copyTo(output)
            }
        }
        destFile.absolutePath
    } catch (e: Exception) {
        uri.toString()
    }
}
