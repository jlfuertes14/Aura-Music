package com.auramusic.app.ui

import android.app.Application
import android.util.Log
import android.widget.Toast
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.compose.ui.graphics.Color
import com.auramusic.app.core.extractor.SimpMusicExtractorEngine
import com.auramusic.app.core.extractor.ArtistEditorialRepository
import com.auramusic.app.core.comments.YouTubeCommentsRepository
import com.auramusic.app.core.lyrics.SimpMusicLyricsRepository
import com.auramusic.app.core.media.AuraPlayerManager
import com.auramusic.app.core.recommendations.LocalRecommendationEngine
import com.auramusic.app.core.notifications.AuraNotificationManager
import com.auramusic.app.core.sponsorblock.SponsorBlockRepository
import com.auramusic.app.core.update.AuraReleaseInfo
import com.auramusic.app.core.update.AuraUpdateManager
import com.auramusic.app.core.update.UpdateDownloadProgress
import com.auramusic.app.core.update.UpdateState
import com.auramusic.app.data.AuraStorageRepository
import com.auramusic.app.domain.model.ArtistCarouselItem
import com.auramusic.app.domain.model.ArtistDetail
import com.auramusic.app.domain.model.AuraCommentsPage
import com.auramusic.app.domain.model.ArtistSong
import com.auramusic.app.domain.model.AuraPalette
import com.auramusic.app.domain.model.AuraPlaylist
import com.auramusic.app.domain.model.AuraTrack
import com.auramusic.app.domain.model.AuraVideoInfo
import com.auramusic.app.domain.model.AuraChart
import com.auramusic.app.domain.model.ChartSupportedCountries
import com.auramusic.app.domain.model.EqualizerPreset
import com.auramusic.app.domain.model.ListeningStats
import com.auramusic.app.domain.model.LyricsData
import com.auramusic.app.domain.model.MoodCategory
import com.auramusic.app.domain.model.NowPlayingStyle
import com.auramusic.app.domain.model.PlaylistDetail
import com.auramusic.app.domain.model.PlaylistTrackItem
import com.auramusic.app.domain.model.CatalogDetail
import com.auramusic.app.domain.model.YtmMoodCategorySection
import com.auramusic.app.domain.model.YtmMoodCategoryItem
import com.auramusic.app.domain.model.ReturnYouTubeDislikeData
import com.auramusic.app.domain.model.YtmChartSection
import com.auramusic.app.domain.model.YtmCommunityPlaylist
import com.auramusic.app.domain.model.YtmMoodBrowseParams
import com.auramusic.app.domain.model.YtmMoodShelf
import com.auramusic.app.domain.model.YtmTopArtist
import com.auramusic.app.ui.theme.extractAuraPaletteFromUrl
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.distinctUntilChangedBy
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

class AuraViewModel(application: Application) : AndroidViewModel(application) {
    private val storage = AuraStorageRepository(application)

    // Home Charts & Moods
    private val _chartSections = MutableStateFlow<List<YtmChartSection>>(emptyList())
    val chartSections: StateFlow<List<YtmChartSection>> = _chartSections.asStateFlow()

    private val _selectedChartCountry = MutableStateFlow(storage.loadChartCountry())
    val selectedChartCountry: StateFlow<String> = _selectedChartCountry.asStateFlow()

    private val _chartData = MutableStateFlow<AuraChart?>(null)
    val chartData: StateFlow<AuraChart?> = _chartData.asStateFlow()

    private val _isChartLoading = MutableStateFlow(false)
    val isChartLoading: StateFlow<Boolean> = _isChartLoading.asStateFlow()

    private val _topArtists = MutableStateFlow<List<YtmTopArtist>>(emptyList())
    val topArtists: StateFlow<List<YtmTopArtist>> = _topArtists.asStateFlow()

    private val _visibleTopArtists = MutableStateFlow<List<YtmTopArtist>>(emptyList())
    val visibleTopArtists: StateFlow<List<YtmTopArtist>> = _visibleTopArtists.asStateFlow()

    private val _visibleRecentlyPlayed = MutableStateFlow<List<AuraTrack>>(emptyList())
    val visibleRecentlyPlayed: StateFlow<List<AuraTrack>> = _visibleRecentlyPlayed.asStateFlow()

    private val _visibleUserPlaylists = MutableStateFlow<List<AuraPlaylist>>(emptyList())
    val visibleUserPlaylists: StateFlow<List<AuraPlaylist>> = _visibleUserPlaylists.asStateFlow()

    private val _selectedArtistDetail = MutableStateFlow<ArtistDetail?>(null)
    val selectedArtistDetail: StateFlow<ArtistDetail?> = _selectedArtistDetail.asStateFlow()

    private val _nowPlayingArtistDetail = MutableStateFlow<ArtistDetail?>(null)
    val nowPlayingArtistDetail: StateFlow<ArtistDetail?> = _nowPlayingArtistDetail.asStateFlow()
    private val _isNowPlayingArtistLoading = MutableStateFlow(false)
    val isNowPlayingArtistLoading: StateFlow<Boolean> = _isNowPlayingArtistLoading.asStateFlow()
    private var nowPlayingArtistJob: Job? = null
    private var nowPlayingArtistKey: String? = null
    private val _nowPlayingVideoInfo = MutableStateFlow<AuraVideoInfo?>(null)
    val nowPlayingVideoInfo: StateFlow<AuraVideoInfo?> = _nowPlayingVideoInfo.asStateFlow()

    private val _isArtistLoading = MutableStateFlow(false)
    val isArtistLoading: StateFlow<Boolean> = _isArtistLoading.asStateFlow()

    private val _playerComments = MutableStateFlow<AuraCommentsPage?>(null)
    val playerComments: StateFlow<AuraCommentsPage?> = _playerComments.asStateFlow()
    private val _areCommentsLoading = MutableStateFlow(false)
    val areCommentsLoading: StateFlow<Boolean> = _areCommentsLoading.asStateFlow()
    private val _commentsError = MutableStateFlow<String?>(null)
    val commentsError: StateFlow<String?> = _commentsError.asStateFlow()
    private var commentsVideoId: String? = null

    private val _activeCategory = MutableStateFlow("All")
    val activeCategory: StateFlow<String> = _activeCategory.asStateFlow()

    private val _homeCategoryBackground = MutableStateFlow<String?>(null)
    val homeCategoryBackground: StateFlow<String?> = _homeCategoryBackground.asStateFlow()

    private val _categoryTracks = MutableStateFlow<List<AuraTrack>>(emptyList())
    val categoryTracks: StateFlow<List<AuraTrack>> = _categoryTracks.asStateFlow()

    private val _categoryShelves = MutableStateFlow<List<YtmMoodShelf>>(emptyList())
    val categoryShelves: StateFlow<List<YtmMoodShelf>> = _categoryShelves.asStateFlow()

    private val _homeRecommendations = MutableStateFlow<List<AuraTrack>>(emptyList())
    val homeRecommendations: StateFlow<List<AuraTrack>> = _homeRecommendations.asStateFlow()

    private val _homeAlbumRecommendations = MutableStateFlow<List<AuraTrack>>(emptyList())
    val homeAlbumRecommendations: StateFlow<List<AuraTrack>> = _homeAlbumRecommendations.asStateFlow()

    private val _homeLikedRecommendations = MutableStateFlow<List<AuraTrack>>(emptyList())
    val homeLikedRecommendations: StateFlow<List<AuraTrack>> = _homeLikedRecommendations.asStateFlow()

    private var rawHomeTracks: List<AuraTrack> = emptyList()
    private var rawHomeVideos: List<AuraTrack> = emptyList()
    private var rawHomeAlbums: List<AuraTrack> = emptyList()
    private var rawCategoryTracks: List<AuraTrack> = emptyList()
    private var rawCategoryVideos: List<AuraTrack> = emptyList()
    private var rawCategoryAlbums: List<AuraTrack> = emptyList()
    private var categoryJob: Job? = null

    private val _isCategoryLoading = MutableStateFlow(false)
    val isCategoryLoading: StateFlow<Boolean> = _isCategoryLoading.asStateFlow()

    private val _communityPlaylists = MutableStateFlow<List<YtmCommunityPlaylist>>(emptyList())
    val communityPlaylists: StateFlow<List<YtmCommunityPlaylist>> = _communityPlaylists.asStateFlow()

    private val _homeMusicVideos = MutableStateFlow<List<AuraTrack>>(emptyList())
    val homeMusicVideos: StateFlow<List<AuraTrack>> = _homeMusicVideos.asStateFlow()

    private val _selectedPlaylistDetail = MutableStateFlow<PlaylistDetail?>(null)
    val selectedPlaylistDetail: StateFlow<PlaylistDetail?> = _selectedPlaylistDetail.asStateFlow()

    private val _selectedCatalogDetail = MutableStateFlow<CatalogDetail?>(null)
    val selectedCatalogDetail: StateFlow<CatalogDetail?> = _selectedCatalogDetail.asStateFlow()

    private val _isCatalogLoading = MutableStateFlow(false)
    val isCatalogLoading: StateFlow<Boolean> = _isCatalogLoading.asStateFlow()

    private val catalogDetailCache = java.util.concurrent.ConcurrentHashMap<String, List<YtmMoodShelf>>()

    private val _activePlayingPlaylistDetail = MutableStateFlow<PlaylistDetail?>(null)
    val activePlayingPlaylistDetail: StateFlow<PlaylistDetail?> = _activePlayingPlaylistDetail.asStateFlow()

    private val _activePlayingUserPlaylist = MutableStateFlow<AuraPlaylist?>(null)
    val activePlayingUserPlaylist: StateFlow<AuraPlaylist?> = _activePlayingUserPlaylist.asStateFlow()

    private val _playlistScrollTargetTrackId = MutableStateFlow<String?>(null)
    val playlistScrollTargetTrackId: StateFlow<String?> = _playlistScrollTargetTrackId.asStateFlow()

    private val _isPlaylistLoading = MutableStateFlow(false)
    val isPlaylistLoading: StateFlow<Boolean> = _isPlaylistLoading.asStateFlow()

    private val _moodCategories = MutableStateFlow<List<MoodCategory>>(emptyList())
    val moodCategories: StateFlow<List<MoodCategory>> = _moodCategories.asStateFlow()

    private val _isLoadingHome = MutableStateFlow(true)
    val isLoadingHome: StateFlow<Boolean> = _isLoadingHome.asStateFlow()

    // Search & Spotify Importer
    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()

    private val _searchFilter = MutableStateFlow("all")
    val searchFilter: StateFlow<String> = _searchFilter.asStateFlow()

    private val _searchResults = MutableStateFlow<List<AuraTrack>>(SimpMusicExtractorEngine.DEFAULT_SEED_TRACKS)
    val searchResults: StateFlow<List<AuraTrack>> = _searchResults.asStateFlow()

    private val _searchExploreCoverTracks = MutableStateFlow<Map<String, List<AuraTrack>>>(emptyMap())
    val searchExploreCoverTracks: StateFlow<Map<String, List<AuraTrack>>> = _searchExploreCoverTracks.asStateFlow()
    private var searchExploreCoversLoaded = false
    private var searchExploreCoversJob: Job? = null

    private val _isSearching = MutableStateFlow(false)
    val isSearching: StateFlow<Boolean> = _isSearching.asStateFlow()

    private val _searchMatchedArtist = MutableStateFlow<YtmTopArtist?>(null)
    val searchMatchedArtist: StateFlow<YtmTopArtist?> = _searchMatchedArtist.asStateFlow()

    private val _searchMatchedArtists = MutableStateFlow<List<YtmTopArtist>>(emptyList())
    val searchMatchedArtists: StateFlow<List<YtmTopArtist>> = _searchMatchedArtists.asStateFlow()

    private val _artistFeaturingItems = MutableStateFlow<List<ArtistCarouselItem>>(emptyList())
    val artistFeaturingItems: StateFlow<List<ArtistCarouselItem>> = _artistFeaturingItems.asStateFlow()

    private val _searchPlaylistResults = MutableStateFlow<List<YtmCommunityPlaylist>>(emptyList())
    val searchPlaylistResults: StateFlow<List<YtmCommunityPlaylist>> = _searchPlaylistResults.asStateFlow()

    private val _spotifyImportStatus = MutableStateFlow<String?>(null)
    val spotifyImportStatus: StateFlow<String?> = _spotifyImportStatus.asStateFlow()

    private val _importedPlaylistSuccess = MutableStateFlow<AuraPlaylist?>(null)
    val importedPlaylistSuccess: StateFlow<AuraPlaylist?> = _importedPlaylistSuccess.asStateFlow()

    private val _selectedUserPlaylist = MutableStateFlow<AuraPlaylist?>(null)
    val selectedUserPlaylist: StateFlow<AuraPlaylist?> = _selectedUserPlaylist.asStateFlow()

    // Library (Favorites, Downloads, Playlists, Stats)
    private val _favorites = MutableStateFlow<List<AuraTrack>>(emptyList())
    val favorites: StateFlow<List<AuraTrack>> = _favorites.asStateFlow()

    private val _downloads = MutableStateFlow<List<AuraTrack>>(emptyList())
    val downloads: StateFlow<List<AuraTrack>> = _downloads.asStateFlow()

    private val _playlists = MutableStateFlow<List<AuraPlaylist>>(emptyList())
    val playlists: StateFlow<List<AuraPlaylist>> = _playlists.asStateFlow()

    private val _recentlyPlayed = MutableStateFlow<List<AuraTrack>>(emptyList())
    val recentlyPlayed: StateFlow<List<AuraTrack>> = _recentlyPlayed.asStateFlow()

    private val _listeningStats = MutableStateFlow(ListeningStats())
    val listeningStats: StateFlow<ListeningStats> = _listeningStats.asStateFlow()

    private val _downloadingIds = MutableStateFlow<Set<String>>(emptySet())
    val downloadingIds: StateFlow<Set<String>> = _downloadingIds.asStateFlow()

    private val _downloadProgress = MutableStateFlow<Map<String, Float>>(emptyMap())
    val downloadProgress: StateFlow<Map<String, Float>> = _downloadProgress.asStateFlow()

    // Now Playing Enrichments (Palette, Lyrics, RYD, Style)
    private val _palette = MutableStateFlow(AuraPalette())
    val palette: StateFlow<AuraPalette> = _palette.asStateFlow()

    private val _lyrics = MutableStateFlow<LyricsData?>(null)
    val lyrics: StateFlow<LyricsData?> = _lyrics.asStateFlow()

    private val _lyricsOffsetMs = MutableStateFlow(0)
    val lyricsOffsetMs: StateFlow<Int> = _lyricsOffsetMs.asStateFlow()

    private val _showRomanizedLyrics = MutableStateFlow(true)
    val showRomanizedLyrics: StateFlow<Boolean> = _showRomanizedLyrics.asStateFlow()

    private val _romanizationLanguages = MutableStateFlow(storage.loadRomanizationLanguages())
    val romanizationLanguages: StateFlow<Set<String>> = _romanizationLanguages.asStateFlow()

    private val _rydData = MutableStateFlow<ReturnYouTubeDislikeData?>(null)
    val rydData: StateFlow<ReturnYouTubeDislikeData?> = _rydData.asStateFlow()

    private val _nowPlayingStyle = MutableStateFlow(NowPlayingStyle.SPOTIFY)
    val nowPlayingStyle: StateFlow<NowPlayingStyle> = _nowPlayingStyle.asStateFlow()

    val isVideoMode: StateFlow<Boolean> = AuraPlayerManager.isVideoMode
    val isVideoLoading: StateFlow<Boolean> = AuraPlayerManager.isVideoLoading

    private val _livePerformances = MutableStateFlow<List<AuraTrack>>(emptyList())
    val livePerformances: StateFlow<List<AuraTrack>> = _livePerformances.asStateFlow()

    private val _isLoadingLivePerformances = MutableStateFlow(false)
    val isLoadingLivePerformances: StateFlow<Boolean> = _isLoadingLivePerformances.asStateFlow()

    // In-App Updates & Releases
    private val _currentUpdateRelease = MutableStateFlow<AuraReleaseInfo?>(null)
    val currentUpdateRelease: StateFlow<AuraReleaseInfo?> = _currentUpdateRelease.asStateFlow()

    private val _isUpdateModalVisible = MutableStateFlow(false)
    val isUpdateModalVisible: StateFlow<Boolean> = _isUpdateModalVisible.asStateFlow()

    private val _updateState = MutableStateFlow(UpdateState.IDLE)
    val updateState: StateFlow<UpdateState> = _updateState.asStateFlow()

    private val _updateDownloadProgress = MutableStateFlow(UpdateDownloadProgress())
    val updateDownloadProgress: StateFlow<UpdateDownloadProgress> = _updateDownloadProgress.asStateFlow()

    private val _updateErrorMessage = MutableStateFlow<String?>(null)
    val updateErrorMessage: StateFlow<String?> = _updateErrorMessage.asStateFlow()

    private var downloadedApkFile: File? = null

    private var searchJob: Job? = null

    init {
        AuraPlayerManager.initialize(application)
        SimpMusicExtractorEngine.clearStreamCache()
        _favorites.value = storage.loadFavorites()
        _downloads.value = storage.loadDownloads()
        _playlists.value = storage.loadPlaylists()
        _recentlyPlayed.value = storage.loadRecentlyPlayed()
        _listeningStats.value = storage.loadListeningStats()
        _nowPlayingStyle.value = storage.loadNowPlayingStyle()
        _visibleRecentlyPlayed.value = _recentlyPlayed.value
        _visibleUserPlaylists.value = _playlists.value

        // Automatically upgrade any previously imported Spotify playlists that were capped at 100 tracks
        // or had the playlist cover art shared across all tracks.
        upgradeLegacySpotifyPlaylistsIfNeeded()

        // Silent startup check against GitHub Releases
        checkForUpdates(force = false)

        AuraPlayerManager.onTrackFinishedCallback = { finishedTrack ->
            storage.recordRecommendationSignal(finishedTrack, "complete")
            applyLocalRecommendations()
        }
        AuraPlayerManager.onTrackSkippedCallback = { skippedTrack ->
            storage.recordRecommendationSignal(skippedTrack, "skip")
            applyLocalRecommendations()
        }
        AuraPlayerManager.onVideoUnavailableCallback = { track ->
            Toast.makeText(
                getApplication(),
                "No music video found for ${track.title}; continuing with audio",
                Toast.LENGTH_SHORT
            ).show()
        }
        AuraPlayerManager.onTrackResolvedCallback = { resolvedTrack ->
            viewModelScope.launch {
                var changed = false
                val updatedPlaylists = _playlists.value.map { pl ->
                    val trackIdx = pl.tracks.indexOfFirst {
                        it.id == resolvedTrack.id || (it.title.equals(resolvedTrack.title, ignoreCase = true) && it.artist.equals(resolvedTrack.artist, ignoreCase = true))
                    }
                    if (trackIdx >= 0) {
                        changed = true
                        val newTracks = pl.tracks.toMutableList()
                        newTracks[trackIdx] = resolvedTrack
                        pl.copy(tracks = newTracks)
                    } else {
                        pl
                    }
                }
                if (changed) {
                    _playlists.value = updatedPlaylists
                    storage.savePlaylists(updatedPlaylists)
                }
            }
        }

        // Observe current track changes to fetch Palette, SimpMusic RichSync Lyrics, SponsorBlock, and RYD in parallel
        viewModelScope.launch {
            AuraPlayerManager.currentTrack.distinctUntilChangedBy { it?.videoId }.collectLatest { track ->
                if (track == null) return@collectLatest
                // Avoid showing the previous song's artwork color while this track's palette loads.
                _palette.value = AuraPalette(
                    primary = Color(0xFF211F26),
                    secondary = Color(0xFF333038),
                    backgroundTop = Color(0xFF1B1920),
                    backgroundBottom = Color(0xFF0A0B0E)
                )
                _listeningStats.value = storage.recordTrackPlay(track)
                storage.recordRecommendationSignal(track, "play")
                val updatedRecent = storage.loadRecentlyPlayed()
                _recentlyPlayed.value = updatedRecent
                if (_activeCategory.value == "All") {
                    _visibleRecentlyPlayed.value = updatedRecent
                }
                applyLocalRecommendations()
                val canonicalTrack = AuraPlayerManager.originalAudioTrack ?: track
                val canonicalVid = canonicalTrack.videoId
                val savedOffset = storage.getLyricsOffsetMs(canonicalVid).takeIf { it != 0 }
                    ?: storage.getLyricsOffsetMs(track.videoId)
                _lyricsOffsetMs.value = savedOffset
                _nowPlayingVideoInfo.value = null

                val paletteDef = async { extractAuraPaletteFromUrl(track.highResArtworkUrl) }
                val videoInfoDef = async { SimpMusicExtractorEngine.fetchVideoInfo(track.videoId) }

                // Check if lyrics are already loaded for this song so switching between Audio and Video mode
                // retains the exact same verified, synced lyrics without re-querying raw video metadata.
                val isSameSongLyrics = _lyrics.value != null && (
                    _lyrics.value?.videoId == canonicalVid ||
                    _lyrics.value?.videoId == track.videoId ||
                    (AuraPlayerManager.isVideoMode.value && AuraPlayerManager.originalAudioTrack != null && _lyrics.value?.syncedLines?.isNotEmpty() == true)
                )

                val lyricsDef = if (isSameSongLyrics) {
                    null
                } else {
                    async {
                        val cleanTitle = SimpMusicExtractorEngine.cleanSongTitleForVideoSearch(canonicalTrack.title, canonicalTrack.artist)
                        val cleanArtist = SimpMusicExtractorEngine.primaryArtistName(canonicalTrack.artist)
                        SimpMusicLyricsRepository.getLyrics(
                            videoId = canonicalVid,
                            title = cleanTitle.ifBlank { canonicalTrack.title },
                            artist = cleanArtist.ifBlank { canonicalTrack.artist },
                            durationSeconds = canonicalTrack.durationSeconds
                        )
                    }
                }
                val sponsorDef = async { SponsorBlockRepository.getSponsorSegments(track.videoId) }
                val rydDef = async { SponsorBlockRepository.getReturnYouTubeDislike(track.videoId) }

                _palette.value = paletteDef.await()
                if (lyricsDef != null) {
                    _lyrics.value = lyricsDef.await()
                }
                AuraPlayerManager.setSponsorSegments(sponsorDef.await())
                val videoInfo = videoInfoDef.await()
                val ryd = rydDef.await()
                if (AuraPlayerManager.currentTrack.value?.videoId == track.videoId) {
                    _nowPlayingVideoInfo.value = videoInfo?.copy(
                        viewCount = ryd?.viewCount?.takeIf { it > 0L } ?: track.viewCount ?: 0L,
                        likes = ryd?.likes ?: 0L,
                        dislikes = ryd?.dislikes ?: 0L
                    )
                    _rydData.value = ryd
                }
            }
        }

        refreshHome()
        // Pre-warm the default seed track so first tap is instant
        SimpMusicExtractorEngine.prefetchStream(SimpMusicExtractorEngine.DEFAULT_SEED_TRACKS.first().videoId)
    }

    fun refreshHome() {
        viewModelScope.launch(Dispatchers.IO) {
            val currentCategory = _activeCategory.value
            if (currentCategory != "All") {
                _isCategoryLoading.value = true
                try {
                    val moodResult = SimpMusicExtractorEngine.fetchYtmMoodBrowse(currentCategory)
                    if (!moodResult.backgroundUrl.isNullOrBlank()) {
                        _homeCategoryBackground.value = moodResult.backgroundUrl
                    }
                    _categoryShelves.value = moodResult.shelves
                    val videosDeferred = async {
                        runCatching {
                            SimpMusicExtractorEngine.searchYouTubeMusic("$currentCategory official music videos", "videos").take(20)
                        }.getOrDefault(emptyList())
                    }
                    rawCategoryTracks = moodResult.tracks
                    rawCategoryAlbums = moodResult.albums
                    rawCategoryVideos = videosDeferred.await()
                    applyLocalRecommendations()
                } catch (e: Exception) {
                    Log.e("AuraViewModel", "Category refresh failed for $currentCategory: ${e.message}")
                } finally {
                    _isCategoryLoading.value = false
                }
            }

            _isLoadingHome.value = true
            try {
                val artistQuery = preferredArtistQuery()
                val savedCountry = storage.loadChartCountry()
                _selectedChartCountry.value = savedCountry

                val chartsDeferred = async { SimpMusicExtractorEngine.fetchHomeChartsAndMoods() }
                val countryChartDeferred = async { SimpMusicExtractorEngine.fetchHomeChartData(savedCountry) }
                val playlistsDeferred = async { SimpMusicExtractorEngine.fetchCommunityPlaylists() }
                val videosDeferred = async {
                    SimpMusicExtractorEngine.searchYouTubeMusic(
                        if (artistQuery.isBlank()) "trending popular official music videos"
                        else "$artistQuery official music videos",
                        filter = "videos"
                    ).take(16)
                }
                val playedArtists = (_recentlyPlayed.value + _favorites.value)
                    .flatMap { track ->
                        track.artist.split(Regex("\\s*(?:,|;|\\bfeat\\.?\\b|\\bft\\.?\\b)\\s*"))
                            .map(String::trim)
                            .filter { it.isNotBlank() && !it.equals("YouTube Artist", true) && it.length > 1 }
                    }
                    .groupingBy { it }
                    .eachCount()
                    .entries
                    .sortedByDescending { it.value }
                    .map { it.key }
                    .take(4)

                val albumsDeferred = async {
                    if (playedArtists.isNotEmpty()) {
                        coroutineScope {
                            val perArtist = playedArtists.map { artist ->
                                async {
                                    runCatching {
                                        SimpMusicExtractorEngine.searchYouTubeMusic(artist, filter = "albums").take(4)
                                    }.getOrDefault(emptyList())
                                }
                            }.awaitAll().flatten()

                            val fallbackGeneral = if (perArtist.size < 6) {
                                runCatching {
                                    SimpMusicExtractorEngine.searchYouTubeMusic("popular new albums", filter = "albums").take(10)
                                }.getOrDefault(emptyList())
                            } else emptyList()

                            (perArtist + fallbackGeneral)
                                .distinctBy { "${it.artist.lowercase()}_${it.title.lowercase()}" }
                        }
                    } else {
                        SimpMusicExtractorEngine.searchYouTubeMusic("popular new albums", filter = "albums").take(16)
                    }
                }

                val (sections, moods) = chartsDeferred.await()
                _chartSections.value = sections
                _moodCategories.value = moods
                val countryChart = countryChartDeferred.await()
                _chartData.value = countryChart
                val artistsList = if (countryChart.topArtists.isNotEmpty()) {
                    countryChart.topArtists.map {
                        YtmTopArtist(id = it.id, name = it.name, subscribers = it.subscribers, avatarUrl = it.avatarUrl)
                    }
                } else {
                    SimpMusicExtractorEngine.fetchTopArtists()
                }
                _topArtists.value = artistsList
                _visibleTopArtists.value = artistsList
                _communityPlaylists.value = playlistsDeferred.await()
                rawHomeTracks = (_recentlyPlayed.value + _favorites.value + sections.flatMap { it.tracks } +
                    SimpMusicExtractorEngine.DEFAULT_SEED_TRACKS).distinctBy { LocalRecommendationEngine.songIdentityKey(it) }
                rawHomeVideos = videosDeferred.await()
                rawHomeAlbums = albumsDeferred.await()
                applyLocalRecommendations()

                val topTracks = sections.firstOrNull()?.tracks ?: emptyList()
                if (_searchQuery.value.isBlank() && topTracks.isNotEmpty()) {
                    _searchResults.value = topTracks
                }

                topTracks.firstOrNull()?.let {
                    SimpMusicExtractorEngine.prefetchStream(it.videoId)
                }
            } catch (e: Exception) {
                Log.e("AuraViewModel", "Home refresh error: ${e.message}")
            } finally {
                _isLoadingHome.value = false
            }
        }
    }

    fun selectChartCountry(countryCode: String) {
        if (_selectedChartCountry.value == countryCode && _chartData.value != null && !_isChartLoading.value) return
        _selectedChartCountry.value = countryCode
        storage.saveChartCountry(countryCode)
        viewModelScope.launch(Dispatchers.IO) {
            _isChartLoading.value = true
            try {
                val chart = SimpMusicExtractorEngine.fetchHomeChartData(countryCode)
                _chartData.value = chart
                if (chart.topArtists.isNotEmpty()) {
                    val artistsList = chart.topArtists.map {
                        YtmTopArtist(id = it.id, name = it.name, subscribers = it.subscribers, avatarUrl = it.avatarUrl)
                    }
                    _topArtists.value = artistsList
                    _visibleTopArtists.value = artistsList
                }
            } catch (e: Exception) {
                Log.w("AuraViewModel", "Failed to fetch charts for $countryCode: ${e.message}")
            } finally {
                _isChartLoading.value = false
            }
        }
    }

    fun playCommunityPlaylist(playlist: YtmCommunityPlaylist) {
        viewModelScope.launch {
            _isPlaylistLoading.value = true
            val fullDetail = SimpMusicExtractorEngine.fetchPlaylistDetail(playlist)
            _isPlaylistLoading.value = false
            _activePlayingPlaylistDetail.value = fullDetail
            _activePlayingUserPlaylist.value = null
            val taggedTracks = fullDetail.tracks.map { it.track.copy(album = fullDetail.title) }
            val tracks = if (taggedTracks.isNotEmpty()) taggedTracks else SimpMusicExtractorEngine.fetchPlaylistTracks(playlist.id).map { it.copy(album = playlist.title) }
            if (tracks.isNotEmpty()) {
                playTrack(tracks.first(), tracks)
            }
        }
    }

    fun playHomeMusicVideo(track: AuraTrack, videoQueue: List<AuraTrack>) {
        AuraPlayerManager.playStandaloneVideo(track, videoQueue)
    }

    fun openArtistDetail(artist: YtmTopArtist) {
        viewModelScope.launch {
            _isArtistLoading.value = true
            // Immediately set a preliminary state so transition is instant
            _selectedArtistDetail.value = ArtistDetail(
                id = artist.id,
                name = artist.name,
                subscribers = artist.subscribers,
                bannerUrl = artist.avatarUrl,
                topSongs = emptyList(),
                albums = emptyList()
            )
            val fullDetail = SimpMusicExtractorEngine.fetchArtistDetail(artist)
            _selectedArtistDetail.value = fullDetail
            _isArtistLoading.value = false
            loadArtistEditorial(fullDetail.name)
        }
    }

    fun openArtistByName(artistName: String, songTitle: String = "", fallbackArtwork: String = "") {
        if (artistName.isBlank()) return
        val existingInTop = _topArtists.value.firstOrNull { it.name == artistName }
        if (existingInTop != null && songTitle.isBlank()) {
            openArtistDetail(existingInTop)
            return
        }
        viewModelScope.launch {
            _isArtistLoading.value = true
            _selectedArtistDetail.value = ArtistDetail(
                id = "",
                name = artistName,
                subscribers = "",
                bannerUrl = fallbackArtwork,
                topSongs = emptyList(),
                albums = emptyList()
            )
            val fullDetail = SimpMusicExtractorEngine.fetchArtistByName(artistName, songTitle, fallbackArtwork)
            _selectedArtistDetail.value = fullDetail
            _isArtistLoading.value = false
            loadArtistEditorial(fullDetail.name)
        }
    }

    fun loadNowPlayingArtist(artistName: String, songTitle: String, fallbackArtwork: String) {
        val cleanArtist = artistName
            .split(Regex("(?i)\\s+(?:feat\\.?|ft\\.?)\\s+|\\s*&\\s*|,"), limit = 2)
            .firstOrNull()
            .orEmpty()
            .trim()
        val key = cleanArtist.lowercase()
        if (key.isBlank() || nowPlayingArtistKey == key && (_nowPlayingArtistDetail.value != null || _isNowPlayingArtistLoading.value)) return

        nowPlayingArtistJob?.cancel()
        nowPlayingArtistKey = key
        _nowPlayingArtistDetail.value = null
        _isNowPlayingArtistLoading.value = true
        nowPlayingArtistJob = viewModelScope.launch {
            val detail = runCatching {
                SimpMusicExtractorEngine.fetchArtistByName(cleanArtist, songTitle, fallbackArtwork)
            }.getOrNull()
            if (nowPlayingArtistKey == key) {
                _nowPlayingArtistDetail.value = detail
                _isNowPlayingArtistLoading.value = false
            }
        }
    }

    private fun loadArtistEditorial(artistName: String) {
        viewModelScope.launch {
            val selectedId = _selectedArtistDetail.value?.id
            val current = _selectedArtistDetail.value ?: return@launch
            if (current.name != artistName) return@launch
            _selectedArtistDetail.value = current.copy(motionLoading = true)
            val motion = ArtistEditorialRepository.fetch(getApplication(), artistName)
            val latest = _selectedArtistDetail.value
            if (latest?.name == artistName && latest.id == selectedId) {
                _selectedArtistDetail.value = latest.copy(motion = motion, motionLoading = false)
            }
        }
    }

    fun loadPlayerComments(videoId: String, force: Boolean = false) {
        if (videoId.isBlank() || (_areCommentsLoading.value && commentsVideoId == videoId)) return
        if (!force && commentsVideoId == videoId && _playerComments.value != null) return
        commentsVideoId = videoId
        _playerComments.value = null
        _commentsError.value = null
        _areCommentsLoading.value = true
        viewModelScope.launch {
            runCatching { YouTubeCommentsRepository.firstPage(videoId) }
                .onSuccess { page -> if (commentsVideoId == videoId) _playerComments.value = page }
                .onFailure { error -> if (commentsVideoId == videoId) _commentsError.value = error.message ?: "Could not load comments" }
            if (commentsVideoId == videoId) _areCommentsLoading.value = false
        }
    }

    fun loadMorePlayerComments() {
        val page = _playerComments.value ?: return
        val continuation = page.continuation ?: return
        if (_areCommentsLoading.value) return
        val videoId = commentsVideoId ?: return
        _areCommentsLoading.value = true
        viewModelScope.launch {
            runCatching { YouTubeCommentsRepository.nextPage(continuation) }
                .onSuccess { next ->
                    if (commentsVideoId == videoId) {
                        val currentPage = _playerComments.value ?: page
                        _playerComments.value = currentPage.copy(
                            comments = (currentPage.comments + next.comments).distinctBy { it.id },
                            continuation = next.continuation
                        )
                    }
                }
                .onFailure { error -> if (commentsVideoId == videoId) _commentsError.value = error.message ?: "Could not load more comments" }
            if (commentsVideoId == videoId) _areCommentsLoading.value = false
        }
    }

    fun clearPlayerComments() {
        commentsVideoId = null
        _playerComments.value = null
        _commentsError.value = null
        _areCommentsLoading.value = false
    }

    fun closeArtistDetail() {
        _selectedArtistDetail.value = null
    }

    fun selectUserPlaylist(playlist: AuraPlaylist?) {
        _selectedUserPlaylist.value = playlist
    }

    fun clearSelectedUserPlaylist() {
        _selectedUserPlaylist.value = null
    }

    fun dismissImportSuccessModal() {
        _importedPlaylistSuccess.value = null
    }

    fun openPlaylistDetail(playlist: YtmCommunityPlaylist) {
        viewModelScope.launch {
            _isPlaylistLoading.value = true
            _selectedPlaylistDetail.value = PlaylistDetail(
                id = playlist.id,
                title = playlist.title,
                author = playlist.author,
                trackCount = playlist.trackCount,
                bannerUrl = playlist.artworkUrl,
                tracks = emptyList(),
                isPodcast = playlist.id.startsWith("MPSP") || playlist.id.startsWith("MP_") || playlist.id.contains("podcast", ignoreCase = true)
            )
            val fullDetail = SimpMusicExtractorEngine.fetchPlaylistDetail(playlist)
            _selectedPlaylistDetail.value = fullDetail
            _isPlaylistLoading.value = false
        }
    }

    fun openAlbumOrPlaylist(id: String, title: String, author: String, artworkUrl: String) {
        openPlaylistDetail(
            YtmCommunityPlaylist(
                id = id,
                title = title,
                author = author,
                trackCount = "",
                artworkUrl = artworkUrl
            )
        )
    }

    fun openAlbum(track: AuraTrack) {
        viewModelScope.launch {
            var cleanVid = track.videoId.trim()
            while (cleanVid.startsWith("album_") || cleanVid.startsWith("yt_album_") || cleanVid.startsWith("pl_") || cleanVid.startsWith("track_") || cleanVid.startsWith("yt_")) {
                cleanVid = cleanVid.removePrefix("album_")
                    .removePrefix("yt_album_")
                    .removePrefix("pl_")
                    .removePrefix("track_")
                    .removePrefix("yt_")
                    .trim()
            }
            val albumName = track.album.takeIf { it.isNotBlank() && !it.equals("YouTube Music", true) } ?: track.title
            val targetId = if (cleanVid.startsWith("MPRE") || cleanVid.startsWith("OLAK5uy_") || cleanVid.startsWith("VL")) {
                cleanVid
            } else {
                SimpMusicExtractorEngine.findAlbumBrowseId(albumName, track.artist) ?: cleanVid
            }
            openAlbumOrPlaylist(
                id = targetId,
                title = albumName,
                author = track.artist,
                artworkUrl = track.highResArtworkUrl
            )
        }
    }

    fun closePlaylistDetail() {
        _selectedPlaylistDetail.value = null
    }

    fun openCatalogCategory(
        title: String,
        params: String = "",
        browseId: String = "FEmusic_moods_and_genres_category"
    ) {
        val cacheKey = "${browseId}_${params}_${title}"
        val cached = catalogDetailCache[cacheKey]
        if (cached != null && cached.isNotEmpty()) {
            _selectedCatalogDetail.value = CatalogDetail(
                title = title,
                params = params,
                browseId = browseId,
                shelves = cached,
                isLoading = false
            )
            return
        }

        _selectedCatalogDetail.value = CatalogDetail(
            title = title,
            params = params,
            browseId = browseId,
            shelves = emptyList(),
            isLoading = true
        )
        _isCatalogLoading.value = true

        viewModelScope.launch {
            try {
                val shelves = SimpMusicExtractorEngine.fetchMoodCategoryDetails(
                    browseId = browseId,
                    params = params.ifBlank { null },
                    title = title
                )
                if (shelves.isNotEmpty()) {
                    catalogDetailCache[cacheKey] = shelves
                }
                _selectedCatalogDetail.value = CatalogDetail(
                    title = title,
                    params = params,
                    browseId = browseId,
                    shelves = shelves,
                    isLoading = false
                )
            } catch (e: Exception) {
                android.util.Log.e("AuraViewModel", "Error fetching catalog category $title: ${e.message}")
                _selectedCatalogDetail.value = CatalogDetail(
                    title = title,
                    params = params,
                    browseId = browseId,
                    shelves = emptyList(),
                    isLoading = false,
                    error = e.message ?: "Failed to load"
                )
            } finally {
                _isCatalogLoading.value = false
            }
        }
    }

    fun closeCatalogDetail() {
        _selectedCatalogDetail.value = null
    }

    fun setActivePlaylistDetail(detail: PlaylistDetail) {
        _activePlayingPlaylistDetail.value = detail
        _activePlayingUserPlaylist.value = null
    }

    fun setActiveUserPlaylist(playlist: AuraPlaylist) {
        _activePlayingUserPlaylist.value = playlist
        _activePlayingPlaylistDetail.value = null
    }

    fun clearPlaylistScrollTarget() {
        _playlistScrollTargetTrackId.value = null
    }

    fun openCurrentPlaylistScreen(): Boolean {
        val track = AuraPlayerManager.currentTrack.value ?: return false
        val targetTrackId = track.videoId.ifBlank { track.id }
        _playlistScrollTargetTrackId.value = targetTrackId

        // 1. Check active Community / YTM PlaylistDetail
        val activeCommunity = _activePlayingPlaylistDetail.value
        if (activeCommunity != null &&
            (activeCommunity.title.equals(track.album, ignoreCase = true) ||
             activeCommunity.tracks.any { it.track.videoId == track.videoId || it.track.id == track.id })) {
            _selectedPlaylistDetail.value = activeCommunity
            return true
        }

        // 2. Check active UserPlaylist
        val activeUser = _activePlayingUserPlaylist.value
        if (activeUser != null &&
            (activeUser.name.equals(track.album, ignoreCase = true) ||
             activeUser.tracks.any { it.videoId == track.videoId || it.id == track.id })) {
            _selectedUserPlaylist.value = activeUser
            return true
        }

        // 3. Search saved user playlists
        val matchingUserPl = _playlists.value.firstOrNull { pl ->
            pl.name.equals(track.album, ignoreCase = true) ||
            pl.tracks.any { it.videoId == track.videoId || (track.id.isNotBlank() && it.id == track.id) }
        }
        if (matchingUserPl != null) {
            _activePlayingUserPlaylist.value = matchingUserPl
            _selectedUserPlaylist.value = matchingUserPl
            return true
        }

        // 4. Check currently selected PlaylistDetail if available
        val currentDetail = _selectedPlaylistDetail.value
        if (currentDetail != null &&
            (currentDetail.title.equals(track.album, ignoreCase = true) ||
             currentDetail.tracks.any { it.track.videoId == track.videoId || it.track.id == track.id })) {
            _activePlayingPlaylistDetail.value = currentDetail
            return true
        }

        // 5. If album represents a playlist/album name, load it
        val cleanAlbum = track.album.trim()
        if (cleanAlbum.isNotBlank() &&
            !cleanAlbum.equals("YouTube Music", ignoreCase = true) &&
            !cleanAlbum.matches(Regex("^\\d+:\\d{2}(:\\d{2})?$")) &&
            !cleanAlbum.endsWith("views", ignoreCase = true)
        ) {
            val matchingCommunity = _communityPlaylists.value.firstOrNull {
                it.title.equals(cleanAlbum, ignoreCase = true) ||
                it.title.contains(cleanAlbum, ignoreCase = true) ||
                cleanAlbum.contains(it.title, ignoreCase = true)
            }
            if (matchingCommunity != null) {
                openPlaylistDetail(matchingCommunity)
                return true
            }

            openAlbum(track)
            return true
        }

        return false
    }

    fun playArtist(artist: YtmTopArtist) {
        viewModelScope.launch {
            val tracks = SimpMusicExtractorEngine.searchYouTubeMusic("${artist.name} official songs", "songs")
            if (tracks.isNotEmpty()) {
                playTrack(tracks.first(), tracks)
            }
        }
    }

    fun selectCategory(category: String) {
        if (_activeCategory.value == category) {
            // Tapping currently selected category toggles it off back to "All"
            _activeCategory.value = "All"
            _homeCategoryBackground.value = null
            categoryJob?.cancel()
            rawCategoryTracks = emptyList()
            rawCategoryVideos = emptyList()
            rawCategoryAlbums = emptyList()
            _categoryTracks.value = emptyList()
            _categoryShelves.value = emptyList()
            _isCategoryLoading.value = false
            applyLocalRecommendations()
            return
        }

        _activeCategory.value = category
        categoryJob?.cancel()

        if (category == "All") {
            _homeCategoryBackground.value = null
            rawCategoryTracks = emptyList()
            rawCategoryVideos = emptyList()
            rawCategoryAlbums = emptyList()
            _categoryTracks.value = emptyList()
            _categoryShelves.value = emptyList()
            _isCategoryLoading.value = false
            applyLocalRecommendations()
            return
        }

        // Keep background dark (null) while skeleton UI is rendering; reveal simultaneously when content is ready
        _homeCategoryBackground.value = null
        rawCategoryTracks = emptyList()
        rawCategoryVideos = emptyList()
        rawCategoryAlbums = emptyList()
        _categoryTracks.value = emptyList()
        _categoryShelves.value = emptyList()
        _homeMusicVideos.value = emptyList()
        _homeAlbumRecommendations.value = emptyList()
        _visibleTopArtists.value = emptyList()
        _visibleRecentlyPlayed.value = emptyList()
        _visibleUserPlaylists.value = emptyList()
        _isCategoryLoading.value = true

        categoryJob = viewModelScope.launch(Dispatchers.IO) {
            try {
                // 1. Directly call YouTube Music InnerTube browse endpoint (FEmusic_home with params)
                val moodDeferred = async { SimpMusicExtractorEngine.fetchYtmMoodBrowse(category) }

                // 2. Fetch category-tailored official music videos in parallel
                val videosDeferred = async {
                    runCatching {
                        SimpMusicExtractorEngine.searchYouTubeMusic("$category official music videos", "videos").take(20)
                    }.getOrDefault(emptyList())
                }

                // 3. Fetch popular tracks for this category so the bottom tracks section has rich, distinct songs
                val categoryTracksDeferred = async {
                    runCatching {
                        SimpMusicExtractorEngine.searchYouTubeMusic("$category hits songs", "songs").take(30)
                    }.getOrDefault(emptyList())
                }

                val moodResult = moodDeferred.await()
                val popularSongs = categoryTracksDeferred.await()
                val shelfTrackIds = moodResult.tracks.map { it.videoId }.toSet()

                // Filter out any songs that already appear in the top Quick picks / carousel shelves
                val distinctCategoryTracks = popularSongs.filterNot { shelfTrackIds.contains(it.videoId) }
                rawCategoryTracks = if (distinctCategoryTracks.isNotEmpty()) distinctCategoryTracks else moodResult.tracks
                rawCategoryAlbums = moodResult.albums
                rawCategoryVideos = videosDeferred.await()
                _categoryShelves.value = moodResult.shelves

                applyLocalRecommendations()
                _homeCategoryBackground.value = YtmMoodBrowseParams.DEFAULT_BACKGROUNDS[category] ?: moodResult.backgroundUrl
            } catch (e: Exception) {
                Log.e("AuraViewModel", "Category browse failed for $category: ${e.message}")
                _homeCategoryBackground.value = YtmMoodBrowseParams.DEFAULT_BACKGROUNDS[category]
            } finally {
                _isCategoryLoading.value = false
            }
        }
    }

    private fun preferredArtistQuery(): String {
        return (_favorites.value + _recentlyPlayed.value)
            .flatMap { track ->
                track.artist.split(Regex("\\s*(?:,|;|\\bfeat\\.?\\b|\\bft\\.?\\b)\\s*"))
                    .map(String::trim)
                    .filter { it.isNotBlank() && !it.equals("YouTube Artist", true) }
            }
            .groupingBy { it.lowercase() }
            .eachCount()
            .entries
            .sortedByDescending { it.value }
            .take(2)
            .map { it.key }
            .joinToString(" ")
    }

    private fun albumCandidatesFromTracks(tracks: List<AuraTrack>): List<AuraTrack> = tracks
        .filter { it.album.isNotBlank() && !it.album.equals("YouTube Music", ignoreCase = true) }
        .distinctBy { "${it.artist.lowercase()}_${it.album.lowercase()}" }

    fun loadSearchExploreCoverTracks() {
        if (searchExploreCoversLoaded || searchExploreCoversJob?.isActive == true) return

        // 1. Check persistent disk cache first so it loads only once and never hits network repeatedly
        val cachedCovers = storage.loadExploreCoverCache()
        if (cachedCovers.isNotEmpty()) {
            _searchExploreCoverTracks.value = cachedCovers.mapValues { (catTitle, urls) ->
                urls.mapIndexed { idx, url ->
                    AuraTrack(
                        id = "cache_${catTitle}_$idx",
                        videoId = "cache_${catTitle}_$idx",
                        title = catTitle,
                        artist = catTitle,
                        artworkUrl = url
                    )
                }
            }
            searchExploreCoversLoaded = true
            return
        }

        searchExploreCoversJob = viewModelScope.launch {
            val artistQuery = preferredArtistQuery()
            val categoryQueries = linkedMapOf(
                // Top Browse
                "New Releases" to ("new music albums singles official" to "songs"),
                "Charts" to ("top global music charts hit songs" to "songs"),
                "Pop" to ("popular pop hits official audio" to "songs"),
                "Hip-Hop" to ("popular hip hop hits official audio" to "songs"),
                // Moods & Moments
                "Chill" to ("chill relax lo fi beats music" to "songs"),
                "Workout" to ("workout fitness gym pump songs" to "songs"),
                "Party" to ("party dance club hits songs" to "songs"),
                "Focus" to ("deep focus study instrumental music" to "songs"),
                "Feel Good" to ("feel good happy upbeat songs" to "songs"),
                "Romance" to ("romantic love songs acoustic" to "songs"),
                "Energize" to ("energizing electronic pop songs" to "songs"),
                "Sleep" to ("peaceful sleep calming night music" to "songs"),
                "Commute" to ("road trip driving commute playlist" to "songs"),
                "Gaming" to ("gaming music edm synthwave bass" to "songs"),
                "Sad" to ("sad emotional acoustic songs" to "songs"),
                // Genres
                "Rock" to ("rock classics rock anthems official audio" to "songs"),
                "R&B & Soul" to ("r&b soul hits official audio" to "songs"),
                "Dance & Electronic" to ("dance electronic edm club music" to "songs"),
                "Indie & Alternative" to ("indie alternative rock songs" to "songs"),
                "Country & Americana" to ("country music hits americana" to "songs"),
                "Latin" to ("latin hits reggaeton pop latino" to "songs"),
                "K-Pop" to ("popular k-pop hits" to "songs"),
                "J-Pop & Anime" to ("popular anime songs j-pop" to "songs"),
                "Classical" to ("classical music masterpieces piano orchestra" to "songs"),
                "Jazz" to ("smooth jazz cafe instrumental" to "songs"),
                "Metal" to ("heavy metal rock anthems" to "songs"),
                "Decades" to ("throwback classics 80s 90s 2000s hits" to "songs"),
                "Folk & Acoustic" to ("folk acoustic guitar singer songwriter" to "songs"),
                "Soundtracks" to ("epic movie soundtrack scores" to "songs"),
                "Reggae & Caribbean" to ("reggae dancehall caribbean vibes" to "songs"),
                "Blues" to ("blues classics electric blues guitar" to "songs"),
                "Bollywood & Indian" to ("bollywood hits hindi songs" to "songs"),
                "African" to ("afrobeats amapiano african hits" to "songs"),
                "Arabic" to ("arabic pop traditional hits" to "songs"),
                "Christian & Gospel" to ("christian worship gospel music" to "songs"),
                "Mandopop & Cantopop" to ("mandopop cantopop chinese hits" to "songs"),
                "OPM" to ("opm tagalog love songs acoustic" to "songs"),
                "Indonesian" to ("indonesian pop hits lagu pop" to "songs"),
                "Family" to ("kids children family fun songs" to "songs"),
                "Seasonal" to ("holiday seasonal celebration music" to "songs"),
                "Thai" to ("thai pop hits music" to "songs")
            )
            val fetchedByCategory = mutableMapOf<String, List<AuraTrack>>()

            fun publishArtworkFanPools() {
                val fallbackArtworkPool = (
                    fetchedByCategory.values.flatten() + _homeRecommendations.value + _favorites.value +
                        _recentlyPlayed.value + SimpMusicExtractorEngine.DEFAULT_SEED_TRACKS
                ).distinctBy { it.videoId }

                val mapped = categoryQueries.keys.associateWith { title ->
                    val specific = fetchedByCategory[title].orEmpty()
                    if (specific.size >= 3) {
                        specific.take(3)
                    } else {
                        val offset = Math.abs(title.hashCode()) % fallbackArtworkPool.size.coerceAtLeast(1)
                        val rotatedPool = if (fallbackArtworkPool.isNotEmpty()) {
                            fallbackArtworkPool.drop(offset) + fallbackArtworkPool.take(offset)
                        } else emptyList()
                        (specific + rotatedPool).distinctBy { it.videoId }.take(3)
                    }
                }
                _searchExploreCoverTracks.value = mapped
            }

            publishArtworkFanPools()

            // Fetch high-priority categories first, then remainder in small batches
            categoryQueries.entries.chunked(4).forEach { batch ->
                val requests = batch.map { entry ->
                    entry.key to async {
                        runCatching {
                            SimpMusicExtractorEngine.searchYouTubeMusic(
                                query = entry.value.first,
                                filter = entry.value.second,
                                prefetchTopResult = false
                            ).take(4)
                        }.getOrDefault(emptyList())
                    }
                }
                requests.forEach { (title, request) -> fetchedByCategory[title] = request.await() }
                publishArtworkFanPools()
            }

            val madeForYouCandidates = (
                _homeRecommendations.value + _favorites.value + _recentlyPlayed.value +
                    fetchedByCategory["Made For You"].orEmpty()
            ).distinctBy { it.videoId }
            val madeForYou = LocalRecommendationEngine.rankTracks(
                candidates = madeForYouCandidates,
                favorites = _favorites.value,
                recentlyPlayed = _recentlyPlayed.value,
                interactionScores = storage.loadRecommendationInteractionScores(),
                limit = 3
            )
            fetchedByCategory["Made For You"] = madeForYou
            publishArtworkFanPools()
            searchExploreCoversLoaded = true

            // Persist resolved fan-out covers to disk so subsequent launches use cache immediately
            val coversToPersist = _searchExploreCoverTracks.value.mapValues { (_, tracks) ->
                tracks.mapNotNull { it.highResArtworkUrl.takeIf { u -> u.isNotBlank() } }.take(3)
            }
            if (coversToPersist.isNotEmpty()) {
                storage.saveExploreCoverCache(coversToPersist)
            }
        }
    }

    private fun applyLocalRecommendations() {
        viewModelScope.launch(Dispatchers.Default) {
            val favorites = _favorites.value
            val recent = _recentlyPlayed.value
            val allArtists = _topArtists.value
            val allPlaylists = _playlists.value
            val signals = withContext(Dispatchers.IO) { storage.loadRecommendationInteractionScores() }
            val isAll = _activeCategory.value == "All"
            val tracks = if (isAll) rawHomeTracks else rawCategoryTracks
            val videos = if (isAll) rawHomeVideos else rawCategoryVideos
            val albums = if (isAll) rawHomeAlbums else rawCategoryAlbums
            val personalizedTracks = LocalRecommendationEngine.rankTracks(tracks, favorites, recent, signals, limit = 40)
            val rankedVideos = LocalRecommendationEngine.rankTracks(
                videos, favorites, recent, signals, limit = 20, favorOfficialVideos = true
            )
            val rankedAlbums = LocalRecommendationEngine.rankTracks(
                albums, favorites, recent, signals, limit = 16
            )

            val (candidateArtists, candidateGenres) = if (isAll) {
                emptySet<String>() to emptySet<String>()
            } else {
                LocalRecommendationEngine.buildCategoryFilterContext(tracks)
            }

            val matchingLikes = if (isAll) favorites else if (tracks.isEmpty()) emptyList() else favorites.filter {
                LocalRecommendationEngine.categoryMatches(it, candidateArtists, candidateGenres)
            }
            val rankedLikes = LocalRecommendationEngine.rankTracks(
                matchingLikes, favorites, recent, signals, limit = 12
            )

            val matchingArtists = if (isAll) allArtists else if (tracks.isEmpty()) emptyList() else allArtists.filter { artist ->
                val artistLower = artist.name.lowercase().trim()
                candidateArtists.any { it.contains(artistLower) || artistLower.contains(it) }
            }
            val matchingRecent = if (isAll) {
                recent.distinctBy { LocalRecommendationEngine.songIdentityKey(it) }
            } else if (tracks.isEmpty()) emptyList() else {
                recent.filter {
                    LocalRecommendationEngine.categoryMatches(it, candidateArtists, candidateGenres)
                }.distinctBy { LocalRecommendationEngine.songIdentityKey(it) }
            }
            val matchingPlaylists = if (isAll) allPlaylists else if (tracks.isEmpty()) emptyList() else allPlaylists.filter { playlist ->
                playlist.tracks.any { LocalRecommendationEngine.categoryMatches(it, candidateArtists, candidateGenres) }
            }

            withContext(Dispatchers.Main.immediate) {
                _homeRecommendations.value = personalizedTracks
                if (!isAll) _categoryTracks.value = personalizedTracks
                _homeMusicVideos.value = rankedVideos
                _homeAlbumRecommendations.value = rankedAlbums
                _homeLikedRecommendations.value = rankedLikes
                _visibleTopArtists.value = matchingArtists
                _visibleRecentlyPlayed.value = matchingRecent
                _visibleUserPlaylists.value = matchingPlaylists
            }
        }
    }

    fun updateSearchQuery(query: String) {
        _searchQuery.value = query
        searchJob?.cancel()
        if (query.isBlank()) {
            _searchMatchedArtist.value = null
            _searchMatchedArtists.value = emptyList()
            _artistFeaturingItems.value = emptyList()
            _searchPlaylistResults.value = emptyList()
            _searchResults.value = _chartSections.value.firstOrNull()?.tracks
                ?: SimpMusicExtractorEngine.DEFAULT_SEED_TRACKS
            return
        }
        searchJob = viewModelScope.launch {
            _isSearching.value = true
            _searchResults.value = emptyList()
            _searchMatchedArtist.value = null
            _searchMatchedArtists.value = emptyList()
            _artistFeaturingItems.value = emptyList()
            _searchPlaylistResults.value = emptyList()

            // If Playlists filter pill is active, fetch ONLY playlists (no songs, no artist featuring shelf)
            if (_searchFilter.value == "playlists") {
                val playlists = try {
                    SimpMusicExtractorEngine.fetchCommunityPlaylists(query.trim())
                } catch (_: Exception) {
                    emptyList()
                }
                _searchPlaylistResults.value = playlists
                _isSearching.value = false
                return@launch
            }

            // Parallel artist search for top artist card, artist list & featuring carousels
            val artistDeferred = async {
                if (query.trim().isNotEmpty()) {
                    try {
                        SimpMusicExtractorEngine.searchArtists(query.trim())
                    } catch (_: Exception) {
                        emptyList()
                    }
                } else emptyList()
            }
            val resultsDeferred = async {
                SimpMusicExtractorEngine.searchYouTubeMusic(query, _searchFilter.value)
            }

            val artists = artistDeferred.await()
            val matched = artists.firstOrNull()
            _searchMatchedArtists.value = artists
            _searchMatchedArtist.value = matched

            val results = resultsDeferred.await()
            if (results.isNotEmpty()) {
                _searchResults.value = results
            } else if (_searchFilter.value == "artists" && matched != null) {
                // If filter is artists and tracks are empty, prefetch top tracks for that artist
                try {
                    val detail = SimpMusicExtractorEngine.fetchArtistDetail(matched)
                    _searchResults.value = detail.topSongs.map { it.track }
                } catch (_: Exception) {}
            }

            if (matched != null) {
                try {
                    val detail = SimpMusicExtractorEngine.fetchArtistDetail(matched)
                    val feat = detail.carousels.firstOrNull {
                        it.title.contains("Featuring", ignoreCase = true) ||
                        it.title.contains("Singles", ignoreCase = true) ||
                        it.title.contains("Albums", ignoreCase = true)
                    }?.items ?: detail.albums.map {
                        ArtistCarouselItem(id = it.id, title = it.title, subtitle = it.year, artworkUrl = it.artworkUrl, browseId = it.id)
                    }
                    _artistFeaturingItems.value = feat.take(10)
                } catch (_: Exception) {
                    _artistFeaturingItems.value = emptyList()
                }
            } else {
                _artistFeaturingItems.value = emptyList()
            }

            _isSearching.value = false
        }
    }

    fun removeFromRecentlyPlayed(track: AuraTrack) {
        val trackKey = LocalRecommendationEngine.songIdentityKey(track)
        storage.removeRecentlyPlayed(track.videoId, trackKey)
        val updatedRecent = storage.loadRecentlyPlayed()
        _recentlyPlayed.value = updatedRecent
        if (_activeCategory.value == "All") {
            _visibleRecentlyPlayed.value = updatedRecent
        }
        applyLocalRecommendations()
        Toast.makeText(getApplication(), "Removed '${track.title}' from recently played", Toast.LENGTH_SHORT).show()
    }

    fun setSearchFilter(filter: String) {
        _searchFilter.value = filter
        if (_searchQuery.value.isNotBlank()) {
            updateSearchQuery(_searchQuery.value)
        }
    }

    fun playMoodCategory(mood: MoodCategory) {
        viewModelScope.launch {
            _isSearching.value = true
            val tracks = SimpMusicExtractorEngine.searchYouTubeMusic(mood.query, "songs")
            _isSearching.value = false
            if (tracks.isNotEmpty()) {
                playTrack(tracks.first(), tracks)
            }
        }
    }

    fun playTrack(track: AuraTrack, queue: List<AuraTrack>? = null) {
        val currentCommunity = _selectedPlaylistDetail.value
        if (currentCommunity != null && currentCommunity.tracks.any { it.track.videoId == track.videoId || it.track.id == track.id }) {
            _activePlayingPlaylistDetail.value = currentCommunity
            _activePlayingUserPlaylist.value = null
        }
        val currentUserPl = _selectedUserPlaylist.value
        if (currentUserPl != null && currentUserPl.tracks.any { it.videoId == track.videoId || it.id == track.id }) {
            _activePlayingUserPlaylist.value = currentUserPl
            _activePlayingPlaylistDetail.value = null
        }
        val isSameCurrentTrack = AuraPlayerManager.currentTrack.value == track
        AuraPlayerManager.playTrack(track, queue)
        if (isSameCurrentTrack) _listeningStats.value = storage.recordTrackPlay(track)
    }

    fun playSearchTrack(track: AuraTrack, results: List<AuraTrack>) {
        val isSameCurrentTrack = AuraPlayerManager.currentTrack.value == track
        if (AuraPlayerManager.isShuffleEnabled.value) {
            val shuffledQueue = results.filterNot { it.videoId == track.videoId }.shuffled()
            AuraPlayerManager.playTrack(track, listOf(track) + shuffledQueue, allowRadioQueue = true)
        } else {
            // Playing from search query should NOT auto-play other songs from search results unless shuffle is enabled
            AuraPlayerManager.playTrack(track, listOf(track), allowRadioQueue = false)
        }
        if (isSameCurrentTrack) _listeningStats.value = storage.recordTrackPlay(track)
    }

    fun toggleFavorite(track: AuraTrack) {
        val current = _favorites.value.toMutableList()
        val existingIdx = current.indexOfFirst { it.videoId == track.videoId }
        if (existingIdx >= 0) {
            current.removeAt(existingIdx)
        } else {
            current.add(0, track)
        }
        _favorites.value = current
        storage.saveFavorites(current)
        applyLocalRecommendations()
    }

    fun isFavorite(videoId: String): Boolean {
        return _favorites.value.any { it.videoId == videoId }
    }

    fun isTrackDownloaded(videoId: String): Boolean {
        return _downloads.value.any { it.videoId == videoId && it.isDownloaded }
    }

    fun downloadTrack(track: AuraTrack) {
        if (_downloadingIds.value.contains(track.videoId)) return
        val isAlreadyDownloaded = _downloads.value.any {
            it.videoId == track.videoId && it.isDownloaded && it.localFilePath?.let { p -> File(p).exists() } == true
        }
        if (isAlreadyDownloaded) {
            Toast.makeText(getApplication(), "Already downloaded", Toast.LENGTH_SHORT).show()
            return
        }

        viewModelScope.launch {
            // Immediate feedback: add placeholder to downloads list so it appears in "Downloaded" pill right away!
            val placeholder = track.copy(isDownloaded = false)
            val currentDownloads = _downloads.value.filterNot { it.videoId == track.videoId }
            _downloads.value = listOf(placeholder) + currentDownloads

            _downloadingIds.value = _downloadingIds.value + track.videoId
            _downloadProgress.value = _downloadProgress.value + (track.videoId to 0.02f)

            // Cache lyrics and color palette for offline self-sufficiency in parallel
            val lyricsJob = async {
                try {
                    SimpMusicLyricsRepository.getLyrics(
                        videoId = track.videoId,
                        title = track.title,
                        artist = track.artist,
                        durationSeconds = track.durationSeconds
                    )?.let { storage.saveOfflineLyrics(track.videoId, it) }
                } catch (e: Exception) {
                    Log.w("AuraViewModel", "Failed to cache offline lyrics for ${track.videoId}: ${e.message}")
                }
            }

            val paletteJob = async {
                try {
                    val pal = extractAuraPaletteFromUrl(track.highResArtworkUrl)
                    storage.saveOfflinePalette(track.videoId, pal)
                } catch (e: Exception) {
                    Log.w("AuraViewModel", "Failed to cache offline palette for ${track.videoId}: ${e.message}")
                }
            }

            val file = AuraPlayerManager.downloadTrackOffline(getApplication(), track) { _, _, progress ->
                _downloadProgress.value = _downloadProgress.value + (track.videoId to progress)
            }

            lyricsJob.await()
            paletteJob.await()

            _downloadingIds.value = _downloadingIds.value - track.videoId
            _downloadProgress.value = _downloadProgress.value - track.videoId

            if (file != null && file.exists() && file.length() > 10_000L) {
                val downloadedTrack = track.copy(isDownloaded = true, localFilePath = file.absolutePath)
                val updated = listOf(downloadedTrack) + _downloads.value.filterNot { it.videoId == track.videoId }
                _downloads.value = updated
                storage.saveDownloads(updated.filter { it.isDownloaded })
                withContext(Dispatchers.Main) {
                    Toast.makeText(getApplication(), "Downloaded: ${track.title}", Toast.LENGTH_SHORT).show()
                }
            } else {
                // If failed, remove placeholder
                _downloads.value = _downloads.value.filterNot { it.videoId == track.videoId && !it.isDownloaded }
                withContext(Dispatchers.Main) {
                    Toast.makeText(getApplication(), "Download failed for ${track.title}", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    fun removeDownload(track: AuraTrack) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                // 1. Delete by localFilePath
                track.localFilePath?.let { p ->
                    val f = File(p)
                    if (f.exists()) f.delete()
                }
                // 2. Delete from dedicated folder
                val dedicated = AuraPlayerManager.getOfflineFile(getApplication(), track.videoId)
                if (dedicated.exists()) dedicated.delete()
            } catch (e: Exception) {
                Log.w("AuraViewModel", "Error deleting offline file: ${e.message}")
            }

            val updated = _downloads.value.filterNot { it.videoId == track.videoId }
            _downloads.value = updated
            storage.saveDownloads(updated.filter { it.isDownloaded })

            withContext(Dispatchers.Main) {
                Toast.makeText(getApplication(), "Removed '${track.title}' from downloads", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun upgradeLegacySpotifyPlaylistsIfNeeded() {
        viewModelScope.launch(Dispatchers.IO) {
            val currentPlaylists = _playlists.value
            val legacySpotifyPlaylists = currentPlaylists.filter { pl ->
                pl.id.startsWith("spotify_") && pl.tracks.isNotEmpty() && (
                    pl.tracks.size == 100 ||
                    pl.tracks.map { it.artworkUrl }.distinct().size <= 1
                )
            }
            if (legacySpotifyPlaylists.isEmpty()) return@launch

            for (legacyPl in legacySpotifyPlaylists) {
                val rawSpotifyId = legacyPl.id.removePrefix("spotify_")
                val refreshed = SimpMusicExtractorEngine.fetchSpotifyPlaylistDirect("https://open.spotify.com/playlist/$rawSpotifyId")
                if (refreshed != null && refreshed.tracks.isNotEmpty()) {
                    val updatedList = _playlists.value.map { existing ->
                        if (existing.id == legacyPl.id) {
                            refreshed.copy(
                                name = existing.name.ifBlank { refreshed.name },
                                coverUrl = existing.coverUrl.ifBlank { refreshed.coverUrl }
                            )
                        } else {
                            existing
                        }
                    }
                    _playlists.value = updatedList
                    _visibleUserPlaylists.value = updatedList
                    storage.savePlaylists(updatedList)
                }
            }
        }
    }

    fun isPlaylistAlreadyImported(url: String): AuraPlaylist? {
        val spotifyId = Regex("playlist[/:]?([a-zA-Z0-9]+)").find(url)?.groupValues?.getOrNull(1)
        if (!spotifyId.isNullOrBlank()) {
            return _playlists.value.firstOrNull {
                it.id == "spotify_$spotifyId" || it.id.contains(spotifyId)
            }
        }
        return null
    }

    fun importSpotifyPlaylist(url: String) {
        if (url.isBlank()) return
        val existing = isPlaylistAlreadyImported(url)
        if (existing != null) {
            _spotifyImportStatus.value = "Playlist '${existing.name}' is already in your library"
            Toast.makeText(
                getApplication(),
                "Playlist '${existing.name}' is already in your library (${existing.tracks.size} tracks)",
                Toast.LENGTH_LONG
            ).show()
            return
        }

        viewModelScope.launch {
            _spotifyImportStatus.value = "Connecting to Spotify…"
            val playlist = SimpMusicExtractorEngine.fetchSpotifyPlaylistDirect(url) { done, total ->
                _spotifyImportStatus.value = "Importing tracks ($done / $total)…"
            }
            if (playlist != null && playlist.tracks.isNotEmpty()) {
                val alreadyExists = _playlists.value.firstOrNull { it.id == playlist.id }
                if (alreadyExists != null) {
                    _spotifyImportStatus.value = "Playlist '${alreadyExists.name}' is already in your library"
                    withContext(Dispatchers.Main) {
                        Toast.makeText(
                            getApplication(),
                            "Playlist '${alreadyExists.name}' is already in your library",
                            Toast.LENGTH_SHORT
                        ).show()
                    }
                    return@launch
                }
                val updated = listOf(playlist) + _playlists.value.filterNot { it.id == playlist.id }
                _playlists.value = updated
                _visibleUserPlaylists.value = updated
                storage.savePlaylists(updated)
                _spotifyImportStatus.value = "Imported '${playlist.name}' (${playlist.tracks.size} tracks)"
                _importedPlaylistSuccess.value = playlist
                withContext(Dispatchers.Main) {
                    Toast.makeText(
                        getApplication(),
                        "Imported '${playlist.name}' (${playlist.tracks.size} tracks) successfully!",
                        Toast.LENGTH_LONG
                    ).show()
                }
            } else {
                _spotifyImportStatus.value = "Could not parse public Spotify playlist URL"
                withContext(Dispatchers.Main) {
                    Toast.makeText(
                        getApplication(),
                        "Could not parse Spotify playlist. Please check the link and try again.",
                        Toast.LENGTH_SHORT
                    ).show()
                }
            }
        }
    }

    fun createPlaylist(name: String, description: String = "", coverUrl: String = ""): AuraPlaylist {
        val newPlaylist = AuraPlaylist(
            id = "playlist_${System.currentTimeMillis()}",
            name = name.ifBlank { "My Playlist" },
            description = description,
            coverUrl = coverUrl,
            tracks = emptyList()
        )
        val updated = listOf(newPlaylist) + _playlists.value
        _playlists.value = updated
        storage.savePlaylists(updated)
        Toast.makeText(getApplication(), "Created playlist: ${newPlaylist.name}", Toast.LENGTH_SHORT).show()
        return newPlaylist
    }

    fun addTrackToPlaylist(playlistId: String, track: AuraTrack) {
        val target = _playlists.value.firstOrNull { it.id == playlistId }
        if (target == null) return

        if (target.tracks.any { it.videoId == track.videoId }) {
            Toast.makeText(getApplication(), "'${track.title}' is already in ${target.name}", Toast.LENGTH_SHORT).show()
            return
        }

        val updated = _playlists.value.map { pl ->
            if (pl.id == playlistId) {
                pl.copy(tracks = pl.tracks + track)
            } else {
                pl
            }
        }
        _playlists.value = updated
        storage.savePlaylists(updated)
        Toast.makeText(getApplication(), "Added '${track.title}' to ${target.name}", Toast.LENGTH_SHORT).show()
    }

    fun updatePlaylistCover(playlistId: String, newCoverUrl: String) {
        val updated = _playlists.value.map { pl ->
            if (pl.id == playlistId) pl.copy(coverUrl = newCoverUrl) else pl
        }
        _playlists.value = updated
        storage.savePlaylists(updated)
    }

    fun updatePlaylistTitle(playlistId: String, newTitle: String) {
        val updated = _playlists.value.map { pl ->
            if (pl.id == playlistId) pl.copy(name = newTitle) else pl
        }
        _playlists.value = updated
        storage.savePlaylists(updated)
    }

    fun deletePlaylist(playlistId: String) {
        val target = _playlists.value.firstOrNull { it.id == playlistId }
        val updated = _playlists.value.filterNot { it.id == playlistId }
        _playlists.value = updated
        storage.savePlaylists(updated)
        Toast.makeText(getApplication(), "Deleted playlist: ${target?.name ?: "Playlist"}", Toast.LENGTH_SHORT).show()
    }

    fun removeTrackFromPlaylist(playlistId: String, videoId: String) {
        val pl = _playlists.value.firstOrNull { it.id == playlistId }
        val track = pl?.tracks?.firstOrNull { it.videoId == videoId }
        val updated = _playlists.value.map { p ->
            if (p.id == playlistId) {
                p.copy(tracks = p.tracks.filterNot { it.videoId == videoId })
            } else {
                p
            }
        }
        _playlists.value = updated
        storage.savePlaylists(updated)
        Toast.makeText(getApplication(), "Removed '${track?.title ?: "song"}' from playlist", Toast.LENGTH_SHORT).show()
    }

    fun isPlaylistSaved(playlistId: String): Boolean {
        val cleanId = playlistId.removePrefix("VL").removePrefix("ytm_")
        return _playlists.value.any { pl ->
            pl.id == playlistId ||
            pl.id == "ytm_$playlistId" ||
            pl.id.removePrefix("VL").removePrefix("ytm_") == cleanId
        }
    }

    fun toggleSaveCommunityPlaylist(playlist: YtmCommunityPlaylist) {
        val cleanId = playlist.id.removePrefix("VL").removePrefix("ytm_")
        val existing = _playlists.value.firstOrNull { pl ->
            pl.id == playlist.id ||
            pl.id == "ytm_${playlist.id}" ||
            pl.id.removePrefix("VL").removePrefix("ytm_") == cleanId
        }

        if (existing != null) {
            val updated = _playlists.value.filterNot { it.id == existing.id }
            _playlists.value = updated
            storage.savePlaylists(updated)
            Toast.makeText(getApplication(), "Removed '${playlist.title}' from Library", Toast.LENGTH_SHORT).show()
        } else {
            val targetId = if (playlist.id.startsWith("VL") || playlist.id.startsWith("PL") || playlist.id.startsWith("RD")) playlist.id else "ytm_${playlist.id}"
            val newPlaylist = AuraPlaylist(
                id = targetId,
                name = playlist.title,
                description = if (playlist.author.isNotBlank()) "Curated by ${playlist.author}" else "Saved playlist",
                coverUrl = playlist.highResArtworkUrl,
                tracks = emptyList()
            )
            val updated = listOf(newPlaylist) + _playlists.value
            _playlists.value = updated
            storage.savePlaylists(updated)
            Toast.makeText(getApplication(), "Saved '${playlist.title}' to Library", Toast.LENGTH_SHORT).show()

            // Asynchronously fetch and populate tracks
            viewModelScope.launch {
                try {
                    val tracks = SimpMusicExtractorEngine.fetchPlaylistTracks(playlist.id)
                    if (tracks.isNotEmpty()) {
                        val populated = _playlists.value.map { pl ->
                            if (pl.id == newPlaylist.id) {
                                pl.copy(tracks = tracks.map { it.copy(album = playlist.title) })
                            } else pl
                        }
                        _playlists.value = populated
                        storage.savePlaylists(populated)
                    }
                } catch (e: Exception) {
                    Log.w("AuraViewModel", "Error populating saved playlist tracks: ${e.message}")
                }
            }
        }
    }

    fun toggleSavePlaylistDetail(detail: PlaylistDetail) {
        val cleanId = detail.id.removePrefix("VL").removePrefix("ytm_")
        val existing = _playlists.value.firstOrNull { pl ->
            pl.id == detail.id ||
            pl.id == "ytm_${detail.id}" ||
            pl.id.removePrefix("VL").removePrefix("ytm_") == cleanId
        }

        if (existing != null) {
            val updated = _playlists.value.filterNot { it.id == existing.id }
            _playlists.value = updated
            storage.savePlaylists(updated)
            Toast.makeText(getApplication(), "Removed '${detail.title}' from Library", Toast.LENGTH_SHORT).show()
        } else {
            val targetId = if (detail.id.startsWith("VL") || detail.id.startsWith("PL") || detail.id.startsWith("RD")) detail.id else "ytm_${detail.id}"
            val tracks = detail.tracks.map { it.track.copy(album = detail.title, isPodcast = detail.isPodcast) }
            val newPlaylist = AuraPlaylist(
                id = targetId,
                name = detail.title,
                description = if (detail.author.isNotBlank()) "Curated by ${detail.author}" else if (detail.isPodcast) "Podcast Show" else "Saved playlist",
                coverUrl = detail.bannerUrl,
                tracks = tracks,
                isPodcast = detail.isPodcast
            )
            val updated = listOf(newPlaylist) + _playlists.value
            _playlists.value = updated
            storage.savePlaylists(updated)
            Toast.makeText(getApplication(), "Saved '${detail.title}' to Library", Toast.LENGTH_SHORT).show()
        }
    }

    fun adjustLyricsOffset(deltaMs: Int) {
        val vid = AuraPlayerManager.currentTrack.value?.videoId ?: return
        val canonicalVid = AuraPlayerManager.originalAudioTrack?.videoId ?: vid
        val next = (_lyricsOffsetMs.value + deltaMs).coerceIn(-120000, 120000)
        _lyricsOffsetMs.value = next
        storage.setLyricsOffsetMs(vid, next)
        if (canonicalVid != vid) {
            storage.setLyricsOffsetMs(canonicalVid, next)
        }
    }

    fun toggleRomanizedLyrics() {
        _showRomanizedLyrics.value = !_showRomanizedLyrics.value
    }

    fun toggleRomanizationLanguage(language: String) {
        val current = _romanizationLanguages.value.toMutableSet()
        if (current.contains(language)) {
            current.remove(language)
        } else {
            current.add(language)
        }
        _romanizationLanguages.value = current
        storage.saveRomanizationLanguages(current)
    }

    fun setRomanizationLanguages(languages: Set<String>) {
        _romanizationLanguages.value = languages
        storage.saveRomanizationLanguages(languages)
    }

    fun setNowPlayingStyle(style: NowPlayingStyle) {
        _nowPlayingStyle.value = style
        storage.saveNowPlayingStyle(style)
    }

    fun selectEqualizerPreset(preset: EqualizerPreset) {
        AuraPlayerManager.selectEqualizerPreset(preset)
    }

    fun toggleVideoMode() {
        if (AuraPlayerManager.isVideoMode.value) {
            AuraPlayerManager.switchToAudioMode()
            Toast.makeText(getApplication(), "Switched to Audio Mode", Toast.LENGTH_SHORT).show()
            return
        }

        val track = AuraPlayerManager.currentTrack.value
        if (track == null) {
            Toast.makeText(getApplication(), "No track currently playing", Toast.LENGTH_SHORT).show()
            return
        }

        viewModelScope.launch {
            AuraPlayerManager.setVideoLoading(true)
            Toast.makeText(getApplication(), "Checking for Official Music Video…", Toast.LENGTH_SHORT).show()

            val targetKey = LocalRecommendationEngine.songIdentityKey(track)
            val matchedShelfVideo = (_homeMusicVideos.value + rawHomeVideos + rawCategoryVideos).firstOrNull { candidate ->
                candidate.videoId.isNotBlank() && (
                    candidate.videoId == track.videoId ||
                        (targetKey.isNotBlank() && LocalRecommendationEngine.songIdentityKey(candidate) == targetKey)
                    )
            }
            val official = SimpMusicExtractorEngine.findOfficialMusicVideo(track)
                ?: matchedShelfVideo?.takeIf {
                    withContext(Dispatchers.IO) { SimpMusicExtractorEngine.resolveVideoStream(it.videoId) != null }
                }
            AuraPlayerManager.setVideoLoading(false)

            if (official != null) {
                AuraPlayerManager.playVideoForTrack(official, track)
                withContext(Dispatchers.Main) {
                    Toast.makeText(getApplication(), "Playing Official Video: ${official.title}", Toast.LENGTH_SHORT).show()
                }
            } else {
                withContext(Dispatchers.Main) {
                    Toast.makeText(
                        getApplication(),
                        "No official music video available for this song",
                        Toast.LENGTH_SHORT
                    ).show()
                }
            }
        }
    }

    fun setVideoMode(enabled: Boolean) {
        AuraPlayerManager.setVideoMode(enabled)
    }

    fun loadLivePerformances(track: AuraTrack? = AuraPlayerManager.currentTrack.value) {
        val target = track ?: return
        viewModelScope.launch {
            _isLoadingLivePerformances.value = true
            _livePerformances.value = SimpMusicExtractorEngine.fetchLivePerformancesAndVideos(target)
            _isLoadingLivePerformances.value = false
        }
    }

    fun playLivePerformance(track: AuraTrack) {
        AuraPlayerManager.setVideoMode(true)
        val currentPos = AuraPlayerManager.exoPlayer?.currentPosition ?: 0L
        AuraPlayerManager.playTrack(track, listOf(track))
        AuraPlayerManager.exoPlayer?.seekTo(currentPos)
        Toast.makeText(getApplication(), "Playing: ${track.title}", Toast.LENGTH_SHORT).show()
    }

    fun checkForUpdates(force: Boolean = false) {
        viewModelScope.launch {
            val release = AuraUpdateManager.checkForUpdates(getApplication(), force)
            if (release != null) {
                _currentUpdateRelease.value = release
                _updateState.value = UpdateState.IDLE
                _updateErrorMessage.value = null
                _isUpdateModalVisible.value = true
            } else if (force) {
                withContext(Dispatchers.Main) {
                    Toast.makeText(
                        getApplication(),
                        "You're already on the latest version of Aura!",
                        Toast.LENGTH_SHORT
                    ).show()
                }
            }
        }
    }

    fun openUpdateModalForRelease(release: AuraReleaseInfo) {
        _currentUpdateRelease.value = release
        _updateState.value = UpdateState.IDLE
        _updateErrorMessage.value = null
        _isUpdateModalVisible.value = true
    }

    /**
     * Test trigger to preview the AuraUpdateModal UI and verify release changelog styling.
     */
    fun triggerTestUpdateModal() {
        val testRelease = AuraReleaseInfo(
            version = "1.4.7",
            tagName = "v1.4.7",
            releaseTitle = "Aura v1.4.7 • Circled APK Icon, Borderless Playlist Controls & Home Song 3-Dot Options",
            releaseNotes = """
# Aura v1.4.7

Release Tags: [release, v1.4.7, android, kotlin, jetpack-compose, in-app-update, playlist-controls, home-options]

---

## Overview

Aura v1.4.7 establishes exact circular APK icon branding in the in-app update experience and system notifications, refines playlist and album action controls with borderless icons and dynamic Shuffle/Repeat swap, and replaces redundant circular play buttons on Home screen song tiles with standardized 3-dot options.

---

## What's Changed

- **Permanent Release Signing Keystore**:
  - Committed official permanent release keystore to guarantee uniform cryptographic signatures across all GitHub Actions builds.
  - Fixes `App not installed as package conflicts with an existing package` (`INSTALL_FAILED_UPDATE_INCOMPATIBLE`) when updating the app directly.
- **Monochrome Mascot Notification Icon**:
  - Replaced the circular launcher stencil with a custom vector silhouette of the Aura bunny mascot with headphones (`ic_aura_notification.xml`).
  - Correctly fills the notification badge circle and status bar with the Aura mascot.
- **Notification Layout Polish**:
  - Retained the high-resolution mascot thumbnail on the right-side of the notification card via `setLargeIcon`.
  - Removed full-bleed `BigPicture` expansion so notifications remain compact and clean when the notification dropdown is opened.
            """.trimIndent(),
            downloadUrl = "https://github.com/jlfuertes14/Aura-Music/releases",
            fileName = "aura-music-v1.4.4-universal.apk",
            apkSize = 34_820_000L,
            publishedAt = "2026-11-02T12:00:00Z",
            htmlUrl = "https://github.com/jlfuertes14/Aura-Music/releases"
        )
        _currentUpdateRelease.value = testRelease
        _updateState.value = UpdateState.IDLE
        _updateErrorMessage.value = null
        _isUpdateModalVisible.value = true
    }

    fun dismissUpdateModal() {
        if (_updateState.value != UpdateState.DOWNLOADING) {
            _isUpdateModalVisible.value = false
        }
    }

    fun snoozeUpdate(version: String) {
        AuraUpdateManager.snoozeUpdate(getApplication(), version)
        _isUpdateModalVisible.value = false
    }

    fun downloadAndInstallUpdate(release: AuraReleaseInfo) {
        viewModelScope.launch {
            if (!release.downloadUrl.endsWith(".apk", ignoreCase = true) && !release.downloadUrl.contains("/download/")) {
                // Interactive preview mode: simulate smooth download streaming for UI evaluation
                _updateState.value = UpdateState.DOWNLOADING
                _updateErrorMessage.value = null
                for (pct in 1..100) {
                    kotlinx.coroutines.delay(35)
                    val written = (release.apkSize * (pct / 100f)).toLong()
                    _updateDownloadProgress.value = UpdateDownloadProgress(
                        writtenBytes = written,
                        totalBytes = release.apkSize,
                        fraction = pct / 100f
                    )
                }
                _updateState.value = UpdateState.READY
                return@launch
            }

            _updateState.value = UpdateState.DOWNLOADING
            _updateErrorMessage.value = null
            _updateDownloadProgress.value = UpdateDownloadProgress()

            val result = AuraUpdateManager.downloadApk(getApplication(), release) { progress ->
                _updateDownloadProgress.value = progress
            }

            result.onSuccess { apkFile ->
                downloadedApkFile = apkFile
                _updateState.value = UpdateState.READY
                AuraUpdateManager.installApk(getApplication(), apkFile)
            }.onFailure { err ->
                _updateState.value = UpdateState.ERROR
                _updateErrorMessage.value = err.message ?: "Failed to download update APK"
            }
        }
    }

    fun installDownloadedApk(release: AuraReleaseInfo) {
        downloadedApkFile?.let { apkFile ->
            AuraUpdateManager.installApk(getApplication(), apkFile)
        } ?: downloadAndInstallUpdate(release)
    }
}
