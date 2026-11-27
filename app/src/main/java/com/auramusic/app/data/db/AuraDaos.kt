package com.auramusic.app.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import kotlinx.coroutines.flow.Flow

@Dao
interface FavoriteTrackDao {
    @Query("SELECT * FROM favorite_tracks ORDER BY position ASC")
    suspend fun getAll(): List<FavoriteTrackEntity>

    @Query("SELECT * FROM favorite_tracks ORDER BY position ASC")
    fun observeAll(): Flow<List<FavoriteTrackEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(items: List<FavoriteTrackEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(item: FavoriteTrackEntity)

    @Query("DELETE FROM favorite_tracks WHERE trackKey = :trackKey")
    suspend fun deleteByKey(trackKey: String)

    @Query("DELETE FROM favorite_tracks")
    suspend fun clearAll()

    @Transaction
    suspend fun replaceAll(items: List<FavoriteTrackEntity>) {
        clearAll()
        if (items.isNotEmpty()) {
            upsertAll(items)
        }
    }
}

@Dao
interface DownloadTrackDao {
    @Query("SELECT * FROM download_tracks ORDER BY position ASC")
    suspend fun getAll(): List<DownloadTrackEntity>

    @Query("SELECT * FROM download_tracks ORDER BY position ASC")
    fun observeAll(): Flow<List<DownloadTrackEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(items: List<DownloadTrackEntity>)

    @Query("DELETE FROM download_tracks WHERE trackKey = :trackKey")
    suspend fun deleteByKey(trackKey: String)

    @Query("DELETE FROM download_tracks")
    suspend fun clearAll()

    @Transaction
    suspend fun replaceAll(items: List<DownloadTrackEntity>) {
        clearAll()
        if (items.isNotEmpty()) {
            upsertAll(items)
        }
    }
}

@Dao
interface PlaylistDao {
    @Query("SELECT * FROM local_playlists ORDER BY position ASC")
    suspend fun getAllPlaylists(): List<LocalPlaylistEntity>

    @Query("SELECT * FROM local_playlists ORDER BY position ASC")
    fun observeAllPlaylists(): Flow<List<LocalPlaylistEntity>>

    @Query("SELECT * FROM playlist_tracks WHERE playlistId = :playlistId ORDER BY position ASC")
    suspend fun getTracksForPlaylist(playlistId: String): List<PlaylistTrackEntity>

    @Query("SELECT * FROM playlist_tracks ORDER BY playlistId ASC, position ASC")
    suspend fun getAllPlaylistTracks(): List<PlaylistTrackEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertPlaylists(playlists: List<LocalPlaylistEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertPlaylistTracks(tracks: List<PlaylistTrackEntity>)

    @Query("DELETE FROM playlist_tracks")
    suspend fun clearAllPlaylistTracks()

    @Query("DELETE FROM local_playlists")
    suspend fun clearAllPlaylists()

    @Transaction
    suspend fun replaceAll(
        playlists: List<LocalPlaylistEntity>,
        tracks: List<PlaylistTrackEntity>
    ) {
        clearAllPlaylistTracks()
        clearAllPlaylists()
        if (playlists.isNotEmpty()) {
            upsertPlaylists(playlists)
        }
        if (tracks.isNotEmpty()) {
            upsertPlaylistTracks(tracks)
        }
    }
}

@Dao
interface RecentTrackDao {
    @Query("SELECT * FROM recent_tracks ORDER BY playedAtMs DESC LIMIT :limit")
    suspend fun getRecent(limit: Int = 30): List<RecentTrackEntity>

    @Query("SELECT * FROM recent_tracks ORDER BY playedAtMs DESC LIMIT :limit")
    fun observeRecent(limit: Int = 30): Flow<List<RecentTrackEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(item: RecentTrackEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(items: List<RecentTrackEntity>)

    @Query("DELETE FROM recent_tracks WHERE videoId = :videoId OR id = :videoId OR (:identityKey IS NOT NULL AND identityKey = :identityKey)")
    suspend fun deleteMatching(videoId: String, identityKey: String?)

    @Query("DELETE FROM recent_tracks WHERE identityKey NOT IN (SELECT identityKey FROM recent_tracks ORDER BY playedAtMs DESC LIMIT :limit)")
    suspend fun trimToLimit(limit: Int = 30)

    @Query("DELETE FROM recent_tracks")
    suspend fun clearAll()

    @Transaction
    suspend fun replaceAll(items: List<RecentTrackEntity>) {
        clearAll()
        if (items.isNotEmpty()) {
            upsertAll(items)
        }
    }
}

data class HourlyPlayCount(
    val hourOfDay: Int,
    val playCount: Int
)

data class NamePlayCount(
    val name: String,
    val playCount: Int
)

data class TrackPlayCountRow(
    val videoId: String,
    val title: String,
    val artist: String,
    val genre: String,
    val artworkUrl: String,
    val album: String,
    val durationSeconds: Int,
    val playCount: Int
)

@Dao
interface PlaybackEventDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(event: PlaybackEventEntity): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(events: List<PlaybackEventEntity>)

    @Query("SELECT * FROM playback_events WHERE timestampMs >= :sinceMs ORDER BY timestampMs DESC")
    suspend fun querySince(sinceMs: Long): List<PlaybackEventEntity>

    @Query("SELECT * FROM playback_events WHERE timestampMs BETWEEN :startMs AND :endMs ORDER BY timestampMs DESC")
    suspend fun queryInRange(startMs: Long, endMs: Long): List<PlaybackEventEntity>

    @Query("SELECT * FROM playback_events ORDER BY timestampMs DESC")
    suspend fun getAll(): List<PlaybackEventEntity>

    @Query(
        "SELECT hourOfDay, COUNT(*) AS playCount FROM playback_events " +
            "WHERE timestampMs >= :sinceMs AND hourOfDay BETWEEN 0 AND 23 " +
            "GROUP BY hourOfDay ORDER BY playCount DESC, hourOfDay ASC"
    )
    suspend fun getHourlyDistribution(sinceMs: Long = 0L): List<HourlyPlayCount>

    @Query(
        "SELECT artist AS name, COUNT(*) AS playCount FROM playback_events " +
            "WHERE timestampMs >= :sinceMs AND artist != '' " +
            "GROUP BY artist ORDER BY playCount DESC LIMIT :limit"
    )
    suspend fun getTopArtists(sinceMs: Long = 0L, limit: Int = 5): List<NamePlayCount>

    @Query(
        "SELECT genre AS name, COUNT(*) AS playCount FROM playback_events " +
            "WHERE timestampMs >= :sinceMs AND genre != '' " +
            "GROUP BY genre ORDER BY playCount DESC LIMIT :limit"
    )
    suspend fun getTopGenres(sinceMs: Long = 0L, limit: Int = 4): List<NamePlayCount>

    @Query(
        "SELECT videoId, " +
            "MAX(title) AS title, " +
            "MAX(artist) AS artist, " +
            "MAX(genre) AS genre, " +
            "MAX(artworkUrl) AS artworkUrl, " +
            "MAX(album) AS album, " +
            "MAX(durationSeconds) AS durationSeconds, " +
            "COUNT(*) AS playCount " +
            "FROM playback_events " +
            "WHERE timestampMs >= :sinceMs AND videoId != '' " +
            "GROUP BY videoId ORDER BY playCount DESC LIMIT :limit"
    )
    suspend fun getTopTracks(sinceMs: Long = 0L, limit: Int = 3): List<TrackPlayCountRow>

    @Query("SELECT COALESCE(SUM(durationPlayedMs), 0) FROM playback_events WHERE timestampMs >= :sinceMs")
    suspend fun getTotalDurationPlayedMs(sinceMs: Long = 0L): Long

    @Query("SELECT COUNT(*) FROM playback_events WHERE timestampMs >= :sinceMs")
    suspend fun getEventCount(sinceMs: Long = 0L): Int

    @Query("DELETE FROM playback_events")
    suspend fun clearAll()
}

@Dao
interface OfflineLyricsDao {
    @Query("SELECT * FROM offline_lyrics WHERE videoId = :videoId LIMIT 1")
    suspend fun getByVideoId(videoId: String): OfflineLyricsEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(entity: OfflineLyricsEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(entities: List<OfflineLyricsEntity>)

    @Query("UPDATE offline_lyrics SET translatedJson = :translatedJson, updatedAtMs = :updatedAtMs WHERE videoId = :videoId")
    suspend fun updateTranslation(videoId: String, translatedJson: String?, updatedAtMs: Long)

    @Query("DELETE FROM offline_lyrics WHERE videoId = :videoId")
    suspend fun deleteByVideoId(videoId: String)

    @Query("DELETE FROM offline_lyrics")
    suspend fun clearAll()
}

@Dao
interface OfflinePaletteDao {
    @Query("SELECT * FROM offline_palettes WHERE videoId = :videoId LIMIT 1")
    suspend fun getByVideoId(videoId: String): OfflinePaletteEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(entity: OfflinePaletteEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(entities: List<OfflinePaletteEntity>)

    @Query("DELETE FROM offline_palettes WHERE videoId = :videoId")
    suspend fun deleteByVideoId(videoId: String)

    @Query("DELETE FROM offline_palettes")
    suspend fun clearAll()
}

@Dao
interface SavedQueueDao {
    @Query("SELECT * FROM saved_queue ORDER BY position ASC")
    suspend fun getAll(): List<SavedQueueEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(items: List<SavedQueueEntity>)

    @Query("DELETE FROM saved_queue")
    suspend fun clearAll()

    @Transaction
    suspend fun replaceAll(items: List<SavedQueueEntity>) {
        clearAll()
        if (items.isNotEmpty()) {
            upsertAll(items)
        }
    }
}

@Dao
interface LyricsOffsetDao {
    @Query("SELECT offsetMs FROM lyrics_offsets WHERE videoId = :videoId LIMIT 1")
    suspend fun getOffsetMs(videoId: String): Int?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(entity: LyricsOffsetEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(entities: List<LyricsOffsetEntity>)

    @Query("DELETE FROM lyrics_offsets")
    suspend fun clearAll()
}

@Dao
interface SearchHistoryDao {
    @Query("SELECT * FROM search_history ORDER BY timestampMs DESC LIMIT :limit")
    suspend fun getRecent(limit: Int = 30): List<SearchHistoryEntity>

    @Query("SELECT * FROM search_history ORDER BY timestampMs DESC LIMIT :limit")
    fun observeRecent(limit: Int = 30): Flow<List<SearchHistoryEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(entity: SearchHistoryEntity)

    @Query("DELETE FROM search_history WHERE `query` = :query")
    suspend fun deleteByQuery(query: String)

    @Query("DELETE FROM search_history")
    suspend fun clearAll()
}

@Dao
interface SmartDownloadDao {
    @Query("SELECT * FROM smart_downloads ORDER BY downloadedAtMs ASC")
    suspend fun getAll(): List<SmartDownloadEntity>

    @Query("SELECT * FROM smart_downloads WHERE isSmartDownload = 1 ORDER BY downloadedAtMs ASC")
    suspend fun getSmartDownloadsOnly(): List<SmartDownloadEntity>

    @Query("SELECT * FROM smart_downloads WHERE videoId = :videoId LIMIT 1")
    suspend fun getByVideoId(videoId: String): SmartDownloadEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(entity: SmartDownloadEntity)

    @Query("DELETE FROM smart_downloads WHERE videoId = :videoId")
    suspend fun deleteByVideoId(videoId: String)

    @Query("DELETE FROM smart_downloads")
    suspend fun clearAll()
}
