package com.auramusic.app.ui.screens.player.applemusic

import androidx.activity.compose.BackHandler
import androidx.compose.animation.Crossfade
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.MarqueeAnimationMode
import androidx.compose.foundation.background
import androidx.compose.foundation.basicMarquee
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Audiotrack
import androidx.compose.material.icons.rounded.MoreHoriz
import androidx.compose.material.icons.rounded.Videocam
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import coil.request.CachePolicy
import coil.request.ImageRequest
import com.auramusic.app.domain.model.AuraPalette
import com.auramusic.app.domain.model.AuraTrack
import com.auramusic.app.domain.model.PlaybackMode
import com.auramusic.app.ui.theme.AuraViolet
import com.auramusic.app.ui.theme.LocalGlassBackdrop
import com.auramusic.app.ui.theme.LocalLiquidGlassEnabled
import com.auramusic.app.ui.theme.glassBackdropSource
import com.auramusic.app.ui.theme.liquidGlass
import com.auramusic.app.ui.theme.rememberGlassBackdrop
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.distinctUntilChanged

private val GRABBER_HEIGHT = 28.dp
private val TOP_BAND_STRIP = 32.dp
private const val ARTWORK_FADE_FRACTION = 0.42f
private const val ARTWORK_MAX_HEIGHT_FRACTION = 0.62f
private const val LYRICS_ABSENCE_GRACE_MS = 2_500L

/**
 * Full-screen Apple Music Now Playing experience hosting three crossfaded tabs:
 * - [AppleMusicView.MAIN]: edge-to-edge fading artwork [HorizontalPager] (or [videoOverlaySlot]),
 *   [rememberAppleMusicMesh] seamless canvas backdrop, [AppleMusicMainTitleRow],
 *   [lyricStripSlot], and [AppleMusicBottomCluster].
 * - [AppleMusicView.LYRICS]: delegated to [lyricsTabSlot] with shared [AppleMusicCompactHeader]
 *   and [AppleMusicBottomCluster].
 * - [AppleMusicView.QUEUE]: [AppleMusicQueueView] with action pills and reorderable queue.
 */
@Composable
fun AppleMusicNowPlayingView(
    track: AuraTrack,
    queue: List<AuraTrack>,
    isPlaying: Boolean,
    isBuffering: Boolean,
    positionSec: Float,
    durationSec: Float,
    palette: AuraPalette,
    lyricsAvailable: Boolean,
    isFavorite: Boolean,
    isShuffleEnabled: Boolean,
    playbackMode: PlaybackMode,
    onCollapse: () -> Unit,
    onTogglePlayPause: () -> Unit,
    onSeekTo: (Float) -> Unit,
    onSkipNext: () -> Unit,
    onSkipPrevious: () -> Unit,
    onToggleFavorite: () -> Unit,
    onToggleShuffle: () -> Unit,
    onCyclePlaybackMode: () -> Unit,
    onOpenArtist: () -> Unit,
    onOptionsClick: () -> Unit,
    onOpenComments: () -> Unit,
    onShareTrack: () -> Unit,
    onSaveToPlaylist: () -> Unit,
    onPlayQueueTrack: (AuraTrack) -> Unit,
    modifier: Modifier = Modifier,
    isVideoMode: Boolean = false,
    onToggleVideoMode: (() -> Unit)? = null,
    hideArtworkForTransition: Boolean = false,
    onArtworkPositioned: (Rect) -> Unit = {},
    onRemoveQueueItem: ((Int) -> Unit)? = null,
    onMoveQueueItem: ((fromIndex: Int, toIndex: Int) -> Unit)? = null,
    activeView: AppleMusicView? = null,
    onActiveViewChange: ((AppleMusicView) -> Unit)? = null,
    outputDeviceLabel: String? = null,
    lyricStripSlot: @Composable (onOpenLyrics: () -> Unit) -> Unit = {},
    lyricsTabSlot: @Composable (
        compactHeader: @Composable () -> Unit,
        bottomCluster: @Composable () -> Unit,
        onBackToMain: () -> Unit,
    ) -> Unit = { _, _, _ -> },
    videoOverlaySlot: @Composable () -> Unit = {},
) {
    var internalView by rememberSaveable { mutableStateOf(AppleMusicView.MAIN) }
    val currentView = activeView ?: internalView
    val selectView: (AppleMusicView) -> Unit = { next ->
        internalView = next
        onActiveViewChange?.invoke(next)
    }

    // Return to MAIN on system Back when browsing Lyrics or Queue
    BackHandler(enabled = currentView != AppleMusicView.MAIN) {
        selectView(AppleMusicView.MAIN)
    }

    // Grace period when skipping onto a track that turns out to have no lyrics
    LaunchedEffect(lyricsAvailable, currentView, track.videoId) {
        if (currentView == AppleMusicView.LYRICS && !lyricsAvailable) {
            delay(LYRICS_ABSENCE_GRACE_MS)
            selectView(AppleMusicView.MAIN)
        }
    }

    val rawSeed = remember(palette.primary) {
        if (palette.primary == Color.Black) AuraViolet else palette.primary
    }
    val seedColor by animateColorAsState(
        targetValue = rawSeed,
        animationSpec = tween(500),
        label = "appleMusicSeedColor",
    )

    val typography = rememberAppleMusicTypography()
    var artworkBitmap by remember(track.videoId) { mutableStateOf<ImageBitmap?>(null) }

    // Sibling backdrop source for Liquid Glass pills inside bottom cluster & top-right chrome
    val glassBackdrop = rememberGlassBackdrop(Color.Black)

    val sharedBottomCluster: @Composable () -> Unit = {
        AppleMusicBottomCluster(
            track = track,
            isPlaying = isPlaying,
            isBuffering = isBuffering,
            positionSec = positionSec,
            durationSec = durationSec,
            viewState = currentView,
            onSelectView = selectView,
            seedColor = seedColor,
            lyricsAvailable = lyricsAvailable,
            onSeekTo = onSeekTo,
            onTogglePlayPause = onTogglePlayPause,
            onSkipPrevious = onSkipPrevious,
            onSkipNext = onSkipNext,
            onOpenComments = onOpenComments,
            onOpenOptions = onOptionsClick,
            onShareTrack = onShareTrack,
            typography = typography,
            outputDeviceLabel = outputDeviceLabel,
        )
    }

    val sharedCompactHeader: @Composable () -> Unit = {
        AppleMusicCompactHeader(
            track = track,
            onTapTrack = { selectView(AppleMusicView.MAIN) },
            onTapArtist = onOpenArtist,
            typography = typography,
            trailingActions = {
                AppleMusicHeaderActions(
                    isFavorite = isFavorite,
                    onToggleFavorite = onToggleFavorite,
                    onOptionsClick = onOptionsClick,
                    onAddToPlaylist = onSaveToPlaylist,
                )
            },
        )
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Color.Black),
    ) {
        // 1. Sibling frosted backdrop source (outside Crossfade so LYRICS & QUEUE share it)
        Box(
            modifier = Modifier
                .matchParentSize()
                .glassBackdropSource(glassBackdrop),
        ) {
            AppleMusicArtworkBackdrop(
                artworkUrl = track.highResArtworkUrl,
                seedColor = seedColor,
                onArtworkBitmap = { bmp -> artworkBitmap = bmp },
            )
        }

        // 2. Foreground tabs provided with glassBackdrop
        CompositionLocalProvider(LocalGlassBackdrop provides glassBackdrop) {
            Crossfade(
                targetState = currentView,
                animationSpec = tween(300),
                label = "appleMusicViewCrossfade",
            ) { view ->
                when (view) {
                    AppleMusicView.MAIN -> {
                        AppleMusicMainView(
                            track = track,
                            queue = queue,
                            isPlaying = isPlaying,
                            isBuffering = isBuffering,
                            positionSec = positionSec,
                            durationSec = durationSec,
                            seedColor = seedColor,
                            artworkBitmap = artworkBitmap,
                            onArtworkBitmap = { artworkBitmap = it },
                            lyricsAvailable = lyricsAvailable,
                            isFavorite = isFavorite,
                            playbackMode = playbackMode,
                            isVideoMode = isVideoMode,
                            hideArtworkForTransition = hideArtworkForTransition,
                            onArtworkPositioned = onArtworkPositioned,
                            onSelectView = selectView,
                            onTogglePlayPause = onTogglePlayPause,
                            onSeekTo = onSeekTo,
                            onSkipNext = onSkipNext,
                            onSkipPrevious = onSkipPrevious,
                            onToggleFavorite = onToggleFavorite,
                            onOpenArtist = onOpenArtist,
                            onOptionsClick = onOptionsClick,
                            onOpenComments = onOpenComments,
                            onShareTrack = onShareTrack,
                            onSaveToPlaylist = onSaveToPlaylist,
                            onPlayQueueTrack = onPlayQueueTrack,
                            typography = typography,
                            outputDeviceLabel = outputDeviceLabel,
                            lyricStripSlot = lyricStripSlot,
                            videoOverlaySlot = videoOverlaySlot,
                        )
                    }

                    AppleMusicView.LYRICS -> {
                        Column(modifier = Modifier.fillMaxSize()) {
                            Spacer(
                                modifier = Modifier
                                    .statusBarsPadding()
                                    .height(24.dp),
                            )
                            Box(modifier = Modifier.weight(1f)) {
                                lyricsTabSlot(
                                    sharedCompactHeader,
                                    sharedBottomCluster,
                                    { selectView(AppleMusicView.MAIN) },
                                )
                            }
                        }
                    }

                    AppleMusicView.QUEUE -> {
                        AppleMusicQueueView(
                            currentTrack = track,
                            queue = queue,
                            isPlaying = isPlaying,
                            isFavorite = isFavorite,
                            isShuffleEnabled = isShuffleEnabled,
                            playbackMode = playbackMode,
                            seedColor = seedColor,
                            onTapHeaderTrack = { selectView(AppleMusicView.MAIN) },
                            onTapArtist = onOpenArtist,
                            onToggleFavorite = onToggleFavorite,
                            onOptionsClick = onOptionsClick,
                            onToggleShuffle = onToggleShuffle,
                            onCyclePlaybackMode = onCyclePlaybackMode,
                            onSaveToPlaylist = onSaveToPlaylist,
                            onPlayQueueTrack = onPlayQueueTrack,
                            typography = typography,
                            onRemoveQueueItem = onRemoveQueueItem,
                            onMoveQueueItem = onMoveQueueItem,
                            bottomCluster = sharedBottomCluster,
                        )
                    }
                }
            }

            // 3. Top floating grabber (persistent across all 3 views)
            Box(
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .statusBarsPadding()
                    .size(width = 64.dp, height = GRABBER_HEIGHT)
                    .semantics {
                        contentDescription = "Collapse player"
                        role = Role.Button
                    }
                    .clickable(
                        indication = null,
                        interactionSource = remember { MutableInteractionSource() },
                        onClick = onCollapse,
                    ),
                contentAlignment = Alignment.Center,
            ) {
                Box(
                    modifier = Modifier
                        .size(width = 36.dp, height = 5.dp)
                        .clip(RoundedCornerShape(50))
                        .background(Color.White.copy(alpha = 0.40f)),
                )
            }

            // 4. Optional top-right video/options pill on MAIN
            if (currentView == AppleMusicView.MAIN && (onToggleVideoMode != null || isVideoMode)) {
                val useGlass = LocalLiquidGlassEnabled.current
                val pillModifier = if (useGlass) {
                    Modifier.liquidGlass(
                        backdrop = glassBackdrop,
                        shape = CircleShape,
                        interactive = true,
                        fallbackColor = Color.White.copy(alpha = 0.16f),
                    )
                } else {
                    Modifier
                        .clip(CircleShape)
                        .background(Color.White.copy(alpha = 0.16f))
                }
                Row(
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .statusBarsPadding()
                        .padding(top = 6.dp, end = 16.dp)
                        .then(pillModifier)
                        .padding(horizontal = 10.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    if (onToggleVideoMode != null) {
                        Icon(
                            imageVector = if (isVideoMode) Icons.Rounded.Audiotrack else Icons.Rounded.Videocam,
                            contentDescription = if (isVideoMode) "Switch to audio mode" else "Switch to video mode",
                            tint = Color.White,
                            modifier = Modifier
                                .size(18.dp)
                                .clickable(onClick = onToggleVideoMode),
                        )
                    }
                    Icon(
                        imageVector = Icons.Rounded.MoreHoriz,
                        contentDescription = "More options",
                        tint = Color.White,
                        modifier = Modifier
                            .size(18.dp)
                            .clickable(onClick = onOptionsClick),
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun AppleMusicMainView(
    track: AuraTrack,
    queue: List<AuraTrack>,
    isPlaying: Boolean,
    isBuffering: Boolean,
    positionSec: Float,
    durationSec: Float,
    seedColor: Color,
    artworkBitmap: ImageBitmap?,
    onArtworkBitmap: (ImageBitmap) -> Unit,
    lyricsAvailable: Boolean,
    isFavorite: Boolean,
    playbackMode: PlaybackMode,
    isVideoMode: Boolean,
    hideArtworkForTransition: Boolean,
    onArtworkPositioned: (Rect) -> Unit,
    onSelectView: (AppleMusicView) -> Unit,
    onTogglePlayPause: () -> Unit,
    onSeekTo: (Float) -> Unit,
    onSkipNext: () -> Unit,
    onSkipPrevious: () -> Unit,
    onToggleFavorite: () -> Unit,
    onOpenArtist: () -> Unit,
    onOptionsClick: () -> Unit,
    onOpenComments: () -> Unit,
    onShareTrack: () -> Unit,
    onSaveToPlaylist: () -> Unit,
    onPlayQueueTrack: (AuraTrack) -> Unit,
    typography: AppleMusicTypography,
    outputDeviceLabel: String?,
    lyricStripSlot: @Composable (onOpenLyrics: () -> Unit) -> Unit,
    videoOverlaySlot: @Composable () -> Unit,
) {
    val density = LocalDensity.current
    val context = LocalContext.current
    val statusBarHeight = with(density) { WindowInsets.statusBars.getTop(density).toDp() }
    val isRepeatOne = playbackMode == PlaybackMode.REPEAT_ONE

    val pagerQueue = remember(queue, track) {
        if (queue.isNotEmpty()) queue else listOf(track)
    }
    val currentIndex = remember(pagerQueue, track.videoId, track.id) {
        pagerQueue.indexOfFirst { it.videoId == track.videoId && it.id == track.id }
            .takeIf { it >= 0 }
            ?: pagerQueue.indexOfFirst { it.videoId == track.videoId }.coerceAtLeast(0)
    }
    val pagerState = rememberPagerState(initialPage = currentIndex) { pagerQueue.size }
    var programmaticTargetPage by remember { mutableIntStateOf(-1) }

    LaunchedEffect(currentIndex, pagerQueue.size) {
        if (pagerState.currentPage != currentIndex && currentIndex in pagerQueue.indices) {
            programmaticTargetPage = currentIndex
            pagerState.animateScrollToPage(currentIndex)
            programmaticTargetPage = -1
        }
    }

    LaunchedEffect(pagerState, currentIndex, pagerQueue) {
        snapshotFlow { pagerState.settledPage }
            .distinctUntilChanged()
            .collect { settled ->
                if (settled != currentIndex && settled != programmaticTargetPage && settled in pagerQueue.indices) {
                    onPlayQueueTrack(pagerQueue[settled])
                }
            }
    }

    val artworkPlayScale by animateFloatAsState(
        targetValue = if (isPlaying) 1f else 0.94f,
        animationSpec = spring(dampingRatio = 0.72f, stiffness = 260f),
        label = "appleMusicArtworkPlayScale",
    )

    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        val artworkHeight = minOf(maxWidth, maxHeight * ARTWORK_MAX_HEIGHT_FRACTION)
        val artworkFade = artworkHeight * ARTWORK_FADE_FRACTION
        val topInset = statusBarHeight
        val topFade = topInset
        val meshSeam = topInset + artworkHeight
        val topHold = if (topInset > 0.dp) {
            topInset + topFade + ((artworkHeight - topFade - artworkFade) / 2).coerceAtLeast(0.dp)
        } else {
            0.dp
        }
        val topBand = statusBarHeight + TOP_BAND_STRIP

        val mesh = if (!isVideoMode) {
            rememberAppleMusicMesh(
                artwork = artworkBitmap,
                seed = track.highResArtworkUrl.hashCode(),
            )
        } else {
            null
        }

        if (!isVideoMode) {
            AppleMusicMeshBackdrop(
                mesh = mesh,
                seam = meshSeam,
                topHold = topHold,
            )
        }

        if (isVideoMode) {
            Box(
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .padding(top = statusBarHeight + GRABBER_HEIGHT + 12.dp)
                    .fillMaxWidth()
                    .height(artworkHeight)
                    .onGloballyPositioned { onArtworkPositioned(it.boundsInRoot()) },
                contentAlignment = Alignment.Center,
            ) {
                videoOverlaySlot()
            }
        } else {
            HorizontalPager(
                state = pagerState,
                modifier = Modifier.fillMaxSize(),
                beyondViewportPageCount = 1,
                userScrollEnabled = !isRepeatOne && pagerQueue.size > 1,
                key = { idx ->
                    val item = pagerQueue.getOrNull(idx)
                    "am_art_${item?.videoId.orEmpty()}_$idx"
                },
            ) { page ->
                val pageTrack = pagerQueue.getOrNull(page) ?: track
                val isCurrentPage = page == currentIndex
                var pageUrl by remember(pageTrack.highResArtworkUrl) {
                    mutableStateOf(pageTrack.highResArtworkUrl)
                }

                Box(modifier = Modifier.fillMaxSize()) {
                    Box(
                        modifier = Modifier
                            .align(Alignment.TopCenter)
                            .padding(top = topInset)
                            .fillMaxWidth()
                            .height(artworkHeight)
                            .graphicsLayer {
                                if (isCurrentPage) {
                                    scaleX = artworkPlayScale
                                    scaleY = artworkPlayScale
                                    alpha = if (hideArtworkForTransition) 0f else 1f
                                }
                            }
                            .then(
                                if (isCurrentPage) {
                                    Modifier.onGloballyPositioned { onArtworkPositioned(it.boundsInRoot()) }
                                } else {
                                    Modifier
                                },
                            ),
                    ) {
                        AsyncImage(
                            model = ImageRequest.Builder(context)
                                .data(pageUrl)
                                .allowHardware(false)
                                .diskCachePolicy(CachePolicy.ENABLED)
                                .crossfade(550)
                                .build(),
                            contentDescription = pageTrack.title,
                            contentScale = ContentScale.Crop,
                            onSuccess = { success ->
                                if (isCurrentPage) {
                                    drawableToSoftwareImageBitmap(success.result.drawable)?.let { bmp ->
                                        onArtworkBitmap(bmp)
                                    }
                                }
                            },
                            onError = {
                                val fallback = pageUrl.replace("maxresdefault", "hqdefault")
                                if (fallback != pageUrl) pageUrl = fallback
                            },
                            modifier = Modifier
                                .fillMaxSize()
                                .appleMusicVerticalFadeEdges(
                                    topFadeHeight = topFade,
                                    bottomFadeHeight = artworkFade,
                                ),
                        )
                    }
                }
            }
        }

        // Status bar protection wash adapting to the cover's top band luminance
        AppleMusicTopStatusWash(
            topBandHeight = topBand,
            topLuminance = mesh?.topLuminance ?: 0.5f,
            modifier = Modifier.align(Alignment.TopCenter),
        )

        // Bottom-anchored title, lyric strip, and playback cluster
        Column(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth(),
        ) {
            AppleMusicMainTitleRow(
                track = track,
                isFavorite = isFavorite,
                onToggleFavorite = onToggleFavorite,
                onOpenArtist = onOpenArtist,
                onOptionsClick = onOptionsClick,
                onAddToPlaylist = onSaveToPlaylist,
                typography = typography,
            )

            Spacer(modifier = Modifier.height(20.dp))

            lyricStripSlot { onSelectView(AppleMusicView.LYRICS) }

            AppleMusicBottomCluster(
                track = track,
                isPlaying = isPlaying,
                isBuffering = isBuffering,
                positionSec = positionSec,
                durationSec = durationSec,
                viewState = AppleMusicView.MAIN,
                onSelectView = onSelectView,
                seedColor = seedColor,
                lyricsAvailable = lyricsAvailable,
                onSeekTo = onSeekTo,
                onTogglePlayPause = onTogglePlayPause,
                onSkipPrevious = onSkipPrevious,
                onSkipNext = onSkipNext,
                onOpenComments = onOpenComments,
                onOpenOptions = onOptionsClick,
                onShareTrack = onShareTrack,
                typography = typography,
                topPadding = 2.dp,
                outputDeviceLabel = outputDeviceLabel,
            )
        }
    }
}

/**
 * Main Now Playing track title & artist row with marquee overflow, [ExplicitBadge],
 * clickable artist link, celebratory heart-burst favorite toggle, and options trigger.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun AppleMusicMainTitleRow(
    track: AuraTrack,
    isFavorite: Boolean,
    onToggleFavorite: () -> Unit,
    onOpenArtist: () -> Unit,
    onOptionsClick: () -> Unit,
    modifier: Modifier = Modifier,
    onAddToPlaylist: (() -> Unit)? = null,
    typography: AppleMusicTypography = rememberAppleMusicTypography(),
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = track.title,
                style = typography.mainTitle,
                maxLines = 1,
                modifier = Modifier
                    .fillMaxWidth()
                    .basicMarquee(
                        iterations = Int.MAX_VALUE,
                        animationMode = MarqueeAnimationMode.Immediately,
                    )
                    .focusable(),
            )
            Spacer(modifier = Modifier.height(4.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (track.isExplicit) {
                    ExplicitBadge(
                        modifier = Modifier
                            .size(18.dp)
                            .padding(end = 4.dp),
                    )
                }
                Text(
                    text = track.artist,
                    style = typography.mainArtist,
                    maxLines = 1,
                    modifier = Modifier
                        .basicMarquee(
                            iterations = Int.MAX_VALUE,
                            animationMode = MarqueeAnimationMode.Immediately,
                        )
                        .focusable()
                        .clickable(onClick = onOpenArtist),
                )
            }
        }
        Spacer(modifier = Modifier.width(12.dp))
        AppleMusicHeaderActions(
            isFavorite = isFavorite,
            onToggleFavorite = onToggleFavorite,
            onOptionsClick = onOptionsClick,
            onAddToPlaylist = onAddToPlaylist,
        )
    }
}
