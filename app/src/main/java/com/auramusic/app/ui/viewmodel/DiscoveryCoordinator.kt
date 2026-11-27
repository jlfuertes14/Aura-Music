package com.auramusic.app.ui.viewmodel

import android.app.Application
import android.util.Log
import com.auramusic.app.core.extractor.ArtistEditorialRepository
import com.auramusic.app.core.extractor.SimpMusicExtractorEngine
import com.auramusic.app.core.recommendations.LocalRecommendationEngine
import com.auramusic.app.data.AuraStorageRepository
import com.auramusic.app.domain.model.ArtistCarouselItem
import com.auramusic.app.domain.model.ArtistDetail
import com.auramusic.app.domain.model.AuraChart
import com.auramusic.app.domain.model.AuraPlaylist
import com.auramusic.app.domain.model.AuraTrack
import com.auramusic.app.domain.model.CatalogDetail
import com.auramusic.app.domain.model.MoodCategory
import com.auramusic.app.domain.model.PlaylistDetail
import com.auramusic.app.domain.model.YtmChartSection
import com.auramusic.app.domain.model.YtmCommunityPlaylist
import com.auramusic.app.domain.model.YtmMoodBrowseParams
import com.auramusic.app.domain.model.YtmMoodShelf
import com.auramusic.app.domain.model.YtmTopArtist
import com.auramusic.app.ui.DetailScreenEntry
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.concurrent.ConcurrentHashMap

internal class DiscoveryCoordinator(
    private val application: Application,
    private val scope: CoroutineScope,
    private val storage: AuraStorageRepository,
    private val library: LibraryCoordinator,
    private val onPlayTrack: (AuraTrack, List<AuraTrack>?) -> Unit
) {
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

    private val detailStackLock = Any()
    private var nextDetailStackId = 0L
    private val _detailStack = MutableStateFlow<List<DetailScreenEntry>>(emptyList())
    val detailStack: StateFlow<List<DetailScreenEntry>> = _detailStack.asStateFlow()

    private val _isArtistLoading = MutableStateFlow(false)
    val isArtistLoading: StateFlow<Boolean> = _isArtistLoading.asStateFlow()

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

    private val catalogDetailCache = ConcurrentHashMap<String, List<YtmMoodShelf>>()

    private val _isPlaylistLoading = MutableStateFlow(false)
    val isPlaylistLoading: StateFlow<Boolean> = _isPlaylistLoading.asStateFlow()

    private val _moodCategories = MutableStateFlow<List<MoodCategory>>(emptyList())
    val moodCategories: StateFlow<List<MoodCategory>> = _moodCategories.asStateFlow()

    private val _isLoadingHome = MutableStateFlow(true)
    val isLoadingHome: StateFlow<Boolean> = _isLoadingHome.asStateFlow()

    // Search
    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()

    private val _searchFilter = MutableStateFlow("all")
    val searchFilter: StateFlow<String> = _searchFilter.asStateFlow()

    private val _searchResults = MutableStateFlow<List<AuraTrack>>(SimpMusicExtractorEngine.DEFAULT_SEED_TRACKS)
    val searchResults: StateFlow<List<AuraTrack>> = _searchResults.asStateFlow()

    private val _searchSuggestions = MutableStateFlow<List<String>>(emptyList())
    val searchSuggestions: StateFlow<List<String>> = _searchSuggestions.asStateFlow()

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

    private var searchJob: Job? = null

    fun syncInitialVisibleLibrary() {
        _visibleRecentlyPlayed.value = library.recentlyPlayed.value
        _visibleUserPlaylists.value = library.playlists.value
    }

    fun syncAfterBackupRestore() {
        _visibleUserPlaylists.value = library.playlists.value
        _visibleRecentlyPlayed.value = library.recentlyPlayed.value
        _selectedChartCountry.value = storage.loadChartCountry()
        applyLocalRecommendations()
    }

    fun updateVisibleRecentlyPlayedIfAll(tracks: List<AuraTrack>) {
        if (_activeCategory.value == "All") {
            _visibleRecentlyPlayed.value = tracks
        }
    }

    fun setVisibleUserPlaylists(playlists: List<AuraPlaylist>) {
        _visibleUserPlaylists.value = playlists
    }

    fun findMatchedShelfVideo(track: AuraTrack): AuraTrack? {
        val targetKey = LocalRecommendationEngine.songIdentityKey(track)
        return (_homeMusicVideos.value + rawHomeVideos + rawCategoryVideos).firstOrNull { candidate ->
            candidate.videoId.isNotBlank() && (
                candidate.videoId == track.videoId ||
                    (targetKey.isNotBlank() && LocalRecommendationEngine.songIdentityKey(candidate) == targetKey)
                )
        }
    }

    fun refreshHome() {
        scope.launch(Dispatchers.IO) {
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
                val playedArtists = (library.recentlyPlayed.value + library.favorites.value)
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
                rawHomeTracks = (library.recentlyPlayed.value + library.favorites.value + sections.flatMap { it.tracks } +
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
        SimpMusicExtractorEngine.contentCountry = countryCode
        storage.saveChartCountry(countryCode)
        scope.launch(Dispatchers.IO) {
            _isChartLoading.value = true
            try {
                val chartDeferred = async { SimpMusicExtractorEngine.fetchHomeChartData(countryCode) }
                val homeChartsDeferred = async { SimpMusicExtractorEngine.fetchHomeChartsAndMoods() }
                val playlistsDeferred = async { SimpMusicExtractorEngine.fetchCommunityPlaylists() }

                val chart = chartDeferred.await()
                _chartData.value = chart
                if (chart.topArtists.isNotEmpty()) {
                    val artistsList = chart.topArtists.map {
                        YtmTopArtist(id = it.id, name = it.name, subscribers = it.subscribers, avatarUrl = it.avatarUrl)
                    }
                    _topArtists.value = artistsList
                    _visibleTopArtists.value = artistsList
                }

                val (sections, moods) = homeChartsDeferred.await()
                if (sections.isNotEmpty()) _chartSections.value = sections
                if (moods.isNotEmpty()) _moodCategories.value = moods
                val community = playlistsDeferred.await()
                if (community.isNotEmpty()) _communityPlaylists.value = community
            } catch (e: Exception) {
                Log.w("AuraViewModel", "Failed to fetch charts for $countryCode: ${e.message}")
            } finally {
                _isChartLoading.value = false
            }
        }
    }

    fun playCommunityPlaylist(playlist: YtmCommunityPlaylist) {
        scope.launch {
            _isPlaylistLoading.value = true
            val fullDetail = SimpMusicExtractorEngine.fetchPlaylistDetail(playlist)
            _isPlaylistLoading.value = false
            library.setActivePlaylistDetail(fullDetail)
            val taggedTracks = fullDetail.tracks.map { it.track.copy(album = fullDetail.title) }
            val tracks = if (taggedTracks.isNotEmpty()) taggedTracks else SimpMusicExtractorEngine.fetchPlaylistTracks(playlist.id).map { it.copy(album = playlist.title) }
            if (tracks.isNotEmpty()) {
                onPlayTrack(tracks.first(), tracks)
            }
        }
    }

    private fun nextStackKey(prefix: String): String = synchronized(detailStackLock) {
        "${prefix}_${++nextDetailStackId}"
    }

    private fun updateDetailStack(transform: (List<DetailScreenEntry>) -> List<DetailScreenEntry>) {
        val updated = synchronized(detailStackLock) {
            val next = transform(_detailStack.value)
            _detailStack.value = next
            next
        }
        _selectedArtistDetail.value = (updated.lastOrNull { it is DetailScreenEntry.Artist } as? DetailScreenEntry.Artist)?.detail
        _selectedPlaylistDetail.value = (updated.lastOrNull { it is DetailScreenEntry.Playlist } as? DetailScreenEntry.Playlist)?.detail
        _selectedCatalogDetail.value = (updated.lastOrNull { it is DetailScreenEntry.Catalog } as? DetailScreenEntry.Catalog)?.detail
        _isArtistLoading.value = (updated.lastOrNull() as? DetailScreenEntry.Artist)?.isLoading == true
        _isPlaylistLoading.value = (updated.lastOrNull() as? DetailScreenEntry.Playlist)?.isLoading == true
        _isCatalogLoading.value = (updated.lastOrNull() as? DetailScreenEntry.Catalog)?.isLoading == true
    }

    private fun updateDetailEntry(stackKey: String, transform: (DetailScreenEntry) -> DetailScreenEntry) {
        updateDetailStack { stack ->
            stack.map { entry ->
                if (entry.stackKey == stackKey) transform(entry) else entry
            }
        }
    }

    fun pushPlaylistIfNotTop(detail: PlaylistDetail) {
        val topPlaylist = _detailStack.value.lastOrNull() as? DetailScreenEntry.Playlist
        if (topPlaylist?.detail?.id != detail.id) {
            updateDetailStack { it + DetailScreenEntry.Playlist(nextStackKey("playlist"), detail, isLoading = false) }
        }
    }

    fun popTopDetail() {
        updateDetailStack { stack ->
            if (stack.isEmpty()) stack else stack.dropLast(1)
        }
    }

    fun closeDetail(stackKey: String) {
        updateDetailStack { stack ->
            stack.filterNot { it.stackKey == stackKey }
        }
    }

    fun clearDetailStack() {
        updateDetailStack { emptyList() }
    }

    fun openArtistDetail(artist: YtmTopArtist) {
        val topArtist = _detailStack.value.lastOrNull() as? DetailScreenEntry.Artist
        if (topArtist != null &&
            ((artist.id.isNotBlank() && topArtist.detail.id == artist.id) ||
                topArtist.detail.name.equals(artist.name, ignoreCase = true))
        ) {
            return
        }
        val stackKey = nextStackKey("artist")
        scope.launch {
            // Immediately push a preliminary state so transition is instant
            val preliminary = ArtistDetail(
                id = artist.id,
                name = artist.name,
                subscribers = artist.subscribers,
                bannerUrl = artist.avatarUrl,
                topSongs = emptyList(),
                albums = emptyList()
            )
            updateDetailStack { it + DetailScreenEntry.Artist(stackKey, preliminary, isLoading = true) }
            val fullDetail = SimpMusicExtractorEngine.fetchArtistDetail(artist)
            updateDetailEntry(stackKey) { entry ->
                if (entry is DetailScreenEntry.Artist) entry.copy(detail = fullDetail, isLoading = false) else entry
            }
            if (_detailStack.value.any { it.stackKey == stackKey }) {
                loadArtistEditorial(fullDetail.name, stackKey)
            }
        }
    }

    fun openArtistByName(artistName: String, songTitle: String = "", fallbackArtwork: String = "") {
        if (artistName.isBlank()) return
        val existingInTop = _topArtists.value.firstOrNull { it.name == artistName }
        if (existingInTop != null && songTitle.isBlank()) {
            openArtistDetail(existingInTop)
            return
        }
        val topArtist = _detailStack.value.lastOrNull() as? DetailScreenEntry.Artist
        if (topArtist != null && topArtist.detail.name.equals(artistName, ignoreCase = true)) {
            return
        }
        val stackKey = nextStackKey("artist")
        scope.launch {
            val preliminary = ArtistDetail(
                id = "",
                name = artistName,
                subscribers = "",
                bannerUrl = fallbackArtwork,
                topSongs = emptyList(),
                albums = emptyList()
            )
            updateDetailStack { it + DetailScreenEntry.Artist(stackKey, preliminary, isLoading = true) }
            val fullDetail = SimpMusicExtractorEngine.fetchArtistByName(artistName, songTitle, fallbackArtwork)
            updateDetailEntry(stackKey) { entry ->
                if (entry is DetailScreenEntry.Artist) entry.copy(detail = fullDetail, isLoading = false) else entry
            }
            if (_detailStack.value.any { it.stackKey == stackKey }) {
                loadArtistEditorial(fullDetail.name, stackKey)
            }
        }
    }

    private fun loadArtistEditorial(artistName: String, stackKey: String) {
        scope.launch {
            val current = (_detailStack.value.firstOrNull { it.stackKey == stackKey } as? DetailScreenEntry.Artist)?.detail ?: return@launch
            if (current.name != artistName) return@launch
            val selectedId = current.id
            updateDetailEntry(stackKey) { entry ->
                if (entry is DetailScreenEntry.Artist) entry.copy(detail = entry.detail.copy(motionLoading = true)) else entry
            }
            val motion = ArtistEditorialRepository.fetch(application, artistName)
            updateDetailEntry(stackKey) { entry ->
                if (entry is DetailScreenEntry.Artist && entry.detail.name == artistName && entry.detail.id == selectedId) {
                    entry.copy(detail = entry.detail.copy(motion = motion, motionLoading = false))
                } else entry
            }
        }
    }

    fun closeArtistDetail() {
        updateDetailStack { stack ->
            val idx = stack.indexOfLast { it is DetailScreenEntry.Artist }
            if (idx >= 0) stack.toMutableList().apply { removeAt(idx) } else stack
        }
    }

    fun openPlaylistDetail(playlist: YtmCommunityPlaylist) {
        val topPlaylist = _detailStack.value.lastOrNull() as? DetailScreenEntry.Playlist
        if (topPlaylist != null && topPlaylist.detail.id == playlist.id && playlist.id.isNotBlank()) {
            return
        }
        val stackKey = nextStackKey("playlist")
        scope.launch {
            val preliminary = PlaylistDetail(
                id = playlist.id,
                title = playlist.title,
                author = playlist.author,
                trackCount = playlist.trackCount,
                bannerUrl = playlist.artworkUrl,
                tracks = emptyList(),
                isPodcast = playlist.id.startsWith("MPSP") || playlist.id.startsWith("MP_") || playlist.id.contains("podcast", ignoreCase = true)
            )
            updateDetailStack { it + DetailScreenEntry.Playlist(stackKey, preliminary, isLoading = true) }
            val fullDetail = SimpMusicExtractorEngine.fetchPlaylistDetail(playlist)
            updateDetailEntry(stackKey) { entry ->
                if (entry is DetailScreenEntry.Playlist) entry.copy(detail = fullDetail, isLoading = false) else entry
            }
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
        scope.launch {
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
        updateDetailStack { stack ->
            val idx = stack.indexOfLast { it is DetailScreenEntry.Playlist }
            if (idx >= 0) stack.toMutableList().apply { removeAt(idx) } else stack
        }
    }

    fun openCatalogCategory(
        title: String,
        params: String = "",
        browseId: String = "FEmusic_moods_and_genres_category"
    ) {
        val topCatalog = _detailStack.value.lastOrNull() as? DetailScreenEntry.Catalog
        val isRetryOnTop = topCatalog != null &&
            topCatalog.detail.browseId == browseId &&
            topCatalog.detail.params == params &&
            topCatalog.detail.title == title
        if (isRetryOnTop && (topCatalog.isLoading || topCatalog.detail.shelves.isNotEmpty())) {
            return
        }
        val stackKey = if (isRetryOnTop) topCatalog.stackKey else nextStackKey("catalog")
        val cacheKey = "${browseId}_${params}_${title}"
        val cached = catalogDetailCache[cacheKey]
        if (cached != null && cached.isNotEmpty()) {
            val detail = CatalogDetail(
                title = title,
                params = params,
                browseId = browseId,
                shelves = cached,
                isLoading = false
            )
            if (isRetryOnTop) {
                updateDetailEntry(stackKey) { DetailScreenEntry.Catalog(stackKey, detail, isLoading = false) }
            } else {
                updateDetailStack { it + DetailScreenEntry.Catalog(stackKey, detail, isLoading = false) }
            }
            return
        }

        val loadingDetail = CatalogDetail(
            title = title,
            params = params,
            browseId = browseId,
            shelves = emptyList(),
            isLoading = true
        )
        if (isRetryOnTop) {
            updateDetailEntry(stackKey) { DetailScreenEntry.Catalog(stackKey, loadingDetail, isLoading = true) }
        } else {
            updateDetailStack { it + DetailScreenEntry.Catalog(stackKey, loadingDetail, isLoading = true) }
        }

        scope.launch {
            val resolvedDetail = try {
                val shelves = SimpMusicExtractorEngine.fetchMoodCategoryDetails(
                    browseId = browseId,
                    params = params.ifBlank { null },
                    title = title
                )
                if (shelves.isNotEmpty()) {
                    catalogDetailCache[cacheKey] = shelves
                }
                CatalogDetail(
                    title = title,
                    params = params,
                    browseId = browseId,
                    shelves = shelves,
                    isLoading = false
                )
            } catch (e: Exception) {
                Log.e("AuraViewModel", "Error fetching catalog category $title: ${e.message}")
                CatalogDetail(
                    title = title,
                    params = params,
                    browseId = browseId,
                    shelves = emptyList(),
                    isLoading = false,
                    error = e.message ?: "Failed to load"
                )
            }
            updateDetailEntry(stackKey) { entry ->
                if (entry is DetailScreenEntry.Catalog) entry.copy(detail = resolvedDetail, isLoading = false) else entry
            }
        }
    }

    fun closeCatalogDetail() {
        updateDetailStack { stack ->
            val idx = stack.indexOfLast { it is DetailScreenEntry.Catalog }
            if (idx >= 0) stack.toMutableList().apply { removeAt(idx) } else stack
        }
    }

    fun playArtist(artist: YtmTopArtist) {
        scope.launch {
            val tracks = SimpMusicExtractorEngine.searchYouTubeMusic("${artist.name} official songs", "songs")
            if (tracks.isNotEmpty()) {
                onPlayTrack(tracks.first(), tracks)
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

        categoryJob = scope.launch(Dispatchers.IO) {
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
        return (library.favorites.value + library.recentlyPlayed.value)
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

        searchExploreCoversJob = scope.launch {
            preferredArtistQuery()
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
                    fetchedByCategory.values.flatten() + _homeRecommendations.value + library.favorites.value +
                        library.recentlyPlayed.value + SimpMusicExtractorEngine.DEFAULT_SEED_TRACKS
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
                _homeRecommendations.value + library.favorites.value + library.recentlyPlayed.value +
                    fetchedByCategory["Made For You"].orEmpty()
                ).distinctBy { it.videoId }
            val madeForYou = LocalRecommendationEngine.rankTracks(
                candidates = madeForYouCandidates,
                favorites = library.favorites.value,
                recentlyPlayed = library.recentlyPlayed.value,
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

    fun applyLocalRecommendations() {
        scope.launch(Dispatchers.Default) {
            val favorites = library.favorites.value
            val recent = library.recentlyPlayed.value
            val allArtists = _topArtists.value
            val allPlaylists = library.playlists.value
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
            _searchSuggestions.value = emptyList()
            _searchResults.value = _chartSections.value.firstOrNull()?.tracks
                ?: SimpMusicExtractorEngine.DEFAULT_SEED_TRACKS
            return
        }
        searchJob = scope.launch {
            delay(150L)
            _isSearching.value = true
            _searchResults.value = emptyList()
            _searchMatchedArtist.value = null
            _searchMatchedArtists.value = emptyList()
            _artistFeaturingItems.value = emptyList()
            _searchPlaylistResults.value = emptyList()

            val suggestionsDeferred = async {
                try {
                    SimpMusicExtractorEngine.fetchSearchSuggestions(query.trim())
                } catch (_: Exception) {
                    emptyList()
                }
            }

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
            _searchSuggestions.value = suggestionsDeferred.await()
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

    fun setSearchFilter(filter: String) {
        _searchFilter.value = filter
        if (_searchQuery.value.isNotBlank()) {
            updateSearchQuery(_searchQuery.value)
        }
    }

    fun playMoodCategory(mood: MoodCategory) {
        scope.launch {
            _isSearching.value = true
            val tracks = SimpMusicExtractorEngine.searchYouTubeMusic(mood.query, "songs")
            _isSearching.value = false
            if (tracks.isNotEmpty()) {
                onPlayTrack(tracks.first(), tracks)
            }
        }
    }
}
