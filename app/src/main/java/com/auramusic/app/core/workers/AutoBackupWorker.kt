package com.auramusic.app.core.workers

import android.content.Context
import android.os.Environment
import android.util.Log
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import com.auramusic.app.data.AuraStorageRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.TimeUnit

object AutoBackupScheduler {
    private const val PERIODIC_WORK_NAME = "aura_auto_backup_periodic"
    private const val IMMEDIATE_WORK_NAME = "aura_auto_backup_immediate"

    enum class BackupFrequency(val label: String, val intervalDays: Long) {
        OFF("Off", 0),
        DAILY("Daily", 1),
        WEEKLY("Weekly", 7),
        MONTHLY("Monthly", 30)
    }

    fun schedule(context: Context, frequency: BackupFrequency, keepCount: Int) {
        val workManager = WorkManager.getInstance(context.applicationContext)
        if (frequency == BackupFrequency.OFF || frequency.intervalDays <= 0L) {
            workManager.cancelUniqueWork(PERIODIC_WORK_NAME)
            return
        }
        val safeKeepCount = keepCount.coerceIn(1, 50)
        val request = PeriodicWorkRequestBuilder<AutoBackupWorker>(
            frequency.intervalDays,
            TimeUnit.DAYS
        )
            .setInputData(workDataOf(AutoBackupWorker.KEY_KEEP_COUNT to safeKeepCount))
            .build()
        workManager.enqueueUniquePeriodicWork(
            PERIODIC_WORK_NAME,
            ExistingPeriodicWorkPolicy.UPDATE,
            request
        )
    }

    fun runNow(context: Context, keepCount: Int = AutoBackupWorker.DEFAULT_KEEP_COUNT) {
        val workManager = WorkManager.getInstance(context.applicationContext)
        val safeKeepCount = keepCount.coerceIn(1, 50)
        val request = OneTimeWorkRequestBuilder<AutoBackupWorker>()
            .setInputData(workDataOf(AutoBackupWorker.KEY_KEEP_COUNT to safeKeepCount))
            .build()
        workManager.enqueueUniqueWork(
            IMMEDIATE_WORK_NAME,
            ExistingWorkPolicy.REPLACE,
            request
        )
    }
}

class AutoBackupWorker(
    appContext: Context,
    params: WorkerParameters
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        try {
            val keepCount = inputData.getInt(KEY_KEEP_COUNT, DEFAULT_KEEP_COUNT).coerceIn(1, 50)
            val storage = AuraStorageRepository(applicationContext)
            val backupJson = storage.exportBackupJson()

            val baseDir = applicationContext.getExternalFilesDir(Environment.DIRECTORY_DOCUMENTS)
                ?: applicationContext.filesDir
            val backupDir = File(baseDir, BACKUP_FOLDER_NAME).apply {
                if (!exists()) mkdirs()
            }

            val stamp = SimpleDateFormat("yyyyMMdd_HHmmss_SSS", Locale.US).format(Date())
            val outFile = File(backupDir, "$BACKUP_PREFIX$stamp$BACKUP_EXTENSION")
            outFile.writeText(backupJson, Charsets.UTF_8)

            val pruned = pruneOldBackups(backupDir, keepCount)
            Log.i(TAG, "Auto-backup saved to ${outFile.absolutePath}; pruned=$pruned")
            Result.success(
                workDataOf(
                    KEY_BACKUP_PATH to outFile.absolutePath,
                    KEY_PRUNED_COUNT to pruned
                )
            )
        } catch (e: Exception) {
            Log.w(TAG, "AutoBackupWorker failed: ${e.message}")
            if (runAttemptCount < 2) Result.retry() else Result.failure()
        }
    }

    companion object {
        private const val TAG = "AutoBackupWorker"
        private const val BACKUP_FOLDER_NAME = "AuraBackups"
        private const val BACKUP_PREFIX = "aura_backup_"
        private const val BACKUP_EXTENSION = ".json"
        const val KEY_KEEP_COUNT = "auto_backup_keep_count"
        const val KEY_BACKUP_PATH = "auto_backup_path"
        const val KEY_PRUNED_COUNT = "auto_backup_pruned_count"
        const val DEFAULT_KEEP_COUNT = 5

        internal fun pruneOldBackups(backupDir: File, keepCount: Int): Int {
            val files = backupDir.listFiles { file ->
                file.isFile &&
                    file.name.startsWith(BACKUP_PREFIX) &&
                    file.name.endsWith(BACKUP_EXTENSION)
            } ?: return 0

            val sortedNewestFirst = files.sortedWith(
                compareByDescending<File> { it.lastModified() }
                    .thenByDescending { it.name }
            )
            var deleted = 0
            sortedNewestFirst.drop(keepCount.coerceAtLeast(1)).forEach { old ->
                if (old.delete()) deleted++
            }
            return deleted
        }
    }
}
