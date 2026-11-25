package com.auramusic.app.core.media

import android.app.Notification
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Build
import androidx.annotation.OptIn
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.session.LibraryResult
import androidx.media3.session.MediaLibraryService
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaStyleNotificationHelper
import androidx.media3.session.SessionError
import com.auramusic.app.AuraApplication
import com.auramusic.app.MainActivity
import com.auramusic.app.R
import com.auramusic.app.core.extractor.SimpMusicExtractorEngine
import com.auramusic.app.data.AuraStorageRepository
import com.auramusic.app.domain.model.AuraPlaylist
import com.auramusic.app.domain.model.AuraTrack
import com.google.common.collect.ImmutableList
import com.google.common.util.concurrent.Futures
import com.google.common.util.concurrent.ListenableFuture
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.guava.future
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import java.net.URL
import java.util.concurrent.ConcurrentHashMap

/**
 * Pure-Kotlin browse tree ID helpers for Android Auto / Media3 [MediaLibraryService].
 */
object AuraBrowseTree {
    const val ROOT_ID = "aura_root"
    const val LIKED_ID = "aura_liked"
    const val DOWNLOADED_ID = "aura_downloaded"
    const val RECENT_ID = "aura_recent"
    const val PLAYLISTS_ID = "aura_playlists"
    const val PLAYLIST_PREFIX = "aura_playlist:"
    const val SEARCH_ID = "aura_search"

    fun isKnownNodeId(id: String): Boolean =
        id == ROOT_ID ||
            id == LIKED_ID ||
            id == DOWNLOADED_ID ||
            id == RECENT_ID ||
            id == PLAYLISTS_ID ||
            extractPlaylistId(id) != null

    fun playlistNodeId(playlistId: String): String = "$PLAYLIST_PREFIX$playlistId"

    fun extractPlaylistId(nodeId: String): String? =
        nodeId.takeIf { it.startsWith(PLAYLIST_PREFIX) }
            ?.removePrefix(PLAYLIST_PREFIX)
            ?.takeIf { it.isNotBlank() }

    fun trackBrowseId(parentId: String, trackKey: String): String = "$parentId/$trackKey"

    fun parseTrackBrowseId(mediaId: String): Pair<String?, String> {
        val slashIdx = mediaId.indexOf('/')
        if (slashIdx <= 0 || slashIdx >= mediaId.lastIndex) {
            return null to mediaId
        }
        val containerId = mediaId.substring(0, slashIdx)
        val trackKey = mediaId.substring(slashIdx + 1)
        return if (isKnownNodeId(containerId) || containerId == SEARCH_ID) {
            containerId to trackKey
        } else {
            null to mediaId
        }
    }

    fun <T> paginate(items: List<T>, page: Int, pageSize: Int): List<T> {
        if (page < 0 || pageSize <= 0) return items
        val fromIndexLong = page.toLong() * pageSize.toLong()
        if (fromIndexLong >= items.size.toLong()) return emptyList()
        val fromIndex = fromIndexLong.toInt()
        val toIndex = minOf(fromIndexLong + pageSize.toLong(), items.size.toLong()).toInt()
        return items.subList(fromIndex, toIndex)
    }
}

@OptIn(UnstableApi::class)
class AuraMediaService : MediaLibraryService() {
    private var mediaLibrarySession: MediaLibrarySession? = null
    private var playerListener: Player.Listener? = null
    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private var artworkJob: Job? = null
    private var currentArtwork: Bitmap? = null
    private var lastArtworkUrl: String? = null

    private val searchCache = ConcurrentHashMap<String, List<AuraTrack>>()
    private val searchResultTracks = ConcurrentHashMap<String, AuraTrack>()
    @Volatile
    private var lastSearchResults: List<AuraTrack> = emptyList()

    companion object {
        const val NOTIFICATION_ID = 1001
        const val ACTION_PLAY_PAUSE = "com.auramusic.app.ACTION_PLAY_PAUSE"
        const val ACTION_PREVIOUS = "com.auramusic.app.ACTION_PREVIOUS"
        const val ACTION_NEXT = "com.auramusic.app.ACTION_NEXT"
        const val ACTION_UPDATE = "com.auramusic.app.ACTION_UPDATE"

        const val ROOT_ID = AuraBrowseTree.ROOT_ID
        const val LIKED_ID = AuraBrowseTree.LIKED_ID
        const val DOWNLOADED_ID = AuraBrowseTree.DOWNLOADED_ID
        const val RECENT_ID = AuraBrowseTree.RECENT_ID
        const val PLAYLISTS_ID = AuraBrowseTree.PLAYLISTS_ID
    }

    override fun onCreate() {
        super.onCreate()

        AuraPlayerManager.initialize(applicationContext)
        val player = AuraPlayerManager.exoPlayer ?: return

        val sessionActivityIntent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP
        }
        val pendingIntent = PendingIntent.getActivity(
            this,
            0,
            sessionActivityIntent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        val session = MediaLibrarySession.Builder(this, player, AuraLibrarySessionCallback())
            .setSessionActivity(pendingIntent)
            .build()

        mediaLibrarySession = session
        addSession(session)

        playerListener = object : Player.Listener {
            override fun onIsPlayingChanged(isPlaying: Boolean) {
                postNotification()
            }

            override fun onPlaybackStateChanged(playbackState: Int) {
                postNotification()
            }

            override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
                AuraPlayerManager.currentTrack.value?.let { track ->
                    updateArtwork(track.highResArtworkUrl)
                }
                postNotification()
            }
        }
        player.addListener(playerListener!!)

        AuraPlayerManager.currentTrack.value?.let { track ->
            updateArtwork(track.highResArtworkUrl)
        }
        postNotification(startInForegroundRequired = true)
    }

    private inner class AuraLibrarySessionCallback : MediaLibrarySession.Callback {

        override fun onConnect(
            session: MediaSession,
            controller: MediaSession.ControllerInfo
        ): MediaSession.ConnectionResult {
            val baseResult = super.onConnect(session, controller)
            val availablePlayerCommands = baseResult.availablePlayerCommands.buildUpon()
                .add(Player.COMMAND_PLAY_PAUSE)
                .add(Player.COMMAND_SEEK_IN_CURRENT_MEDIA_ITEM)
                .add(Player.COMMAND_SEEK_TO_NEXT)
                .add(Player.COMMAND_SEEK_TO_NEXT_MEDIA_ITEM)
                .add(Player.COMMAND_SEEK_TO_PREVIOUS)
                .add(Player.COMMAND_SEEK_TO_PREVIOUS_MEDIA_ITEM)
                .build()

            return MediaSession.ConnectionResult.AcceptedResultBuilder(session)
                .setAvailableSessionCommands(baseResult.availableSessionCommands)
                .setAvailablePlayerCommands(availablePlayerCommands)
                .build()
        }

        override fun onGetLibraryRoot(
            session: MediaLibrarySession,
            browser: MediaSession.ControllerInfo,
            params: LibraryParams?
        ): ListenableFuture<LibraryResult<MediaItem>> {
            return Futures.immediateFuture(LibraryResult.ofItem(buildRootMediaItem(), params))
        }

        override fun onGetChildren(
            session: MediaLibrarySession,
            browser: MediaSession.ControllerInfo,
            parentId: String,
            page: Int,
            pageSize: Int,
            params: LibraryParams?
        ): ListenableFuture<LibraryResult<ImmutableList<MediaItem>>> =
            serviceScope.future(Dispatchers.IO) {
                val storage = AuraStorageRepository(applicationContext)
                val items: List<MediaItem>? = when (parentId) {
                    AuraBrowseTree.ROOT_ID -> buildRootChildren(storage)
                    AuraBrowseTree.LIKED_ID -> storage.loadFavorites()
                        .map { buildBrowsableTrackItem(it, AuraBrowseTree.LIKED_ID) }
                    AuraBrowseTree.DOWNLOADED_ID -> storage.loadDownloads()
                        .map { buildBrowsableTrackItem(it, AuraBrowseTree.DOWNLOADED_ID) }
                    AuraBrowseTree.RECENT_ID -> storage.loadRecentlyPlayed()
                        .map { buildBrowsableTrackItem(it, AuraBrowseTree.RECENT_ID) }
                    AuraBrowseTree.PLAYLISTS_ID -> storage.loadPlaylists()
                        .map { buildPlaylistFolderItem(it) }
                    else -> {
                        val playlistId = AuraBrowseTree.extractPlaylistId(parentId)
                        if (playlistId != null) {
                            val playlist = storage.loadPlaylists().firstOrNull { it.id == playlistId }
                            playlist?.tracks?.map { buildBrowsableTrackItem(it, parentId) }.orEmpty()
                        } else {
                            null
                        }
                    }
                }
                if (items == null) {
                    LibraryResult.ofError(SessionError.ERROR_BAD_VALUE, params)
                } else {
                    val paged = AuraBrowseTree.paginate(items, page, pageSize)
                    LibraryResult.ofItemList(ImmutableList.copyOf(paged), params)
                }
            }

        override fun onGetItem(
            session: MediaLibrarySession,
            browser: MediaSession.ControllerInfo,
            mediaId: String
        ): ListenableFuture<LibraryResult<MediaItem>> =
            serviceScope.future(Dispatchers.IO) {
                val storage = AuraStorageRepository(applicationContext)
                val item: MediaItem? = when (mediaId) {
                    AuraBrowseTree.ROOT_ID -> buildRootMediaItem()
                    AuraBrowseTree.LIKED_ID,
                    AuraBrowseTree.DOWNLOADED_ID,
                    AuraBrowseTree.RECENT_ID,
                    AuraBrowseTree.PLAYLISTS_ID -> buildRootChildren(storage)
                        .firstOrNull { it.mediaId == mediaId }
                    else -> {
                        val playlistId = AuraBrowseTree.extractPlaylistId(mediaId)
                        if (playlistId != null) {
                            storage.loadPlaylists().firstOrNull { it.id == playlistId }
                                ?.let { buildPlaylistFolderItem(it) }
                        } else {
                            val (containerId, trackKey) = AuraBrowseTree.parseTrackBrowseId(mediaId)
                            findTrackByKey(storage, trackKey)?.let {
                                buildBrowsableTrackItem(it, containerId ?: AuraBrowseTree.RECENT_ID)
                            }
                        }
                    }
                }
                if (item != null) {
                    LibraryResult.ofItem(item, null)
                } else {
                    LibraryResult.ofError(SessionError.ERROR_BAD_VALUE)
                }
            }

        override fun onSearch(
            session: MediaLibrarySession,
            browser: MediaSession.ControllerInfo,
            query: String,
            params: LibraryParams?
        ): ListenableFuture<LibraryResult<Void>> =
            serviceScope.future(Dispatchers.IO) {
                val results = performSearch(query)
                session.notifySearchResultChanged(browser, query, results.size, params)
                LibraryResult.ofVoid(params)
            }

        override fun onGetSearchResult(
            session: MediaLibrarySession,
            browser: MediaSession.ControllerInfo,
            query: String,
            page: Int,
            pageSize: Int,
            params: LibraryParams?
        ): ListenableFuture<LibraryResult<ImmutableList<MediaItem>>> =
            serviceScope.future(Dispatchers.IO) {
                val normalized = query.trim().lowercase()
                val tracks = searchCache[normalized] ?: performSearch(query)
                val items = AuraBrowseTree.paginate(tracks, page, pageSize)
                    .map { buildBrowsableTrackItem(it, AuraBrowseTree.SEARCH_ID) }
                LibraryResult.ofItemList(ImmutableList.copyOf(items), params)
            }

        override fun onPlaybackResumption(
            mediaSession: MediaSession,
            controller: MediaSession.ControllerInfo
        ): ListenableFuture<MediaSession.MediaItemsWithStartPosition> =
            serviceScope.future(Dispatchers.Main) {
                val resumed = AuraPlayerManager.prepareMediaItemsForResumption(applicationContext)
                if (resumed != null && resumed.first.isNotEmpty()) {
                    MediaSession.MediaItemsWithStartPosition(resumed.first, resumed.second, resumed.third)
                } else {
                    MediaSession.MediaItemsWithStartPosition(emptyList(), C.INDEX_UNSET, C.TIME_UNSET)
                }
            }

        override fun onSetMediaItems(
            mediaSession: MediaSession,
            controller: MediaSession.ControllerInfo,
            mediaItems: List<MediaItem>,
            startIndex: Int,
            startPositionMs: Long
        ): ListenableFuture<MediaSession.MediaItemsWithStartPosition> =
            serviceScope.future(Dispatchers.IO) {
                if (mediaItems.isEmpty()) {
                    return@future MediaSession.MediaItemsWithStartPosition(
                        emptyList(),
                        C.INDEX_UNSET,
                        C.TIME_UNSET
                    )
                }
                if (mediaItems.all { it.localConfiguration != null }) {
                    return@future MediaSession.MediaItemsWithStartPosition(
                        mediaItems,
                        startIndex,
                        startPositionMs
                    )
                }
                if (mediaItems.size == 1) {
                    val resolvedQueue = resolveQueueForBrowseItem(mediaItems[0].mediaId)
                    if (resolvedQueue != null && resolvedQueue.first.isNotEmpty()) {
                        val (tracks, resolvedIdx) = resolvedQueue
                        withContext(Dispatchers.Main) {
                            AuraPlayerManager.syncExternalQueueSelection(tracks, resolvedIdx)
                        }
                        return@future MediaSession.MediaItemsWithStartPosition(
                            tracks.map { AuraPlayerManager.buildMediaItemForTrack(it) },
                            resolvedIdx,
                            startPositionMs.coerceAtLeast(0L)
                        )
                    }
                }
                val storage = AuraStorageRepository(applicationContext)
                val resolvedTracks = mediaItems.mapNotNull { item ->
                    val (_, key) = AuraBrowseTree.parseTrackBrowseId(item.mediaId)
                    findTrackByKey(storage, key)
                }
                if (resolvedTracks.isNotEmpty()) {
                    val safeIdx = startIndex.coerceIn(0, resolvedTracks.lastIndex)
                    withContext(Dispatchers.Main) {
                        AuraPlayerManager.syncExternalQueueSelection(resolvedTracks, safeIdx)
                    }
                    MediaSession.MediaItemsWithStartPosition(
                        resolvedTracks.map { AuraPlayerManager.buildMediaItemForTrack(it) },
                        safeIdx,
                        startPositionMs.coerceAtLeast(0L)
                    )
                } else {
                    MediaSession.MediaItemsWithStartPosition(mediaItems, startIndex, startPositionMs)
                }
            }

        override fun onAddMediaItems(
            mediaSession: MediaSession,
            controller: MediaSession.ControllerInfo,
            mediaItems: List<MediaItem>
        ): ListenableFuture<List<MediaItem>> =
            serviceScope.future(Dispatchers.IO) {
                val storage = AuraStorageRepository(applicationContext)
                mediaItems.map { item ->
                    if (item.localConfiguration != null) {
                        item
                    } else {
                        val (_, key) = AuraBrowseTree.parseTrackBrowseId(item.mediaId)
                        findTrackByKey(storage, key)
                            ?.let { AuraPlayerManager.buildMediaItemForTrack(it) }
                            ?: item
                    }
                }
            }
    }

    private fun buildRootMediaItem(): MediaItem {
        val metadata = MediaMetadata.Builder()
            .setTitle("Aura Music")
            .setIsBrowsable(true)
            .setIsPlayable(false)
            .setMediaType(MediaMetadata.MEDIA_TYPE_FOLDER_MIXED)
            .build()
        return MediaItem.Builder()
            .setMediaId(AuraBrowseTree.ROOT_ID)
            .setMediaMetadata(metadata)
            .build()
    }

    private fun buildRootChildren(storage: AuraStorageRepository): List<MediaItem> {
        val favCount = storage.loadFavorites().size
        val downCount = storage.loadDownloads().size
        val recentCount = storage.loadRecentlyPlayed().size
        val playlistCount = storage.loadPlaylists().size
        return listOf(
            buildFolderItem(
                id = AuraBrowseTree.LIKED_ID,
                title = "Liked Songs",
                subtitle = if (favCount > 0) "$favCount songs" else "Your favorites",
                mediaType = MediaMetadata.MEDIA_TYPE_FOLDER_PLAYLISTS
            ),
            buildFolderItem(
                id = AuraBrowseTree.DOWNLOADED_ID,
                title = "Downloaded",
                subtitle = if (downCount > 0) "$downCount offline songs" else "Available offline",
                mediaType = MediaMetadata.MEDIA_TYPE_FOLDER_PLAYLISTS
            ),
            buildFolderItem(
                id = AuraBrowseTree.RECENT_ID,
                title = "Recently Played",
                subtitle = if (recentCount > 0) "$recentCount recent tracks" else "Jump back in",
                mediaType = MediaMetadata.MEDIA_TYPE_FOLDER_PLAYLISTS
            ),
            buildFolderItem(
                id = AuraBrowseTree.PLAYLISTS_ID,
                title = "Playlists",
                subtitle = if (playlistCount > 0) "$playlistCount playlists" else "Your library playlists",
                mediaType = MediaMetadata.MEDIA_TYPE_FOLDER_PLAYLISTS
            )
        )
    }

    private fun buildFolderItem(
        id: String,
        title: String,
        subtitle: String,
        mediaType: Int,
        artworkUrl: String = ""
    ): MediaItem {
        val metadataBuilder = MediaMetadata.Builder()
            .setTitle(title)
            .setSubtitle(subtitle)
            .setIsBrowsable(true)
            .setIsPlayable(false)
            .setMediaType(mediaType)
        if (artworkUrl.isNotBlank()) {
            metadataBuilder.setArtworkUri(Uri.parse(artworkUrl))
        }
        return MediaItem.Builder()
            .setMediaId(id)
            .setMediaMetadata(metadataBuilder.build())
            .build()
    }

    private fun buildPlaylistFolderItem(playlist: AuraPlaylist): MediaItem {
        val cover = playlist.coverUrl.ifBlank {
            playlist.tracks.firstOrNull()?.highResArtworkUrl.orEmpty()
        }
        return buildFolderItem(
            id = AuraBrowseTree.playlistNodeId(playlist.id),
            title = playlist.name.ifBlank { "Playlist" },
            subtitle = "${playlist.tracks.size} tracks",
            mediaType = MediaMetadata.MEDIA_TYPE_PLAYLIST,
            artworkUrl = cover
        )
    }

    private fun buildBrowsableTrackItem(track: AuraTrack, parentId: String): MediaItem {
        val key = trackKeyFor(track)
        val artwork = track.highResArtworkUrl
        val metadataBuilder = MediaMetadata.Builder()
            .setTitle(track.title)
            .setArtist(track.artist)
            .setAlbumTitle(track.album)
            .setDisplayTitle(track.title)
            .setSubtitle(track.artist)
            .setIsBrowsable(false)
            .setIsPlayable(true)
            .setMediaType(MediaMetadata.MEDIA_TYPE_MUSIC)
        if (artwork.isNotBlank()) {
            metadataBuilder.setArtworkUri(Uri.parse(artwork))
        }
        return MediaItem.Builder()
            .setMediaId(AuraBrowseTree.trackBrowseId(parentId, key))
            .setMediaMetadata(metadataBuilder.build())
            .build()
    }

    private fun trackKeyFor(track: AuraTrack): String =
        track.videoId.ifBlank { track.id }

    private fun trackMatchesKey(track: AuraTrack, key: String): Boolean =
        track.videoId == key || track.id == key || trackKeyFor(track) == key

    private fun resolveQueueForBrowseItem(mediaId: String): Pair<List<AuraTrack>, Int>? {
        val (containerId, trackKey) = AuraBrowseTree.parseTrackBrowseId(mediaId)
        val storage = AuraStorageRepository(applicationContext)
        val containerTracks: List<AuraTrack> = when (containerId) {
            AuraBrowseTree.LIKED_ID -> storage.loadFavorites()
            AuraBrowseTree.DOWNLOADED_ID -> storage.loadDownloads()
            AuraBrowseTree.RECENT_ID -> storage.loadRecentlyPlayed()
            AuraBrowseTree.SEARCH_ID -> lastSearchResults.ifEmpty {
                searchResultTracks[trackKey]?.let { listOf(it) }.orEmpty()
            }
            else -> {
                val playlistId = containerId?.let { AuraBrowseTree.extractPlaylistId(it) }
                if (playlistId != null) {
                    storage.loadPlaylists().firstOrNull { it.id == playlistId }?.tracks.orEmpty()
                } else {
                    emptyList()
                }
            }
        }
        if (containerTracks.isNotEmpty()) {
            val matchIdx = containerTracks.indexOfFirst { trackMatchesKey(it, trackKey) }
            if (matchIdx >= 0) {
                return containerTracks to matchIdx
            }
        }
        val fallback = findTrackByKey(storage, trackKey) ?: return null
        return listOf(fallback) to 0
    }

    private fun findTrackByKey(storage: AuraStorageRepository, key: String): AuraTrack? {
        searchResultTracks[key]?.let { return it }
        AuraPlayerManager.queue.value.firstOrNull { trackMatchesKey(it, key) }?.let { return it }
        storage.loadFavorites().firstOrNull { trackMatchesKey(it, key) }?.let { return it }
        storage.loadDownloads().firstOrNull { trackMatchesKey(it, key) }?.let { return it }
        storage.loadRecentlyPlayed().firstOrNull { trackMatchesKey(it, key) }?.let { return it }
        storage.loadPlaylists().forEach { playlist ->
            playlist.tracks.firstOrNull { trackMatchesKey(it, key) }?.let { return it }
        }
        return null
    }

    private suspend fun performSearch(query: String): List<AuraTrack> {
        val trimmed = query.trim()
        if (trimmed.isBlank()) return emptyList()
        val lower = trimmed.lowercase()

        val storage = AuraStorageRepository(applicationContext)
        val localCandidates = buildList {
            addAll(storage.loadFavorites())
            addAll(storage.loadDownloads())
            addAll(storage.loadRecentlyPlayed())
            storage.loadPlaylists().forEach { addAll(it.tracks) }
        }
        val localMatches = localCandidates
            .filter {
                it.title.lowercase().contains(lower) ||
                    it.artist.lowercase().contains(lower) ||
                    it.album.lowercase().contains(lower)
            }
            .distinctBy { trackKeyFor(it) }

        val remoteMatches = runCatching {
            withTimeoutOrNull(6_000L) {
                SimpMusicExtractorEngine.searchYouTubeMusic(
                    query = trimmed,
                    filter = "songs",
                    prefetchTopResult = false
                )
            }.orEmpty()
        }.getOrDefault(emptyList())

        val combined = (localMatches + remoteMatches)
            .distinctBy { trackKeyFor(it) }
            .take(30)

        combined.forEach { track ->
            searchResultTracks[trackKeyFor(track)] = track
            if (track.videoId.isNotBlank()) {
                searchResultTracks[track.videoId] = track
            }
            if (track.id.isNotBlank()) {
                searchResultTracks[track.id] = track
            }
        }
        lastSearchResults = combined
        searchCache[lower] = combined
        return combined
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_PLAY_PAUSE -> AuraPlayerManager.togglePlayPause()
            ACTION_PREVIOUS -> AuraPlayerManager.skipToPrevious()
            ACTION_NEXT -> AuraPlayerManager.skipToNext()
            ACTION_UPDATE -> {
                AuraPlayerManager.currentTrack.value?.let { track ->
                    updateArtwork(track.highResArtworkUrl)
                }
            }
        }
        super.onStartCommand(intent, flags, startId)
        postNotification()
        return START_NOT_STICKY
    }

    private var isForegroundService = false

    override fun onUpdateNotification(session: MediaSession, startInForegroundRequired: Boolean) {
        postNotification(startInForegroundRequired = startInForegroundRequired)
    }

    private fun postNotification(startInForegroundRequired: Boolean = false) {
        val notification = buildNotification() ?: return
        val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val isPlaying = AuraPlayerManager.isPlaying.value
        val hasTrack = AuraPlayerManager.currentTrack.value != null

        val shouldBeForeground = startInForegroundRequired || isPlaying || hasTrack

        try {
            if (shouldBeForeground) {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    startForeground(
                        NOTIFICATION_ID,
                        notification,
                        ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK
                    )
                } else {
                    startForeground(NOTIFICATION_ID, notification)
                }
                isForegroundService = true
            } else {
                if (isForegroundService) {
                    ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_DETACH)
                    isForegroundService = false
                }
                notificationManager.notify(NOTIFICATION_ID, notification)
            }
        } catch (_: Exception) {
            try {
                notificationManager.notify(NOTIFICATION_ID, notification)
            } catch (_: Exception) {}
        }
    }

    private fun buildNotification(): Notification? {
        val session = mediaLibrarySession ?: return null
        val currentTrack = AuraPlayerManager.currentTrack.value
        val isPlaying = AuraPlayerManager.isPlaying.value

        val title = currentTrack?.title?.ifBlank { "Aura" } ?: "Aura"
        val artist = currentTrack?.artist?.ifBlank { "Playing" } ?: "Playing"
        val album = currentTrack?.album ?: ""

        val appIntent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP
        }
        val contentPendingIntent = PendingIntent.getActivity(
            this,
            0,
            appIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val mediaStyle = MediaStyleNotificationHelper.MediaStyle(session)
            .setShowActionsInCompactView(0, 1, 2)

        val builder = NotificationCompat.Builder(this, AuraApplication.CHANNEL_ID)
            .setSmallIcon(R.drawable.aura_app_icon_round)
            .setContentTitle(title)
            .setContentText(artist)
            .setSubText(album)
            .setLargeIcon(currentArtwork)
            .setContentIntent(contentPendingIntent)
            .setStyle(mediaStyle)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setCategory(NotificationCompat.CATEGORY_TRANSPORT)
            .setOngoing(isPlaying)
            .setShowWhen(false)

        builder.addAction(
            NotificationCompat.Action(
                android.R.drawable.ic_media_previous,
                "Previous",
                buildActionPendingIntent(ACTION_PREVIOUS)
            )
        )

        builder.addAction(
            NotificationCompat.Action(
                if (isPlaying) android.R.drawable.ic_media_pause else android.R.drawable.ic_media_play,
                if (isPlaying) "Pause" else "Play",
                buildActionPendingIntent(ACTION_PLAY_PAUSE)
            )
        )

        builder.addAction(
            NotificationCompat.Action(
                android.R.drawable.ic_media_next,
                "Next",
                buildActionPendingIntent(ACTION_NEXT)
            )
        )

        return builder.build()
    }

    private fun buildActionPendingIntent(action: String): PendingIntent {
        val intent = Intent(this, AuraMediaService::class.java).setAction(action)
        return PendingIntent.getService(
            this,
            action.hashCode(),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }

    private fun updateArtwork(artworkUrl: String?) {
        if (artworkUrl.isNullOrBlank() || artworkUrl == lastArtworkUrl) return
        lastArtworkUrl = artworkUrl
        artworkJob?.cancel()
        artworkJob = serviceScope.launch(Dispatchers.IO) {
            try {
                val url = URL(artworkUrl)
                val connection = url.openConnection()
                connection.connectTimeout = 4000
                connection.readTimeout = 4000
                val bitmap = connection.getInputStream().use {
                    BitmapFactory.decodeStream(it)
                }
                if (isActive && bitmap != null) {
                    withContext(Dispatchers.Main) {
                        currentArtwork = bitmap
                        postNotification()
                    }
                }
            } catch (_: Exception) {}
        }
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaLibrarySession? {
        return mediaLibrarySession
    }

    override fun onTaskRemoved(rootIntent: Intent?) {
        val player = mediaLibrarySession?.player
        if (AuraPlayerManager.killServiceOnExit.value) {
            player?.pause()
            stopForeground(STOP_FOREGROUND_REMOVE)
            stopSelf()
            return
        }
        if (player == null || !player.playWhenReady || player.mediaItemCount == 0) {
            stopForeground(STOP_FOREGROUND_REMOVE)
            stopSelf()
        }
    }

    override fun onDestroy() {
        serviceScope.cancel()
        artworkJob?.cancel()
        val player = mediaLibrarySession?.player
        playerListener?.let { player?.removeListener(it) }
        mediaLibrarySession?.run {
            removeSession(this)
            release()
            mediaLibrarySession = null
        }
        super.onDestroy()
    }
}
