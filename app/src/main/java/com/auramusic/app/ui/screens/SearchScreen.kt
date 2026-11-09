package com.auramusic.app.ui.screens

import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ArrowBack
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Clear
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.FavoriteBorder
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import coil.compose.AsyncImage
import com.auramusic.app.ui.components.AudioPlayingIndicator
import com.auramusic.app.core.media.AuraPlayerManager
import com.auramusic.app.domain.model.ArtistCarouselItem
import com.auramusic.app.domain.model.AuraPlaylist
import com.auramusic.app.domain.model.AuraTrack
import com.auramusic.app.domain.model.YtmCommunityPlaylist
import com.auramusic.app.domain.model.YtmTopArtist
import com.auramusic.app.ui.components.AuraAddToPlaylistModal
import com.auramusic.app.ui.components.AuraCommunityPlaylistOptionsModal
import com.auramusic.app.ui.components.AuraCreatePlaylistModal
import com.auramusic.app.ui.components.AuraTrackOptionsModal
import com.auramusic.app.ui.components.TrackItemSkeleton
import com.auramusic.app.ui.theme.AuraNeonLime
import com.auramusic.app.ui.theme.AuraPillInactive
import com.auramusic.app.ui.theme.AuraPillInactiveText
import com.auramusic.app.ui.theme.SpotifyPitchBlack
import com.auramusic.app.ui.theme.TextPrimary
import com.auramusic.app.ui.theme.TextSecondary

private val FluidEasing = CubicBezierEasing(0.23f, 1f, 0.32f, 1f)

data class BrowseCategory(
    val title: String,
    val backgroundColor: Color,
    val artworkUrl: String,
    val searchQuery: String
)

data class WatchPlaylistCard(
    val title: String,
    val badge: String? = null,
    val artworkUrl: String,
    val query: String
)

private val CURATED_BROWSE_CATEGORIES = listOf(
    BrowseCategory("Music", Color(0xFFE11D48), "https://lh3.googleusercontent.com/RCpTA6EXJQyjVFDosWOKa2SMmqkua_lA9mHPDWWciLwgqpZLz9fS3xYq9R0_L9kF=w544-h544-l90-rj", "Pop"),
    BrowseCategory("Podcasts", Color(0xFF006450), "https://lh3.googleusercontent.com/hnefGBrazRhn4Z92bdSZBUENl40ONjRiVDsmZKZh-WZ2iCKE-W_YyJtZ_fL4w=w544-h544-l90-rj", "Podcast"),
    BrowseCategory("Audiobooks", Color(0xFF283EA3), "https://lh3.googleusercontent.com/f0uIq9s7qQYV4f_4zB1=w544-h544-l90-rj", "Audiobook"),
    BrowseCategory("Live Events", Color(0xFF7358FF), "https://lh3.googleusercontent.com/zV8Q8k=w544-h544-l90-rj", "Live Performance"),
    BrowseCategory("Made For You", Color(0xFF8C1932), "https://lh3.googleusercontent.com/dK0M_Fh5uQ6s4z7=w544-h544-l90-rj", "Favorites"),
    BrowseCategory("Fitness", Color(0xFF4B5563), "https://lh3.googleusercontent.com/k9_a1=w544-h544-l90-rj", "Workout"),
    BrowseCategory("Pop", Color(0xFFFF4632), "https://lh3.googleusercontent.com/sc_b2=w544-h544-l90-rj", "Pop Hits"),
    BrowseCategory("Hip-Hop", Color(0xFFBA5D07), "https://lh3.googleusercontent.com/RCpTA6EXJQyjVFDosWOKa2SMmqkua_lA9mHPDWWciLwgqpZLz9fS3xYq9R0_L9kF=w544-h544-l90-rj", "Hip Hop"),
    BrowseCategory("Rock", Color(0xFFE91429), "https://lh3.googleusercontent.com/f0uIq9s7qQYV4f_4zB1=w544-h544-l90-rj", "Rock Classics"),
    BrowseCategory("Anime & J-Pop", Color(0xFF0D72EC), "https://lh3.googleusercontent.com/hnefGBrazRhn4Z92bdSZBUENl40ONjRiVDsmZKZh-WZ2iCKE-W_YyJtZ_fL4w=w544-h544-l90-rj", "Anime Jpop"),
    BrowseCategory("Chill & Lo-Fi", Color(0xFF148A08), "https://lh3.googleusercontent.com/zV8Q8k=w544-h544-l90-rj", "Lofi Chill"),
    BrowseCategory("Charts", Color(0xFF8D67AB), "https://lh3.googleusercontent.com/k9_a1=w544-h544-l90-rj", "Top 50")
)

private val WATCH_PLAYLISTS = listOf(
    WatchPlaylistCard("VIDEOS FOR YOU", "VIDEOS FOR YOU", "https://images.unsplash.com/photo-1514525253161-7a46d19cd819?w=600&q=80", "Music Videos"),
    WatchPlaylistCard("Top Viral Throwbacks", null, "https://images.unsplash.com/photo-1470225620780-dba8ba36b745?w=600&q=80", "Throwback Hits"),
    WatchPlaylistCard("Throwback Party", null, "https://images.unsplash.com/photo-1516450360452-9312f5e86fc7?w=600&q=80", "Party Hits"),
    WatchPlaylistCard("Anime Soundtracks", "J-POP", "https://images.unsplash.com/photo-1578632767115-351597cf2477?w=600&q=80", "Anime OST"),
    WatchPlaylistCard("Lofi Study Sessions", "LO-FI", "https://images.unsplash.com/photo-1507525428034-b723cf961d3e?w=600&q=80", "Lofi Beats")
)

@Composable
fun SearchScreen(
    query: String,
    filter: String,
    results: List<AuraTrack>,
    isSearching: Boolean,
    favorites: List<AuraTrack>,
    downloads: List<AuraTrack>,
    downloadingIds: Set<String>,
    recentlyPlayed: List<AuraTrack> = emptyList(),
    matchedArtist: YtmTopArtist? = null,
    matchedArtists: List<YtmTopArtist> = emptyList(),
    artistFeaturingItems: List<ArtistCarouselItem> = emptyList(),
    playlistResults: List<YtmCommunityPlaylist> = emptyList(),
    exploreArtworkTracks: Map<String, List<AuraTrack>> = emptyMap(),
    currentPlayingId: String? = null,
    isPlaying: Boolean = false,
    onQueryChange: (String) -> Unit,
    onFilterChange: (String) -> Unit,
    onPlayTrack: (AuraTrack, List<AuraTrack>) -> Unit,
    onPlaySearchTrack: (AuraTrack, List<AuraTrack>) -> Unit = onPlayTrack,
    onPlayMusicVideo: (AuraTrack, List<AuraTrack>) -> Unit = onPlayTrack,
    onToggleFavorite: (AuraTrack) -> Unit,
    onDownloadTrack: (AuraTrack) -> Unit,
    playlists: List<AuraPlaylist> = emptyList(),
    onAddTrackToPlaylist: (playlistId: String, track: AuraTrack) -> Unit = { _, _ -> },
    onCreatePlaylist: (name: String, description: String, coverUrl: String) -> Unit = { _, _, _ -> },
    onRemoveRecentlyPlayed: (AuraTrack) -> Unit = {},
    onOpenArtist: (String, String) -> Unit = { _, _ -> },
    onOpenCommunityPlaylist: (YtmCommunityPlaylist) -> Unit = {},
    onPlayCommunityPlaylist: (YtmCommunityPlaylist) -> Unit = {},
    onToggleSaveCommunityPlaylist: (YtmCommunityPlaylist) -> Unit = {}
) {
    val context = LocalContext.current
    var isSearchActive by rememberSaveable { mutableStateOf(query.isNotBlank()) }
    var selectedModalTrack by remember { mutableStateOf<AuraTrack?>(null) }
    var selectedCommunityPlaylistForOptions by remember { mutableStateOf<YtmCommunityPlaylist?>(null) }
    var trackForAddToPlaylist by remember { mutableStateOf<AuraTrack?>(null) }
    var showCreatePlaylistModal by remember { mutableStateOf(false) }
    val focusRequester = remember { FocusRequester() }

    val showActiveSearch = isSearchActive || query.isNotBlank()

    // If query gets populated from external or user types, activate search screen
    LaunchedEffect(query) {
        if (query.isNotBlank() && !isSearchActive) {
            isSearchActive = true
        }
    }

    // Intercept back button when dedicated search screen is open
    BackHandler(enabled = showActiveSearch) {
        if (query.isNotEmpty()) {
            onQueryChange("")
        } else {
            isSearchActive = false
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(SpotifyPitchBlack)
    ) {
        if (!showActiveSearch) {
            // =========================================================================
            // 1. SEARCH EXPLORE SCREEN (Default Browse Mode matching Image 1)
            // =========================================================================
            SearchExploreView(
                exploreArtworkTracks = exploreArtworkTracks,
                onSearchFieldClick = {
                    isSearchActive = true
                },
                onCategoryClick = { category ->
                    isSearchActive = true
                    onQueryChange(category.searchQuery)
                },
                onWatchPlaylistClick = { watchItem ->
                    isSearchActive = true
                    onQueryChange(watchItem.query)
                }
            )
        } else {
            // =========================================================================
            // 2. DEDICATED SEARCHING SCREEN (Active Search Mode matching Image 2)
            // =========================================================================
            DedicatedSearchingView(
                query = query,
                filter = filter,
                results = results,
                isSearching = isSearching,
                favorites = favorites,
                recentlyPlayed = recentlyPlayed,
                matchedArtist = matchedArtist,
                matchedArtists = matchedArtists,
                artistFeaturingItems = artistFeaturingItems,
                playlistResults = playlistResults,
                currentPlayingId = currentPlayingId,
                isPlaying = isPlaying,
                focusRequester = focusRequester,
                onBackClick = {
                    onQueryChange("")
                    isSearchActive = false
                },
                onQueryChange = onQueryChange,
                onFilterChange = onFilterChange,
                onPlayTrack = onPlayTrack,
                onPlaySearchTrack = onPlaySearchTrack,
                onPlayMusicVideo = onPlayMusicVideo,
                onToggleFavorite = onToggleFavorite,
                onRemoveRecentlyPlayed = onRemoveRecentlyPlayed,
                onOptionsClick = { selectedModalTrack = it },
                onOpenArtist = onOpenArtist,
                onOpenCommunityPlaylist = onOpenCommunityPlaylist,
                playlists = playlists,
                onToggleSaveCommunityPlaylist = onToggleSaveCommunityPlaylist,
                onCommunityPlaylistOptionsClick = { selectedCommunityPlaylistForOptions = it }
            )
        }

        // Track Options Modal (Play Next, Add to Queue, Liked Songs, Go to Artist)
        selectedModalTrack?.let { modalTrack ->
            AuraTrackOptionsModal(
                track = modalTrack,
                isFavorite = favorites.any { it.videoId == modalTrack.videoId },
                onDismiss = { selectedModalTrack = null },
                onPlayNext = {
                    AuraPlayerManager.playNext(modalTrack)
                    Toast.makeText(context, "Playing next: ${modalTrack.title}", Toast.LENGTH_SHORT).show()
                    selectedModalTrack = null
                },
                onAddToQueue = {
                    AuraPlayerManager.enqueueTrack(modalTrack)
                    Toast.makeText(context, "Added to queue: ${modalTrack.title}", Toast.LENGTH_SHORT).show()
                    selectedModalTrack = null
                },
                onToggleFavorite = {
                    val wasFav = favorites.any { it.videoId == modalTrack.videoId }
                    onToggleFavorite(modalTrack)
                    val msg = if (wasFav) "Removed from Liked Songs" else "Added to Liked Songs"
                    Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
                    selectedModalTrack = null
                },
                onAddToPlaylist = {
                    val t = modalTrack
                    selectedModalTrack = null
                    trackForAddToPlaylist = t
                },
                onGoToArtist = {
                    selectedModalTrack = null
                    onOpenArtist(modalTrack.artist, modalTrack.title)
                },
                isDownloaded = downloads.any { it.videoId == modalTrack.videoId },
                onDownloadTrack = {
                    onDownloadTrack(modalTrack)
                    Toast.makeText(context, "Downloading ${modalTrack.title}…", Toast.LENGTH_SHORT).show()
                    selectedModalTrack = null
                },
                onWatchVideo = {
                    selectedModalTrack = null
                    onPlayTrack(modalTrack, listOf(modalTrack))
                    com.auramusic.app.core.media.AuraPlayerManager.setVideoMode(true)
                }
            )
        }

        // Modal: Add Song to Playlist
        trackForAddToPlaylist?.let { trackToAdd ->
            AuraAddToPlaylistModal(
                track = trackToAdd,
                playlists = playlists,
                onDismiss = { trackForAddToPlaylist = null },
                onSelectPlaylist = { targetPl ->
                    onAddTrackToPlaylist(targetPl.id, trackToAdd)
                    trackForAddToPlaylist = null
                    Toast.makeText(context, "Added '${trackToAdd.title}' to '${targetPl.name}'", Toast.LENGTH_SHORT).show()
                },
                onCreateNewPlaylist = {
                    trackForAddToPlaylist = null
                    showCreatePlaylistModal = true
                }
            )
        }

        // Modal: Create Playlist on the fly
        if (showCreatePlaylistModal) {
            AuraCreatePlaylistModal(
                onDismiss = { showCreatePlaylistModal = false },
                onCreate = { title, desc, coverUri ->
                    showCreatePlaylistModal = false
                    onCreatePlaylist(title, desc, coverUri)
                    Toast.makeText(context, "Created playlist: $title", Toast.LENGTH_SHORT).show()
                }
            )
        }

        // Modal: Community / Search Playlist 3-Dot Options Modal
        selectedCommunityPlaylistForOptions?.let { pl ->
            val isSaved = playlists.any {
                it.id == pl.id || it.id == "ytm_${pl.id}" || it.id.removePrefix("VL") == pl.id.removePrefix("VL")
            }
            AuraCommunityPlaylistOptionsModal(
                playlist = pl,
                isSaved = isSaved,
                onDismiss = { selectedCommunityPlaylistForOptions = null },
                onToggleSave = { onToggleSaveCommunityPlaylist(pl) },
                onPlayPlaylist = { onPlayCommunityPlaylist(pl) },
                onOpenDetails = { onOpenCommunityPlaylist(pl) }
            )
        }
    }
}

/**
 * Explore Screen matching Image 1:
 * - Search headline
 * - White search input button ("What do you want to listen to?")
 * - 2-column colorful angled-image category cards
 * - "Playlists you can watch" portrait video carousel
 * - "More for you" category cards
 */
@Composable
private fun SearchExploreView(
    exploreArtworkTracks: Map<String, List<AuraTrack>>,
    onSearchFieldClick: () -> Unit,
    onCategoryClick: (BrowseCategory) -> Unit,
    onWatchPlaylistClick: (WatchPlaylistCard) -> Unit
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(bottom = 140.dp)
    ) {
        item {
            // Header: Title & White Search Bar
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp)
                    .padding(top = 10.dp)
            ) {
                Text(
                    text = "Search",
                    style = MaterialTheme.typography.headlineLarge,
                    color = TextPrimary,
                    fontWeight = FontWeight.Bold
                )

                Spacer(modifier = Modifier.height(14.dp))

                // White Search Bar matching Image 1
                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .clickable(onClick = onSearchFieldClick),
                    shape = RoundedCornerShape(8.dp),
                    color = Color.White
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(horizontal = 14.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Search,
                            contentDescription = "Search",
                            tint = Color(0xFF191414),
                            modifier = Modifier.size(24.dp)
                        )

                        Spacer(modifier = Modifier.width(10.dp))

                        Text(
                            text = "What do you want to listen to?",
                            fontSize = 14.5.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFF555555)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(20.dp))

                Text(
                    text = "Start browsing",
                    fontSize = 17.sp,
                    fontWeight = FontWeight.Bold,
                    color = TextPrimary
                )

                Spacer(modifier = Modifier.height(12.dp))
            }
        }

        // First 4 Top Category Cards (2 columns)
        val firstBatch = CURATED_BROWSE_CATEGORIES.take(4)
        items(firstBatch.chunked(2)) { pair ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 6.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                pair.forEach { category ->
                    Box(modifier = Modifier.weight(1f)) {
                        SpotifyCategoryCard(
                            category = category,
                            artworkTracks = exploreArtworkTracks[category.title].orEmpty(),
                            onClick = { onCategoryClick(category) }
                        )
                    }
                }
                if (pair.size == 1) {
                    Spacer(modifier = Modifier.weight(1f))
                }
            }
        }

        // Carousel Section: "Playlists you can watch" matching Image 1
        item {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 22.dp, bottom = 12.dp)
            ) {
                Text(
                    text = "Playlists you can watch",
                    fontSize = 19.sp,
                    fontWeight = FontWeight.Bold,
                    color = TextPrimary,
                    modifier = Modifier.padding(horizontal = 16.dp)
                )

                Spacer(modifier = Modifier.height(14.dp))

                LazyRow(
                    contentPadding = PaddingValues(horizontal = 16.dp),
                    horizontalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    items(WATCH_PLAYLISTS) { watchCard ->
                        PortraitWatchPlaylistCard(
                            item = watchCard,
                            onClick = { onWatchPlaylistClick(watchCard) }
                        )
                    }
                }
            }
        }

        // Section: "More for you" category cards
        item {
            Text(
                text = "More for you",
                fontSize = 19.sp,
                fontWeight = FontWeight.Bold,
                color = TextPrimary,
                modifier = Modifier
                    .padding(horizontal = 16.dp)
                    .padding(top = 16.dp, bottom = 12.dp)
            )
        }

        val secondBatch = CURATED_BROWSE_CATEGORIES.drop(4)
        items(secondBatch.chunked(2)) { pair ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 6.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                pair.forEach { category ->
                    Box(modifier = Modifier.weight(1f)) {
                        SpotifyCategoryCard(
                            category = category,
                            artworkTracks = exploreArtworkTracks[category.title].orEmpty(),
                            onClick = { onCategoryClick(category) }
                        )
                    }
                }
                if (pair.size == 1) {
                    Spacer(modifier = Modifier.weight(1f))
                }
            }
        }
    }
}

/**
 * Spotify Category Card:
 * Bold white title in top-left, rotated angled artwork overflowing at bottom-right corner.
 */
@Composable
private fun SpotifyCategoryCard(
    category: BrowseCategory,
    artworkTracks: List<AuraTrack>,
    onClick: () -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(98.dp)
            .clip(RoundedCornerShape(8.dp))
            .background(category.backgroundColor)
            .clickable(onClick = onClick)
    ) {
        if (artworkTracks.size >= 2) {
            Box(
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .graphicsLayer {
                        translationX = 14.dp.toPx()
                        translationY = 8.dp.toPx()
                    }
                    .size(width = 96.dp, height = 84.dp)
            ) {
                artworkTracks.take(3).forEachIndexed { index, track ->
                    AsyncImage(
                        model = track.highResArtworkUrl,
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier
                            .align(Alignment.Center)
                            .offset(x = ((index - 1) * 21).dp, y = 5.dp)
                            .rotate((index - 1) * 16f)
                            .zIndex(index.toFloat())
                            .size(width = 48.dp, height = 58.dp)
                            .clip(RoundedCornerShape(6.dp))
                    )
                }
            }
        } else {
            // Keep curated fallback artwork visible while category covers load.
            AsyncImage(
                model = category.artworkUrl,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .graphicsLayer {
                        translationX = 14.dp.toPx()
                        translationY = 8.dp.toPx()
                        rotationZ = 25f
                    }
                    .size(68.dp)
                    .clip(RoundedCornerShape(6.dp))
            )
        }

        // Category Title
        Text(
            text = category.title,
            fontSize = 16.sp,
            fontWeight = FontWeight.Bold,
            color = Color.White,
            modifier = Modifier
                .align(Alignment.TopStart)
                .padding(top = 12.dp, start = 12.dp, end = 46.dp),
            maxLines = 2,
            lineHeight = 20.sp
        )
    }
}

/**
 * Vertical Portrait Playlist Card matching "Playlists you can watch" in Image 1.
 */
@Composable
private fun PortraitWatchPlaylistCard(
    item: WatchPlaylistCard,
    onClick: () -> Unit
) {
    Surface(
        modifier = Modifier
            .width(145.dp)
            .height(215.dp)
            .clip(RoundedCornerShape(12.dp))
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(12.dp),
        color = Color(0xFF1E2028)
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            AsyncImage(
                model = item.artworkUrl,
                contentDescription = item.title,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize()
            )

            // Scrim overlay for legibility
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        Brush.verticalGradient(
                            colors = listOf(
                                Color.Transparent,
                                Color(0x33000000),
                                Color(0xCC000000),
                                Color(0xFA000000)
                            )
                        )
                    )
            )

            // Content at the bottom
            Column(
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .padding(12.dp)
            ) {
                if (item.badge != null) {
                    Surface(
                        shape = RoundedCornerShape(4.dp),
                        color = Color(0xFFD946EF),
                        modifier = Modifier.padding(bottom = 6.dp)
                    ) {
                        Text(
                            text = item.badge,
                            fontSize = 9.sp,
                            fontWeight = FontWeight.ExtraBold,
                            color = Color.White,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                            letterSpacing = 0.5.sp
                        )
                    }
                }

                Text(
                    text = item.title,
                    fontSize = 13.5.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White,
                    maxLines = 2,
                    lineHeight = 17.sp,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

/**
 * Dedicated Searching Screen matching Image 2:
 * - Header with `←` back arrow, search text field with auto-focus and clear button
 * - Filter pills pinned inside the header below search input
 * - When query is blank: list of recently played songs with cover image, title, artist, heart icon, and `✕` remove button
 * - When query is active: matched artist header with "Follow" button, "Featuring" carousel, and song search tiles with 3-dot and heart icons
 */
@Composable
private fun DedicatedSearchingView(
    query: String,
    filter: String,
    results: List<AuraTrack>,
    isSearching: Boolean,
    favorites: List<AuraTrack>,
    recentlyPlayed: List<AuraTrack>,
    matchedArtist: YtmTopArtist?,
    matchedArtists: List<YtmTopArtist> = emptyList(),
    artistFeaturingItems: List<ArtistCarouselItem>,
    playlistResults: List<YtmCommunityPlaylist> = emptyList(),
    currentPlayingId: String?,
    isPlaying: Boolean = false,
    focusRequester: FocusRequester,
    onBackClick: () -> Unit,
    onQueryChange: (String) -> Unit,
    onFilterChange: (String) -> Unit,
    onPlayTrack: (AuraTrack, List<AuraTrack>) -> Unit,
    onPlaySearchTrack: (AuraTrack, List<AuraTrack>) -> Unit = onPlayTrack,
    onPlayMusicVideo: (AuraTrack, List<AuraTrack>) -> Unit = onPlayTrack,
    onToggleFavorite: (AuraTrack) -> Unit,
    onRemoveRecentlyPlayed: (AuraTrack) -> Unit,
    onOptionsClick: (AuraTrack) -> Unit,
    onOpenArtist: (String, String) -> Unit,
    onOpenCommunityPlaylist: (YtmCommunityPlaylist) -> Unit = {},
    playlists: List<AuraPlaylist> = emptyList(),
    onToggleSaveCommunityPlaylist: (YtmCommunityPlaylist) -> Unit = {},
    onCommunityPlaylistOptionsClick: (YtmCommunityPlaylist) -> Unit = {}
) {
    val listState = rememberLazyListState()
    val focusManager = LocalFocusManager.current
    val keyboardController = LocalSoftwareKeyboardController.current
    val uniqueRecent = remember(recentlyPlayed) {
        recentlyPlayed.distinctBy { it.videoId.ifBlank { it.id } }
    }

    // Request keyboard focus when dedicated search screen is mounted
    LaunchedEffect(Unit) {
        if (query.isEmpty()) {
            try {
                focusRequester.requestFocus()
            } catch (_: Exception) {}
        }
    }

    // Clear input focus when scrolling search results so opening modals/player later never re-triggers the IME
    LaunchedEffect(listState.isScrollInProgress) {
        if (listState.isScrollInProgress) {
            focusManager.clearFocus()
            keyboardController?.hide()
        }
    }

    // Always ensure list starts at top when typing a query or when matched artist/results arrive
    LaunchedEffect(query) {
        listState.scrollToItem(0)
    }
    LaunchedEffect(matchedArtist) {
        if (matchedArtist != null) {
            listState.scrollToItem(0)
        }
    }
    LaunchedEffect(results) {
        if (results.isNotEmpty() && matchedArtist == null) {
            listState.scrollToItem(0)
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(SpotifyPitchBlack)
    ) {
        // =========================================================================
        // PINNED HEADER: Back Button + Search Box + Pinned Filter Pills
        // =========================================================================
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(Color(0xFF14151B))
                .statusBarsPadding()
                .padding(top = 4.dp, bottom = 8.dp)
        ) {
            // Search Input Row
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(
                    onClick = onBackClick,
                    modifier = Modifier.size(44.dp)
                ) {
                    Icon(
                        imageVector = Icons.Rounded.ArrowBack,
                        contentDescription = "Back to Explore",
                        tint = Color.White,
                        modifier = Modifier.size(24.dp)
                    )
                }

                Spacer(modifier = Modifier.width(4.dp))

                // Search Text Box
                Surface(
                    modifier = Modifier
                        .weight(1f)
                        .height(42.dp),
                    shape = RoundedCornerShape(8.dp),
                    color = Color(0xFF262832)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(horizontal = 12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier.weight(1f),
                            contentAlignment = Alignment.CenterStart
                        ) {
                            if (query.isEmpty()) {
                                Text(
                                    text = "What do you want to listen to?",
                                    color = Color(0xFF8E929E),
                                    fontSize = 13.5.sp,
                                    maxLines = 1
                                )
                            }

                            BasicTextField(
                                value = query,
                                onValueChange = onQueryChange,
                                singleLine = true,
                                textStyle = MaterialTheme.typography.bodyMedium.copy(
                                    color = Color.White,
                                    fontSize = 14.sp
                                ),
                                cursorBrush = SolidColor(AuraNeonLime),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .focusRequester(focusRequester)
                            )
                        }

                        if (query.isNotEmpty()) {
                            IconButton(
                                onClick = { onQueryChange("") },
                                modifier = Modifier.size(28.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.Clear,
                                    contentDescription = "Clear search",
                                    tint = Color(0xFFB0B4C0),
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.width(8.dp))
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Filter Pills pinned inside the header below search field matching Image 2
            val filterOptions = listOf(
                "all" to "All",
                "artists" to "Artists",
                "songs" to "Songs",
                "videos" to "Videos",
                "playlists" to "Playlists",
                "albums" to "Albums"
            )

            LazyRow(
                contentPadding = PaddingValues(horizontal = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(filterOptions) { (key, label) ->
                    val isSelected = filter == key
                    Surface(
                        shape = RoundedCornerShape(16.dp),
                        color = if (isSelected) AuraNeonLime else Color(0xFF262832),
                        modifier = Modifier
                            .clip(RoundedCornerShape(16.dp))
                            .clickable {
                                focusManager.clearFocus()
                                keyboardController?.hide()
                                onFilterChange(key)
                            }
                    ) {
                        Text(
                            text = label,
                            color = if (isSelected) Color(0xFF111215) else Color(0xFFC0C4CC),
                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                            fontSize = 12.sp,
                            modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp)
                        )
                    }
                }
            }
        }

        // =========================================================================
        // SCROLL CONTENT: Recent Searches (if blank) OR Query Search Results (if active)
        // =========================================================================
        LazyColumn(
            state = listState,
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(top = 10.dp, bottom = 140.dp)
        ) {
            if (query.isBlank()) {
                // -------------------------------------------------------------
                // EMPTY QUERY: POPULATE RECENT PLAYED SONGS WITH HEART & X
                // -------------------------------------------------------------
                item {
                    Text(
                        text = "Recent",
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White,
                        modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp)
                    )
                }

                if (recentlyPlayed.isEmpty()) {
                    item {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 48.dp, horizontal = 24.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Text(
                                text = "Play songs or search to build your recent history",
                                color = Color(0xFF7A7D87),
                                fontSize = 13.5.sp
                            )
                        }
                    }
                } else {
                    items(uniqueRecent, key = { "recent_${it.videoId.ifBlank { it.id }}" }) { track ->
                        val isFav = favorites.any { it.videoId == track.videoId }
                        val isCurrent = currentPlayingId == track.videoId
                        RecentPlayedTrackRow(
                            track = track,
                            isCurrent = isCurrent,
                            isPlaying = isPlaying,
                            isFavorite = isFav,
                            onClick = { onPlayTrack(track, uniqueRecent) },
                            onOptionsClick = { onOptionsClick(track) },
                            onToggleFavorite = { onToggleFavorite(track) },
                            onRemove = { onRemoveRecentlyPlayed(track) }
                        )
                    }
                }
            } else {
                // -------------------------------------------------------------
                // ACTIVE QUERY: ARTIST MATCH + FEATURING CAROUSEL + SONG TILES
                // -------------------------------------------------------------
                if (filter == "playlists") {
                    if (isSearching && playlistResults.isEmpty()) {
                        items(6) {
                            TrackItemSkeleton()
                        }
                    } else {
                        items(playlistResults.distinctBy { it.id }, key = { "pl_res_${it.id}" }) { playlist ->
                            val isSaved = playlists.any { pl ->
                                pl.id == playlist.id ||
                                pl.id == "ytm_${playlist.id}" ||
                                pl.id.removePrefix("VL") == playlist.id.removePrefix("VL")
                            }
                            SearchResultPlaylistTile(
                                playlist = playlist,
                                isSaved = isSaved,
                                onClick = {
                                    focusManager.clearFocus()
                                    keyboardController?.hide()
                                    onOpenCommunityPlaylist(playlist)
                                },
                                onToggleSave = {
                                    onToggleSaveCommunityPlaylist(playlist)
                                },
                                onOptionsClick = {
                                    focusManager.clearFocus()
                                    keyboardController?.hide()
                                    onCommunityPlaylistOptionsClick(playlist)
                                }
                            )
                        }
                    }
                } else if (isSearching && results.isEmpty()) {
                    items(6) {
                        TrackItemSkeleton()
                    }
                } else {
                    // 1. Matched Artist Header Card (Image 2 style)
                    if (matchedArtist != null && (filter == "all" || filter == "artists")) {
                        item(key = "matched_artist_${matchedArtist.id}") {
                            ArtistSearchResultHeader(
                                artist = matchedArtist,
                                onClick = { onOpenArtist(matchedArtist.name, "") }
                            )
                        }
                    }

                    // 2. Featuring Carousel (Image 2 style)
                    if (matchedArtist != null && artistFeaturingItems.isNotEmpty() && (filter == "all" || filter == "artists" || filter == "albums")) {
                        item(key = "feat_carousel_${matchedArtist.id}") {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(top = 16.dp, bottom = 8.dp)
                            ) {
                                Text(
                                    text = "Featuring ${matchedArtist.name}",
                                    fontSize = 18.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Color.White,
                                    modifier = Modifier.padding(horizontal = 20.dp, vertical = 6.dp)
                                )

                                LazyRow(
                                    contentPadding = PaddingValues(horizontal = 20.dp),
                                    horizontalArrangement = Arrangement.spacedBy(14.dp)
                                ) {
                                    items(artistFeaturingItems, key = { it.id }) { featItem ->
                                        FeaturingAlbumCard(
                                            item = featItem,
                                            onClick = { onOpenArtist(matchedArtist.name, "") }
                                        )
                                    }
                                }
                            }
                        }
                    }

                    // In "all" mode, show secondary artist if present right below featuring carousel (Image 2 style)
                    if (filter == "all" && matchedArtists.size > 1) {
                        val secondaryArtist = matchedArtists[1]
                        item(key = "secondary_artist_${secondaryArtist.id}") {
                            ArtistSearchResultHeader(
                                artist = secondaryArtist,
                                onClick = { onOpenArtist(secondaryArtist.name, "") }
                            )
                        }
                    }

                    // In "artists" mode, show all other matching artists
                    if (filter == "artists") {
                        val remainingArtists = if (matchedArtist != null) {
                            matchedArtists.filter { it.id != matchedArtist.id }
                        } else matchedArtists

                        items(remainingArtists, key = { "artist_${it.id}" }) { artist ->
                            ArtistSearchResultHeader(
                                artist = artist,
                                onClick = { onOpenArtist(artist.name, "") }
                            )
                        }
                    }

                    // 3. Song Search Result Tiles (Image 2 style: Cover + Title/Artist + 3-dot + Heart)
                    items(results.distinctBy { it.videoId }, key = { "res_${it.videoId}" }) { track ->
                        val isFav = favorites.any { it.videoId == track.videoId }
                        val isCurrent = currentPlayingId == track.videoId
                        val isVideoItem = filter == "videos" || (track.musicVideoType != null && track.musicVideoType != "PODCAST")

                        SearchResultSongTile(
                            track = track,
                            isCurrent = isCurrent,
                            isPlaying = isPlaying,
                            isFavorite = isFav,
                            isVideo = isVideoItem,
                            onClick = {
                                focusManager.clearFocus()
                                keyboardController?.hide()
                                if (isVideoItem) {
                                    onPlayMusicVideo(track, results)
                                } else {
                                    onPlaySearchTrack(track, results)
                                }
                            },
                            onToggleFavorite = { onToggleFavorite(track) },
                            onOptionsClick = {
                                focusManager.clearFocus()
                                keyboardController?.hide()
                                onOptionsClick(track)
                            }
                        )
                    }
                }
            }
        }
    }
}

/**
 * Tile for Recent Played Songs on the Dedicated Search Screen:
 * Cover image, Title, Artist, and on the right side: Heart icon and 'X' remove icon.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun RecentPlayedTrackRow(
    track: AuraTrack,
    isCurrent: Boolean = false,
    isPlaying: Boolean = false,
    isFavorite: Boolean,
    onClick: () -> Unit,
    onOptionsClick: () -> Unit,
    onToggleFavorite: () -> Unit,
    onRemove: () -> Unit
) {
    val isTrackPlaying = isCurrent && isPlaying
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .combinedClickable(onClick = onClick, onLongClick = onOptionsClick)
            .padding(horizontal = 20.dp, vertical = 7.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(52.dp)
                .clip(RoundedCornerShape(8.dp))
                .background(Color(0xFF202127)),
            contentAlignment = Alignment.Center
        ) {
            AsyncImage(
                model = track.highResArtworkUrl.ifBlank { "https://i.ytimg.com/vi/${track.videoId}/hqdefault.jpg" },
                contentDescription = track.title,
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
                        modifier = Modifier.fillMaxSize(0.70f),
                        color = AuraNeonLime
                    )
                }
            }
        }

        Spacer(modifier = Modifier.width(14.dp))

        Column(
            modifier = Modifier.weight(1f)
        ) {
            Text(
                text = track.title,
                style = MaterialTheme.typography.titleSmall,
                color = Color.White,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )

            Spacer(modifier = Modifier.height(2.dp))

            Text(
                text = "Song • ${track.artist}",
                style = MaterialTheme.typography.bodySmall,
                color = Color(0xFFA0A5B5),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }

        Spacer(modifier = Modifier.width(8.dp))

        // Heart Icon on the right
        IconButton(
            onClick = onToggleFavorite,
            modifier = Modifier.size(38.dp)
        ) {
            Icon(
                imageVector = if (isFavorite) Icons.Rounded.Favorite else Icons.Rounded.FavoriteBorder,
                contentDescription = if (isFavorite) "Unlike" else "Like",
                tint = if (isFavorite) AuraNeonLime else Color(0xFFA0A5B5),
                modifier = Modifier.size(20.dp)
            )
        }

        // 'X' Remove from recent list icon button
        IconButton(
            onClick = onRemove,
            modifier = Modifier.size(38.dp)
        ) {
            Icon(
                imageVector = Icons.Rounded.Close,
                contentDescription = "Remove from recent",
                tint = Color(0xFF7E8290),
                modifier = Modifier.size(20.dp)
            )
        }
    }
}

/**
 * Artist Result Header Tile matching Image 2:
 * Circular Avatar + Artist Name with Verified Checkmark + "Follow" Pill Button.
 */
@Composable
private fun ArtistSearchResultHeader(
    artist: YtmTopArtist,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 20.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        AsyncImage(
            model = artist.highResAvatarUrl,
            contentDescription = artist.name,
            contentScale = ContentScale.Crop,
            modifier = Modifier
                .size(60.dp)
                .clip(CircleShape)
        )

        Spacer(modifier = Modifier.width(14.dp))

        Column(modifier = Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = artist.name,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )

                Spacer(modifier = Modifier.width(6.dp))

                Icon(
                    imageVector = Icons.Rounded.CheckCircle,
                    contentDescription = "Verified Artist",
                    tint = AuraNeonLime,
                    modifier = Modifier.size(16.dp)
                )
            }

            Spacer(modifier = Modifier.height(3.dp))

            val subText = if (artist.subscribers.isNotBlank()) {
                if (artist.subscribers.startsWith("Artist", ignoreCase = true)) artist.subscribers else "Artist • ${artist.subscribers}"
            } else "Artist"

            Text(
                text = subText,
                fontSize = 12.5.sp,
                color = Color(0xFFA0A5B5),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

/**
 * Square Featuring Card matching the horizontal carousel below the artist in Image 2.
 */
@Composable
private fun FeaturingAlbumCard(
    item: ArtistCarouselItem,
    onClick: () -> Unit
) {
    Column(
        modifier = Modifier
            .width(135.dp)
            .clickable(onClick = onClick)
    ) {
        AsyncImage(
            model = item.highResArtworkUrl,
            contentDescription = item.title,
            contentScale = ContentScale.Crop,
            modifier = Modifier
                .size(135.dp)
                .clip(RoundedCornerShape(8.dp))
        )

        Spacer(modifier = Modifier.height(8.dp))

        Text(
            text = item.title,
            fontSize = 13.sp,
            fontWeight = FontWeight.Bold,
            color = Color.White,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )

        if (item.subtitle.isNotBlank()) {
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = item.subtitle,
                fontSize = 11.5.sp,
                color = Color(0xFFA0A5B5),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

/**
 * Playlist Search Result Tile rendered when the "Playlists" filter pill is active.
 */
@Composable
private fun SearchResultPlaylistTile(
    playlist: YtmCommunityPlaylist,
    isSaved: Boolean,
    onClick: () -> Unit,
    onToggleSave: () -> Unit,
    onOptionsClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 20.dp, vertical = 9.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        AsyncImage(
            model = playlist.highResArtworkUrl,
            contentDescription = playlist.title,
            contentScale = ContentScale.Crop,
            modifier = Modifier
                .size(54.dp)
                .clip(RoundedCornerShape(8.dp))
        )

        Spacer(modifier = Modifier.width(14.dp))

        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = playlist.title,
                fontSize = 14.5.sp,
                fontWeight = FontWeight.Bold,
                color = TextPrimary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Spacer(modifier = Modifier.height(3.dp))
            val subtitleText = buildString {
                append("Playlist")
                if (playlist.author.isNotBlank()) {
                    append(" • ")
                    append(playlist.author)
                }
                if (playlist.trackCount.isNotBlank() && !playlist.trackCount.equals("Playlist", ignoreCase = true)) {
                    append(" • ")
                    append(playlist.trackCount)
                }
            }
            Text(
                text = subtitleText,
                fontSize = 12.5.sp,
                color = TextSecondary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }

        // Heart / Like Button
        IconButton(
            onClick = onToggleSave,
            modifier = Modifier.size(36.dp)
        ) {
            Icon(
                imageVector = if (isSaved) Icons.Rounded.Favorite else Icons.Rounded.FavoriteBorder,
                contentDescription = if (isSaved) "Liked playlist" else "Like playlist",
                tint = if (isSaved) AuraNeonLime else TextSecondary,
                modifier = Modifier.size(20.dp)
            )
        }

        // 3-Dot Options Button
        IconButton(
            onClick = onOptionsClick,
            modifier = Modifier.size(36.dp)
        ) {
            Icon(
                imageVector = Icons.Rounded.MoreVert,
                contentDescription = "Playlist options",
                tint = TextSecondary,
                modifier = Modifier.size(20.dp)
            )
        }
    }
}

/**
 * Song Search Result Tile matching Image 2:
 * Square Artwork + Title & Artist + 3-Dot Options Icon + Heart Favorite Icon.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun SearchResultSongTile(
    track: AuraTrack,
    isCurrent: Boolean,
    isPlaying: Boolean = false,
    isFavorite: Boolean,
    isVideo: Boolean = false,
    onClick: () -> Unit,
    onToggleFavorite: () -> Unit,
    onOptionsClick: () -> Unit,
    onOpenArtist: (String, String) -> Unit = { _, _ -> }
) {
    val isTrackPlaying = isCurrent && isPlaying
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
        Box(
            modifier = Modifier
                .size(52.dp)
                .clip(RoundedCornerShape(8.dp))
                .background(Color(0xFF202127)),
            contentAlignment = Alignment.Center
        ) {
            AsyncImage(
                model = track.highResArtworkUrl.ifBlank { "https://i.ytimg.com/vi/${track.videoId}/hqdefault.jpg" },
                contentDescription = track.title,
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
                        modifier = Modifier.fillMaxSize(0.70f),
                        color = AuraNeonLime
                    )
                }
            }
        }

        Spacer(modifier = Modifier.width(14.dp))

        Column(
            modifier = Modifier.weight(1f)
        ) {
            Text(
                text = track.title,
                fontSize = 14.5.sp,
                fontWeight = FontWeight.Bold,
                color = if (isCurrent) AuraNeonLime else Color.White,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )

            Spacer(modifier = Modifier.height(3.dp))

            val typeTag = when {
                isVideo || (track.musicVideoType != null && track.musicVideoType != "PODCAST") -> "Video"
                track.isPodcast || track.musicVideoType == "PODCAST" -> "Podcast"
                else -> "Song"
            }

            Text(
                text = "$typeTag • ${track.artist}",
                fontSize = 12.5.sp,
                color = Color(0xFFA0A5B5),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }

        Spacer(modifier = Modifier.width(6.dp))

        // 3-Dot Options Menu Icon
        IconButton(
            onClick = onOptionsClick,
            modifier = Modifier.size(38.dp)
        ) {
            Icon(
                imageVector = Icons.Rounded.MoreVert,
                contentDescription = "Song options",
                tint = Color(0xFFA0A5B5),
                modifier = Modifier.size(20.dp)
            )
        }

        // Heart Like Icon
        IconButton(
            onClick = onToggleFavorite,
            modifier = Modifier.size(38.dp)
        ) {
            Icon(
                imageVector = if (isFavorite) Icons.Rounded.Favorite else Icons.Rounded.FavoriteBorder,
                contentDescription = if (isFavorite) "Unlike" else "Like",
                tint = if (isFavorite) AuraNeonLime else Color(0xFFA0A5B5),
                modifier = Modifier.size(20.dp)
            )
        }
    }
}
