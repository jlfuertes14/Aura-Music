package com.auramusic.app.ui.screens

import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.BorderStroke
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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.QueueMusic
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.FavoriteBorder
import androidx.compose.material.icons.rounded.LibraryAdd
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.PlaylistAdd
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.runtime.DisposableEffect
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView
import com.auramusic.app.core.media.AuraPlayerManager
import com.auramusic.app.domain.model.ArtistCarouselItem
import com.auramusic.app.domain.model.ArtistDetail
import com.auramusic.app.domain.model.AuraTrack
import com.auramusic.app.ui.components.AlbumCarouselSkeleton
import com.auramusic.app.ui.components.ArtistCarouselSkeleton
import com.auramusic.app.ui.components.TrackItemSkeleton
import com.auramusic.app.ui.components.auraShimmer
import com.auramusic.app.ui.theme.AuraNeonLime
import com.auramusic.app.ui.theme.SpotifyPitchBlack
import com.auramusic.app.ui.theme.TextPrimary
import com.auramusic.app.ui.theme.TextSecondary

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ArtistDetailScreen(
    artistDetail: ArtistDetail,
    isLoading: Boolean,
    currentPlayingId: String?,
    onClose: () -> Unit,
    onPlayTrack: (AuraTrack, List<AuraTrack>) -> Unit,
    onPlayMusicVideo: (AuraTrack, List<AuraTrack>) -> Unit = onPlayTrack,
    onToggleFavorite: (AuraTrack) -> Unit,
    onOpenArtist: (String) -> Unit = {},
    onOpenPlaylist: (ArtistCarouselItem) -> Unit = {}
) {
    val context = LocalContext.current
    var selectedModalTrack by remember { mutableStateOf<AuraTrack?>(null) }
    var isExpandedSongs by remember(artistDetail.id) { mutableStateOf(false) }
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    val allTracks = remember(artistDetail.topSongs) {
        artistDetail.topSongs.map { it.track }
    }

    BackHandler {
        onClose()
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(SpotifyPitchBlack)
    ) {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(bottom = 140.dp)
        ) {
            // 1. Top Screen Banner Picture with Name & Subs on the Bottom-Right
            item {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(430.dp)
                        .background(Color(0xFF161820))
                ) {
                    if (artistDetail.bannerUrl.isNotBlank()) {
                        AsyncImage(
                            model = artistDetail.bannerUrl,
                            contentDescription = artistDetail.name,
                            contentScale = ContentScale.Crop,
                            modifier = Modifier.fillMaxSize()
                        )
                    } else if (isLoading) {
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .auraShimmer()
                        )
                    }

                    ArtistMotionPlayer(
                        motion = artistDetail.motion,
                        modifier = Modifier.fillMaxSize()
                    )

                    // Gradient overlay to darken bottom so text is crisp and readable
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(
                                Brush.verticalGradient(
                                    colors = listOf(
                                        Color.Black.copy(alpha = 0.30f),
                                        Color.Transparent,
                                        Color.Black.copy(alpha = 0.65f),
                                        SpotifyPitchBlack
                                    )
                                )
                            )
                    )

                    // Back button at top-left
                    IconButton(
                        onClick = onClose,
                        modifier = Modifier
                            .statusBarsPadding()
                            .padding(start = 8.dp, top = 4.dp)
                            .size(44.dp)
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Rounded.ArrowBack,
                            contentDescription = "Back",
                            tint = Color.White,
                            modifier = Modifier.size(24.dp)
                        )
                    }

                    // Artist name on bottom right & subscriber count directly below it
                    Column(
                        modifier = Modifier
                            .align(Alignment.BottomEnd)
                            .padding(end = 20.dp, bottom = 12.dp),
                        horizontalAlignment = Alignment.End
                    ) {
                        Text(
                            text = artistDetail.name,
                            color = Color.White,
                            fontWeight = FontWeight.ExtraBold,
                            fontSize = 26.sp,
                            textAlign = TextAlign.End,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Spacer(modifier = Modifier.height(3.dp))
                        if (artistDetail.subscribers.isNotBlank()) {
                            Text(
                                text = artistDetail.subscribers,
                                color = AuraNeonLime,
                                fontWeight = FontWeight.SemiBold,
                                fontSize = 13.sp,
                                textAlign = TextAlign.End
                            )
                        } else if (isLoading) {
                            Box(
                                modifier = Modifier
                                    .width(96.dp)
                                    .height(13.dp)
                                    .clip(RoundedCornerShape(4.dp))
                                    .auraShimmer()
                            )
                        }
                    }
                }
            }

            // 2. Section Header: Popular Songs
            item {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp, vertical = 14.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Popular Songs",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        color = Color.White,
                        fontSize = 19.sp
                    )
                }
            }

            val displayedSongs = if (isExpandedSongs) artistDetail.topSongs else artistDetail.topSongs.take(5)

            // 3. Ranked Popular Songs Tiles with 3-dot vertical menu (or Skeleton loading)
            if (isLoading && artistDetail.topSongs.isEmpty()) {
                items(5) {
                    com.auramusic.app.ui.components.TrackItemSkeleton()
                }
            } else {
                items(displayedSongs, key = { "top_${it.rank}_${it.track.videoId}" }) { songItem ->
                    val isCurrent = currentPlayingId == songItem.track.videoId
                    ArtistPopularSongRow(
                        rank = songItem.rank,
                        track = songItem.track,
                        playCount = songItem.playCount,
                        isCurrent = isCurrent,
                        onClick = { onPlayTrack(songItem.track, allTracks) },
                        onOptionsClick = { selectedModalTrack = songItem.track }
                    )
                }
            }

            // "See More" Button for Popular Songs
            if (artistDetail.topSongs.size > 5) {
                item {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 20.dp, vertical = 6.dp),
                        contentAlignment = Alignment.CenterStart
                    ) {
                        Surface(
                            shape = RoundedCornerShape(20.dp),
                            color = Color(0xFF1E2028),
                            border = BorderStroke(1.dp, Color(0xFF2E323D)),
                            modifier = Modifier
                                .clip(RoundedCornerShape(20.dp))
                                .clickable { isExpandedSongs = !isExpandedSongs }
                        ) {
                            Text(
                                text = if (isExpandedSongs) "Show Less" else "See More (${artistDetail.topSongs.size - 5} more)",
                                color = Color.White,
                                fontWeight = FontWeight.Bold,
                                fontSize = 12.5.sp,
                                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                            )
                        }
                    }
                }
            }

            // 4. Carousels Section (Albums, Singles & EPs, Videos, Live performances, Featured on, Playlists, Fans might also like)
            if (artistDetail.carousels.isNotEmpty()) {
                items(artistDetail.carousels, key = { "carousel_${it.title}" }) { carousel ->
                    val isSimilarArtists = carousel.title.contains("fans might also like", ignoreCase = true) ||
                            carousel.title.contains("similar", ignoreCase = true)
                    val isVideos = carousel.title.contains("video", ignoreCase = true) ||
                            carousel.title.contains("live performance", ignoreCase = true)
                    val carouselVideoTracks = if (isVideos) carousel.items.mapNotNull { item ->
                        val videoId = item.videoId.ifBlank { item.id }
                        videoId.takeIf { it.isNotBlank() }?.let {
                            AuraTrack(
                                id = "yt_$it",
                                videoId = it,
                                title = item.title,
                                artist = artistDetail.name,
                                album = carousel.title,
                                artworkUrl = item.artworkUrl
                            )
                        }
                    } else emptyList()

                    Spacer(modifier = Modifier.height(20.dp))
                    Column(modifier = Modifier.fillMaxWidth().padding(bottom = 6.dp)) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 20.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = carousel.title,
                                style = MaterialTheme.typography.titleLarge,
                                fontWeight = FontWeight.Bold,
                                color = Color.White,
                                fontSize = 19.sp
                            )
                            if (carousel.items.isNotEmpty()) {
                                Text(
                                    text = "${carousel.items.size} items",
                                    color = TextSecondary,
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.SemiBold
                                )
                            }
                        }
                        Spacer(modifier = Modifier.height(14.dp))

                        LazyRow(
                            contentPadding = PaddingValues(horizontal = 20.dp),
                            horizontalArrangement = Arrangement.spacedBy(14.dp)
                        ) {
                            items(carousel.items, key = { "${carousel.title}_${it.id}_${it.title}" }) { item ->
                                when {
                                    isSimilarArtists -> {
                                        // Fans might also like: Circular Avatar
                                        Column(
                                            modifier = Modifier
                                                .width(104.dp)
                                                .clip(RoundedCornerShape(12.dp))
                                                .clickable { onOpenArtist(item.title) },
                                            horizontalAlignment = Alignment.CenterHorizontally
                                        ) {
                                            Box(
                                                modifier = Modifier
                                                    .size(96.dp)
                                                    .clip(CircleShape)
                                                    .background(Color(0xFF1E2028))
                                            ) {
                                                AsyncImage(
                                                    model = item.highResArtworkUrl,
                                                    contentDescription = item.title,
                                                    contentScale = ContentScale.Crop,
                                                    modifier = Modifier.fillMaxSize()
                                                )
                                            }
                                            Spacer(modifier = Modifier.height(8.dp))
                                            Text(
                                                text = item.title,
                                                color = Color.White,
                                                fontWeight = FontWeight.Bold,
                                                fontSize = 13.sp,
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis,
                                                textAlign = TextAlign.Center
                                            )
                                            if (item.subtitle.isNotBlank()) {
                                                Text(
                                                    text = item.subtitle,
                                                    color = TextSecondary,
                                                    fontSize = 11.5.sp,
                                                    maxLines = 1,
                                                    overflow = TextOverflow.Ellipsis,
                                                    textAlign = TextAlign.Center
                                                )
                                            }
                                        }
                                    }
                                    isVideos -> {
                                        // Videos & Live Performances: 16:9 Widescreen Cards
                                        Column(
                                            modifier = Modifier
                                                .width(192.dp)
                                                .clip(RoundedCornerShape(12.dp))
                                                .clickable {
                                                    carouselVideoTracks.firstOrNull { track ->
                                                        track.videoId == item.videoId.ifBlank { item.id }
                                                    }?.let { onPlayMusicVideo(it, carouselVideoTracks) }
                                                }
                                        ) {
                                            Box(
                                                modifier = Modifier
                                                    .width(192.dp)
                                                    .height(108.dp)
                                                    .clip(RoundedCornerShape(12.dp))
                                                    .background(Color(0xFF1E2028))
                                            ) {
                                                AsyncImage(
                                                    model = item.highResArtworkUrl,
                                                    contentDescription = item.title,
                                                    contentScale = ContentScale.Crop,
                                                    modifier = Modifier.fillMaxSize()
                                                )
                                                // Play badge overlay
                                                Surface(
                                                    shape = CircleShape,
                                                    color = Color.Black.copy(alpha = 0.65f),
                                                    modifier = Modifier
                                                        .align(Alignment.Center)
                                                        .size(36.dp)
                                                ) {
                                                    Icon(
                                                        imageVector = Icons.Rounded.PlayArrow,
                                                        contentDescription = "Play",
                                                        tint = AuraNeonLime,
                                                        modifier = Modifier
                                                            .padding(6.dp)
                                                            .fillMaxSize()
                                                    )
                                                }
                                            }
                                            Spacer(modifier = Modifier.height(8.dp))
                                            Text(
                                                text = item.title,
                                                color = Color.White,
                                                fontWeight = FontWeight.Bold,
                                                fontSize = 13.sp,
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis
                                            )
                                            if (item.subtitle.isNotBlank()) {
                                                Text(
                                                    text = item.subtitle,
                                                    color = TextSecondary,
                                                    fontSize = 11.5.sp,
                                                    maxLines = 1,
                                                    overflow = TextOverflow.Ellipsis
                                                )
                                            }
                                        }
                                    }
                                    else -> {
                                        // Square Release Cards (Albums, Singles & EPs, Featured on, Playlists)
                                        Column(
                                            modifier = Modifier
                                                .width(130.dp)
                                                .clip(RoundedCornerShape(14.dp))
                                                .clickable {
                                                    onOpenPlaylist(item)
                                                }
                                        ) {
                                            Box(
                                                modifier = Modifier
                                                    .size(130.dp)
                                                    .clip(RoundedCornerShape(12.dp))
                                                    .background(Color(0xFF1E2028))
                                            ) {
                                                AsyncImage(
                                                    model = item.highResArtworkUrl,
                                                    contentDescription = item.title,
                                                    contentScale = ContentScale.Crop,
                                                    modifier = Modifier.fillMaxSize()
                                                )
                                                val badge = when {
                                                    carousel.title.contains("Single", ignoreCase = true) -> "Single"
                                                    carousel.title.contains("EP", ignoreCase = true) -> "EP"
                                                    carousel.title.contains("Album", ignoreCase = true) -> "Album"
                                                    carousel.title.contains("Playlist", ignoreCase = true) -> "Playlist"
                                                    else -> ""
                                                }
                                                if (badge.isNotBlank()) {
                                                    Surface(
                                                        shape = RoundedCornerShape(topStart = 6.dp),
                                                        color = Color.Black.copy(alpha = 0.70f),
                                                        modifier = Modifier.align(Alignment.BottomEnd)
                                                    ) {
                                                        Text(
                                                            text = badge,
                                                            color = AuraNeonLime,
                                                            fontSize = 9.5.sp,
                                                            fontWeight = FontWeight.Bold,
                                                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                                        )
                                                    }
                                                }
                                            }
                                            Spacer(modifier = Modifier.height(8.dp))
                                            Text(
                                                text = item.title,
                                                color = Color.White,
                                                fontWeight = FontWeight.Bold,
                                                fontSize = 13.sp,
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis
                                            )
                                            if (item.subtitle.isNotBlank()) {
                                                Text(
                                                    text = item.subtitle,
                                                    color = TextSecondary,
                                                    fontSize = 11.5.sp,
                                                    maxLines = 1,
                                                    overflow = TextOverflow.Ellipsis
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            } else if (artistDetail.albums.isNotEmpty()) {
                // Backward compatibility: render albums if carousels list is empty
                item {
                    Spacer(modifier = Modifier.height(18.dp))
                    Column(modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp)) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 20.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "Albums & Discography",
                                style = MaterialTheme.typography.titleLarge,
                                fontWeight = FontWeight.Bold,
                                color = Color.White,
                                fontSize = 19.sp
                            )
                            Text(
                                text = "${artistDetail.albums.size} releases",
                                color = TextSecondary,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                        Spacer(modifier = Modifier.height(14.dp))
                        LazyRow(
                            contentPadding = PaddingValues(horizontal = 20.dp),
                            horizontalArrangement = Arrangement.spacedBy(14.dp)
                        ) {
                            items(artistDetail.albums, key = { "alb_${it.id}_${it.title}" }) { album ->
                                Column(
                                    modifier = Modifier
                                        .width(130.dp)
                                        .clip(RoundedCornerShape(14.dp))
                                        .clickable {
                                            onOpenPlaylist(
                                                com.auramusic.app.domain.model.ArtistCarouselItem(
                                                    id = album.id,
                                                    title = album.title,
                                                    subtitle = album.year,
                                                    artworkUrl = album.artworkUrl,
                                                    endpointType = "album",
                                                    browseId = album.id
                                                )
                                            )
                                        }
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .size(130.dp)
                                            .clip(RoundedCornerShape(12.dp))
                                            .background(Color(0xFF1E2028))
                                    ) {
                                        AsyncImage(
                                            model = album.highResArtworkUrl,
                                            contentDescription = album.title,
                                            contentScale = ContentScale.Crop,
                                            modifier = Modifier.fillMaxSize()
                                        )
                                        Surface(
                                            shape = RoundedCornerShape(topStart = 6.dp),
                                            color = Color.Black.copy(alpha = 0.70f),
                                            modifier = Modifier.align(Alignment.BottomEnd)
                                        ) {
                                            Text(
                                                text = album.type,
                                                color = AuraNeonLime,
                                                fontSize = 9.5.sp,
                                                fontWeight = FontWeight.Bold,
                                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                            )
                                        }
                                    }
                                    Spacer(modifier = Modifier.height(8.dp))
                                    Text(
                                        text = album.title,
                                        color = Color.White,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 13.sp,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    Text(
                                        text = album.year,
                                        color = TextSecondary,
                                        fontSize = 11.5.sp,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                            }
                        }
                    }
                }
            } else if (isLoading) {
                // Shimmer Skeletons when initial data is loading
                item {
                    Spacer(modifier = Modifier.height(20.dp))
                    Column(modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp)) {
                        Text(
                            text = "Albums & Discography",
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold,
                            color = Color.White,
                            fontSize = 19.sp,
                            modifier = Modifier.padding(horizontal = 20.dp)
                        )
                        Spacer(modifier = Modifier.height(14.dp))
                        AlbumCarouselSkeleton()
                    }
                    Spacer(modifier = Modifier.height(20.dp))
                    Column(modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp)) {
                        Text(
                            text = "Fans might also like",
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold,
                            color = Color.White,
                            fontSize = 19.sp,
                            modifier = Modifier.padding(horizontal = 20.dp)
                        )
                        Spacer(modifier = Modifier.height(14.dp))
                        ArtistCarouselSkeleton()
                    }
                }
            }
        }
    }

        // 3-Dot Vertical Options Modal BottomSheet
        val modalTrack = selectedModalTrack
        if (modalTrack != null) {
            ModalBottomSheet(
                onDismissRequest = { selectedModalTrack = null },
                sheetState = sheetState,
                containerColor = Color(0xFF14161C),
                dragHandle = {
                    Surface(
                        modifier = Modifier.padding(vertical = 10.dp),
                        color = Color.White.copy(alpha = 0.20f),
                        shape = RoundedCornerShape(4.dp)
                    ) {
                        Spacer(modifier = Modifier.size(width = 36.dp, height = 4.dp))
                    }
                }
            ) {
                com.auramusic.app.ui.components.ForceWhiteSystemBarsEffect()
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 22.dp, vertical = 10.dp)
                ) {
                    // Header preview
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp)
                    ) {
                        AsyncImage(
                            model = modalTrack.highResArtworkUrl,
                            contentDescription = modalTrack.title,
                            modifier = Modifier.size(48.dp).clip(RoundedCornerShape(8.dp)),
                            contentScale = ContentScale.Crop
                        )
                        Spacer(modifier = Modifier.width(12.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = modalTrack.title,
                                color = Color.White,
                                fontWeight = FontWeight.Bold,
                                fontSize = 14.5.sp,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Text(
                                text = modalTrack.artist,
                                color = TextSecondary,
                                fontSize = 12.sp,
                                maxLines = 1
                            )
                        }
                    }

                    HorizontalDivider(color = Color.White.copy(alpha = 0.08f), thickness = 0.5.dp)
                    Spacer(modifier = Modifier.height(10.dp))

                    // Options List
                    ArtistModalOption(
                        icon = Icons.AutoMirrored.Rounded.QueueMusic,
                        label = "Play Next",
                        onClick = {
                            AuraPlayerManager.playNext(modalTrack)
                            Toast.makeText(context, "Playing next: ${modalTrack.title}", Toast.LENGTH_SHORT).show()
                            selectedModalTrack = null
                        }
                    )

                    ArtistModalOption(
                        icon = Icons.Rounded.PlaylistAdd,
                        label = "Add to Queue",
                        onClick = {
                            AuraPlayerManager.enqueueTrack(modalTrack)
                            Toast.makeText(context, "Added to queue: ${modalTrack.title}", Toast.LENGTH_SHORT).show()
                            selectedModalTrack = null
                        }
                    )

                    ArtistModalOption(
                        icon = Icons.Rounded.FavoriteBorder,
                        label = "Add to Liked Songs",
                        onClick = {
                            onToggleFavorite(modalTrack)
                            Toast.makeText(context, "Added to Liked Songs", Toast.LENGTH_SHORT).show()
                            selectedModalTrack = null
                        }
                    )

                    ArtistModalOption(
                        icon = Icons.Rounded.LibraryAdd,
                        label = "Add to Playlist",
                        onClick = {
                            Toast.makeText(context, "Saved to library", Toast.LENGTH_SHORT).show()
                            selectedModalTrack = null
                        }
                    )

                    Spacer(modifier = Modifier.height(24.dp))
                }
            }
        }
    }

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun ArtistPopularSongRow(
    rank: Int,
    track: AuraTrack,
    playCount: String,
    isCurrent: Boolean,
    onClick: () -> Unit,
    onOptionsClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .combinedClickable(
                onClick = onClick,
                onLongClick = onOptionsClick
            )
            .padding(horizontal = 20.dp, vertical = 7.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Ranking number 1-10
        Text(
            text = "$rank",
            color = if (isCurrent) AuraNeonLime else TextSecondary,
            fontWeight = FontWeight.Bold,
            fontSize = 14.sp,
            textAlign = TextAlign.Center,
            modifier = Modifier.width(24.dp)
        )

        Spacer(modifier = Modifier.width(12.dp))

        // Album artwork
        AsyncImage(
            model = track.highResArtworkUrl,
            contentDescription = track.title,
            contentScale = ContentScale.Crop,
            modifier = Modifier
                .size(44.dp)
                .clip(RoundedCornerShape(8.dp))
        )

        Spacer(modifier = Modifier.width(12.dp))

        // Title + Number of streams / play count
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = track.title,
                color = if (isCurrent) AuraNeonLime else Color.White,
                fontWeight = FontWeight.SemiBold,
                fontSize = 14.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = if (playCount.isNotBlank()) playCount else track.artist,
                color = TextSecondary,
                fontSize = 12.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }

        // 3-dot icon stacked vertically
        IconButton(
            onClick = onOptionsClick,
            modifier = Modifier.size(36.dp)
        ) {
            Icon(
                imageVector = Icons.Rounded.MoreVert,
                contentDescription = "More options",
                tint = TextSecondary,
                modifier = Modifier.size(20.dp)
            )
        }
    }
}

@Composable
private fun ArtistMotionPlayer(
    motion: com.auramusic.app.domain.model.ArtistMotion?,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val usePortrait = context.resources.configuration.orientation == android.content.res.Configuration.ORIENTATION_PORTRAIT
    val videoUrl = if (usePortrait) motion?.squareVideoUrl ?: motion?.wideVideoUrl
    else motion?.wideVideoUrl ?: motion?.squareVideoUrl
    val previewUrl = if (usePortrait) motion?.squarePreviewUrl ?: motion?.widePreviewUrl
    else motion?.widePreviewUrl ?: motion?.squarePreviewUrl
    if (videoUrl.isNullOrBlank()) return

    val exoPlayer = androidx.compose.runtime.remember(videoUrl) {
        ExoPlayer.Builder(context).build().apply {
            repeatMode = Player.REPEAT_MODE_ONE
            volume = 0f
            setMediaItem(MediaItem.fromUri(videoUrl))
            prepare()
            playWhenReady = true
        }
    }
    DisposableEffect(exoPlayer, lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_START -> exoPlayer.playWhenReady = true
                Lifecycle.Event.ON_STOP -> exoPlayer.playWhenReady = false
                else -> Unit
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
            exoPlayer.release()
        }
    }

    Box(modifier = modifier) {
        if (!previewUrl.isNullOrBlank()) {
            AsyncImage(
                model = previewUrl,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize()
            )
        }
        AndroidView(
            modifier = Modifier.fillMaxSize(),
            factory = { viewContext ->
                PlayerView(viewContext).apply {
                    useController = false
                    resizeMode = AspectRatioFrameLayout.RESIZE_MODE_ZOOM
                    setShutterBackgroundColor(android.graphics.Color.TRANSPARENT)
                    player = exoPlayer
                }
            },
            update = { it.player = exoPlayer }
        )
    }
}

@Composable
private fun ArtistModalOption(
    icon: ImageVector,
    label: String,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .clickable(onClick = onClick)
            .padding(vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = Color.White.copy(alpha = 0.85f),
            modifier = Modifier.size(22.dp)
        )
        Spacer(modifier = Modifier.width(16.dp))
        Text(
            text = label,
            color = Color.White,
            fontWeight = FontWeight.Medium,
            fontSize = 14.5.sp
        )
    }
}
