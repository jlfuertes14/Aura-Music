package com.auramusic.app.ui.viewmodel

import android.app.Application
import android.util.Log
import android.widget.Toast
import com.auramusic.app.core.extractor.SimpMusicExtractorEngine
import com.auramusic.app.core.lyrics.SimpMusicLyricsRepository
import com.auramusic.app.core.media.AuraPlayerManager
import com.auramusic.app.core.recommendations.LocalRecommendationEngine
import com.auramusic.app.data.AuraStorageRepository
import com.auramusic.app.domain.model.AuraPlaylist
import com.auramusic.app.domain.model.AuraTrack
import com.auramusic.app.domain.model.ListeningStats
import com.auramusic.app.domain.model.PlaylistDetail
import com.auramusic.app.domain.model.YtmCommunityPlaylist
import com.auramusic.app.domain.model.isPendingResolution
import com.auramusic.app.ui.theme.extractAuraPaletteFromUrl
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

internal class LibraryCoordinator(
    private val application: Application,
    private val scope: CoroutineScope,
    private val storage: AuraStorageRepository,
    private val autoDownloadLikedSongs: StateFlow<Boolean>,
    private val onApplyRecommendations: () -> Unit,
    private val onVisibleRecentlyPlayedResetIfAll: (List<AuraTrack>) -> Unit,
    private val onVisibleUserPlaylistsUpdated: (List<AuraPlaylist>) -> Unit
) {
    private val _spotifyImportStatus = MutableStateFlow<String?>(null)
    val spotifyImportStatus: StateFlow<String?> = _spotifyImportStatus.asStateFlow()

    private val _importedPlaylistSuccess = MutableStateFlow<AuraPlaylist?>(null)
    val importedPlaylistSuccess: StateFlow<AuraPlaylist?> = _importedPlaylistSuccess.asStateFlow()

    private val _selectedUserPlaylist = MutableStateFlow<AuraPlaylist?>(null)
    val selectedUserPlaylist: StateFlow<AuraPlaylist?> = _selectedUserPlaylist.asStateFlow()

    private val _activePlayingPlaylistDetail = MutableStateFlow<PlaylistDetail?>(null)
    val activePlayingPlaylistDetail: StateFlow<PlaylistDetail?> = _activePlayingPlaylistDetail.asStateFlow()

    private val _activePlayingUserPlaylist = MutableStateFlow<AuraPlaylist?>(null)
    val activePlayingUserPlaylist: StateFlow<AuraPlaylist?> = _activePlayingUserPlaylist.asStateFlow()

    private val _playlistScrollTargetTrackId = MutableStateFlow<String?>(null)
    val playlistScrollTargetTrackId: StateFlow<String?> = _playlistScrollTargetTrackId.asStateFlow()

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

    private data class DownloadTask(
        val track: AuraTrack,
        val suppressPerItemToast: Boolean,
        val batchId: Long? = null
    )

    private val _isDownloadPaused = MutableStateFlow(false)
    val isDownloadPaused: StateFlow<Boolean> = _isDownloadPaused.asStateFlow()

    private val downloadQueue = ArrayDeque<DownloadTask>()
    private var downloadWorkerJob: Job? = null
    private val batchRemainingCounts = mutableMapOf<Long, Int>()
    private val batchSucceededCounts = mutableMapOf<Long, Int>()
    private var nextBatchId = 1L

    fun loadInitialFromStorage() {
        _favorites.value = storage.loadFavorites()
        _downloads.value = storage.loadDownloads()
        _playlists.value = storage.loadPlaylists()
        _recentlyPlayed.value = storage.loadRecentlyPlayed()
        _listeningStats.value = storage.loadListeningStats()
    }

    fun reloadAfterBackupRestore() {
        _favorites.value = storage.loadFavorites()
        _playlists.value = storage.loadPlaylists()
        _recentlyPlayed.value = storage.loadRecentlyPlayed()
        _listeningStats.value = storage.loadListeningStats()
    }

    fun recordTrackPlay(track: AuraTrack, recordHistory: Boolean) {
        if (!recordHistory) return
        _listeningStats.value = storage.recordTrackPlay(track)
        storage.recordRecommendationSignal(track, "play")
        val updatedRecent = storage.loadRecentlyPlayed()
        _recentlyPlayed.value = updatedRecent
        onVisibleRecentlyPlayedResetIfAll(updatedRecent)
        onApplyRecommendations()
    }

    fun recordTrackPlayStatsOnly(track: AuraTrack) {
        _listeningStats.value = storage.recordTrackPlay(track)
    }

    fun resolvePendingTrackInPlaylists(resolvedTrack: AuraTrack) {
        scope.launch {
            var changed = false
            val updatedPlaylists = _playlists.value.map { pl ->
                val trackIdx = pl.tracks.indexOfFirst {
                    it.id == resolvedTrack.id ||
                        (it.title.equals(resolvedTrack.title, ignoreCase = true) &&
                            it.artist.equals(resolvedTrack.artist, ignoreCase = true))
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

    fun removeFromRecentlyPlayed(track: AuraTrack) {
        val trackKey = LocalRecommendationEngine.songIdentityKey(track)
        storage.removeRecentlyPlayed(track.videoId, trackKey)
        val updatedRecent = storage.loadRecentlyPlayed()
        _recentlyPlayed.value = updatedRecent
        onVisibleRecentlyPlayedResetIfAll(updatedRecent)
        onApplyRecommendations()
        Toast.makeText(application, "Removed '${track.title}' from recently played", Toast.LENGTH_SHORT).show()
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

    fun setActivePlaylistDetail(detail: PlaylistDetail) {
        _activePlayingPlaylistDetail.value = detail
        _activePlayingUserPlaylist.value = null
    }

    fun setActiveUserPlaylist(playlist: AuraPlaylist) {
        _activePlayingUserPlaylist.value = playlist
        _activePlayingPlaylistDetail.value = null
    }

    fun clearActiveUserAndCommunityPointersForCommunity(detail: PlaylistDetail) {
        _activePlayingPlaylistDetail.value = detail
        _activePlayingUserPlaylist.value = null
    }

    fun setPlaylistScrollTarget(trackId: String?) {
        _playlistScrollTargetTrackId.value = trackId
    }

    fun clearPlaylistScrollTarget() {
        _playlistScrollTargetTrackId.value = null
    }

    fun toggleFavorite(track: AuraTrack) {
        val current = _favorites.value.toMutableList()
        val existingIdx = current.indexOfFirst { it.videoId == track.videoId }
        val newlyLiked = existingIdx < 0
        if (existingIdx >= 0) {
            current.removeAt(existingIdx)
        } else {
            current.add(0, track)
        }
        _favorites.value = current
        storage.saveFavorites(current)
        onApplyRecommendations()
        if (newlyLiked && autoDownloadLikedSongs.value && !isTrackAlreadyDownloaded(track)) {
            downloadTrack(track)
        }
    }

    fun isFavorite(videoId: String): Boolean {
        return _favorites.value.any { it.videoId == videoId }
    }

    fun isTrackDownloaded(videoId: String): Boolean {
        return _downloads.value.any { it.videoId == videoId && it.isDownloaded }
    }

    private fun isTrackAlreadyDownloaded(track: AuraTrack): Boolean {
        return _downloads.value.any {
            it.videoId == track.videoId && it.isDownloaded && it.localFilePath?.let { p -> File(p).exists() } == true
        }
    }

    fun downloadTrack(track: AuraTrack) {
        if (_downloadingIds.value.contains(track.videoId)) return
        if (isTrackAlreadyDownloaded(track)) {
            Toast.makeText(application, "Already downloaded", Toast.LENGTH_SHORT).show()
            return
        }
        enqueueDownloadTasks(listOf(track), suppressPerItemToast = false, batchId = null)
    }

    fun downloadAllTracks(tracks: List<AuraTrack>) {
        if (tracks.isEmpty()) return
        val toDownload = tracks
            .distinctBy { it.videoId }
            .filterNot { isTrackAlreadyDownloaded(it) || _downloadingIds.value.contains(it.videoId) }

        if (toDownload.isEmpty()) {
            val anyInProgress = tracks.any { _downloadingIds.value.contains(it.videoId) }
            if (!anyInProgress) {
                Toast.makeText(application, "All songs are already downloaded", Toast.LENGTH_SHORT).show()
            }
            return
        }

        val batchId = nextBatchId++
        batchRemainingCounts[batchId] = toDownload.size
        batchSucceededCounts[batchId] = 0
        Toast.makeText(application, "Downloading all songs", Toast.LENGTH_SHORT).show()
        enqueueDownloadTasks(toDownload, suppressPerItemToast = true, batchId = batchId)
    }

    private fun enqueueDownloadTasks(
        tracks: List<AuraTrack>,
        suppressPerItemToast: Boolean,
        batchId: Long?
    ) {
        val currentList = _downloads.value.toMutableList()
        val addedIds = mutableSetOf<String>()
        val progressUpdates = mutableMapOf<String, Float>()

        for (track in tracks) {
            if (_downloadingIds.value.contains(track.videoId) || addedIds.contains(track.videoId)) continue
            addedIds.add(track.videoId)
            progressUpdates[track.videoId] = 0f
            if (currentList.none { it.videoId == track.videoId }) {
                currentList.add(0, track.copy(isDownloaded = false))
            }
            downloadQueue.addLast(DownloadTask(track, suppressPerItemToast, batchId))
        }

        if (addedIds.isEmpty()) return
        _downloads.value = currentList
        _downloadingIds.value = _downloadingIds.value + addedIds
        _downloadProgress.value = _downloadProgress.value + progressUpdates

        if (downloadWorkerJob?.isActive != true) {
            startDownloadWorker()
        }
    }

    private fun startDownloadWorker() {
        downloadWorkerJob = scope.launch {
            while (downloadQueue.isNotEmpty()) {
                _isDownloadPaused.first { !it }
                val task = downloadQueue.removeFirst()
                val rawTrack = task.track
                _downloadProgress.value = _downloadProgress.value + (rawTrack.videoId to 0.02f)

                val track = if (rawTrack.isPendingResolution || rawTrack.videoId.startsWith("spotify_pending")) {
                    val resolved = withContext(Dispatchers.IO) {
                        SimpMusicExtractorEngine.resolveSpotifyPendingTrack(rawTrack)
                    }
                    if (!resolved.isPendingResolution && !resolved.videoId.startsWith("spotify_pending")) {
                        AuraPlayerManager.onTrackResolvedCallback?.invoke(resolved)
                        resolved
                    } else {
                        rawTrack
                    }
                } else {
                    rawTrack
                }

                // Cache lyrics and color palette in parallel for this single track, sequentially across the queue
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

                val artworkJob = async {
                    AuraPlayerManager.saveOfflineArtwork(application, track)
                }

                val paletteJob = async {
                    try {
                        val pal = extractAuraPaletteFromUrl(track.highResArtworkUrl, application)
                        storage.saveOfflinePalette(track.videoId, pal)
                    } catch (e: Exception) {
                        Log.w("AuraViewModel", "Failed to cache offline palette for ${track.videoId}: ${e.message}")
                    }
                }

                val file = AuraPlayerManager.downloadTrackOffline(
                    application,
                    track,
                    awaitResume = { _isDownloadPaused.first { !it } }
                ) { _, _, progress ->
                    val clamped = progress.coerceAtLeast(0.02f)
                    _downloadProgress.value = _downloadProgress.value + (rawTrack.videoId to clamped)
                }

                lyricsJob.await()
                paletteJob.await()
                val localArtUri = artworkJob.await()

                val succeeded = file != null && file.exists() && file.length() > 10_000L
                if (succeeded) {
                    _downloadProgress.value = _downloadProgress.value + (rawTrack.videoId to 1f)
                    delay(220L)
                }

                _downloadingIds.value = _downloadingIds.value - rawTrack.videoId
                _downloadProgress.value = _downloadProgress.value - rawTrack.videoId

                if (succeeded) {
                    val downloadedTrack = track.copy(
                        isDownloaded = true,
                        localFilePath = file!!.absolutePath,
                        artworkUrl = localArtUri ?: track.artworkUrl
                    )
                    val updated = listOf(downloadedTrack) +
                        _downloads.value.filterNot { it.videoId == track.videoId || it.videoId == rawTrack.videoId }
                    _downloads.value = updated
                    storage.saveDownloads(updated.filter { it.isDownloaded })
                    if (!task.suppressPerItemToast) {
                        Toast.makeText(application, "Downloaded: ${track.title}", Toast.LENGTH_SHORT).show()
                    }
                } else {
                    _downloads.value = _downloads.value.filterNot {
                        (it.videoId == track.videoId || it.videoId == rawTrack.videoId) && !it.isDownloaded
                    }
                    if (!task.suppressPerItemToast) {
                        Toast.makeText(application, "Download failed for ${track.title}", Toast.LENGTH_SHORT).show()
                    }
                }

                val bId = task.batchId
                if (bId != null) {
                    if (succeeded) {
                        batchSucceededCounts[bId] = (batchSucceededCounts[bId] ?: 0) + 1
                    }
                    val remaining = (batchRemainingCounts[bId] ?: 1) - 1
                    if (remaining <= 0) {
                        val totalSucceeded = batchSucceededCounts.remove(bId) ?: 0
                        batchRemainingCounts.remove(bId)
                        val msg = if (totalSucceeded > 0) {
                            "Downloaded all songs in the playlist"
                        } else {
                            "Failed to download songs in the playlist"
                        }
                        Toast.makeText(application, msg, Toast.LENGTH_SHORT).show()
                    } else {
                        batchRemainingCounts[bId] = remaining
                    }
                }
            }
        }
    }

    fun pauseDownloads() {
        if (_downloadingIds.value.isNotEmpty()) _isDownloadPaused.value = true
    }

    fun resumeDownloads() {
        _isDownloadPaused.value = false
    }

    /** Stops the active download, drops everything still queued and discards unfinished entries. */
    fun cancelAllDownloads() {
        if (_downloadingIds.value.isEmpty() && downloadQueue.isEmpty()) return
        downloadWorkerJob?.cancel()
        downloadWorkerJob = null
        downloadQueue.clear()
        batchRemainingCounts.clear()
        batchSucceededCounts.clear()
        _isDownloadPaused.value = false
        _downloadingIds.value = emptySet()
        _downloadProgress.value = emptyMap()
        _downloads.value = _downloads.value.filter { it.isDownloaded }
        Toast.makeText(application, "Downloads cancelled", Toast.LENGTH_SHORT).show()
    }

    fun removeDownload(track: AuraTrack) {
        val queuedMatches = downloadQueue.filter { it.track.videoId == track.videoId }
        if (queuedMatches.isNotEmpty()) {
            downloadQueue.removeAll { it.track.videoId == track.videoId }
            queuedMatches.forEach { removed ->
                removed.batchId?.let { bId ->
                    val remaining = (batchRemainingCounts[bId] ?: 1) - 1
                    if (remaining <= 0) batchRemainingCounts.remove(bId) else batchRemainingCounts[bId] = remaining
                }
            }
            _downloadingIds.value = _downloadingIds.value - track.videoId
            _downloadProgress.value = _downloadProgress.value - track.videoId
        }
        scope.launch(Dispatchers.IO) {
            try {
                // 1. Delete by localFilePath
                track.localFilePath?.let { p ->
                    val f = File(p)
                    if (f.exists()) f.delete()
                }
                // 2. Delete from dedicated folder + offline artwork
                val dedicated = AuraPlayerManager.getOfflineFile(application, track.videoId)
                if (dedicated.exists()) dedicated.delete()
                val offlineArt = AuraPlayerManager.getOfflineArtworkFile(application, track.videoId)
                if (offlineArt.exists()) offlineArt.delete()
            } catch (e: Exception) {
                Log.w("AuraViewModel", "Error deleting offline file: ${e.message}")
            }

            val updated = _downloads.value.filterNot { it.videoId == track.videoId }
            _downloads.value = updated
            storage.saveDownloads(updated.filter { it.isDownloaded })

            withContext(Dispatchers.Main) {
                Toast.makeText(application, "Removed '${track.title}' from downloads", Toast.LENGTH_SHORT).show()
            }
        }
    }

    fun clearAllDownloads() {
        scope.launch {
            val current = _downloads.value.toList()
            withContext(Dispatchers.IO) {
                current.forEach { track ->
                    track.localFilePath?.let { path ->
                        runCatching { File(path).delete() }
                    }
                    AuraPlayerManager.getOfflineFile(application, track.videoId).let { f ->
                        if (f.exists()) runCatching { f.delete() }
                    }
                    val artFile = File(File(application.filesDir, "aura_offline_artwork"), "${track.videoId}.jpg")
                    if (artFile.exists()) runCatching { artFile.delete() }
                }
            }
            _downloads.value = emptyList()
            storage.saveDownloads(emptyList())
            Toast.makeText(application, "All offline downloads removed", Toast.LENGTH_SHORT).show()
        }
    }

    fun upgradeLegacySpotifyPlaylistsIfNeeded() {
        scope.launch(Dispatchers.IO) {
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
                    onVisibleUserPlaylistsUpdated(updatedList)
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
                application,
                "Playlist '${existing.name}' is already in your library (${existing.tracks.size} tracks)",
                Toast.LENGTH_LONG
            ).show()
            return
        }

        scope.launch {
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
                            application,
                            "Playlist '${alreadyExists.name}' is already in your library",
                            Toast.LENGTH_SHORT
                        ).show()
                    }
                    return@launch
                }
                val updated = listOf(playlist) + _playlists.value.filterNot { it.id == playlist.id }
                _playlists.value = updated
                onVisibleUserPlaylistsUpdated(updated)
                storage.savePlaylists(updated)
                _spotifyImportStatus.value = "Imported '${playlist.name}' (${playlist.tracks.size} tracks)"
                _importedPlaylistSuccess.value = playlist
                withContext(Dispatchers.Main) {
                    Toast.makeText(
                        application,
                        "Imported '${playlist.name}' (${playlist.tracks.size} tracks) successfully!",
                        Toast.LENGTH_LONG
                    ).show()
                }
            } else {
                _spotifyImportStatus.value = "Could not parse public Spotify playlist URL"
                withContext(Dispatchers.Main) {
                    Toast.makeText(
                        application,
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
        Toast.makeText(application, "Created playlist: ${newPlaylist.name}", Toast.LENGTH_SHORT).show()
        return newPlaylist
    }

    fun addTrackToPlaylist(playlistId: String, track: AuraTrack) {
        val target = _playlists.value.firstOrNull { it.id == playlistId } ?: return

        if (target.tracks.any { it.videoId == track.videoId }) {
            Toast.makeText(application, "'${track.title}' is already in ${target.name}", Toast.LENGTH_SHORT).show()
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
        Toast.makeText(application, "Added '${track.title}' to ${target.name}", Toast.LENGTH_SHORT).show()
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
        Toast.makeText(application, "Deleted playlist: ${target?.name ?: "Playlist"}", Toast.LENGTH_SHORT).show()
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
        Toast.makeText(application, "Removed '${track?.title ?: "song"}' from playlist", Toast.LENGTH_SHORT).show()
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
            Toast.makeText(application, "Removed '${playlist.title}' from Library", Toast.LENGTH_SHORT).show()
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
            Toast.makeText(application, "Saved '${playlist.title}' to Library", Toast.LENGTH_SHORT).show()

            // Asynchronously fetch and populate tracks
            scope.launch {
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
            Toast.makeText(application, "Removed '${detail.title}' from Library", Toast.LENGTH_SHORT).show()
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
            Toast.makeText(application, "Saved '${detail.title}' to Library", Toast.LENGTH_SHORT).show()
        }
    }

    fun clearListeningHistory() {
        storage.clearListeningHistory()
        _recentlyPlayed.value = emptyList()
        _listeningStats.value = storage.loadListeningStats()
        onVisibleRecentlyPlayedResetIfAll(emptyList())
        onApplyRecommendations()
        Toast.makeText(application, "Listening history cleared", Toast.LENGTH_SHORT).show()
    }
}
