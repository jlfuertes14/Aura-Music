package com.auramusic.app.ui.screens

import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.auramusic.app.core.media.AuraPlayerManager
import com.auramusic.app.domain.model.AuraPlaylist
import com.auramusic.app.domain.model.AuraTrack
import com.auramusic.app.domain.model.CatalogDetail
import com.auramusic.app.domain.model.YtmCommunityPlaylist
import com.auramusic.app.ui.components.AuraAddToPlaylistModal
import com.auramusic.app.ui.components.AuraCreatePlaylistModal
import com.auramusic.app.ui.components.AuraTrackOptionsModal
import com.auramusic.app.ui.theme.AuraNeonLime
import com.auramusic.app.ui.theme.SpotifyPitchBlack
import com.auramusic.app.ui.theme.TextPrimary
import com.auramusic.app.ui.theme.TextSecondary

private val FluidEasing = CubicBezierEasing(0.23f, 1f, 0.32f, 1f)

@Composable
fun SearchCatalogDetailScreen(
    catalogDetail: CatalogDetail,
    isLoading: Boolean,
    currentPlayingId: String?,
    isPlaying: Boolean,
    favorites: List<AuraTrack> = emptyList(),
    downloads: List<AuraTrack> = emptyList(),
    playlists: List<AuraPlaylist> = emptyList(),
    onClose: () -> Unit,
    onPlayTrack: (AuraTrack, List<AuraTrack>) -> Unit,
    onPlayMusicVideo: (AuraTrack, List<AuraTrack>) -> Unit,
    onOpenPlaylist: (YtmCommunityPlaylist) -> Unit,
    onOpenAlbum: (AuraTrack) -> Unit,
    onToggleFavorite: (AuraTrack) -> Unit = {},
    onDownloadTrack: (AuraTrack) -> Unit = {},
    onOpenArtist: (String, String) -> Unit = { _, _ -> },
    onAddTrackToPlaylist: (String, AuraTrack) -> Unit = { _, _ -> },
    onCreatePlaylist: (String, String, String) -> Unit = { _, _, _ -> },
    onRetry: () -> Unit
) {
    val context = LocalContext.current
    val configuration = LocalConfiguration.current
    val pageWidth = (configuration.screenWidthDp.dp - 48.dp)

    var selectedModalTrack by remember { mutableStateOf<AuraTrack?>(null) }
    var trackForAddToPlaylist by remember { mutableStateOf<AuraTrack?>(null) }
    var showCreatePlaylistModal by remember { mutableStateOf(false) }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(SpotifyPitchBlack)
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            // =========================================================================
            // Top App Bar: Back Button & Dynamic Category Title
            // =========================================================================
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(
                        Brush.verticalGradient(
                            colors = listOf(
                                Color(0xFF14161E),
                                Color(0xFF101218).copy(alpha = 0.95f),
                                SpotifyPitchBlack
                            )
                        )
                    )
                    .statusBarsPadding()
                    .padding(horizontal = 8.dp, vertical = 6.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(
                        onClick = onClose,
                        modifier = Modifier
                            .size(44.dp)
                            .clip(CircleShape)
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Rounded.ArrowBack,
                            contentDescription = "Back",
                            tint = Color.White,
                            modifier = Modifier.size(24.dp)
                        )
                    }

                    Spacer(modifier = Modifier.width(8.dp))

                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = catalogDetail.title,
                            color = TextPrimary,
                            style = MaterialTheme.typography.titleLarge.copy(
                                fontWeight = FontWeight.Bold,
                                letterSpacing = (-0.3).sp
                            ),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            text = if (catalogDetail.browseId == "FEmusic_new_releases") {
                                "Latest Albums & Releases"
                            } else {
                                "YouTube Music Catalog"
                            },
                            color = TextSecondary,
                            style = MaterialTheme.typography.bodySmall,
                            maxLines = 1
                        )
                    }
                }
            }

            // =========================================================================
            // Main Content: Loading Shimmer, Error, or Carousel Shelves
            // =========================================================================
            Box(modifier = Modifier.weight(1f)) {
                if (isLoading && catalogDetail.shelves.isEmpty()) {
                    CatalogDetailSkeleton()
                } else if (!catalogDetail.error.isNullOrBlank() && catalogDetail.shelves.isEmpty()) {
                    CatalogErrorState(
                        errorMessage = catalogDetail.error,
                        onRetry = onRetry
                    )
                } else {
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(top = 12.dp, bottom = 120.dp)
                    ) {
                        itemsIndexed(
                            items = catalogDetail.shelves,
                            key = { index, shelf -> "cat_shelf_${index}_${shelf.title}" }
                        ) { _, shelf ->
                            MoodCarouselShelf(
                                shelf = shelf,
                                pageWidth = pageWidth,
                                currentPlayingId = currentPlayingId,
                                isPlaying = isPlaying,
                                onPlayTrack = onPlayTrack,
                                onPlayMusicVideo = onPlayMusicVideo,
                                onOpenPlaylist = onOpenPlaylist,
                                onOpenAlbum = onOpenAlbum,
                                onOptionsClick = { selectedModalTrack = it }
                            )
                        }

                        if (catalogDetail.shelves.isEmpty() && !isLoading) {
                            item {
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(top = 60.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        text = "No playlists or albums available in this category.",
                                        color = TextSecondary,
                                        style = MaterialTheme.typography.bodyMedium
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }

        // =========================================================================
        // Track Options Modal (Play Next, Add to Queue, Liked Songs, Add to Playlist)
        // =========================================================================
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
                    onOpenArtist(modalTrack.artist, modalTrack.title)
                },
                isDownloaded = downloads.any { it.videoId == modalTrack.videoId },
                onDownloadTrack = {
                    onDownloadTrack(modalTrack)
                    Toast.makeText(context, "Downloading ${modalTrack.title}…", Toast.LENGTH_SHORT).show()
                    selectedModalTrack = null
                },
                onWatchVideo = {
                    selectedModalTrack = null
                    onPlayTrack(modalTrack, listOf(modalTrack))
                    AuraPlayerManager.setVideoMode(true)
                }
            )
        }

        // Add Track To Playlist Modal
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
}

@Composable
private fun CatalogErrorState(
    errorMessage: String,
    onRetry: () -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Text(
                text = "Could not load category",
                color = TextPrimary,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold
            )
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = errorMessage,
                color = TextSecondary,
                style = MaterialTheme.typography.bodySmall,
                maxLines = 2
            )
            Spacer(modifier = Modifier.height(16.dp))
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(20.dp))
                    .background(Color(0xFF232530))
                    .clickable(onClick = onRetry)
                    .padding(horizontal = 20.dp, vertical = 10.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Rounded.Refresh,
                        contentDescription = "Retry",
                        tint = AuraNeonLime,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "Retry",
                        color = AuraNeonLime,
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }
    }
}

@Composable
private fun CatalogDetailSkeleton() {
    val transition = rememberInfiniteTransition(label = "catalogSkeleton")
    val alpha by transition.animateFloat(
        initialValue = 0.2f,
        targetValue = 0.55f,
        animationSpec = infiniteRepeatable(
            animation = tween(850, easing = FluidEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "skeletonAlpha"
    )

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(top = 16.dp, start = 16.dp, end = 16.dp),
        verticalArrangement = Arrangement.spacedBy(24.dp)
    ) {
        repeat(3) {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                // Section Title Placeholder
                Box(
                    modifier = Modifier
                        .size(width = 160.dp, height = 20.dp)
                        .clip(RoundedCornerShape(6.dp))
                        .background(Color.White.copy(alpha = alpha))
                )
                // Row of Card Placeholders
                LazyRow(
                    horizontalArrangement = Arrangement.spacedBy(14.dp),
                    userScrollEnabled = false
                ) {
                    items(4) {
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Box(
                                modifier = Modifier
                                    .size(136.dp)
                                    .clip(RoundedCornerShape(10.dp))
                                    .background(Color.White.copy(alpha = alpha))
                            )
                            Box(
                                modifier = Modifier
                                    .size(width = 110.dp, height = 14.dp)
                                    .clip(RoundedCornerShape(4.dp))
                                    .background(Color.White.copy(alpha = alpha * 0.7f))
                            )
                        }
                    }
                }
            }
        }
    }
}
