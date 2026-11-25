package com.auramusic.app.ui.screens

import android.content.Intent
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.unit.dp
import com.auramusic.app.core.media.AuraPlayerManager
import com.auramusic.app.domain.model.ArtistDetail
import com.auramusic.app.domain.model.AuraCommentSort
import com.auramusic.app.domain.model.AuraCommentsPage
import com.auramusic.app.domain.model.AuraPalette
import com.auramusic.app.domain.model.AuraPlaylist
import com.auramusic.app.domain.model.AuraTrack
import com.auramusic.app.domain.model.AuraVideoInfo
import com.auramusic.app.domain.model.LyricsData
import com.auramusic.app.domain.model.NowPlayingStyle
import com.auramusic.app.ui.components.AuraAddToPlaylistModal
import com.auramusic.app.ui.components.AuraCommentsBottomSheet
import com.auramusic.app.ui.components.AuraCreatePlaylistModal
import com.auramusic.app.ui.screens.player.applemusic.AppleMusicArtworkBackdrop
import com.auramusic.app.ui.screens.player.applemusic.AppleMusicLyricStrip
import com.auramusic.app.ui.screens.player.applemusic.AppleMusicLyricsTabView
import com.auramusic.app.ui.screens.player.applemusic.AppleMusicNowPlayingView
import com.auramusic.app.ui.screens.player.applemusic.AppleMusicView
import com.auramusic.app.ui.screens.player.spotify.LivePerformancesModal
import com.auramusic.app.ui.screens.player.spotify.NowPlayingOptionsModal
import com.auramusic.app.ui.screens.player.spotify.SpotifyEncorePlayerView
import com.auramusic.app.ui.screens.player.spotify.SpotifyLyricsOverlay
import com.auramusic.app.ui.screens.player.spotify.SpotifyQueueSheet
import com.auramusic.app.ui.screens.player.spotify.SpotifyVideoPlayerSurface
import com.auramusic.app.ui.theme.AuraViolet
import com.auramusic.app.ui.theme.LocalLiquidGlassEnabled

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun AuraNowPlayingSheet(
    track: AuraTrack,
    isPlaying: Boolean,
    isBuffering: Boolean,
    positionSec: Float,
    durationSec: Float,
    bufferedSec: Float = 0f,
    palette: AuraPalette,
    lyrics: LyricsData?,
    lyricsOffsetMs: Int,
    showRomanized: Boolean,
    isFavorite: Boolean,
    isShuffleEnabled: Boolean,
    queue: List<AuraTrack>,
    currentStyle: NowPlayingStyle,
    isVideoMode: Boolean = false,
    isVideoLoading: Boolean = false,
    livePerformances: List<AuraTrack> = emptyList(),
    isLoadingLivePerformances: Boolean = false,
    artistDetail: ArtistDetail? = null,
    videoInfo: AuraVideoInfo? = null,
    isArtistDetailLoading: Boolean = false,
    onLoadArtistDetail: (String, String, String) -> Unit = { _, _, _ -> },
    onCollapse: () -> Unit,
    hideArtworkForPlayerTransition: Boolean = false,
    playerCollapseProgress: Float = 0f,
    onArtworkPositioned: (Rect) -> Unit = {},
    onSheetDragOffsetChange: (Float) -> Unit = {},
    onTogglePlayPause: () -> Unit,
    onSeekTo: (Float) -> Unit,
    onSkipNext: () -> Unit,
    onSkipPrevious: () -> Unit,
    onToggleFavorite: () -> Unit,
    onToggleShuffle: () -> Unit,
    onToggleStyle: () -> Unit,
    onToggleVideoMode: () -> Unit = {},
    onLoadLivePerformances: () -> Unit = {},
    onPlayLivePerformance: (AuraTrack) -> Unit = {},
    onOpenArtist: (String) -> Unit = {},
    onOpenCurrentPlaylist: () -> Unit = {},
    onPlayQueueTrack: (AuraTrack) -> Unit,
    onAdjustLyricsOffset: (Int) -> Unit,
    onToggleRomanized: () -> Unit,
    mainLyricsProvider: String = "SimpMusic",
    onSwitchLyricsProvider: (String) -> Unit = {},
    isDownloaded: Boolean = false,
    isDownloading: Boolean = false,
    onDownloadTrack: ((AuraTrack) -> Unit)? = null,
    onRemoveDownload: ((AuraTrack) -> Unit)? = null,
    playlists: List<AuraPlaylist> = emptyList(),
    onAddTrackToPlaylist: (String, AuraTrack) -> Unit = { _, _ -> },
    onCreatePlaylist: (String, String, String) -> Unit = { _, _, _ -> },
    comments: AuraCommentsPage? = null,
    isLoadingComments: Boolean = false,
    commentsError: String? = null,
    onLoadComments: (String) -> Unit = {},
    onLoadMoreComments: () -> Unit = {},
    onSelectCommentSort: (AuraCommentSort) -> Unit = {},
    onToggleCommentReplies: (String) -> Unit = {},
    onLoadMoreCommentReplies: (String) -> Unit = {}
) {
    var isLyricsExpanded by remember { mutableStateOf(false) }
    var lyricsCardBounds by remember { mutableStateOf<Rect?>(null) }
    var albumArtBounds by remember { mutableStateOf<Rect?>(null) }
    var lyricsSourceArtBounds by remember { mutableStateOf<Rect?>(null) }
    var lyricsHeaderArtBounds by remember { mutableStateOf<Rect?>(null) }
    var isQueueSheetVisible by remember { mutableStateOf(false) }
    var isOptionsModalVisible by remember { mutableStateOf(false) }
    var isLivePerformancesModalVisible by remember { mutableStateOf(false) }
    var isAddToPlaylistModalVisible by remember { mutableStateOf(false) }
    var isCreatePlaylistModalVisible by remember { mutableStateOf(false) }
    var isCommentsVisible by remember { mutableStateOf(false) }
    var appleMusicActiveView by remember { mutableStateOf(AppleMusicView.MAIN) }
    val playbackMode by AuraPlayerManager.playbackMode.collectAsState()

    val canonicalVideoId = AuraPlayerManager.originalAudioTrack?.videoId ?: track.videoId
    val activeLyrics = lyrics?.takeIf {
        it.videoId == canonicalVideoId || it.videoId == track.videoId
    }
    val isLyricsLoading = activeLyrics == null
    val displayLyrics = activeLyrics?.takeIf { it.source != "None" }
    val lyricsAvailable = displayLyrics != null && (
        displayLyrics.syncedLines.isNotEmpty() || displayLyrics.plainLyrics.isNotBlank()
    )

    val context = LocalContext.current
    LaunchedEffect(track.videoId, track.artist) {
        onLoadArtistDetail(track.artist, track.title, track.highResArtworkUrl)
    }
    val openLyrics = {
        // Freeze the source before the player begins scaling under the lyrics sheet.
        lyricsSourceArtBounds = if (
            currentStyle == NowPlayingStyle.SPOTIFY ||
            appleMusicActiveView == AppleMusicView.MAIN
        ) {
            albumArtBounds
        } else {
            null
        }
        isLyricsExpanded = true
    }
    val shareTrack: () -> Unit = {
        val shareUrl = "https://music.youtube.com/watch?v=${track.videoId}"
        val shareText = if (track.artist.isNotBlank()) {
            "${track.title} — ${track.artist}\n$shareUrl"
        } else {
            "${track.title}\n$shareUrl"
        }
        val sendIntent = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_SUBJECT, track.title)
            putExtra(Intent.EXTRA_TEXT, shareText)
        }
        context.startActivity(Intent.createChooser(sendIntent, "Share track"))
    }
    val statusBarHeightPx = remember(context) {
        val resourceId = context.resources.getIdentifier("status_bar_height", "dimen", "android")
        if (resourceId != 0) context.resources.getDimensionPixelSize(resourceId) else 0
    }
    val focusManager = LocalFocusManager.current
    val keyboardController = LocalSoftwareKeyboardController.current

    // Always dismiss any lingering search input focus / soft keyboard when opening player or its modals
    LaunchedEffect(Unit, isQueueSheetVisible, isOptionsModalVisible, isLyricsExpanded, isLivePerformancesModalVisible, isAddToPlaylistModalVisible, isCreatePlaylistModalVisible, isCommentsVisible) {
        focusManager.clearFocus(force = true)
        keyboardController?.hide()
    }

    // Intercept back gesture / button to smoothly close open sheets / modals
    BackHandler(enabled = isLyricsExpanded || isLivePerformancesModalVisible || isOptionsModalVisible || isQueueSheetVisible || isAddToPlaylistModalVisible || isCreatePlaylistModalVisible || isCommentsVisible) {
        when {
            isCommentsVisible -> isCommentsVisible = false
            isCreatePlaylistModalVisible -> isCreatePlaylistModalVisible = false
            isAddToPlaylistModalVisible -> isAddToPlaylistModalVisible = false
            isLivePerformancesModalVisible -> isLivePerformancesModalVisible = false
            isOptionsModalVisible -> isOptionsModalVisible = false
            isQueueSheetVisible -> isQueueSheetVisible = false
            isLyricsExpanded -> isLyricsExpanded = false
            else -> onCollapse()
        }
    }

    // Root container matching sheet bounds - 100% solid background so underlying screens never bleed through.
    // Apple Music (LIQUID_GLASS) uses its own artwork-sampled backdrop color + live blurred artwork during
    // collapse, completely separate from Spotify Encore / Default's palette.
    val appleMusicSeed = remember(palette.appleMusicBackdrop) {
        if (palette.appleMusicBackdrop == Color.Black) AuraViolet else palette.appleMusicBackdrop
    }
    val collapseTargetBg = if (currentStyle == NowPlayingStyle.LIQUID_GLASS) {
        appleMusicSeed
    } else {
        palette.primary
    }
    val baseSheetBg = if (currentStyle == NowPlayingStyle.LIQUID_GLASS) {
        appleMusicSeed
    } else {
        Color(0xFF14151B)
    }
    BoxWithConstraints(
        modifier = Modifier
            .fillMaxSize()
            .background(
                lerp(
                    baseSheetBg,
                    collapseTargetBg,
                    playerCollapseProgress.coerceIn(0f, 1f)
                )
            )
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = {} // Intercepts all clicks so background screens (search field, home items) are never touched
            )
    ) {
        if (currentStyle == NowPlayingStyle.LIQUID_GLASS && playerCollapseProgress > 0.001f) {
            AppleMusicArtworkBackdrop(
                artworkUrl = track.highResArtworkUrl,
                seedColor = appleMusicSeed
            )
        }
        val fullWidthPx = constraints.maxWidth.toFloat()
        val fullHeightPx = constraints.maxHeight.toFloat()

        // Lyrics expansion progress: 0f = collapsed on card, 1f = fully expanded
        val lyricsTransitionProgress by animateFloatAsState(
            targetValue = if (isLyricsExpanded) 1f else 0f,
            animationSpec = tween(
                durationMillis = 660,
                easing = CubicBezierEasing(0.4f, 0f, 0.2f, 1f)
            ),
            label = "lyricsTransitionProgress"
        )
        // The lyrics sheet, background treatment, and artwork flight share one progress value.
        // This keeps the player feeling like one full-screen surface instead of revealing a
        // separate toolbar before the sheet and artwork begin to move.
        val lyricsExpansionProgress = lyricsTransitionProgress.coerceIn(0f, 1f)
        // Material-style emphasized easing gives the thumbnail a longer, calmer landing.
        val coverFlightEasing = remember { CubicBezierEasing(0.4f, 0f, 0.2f, 1f) }
        val playerBackdropBlur by animateDpAsState(
            targetValue = if (isLyricsExpanded || lyricsExpansionProgress > 0.12f) 18.dp else 0.dp,
            animationSpec = tween(
                durationMillis = 275,
                easing = CubicBezierEasing(0.4f, 0f, 0.2f, 1f)
            ),
            label = "lyricsBackdropBlur"
        )

        // Keep the player under the lyrics transition soft so its preview line and header colors
        // cannot bleed through the moving card edges.
        Box(
            modifier = Modifier
                .fillMaxSize()
                .blur(playerBackdropBlur)
                .graphicsLayer {
                    val scale = 0.94f + (0.06f * (1f - lyricsExpansionProgress))
                    scaleX = scale
                    scaleY = scale
                    // Keep the player surface solid while its artwork flies to the mini
                    // player. Curtain away the full-size controls before the mini controls
                    // resolve, so the two layouts never appear as translucent duplicates.
                    val fadeCutoff = if (currentStyle == NowPlayingStyle.LIQUID_GLASS) 0.78f else 0.58f
                    alpha = 1f - (playerCollapseProgress.coerceIn(0f, 1f) / fadeCutoff).coerceIn(0f, 1f)
                }
        ) {
            Crossfade(
                targetState = currentStyle,
                label = "NowPlayingStyleCrossfade",
                animationSpec = tween(350)
            ) { style ->
                when (style) {
                    NowPlayingStyle.SPOTIFY -> {
                        SpotifyEncorePlayerView(
                            track = track,
                            queue = queue,
                            isPlaying = isPlaying,
                            isBuffering = isBuffering,
                            positionSec = positionSec,
                            durationSec = durationSec,
                            bufferedSec = bufferedSec,
                            palette = palette,
                            lyrics = lyrics,
                            lyricsOffsetMs = lyricsOffsetMs,
                            showRomanized = showRomanized,
                            isFavorite = isFavorite,
                            isShuffleEnabled = isShuffleEnabled,
                            artistDetail = artistDetail,
                            videoInfo = videoInfo,
                            isArtistDetailLoading = isArtistDetailLoading,
                            lyricsExpansionProgress = lyricsExpansionProgress,
                            hideArtworkForLyricsTransition = !isVideoMode &&
                                ((lyricsExpansionProgress > 0.001f && lyricsHeaderArtBounds != null) ||
                                    hideArtworkForPlayerTransition),
                            isVideoMode = isVideoMode,
                            isVideoLoading = isVideoLoading,
                            onCollapse = onCollapse,
                            onTogglePlayPause = onTogglePlayPause,
                            onSeekTo = onSeekTo,
                            onSkipNext = onSkipNext,
                            onSkipPrevious = onSkipPrevious,
                            onToggleFavorite = onToggleFavorite,
                            onToggleShuffle = onToggleShuffle,
                            onToggleVideoMode = onToggleVideoMode,
                            onOpenArtist = { onOpenArtist(track.artist) },
                            onOpenCurrentPlaylist = onOpenCurrentPlaylist,
                            onOptionsClick = { isOptionsModalVisible = true },
                            onOpenLyrics = openLyrics,
                            onAdjustLyricsOffset = onAdjustLyricsOffset,
                            onOpenComments = { isCommentsVisible = true; onLoadComments(track.videoId) },
                            onOpenQueue = { isQueueSheetVisible = true },
                            onCardPositioned = { lyricsCardBounds = it },
                            onArtworkPositioned = {
                                albumArtBounds = it
                                onArtworkPositioned(it)
                            }
                        )
                    }
                    NowPlayingStyle.LIQUID_GLASS -> {
                        AppleMusicNowPlayingView(
                            track = track,
                            queue = queue,
                            isPlaying = isPlaying,
                            isBuffering = isBuffering,
                            positionSec = positionSec,
                            durationSec = durationSec,
                            palette = palette,
                            lyricsAvailable = lyricsAvailable,
                            isFavorite = isFavorite,
                            isShuffleEnabled = isShuffleEnabled,
                            playbackMode = playbackMode,
                            onCollapse = onCollapse,
                            onTogglePlayPause = onTogglePlayPause,
                            onSeekTo = onSeekTo,
                            onSkipNext = onSkipNext,
                            onSkipPrevious = onSkipPrevious,
                            onToggleFavorite = onToggleFavorite,
                            onToggleShuffle = onToggleShuffle,
                            onCyclePlaybackMode = { AuraPlayerManager.cyclePlaybackMode() },
                            onOpenArtist = { onOpenArtist(track.artist) },
                            onOptionsClick = { isOptionsModalVisible = true },
                            onOpenComments = {
                                isCommentsVisible = true
                                onLoadComments(track.videoId)
                            },
                            onShareTrack = shareTrack,
                            onSaveToPlaylist = { isAddToPlaylistModalVisible = true },
                            onPlayQueueTrack = onPlayQueueTrack,
                            isVideoMode = isVideoMode,
                            onToggleVideoMode = onToggleVideoMode,
                            hideArtworkForTransition = !isVideoMode &&
                                ((lyricsExpansionProgress > 0.001f && lyricsHeaderArtBounds != null) ||
                                    hideArtworkForPlayerTransition),
                            onArtworkPositioned = {
                                albumArtBounds = it
                                onArtworkPositioned(it)
                            },
                            onRemoveQueueItem = { index -> AuraPlayerManager.removeQueueItem(index) },
                            onMoveQueueItem = { fromIndex, toIndex ->
                                AuraPlayerManager.moveQueueItem(fromIndex, toIndex)
                            },
                            activeView = appleMusicActiveView,
                            onActiveViewChange = { appleMusicActiveView = it },
                            onSheetDragOffsetChange = onSheetDragOffsetChange,
                            lyricStripSlot = { onOpenLyricsTab ->
                                AppleMusicLyricStrip(
                                    lines = displayLyrics?.syncedLines.orEmpty(),
                                    currentPositionSec = positionSec,
                                    lyricsOffsetMs = lyricsOffsetMs.toLong(),
                                    isPlaying = isPlaying,
                                    showRomanized = showRomanized,
                                    onClick = onOpenLyricsTab
                                )
                            },
                            lyricsTabSlot = { compactHeader, bottomCluster, onBackToMain ->
                                AppleMusicLyricsTabView(
                                    track = track,
                                    lyricsData = displayLyrics,
                                    isLoading = isLyricsLoading,
                                    currentPositionSec = positionSec,
                                    isPlaying = isPlaying,
                                    isBuffering = isBuffering,
                                    lyricsOffsetMs = lyricsOffsetMs.toLong(),
                                    showRomanized = showRomanized,
                                    onSeekToSec = onSeekTo,
                                    onAdjustOffsetMs = onAdjustLyricsOffset,
                                    onToggleRomanized = onToggleRomanized,
                                    onTogglePlayPause = onTogglePlayPause,
                                    onBackToMain = onBackToMain,
                                    compactHeader = compactHeader,
                                    bottomCluster = bottomCluster
                                )
                            },
                            videoOverlaySlot = {
                                if (isVideoMode) {
                                    AuraVideoPlayerSurface(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .aspectRatio(16f / 9f)
                                            .clip(RoundedCornerShape(12.dp)),
                                        track = track,
                                        isLoading = isVideoLoading,
                                        onSwitchToAudio = onToggleVideoMode
                                    )
                                }
                            }
                        )
                    }
                }
            }
        }

        SpotifyLyricsOverlay(
            track = track,
            lyrics = lyrics,
            activeLyrics = activeLyrics,
            isLyricsLoading = isLyricsLoading,
            isLyricsExpanded = isLyricsExpanded,
            lyricsTransitionProgress = lyricsTransitionProgress,
            lyricsExpansionProgress = lyricsExpansionProgress,
            positionSec = positionSec,
            durationSec = durationSec,
            bufferedSec = bufferedSec,
            isPlaying = isPlaying,
            isBuffering = isBuffering,
            isFavorite = isFavorite,
            isVideoMode = isVideoMode,
            lyricsOffsetMs = lyricsOffsetMs,
            showRomanized = showRomanized,
            palette = palette,
            currentStyle = currentStyle,
            fullWidthPx = fullWidthPx,
            fullHeightPx = fullHeightPx,
            statusBarHeightPx = statusBarHeightPx,
            lyricsCardBounds = lyricsCardBounds,
            lyricsSourceArtBounds = lyricsSourceArtBounds,
            lyricsHeaderArtBounds = lyricsHeaderArtBounds,
            coverFlightEasing = coverFlightEasing,
            onHeaderArtPositioned = { lyricsHeaderArtBounds = it },
            onClose = { isLyricsExpanded = false },
            onSeekTo = onSeekTo,
            onTogglePlayPause = onTogglePlayPause,
            onSkipPrevious = onSkipPrevious,
            onSkipNext = onSkipNext,
            onToggleFavorite = onToggleFavorite,
            onOpenArtist = { onOpenArtist(track.artist) },
            onOpenOptions = { isOptionsModalVisible = true },
            onOpenComments = {
                isCommentsVisible = true
                onLoadComments(track.videoId)
            },
            onShareTrack = shareTrack,
            onSaveToPlaylist = { isAddToPlaylistModalVisible = true },
            onToggleVideoMode = onToggleVideoMode,
            onOpenQueue = { isQueueSheetVisible = true },
            onToggleRomanized = onToggleRomanized,
            onAdjustLyricsOffset = onAdjustLyricsOffset
        )

        // Harmonized dark container color subtly tinted with dominant lyrics card palette color
        val isGlass = LocalLiquidGlassEnabled.current
        val modalContainerColor = remember(palette.primary, isGlass) {
            val base = lerp(Color(0xFF14151B), palette.primary, 0.18f)
            if (isGlass) base.copy(alpha = 0.88f) else base
        }

        // 3-Dot Options Bottom Sheet Modal
        if (isOptionsModalVisible) {
            NowPlayingOptionsModal(
                track = track,
                currentStyle = currentStyle,
                isFavorite = isFavorite,
                isVideoMode = isVideoMode,
                modalContainerColor = modalContainerColor,
                onDismiss = { isOptionsModalVisible = false },
                onToggleVideoMode = {
                    isOptionsModalVisible = false
                    onToggleVideoMode()
                },
                onOpenLivePerformances = {
                    isOptionsModalVisible = false
                    onLoadLivePerformances()
                    isLivePerformancesModalVisible = true
                },
                onToggleTheme = {
                    isOptionsModalVisible = false
                    onToggleStyle()
                },
                mainLyricsProvider = mainLyricsProvider,
                onSwitchLyricsProvider = { nextProvider ->
                    isOptionsModalVisible = false
                    onSwitchLyricsProvider(nextProvider)
                    Toast.makeText(context, "Lyrics provider switched to $nextProvider", Toast.LENGTH_SHORT).show()
                },
                onGoToArtist = {
                    isOptionsModalVisible = false
                    onCollapse()
                    onOpenArtist(track.artist)
                },
                onToggleFavorite = {
                    onToggleFavorite()
                    isOptionsModalVisible = false
                },
                onAddToPlaylist = {
                    isOptionsModalVisible = false
                    isAddToPlaylistModalVisible = true
                },
                onPlayNext = {
                    AuraPlayerManager.playNext(track)
                    isOptionsModalVisible = false
                },
                onAddToQueue = {
                    AuraPlayerManager.enqueueTrack(track)
                    isOptionsModalVisible = false
                },
                onOpenQueue = {
                    isOptionsModalVisible = false
                    isQueueSheetVisible = true
                },
                onOpenLyrics = {
                    isOptionsModalVisible = false
                    isLyricsExpanded = true
                },
                isDownloaded = isDownloaded,
                isDownloading = isDownloading,
                onDownloadTrack = onDownloadTrack?.let { callback -> { isOptionsModalVisible = false; callback(track) } },
                onRemoveDownload = onRemoveDownload?.let { callback -> { isOptionsModalVisible = false; callback(track) } }
            )
        }

        // Modal: Add Current Song to Playlist
        if (isAddToPlaylistModalVisible) {
            AuraAddToPlaylistModal(
                track = track,
                playlists = playlists,
                onDismiss = { isAddToPlaylistModalVisible = false },
                onSelectPlaylist = { targetPl ->
                    onAddTrackToPlaylist(targetPl.id, track)
                    isAddToPlaylistModalVisible = false
                    Toast.makeText(context, "Added '${track.title}' to '${targetPl.name}'", Toast.LENGTH_SHORT).show()
                },
                onCreateNewPlaylist = {
                    isAddToPlaylistModalVisible = false
                    isCreatePlaylistModalVisible = true
                }
            )
        }

        // Modal: Create Playlist from Now Playing Sheet
        if (isCreatePlaylistModalVisible) {
            AuraCreatePlaylistModal(
                onDismiss = { isCreatePlaylistModalVisible = false },
                onCreate = { title, desc, coverUri ->
                    isCreatePlaylistModalVisible = false
                    onCreatePlaylist(title, desc, coverUri)
                    Toast.makeText(context, "Created playlist: $title", Toast.LENGTH_SHORT).show()
                }
            )
        }

        // Live Performances & Alternate Versions Bottom Sheet Modal
        if (isLivePerformancesModalVisible) {
            LivePerformancesModal(
                track = track,
                liveTracks = livePerformances,
                isLoading = isLoadingLivePerformances,
                modalContainerColor = modalContainerColor,
                onDismiss = { isLivePerformancesModalVisible = false },
                onSelectTrack = {
                    isLivePerformancesModalVisible = false
                    onPlayLivePerformance(it)
                }
            )
        }

        // Upcoming Queue Mobile Bottom Sheet Modal
        if (isQueueSheetVisible) {
            SpotifyQueueSheet(
                currentTrack = track,
                queue = queue,
                isPlaying = isPlaying,
                modalContainerColor = modalContainerColor,
                onDismiss = { isQueueSheetVisible = false },
                onPlayTrack = onPlayQueueTrack,
                onReorderQueue = { newUpcoming ->
                    AuraPlayerManager.updateUpcomingQueue(newUpcoming)
                }
            )
        }

        if (isCommentsVisible) {
            AuraCommentsBottomSheet(
                track = track,
                page = comments,
                isLoading = isLoadingComments,
                error = commentsError,
                onRetry = { onLoadComments(track.videoId) },
                onLoadMore = onLoadMoreComments,
                onSelectSort = onSelectCommentSort,
                onToggleReplies = onToggleCommentReplies,
                onLoadMoreReplies = onLoadMoreCommentReplies,
                onDismiss = { isCommentsVisible = false }
            )
        }
    }
}

/**
 * High-performance ExoPlayer video surface maximized to the sides when in Video Mode.
 */
@Composable
fun AuraVideoPlayerSurface(
    modifier: Modifier = Modifier,
    track: AuraTrack,
    isLoading: Boolean,
    onSwitchToAudio: () -> Unit
) {
    SpotifyVideoPlayerSurface(
        modifier = modifier,
        track = track,
        isLoading = isLoading,
        onSwitchToAudio = onSwitchToAudio
    )
}
