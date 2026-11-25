package com.auramusic.app.ui

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.auramusic.app.core.extractor.SimpMusicExtractorEngine
import com.auramusic.app.core.media.AuraPlayerManager
import com.auramusic.app.core.update.AuraReleaseInfo
import com.auramusic.app.core.update.UpdateDownloadProgress
import com.auramusic.app.core.update.UpdateState
import com.auramusic.app.data.AuraStorageRepository
import com.auramusic.app.domain.model.AppThemeStyle
import com.auramusic.app.domain.model.ArtistCarouselItem
import com.auramusic.app.domain.model.ArtistDetail
import com.auramusic.app.domain.model.AuraChart
import com.auramusic.app.domain.model.AuraCommentSort
import com.auramusic.app.domain.model.AuraCommentsPage
import com.auramusic.app.domain.model.AuraPalette
import com.auramusic.app.domain.model.AuraPlaylist
import com.auramusic.app.domain.model.AuraTrack
import com.auramusic.app.domain.model.AuraVideoInfo
import com.auramusic.app.domain.model.CatalogDetail
import com.auramusic.app.domain.model.EqualizerPreset
import com.auramusic.app.domain.model.ListeningStats
import com.auramusic.app.domain.model.LyricsData
import com.auramusic.app.domain.model.LyricsStyle
import com.auramusic.app.domain.model.MoodCategory
import com.auramusic.app.domain.model.NowPlayingStyle
import com.auramusic.app.domain.model.PlaylistDetail
import com.auramusic.app.domain.model.ReturnYouTubeDislikeData
import com.auramusic.app.domain.model.YtmChartSection
import com.auramusic.app.domain.model.YtmCommunityPlaylist
import com.auramusic.app.domain.model.YtmMoodShelf
import com.auramusic.app.domain.model.YtmTopArtist
import com.auramusic.app.ui.viewmodel.DiscoveryCoordinator
import com.auramusic.app.ui.viewmodel.LibraryCoordinator
import com.auramusic.app.ui.viewmodel.PlaybackCoordinator
import com.auramusic.app.ui.viewmodel.SettingsCoordinator
import kotlinx.coroutines.flow.StateFlow

sealed interface DetailScreenEntry {
    val stackKey: String

    data class Catalog(
        override val stackKey: String,
        val detail: CatalogDetail,
        val isLoading: Boolean = false
    ) : DetailScreenEntry

    data class Playlist(
        override val stackKey: String,
        val detail: PlaylistDetail,
        val isLoading: Boolean = false
    ) : DetailScreenEntry

    data class Artist(
        override val stackKey: String,
        val detail: ArtistDetail,
        val isLoading: Boolean = false
    ) : DetailScreenEntry
}

class AuraViewModel(application: Application) : AndroidViewModel(application) {
    private val storage = AuraStorageRepository(application)

    private lateinit var library: LibraryCoordinator
    private lateinit var discovery: DiscoveryCoordinator
    private lateinit var playback: PlaybackCoordinator

    private val settings = SettingsCoordinator(
        application = application,
        scope = viewModelScope,
        storage = storage,
        onReloadLyricsForProvider = { provider -> playback.reloadLyricsForProvider(provider) },
        onApplyDefaultLyricsOffsetIfZero = { offsetMs -> playback.applyDefaultLyricsOffsetIfZero(offsetMs) },
        onBackupRestored = {
            library.reloadAfterBackupRestore()
            discovery.syncAfterBackupRestore()
        }
    )

    init {
        library = LibraryCoordinator(
            application = application,
            scope = viewModelScope,
            storage = storage,
            autoDownloadLikedSongs = settings.autoDownloadLikedSongs,
            onApplyRecommendations = { discovery.applyLocalRecommendations() },
            onVisibleRecentlyPlayedResetIfAll = { tracks -> discovery.updateVisibleRecentlyPlayedIfAll(tracks) },
            onVisibleUserPlaylistsUpdated = { playlists -> discovery.setVisibleUserPlaylists(playlists) }
        )

        discovery = DiscoveryCoordinator(
            application = application,
            scope = viewModelScope,
            storage = storage,
            library = library,
            onPlayTrack = { track, queue -> playback.playTrack(track, queue) }
        )

        playback = PlaybackCoordinator(
            application = application,
            scope = viewModelScope,
            storage = storage,
            library = library,
            discovery = discovery,
            settings = settings
        )

        AuraPlayerManager.initialize(application)
        SimpMusicExtractorEngine.clearStreamCache()
        library.loadInitialFromStorage()
        SimpMusicExtractorEngine.contentCountry = discovery.selectedChartCountry.value
        discovery.syncInitialVisibleLibrary()

        // Automatically upgrade any previously imported Spotify playlists that were capped at 100 tracks
        // or had the playlist cover art shared across all tracks.
        library.upgradeLegacySpotifyPlaylistsIfNeeded()

        // Silent startup check against GitHub Releases (respects Auto Check for Updates setting)
        if (settings.autoCheckUpdates.value) {
            settings.checkForUpdates(force = false)
        }

        playback.bindPlayerCallbacksAndObserveTrack()
        discovery.refreshHome()
        // Pre-warm the default seed track so first tap is instant
        SimpMusicExtractorEngine.prefetchStream(SimpMusicExtractorEngine.DEFAULT_SEED_TRACKS.first().videoId)
    }

    // Home Charts & Moods
    val chartSections: StateFlow<List<YtmChartSection>> = discovery.chartSections
    val selectedChartCountry: StateFlow<String> = discovery.selectedChartCountry
    val chartData: StateFlow<AuraChart?> = discovery.chartData
    val isChartLoading: StateFlow<Boolean> = discovery.isChartLoading
    val topArtists: StateFlow<List<YtmTopArtist>> = discovery.topArtists
    val visibleTopArtists: StateFlow<List<YtmTopArtist>> = discovery.visibleTopArtists
    val visibleRecentlyPlayed: StateFlow<List<AuraTrack>> = discovery.visibleRecentlyPlayed
    val visibleUserPlaylists: StateFlow<List<AuraPlaylist>> = discovery.visibleUserPlaylists
    val selectedArtistDetail: StateFlow<ArtistDetail?> = discovery.selectedArtistDetail
    val detailStack: StateFlow<List<DetailScreenEntry>> = discovery.detailStack
    val nowPlayingArtistDetail: StateFlow<ArtistDetail?> = playback.nowPlayingArtistDetail
    val isNowPlayingArtistLoading: StateFlow<Boolean> = playback.isNowPlayingArtistLoading
    val nowPlayingVideoInfo: StateFlow<AuraVideoInfo?> = playback.nowPlayingVideoInfo
    val isArtistLoading: StateFlow<Boolean> = discovery.isArtistLoading
    val playerComments: StateFlow<AuraCommentsPage?> = playback.playerComments
    val areCommentsLoading: StateFlow<Boolean> = playback.areCommentsLoading
    val commentsError: StateFlow<String?> = playback.commentsError
    val activeCategory: StateFlow<String> = discovery.activeCategory
    val homeCategoryBackground: StateFlow<String?> = discovery.homeCategoryBackground
    val categoryTracks: StateFlow<List<AuraTrack>> = discovery.categoryTracks
    val categoryShelves: StateFlow<List<YtmMoodShelf>> = discovery.categoryShelves
    val homeRecommendations: StateFlow<List<AuraTrack>> = discovery.homeRecommendations
    val homeAlbumRecommendations: StateFlow<List<AuraTrack>> = discovery.homeAlbumRecommendations
    val homeLikedRecommendations: StateFlow<List<AuraTrack>> = discovery.homeLikedRecommendations
    val isCategoryLoading: StateFlow<Boolean> = discovery.isCategoryLoading
    val communityPlaylists: StateFlow<List<YtmCommunityPlaylist>> = discovery.communityPlaylists
    val homeMusicVideos: StateFlow<List<AuraTrack>> = discovery.homeMusicVideos
    val selectedPlaylistDetail: StateFlow<PlaylistDetail?> = discovery.selectedPlaylistDetail
    val selectedCatalogDetail: StateFlow<CatalogDetail?> = discovery.selectedCatalogDetail
    val isCatalogLoading: StateFlow<Boolean> = discovery.isCatalogLoading
    val activePlayingPlaylistDetail: StateFlow<PlaylistDetail?> = library.activePlayingPlaylistDetail
    val activePlayingUserPlaylist: StateFlow<AuraPlaylist?> = library.activePlayingUserPlaylist
    val playlistScrollTargetTrackId: StateFlow<String?> = library.playlistScrollTargetTrackId
    val isPlaylistLoading: StateFlow<Boolean> = discovery.isPlaylistLoading
    val moodCategories: StateFlow<List<MoodCategory>> = discovery.moodCategories
    val isLoadingHome: StateFlow<Boolean> = discovery.isLoadingHome

    // Search & Spotify Importer
    val searchQuery: StateFlow<String> = discovery.searchQuery
    val searchFilter: StateFlow<String> = discovery.searchFilter
    val searchResults: StateFlow<List<AuraTrack>> = discovery.searchResults
    val searchSuggestions: StateFlow<List<String>> = discovery.searchSuggestions
    val searchExploreCoverTracks: StateFlow<Map<String, List<AuraTrack>>> = discovery.searchExploreCoverTracks
    val isSearching: StateFlow<Boolean> = discovery.isSearching
    val searchMatchedArtist: StateFlow<YtmTopArtist?> = discovery.searchMatchedArtist
    val searchMatchedArtists: StateFlow<List<YtmTopArtist>> = discovery.searchMatchedArtists
    val artistFeaturingItems: StateFlow<List<ArtistCarouselItem>> = discovery.artistFeaturingItems
    val searchPlaylistResults: StateFlow<List<YtmCommunityPlaylist>> = discovery.searchPlaylistResults
    val spotifyImportStatus: StateFlow<String?> = library.spotifyImportStatus
    val importedPlaylistSuccess: StateFlow<AuraPlaylist?> = library.importedPlaylistSuccess
    val selectedUserPlaylist: StateFlow<AuraPlaylist?> = library.selectedUserPlaylist

    // Library (Favorites, Downloads, Playlists, Stats)
    val favorites: StateFlow<List<AuraTrack>> = library.favorites
    val downloads: StateFlow<List<AuraTrack>> = library.downloads
    val playlists: StateFlow<List<AuraPlaylist>> = library.playlists
    val recentlyPlayed: StateFlow<List<AuraTrack>> = library.recentlyPlayed
    val listeningStats: StateFlow<ListeningStats> = library.listeningStats
    val downloadingIds: StateFlow<Set<String>> = library.downloadingIds
    val downloadProgress: StateFlow<Map<String, Float>> = library.downloadProgress
    val isDownloadPaused: StateFlow<Boolean> = library.isDownloadPaused

    // Now Playing Enrichments (Palette, Lyrics, RYD, Style)
    val palette: StateFlow<AuraPalette> = playback.palette
    val lyrics: StateFlow<LyricsData?> = playback.lyrics
    val lyricsOffsetMs: StateFlow<Int> = playback.lyricsOffsetMs
    val showRomanizedLyrics: StateFlow<Boolean> = playback.showRomanizedLyrics
    val romanizationLanguages: StateFlow<Set<String>> = settings.romanizationLanguages
    val mainLyricsProvider: StateFlow<String> = settings.mainLyricsProvider
    val defaultLyricsOffsetMs: StateFlow<Int> = settings.defaultLyricsOffsetMs
    val autoDownloadLikedSongs: StateFlow<Boolean> = settings.autoDownloadLikedSongs
    val recordListeningHistory: StateFlow<Boolean> = settings.recordListeningHistory
    val autoCheckUpdates: StateFlow<Boolean> = settings.autoCheckUpdates
    val rydData: StateFlow<ReturnYouTubeDislikeData?> = playback.rydData
    val appThemeStyle: StateFlow<AppThemeStyle> = settings.appThemeStyle
    val nowPlayingStyle: StateFlow<NowPlayingStyle> = settings.nowPlayingStyle
    val liquidGlassEnabled: StateFlow<Boolean> = settings.liquidGlassEnabled
    val lyricsStyle: StateFlow<LyricsStyle> = settings.lyricsStyle
    val isVideoMode: StateFlow<Boolean> = playback.isVideoMode
    val isVideoLoading: StateFlow<Boolean> = playback.isVideoLoading
    val livePerformances: StateFlow<List<AuraTrack>> = playback.livePerformances
    val isLoadingLivePerformances: StateFlow<Boolean> = playback.isLoadingLivePerformances

    // In-App Updates & Releases
    val currentUpdateRelease: StateFlow<AuraReleaseInfo?> = settings.currentUpdateRelease
    val isUpdateModalVisible: StateFlow<Boolean> = settings.isUpdateModalVisible
    val updateState: StateFlow<UpdateState> = settings.updateState
    val updateDownloadProgress: StateFlow<UpdateDownloadProgress> = settings.updateDownloadProgress
    val updateErrorMessage: StateFlow<String?> = settings.updateErrorMessage

    fun refreshHome() = discovery.refreshHome()
    fun selectChartCountry(countryCode: String) = discovery.selectChartCountry(countryCode)
    fun playCommunityPlaylist(playlist: YtmCommunityPlaylist) = discovery.playCommunityPlaylist(playlist)
    fun playHomeMusicVideo(track: AuraTrack, videoQueue: List<AuraTrack>) = playback.playHomeMusicVideo(track, videoQueue)

    fun popTopDetail() = discovery.popTopDetail()
    fun closeDetail(stackKey: String) = discovery.closeDetail(stackKey)
    fun clearDetailStack() = discovery.clearDetailStack()

    fun openArtistDetail(artist: YtmTopArtist) = discovery.openArtistDetail(artist)
    fun openArtistByName(artistName: String, songTitle: String = "", fallbackArtwork: String = "") =
        discovery.openArtistByName(artistName, songTitle, fallbackArtwork)

    fun loadNowPlayingArtist(artistName: String, songTitle: String, fallbackArtwork: String) =
        playback.loadNowPlayingArtist(artistName, songTitle, fallbackArtwork)

    fun loadPlayerComments(videoId: String, force: Boolean = false) = playback.loadPlayerComments(videoId, force)
    fun loadMorePlayerComments() = playback.loadMorePlayerComments()
    fun selectCommentSort(sort: AuraCommentSort) = playback.selectCommentSort(sort)
    fun toggleCommentReplies(commentId: String) = playback.toggleCommentReplies(commentId)
    fun loadMoreCommentReplies(commentId: String) = playback.loadMoreCommentReplies(commentId)
    fun clearPlayerComments() = playback.clearPlayerComments()

    fun closeArtistDetail() = discovery.closeArtistDetail()
    fun selectUserPlaylist(playlist: AuraPlaylist?) = library.selectUserPlaylist(playlist)
    fun clearSelectedUserPlaylist() = library.clearSelectedUserPlaylist()
    fun dismissImportSuccessModal() = library.dismissImportSuccessModal()

    fun openPlaylistDetail(playlist: YtmCommunityPlaylist) = discovery.openPlaylistDetail(playlist)
    fun openAlbumOrPlaylist(id: String, title: String, author: String, artworkUrl: String) =
        discovery.openAlbumOrPlaylist(id, title, author, artworkUrl)

    fun openAlbum(track: AuraTrack) = discovery.openAlbum(track)
    fun closePlaylistDetail() = discovery.closePlaylistDetail()

    fun openCatalogCategory(
        title: String,
        params: String = "",
        browseId: String = "FEmusic_moods_and_genres_category"
    ) = discovery.openCatalogCategory(title, params, browseId)

    fun closeCatalogDetail() = discovery.closeCatalogDetail()

    fun setActivePlaylistDetail(detail: PlaylistDetail) = library.setActivePlaylistDetail(detail)
    fun setActiveUserPlaylist(playlist: AuraPlaylist) = library.setActiveUserPlaylist(playlist)
    fun clearPlaylistScrollTarget() = library.clearPlaylistScrollTarget()
    fun openCurrentPlaylistScreen(): Boolean = playback.openCurrentPlaylistScreen()

    fun playArtist(artist: YtmTopArtist) = discovery.playArtist(artist)
    fun selectCategory(category: String) = discovery.selectCategory(category)
    fun loadSearchExploreCoverTracks() = discovery.loadSearchExploreCoverTracks()
    fun updateSearchQuery(query: String) = discovery.updateSearchQuery(query)
    fun removeFromRecentlyPlayed(track: AuraTrack) = library.removeFromRecentlyPlayed(track)
    fun setSearchFilter(filter: String) = discovery.setSearchFilter(filter)
    fun playMoodCategory(mood: MoodCategory) = discovery.playMoodCategory(mood)

    fun playTrack(track: AuraTrack, queue: List<AuraTrack>? = null) = playback.playTrack(track, queue)
    fun playSearchTrack(track: AuraTrack, results: List<AuraTrack>) = playback.playSearchTrack(track, results)

    fun toggleFavorite(track: AuraTrack) = library.toggleFavorite(track)
    fun isFavorite(videoId: String): Boolean = library.isFavorite(videoId)
    fun isTrackDownloaded(videoId: String): Boolean = library.isTrackDownloaded(videoId)
    fun downloadTrack(track: AuraTrack) = library.downloadTrack(track)
    fun downloadAllTracks(tracks: List<AuraTrack>) = library.downloadAllTracks(tracks)
    fun pauseDownloads() = library.pauseDownloads()
    fun resumeDownloads() = library.resumeDownloads()
    fun cancelAllDownloads() = library.cancelAllDownloads()
    fun removeDownload(track: AuraTrack) = library.removeDownload(track)

    fun isPlaylistAlreadyImported(url: String): AuraPlaylist? = library.isPlaylistAlreadyImported(url)
    fun importSpotifyPlaylist(url: String) = library.importSpotifyPlaylist(url)
    fun createPlaylist(name: String, description: String = "", coverUrl: String = ""): AuraPlaylist =
        library.createPlaylist(name, description, coverUrl)

    fun addTrackToPlaylist(playlistId: String, track: AuraTrack) = library.addTrackToPlaylist(playlistId, track)
    fun updatePlaylistCover(playlistId: String, newCoverUrl: String) = library.updatePlaylistCover(playlistId, newCoverUrl)
    fun updatePlaylistTitle(playlistId: String, newTitle: String) = library.updatePlaylistTitle(playlistId, newTitle)
    fun deletePlaylist(playlistId: String) = library.deletePlaylist(playlistId)
    fun removeTrackFromPlaylist(playlistId: String, videoId: String) = library.removeTrackFromPlaylist(playlistId, videoId)
    fun isPlaylistSaved(playlistId: String): Boolean = library.isPlaylistSaved(playlistId)
    fun toggleSaveCommunityPlaylist(playlist: YtmCommunityPlaylist) = library.toggleSaveCommunityPlaylist(playlist)
    fun toggleSavePlaylistDetail(detail: PlaylistDetail) = library.toggleSavePlaylistDetail(detail)

    fun adjustLyricsOffset(deltaMs: Int) = playback.adjustLyricsOffset(deltaMs)
    fun toggleRomanizedLyrics() = playback.toggleRomanizedLyrics()

    fun toggleRomanizationLanguage(language: String) = settings.toggleRomanizationLanguage(language)
    fun setRomanizationLanguages(languages: Set<String>) = settings.setRomanizationLanguages(languages)
    fun setMainLyricsProvider(provider: String) = settings.setMainLyricsProvider(provider)
    fun setDefaultLyricsOffsetMs(offsetMs: Int) = settings.setDefaultLyricsOffsetMs(offsetMs)
    fun setAutoDownloadLikedSongs(enabled: Boolean) = settings.setAutoDownloadLikedSongs(enabled)
    fun setRecordListeningHistory(enabled: Boolean) = settings.setRecordListeningHistory(enabled)
    fun clearListeningHistory() = library.clearListeningHistory()
    fun setAutoCheckUpdates(enabled: Boolean) = settings.setAutoCheckUpdates(enabled)
    fun toggleSponsorBlockCategory(category: String) = settings.toggleSponsorBlockCategory(category)
    fun clearAllDownloads() = library.clearAllDownloads()
    fun exportBackupToUri(uri: Uri) = settings.exportBackupToUri(uri)
    fun importBackupFromUri(uri: Uri) = settings.importBackupFromUri(uri)

    fun setAppThemeStyle(style: AppThemeStyle) = settings.setAppThemeStyle(style)
    fun setNowPlayingStyle(style: NowPlayingStyle) = settings.setNowPlayingStyle(style)
    fun setLiquidGlassEnabled(enabled: Boolean) = settings.setLiquidGlassEnabled(enabled)
    fun setLyricsStyle(style: LyricsStyle) = settings.setLyricsStyle(style)
    fun selectEqualizerPreset(preset: EqualizerPreset) = settings.selectEqualizerPreset(preset)

    fun toggleVideoMode() = playback.toggleVideoMode()
    fun setVideoMode(enabled: Boolean) = playback.setVideoMode(enabled)
    fun loadLivePerformances(track: AuraTrack? = AuraPlayerManager.currentTrack.value) =
        playback.loadLivePerformances(track)

    fun playLivePerformance(track: AuraTrack) = playback.playLivePerformance(track)

    fun checkForUpdates(force: Boolean = false) = settings.checkForUpdates(force)
    fun openUpdateModalForRelease(release: AuraReleaseInfo) = settings.openUpdateModalForRelease(release)

    /** Retained as a no-op for MainActivity compatibility after removing the hardcoded update fixture. */
    fun triggerTestUpdateModal() = Unit

    fun dismissUpdateModal() = settings.dismissUpdateModal()
    fun snoozeUpdate(version: String) = settings.snoozeUpdate(version)
    fun downloadAndInstallUpdate(release: AuraReleaseInfo) = settings.downloadAndInstallUpdate(release)
    fun installDownloadedApk(release: AuraReleaseInfo) = settings.installDownloadedApk(release)
}
