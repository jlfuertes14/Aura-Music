package com.auramusic.app.ui.screens

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.MutableTransitionState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandHorizontally
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkHorizontally
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.MarqueeAnimationMode
import androidx.compose.foundation.background
import androidx.compose.foundation.basicMarquee
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Home
import androidx.compose.material.icons.rounded.LibraryMusic
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.SkipNext
import androidx.compose.material.icons.rounded.SkipPrevious
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.BlurredEdgeTreatment
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.rememberGraphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.zIndex
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.auramusic.app.ui.components.AudioPlayingIndicator
import com.auramusic.app.ui.components.AuraLiquidGlassTabBar
import com.auramusic.app.ui.components.AuraNavTabItem
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.auramusic.app.core.media.AuraPlayerManager
import com.auramusic.app.core.update.AuraUpdateManager
import com.auramusic.app.domain.model.ArtistCarouselItem
import com.auramusic.app.domain.model.AuraTrack
import com.auramusic.app.ui.AuraViewModel
import com.auramusic.app.ui.DetailScreenEntry
import com.auramusic.app.ui.components.AuraUpdateModal
import com.auramusic.app.ui.screens.player.applemusic.appleMusicVerticalFadeEdges
import com.auramusic.app.ui.screens.player.applemusic.prewarmAppleMusicArtwork
import com.auramusic.app.ui.theme.AuraNeonLime
import com.auramusic.app.ui.theme.AuraPlayGreen
import com.auramusic.app.ui.theme.AuraViolet
import com.auramusic.app.ui.theme.LocalGlassBackdrop
import com.auramusic.app.ui.theme.LocalIsDarkTheme
import com.auramusic.app.ui.theme.LocalLiquidGlassEnabled
import com.auramusic.app.ui.theme.SpotifyCard
import com.auramusic.app.ui.theme.SpotifyPitchBlack
import com.auramusic.app.ui.theme.TextPrimary
import com.auramusic.app.ui.theme.TextSecondary
import com.auramusic.app.ui.theme.glassBackdropSource
import com.auramusic.app.ui.theme.isLyricsBlurSupported
import com.auramusic.app.ui.theme.liquidGlass
import com.auramusic.app.ui.theme.rememberGlassBackdrop
import com.auramusic.app.ui.theme.rememberGlassLuminance
import com.kyant.backdrop.backdrops.LayerBackdrop
import kotlin.math.abs
import kotlin.math.roundToInt

private data class RenderedDetailEntry(
    val entry: DetailScreenEntry,
    val transitionState: MutableTransitionState<Boolean>
)

private class NonUniformScaledRoundedShape(
    private val cornerRadiusPx: Float,
    private val scaleX: Float,
    private val scaleY: Float
) : androidx.compose.ui.graphics.Shape {
    override fun createOutline(
        size: androidx.compose.ui.geometry.Size,
        layoutDirection: androidx.compose.ui.unit.LayoutDirection,
        density: androidx.compose.ui.unit.Density
    ): androidx.compose.ui.graphics.Outline {
        val rx = (cornerRadiusPx / scaleX.coerceAtLeast(0.04f)).coerceAtMost(size.width / 2f)
        val ry = (cornerRadiusPx / scaleY.coerceAtLeast(0.04f)).coerceAtMost(size.height / 2f)
        val path = androidx.compose.ui.graphics.Path().apply {
            addRoundRect(
                androidx.compose.ui.geometry.RoundRect(
                    left = 0f,
                    top = 0f,
                    right = size.width,
                    bottom = size.height,
                    cornerRadius = androidx.compose.ui.geometry.CornerRadius(rx, ry)
                )
            )
        }
        return androidx.compose.ui.graphics.Outline.Generic(path)
    }
}

@Composable
fun AuraMainScreen(viewModel: AuraViewModel) {
    var selectedTab by remember { mutableIntStateOf(0) }
    var isPlayerExpanded by remember { mutableStateOf(false) }
    var isPlayerCollapseTransition by remember { mutableStateOf(false) }
    var isPlayerExpandTransition by remember { mutableStateOf(false) }
    var fullPlayerArtworkBounds by remember { mutableStateOf<Rect?>(null) }
    var miniPlayerArtworkBounds by remember { mutableStateOf<Rect?>(null) }
    var fullPlayerContainerBounds by remember { mutableStateOf<Rect?>(null) }
    var miniPlayerContainerBounds by remember { mutableStateOf<Rect?>(null) }
    var playerSheetDragOffsetY by remember { mutableFloatStateOf(0f) }
    var collapseStartDragY by remember { mutableFloatStateOf(0f) }
    val collapseProgress = remember { Animatable(1f) }
    val isVideoMode by viewModel.isVideoMode.collectAsState()
    val playerChromePushProgress = when {
        isPlayerCollapseTransition || isPlayerExpandTransition -> 1f - collapseProgress.value
        isPlayerExpanded -> 1f
        else -> 0f
    }.coerceIn(0f, 1f)
    val screenScope = rememberCoroutineScope()
    var openedPlaylistFromHome by remember { mutableStateOf(false) }
    val lifecycleOwner = LocalLifecycleOwner.current

    val expandFullPlayer: () -> Unit = {
        screenScope.launch {
            playerSheetDragOffsetY = 0f
            collapseStartDragY = 0f
            collapseProgress.snapTo(1f)
            isPlayerCollapseTransition = false
            isPlayerExpandTransition = true
            isPlayerExpanded = true
        }
    }

    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                isPlayerCollapseTransition = false
                isPlayerExpandTransition = false
                playerSheetDragOffsetY = 0f
                collapseStartDragY = 0f
                screenScope.launch {
                    collapseProgress.snapTo(if (isPlayerExpanded) 0f else 1f)
                }
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    LaunchedEffect(isPlayerCollapseTransition) {
        if (isPlayerCollapseTransition) {
            collapseProgress.animateTo(
                targetValue = 1f,
                animationSpec = tween(440, easing = CubicBezierEasing(0.4f, 0f, 0.2f, 1f))
            )
            isPlayerCollapseTransition = false
            collapseStartDragY = 0f
        }
    }

    LaunchedEffect(
        isPlayerExpandTransition,
        fullPlayerContainerBounds != null,
        fullPlayerArtworkBounds != null,
        isVideoMode
    ) {
        if (isPlayerExpandTransition) {
            val isReady = if (isVideoMode) {
                fullPlayerContainerBounds != null
            } else {
                fullPlayerContainerBounds != null && fullPlayerArtworkBounds != null
            }
            if (isReady) {
                collapseProgress.animateTo(
                    targetValue = 0f,
                    animationSpec = tween(440, easing = CubicBezierEasing(0.4f, 0f, 0.2f, 1f))
                )
                isPlayerExpandTransition = false
            } else if (fullPlayerContainerBounds != null) {
                // Safety fallback: if artwork bounds measurement is delayed (e.g. video transition or quick tap),
                // proceed with expansion so the player NEVER deadlocks
                kotlinx.coroutines.delay(120)
                if (isPlayerExpandTransition) {
                    collapseProgress.animateTo(
                        targetValue = 0f,
                        animationSpec = tween(440, easing = CubicBezierEasing(0.4f, 0f, 0.2f, 1f))
                    )
                    isPlayerExpandTransition = false
                }
            } else {
                // Fallback: if container measurement takes longer than 250ms, snap open
                kotlinx.coroutines.delay(250)
                if (isPlayerExpandTransition) {
                    collapseProgress.snapTo(0f)
                    isPlayerExpandTransition = false
                }
            }
        }
    }

    val focusManager = LocalFocusManager.current
    val keyboardController = LocalSoftwareKeyboardController.current

    // Clear any lingering search input focus whenever expanding the player or switching tabs
    LaunchedEffect(isPlayerExpanded, selectedTab) {
        if (isPlayerExpanded || selectedTab != 1) {
            focusManager.clearFocus(force = true)
            keyboardController?.hide()
        }
    }

    val chartSections by viewModel.chartSections.collectAsState()
    val topArtists by viewModel.topArtists.collectAsState()
    val communityPlaylists by viewModel.communityPlaylists.collectAsState()
    val homeMusicVideos by viewModel.homeMusicVideos.collectAsState()
    val nowPlayingArtistDetail by viewModel.nowPlayingArtistDetail.collectAsState()
    val nowPlayingVideoInfo by viewModel.nowPlayingVideoInfo.collectAsState()
    val isNowPlayingArtistLoading by viewModel.isNowPlayingArtistLoading.collectAsState()
    val playerComments by viewModel.playerComments.collectAsState()
    val areCommentsLoading by viewModel.areCommentsLoading.collectAsState()
    val commentsError by viewModel.commentsError.collectAsState()
    val playlistScrollTargetTrackId by viewModel.playlistScrollTargetTrackId.collectAsState()
    val detailStack by viewModel.detailStack.collectAsState()

    BackHandler(enabled = !isPlayerExpanded && detailStack.isEmpty() && selectedTab != 0) {
        selectedTab = 0
    }
    val activeCategory by viewModel.activeCategory.collectAsState()
    val homeCategoryBackground by viewModel.homeCategoryBackground.collectAsState()
    val categoryTracks by viewModel.categoryTracks.collectAsState()
    val categoryShelves by viewModel.categoryShelves.collectAsState()
    val homeRecommendations by viewModel.homeRecommendations.collectAsState()
    val homeAlbumRecommendations by viewModel.homeAlbumRecommendations.collectAsState()
    val homeLikedRecommendations by viewModel.homeLikedRecommendations.collectAsState()
    val isCategoryLoading by viewModel.isCategoryLoading.collectAsState()
    val moodCategories by viewModel.moodCategories.collectAsState()
    val isLoadingHome by viewModel.isLoadingHome.collectAsState()
    val selectedChartCountry by viewModel.selectedChartCountry.collectAsState()
    val chartData by viewModel.chartData.collectAsState()
    val isChartLoading by viewModel.isChartLoading.collectAsState()

    val searchQuery by viewModel.searchQuery.collectAsState()
    val searchFilter by viewModel.searchFilter.collectAsState()
    val searchResults by viewModel.searchResults.collectAsState()
    val searchSuggestions by viewModel.searchSuggestions.collectAsState()
    val isSearching by viewModel.isSearching.collectAsState()
    val searchMatchedArtist by viewModel.searchMatchedArtist.collectAsState()
    val searchMatchedArtists by viewModel.searchMatchedArtists.collectAsState()
    val artistFeaturingItems by viewModel.artistFeaturingItems.collectAsState()
    val searchPlaylistResults by viewModel.searchPlaylistResults.collectAsState()
    val searchExploreCoverTracks by viewModel.searchExploreCoverTracks.collectAsState()
    val spotifyImportStatus by viewModel.spotifyImportStatus.collectAsState()
    val importedPlaylistSuccess by viewModel.importedPlaylistSuccess.collectAsState()
    val selectedUserPlaylist by viewModel.selectedUserPlaylist.collectAsState()

    val favorites by viewModel.favorites.collectAsState()
    val downloads by viewModel.downloads.collectAsState()
    val playlists by viewModel.playlists.collectAsState()
    val recentlyPlayed by viewModel.recentlyPlayed.collectAsState()
    val visibleTopArtists by viewModel.visibleTopArtists.collectAsState()
    val visibleRecentlyPlayed by viewModel.visibleRecentlyPlayed.collectAsState()
    val visibleUserPlaylists by viewModel.visibleUserPlaylists.collectAsState()
    val listeningStats by viewModel.listeningStats.collectAsState()
    val downloadingIds by viewModel.downloadingIds.collectAsState()
    val isDownloadPaused by viewModel.isDownloadPaused.collectAsState()

    LaunchedEffect(selectedTab) {
        if (selectedTab == 1) viewModel.loadSearchExploreCoverTracks()
    }

    val currentTrack by AuraPlayerManager.currentTrack.collectAsState()
    val isPlaying by AuraPlayerManager.isPlaying.collectAsState()
    val isBuffering by AuraPlayerManager.isBuffering.collectAsState()
    val positionSec by AuraPlayerManager.positionSec.collectAsState()
    val bufferedSec by AuraPlayerManager.bufferedSec.collectAsState()
    val durationSec by AuraPlayerManager.durationSec.collectAsState()
    val isShuffleEnabled by AuraPlayerManager.isShuffleEnabled.collectAsState()
    val queue by AuraPlayerManager.queue.collectAsState()
    val streamSourceTier by AuraPlayerManager.streamSourceTier.collectAsState()
    val sponsorSegments by AuraPlayerManager.sponsorSegments.collectAsState()
    val lastSkippedSegment by AuraPlayerManager.lastSkippedSegment.collectAsState()
    val activeEqPreset by AuraPlayerManager.activeEqPreset.collectAsState()
    val eqEnabled by AuraPlayerManager.eqEnabled.collectAsState()
    val sponsorBlockEnabled by AuraPlayerManager.sponsorBlockEnabled.collectAsState()
    val sleepTimerRemainingMin by AuraPlayerManager.sleepTimerRemainingMin.collectAsState()

    val palette by viewModel.palette.collectAsState()
    val lyrics by viewModel.lyrics.collectAsState()
    val lyricsOffsetMs by viewModel.lyricsOffsetMs.collectAsState()
    val showRomanized by viewModel.showRomanizedLyrics.collectAsState()
    val rydData by viewModel.rydData.collectAsState()
    val nowPlayingStyle by viewModel.nowPlayingStyle.collectAsState()
    val isVideoLoading by viewModel.isVideoLoading.collectAsState()
    val livePerformances by viewModel.livePerformances.collectAsState()
    val isLoadingLivePerformances by viewModel.isLoadingLivePerformances.collectAsState()
    val downloadProgress by viewModel.downloadProgress.collectAsState()

    val collapseFullPlayer: () -> Unit = {
        if (isPlayerExpanded && !isPlayerCollapseTransition && !isPlayerExpandTransition) {
            collapseStartDragY = playerSheetDragOffsetY
            playerSheetDragOffsetY = 0f
            screenScope.launch {
                collapseProgress.snapTo(0f)
                isPlayerCollapseTransition = true
                isPlayerExpanded = false
            }
        }
    }

    val context = LocalContext.current
    LaunchedEffect(currentTrack?.highResArtworkUrl) {
        val artworkUrl = currentTrack?.highResArtworkUrl ?: return@LaunchedEffect
        prewarmAppleMusicArtwork(context, artworkUrl)
    }
    var pendingDownloadAction by remember { mutableStateOf<(() -> Unit)?>(null) }

    val storagePermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        val anyGranted = permissions.values.any { it }
        if (anyGranted) {
            pendingDownloadAction?.invoke()
        } else {
            Toast.makeText(context, "Storage permission is needed to save offline music", Toast.LENGTH_SHORT).show()
        }
        pendingDownloadAction = null
    }

    val requestDownloadWithPermission: (() -> Unit) -> Unit = { action ->
        val neededPermissions = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            arrayOf(Manifest.permission.READ_MEDIA_AUDIO)
        } else {
            arrayOf(Manifest.permission.WRITE_EXTERNAL_STORAGE)
        }
        val ungranted = neededPermissions.filter {
            ContextCompat.checkSelfPermission(context, it) != PackageManager.PERMISSION_GRANTED
        }
        if (ungranted.isEmpty()) {
            action()
        } else {
            pendingDownloadAction = action
            storagePermissionLauncher.launch(ungranted.toTypedArray())
        }
    }

    val glassBackdrop = rememberGlassBackdrop(SpotifyPitchBlack)
    val liquidGlassEnabled = LocalLiquidGlassEnabled.current
    val dockGlassLayer = rememberGraphicsLayer()
    val dockLuminance = rememberGlassLuminance(
        source = dockGlassLayer,
        enabled = liquidGlassEnabled && !isPlayerExpanded && !isPlayerExpandTransition,
    )

    CompositionLocalProvider(LocalGlassBackdrop provides glassBackdrop) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(SpotifyPitchBlack)
    ) {
        // Sibling backdrop source: contains ONLY the main tabs + detail navigation stack.
        // Never place any .liquidGlass(...) surface inside this Box to prevent RuntimeShader feedback loops.
        Box(
            modifier = Modifier
                .fillMaxSize()
                .glassBackdropSource(glassBackdrop)
        ) {
        // 1. Screens Content (full screen underneath floating controls) with directional slide transitions
        val saveableStateHolder = rememberSaveableStateHolder()
        val navSlideEasing = remember { CubicBezierEasing(0.23f, 1f, 0.32f, 1f) }
        val mainContentOffsetX by animateFloatAsState(
            targetValue = if (detailStack.isNotEmpty()) -1f else 0f,
            animationSpec = tween(durationMillis = 270, easing = navSlideEasing),
            label = "MainContentSlideOffset"
        )
        AnimatedContent(
            targetState = selectedTab,
            transitionSpec = {
                val direction = if (targetState > initialState) 1 else -1
                (slideInHorizontally(
                    animationSpec = tween(durationMillis = 260, easing = navSlideEasing),
                    initialOffsetX = { fullWidth -> (fullWidth * 0.24f * direction).toInt() }
                ) + fadeIn(
                    animationSpec = tween(durationMillis = 220, easing = navSlideEasing)
                )).togetherWith(
                    slideOutHorizontally(
                        animationSpec = tween(durationMillis = 240, easing = navSlideEasing),
                        targetOffsetX = { fullWidth -> (-fullWidth * 0.24f * direction).toInt() }
                    ) + fadeOut(
                        animationSpec = tween(durationMillis = 170, easing = navSlideEasing)
                    )
                ).using(SizeTransform(clip = false))
            },
            label = "MainNavigationSlideTransition",
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer {
                    translationX = size.width * mainContentOffsetX
                }
        ) { tab ->
            saveableStateHolder.SaveableStateProvider(key = tab) {
                when (tab) {
                    0 -> HomeScreen(
                        chartSections = chartSections,
                        moodCategories = moodCategories,
                        isLoading = isLoadingHome,
                        palette = palette,
                        favorites = favorites,
                        downloads = downloads,
                        downloadingIds = downloadingIds,
                        downloadProgressMap = downloadProgress,
                        currentPlayingId = currentTrack?.videoId,
                        isPlaying = isPlaying,
                        recentlyPlayed = visibleRecentlyPlayed,
                        homeRecommendations = homeRecommendations,
                        albumRecommendations = homeAlbumRecommendations,
                        likedRecommendations = homeLikedRecommendations,
                        userPlaylists = visibleUserPlaylists,
                        topArtists = visibleTopArtists,
                        communityPlaylists = communityPlaylists,
                        musicVideos = homeMusicVideos,
                        activeCategory = activeCategory,
                        homeCategoryBackground = homeCategoryBackground,
                        categoryTracks = categoryTracks,
                        categoryShelves = categoryShelves,
                        isCategoryLoading = isCategoryLoading,
                        onPlayTrack = { track, queue -> viewModel.playTrack(track, queue) },
                        onPlayMusicVideo = { track, queue ->
                            viewModel.playHomeMusicVideo(track, queue)
                            expandFullPlayer()
                        },
                        onPlayArtist = { artist -> viewModel.playArtist(artist) },
                        onOpenArtist = { artist -> viewModel.openArtistDetail(artist) },
                        onPlayCommunityPlaylist = { playlist -> viewModel.playCommunityPlaylist(playlist) },
                        onOpenPlaylist = { playlist -> viewModel.openPlaylistDetail(playlist) },
                        onOpenAlbum = { track -> viewModel.openAlbum(track) },
                        onOpenUserPlaylist = { pl ->
                            openedPlaylistFromHome = true
                            selectedTab = 2
                            viewModel.selectUserPlaylist(pl)
                        },
                        onSelectCategory = { category -> viewModel.selectCategory(category) },
                        onPlayMood = { mood -> viewModel.playMoodCategory(mood) },
                        onToggleFavorite = { track -> viewModel.toggleFavorite(track) },
                        onDownloadTrack = { track -> requestDownloadWithPermission { viewModel.downloadTrack(track) } },
                        onRefresh = { viewModel.refreshHome() },
                        onOpenArtistByName = { artistName, songTitle -> viewModel.openArtistByName(artistName, songTitle) },
                        onAddTrackToPlaylist = { plId, track -> viewModel.addTrackToPlaylist(plId, track) },
                        onCreatePlaylist = { name, desc, cover -> viewModel.createPlaylist(name, desc, cover) },
                        chartData = chartData,
                        selectedChartCountry = selectedChartCountry,
                        isChartLoading = isChartLoading,
                        onSelectChartCountry = { countryCode -> viewModel.selectChartCountry(countryCode) }
                    )
                    1 -> Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .statusBarsPadding()
                    ) {
                        SearchScreen(
                            query = searchQuery,
                            filter = searchFilter,
                            results = searchResults,
                            suggestions = searchSuggestions,
                            isSearching = isSearching,
                            favorites = favorites,
                            downloads = downloads,
                            downloadingIds = downloadingIds,
                            recentlyPlayed = recentlyPlayed,
                            matchedArtist = searchMatchedArtist,
                            matchedArtists = searchMatchedArtists,
                            artistFeaturingItems = artistFeaturingItems,
                            playlistResults = searchPlaylistResults,
                            exploreArtworkTracks = searchExploreCoverTracks,
                            currentPlayingId = currentTrack?.videoId,
                            isPlaying = isPlaying,
                            onQueryChange = { viewModel.updateSearchQuery(it) },
                            onFilterChange = { viewModel.setSearchFilter(it) },
                            onPlayTrack = { track, queue -> viewModel.playTrack(track, queue) },
                            onPlaySearchTrack = { track, results -> viewModel.playSearchTrack(track, results) },
                            onPlayMusicVideo = { track, queue ->
                                viewModel.playHomeMusicVideo(track, queue)
                                expandFullPlayer()
                            },
                            onToggleFavorite = { viewModel.toggleFavorite(it) },
                            onDownloadTrack = { track -> requestDownloadWithPermission { viewModel.downloadTrack(track) } },
                            playlists = playlists,
                            onAddTrackToPlaylist = { plId, track -> viewModel.addTrackToPlaylist(plId, track) },
                            onCreatePlaylist = { name, desc, cover -> viewModel.createPlaylist(name, desc, cover) },
                            onRemoveRecentlyPlayed = { viewModel.removeFromRecentlyPlayed(it) },
                            onOpenArtist = { artistName, songTitle -> viewModel.openArtistByName(artistName, songTitle) },
                            onOpenCommunityPlaylist = { viewModel.openPlaylistDetail(it) },
                            onPlayCommunityPlaylist = { viewModel.playCommunityPlaylist(it) },
                            onToggleSaveCommunityPlaylist = { viewModel.toggleSaveCommunityPlaylist(it) },
                            onOpenCatalogCategory = { category ->
                                viewModel.openCatalogCategory(
                                    title = category.title,
                                    params = category.params,
                                    browseId = category.browseId
                                )
                            }
                        )
                    }
                    2 -> Box(
                        modifier = Modifier.fillMaxSize()
                    ) {
                        LibraryScreen(
                            favorites = favorites,
                            downloads = downloads,
                            playlists = playlists,
                            stats = listeningStats,
                            recentlyPlayed = recentlyPlayed,
                            downloadingIds = downloadingIds,
                            downloadProgressMap = downloadProgress,
                            currentPlayingId = currentTrack?.videoId,
                            isPlaying = isPlaying,
                            spotifyImportStatus = spotifyImportStatus,
                            importedPlaylistSuccess = importedPlaylistSuccess,
                            initialPlaylistId = selectedUserPlaylist?.id,
                            initialScrollToTrackId = playlistScrollTargetTrackId,
                            onClearScrollTarget = { viewModel.clearPlaylistScrollTarget() },
                            onSetActiveUserPlaylist = { viewModel.setActiveUserPlaylist(it) },
                            openedFromHomeScreen = openedPlaylistFromHome,
                            onNavigateToHome = {
                                openedPlaylistFromHome = false
                                viewModel.clearSelectedUserPlaylist()
                                selectedTab = 0
                            },
                            onClearInitialPlaylist = { viewModel.clearSelectedUserPlaylist() },
                            onImportSpotifyPlaylist = { viewModel.importSpotifyPlaylist(it) },
                            onDismissImportSuccess = { viewModel.dismissImportSuccessModal() },
                            onPlayTrack = { track, queue -> viewModel.playTrack(track, queue) },
                            onToggleFavorite = { viewModel.toggleFavorite(it) },
                            onDownloadTrack = { track -> requestDownloadWithPermission { viewModel.downloadTrack(track) } },
                            onDownloadAll = { tracks -> requestDownloadWithPermission { viewModel.downloadAllTracks(tracks) } },
                            isDownloadPaused = isDownloadPaused,
                            onPauseDownloads = { viewModel.pauseDownloads() },
                            onResumeDownloads = { viewModel.resumeDownloads() },
                            onCancelDownloads = { viewModel.cancelAllDownloads() },
                            onRemoveDownload = { track -> viewModel.removeDownload(track) },
                            onUpdatePlaylistCover = { id, cover -> viewModel.updatePlaylistCover(id, cover) },
                            onUpdatePlaylistTitle = { id, title -> viewModel.updatePlaylistTitle(id, title) },
                            onDeletePlaylist = { id -> viewModel.deletePlaylist(id) },
                            onRemoveTrackFromPlaylist = { plId, vId -> viewModel.removeTrackFromPlaylist(plId, vId) },
                            onCreatePlaylist = { name, desc, cover -> viewModel.createPlaylist(name, desc, cover) },
                            onAddTrackToPlaylist = { plId, track -> viewModel.addTrackToPlaylist(plId, track) },
                            onRemoveFromRecentlyPlayed = { viewModel.removeFromRecentlyPlayed(it) },
                            onOpenArtist = { artistName -> viewModel.openArtistByName(artistName) }
                        )
                    }
                    3 -> Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .statusBarsPadding()
                    ) {
                        SettingsScreen(viewModel = viewModel)
                    }
                }
            }
        }

        // 1b. Dynamic Full-Screen Detail Navigation Stack (Catalog, Playlist/Album/Single, Artist)
        var renderedDetailEntries by remember { mutableStateOf<List<RenderedDetailEntry>>(emptyList()) }
        LaunchedEffect(detailStack) {
            val activeByKey = detailStack.associateBy { it.stackKey }
            val merged = mutableListOf<RenderedDetailEntry>()
            for (existing in renderedDetailEntries) {
                val updated = activeByKey[existing.entry.stackKey]
                if (updated != null) {
                    existing.transitionState.targetState = true
                    merged.add(existing.copy(entry = updated))
                } else if (!existing.transitionState.isIdle || existing.transitionState.currentState) {
                    existing.transitionState.targetState = false
                    merged.add(existing)
                }
            }
            val existingKeys = merged.map { it.entry.stackKey }.toHashSet()
            for (entry in detailStack) {
                if (entry.stackKey !in existingKeys) {
                    merged.add(
                        RenderedDetailEntry(
                            entry = entry,
                            transitionState = MutableTransitionState(false).apply { targetState = true }
                        )
                    )
                }
            }
            renderedDetailEntries = merged
        }

        renderedDetailEntries.forEach { rendered ->
            key(rendered.entry.stackKey) {
                LaunchedEffect(
                    rendered.transitionState.isIdle,
                    rendered.transitionState.currentState,
                    rendered.transitionState.targetState
                ) {
                    if (rendered.transitionState.isIdle &&
                        !rendered.transitionState.currentState &&
                        !rendered.transitionState.targetState
                    ) {
                        renderedDetailEntries = renderedDetailEntries.filterNot {
                            it.entry.stackKey == rendered.entry.stackKey
                        }
                    }
                }

                AnimatedVisibility(
                    visibleState = rendered.transitionState,
                    enter = slideInHorizontally(
                        initialOffsetX = { it },
                        animationSpec = tween(durationMillis = 280, easing = navSlideEasing)
                    ) + fadeIn(animationSpec = tween(200, easing = navSlideEasing)),
                    exit = slideOutHorizontally(
                        targetOffsetX = { it },
                        animationSpec = tween(durationMillis = 250, easing = navSlideEasing)
                    ) + fadeOut(animationSpec = tween(180, easing = navSlideEasing)),
                    modifier = Modifier
                        .fillMaxSize()
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null,
                            onClick = {}
                        )
                ) {
                    when (val entry = rendered.entry) {
                        is DetailScreenEntry.Catalog -> {
                            SearchCatalogDetailScreen(
                                catalogDetail = entry.detail,
                                isLoading = entry.isLoading,
                                currentPlayingId = currentTrack?.videoId,
                                isPlaying = isPlaying,
                                favorites = favorites,
                                downloads = downloads,
                                playlists = playlists,
                                onClose = { viewModel.closeDetail(entry.stackKey) },
                                onPlayTrack = { track, queue -> viewModel.playTrack(track, queue) },
                                onPlayMusicVideo = { track, queue ->
                                    viewModel.playHomeMusicVideo(track, queue)
                                    expandFullPlayer()
                                },
                                onOpenPlaylist = { playlist -> viewModel.openPlaylistDetail(playlist) },
                                onOpenAlbum = { albumTrack -> viewModel.openAlbum(albumTrack) },
                                onToggleFavorite = { viewModel.toggleFavorite(it) },
                                onDownloadTrack = { track -> requestDownloadWithPermission { viewModel.downloadTrack(track) } },
                                onOpenArtist = { artist, title -> viewModel.openArtistByName(artist, title) },
                                onAddTrackToPlaylist = { plId, track -> viewModel.addTrackToPlaylist(plId, track) },
                                onCreatePlaylist = { name, desc, cover -> viewModel.createPlaylist(name, desc, cover) },
                                onRetry = {
                                    viewModel.openCatalogCategory(
                                        title = entry.detail.title,
                                        params = entry.detail.params,
                                        browseId = entry.detail.browseId
                                    )
                                }
                            )
                        }
                        is DetailScreenEntry.Playlist -> {
                            PlaylistDetailScreen(
                                playlistDetail = entry.detail,
                                isLoading = entry.isLoading,
                                currentPlayingId = currentTrack?.videoId,
                                isPlaying = isPlaying,
                                initialScrollToTrackId = playlistScrollTargetTrackId,
                                onClearScrollTarget = { viewModel.clearPlaylistScrollTarget() },
                                isSaved = viewModel.isPlaylistSaved(entry.detail.id),
                                onClose = { viewModel.closeDetail(entry.stackKey) },
                                onPlayTrack = { track, queue ->
                                    viewModel.setActivePlaylistDetail(entry.detail)
                                    viewModel.playTrack(track, queue)
                                },
                                onToggleFavorite = { viewModel.toggleFavorite(it) },
                                onToggleSave = { viewModel.toggleSavePlaylistDetail(entry.detail) },
                                enableBackHandler = false
                            )
                        }
                        is DetailScreenEntry.Artist -> {
                            ArtistDetailScreen(
                                artistDetail = entry.detail,
                                isLoading = entry.isLoading,
                                currentPlayingId = currentTrack?.videoId,
                                onClose = { viewModel.closeDetail(entry.stackKey) },
                                onPlayTrack = { track, queue -> viewModel.playTrack(track, queue) },
                                onPlayMusicVideo = { track, queue ->
                                    viewModel.playHomeMusicVideo(track, queue)
                                    expandFullPlayer()
                                },
                                onToggleFavorite = { viewModel.toggleFavorite(it) },
                                onOpenArtist = { artistName -> viewModel.openArtistByName(artistName) },
                                onOpenPlaylist = { item ->
                                    val targetId = item.browseId.ifBlank { item.id }
                                    viewModel.openAlbumOrPlaylist(
                                        id = targetId,
                                        title = item.title,
                                        author = entry.detail.name,
                                        artworkUrl = item.artworkUrl
                                    )
                                },
                                enableBackHandler = false
                            )
                        }
                    }
                }
            }
        }
        } // End sibling glassBackdropSource

        BackHandler(enabled = !isPlayerExpanded && detailStack.isNotEmpty()) {
            viewModel.popTopDetail()
        }

        // 2. Dim gradient scrim behind the bottom floating controls
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(200.dp)
                .align(Alignment.BottomCenter)
                .background(
                    Brush.verticalGradient(
                        colors = if (liquidGlassEnabled) {
                            listOf(
                                Color.Transparent,
                                SpotifyPitchBlack.copy(alpha = 0.22f),
                                SpotifyPitchBlack.copy(alpha = 0.45f),
                                SpotifyPitchBlack.copy(alpha = 0.65f)
                            )
                        } else {
                            listOf(
                                Color.Transparent,
                                SpotifyPitchBlack.copy(alpha = 0.70f),
                                SpotifyPitchBlack.copy(alpha = 0.95f),
                                SpotifyPitchBlack
                            )
                        }
                    )
                )
        )

        // 3. TRUE FLOATING OVERLAY: Liquid Glass / Flat MiniPlayer & Navigation Dock
        Column(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .navigationBarsPadding()
                .padding(bottom = 6.dp)
                .zIndex(
                    if (isPlayerCollapseTransition || isPlayerExpandTransition ||
                        (!isPlayerExpanded && collapseProgress.value > 0f)
                    ) 2f else 0f
                ),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            val activeTrack = currentTrack
            if (activeTrack != null) {
                val currentQueue by AuraPlayerManager.queue.collectAsState()
                val queueTrackId = if (isVideoMode && AuraPlayerManager.originalAudioTrack != null) {
                    AuraPlayerManager.originalAudioTrack?.videoId
                } else activeTrack.videoId
                val currentIndex = remember(currentQueue, queueTrackId) {
                    currentQueue.indexOfFirst { it.videoId == queueTrackId }
                }
                val previousTrack = remember(currentQueue, currentIndex) {
                    if (currentIndex > 0) currentQueue[currentIndex - 1] else null
                }
                val nextTrack = remember(currentQueue, currentIndex) {
                    if (currentIndex in 0 until currentQueue.lastIndex) currentQueue[currentIndex + 1] else null
                }

                AuraMiniPlayer(
                    track = activeTrack,
                    isPlaying = isPlaying,
                    isBuffering = isBuffering,
                    progress = (positionSec / durationSec.coerceAtLeast(1f)).coerceIn(0f, 1f),
                    backgroundColor = palette.primary,
                    backdropTintColor = palette.backgroundTop,
                    glassBackdrop = glassBackdrop,
                    previousTrack = previousTrack,
                    nextTrack = nextTrack,
                    hideArtworkForTransition = (isPlayerCollapseTransition || isPlayerExpandTransition) && !isVideoMode,
                    isPlayerTransition = isPlayerCollapseTransition || isPlayerExpandTransition,
                    isCollapsingTransition = isPlayerCollapseTransition,
                    collapseProgress = collapseProgress.value,
                    onArtworkPositioned = { miniPlayerArtworkBounds = it },
                    onPlayerBoundsPositioned = { miniPlayerContainerBounds = it },
                    onExpand = expandFullPlayer,
                    onTogglePlayPause = { AuraPlayerManager.togglePlayPause() },
                    onSkipPrevious = { AuraPlayerManager.skipToPrevious() },
                    onSkipNext = { AuraPlayerManager.skipToNext() }
                )
                Spacer(modifier = Modifier.height(6.dp))
            }

            val navItems = remember {
                listOf(
                    AuraNavTabItem(0, "Home", Icons.Rounded.Home),
                    AuraNavTabItem(1, "Search", Icons.Rounded.Search),
                    AuraNavTabItem(2, "My Music", Icons.Rounded.LibraryMusic),
                    AuraNavTabItem(3, "Settings", Icons.Rounded.Settings)
                )
            }
            val onSelectNavTab: (Int) -> Unit = { idx ->
                viewModel.clearDetailStack()
                if (selectedTab != idx) {
                    openedPlaylistFromHome = false
                }
                if (idx == 2 && selectedTab != 2) {
                    viewModel.clearSelectedUserPlaylist()
                }
                selectedTab = idx
            }

            // Floating Dynamic Capsule Navigation Dock
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .graphicsLayer {
                        // Keep the dock in the layout and let the growing player push it
                        // below the viewport in sync with the shared full-player motion.
                        translationY = (size.height + 80.dp.toPx()) * playerChromePushProgress
                    },
                contentAlignment = Alignment.Center
            ) {
                if (liquidGlassEnabled) {
                    AuraLiquidGlassTabBar(
                        tabs = navItems,
                        selectedTab = selectedTab,
                        backdrop = glassBackdrop,
                        layer = dockGlassLayer,
                        luminance = dockLuminance,
                        onTabSelected = onSelectNavTab
                    )
                } else {
                    Surface(
                        shape = RoundedCornerShape(40.dp),
                        color = Color(0xFF16181F).copy(alpha = 0.98f),
                        shadowElevation = 16.dp,
                        modifier = Modifier.animateContentSize(
                            animationSpec = spring(
                                dampingRatio = Spring.DampingRatioLowBouncy,
                                stiffness = Spring.StiffnessMediumLow
                            )
                        )
                    ) {
                        Row(
                            modifier = Modifier
                                .padding(vertical = 7.dp, horizontal = 10.dp),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            navItems.forEach { tab ->
                                val idx = tab.index
                                val label = tab.label
                                val icon = tab.icon
                                val isSelected = selectedTab == idx
                                val iconScale by animateFloatAsState(
                                    targetValue = if (isSelected) 1.08f else 1.0f,
                                    animationSpec = spring(
                                        dampingRatio = Spring.DampingRatioMediumBouncy,
                                        stiffness = Spring.StiffnessMediumLow
                                    ),
                                    label = "navIconScale"
                                )

                                Surface(
                                    onClick = { onSelectNavTab(idx) },
                                    shape = RoundedCornerShape(28.dp),
                                    color = if (isSelected) AuraNeonLime else Color.Transparent
                                ) {
                                    Row(
                                        modifier = Modifier.padding(
                                            horizontal = if (isSelected) 18.dp else 14.dp,
                                            vertical = 13.dp
                                        ),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.Center
                                    ) {
                                        Icon(
                                            imageVector = icon,
                                            contentDescription = label,
                                            tint = if (isSelected) Color.Black else TextSecondary,
                                            modifier = Modifier
                                                .size(23.dp)
                                                .graphicsLayer {
                                                    scaleX = iconScale
                                                    scaleY = iconScale
                                                }
                                        )

                                        AnimatedVisibility(
                                            visible = isSelected,
                                            enter = fadeIn(tween(160)) + expandHorizontally(
                                                animationSpec = tween(
                                                    durationMillis = 200,
                                                    easing = CubicBezierEasing(0.23f, 1f, 0.32f, 1f)
                                                )
                                            ),
                                            exit = fadeOut(tween(120)) + shrinkHorizontally(
                                                animationSpec = tween(
                                                    durationMillis = 180,
                                                    easing = CubicBezierEasing(0.23f, 1f, 0.32f, 1f)
                                                )
                                            )
                                        ) {
                                            Row(verticalAlignment = Alignment.CenterVertically) {
                                                Spacer(modifier = Modifier.width(7.dp))
                                                Text(
                                                    text = label,
                                                    color = Color.Black,
                                                    fontWeight = FontWeight.Bold,
                                                    fontSize = 13.5.sp,
                                                    maxLines = 1,
                                                    softWrap = false
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        // Full-Screen Now Playing Sheet
        BackHandler(enabled = isPlayerExpanded, onBack = collapseFullPlayer)
        val activeTrack = currentTrack
        AnimatedVisibility(
            visible = (isPlayerExpanded || isPlayerCollapseTransition || isPlayerExpandTransition) && activeTrack != null,
            enter = if (isPlayerExpandTransition) {
                androidx.compose.animation.EnterTransition.None
            } else {
                slideInVertically(
                    initialOffsetY = { it },
                    animationSpec = tween(durationMillis = 350, easing = CubicBezierEasing(0.23f, 1f, 0.32f, 1f))
                )
            },
            exit = androidx.compose.animation.ExitTransition.None,
            modifier = Modifier
                .fillMaxSize()
                .onGloballyPositioned { coordinates ->
                    if (isPlayerExpanded && !isPlayerCollapseTransition && playerSheetDragOffsetY == 0f &&
                        (fullPlayerContainerBounds == null || !isPlayerExpandTransition)
                    ) {
                        fullPlayerContainerBounds = coordinates.boundsInRoot()
                    }
                }
                .graphicsLayer {
                    val from = fullPlayerContainerBounds
                    val to = miniPlayerContainerBounds
                    val isSharedPlayerTransition = isPlayerCollapseTransition || isPlayerExpandTransition
                    if ((isSharedPlayerTransition || (!isPlayerExpanded && collapseProgress.value > 0f)) &&
                        from != null && to != null && from.width > 0f && from.height > 0f) {
                        val progress = collapseProgress.value.coerceIn(0f, 1f)
                        val dragStart = if (isPlayerCollapseTransition) collapseStartDragY else 0f
                        val sx = 1f + ((to.width / from.width) - 1f) * progress
                        val sy = 1f + ((to.height / from.height) - 1f) * progress
                        translationX = (to.left - from.left) * progress
                        translationY = dragStart * (1f - progress) + (to.top - from.top) * progress
                        scaleX = sx
                        scaleY = sy
                        transformOrigin = androidx.compose.ui.graphics.TransformOrigin(0f, 0f)
                        val startCorner = (dragStart / 10f).coerceIn(0f, 28f).dp
                        val cornerPx = (startCorner + (16.dp - startCorner) * progress).toPx()
                        shape = NonUniformScaledRoundedShape(
                            cornerRadiusPx = cornerPx,
                            scaleX = sx,
                            scaleY = sy
                        )
                        clip = progress > 0f || dragStart > 0f
                        alpha = if (isPlayerCollapseTransition) {
                            1f - ((progress - 0.68f) / 0.32f).coerceIn(0f, 1f)
                        } else 1f
                    } else if (isPlayerExpandTransition && from == null) {
                        // Keep the destination hidden until its full-size bounds are measured.
                        alpha = 0f
                    } else if (playerSheetDragOffsetY > 0f) {
                        val dragY = playerSheetDragOffsetY
                        translationY = dragY
                        val corner = (dragY / 10f).coerceIn(0f, 28f).dp
                        shape = RoundedCornerShape(topStart = corner, topEnd = corner)
                        clip = true
                    }
                }
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = {} // Intercept all touch events so background screens never receive taps
                )
        ) {
            if (activeTrack != null) {
                AuraNowPlayingSheet(
                    track = activeTrack,
                    isPlaying = isPlaying,
                    isBuffering = isBuffering,
                    positionSec = positionSec,
                    durationSec = durationSec,
                    bufferedSec = bufferedSec,
                    palette = palette,
                    lyrics = lyrics,
                    lyricsOffsetMs = lyricsOffsetMs,
                    showRomanized = showRomanized,
                    isFavorite = favorites.any { it.videoId == activeTrack.videoId },
                    isShuffleEnabled = isShuffleEnabled,
                    queue = queue,
                    currentStyle = nowPlayingStyle,
                    isVideoMode = isVideoMode,
                    isVideoLoading = isVideoLoading,
                    livePerformances = livePerformances,
                    isLoadingLivePerformances = isLoadingLivePerformances,
                    artistDetail = nowPlayingArtistDetail,
                    videoInfo = nowPlayingVideoInfo,
                    isArtistDetailLoading = isNowPlayingArtistLoading,
                    onLoadArtistDetail = { artist, title, artwork ->
                        viewModel.loadNowPlayingArtist(artist, title, artwork)
                    },
                    onCollapse = collapseFullPlayer,
                    hideArtworkForPlayerTransition = (isPlayerCollapseTransition || isPlayerExpandTransition) && !isVideoMode,
                    playerCollapseProgress = if (isPlayerCollapseTransition || isPlayerExpandTransition) {
                        collapseProgress.value
                    } else 0f,
                    onArtworkPositioned = {
                        if (isPlayerExpanded && !isPlayerCollapseTransition && playerSheetDragOffsetY == 0f &&
                            (fullPlayerArtworkBounds == null || !isPlayerExpandTransition)
                        ) {
                            fullPlayerArtworkBounds = it
                        }
                    },
                    onSheetDragOffsetChange = { playerSheetDragOffsetY = it },
                    onTogglePlayPause = { AuraPlayerManager.togglePlayPause() },
                    onSeekTo = { AuraPlayerManager.seekToSeconds(it) },
                    onSkipNext = { AuraPlayerManager.skipToNext() },
                    onSkipPrevious = { AuraPlayerManager.skipToPrevious() },
                    onToggleFavorite = { viewModel.toggleFavorite(activeTrack) },
                    onToggleShuffle = { AuraPlayerManager.toggleShuffle() },
                    onToggleVideoMode = { viewModel.toggleVideoMode() },
                    onLoadLivePerformances = { viewModel.loadLivePerformances() },
                    onPlayLivePerformance = { viewModel.playLivePerformance(it) },
                    onToggleStyle = {
                        val styles = com.auramusic.app.domain.model.NowPlayingStyle.entries
                        val next = (styles.indexOf(nowPlayingStyle) + 1).mod(styles.size)
                        viewModel.setNowPlayingStyle(styles[next])
                    },
                    onOpenArtist = { artistName ->
                        val isTopArtistSame = (detailStack.lastOrNull() as? DetailScreenEntry.Artist)
                            ?.detail?.name?.equals(artistName, ignoreCase = true) == true
                        collapseFullPlayer()
                        if (!isTopArtistSame) {
                            screenScope.launch {
                                kotlinx.coroutines.delay(260)
                                viewModel.openArtistByName(artistName, activeTrack.title, activeTrack.highResArtworkUrl)
                            }
                        }
                    },
                    onOpenCurrentPlaylist = {
                        val isAlreadyOpen = (detailStack.lastOrNull() is DetailScreenEntry.Playlist) ||
                            (detailStack.isEmpty() && selectedTab == 2 && selectedUserPlaylist != null)
                        collapseFullPlayer()
                        screenScope.launch {
                            if (!isAlreadyOpen) {
                                kotlinx.coroutines.delay(260)
                                val opened = viewModel.openCurrentPlaylistScreen()
                                if (opened && viewModel.selectedUserPlaylist.value != null) {
                                    selectedTab = 2
                                }
                            } else {
                                kotlinx.coroutines.delay(100)
                                viewModel.openCurrentPlaylistScreen()
                            }
                        }
                    },
                    isDownloaded = downloads.any { it.videoId == activeTrack.videoId && it.isDownloaded },
                    isDownloading = downloadingIds.contains(activeTrack.videoId),
                    onDownloadTrack = { track -> requestDownloadWithPermission { viewModel.downloadTrack(track) } },
                    onRemoveDownload = { track -> viewModel.removeDownload(track) },
                    playlists = playlists,
                    onAddTrackToPlaylist = { plId, track -> viewModel.addTrackToPlaylist(plId, track) },
                    onCreatePlaylist = { name, desc, cover -> viewModel.createPlaylist(name, desc, cover) },
                    onPlayQueueTrack = { viewModel.playTrack(it, queue) },
                    onAdjustLyricsOffset = { viewModel.adjustLyricsOffset(it) },
                    onToggleRomanized = { viewModel.toggleRomanizedLyrics() },
                    comments = playerComments,
                    isLoadingComments = areCommentsLoading,
                    commentsError = commentsError,
                    onLoadComments = { viewModel.loadPlayerComments(it) },
                    onLoadMoreComments = { viewModel.loadMorePlayerComments() },
                    onSelectCommentSort = { viewModel.selectCommentSort(it) },
                    onToggleCommentReplies = { viewModel.toggleCommentReplies(it) },
                    onLoadMoreCommentReplies = { viewModel.loadMoreCommentReplies(it) }
                )
            }
        }

        // The artwork travels independently so it keeps its square shape while the full
        // player surface folds down into the mini-player's smaller, wider destination.
        val activeArtwork = currentTrack
        val artworkStart = fullPlayerArtworkBounds
        val artworkEnd = miniPlayerArtworkBounds
        if ((isPlayerCollapseTransition || isPlayerExpandTransition) && !isVideoMode && activeArtwork != null &&
            artworkStart != null && artworkEnd != null
        ) {
            val progress = collapseProgress.value.coerceIn(0f, 1f)
            val startWidth = artworkStart.width.coerceAtLeast(1f)
            val startHeight = artworkStart.height.coerceAtLeast(1f)
            val density = androidx.compose.ui.platform.LocalDensity.current
            val isAppleMusicStyle = nowPlayingStyle == com.auramusic.app.domain.model.NowPlayingStyle.LIQUID_GLASS
            val sourceCorner = if (isAppleMusicStyle) 0f else 8f
            val corner = sourceCorner + (8f - sourceCorner) * progress
            val statusBarHeightDp = with(density) { artworkStart.top.coerceAtLeast(0f).toDp() }
            val startHeightDp = with(density) { startHeight.toDp() }
            val fadeFactor = (1f - progress * 0.65f).coerceIn(0f, 1f)
            val dragStart = if (isPlayerCollapseTransition) collapseStartDragY else 0f

            AsyncImage(
                model = activeArtwork.highResArtworkUrl,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .offset(
                        x = with(density) { artworkStart.left.toDp() },
                        y = with(density) { artworkStart.top.toDp() }
                    )
                    .size(
                        width = with(density) { startWidth.toDp() },
                        height = startHeightDp
                    )
                    .graphicsLayer {
                        translationX = (artworkEnd.left - artworkStart.left) * progress
                        translationY = dragStart * (1f - progress) + (artworkEnd.top - artworkStart.top) * progress
                        scaleX = 1f + ((artworkEnd.width / startWidth) - 1f) * progress
                        scaleY = 1f + ((artworkEnd.height / startHeight) - 1f) * progress
                        transformOrigin = androidx.compose.ui.graphics.TransformOrigin(0f, 0f)
                    }
                    .clip(RoundedCornerShape(corner.dp))
                    .then(
                        if (isAppleMusicStyle) {
                            Modifier.appleMusicVerticalFadeEdges(
                                topFadeHeight = statusBarHeightDp * fadeFactor,
                                bottomFadeHeight = (startHeightDp * 0.25f) * fadeFactor
                            )
                        } else {
                            Modifier
                        }
                    )
                    .zIndex(20f)
            )
        }

        // In-App Update Modal
        val isUpdateModalVisible by viewModel.isUpdateModalVisible.collectAsState()
        val currentUpdateRelease by viewModel.currentUpdateRelease.collectAsState()
        val updateState by viewModel.updateState.collectAsState()
        val updateDownloadProgress by viewModel.updateDownloadProgress.collectAsState()
        val updateErrorMessage by viewModel.updateErrorMessage.collectAsState()
        val currentAppVersion = remember { AuraUpdateManager.getCurrentAppVersion(context) }

        AuraUpdateModal(
            visible = isUpdateModalVisible,
            release = currentUpdateRelease,
            currentVersion = currentAppVersion,
            updateState = updateState,
            downloadProgress = updateDownloadProgress,
            errorMessage = updateErrorMessage,
            modifier = Modifier.zIndex(100f),
            onDismiss = { viewModel.dismissUpdateModal() },
            onSnooze = { version -> viewModel.snoozeUpdate(version) },
            onUpdateNow = { release -> viewModel.downloadAndInstallUpdate(release) },
            onInstallNow = { release -> viewModel.installDownloadedApk(release) }
        )
    }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun AuraMiniPlayer(
    track: AuraTrack,
    isPlaying: Boolean,
    isBuffering: Boolean,
    progress: Float,
    backgroundColor: Color,
    backdropTintColor: Color = backgroundColor,
    glassBackdrop: LayerBackdrop,
    previousTrack: AuraTrack? = null,
    nextTrack: AuraTrack? = null,
    hideArtworkForTransition: Boolean = false,
    isPlayerTransition: Boolean = false,
    isCollapsingTransition: Boolean = false,
    collapseProgress: Float = 0f,
    onArtworkPositioned: (Rect) -> Unit = {},
    onPlayerBoundsPositioned: (Rect) -> Unit = {},
    onExpand: () -> Unit,
    onTogglePlayPause: () -> Unit,
    onSkipPrevious: () -> Unit,
    onSkipNext: () -> Unit
) {
    val liquidGlassEnabled = LocalLiquidGlassEnabled.current
    val isDarkTheme = LocalIsDarkTheme.current
    val glassLayer = rememberGraphicsLayer()
    val luminance = rememberGlassLuminance(
        source = glassLayer,
        enabled = liquidGlassEnabled && !isPlayerTransition
    )

    val artworkVeilAlpha = remember { Animatable(0f) }
    LaunchedEffect(isCollapsingTransition, isPlayerTransition, liquidGlassEnabled) {
        if (!liquidGlassEnabled) {
            artworkVeilAlpha.snapTo(0f)
        } else if (isCollapsingTransition) {
            artworkVeilAlpha.snapTo(1f)
        } else if (isPlayerTransition) {
            artworkVeilAlpha.snapTo(0f)
        } else if (artworkVeilAlpha.value > 0f) {
            artworkVeilAlpha.animateTo(
                targetValue = 0f,
                animationSpec = tween(
                    durationMillis = 880,
                    easing = CubicBezierEasing(0.22f, 1f, 0.36f, 1f)
                )
            )
        }
    }

    val coroutineScope = rememberCoroutineScope()
    val swipeOffsetX = remember { Animatable(0f) }
    val swipeOffsetY = remember { Animatable(0f) }

    LaunchedEffect(track.videoId) {
        swipeOffsetX.snapTo(0f)
    }

    val animatedBgColor by animateColorAsState(
        targetValue = backgroundColor,
        animationSpec = tween(durationMillis = 420, easing = CubicBezierEasing(0.23f, 1f, 0.32f, 1f)),
        label = "miniPlayerBgColor"
    )
    val animatedTintColor by animateColorAsState(
        targetValue = backdropTintColor,
        animationSpec = tween(durationMillis = 420, easing = CubicBezierEasing(0.23f, 1f, 0.32f, 1f)),
        label = "miniPlayerTintColor"
    )

    val foregroundColor by animateColorAsState(
        targetValue = if (liquidGlassEnabled) {
            if (artworkVeilAlpha.value > 0.35f || isDarkTheme || luminance.value < 0.65f) {
                Color.White
            } else {
                Color(0xFF111318)
            }
        } else {
            Color.White
        },
        animationSpec = tween(durationMillis = 320),
        label = "miniPlayerForegroundColor"
    )

    val lighterProgressColor = remember(animatedBgColor) {
        lerp(animatedBgColor, Color.White, 0.65f)
    }

    val animatedProgress by animateFloatAsState(
        targetValue = progress.coerceIn(0f, 1f),
        animationSpec = tween(durationMillis = 200),
        label = "miniPlayerProgress"
    )

    val miniPlayerShape = RoundedCornerShape(16.dp)
    val baseModifier = Modifier
        .fillMaxWidth()
        .padding(horizontal = 16.dp)
        .offset { IntOffset(0, swipeOffsetY.value.roundToInt()) }
        .onGloballyPositioned { onPlayerBoundsPositioned(it.boundsInRoot()) }
        .graphicsLayer {
            alpha = if (isPlayerTransition) {
                ((collapseProgress - 0.78f) / 0.22f).coerceIn(0f, 1f)
            } else 1f
        }

    val surfaceModifier = if (liquidGlassEnabled) {
        baseModifier
            .liquidGlass(
                backdrop = glassBackdrop,
                layer = glassLayer,
                luminance = luminance,
                shape = miniPlayerShape,
                interactive = true,
                pressedScale = 1.02f,
                fallbackColor = Color(0xFF1A1D24).copy(alpha = 0.94f)
            )
            .border(
                width = 0.75.dp,
                brush = Brush.verticalGradient(
                    colors = listOf(
                        Color.White.copy(alpha = 0.28f),
                        Color.White.copy(alpha = 0.06f)
                    )
                ),
                shape = miniPlayerShape
            )
            .clip(miniPlayerShape)
            .clipToBounds()
    } else {
        baseModifier
            .clip(miniPlayerShape)
            .clipToBounds()
    }

    val gestureModifier = surfaceModifier
        .clickable(onClick = onExpand)
        .pointerInput(track.videoId, isPlaying) {
            detectVerticalDragGestures(
                onVerticalDrag = { change, dragAmount ->
                    if (swipeOffsetY.value + dragAmount >= 0f) {
                        change.consume()
                        coroutineScope.launch {
                            swipeOffsetY.snapTo((swipeOffsetY.value + dragAmount * 0.65f).coerceIn(0f, 56f))
                        }
                    }
                },
                onDragCancel = {
                    coroutineScope.launch {
                        swipeOffsetY.animateTo(
                            targetValue = 0f,
                            animationSpec = spring(
                                dampingRatio = Spring.DampingRatioMediumBouncy,
                                stiffness = Spring.StiffnessMediumLow
                            )
                        )
                    }
                },
                onDragEnd = {
                    coroutineScope.launch {
                        if (swipeOffsetY.value > 28f && isPlaying) {
                            onTogglePlayPause()
                        }
                        swipeOffsetY.animateTo(
                            targetValue = 0f,
                            animationSpec = spring(
                                dampingRatio = Spring.DampingRatioMediumBouncy,
                                stiffness = Spring.StiffnessMediumLow
                            )
                        )
                    }
                }
            )
        }

    val content: @Composable () -> Unit = {
        Column {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(60.dp)
                    .padding(start = 10.dp, end = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                // 1. Stationary Artwork (bounds tracked for flying artwork transition)
                Box(
                    modifier = Modifier
                        .size(42.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .onGloballyPositioned { onArtworkPositioned(it.boundsInRoot()) }
                        .graphicsLayer { alpha = if (hideArtworkForTransition) 0f else 1f },
                    contentAlignment = Alignment.Center
                ) {
                    AsyncImage(
                        model = track.highResArtworkUrl,
                        contentDescription = track.title,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize()
                    )
                }

                Spacer(modifier = Modifier.width(10.dp))

                // 2. Sliding Text Viewport with axis-locked horizontal swipe-to-skip & marquee
                var textWidthPx by remember { mutableFloatStateOf(260f) }

                Box(
                    modifier = Modifier
                        .weight(1f)
                        .height(44.dp)
                        .clipToBounds()
                        .onGloballyPositioned { coordinates ->
                            if (coordinates.size.width > 0) {
                                textWidthPx = coordinates.size.width.toFloat()
                            }
                        }
                        .pointerInput(track.videoId) {
                            awaitEachGesture {
                                val down = awaitFirstDown(requireUnconsumed = false)
                                var totalDragX = 0f
                                var totalDragY = 0f
                                var isHorizontalSwipe = false
                                var isVerticalDrag = false
                                while (true) {
                                    val event = awaitPointerEvent()
                                    val change = event.changes.firstOrNull { it.id == down.id } ?: break
                                    if (change.isConsumed) break
                                    if (!change.pressed) {
                                        if (!isHorizontalSwipe && !isVerticalDrag) {
                                            onExpand()
                                        } else if (isHorizontalSwipe) {
                                            if (totalDragX > 50f) {
                                                onSkipPrevious()
                                            } else if (totalDragX < -50f) {
                                                onSkipNext()
                                            }
                                            coroutineScope.launch {
                                                swipeOffsetX.animateTo(
                                                    targetValue = 0f,
                                                    animationSpec = spring(
                                                        dampingRatio = Spring.DampingRatioLowBouncy,
                                                        stiffness = Spring.StiffnessMediumLow
                                                    )
                                                )
                                            }
                                        }
                                        break
                                    }
                                    val dx = change.position.x - change.previousPosition.x
                                    val dy = change.position.y - change.previousPosition.y
                                    totalDragX += dx
                                    totalDragY += dy
                                    if (!isHorizontalSwipe && !isVerticalDrag) {
                                        if (abs(totalDragX) > viewConfiguration.touchSlop && abs(totalDragX) >= abs(totalDragY)) {
                                            isHorizontalSwipe = true
                                        } else if (abs(totalDragY) > viewConfiguration.touchSlop && abs(totalDragY) > abs(totalDragX)) {
                                            isVerticalDrag = true
                                        }
                                    }
                                    if (isHorizontalSwipe) {
                                        change.consume()
                                        coroutineScope.launch {
                                            swipeOffsetX.snapTo((swipeOffsetX.value + dx).coerceIn(-textWidthPx, textWidthPx))
                                        }
                                    }
                                }
                            }
                        },
                    contentAlignment = Alignment.CenterStart
                ) {
                    AnimatedContent(
                        targetState = track,
                        contentKey = { it.videoId },
                        transitionSpec = {
                            (slideInHorizontally(tween(240)) { width -> (width * 0.35f).roundToInt() } + fadeIn(tween(200)))
                                .togetherWith(
                                    slideOutHorizontally(tween(200)) { width -> -(width * 0.35f).roundToInt() } + fadeOut(tween(160))
                                )
                                .using(SizeTransform(clip = false))
                        },
                        label = "miniPlayerTrackText"
                    ) { active ->
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .graphicsLayer {
                                    translationX = swipeOffsetX.value
                                    alpha = (1f - (abs(swipeOffsetX.value) / textWidthPx.coerceAtLeast(1f)) * 0.45f).coerceIn(0.55f, 1f)
                                },
                            verticalArrangement = Arrangement.Center
                        ) {
                            Text(
                                text = active.title,
                                color = foregroundColor,
                                style = MaterialTheme.typography.titleSmall,
                                maxLines = 1,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .basicMarquee(
                                        iterations = Int.MAX_VALUE,
                                        animationMode = MarqueeAnimationMode.Immediately
                                    )
                                    .focusable()
                            )
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = active.artist,
                                color = foregroundColor.copy(alpha = 0.72f),
                                style = MaterialTheme.typography.bodySmall,
                                maxLines = 1,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .basicMarquee(
                                        iterations = Int.MAX_VALUE,
                                        animationMode = MarqueeAnimationMode.Immediately
                                    )
                                    .focusable()
                            )
                        }
                    }

                    // Preview incoming previous track while dragging right
                    if (swipeOffsetX.value > 0f) {
                        val displayPrev = previousTrack ?: track
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .graphicsLayer {
                                    translationX = swipeOffsetX.value - textWidthPx
                                    alpha = (swipeOffsetX.value / (textWidthPx * 0.5f).coerceAtLeast(1f)).coerceIn(0f, 1f)
                                },
                            verticalArrangement = Arrangement.Center
                        ) {
                            Text(
                                text = displayPrev.title,
                                color = foregroundColor,
                                style = MaterialTheme.typography.titleSmall,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = displayPrev.artist,
                                color = foregroundColor.copy(alpha = 0.72f),
                                style = MaterialTheme.typography.bodySmall,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }

                    // Preview incoming next track while dragging left
                    if (swipeOffsetX.value < 0f) {
                        val displayNext = nextTrack ?: track
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .graphicsLayer {
                                    translationX = swipeOffsetX.value + textWidthPx
                                    alpha = (-swipeOffsetX.value / (textWidthPx * 0.5f).coerceAtLeast(1f)).coerceIn(0f, 1f)
                                },
                            verticalArrangement = Arrangement.Center
                        ) {
                            Text(
                                text = displayNext.title,
                                color = foregroundColor,
                                style = MaterialTheme.typography.titleSmall,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = displayNext.artist,
                                color = foregroundColor.copy(alpha = 0.72f),
                                style = MaterialTheme.typography.bodySmall,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.width(4.dp))

                // 3. Transport Controls with >= 44dp touch targets
                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(
                        onClick = onSkipPrevious,
                        modifier = Modifier.size(44.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.SkipPrevious,
                            contentDescription = "Previous Track",
                            tint = foregroundColor,
                            modifier = Modifier.size(22.dp)
                        )
                    }

                    Box(
                        modifier = Modifier
                            .size(44.dp)
                            .clip(CircleShape)
                            .clickable(onClick = onTogglePlayPause),
                        contentAlignment = Alignment.Center
                    ) {
                        Box(
                            modifier = Modifier
                                .size(34.dp)
                                .clip(CircleShape)
                                .background(
                                    if (liquidGlassEnabled) {
                                        foregroundColor.copy(alpha = 0.16f)
                                    } else {
                                        lighterProgressColor
                                    }
                                ),
                            contentAlignment = Alignment.Center
                        ) {
                            val playTint = if (liquidGlassEnabled) foregroundColor else Color.Black
                            if (isBuffering) {
                                CircularProgressIndicator(
                                    color = playTint,
                                    strokeWidth = 2.dp,
                                    modifier = Modifier.size(16.dp)
                                )
                            } else {
                                Icon(
                                    imageVector = if (isPlaying) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
                                    contentDescription = if (isPlaying) "Pause" else "Play",
                                    tint = playTint,
                                    modifier = Modifier
                                        .size(20.dp)
                                        .padding(start = if (!isPlaying) 1.5.dp else 0.dp)
                                )
                            }
                        }
                    }

                    IconButton(
                        onClick = onSkipNext,
                        modifier = Modifier.size(44.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.SkipNext,
                            contentDescription = "Next Track",
                            tint = foregroundColor,
                            modifier = Modifier.size(22.dp)
                        )
                    }
                }
            }

            // 4. Bottom 1.5dp progress bar
            val trackBg = if (liquidGlassEnabled) {
                foregroundColor.copy(alpha = 0.16f)
            } else {
                animatedBgColor.copy(alpha = 0.35f)
            }
            val trackFill = if (liquidGlassEnabled) {
                foregroundColor.copy(alpha = 0.90f)
            } else {
                lighterProgressColor
            }
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(1.5.dp)
                    .background(trackBg)
            ) {
                if (animatedProgress > 0f) {
                    Box(
                        modifier = Modifier
                            .fillMaxHeight()
                            .fillMaxWidth(animatedProgress)
                            .background(trackFill)
                    )
                }
            }
        }
    }

    if (liquidGlassEnabled) {
        Box(modifier = gestureModifier) {
            // Transitional blurred cover-color veil from the closing Apple Music player
            // that smoothly parts from the center outward to the left and right edges into live Liquid Glass.
            if (artworkVeilAlpha.value > 0.001f) {
                val revealProgress = (1f - artworkVeilAlpha.value).coerceIn(0f, 1f)
                Box(
                    modifier = Modifier
                        .matchParentSize()
                        .clip(miniPlayerShape)
                        .graphicsLayer {
                            compositingStrategy = CompositingStrategy.Offscreen
                            shape = miniPlayerShape
                            clip = true
                        }
                        .drawWithContent {
                            drawContent()
                            if (revealProgress > 0.001f) {
                                val feather = 0.26f
                                val halfSpan = revealProgress * (0.5f + feather)
                                val innerClear = (halfSpan - feather).coerceAtLeast(0f)
                                val centerAlpha = (1f - (revealProgress / 0.25f)).coerceIn(0f, 1f)
                                val edgeAlpha = (1f - ((revealProgress - 0.52f) / 0.48f).coerceIn(0f, 1f))
                                val leftOuter = (0.5f - halfSpan).coerceIn(0f, 0.499f)
                                val leftInner = (0.5f - innerClear).coerceIn(leftOuter, 0.5f)
                                val rightInner = (0.5f + innerClear).coerceIn(0.5f, 1f)
                                val rightOuter = (0.5f + halfSpan).coerceIn(rightInner, 1f)

                                drawRect(
                                    brush = Brush.horizontalGradient(
                                        0.0f to Color.White.copy(alpha = edgeAlpha),
                                        leftOuter to Color.White.copy(alpha = edgeAlpha),
                                        leftInner to Color.White.copy(alpha = centerAlpha),
                                        0.5f to Color.White.copy(alpha = centerAlpha),
                                        rightInner to Color.White.copy(alpha = centerAlpha),
                                        rightOuter to Color.White.copy(alpha = edgeAlpha),
                                        1.0f to Color.White.copy(alpha = edgeAlpha),
                                    ),
                                    blendMode = BlendMode.DstIn
                                )
                            }
                        }
                ) {
                    if (isLyricsBlurSupported() && track.highResArtworkUrl.isNotBlank()) {
                        AsyncImage(
                            model = track.highResArtworkUrl,
                            contentDescription = null,
                            contentScale = ContentScale.Crop,
                            modifier = Modifier
                                .fillMaxSize()
                                .blur(42.dp, BlurredEdgeTreatment(miniPlayerShape))
                        )
                    }
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(
                                Brush.horizontalGradient(
                                    colors = listOf(
                                        animatedTintColor.copy(alpha = 0.78f),
                                        animatedBgColor.copy(alpha = 0.72f),
                                        Color(0xFF14151B).copy(alpha = 0.80f)
                                    )
                                )
                            )
                    )
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(Color.Black.copy(alpha = 0.28f))
                    )
                }
            }
            content()
        }
    } else {
        Surface(
            shape = miniPlayerShape,
            color = animatedBgColor.copy(alpha = 0.96f),
            shadowElevation = 10.dp,
            modifier = gestureModifier
        ) {
            content()
        }
    }
}
