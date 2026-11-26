package com.auramusic.app.ui.screens.player.spotify

import android.view.ViewGroup
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.QueueMusic
import androidx.compose.material.icons.rounded.Audiotrack
import androidx.compose.material.icons.rounded.Bedtime
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.FavoriteBorder
import androidx.compose.material.icons.rounded.LiveTv
import androidx.compose.material.icons.rounded.Lyrics
import androidx.compose.material.icons.rounded.Palette
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material.icons.rounded.PlaylistAdd
import androidx.compose.material.icons.rounded.Videocam
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView
import coil.compose.AsyncImage
import com.auramusic.app.LocalIsInPipMode
import com.auramusic.app.core.media.AuraPlayerManager
import com.auramusic.app.domain.model.AuraTrack
import com.auramusic.app.domain.model.NowPlayingStyle
import com.auramusic.app.ui.components.ForceLightIconsSystemBarsEffect
import com.auramusic.app.ui.theme.TextSecondary
import kotlinx.coroutines.launch

@Suppress("NOTHING_TO_INLINE")
internal inline fun lerpFloat(start: Float, stop: Float, fraction: Float): Float =
    start + (stop - start) * fraction

internal val LyricsEaseOutCubic = CubicBezierEasing(0.23f, 1f, 0.32f, 1f)
internal val LyricsEaseInCubic = CubicBezierEasing(0.32f, 0f, 0.67f, 0f)

internal fun formatLyricsOffsetDisplay(offsetMs: Int): String {
    if (offsetMs == 0) return "0.0s"
    val sign = if (offsetMs > 0) "+" else "-"
    val absMs = kotlin.math.abs(offsetMs)
    val s = absMs / 1000
    val tenths = (absMs % 1000) / 100
    return "$sign$s.${tenths}s"
}

/**
 * 3-Dot Options Bottom Sheet Modal on Full Music Player
 * Unified options with theme toggling, go to artist, liked songs, queue actions, and lyrics.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun NowPlayingOptionsModal(
    track: AuraTrack,
    currentStyle: NowPlayingStyle,
    isFavorite: Boolean,
    isVideoMode: Boolean = false,
    modalContainerColor: Color,
    onDismiss: () -> Unit,
    onToggleVideoMode: () -> Unit,
    onOpenLivePerformances: () -> Unit,
    onToggleTheme: () -> Unit,
    mainLyricsProvider: String = "SimpMusic",
    onSwitchLyricsProvider: (String) -> Unit = {},
    onGoToArtist: () -> Unit,
    onToggleFavorite: () -> Unit,
    onAddToPlaylist: () -> Unit = {},
    onPlayNext: () -> Unit,
    onAddToQueue: () -> Unit,
    onOpenQueue: () -> Unit = {},
    onOpenLyrics: () -> Unit = {},
    isDownloaded: Boolean = false,
    isDownloading: Boolean = false,
    onDownloadTrack: (() -> Unit)? = null,
    onRemoveDownload: (() -> Unit)? = null,
    sleepTimerStatus: String? = null,
    onOpenSleepTimer: (() -> Unit)? = null
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = modalContainerColor,
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
        ForceLightIconsSystemBarsEffect()
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 22.dp, vertical = 6.dp)
        ) {
            // Header preview
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 14.dp)
            ) {
                AsyncImage(
                    model = track.highResArtworkUrl,
                    contentDescription = track.title,
                    modifier = Modifier
                        .size(50.dp)
                        .clip(RoundedCornerShape(8.dp)),
                    contentScale = ContentScale.Crop
                )
                Spacer(modifier = Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = track.title,
                        style = MaterialTheme.typography.titleSmall,
                        color = Color.White,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = track.artist,
                        style = MaterialTheme.typography.bodySmall,
                        color = TextSecondary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }

            HorizontalDivider(color = Color.White.copy(alpha = 0.08f), thickness = 0.5.dp)
            Spacer(modifier = Modifier.height(8.dp))

            // 1. Video Player Mode Toggle
            NowPlayingModalOptionRow(
                icon = if (isVideoMode) Icons.Rounded.Audiotrack else Icons.Rounded.Videocam,
                label = if (isVideoMode) "Switch to Audio Mode" else "Watch Official Video",
                onClick = onToggleVideoMode
            )

            // 2. Download / Remove Download
            if (isDownloaded && onRemoveDownload != null) {
                NowPlayingModalOptionRow(
                    icon = Icons.Rounded.DeleteOutline,
                    label = "Remove from Downloads",
                    onClick = onRemoveDownload
                )
            } else if (onDownloadTrack != null) {
                NowPlayingModalOptionRow(
                    icon = if (isDownloaded) Icons.Rounded.DeleteOutline else Icons.Rounded.Download,
                    label = if (isDownloading) "Downloading…" else if (isDownloaded) "Remove from Downloads" else "Download Song",
                    onClick = {
                        if (isDownloaded && onRemoveDownload != null) {
                            onRemoveDownload()
                        } else {
                            onDownloadTrack()
                        }
                    }
                )
            }

            // 2. Live Performances & Alternate Videos
            NowPlayingModalOptionRow(
                icon = Icons.Rounded.LiveTv,
                label = "More Videos",
                onClick = onOpenLivePerformances
            )

            // 3. Toggle Theme Option
            val nextThemeLabel = if (currentStyle == NowPlayingStyle.SPOTIFY) "Liquid Glass" else "Default"
            NowPlayingModalOptionRow(
                icon = Icons.Rounded.Palette,
                label = "Switch Theme to $nextThemeLabel",
                onClick = onToggleTheme
            )

            // 3b. Switch Lyrics Provider (SimpMusic <-> LRCLIB)
            val nextLyricsProvider = if (mainLyricsProvider == "LRCLIB") "SimpMusic" else "LRCLIB"
            NowPlayingModalOptionRow(
                icon = Icons.Rounded.Lyrics,
                label = "Switch Lyrics Provider to $nextLyricsProvider",
                onClick = { onSwitchLyricsProvider(nextLyricsProvider) }
            )

            // 3c. Sleep Timer
            if (onOpenSleepTimer != null) {
                NowPlayingModalOptionRow(
                    icon = Icons.Rounded.Bedtime,
                    label = if (sleepTimerStatus != null) "Sleep Timer ($sleepTimerStatus)" else "Sleep Timer",
                    onClick = onOpenSleepTimer
                )
            }

            // 4. Go to Artist Screen
            NowPlayingModalOptionRow(
                icon = Icons.Rounded.Person,
                label = "View Artist",
                onClick = onGoToArtist
            )

            // 5. Favorite / Liked Songs
            NowPlayingModalOptionRow(
                icon = if (isFavorite) Icons.Rounded.Favorite else Icons.Rounded.FavoriteBorder,
                label = if (isFavorite) "Remove from Liked Songs" else "Add to Liked Songs",
                onClick = onToggleFavorite
            )

            // 5b. Add to Playlist
            NowPlayingModalOptionRow(
                icon = Icons.Rounded.PlaylistAdd,
                label = "Add to Playlist",
                onClick = onAddToPlaylist
            )

            // 6. Play Next
            NowPlayingModalOptionRow(
                icon = Icons.AutoMirrored.Rounded.QueueMusic,
                label = "Play Next",
                onClick = onPlayNext
            )

            // 7. Add to Queue
            NowPlayingModalOptionRow(
                icon = Icons.Rounded.PlaylistAdd,
                label = "Add to Queue",
                onClick = onAddToQueue
            )

            Spacer(modifier = Modifier.height(24.dp))
        }
    }
}

@Composable
private fun NowPlayingModalOptionRow(
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
            tint = Color.White,
            modifier = Modifier.size(22.dp)
        )
        Spacer(modifier = Modifier.width(16.dp))
        Text(
            text = label,
            color = Color.White,
            fontWeight = FontWeight.Medium,
            fontSize = 15.sp
        )
    }
}

/**
 * High-performance ExoPlayer video surface maximized to the sides when in Video Mode.
 */
@Composable
internal fun SpotifyVideoPlayerSurface(
    modifier: Modifier = Modifier,
    track: AuraTrack,
    isLoading: Boolean,
    onSwitchToAudio: () -> Unit
) {
    val isInPipMode = LocalIsInPipMode.current

    Box(
        modifier = modifier
            .background(Color.Black)
            .clipToBounds(),
        contentAlignment = Alignment.Center
    ) {
        // A soft, dimmed poster fills any space left by the video's native aspect ratio.
        AsyncImage(
            model = track.highResArtworkUrl.ifBlank { "https://i.ytimg.com/vi/${track.videoId}/hqdefault.jpg" },
            contentDescription = track.title,
            contentScale = ContentScale.Crop,
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer {
                    scaleX = 1.18f
                    scaleY = 1.18f
                    alpha = 0.62f
                }
                .blur(28.dp)
        )

        if (!isInPipMode) {
            AndroidView(
                factory = { ctx ->
                    PlayerView(ctx).apply {
                        player = AuraPlayerManager.exoPlayer
                        useController = false
                        resizeMode = AspectRatioFrameLayout.RESIZE_MODE_FIT
                        setKeepContentOnPlayerReset(true)
                        setShutterBackgroundColor(android.graphics.Color.TRANSPARENT)
                        setBackgroundColor(android.graphics.Color.TRANSPARENT)
                        layoutParams = ViewGroup.LayoutParams(
                            ViewGroup.LayoutParams.MATCH_PARENT,
                            ViewGroup.LayoutParams.MATCH_PARENT
                        )
                        post {
                            if (isAttachedToWindow && player !== AuraPlayerManager.exoPlayer) {
                                player = AuraPlayerManager.exoPlayer
                            }
                        }
                    }
                },
                update = { view ->
                    if (view.player !== AuraPlayerManager.exoPlayer) {
                        view.player = AuraPlayerManager.exoPlayer
                    }
                },
                modifier = Modifier.fillMaxSize(),
                onRelease = { view ->
                    // PlayerView uses a separate video surface by default. Explicitly detach it
                    // when the full player leaves composition so it cannot cover or intercept
                    // the mini-player after a video is collapsed or during PiP handoff.
                    view.player = null
                }
            )
        }

        // Loading indicator overlay while resolving video stream
        if (isLoading) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.55f)),
                contentAlignment = Alignment.Center
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    CircularProgressIndicator(
                        color = Color.White,
                        strokeWidth = 3.dp,
                        modifier = Modifier.size(36.dp)
                    )
                    Text(
                        text = "Loading Video…",
                        color = Color.White.copy(alpha = 0.9f),
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Medium
                    )
                }
            }
        }
    }
}

/**
 * Sleek, slim music player timeline scrubber with a continuous active/inactive line
 * and a perfectly centered circular point thumb (no thick pill gaps, no stop indicator dots).
 */
@Composable
internal fun CompactPointSlider(
    value: Float,
    onValueChange: (Float) -> Unit,
    valueRange: ClosedFloatingPointRange<Float>,
    modifier: Modifier = Modifier,
    bufferedValue: Float = 0f,
    pointColor: Color = Color.White,
    activeTrackColor: Color = Color.White,
    bufferTrackColor: Color = Color.White.copy(alpha = 0.45f),
    inactiveTrackColor: Color = Color.White.copy(alpha = 0.20f),
    trackHeight: Dp = 2.5.dp,
    thumbRadius: Dp = 5.dp
) {
    val totalRange = (valueRange.endInclusive - valueRange.start).coerceAtLeast(0.001f)
    val currentFraction = ((value - valueRange.start) / totalRange).coerceIn(0f, 1f)
    val bufferedFraction = ((bufferedValue - valueRange.start) / totalRange).coerceIn(0f, 1f)

    var isDragging by remember { mutableStateOf(false) }
    var dragFraction by remember { mutableFloatStateOf(0f) }

    val displayFraction = if (isDragging) dragFraction else currentFraction

    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(28.dp)
            .pointerInput(valueRange) {
                detectTapGestures { offset ->
                    val availableWidth = (size.width - 2 * thumbRadius.toPx()).coerceAtLeast(1f)
                    val newFraction = ((offset.x - thumbRadius.toPx()) / availableWidth).coerceIn(0f, 1f)
                    val newValue = valueRange.start + newFraction * totalRange
                    onValueChange(newValue)
                }
            }
            .pointerInput(valueRange) {
                detectHorizontalDragGestures(
                    onDragStart = { offset ->
                        isDragging = true
                        val availableWidth = (size.width - 2 * thumbRadius.toPx()).coerceAtLeast(1f)
                        dragFraction = ((offset.x - thumbRadius.toPx()) / availableWidth).coerceIn(0f, 1f)
                    },
                    onDragEnd = {
                        isDragging = false
                        val finalValue = valueRange.start + dragFraction * totalRange
                        onValueChange(finalValue)
                    },
                    onDragCancel = {
                        isDragging = false
                    },
                    onHorizontalDrag = { change, _ ->
                        change.consume()
                        val availableWidth = (size.width - 2 * thumbRadius.toPx()).coerceAtLeast(1f)
                        val newFraction = ((change.position.x - thumbRadius.toPx()) / availableWidth).coerceIn(0f, 1f)
                        dragFraction = newFraction
                        val intermediateValue = valueRange.start + newFraction * totalRange
                        onValueChange(intermediateValue)
                    }
                )
            },
        contentAlignment = Alignment.Center
    ) {
        Canvas(
            modifier = Modifier
                .fillMaxWidth()
                .height(24.dp)
        ) {
            val width = size.width
            val centerY = size.height / 2f
            val trackHeightPx = trackHeight.toPx()
            val thumbRadiusPx = thumbRadius.toPx()

            val startX = thumbRadiusPx
            val endX = width - thumbRadiusPx
            val trackSpan = (endX - startX).coerceAtLeast(1f)
            val thumbX = startX + displayFraction * trackSpan
            val bufferX = startX + bufferedFraction * trackSpan

            // 1. Inactive full-width background slim line
            drawLine(
                color = inactiveTrackColor,
                start = Offset(startX, centerY),
                end = Offset(endX, centerY),
                strokeWidth = trackHeightPx,
                cap = StrokeCap.Round
            )

            // 2. Buffer bar / buffering indicator (pre-filled, lighter line indicating downloaded memory)
            if (bufferX > startX) {
                drawLine(
                    color = bufferTrackColor,
                    start = Offset(startX, centerY),
                    end = Offset(bufferX, centerY),
                    strokeWidth = trackHeightPx,
                    cap = StrokeCap.Round
                )
            }

            // 3. Active played slim line (continuous, no gap!)
            if (thumbX > startX) {
                drawLine(
                    color = activeTrackColor,
                    start = Offset(startX, centerY),
                    end = Offset(thumbX, centerY),
                    strokeWidth = trackHeightPx,
                    cap = StrokeCap.Round
                )
            }

            // 4. Circular point thumb (dead-center on the line, perfectly bisecting it)
            drawCircle(
                color = pointColor,
                radius = if (isDragging) thumbRadiusPx * 1.35f else thumbRadiusPx,
                center = Offset(thumbX, centerY)
            )
        }
    }
}

/**
 * Interactive draggable cover art implementing Spotify-grade horizontal direct manipulation:
 * - 1:1 finger tracking with smooth horizontal translation
 * - Visual peek and entrance of previous and next track artwork in queue
 * - Subtle dynamic tilt (rotationZ) and depth scale during drag
 * - Boundary rubber-band resistance when at the start/end of queue
 * - Fast fling / threshold detection (>72dp) with spring snap-back or exit-to-skip
 * - Fluid entrance animation on song change
 */
@Composable
internal fun AuraDraggableCoverArt(
    currentTrack: AuraTrack,
    queue: List<AuraTrack>,
    cornerRadius: Dp,
    hideArtworkForLyricsTransition: Boolean,
    onSkipNext: () -> Unit,
    onSkipPrevious: () -> Unit,
    onArtworkPositioned: (Rect) -> Unit,
    modifier: Modifier = Modifier,
    overlayContent: @Composable () -> Unit = {}
) {
    val coroutineScope = rememberCoroutineScope()
    val dragOffset = remember { Animatable(0f) }
    var pendingTrack by remember { mutableStateOf<AuraTrack?>(null) }
    var containerWidthPx by remember { mutableFloatStateOf(0f) }
    val density = LocalDensity.current
    val swipeThresholdPx = with(density) { 72.dp.toPx() }

    // Use pendingTrack during the 1-frame handoff after a swipe so neither the old track
    // nor the next-of-next preview card can ever flash on screen.
    val displayTrack = pendingTrack ?: currentTrack

    // Resolve queue neighbors relative to displayTrack
    val curIdx = remember(queue, displayTrack.videoId) {
        queue.indexOfFirst { it.videoId == displayTrack.videoId }
    }
    val prevTrack = remember(queue, curIdx) {
        if (curIdx > 0 && curIdx <= queue.lastIndex) queue[curIdx - 1] else null
    }
    val nextTrack = remember(queue, curIdx) {
        if (curIdx >= 0 && curIdx < queue.lastIndex) queue[curIdx + 1] else null
    }

    val gapPx = with(density) { 16.dp.toPx() }

    LaunchedEffect(currentTrack.videoId) {
        if (pendingTrack != null && currentTrack.videoId == pendingTrack?.videoId) {
            pendingTrack = null
        } else if (pendingTrack == null) {
            dragOffset.snapTo(0f)
        }
    }

    Box(
        modifier = modifier
            .clipToBounds()
            .onGloballyPositioned { coordinates ->
                containerWidthPx = coordinates.size.width.toFloat()
                onArtworkPositioned(coordinates.boundsInRoot())
            }
            .pointerInput(displayTrack.videoId, queue.size, curIdx) {
                detectHorizontalDragGestures(
                    onDragStart = { /* touch down */ },
                    onHorizontalDrag = { change, dragAmount ->
                        change.consume()
                        coroutineScope.launch {
                            val currentVal = dragOffset.value
                            // Rubber band resistance if dragging against edge of queue
                            val hasBoundary = (currentVal > 0 && prevTrack == null) ||
                                (currentVal < 0 && nextTrack == null)
                            val resistance = if (hasBoundary) 0.32f else 1f
                            dragOffset.snapTo(currentVal + dragAmount * resistance)
                        }
                    },
                    onDragEnd = {
                        coroutineScope.launch {
                            val currentVal = dragOffset.value
                            val effectiveWidth = if (containerWidthPx > 0f) containerWidthPx else with(density) { 320.dp.toPx() }
                            if (currentVal < -swipeThresholdPx && nextTrack != null) {
                                // Dragged left -> Next Track (incoming preview lands cleanly at 0f)
                                val target = nextTrack
                                dragOffset.animateTo(
                                    targetValue = -(effectiveWidth + gapPx),
                                    animationSpec = tween(200, easing = CubicBezierEasing(0.23f, 1f, 0.32f, 1f))
                                )
                                // Lock displayTrack to target and reset offset atomically before seeking
                                pendingTrack = target
                                dragOffset.snapTo(0f)
                                if (curIdx >= 0 && curIdx + 1 in queue.indices) {
                                    AuraPlayerManager.seekToQueueIndex(curIdx + 1)
                                } else {
                                    onSkipNext()
                                }
                            } else if (currentVal > swipeThresholdPx && prevTrack != null) {
                                // Dragged right -> Previous Track (incoming preview lands cleanly at 0f)
                                val target = prevTrack
                                dragOffset.animateTo(
                                    targetValue = effectiveWidth + gapPx,
                                    animationSpec = tween(200, easing = CubicBezierEasing(0.23f, 1f, 0.32f, 1f))
                                )
                                // Lock displayTrack to target and reset offset atomically before seeking
                                pendingTrack = target
                                dragOffset.snapTo(0f)
                                if (curIdx > 0 && curIdx - 1 in queue.indices) {
                                    AuraPlayerManager.seekToQueueIndex(curIdx - 1)
                                } else {
                                    onSkipPrevious()
                                }
                            } else {
                                // Snap back to center
                                dragOffset.animateTo(
                                    targetValue = 0f,
                                    animationSpec = spring(
                                        dampingRatio = Spring.DampingRatioMediumBouncy,
                                        stiffness = Spring.StiffnessMediumLow
                                    )
                                )
                            }
                        }
                    },
                    onDragCancel = {
                        coroutineScope.launch {
                            dragOffset.animateTo(
                                targetValue = 0f,
                                animationSpec = spring(
                                    dampingRatio = Spring.DampingRatioMediumBouncy,
                                    stiffness = Spring.StiffnessMediumLow
                                )
                            )
                        }
                    }
                )
            },
        contentAlignment = Alignment.Center
    ) {
        val offset = dragOffset.value
        val width = if (containerWidthPx > 0f) containerWidthPx else with(density) { 320.dp.toPx() }

        // 1. Previous Track Card Preview (revealed on drag right)
        if (prevTrack != null && offset > 4f) {
            val dragFraction = (offset / width).coerceIn(0f, 1f)
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .graphicsLayer {
                        translationX = offset - width - 16.dp.toPx()
                        scaleX = 0.92f + 0.08f * dragFraction
                        scaleY = 0.92f + 0.08f * dragFraction
                        alpha = 0.4f + 0.6f * dragFraction
                    }
                    .clip(RoundedCornerShape(cornerRadius))
                    .background(Color(0xFF202020))
            ) {
                AsyncImage(
                    model = prevTrack.highResArtworkUrl,
                    contentDescription = prevTrack.title,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize()
                )
            }
        }

        // 2. Next Track Card Preview (revealed on drag left)
        if (nextTrack != null && offset < -4f) {
            val dragFraction = (-offset / width).coerceIn(0f, 1f)
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .graphicsLayer {
                        translationX = offset + width + 16.dp.toPx()
                        scaleX = 0.92f + 0.08f * dragFraction
                        scaleY = 0.92f + 0.08f * dragFraction
                        alpha = 0.4f + 0.6f * dragFraction
                    }
                    .clip(RoundedCornerShape(cornerRadius))
                    .background(Color(0xFF202020))
            ) {
                AsyncImage(
                    model = nextTrack.highResArtworkUrl,
                    contentDescription = nextTrack.title,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize()
                )
            }
        }

        // 3. Current Track Artwork (main draggable surface)
        val dragFraction = (kotlin.math.abs(offset) / width).coerceIn(0f, 1f)
        val tiltDegrees = (offset / width) * 4f
        Box(
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer {
                    translationX = offset
                    rotationZ = tiltDegrees
                    scaleX = 1f - 0.04f * dragFraction
                    scaleY = 1f - 0.04f * dragFraction
                    alpha = if (hideArtworkForLyricsTransition) 0f else (1f - 0.28f * dragFraction)
                }
                .clip(RoundedCornerShape(cornerRadius))
                .background(Color(0xFF242424))
        ) {
            AsyncImage(
                model = displayTrack.highResArtworkUrl,
                contentDescription = displayTrack.title,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize()
            )
            overlayContent()
        }
    }
}
