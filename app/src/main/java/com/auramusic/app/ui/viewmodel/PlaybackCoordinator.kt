package com.auramusic.app.ui.viewmodel

import android.app.Application
import android.widget.Toast
import androidx.compose.ui.graphics.Color
import com.auramusic.app.core.comments.YouTubeCommentsRepository
import com.auramusic.app.core.extractor.SimpMusicExtractorEngine
import com.auramusic.app.core.lyrics.SimpMusicLyricsRepository
import com.auramusic.app.core.media.AuraPlayerManager
import com.auramusic.app.core.sponsorblock.SponsorBlockRepository
import com.auramusic.app.data.AuraStorageRepository
import com.auramusic.app.domain.model.ArtistDetail
import com.auramusic.app.domain.model.AuraCommentSort
import com.auramusic.app.domain.model.AuraCommentsPage
import com.auramusic.app.domain.model.AuraPalette
import com.auramusic.app.domain.model.AuraTrack
import com.auramusic.app.domain.model.AuraVideoInfo
import com.auramusic.app.domain.model.AuraYouTubeComment
import com.auramusic.app.domain.model.LyricsData
import com.auramusic.app.domain.model.ReturnYouTubeDislikeData
import com.auramusic.app.ui.theme.extractAuraPaletteFromUrl
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.distinctUntilChangedBy
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

internal class PlaybackCoordinator(
    private val application: Application,
    private val scope: CoroutineScope,
    private val storage: AuraStorageRepository,
    private val library: LibraryCoordinator,
    private val discovery: DiscoveryCoordinator,
    private val settings: SettingsCoordinator
) {
    private val _nowPlayingArtistDetail = MutableStateFlow<ArtistDetail?>(null)
    val nowPlayingArtistDetail: StateFlow<ArtistDetail?> = _nowPlayingArtistDetail.asStateFlow()

    private val _isNowPlayingArtistLoading = MutableStateFlow(false)
    val isNowPlayingArtistLoading: StateFlow<Boolean> = _isNowPlayingArtistLoading.asStateFlow()

    private var nowPlayingArtistJob: Job? = null
    private var nowPlayingArtistKey: String? = null

    private val _nowPlayingVideoInfo = MutableStateFlow<AuraVideoInfo?>(null)
    val nowPlayingVideoInfo: StateFlow<AuraVideoInfo?> = _nowPlayingVideoInfo.asStateFlow()

    private val _playerComments = MutableStateFlow<AuraCommentsPage?>(null)
    val playerComments: StateFlow<AuraCommentsPage?> = _playerComments.asStateFlow()

    private val _areCommentsLoading = MutableStateFlow(false)
    val areCommentsLoading: StateFlow<Boolean> = _areCommentsLoading.asStateFlow()

    private val _commentsError = MutableStateFlow<String?>(null)
    val commentsError: StateFlow<String?> = _commentsError.asStateFlow()
    private var commentsVideoId: String? = null

    private val _palette = MutableStateFlow(AuraPalette())
    val palette: StateFlow<AuraPalette> = _palette.asStateFlow()

    private val _lyrics = MutableStateFlow<LyricsData?>(null)
    val lyrics: StateFlow<LyricsData?> = _lyrics.asStateFlow()

    private val _lyricsOffsetMs = MutableStateFlow(0)
    val lyricsOffsetMs: StateFlow<Int> = _lyricsOffsetMs.asStateFlow()

    private val _showRomanizedLyrics = MutableStateFlow(true)
    val showRomanizedLyrics: StateFlow<Boolean> = _showRomanizedLyrics.asStateFlow()

    private val _rydData = MutableStateFlow<ReturnYouTubeDislikeData?>(null)
    val rydData: StateFlow<ReturnYouTubeDislikeData?> = _rydData.asStateFlow()

    val isVideoMode: StateFlow<Boolean> = AuraPlayerManager.isVideoMode
    val isVideoLoading: StateFlow<Boolean> = AuraPlayerManager.isVideoLoading

    private val _livePerformances = MutableStateFlow<List<AuraTrack>>(emptyList())
    val livePerformances: StateFlow<List<AuraTrack>> = _livePerformances.asStateFlow()

    private val _isLoadingLivePerformances = MutableStateFlow(false)
    val isLoadingLivePerformances: StateFlow<Boolean> = _isLoadingLivePerformances.asStateFlow()

    fun bindPlayerCallbacksAndObserveTrack() {
        AuraPlayerManager.onTrackFinishedCallback = { finishedTrack ->
            storage.recordRecommendationSignal(finishedTrack, "complete")
            discovery.applyLocalRecommendations()
        }
        AuraPlayerManager.onTrackSkippedCallback = { skippedTrack ->
            storage.recordRecommendationSignal(skippedTrack, "skip")
            discovery.applyLocalRecommendations()
        }
        AuraPlayerManager.onVideoUnavailableCallback = { track ->
            Toast.makeText(
                application,
                "No music video found for ${track.title}; continuing with audio",
                Toast.LENGTH_SHORT
            ).show()
        }
        AuraPlayerManager.onTrackResolvedCallback = { resolvedTrack ->
            library.resolvePendingTrackInPlaylists(resolvedTrack)
        }

        // Observe current track changes to fetch Palette, SimpMusic RichSync Lyrics, SponsorBlock, and RYD in parallel
        scope.launch {
            AuraPlayerManager.currentTrack.distinctUntilChangedBy { it?.videoId }.collectLatest { track ->
                if (track == null) return@collectLatest
                // Avoid showing the previous song's artwork color while this track's palette loads.
                _palette.value = AuraPalette(
                    primary = Color(0xFF211F26),
                    secondary = Color(0xFF333038),
                    backgroundTop = Color(0xFF1B1920),
                    backgroundBottom = Color(0xFF0A0B0E)
                )
                library.recordTrackPlay(track, settings.recordListeningHistory.value)
                val canonicalTrack = AuraPlayerManager.originalAudioTrack ?: track
                val canonicalVid = canonicalTrack.videoId
                val savedOffset = storage.getLyricsOffsetMs(canonicalVid).takeIf { it != 0 }
                    ?: storage.getLyricsOffsetMs(track.videoId).takeIf { it != 0 }
                    ?: settings.defaultLyricsOffsetMs.value
                _lyricsOffsetMs.value = savedOffset
                _nowPlayingVideoInfo.value = null

                val cachedOfflinePalette = storage.loadOfflinePalette(canonicalVid)
                    ?: storage.loadOfflinePalette(track.videoId)
                if (cachedOfflinePalette != null) {
                    _palette.value = cachedOfflinePalette
                }
                val paletteDef = async {
                    cachedOfflinePalette ?: extractAuraPaletteFromUrl(track.highResArtworkUrl, application).also { extracted ->
                        if (library.isTrackDownloaded(canonicalVid) || library.isTrackDownloaded(track.videoId)) {
                            storage.saveOfflinePalette(track.videoId, extracted)
                        }
                    }
                }
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
                        val offlineLyrics = storage.loadOfflineLyrics(canonicalVid)
                            ?: storage.loadOfflineLyrics(track.videoId)
                        val cleanTitle = SimpMusicExtractorEngine.cleanSongTitleForVideoSearch(canonicalTrack.title, canonicalTrack.artist)
                        val cleanArtist = SimpMusicExtractorEngine.primaryArtistName(canonicalTrack.artist)
                        offlineLyrics ?: SimpMusicLyricsRepository.getLyrics(
                            videoId = canonicalVid,
                            title = cleanTitle.ifBlank { canonicalTrack.title },
                            artist = cleanArtist.ifBlank { canonicalTrack.artist },
                            durationSeconds = canonicalTrack.durationSeconds,
                            preferredProvider = settings.mainLyricsProvider.value
                        )?.also { fetched ->
                            if (library.isTrackDownloaded(canonicalVid) || library.isTrackDownloaded(track.videoId)) {
                                storage.saveOfflineLyrics(track.videoId, fetched)
                            }
                        }
                    }
                }
                val sponsorDef = async {
                    SponsorBlockRepository.getSponsorSegments(
                        videoId = track.videoId,
                        categories = AuraPlayerManager.sponsorBlockCategories.value
                    )
                }
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
    }

    fun playHomeMusicVideo(track: AuraTrack, videoQueue: List<AuraTrack>) {
        AuraPlayerManager.playStandaloneVideo(track, videoQueue)
    }

    fun playTrack(track: AuraTrack, queue: List<AuraTrack>? = null) {
        val currentCommunity = discovery.selectedPlaylistDetail.value
        if (currentCommunity != null && currentCommunity.tracks.any { it.track.videoId == track.videoId || it.track.id == track.id }) {
            library.setActivePlaylistDetail(currentCommunity)
        }
        val currentUserPl = library.selectedUserPlaylist.value
        if (currentUserPl != null && currentUserPl.tracks.any { it.videoId == track.videoId || it.id == track.id }) {
            library.setActiveUserPlaylist(currentUserPl)
        }
        val isSameCurrentTrack = AuraPlayerManager.currentTrack.value == track
        if (AuraPlayerManager.playVideoForVideoTrack.value &&
            track.musicVideoType?.equals("MUSIC_VIDEO_TYPE_OMV", ignoreCase = true) == true
        ) {
            AuraPlayerManager.playStandaloneVideo(track, queue ?: listOf(track))
        } else {
            AuraPlayerManager.playTrack(track, queue)
        }
        if (isSameCurrentTrack && settings.recordListeningHistory.value) {
            library.recordTrackPlayStatsOnly(track)
        }
    }

    fun playSearchTrack(track: AuraTrack, results: List<AuraTrack>) {
        val isSameCurrentTrack = AuraPlayerManager.currentTrack.value == track
        if (AuraPlayerManager.playVideoForVideoTrack.value &&
            track.musicVideoType?.equals("MUSIC_VIDEO_TYPE_OMV", ignoreCase = true) == true
        ) {
            AuraPlayerManager.playStandaloneVideo(track, listOf(track))
        } else if (AuraPlayerManager.isShuffleEnabled.value) {
            val shuffledQueue = results.filterNot { it.videoId == track.videoId }.shuffled()
            AuraPlayerManager.playTrack(track, listOf(track) + shuffledQueue, allowRadioQueue = true)
        } else {
            // Playing from search query should NOT auto-play other songs from search results unless shuffle is enabled
            AuraPlayerManager.playTrack(track, listOf(track), allowRadioQueue = false)
        }
        if (isSameCurrentTrack && settings.recordListeningHistory.value) {
            library.recordTrackPlayStatsOnly(track)
        }
    }

    fun openCurrentPlaylistScreen(): Boolean {
        val track = AuraPlayerManager.currentTrack.value ?: return false
        val targetTrackId = track.videoId.ifBlank { track.id }
        library.setPlaylistScrollTarget(targetTrackId)

        // 1. Check active Community / YTM PlaylistDetail
        val activeCommunity = library.activePlayingPlaylistDetail.value
        if (activeCommunity != null &&
            (activeCommunity.title.equals(track.album, ignoreCase = true) ||
                activeCommunity.tracks.any { it.track.videoId == track.videoId || it.track.id == track.id })
        ) {
            discovery.pushPlaylistIfNotTop(activeCommunity)
            return true
        }

        // 2. Check active UserPlaylist
        val activeUser = library.activePlayingUserPlaylist.value
        if (activeUser != null &&
            (activeUser.name.equals(track.album, ignoreCase = true) ||
                activeUser.tracks.any { it.videoId == track.videoId || it.id == track.id })
        ) {
            library.selectUserPlaylist(activeUser)
            discovery.clearDetailStack()
            return true
        }

        // 3. Search saved user playlists
        val matchingUserPl = library.playlists.value.firstOrNull { pl ->
            pl.name.equals(track.album, ignoreCase = true) ||
                pl.tracks.any { it.videoId == track.videoId || (track.id.isNotBlank() && it.id == track.id) }
        }
        if (matchingUserPl != null) {
            library.setActiveUserPlaylist(matchingUserPl)
            library.selectUserPlaylist(matchingUserPl)
            discovery.clearDetailStack()
            return true
        }

        // 4. Check currently selected PlaylistDetail if available
        val currentDetail = discovery.selectedPlaylistDetail.value
        if (currentDetail != null &&
            (currentDetail.title.equals(track.album, ignoreCase = true) ||
                currentDetail.tracks.any { it.track.videoId == track.videoId || it.track.id == track.id })
        ) {
            library.setActivePlaylistDetail(currentDetail)
            discovery.pushPlaylistIfNotTop(currentDetail)
            return true
        }

        // 5. If album represents a playlist/album name, load it
        val cleanAlbum = track.album.trim()
        if (cleanAlbum.isNotBlank() &&
            !cleanAlbum.equals("YouTube Music", ignoreCase = true) &&
            !cleanAlbum.matches(Regex("^\\d+:\\d{2}(:\\d{2})?$")) &&
            !cleanAlbum.endsWith("views", ignoreCase = true)
        ) {
            val matchingCommunity = discovery.communityPlaylists.value.firstOrNull {
                it.title.equals(cleanAlbum, ignoreCase = true) ||
                    it.title.contains(cleanAlbum, ignoreCase = true) ||
                    cleanAlbum.contains(it.title, ignoreCase = true)
            }
            if (matchingCommunity != null) {
                discovery.openPlaylistDetail(matchingCommunity)
                return true
            }

            discovery.openAlbum(track)
            return true
        }

        return false
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
        nowPlayingArtistJob = scope.launch {
            val detail = runCatching {
                SimpMusicExtractorEngine.fetchArtistByName(cleanArtist, songTitle, fallbackArtwork)
            }.getOrNull()
            if (nowPlayingArtistKey == key) {
                _nowPlayingArtistDetail.value = detail
                _isNowPlayingArtistLoading.value = false
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
        scope.launch {
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
        scope.launch {
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

    fun selectCommentSort(sort: AuraCommentSort) {
        val page = _playerComments.value ?: return
        val videoId = commentsVideoId ?: return
        if (_areCommentsLoading.value) return
        if (page.activeSort == sort && page.comments.isNotEmpty()) return
        val token = when (sort) {
            AuraCommentSort.TOP -> page.topSortToken
            AuraCommentSort.NEWEST -> page.newestSortToken
        } ?: return

        _commentsError.value = null
        _areCommentsLoading.value = true
        _playerComments.value = page.copy(
            activeSort = sort,
            comments = emptyList(),
            continuation = null
        )
        scope.launch {
            runCatching { YouTubeCommentsRepository.nextPage(token) }
                .onSuccess { sorted ->
                    if (commentsVideoId == videoId) {
                        _playerComments.value = sorted.copy(
                            countText = sorted.countText ?: page.countText,
                            activeSort = sort,
                            topSortToken = sorted.topSortToken ?: page.topSortToken,
                            newestSortToken = sorted.newestSortToken ?: page.newestSortToken
                        )
                    }
                }
                .onFailure { error ->
                    if (commentsVideoId == videoId) {
                        _playerComments.value = page
                        Toast.makeText(
                            application,
                            error.message ?: "Could not sort comments",
                            Toast.LENGTH_SHORT
                        ).show()
                    }
                }
            if (commentsVideoId == videoId) _areCommentsLoading.value = false
        }
    }

    fun toggleCommentReplies(commentId: String) {
        val page = _playerComments.value ?: return
        val videoId = commentsVideoId ?: return
        val target = page.comments.firstOrNull { it.id == commentId } ?: return

        if (target.replies.isNotEmpty()) {
            updateCommentInPage(videoId, commentId) {
                it.copy(isRepliesExpanded = !it.isRepliesExpanded)
            }
            return
        }

        val token = target.repliesContinuation ?: return
        if (target.isLoadingReplies) return
        fetchRepliesForComment(videoId, commentId, token, append = false)
    }

    fun loadMoreCommentReplies(commentId: String) {
        val page = _playerComments.value ?: return
        val videoId = commentsVideoId ?: return
        val target = page.comments.firstOrNull { it.id == commentId } ?: return
        val token = target.repliesNextContinuation ?: return
        if (target.isLoadingReplies) return
        fetchRepliesForComment(videoId, commentId, token, append = true)
    }

    private fun fetchRepliesForComment(
        videoId: String,
        commentId: String,
        token: String,
        append: Boolean
    ) {
        updateCommentInPage(videoId, commentId) {
            it.copy(isLoadingReplies = true, isRepliesExpanded = true)
        }
        scope.launch {
            runCatching { YouTubeCommentsRepository.loadReplies(token) }
                .onSuccess { res ->
                    updateCommentInPage(videoId, commentId) { current ->
                        val merged = if (append) {
                            (current.replies + res.replies).distinctBy { it.id }
                        } else {
                            res.replies
                        }
                        current.copy(
                            replies = merged,
                            repliesNextContinuation = res.continuation,
                            isLoadingReplies = false,
                            isRepliesExpanded = true
                        )
                    }
                }
                .onFailure { error ->
                    updateCommentInPage(videoId, commentId) { current ->
                        current.copy(
                            isLoadingReplies = false,
                            isRepliesExpanded = if (append) current.isRepliesExpanded else false
                        )
                    }
                    if (commentsVideoId == videoId) {
                        Toast.makeText(
                            application,
                            error.message ?: "Could not load replies",
                            Toast.LENGTH_SHORT
                        ).show()
                    }
                }
        }
    }

    private fun updateCommentInPage(
        videoId: String,
        commentId: String,
        transform: (AuraYouTubeComment) -> AuraYouTubeComment
    ) {
        if (commentsVideoId != videoId) return
        val currentPage = _playerComments.value ?: return
        _playerComments.value = currentPage.copy(
            comments = currentPage.comments.map { if (it.id == commentId) transform(it) else it }
        )
    }

    fun clearPlayerComments() {
        commentsVideoId = null
        _playerComments.value = null
        _commentsError.value = null
        _areCommentsLoading.value = false
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

    fun reloadLyricsForProvider(provider: String) {
        val track = AuraPlayerManager.originalAudioTrack ?: AuraPlayerManager.currentTrack.value ?: return
        _lyrics.value = null
        scope.launch {
            val cleanTitle = SimpMusicExtractorEngine.cleanSongTitleForVideoSearch(track.title, track.artist)
            val cleanArtist = SimpMusicExtractorEngine.primaryArtistName(track.artist)
            _lyrics.value = SimpMusicLyricsRepository.getLyrics(
                videoId = track.videoId,
                title = cleanTitle.ifBlank { track.title },
                artist = cleanArtist.ifBlank { track.artist },
                durationSeconds = track.durationSeconds,
                preferredProvider = provider
            )
        }
    }

    fun applyDefaultLyricsOffsetIfZero(offsetMs: Int) {
        val currentTrack = AuraPlayerManager.originalAudioTrack ?: AuraPlayerManager.currentTrack.value
        if (currentTrack != null && storage.getLyricsOffsetMs(currentTrack.videoId) == 0) {
            _lyricsOffsetMs.value = offsetMs
        }
    }

    fun toggleVideoMode() {
        if (AuraPlayerManager.isVideoMode.value) {
            AuraPlayerManager.switchToAudioMode()
            Toast.makeText(application, "Switched to Audio Mode", Toast.LENGTH_SHORT).show()
            return
        }

        val track = AuraPlayerManager.currentTrack.value
        if (track == null) {
            Toast.makeText(application, "No track currently playing", Toast.LENGTH_SHORT).show()
            return
        }

        scope.launch {
            AuraPlayerManager.setVideoLoading(true)
            Toast.makeText(application, "Checking for Official Music Video…", Toast.LENGTH_SHORT).show()

            val matchedShelfVideo = discovery.findMatchedShelfVideo(track)
            val official = SimpMusicExtractorEngine.findOfficialMusicVideo(track)
                ?: matchedShelfVideo?.takeIf {
                    withContext(Dispatchers.IO) { SimpMusicExtractorEngine.resolveVideoStream(it.videoId) != null }
                }
            AuraPlayerManager.setVideoLoading(false)

            if (official != null) {
                AuraPlayerManager.playVideoForTrack(official, track)
                withContext(Dispatchers.Main) {
                    Toast.makeText(application, "Playing Official Video: ${official.title}", Toast.LENGTH_SHORT).show()
                }
            } else {
                withContext(Dispatchers.Main) {
                    Toast.makeText(
                        application,
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
        scope.launch {
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
        Toast.makeText(application, "Playing: ${track.title}", Toast.LENGTH_SHORT).show()
    }
}
