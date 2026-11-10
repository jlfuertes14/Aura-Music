package com.auramusic.app.ui.screens

import android.content.Intent
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.BorderStroke
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
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
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
import androidx.compose.material.icons.rounded.Repeat
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material.icons.rounded.Shuffle
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
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
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.auramusic.app.core.media.AuraPlayerManager
import com.auramusic.app.domain.model.AuraTrack
import com.auramusic.app.domain.model.PlaylistDetail
import com.auramusic.app.ui.components.AudioPlayingIndicator
import com.auramusic.app.ui.components.LazyColumnScrollbar
import com.auramusic.app.ui.theme.AuraNeonLime
import com.auramusic.app.ui.theme.SpotifyPitchBlack
import com.auramusic.app.ui.theme.TextSecondary

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PlaylistDetailScreen(
    playlistDetail: PlaylistDetail,
    isLoading: Boolean,
    currentPlayingId: String?,
    isPlaying: Boolean = false,
    initialScrollToTrackId: String? = null,
    onClearScrollTarget: () -> Unit = {},
    isSaved: Boolean = false,
    onClose: () -> Unit,
    onPlayTrack: (AuraTrack, List<AuraTrack>) -> Unit,
    onToggleFavorite: (AuraTrack) -> Unit,
    onToggleSave: () -> Unit = {}
) {
    val context = LocalContext.current
    var selectedModalTrack by remember { mutableStateOf<AuraTrack?>(null) }
    var isRepeatMode by remember { mutableStateOf(false) }
    var isDescriptionExpanded by remember { mutableStateOf(false) }
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val listState = rememberLazyListState()

    val allTracks = remember(playlistDetail.tracks, playlistDetail.title) {
        playlistDetail.tracks.map { it.track.copy(album = playlistDetail.title) }
    }

    val density = LocalDensity.current
    val topBarOffsetPx = with(density) {
        (WindowInsets.statusBars.asPaddingValues().calculateTopPadding() + 52.dp).roundToPx()
    }

    // Auto-scroll to currently playing track when navigated from full-screen now playing
    androidx.compose.runtime.LaunchedEffect(initialScrollToTrackId, playlistDetail.tracks) {
        if (!initialScrollToTrackId.isNullOrBlank() && playlistDetail.tracks.isNotEmpty()) {
            val trackIdx = playlistDetail.tracks.indexOfFirst {
                it.track.videoId == initialScrollToTrackId || it.track.id == initialScrollToTrackId
            }
            if (trackIdx >= 0) {
                val targetIndex = if (playlistDetail.isPodcast) trackIdx + 1 else trackIdx + 2
                // Wait for the playlist screen slide-in transition (280ms) to finish and settle first,
                // then gracefully scroll down to the song location
                kotlinx.coroutines.delay(340)
                listState.animateScrollToItem(index = targetIndex, scrollOffset = -topBarOffsetPx)
                onClearScrollTarget()
            }
        }
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
            state = listState,
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(bottom = 128.dp)
        ) {
            if (playlistDetail.isPodcast) {
                // ==========================================
                // PODCAST SHOW VIEW (Matches SimpMusic & Reference)
                // ==========================================
                item {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(
                                Brush.verticalGradient(
                                    colors = listOf(
                                        Color(0xFF242732),
                                        Color(0xFF161820),
                                        SpotifyPitchBlack
                                    )
                                )
                            )
                    ) {
                        // Top back button
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

                        // Centered High-Res Podcast Cover Image
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(top = 10.dp, bottom = 18.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            AsyncImage(
                                model = playlistDetail.bannerUrl,
                                contentDescription = playlistDetail.title,
                                contentScale = ContentScale.Crop,
                                modifier = Modifier
                                    .size(240.dp)
                                    .clip(RoundedCornerShape(10.dp))
                                    .background(Color(0xFF1E2028))
                            )
                        }

                        // Header Metadata & Controls Section
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 22.dp)
                        ) {
                            // Podcast Title
                            Text(
                                text = playlistDetail.title,
                                color = Color.White,
                                fontWeight = FontWeight.Bold,
                                fontSize = 23.sp,
                                lineHeight = 30.sp,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis
                            )

                            Spacer(modifier = Modifier.height(10.dp))

                            // Author / Host Row (circular author avatar + name)
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                if (playlistDetail.authorAvatarUrl.isNotBlank()) {
                                    AsyncImage(
                                        model = playlistDetail.authorAvatarUrl,
                                        contentDescription = playlistDetail.author,
                                        contentScale = ContentScale.Crop,
                                        modifier = Modifier
                                            .size(24.dp)
                                            .clip(CircleShape)
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                }
                                Text(
                                    text = playlistDetail.author.ifBlank { playlistDetail.title },
                                    color = Color.White,
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.Bold,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }

                            Spacer(modifier = Modifier.height(4.dp))

                            // "Podcasts" label
                            Text(
                                text = "Podcasts",
                                color = TextSecondary,
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Medium
                            )

                            Spacer(modifier = Modifier.height(16.dp))

                            // Action Buttons Row: Play (white circle), Like, Shuffle, Share
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                // Big White Play Button
                                Surface(
                                    shape = CircleShape,
                                    color = Color.White,
                                    modifier = Modifier
                                        .size(46.dp)
                                        .clip(CircleShape)
                                        .clickable {
                                            if (allTracks.isNotEmpty()) {
                                                onPlayTrack(allTracks.first(), allTracks)
                                            }
                                        }
                                ) {
                                    Box(contentAlignment = Alignment.Center) {
                                        Icon(
                                            imageVector = Icons.Rounded.PlayArrow,
                                            contentDescription = "Play All Episodes",
                                            tint = Color.Black,
                                            modifier = Modifier.size(28.dp)
                                        )
                                    }
                                }

                                Spacer(modifier = Modifier.width(10.dp))

                                // Heart / Save
                                IconButton(
                                    onClick = onToggleSave,
                                    modifier = Modifier.size(42.dp)
                                ) {
                                    Icon(
                                        imageVector = if (isSaved) Icons.Rounded.Favorite else Icons.Rounded.FavoriteBorder,
                                        contentDescription = if (isSaved) "Saved" else "Save",
                                        tint = if (isSaved) AuraNeonLime else Color.White,
                                        modifier = Modifier.size(24.dp)
                                    )
                                }

                                Spacer(modifier = Modifier.weight(1f))

                                // Shuffle
                                IconButton(
                                    onClick = {
                                        if (allTracks.isNotEmpty()) {
                                            val shuffled = allTracks.shuffled()
                                            onPlayTrack(shuffled.first(), shuffled)
                                        }
                                    },
                                    modifier = Modifier.size(42.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Rounded.Shuffle,
                                        contentDescription = "Shuffle Episodes",
                                        tint = Color.White,
                                        modifier = Modifier.size(24.dp)
                                    )
                                }

                                Spacer(modifier = Modifier.width(4.dp))

                                // Share
                                IconButton(
                                    onClick = {
                                        val shareIntent = Intent(Intent.ACTION_SEND).apply {
                                            type = "text/plain"
                                            putExtra(
                                                Intent.EXTRA_TEXT,
                                                "Listen to ${playlistDetail.title} on YouTube Music: https://music.youtube.com/playlist?list=${playlistDetail.id}"
                                            )
                                        }
                                        context.startActivity(Intent.createChooser(shareIntent, "Share Podcast"))
                                    },
                                    modifier = Modifier.size(42.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Rounded.Share,
                                        contentDescription = "Share",
                                        tint = Color.White,
                                        modifier = Modifier.size(22.dp)
                                    )
                                }
                            }

                            // Expandable Show Description
                            if (playlistDetail.description.isNotBlank()) {
                                Spacer(modifier = Modifier.height(14.dp))
                                Text(
                                    text = playlistDetail.description,
                                    color = Color.White.copy(alpha = 0.82f),
                                    fontSize = 13.sp,
                                    lineHeight = 18.5.sp,
                                    maxLines = if (isDescriptionExpanded) Int.MAX_VALUE else 3,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    text = if (isDescriptionExpanded) "Less" else "More",
                                    color = Color.White,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 13.5.sp,
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(4.dp))
                                        .clickable { isDescriptionExpanded = !isDescriptionExpanded }
                                        .padding(vertical = 2.dp)
                                )
                            }

                            Spacer(modifier = Modifier.height(12.dp))

                            // Total Tracks / Episodes Count
                            Text(
                                text = "${playlistDetail.tracks.size} tracks",
                                color = Color.White.copy(alpha = 0.85f),
                                fontSize = 13.5.sp,
                                fontWeight = FontWeight.SemiBold,
                                modifier = Modifier.padding(bottom = 8.dp)
                            )
                        }
                    }
                }

                // Loading Skeleton for Podcasts
                if (isLoading && playlistDetail.tracks.isEmpty()) {
                    items(6) {
                        com.auramusic.app.ui.components.TrackItemSkeleton()
                    }
                }

                // Podcast Episodes List
                items(playlistDetail.tracks, key = { "pod_ep_${it.rank}_${it.track.videoId}" }) { item ->
                    val isCurrent = item.track.videoId == currentPlayingId || (currentPlayingId != null && item.track.id == currentPlayingId)
                    val isTrackPlaying = isCurrent && isPlaying

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onPlayTrack(item.track.copy(album = playlistDetail.title), allTracks) }
                            .padding(horizontal = 20.dp, vertical = 9.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // Video / Episode Thumbnail with Beat Indicator Overlay
                        Box(
                            modifier = Modifier
                                .size(width = 66.dp, height = 44.dp)
                                .clip(RoundedCornerShape(6.dp))
                                .background(Color(0xFF1E2028)),
                            contentAlignment = Alignment.Center
                        ) {
                            AsyncImage(
                                model = item.track.artworkUrl.ifBlank { item.track.highResArtworkUrl },
                                contentDescription = item.track.title,
                                contentScale = ContentScale.Crop,
                                modifier = Modifier.fillMaxSize()
                            )
                            if (isTrackPlaying) {
                                Box(
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .background(Color.Black.copy(alpha = 0.55f)),
                                    contentAlignment = Alignment.Center
                                ) {
                                    AudioPlayingIndicator(
                                        modifier = Modifier.fillMaxSize(0.65f),
                                        color = AuraNeonLime
                                    )
                                }
                            }
                        }

                        Spacer(modifier = Modifier.width(14.dp))

                        // Episode Title, Subtitle (Views • Duration), and Description Preview
                        Column(
                            modifier = Modifier.weight(1f)
                        ) {
                            Text(
                                text = item.track.title,
                                color = if (isCurrent) AuraNeonLime else Color.White,
                                fontWeight = FontWeight.Bold,
                                fontSize = 13.5.sp,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = item.viewCount,
                                color = TextSecondary,
                                fontSize = 11.5.sp,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            if (item.description.isNotBlank()) {
                                Spacer(modifier = Modifier.height(2.dp))
                                Text(
                                    text = item.description,
                                    color = TextSecondary.copy(alpha = 0.70f),
                                    fontSize = 11.sp,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }

                        // 3-dot options
                        IconButton(
                            onClick = { selectedModalTrack = item.track },
                            modifier = Modifier.size(36.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.MoreVert,
                                contentDescription = "Episode options",
                                tint = Color.White.copy(alpha = 0.70f),
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    }
                }

            } else {
                // ==========================================
                // STANDARD PLAYLIST / ALBUM VIEW
                // ==========================================
                // 1. Top Screen Banner Picture with Title & Views / Author
                item {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(290.dp)
                    ) {
                        AsyncImage(
                            model = playlistDetail.bannerUrl,
                            contentDescription = playlistDetail.title,
                            contentScale = ContentScale.Crop,
                            modifier = Modifier.fillMaxSize()
                        )

                        // Gradient overlay to darken bottom so text is crisp and readable
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .background(
                                    Brush.verticalGradient(
                                        colors = listOf(
                                            Color.Black.copy(alpha = 0.35f),
                                            Color.Transparent,
                                            Color.Black.copy(alpha = 0.70f),
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

                        // Playlist title on bottom right & views/author directly below it
                        Column(
                            modifier = Modifier
                                .align(Alignment.BottomEnd)
                                .padding(end = 20.dp, bottom = 12.dp)
                                .fillMaxWidth(0.85f),
                            horizontalAlignment = Alignment.End
                        ) {
                            Text(
                                text = playlistDetail.title,
                                color = Color.White,
                                fontWeight = FontWeight.ExtraBold,
                                fontSize = 24.sp,
                                textAlign = TextAlign.End,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = playlistDetail.author,
                                color = TextSecondary,
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Medium,
                                textAlign = TextAlign.End,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            if (playlistDetail.totalViews.isNotBlank()) {
                                Spacer(modifier = Modifier.height(3.dp))
                                Text(
                                    text = playlistDetail.totalViews,
                                    color = AuraNeonLime,
                                    fontSize = 12.5.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    textAlign = TextAlign.End,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }
                    }
                }

                // 2. Section Header: Songs count, Total Views & Quick Play All + Shuffle
                item {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 20.dp, vertical = 14.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text(
                                text = "Tracks",
                                color = Color.White,
                                fontWeight = FontWeight.Bold,
                                fontSize = 18.sp
                            )
                            val subtitleText = buildString {
                                if (playlistDetail.tracks.isNotEmpty()) {
                                    append("${playlistDetail.tracks.size} tracks")
                                } else {
                                    append(playlistDetail.trackCount)
                                }
                                if (playlistDetail.totalViews.isNotBlank()) {
                                    append(" • ${playlistDetail.totalViews}")
                                }
                            }
                            Text(
                                text = subtitleText,
                                color = TextSecondary,
                                fontSize = 12.sp
                            )
                        }

                        Row(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            // Like / Save Playlist Button
                            IconButton(
                                onClick = onToggleSave,
                                modifier = Modifier.size(42.dp)
                            ) {
                                Icon(
                                    imageVector = if (isSaved) Icons.Rounded.Favorite else Icons.Rounded.FavoriteBorder,
                                    contentDescription = if (isSaved) "Remove from Library" else "Save to Library",
                                    tint = if (isSaved) AuraNeonLime else Color.White,
                                    modifier = Modifier.size(24.dp)
                                )
                            }

                            if (allTracks.isNotEmpty()) {
                                // Shuffle / Repeat All Button
                                IconButton(
                                    onClick = {
                                        if (!isRepeatMode) {
                                            val shuffled = allTracks.shuffled()
                                            onPlayTrack(shuffled.first(), shuffled)
                                            isRepeatMode = true
                                        } else {
                                            onPlayTrack(allTracks.first(), allTracks)
                                            isRepeatMode = false
                                        }
                                    },
                                    modifier = Modifier.size(42.dp)
                                ) {
                                    Icon(
                                        imageVector = if (isRepeatMode) Icons.Rounded.Repeat else Icons.Rounded.Shuffle,
                                        contentDescription = if (isRepeatMode) "Repeat All" else "Shuffle Playlist",
                                        tint = if (isRepeatMode) AuraNeonLime else Color.White,
                                        modifier = Modifier.size(24.dp)
                                    )
                                }

                                Spacer(modifier = Modifier.width(4.dp))

                                // Play All Button
                                Surface(
                                    shape = CircleShape,
                                    color = AuraNeonLime,
                                    modifier = Modifier
                                        .size(44.dp)
                                        .clip(CircleShape)
                                        .clickable { onPlayTrack(allTracks.first(), allTracks) }
                                ) {
                                    Box(contentAlignment = Alignment.Center) {
                                        Icon(
                                            imageVector = Icons.Rounded.PlayArrow,
                                            contentDescription = "Play Playlist",
                                            tint = Color.Black,
                                            modifier = Modifier.size(26.dp)
                                        )
                                    }
                                }
                            }
                        }
                    }
                }

                // 3. Loading indicator / Skeleton Loading
                if (isLoading && playlistDetail.tracks.isEmpty()) {
                    items(6) {
                        com.auramusic.app.ui.components.TrackItemSkeleton()
                    }
                }

                // 4. Ranked Song List (1..N) with Album Art, Title, Views/Duration, and 3-Dot options (All tracks without artificial cap)
                items(playlistDetail.tracks, key = { "pl_song_${it.rank}_${it.track.videoId}" }) { item ->
                    val isCurrent = item.track.videoId == currentPlayingId || (currentPlayingId != null && item.track.id == currentPlayingId)
                    val isTrackPlaying = isCurrent && isPlaying

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onPlayTrack(item.track.copy(album = playlistDetail.title), allTracks) }
                            .padding(horizontal = 20.dp, vertical = 9.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // Ranking number 1, 2, 3...
                        Text(
                            text = "${item.rank}",
                            color = if (isCurrent) AuraNeonLime else TextSecondary,
                            fontSize = 14.sp,
                            fontWeight = if (isCurrent) FontWeight.Bold else FontWeight.Medium,
                            modifier = Modifier.width(28.dp),
                            textAlign = TextAlign.Start
                        )

                        // Album Art with Beat Indicator Overlay
                        Box(
                            modifier = Modifier
                                .size(46.dp)
                                .clip(RoundedCornerShape(8.dp)),
                            contentAlignment = Alignment.Center
                        ) {
                            AsyncImage(
                                model = item.track.artworkUrl.ifBlank { item.track.highResArtworkUrl },
                                contentDescription = item.track.title,
                                contentScale = ContentScale.Crop,
                                modifier = Modifier.fillMaxSize()
                            )
                            if (isTrackPlaying) {
                                Box(
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .background(Color.Black.copy(alpha = 0.55f)),
                                    contentAlignment = Alignment.Center
                                ) {
                                    AudioPlayingIndicator(
                                        modifier = Modifier.fillMaxSize(0.65f),
                                        color = AuraNeonLime
                                    )
                                }
                            }
                        }

                        Spacer(modifier = Modifier.width(12.dp))

                        // Title & Artist + Views/Duration
                        Column(
                            modifier = Modifier.weight(1f)
                        ) {
                            Text(
                                text = item.track.title,
                                color = if (isCurrent) AuraNeonLime else Color.White,
                                fontWeight = FontWeight.Bold,
                                fontSize = 13.5.sp,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Spacer(modifier = Modifier.height(2.dp))
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    text = item.track.artist,
                                    color = TextSecondary,
                                    fontSize = 11.5.sp,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    modifier = Modifier.weight(1f, fill = false)
                                )
                                if (item.viewCount.isNotBlank()) {
                                    val isMetric = item.viewCount.contains("play", ignoreCase = true) || item.viewCount.contains("view", ignoreCase = true)
                                    Text(
                                        text = " • ${item.viewCount}",
                                        color = if (isMetric) AuraNeonLime.copy(alpha = 0.90f) else TextSecondary.copy(alpha = 0.75f),
                                        fontSize = 11.sp,
                                        fontWeight = if (isMetric) FontWeight.SemiBold else FontWeight.Normal,
                                        maxLines = 1
                                    )
                                }
                            }
                        }

                        // 3-dot stacked vertical icon opening modal options
                        IconButton(
                            onClick = { selectedModalTrack = item.track },
                            modifier = Modifier.size(36.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.MoreVert,
                                contentDescription = "Song options",
                                tint = Color.White.copy(alpha = 0.70f),
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    }
                }
            }

            // Bottom blank space equivalent to ~2 song tiles so mini player never covers the last item
            item(key = "podcast_bottom_blank_space") {
                Spacer(modifier = Modifier.height(16.dp))
            }
        }

        // Draggable Fast Scrollbar on screen edge for effortless navigation across 100+ items
        LazyColumnScrollbar(
            state = listState,
            modifier = Modifier.align(Alignment.CenterEnd),
            onlyWhenTrackScrolling = playlistDetail.isPodcast
        )

        // Pinned floating back button so it's always accessible when scrolled down into tracks
        AnimatedVisibility(
            visible = listState.firstVisibleItemIndex > 0,
            enter = fadeIn(tween(200)),
            exit = fadeOut(tween(200)),
            modifier = Modifier
                .align(Alignment.TopStart)
                .statusBarsPadding()
                .padding(start = 12.dp, top = 6.dp)
        ) {
            IconButton(
                onClick = onClose,
                modifier = Modifier
                    .size(40.dp)
                    .background(Color.Black.copy(alpha = 0.65f), CircleShape)
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Rounded.ArrowBack,
                    contentDescription = "Back",
                    tint = Color.White,
                    modifier = Modifier.size(22.dp)
                )
            }
        }

        // 5. Options Modal Bottom Sheet
        selectedModalTrack?.let { track ->
            ModalBottomSheet(
                onDismissRequest = { selectedModalTrack = null },
                sheetState = sheetState,
                containerColor = Color(0xFF14161E),
                scrimColor = Color.Black.copy(alpha = 0.75f)
            ) {
                com.auramusic.app.ui.components.ForceWhiteSystemBarsEffect()
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 22.dp, vertical = 8.dp)
                        .padding(bottom = 36.dp)
                ) {
                    // Modal Header with Track Artwork & Title
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp)
                    ) {
                        AsyncImage(
                            model = track.highResArtworkUrl,
                            contentDescription = track.title,
                            contentScale = ContentScale.Crop,
                            modifier = Modifier
                                .size(52.dp)
                                .clip(RoundedCornerShape(8.dp))
                        )
                        Spacer(modifier = Modifier.width(14.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = track.title,
                                color = Color.White,
                                fontWeight = FontWeight.Bold,
                                fontSize = 15.sp,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Text(
                                text = track.artist,
                                color = TextSecondary,
                                fontSize = 12.5.sp,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }

                    HorizontalDivider(
                        color = Color.White.copy(alpha = 0.08f),
                        thickness = 0.5.dp,
                        modifier = Modifier.padding(bottom = 12.dp)
                    )

                    // Option 1: Play Next
                    PlaylistModalOptionItem(
                        icon = Icons.AutoMirrored.Rounded.QueueMusic,
                        label = "Play Next",
                        onClick = {
                            AuraPlayerManager.playNext(track)
                            Toast.makeText(context, "Playing next: ${track.title}", Toast.LENGTH_SHORT).show()
                            selectedModalTrack = null
                        }
                    )

                    // Option 2: Add to Queue
                    PlaylistModalOptionItem(
                        icon = Icons.Rounded.PlaylistAdd,
                        label = "Add to Queue",
                        onClick = {
                            AuraPlayerManager.enqueueTrack(track)
                            Toast.makeText(context, "Added to queue", Toast.LENGTH_SHORT).show()
                            selectedModalTrack = null
                        }
                    )

                    // Option 3: Add to Liked Songs
                    PlaylistModalOptionItem(
                        icon = Icons.Rounded.Favorite,
                        label = "Liked Songs",
                        onClick = {
                            onToggleFavorite(track)
                            Toast.makeText(context, "Saved to Liked Songs", Toast.LENGTH_SHORT).show()
                            selectedModalTrack = null
                        }
                    )

                    // Option 4: Add to Playlist
                    PlaylistModalOptionItem(
                        icon = Icons.Rounded.LibraryAdd,
                        label = "Add to Playlist",
                        onClick = {
                            Toast.makeText(context, "Saved to library", Toast.LENGTH_SHORT).show()
                            selectedModalTrack = null
                        }
                    )
                }
            }
        }
    }
}

@Composable
private fun PlaylistModalOptionItem(
    icon: ImageVector,
    label: String,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .clickable(onClick = onClick)
            .padding(vertical = 12.dp, horizontal = 4.dp),
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
