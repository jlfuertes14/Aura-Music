package com.auramusic.app.data.db

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import com.auramusic.app.domain.model.AuraPalette
import com.auramusic.app.domain.model.AuraPlaylist
import com.auramusic.app.domain.model.AuraTrack

@Entity(tableName = "favorite_tracks")
data class FavoriteTrackEntity(
    @PrimaryKey val trackKey: String,
    val id: String,
    val videoId: String,
    val title: String,
    val artist: String,
    val album: String = "YouTube Music",
    val durationSeconds: Int = 210,
    val artworkUrl: String = "",
    val source: String = "youtube",
    val genre: String = "Pop",
    val bitrateKbps: Int = 160,
    val codec: String = "AAC",
    val loudnessDb: Float = 0f,
    val isDownloaded: Boolean = false,
    val localFilePath: String? = null,
    val isExplicit: Boolean = false,
    val hasLyricsBadge: Boolean = true,
    val musicVideoType: String? = null,
    val viewCount: Long? = null,
    val isPodcast: Boolean = false,
    val position: Int = 0,
    val addedAtMs: Long = 0L
)

@Entity(tableName = "download_tracks")
data class DownloadTrackEntity(
    @PrimaryKey val trackKey: String,
    val id: String,
    val videoId: String,
    val title: String,
    val artist: String,
    val album: String = "YouTube Music",
    val durationSeconds: Int = 210,
    val artworkUrl: String = "",
    val source: String = "youtube",
    val genre: String = "Pop",
    val bitrateKbps: Int = 160,
    val codec: String = "AAC",
    val loudnessDb: Float = 0f,
    val isDownloaded: Boolean = true,
    val localFilePath: String? = null,
    val isExplicit: Boolean = false,
    val hasLyricsBadge: Boolean = true,
    val musicVideoType: String? = null,
    val viewCount: Long? = null,
    val isPodcast: Boolean = false,
    val position: Int = 0,
    val downloadedAtMs: Long = 0L
)

@Entity(tableName = "local_playlists")
data class LocalPlaylistEntity(
    @PrimaryKey val id: String,
    val name: String,
    val description: String = "",
    val coverUrl: String = "",
    val isPodcast: Boolean = false,
    val position: Int = 0,
    val updatedAtMs: Long = 0L
)

@Entity(
    tableName = "playlist_tracks",
    primaryKeys = ["playlistId", "position"],
    foreignKeys = [
        ForeignKey(
            entity = LocalPlaylistEntity::class,
            parentColumns = ["id"],
            childColumns = ["playlistId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index(value = ["playlistId"])]
)
data class PlaylistTrackEntity(
    val playlistId: String,
    val position: Int,
    val id: String,
    val videoId: String,
    val title: String,
    val artist: String,
    val album: String = "YouTube Music",
    val durationSeconds: Int = 210,
    val artworkUrl: String = "",
    val source: String = "youtube",
    val genre: String = "Pop",
    val bitrateKbps: Int = 160,
    val codec: String = "AAC",
    val loudnessDb: Float = 0f,
    val isDownloaded: Boolean = false,
    val localFilePath: String? = null,
    val isExplicit: Boolean = false,
    val hasLyricsBadge: Boolean = true,
    val musicVideoType: String? = null,
    val viewCount: Long? = null,
    val isPodcast: Boolean = false
)

@Entity(
    tableName = "recent_tracks",
    indices = [Index(value = ["playedAtMs"])]
)
data class RecentTrackEntity(
    @PrimaryKey val identityKey: String,
    val id: String,
    val videoId: String,
    val title: String,
    val artist: String,
    val album: String = "YouTube Music",
    val durationSeconds: Int = 210,
    val artworkUrl: String = "",
    val source: String = "youtube",
    val genre: String = "Pop",
    val bitrateKbps: Int = 160,
    val codec: String = "AAC",
    val loudnessDb: Float = 0f,
    val isDownloaded: Boolean = false,
    val localFilePath: String? = null,
    val isExplicit: Boolean = false,
    val hasLyricsBadge: Boolean = true,
    val musicVideoType: String? = null,
    val viewCount: Long? = null,
    val isPodcast: Boolean = false,
    val playedAtMs: Long
)

@Entity(
    tableName = "playback_events",
    indices = [
        Index(value = ["timestampMs"]),
        Index(value = ["videoId"]),
        Index(value = ["hourOfDay"])
    ]
)
data class PlaybackEventEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0L,
    val videoId: String,
    val title: String,
    val artist: String,
    val genre: String,
    val artworkUrl: String,
    val timestampMs: Long,
    val hourOfDay: Int,
    val durationPlayedMs: Long,
    val completed: Boolean,
    val album: String = "YouTube Music",
    val durationSeconds: Int = 210
)

@Entity(tableName = "offline_lyrics")
data class OfflineLyricsEntity(
    @PrimaryKey val videoId: String,
    val lyricsJson: String,
    val translatedJson: String? = null,
    val source: String,
    val updatedAtMs: Long
)

@Entity(tableName = "offline_palettes")
data class OfflinePaletteEntity(
    @PrimaryKey val videoId: String,
    val primaryArgb: Int,
    val secondaryArgb: Int,
    val backgroundTopArgb: Int,
    val backgroundBottomArgb: Int,
    val surfaceGlassArgb: Int,
    val appleMusicBackdropArgb: Int,
    val updatedAtMs: Long
)

@Entity(tableName = "saved_queue")
data class SavedQueueEntity(
    @PrimaryKey val position: Int,
    val trackJson: String
)

@Entity(tableName = "lyrics_offsets")
data class LyricsOffsetEntity(
    @PrimaryKey val videoId: String,
    val offsetMs: Int
)

@Entity(
    tableName = "search_history",
    indices = [Index(value = ["timestampMs"])]
)
data class SearchHistoryEntity(
    @PrimaryKey val query: String,
    val timestampMs: Long
)

@Entity(tableName = "smart_downloads")
data class SmartDownloadEntity(
    @PrimaryKey val videoId: String,
    val downloadedAtMs: Long,
    val isSmartDownload: Boolean
)

internal fun AuraTrack.storageKey(fallbackIndex: Int = 0): String =
    videoId.ifBlank { id }.ifBlank { "track_$fallbackIndex" }

internal fun LocalPlaylistEntity.toAuraPlaylist(tracks: List<AuraTrack>): AuraPlaylist =
    AuraPlaylist(
        id = id,
        name = name,
        description = description,
        coverUrl = coverUrl,
        tracks = tracks,
        isPodcast = isPodcast
    )

internal fun AuraTrack.toFavoriteEntity(position: Int, addedAtMs: Long): FavoriteTrackEntity =
    FavoriteTrackEntity(
        trackKey = storageKey(position),
        id = id,
        videoId = videoId,
        title = title,
        artist = artist,
        album = album,
        durationSeconds = durationSeconds,
        artworkUrl = artworkUrl,
        source = source,
        genre = genre,
        bitrateKbps = bitrateKbps,
        codec = codec,
        loudnessDb = loudnessDb,
        isDownloaded = isDownloaded,
        localFilePath = localFilePath,
        isExplicit = isExplicit,
        hasLyricsBadge = hasLyricsBadge,
        musicVideoType = musicVideoType,
        viewCount = viewCount,
        isPodcast = isPodcast,
        position = position,
        addedAtMs = addedAtMs
    )

internal fun FavoriteTrackEntity.toAuraTrack(): AuraTrack = AuraTrack(
    id = id,
    videoId = videoId,
    title = title,
    artist = artist,
    album = album,
    durationSeconds = durationSeconds,
    artworkUrl = artworkUrl,
    source = source,
    genre = genre,
    bitrateKbps = bitrateKbps,
    codec = codec,
    loudnessDb = loudnessDb,
    isDownloaded = isDownloaded,
    localFilePath = localFilePath,
    isExplicit = isExplicit,
    hasLyricsBadge = hasLyricsBadge,
    musicVideoType = musicVideoType,
    viewCount = viewCount,
    isPodcast = isPodcast
)

internal fun AuraTrack.toDownloadEntity(position: Int, downloadedAtMs: Long): DownloadTrackEntity =
    DownloadTrackEntity(
        trackKey = storageKey(position),
        id = id,
        videoId = videoId,
        title = title,
        artist = artist,
        album = album,
        durationSeconds = durationSeconds,
        artworkUrl = artworkUrl,
        source = source,
        genre = genre,
        bitrateKbps = bitrateKbps,
        codec = codec,
        loudnessDb = loudnessDb,
        isDownloaded = isDownloaded,
        localFilePath = localFilePath,
        isExplicit = isExplicit,
        hasLyricsBadge = hasLyricsBadge,
        musicVideoType = musicVideoType,
        viewCount = viewCount,
        isPodcast = isPodcast,
        position = position,
        downloadedAtMs = downloadedAtMs
    )

internal fun DownloadTrackEntity.toAuraTrack(): AuraTrack = AuraTrack(
    id = id,
    videoId = videoId,
    title = title,
    artist = artist,
    album = album,
    durationSeconds = durationSeconds,
    artworkUrl = artworkUrl,
    source = source,
    genre = genre,
    bitrateKbps = bitrateKbps,
    codec = codec,
    loudnessDb = loudnessDb,
    isDownloaded = isDownloaded,
    localFilePath = localFilePath,
    isExplicit = isExplicit,
    hasLyricsBadge = hasLyricsBadge,
    musicVideoType = musicVideoType,
    viewCount = viewCount,
    isPodcast = isPodcast
)

internal fun AuraTrack.toPlaylistTrackEntity(playlistId: String, position: Int): PlaylistTrackEntity =
    PlaylistTrackEntity(
        playlistId = playlistId,
        position = position,
        id = id,
        videoId = videoId,
        title = title,
        artist = artist,
        album = album,
        durationSeconds = durationSeconds,
        artworkUrl = artworkUrl,
        source = source,
        genre = genre,
        bitrateKbps = bitrateKbps,
        codec = codec,
        loudnessDb = loudnessDb,
        isDownloaded = isDownloaded,
        localFilePath = localFilePath,
        isExplicit = isExplicit,
        hasLyricsBadge = hasLyricsBadge,
        musicVideoType = musicVideoType,
        viewCount = viewCount,
        isPodcast = isPodcast
    )

internal fun PlaylistTrackEntity.toAuraTrack(): AuraTrack = AuraTrack(
    id = id,
    videoId = videoId,
    title = title,
    artist = artist,
    album = album,
    durationSeconds = durationSeconds,
    artworkUrl = artworkUrl,
    source = source,
    genre = genre,
    bitrateKbps = bitrateKbps,
    codec = codec,
    loudnessDb = loudnessDb,
    isDownloaded = isDownloaded,
    localFilePath = localFilePath,
    isExplicit = isExplicit,
    hasLyricsBadge = hasLyricsBadge,
    musicVideoType = musicVideoType,
    viewCount = viewCount,
    isPodcast = isPodcast
)

internal fun AuraTrack.toRecentEntity(identityKey: String, playedAtMs: Long): RecentTrackEntity =
    RecentTrackEntity(
        identityKey = identityKey,
        id = id,
        videoId = videoId,
        title = title,
        artist = artist,
        album = album,
        durationSeconds = durationSeconds,
        artworkUrl = artworkUrl,
        source = source,
        genre = genre,
        bitrateKbps = bitrateKbps,
        codec = codec,
        loudnessDb = loudnessDb,
        isDownloaded = isDownloaded,
        localFilePath = localFilePath,
        isExplicit = isExplicit,
        hasLyricsBadge = hasLyricsBadge,
        musicVideoType = musicVideoType,
        viewCount = viewCount,
        isPodcast = isPodcast,
        playedAtMs = playedAtMs
    )

internal fun RecentTrackEntity.toAuraTrack(): AuraTrack = AuraTrack(
    id = id,
    videoId = videoId,
    title = title,
    artist = artist,
    album = album,
    durationSeconds = durationSeconds,
    artworkUrl = artworkUrl,
    source = source,
    genre = genre,
    bitrateKbps = bitrateKbps,
    codec = codec,
    loudnessDb = loudnessDb,
    isDownloaded = isDownloaded,
    localFilePath = localFilePath,
    isExplicit = isExplicit,
    hasLyricsBadge = hasLyricsBadge,
    musicVideoType = musicVideoType,
    viewCount = viewCount,
    isPodcast = isPodcast
)

internal fun AuraPalette.toEntity(
    videoId: String,
    updatedAtMs: Long = System.currentTimeMillis()
): OfflinePaletteEntity =
    OfflinePaletteEntity(
        videoId = videoId,
        primaryArgb = primary.toArgb(),
        secondaryArgb = secondary.toArgb(),
        backgroundTopArgb = backgroundTop.toArgb(),
        backgroundBottomArgb = backgroundBottom.toArgb(),
        surfaceGlassArgb = surfaceGlass.toArgb(),
        appleMusicBackdropArgb = appleMusicBackdrop.toArgb(),
        updatedAtMs = updatedAtMs
    )

internal fun OfflinePaletteEntity.toAuraPalette(): AuraPalette = AuraPalette(
    primary = Color(primaryArgb),
    secondary = Color(secondaryArgb),
    backgroundTop = Color(backgroundTopArgb),
    backgroundBottom = Color(backgroundBottomArgb),
    surfaceGlass = Color(surfaceGlassArgb),
    appleMusicBackdrop = Color(appleMusicBackdropArgb)
)
