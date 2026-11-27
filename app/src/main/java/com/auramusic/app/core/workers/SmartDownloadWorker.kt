package com.auramusic.app.core.workers

import android.content.Context
import android.util.Log
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import com.auramusic.app.core.extractor.SimpMusicExtractorEngine
import com.auramusic.app.core.media.AuraPlayerManager
import com.auramusic.app.data.AuraStorageRepository
import com.auramusic.app.domain.model.AuraTrack
import com.auramusic.app.domain.model.isPendingResolution
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.concurrent.TimeUnit

object SmartDownloadScheduler {
    private const val PERIODIC_WORK_NAME = "aura_smart_download_periodic"
    private const val IMMEDIATE_WORK_NAME = "aura_smart_download_immediate"

    fun schedule(context: Context, enabled: Boolean, limit: Int) {
        val workManager = WorkManager.getInstance(context.applicationContext)
        if (!enabled) {
            workManager.cancelUniqueWork(PERIODIC_WORK_NAME)
            return
        }
        val normalizedLimit = limit.coerceIn(1, 200)
        val constraints = Constraints.Builder()
            .setRequiredNetworkType(NetworkType.UNMETERED)
            .setRequiresBatteryNotLow(true)
            .build()
        val request = PeriodicWorkRequestBuilder<SmartDownloadWorker>(1, TimeUnit.DAYS)
            .setConstraints(constraints)
            .setInputData(workDataOf(SmartDownloadWorker.KEY_LIMIT to normalizedLimit))
            .build()
        workManager.enqueueUniquePeriodicWork(
            PERIODIC_WORK_NAME,
            ExistingPeriodicWorkPolicy.UPDATE,
            request
        )
    }

    fun runNow(context: Context, limit: Int) {
        val workManager = WorkManager.getInstance(context.applicationContext)
        val normalizedLimit = limit.coerceIn(1, 200)
        val constraints = Constraints.Builder()
            .setRequiredNetworkType(NetworkType.CONNECTED)
            .build()
        val request = OneTimeWorkRequestBuilder<SmartDownloadWorker>()
            .setConstraints(constraints)
            .setInputData(workDataOf(SmartDownloadWorker.KEY_LIMIT to normalizedLimit))
            .build()
        workManager.enqueueUniqueWork(
            IMMEDIATE_WORK_NAME,
            ExistingWorkPolicy.REPLACE,
            request
        )
    }
}

class SmartDownloadWorker(
    appContext: Context,
    params: WorkerParameters
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        try {
            val limit = inputData.getInt(KEY_LIMIT, DEFAULT_LIMIT).coerceIn(1, 200)
            val storage = AuraStorageRepository(applicationContext)
            SimpMusicExtractorEngine.initExtractors()
            SimpMusicExtractorEngine.setStorageRepository(storage)

            val candidates = selectTopCandidates(storage, limit)
            if (candidates.isEmpty()) {
                return@withContext Result.success(
                    workDataOf(KEY_DOWNLOADED_COUNT to 0, KEY_EVICTED_COUNT to 0)
                )
            }

            val existingDownloads = storage.loadDownloads().toMutableList()
            val smartEntries = storage.loadSmartDownloads().associateBy { it.videoId }.toMutableMap()
            val manualVideoIds = existingDownloads
                .map { it.videoId }
                .filter { vid -> vid.isNotBlank() && smartEntries[vid]?.isSmartDownload != true }
                .toSet()

            var downloadedCount = 0
            for (track in candidates) {
                if (isStopped) break
                val videoId = track.videoId
                if (videoId.isBlank() || track.isPendingResolution) continue
                if (manualVideoIds.contains(videoId)) continue

                val existingFile = AuraPlayerManager.getOfflineFile(applicationContext, videoId)
                if (existingFile.exists() && existingFile.length() > MIN_VALID_AUDIO_BYTES) {
                    if (smartEntries[videoId] == null) {
                        storage.saveSmartDownload(videoId, isSmartDownload = true)
                        smartEntries[videoId] = com.auramusic.app.data.db.SmartDownloadEntity(
                            videoId = videoId,
                            downloadedAtMs = System.currentTimeMillis(),
                            isSmartDownload = true
                        )
                    }
                    continue
                }

                val file = AuraPlayerManager.downloadTrackOffline(
                    context = applicationContext,
                    track = track
                )
                if (file != null && file.exists() && file.length() > MIN_VALID_AUDIO_BYTES) {
                    val savedArt = AuraPlayerManager.saveOfflineArtwork(applicationContext, track)
                    val completedTrack = track.copy(
                        isDownloaded = true,
                        localFilePath = file.absolutePath,
                        artworkUrl = savedArt ?: track.artworkUrl
                    )
                    storage.saveSmartDownload(videoId, isSmartDownload = true)
                    smartEntries[videoId] = com.auramusic.app.data.db.SmartDownloadEntity(
                        videoId = videoId,
                        downloadedAtMs = System.currentTimeMillis(),
                        isSmartDownload = true
                    )
                    if (existingDownloads.none { it.videoId == videoId }) {
                        existingDownloads.add(0, completedTrack)
                    }
                    downloadedCount++
                }
            }

            val desiredSmartIds = candidates.map { it.videoId }.toSet()
            val allSmart = storage.loadSmartDownloads()
                .filter { it.isSmartDownload }
                .sortedBy { it.downloadedAtMs }

            var evictedCount = 0
            if (allSmart.size > limit) {
                val excessCount = allSmart.size - limit
                val toEvict = allSmart
                    .sortedWith(
                        compareBy<com.auramusic.app.data.db.SmartDownloadEntity> {
                            if (desiredSmartIds.contains(it.videoId)) 1 else 0
                        }.thenBy { it.downloadedAtMs }
                    )
                    .take(excessCount)

                for (entry in toEvict) {
                    if (manualVideoIds.contains(entry.videoId)) continue
                    val targetTrack = existingDownloads.firstOrNull { it.videoId == entry.videoId }
                        ?: AuraTrack(
                            id = "yt_${entry.videoId}",
                            videoId = entry.videoId,
                            title = entry.videoId,
                            artist = ""
                        )
                    AuraPlayerManager.offlineDownloader.deleteOfflineTrack(applicationContext, targetTrack)
                    storage.removeSmartDownload(entry.videoId)
                    existingDownloads.removeAll { it.videoId == entry.videoId }
                    evictedCount++
                }
            }

            storage.saveDownloads(existingDownloads)
            Log.i(TAG, "Smart downloads complete: downloaded=$downloadedCount, evicted=$evictedCount")
            Result.success(
                workDataOf(
                    KEY_DOWNLOADED_COUNT to downloadedCount,
                    KEY_EVICTED_COUNT to evictedCount
                )
            )
        } catch (e: Exception) {
            Log.w(TAG, "SmartDownloadWorker failed: ${e.message}")
            if (runAttemptCount < 2) Result.retry() else Result.failure()
        }
    }

    private fun selectTopCandidates(storage: AuraStorageRepository, limit: Int): List<AuraTrack> {
        val events = storage.queryPlaybackEventsSince(0L)
        val rankedFromEvents = events
            .filter { it.videoId.isNotBlank() && !it.videoId.startsWith("spotify_pending") }
            .groupBy { it.videoId }
            .entries
            .sortedWith(
                compareByDescending<Map.Entry<String, List<com.auramusic.app.data.db.PlaybackEventEntity>>> {
                    it.value.size
                }.thenByDescending { entry ->
                    entry.value.maxOfOrNull { it.timestampMs } ?: 0L
                }
            )
            .map { (videoId, group) ->
                val latest = group.maxByOrNull { it.timestampMs } ?: group.first()
                AuraTrack(
                    id = "yt_$videoId",
                    videoId = videoId,
                    title = latest.title.ifBlank { "Track" },
                    artist = latest.artist.ifBlank { "Unknown Artist" },
                    album = latest.album,
                    durationSeconds = latest.durationSeconds.coerceAtLeast(1),
                    artworkUrl = latest.artworkUrl,
                    genre = latest.genre
                )
            }

        val combined = LinkedHashMap<String, AuraTrack>()
        for (track in rankedFromEvents) {
            if (combined.size >= limit) break
            combined.putIfAbsent(track.videoId, track)
        }
        if (combined.size < limit) {
            for (fav in storage.loadFavorites()) {
                if (combined.size >= limit) break
                if (!fav.isPendingResolution && fav.videoId.isNotBlank()) {
                    combined.putIfAbsent(fav.videoId, fav)
                }
            }
        }
        return combined.values.take(limit)
    }

    companion object {
        private const val TAG = "SmartDownloadWorker"
        const val KEY_LIMIT = "smart_download_limit"
        const val KEY_DOWNLOADED_COUNT = "smart_downloaded_count"
        const val KEY_EVICTED_COUNT = "smart_evicted_count"
        private const val DEFAULT_LIMIT = 25
        private const val MIN_VALID_AUDIO_BYTES = 10_000L
    }
}
