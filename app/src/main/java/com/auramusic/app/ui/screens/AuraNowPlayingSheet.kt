package com.auramusic.app.ui.screens

import android.content.Context
import android.media.AudioManager
import android.view.ViewGroup
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.ui.PlayerView
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.Crossfade
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.expandHorizontally
import androidx.compose.animation.shrinkHorizontally
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import java.util.Collections
import kotlinx.coroutines.launch
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.interaction.collectIsDraggedAsState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.Comment
import androidx.compose.material.icons.automirrored.rounded.QueueMusic
import androidx.compose.material.icons.automirrored.rounded.VolumeDown
import androidx.compose.material.icons.automirrored.rounded.VolumeUp
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.AddCircleOutline
import androidx.compose.material.icons.rounded.Audiotrack
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.DownloadDone
import androidx.compose.material.icons.rounded.DragHandle
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.FavoriteBorder
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material.icons.rounded.LiveTv
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.rounded.OpenInFull
import androidx.compose.material.icons.rounded.Palette
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.PlayCircle
import androidx.compose.material.icons.rounded.PlaylistAdd
import androidx.compose.material.icons.rounded.Remove
import androidx.compose.material.icons.rounded.Repeat
import androidx.compose.material.icons.rounded.RepeatOne
import androidx.compose.material.icons.rounded.RestartAlt
import androidx.compose.material.icons.rounded.Shuffle
import androidx.compose.material.icons.rounded.SkipNext
import androidx.compose.material.icons.rounded.SkipPrevious
import androidx.compose.material.icons.rounded.Star
import androidx.compose.material.icons.rounded.StarBorder
import androidx.compose.material.icons.rounded.Timer
import androidx.compose.material.icons.rounded.Translate
import androidx.compose.material.icons.rounded.Videocam
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import kotlin.math.roundToInt
import coil.compose.AsyncImage
import com.auramusic.app.ui.components.AudioPlayingIndicator
import com.auramusic.app.core.lyrics.SimpMusicLyricsRepository
import com.auramusic.app.core.media.AuraPlayerManager
import com.auramusic.app.domain.model.AuraPalette
import com.auramusic.app.domain.model.AuraCommentsPage
import com.auramusic.app.domain.model.AuraPlaylist
import com.auramusic.app.domain.model.AuraTrack
import com.auramusic.app.domain.model.AuraVideoInfo
import com.auramusic.app.domain.model.ArtistDetail
import com.auramusic.app.domain.model.LyricsData
import com.auramusic.app.domain.model.NowPlayingStyle
import com.auramusic.app.domain.model.PlaybackMode
import com.auramusic.app.ui.components.AuraAddToPlaylistModal
import com.auramusic.app.ui.components.AuraCreatePlaylistModal
import com.auramusic.app.ui.components.AuraCommentsBottomSheet
import com.auramusic.app.ui.theme.AuraCircleButtonBg
import com.auramusic.app.ui.theme.AuraNeonLime
import com.auramusic.app.ui.theme.AuraPoppinsFontFamily
import com.auramusic.app.ui.theme.AuraPlayGreen
import com.auramusic.app.ui.theme.SpotifyCard
import com.auramusic.app.ui.theme.SpotifyPitchBlack
import com.auramusic.app.ui.theme.TextPrimary
import com.auramusic.app.ui.theme.TextSecondary

private inline fun lerpFloat(start: Float, stop: Float, fraction: Float): Float = start + (stop - start) * fraction

private val LyricsEaseOutCubic = CubicBezierEasing(0.23f, 1f, 0.32f, 1f)
private val LyricsEaseInCubic = CubicBezierEasing(0.32f, 0f, 0.67f, 0f)

private fun formatLyricsOffsetDisplay(offsetMs: Int): String {
    if (offsetMs == 0) return "0.0s"
    val sign = if (offsetMs > 0) "+" else "-"
    val absMs = kotlin.math.abs(offsetMs)
    val s = absMs / 1000
    val tenths = (absMs % 1000) / 100
    return "$sign$s.${tenths}s"
}

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
    onLoadMoreComments: () -> Unit = {}
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

    val context = LocalContext.current
    LaunchedEffect(track.videoId, track.artist) {
        onLoadArtistDetail(track.artist, track.title, track.highResArtworkUrl)
    }
    val openLyrics = {
        // Freeze the source before the player begins scaling under the lyrics sheet.
        lyricsSourceArtBounds = albumArtBounds
        isLyricsExpanded = true
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

    // Root container matching sheet bounds - 100% solid background so underlying screens never bleed through
    BoxWithConstraints(
        modifier = Modifier
            .fillMaxSize()
            .background(
                lerp(
                    Color(0xFF14151B),
                    palette.primary,
                    playerCollapseProgress.coerceIn(0f, 1f)
                )
            )
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = {} // Intercepts all clicks so background screens (search field, home items) are never touched
            )
    ) {
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
                    alpha = 1f - (playerCollapseProgress.coerceIn(0f, 1f) / 0.58f).coerceIn(0f, 1f)
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
                        LiquidGlassPlayerView(
                            track = track,
                            queue = queue,
                            isPlaying = isPlaying,
                            isBuffering = isBuffering,
                            positionSec = positionSec,
                            durationSec = durationSec,
                            bufferedSec = bufferedSec,
                            isFavorite = isFavorite,
                            isShuffleEnabled = isShuffleEnabled,
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
                }
            }
        }

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
            val fullWidthPx = constraints.maxWidth.toFloat()
            val density = LocalDensity.current

            val fallbackMargin = with(density) { 20.dp.toPx() }
            val fallbackWidth = fullWidthPx - (fallbackMargin * 2f)
            val fallbackHeight = with(density) { 280.dp.toPx() }
            val fallbackTop = fullHeightPx - with(density) { 340.dp.toPx() }
            val compactHeaderBottomPx = statusBarHeightPx.toFloat() +
                with(density) { 66.dp.toPx() }

            val startLeft = lyricsCardBounds?.left ?: fallbackMargin
            val startTop = lyricsCardBounds?.top ?: fallbackTop
            val startWidth = lyricsCardBounds?.width?.takeIf { it > 10f } ?: fallbackWidth
            val startHeight = lyricsCardBounds?.height?.takeIf { it > 10f } ?: fallbackHeight

            val clampedProgress = lyricsExpansionProgress.coerceIn(0f, 1f)

            val currentLeft = lerpFloat(startLeft, 0f, clampedProgress)
            val currentTop = lerpFloat(startTop, compactHeaderBottomPx, clampedProgress)
            val currentWidth = lerpFloat(startWidth, fullWidthPx, clampedProgress).coerceAtLeast(10f)
            val lyricsAreaHeightPx = (fullHeightPx - compactHeaderBottomPx).coerceAtLeast(10f)
            val currentHeight = lerpFloat(startHeight, lyricsAreaHeightPx, clampedProgress).coerceAtLeast(10f)

            val currentLeftDp = with(density) { currentLeft.toDp() }
            val currentTopDp = with(density) { currentTop.toDp() }
            val currentWidthDp = with(density) { currentWidth.toDp() }
            val currentHeightDp = with(density) { currentHeight.toDp() }
            val currentCornerRadius = lerpFloat(20f, 18f, clampedProgress).coerceIn(0f, 20f)
            val currentElevation = (16.dp * (1f - clampedProgress)).coerceAtLeast(0.dp)

            Box(
                modifier = Modifier
                    .offset(x = currentLeftDp, y = currentTopDp)
                    .size(width = currentWidthDp, height = currentHeightDp)
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
                    lyrics = lyrics,
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
                    onSeekTo = onSeekTo,
                    onTogglePlayPause = onTogglePlayPause,
                    onSkipPrevious = onSkipPrevious,
                    onSkipNext = onSkipNext,
                    onOpenQueue = { isQueueSheetVisible = true },
                    onAdjustLyricsOffset = onAdjustLyricsOffset
                )

                // Smooth seamless card preview blend when close to the bottom slot
                // Matches the card's exact 16.sp font size and layout without any jump
                if (lyricsExpansionProgress < 0.35f) {
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
            val sourceCornerRadius = if (currentStyle == NowPlayingStyle.LIQUID_GLASS) 22f else 8f
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
                    artworkExpansionProgress = coverFlightProgress,
                    statusBarHeightPx = statusBarHeightPx,
                    headerColor = lerp(Color.Black, palette.primary, 0.08f),
                    onArtworkPositioned = { lyricsHeaderArtBounds = it },
                    onClose = { isLyricsExpanded = false },
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

        // Harmonized dark container color subtly tinted with dominant lyrics card palette color
        val modalContainerColor = remember(palette.primary) {
            lerp(Color(0xFF14151B), palette.primary, 0.18f)
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
            ModalBottomSheet(
                onDismissRequest = { isQueueSheetVisible = false },
                sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
                containerColor = modalContainerColor,
                contentColor = TextPrimary,
                dragHandle = {
                    Surface(
                        modifier = Modifier.padding(top = 10.dp, bottom = 6.dp),
                        color = Color.White.copy(alpha = 0.25f),
                        shape = RoundedCornerShape(4.dp)
                    ) {
                        Spacer(modifier = Modifier.size(width = 36.dp, height = 4.dp))
                    }
                }
            ) {
                com.auramusic.app.ui.components.ForceWhiteSystemBarsEffect()
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .fillMaxHeight(0.55f)
                ) {
                    QueueSheetContent(
                        currentTrack = track,
                        queue = queue,
                        isPlaying = isPlaying,
                        modalContainerColor = modalContainerColor,
                        onPlayTrack = {
                            onPlayQueueTrack(it)
                            isQueueSheetVisible = false
                        },
                        onReorderQueue = { newUpcoming ->
                            AuraPlayerManager.updateUpcomingQueue(newUpcoming)
                        }
                    )
                }
            }
        }

        if (isCommentsVisible) {
            AuraCommentsBottomSheet(
                track = track,
                page = comments,
                isLoading = isLoadingComments,
                error = commentsError,
                onRetry = { onLoadComments(track.videoId) },
                onLoadMore = onLoadMoreComments,
                onDismiss = { isCommentsVisible = false }
            )
        }

    }
}

/**
 * 3-Dot Options Bottom Sheet Modal on Full Music Player
 * Unified options with theme toggling, go to artist, liked songs, queue actions, and lyrics.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun NowPlayingOptionsModal(
    track: AuraTrack,
    currentStyle: NowPlayingStyle,
    isFavorite: Boolean,
    isVideoMode: Boolean = false,
    modalContainerColor: Color,
    onDismiss: () -> Unit,
    onToggleVideoMode: () -> Unit,
    onOpenLivePerformances: () -> Unit,
    onToggleTheme: () -> Unit,
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
    onRemoveDownload: (() -> Unit)? = null
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
        com.auramusic.app.ui.components.ForceWhiteSystemBarsEffect()
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
            val nextThemeLabel = if (currentStyle == NowPlayingStyle.SPOTIFY) "Apple Music" else "Spotify Encore"
            NowPlayingModalOptionRow(
                icon = Icons.Rounded.Palette,
                label = "Switch Theme to $nextThemeLabel",
                onClick = onToggleTheme
            )

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
    icon: androidx.compose.ui.graphics.vector.ImageVector,
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
fun AuraVideoPlayerSurface(
    modifier: Modifier = Modifier,
    track: AuraTrack,
    isLoading: Boolean,
    onSwitchToAudio: () -> Unit
) {
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

        AndroidView(
            factory = { ctx ->
                PlayerView(ctx).apply {
                    player = com.auramusic.app.core.media.AuraPlayerManager.exoPlayer
                    useController = false
                    resizeMode = AspectRatioFrameLayout.RESIZE_MODE_FIT
                    setKeepContentOnPlayerReset(true)
                    setShutterBackgroundColor(android.graphics.Color.TRANSPARENT)
                    setBackgroundColor(android.graphics.Color.TRANSPARENT)
                    layoutParams = ViewGroup.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.MATCH_PARENT
                    )
                }
            },
            update = { view ->
                if (view.player != com.auramusic.app.core.media.AuraPlayerManager.exoPlayer) {
                    view.player = com.auramusic.app.core.media.AuraPlayerManager.exoPlayer
                }
            },
            modifier = Modifier.fillMaxSize(),
            onRelease = { view ->
                // PlayerView uses a separate video surface by default. Explicitly detach it
                // when the full player leaves composition so it cannot cover or intercept
                // the mini-player after a video is collapsed.
                view.player = null
            }
        )

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
 * Mobile Bottom Sheet Modal presenting Live Concert Performances, Acoustic Sessions,
 * and Official Music Videos for the current track.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun LivePerformancesModal(
    track: AuraTrack,
    liveTracks: List<AuraTrack>,
    isLoading: Boolean,
    modalContainerColor: Color,
    onDismiss: () -> Unit,
    onSelectTrack: (AuraTrack) -> Unit
) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
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
        com.auramusic.app.ui.components.ForceWhiteSystemBarsEffect()
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 22.dp, vertical = 6.dp)
                .padding(bottom = 36.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp)
            ) {
                Icon(
                    imageVector = Icons.Rounded.LiveTv,
                    contentDescription = null,
                    tint = Color(0xFFFFB74D),
                    modifier = Modifier.size(24.dp)
                )
                Spacer(modifier = Modifier.width(10.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Live & Alternate Versions",
                        color = Color.White,
                        fontWeight = FontWeight.Bold,
                        fontSize = 17.sp
                    )
                    Text(
                        text = "${track.title} • ${track.artist}",
                        color = TextSecondary,
                        fontSize = 12.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }

            HorizontalDivider(color = Color.White.copy(alpha = 0.08f), thickness = 0.5.dp)
            Spacer(modifier = Modifier.height(12.dp))

            if (isLoading) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(180.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        CircularProgressIndicator(color = AuraNeonLime, modifier = Modifier.size(32.dp))
                        Text(
                            text = "Searching live concert stages & performances…",
                            color = TextSecondary,
                            fontSize = 13.sp
                        )
                    }
                }
            } else if (liveTracks.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(150.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "No alternate live performances found for this song.",
                        color = TextSecondary,
                        fontSize = 13.5.sp
                    )
                }
            } else {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 420.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    items(liveTracks) { item ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(12.dp))
                                .background(Color(0xFF1B1E28))
                                .clickable {
                                    onDismiss()
                                    onSelectTrack(item)
                                }
                                .padding(10.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(width = 84.dp, height = 52.dp)
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(Color(0xFF242735)),
                                contentAlignment = Alignment.Center
                            ) {
                                AsyncImage(
                                    model = item.highResArtworkUrl.ifBlank { "https://i.ytimg.com/vi/${item.videoId}/hqdefault.jpg" },
                                    contentDescription = item.title,
                                    contentScale = ContentScale.Crop,
                                    modifier = Modifier.fillMaxSize()
                                )
                            }

                            Spacer(modifier = Modifier.width(12.dp))

                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = item.title,
                                    color = Color.White,
                                    fontSize = 13.5.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    maxLines = 2,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Spacer(modifier = Modifier.height(3.dp))
                                Text(
                                    text = item.artist,
                                    color = TextSecondary,
                                    fontSize = 11.5.sp,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }

                            Icon(
                                imageVector = Icons.Rounded.PlayCircle,
                                contentDescription = "Play",
                                tint = AuraNeonLime,
                                modifier = Modifier
                                    .size(28.dp)
                                    .padding(start = 4.dp)
                            )
                        }
                    }
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
private fun CompactPointSlider(
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
private fun AuraDraggableCoverArt(
    currentTrack: AuraTrack,
    queue: List<AuraTrack>,
    cornerRadius: androidx.compose.ui.unit.Dp,
    hideArtworkForLyricsTransition: Boolean,
    onSkipNext: () -> Unit,
    onSkipPrevious: () -> Unit,
    onArtworkPositioned: (Rect) -> Unit,
    modifier: Modifier = Modifier,
    overlayContent: @Composable () -> Unit = {}
) {
    val coroutineScope = rememberCoroutineScope()
    val dragOffset = remember { Animatable(0f) }
    var lastSwipeDirection by remember { mutableIntStateOf(0) } // +1 = next, -1 = prev
    var containerWidthPx by remember { mutableFloatStateOf(0f) }
    val density = LocalDensity.current
    val swipeThresholdPx = with(density) { 72.dp.toPx() }

    // Resolve queue neighbors
    val curIdx = remember(queue, currentTrack.videoId) {
        queue.indexOfFirst { it.videoId == currentTrack.videoId }
    }
    val prevTrack = remember(queue, curIdx) {
        if (curIdx > 0 && curIdx <= queue.lastIndex) queue[curIdx - 1] else null
    }
    val nextTrack = remember(queue, curIdx) {
        if (curIdx >= 0 && curIdx < queue.lastIndex) queue[curIdx + 1] else null
    }

    // When track changes (e.g. after swipe or external skip), animate smoothly into place
    LaunchedEffect(currentTrack.videoId) {
        val width = if (containerWidthPx > 0f) containerWidthPx else with(density) { 320.dp.toPx() }
        if (lastSwipeDirection == 1) {
            // New track entering from right
            dragOffset.snapTo(width * 0.42f)
            dragOffset.animateTo(
                targetValue = 0f,
                animationSpec = tween(280, easing = CubicBezierEasing(0.23f, 1f, 0.32f, 1f))
            )
        } else if (lastSwipeDirection == -1) {
            // New track entering from left
            dragOffset.snapTo(-width * 0.42f)
            dragOffset.animateTo(
                targetValue = 0f,
                animationSpec = tween(280, easing = CubicBezierEasing(0.23f, 1f, 0.32f, 1f))
            )
        } else {
            dragOffset.snapTo(0f)
        }
        lastSwipeDirection = 0
    }

    Box(
        modifier = modifier
            .clipToBounds()
            .onGloballyPositioned { coordinates ->
                containerWidthPx = coordinates.size.width.toFloat()
                onArtworkPositioned(coordinates.boundsInRoot())
            }
            .pointerInput(currentTrack.videoId, queue.size) {
                detectHorizontalDragGestures(
                    onDragStart = { /* touch down */ },
                    onHorizontalDrag = { change, dragAmount ->
                        change.consume()
                        coroutineScope.launch {
                            val currentVal = dragOffset.value
                            // Rubber band resistance if dragging against edge of queue
                            val hasBoundary = (currentVal > 0 && prevTrack == null) ||
                                (currentVal < 0 && nextTrack == null && queue.size <= 1)
                            val resistance = if (hasBoundary) 0.32f else 1f
                            dragOffset.snapTo(currentVal + dragAmount * resistance)
                        }
                    },
                    onDragEnd = {
                        coroutineScope.launch {
                            val currentVal = dragOffset.value
                            val effectiveWidth = if (containerWidthPx > 0f) containerWidthPx else with(density) { 320.dp.toPx() }
                            if (currentVal < -swipeThresholdPx) {
                                // Dragged left -> Next Track
                                lastSwipeDirection = 1
                                dragOffset.animateTo(
                                    targetValue = -effectiveWidth - 48f,
                                    animationSpec = tween(180, easing = CubicBezierEasing(0.23f, 1f, 0.32f, 1f))
                                )
                                onSkipNext()
                            } else if (currentVal > swipeThresholdPx) {
                                // Dragged right -> Previous Track
                                lastSwipeDirection = -1
                                dragOffset.animateTo(
                                    targetValue = effectiveWidth + 48f,
                                    animationSpec = tween(180, easing = CubicBezierEasing(0.23f, 1f, 0.32f, 1f))
                                )
                                onSkipPrevious()
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
                model = currentTrack.highResArtworkUrl,
                contentDescription = currentTrack.title,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize()
            )
            overlayContent()
        }
    }
}

/**
 * 1. Blur Style - Liquid Glass Full Screen Player
 * Directly matches the reference image:
 * Maximized high-resolution artwork, ambient backdrop blur, frosted glass controls container,
 * solid Apple playback glyphs, AAC audio badge, and tactile horizontal point volume slider.
 */
@Composable
private fun LiquidGlassPlayerView(
    track: AuraTrack,
    queue: List<AuraTrack> = emptyList(),
    isPlaying: Boolean,
    isBuffering: Boolean,
    positionSec: Float,
    durationSec: Float,
    bufferedSec: Float = 0f,
    isFavorite: Boolean,
    isShuffleEnabled: Boolean,
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
    onOpenLyrics: () -> Unit,
    onAdjustLyricsOffset: (Int) -> Unit = {},
    onOpenComments: () -> Unit,
    onOpenQueue: () -> Unit,
    onCardPositioned: (Rect) -> Unit = {},
    onArtworkPositioned: (Rect) -> Unit = {}
) {
    val context = LocalContext.current
    val audioManager = remember { context.getSystemService(Context.AUDIO_SERVICE) as? AudioManager }
    val maxVol = remember { audioManager?.getStreamMaxVolume(AudioManager.STREAM_MUSIC) ?: 15 }
    var systemVolume by remember {
        mutableFloatStateOf(
            audioManager?.let { it.getStreamVolume(AudioManager.STREAM_MUSIC).toFloat() / maxVol.coerceAtLeast(1) } ?: 0.65f
        )
    }

    // Fully opaque solid black background
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
    ) {
        // 1. Full-screen Ambient Backdrop Blur Layer using high-res artwork
        AsyncImage(
            model = track.highResArtworkUrl,
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier
                .fillMaxSize()
                .blur(radius = 50.dp)
        )

        // Dark ambient overlay for contrast and legibility
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        colors = listOf(
                            Color.Black.copy(alpha = 0.22f),
                            Color.Black.copy(alpha = 0.40f),
                            Color.Black.copy(alpha = 0.85f)
                        )
                    )
                )
        )

        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding()
        ) {
            // Top Bar: Collapse, Playing from Header, and More Options Menu
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 16.dp, end = 16.dp, top = 6.dp, bottom = 8.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = onCollapse, modifier = Modifier.size(36.dp)) {
                    Icon(
                        imageVector = Icons.Rounded.KeyboardArrowDown,
                        contentDescription = "Collapse",
                        tint = Color.White,
                        modifier = Modifier.size(28.dp)
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
                        "Playing from $cleanAlbum"
                    } else {
                        "Playing from Playlist"
                    }
                    Text(
                        text = playingFrom,
                        fontSize = 10.5.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White.copy(alpha = 0.7f),
                        letterSpacing = 0.5.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = track.title,
                        style = MaterialTheme.typography.titleSmall,
                        color = Color.White,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                IconButton(onClick = onOptionsClick, modifier = Modifier.size(36.dp)) {
                    Icon(
                        imageVector = Icons.Rounded.MoreVert,
                        contentDescription = "More",
                        tint = Color.White,
                        modifier = Modifier.size(22.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            if (isVideoMode) {
                // Maximized edge-to-edge video player spanning the entire width preserving ambient blur backdrop
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                    contentAlignment = Alignment.Center
                ) {
                    AuraVideoPlayerSurface(
                        modifier = Modifier
                            .fillMaxWidth()
                            .aspectRatio(16f / 9f)
                            .clip(RoundedCornerShape(12.dp))
                            .onGloballyPositioned { onArtworkPositioned(it.boundsInRoot()) },
                        track = track,
                        isLoading = isVideoLoading,
                        onSwitchToAudio = onToggleVideoMode
                    )
                }
            } else {
                // 2. High Quality Artwork with generous, balanced breathing room
                BoxWithConstraints(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)
                        .padding(start = 24.dp, end = 24.dp, top = 8.dp, bottom = 12.dp),
                    contentAlignment = Alignment.Center
                ) {
                    // Dynamically maximize size while fitting within available vertical & horizontal bounds
                    val artSize = minOf(maxHeight, maxWidth)

                    AuraDraggableCoverArt(
                        currentTrack = track,
                        queue = queue,
                        cornerRadius = 22.dp,
                        hideArtworkForLyricsTransition = hideArtworkForLyricsTransition,
                        onSkipNext = onSkipNext,
                        onSkipPrevious = onSkipPrevious,
                        onArtworkPositioned = onArtworkPositioned,
                        modifier = Modifier.size(artSize),
                        overlayContent = {
                            // Soft bottom gradient mask on top of artwork for liquid blend into controls
                            Box(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .background(
                                        Brush.verticalGradient(
                                            colors = listOf(
                                                Color.Transparent,
                                                Color.Transparent,
                                                Color.Black.copy(alpha = 0.20f),
                                                Color.Black.copy(alpha = 0.65f)
                                            )
                                        )
                                    )
                            )
                        }
                    )
                }
            }

            // 3. Lower Frosted Glass Controls Container
            Surface(
                shape = RoundedCornerShape(28.dp),
                color = Color.White.copy(alpha = 0.10f),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 2.dp)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 18.dp, vertical = 14.dp)
                ) {
                    // Track Title & 3 Action Icons Row
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f).padding(end = 8.dp)) {
                            Text(
                                text = track.title.uppercase(),
                                style = MaterialTheme.typography.titleMedium,
                                color = Color.White,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                letterSpacing = 0.5.sp,
                                modifier = Modifier
                                    .clip(RoundedCornerShape(4.dp))
                                    .clickable(onClick = onOpenCurrentPlaylist)
                            )
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = track.artist.uppercase(),
                                style = MaterialTheme.typography.bodySmall,
                                color = Color.White.copy(alpha = 0.65f),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                letterSpacing = 0.6.sp,
                                modifier = Modifier
                                    .clip(RoundedCornerShape(4.dp))
                                    .clickable(onClick = onOpenArtist)
                            )
                        }

                        // Right 3 Actions: (+) Add, (★) Star Favorite, (⋮) More
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(2.dp)
                        ) {
                            IconButton(onClick = onOpenQueue, modifier = Modifier.size(34.dp)) {
                                Icon(
                                    imageVector = Icons.Rounded.AddCircleOutline,
                                    contentDescription = "Add to Queue",
                                    tint = Color.White.copy(alpha = 0.9f),
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                            IconButton(onClick = onToggleFavorite, modifier = Modifier.size(34.dp)) {
                                Icon(
                                    imageVector = if (isFavorite) Icons.Rounded.Star else Icons.Rounded.StarBorder,
                                    contentDescription = "Favorite",
                                    tint = if (isFavorite) Color(0xFFFFB74D) else Color.White.copy(alpha = 0.9f),
                                    modifier = Modifier.size(22.dp)
                                )
                            }
                            IconButton(onClick = onOptionsClick, modifier = Modifier.size(34.dp)) {
                                Icon(
                                    imageVector = Icons.Rounded.MoreVert,
                                    contentDescription = "More",
                                    tint = Color.White.copy(alpha = 0.9f),
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(4.dp))

                    // Horizontal Point Scrubber Slider
                    CompactPointSlider(
                        value = positionSec.coerceIn(0f, durationSec.coerceAtLeast(1f)),
                        bufferedValue = bufferedSec.coerceIn(0f, durationSec.coerceAtLeast(1f)),
                        onValueChange = onSeekTo,
                        valueRange = 0f..durationSec.coerceAtLeast(1f),
                        pointColor = Color.White,
                        activeTrackColor = Color.White,
                        modifier = Modifier.fillMaxWidth()
                    )

                    // Timestamps & Audio Codec Badge Row
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 4.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = formatDuration(positionSec.toInt()),
                            fontFamily = AuraPoppinsFontFamily,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Medium,
                            color = Color.White.copy(alpha = 0.65f)
                        )

                        // Audio Codec Glass Pill: [ •||• AAC ]
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = Color.White.copy(alpha = 0.12f),
                            border = BorderStroke(0.6.dp, Color.White.copy(alpha = 0.25f))
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(1.5.dp)
                                ) {
                                    Box(modifier = Modifier.width(2.dp).height(7.dp).background(Color.White, RoundedCornerShape(1.dp)))
                                    Box(modifier = Modifier.width(2.dp).height(11.dp).background(Color.White, RoundedCornerShape(1.dp)))
                                    Box(modifier = Modifier.width(2.dp).height(5.dp).background(Color.White, RoundedCornerShape(1.dp)))
                                }
                                Text(
                                    text = track.codec.ifBlank { "AAC" },
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Color.White,
                                    letterSpacing = 0.5.sp
                                )
                            }
                        }

                        Text(
                            text = "-" + formatDuration((durationSec - positionSec).toInt().coerceAtLeast(0)),
                            fontFamily = AuraPoppinsFontFamily,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Medium,
                            color = Color.White.copy(alpha = 0.65f)
                        )
                    }

                    Spacer(modifier = Modifier.height(6.dp))

                    // Solid Apple-style Playback Controls Row: Prev, Play/Pause, Next
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.Center,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        IconButton(onClick = onSkipPrevious, modifier = Modifier.size(54.dp)) {
                            Icon(
                                imageVector = Icons.Rounded.SkipPrevious,
                                contentDescription = "Previous Track",
                                tint = Color.White,
                                modifier = Modifier.size(34.dp)
                            )
                        }

                        Spacer(modifier = Modifier.width(32.dp))

                        IconButton(onClick = onTogglePlayPause, modifier = Modifier.size(62.dp)) {
                            if (isBuffering) {
                                CircularProgressIndicator(
                                    color = Color.White,
                                    strokeWidth = 3.dp,
                                    modifier = Modifier.size(32.dp)
                                )
                            } else {
                                Icon(
                                    imageVector = if (isPlaying) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
                                    contentDescription = if (isPlaying) "Pause" else "Play",
                                    tint = Color.White,
                                    modifier = Modifier.size(46.dp)
                                )
                            }
                        }

                        Spacer(modifier = Modifier.width(32.dp))

                        IconButton(onClick = onSkipNext, modifier = Modifier.size(54.dp)) {
                            Icon(
                                imageVector = Icons.Rounded.SkipNext,
                                contentDescription = "Next Track",
                                tint = Color.White,
                                modifier = Modifier.size(34.dp)
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(4.dp))

                    // Horizontal Point Volume Slider Row
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Rounded.VolumeDown,
                            contentDescription = "Volume Down",
                            tint = Color.White.copy(alpha = 0.6f),
                            modifier = Modifier.size(16.dp)
                        )
                        CompactPointSlider(
                            value = systemVolume,
                            onValueChange = { newVol ->
                                systemVolume = newVol
                                audioManager?.setStreamVolume(
                                    AudioManager.STREAM_MUSIC,
                                    (newVol * maxVol).toInt(),
                                    0
                                )
                            },
                            valueRange = 0f..1f,
                            pointColor = Color.White,
                            activeTrackColor = Color.White.copy(alpha = 0.9f),
                            modifier = Modifier
                                .weight(1f)
                                .padding(horizontal = 8.dp)
                        )
                        Icon(
                            imageVector = Icons.AutoMirrored.Rounded.VolumeUp,
                            contentDescription = "Volume Up",
                            tint = Color.White.copy(alpha = 0.6f),
                            modifier = Modifier.size(16.dp)
                        )
                    }

                    // Bottom Row: Lyrics, Shuffle, Queue
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 2.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        IconButton(onClick = onOpenLyrics, modifier = Modifier.size(40.dp)) {
                            Icon(
                                imageVector = Icons.Rounded.Audiotrack,
                                contentDescription = "Lyrics",
                                tint = Color.White.copy(alpha = 0.8f),
                                modifier = Modifier.size(20.dp)
                            )
                        }

                        IconButton(onClick = onOpenComments, modifier = Modifier.size(40.dp)) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Rounded.Comment,
                                contentDescription = "Comments",
                                tint = Color.White.copy(alpha = 0.8f),
                                modifier = Modifier.size(20.dp)
                            )
                        }

                        IconButton(onClick = onToggleShuffle, modifier = Modifier.size(40.dp)) {
                            Icon(
                                imageVector = Icons.Rounded.Shuffle,
                                contentDescription = "Shuffle",
                                tint = if (isShuffleEnabled) AuraPlayGreen else Color.White.copy(alpha = 0.6f),
                                modifier = Modifier.size(20.dp)
                            )
                        }

                        IconButton(onClick = onOpenQueue, modifier = Modifier.size(40.dp)) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Rounded.QueueMusic,
                                contentDescription = "Queue",
                                tint = Color.White.copy(alpha = 0.8f),
                                modifier = Modifier.size(22.dp)
                            )
                        }
                    }
                }
            }
        }
    }
}

/**
 * 2. Spotify Encore Full Screen Player View
 */
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
private fun SpotifyEncorePlayerView(
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
                        AuraVideoPlayerSurface(
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
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier
                            .clip(RoundedCornerShape(4.dp))
                            .clickable(onClick = onOpenCurrentPlaylist)
                    )
                    Spacer(modifier = Modifier.height(3.dp))
                    Text(
                        text = track.artist,
                        style = MaterialTheme.typography.bodyMedium,
                        color = Color.White.copy(alpha = 0.7f),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier
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
                    if (isBuffering) {
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

            Spacer(modifier = Modifier.height(20.dp))

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
                                        color = if (lyricsOffsetMs != 0) AuraNeonLime else Color.White,
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

                            // Lyrics Delayer / Time Setter Toggle Button (SimpMusic)
                            IconButton(
                                onClick = { showLyricsOffsetControl = !showLyricsOffsetControl },
                                modifier = Modifier.size(36.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.Timer,
                                    contentDescription = "Lyrics Timing Offset",
                                    tint = if (lyricsOffsetMs != 0 || showLyricsOffsetControl) AuraNeonLime else Color.White.copy(alpha = 0.85f),
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
                modifier = Modifier
                    .fillMaxWidth()
            )

            Spacer(modifier = Modifier.height(36.dp))
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
                        contentDescription = if (showRomanized) "Romaji lyrics active (tap for original)" else "Original lyrics active (tap for romaji)",
                        tint = if (showRomanized) AuraPlayGreen else Color.White.copy(alpha = 0.82f),
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
                                color = if (lyricsOffsetMs != 0) AuraNeonLime else Color.White,
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
                            contentDescription = "Lyrics Timing Offset",
                            tint = if (lyricsOffsetMs != 0 || showOffsetControl) AuraNeonLime else Color.White.copy(alpha = 0.85f),
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
                    if (isBuffering) {
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
    onSeekTo: (Float) -> Unit,
    onTogglePlayPause: () -> Unit,
    onSkipPrevious: () -> Unit,
    onSkipNext: () -> Unit,
    onOpenQueue: () -> Unit = {},
    onAdjustLyricsOffset: (Int) -> Unit
) {
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
                                if (isBuffering) {
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
        if (isLoading) "Loading video details…" else "Video description is not available."
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
                    color = Color.White.copy(alpha = 0.68f), fontSize = 12.sp
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
                    tint = if (isFavorite) AuraNeonLime else Color.White,
                    modifier = Modifier.size(22.dp)
                )
            }
            IconButton(onClick = onTogglePlayPause, modifier = Modifier.size(42.dp)) {
                if (isBuffering) {
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

/**
 * Upcoming Queue Sheet matching Spotify flat minimalist UX:
 * - Real Mobile Bottom Sheet Modal anchored to the bottom with full dimming scrim
 * - Flat transparent list items with zero borders
 * - Drag to reorder via holding ANY tile OR dragging the handle icon
 * - Damped spring reorder animation with tactile haptic feedback
 * - Auto-scroll when dragging near viewport boundaries
 */
@Composable
private fun QueueSheetContent(
    currentTrack: AuraTrack,
    queue: List<AuraTrack>,
    isPlaying: Boolean = false,
    modalContainerColor: Color,
    onPlayTrack: (AuraTrack) -> Unit,
    onReorderQueue: (List<AuraTrack>) -> Unit
) {
    val curIdx = queue.indexOfFirst { it.videoId == currentTrack.videoId }
    val upcomingTracks = remember(queue, currentTrack.videoId) {
        if (curIdx >= 0 && curIdx < queue.lastIndex) {
            queue.subList(curIdx + 1, queue.size)
        } else {
            queue.filterNot { it.videoId == currentTrack.videoId }
        }
    }

    var upcomingList by remember(upcomingTracks) { mutableStateOf(upcomingTracks) }

    // Decoupled UI & Data: List data remains frozen during drag so LazyColumn never re-layouts or scrolls unexpectedly
    var draggedIndex by remember { mutableStateOf<Int?>(null) }
    var targetDropIndex by remember { mutableStateOf<Int?>(null) }
    var dragOffsetY by remember { mutableFloatStateOf(0f) }

    val density = LocalDensity.current
    val haptics = LocalHapticFeedback.current
    val coroutineScope = rememberCoroutineScope()
    val lazyListState = rememberLazyListState()
    val tileHeightPx = with(density) { 56.dp.toPx() }

    fun startDrag(itemId: String) {
        val currentIdx = upcomingList.indexOfFirst { (it.videoId.ifBlank { it.id }) == itemId }
        if (currentIdx < 0) return
        haptics.performHapticFeedback(HapticFeedbackType.LongPress)
        draggedIndex = currentIdx
        targetDropIndex = currentIdx
        dragOffsetY = 0f
    }

    fun handleDragDelta(deltaY: Float) {
        val current = draggedIndex ?: return
        dragOffsetY += deltaY

        val deltaSlots = (dragOffsetY / tileHeightPx).roundToInt()
        val newTarget = (current + deltaSlots).coerceIn(0, upcomingList.lastIndex)
        if (newTarget != targetDropIndex) {
            targetDropIndex = newTarget
            haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
        }

        // Auto-scroll when dragged near top or bottom of viewport
        val layoutInfo = lazyListState.layoutInfo
        val visible = layoutInfo.visibleItemsInfo
        val itemInfo = visible.firstOrNull { it.index == current }
        if (itemInfo != null) {
            val viewportHeight = layoutInfo.viewportEndOffset - layoutInfo.viewportStartOffset
            val topInViewport = itemInfo.offset + dragOffsetY
            val bottomInViewport = topInViewport + itemInfo.size
            if (topInViewport < 48f) {
                coroutineScope.launch {
                    lazyListState.scrollBy(-18f)
                }
            } else if (bottomInViewport > viewportHeight - 48f) {
                coroutineScope.launch {
                    lazyListState.scrollBy(18f)
                }
            }
        }
    }

    fun commitDrop() {
        val current = draggedIndex
        val target = targetDropIndex
        if (current != null && target != null && current != target &&
            current in upcomingList.indices && target in upcomingList.indices
        ) {
            val mutable = upcomingList.toMutableList()
            val moved = mutable.removeAt(current)
            mutable.add(target, moved)
            upcomingList = mutable
            onReorderQueue(mutable)
        }
        draggedIndex = null
        targetDropIndex = null
        dragOffsetY = 0f
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 20.dp, vertical = 6.dp)
    ) {
        Text(
            text = "Queue",
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold,
            color = Color.White
        )

        Spacer(modifier = Modifier.height(10.dp))

        Text(
            text = "NOW PLAYING",
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.Bold,
            color = AuraNeonLime,
            letterSpacing = 1.sp
        )

        Spacer(modifier = Modifier.height(6.dp))

        // Flat "Now Playing" Tile (Zero Border, Flat Transparent Design)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(44.dp)
                    .clip(RoundedCornerShape(6.dp))
                    .background(Color(0xFF202127)),
                contentAlignment = Alignment.Center
            ) {
                AsyncImage(
                    model = currentTrack.highResArtworkUrl,
                    contentDescription = currentTrack.title,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize()
                )
                if (isPlaying) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(Color.Black.copy(alpha = 0.55f)),
                        contentAlignment = Alignment.Center
                    ) {
                        AudioPlayingIndicator(
                            modifier = Modifier.fillMaxSize(0.72f),
                            color = AuraNeonLime
                        )
                    }
                }
            }
            Spacer(modifier = Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = currentTrack.title,
                    color = AuraNeonLime,
                    style = MaterialTheme.typography.titleSmall,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = currentTrack.artist,
                    color = Color.White.copy(alpha = 0.70f),
                    style = MaterialTheme.typography.bodySmall,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            Spacer(modifier = Modifier.width(8.dp))
            Row(
                modifier = Modifier
                    .clip(RoundedCornerShape(12.dp))
                    .background(AuraNeonLime.copy(alpha = 0.15f))
                    .padding(horizontal = 8.dp, vertical = 3.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = Icons.Rounded.PlayArrow,
                    contentDescription = null,
                    tint = AuraNeonLime,
                    modifier = Modifier.size(12.dp)
                )
                Spacer(modifier = Modifier.width(3.dp))
                Text(
                    text = "Playing",
                    color = AuraNeonLime,
                    fontSize = 10.5.sp,
                    fontWeight = FontWeight.ExtraBold
                )
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "UP NEXT",
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.Bold,
                color = TextSecondary,
                letterSpacing = 1.sp
            )
            if (upcomingList.isNotEmpty()) {
                Text(
                    text = "${upcomingList.size} upcoming",
                    style = MaterialTheme.typography.labelSmall,
                    color = TextSecondary,
                    fontSize = 11.sp
                )
            }
        }

        Spacer(modifier = Modifier.height(4.dp))

        if (upcomingList.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "End of queue. Radio songs will load automatically.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = TextSecondary,
                    textAlign = TextAlign.Center
                )
            }
        } else {
            LazyColumn(
                state = lazyListState,
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                contentPadding = PaddingValues(bottom = 24.dp)
            ) {
                itemsIndexed(
                    items = upcomingList,
                    key = { _, item -> item.videoId.ifBlank { item.id } }
                ) { index, item ->
                    val itemKey = item.videoId.ifBlank { item.id }
                    val isDraggingThis = draggedIndex == index
                    val currentDrag = draggedIndex
                    val currentTarget = targetDropIndex

                    // Physics engine spring displacement: surrounding items glide out of the way smoothly
                    val itemDisplacementY by animateFloatAsState(
                        targetValue = when {
                            currentDrag == null || currentTarget == null -> 0f
                            index == currentDrag -> 0f
                            // Dragging UP: items between currentTarget and currentDrag glide DOWN (+tileHeightPx)
                            currentDrag > currentTarget && index in currentTarget until currentDrag -> tileHeightPx
                            // Dragging DOWN: items between currentDrag and currentTarget glide UP (-tileHeightPx)
                            currentDrag < currentTarget && index in (currentDrag + 1)..currentTarget -> -tileHeightPx
                            else -> 0f
                        },
                        animationSpec = spring(
                            dampingRatio = Spring.DampingRatioLowBouncy,
                            stiffness = Spring.StiffnessMediumLow
                        ),
                        label = "disp_$index"
                    )

                    val animatedElevation by animateDpAsState(
                        targetValue = if (isDraggingThis) 12.dp else 0.dp,
                        animationSpec = spring(
                            dampingRatio = Spring.DampingRatioNoBouncy,
                            stiffness = Spring.StiffnessMedium
                        ),
                        label = "elev_$index"
                    )
                    val animatedScale by animateFloatAsState(
                        targetValue = if (isDraggingThis) 1.03f else 1.0f,
                        animationSpec = spring(
                            dampingRatio = Spring.DampingRatioNoBouncy,
                            stiffness = Spring.StiffnessMedium
                        ),
                        label = "scale_$index"
                    )

                    Surface(
                        shape = RoundedCornerShape(10.dp),
                        // Dragged tile uses SOLID light gray color (not transparent)
                        color = if (isDraggingThis) Color(0xFF3A3D46) else Color.Transparent,
                        border = null,
                        shadowElevation = animatedElevation,
                        modifier = Modifier
                            .fillMaxWidth()
                            .zIndex(if (isDraggingThis) 25f else 1f)
                            .graphicsLayer {
                                translationY = if (isDraggingThis) dragOffsetY else itemDisplacementY
                                scaleX = animatedScale
                                scaleY = animatedScale
                            }
                            // Holding anywhere on the song tile activates drag mode
                            .pointerInput(itemKey) {
                                detectDragGesturesAfterLongPress(
                                    onDragStart = { startDrag(itemKey) },
                                    onDrag = { change, dragAmount ->
                                        change.consume()
                                        handleDragDelta(dragAmount.y)
                                    },
                                    onDragEnd = { commitDrop() },
                                    onDragCancel = { commitDrop() }
                                )
                            }
                            .clickable {
                                if (draggedIndex == null) {
                                    onPlayTrack(item)
                                }
                            }
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 4.dp, vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            AsyncImage(
                                model = item.highResArtworkUrl,
                                contentDescription = item.title,
                                contentScale = ContentScale.Crop,
                                modifier = Modifier
                                    .size(44.dp)
                                    .clip(RoundedCornerShape(6.dp))
                            )
                            Spacer(modifier = Modifier.width(12.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = item.title,
                                    fontWeight = FontWeight.SemiBold,
                                    color = if (isDraggingThis) AuraNeonLime else Color.White,
                                    fontSize = 13.5.sp,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Spacer(modifier = Modifier.height(2.dp))
                                Text(
                                    text = item.artist,
                                    color = TextSecondary,
                                    fontSize = 11.5.sp,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                            Spacer(modifier = Modifier.width(8.dp))

                            // Drag Handle Icon on the right: Immediate touch & drag without waiting for long-press
                            Box(
                                modifier = Modifier
                                    .size(44.dp)
                                    .pointerInput(itemKey) {
                                        detectDragGestures(
                                            onDragStart = { startDrag(itemKey) },
                                            onDrag = { change, dragAmount ->
                                                change.consume()
                                                handleDragDelta(dragAmount.y)
                                            },
                                            onDragEnd = { commitDrop() },
                                            onDragCancel = { commitDrop() }
                                        )
                                    },
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.DragHandle,
                                    contentDescription = "Drag to reorder",
                                    tint = if (isDraggingThis) AuraNeonLime else Color.White.copy(alpha = 0.45f),
                                    modifier = Modifier.size(22.dp)
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
