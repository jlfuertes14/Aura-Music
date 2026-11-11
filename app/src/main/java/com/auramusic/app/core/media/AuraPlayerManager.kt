package com.auramusic.app.core.media

import android.content.Context
import android.content.Intent
import android.media.audiofx.Equalizer
import android.net.Uri
import android.os.Build
import android.os.Environment
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
import androidx.media3.datasource.DataSpec
import androidx.media3.datasource.DefaultDataSource
import androidx.media3.datasource.ResolvingDataSource
import androidx.media3.datasource.cache.CacheDataSource
import androidx.media3.datasource.cache.LeastRecentlyUsedCacheEvictor
import androidx.media3.datasource.cache.SimpleCache
import androidx.media3.datasource.okhttp.OkHttpDataSource
import androidx.media3.exoplayer.DefaultLoadControl
import androidx.media3.exoplayer.DefaultRenderersFactory
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.exoplayer.source.MergingMediaSource
import androidx.media3.exoplayer.source.ProgressiveMediaSource
import com.auramusic.app.core.extractor.SimpMusicExtractorEngine
import com.auramusic.app.data.AuraStorageRepository
import com.auramusic.app.domain.model.AuraTrack
import com.auramusic.app.domain.model.isPendingResolution
import com.auramusic.app.domain.model.EqualizerPreset
import com.auramusic.app.domain.model.PlaybackMode
import com.auramusic.app.domain.model.SIMPMUSIC_ALL_EQ_PRESETS
import com.auramusic.app.domain.model.SIMPMUSIC_EQ_PRESETS
import com.auramusic.app.domain.model.SponsorSegment
import com.auramusic.app.domain.model.findMatchingPreset
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
import okhttp3.Request
import java.io.File

@OptIn(UnstableApi::class)
object AuraPlayerManager {
    private const val TAG = "AuraPlayerManager"
    private const val MAX_CACHE_BYTES = 512L * 1024L * 1024L // 512 MB SimpMusic SimpleCache

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private var progressJob: Job? = null
    private var sleepTimerJob: Job? = null
    private var radioFetchJob: Job? = null
    private var fadeJob: Job? = null
    private var baseVolume: Float = 1.0f

    private var appContext: Context? = null
    private var simpleCache: SimpleCache? = null
    private var hardwareEq: Equalizer? = null

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

    private val _activeEqPreset = MutableStateFlow(
        SIMPMUSIC_ALL_EQ_PRESETS.find { it.id == "flat" } ?: SIMPMUSIC_ALL_EQ_PRESETS.first()
    )
    val activeEqPreset: StateFlow<EqualizerPreset> = _activeEqPreset.asStateFlow()

    private val _equalizerBands = MutableStateFlow(listOf(0f, 0f, 0f, 0f, 0f, 0f, 0f, 0f, 0f, 0f))
    val equalizerBands: StateFlow<List<Float>> = _equalizerBands.asStateFlow()

    private val _equalizerPreamp = MutableStateFlow(0f)
    val equalizerPreamp: StateFlow<Float> = _equalizerPreamp.asStateFlow()

    private val _activeEqPresetName = MutableStateFlow("Flat")
    val activeEqPresetName: StateFlow<String> = _activeEqPresetName.asStateFlow()

    // Default EQ to false for bit-perfect, uncolored, distortion-free audio
    private val _eqEnabled = MutableStateFlow(false)
    val eqEnabled: StateFlow<Boolean> = _eqEnabled.asStateFlow()

    private val _audioQuality = MutableStateFlow("Auto")
    val audioQuality: StateFlow<String> = _audioQuality.asStateFlow()

    private val _videoQuality = MutableStateFlow("Auto (1080p)")
    val videoQuality: StateFlow<String> = _videoQuality.asStateFlow()

    private val _skipSilence = MutableStateFlow(false)
    val skipSilence: StateFlow<Boolean> = _skipSilence.asStateFlow()

    private val _maxCacheSizeMb = MutableStateFlow(1024)
    val maxCacheSizeMb: StateFlow<Int> = _maxCacheSizeMb.asStateFlow()

    private val _loudnessNormalization = MutableStateFlow(true)
    val loudnessNormalization: StateFlow<Boolean> = _loudnessNormalization.asStateFlow()

    private val _sponsorBlockEnabled = MutableStateFlow(true)
    val sponsorBlockEnabled: StateFlow<Boolean> = _sponsorBlockEnabled.asStateFlow()

    private val _crossfadeSeconds = MutableStateFlow(4)
    val crossfadeSeconds: StateFlow<Int> = _crossfadeSeconds.asStateFlow()

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

        val storage = AuraStorageRepository(ctx)
        _equalizerBands.value = storage.loadEqualizerBands()
        _equalizerPreamp.value = storage.loadEqualizerPreamp()
        val savedPreset = storage.loadEqualizerPreset()
        _activeEqPresetName.value = savedPreset
        _activeEqPreset.value = SIMPMUSIC_ALL_EQ_PRESETS.firstOrNull { it.name == savedPreset }
            ?: SIMPMUSIC_ALL_EQ_PRESETS.first()
        _audioQuality.value = storage.loadAudioQuality()
        _videoQuality.value = storage.loadVideoQuality()
        _loudnessNormalization.value = storage.loadNormalizeVolume()
        _skipSilence.value = storage.loadSkipSilence()
        _crossfadeSeconds.value = storage.loadCrossfadeDuration()
        _maxCacheSizeMb.value = storage.loadMaxCacheSize()

        SimpMusicExtractorEngine.initExtractors()

        // 1. SimpMusic SimpleCache (512 MB disk cache for 0ms replays & instant scrubbing)
        val cacheDir = File(ctx.cacheDir, "aura_media3_cache")
        val dbProvider = StandaloneDatabaseProvider(ctx)
        val cache = SimpleCache(cacheDir, LeastRecentlyUsedCacheEvictor(MAX_CACHE_BYTES), dbProvider)
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
                    _streamSourceTier.value = "Offline • Lossless"
                    return@Factory dataSpec.buildUpon()
                        .setUri(Uri.fromFile(offlineFile))
                        .setKey(videoId)
                        .build()
                }

                // Otherwise resolve via SimpMusicExtractorEngine (Tier 0 memory cache -> Tier 1 PipePipe -> Tier 2 NewPipe -> Tier 3 Edge)
                val resolved = runBlocking(Dispatchers.IO) {
                    SimpMusicExtractorEngine.resolveStream(videoId)
                }
                _streamSourceTier.value = if (resolved.bitrateKbps >= 140) "Ultra HD • ${resolved.bitrateKbps} KBPS" else "High Quality Audio"

                return@Factory dataSpec.buildUpon()
                    .setUri(Uri.parse(resolved.streamUrl))
                    .setKey(videoId)
                    .build()
            }

            if (uri.scheme == "aura" && uri.host == "video") {
                val videoId = uri.lastPathSegment ?: throw PlaybackException(
                    "Missing videoId",
                    null,
                    PlaybackException.ERROR_CODE_IO_UNSPECIFIED
                )

                _isVideoLoading.value = true
                val resolvedVideo = runBlocking(Dispatchers.IO) {
                    SimpMusicExtractorEngine.resolveVideoStream(videoId)
                }
                _isVideoLoading.value = false

                if (resolvedVideo != null) {
                    _streamSourceTier.value = "Video HD • ${resolvedVideo.quality}"
                    return@Factory dataSpec.buildUpon()
                        .setUri(Uri.parse(resolvedVideo.streamUrl))
                        .setKey("video_$videoId")
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

        // 4. LoadControl with a robust rebuffer safety cushion to prevent endless pause/play loops
        val loadControl = DefaultLoadControl.Builder()
            .setBufferDurationsMs(
                30_000, // minBufferMs: attempt to buffer up to 30s ahead
                60_000, // maxBufferMs: keep maximum 60s in buffer
                1_500,  // bufferForPlaybackMs: quick start with 1.5s buffer
                4_000   // bufferForPlaybackAfterRebufferMs: MUST collect 4.0s of buffer before resuming after running dry!
            )
            .setPrioritizeTimeOverSizeThresholds(true)
            .setBackBuffer(15_000, true) // Retain 15s behind current position for instant rewind
            .build()

        val audioAttributes = AudioAttributes.Builder()
            .setUsage(C.USAGE_MEDIA)
            .setContentType(C.AUDIO_CONTENT_TYPE_MUSIC)
            .build()

        val renderersFactory = DefaultRenderersFactory(ctx)
            .setExtensionRendererMode(DefaultRenderersFactory.EXTENSION_RENDERER_MODE_PREFER)

        val player = ExoPlayer.Builder(ctx, renderersFactory)
            .setMediaSourceFactory(DefaultMediaSourceFactory(resolvingFactory))
            .setLoadControl(loadControl)
            .setAudioAttributes(audioAttributes, true)
            .setHandleAudioBecomingNoisy(true)
            .setWakeMode(C.WAKE_MODE_NETWORK)
            .build()
        player.skipSilenceEnabled = _skipSilence.value

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
                    bindHardwareEqualizer(player.audioSessionId)
                } else if (playbackState == Player.STATE_ENDED) {
                    _currentTrack.value?.let { finished ->
                        onTrackFinishedCallback?.invoke(finished)
                    }
                    skipToNext(recordSkip = false)
                }
            }

            override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
                fadeJob?.cancel()
                player.volume = baseVolume
                val mediaId = mediaItem?.mediaId ?: return
                val matched = _queue.value.firstOrNull { it.videoId == mediaId || it.id == mediaId }
                if (matched != null) {
                    _currentTrack.value = matched
                    _durationSec.value = matched.durationSeconds.toFloat().coerceAtLeast(1f)
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

        val dedupedNewQueue = newQueue?.distinctBy { if (it.id.isNotBlank()) it.id else it.videoId }
        if (!dedupedNewQueue.isNullOrEmpty()) {
            originalQueue = dedupedNewQueue
        } else if (originalQueue.isEmpty()) {
            originalQueue = _queue.value
        }

        val baseQueue = when {
            !dedupedNewQueue.isNullOrEmpty() -> dedupedNewQueue
            _queue.value.any { it.id == track.id || it.videoId == track.videoId } -> _queue.value
            else -> listOf(track) + _queue.value
        }

        val updatedQueue = if (_isShuffleEnabled.value) {
            val others = baseQueue.filterNot { it.id == track.id || it.videoId == track.videoId }.shuffled()
            listOf(track) + others
        } else {
            val idx = baseQueue.indexOfFirst { it.id == track.id || it.videoId == track.videoId }
            if (idx >= 0) {
                baseQueue.subList(idx, baseQueue.size) + baseQueue.subList(0, idx)
            } else {
                listOf(track) + baseQueue
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
                val radio = SimpMusicExtractorEngine.fetchRadioQueue(track.videoId)
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
                val radio = SimpMusicExtractorEngine.fetchRadioQueue(cur.videoId)
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

    fun setSponsorSegments(segments: List<SponsorSegment>) {
        _sponsorSegments.value = segments
    }

    fun setSponsorBlockEnabled(enabled: Boolean) {
        _sponsorBlockEnabled.value = enabled
    }

    fun setEqualizerBands(bands: List<Float>) {
        _equalizerBands.value = bands
        val match = findMatchingPreset(bands)
        _activeEqPresetName.value = match?.name ?: "Custom"
        if (match != null) {
            _activeEqPreset.value = match
        }
        applyEqualizerCurve()
        appContext?.let {
            val storage = AuraStorageRepository(it)
            storage.saveEqualizerBands(bands)
            storage.saveEqualizerPreset(_activeEqPresetName.value)
        }
    }

    fun setEqualizerPreamp(preampDb: Float) {
        _equalizerPreamp.value = preampDb
        applyEqualizerCurve()
        appContext?.let { AuraStorageRepository(it).saveEqualizerPreamp(preampDb) }
    }

    fun applyEqualizerPreset(preset: EqualizerPreset) {
        _activeEqPreset.value = preset
        _activeEqPresetName.value = preset.name
        _equalizerBands.value = preset.bandsDb
        _equalizerPreamp.value = preset.preampDb
        applyEqualizerCurve()
        appContext?.let {
            val storage = AuraStorageRepository(it)
            storage.saveEqualizerBands(preset.bandsDb)
            storage.saveEqualizerPreamp(preset.preampDb)
            storage.saveEqualizerPreset(preset.name)
        }
    }

    fun resetEqualizer() {
        val flat = SIMPMUSIC_ALL_EQ_PRESETS.firstOrNull { it.id == "flat" }
            ?: EqualizerPreset("flat", "Flat", "Flat response", listOf(0f, 0f, 0f, 0f, 0f, 0f, 0f, 0f, 0f, 0f))
        applyEqualizerPreset(flat)
    }

    fun selectEqualizerPreset(preset: EqualizerPreset) {
        applyEqualizerPreset(preset)
    }

    fun setEqualizerEnabled(enabled: Boolean) {
        _eqEnabled.value = enabled
        applyEqualizerCurve()
    }

    fun setLoudnessNormalization(enabled: Boolean) {
        _loudnessNormalization.value = enabled
        applyEqualizerCurve()
        appContext?.let { AuraStorageRepository(it).saveNormalizeVolume(enabled) }
    }

    fun setAudioQuality(quality: String) {
        _audioQuality.value = quality
        appContext?.let { AuraStorageRepository(it).saveAudioQuality(quality) }
    }

    fun setVideoQuality(quality: String) {
        _videoQuality.value = quality
        appContext?.let { AuraStorageRepository(it).saveVideoQuality(quality) }
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
        _crossfadeSeconds.value = seconds.coerceIn(0, 12)
        appContext?.let { AuraStorageRepository(it).saveCrossfadeDuration(seconds) }
    }

    fun setSleepTimerMinutes(minutes: Int?) {
        sleepTimerJob?.cancel()
        _sleepTimerRemainingMin.value = minutes
        if (minutes != null && minutes > 0) {
            sleepTimerJob = scope.launch {
                var rem = minutes
                while (rem > 0 && isActive) {
                    delay(60_000L)
                    rem--
                    _sleepTimerRemainingMin.value = rem
                }
                if (rem <= 0) {
                    exoPlayer?.pause()
                    _sleepTimerRemainingMin.value = null
                }
            }
        }
    }

    fun getDedicatedDownloadDir(context: Context): File {
        try {
            val publicMusic = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_MUSIC)
            val auraPublic = File(publicMusic, "AuraMusic")
            if (auraPublic.exists() || auraPublic.mkdirs()) {
                return auraPublic
            }
        } catch (e: Exception) {
            Log.w(TAG, "Cannot access public Music directory: ${e.message}")
        }
        val extDir = context.getExternalFilesDir(Environment.DIRECTORY_MUSIC)
        val auraExt = File(extDir, "AuraMusic")
        if (auraExt.exists() || auraExt.mkdirs()) {
            return auraExt
        }
        val internalDir = File(context.filesDir, "aura_offline_tracks")
        if (!internalDir.exists()) internalDir.mkdirs()
        return internalDir
    }

    fun getOfflineFile(context: Context, videoId: String): File {
        val dedicated = File(getDedicatedDownloadDir(context), "$videoId.m4a")
        if (dedicated.exists() && dedicated.length() > 10_000L) return dedicated
        val fallback = File(context.filesDir, "aura_offline_tracks/$videoId.m4a")
        if (fallback.exists() && fallback.length() > 10_000L) return fallback
        return dedicated
    }

    suspend fun downloadTrackOffline(
        context: Context,
        track: AuraTrack,
        onProgress: (bytesRead: Long, totalBytes: Long, progress: Float) -> Unit = { _, _, _ -> }
    ): File? = withContext(Dispatchers.IO) {
        try {
            val resolved = SimpMusicExtractorEngine.resolveStream(track.videoId)
            val targetFile = getOfflineFile(context, track.videoId)
            val req = Request.Builder()
                .url(resolved.streamUrl)
                .header(
                    "User-Agent",
                    "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/131.0.0.0 Safari/537.36"
                )
                .build()

            SimpMusicExtractorEngine.httpClient.newCall(req).execute().use { resp ->
                if (!resp.isSuccessful) return@withContext null
                val body = resp.body ?: return@withContext null
                val totalBytes = body.contentLength().takeIf { it > 0 } ?: (track.durationSeconds * 20_000L)
                var bytesReadTotal = 0L

                targetFile.outputStream().use { out ->
                    body.byteStream().use { input ->
                        val buffer = ByteArray(16 * 1024)
                        var read: Int
                        var lastProgressUpdate = 0L
                        while (input.read(buffer).also { read = it } != -1) {
                            out.write(buffer, 0, read)
                            bytesReadTotal += read
                            val now = System.currentTimeMillis()
                            if (now - lastProgressUpdate > 100 || bytesReadTotal == totalBytes) {
                                lastProgressUpdate = now
                                val progress = (bytesReadTotal.toFloat() / totalBytes.coerceAtLeast(1L)).coerceIn(0f, 1f)
                                onProgress(bytesReadTotal, totalBytes, progress)
                            }
                        }
                        onProgress(bytesReadTotal, totalBytes, 1f)
                    }
                }
            }
            if (targetFile.exists() && targetFile.length() > 10_000L) targetFile else null
        } catch (e: Exception) {
            Log.e(TAG, "Offline download failed for ${track.videoId}: ${e.message}")
            null
        }
    }

    fun setVideoLoading(loading: Boolean) {
        _isVideoLoading.value = loading
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
            val resolved = withContext(Dispatchers.IO) {
                SimpMusicExtractorEngine.resolveVideoStream(videoTrack.videoId)
            }
            _isVideoLoading.value = false

            val player = exoPlayer ?: return@launch
            if (resolved != null) {
                _streamSourceTier.value = "Video HD • ${resolved.quality}"
                val cFactory = cacheDataSourceFactory ?: DefaultDataSource.Factory(appContext!!)

                val mediaSource = if (!resolved.audioUrl.isNullOrBlank()) {
                    // Combine HD 1080p/720p video stream + high-fidelity 160kbps audio stream!
                    val videoSource = ProgressiveMediaSource.Factory(cFactory)
                        .createMediaSource(MediaItem.fromUri(Uri.parse(resolved.streamUrl)))
                    val audioSource = ProgressiveMediaSource.Factory(cFactory)
                        .createMediaSource(MediaItem.fromUri(Uri.parse(resolved.audioUrl)))
                    MergingMediaSource(videoSource, audioSource)
                } else {
                    ProgressiveMediaSource.Factory(cFactory)
                        .createMediaSource(MediaItem.fromUri(Uri.parse(resolved.streamUrl)))
                }

                player.setMediaSource(mediaSource, currentPos)
                player.prepare()
                if (wasPlaying) {
                    player.play()
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
    fun playStandaloneVideo(videoTrack: AuraTrack, videoQueue: List<AuraTrack>) {
        val previousTrack = _currentTrack.value
        val previousQueue = _queue.value
        val wasPlaying = exoPlayer?.isPlaying == true
        exoPlayer?.pause()
        originalAudioTrack = null
        originalAudioQueue = null
        originalAudioIndex = -1
        originalQueue = videoQueue
        _queue.value = videoQueue
        _positionSec.value = 0f
        _bufferedSec.value = 0f
        _durationSec.value = videoTrack.durationSeconds.toFloat().coerceAtLeast(1f)
        _isVideoMode.value = true
        _currentTrack.value = videoTrack.copy(
            artworkUrl = "https://i.ytimg.com/vi/${videoTrack.videoId}/hqdefault.jpg",
            musicVideoType = videoTrack.musicVideoType ?: "MUSIC_VIDEO_TYPE_OMV"
        )

        scope.launch {
            _isVideoLoading.value = true
            val resolved = withContext(Dispatchers.IO) {
                SimpMusicExtractorEngine.resolveVideoStream(videoTrack.videoId)
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

            _streamSourceTier.value = "Video HD • ${resolved.quality}"
            val dataSourceFactory = cacheDataSourceFactory ?: DefaultDataSource.Factory(appContext!!)
            val mediaSource = if (!resolved.audioUrl.isNullOrBlank()) {
                val videoSource = ProgressiveMediaSource.Factory(dataSourceFactory)
                    .createMediaSource(MediaItem.fromUri(Uri.parse(resolved.streamUrl)))
                val audioSource = ProgressiveMediaSource.Factory(dataSourceFactory)
                    .createMediaSource(MediaItem.fromUri(Uri.parse(resolved.audioUrl)))
                MergingMediaSource(videoSource, audioSource)
            } else {
                ProgressiveMediaSource.Factory(dataSourceFactory)
                    .createMediaSource(MediaItem.fromUri(Uri.parse(resolved.streamUrl)))
            }
            player.setMediaSource(mediaSource, 0L)
            player.prepare()
            player.play()
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
        val origIndex = originalAudioIndex

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
        } else {
            val cur = currentTrack.value ?: return
            val mediaItem = buildMediaItem(cur)
            player.setMediaItem(mediaItem, currentPos)
            player.prepare()
            if (wasPlaying) {
                player.play()
            }
        }
    }

    fun setVideoMode(enabled: Boolean) {
        if (_isVideoMode.value == enabled) return
        if (!enabled) {
            switchToAudioMode()
        } else {
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
        if (idx >= 0) {
            val nextTrack = list.getOrNull(idx + 1)
            if (nextTrack != null) {
                if (nextTrack.isPendingResolution) {
                    // Auto-prefetch: Silently resolve the next track's YouTube ID in background
                    scope.launch {
                        val resolvedNext = SimpMusicExtractorEngine.resolveSpotifyPendingTrack(nextTrack)
                        if (!resolvedNext.isPendingResolution) {
                            withContext(Dispatchers.Main) {
                                val currentQ = _queue.value.toMutableList()
                                val qIdx = currentQ.indexOfFirst { it.id == nextTrack.id }
                                if (qIdx >= 0) {
                                    currentQ[qIdx] = resolvedNext
                                    _queue.value = currentQ
                                }
                                val origIdx = originalQueue.indexOfFirst { it.id == nextTrack.id }
                                if (origIdx >= 0 && origIdx < originalQueue.size) {
                                    val origList = originalQueue.toMutableList()
                                    origList[origIdx] = resolvedNext
                                    originalQueue = origList
                                }
                                onTrackResolvedCallback?.invoke(resolvedNext)
                            }
                            SimpMusicExtractorEngine.prefetchStream(resolvedNext.videoId)
                        }
                    }
                } else {
                    SimpMusicExtractorEngine.prefetchStream(nextTrack.videoId)
                }
            }

            val nextNextTrack = list.getOrNull(idx + 2)
            if (nextNextTrack != null) {
                if (nextNextTrack.isPendingResolution) {
                    scope.launch {
                        val resolvedNextNext = SimpMusicExtractorEngine.resolveSpotifyPendingTrack(nextNextTrack)
                        if (!resolvedNextNext.isPendingResolution) {
                            withContext(Dispatchers.Main) {
                                val currentQ = _queue.value.toMutableList()
                                val qIdx = currentQ.indexOfFirst { it.id == nextNextTrack.id }
                                if (qIdx >= 0) {
                                    currentQ[qIdx] = resolvedNextNext
                                    _queue.value = currentQ
                                }
                                val origIdx = originalQueue.indexOfFirst { it.id == nextNextTrack.id }
                                if (origIdx >= 0 && origIdx < originalQueue.size) {
                                    val origList = originalQueue.toMutableList()
                                    origList[origIdx] = resolvedNextNext
                                    originalQueue = origList
                                }
                                onTrackResolvedCallback?.invoke(resolvedNextNext)
                            }
                            SimpMusicExtractorEngine.prefetchStream(resolvedNextNext.videoId)
                        }
                    }
                } else {
                    SimpMusicExtractorEngine.prefetchStream(nextNextTrack.videoId)
                }
            }
        }
    }

    private fun bindHardwareEqualizer(audioSessionId: Int) {
        if (audioSessionId == C.AUDIO_SESSION_ID_UNSET || audioSessionId <= 0) return
        try {
            hardwareEq?.release()
            hardwareEq = Equalizer(0, audioSessionId).apply {
                enabled = _eqEnabled.value
            }
            applyEqualizerCurve()
        } catch (e: Exception) {
            Log.w(TAG, "Hardware Equalizer binding notice: ${e.message}")
        }
    }

    private fun applyEqualizerCurve() {
        val player = exoPlayer ?: return
        val enabled = _eqEnabled.value
        val bands = _equalizerBands.value
        val preampDb = _equalizerPreamp.value

        try {
            val eq = hardwareEq
            if (eq != null) {
                eq.enabled = enabled
                val numBands = eq.numberOfBands.toInt()
                val range = eq.bandLevelRange // millibels, e.g. [-1500, 1500]
                val minMb = range[0].toInt()
                val maxMb = range[1].toInt()

                for (b in 0 until numBands) {
                    val mappedIdx = ((b.toFloat() / numBands.coerceAtLeast(1)) * bands.size)
                        .toInt()
                        .coerceIn(0, bands.lastIndex)
                    val targetMb = if (enabled) (bands[mappedIdx] * 100f).toInt() else 0
                    eq.setBandLevel(b.toShort(), targetMb.coerceIn(minMb, maxMb).toShort())
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "Equalizer curve apply notice: ${e.message}")
        }

        // Anti-Clipping Headroom: calculate max band boost and apply negative headroom
        // so boosted low/high frequencies never clip against 0 dBFS ceiling
        val maxBoost = if (enabled) bands.maxOrNull()?.coerceAtLeast(0f) ?: 0f else 0f
        val safeHeadroom = if (maxBoost > 0f) Math.pow(10.0, -maxBoost.toDouble() / 20.0).toFloat() else 1.0f
        val preampFactor = if (enabled) Math.pow(10.0, preampDb.toDouble() / 20.0).toFloat() else 1.0f
        val gain = if (enabled) (preampFactor * safeHeadroom) else 1.0f
        baseVolume = gain.coerceIn(0.15f, 1.0f)
        if (fadeJob?.isActive != true) {
            player.volume = baseVolume
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

                        // Smooth audio fade out effect when the song is finishing (last 2 seconds)
                        val remainingMs = durMs - player.currentPosition
                        if (player.isPlaying && remainingMs in 50L..2000L && fadeJob?.isActive != true) {
                            val fadeFactor = (remainingMs / 2000f).coerceIn(0f, 1f)
                            player.volume = (baseVolume * fadeFactor).coerceIn(0f, 1f)
                        } else if (remainingMs > 2000L && fadeJob?.isActive != true && player.volume < baseVolume) {
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
