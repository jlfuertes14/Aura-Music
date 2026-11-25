package com.auramusic.app.core.media

import android.content.Context
import android.os.Environment
import android.util.Log
import androidx.annotation.OptIn
import coil.Coil
import coil.annotation.ExperimentalCoilApi
import com.auramusic.app.core.extractor.SimpMusicExtractorEngine
import com.auramusic.app.core.lyrics.SimpMusicLyricsRepository
import com.auramusic.app.data.AuraStorageRepository
import com.auramusic.app.domain.model.AuraPalette
import com.auramusic.app.domain.model.AuraTrack
import com.auramusic.app.domain.model.isPendingResolution
import com.auramusic.app.ui.theme.extractAuraPaletteFromUrl
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.Request
import java.io.File
import java.io.IOException
import java.io.RandomAccessFile
import java.nio.ByteBuffer
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicLong

enum class DownloadStatus {
    QUEUED,
    DOWNLOADING,
    PAUSED,
    COMPLETED,
    FAILED,
    CANCELLED
}

data class DownloadItemState(
    val track: AuraTrack,
    val status: DownloadStatus = DownloadStatus.QUEUED,
    val progress: Float = 0f,
    val bytesRead: Long = 0L,
    val totalBytes: Long = 0L,
    val errorMessage: String? = null
)

data class OfflineAssetBundle(
    val artworkUri: String?,
    val hasLyrics: Boolean,
    val hasPalette: Boolean
)

/**
 * Encapsulates offline audio downloading (4-connection 2 MB ranged chunk downloader with
 * single-stream fallback), queue state (`activeDownloads`, pause/resume/cancel, progress flows),
 * offline artwork/lyrics/palette bundling, and file deletion/storage sizing.
 */
class AuraOfflineDownloader(
    private val scope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.IO),
    private val paletteExtractor: suspend (artworkUrl: String, context: Context) -> AuraPalette? =
        { url, ctx -> extractAuraPaletteFromUrl(url, ctx) }
) {
    companion object {
        private const val TAG = "AuraOfflineDownloader"
        private const val DOWNLOAD_USER_AGENT =
            "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/131.0.0.0 Safari/537.36"
        private const val DOWNLOAD_CHUNK_BYTES = 2L * 1024 * 1024
        private const val DOWNLOAD_PARALLELISM = 4
        private const val MIN_VALID_AUDIO_BYTES = 10_000L
        private const val MIN_VALID_ARTWORK_BYTES = 500L
    }

    private data class QueuedDownloadTask(
        val context: Context,
        val track: AuraTrack,
        val audioQuality: String,
        val bundleAssets: Boolean,
        val onCompleted: ((AuraTrack, File?) -> Unit)? = null
    )

    private val queueLock = Any()
    private val downloadQueue = ArrayDeque<QueuedDownloadTask>()
    private var workerJob: Job? = null
    private var activeTrackJob: Job? = null
    @Volatile
    private var activeVideoId: String? = null

    private val _activeDownloads = MutableStateFlow<Map<String, DownloadItemState>>(emptyMap())
    val activeDownloads: StateFlow<Map<String, DownloadItemState>> = _activeDownloads.asStateFlow()

    private val _downloadProgress = MutableStateFlow<Map<String, Float>>(emptyMap())
    val downloadProgress: StateFlow<Map<String, Float>> = _downloadProgress.asStateFlow()

    private val _downloadingIds = MutableStateFlow<Set<String>>(emptySet())
    val downloadingIds: StateFlow<Set<String>> = _downloadingIds.asStateFlow()

    private val _isPaused = MutableStateFlow(false)
    val isPaused: StateFlow<Boolean> = _isPaused.asStateFlow()

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

    fun getOfflineArtworkFile(context: Context, videoId: String): File {
        val dir = File(context.filesDir, "aura_offline_artwork").apply { if (!exists()) mkdirs() }
        return File(dir, "$videoId.jpg")
    }

    fun getOfflineArtworkUri(context: Context, videoId: String): String? {
        val f = getOfflineArtworkFile(context, videoId)
        return if (f.exists() && f.length() > MIN_VALID_ARTWORK_BYTES) "file://${f.absolutePath}" else null
    }

    fun withOfflineArtwork(context: Context?, track: AuraTrack): AuraTrack {
        val ctx = context ?: return track
        val localUri = getOfflineArtworkUri(ctx, track.videoId) ?: return track
        return track.copy(artworkUrl = localUri)
    }

    @OptIn(ExperimentalCoilApi::class)
    suspend fun saveOfflineArtwork(context: Context, track: AuraTrack): String? = withContext(Dispatchers.IO) {
        getOfflineArtworkUri(context, track.videoId)?.let { return@withContext it }
        val target = getOfflineArtworkFile(context, track.videoId)
        val artUrl = track.highResArtworkUrl.ifBlank { track.artworkUrl }
        if (artUrl.isBlank()) return@withContext null
        if (artUrl.startsWith("file://") || artUrl.startsWith("/")) {
            val src = File(artUrl.removePrefix("file://"))
            if (src.exists() && src.length() > MIN_VALID_ARTWORK_BYTES) {
                runCatching { src.copyTo(target, overwrite = true) }
                return@withContext getOfflineArtworkUri(context, track.videoId)
            }
        }
        try {
            val diskCache = Coil.imageLoader(context).diskCache
            val snap = diskCache?.openSnapshot(track.highResArtworkUrl)
                ?: diskCache?.openSnapshot(track.artworkUrl)
            if (snap != null) {
                snap.use { it.data.toFile().copyTo(target, overwrite = true) }
                getOfflineArtworkUri(context, track.videoId)?.let { return@withContext it }
            }
        } catch (_: Exception) {}
        try {
            SimpMusicExtractorEngine.httpClient.newCall(downloadRequest(artUrl)).execute().use { resp ->
                if (!resp.isSuccessful) return@withContext null
                val bytes = resp.body?.bytes() ?: return@withContext null
                if (bytes.size > MIN_VALID_ARTWORK_BYTES) {
                    target.writeBytes(bytes)
                    return@withContext "file://${target.absolutePath}"
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "Failed to save offline artwork for ${track.videoId}: ${e.message}")
        }
        null
    }

    /**
     * Fetches and persists offline artwork, lyrics, and dynamic color palette in parallel
     * so downloaded tracks render rich metadata with zero network access.
     */
    suspend fun bundleOfflineAssets(
        context: Context,
        track: AuraTrack
    ): OfflineAssetBundle = withContext(Dispatchers.IO) {
        val storage = AuraStorageRepository(context)
        coroutineScope {
            val lyricsDef = async {
                try {
                    val lyrics = SimpMusicLyricsRepository.getLyrics(
                        videoId = track.videoId,
                        title = track.title,
                        artist = track.artist,
                        durationSeconds = track.durationSeconds
                    )
                    if (lyrics.source != "None") {
                        storage.saveOfflineLyrics(track.videoId, lyrics)
                        true
                    } else {
                        false
                    }
                } catch (e: Exception) {
                    Log.w(TAG, "Failed to bundle offline lyrics for ${track.videoId}: ${e.message}")
                    false
                }
            }

            val artworkDef = async {
                saveOfflineArtwork(context, track)
            }

            val paletteDef = async {
                try {
                    val palette = paletteExtractor(track.highResArtworkUrl, context)
                    if (palette != null) {
                        storage.saveOfflinePalette(track.videoId, palette)
                        true
                    } else {
                        false
                    }
                } catch (e: Exception) {
                    Log.w(TAG, "Failed to bundle offline palette for ${track.videoId}: ${e.message}")
                    false
                }
            }

            OfflineAssetBundle(
                artworkUri = artworkDef.await(),
                hasLyrics = lyricsDef.await(),
                hasPalette = paletteDef.await()
            )
        }
    }

    fun getOfflineFile(context: Context, videoId: String): File {
        val dedicated = File(getDedicatedDownloadDir(context), "$videoId.m4a")
        if (dedicated.exists() && dedicated.length() > MIN_VALID_AUDIO_BYTES) return dedicated
        val fallback = File(context.filesDir, "aura_offline_tracks/$videoId.m4a")
        if (fallback.exists() && fallback.length() > MIN_VALID_AUDIO_BYTES) return fallback
        return dedicated
    }

    /** Enqueues a single track into the managed offline download worker queue. */
    fun enqueue(
        context: Context,
        track: AuraTrack,
        audioQuality: String = "Auto",
        bundleAssets: Boolean = true,
        onCompleted: ((AuraTrack, File?) -> Unit)? = null
    ) {
        enqueueBatch(context, listOf(track), audioQuality, bundleAssets, onCompleted)
    }

    /** Enqueues multiple tracks into the managed offline download worker queue. */
    fun enqueueBatch(
        context: Context,
        tracks: List<AuraTrack>,
        audioQuality: String = "Auto",
        bundleAssets: Boolean = true,
        onItemCompleted: ((AuraTrack, File?) -> Unit)? = null
    ) {
        if (tracks.isEmpty()) return
        val appCtx = context.applicationContext
        val newStates = mutableMapOf<String, DownloadItemState>()
        val newProgress = mutableMapOf<String, Float>()
        val addedIds = mutableSetOf<String>()

        synchronized(queueLock) {
            for (track in tracks) {
                val id = track.videoId
                if (_downloadingIds.value.contains(id) || addedIds.contains(id)) continue
                addedIds.add(id)
                newStates[id] = DownloadItemState(track = track, status = DownloadStatus.QUEUED, progress = 0f)
                newProgress[id] = 0f
                downloadQueue.addLast(
                    QueuedDownloadTask(
                        context = appCtx,
                        track = track,
                        audioQuality = audioQuality,
                        bundleAssets = bundleAssets,
                        onCompleted = onItemCompleted
                    )
                )
            }

            if (addedIds.isEmpty()) return
            _activeDownloads.update { it + newStates }
            _downloadProgress.update { it + newProgress }
            _downloadingIds.update { it + addedIds }

            if (workerJob?.isActive != true) {
                startWorkerLocked()
            }
        }
    }

    private fun startWorkerLocked() {
        workerJob = scope.launch {
            while (true) {
                _isPaused.first { !it }
                val task = synchronized(queueLock) {
                    if (downloadQueue.isEmpty()) {
                        workerJob = null
                        null
                    } else {
                        downloadQueue.removeFirst()
                    }
                } ?: break

                val rawKey = task.track.videoId
                activeVideoId = rawKey
                updateDownloadState(rawKey) {
                    it.copy(status = DownloadStatus.DOWNLOADING, progress = 0.02f)
                }
                _downloadProgress.update { it + (rawKey to 0.02f) }

                var downloadedFile: File? = null
                var resolvedTrack: AuraTrack = task.track

                val job = scope.launch {
                    val activeTrack = if (task.track.isPendingResolution || task.track.videoId.startsWith("spotify_pending")) {
                        val resolvedPending = SimpMusicExtractorEngine.resolveSpotifyPendingTrack(task.track)
                        if (!resolvedPending.isPendingResolution && !resolvedPending.videoId.startsWith("spotify_pending")) {
                            resolvedTrack = resolvedPending
                            resolvedPending
                        } else {
                            task.track
                        }
                    } else {
                        task.track
                    }

                    val bundleDef = if (task.bundleAssets) {
                        async { bundleOfflineAssets(task.context, activeTrack) }
                    } else null

                    val file = downloadTrackOffline(
                        context = task.context,
                        track = activeTrack,
                        audioQuality = task.audioQuality,
                        awaitResume = { _isPaused.first { !it } }
                    ) { bytesRead, totalBytes, progress ->
                        if (!isActive) return@downloadTrackOffline
                        val clamped = progress.coerceAtLeast(0.02f)
                        _downloadProgress.update { current ->
                            if (_downloadingIds.value.contains(rawKey)) current + (rawKey to clamped) else current
                        }
                        updateDownloadState(rawKey) {
                            it.copy(
                                status = if (_isPaused.value) DownloadStatus.PAUSED else DownloadStatus.DOWNLOADING,
                                progress = clamped,
                                bytesRead = bytesRead,
                                totalBytes = totalBytes
                            )
                        }
                    }

                    val bundle = bundleDef?.await()
                    if (bundle?.artworkUri != null) {
                        resolvedTrack = resolvedTrack.copy(artworkUrl = bundle.artworkUri)
                    }
                    downloadedFile = file
                }

                synchronized(queueLock) {
                    activeTrackJob = job
                }
                try {
                    job.join()
                } finally {
                    synchronized(queueLock) {
                        if (activeTrackJob == job) activeTrackJob = null
                        activeVideoId = null
                    }
                }

                val succeeded = downloadedFile != null &&
                    downloadedFile!!.exists() &&
                    downloadedFile!!.length() > MIN_VALID_AUDIO_BYTES

                if (succeeded) {
                    resolvedTrack = resolvedTrack.copy(
                        isDownloaded = true,
                        localFilePath = downloadedFile!!.absolutePath
                    )
                    updateDownloadState(rawKey) {
                        it.copy(track = resolvedTrack, status = DownloadStatus.COMPLETED, progress = 1f)
                    }
                } else if (!job.isCancelled) {
                    updateDownloadState(rawKey) {
                        it.copy(status = DownloadStatus.FAILED, errorMessage = "Download failed")
                    }
                }

                _downloadingIds.update { it - rawKey }
                _downloadProgress.update { it - rawKey }
                _activeDownloads.update { it - rawKey }

                if (!job.isCancelled) {
                    task.onCompleted?.invoke(resolvedTrack, downloadedFile)
                }
            }
        }
    }

    private fun updateDownloadState(videoId: String, transform: (DownloadItemState) -> DownloadItemState) {
        _activeDownloads.update { map ->
            val current = map[videoId] ?: return@update map
            map + (videoId to transform(current))
        }
    }

    fun pauseDownloads() {
        if (_downloadingIds.value.isEmpty()) return
        _isPaused.value = true
        _activeDownloads.update { map ->
            map.mapValues { (_, item) ->
                if (item.status == DownloadStatus.DOWNLOADING) item.copy(status = DownloadStatus.PAUSED) else item
            }
        }
    }

    fun resumeDownloads() {
        _isPaused.value = false
        _activeDownloads.update { map ->
            map.mapValues { (_, item) ->
                if (item.status == DownloadStatus.PAUSED) item.copy(status = DownloadStatus.DOWNLOADING) else item
            }
        }
    }

    /** Cancels a single queued or active download by [videoId]. */
    fun cancelDownload(videoId: String) {
        var jobToCancel: Job? = null
        synchronized(queueLock) {
            downloadQueue.removeAll { it.track.videoId == videoId }
            if (activeVideoId == videoId) {
                jobToCancel = activeTrackJob
            }
        }
        jobToCancel?.cancel()
        _downloadingIds.update { it - videoId }
        _downloadProgress.update { it - videoId }
        _activeDownloads.update { it - videoId }
    }

    /** Stops the active download, clears the queue, and resets download tracking state. */
    fun cancelAllDownloads() {
        val workerToCancel: Job?
        val trackJobToCancel: Job?
        synchronized(queueLock) {
            workerToCancel = workerJob
            trackJobToCancel = activeTrackJob
            workerJob = null
            activeTrackJob = null
            activeVideoId = null
            downloadQueue.clear()
        }
        trackJobToCancel?.cancel()
        workerToCancel?.cancel()
        _isPaused.value = false
        _downloadingIds.update { emptySet() }
        _downloadProgress.update { emptyMap() }
        _activeDownloads.update { emptyMap() }
    }

    /** Deletes offline audio, artwork, and any partial temp files for [track]. */
    fun deleteOfflineTrack(context: Context, track: AuraTrack): Boolean {
        var deletedAny = false
        try {
            track.localFilePath?.let { path ->
                val f = File(path)
                if (f.exists() && f.delete()) deletedAny = true
            }
            val dedicated = getOfflineFile(context, track.videoId)
            if (dedicated.exists() && dedicated.delete()) deletedAny = true
            val fallback = File(context.filesDir, "aura_offline_tracks/${track.videoId}.m4a")
            if (fallback.exists() && fallback.delete()) deletedAny = true
            val offlineArt = getOfflineArtworkFile(context, track.videoId)
            if (offlineArt.exists() && offlineArt.delete()) deletedAny = true
            val partFile = File(File(context.cacheDir, "aura_dl_tmp"), "${track.videoId}.m4a.part")
            if (partFile.exists()) partFile.delete()
        } catch (e: Exception) {
            Log.w(TAG, "Error deleting offline files for ${track.videoId}: ${e.message}")
        }
        return deletedAny
    }

    /** Deletes offline audio and artwork files for all [tracks]. */
    fun deleteAllOfflineTracks(context: Context, tracks: List<AuraTrack>) {
        tracks.forEach { deleteOfflineTrack(context, it) }
    }

    /** Returns the on-disk audio file size in bytes for a downloaded [track], or `0L`. */
    fun getOfflineTrackSizeBytes(context: Context, track: AuraTrack): Long {
        val file = track.localFilePath?.let { File(it) } ?: getOfflineFile(context, track.videoId)
        return if (file.exists()) file.length() else 0L
    }

    /** Calculates total bytes occupied by all downloaded [tracks]. */
    fun calculateTotalDownloadsSizeBytes(context: Context, tracks: List<AuraTrack>): Long {
        return tracks.sumOf { getOfflineTrackSizeBytes(context, it) }
    }

    /** Recursively calculates total bytes used inside [dir]. */
    fun calculateDirectorySizeBytes(dir: File?): Long {
        if (dir == null || !dir.exists()) return 0L
        if (dir.isFile) return dir.length()
        val children = dir.listFiles() ?: return 0L
        var total = 0L
        for (child in children) {
            total += if (child.isDirectory) calculateDirectorySizeBytes(child) else child.length()
        }
        return total
    }

    private suspend fun checkActive() = currentCoroutineContext().ensureActive()

    private fun downloadRequest(url: String, rangeStart: Long? = null, rangeEnd: Long? = null): Request {
        val builder = Request.Builder().url(url).header("User-Agent", DOWNLOAD_USER_AGENT)
        if (rangeStart != null && rangeEnd != null) builder.header("Range", "bytes=$rangeStart-$rangeEnd")
        return builder.build()
    }

    /** Returns total size in bytes if the server honours HTTP range requests, else null. */
    private fun probeRangeSupport(url: String): Long? = try {
        SimpMusicExtractorEngine.httpClient.newCall(downloadRequest(url, 0, 0)).execute().use { resp ->
            if (resp.code != 206) return@use null
            resp.header("Content-Range")?.substringAfterLast('/')?.trim()?.toLongOrNull()
        }
    } catch (_: IOException) {
        null
    }

    /**
     * Downloads the stream in ranged chunks over several parallel connections. YouTube throttles a
     * single unbounded request, while small ranged requests run at full speed.
     */
    private suspend fun downloadChunked(
        url: String,
        part: File,
        total: Long,
        awaitResume: suspend () -> Unit,
        onProgress: (Long, Long, Float) -> Unit
    ) = coroutineScope {
        RandomAccessFile(part, "rw").use { raf ->
            raf.setLength(total)
            val channel = raf.channel
            val chunkCount = ((total + DOWNLOAD_CHUNK_BYTES - 1) / DOWNLOAD_CHUNK_BYTES).toInt()
            val nextChunk = AtomicInteger(0)
            val downloaded = AtomicLong(0)
            val lastReport = AtomicLong(0)

            val workers = (1..minOf(DOWNLOAD_PARALLELISM, chunkCount)).map {
                async {
                    while (true) {
                        awaitResume()
                        checkActive()
                        val idx = nextChunk.getAndIncrement()
                        if (idx >= chunkCount) break
                        val start = idx * DOWNLOAD_CHUNK_BYTES
                        val end = minOf(start + DOWNLOAD_CHUNK_BYTES, total) - 1
                        var attempt = 0
                        while (true) {
                            var written = 0L
                            try {
                                SimpMusicExtractorEngine.httpClient
                                    .newCall(downloadRequest(url, start, end)).execute().use { resp ->
                                        if (resp.code != 206) throw IOException("Range not honoured: ${resp.code}")
                                        val input = resp.body?.byteStream() ?: throw IOException("Empty body")
                                        val buffer = ByteArray(32 * 1024)
                                        var pos = start
                                        while (true) {
                                            val read = input.read(buffer)
                                            if (read == -1) break
                                            val bb = ByteBuffer.wrap(buffer, 0, read)
                                            while (bb.hasRemaining()) pos += channel.write(bb, pos)
                                            written += read
                                            downloaded.addAndGet(read.toLong())
                                            val now = System.currentTimeMillis()
                                            val last = lastReport.get()
                                            if (now - last > 32 && lastReport.compareAndSet(last, now)) {
                                                val soFar = downloaded.get()
                                                onProgress(soFar, total, (soFar.toFloat() / total).coerceIn(0f, 1f))
                                            }
                                            checkActive()
                                        }
                                    }
                                if (written != end - start + 1) throw IOException("Short chunk")
                                break
                            } catch (e: IOException) {
                                downloaded.addAndGet(-written)
                                if (++attempt >= 3) throw e
                            }
                        }
                    }
                }
            }
            workers.forEach { it.await() }
            onProgress(total, total, 1f)
        }
    }

    private suspend fun downloadSingleStream(
        url: String,
        part: File,
        fallbackTotal: Long,
        awaitResume: suspend () -> Unit,
        onProgress: (Long, Long, Float) -> Unit
    ) {
        SimpMusicExtractorEngine.httpClient.newCall(downloadRequest(url)).execute().use { resp ->
            if (!resp.isSuccessful) throw IOException("HTTP ${resp.code}")
            val body = resp.body ?: throw IOException("Empty body")
            val totalBytes = body.contentLength().takeIf { it > 0 } ?: fallbackTotal
            var bytesReadTotal = 0L
            part.outputStream().use { out ->
                body.byteStream().use { input ->
                    val buffer = ByteArray(32 * 1024)
                    var lastProgressUpdate = 0L
                    while (true) {
                        awaitResume()
                        checkActive()
                        val read = input.read(buffer)
                        if (read == -1) break
                        out.write(buffer, 0, read)
                        bytesReadTotal += read
                        val now = System.currentTimeMillis()
                        if (now - lastProgressUpdate > 32) {
                            lastProgressUpdate = now
                            onProgress(
                                bytesReadTotal,
                                totalBytes,
                                (bytesReadTotal.toFloat() / totalBytes.coerceAtLeast(1L)).coerceIn(0f, 1f)
                            )
                        }
                    }
                    onProgress(bytesReadTotal, totalBytes, 1f)
                }
            }
        }
    }

    suspend fun downloadTrackOffline(
        context: Context,
        track: AuraTrack,
        audioQuality: String = "Auto",
        awaitResume: suspend () -> Unit = {},
        onProgress: (bytesRead: Long, totalBytes: Long, progress: Float) -> Unit = { _, _, _ -> }
    ): File? = withContext(Dispatchers.IO) {
        val activeTrack = if (track.isPendingResolution || track.videoId.startsWith("spotify_pending")) {
            val resolvedPending = SimpMusicExtractorEngine.resolveSpotifyPendingTrack(track)
            if (resolvedPending.isPendingResolution || resolvedPending.videoId.startsWith("spotify_pending")) {
                Log.e(TAG, "Offline download failed: could not resolve pending track ${track.title}")
                return@withContext null
            }
            resolvedPending
        } else {
            track
        }
        val cacheTmpDir = File(context.cacheDir, "aura_dl_tmp").apply { mkdirs() }
        // Download into app-private cache first; Scoped Storage blocks .part files in Music/ with EPERM.
        val partFile = File(cacheTmpDir, "${activeTrack.videoId}.m4a.part")
        try {
            val resolved = SimpMusicExtractorEngine.resolveStream(activeTrack.videoId, audioQuality)
            val url = resolved.streamUrl
            val rangeTotal = probeRangeSupport(url)
            var done = false
            if (rangeTotal != null && rangeTotal > DOWNLOAD_CHUNK_BYTES) {
                try {
                    downloadChunked(url, partFile, rangeTotal, awaitResume, onProgress)
                    done = true
                } catch (e: IOException) {
                    Log.w(TAG, "Chunked download failed for ${activeTrack.videoId}, falling back: ${e.message}")
                }
            }
            if (!done) {
                downloadSingleStream(url, partFile, activeTrack.durationSeconds * 20_000L, awaitResume, onProgress)
            }
            if (partFile.length() > MIN_VALID_AUDIO_BYTES) {
                val primaryTarget = getOfflineFile(context, activeTrack.videoId)
                val fallbackTarget = File(
                    File(context.filesDir, "aura_offline_tracks").apply { mkdirs() },
                    "${activeTrack.videoId}.m4a"
                )
                val saved = moveOrCopyToTarget(partFile, primaryTarget)
                    ?: moveOrCopyToTarget(partFile, fallbackTarget)
                partFile.delete()
                saved
            } else {
                partFile.delete()
                null
            }
        } catch (e: CancellationException) {
            partFile.delete()
            throw e
        } catch (e: Exception) {
            Log.e(TAG, "Offline download failed for ${activeTrack.videoId}: ${e.message}")
            partFile.delete()
            null
        }
    }

    private fun moveOrCopyToTarget(partFile: File, targetFile: File): File? = try {
        targetFile.parentFile?.mkdirs()
        if (targetFile.exists()) targetFile.delete()
        if (!partFile.renameTo(targetFile)) {
            partFile.copyTo(targetFile, overwrite = true)
        }
        if (targetFile.exists() && targetFile.length() > MIN_VALID_AUDIO_BYTES) targetFile else null
    } catch (e: Exception) {
        Log.w(TAG, "Cannot write offline track to ${targetFile.absolutePath}: ${e.message}")
        null
    }
}
