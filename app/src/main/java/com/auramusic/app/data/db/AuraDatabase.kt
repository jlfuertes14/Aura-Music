package com.auramusic.app.data.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(
    entities = [
        FavoriteTrackEntity::class,
        DownloadTrackEntity::class,
        LocalPlaylistEntity::class,
        PlaylistTrackEntity::class,
        RecentTrackEntity::class,
        PlaybackEventEntity::class,
        OfflineLyricsEntity::class,
        OfflinePaletteEntity::class,
        SavedQueueEntity::class,
        LyricsOffsetEntity::class,
        SearchHistoryEntity::class,
        SmartDownloadEntity::class
    ],
    version = 1,
    exportSchema = false
)
abstract class AuraDatabase : RoomDatabase() {
    abstract fun favoriteTrackDao(): FavoriteTrackDao
    abstract fun downloadTrackDao(): DownloadTrackDao
    abstract fun playlistDao(): PlaylistDao
    abstract fun recentTrackDao(): RecentTrackDao
    abstract fun playbackEventDao(): PlaybackEventDao
    abstract fun offlineLyricsDao(): OfflineLyricsDao
    abstract fun offlinePaletteDao(): OfflinePaletteDao
    abstract fun savedQueueDao(): SavedQueueDao
    abstract fun lyricsOffsetDao(): LyricsOffsetDao
    abstract fun searchHistoryDao(): SearchHistoryDao
    abstract fun smartDownloadDao(): SmartDownloadDao

    companion object {
        @Volatile
        private var instance: AuraDatabase? = null

        fun getInstance(context: Context): AuraDatabase {
            return instance ?: synchronized(this) {
                instance ?: Room.databaseBuilder(
                    context.applicationContext,
                    AuraDatabase::class.java,
                    "aura_database"
                )
                    .fallbackToDestructiveMigration(false)
                    .build()
                    .also { instance = it }
            }
        }
    }
}
