package com.auramusic.app.core.extractor

import com.auramusic.app.data.AuraStorageRepository
import com.auramusic.app.domain.model.ArtistDetail
import com.auramusic.app.domain.model.AuraChart
import com.auramusic.app.domain.model.AuraPlaylist
import com.auramusic.app.domain.model.AuraTrack
import com.auramusic.app.domain.model.AuraVideoInfo
import com.auramusic.app.domain.model.MoodBrowseResult
import com.auramusic.app.domain.model.MoodCategory
import com.auramusic.app.domain.model.PlaylistDetail
import com.auramusic.app.domain.model.ResolvedVideoStream
import com.auramusic.app.domain.model.YtmChartSection
import com.auramusic.app.domain.model.YtmCommunityPlaylist
import com.auramusic.app.domain.model.YtmMoodCategorySection
import com.auramusic.app.domain.model.YtmMoodShelf
import com.auramusic.app.domain.model.YtmTopArtist
import okhttp3.OkHttpClient

/**
 * Facade preserving 100% API compatibility while delegating to cohesive extractor modules:
 * - [InnerTubeClient]: OkHttp configuration, InnerTube request context & JSON helpers
 * - [StreamUrlResolver]: Multi-tier audio/video stream extraction, xtags ranking & caching
 * - [SpotifyPlaylistImporter]: Zero-server Spotify public playlist import & lazy resolution
 * - [YtmBrowseParser]: YouTube Music Home, Moods, Charts, Search, Artist, Playlist & Video parsers
 */
object SimpMusicExtractorEngine {
    var contentCountry: String
        get() = InnerTubeClient.contentCountry
        set(value) {
            InnerTubeClient.contentCountry = value
        }

    fun effectiveGl(): String = InnerTubeClient.effectiveGl()

    val httpClient: OkHttpClient
        get() = InnerTubeClient.httpClient

    // --- Stream URL resolution & caching ---

    fun getTrackLoudnessDb(videoId: String): Float? =
        StreamUrlResolver.getTrackLoudnessDb(videoId)

    fun initExtractors() = StreamUrlResolver.initExtractors()

    fun getCachedStream(videoId: String, audioQuality: String = "Auto"): ResolvedStream? =
        StreamUrlResolver.getCachedStream(videoId, audioQuality)

    fun invalidateStream(videoId: String) = StreamUrlResolver.invalidateStream(videoId)

    fun prefetchStream(videoId: String, audioQuality: String = "Auto") =
        StreamUrlResolver.prefetchStream(videoId, audioQuality)

    internal fun audioTrackRank(url: String, preferredLanguage: String = "en"): Int =
        StreamUrlResolver.audioTrackRank(url, preferredLanguage)

    fun clearStreamCache() = StreamUrlResolver.clearStreamCache()

    suspend fun resolveStream(
        videoId: String,
        audioQuality: String = "Auto"
    ): ResolvedStream = StreamUrlResolver.resolveStream(videoId, audioQuality)

    suspend fun resolveVideoStream(
        videoId: String,
        videoQuality: String = "Auto (1080p)",
        audioQuality: String = "Auto"
    ): ResolvedVideoStream? = StreamUrlResolver.resolveVideoStream(videoId, videoQuality, audioQuality)

    // --- Video / audio track matching & search ---

    fun primaryArtistName(artist: String): String =
        YtmBrowseParser.primaryArtistName(artist)

    fun cleanSongTitleForVideoSearch(rawTitle: String, rawArtist: String): String =
        YtmBrowseParser.cleanSongTitleForVideoSearch(rawTitle, rawArtist)

    suspend fun findOfficialMusicVideo(track: AuraTrack): AuraTrack? =
        YtmBrowseParser.findOfficialMusicVideo(track)

    fun isVideoThumbnailUrl(url: String): Boolean =
        YtmBrowseParser.isVideoThumbnailUrl(url)

    suspend fun findOfficialAudioTrack(track: AuraTrack): AuraTrack? =
        YtmBrowseParser.findOfficialAudioTrack(track)

    suspend fun fetchLivePerformancesAndVideos(track: AuraTrack): List<AuraTrack> =
        YtmBrowseParser.fetchLivePerformancesAndVideos(track)

    suspend fun searchYouTubeMusic(
        query: String,
        filter: String = "songs",
        prefetchTopResult: Boolean = true
    ): List<AuraTrack> = YtmBrowseParser.searchYouTubeMusic(query, filter, prefetchTopResult)

    suspend fun fetchRadioQueue(videoId: String, audioOnly: Boolean = true): List<AuraTrack> =
        YtmBrowseParser.fetchRadioQueue(videoId, audioOnly)

    suspend fun fetchVideoInfo(videoId: String): AuraVideoInfo? =
        YtmBrowseParser.fetchVideoInfo(videoId)

    // --- Home, Moods, Charts, Playlists & Artists ---

    suspend fun fetchHomeChartsAndMoods(): Pair<List<YtmChartSection>, List<MoodCategory>> =
        YtmBrowseParser.fetchHomeChartsAndMoods()

    suspend fun fetchYtmMoodBrowse(category: String): MoodBrowseResult =
        YtmBrowseParser.fetchYtmMoodBrowse(category)

    suspend fun fetchMoodCategoryDetails(
        browseId: String = "FEmusic_moods_and_genres_category",
        params: String? = null,
        title: String
    ): List<YtmMoodShelf> = YtmBrowseParser.fetchMoodCategoryDetails(browseId, params, title)

    suspend fun fetchMoodAndGenresCategories(): List<YtmMoodCategorySection> =
        YtmBrowseParser.fetchMoodAndGenresCategories()

    suspend fun fetchHomeChartData(countryCode: String = "US"): AuraChart =
        YtmBrowseParser.fetchHomeChartData(countryCode)

    suspend fun findAlbumBrowseId(albumName: String, artistName: String): String? =
        YtmBrowseParser.findAlbumBrowseId(albumName, artistName)

    suspend fun fetchTopArtists(): List<YtmTopArtist> =
        YtmBrowseParser.fetchTopArtists()

    suspend fun fetchCommunityPlaylists(query: String = "Top Hits 2025"): List<YtmCommunityPlaylist> =
        YtmBrowseParser.fetchCommunityPlaylists(query)

    suspend fun fetchPlaylistTracks(browseId: String): List<AuraTrack> =
        YtmBrowseParser.fetchPlaylistTracks(browseId)

    suspend fun fetchPlaylistDetail(playlist: YtmCommunityPlaylist): PlaylistDetail =
        YtmBrowseParser.fetchPlaylistDetail(playlist)

    suspend fun fetchSearchSuggestions(query: String): List<String> =
        YtmBrowseParser.fetchSearchSuggestions(query)

    suspend fun searchArtists(artistName: String): List<YtmTopArtist> =
        YtmBrowseParser.searchArtists(artistName)

    suspend fun searchArtist(artistName: String): YtmTopArtist? =
        YtmBrowseParser.searchArtist(artistName)

    suspend fun fetchArtistDetail(artist: YtmTopArtist): ArtistDetail =
        YtmBrowseParser.fetchArtistDetail(artist)

    suspend fun fetchArtistByName(
        artistName: String,
        songTitle: String = "",
        fallbackArtwork: String = ""
    ): ArtistDetail = YtmBrowseParser.fetchArtistByName(artistName, songTitle, fallbackArtwork)

    suspend fun resolveArtistBrowseId(artistName: String, songTitle: String): String? =
        YtmBrowseParser.resolveArtistBrowseId(artistName, songTitle)

    // --- Spotify playlist import & cache ---

    suspend fun fetchSpotifyPlaylistDirect(
        playlistUrl: String,
        onProgress: (resolved: Int, total: Int) -> Unit = { _, _ -> }
    ): AuraPlaylist? = SpotifyPlaylistImporter.fetchSpotifyPlaylistDirect(playlistUrl, onProgress)

    fun setStorageRepository(storage: AuraStorageRepository) =
        SpotifyPlaylistImporter.setStorageRepository(storage)

    fun getCachedSpotifyResolvedTrack(title: String, artist: String): AuraTrack? =
        SpotifyPlaylistImporter.getCachedSpotifyResolvedTrack(title, artist)

    fun cacheSpotifyResolvedTrack(title: String, artist: String, track: AuraTrack) =
        SpotifyPlaylistImporter.cacheSpotifyResolvedTrack(title, artist, track)

    suspend fun resolveSpotifyPendingTrack(track: AuraTrack): AuraTrack =
        SpotifyPlaylistImporter.resolveSpotifyPendingTrack(track)

    val DEFAULT_SEED_TRACKS: List<AuraTrack>
        get() = YtmBrowseParser.DEFAULT_SEED_TRACKS
}
