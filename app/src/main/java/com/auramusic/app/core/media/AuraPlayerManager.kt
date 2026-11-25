package com.auramusic.app.core.media

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.util.Log
import androidx.annotation.OptIn
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.database.StandaloneDatabaseProvider
import androidx.media3.datasource.DataSource
import androidx.media3.datasource.DataSpec
import androidx.media3.datasource.DefaultDataSource
import androidx.media3.datasource.ResolvingDataSource
import androidx.media3.datasource.cache.CacheDataSource
import androidx.media3.datasource.cache.LeastRecentlyUsedCacheEvictor
import androidx.media3.datasource.cache.NoOpCacheEvictor
import androidx.media3.datasource.cache.SimpleCache
import androidx.media3.datasource.okhttp.OkHttpDataSource
import androidx.media3.exoplayer.DefaultLoadControl
import androidx.media3.exoplayer.DefaultRenderersFactory
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.audio.AudioSink
import androidx.media3.exoplayer.audio.DefaultAudioSink
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.exoplayer.source.MediaSource
import androidx.media3.exoplayer.source.MergingMediaSource
import androidx.media3.exoplayer.source.ProgressiveMediaSource
import com.auramusic.app.core.extractor.ResolvedStream
import com.auramusic.app.core.extractor.SimpMusicExtractorEngine
import com.auramusic.app.data.AuraStorageRepository
import com.auramusic.app.domain.model.AuraTrack
import com.auramusic.app.domain.model.EqualizerPreset
import com.auramusic.app.domain.model.PlaybackMode
import com.auramusic.app.domain.model.ResolvedVideoStream
import com.auramusic.app.domain.model.SponsorSegment
import com.auramusic.app.domain.model.isPendingResolution
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import java.io.File
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.sin

@OptIn(UnstableApi::class)
object AuraPlayerManager {
    private const val TAG = "AuraPlayerManager"

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private var progressJob: Job? = null
    private var sleepTimerJob: Job? = null
    private var radioFetchJob: Job? = null
    private var fadeJob: Job? = null
    private var crossfadeJob: Job? = null

    private var appContext: Context? = null
    private var simpleCache: SimpleCache? = null
    private var resolvingDataSourceFactory: DataSource.Factory? = null
    private var crossfadePlayer: ExoPlayer? = null
    private var crossfadeSecondaryProcessors: Array<androidx.media3.common.audio.AudioProcessor>? = null
    private var crossfadePreparedTrackKey: String? = null
    private var crossfadeSourceTrackKey: String? = null

    @Volatile
    private var isSleepTimerFading: Boolean = false

    val dspManager = AuraAudioDspManager()
    val offlineDownloader = AuraOfflineDownloader()

    private val baseVolume: Float
        get() = dspManager.baseVolume

    private val isAnyFadeActive: Boolean
        get() = fadeJob?.isActive == true || _isCrossfading.value || isSleepTimerFading

    var exoPlayer: ExoPlayer? = null
        private set

    // Reactive Player StateFlows
    private val _currentTrack = MutableStateFlow<AuraTrack?>(null)
    val currentTrack: StateFlow<AuraTrack?> = _currentTrack.asStateFlow()

    private val _queue = MutableStateFlow<List<AuraTrack>>(emptyList())
    val queue: StateFlow<List<AuraTrack>> = _queue.asStateFlow()
    private var originalQueue: List<AuraTrack> = emptyList()

    private val _isPlaying = MutableStateFlow(false)
    val isPlaying: StateFlow<Boolean> = _isPlaying.asStateFlow()

    private val _isBuffering = MutableStateFlow(false)
    val isBuffering: StateFlow<Boolean> = _isBuffering.asStateFlow()

    private val _positionSec = MutableStateFlow(0f)
    val positionSec: StateFlow<Float> = _positionSec.asStateFlow()

    private val _bufferedSec = MutableStateFlow(0f)
    val bufferedSec: StateFlow<Float> = _bufferedSec.asStateFlow()

    private val _durationSec = MutableStateFlow(210f)
    val durationSec: StateFlow<Float> = _durationSec.asStateFlow()

    private val _streamSourceTier = MutableStateFlow("Ultra HD • 320 KBPS")
    val streamSourceTier: StateFlow<String> = _streamSourceTier.asStateFlow()

    private val _sponsorSegments = MutableStateFlow<List<SponsorSegment>>(emptyList())
    val sponsorSegments: StateFlow<List<SponsorSegment>> = _sponsorSegments.asStateFlow()

    private val _lastSkippedSegment = MutableStateFlow<String?>(null)
    val lastSkippedSegment: StateFlow<String?> = _lastSkippedSegment.asStateFlow()

    // Delegated DSP StateFlows (100% API compatibility)
    val activeEqPreset: StateFlow<EqualizerPreset> = dspManager.activeEqPreset
    val equalizerBands: StateFlow<List<Float>> = dspManager.equalizerBands
    val equalizerPreamp: StateFlow<Float> = dspManager.equalizerPreamp
    val activeEqPresetName: StateFlow<String> = dspManager.activeEqPresetName
    val eqEnabled: StateFlow<Boolean> = dspManager.eqEnabled
    val loudnessNormalization: StateFlow<Boolean> = dspManager.loudnessNormalization
    val audioReverbPreset: StateFlow<Int> = dspManager.audioReverbPreset
    val activeAutoEqProfile: StateFlow<AutoEqProfile?> = dspManager.activeAutoEqProfile

    private val _activeAutoEqProfileName = MutableStateFlow<String?>(null)
    val activeAutoEqProfileName: StateFlow<String?> = _activeAutoEqProfileName.asStateFlow()

    private val _activeAutoEqProfileId = MutableStateFlow<String?>(null)
    val activeAutoEqProfileId: StateFlow<String?> = _activeAutoEqProfileId.asStateFlow()

    // Delegated Offline Download StateFlows
    val activeDownloads: StateFlow<Map<String, DownloadItemState>> = offlineDownloader.activeDownloads

    private val _audioQuality = MutableStateFlow("Auto")
    val audioQuality: StateFlow<String> = _audioQuality.asStateFlow()

    private val _videoQuality = MutableStateFlow("Auto (1080p)")
    val videoQuality: StateFlow<String> = _videoQuality.asStateFlow()

    private val _skipSilence = MutableStateFlow(false)
    val skipSilence: StateFlow<Boolean> = _skipSilence.asStateFlow()

    private val _maxCacheSizeMb = MutableStateFlow(1024)
    val maxCacheSizeMb: StateFlow<Int> = _maxCacheSizeMb.asStateFlow()

    private val _sponsorBlockEnabled = MutableStateFlow(true)
    val sponsorBlockEnabled: StateFlow<Boolean> = _sponsorBlockEnabled.asStateFlow()

    private val _sponsorBlockCategories = MutableStateFlow(
        setOf("sponsor", "selfpromo", "interaction", "intro", "outro", "music_offtopic")
    )
    val sponsorBlockCategories: StateFlow<Set<String>> = _sponsorBlockCategories.asStateFlow()

    private val _savePlaybackState = MutableStateFlow(true)
    val savePlaybackState: StateFlow<Boolean> = _savePlaybackState.asStateFlow()

    private val _killServiceOnExit = MutableStateFlow(false)
    val killServiceOnExit: StateFlow<Boolean> = _killServiceOnExit.asStateFlow()

    private val _playVideoForVideoTrack = MutableStateFlow(false)
    val playVideoForVideoTrack: StateFlow<Boolean> = _playVideoForVideoTrack.asStateFlow()

    private val _radioAudioOnly = MutableStateFlow(true)
    val radioAudioOnly: StateFlow<Boolean> = _radioAudioOnly.asStateFlow()

    private val _crossfadeSeconds = MutableStateFlow(4)
    val crossfadeSeconds: StateFlow<Int> = _crossfadeSeconds.asStateFlow()

    private val _isCrossfading = MutableStateFlow(false)
    val isCrossfading: StateFlow<Boolean> = _isCrossfading.asStateFlow()

    private val _skipCrossfadeWithinAlbum = MutableStateFlow(false)
    val skipCrossfadeWithinAlbum: StateFlow<Boolean> = _skipCrossfadeWithinAlbum.asStateFlow()

    // Spotify-style Video Player Mode State
    private val _isVideoMode = MutableStateFlow(false)
    val isVideoMode: StateFlow<Boolean> = _isVideoMode.asStateFlow()

    private val _isVideoLoading = MutableStateFlow(false)
    val isVideoLoading: StateFlow<Boolean> = _isVideoLoading.asStateFlow()

    // Preserved Audio Mode state so switching between video & audio mode never loses original Spotify artwork or queue
    var originalAudioTrack: AuraTrack? = null
        private set
    var originalAudioQueue: List<AuraTrack>? = null
        private set
    var originalAudioIndex: Int = -1
        private set

    private var cacheDataSourceFactory: CacheDataSource.Factory? = null

    private val _sleepTimerRemainingMin = MutableStateFlow<Int?>(null)
    val sleepTimerRemainingMin: StateFlow<Int?> = _sleepTimerRemainingMin.asStateFlow()

    private val _sleepTimerEndOfTrack = MutableStateFlow(false)
    val sleepTimerEndOfTrack: StateFlow<Boolean> = _sleepTimerEndOfTrack.asStateFlow()

    private val _isShuffleEnabled = MutableStateFlow(false)
    val isShuffleEnabled: StateFlow<Boolean> = _isShuffleEnabled.asStateFlow()

    private val _playbackMode = MutableStateFlow(PlaybackMode.NORMAL)
    val playbackMode: StateFlow<PlaybackMode> = _playbackMode.asStateFlow()

    var onTrackFinishedCallback: ((AuraTrack) -> Unit)? = null
    var onTrackSkippedCallback: ((AuraTrack) -> Unit)? = null
    var onVideoUnavailableCallback: ((AuraTrack) -> Unit)? = null

    fun initialize(context: Context) {
        if (exoPlayer != null) return
        val ctx = context.applicationContext
        appContext = ctx

        dspManager.loadFromStorage(ctx)
        val storage = AuraStorageRepository(ctx)
        _audioQuality.value = storage.loadAudioQuality()
        _videoQuality.value = storage.loadVideoQuality()
        _skipSilence.value = storage.loadSkipSilence()
        _crossfadeSeconds.value = storage.loadCrossfadeDuration()
        _maxCacheSizeMb.value = storage.loadMaxCacheSize()
        _sponsorBlockEnabled.value = storage.loadSponsorBlockEnabled()
        _sponsorBlockCategories.value = storage.loadSponsorBlockCategories()
        _savePlaybackState.value = storage.loadSavePlaybackState()
        _killServiceOnExit.value = storage.loadKillServiceOnExit()
        _playVideoForVideoTrack.value = storage.loadPlayVideoForVideoTrack()
        _radioAudioOnly.value = storage.loadRadioAudioOnly()
        if (_savePlaybackState.value) {
            val savedMode = PlaybackMode.entries.firstOrNull { it.name == storage.loadSavedPlaybackMode() }
                ?: PlaybackMode.NORMAL
            _playbackMode.value = savedMode
            _isShuffleEnabled.value = savedMode == PlaybackMode.SHUFFLE
        }

        SimpMusicExtractorEngine.initExtractors()

        // 1. SimpMusic SimpleCache (respect configured cache limit; <= 0 means Unlimited)
        val cacheDir = File(ctx.cacheDir, "aura_media3_cache")
        val dbProvider = StandaloneDatabaseProvider(ctx)
        val configuredCacheMb = _maxCacheSizeMb.value
        val cacheEvictor = if (configuredCacheMb <= 0) {
            NoOpCacheEvictor()
        } else {
            LeastRecentlyUsedCacheEvictor(configuredCacheMb.toLong() * 1024L * 1024L)
        }
        val cache = SimpleCache(cacheDir, cacheEvictor, dbProvider)
        simpleCache = cache

        // 2. OkHttpDataSource + DefaultDataSource (handles both HTTP streams & file:// offline tracks)
        val okHttpFactory = OkHttpDataSource.Factory(SimpMusicExtractorEngine.httpClient)
            .setDefaultRequestProperties(
                mapOf(
                    "User-Agent" to "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/131.0.0.0 Safari/537.36"
                )
            )
        val upstreamFactory = DefaultDataSource.Factory(ctx, okHttpFactory)

        val cacheDataSourceFactory = CacheDataSource.Factory()
            .setCache(cache)
            .setUpstreamDataSourceFactory(upstreamFactory)
            .setFlags(CacheDataSource.FLAG_IGNORE_CACHE_ON_ERROR)
        this.cacheDataSourceFactory = cacheDataSourceFactory

        // 3. SimpMusic ResolvingDataSource: intercepts aura://track/{videoId} on ExoPlayer's loader thread
        val resolvingFactory = ResolvingDataSource.Factory(cacheDataSourceFactory) { dataSpec: DataSpec ->
            val uri = dataSpec.uri
            if (uri.scheme == "aura" && uri.host == "track") {
                val rawId = uri.lastPathSegment ?: throw PlaybackException(
                    "Missing videoId",
                    null,
                    PlaybackException.ERROR_CODE_IO_UNSPECIFIED
                )

                var videoId = rawId

                // Locate track in queue by unique id or videoId
                val trackInQueue = _queue.value.firstOrNull {
                    it.id == rawId || it.videoId == rawId || (rawId.startsWith("spotify_pending") && it.id.endsWith(rawId.substringAfter("spotify_pending")))
                }

                if (trackInQueue != null && !trackInQueue.isPendingResolution) {
                    videoId = trackInQueue.videoId
                } else if (rawId.startsWith("spotify_pending") || trackInQueue?.isPendingResolution == true) {
                    val trackToResolve = trackInQueue ?: _queue.value.firstOrNull { it.isPendingResolution }
                    if (trackToResolve != null) {
                        val resolved = runBlocking(Dispatchers.IO) {
                            SimpMusicExtractorEngine.resolveSpotifyPendingTrack(trackToResolve)
                        }
                        if (resolved.videoId.isNotBlank() && !resolved.videoId.startsWith("spotify_pending")) {
                            videoId = resolved.videoId
                            scope.launch(Dispatchers.Main) {
                                val currentQ = _queue.value.toMutableList()
                                val qIdx = currentQ.indexOfFirst { it.id == trackToResolve.id }
                                if (qIdx >= 0) {
                                    currentQ[qIdx] = resolved
                                    _queue.value = currentQ
                                }
                                val origIdx = originalQueue.indexOfFirst { it.id == trackToResolve.id }
                                if (origIdx >= 0 && origIdx < originalQueue.size) {
                                    val origList = originalQueue.toMutableList()
                                    origList[origIdx] = resolved
                                    originalQueue = origList
                                }
                                if (_currentTrack.value?.id == trackToResolve.id) {
                                    _currentTrack.value = resolved
                                    _durationSec.value = resolved.durationSeconds.toFloat().coerceAtLeast(1f)
                                }
                                onTrackResolvedCallback?.invoke(resolved)
                            }
                        }
                    }
                }

                // Check offline downloaded file first
                val offlineFile = getOfflineFile(ctx, videoId)
                if (offlineFile.exists() && offlineFile.length() > 10_000L) {
                    if (_currentTrack.value?.videoId == videoId) {
                        dspManager.setTrackLoudnessDb(0f)
                        _streamSourceTier.value = "Offline • Lossless"
                        scope.launch(Dispatchers.Main) { applyEqualizerCurve() }
                    }
                    return@Factory dataSpec.buildUpon()
                        .setUri(Uri.fromFile(offlineFile))
                        .setKey("offline_$videoId")
                        .build()
                }

                // Otherwise resolve via SimpMusicExtractorEngine (Tier 0 memory cache -> Tier 1 PipePipe -> Tier 2 NewPipe -> Tier 3 Edge)
                val audioQualityPref = _audioQuality.value
                val resolved = runBlocking(Dispatchers.IO) {
                    SimpMusicExtractorEngine.resolveStream(videoId, audioQualityPref)
                }
                if (_currentTrack.value?.videoId == videoId) {
                    applyResolvedAudioMetadata(videoId, resolved)
                }

                return@Factory dataSpec.buildUpon()
                    .setUri(Uri.parse(resolved.streamUrl))
                    .setKey("audio_${videoId}_$audioQualityPref")
                    .build()
            }

            if (uri.scheme == "aura" && uri.host == "video") {
                val videoId = uri.lastPathSegment ?: throw PlaybackException(
                    "Missing videoId",
                    null,
                    PlaybackException.ERROR_CODE_IO_UNSPECIFIED
                )

                _isVideoLoading.value = true
                val videoQualityPref = _videoQuality.value
                val audioQualityPref = _audioQuality.value
                val resolvedVideo = runBlocking(Dispatchers.IO) {
                    SimpMusicExtractorEngine.resolveVideoStream(videoId, videoQualityPref, audioQualityPref)
                }
                _isVideoLoading.value = false

                if (resolvedVideo != null) {
                    if (_currentTrack.value?.videoId == videoId) {
                        dspManager.setTrackLoudnessDb(SimpMusicExtractorEngine.getTrackLoudnessDb(videoId) ?: 0f)
                        _streamSourceTier.value = "Video • ${resolvedVideo.quality}"
                        scope.launch(Dispatchers.Main) { applyEqualizerCurve() }
                    }
                    return@Factory dataSpec.buildUpon()
                        .setUri(Uri.parse(resolvedVideo.streamUrl))
                        .setKey("video_${videoId}_${videoQualityPref}_$audioQualityPref")
                        .build()
                } else {
                    throw PlaybackException(
                        "Unable to resolve a video stream for $videoId",
                        null,
                        PlaybackException.ERROR_CODE_IO_UNSPECIFIED
                    )
                }
            }
            dataSpec
        }
        this.resolvingDataSourceFactory = resolvingFactory

        val player = buildAudioExoPlayer(
            ctx = ctx,
            resolvingFactory = resolvingFactory,
            handleAudioFocus = true,
            useSecondaryDsp = false
        )
        player.skipSilenceEnabled = _skipSilence.value
        player.repeatMode = when (_playbackMode.value) {
            PlaybackMode.REPEAT_ALL -> Player.REPEAT_MODE_ALL
            PlaybackMode.REPEAT_ONE -> Player.REPEAT_MODE_ONE
            else -> Player.REPEAT_MODE_OFF
        }

        player.addListener(object : Player.Listener {
            override fun onIsPlayingChanged(isPlaying: Boolean) {
                _isPlaying.value = isPlaying
            }

            override fun onPlaybackStateChanged(playbackState: Int) {
                _isBuffering.value = playbackState == Player.STATE_BUFFERING
                if (playbackState == Player.STATE_READY) {
                    val durMs = player.duration
                    if (durMs > 0 && durMs != C.TIME_UNSET) {
                        _durationSec.value = (durMs / 1000f).coerceAtLeast(1f)
                    }
                    dspManager.attachAudioSession(
                        audioSessionId = player.audioSessionId,
                        player = player,
                        isFadeActive = isAnyFadeActive
                    )
                } else if (playbackState == Player.STATE_ENDED) {
                    _currentTrack.value?.let { finished ->
                        onTrackFinishedCallback?.invoke(finished)
                    }
                    if (_sleepTimerEndOfTrack.value) {
                        _sleepTimerEndOfTrack.value = false
                        _sleepTimerRemainingMin.value = null
                        isSleepTimerFading = false
                        cancelCrossfade()
                        player.volume = baseVolume
                        player.pause()
                    } else {
                        skipToNext(recordSkip = false)
                    }
                }
            }

            override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
                fadeJob?.cancel()
                if (!_isCrossfading.value) {
                    cancelCrossfade()
                }
                if (_sleepTimerEndOfTrack.value &&
                    (reason == Player.MEDIA_ITEM_TRANSITION_REASON_AUTO || reason == Player.MEDIA_ITEM_TRANSITION_REASON_REPEAT)
                ) {
                    _sleepTimerEndOfTrack.value = false
                    _sleepTimerRemainingMin.value = null
                    isSleepTimerFading = false
                    cancelCrossfade()
                    player.volume = baseVolume
                    player.pause()
                    player.seekToDefaultPosition(player.currentMediaItemIndex)
                }
                val mediaId = mediaItem?.mediaId ?: return
                val matched = _queue.value.firstOrNull { it.videoId == mediaId || it.id == mediaId }
                if (matched != null) {
                    _currentTrack.value = matched
                    _durationSec.value = matched.durationSeconds.toFloat().coerceAtLeast(1f)
                    val cachedStream = SimpMusicExtractorEngine.getCachedStream(matched.videoId, _audioQuality.value)
                    if (cachedStream != null) {
                        applyResolvedAudioMetadata(matched.videoId, cachedStream)
                    } else {
                        dspManager.updateTrackLoudness(
                            loudnessDb = SimpMusicExtractorEngine.getTrackLoudnessDb(matched.videoId) ?: 0f,
                            player = player,
                            isFadeActive = isAnyFadeActive
                        )
                    }
                    if (matched.isPendingResolution) {
                        scope.launch {
                            val resolved = SimpMusicExtractorEngine.resolveSpotifyPendingTrack(matched)
                            if (!resolved.isPendingResolution) {
                                withContext(Dispatchers.Main) {
                                    val currentQ = _queue.value.toMutableList()
                                    val qIdx = currentQ.indexOfFirst { it.id == matched.id }
                                    if (qIdx >= 0) {
                                        currentQ[qIdx] = resolved
                                        _queue.value = currentQ
                                    }
                                    if (_currentTrack.value?.id == matched.id) {
                                        _currentTrack.value = resolved
                                        _durationSec.value = resolved.durationSeconds.toFloat().coerceAtLeast(1f)
                                    }
                                    onTrackResolvedCallback?.invoke(resolved)
                                }
                                prefetchUpcomingTracks(resolved.videoId)
                            }
                        }
                    } else {
                        prefetchUpcomingTracks(matched.videoId)
                    }
                }
            }

            override fun onPlayerError(error: PlaybackException) {
                Log.e(TAG, "ExoPlayer error: ${error.message}", error)
                if (_isVideoMode.value) {
                    _isVideoLoading.value = false
                    Log.w(TAG, "Video playback failed; restoring the original audio track")
                    (originalAudioTrack ?: _currentTrack.value)?.let { onVideoUnavailableCallback?.invoke(it) }
                    switchToAudioMode()
                    return
                }
                val failedVid = _currentTrack.value?.videoId ?: return
                if (failedVid.startsWith("spotify_pending") || _currentTrack.value?.isPendingResolution == true) {
                    _currentTrack.value?.let { pending ->
                        scope.launch {
                            val resolved = SimpMusicExtractorEngine.resolveSpotifyPendingTrack(pending)
                            if (!resolved.isPendingResolution) {
                                withContext(Dispatchers.Main) {
                                    playTrack(resolved, _queue.value)
                                }
                            } else {
                                withContext(Dispatchers.Main) {
                                    skipToNext(recordSkip = false)
                                }
                            }
                        }
                    }
                    return
                }
                SimpMusicExtractorEngine.invalidateStream(failedVid)
                // Automatic SimpMusic stream recovery
                scope.launch {
                    delay(350)
                    player.prepare()
                    player.play()
                }
            }
        })

        exoPlayer = player
        startProgressTicker()
    }

    var onTrackResolvedCallback: ((AuraTrack) -> Unit)? = null
    var activeQueueTag: String? = null
        private set

    fun playTrack(
        track: AuraTrack,
        newQueue: List<AuraTrack>? = null,
        allowRadioQueue: Boolean = _isShuffleEnabled.value && newQueue == null,
        queueTag: String? = null
    ) {
        val player = exoPlayer ?: return
        radioFetchJob?.cancel()
        activeQueueTag = queueTag

        // Handle Lazy Resolution for Spotify tracks: Instant UI response + background YouTube match
        if (track.isPendingResolution) {
            _currentTrack.value = track
            _isBuffering.value = true
            scope.launch {
                val resolved = SimpMusicExtractorEngine.resolveSpotifyPendingTrack(track)
                val updatedQ = (newQueue ?: _queue.value).map { if (it.id == track.id) resolved else it }
                withContext(Dispatchers.Main) {
                    playTrack(resolved, updatedQ, allowRadioQueue, queueTag)
                    onTrackResolvedCallback?.invoke(resolved)
                }
            }
            return
        }

        // Clean slate for new audio playback
        originalAudioTrack = null
        originalAudioQueue = null
        originalAudioIndex = -1
        _isVideoMode.value = false

        val rawNewQueue = newQueue?.map(::withOfflineArtwork)
        val effectiveTrack = withOfflineArtwork(track)
        val dedupedNewQueue = rawNewQueue?.distinctBy { if (it.id.isNotBlank()) it.id else it.videoId }
        if (!dedupedNewQueue.isNullOrEmpty()) {
            originalQueue = dedupedNewQueue
        } else if (originalQueue.isEmpty()) {
            originalQueue = _queue.value
        }

        val baseQueue = when {
            !dedupedNewQueue.isNullOrEmpty() -> dedupedNewQueue
            _queue.value.any { it.id == effectiveTrack.id || it.videoId == effectiveTrack.videoId } -> _queue.value.map(::withOfflineArtwork)
            else -> listOf(effectiveTrack) + _queue.value
        }

        val updatedQueue = if (_isShuffleEnabled.value) {
            val others = baseQueue.filterNot { it.id == effectiveTrack.id || it.videoId == effectiveTrack.videoId }.shuffled()
            listOf(effectiveTrack) + others
        } else {
            val idx = baseQueue.indexOfFirst { it.id == effectiveTrack.id || it.videoId == effectiveTrack.videoId }
            if (idx >= 0) {
                baseQueue.subList(idx, baseQueue.size) + baseQueue.subList(0, idx)
            } else {
                listOf(effectiveTrack) + baseQueue
            }
        }

        _queue.value = updatedQueue
        _currentTrack.value = track
        _positionSec.value = 0f
        _bufferedSec.value = 0f
        _durationSec.value = track.durationSeconds.toFloat().coerceAtLeast(1f)
        _isBuffering.value = true

        val mediaItems = updatedQueue.map { item -> buildMediaItem(item) }
        val startIndex = updatedQueue.indexOfFirst { it.id == track.id || it.videoId == track.videoId }.coerceAtLeast(0)

        fadeJob?.cancel()
        cancelCrossfade()
        isSleepTimerFading = false
        player.volume = baseVolume
        player.shuffleModeEnabled = false // Transparent explicit order
        player.setMediaItems(mediaItems, startIndex, 0L)
        player.prepare()
        player.play()

        // Start MediaSessionService to keep media session active and system notification connected
        appContext?.let { ctx ->
            try {
                val serviceIntent = Intent(ctx, AuraMediaService::class.java)
                ctx.startService(serviceIntent)
            } catch (e: Exception) {
                Log.w(TAG, "Service start warning: ${e.message}")
            }
        }

        // Proactively pre-warm the next track & fetch radio queue only if radio auto-play is permitted
        prefetchUpcomingTracks(track.videoId)
        if (allowRadioQueue && updatedQueue.size <= 3) {
            radioFetchJob = scope.launch {
                val radio = SimpMusicExtractorEngine.fetchRadioQueue(track.videoId, _radioAudioOnly.value)
                if (radio.isNotEmpty()) {
                    val existingIds = _queue.value.map { it.videoId }.toSet()
                    val fresh = radio.filterNot { existingIds.contains(it.videoId) }
                    if (fresh.isNotEmpty()) {
                        _queue.value = _queue.value + fresh
                        player.addMediaItems(fresh.map { buildMediaItem(it) })
                        fresh.firstOrNull()?.let { SimpMusicExtractorEngine.prefetchStream(it.videoId) }
                    }
                }
            }
        }
    }

    fun enqueueTrack(track: AuraTrack) {
        if (_queue.value.none { it.videoId == track.videoId }) {
            _queue.value = _queue.value + track
            originalQueue = originalQueue + track
            exoPlayer?.addMediaItem(buildMediaItem(track))
        }
    }

    fun syncFavoriteTrack(track: AuraTrack, isLiked: Boolean) {
        val currentList = _queue.value.toMutableList()
        val curTrack = _currentTrack.value
        val curIdx = curTrack?.let { cur -> currentList.indexOfFirst { it.videoId == cur.videoId } } ?: -1

        if (activeQueueTag == "favorites" || currentList.any { it.videoId == track.videoId } || isLiked) {
            if (isLiked) {
                if (currentList.none { it.videoId == track.videoId }) {
                    val insertIdx = if (curIdx >= 0 && curIdx < currentList.size) curIdx + 1 else currentList.size
                    currentList.add(insertIdx, track)
                    _queue.value = currentList
                    originalQueue = originalQueue + track
                    val player = exoPlayer
                    if (player != null && player.playbackState != Player.STATE_IDLE) {
                        syncUpcomingItemsInPlayer(currentList.drop(curIdx + 1))
                    }
                }
            } else {
                val removeIdx = currentList.indexOfFirst { it.videoId == track.videoId }
                if (removeIdx > curIdx) {
                    currentList.removeAt(removeIdx)
                    _queue.value = currentList
                    originalQueue = originalQueue.filterNot { it.videoId == track.videoId }
                    val player = exoPlayer
                    if (player != null && player.playbackState != Player.STATE_IDLE) {
                        syncUpcomingItemsInPlayer(currentList.drop(curIdx + 1))
                    }
                }
            }
        }
    }

    fun playNext(track: AuraTrack) {
        val current = _queue.value.toMutableList()
        val curIndex = exoPlayer?.currentMediaItemIndex ?: 0
        current.removeAll { it.videoId == track.videoId }
        val insertIndex = (curIndex + 1).coerceAtMost(current.size)
        current.add(insertIndex, track)
        _queue.value = current
        exoPlayer?.addMediaItem(insertIndex, buildMediaItem(track))
    }

    fun pauseWithFadeOut(onComplete: (() -> Unit)? = null) {
        val player = exoPlayer ?: return
        if (!player.isPlaying) return
        cancelCrossfade()
        fadeJob?.cancel()
        fadeJob = scope.launch {
            val startVol = player.volume
            val steps = 8
            val stepDelayMs = 25L // 200ms smooth fade out
            for (i in steps downTo 0) {
                if (!isActive) break
                player.volume = (startVol * (i / steps.toFloat())).coerceIn(0f, 1f)
                delay(stepDelayMs)
            }
            player.pause()
            player.volume = startVol
            onComplete?.invoke()
        }
    }

    fun playWithFadeIn() {
        val player = exoPlayer ?: return
        fadeJob?.cancel()
        val targetVol = baseVolume
        if (player.playbackState == Player.STATE_IDLE) {
            player.prepare()
        }
        player.volume = 0f
        player.play()
        fadeJob = scope.launch {
            val steps = 8
            val stepDelayMs = 25L // 200ms smooth fade in
            for (i in 1..steps) {
                if (!isActive) break
                player.volume = (targetVol * (i / steps.toFloat())).coerceIn(0f, 1f)
                delay(stepDelayMs)
            }
            player.volume = targetVol
        }
    }

    fun togglePlayPause() {
        val player = exoPlayer ?: return
        if (player.isPlaying) {
            pauseWithFadeOut()
        } else {
            playWithFadeIn()
        }
    }

    fun cyclePlaybackMode(): PlaybackMode {
        val nextMode = when (_playbackMode.value) {
            PlaybackMode.NORMAL -> PlaybackMode.SHUFFLE
            PlaybackMode.SHUFFLE -> PlaybackMode.REPEAT_ALL
            PlaybackMode.REPEAT_ALL -> PlaybackMode.REPEAT_ONE
            PlaybackMode.REPEAT_ONE -> PlaybackMode.NORMAL
        }
        _playbackMode.value = nextMode
        exoPlayer?.let { player ->
            when (nextMode) {
                PlaybackMode.NORMAL -> {
                    player.shuffleModeEnabled = false
                    player.repeatMode = Player.REPEAT_MODE_OFF
                    _isShuffleEnabled.value = false
                    restoreOriginalQueue()
                }
                PlaybackMode.SHUFFLE -> {
                    player.shuffleModeEnabled = false
                    player.repeatMode = Player.REPEAT_MODE_OFF
                    _isShuffleEnabled.value = true
                    shuffleUpcomingQueue()
                }
                PlaybackMode.REPEAT_ALL -> {
                    player.shuffleModeEnabled = false
                    player.repeatMode = Player.REPEAT_MODE_ALL
                    _isShuffleEnabled.value = false
                    restoreOriginalQueue()
                }
                PlaybackMode.REPEAT_ONE -> {
                    player.shuffleModeEnabled = false
                    player.repeatMode = Player.REPEAT_MODE_ONE
                    _isShuffleEnabled.value = false
                }
            }
        }
        if (_savePlaybackState.value) {
            appContext?.let { AuraStorageRepository(it).saveSavedPlaybackMode(nextMode.name) }
        }
        return nextMode
    }

    fun toggleShuffle() {
        cyclePlaybackMode()
    }

    fun shuffleUpcomingQueue() {
        val cur = _currentTrack.value ?: return
        val list = _queue.value
        if (list.size <= 1) {
            // Queue only has 1 song (e.g. played single search track), and user activated Shuffle mode!
            // Dynamically fetch radio queue so playback auto-plays into similar tracks
            scope.launch {
                val radio = SimpMusicExtractorEngine.fetchRadioQueue(cur.videoId, _radioAudioOnly.value)
                if (radio.isNotEmpty()) {
                    val existingIds = _queue.value.map { it.videoId }.toSet()
                    val fresh = radio.filterNot { existingIds.contains(it.videoId) }
                    if (fresh.isNotEmpty()) {
                        _queue.value = _queue.value + fresh
                        syncUpcomingItemsInPlayer(fresh)
                        fresh.firstOrNull()?.let { SimpMusicExtractorEngine.prefetchStream(it.videoId) }
                    }
                }
            }
            return
        }
        if (originalQueue.isEmpty() || originalQueue.size != list.size) {
            originalQueue = list
        }
        val curIdx = list.indexOfFirst { it.id == cur.id || it.videoId == cur.videoId }
        val before = if (curIdx >= 0) list.take(curIdx + 1) else listOf(cur)
        val after = if (curIdx >= 0) list.drop(curIdx + 1).shuffled() else list.filterNot { it.id == cur.id || it.videoId == cur.videoId }.shuffled()
        _queue.value = before + after
        syncUpcomingItemsInPlayer(after)
    }

    fun restoreOriginalQueue() {
        if (originalQueue.isEmpty()) return
        val cur = _currentTrack.value ?: return
        val origIdx = originalQueue.indexOfFirst { it.id == cur.id || it.videoId == cur.videoId }
        if (origIdx >= 0) {
            val before = originalQueue.take(origIdx + 1)
            val after = originalQueue.drop(origIdx + 1)
            _queue.value = before + after
            syncUpcomingItemsInPlayer(after)
        }
    }

    fun updateUpcomingQueue(newUpcomingList: List<AuraTrack>) {
        val cur = _currentTrack.value ?: return
        val list = _queue.value
        val curIdx = list.indexOfFirst { it.id == cur.id || it.videoId == cur.videoId }
        val before = if (curIdx >= 0) list.take(curIdx + 1) else listOf(cur)
        val updated = before + newUpcomingList
        _queue.value = updated
        syncUpcomingItemsInPlayer(newUpcomingList)
    }

    /**
     * Removes a non-active track at [index] from the queue and syncs ExoPlayer's playlist.
     */
    fun removeQueueItem(index: Int) {
        val list = _queue.value
        if (index !in list.indices || list.size <= 1) return
        val cur = _currentTrack.value
        val curIdx = cur?.let { c ->
            list.indexOfFirst { it.id == c.id && it.videoId == c.videoId }
                .takeIf { it >= 0 }
                ?: list.indexOfFirst { it.videoId == c.videoId }
        } ?: -1
        if (index == curIdx) return

        val mutable = list.toMutableList()
        val removed = mutable.removeAt(index)
        _queue.value = mutable
        originalQueue = originalQueue.filterNot { it.id == removed.id && it.videoId == removed.videoId }

        if (!_isVideoMode.value) {
            val player = exoPlayer ?: return
            try {
                if (player.mediaItemCount == list.size && index in 0 until player.mediaItemCount) {
                    player.removeMediaItem(index)
                } else if (curIdx >= 0 && index > curIdx) {
                    syncUpcomingItemsInPlayer(mutable.drop(curIdx + 1))
                }
            } catch (e: Exception) {
                Log.w(TAG, "Non-critical error removing queue item from player: ${e.message}")
            }
        }
    }

    /**
     * Reorders a queue item from [fromIndex] to [toIndex] without interrupting active playback.
     */
    fun moveQueueItem(fromIndex: Int, toIndex: Int) {
        val list = _queue.value
        if (fromIndex == toIndex || fromIndex !in list.indices || toIndex !in list.indices) return
        val mutable = list.toMutableList()
        val moved = mutable.removeAt(fromIndex)
        mutable.add(toIndex, moved)
        _queue.value = mutable
        if (!_isShuffleEnabled.value && originalQueue.size == list.size) {
            originalQueue = mutable
        }

        if (!_isVideoMode.value) {
            val player = exoPlayer ?: return
            try {
                if (player.mediaItemCount == list.size &&
                    fromIndex in 0 until player.mediaItemCount &&
                    toIndex in 0 until player.mediaItemCount
                ) {
                    player.moveMediaItem(fromIndex, toIndex)
                } else {
                    val cur = _currentTrack.value
                    val curIdx = cur?.let { c ->
                        mutable.indexOfFirst { it.id == c.id && it.videoId == c.videoId }
                            .takeIf { it >= 0 }
                            ?: mutable.indexOfFirst { it.videoId == c.videoId }
                    } ?: -1
                    if (curIdx >= 0) {
                        syncUpcomingItemsInPlayer(mutable.drop(curIdx + 1))
                    }
                }
            } catch (e: Exception) {
                Log.w(TAG, "Non-critical error moving queue item in player: ${e.message}")
            }
        }
    }

    /**
     * Seamlessly updates upcoming items in ExoPlayer timeline WITHOUT calling setMediaItems.
     * Calling setMediaItems would re-initialize the media source, causing playback to pause,
     * re-buffer, and glitch the slider/play-pause icon.
     */
    private fun syncUpcomingItemsInPlayer(newUpcomingList: List<AuraTrack>) {
        val player = exoPlayer ?: return
        try {
            val playerCurIdx = player.currentMediaItemIndex
            val totalItems = player.mediaItemCount
            // Safely remove only upcoming items that follow the active playing track
            if (totalItems > playerCurIdx + 1) {
                player.removeMediaItems(playerCurIdx + 1, totalItems)
            }
            // Append newly reordered upcoming items to the end of the timeline
            val newMediaItems = newUpcomingList.map { buildMediaItem(it) }
            if (newMediaItems.isNotEmpty()) {
                player.addMediaItems(newMediaItems)
            }
        } catch (e: Exception) {
            Log.w(TAG, "Non-critical error syncing upcoming items in player: ${e.message}")
        }
    }

    fun seekToSeconds(seconds: Float) {
        val player = exoPlayer ?: return
        fadeJob?.cancel()
        cancelCrossfade()
        isSleepTimerFading = false
        player.volume = baseVolume
        val targetMs = (seconds.coerceAtLeast(0f) * 1000f).toLong()
        player.seekTo(targetMs)
        _positionSec.value = seconds.coerceAtLeast(0f)
    }

    fun skipToNext(recordSkip: Boolean = true) {
        val player = exoPlayer ?: return
        if (recordSkip) _currentTrack.value?.let { track ->
            val duration = track.durationSeconds.coerceAtLeast(1)
            if (_positionSec.value < duration * 0.65f) onTrackSkippedCallback?.invoke(track)
        }
        fadeJob?.cancel()
        cancelCrossfade()
        isSleepTimerFading = false
        player.volume = baseVolume
        if (_isVideoMode.value) {
            if (originalAudioTrack != null) {
                advanceSongVideoQueue()
            } else {
                val list = _queue.value
                val idx = list.indexOfFirst { it.videoId == _currentTrack.value?.videoId }
                val nextVideo = list.getOrNull(idx + 1)
                    ?: list.firstOrNull().takeIf { list.isNotEmpty() && player.repeatMode == Player.REPEAT_MODE_ALL }
                if (nextVideo != null) playStandaloneVideo(nextVideo, list)
            }
            return
        }
        if (player.hasNextMediaItem()) {
            player.seekToNextMediaItem()
            player.play()
        } else {
            val list = _queue.value
            val cur = _currentTrack.value
            val idx = list.indexOfFirst { it.id == cur?.id || it.videoId == cur?.videoId }
            if (idx in 0 until list.lastIndex) {
                playTrack(list[idx + 1], list, allowRadioQueue = _isShuffleEnabled.value)
            } else if (list.isNotEmpty() && player.repeatMode == Player.REPEAT_MODE_ALL) {
                playTrack(list.first(), list, allowRadioQueue = _isShuffleEnabled.value)
            }
        }
    }

    fun skipToPrevious() {
        val player = exoPlayer ?: return
        fadeJob?.cancel()
        cancelCrossfade()
        isSleepTimerFading = false
        player.volume = baseVolume
        if (_positionSec.value > 4f) {
            seekToSeconds(0f)
            return
        }
        if (_isVideoMode.value && originalAudioTrack != null) {
            val list = originalAudioQueue ?: _queue.value
            val index = list.indexOfFirst { it.id == originalAudioTrack?.id || it.videoId == originalAudioTrack?.videoId }
            val previousAudio = list.getOrNull(index - 1) ?: run {
                seekToSeconds(0f)
                return
            }
            scope.launch {
                _isVideoLoading.value = true
                val video = withContext(Dispatchers.IO) {
                    SimpMusicExtractorEngine.findOfficialMusicVideo(previousAudio)
                }
                _isVideoLoading.value = false
                if (video != null) {
                    originalAudioTrack = previousAudio
                    originalAudioIndex = list.indexOfFirst { it.id == previousAudio.id || it.videoId == previousAudio.videoId }
                    playVideoForTrack(video, previousAudio)
                } else {
                    onVideoUnavailableCallback?.invoke(previousAudio)
                    originalAudioTrack = null
                    originalAudioQueue = null
                    originalAudioIndex = -1
                    _isVideoMode.value = false
                    playTrack(previousAudio, list, allowRadioQueue = false)
                }
            }
        } else if (player.hasPreviousMediaItem()) {
            player.seekToPreviousMediaItem()
            player.play()
        } else {
            val list = _queue.value
            val cur = _currentTrack.value
            val idx = list.indexOfFirst { it.id == cur?.id || it.videoId == cur?.videoId }
            if (_isVideoMode.value) {
                list.getOrNull(idx - 1)?.let { playStandaloneVideo(it, list) } ?: seekToSeconds(0f)
                return
            }
            if (idx > 0) {
                playTrack(list[idx - 1], list, allowRadioQueue = _isShuffleEnabled.value)
            } else {
                seekToSeconds(0f)
            }
        }
    }

    /**
     * Seeks directly to [index] inside the current [_queue] without rotating or reshuffling the
     * queue order, keeping cover-art swipe pagers aligned with the exact swiped track and
     * preserving previous tracks in the queue.
     */
    fun seekToQueueIndex(index: Int) {
        val player = exoPlayer ?: return
        val list = _queue.value
        if (index !in list.indices) return
        val target = list[index]

        fadeJob?.cancel()
        cancelCrossfade()
        isSleepTimerFading = false
        player.volume = baseVolume

        if (_isVideoMode.value) {
            if (originalAudioTrack != null) {
                originalAudioTrack = null
                originalAudioQueue = null
                originalAudioIndex = -1
                _isVideoMode.value = false
            } else {
                playStandaloneVideo(target, list)
                return
            }
        }

        val mediaIndex = (0 until player.mediaItemCount).firstOrNull { idx ->
            val mid = player.getMediaItemAt(idx).mediaId
            mid == target.videoId || mid == target.id
        } ?: index.takeIf { it in 0 until player.mediaItemCount }

        if (mediaIndex != null) {
            _currentTrack.value = target
            _positionSec.value = 0f
            _bufferedSec.value = 0f
            _durationSec.value = target.durationSeconds.toFloat().coerceAtLeast(1f)
            _isBuffering.value = true
            player.seekToDefaultPosition(mediaIndex)
            player.play()
            prefetchUpcomingTracks(target.videoId)
        } else {
            playTrack(target, list, allowRadioQueue = false)
        }
    }

    fun setSponsorSegments(segments: List<SponsorSegment>) {
        _sponsorSegments.value = segments
    }

    fun setSponsorBlockEnabled(enabled: Boolean) {
        _sponsorBlockEnabled.value = enabled
        appContext?.let { AuraStorageRepository(it).saveSponsorBlockEnabled(enabled) }
    }

    fun setSponsorBlockCategories(categories: Set<String>) {
        _sponsorBlockCategories.value = categories
        appContext?.let { AuraStorageRepository(it).saveSponsorBlockCategories(categories) }
    }

    fun setAudioReverbPreset(preset: Int) {
        dspManager.setAudioReverbPreset(
            context = appContext,
            preset = preset,
            player = exoPlayer,
            isFadeActive = isAnyFadeActive
        )
    }

    fun setSavePlaybackState(enabled: Boolean) {
        _savePlaybackState.value = enabled
        appContext?.let {
            val storage = AuraStorageRepository(it)
            storage.saveSavePlaybackState(enabled)
            if (enabled) storage.saveSavedPlaybackMode(_playbackMode.value.name)
        }
    }

    fun setKillServiceOnExit(enabled: Boolean) {
        _killServiceOnExit.value = enabled
        appContext?.let { AuraStorageRepository(it).saveKillServiceOnExit(enabled) }
    }

    fun setPlayVideoForVideoTrack(enabled: Boolean) {
        _playVideoForVideoTrack.value = enabled
        appContext?.let { AuraStorageRepository(it).savePlayVideoForVideoTrack(enabled) }
    }

    fun setRadioAudioOnly(enabled: Boolean) {
        _radioAudioOnly.value = enabled
        appContext?.let { AuraStorageRepository(it).saveRadioAudioOnly(enabled) }
    }

    fun getAudioSessionId(): Int {
        return dspManager.getAudioSessionId(exoPlayer?.audioSessionId)
    }

    fun openSystemEqualizer(context: Context): Boolean {
        return dspManager.openSystemEqualizer(context, exoPlayer?.audioSessionId)
    }

    fun reloadSettingsFromStorage() {
        val ctx = appContext ?: return
        dspManager.loadFromStorage(ctx)
        val storage = AuraStorageRepository(ctx)
        _audioQuality.value = storage.loadAudioQuality()
        _videoQuality.value = storage.loadVideoQuality()
        _skipSilence.value = storage.loadSkipSilence()
        exoPlayer?.skipSilenceEnabled = _skipSilence.value
        _crossfadeSeconds.value = storage.loadCrossfadeDuration()
        _maxCacheSizeMb.value = storage.loadMaxCacheSize()
        _sponsorBlockEnabled.value = storage.loadSponsorBlockEnabled()
        _sponsorBlockCategories.value = storage.loadSponsorBlockCategories()
        _savePlaybackState.value = storage.loadSavePlaybackState()
        _killServiceOnExit.value = storage.loadKillServiceOnExit()
        _playVideoForVideoTrack.value = storage.loadPlayVideoForVideoTrack()
        _radioAudioOnly.value = storage.loadRadioAudioOnly()
        applyEqualizerCurve()
    }

    fun setEqualizerBands(bands: List<Float>) {
        dspManager.setEqualizerBands(
            context = appContext,
            bands = bands,
            player = exoPlayer,
            isFadeActive = isAnyFadeActive
        )
    }

    fun setEqualizerPreamp(preampDb: Float) {
        dspManager.setEqualizerPreamp(
            context = appContext,
            preampDb = preampDb,
            player = exoPlayer,
            isFadeActive = isAnyFadeActive
        )
    }

    fun applyEqualizerPreset(preset: EqualizerPreset) {
        dspManager.applyEqualizerPreset(
            context = appContext,
            preset = preset,
            player = exoPlayer,
            isFadeActive = isAnyFadeActive
        )
    }

    fun resetEqualizer() {
        dspManager.resetEqualizer(
            context = appContext,
            player = exoPlayer,
            isFadeActive = isAnyFadeActive
        )
    }

    fun selectEqualizerPreset(preset: EqualizerPreset) {
        applyEqualizerPreset(preset)
    }

    fun setEqualizerEnabled(enabled: Boolean) {
        dspManager.setEqualizerEnabled(
            context = appContext,
            enabled = enabled,
            player = exoPlayer,
            isFadeActive = isAnyFadeActive
        )
    }

    fun setLoudnessNormalization(enabled: Boolean) {
        dspManager.setLoudnessNormalization(
            context = appContext,
            enabled = enabled,
            player = exoPlayer,
            isFadeActive = isAnyFadeActive
        )
    }

    /** Applies or clears a named 10-band AutoEq headphone compensation curve. */
    fun applyAutoEqProfile(name: String, bandsDb: List<Float>) {
        if (name.isBlank() || (bandsDb.all { abs(it) < 0.01f } && name.equals("None", ignoreCase = true))) {
            applyAutoEqProfile(profileId = null)
            return
        }
        val matched = AutoEqProfiles.findByName(name)
        val maxBoost = bandsDb.maxOrNull()?.coerceAtLeast(0f) ?: 0f
        val profile = matched ?: AutoEqProfile(
            id = AutoEqRepository.toProfileId(name),
            name = name,
            source = "AutoEq",
            bandsDb = bandsDb,
            preampDb = if (maxBoost > 0f) -maxBoost else 0f
        )
        _activeAutoEqProfileName.value = profile.name
        _activeAutoEqProfileId.value = profile.id
        dspManager.applyAutoEqProfile(
            profile = profile,
            player = exoPlayer,
            isFadeActive = isAnyFadeActive
        )
    }

    /** Applies or clears a curated AutoEq headphone profile by [profileId]. */
    fun applyAutoEqProfile(profileId: String?) {
        if (profileId.isNullOrBlank()) {
            _activeAutoEqProfileId.value = null
            _activeAutoEqProfileName.value = null
            dspManager.applyAutoEqProfile(
                profile = null,
                player = exoPlayer,
                isFadeActive = isAnyFadeActive
            )
            return
        }
        val profile = AutoEqProfiles.findById(profileId) ?: AutoEqProfiles.findByName(profileId)
        _activeAutoEqProfileId.value = profile?.id
        _activeAutoEqProfileName.value = profile?.name
        dspManager.applyAutoEqProfile(
            profile = profile,
            player = exoPlayer,
            isFadeActive = isAnyFadeActive
        )
    }

    fun setAudioQuality(quality: String) {
        if (_audioQuality.value == quality) return
        _audioQuality.value = quality
        appContext?.let { AuraStorageRepository(it).saveAudioQuality(quality) }
        reloadCurrentPlaybackForQualityChange(videoOnlyChanged = false)
    }

    fun setVideoQuality(quality: String) {
        if (_videoQuality.value == quality) return
        _videoQuality.value = quality
        appContext?.let { AuraStorageRepository(it).saveVideoQuality(quality) }
        reloadCurrentPlaybackForQualityChange(videoOnlyChanged = true)
    }

    private fun applyResolvedAudioMetadata(videoId: String, resolved: ResolvedStream) {
        dspManager.setTrackLoudnessDb(resolved.loudnessDb)
        _streamSourceTier.value = when {
            resolved.bitrateKbps >= 180 -> "Ultra HD • ${resolved.codec} ${resolved.bitrateKbps} KBPS"
            resolved.bitrateKbps >= 110 -> "High Quality • ${resolved.codec} ${resolved.bitrateKbps} KBPS"
            else -> "Data Saver • ${resolved.codec} ${resolved.bitrateKbps} KBPS"
        }
        scope.launch(Dispatchers.Main) {
            val cur = _currentTrack.value
            if (cur != null && cur.videoId == videoId) {
                val updated = cur.copy(codec = resolved.codec, bitrateKbps = resolved.bitrateKbps)
                _currentTrack.value = updated
                val currentQ = _queue.value.toMutableList()
                val idx = currentQ.indexOfFirst { it.id == cur.id || it.videoId == videoId }
                if (idx >= 0) {
                    currentQ[idx] = updated
                    _queue.value = currentQ
                }
            }
            applyEqualizerCurve()
        }
    }

    private fun reloadCurrentPlaybackForQualityChange(videoOnlyChanged: Boolean) {
        val player = exoPlayer ?: return
        val cur = _currentTrack.value ?: return
        val currentPos = player.currentPosition.coerceAtLeast(0L)
        val wasPlaying = player.isPlaying || player.playWhenReady

        if (_isVideoMode.value) {
            val origAudio = originalAudioTrack
            if (origAudio != null) {
                playVideoForTrack(cur, origAudio, startPlayback = false)
            } else {
                playStandaloneVideo(
                    videoTrack = cur,
                    videoQueue = _queue.value.ifEmpty { listOf(cur) },
                    startPositionMs = currentPos,
                    autoPlay = wasPlaying
                )
            }
            return
        }

        if (videoOnlyChanged) return
        val ctx = appContext
        if (ctx != null) {
            val offlineFile = getOfflineFile(ctx, cur.videoId)
            if (offlineFile.exists() && offlineFile.length() > 10_000L) return
        }

        val list = _queue.value
        val curIdx = list.indexOfFirst { it.id == cur.id || it.videoId == cur.videoId }.coerceAtLeast(0)
        if (list.isEmpty()) return
        val mediaItems = list.map { buildMediaItem(it) }
        player.setMediaItems(mediaItems, curIdx, currentPos)
        player.prepare()
        if (wasPlaying) player.play() else player.pause()
        prefetchUpcomingTracks(cur.videoId)
    }

    fun setSkipSilence(enabled: Boolean) {
        _skipSilence.value = enabled
        exoPlayer?.skipSilenceEnabled = enabled
        appContext?.let { AuraStorageRepository(it).saveSkipSilence(enabled) }
    }

    fun setMaxCacheSizeMb(sizeMb: Int) {
        _maxCacheSizeMb.value = sizeMb
        appContext?.let { AuraStorageRepository(it).saveMaxCacheSize(sizeMb) }
    }

    fun setCrossfadeSeconds(seconds: Int) {
        val clamped = seconds.coerceIn(0, 12)
        _crossfadeSeconds.value = clamped
        if (clamped == 0) {
            cancelCrossfade()
        }
        appContext?.let { AuraStorageRepository(it).saveCrossfadeDuration(clamped) }
    }

    fun setSkipCrossfadeWithinAlbum(enabled: Boolean) {
        _skipCrossfadeWithinAlbum.value = enabled
    }

    fun setSleepTimerEndOfTrack(enabled: Boolean) {
        _sleepTimerEndOfTrack.value = enabled
        if (enabled) {
            sleepTimerJob?.cancel()
            _sleepTimerRemainingMin.value = null
            isSleepTimerFading = false
            cancelCrossfade()
            if (fadeJob?.isActive != true) {
                exoPlayer?.volume = baseVolume
            }
        }
    }

    fun setSleepTimerMinutes(minutes: Int?) {
        sleepTimerJob?.cancel()
        isSleepTimerFading = false
        if (minutes == null || minutes <= 0) {
            _sleepTimerRemainingMin.value = null
            if (!_isCrossfading.value && fadeJob?.isActive != true) {
                exoPlayer?.volume = baseVolume
            }
            return
        }
        _sleepTimerEndOfTrack.value = false
        _sleepTimerRemainingMin.value = minutes
        sleepTimerJob = scope.launch {
            var remainingSec = minutes * 60
            while (remainingSec > 5 && isActive) {
                delay(1_000L)
                remainingSec--
                _sleepTimerRemainingMin.value = ((remainingSec + 59) / 60).coerceAtLeast(1)
            }
            if (!isActive) return@launch

            val player = exoPlayer
            if (player != null && player.isPlaying) {
                isSleepTimerFading = true
                cancelCrossfade()
                val startVol = player.volume.coerceIn(0f, 1f)
                val steps = 50 // 50 * 100ms = 5 seconds smooth fade-out
                for (step in steps downTo 0) {
                    if (!isActive) break
                    player.volume = (startVol * (step / steps.toFloat())).coerceIn(0f, 1f)
                    if (step % 10 == 0 && step > 0) {
                        _sleepTimerRemainingMin.value = 1
                    }
                    delay(100L)
                }
                player.pause()
                isSleepTimerFading = false
                player.volume = 1.0f.coerceAtMost(baseVolume)
            } else {
                exoPlayer?.pause()
                isSleepTimerFading = false
                exoPlayer?.volume = baseVolume
            }
            _sleepTimerRemainingMin.value = null
        }
    }

    /** Returns the current size in bytes of Media3's [SimpleCache]. */
    fun getMediaCacheSizeBytes(): Long {
        val cache = simpleCache
        if (cache != null) {
            return runCatching { cache.cacheSpace }.getOrElse { 0L }
        }
        val ctx = appContext ?: return 0L
        return calculateDirectorySize(File(ctx.cacheDir, "aura_media3_cache"))
    }

    /**
     * Safely evicts every entry from Media3's [SimpleCache] via `removeResource(key)`
     * without deleting files out from under open cache handles. Returns approximate freed bytes.
     */
    fun clearMediaCache(): Long {
        val cache = simpleCache ?: return 0L
        return runCatching {
            val beforeBytes = cache.cacheSpace
            val keys = cache.keys.toList()
            for (key in keys) {
                cache.removeResource(key)
            }
            val afterBytes = cache.cacheSpace
            (beforeBytes - afterBytes).coerceAtLeast(0L)
        }.getOrElse { e ->
            Log.w(TAG, "Safe media cache clear notice: ${e.message}")
            0L
        }
    }

    private fun calculateDirectorySize(dir: File): Long {
        if (!dir.exists()) return 0L
        var total = 0L
        val files = dir.listFiles() ?: return 0L
        for (file in files) {
            total += if (file.isDirectory) calculateDirectorySize(file) else file.length()
        }
        return total
    }

    fun getDedicatedDownloadDir(context: Context): File =
        offlineDownloader.getDedicatedDownloadDir(context)

    fun getOfflineArtworkFile(context: Context, videoId: String): File =
        offlineDownloader.getOfflineArtworkFile(context, videoId)

    fun getOfflineArtworkUri(context: Context, videoId: String): String? =
        offlineDownloader.getOfflineArtworkUri(context, videoId)

    private fun withOfflineArtwork(track: AuraTrack): AuraTrack =
        offlineDownloader.withOfflineArtwork(appContext, track)

    suspend fun saveOfflineArtwork(context: Context, track: AuraTrack): String? =
        offlineDownloader.saveOfflineArtwork(context, track)

    fun getOfflineFile(context: Context, videoId: String): File =
        offlineDownloader.getOfflineFile(context, videoId)

    suspend fun downloadTrackOffline(
        context: Context,
        track: AuraTrack,
        awaitResume: suspend () -> Unit = {},
        onProgress: (bytesRead: Long, totalBytes: Long, progress: Float) -> Unit = { _, _, _ -> }
    ): File? = offlineDownloader.downloadTrackOffline(
        context = context,
        track = track,
        audioQuality = _audioQuality.value,
        awaitResume = awaitResume,
        onProgress = onProgress
    )

    fun setVideoLoading(loading: Boolean) {
        _isVideoLoading.value = loading
    }

    private fun buildVideoMediaSource(
        resolved: ResolvedVideoStream,
        dataSourceFactory: DataSource.Factory
    ): MediaSource {
        val videoItem = MediaItem.Builder()
            .setUri(Uri.parse(resolved.streamUrl))
            .setCustomCacheKey("video_${resolved.videoId}_${resolved.quality}")
            .build()
        val videoSource = ProgressiveMediaSource.Factory(dataSourceFactory).createMediaSource(videoItem)
        return if (!resolved.audioUrl.isNullOrBlank()) {
            val audioItem = MediaItem.Builder()
                .setUri(Uri.parse(resolved.audioUrl))
                .setCustomCacheKey("video_audio_${resolved.videoId}_${_audioQuality.value}")
                .build()
            val audioSource = ProgressiveMediaSource.Factory(dataSourceFactory).createMediaSource(audioItem)
            MergingMediaSource(videoSource, audioSource)
        } else {
            videoSource
        }
    }

    /**
     * Seamlessly launches official music video playback while preserving the original
     * Spotify song artwork, metadata, and playlist queue.
     */
    fun playVideoForTrack(
        videoTrack: AuraTrack,
        fromAudioTrack: AuraTrack? = null,
        startPlayback: Boolean = false
    ) {
        val currentAudio = fromAudioTrack ?: currentTrack.value
        val player = exoPlayer
        val currentPos = if (startPlayback) 0L else (player?.currentPosition ?: 0L)
        if (currentAudio != null && originalAudioTrack == null) {
            originalAudioTrack = currentAudio
            originalAudioQueue = _queue.value
            originalAudioIndex = _queue.value.indexOfFirst { it.videoId == currentAudio.videoId }
        }
        originalAudioTrack?.let { orig ->
            if (SimpMusicExtractorEngine.isVideoThumbnailUrl(orig.artworkUrl)) {
                scope.launch(Dispatchers.IO) {
                    val officialAudio = SimpMusicExtractorEngine.findOfficialAudioTrack(orig)
                    if (officialAudio != null && !SimpMusicExtractorEngine.isVideoThumbnailUrl(officialAudio.artworkUrl)) {
                        withContext(Dispatchers.Main) {
                            if (originalAudioTrack?.videoId == orig.videoId) {
                                val enriched = orig.copy(
                                    artworkUrl = officialAudio.artworkUrl,
                                    album = officialAudio.album.ifBlank { orig.album },
                                    musicVideoType = "MUSIC_VIDEO_TYPE_ATV"
                                )
                                originalAudioTrack = enriched
                                originalAudioQueue = originalAudioQueue?.map {
                                    if (it.id == orig.id || it.videoId == orig.videoId) enriched else it
                                }
                            }
                        }
                    }
                }
            }
        }
        cancelCrossfade()
        _isVideoMode.value = true
        val wasPlaying = player?.isPlaying == true || startPlayback
        player?.pause()

        // Keep the video's own thumbnail as the loading poster. Reusing the audio album cover here
        // made an audio fallback look like a successfully loaded music video.
        val preservedTrack = videoTrack.copy(
            artworkUrl = "https://i.ytimg.com/vi/${videoTrack.videoId}/hqdefault.jpg",
            album = currentAudio?.album ?: videoTrack.album,
            musicVideoType = videoTrack.musicVideoType ?: "MUSIC_VIDEO_TYPE_OMV"
        )
        _currentTrack.value = preservedTrack

        scope.launch {
            _isVideoLoading.value = true
            val videoQualityPref = _videoQuality.value
            val audioQualityPref = _audioQuality.value
            val resolved = withContext(Dispatchers.IO) {
                SimpMusicExtractorEngine.resolveVideoStream(
                    videoTrack.videoId,
                    videoQualityPref,
                    audioQualityPref
                )
            }
            _isVideoLoading.value = false

            val activePlayer = exoPlayer ?: return@launch
            if (resolved != null) {
                dspManager.updateTrackLoudness(
                    loudnessDb = SimpMusicExtractorEngine.getTrackLoudnessDb(videoTrack.videoId) ?: 0f,
                    player = activePlayer,
                    isFadeActive = isAnyFadeActive
                )
                _streamSourceTier.value = "Video • ${resolved.quality}"
                val cFactory = cacheDataSourceFactory ?: DefaultDataSource.Factory(appContext!!)
                val mediaSource = buildVideoMediaSource(resolved, cFactory)

                activePlayer.setMediaSource(mediaSource, currentPos)
                activePlayer.prepare()
                if (wasPlaying) {
                    activePlayer.play()
                }
            } else {
                Log.w(TAG, "Could not resolve video ${videoTrack.videoId}; restoring audio playback")
                switchToAudioMode()
                if (wasPlaying) exoPlayer?.play()
                currentAudio?.let { onVideoUnavailableCallback?.invoke(it) }
            }
        }
    }

    private fun advanceSongVideoQueue() {
        val list = originalAudioQueue ?: _queue.value
        val currentAudio = originalAudioTrack ?: return
        val currentIndex = list.indexOfFirst { it.videoId == currentAudio.videoId }
        val nextAudio = list.getOrNull(currentIndex + 1)
            ?: list.firstOrNull().takeIf { list.isNotEmpty() && exoPlayer?.repeatMode == Player.REPEAT_MODE_ALL }
            ?: return
        scope.launch {
            _isVideoLoading.value = true
            val officialVideo = withContext(Dispatchers.IO) {
                SimpMusicExtractorEngine.findOfficialMusicVideo(nextAudio)
            }
            _isVideoLoading.value = false
            if (officialVideo != null) {
                originalAudioTrack = nextAudio
                originalAudioQueue = list
                originalAudioIndex = list.indexOfFirst { it.videoId == nextAudio.videoId }
                _queue.value = list
                playVideoForTrack(officialVideo, nextAudio, startPlayback = true)
            } else {
                Log.i(TAG, "No official video found for '${nextAudio.title}', continuing with audio")
                onVideoUnavailableCallback?.invoke(nextAudio)
                originalAudioTrack = null
                originalAudioQueue = null
                originalAudioIndex = -1
                _isVideoMode.value = false
                playTrack(nextAudio, list, allowRadioQueue = false)
            }
        }
    }

    /** Starts a video discovered from the Home carousel as a video player session. */
    fun playStandaloneVideo(
        videoTrack: AuraTrack,
        videoQueue: List<AuraTrack>,
        startPositionMs: Long = 0L,
        autoPlay: Boolean = true
    ) {
        val previousTrack = _currentTrack.value
        val previousQueue = _queue.value
        val wasPlaying = exoPlayer?.isPlaying == true
        cancelCrossfade()
        exoPlayer?.pause()
        originalAudioTrack = null
        originalAudioQueue = null
        originalAudioIndex = -1
        originalQueue = videoQueue
        _queue.value = videoQueue
        _positionSec.value = (startPositionMs / 1000f).coerceAtLeast(0f)
        _bufferedSec.value = 0f
        _durationSec.value = videoTrack.durationSeconds.toFloat().coerceAtLeast(1f)
        _isVideoMode.value = true
        _currentTrack.value = videoTrack.copy(
            artworkUrl = "https://i.ytimg.com/vi/${videoTrack.videoId}/hqdefault.jpg",
            musicVideoType = videoTrack.musicVideoType ?: "MUSIC_VIDEO_TYPE_OMV"
        )

        scope.launch(Dispatchers.IO) {
            val officialAudio = SimpMusicExtractorEngine.findOfficialAudioTrack(videoTrack)
            if (officialAudio != null && !SimpMusicExtractorEngine.isVideoThumbnailUrl(officialAudio.artworkUrl)) {
                withContext(Dispatchers.Main) {
                    if (_isVideoMode.value && _currentTrack.value?.videoId == videoTrack.videoId && originalAudioTrack == null) {
                        originalAudioTrack = videoTrack.copy(
                            artworkUrl = officialAudio.artworkUrl,
                            album = officialAudio.album.ifBlank { videoTrack.album },
                            musicVideoType = "MUSIC_VIDEO_TYPE_ATV"
                        )
                    }
                }
            }
        }

        scope.launch {
            _isVideoLoading.value = true
            val videoQualityPref = _videoQuality.value
            val audioQualityPref = _audioQuality.value
            val resolved = withContext(Dispatchers.IO) {
                SimpMusicExtractorEngine.resolveVideoStream(
                    videoTrack.videoId,
                    videoQualityPref,
                    audioQualityPref
                )
            }
            _isVideoLoading.value = false

            val player = exoPlayer ?: return@launch
            if (resolved == null) {
                Log.w(TAG, "Could not resolve Home music video ${videoTrack.videoId}")
                _isVideoMode.value = false
                _currentTrack.value = previousTrack
                _queue.value = previousQueue
                originalQueue = previousQueue
                if (wasPlaying) exoPlayer?.play()
                return@launch
            }

            dspManager.updateTrackLoudness(
                loudnessDb = SimpMusicExtractorEngine.getTrackLoudnessDb(videoTrack.videoId) ?: 0f,
                player = player,
                isFadeActive = isAnyFadeActive
            )
            _streamSourceTier.value = "Video • ${resolved.quality}"
            val dataSourceFactory = cacheDataSourceFactory ?: DefaultDataSource.Factory(appContext!!)
            val mediaSource = buildVideoMediaSource(resolved, dataSourceFactory)
            player.setMediaSource(mediaSource, startPositionMs)
            player.prepare()
            if (autoPlay) {
                player.play()
            } else {
                player.pause()
            }
        }
    }

    /**
     * Restores the exact original Spotify song, artwork, and full playlist queue
     * when switching back from video mode to audio mode.
     */
    fun switchToAudioMode() {
        if (!_isVideoMode.value) return
        _isVideoMode.value = false
        val player = exoPlayer ?: return
        val currentPos = player.currentPosition
        val wasPlaying = player.isPlaying

        val origTrack = originalAudioTrack
        val origQueue = originalAudioQueue

        originalAudioTrack = null
        originalAudioQueue = null
        originalAudioIndex = -1

        if (origTrack != null) {
            _currentTrack.value = origTrack
            if (origQueue != null) {
                _queue.value = origQueue
            }
            val mediaItem = buildMediaItem(origTrack)
            player.setMediaItem(mediaItem, currentPos)
            player.prepare()
            if (wasPlaying) {
                player.play()
            }
            if (SimpMusicExtractorEngine.isVideoThumbnailUrl(origTrack.artworkUrl)) {
                resolveAndApplyOfficialAudioArtwork(origTrack)
            }
        } else {
            val cur = currentTrack.value ?: return
            val mediaItem = buildMediaItem(cur)
            player.setMediaItem(mediaItem, currentPos)
            player.prepare()
            if (wasPlaying) {
                player.play()
            }
            if (SimpMusicExtractorEngine.isVideoThumbnailUrl(cur.artworkUrl)) {
                resolveAndApplyOfficialAudioArtwork(cur)
            }
        }
    }

    private fun resolveAndApplyOfficialAudioArtwork(targetTrack: AuraTrack) {
        scope.launch(Dispatchers.IO) {
            val officialAudio = SimpMusicExtractorEngine.findOfficialAudioTrack(targetTrack) ?: return@launch
            if (SimpMusicExtractorEngine.isVideoThumbnailUrl(officialAudio.artworkUrl)) return@launch
            withContext(Dispatchers.Main) {
                if (!_isVideoMode.value && _currentTrack.value?.videoId == targetTrack.videoId) {
                    val active = _currentTrack.value ?: return@withContext
                    val updated = active.copy(
                        artworkUrl = officialAudio.artworkUrl,
                        album = officialAudio.album.ifBlank { active.album },
                        musicVideoType = "MUSIC_VIDEO_TYPE_ATV"
                    )
                    _currentTrack.value = updated
                    _queue.value = _queue.value.map {
                        if (it.id == active.id || it.videoId == active.videoId) {
                            it.copy(
                                artworkUrl = officialAudio.artworkUrl,
                                album = officialAudio.album.ifBlank { it.album },
                                musicVideoType = "MUSIC_VIDEO_TYPE_ATV"
                            )
                        } else it
                    }
                    originalQueue = originalQueue.map {
                        if (it.id == active.id || it.videoId == active.videoId) {
                            it.copy(
                                artworkUrl = officialAudio.artworkUrl,
                                album = officialAudio.album.ifBlank { it.album },
                                musicVideoType = "MUSIC_VIDEO_TYPE_ATV"
                            )
                        } else it
                    }
                }
            }
        }
    }

    fun setVideoMode(enabled: Boolean) {
        if (_isVideoMode.value == enabled) return
        if (!enabled) {
            switchToAudioMode()
        } else {
            cancelCrossfade()
            val cur = currentTrack.value ?: return
            scope.launch {
                _isVideoLoading.value = true
                val video = withContext(Dispatchers.IO) {
                    SimpMusicExtractorEngine.findOfficialMusicVideo(cur)
                }
                _isVideoLoading.value = false
                if (video != null) playVideoForTrack(video, cur)
                else {
                    _isVideoMode.value = false
                    onVideoUnavailableCallback?.invoke(cur)
                }
            }
        }
    }

    private fun buildMediaItem(track: AuraTrack): MediaItem {
        val metadata = MediaMetadata.Builder()
            .setTitle(track.title)
            .setArtist(track.artist)
            .setAlbumTitle(track.album)
            .setDisplayTitle(track.title)
            .setSubtitle(track.artist)
            .setArtworkUri(Uri.parse(track.highResArtworkUrl))
            .setIsPlayable(true)
            .build()

        val stableId = if (track.id.isNotBlank()) track.id else track.videoId
        val schemeHost = if (_isVideoMode.value) "aura://video/" else "aura://track/"
        return MediaItem.Builder()
            .setMediaId(stableId)
            .setUri(Uri.parse("$schemeHost$stableId"))
            .setMediaMetadata(metadata)
            .build()
    }

    private fun prefetchUpcomingTracks(currentVideoId: String) {
        val list = _queue.value
        val curTrack = _currentTrack.value
        val idx = list.indexOfFirst {
            (curTrack != null && it.id == curTrack.id) || it.videoId == currentVideoId
        }
        if (idx < 0) return

        val qualityPref = _audioQuality.value
        for (offset in 1..2) {
            val upcoming = list.getOrNull(idx + offset) ?: continue
            if (upcoming.isPendingResolution) {
                scope.launch {
                    val resolvedUpcoming = SimpMusicExtractorEngine.resolveSpotifyPendingTrack(upcoming)
                    if (!resolvedUpcoming.isPendingResolution) {
                        withContext(Dispatchers.Main) {
                            val currentQ = _queue.value.toMutableList()
                            val qIdx = currentQ.indexOfFirst { it.id == upcoming.id }
                            if (qIdx >= 0) {
                                currentQ[qIdx] = resolvedUpcoming
                                _queue.value = currentQ
                            }
                            val origIdx = originalQueue.indexOfFirst { it.id == upcoming.id }
                            if (origIdx >= 0 && origIdx < originalQueue.size) {
                                val origList = originalQueue.toMutableList()
                                origList[origIdx] = resolvedUpcoming
                                originalQueue = origList
                            }
                            onTrackResolvedCallback?.invoke(resolvedUpcoming)
                        }
                        SimpMusicExtractorEngine.prefetchStream(resolvedUpcoming.videoId, qualityPref)
                    }
                }
            } else {
                SimpMusicExtractorEngine.prefetchStream(upcoming.videoId, qualityPref)
            }
        }
    }

    private fun applyEqualizerCurve() {
        dspManager.applyCurve(
            player = exoPlayer,
            isFadeActive = isAnyFadeActive
        )
    }

    private fun buildAudioExoPlayer(
        ctx: Context,
        resolvingFactory: DataSource.Factory,
        handleAudioFocus: Boolean,
        useSecondaryDsp: Boolean
    ): ExoPlayer {
        val loadControl = DefaultLoadControl.Builder()
            .setBufferDurationsMs(
                30_000,
                60_000,
                1_500,
                4_000
            )
            .setPrioritizeTimeOverSizeThresholds(true)
            .setBackBuffer(15_000, true)
            .build()

        val audioAttributes = AudioAttributes.Builder()
            .setUsage(C.USAGE_MEDIA)
            .setContentType(C.AUDIO_CONTENT_TYPE_MUSIC)
            .build()

        val processors = if (useSecondaryDsp) {
            dspManager.createSecondaryAudioProcessors().also {
                crossfadeSecondaryProcessors = it
            }
        } else {
            dspManager.getAudioProcessors()
        }

        val renderersFactory = object : DefaultRenderersFactory(ctx) {
            override fun buildAudioSink(
                context: Context,
                enableFloatOutput: Boolean,
                enableAudioTrackPlaybackParams: Boolean
            ): AudioSink? {
                if (processors.isEmpty()) {
                    return super.buildAudioSink(
                        context,
                        enableFloatOutput,
                        enableAudioTrackPlaybackParams
                    )
                }
                return DefaultAudioSink.Builder(context)
                    .setEnableFloatOutput(enableFloatOutput)
                    .setEnableAudioTrackPlaybackParams(enableAudioTrackPlaybackParams)
                    .setAudioProcessors(processors)
                    .build()
            }
        }.setExtensionRendererMode(DefaultRenderersFactory.EXTENSION_RENDERER_MODE_PREFER)

        return ExoPlayer.Builder(ctx, renderersFactory)
            .setMediaSourceFactory(DefaultMediaSourceFactory(resolvingFactory))
            .setLoadControl(loadControl)
            .setAudioAttributes(audioAttributes, handleAudioFocus)
            .setHandleAudioBecomingNoisy(handleAudioFocus)
            .setWakeMode(C.WAKE_MODE_NETWORK)
            .build()
    }

    private fun trackUniqueKey(track: AuraTrack): String {
        return if (track.id.isNotBlank()) "${track.id}:${track.videoId}" else track.videoId
    }

    private fun resolveNextTrackForCrossfade(): Pair<Int, AuraTrack>? {
        val cfSec = _crossfadeSeconds.value
        if (cfSec <= 0 || _isVideoMode.value || _sleepTimerEndOfTrack.value) return null
        if (_playbackMode.value == PlaybackMode.REPEAT_ONE || exoPlayer?.repeatMode == Player.REPEAT_MODE_ONE) {
            return null
        }

        val list = _queue.value
        val cur = _currentTrack.value ?: return null
        val curIdx = list.indexOfFirst { it.id == cur.id || it.videoId == cur.videoId }
        if (curIdx < 0) return null

        val nextPair = when {
            curIdx < list.lastIndex -> (curIdx + 1) to list[curIdx + 1]
            list.size > 1 && (_playbackMode.value == PlaybackMode.REPEAT_ALL || exoPlayer?.repeatMode == Player.REPEAT_MODE_ALL) -> {
                0 to list[0]
            }
            else -> null
        } ?: return null

        val nextTrack = nextPair.second
        if (nextTrack.isPendingResolution) return null

        if (_skipCrossfadeWithinAlbum.value) {
            val curAlbum = cur.album.trim()
            val nextAlbum = nextTrack.album.trim()
            if (curAlbum.isNotBlank() &&
                nextAlbum.isNotBlank() &&
                !curAlbum.equals("YouTube Music", ignoreCase = true) &&
                curAlbum.equals(nextAlbum, ignoreCase = true)
            ) {
                return null
            }
        }
        return nextPair
    }

    private fun prepareCrossfadeTarget(currentTrack: AuraTrack, nextTrack: AuraTrack) {
        val ctx = appContext ?: return
        val factory = resolvingDataSourceFactory ?: return
        val sourceKey = trackUniqueKey(currentTrack)
        val targetKey = trackUniqueKey(nextTrack)
        if (crossfadePreparedTrackKey == targetKey && crossfadeSourceTrackKey == sourceKey && crossfadePlayer != null) {
            return
        }

        releaseCrossfadePlayerOnly()
        val secondary = buildAudioExoPlayer(
            ctx = ctx,
            resolvingFactory = factory,
            handleAudioFocus = false,
            useSecondaryDsp = true
        )
        secondary.volume = 0f
        secondary.playWhenReady = false
        secondary.setMediaItem(buildMediaItem(nextTrack), 0L)
        secondary.prepare()

        crossfadePlayer = secondary
        crossfadePreparedTrackKey = targetKey
        crossfadeSourceTrackKey = sourceKey
    }

    private fun startCrossfadeTransition(
        outgoingTrack: AuraTrack,
        nextQueueIndex: Int,
        nextTrack: AuraTrack,
        crossfadeDurationMs: Long
    ) {
        val primary = exoPlayer ?: return
        if (_isCrossfading.value || crossfadeJob?.isActive == true) return

        prepareCrossfadeTarget(outgoingTrack, nextTrack)
        val secondary = crossfadePlayer ?: return

        _isCrossfading.value = true
        crossfadeJob = scope.launch {
            try {
                // Wait briefly for pre-warmed secondary player to become ready without blocking forever
                var readyWaitMs = 0L
                val maxReadyWaitMs = minOf(2_000L, crossfadeDurationMs / 2L)
                while (isActive &&
                    secondary.playbackState != Player.STATE_READY &&
                    readyWaitMs < maxReadyWaitMs
                ) {
                    if (!primary.isPlaying || primary.playbackState == Player.STATE_ENDED) {
                        cancelCrossfade()
                        return@launch
                    }
                    delay(40L)
                    readyWaitMs += 40L
                }

                if (!isActive || secondary.playbackState != Player.STATE_READY || !primary.isPlaying) {
                    cancelCrossfade()
                    return@launch
                }

                val remainingAtStartMs = (primary.duration - primary.currentPosition - 120L).coerceAtLeast(300L)
                val actualFadeMs = minOf(crossfadeDurationMs, remainingAtStartMs)

                secondary.volume = 0f
                secondary.play()

                val startTimeMs = System.currentTimeMillis()
                while (isActive) {
                    if (!primary.isPlaying) {
                        cancelCrossfade()
                        return@launch
                    }
                    val elapsedMs = System.currentTimeMillis() - startTimeMs
                    val t = (elapsedMs.toFloat() / actualFadeMs.toFloat()).coerceIn(0f, 1f)
                    val angle = t * (PI / 2.0)
                    val outGain = cos(angle).toFloat().coerceIn(0f, 1f)
                    val inGain = sin(angle).toFloat().coerceIn(0f, 1f)

                    primary.volume = (baseVolume * outGain).coerceIn(0f, 1f)
                    secondary.volume = (baseVolume * inGain).coerceIn(0f, 1f)

                    if (t >= 1f || (primary.duration > 0L && primary.duration - primary.currentPosition <= 100L)) {
                        break
                    }
                    delay(40L)
                }
                if (!isActive) return@launch

                onTrackFinishedCallback?.invoke(outgoingTrack)

                val handoffPosMs = secondary.currentPosition.coerceAtLeast(0L)
                val targetMediaIdx = (0 until primary.mediaItemCount).firstOrNull { idx ->
                    val mid = primary.getMediaItemAt(idx).mediaId
                    mid == nextTrack.id || mid == nextTrack.videoId
                } ?: nextQueueIndex.coerceIn(0, (primary.mediaItemCount - 1).coerceAtLeast(0))

                _currentTrack.value = nextTrack
                _durationSec.value = nextTrack.durationSeconds.toFloat().coerceAtLeast(1f)
                _positionSec.value = (handoffPosMs / 1000f).coerceAtLeast(0f)

                primary.volume = 0f
                primary.seekTo(targetMediaIdx, handoffPosMs)
                primary.play()

                // Keep secondary carrying the beat until primary is READY at handoffPosMs
                var handoffWait = 0L
                while (isActive && primary.playbackState != Player.STATE_READY && handoffWait < 500L) {
                    delay(20L)
                    handoffWait += 20L
                }

                // Short 80ms micro-blend from secondary -> primary so the handoff is seamless
                val blendSteps = 4
                for (step in 1..blendSteps) {
                    if (!isActive) break
                    val frac = step / blendSteps.toFloat()
                    primary.volume = (baseVolume * frac).coerceIn(0f, 1f)
                    secondary.volume = (baseVolume * (1f - frac)).coerceIn(0f, 1f)
                    delay(20L)
                }

                primary.volume = baseVolume
                releaseCrossfadePlayerOnly()
                _isCrossfading.value = false
                prefetchUpcomingTracks(nextTrack.videoId)
            } catch (e: Exception) {
                Log.w(TAG, "Crossfade transition notice: ${e.message}")
                cancelCrossfade()
            }
        }
    }

    private fun releaseCrossfadePlayerOnly() {
        crossfadePlayer?.let { p ->
            runCatching {
                p.stop()
                p.clearMediaItems()
                p.release()
            }
        }
        crossfadePlayer = null
        dspManager.releaseSecondaryAudioProcessors(crossfadeSecondaryProcessors)
        crossfadeSecondaryProcessors = null
        crossfadePreparedTrackKey = null
        crossfadeSourceTrackKey = null
    }

    private fun cancelCrossfade() {
        crossfadeJob?.cancel()
        crossfadeJob = null
        releaseCrossfadePlayerOnly()
        _isCrossfading.value = false
        if (!isSleepTimerFading && fadeJob?.isActive != true) {
            exoPlayer?.volume = baseVolume
        }
    }

    private fun startProgressTicker() {
        progressJob?.cancel()
        progressJob = scope.launch {
            while (isActive) {
                val player = exoPlayer
                if (player != null) {
                    val posSec = (player.currentPosition / 1000f).coerceAtLeast(0f)
                    val bufSec = (player.bufferedPosition / 1000f).coerceAtLeast(0f)
                    _positionSec.value = posSec
                    _bufferedSec.value = bufSec

                    val durMs = player.duration
                    if (durMs > 0 && durMs != C.TIME_UNSET) {
                        _durationSec.value = (durMs / 1000f).coerceAtLeast(1f)

                        val remainingMs = durMs - player.currentPosition
                        val crossfadeMs = _crossfadeSeconds.value * 1000L
                        val curTrack = _currentTrack.value
                        val nextTarget = if (crossfadeMs > 0L && durMs > crossfadeMs * 2L + 2000L) {
                            resolveNextTrackForCrossfade()
                        } else {
                            null
                        }

                        if (player.isPlaying && curTrack != null && nextTarget != null && !_isCrossfading.value) {
                            if (remainingMs in (crossfadeMs + 1000L)..(crossfadeMs + 6000L)) {
                                prepareCrossfadeTarget(curTrack, nextTarget.second)
                            } else if (remainingMs in 300L..crossfadeMs) {
                                startCrossfadeTransition(
                                    outgoingTrack = curTrack,
                                    nextQueueIndex = nextTarget.first,
                                    nextTrack = nextTarget.second,
                                    crossfadeDurationMs = crossfadeMs
                                )
                            }
                        } else if (!isAnyFadeActive && player.volume != baseVolume) {
                            player.volume = baseVolume
                        }
                    }

                    // SponsorBlock real-time auto-skip check
                    if (player.isPlaying && _sponsorBlockEnabled.value) {
                        val segments = _sponsorSegments.value
                        val hit = segments.firstOrNull { seg ->
                            posSec >= seg.startTimeSec && posSec < seg.endTimeSec - 0.35f
                        }
                        if (hit != null) {
                            seekToSeconds(hit.endTimeSec)
                            _lastSkippedSegment.value = "Skipped ${hit.displayLabel}"
                        }
                    }
                }
                delay(100L)
            }
        }
    }
}
