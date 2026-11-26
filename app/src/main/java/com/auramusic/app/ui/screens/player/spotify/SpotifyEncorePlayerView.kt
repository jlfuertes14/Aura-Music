package com.auramusic.app.ui.screens.player.spotify

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandHorizontally
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkHorizontally
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.MarqueeAnimationMode
import androidx.compose.foundation.background
import androidx.compose.foundation.basicMarquee
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.Comment
import androidx.compose.material.icons.automirrored.rounded.QueueMusic
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Bedtime
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.FavoriteBorder
import androidx.compose.material.icons.rounded.Headphones
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.rounded.OpenInFull
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Remove
import androidx.compose.material.icons.rounded.Repeat
import androidx.compose.material.icons.rounded.RepeatOne
import androidx.compose.material.icons.rounded.RestartAlt
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material.icons.rounded.Shuffle
import androidx.compose.material.icons.rounded.SkipNext
import androidx.compose.material.icons.rounded.SkipPrevious
import androidx.compose.material.icons.rounded.Speed
import androidx.compose.material.icons.rounded.Timer
import androidx.compose.material.icons.rounded.Translate
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import coil.compose.AsyncImage
import com.auramusic.app.core.lyrics.SimpMusicLyricsRepository
import com.auramusic.app.core.media.AuraPlayerManager
import com.auramusic.app.domain.model.ArtistDetail
import com.auramusic.app.domain.model.AuraPalette
import com.auramusic.app.domain.model.AuraTrack
import com.auramusic.app.domain.model.AuraVideoInfo
import com.auramusic.app.domain.model.LyricsData
import com.auramusic.app.domain.model.PlaybackMode
import com.auramusic.app.ui.screens.formatDuration
import com.auramusic.app.ui.theme.AuraPlayGreen
import com.auramusic.app.ui.theme.AuraPoppinsFontFamily

/**
 * 2. Authentic Spotify Full Screen Music Player View
 * Signature Spotify layout:
 * - Dynamic artwork gradient transitioning into deep obsidian charcoal
 * - Top Bar: Chevron collapse, PLAYING FROM PLAYLIST, More options
 * - Full-width square album artwork with subtle rounded corners
 * - Left-aligned Title (bold 22sp) and Artist (15sp) with Heart on the right
 * - Slim timeline scrubber with dead-center circular point thumb
 * - Signature playback controls: Shuffle cycle, Skip Previous, large white Play/Pause FAB, Skip Next, Queue
 * - Connected Audio / Fidelity indicator
 * - Synced lyrics card preview with expand button directly below controls
 */
@OptIn(ExperimentalFoundationApi::class, ExperimentalLayoutApi::class)
@Composable
internal fun SpotifyEncorePlayerView(
    track: AuraTrack,
    queue: List<AuraTrack> = emptyList(),
    isPlaying: Boolean,
    isBuffering: Boolean,
    positionSec: Float,
    durationSec: Float,
    bufferedSec: Float = 0f,
    palette: AuraPalette,
    lyrics: LyricsData?,
    lyricsOffsetMs: Int,
    showRomanized: Boolean = false,
    isFavorite: Boolean,
    isShuffleEnabled: Boolean,
    artistDetail: ArtistDetail? = null,
    videoInfo: AuraVideoInfo? = null,
    isArtistDetailLoading: Boolean = false,
    lyricsExpansionProgress: Float = 0f,
    hideArtworkForLyricsTransition: Boolean = false,
    isVideoMode: Boolean = false,
    isVideoLoading: Boolean = false,
    onCollapse: () -> Unit,
    onTogglePlayPause: () -> Unit,
    onSeekTo: (Float) -> Unit,
    onSkipNext: () -> Unit,
    onSkipPrevious: () -> Unit,
    onToggleFavorite: () -> Unit,
    onToggleShuffle: () -> Unit,
    onToggleVideoMode: () -> Unit = {},
    onOpenArtist: () -> Unit,
    onOpenCurrentPlaylist: () -> Unit = {},
    onOptionsClick: () -> Unit,
    sleepTimerRemainingMin: Int? = null,
    sleepTimerEndOfTrack: Boolean = false,
    onOpenSleepTimer: () -> Unit = {},
    playbackSpeed: Float = 1.0f,
    playbackPitchSemitones: Int = 0,
    translatedLyricsMap: Map<Int, String> = emptyMap(),
    isTranslatingLyrics: Boolean = false,
    onOpenSpeedPitch: () -> Unit = {},
    onOpenTrackInfo: () -> Unit = {},
    onOpenOutputRouter: () -> Unit = {},
    onTranslateLyrics: () -> Unit = {},
    onShareLyricsCard: () -> Unit = {},
    onOpenLyrics: () -> Unit,
    onAdjustLyricsOffset: (Int) -> Unit = {},
    onOpenComments: () -> Unit,
    onOpenQueue: () -> Unit,
    onCardPositioned: (Rect) -> Unit = {},
    onArtworkPositioned: (Rect) -> Unit = {}
) {
    val animatedTopColor by animateColorAsState(
        targetValue = palette.backgroundTop.copy(alpha = 0.65f),
        animationSpec = tween(500),
        label = "bgTop"
    )

    val scrollState = rememberScrollState()
    var playerViewportCenterPx by remember(track.videoId) { mutableFloatStateOf(Float.NaN) }
    var showArtistPlaybackHeader by remember(track.videoId) { mutableStateOf(false) }
    var showLyricsOffsetControl by remember(track.videoId) { mutableStateOf(false) }
    val density = LocalDensity.current
    val headerHysteresisPx = with(density) { 48.dp.toPx() }
    val miniPlayerProgressColor = lerp(palette.primary, Color.White, 0.65f)
    val artistHeaderReveal by animateFloatAsState(
        targetValue = if (showArtistPlaybackHeader) 1f else 0f,
        animationSpec = tween(260, easing = CubicBezierEasing(0.2f, 0f, 0.2f, 1f)),
        label = "artistCurtainHeader"
    )
    val artistHeaderColor = lerp(animatedTopColor, Color.Black, 0.78f).copy(alpha = 1f)

    // Authentic Spotify dynamic gradient background (ambient artwork glow into pure deep obsidian black)
    Box(
        modifier = Modifier
            .fillMaxSize()
            .onGloballyPositioned { playerViewportCenterPx = it.boundsInRoot().center.y }
            .background(artistHeaderColor)
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer { alpha = 1f - artistHeaderReveal }
                .background(
                    Brush.verticalGradient(
                        colors = listOf(
                            animatedTopColor,
                            Color(0xFF181818),
                            Color(0xFF121212)
                        )
                    )
                )
        )
        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding(),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // 1. Pinned Top Bar: Chevron Collapse, "PLAYING FROM PLAYLIST", More Options
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(72.dp)
                    .zIndex(3f),
                contentAlignment = Alignment.TopCenter
            ) {
                if (artistHeaderReveal < 1f) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 20.dp, vertical = 6.dp)
                            .graphicsLayer {
                                alpha = 1f - artistHeaderReveal
                                translationY = -size.height * artistHeaderReveal
                            },
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        IconButton(onClick = onCollapse, modifier = Modifier.size(38.dp)) {
                            Icon(
                                imageVector = Icons.Rounded.KeyboardArrowDown,
                                contentDescription = "Collapse Player",
                                tint = Color.White,
                                modifier = Modifier.size(32.dp)
                            )
                        }
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            modifier = Modifier
                                .weight(1f)
                                .padding(horizontal = 8.dp)
                                .clip(RoundedCornerShape(8.dp))
                                .clickable(onClick = onOpenCurrentPlaylist)
                        ) {
                            val cleanAlbum = track.album.trim()
                            val isValidSource = cleanAlbum.isNotBlank() &&
                                cleanAlbum != track.title &&
                                !cleanAlbum.matches(Regex("^\\d+:\\d{2}(:\\d{2})?$")) &&
                                !cleanAlbum.endsWith("views", ignoreCase = true)
                            val playingFrom = if (isValidSource) {
                                "PLAYING FROM ${cleanAlbum.uppercase()}"
                            } else {
                                "PLAYING FROM PLAYLIST"
                            }
                            Text(
                                text = playingFrom,
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color.White.copy(alpha = 0.7f),
                                letterSpacing = 0.8.sp,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Spacer(modifier = Modifier.height(1.dp))
                            Text(
                                text = track.title,
                                style = MaterialTheme.typography.titleSmall,
                                color = Color.White,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                        IconButton(onClick = onOptionsClick, modifier = Modifier.size(38.dp)) {
                            Icon(
                                imageVector = Icons.Rounded.MoreVert,
                                contentDescription = "More Options",
                                tint = Color.White,
                                modifier = Modifier.size(24.dp)
                            )
                        }
                    }
                }
                if (artistHeaderReveal > 0f) {
                    ArtistSectionPlaybackHeader(
                        modifier = Modifier
                            .align(Alignment.Center)
                            .graphicsLayer {
                                alpha = artistHeaderReveal
                                translationY = -size.height * (1f - artistHeaderReveal)
                            },
                        track = track,
                        isPlaying = isPlaying,
                        isBuffering = isBuffering,
                        isFavorite = isFavorite,
                        positionSec = positionSec,
                        durationSec = durationSec,
                        backgroundColor = artistHeaderColor,
                        progressColor = miniPlayerProgressColor,
                        progressTrackColor = palette.primary.copy(alpha = 0.35f),
                        onTogglePlayPause = onTogglePlayPause,
                        onToggleFavorite = onToggleFavorite
                    )
                }
            }

            // Scrollable Content Column: Artwork / Video, Metadata, Scrubber, Transport, Lyrics Card
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .verticalScroll(scrollState),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                if (isVideoMode) {
                    // Video Player maximized edge-to-edge across the sides (no horizontal padding!)
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .aspectRatio(16f / 9f)
                            .background(Color.Black)
                            .onGloballyPositioned { onArtworkPositioned(it.boundsInRoot()) },
                        contentAlignment = Alignment.Center
                    ) {
                        SpotifyVideoPlayerSurface(
                            modifier = Modifier.fillMaxSize(),
                            track = track,
                            isLoading = isVideoLoading,
                            onSwitchToAudio = onToggleVideoMode
                        )
                    }
                } else {
                    Spacer(modifier = Modifier.height(8.dp))

                    // Large square draggable artwork with Spotify Encore corner radius.
                    AuraDraggableCoverArt(
                        currentTrack = track,
                        queue = queue,
                        cornerRadius = 8.dp,
                        hideArtworkForLyricsTransition = hideArtworkForLyricsTransition,
                        onSkipNext = onSkipNext,
                        onSkipPrevious = onSkipPrevious,
                        onArtworkPositioned = onArtworkPositioned,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 24.dp)
                            .aspectRatio(1f)
                    )
                }

                // Controls, Metadata, and Lyrics Container with standard 24dp horizontal margins
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Spacer(modifier = Modifier.height(if (isVideoMode) 16.dp else 24.dp))

                    // 3. Track Title, Artist & Favorite Heart Button Row
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = track.title,
                                style = MaterialTheme.typography.titleMedium,
                                color = Color.White,
                                maxLines = 1,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .basicMarquee(
                                        iterations = Int.MAX_VALUE,
                                        animationMode = MarqueeAnimationMode.Immediately
                                    )
                                    .focusable()
                                    .clip(RoundedCornerShape(4.dp))
                                    .clickable(onClick = onOpenCurrentPlaylist)
                            )
                            Spacer(modifier = Modifier.height(3.dp))
                            Text(
                                text = track.artist,
                                style = MaterialTheme.typography.bodyMedium,
                                color = Color.White.copy(alpha = 0.7f),
                                maxLines = 1,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .basicMarquee(
                                        iterations = Int.MAX_VALUE,
                                        animationMode = MarqueeAnimationMode.Immediately
                                    )
                                    .focusable()
                                    .clip(RoundedCornerShape(4.dp))
                                    .clickable(onClick = onOpenArtist)
                            )
                        }

                        IconButton(
                            onClick = onToggleFavorite,
                            modifier = Modifier.size(44.dp)
                        ) {
                            Icon(
                                imageVector = if (isFavorite) Icons.Rounded.Favorite else Icons.Rounded.FavoriteBorder,
                                contentDescription = "Favorite",
                                tint = if (isFavorite) AuraPlayGreen else Color.White,
                                modifier = Modifier.size(26.dp)
                            )
                        }
                        IconButton(onClick = onOpenComments, modifier = Modifier.size(42.dp)) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Rounded.Comment,
                                contentDescription = "Comments",
                                tint = Color.White.copy(alpha = 0.76f),
                                modifier = Modifier.size(22.dp)
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(14.dp))

                    // 4. Slim Timeline Scrubber & Timestamps
                    Column(modifier = Modifier.fillMaxWidth()) {
                        CompactPointSlider(
                            value = positionSec.coerceIn(0f, durationSec.coerceAtLeast(1f)),
                            bufferedValue = bufferedSec.coerceIn(0f, durationSec.coerceAtLeast(1f)),
                            onValueChange = onSeekTo,
                            valueRange = 0f..durationSec.coerceAtLeast(1f),
                            pointColor = Color.White,
                            activeTrackColor = Color.White,
                            inactiveTrackColor = Color.White.copy(alpha = 0.22f),
                            trackHeight = 2.5.dp,
                            thumbRadius = 5.dp,
                            modifier = Modifier.fillMaxWidth()
                        )

                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 4.dp),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                text = formatDuration(positionSec.toInt()),
                                fontFamily = AuraPoppinsFontFamily,
                                fontSize = 11.5.sp,
                                color = Color.White.copy(alpha = 0.65f)
                            )
                            Text(
                                text = formatDuration(durationSec.toInt()),
                                fontFamily = AuraPoppinsFontFamily,
                                fontSize = 11.5.sp,
                                color = Color.White.copy(alpha = 0.65f)
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    // 5. Primary Spotify Transport Controls Row
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 4.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        val playbackMode by AuraPlayerManager.playbackMode.collectAsState()
                        IconButton(
                            onClick = onToggleShuffle,
                            modifier = Modifier.size(44.dp)
                        ) {
                            val (icon, tint) = when (playbackMode) {
                                PlaybackMode.NORMAL -> Icons.Rounded.Shuffle to Color.White.copy(alpha = 0.6f)
                                PlaybackMode.SHUFFLE -> Icons.Rounded.Shuffle to AuraPlayGreen
                                PlaybackMode.REPEAT_ALL -> Icons.Rounded.Repeat to AuraPlayGreen
                                PlaybackMode.REPEAT_ONE -> Icons.Rounded.RepeatOne to AuraPlayGreen
                            }
                            Icon(
                                imageVector = icon,
                                contentDescription = playbackMode.label,
                                tint = tint,
                                modifier = Modifier.size(24.dp)
                            )
                        }

                        IconButton(
                            onClick = onSkipPrevious,
                            modifier = Modifier.size(48.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.SkipPrevious,
                                contentDescription = "Previous Track",
                                tint = Color.White,
                                modifier = Modifier.size(36.dp)
                            )
                        }

                        // Large Solid White Circular Play/Pause Button (Signature Spotify styling)
                        Box(
                            modifier = Modifier
                                .size(64.dp)
                                .clip(CircleShape)
                                .background(Color.White)
                                .clickable(onClick = onTogglePlayPause),
                            contentAlignment = Alignment.Center
                        ) {
                            if (isBuffering && !isPlaying) {
                                CircularProgressIndicator(
                                    color = Color.Black,
                                    strokeWidth = 3.dp,
                                    modifier = Modifier.size(28.dp)
                                )
                            } else {
                                Icon(
                                    imageVector = if (isPlaying) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
                                    contentDescription = if (isPlaying) "Pause" else "Play",
                                    tint = Color.Black,
                                    modifier = Modifier.size(36.dp)
                                )
                            }
                        }

                        IconButton(
                            onClick = onSkipNext,
                            modifier = Modifier.size(48.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.SkipNext,
                                contentDescription = "Next Track",
                                tint = Color.White,
                                modifier = Modifier.size(36.dp)
                            )
                        }

                        IconButton(
                            onClick = onOpenQueue,
                            modifier = Modifier.size(44.dp)
                        ) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Rounded.QueueMusic,
                                contentDescription = "Upcoming Queue",
                                tint = Color.White.copy(alpha = 0.75f),
                                modifier = Modifier.size(24.dp)
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    // 5b. Tier 2 Utility Bar: Audio Output, Speed/Pitch, Track Stats
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        val isSpeedOrPitchModified = playbackSpeed != 1.0f || playbackPitchSemitones != 0
                        val speedLabel = buildString {
                            append(String.format(java.util.Locale.US, "%.2gx", playbackSpeed))
                            if (playbackPitchSemitones != 0) {
                                append(" · ")
                                append(if (playbackPitchSemitones > 0) "+$playbackPitchSemitones" else "$playbackPitchSemitones")
                                append("st")
                            }
                        }

                        Row(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(50))
                                    .background(Color.White.copy(alpha = 0.08f))
                                    .clickable(onClick = onOpenOutputRouter)
                                    .padding(horizontal = 11.dp, vertical = 6.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(5.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.Headphones,
                                    contentDescription = "Audio Output Device",
                                    tint = Color.White.copy(alpha = 0.85f),
                                    modifier = Modifier.size(15.dp)
                                )
                                Text(
                                    text = "Output",
                                    color = Color.White.copy(alpha = 0.85f),
                                    fontSize = 11.5.sp,
                                    fontWeight = FontWeight.Medium
                                )
                            }

                            Row(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(50))
                                    .background(
                                        if (isSpeedOrPitchModified) Color.White.copy(alpha = 0.16f)
                                        else Color.White.copy(alpha = 0.08f)
                                    )
                                    .clickable(onClick = onOpenSpeedPitch)
                                    .padding(horizontal = 11.dp, vertical = 6.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(5.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.Speed,
                                    contentDescription = "Playback Speed & Pitch",
                                    tint = if (isSpeedOrPitchModified) Color.White else Color.White.copy(alpha = 0.85f),
                                    modifier = Modifier.size(15.dp)
                                )
                                Text(
                                    text = if (isSpeedOrPitchModified) speedLabel else "1x",
                                    color = if (isSpeedOrPitchModified) Color.White else Color.White.copy(alpha = 0.85f),
                                    fontSize = 11.5.sp,
                                    fontWeight = if (isSpeedOrPitchModified) FontWeight.Bold else FontWeight.Medium
                                )
                            }
                        }

                        val isSleepTimerActive = (sleepTimerRemainingMin != null && sleepTimerRemainingMin > 0) || sleepTimerEndOfTrack
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(50))
                                    .then(
                                        if (isSleepTimerActive) {
                                            Modifier.background(Color.White.copy(alpha = 0.16f))
                                        } else {
                                            Modifier
                                        }
                                    )
                                    .clickable(onClick = onOpenSleepTimer)
                                    .padding(horizontal = if (isSleepTimerActive) 9.dp else 8.dp, vertical = 7.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.Bedtime,
                                    contentDescription = "Sleep Timer",
                                    tint = if (isSleepTimerActive) Color.White else Color.White.copy(alpha = 0.78f),
                                    modifier = Modifier.size(18.dp)
                                )
                                if (isSleepTimerActive) {
                                    val badgeLabel = when {
                                        sleepTimerRemainingMin != null && sleepTimerRemainingMin > 0 -> "${sleepTimerRemainingMin}m"
                                        else -> "Track"
                                    }
                                    Text(
                                        text = badgeLabel,
                                        color = Color.White,
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            }

                            IconButton(
                                onClick = onOpenTrackInfo,
                                modifier = Modifier.size(34.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.Info,
                                    contentDescription = "Song Info",
                                    tint = Color.White.copy(alpha = 0.78f),
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(14.dp))

                    // Signature Spotify Synced Lyrics Card (Automatically uses scanned cover art color, no borders, covers 5 lines)
                    val coverColor = palette.primary

                    Surface(
                        shape = RoundedCornerShape(20.dp),
                        color = coverColor,
                        shadowElevation = 6.dp,
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = 340.dp)
                            .clip(RoundedCornerShape(20.dp))
                            .onGloballyPositioned { coordinates ->
                                val lyricsBounds = coordinates.boundsInRoot()
                                onCardPositioned(lyricsBounds)
                                val viewportCenter = playerViewportCenterPx
                                if (viewportCenter.isFinite() && scrollState.value > 0) {
                                    val lyricsCenter = lyricsBounds.center.y
                                    when {
                                        !showArtistPlaybackHeader && lyricsCenter < viewportCenter -> {
                                            showArtistPlaybackHeader = true
                                        }
                                        showArtistPlaybackHeader && lyricsCenter > viewportCenter + headerHysteresisPx -> {
                                            showArtistPlaybackHeader = false
                                        }
                                    }
                                }
                            }
                            .graphicsLayer {
                                // When lyrics is expanding or in flight, smoothly hide static card underneath
                                // so the flying morph surface appears as the single continuous shared element
                                alpha = if (lyricsExpansionProgress > 0.05f) 0f else (1f - (lyricsExpansionProgress / 0.05f)).coerceIn(0f, 1f)
                            }
                            .clickable(onClick = onOpenLyrics)
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(20.dp)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = "Lyrics",
                                    fontSize = 17.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Color.White
                                )

                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                                ) {
                                    // Animated timing bar (SimpMusic LyricsOffsetBar)
                                    AnimatedVisibility(
                                        visible = showLyricsOffsetControl,
                                        enter = fadeIn(tween(180, easing = LyricsEaseOutCubic)) + expandHorizontally(tween(220, easing = LyricsEaseOutCubic)),
                                        exit = fadeOut(tween(140, easing = LyricsEaseInCubic)) + shrinkHorizontally(tween(180, easing = LyricsEaseInCubic))
                                    ) {
                                        val signedOffset = formatLyricsOffsetDisplay(lyricsOffsetMs)
                                        val haptics = LocalHapticFeedback.current
                                        Row(
                                            modifier = Modifier
                                                .clip(CircleShape)
                                                .background(Color.Black.copy(alpha = 0.45f))
                                                .padding(horizontal = 4.dp, vertical = 2.dp),
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Box(
                                                modifier = Modifier
                                                    .size(28.dp)
                                                    .clip(CircleShape)
                                                    .combinedClickable(
                                                        onClick = { onAdjustLyricsOffset(-100) },
                                                        onLongClick = {
                                                            haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                                                            onAdjustLyricsOffset(-500)
                                                        }
                                                    ),
                                                contentAlignment = Alignment.Center
                                            ) {
                                                Icon(
                                                    imageVector = Icons.Rounded.Remove,
                                                    contentDescription = "Delay -0.1s (Hold for -0.5s)",
                                                    tint = Color.White,
                                                    modifier = Modifier.size(15.dp)
                                                )
                                            }
                                            Text(
                                                text = signedOffset,
                                                fontSize = 11.5.sp,
                                                fontWeight = FontWeight.SemiBold,
                                                color = Color.White,
                                                textAlign = TextAlign.Center,
                                                maxLines = 1,
                                                modifier = Modifier.widthIn(min = 52.dp)
                                            )
                                            Box(
                                                modifier = Modifier
                                                    .size(28.dp)
                                                    .clip(CircleShape)
                                                    .combinedClickable(
                                                        onClick = { onAdjustLyricsOffset(100) },
                                                        onLongClick = {
                                                            haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                                                            onAdjustLyricsOffset(500)
                                                        }
                                                    ),
                                                contentAlignment = Alignment.Center
                                            ) {
                                                Icon(
                                                    imageVector = Icons.Rounded.Add,
                                                    contentDescription = "Advance +0.1s (Hold for +0.5s)",
                                                    tint = Color.White,
                                                    modifier = Modifier.size(15.dp)
                                                )
                                            }
                                            if (lyricsOffsetMs != 0) {
                                                IconButton(
                                                    onClick = { onAdjustLyricsOffset(-lyricsOffsetMs) },
                                                    modifier = Modifier.size(28.dp)
                                                ) {
                                                    Icon(
                                                        imageVector = Icons.Rounded.RestartAlt,
                                                        contentDescription = "Reset offset",
                                                        tint = Color.White.copy(alpha = 0.8f),
                                                        modifier = Modifier.size(14.dp)
                                                    )
                                                }
                                            }
                                        }
                                    }

                                    // Translate Lyrics Button
                                    IconButton(
                                        onClick = onTranslateLyrics,
                                        modifier = Modifier.size(34.dp)
                                    ) {
                                        if (isTranslatingLyrics) {
                                            CircularProgressIndicator(
                                                color = Color.White,
                                                strokeWidth = 2.dp,
                                                modifier = Modifier.size(16.dp)
                                            )
                                        } else {
                                            Icon(
                                                imageVector = Icons.Rounded.Translate,
                                                contentDescription = "Translate Lyrics",
                                                tint = if (translatedLyricsMap.isNotEmpty()) Color.White else Color.White.copy(alpha = 0.78f),
                                                modifier = Modifier.size(18.dp)
                                            )
                                        }
                                    }

                                    // Share Lyric Card Button
                                    IconButton(
                                        onClick = onShareLyricsCard,
                                        modifier = Modifier.size(34.dp)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Rounded.Share,
                                            contentDescription = "Share Lyrics",
                                            tint = Color.White.copy(alpha = 0.85f),
                                            modifier = Modifier.size(17.dp)
                                        )
                                    }

                                    // Lyrics Delayer / Time Setter Toggle Button
                                    IconButton(
                                        onClick = { showLyricsOffsetControl = !showLyricsOffsetControl },
                                        modifier = Modifier.size(36.dp)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Rounded.Timer,
                                            contentDescription = "Lyrics Timing",
                                            tint = if (lyricsOffsetMs != 0 || showLyricsOffsetControl) Color.White else Color.White.copy(alpha = 0.78f),
                                            modifier = Modifier.size(19.dp)
                                        )
                                    }

                                    // Expand Lyrics Button (Circle gray fill removed as requested!)
                                    IconButton(
                                        onClick = onOpenLyrics,
                                        modifier = Modifier.size(36.dp)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Rounded.OpenInFull,
                                            contentDescription = "Expand Lyrics",
                                            tint = Color.White,
                                            modifier = Modifier.size(17.dp)
                                        )
                                    }
                                }
                            }

                            Spacer(modifier = Modifier.height(16.dp))

                            val previewTextAlpha = if (lyricsExpansionProgress > 0.05f) 0f else (1f - (lyricsExpansionProgress / 0.05f)).coerceIn(0f, 1f)
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .heightIn(min = 220.dp)
                                    .graphicsLayer { alpha = previewTextAlpha },
                                contentAlignment = Alignment.CenterStart
                            ) {
                                Column(
                                    modifier = Modifier.fillMaxWidth(),
                                    verticalArrangement = Arrangement.Center
                                ) {
                                    val lines = lyrics?.syncedLines ?: emptyList()
                                    val effectiveSec = positionSec + (lyricsOffsetMs / 1000f)
                                    val activeIdx = lines.indexOfLast { effectiveSec >= it.timeSec }.coerceAtLeast(0)

                                    if (lines.isEmpty()) {
                                        Text(
                                            text = SimpMusicLyricsRepository.unescapeLyricsHtml(
                                                lyrics?.plainLyrics?.take(180)?.ifBlank { "Tap to view lyrics" }
                                                    ?: "Tap to view lyrics"
                                            ),
                                            fontSize = 16.sp,
                                            fontWeight = FontWeight.Medium,
                                            color = Color.White.copy(alpha = 0.85f),
                                            lineHeight = 24.sp,
                                            maxLines = 6
                                        )
                                    } else {
                                        val prevPrev = lines.getOrNull(activeIdx - 2)
                                        val prev = lines.getOrNull(activeIdx - 1)
                                        val current = lines.getOrNull(activeIdx)
                                        val next1 = lines.getOrNull(activeIdx + 1)
                                        val next2 = lines.getOrNull(activeIdx + 2)

                                        if (prevPrev != null) {
                                            Text(
                                                text = SimpMusicLyricsRepository.unescapeLyricsHtml(prevPrev.text),
                                                fontSize = 14.sp,
                                                color = Color.White.copy(alpha = 0.35f),
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis
                                            )
                                            Spacer(modifier = Modifier.height(6.dp))
                                        }

                                        if (prev != null) {
                                            Text(
                                                text = SimpMusicLyricsRepository.unescapeLyricsHtml(prev.text),
                                                fontSize = 15.sp,
                                                color = Color.White.copy(alpha = 0.50f),
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis
                                            )
                                            Spacer(modifier = Modifier.height(6.dp))
                                        }

                                        if (current != null) {
                                            if (current.words.isNotEmpty()) {
                                                FlowRow(
                                                    horizontalArrangement = Arrangement.spacedBy(5.dp),
                                                    verticalArrangement = Arrangement.spacedBy(3.dp)
                                                ) {
                                                    current.words.forEach { w ->
                                                        val wordReached = effectiveSec >= w.startTimeSec
                                                        Text(
                                                            text = SimpMusicLyricsRepository.unescapeLyricsHtml(w.word),
                                                            fontSize = 20.sp,
                                                            fontWeight = FontWeight.Bold,
                                                            color = if (wordReached) Color.White else Color.White.copy(alpha = 0.40f),
                                                            lineHeight = 28.sp
                                                        )
                                                    }
                                                }
                                            } else {
                                                Text(
                                                    text = SimpMusicLyricsRepository.unescapeLyricsHtml(current.text),
                                                    fontSize = 20.sp,
                                                    fontWeight = FontWeight.Bold,
                                                    color = Color.White,
                                                    lineHeight = 28.sp
                                                )
                                            }

                                            if (showRomanized && !current.romanizedText.isNullOrBlank()) {
                                                Spacer(modifier = Modifier.height(3.dp))
                                                Text(
                                                    text = SimpMusicLyricsRepository.unescapeLyricsHtml(current.romanizedText),
                                                    fontSize = 13.sp,
                                                    fontWeight = FontWeight.Medium,
                                                    color = Color.White.copy(alpha = 0.75f),
                                                    lineHeight = 18.sp
                                                )
                                            }
                                            val translatedCurrent = translatedLyricsMap[activeIdx]
                                            if (!translatedCurrent.isNullOrBlank()) {
                                                Spacer(modifier = Modifier.height(3.dp))
                                                Text(
                                                    text = translatedCurrent,
                                                    fontSize = 13.5.sp,
                                                    fontWeight = FontWeight.SemiBold,
                                                    color = Color.White.copy(alpha = 0.85f),
                                                    lineHeight = 18.sp
                                                )
                                            }
                                            Spacer(modifier = Modifier.height(8.dp))
                                        }

                                        if (next1 != null) {
                                            Text(
                                                text = SimpMusicLyricsRepository.unescapeLyricsHtml(next1.text),
                                                fontSize = 16.sp,
                                                fontWeight = FontWeight.SemiBold,
                                                color = Color.White.copy(alpha = 0.55f),
                                                lineHeight = 22.sp
                                            )
                                        }

                                        if (next2 != null) {
                                            Spacer(modifier = Modifier.height(6.dp))
                                            Text(
                                                text = SimpMusicLyricsRepository.unescapeLyricsHtml(next2.text),
                                                fontSize = 14.sp,
                                                color = Color.White.copy(alpha = 0.35f),
                                                lineHeight = 20.sp,
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(16.dp))
                    PlayerArtistAndDescription(
                        track = track,
                        artistDetail = artistDetail,
                        videoInfo = videoInfo,
                        palette = palette,
                        isLoading = isArtistDetailLoading,
                        onOpenArtist = onOpenArtist,
                        modifier = Modifier.fillMaxWidth()
                    )

                    Spacer(modifier = Modifier.height(36.dp))
                }
            }
        }
    }
}

@Composable
private fun PlayerArtistAndDescription(
    track: AuraTrack,
    artistDetail: ArtistDetail?,
    videoInfo: AuraVideoInfo?,
    palette: AuraPalette,
    isLoading: Boolean,
    onOpenArtist: () -> Unit,
    modifier: Modifier = Modifier
) {
    var isDescriptionExpanded by remember(track.videoId) { mutableStateOf(false) }
    val cardColor = Color(0xFF202124)
    val artistName = videoInfo?.author?.takeIf(String::isNotBlank)
        ?: artistDetail?.name?.takeIf(String::isNotBlank)
        ?: track.artist
    val artistImage = videoInfo?.authorThumbnail?.takeIf(String::isNotBlank)
        ?: artistDetail?.bannerUrl?.takeIf(String::isNotBlank)
        ?: track.highResArtworkUrl
    val description = videoInfo?.description.orEmpty().ifBlank {
        if (isLoading) "Loading details…" else "No description available."
    }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(bottom = 20.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(18.dp))
                .background(cardColor)
                .clickable(onClick = onOpenArtist)
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(238.dp)
                    .background(Color(0xFF151515))
            ) {
                AsyncImage(
                    model = artistImage,
                    contentDescription = artistName,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize()
                )
                Box(
                    modifier = Modifier
                        .align(Alignment.TopStart)
                        .fillMaxWidth()
                        .height(72.dp)
                        .background(Brush.verticalGradient(listOf(Color.Black.copy(alpha = 0.58f), Color.Transparent)))
                )
                Text(
                    text = "Artists",
                    color = Color.White,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier
                        .align(Alignment.TopStart)
                        .padding(start = 16.dp, top = 14.dp)
                )
            }
            Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp)) {
                Text(
                    text = artistName,
                    color = Color.White,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                val subscribers = videoInfo?.subscribers.orEmpty().ifBlank { artistDetail?.subscribers.orEmpty() }
                if (subscribers.isNotBlank()) {
                    Spacer(Modifier.height(3.dp))
                    Text(subscribers, color = Color.White.copy(alpha = 0.68f), fontSize = 12.sp)
                } else if (isLoading) {
                    Spacer(Modifier.height(8.dp))
                    CircularProgressIndicator(
                        modifier = Modifier.size(14.dp),
                        color = Color.White.copy(alpha = 0.68f),
                        strokeWidth = 1.5.dp
                    )
                }
            }
        }

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(16.dp))
                .background(palette.primary)
                .padding(16.dp)
                .animateContentSize()
        ) {
            if (videoInfo?.uploadDate.orEmpty().isNotBlank()) {
                Text(
                    "Published on ${videoInfo?.uploadDate}",
                    color = Color.White,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold
                )
                Spacer(Modifier.height(10.dp))
            }
            if ((videoInfo?.viewCount ?: 0L) > 0L) {
                Text("${String.format("%,d", videoInfo?.viewCount)} views", color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                Spacer(Modifier.height(8.dp))
            }
            if ((videoInfo?.likes ?: 0L) > 0L || (videoInfo?.dislikes ?: 0L) > 0L) {
                Text(
                    "${String.format("%,d", videoInfo?.likes ?: 0L)} likes  ·  ${String.format("%,d", videoInfo?.dislikes ?: 0L)} dislikes",
                    color = Color.White.copy(alpha = 0.68f),
                    fontSize = 12.sp
                )
                Spacer(Modifier.height(12.dp))
            }
            Text("Description", color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(8.dp))
            Text(
                text = description,
                color = Color.White.copy(alpha = 0.72f),
                fontSize = 13.sp,
                lineHeight = 19.sp,
                maxLines = if (isDescriptionExpanded) Int.MAX_VALUE else 5,
                overflow = TextOverflow.Ellipsis
            )
            if (description.length > 180) {
                Spacer(Modifier.height(10.dp))
                Text(
                    text = if (isDescriptionExpanded) "Less" else "More",
                    color = Color.White,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.clickable { isDescriptionExpanded = !isDescriptionExpanded }
                )
            }
        }
    }
}

@Composable
private fun ArtistSectionPlaybackHeader(
    modifier: Modifier = Modifier,
    track: AuraTrack,
    isPlaying: Boolean,
    isBuffering: Boolean,
    isFavorite: Boolean,
    positionSec: Float,
    durationSec: Float,
    backgroundColor: Color,
    progressColor: Color,
    progressTrackColor: Color,
    onTogglePlayPause: () -> Unit,
    onToggleFavorite: () -> Unit
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(backgroundColor)
    ) {
        Spacer(Modifier.height(8.dp))
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(58.dp)
                .padding(start = 24.dp, end = 16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = track.title,
                    color = Color.White,
                    style = MaterialTheme.typography.titleSmall,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = track.artist,
                    color = Color.White.copy(alpha = 0.66f),
                    style = MaterialTheme.typography.bodySmall,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            IconButton(onClick = onToggleFavorite, modifier = Modifier.size(42.dp)) {
                Icon(
                    imageVector = if (isFavorite) Icons.Rounded.Favorite else Icons.Rounded.FavoriteBorder,
                    contentDescription = if (isFavorite) "Remove from liked songs" else "Like song",
                    tint = if (isFavorite) AuraPlayGreen else Color.White,
                    modifier = Modifier.size(22.dp)
                )
            }
            IconButton(onClick = onTogglePlayPause, modifier = Modifier.size(42.dp)) {
                if (isBuffering && !isPlaying) {
                    CircularProgressIndicator(color = Color.White, strokeWidth = 2.dp, modifier = Modifier.size(20.dp))
                } else {
                    Icon(
                        imageVector = if (isPlaying) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
                        contentDescription = if (isPlaying) "Pause" else "Play",
                        tint = Color.White,
                        modifier = Modifier.size(24.dp)
                    )
                }
            }
        }
        Canvas(
            modifier = Modifier
                .fillMaxWidth()
                .height(2.dp)
        ) {
            val fraction = (positionSec / durationSec.coerceAtLeast(1f)).coerceIn(0f, 1f)
            drawRect(progressTrackColor)
            drawRect(progressColor, size = size.copy(width = size.width * fraction))
        }
    }
}
