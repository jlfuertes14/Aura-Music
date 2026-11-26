package com.auramusic.app.ui.screens.player.spotify

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandHorizontally
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkHorizontally
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.interaction.collectIsDraggedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.QueueMusic
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.OpenInFull
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Remove
import androidx.compose.material.icons.rounded.Repeat
import androidx.compose.material.icons.rounded.RepeatOne
import androidx.compose.material.icons.rounded.RestartAlt
import androidx.compose.material.icons.rounded.Shuffle
import androidx.compose.material.icons.rounded.SkipNext
import androidx.compose.material.icons.rounded.SkipPrevious
import androidx.compose.material.icons.rounded.Timer
import androidx.compose.material.icons.rounded.Translate
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import coil.compose.AsyncImage
import com.auramusic.app.core.lyrics.SimpMusicLyricsRepository
import com.auramusic.app.core.media.AuraPlayerManager
import com.auramusic.app.domain.model.AuraPalette
import com.auramusic.app.domain.model.AuraTrack
import com.auramusic.app.domain.model.LyricsData
import com.auramusic.app.domain.model.LyricsStyle
import com.auramusic.app.domain.model.NowPlayingStyle
import com.auramusic.app.domain.model.PlaybackMode
import com.auramusic.app.ui.screens.formatDuration
import com.auramusic.app.ui.screens.player.applemusic.AppleMusicArtworkBackdrop
import com.auramusic.app.ui.screens.player.applemusic.AppleMusicFullScreenLyricsView
import com.auramusic.app.ui.screens.player.applemusic.AppleMusicLyricsList
import com.auramusic.app.ui.theme.AuraPlayGreen
import com.auramusic.app.ui.theme.AuraPoppinsFontFamily
import com.auramusic.app.ui.theme.AuraViolet
import com.auramusic.app.ui.theme.LocalLyricsStyle

/**
 * Full-screen Song Lyrics Overlay with Shared Element FLIP morphing from the Spotify lyrics card
 * and dedicated Apple Music Full-Screen Lyrics rendering when [LyricsStyle.APPLE_MUSIC] is active.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun SpotifyLyricsOverlay(
    track: AuraTrack,
    lyrics: LyricsData?,
    activeLyrics: LyricsData?,
    isLyricsLoading: Boolean,
    isLyricsExpanded: Boolean,
    lyricsTransitionProgress: Float,
    lyricsExpansionProgress: Float,
    positionSec: Float,
    durationSec: Float,
    bufferedSec: Float,
    isPlaying: Boolean,
    isBuffering: Boolean,
    isFavorite: Boolean,
    isVideoMode: Boolean,
    lyricsOffsetMs: Int,
    showRomanized: Boolean,
    palette: AuraPalette,
    currentStyle: NowPlayingStyle,
    fullWidthPx: Float,
    fullHeightPx: Float,
    statusBarHeightPx: Int,
    lyricsCardBounds: Rect?,
    lyricsSourceArtBounds: Rect?,
    lyricsHeaderArtBounds: Rect?,
    coverFlightEasing: CubicBezierEasing,
    onHeaderArtPositioned: (Rect) -> Unit,
    onClose: () -> Unit,
    onSeekTo: (Float) -> Unit,
    onTogglePlayPause: () -> Unit,
    onSkipPrevious: () -> Unit,
    onSkipNext: () -> Unit,
    onToggleFavorite: () -> Unit,
    onOpenArtist: () -> Unit,
    onOpenOptions: () -> Unit,
    onOpenComments: () -> Unit,
    onShareTrack: () -> Unit,
    onSaveToPlaylist: () -> Unit,
    onToggleVideoMode: () -> Unit,
    onOpenQueue: () -> Unit,
    onToggleRomanized: () -> Unit,
    onAdjustLyricsOffset: (Int) -> Unit
) {
    // Dark dimming scrim over player view when lyrics is expanding/expanded
    if (lyricsExpansionProgress > 0.001f) {
        val scrimAlpha = (0.50f * lyricsExpansionProgress).coerceIn(0f, 1f)
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = scrimAlpha))
        )
    }

    // Full Screen Song Lyrics Screen:
    // Shared Element Transition (Hero animation / FLIP morphing):
    // Automatically tracks the exact physical position, width, and height of the lyrics card.
    // Opens by expanding fluidly outwards from the card's exact geometry.
    // Closes by morphing and physically traveling back into its original slot on the screen!
    if (lyricsTransitionProgress > 0.001f) {
        val lyricsStyle = LocalLyricsStyle.current
        val isAppleMusicLyrics = lyricsStyle == LyricsStyle.APPLE_MUSIC
        val clampedProgress = lyricsExpansionProgress.coerceIn(0f, 1f)

        if (isAppleMusicLyrics) {
            // Dedicated Apple Music Full-Screen Lyrics (separate from Spotify's solid-color view):
            // Blurry/frosted compact header over scrolling lyrics + 3s auto-hiding player controls.
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .graphicsLayer { alpha = clampedProgress }
            ) {
                AppleMusicFullScreenLyricsView(
                    track = track,
                    lyricsData = activeLyrics,
                    isLoading = isLyricsLoading,
                    positionSec = positionSec,
                    durationSec = durationSec,
                    isPlaying = isPlaying,
                    isBuffering = isBuffering,
                    isFavorite = isFavorite,
                    isVideoMode = isVideoMode,
                    lyricsOffsetMs = lyricsOffsetMs,
                    showRomanized = showRomanized,
                    palette = palette,
                    showControlsAllowed = isLyricsExpanded && clampedProgress >= 0.45f,
                    onClose = onClose,
                    onSeekTo = onSeekTo,
                    onTogglePlayPause = onTogglePlayPause,
                    onSkipPrevious = onSkipPrevious,
                    onSkipNext = onSkipNext,
                    onToggleFavorite = onToggleFavorite,
                    onOpenArtist = onOpenArtist,
                    onOpenOptions = onOpenOptions,
                    onOpenComments = onOpenComments,
                    onShareTrack = onShareTrack,
                    onSaveToPlaylist = onSaveToPlaylist,
                    onToggleVideoMode = onToggleVideoMode,
                    onOpenQueue = onOpenQueue,
                    onToggleRomanized = onToggleRomanized,
                    onAdjustLyricsOffset = onAdjustLyricsOffset,
                    onArtworkPositioned = onHeaderArtPositioned
                )
            }
        } else {
            val density = LocalDensity.current

            val fallbackMargin = with(density) { 20.dp.toPx() }
            val fallbackWidth = fullWidthPx - (fallbackMargin * 2f)
            val fallbackHeight = with(density) { 280.dp.toPx() }
            val fallbackTop = fullHeightPx - with(density) { 340.dp.toPx() }
            val compactHeaderBottomPx = statusBarHeightPx.toFloat() +
                with(density) { 66.dp.toPx() }
            val compactHeaderBottomDp = with(density) { compactHeaderBottomPx.toDp() }

            val hasCardOrigin = currentStyle == NowPlayingStyle.SPOTIFY
            val startLeft = lyricsCardBounds?.left ?: fallbackMargin
            val startTop = lyricsCardBounds?.top ?: fallbackTop
            val startWidth = lyricsCardBounds?.width?.takeIf { it > 10f } ?: fallbackWidth
            val startHeight = lyricsCardBounds?.height?.takeIf { it > 10f } ?: fallbackHeight

            val targetTop = compactHeaderBottomPx
            val targetHeight = (fullHeightPx - compactHeaderBottomPx).coerceAtLeast(10f)

            val currentLeft = if (hasCardOrigin) lerpFloat(startLeft, 0f, clampedProgress) else 0f
            val currentTop = if (hasCardOrigin) lerpFloat(startTop, targetTop, clampedProgress) else targetTop
            val currentWidth = (if (hasCardOrigin) lerpFloat(startWidth, fullWidthPx, clampedProgress) else fullWidthPx).coerceAtLeast(10f)
            val currentHeight = (if (hasCardOrigin) lerpFloat(startHeight, targetHeight, clampedProgress) else targetHeight).coerceAtLeast(10f)

            val currentLeftDp = with(density) { currentLeft.toDp() }
            val currentTopDp = with(density) { currentTop.toDp() }
            val currentWidthDp = with(density) { currentWidth.toDp() }
            val currentHeightDp = with(density) { currentHeight.toDp() }
            val currentCornerRadius = if (!hasCardOrigin) {
                0f
            } else {
                lerpFloat(20f, 18f, clampedProgress).coerceIn(0f, 20f)
            }
            val currentElevation = if (hasCardOrigin) (16.dp * (1f - clampedProgress)).coerceAtLeast(0.dp) else 0.dp
            val containerAlpha = if (hasCardOrigin) 1f else clampedProgress

            Box(
                modifier = Modifier.fillMaxSize()
            ) {
                Box(
                    modifier = Modifier
                        .offset(x = currentLeftDp, y = currentTopDp)
                        .size(width = currentWidthDp, height = currentHeightDp)
                        .graphicsLayer { alpha = containerAlpha }
                        .clip(RoundedCornerShape(currentCornerRadius.dp))
                        .background(palette.primary)
                        .shadow(
                            elevation = currentElevation,
                            shape = RoundedCornerShape(currentCornerRadius.dp)
                        )
                ) {
                    // When expanding or collapsing, FullScreenSongLyricsView renders inside the clipped container
                    // with 100% natural font size (scaleX = 1f, scaleY = 1f, ZERO squashing or text compression!)
                    // Player controls are completely hidden when shrinking back to the lyrics card slot
                    FullScreenSongLyricsView(
                        track = track,
                        lyrics = activeLyrics,
                        isLyricsLoading = isLyricsLoading,
                        positionSec = positionSec,
                        durationSec = durationSec,
                        bufferedSec = bufferedSec,
                        isPlaying = isPlaying,
                        isBuffering = isBuffering,
                        lyricsOffsetMs = lyricsOffsetMs,
                        showRomanized = showRomanized,
                        palette = palette,
                        showPlayerControls = lyricsExpansionProgress >= 0.38f,
                        lyricsExpansionProgress = lyricsExpansionProgress,
                        compactHeaderTopPadding = compactHeaderBottomDp,
                        onSeekTo = onSeekTo,
                        onTogglePlayPause = onTogglePlayPause,
                        onSkipPrevious = onSkipPrevious,
                        onSkipNext = onSkipNext,
                        onOpenQueue = onOpenQueue,
                        onAdjustLyricsOffset = onAdjustLyricsOffset
                    )

                    // Smooth seamless card preview blend when close to the bottom slot
                    // Matches the card's exact 16.sp font size and layout without any jump
                    if (hasCardOrigin && lyricsExpansionProgress < 0.35f) {
                        val previewFadeAlpha = (1f - (lyricsExpansionProgress / 0.35f)).coerceIn(0f, 1f)
                        Surface(
                            color = palette.primary,
                            shape = RoundedCornerShape(currentCornerRadius.dp),
                            modifier = Modifier
                                .fillMaxSize()
                                .graphicsLayer { alpha = previewFadeAlpha }
                        ) {
                            Column(
                                modifier = Modifier
                                    .fillMaxSize()
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

                                    Surface(
                                        shape = CircleShape,
                                        color = Color.Black.copy(alpha = 0.35f),
                                        modifier = Modifier.size(32.dp)
                                    ) {
                                        Box(contentAlignment = Alignment.Center) {
                                            Icon(
                                                imageVector = Icons.Rounded.OpenInFull,
                                                contentDescription = null,
                                                tint = Color.White,
                                                modifier = Modifier.size(15.dp)
                                            )
                                        }
                                    }
                                }

                                Spacer(modifier = Modifier.height(16.dp))

                                val lines = lyrics?.syncedLines ?: emptyList()
                                val effectiveSec = positionSec + (lyricsOffsetMs / 1000f)
                                val activeIdx = lines.indexOfLast { effectiveSec >= it.timeSec }.coerceAtLeast(0)

                                if (lines.isEmpty()) {
                                    Text(
                                        text = SimpMusicLyricsRepository.unescapeLyricsHtml(
                                            lyrics?.plainLyrics?.take(180)?.ifBlank { "Tap to view lyrics" } ?: "Tap to view lyrics"
                                        ),
                                        fontSize = 16.sp,
                                        lineHeight = 24.sp,
                                        color = Color.White.copy(alpha = 0.85f),
                                        fontWeight = FontWeight.SemiBold
                                    )
                                } else {
                                    val current = lines.getOrNull(activeIdx)
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
                                                        fontSize = 18.sp,
                                                        fontWeight = FontWeight.Bold,
                                                        color = if (wordReached) Color.White else Color.White.copy(alpha = 0.40f),
                                                        lineHeight = 26.sp
                                                    )
                                                }
                                            }
                                        } else {
                                            Text(
                                                text = SimpMusicLyricsRepository.unescapeLyricsHtml(current.text),
                                                fontSize = 18.sp,
                                                fontWeight = FontWeight.Bold,
                                                color = Color.White,
                                                lineHeight = 26.sp
                                            )
                                        }
                                        if (showRomanized && !current.romanizedText.isNullOrBlank()) {
                                            Spacer(modifier = Modifier.height(2.dp))
                                            Text(
                                                text = SimpMusicLyricsRepository.unescapeLyricsHtml(current.romanizedText),
                                                fontSize = 12.sp,
                                                color = Color.White.copy(alpha = 0.75f),
                                                lineHeight = 16.sp
                                            )
                                        }
                                    }
                                    val next1 = lines.getOrNull(activeIdx + 1)
                                    if (next1 != null) {
                                        Spacer(modifier = Modifier.height(6.dp))
                                        Text(
                                            text = SimpMusicLyricsRepository.unescapeLyricsHtml(next1.text),
                                            fontSize = 16.sp,
                                            fontWeight = FontWeight.Medium,
                                            color = Color.White.copy(alpha = 0.60f),
                                            lineHeight = 24.sp
                                        )
                                    }
                                }
                            }
                        }
                    }
                }

                // Draw the cover flight above the expanding card so it can travel from the player artwork
                // into the compact header without being clipped by the lyrics card's rounded bounds.
                val sourceArt = lyricsSourceArtBounds.takeIf { !isVideoMode }
                val headerArt = lyricsHeaderArtBounds
                // Let the sheet's advancing top edge lead the cover flight. The cover starts moving
                // shortly before the sheet reaches its lower edge, which makes the card feel like it
                // is lifting the artwork into the compact header instead of the image moving alone.
                val coverContactProgress = sourceArt?.let { art ->
                    ((startTop - art.bottom) / (startTop - compactHeaderBottomPx).coerceAtLeast(1f))
                        .coerceIn(0f, 0.62f)
                } ?: 0f
                val coverPushStartProgress = (coverContactProgress - 0.12f).coerceAtLeast(0f)
                val coverLinearProgress =
                    ((clampedProgress - coverPushStartProgress) / (1f - coverPushStartProgress))
                        .coerceIn(0f, 1f)
                // Start responsively, then decelerate into the compact header for a soft landing.
                val coverFlightProgress = coverFlightEasing.transform(coverLinearProgress)
                val sourceCornerRadius = 8f
                val coverCornerRadius = lerpFloat(sourceCornerRadius, 4f, coverFlightProgress).dp

                AnimatedVisibility(
                    visible = true,
                    modifier = Modifier.align(Alignment.TopCenter),
                    enter = fadeIn(tween(180)),
                    exit = fadeOut(tween(140)) + slideOutVertically(
                        targetOffsetY = { -it },
                        animationSpec = tween(220, easing = CubicBezierEasing(0.2f, 0f, 0.2f, 1f))
                    )
                ) {
                    FullScreenLyricsCompactHeader(
                        modifier = Modifier,
                        track = track,
                        isPlaying = isPlaying,
                        isBuffering = isBuffering,
                        showRomanized = showRomanized,
                        lyricsOffsetMs = lyricsOffsetMs,
                        onAdjustLyricsOffset = onAdjustLyricsOffset,
                        artworkExpansionProgress = if (hasCardOrigin) coverFlightProgress else clampedProgress,
                        statusBarHeightPx = statusBarHeightPx,
                        headerColor = lerp(Color.Black, palette.primary, 0.08f),
                        onArtworkPositioned = onHeaderArtPositioned,
                        onClose = onClose,
                        onTogglePlayPause = onTogglePlayPause,
                        onToggleRomanized = onToggleRomanized
                    )
                }
                if (coverFlightProgress < 1f && sourceArt != null && headerArt != null) {
                    val sourceWidth = sourceArt.width.coerceAtLeast(1f)
                    val sourceHeight = sourceArt.height.coerceAtLeast(1f)
                    val flightX = (headerArt.left - sourceArt.left) * coverFlightProgress
                    val flightY = (headerArt.top - sourceArt.top) * coverFlightProgress
                    val flightScaleX = lerpFloat(1f, headerArt.width / sourceWidth, coverFlightProgress)
                    val flightScaleY = lerpFloat(1f, headerArt.height / sourceHeight, coverFlightProgress)
                    val headerHandoff = ((coverFlightProgress - 0.88f) / 0.12f).coerceIn(0f, 1f)
                    val flightAlpha = 1f - headerHandoff
                    AsyncImage(
                        model = track.highResArtworkUrl,
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier
                            .offset(
                                x = with(density) { sourceArt.left.toDp() },
                                y = with(density) { sourceArt.top.toDp() }
                            )
                            .size(
                                width = with(density) { sourceWidth.toDp() },
                                height = with(density) { sourceHeight.toDp() }
                            )
                            .graphicsLayer {
                                translationX = flightX
                                translationY = flightY
                                scaleX = flightScaleX
                                scaleY = flightScaleY
                                alpha = flightAlpha
                                transformOrigin = TransformOrigin(0f, 0f)
                            }
                            .clip(RoundedCornerShape(coverCornerRadius))
                            .zIndex(2f)
                    )
                }
            }
        }
    }
}

/**
 * Full Screen Song Lyrics View with track controls at bottom,
 * 100% solid, fully opaque background (no transparency).
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun FullScreenLyricsCompactHeader(
    modifier: Modifier = Modifier,
    track: AuraTrack,
    isPlaying: Boolean,
    isBuffering: Boolean,
    showRomanized: Boolean,
    lyricsOffsetMs: Int = 0,
    onAdjustLyricsOffset: ((Int) -> Unit)? = null,
    artworkExpansionProgress: Float,
    statusBarHeightPx: Int,
    headerColor: Color,
    onArtworkPositioned: (Rect) -> Unit,
    onClose: () -> Unit,
    onTogglePlayPause: () -> Unit,
    onToggleRomanized: () -> Unit
) {
    val animatedHeaderColor by animateColorAsState(
        targetValue = headerColor,
        animationSpec = tween(400),
        label = "lyricsHeaderDarkColor"
    )
    val headerArtworkAlpha = ((artworkExpansionProgress - 0.88f) / 0.12f).coerceIn(0f, 1f)
    // Keep the full player's own backdrop visible until the moving cover reaches its target.
    // Only then resolve the compact tint and controls, together, over a short fade.
    val compactContentProgress by animateFloatAsState(
        targetValue = if (artworkExpansionProgress >= 0.985f) 1f else 0f,
        animationSpec = tween(190, easing = CubicBezierEasing(0.2f, 0f, 0f, 1f)),
        label = "lyricsCompactContentReveal"
    )
    val headerHeight = with(LocalDensity.current) { statusBarHeightPx.toDp() } + 66.dp

    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(headerHeight)
            .clipToBounds()
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                // Transparent during the cover flight: the full player backdrop remains continuous.
                .background(animatedHeaderColor.copy(alpha = compactContentProgress))
                .pointerInput(Unit) {
                    detectVerticalDragGestures { _, dragAmount ->
                        if (dragAmount > 16f) onClose()
                    }
                }
        ) {
            // Status-bar area and header share the same dark palette tint.
            Spacer(
                modifier = Modifier.height(
                    with(LocalDensity.current) { statusBarHeightPx.toDp() }
                )
            )
            Spacer(modifier = Modifier.height(8.dp))

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(58.dp)
                    .padding(horizontal = 8.dp, vertical = 6.dp),
                horizontalArrangement = Arrangement.spacedBy(4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                AsyncImage(
                    model = track.highResArtworkUrl,
                    contentDescription = "Close lyrics",
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .size(46.dp)
                        .clip(RoundedCornerShape(4.dp))
                        .graphicsLayer { alpha = headerArtworkAlpha }
                        .onGloballyPositioned { onArtworkPositioned(it.boundsInRoot()) }
                        .clickable(onClick = onClose)
                )

                Column(
                    horizontalAlignment = Alignment.Start,
                    modifier = Modifier
                        .weight(1f)
                        .padding(start = 4.dp, end = 2.dp)
                        .clip(RoundedCornerShape(4.dp))
                        .graphicsLayer { alpha = compactContentProgress }
                        .clickable(onClick = onClose)
                ) {
                    Text(
                        text = track.title,
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Medium,
                        color = Color.White,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = track.artist,
                        style = MaterialTheme.typography.bodySmall,
                        color = Color.White.copy(alpha = 0.72f),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                IconButton(
                    onClick = onToggleRomanized,
                    modifier = Modifier
                        .size(36.dp)
                        .graphicsLayer { alpha = compactContentProgress }
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Translate,
                        contentDescription = if (showRomanized) "Show original lyrics" else "Show romanized lyrics",
                        tint = if (showRomanized) Color.White else Color.White.copy(alpha = 0.72f),
                        modifier = Modifier.size(19.dp)
                    )
                }

                if (onAdjustLyricsOffset != null) {
                    var showOffsetControl by remember(track.videoId) { mutableStateOf(false) }

                    AnimatedVisibility(
                        visible = showOffsetControl,
                        enter = fadeIn(tween(160, easing = LyricsEaseOutCubic)) + expandHorizontally(tween(200, easing = LyricsEaseOutCubic)),
                        exit = fadeOut(tween(140, easing = LyricsEaseInCubic)) + shrinkHorizontally(tween(180, easing = LyricsEaseInCubic))
                    ) {
                        val signedOffset = formatLyricsOffsetDisplay(lyricsOffsetMs)
                        val haptics = LocalHapticFeedback.current
                        Row(
                            modifier = Modifier
                                .clip(CircleShape)
                                .background(Color.Black.copy(alpha = 0.45f))
                                .padding(horizontal = 2.dp, vertical = 2.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(26.dp)
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
                                Icon(Icons.Rounded.Remove, contentDescription = "Delay -0.1s (Hold for -0.5s)", tint = Color.White, modifier = Modifier.size(14.dp))
                            }
                            Text(
                                text = signedOffset,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = Color.White,
                                textAlign = TextAlign.Center,
                                modifier = Modifier.widthIn(min = 40.dp)
                            )
                            Box(
                                modifier = Modifier
                                    .size(26.dp)
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
                                Icon(Icons.Rounded.Add, contentDescription = "Advance +0.1s (Hold for +0.5s)", tint = Color.White, modifier = Modifier.size(14.dp))
                            }
                            if (lyricsOffsetMs != 0) {
                                IconButton(onClick = { onAdjustLyricsOffset(-lyricsOffsetMs) }, modifier = Modifier.size(26.dp)) {
                                    Icon(Icons.Rounded.RestartAlt, contentDescription = "Reset offset", tint = Color.White.copy(alpha = 0.8f), modifier = Modifier.size(13.dp))
                                }
                            }
                        }
                    }

                    IconButton(
                        onClick = { showOffsetControl = !showOffsetControl },
                        modifier = Modifier
                            .size(36.dp)
                            .graphicsLayer { alpha = compactContentProgress }
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Timer,
                            contentDescription = "Lyrics Timing",
                            tint = if (lyricsOffsetMs != 0 || showOffsetControl) Color.White else Color.White.copy(alpha = 0.72f),
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }

                IconButton(
                    onClick = onTogglePlayPause,
                    modifier = Modifier
                        .size(42.dp)
                        .graphicsLayer { alpha = compactContentProgress }
                ) {
                    if (isBuffering && !isPlaying) {
                        CircularProgressIndicator(
                            color = Color.White,
                            strokeWidth = 2.dp,
                            modifier = Modifier.size(20.dp)
                        )
                    } else {
                        Icon(
                            imageVector = if (isPlaying) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
                            contentDescription = if (isPlaying) "Pause" else "Play",
                            tint = Color.White,
                            modifier = Modifier.size(25.dp)
                        )
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun FullScreenSongLyricsView(
    track: AuraTrack,
    lyrics: LyricsData?,
    isLyricsLoading: Boolean = false,
    positionSec: Float,
    durationSec: Float,
    bufferedSec: Float = 0f,
    isPlaying: Boolean,
    isBuffering: Boolean,
    lyricsOffsetMs: Int,
    showRomanized: Boolean,
    palette: AuraPalette,
    showPlayerControls: Boolean = true,
    lyricsExpansionProgress: Float = 1f,
    compactHeaderTopPadding: Dp = 0.dp,
    onSeekTo: (Float) -> Unit,
    onTogglePlayPause: () -> Unit,
    onSkipPrevious: () -> Unit,
    onSkipNext: () -> Unit,
    onOpenQueue: () -> Unit = {},
    onAdjustLyricsOffset: (Int) -> Unit
) {
    if (LocalLyricsStyle.current == LyricsStyle.APPLE_MUSIC) {
        val rawSeed = remember(palette.primary) {
            if (palette.primary == Color.Black) AuraViolet else palette.primary
        }
        val seedColor by animateColorAsState(
            targetValue = rawSeed,
            animationSpec = tween(500),
            label = "fullScreenLyricsSeedColor"
        )
        val displayLyrics = lyrics?.takeIf { it.source != "None" }

        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black)
        ) {
            AppleMusicArtworkBackdrop(
                artworkUrl = track.highResArtworkUrl,
                seedColor = seedColor
            )

            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .navigationBarsPadding()
            ) {
                if (compactHeaderTopPadding > 0.dp) {
                    Spacer(modifier = Modifier.height(compactHeaderTopPadding))
                }

                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                ) {
                    AppleMusicLyricsList(
                        lyricsData = displayLyrics,
                        currentPositionSec = positionSec,
                        lyricsOffsetMs = lyricsOffsetMs.toLong(),
                        isPlaying = isPlaying,
                        showRomanized = showRomanized,
                        onLineClick = onSeekTo,
                        isLoading = isLyricsLoading,
                        bottomExtraPadding = if (showPlayerControls) 16.dp else 0.dp
                    )
                }

                if (showPlayerControls) {
                    FullScreenLyricsTransportChrome(
                        positionSec = positionSec,
                        durationSec = durationSec,
                        bufferedSec = bufferedSec,
                        isPlaying = isPlaying,
                        isBuffering = isBuffering,
                        lyricsExpansionProgress = lyricsExpansionProgress,
                        onSeekTo = onSeekTo,
                        onTogglePlayPause = onTogglePlayPause,
                        onSkipPrevious = onSkipPrevious,
                        onSkipNext = onSkipNext,
                        onOpenQueue = onOpenQueue
                    )
                }
            }
        }
        return
    }

    val lines = lyrics?.syncedLines ?: emptyList()
    val effectiveSec = positionSec + (lyricsOffsetMs / 1000f)
    val activeIdx = lines.indexOfLast { effectiveSec >= it.timeSec }.coerceAtLeast(0)
    // Pre-calculate initial index on frame 1 so the active line is already placed in the center immediately
    val initialIdx = remember(track.videoId) { (activeIdx - 1).coerceAtLeast(0) }
    val listState = rememberLazyListState(initialFirstVisibleItemIndex = initialIdx)
    var hasUserBrowsedLyrics by remember(track.videoId) { mutableStateOf(false) }
    var lastFirstVisibleIndex by remember(track.videoId) { mutableStateOf(initialIdx) }
    val isUserDraggingLyrics by listState.interactionSource.collectIsDraggedAsState()
    LaunchedEffect(isUserDraggingLyrics) {
        if (isUserDraggingLyrics) hasUserBrowsedLyrics = true
    }
    LaunchedEffect(listState.firstVisibleItemIndex, activeIdx, hasUserBrowsedLyrics) {
        val currentFirstVisibleIndex = listState.firstVisibleItemIndex
        val scrolledBackToPlaybackLine = currentFirstVisibleIndex < lastFirstVisibleIndex &&
            currentFirstVisibleIndex <= activeIdx + 1
        if (hasUserBrowsedLyrics && scrolledBackToPlaybackLine) {
            hasUserBrowsedLyrics = false
        }
        lastFirstVisibleIndex = currentFirstVisibleIndex
    }

    val animatedCoverColor by animateColorAsState(
        targetValue = palette.primary,
        animationSpec = tween(400),
        label = "lyricsSolidCoverBg"
    )
    LaunchedEffect(activeIdx, hasUserBrowsedLyrics) {
        // Follow the current line while the user is in the lyrics. Once they scroll past
        // the active line toward the artist section, stop pulling the list back up.
        val firstVisible = listState.firstVisibleItemIndex
        val isNearPlaybackLine = firstVisible <= activeIdx + 2
        if (lines.isNotEmpty() && activeIdx >= 0 && !hasUserBrowsedLyrics && isNearPlaybackLine) {
            listState.animateScrollToItem((activeIdx - 1).coerceAtLeast(0))
        }
    }

    // 100% solid single color background automatically using the scanned cover art color (NO gradient)
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(animatedCoverColor)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .navigationBarsPadding()
        ) {
            // Synced Lyrics List
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
            ) {
                LazyColumn(
                    state = listState,
                    contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 112.dp, bottom = 32.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                    modifier = Modifier.fillMaxSize()
                ) {
                    if (lines.isEmpty()) {
                        item(key = "lyrics_unavailable") {
                            Text(
                                text = SimpMusicLyricsRepository.unescapeLyricsHtml(
                                    lyrics?.plainLyrics?.ifBlank { "No synchronized lyrics available for this track." }
                                        ?: "Loading lyrics…"
                                ),
                                style = MaterialTheme.typography.bodyLarge,
                                color = Color.White.copy(alpha = 0.8f),
                                lineHeight = 28.sp,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 30.dp)
                            )
                        }
                    } else {
                        itemsIndexed(lines) { idx, line ->
                            val isCurrentLine = idx == activeIdx
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(12.dp))
                                    .clickable { onSeekTo(line.timeSec) }
                                    .padding(vertical = 4.dp)
                            ) {
                                if (isCurrentLine && line.words.isNotEmpty()) {
                                    FlowRow(
                                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                                        verticalArrangement = Arrangement.spacedBy(4.dp)
                                    ) {
                                        line.words.forEach { w ->
                                            val wordReached = effectiveSec >= w.startTimeSec
                                            Text(
                                                text = SimpMusicLyricsRepository.unescapeLyricsHtml(w.word),
                                                fontSize = 24.sp,
                                                fontWeight = FontWeight.ExtraBold,
                                                color = if (wordReached) Color.White else Color.White.copy(alpha = 0.4f)
                                            )
                                        }
                                    }
                                } else {
                                    Text(
                                        text = SimpMusicLyricsRepository.unescapeLyricsHtml(line.text),
                                        fontSize = if (isCurrentLine) 24.sp else 19.sp,
                                        fontWeight = if (isCurrentLine) FontWeight.ExtraBold else FontWeight.Bold,
                                        color = if (isCurrentLine) Color.White else Color.White.copy(alpha = 0.4f)
                                    )
                                }

                                if (showRomanized && !line.romanizedText.isNullOrBlank()) {
                                    Spacer(modifier = Modifier.height(2.dp))
                                    Text(
                                        text = SimpMusicLyricsRepository.unescapeLyricsHtml(line.romanizedText),
                                        fontSize = 14.sp,
                                        color = if (isCurrentLine) Color.White.copy(alpha = 0.85f) else Color.White.copy(alpha = 0.35f)
                                    )
                                }
                            }
                        }
                    }
                }

                // Soft, palette-matched boundaries keep scrolling lyrics from appearing
                // clipped against the rounded sheet edge or the controls below.
                Box(
                    modifier = Modifier
                        .align(Alignment.TopCenter)
                        .fillMaxWidth()
                        .height(112.dp)
                        .background(
                            Brush.verticalGradient(
                                colors = listOf(animatedCoverColor, animatedCoverColor.copy(alpha = 0f))
                            )
                        )
                        .blur(10.dp)
                )
                Box(
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .fillMaxWidth()
                        .height(92.dp)
                        .background(
                            Brush.verticalGradient(
                                colors = listOf(animatedCoverColor.copy(alpha = 0f), animatedCoverColor)
                            )
                        )
                        .blur(10.dp)
                )
            }

            // Compact Track UI Controls inside Full Lyrics View with CompactPointSlider
            // 100% seamless: uses the EXACT same color as the lyrics background (no gray transparent overlay)
            // Completely hidden during shrinking back to the lyrics card so only lyrics are present!
            if (showPlayerControls) {
                FullScreenLyricsTransportChrome(
                    positionSec = positionSec,
                    durationSec = durationSec,
                    bufferedSec = bufferedSec,
                    isPlaying = isPlaying,
                    isBuffering = isBuffering,
                    lyricsExpansionProgress = lyricsExpansionProgress,
                    onSeekTo = onSeekTo,
                    onTogglePlayPause = onTogglePlayPause,
                    onSkipPrevious = onSkipPrevious,
                    onSkipNext = onSkipNext,
                    onOpenQueue = onOpenQueue
                )
            }
        }
    }
}

@Composable
private fun FullScreenLyricsTransportChrome(
    positionSec: Float,
    durationSec: Float,
    bufferedSec: Float,
    isPlaying: Boolean,
    isBuffering: Boolean,
    lyricsExpansionProgress: Float,
    onSeekTo: (Float) -> Unit,
    onTogglePlayPause: () -> Unit,
    onSkipPrevious: () -> Unit,
    onSkipNext: () -> Unit,
    onOpenQueue: () -> Unit
) {
    Surface(
        color = Color.Transparent,
        modifier = Modifier
            .fillMaxWidth()
            .graphicsLayer {
                alpha = ((lyricsExpansionProgress - 0.38f) / 0.42f).coerceIn(0f, 1f)
            }
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp, vertical = 12.dp)
        ) {
            CompactPointSlider(
                value = positionSec.coerceIn(0f, durationSec.coerceAtLeast(1f)),
                bufferedValue = bufferedSec.coerceIn(0f, durationSec.coerceAtLeast(1f)),
                onValueChange = onSeekTo,
                valueRange = 0f..durationSec.coerceAtLeast(1f),
                pointColor = Color.White,
                activeTrackColor = Color.White,
                modifier = Modifier.fillMaxWidth()
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = formatDuration(positionSec.toInt()),
                    fontFamily = AuraPoppinsFontFamily,
                    fontSize = 11.sp,
                    color = Color.White.copy(alpha = 0.8f)
                )
                Text(
                    text = formatDuration(durationSec.toInt()),
                    fontFamily = AuraPoppinsFontFamily,
                    fontSize = 11.sp,
                    color = Color.White.copy(alpha = 0.8f)
                )
            }

            Spacer(modifier = Modifier.height(6.dp))

            val playbackMode by AuraPlayerManager.playbackMode.collectAsState()

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(
                    onClick = { AuraPlayerManager.cyclePlaybackMode() },
                    modifier = Modifier.size(46.dp)
                ) {
                    val (icon, tint) = when (playbackMode) {
                        PlaybackMode.NORMAL -> Icons.Rounded.Shuffle to Color.White.copy(alpha = 0.5f)
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
                        modifier = Modifier.size(32.dp)
                    )
                }

                Box(
                    modifier = Modifier
                        .size(58.dp)
                        .clip(CircleShape)
                        .background(Color.White)
                        .clickable(onClick = onTogglePlayPause),
                    contentAlignment = Alignment.Center
                ) {
                    if (isBuffering && !isPlaying) {
                        CircularProgressIndicator(
                            color = Color.Black,
                            strokeWidth = 2.5.dp,
                            modifier = Modifier.size(24.dp)
                        )
                    } else {
                        Icon(
                            imageVector = if (isPlaying) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
                            contentDescription = if (isPlaying) "Pause" else "Play",
                            tint = Color.Black,
                            modifier = Modifier.size(32.dp)
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
                        modifier = Modifier.size(32.dp)
                    )
                }

                IconButton(
                    onClick = onOpenQueue,
                    modifier = Modifier.size(46.dp)
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Rounded.QueueMusic,
                        contentDescription = "Upcoming Queue",
                        tint = Color.White.copy(alpha = 0.85f),
                        modifier = Modifier.size(24.dp)
                    )
                }
            }
        }
    }
}
