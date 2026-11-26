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
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.QueueMusic
import androidx.compose.material.icons.rounded.Audiotrack
import androidx.compose.material.icons.rounded.Bedtime
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.FavoriteBorder
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.LiveTv
import androidx.compose.material.icons.rounded.Lyrics
import androidx.compose.material.icons.rounded.Palette
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material.icons.rounded.PlaylistAdd
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material.icons.rounded.SpeakerGroup
import androidx.compose.material.icons.rounded.Speed
import androidx.compose.material.icons.rounded.Translate
import androidx.compose.material.icons.rounded.Videocam
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
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.auramusic.app.core.media.AudioOutputDevice
import com.auramusic.app.core.media.AudioOutputRouter
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
import com.auramusic.app.domain.model.ReturnYouTubeDislikeData
import com.auramusic.app.ui.components.AuraAddToPlaylistModal
import com.auramusic.app.ui.components.AuraCommentsBottomSheet
import com.auramusic.app.ui.components.AuraCreatePlaylistModal
import com.auramusic.app.ui.components.AuraOutputSheet
import com.auramusic.app.ui.components.AuraShareLyricsSheet
import com.auramusic.app.ui.components.AuraSleepTimerSheet
import com.auramusic.app.ui.components.AuraSpeedPitchSheet
import com.auramusic.app.ui.components.AuraTrackInfoSheet
import com.auramusic.app.ui.components.ForceLightIconsSystemBarsEffect
import com.auramusic.app.ui.components.formatSleepTimerStatus
import com.auramusic.app.ui.screens.player.applemusic.AppleMusicArtworkBackdrop
import com.auramusic.app.ui.screens.player.applemusic.AppleMusicLyricStrip
import com.auramusic.app.ui.screens.player.applemusic.AppleMusicLyricsTabView
import com.auramusic.app.ui.screens.player.applemusic.AppleMusicNowPlayingView
import com.auramusic.app.ui.screens.player.applemusic.AppleMusicView
import com.auramusic.app.ui.screens.player.spotify.LivePerformancesModal
import com.auramusic.app.ui.screens.player.spotify.SpotifyEncorePlayerView
import com.auramusic.app.ui.screens.player.spotify.SpotifyLyricsOverlay
import com.auramusic.app.ui.screens.player.spotify.SpotifyQueueSheet
import com.auramusic.app.ui.screens.player.spotify.SpotifyVideoPlayerSurface
import com.auramusic.app.ui.theme.AuraNeonLime
import com.auramusic.app.ui.theme.AuraViolet
import com.auramusic.app.ui.theme.LocalLiquidGlassEnabled
import com.auramusic.app.ui.theme.TextSecondary
import java.util.Locale

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
    onLoadMoreCommentReplies: (String) -> Unit = {},
    rydData: ReturnYouTubeDislikeData? = null,
    playbackSpeed: Float = 1.0f,
    playbackPitchSemitones: Int = 0,
    onPlaybackSpeedChange: (Float) -> Unit = { AuraPlayerManager.setPlaybackSpeed(it) },
    onPlaybackPitchChange: (Int) -> Unit = { AuraPlayerManager.setPlaybackPitchSemitones(it) },
    onResetSpeedPitch: () -> Unit = { AuraPlayerManager.resetSpeedAndPitch() },
    audioOutputDevices: List<AudioOutputDevice> = emptyList(),
    selectedAudioOutputId: Int? = null,
    onRefreshOutputDevices: () -> Unit = {},
    onSelectAudioOutputDevice: (AudioOutputDevice?) -> Unit = { AuraPlayerManager.selectAudioOutputDevice(it) },
    translatedLyricsMap: Map<Int, String> = emptyMap(),
    isTranslatingLyrics: Boolean = false,
    onTranslateLyrics: () -> Unit = {}
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
    var isSleepTimerSheetVisible by remember { mutableStateOf(false) }
    var isSpeedPitchSheetVisible by remember { mutableStateOf(false) }
    var isTrackInfoSheetVisible by remember { mutableStateOf(false) }
    var isOutputSheetVisible by remember { mutableStateOf(false) }
    var isShareLyricsSheetVisible by remember { mutableStateOf(false) }
    var appleMusicActiveView by remember { mutableStateOf(AppleMusicView.MAIN) }
    val playbackMode by AuraPlayerManager.playbackMode.collectAsState()
    val sleepTimerRemainingMin by AuraPlayerManager.sleepTimerRemainingMin.collectAsState()
    val sleepTimerEndOfTrack by AuraPlayerManager.sleepTimerEndOfTrack.collectAsState()
    val isSleepTimerActive = (sleepTimerRemainingMin != null && (sleepTimerRemainingMin ?: 0) > 0) || sleepTimerEndOfTrack
    val sleepTimerStatus = formatSleepTimerStatus(sleepTimerRemainingMin, sleepTimerEndOfTrack)

    val canonicalVideoId = AuraPlayerManager.originalAudioTrack?.videoId ?: track.videoId
    val activeLyrics = lyrics?.takeIf {
        it.videoId == canonicalVideoId || it.videoId == track.videoId
    }
    val isLyricsLoading = activeLyrics == null
    val displayLyrics = activeLyrics?.takeIf { it.source != "None" }
    val lyricsAvailable = displayLyrics != null && (
        displayLyrics.syncedLines.isNotEmpty() || displayLyrics.plainLyrics.isNotBlank()
    )

    if (playerCollapseProgress < 0.5f) {
        ForceLightIconsSystemBarsEffect()
    }

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
    LaunchedEffect(
        Unit, isQueueSheetVisible, isOptionsModalVisible, isLyricsExpanded,
        isLivePerformancesModalVisible, isAddToPlaylistModalVisible, isCreatePlaylistModalVisible,
        isCommentsVisible, isSleepTimerSheetVisible, isSpeedPitchSheetVisible,
        isTrackInfoSheetVisible, isOutputSheetVisible, isShareLyricsSheetVisible
    ) {
        focusManager.clearFocus(force = true)
        keyboardController?.hide()
    }

    // Intercept back gesture / button to smoothly close open sheets / modals
    BackHandler(
        enabled = isLyricsExpanded || isLivePerformancesModalVisible || isOptionsModalVisible ||
            isQueueSheetVisible || isAddToPlaylistModalVisible || isCreatePlaylistModalVisible ||
            isCommentsVisible || isSleepTimerSheetVisible || isSpeedPitchSheetVisible ||
            isTrackInfoSheetVisible || isOutputSheetVisible || isShareLyricsSheetVisible
    ) {
        when {
            isShareLyricsSheetVisible -> isShareLyricsSheetVisible = false
            isOutputSheetVisible -> isOutputSheetVisible = false
            isTrackInfoSheetVisible -> isTrackInfoSheetVisible = false
            isSpeedPitchSheetVisible -> isSpeedPitchSheetVisible = false
            isSleepTimerSheetVisible -> isSleepTimerSheetVisible = false
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
                            sleepTimerRemainingMin = sleepTimerRemainingMin,
                            sleepTimerEndOfTrack = sleepTimerEndOfTrack,
                            onOpenSleepTimer = { isSleepTimerSheetVisible = true },
                            playbackSpeed = playbackSpeed,
                            playbackPitchSemitones = playbackPitchSemitones,
                            translatedLyricsMap = translatedLyricsMap,
                            isTranslatingLyrics = isTranslatingLyrics,
                            onOpenSpeedPitch = { isSpeedPitchSheetVisible = true },
                            onOpenTrackInfo = { isTrackInfoSheetVisible = true },
                            onOpenOutputRouter = {
                                onRefreshOutputDevices()
                                isOutputSheetVisible = true
                            },
                            onTranslateLyrics = onTranslateLyrics,
                            onShareLyricsCard = {
                                if (lyricsAvailable) {
                                    isShareLyricsSheetVisible = true
                                } else {
                                    Toast.makeText(context, "No lyrics loaded to share", Toast.LENGTH_SHORT).show()
                                }
                            },
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
                            isSleepTimerActive = isSleepTimerActive,
                            onOpenSleepTimer = { isSleepTimerSheetVisible = true },
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
            AuraNowPlayingOptionsSheet(
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
                isDownloaded = isDownloaded,
                isDownloading = isDownloading,
                onDownloadTrack = onDownloadTrack?.let { callback -> { isOptionsModalVisible = false; callback(track) } },
                onRemoveDownload = onRemoveDownload?.let { callback -> { isOptionsModalVisible = false; callback(track) } },
                sleepTimerStatus = sleepTimerStatus,
                onOpenSleepTimer = {
                    isOptionsModalVisible = false
                    isSleepTimerSheetVisible = true
                },
                playbackSpeed = playbackSpeed,
                playbackPitchSemitones = playbackPitchSemitones,
                onOpenSpeedPitch = {
                    isOptionsModalVisible = false
                    isSpeedPitchSheetVisible = true
                },
                onOpenOutputRouter = {
                    isOptionsModalVisible = false
                    onRefreshOutputDevices()
                    isOutputSheetVisible = true
                },
                onOpenTrackInfo = {
                    isOptionsModalVisible = false
                    isTrackInfoSheetVisible = true
                },
                lyricsAvailable = lyricsAvailable,
                hasTranslatedLyrics = translatedLyricsMap.isNotEmpty(),
                isTranslatingLyrics = isTranslatingLyrics,
                onTranslateLyrics = {
                    isOptionsModalVisible = false
                    onTranslateLyrics()
                },
                onShareLyricsCard = {
                    isOptionsModalVisible = false
                    if (lyricsAvailable) {
                        isShareLyricsSheetVisible = true
                    } else {
                        Toast.makeText(context, "No lyrics loaded to share", Toast.LENGTH_SHORT).show()
                    }
                }
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

        if (isSleepTimerSheetVisible) {
            AuraSleepTimerSheet(
                remainingMinutes = sleepTimerRemainingMin,
                endOfTrackEnabled = sleepTimerEndOfTrack,
                onSelectMinutes = { AuraPlayerManager.setSleepTimerMinutes(it) },
                onToggleEndOfTrack = { AuraPlayerManager.setSleepTimerEndOfTrack(it) },
                onDismiss = { isSleepTimerSheetVisible = false },
                containerColor = modalContainerColor
            )
        }

        if (isSpeedPitchSheetVisible) {
            AuraSpeedPitchSheet(
                speed = playbackSpeed,
                pitchSemitones = playbackPitchSemitones,
                onSpeedChange = onPlaybackSpeedChange,
                onPitchChange = onPlaybackPitchChange,
                onReset = onResetSpeedPitch,
                onDismiss = { isSpeedPitchSheetVisible = false }
            )
        }

        if (isTrackInfoSheetVisible) {
            AuraTrackInfoSheet(
                track = track,
                rydData = rydData,
                description = videoInfo?.description.orEmpty(),
                onSeekToSec = { sec ->
                    onSeekTo(sec.toFloat())
                    isTrackInfoSheetVisible = false
                },
                onDismiss = { isTrackInfoSheetVisible = false }
            )
        }

        if (isOutputSheetVisible) {
            val devices = audioOutputDevices.ifEmpty { AudioOutputRouter.listOutputDevices(context) }
            AuraOutputSheet(
                devices = devices,
                selectedDeviceId = selectedAudioOutputId,
                onSelectDevice = { dev ->
                    onSelectAudioOutputDevice(dev)
                    isOutputSheetVisible = false
                },
                onDismiss = { isOutputSheetVisible = false }
            )
        }

        if (isShareLyricsSheetVisible && displayLyrics != null) {
            AuraShareLyricsSheet(
                track = track,
                lyrics = displayLyrics,
                palette = palette,
                onDismiss = { isShareLyricsSheetVisible = false }
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AuraNowPlayingOptionsSheet(
    track: AuraTrack,
    currentStyle: NowPlayingStyle,
    isFavorite: Boolean,
    isVideoMode: Boolean,
    modalContainerColor: Color,
    onDismiss: () -> Unit,
    onToggleVideoMode: () -> Unit,
    onOpenLivePerformances: () -> Unit,
    onToggleTheme: () -> Unit,
    mainLyricsProvider: String,
    onSwitchLyricsProvider: (String) -> Unit,
    onGoToArtist: () -> Unit,
    onToggleFavorite: () -> Unit,
    onAddToPlaylist: () -> Unit,
    onPlayNext: () -> Unit,
    onAddToQueue: () -> Unit,
    isDownloaded: Boolean,
    isDownloading: Boolean,
    onDownloadTrack: (() -> Unit)?,
    onRemoveDownload: (() -> Unit)?,
    sleepTimerStatus: String?,
    onOpenSleepTimer: () -> Unit,
    playbackSpeed: Float,
    playbackPitchSemitones: Int,
    onOpenSpeedPitch: () -> Unit,
    onOpenOutputRouter: () -> Unit,
    onOpenTrackInfo: () -> Unit,
    lyricsAvailable: Boolean,
    hasTranslatedLyrics: Boolean,
    isTranslatingLyrics: Boolean,
    onTranslateLyrics: () -> Unit,
    onShareLyricsCard: () -> Unit
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
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 22.dp, vertical = 6.dp)
        ) {
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

            val speedPitchLabel = if (playbackSpeed != 1.0f || playbackPitchSemitones != 0) {
                val speedTxt = String.format(Locale.US, "%.2fx", playbackSpeed)
                val pitchTxt = if (playbackPitchSemitones == 0) "" else " · ${if (playbackPitchSemitones > 0) "+$playbackPitchSemitones" else "$playbackPitchSemitones"}st"
                "Speed & Pitch ($speedTxt$pitchTxt)"
            } else {
                "Speed & Pitch"
            }
            OptionsSheetRow(
                icon = Icons.Rounded.Speed,
                label = speedPitchLabel,
                accentTint = playbackSpeed != 1.0f || playbackPitchSemitones != 0,
                onClick = onOpenSpeedPitch
            )
            OptionsSheetRow(
                icon = Icons.Rounded.SpeakerGroup,
                label = "Audio Output",
                onClick = onOpenOutputRouter
            )
            OptionsSheetRow(
                icon = Icons.Rounded.Info,
                label = "Track Info",
                onClick = onOpenTrackInfo
            )
            if (lyricsAvailable) {
                OptionsSheetRow(
                    icon = Icons.Rounded.Translate,
                    label = when {
                        isTranslatingLyrics -> "Translating…"
                        hasTranslatedLyrics -> "Hide Translation"
                        else -> "Translate Lyrics"
                    },
                    accentTint = hasTranslatedLyrics,
                    onClick = onTranslateLyrics
                )
                OptionsSheetRow(
                    icon = Icons.Rounded.Share,
                    label = "Share Lyrics",
                    onClick = onShareLyricsCard
                )
            }

            HorizontalDivider(color = Color.White.copy(alpha = 0.06f), thickness = 0.5.dp)
            Spacer(modifier = Modifier.height(4.dp))

            OptionsSheetRow(
                icon = if (isVideoMode) Icons.Rounded.Audiotrack else Icons.Rounded.Videocam,
                label = if (isVideoMode) "Switch to Audio" else "Watch Video",
                onClick = onToggleVideoMode
            )

            if (isDownloaded && onRemoveDownload != null) {
                OptionsSheetRow(
                    icon = Icons.Rounded.DeleteOutline,
                    label = "Remove Download",
                    onClick = onRemoveDownload
                )
            } else if (onDownloadTrack != null) {
                OptionsSheetRow(
                    icon = if (isDownloaded) Icons.Rounded.DeleteOutline else Icons.Rounded.Download,
                    label = if (isDownloading) "Downloading…" else if (isDownloaded) "Remove Download" else "Download",
                    onClick = {
                        if (isDownloaded && onRemoveDownload != null) onRemoveDownload() else onDownloadTrack()
                    }
                )
            }

            OptionsSheetRow(
                icon = Icons.Rounded.LiveTv,
                label = "More Videos",
                onClick = onOpenLivePerformances
            )

            val nextThemeLabel = if (currentStyle == NowPlayingStyle.SPOTIFY) "Liquid Glass" else "Default"
            OptionsSheetRow(
                icon = Icons.Rounded.Palette,
                label = "Switch to $nextThemeLabel",
                onClick = onToggleTheme
            )

            val providers = listOf("SimpMusic", "BetterLyrics", "LRCLIB", "YouTube")
            val curIdx = providers.indexOfFirst { it.equals(mainLyricsProvider, ignoreCase = true) }
            val nextLyricsProvider = providers[(curIdx + 1).mod(providers.size)]
            OptionsSheetRow(
                icon = Icons.Rounded.Lyrics,
                label = "Lyrics Source: $mainLyricsProvider",
                onClick = { onSwitchLyricsProvider(nextLyricsProvider) }
            )

            OptionsSheetRow(
                icon = Icons.Rounded.Bedtime,
                label = if (sleepTimerStatus != null) "Sleep Timer ($sleepTimerStatus)" else "Sleep Timer",
                accentTint = sleepTimerStatus != null,
                onClick = onOpenSleepTimer
            )

            OptionsSheetRow(
                icon = Icons.Rounded.Person,
                label = "View Artist",
                onClick = onGoToArtist
            )
            OptionsSheetRow(
                icon = if (isFavorite) Icons.Rounded.Favorite else Icons.Rounded.FavoriteBorder,
                label = if (isFavorite) "Remove from Liked" else "Like Song",
                onClick = onToggleFavorite
            )
            OptionsSheetRow(
                icon = Icons.Rounded.PlaylistAdd,
                label = "Add to Playlist",
                onClick = onAddToPlaylist
            )
            OptionsSheetRow(
                icon = Icons.AutoMirrored.Rounded.QueueMusic,
                label = "Play Next",
                onClick = onPlayNext
            )
            OptionsSheetRow(
                icon = Icons.Rounded.PlaylistAdd,
                label = "Add to Queue",
                onClick = onAddToQueue
            )

            Spacer(modifier = Modifier.height(24.dp))
        }
    }
}

@Composable
private fun OptionsSheetRow(
    icon: ImageVector,
    label: String,
    accentTint: Boolean = false,
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
            fontSize = 15.sp,
            modifier = Modifier.weight(1f)
        )
        if (accentTint) {
            Spacer(modifier = Modifier.width(8.dp))
            Box(
                modifier = Modifier
                    .size(6.dp)
                    .clip(CircleShape)
                    .background(AuraNeonLime)
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
