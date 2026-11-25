package com.auramusic.app.data

import android.content.Context
import android.content.SharedPreferences
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import com.auramusic.app.core.recommendations.LocalRecommendationEngine
import com.auramusic.app.data.db.AuraDatabase
import com.auramusic.app.data.db.LocalPlaylistEntity
import com.auramusic.app.data.db.LyricsOffsetEntity
import com.auramusic.app.data.db.OfflineLyricsEntity
import com.auramusic.app.data.db.OfflinePaletteEntity
import com.auramusic.app.data.db.PlaybackEventEntity
import com.auramusic.app.data.db.PlaylistTrackEntity
import com.auramusic.app.data.db.SavedQueueEntity
import com.auramusic.app.data.db.SearchHistoryEntity
import com.auramusic.app.data.db.SmartDownloadEntity
import com.auramusic.app.data.db.toAuraPalette
import com.auramusic.app.data.db.toAuraTrack
import com.auramusic.app.data.db.toDownloadEntity
import com.auramusic.app.data.db.toEntity
import com.auramusic.app.data.db.toFavoriteEntity
import com.auramusic.app.data.db.toPlaylistTrackEntity
import com.auramusic.app.data.db.toRecentEntity
import com.auramusic.app.data.prefs.AuraPreferencesStore
import com.auramusic.app.domain.model.AppThemeMode
import com.auramusic.app.domain.model.AppThemeStyle
import com.auramusic.app.domain.model.AuraPalette
import com.auramusic.app.domain.model.AuraPlaylist
import com.auramusic.app.domain.model.AuraTrack
import com.auramusic.app.domain.model.ListeningStats
import com.auramusic.app.domain.model.LyricLine
import com.auramusic.app.domain.model.LyricsData
import com.auramusic.app.domain.model.LyricsStyle
import com.auramusic.app.domain.model.NowPlayingStyle
import com.auramusic.app.domain.model.ThemeColorSource
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import org.json.JSONArray
import org.json.JSONObject
import java.util.Calendar

class AuraStorageRepository @JvmOverloads constructor(
    context: Context,
    private val db: AuraDatabase = AuraDatabase.getInstance(context),
    private val prefsStore: AuraPreferencesStore = AuraPreferencesStore.getInstance(context)
) {
    private val appContext: Context = context.applicationContext
    private val legacyPrefs: SharedPreferences by lazy {
        appContext.getSharedPreferences(LEGACY_PREFS_NAME, Context.MODE_PRIVATE)
    }

    init {
        ensureMigrated()
    }

    private fun ensureMigrated() {
        if (processMigrated) return
        synchronized(migrationLock) {
            if (processMigrated) return
            if (prefsStore.getBoolean(AuraPreferencesStore.Keys.MIGRATED_TO_ROOM_V1, false)) {
                processMigrated = true
                return
            }
            runBlocking(Dispatchers.IO) {
                migrateFromLegacySharedPreferences()
            }
            processMigrated = true
        }
    }

    private suspend fun migrateFromLegacySharedPreferences() {
        val allLegacy = legacyPrefs.all
        if (allLegacy.isEmpty()) {
            prefsStore.putBoolean(AuraPreferencesStore.Keys.MIGRATED_TO_ROOM_V1, true)
            return
        }

        val now = System.currentTimeMillis()

        // 1. Favorites
        (allLegacy["favorites_json"] as? String)?.let { raw ->
            val tracks = parseTrackArray(raw)
            if (tracks.isNotEmpty()) {
                db.favoriteTrackDao().replaceAll(
                    tracks.mapIndexed { idx, track -> track.toFavoriteEntity(idx, now) }
                )
            }
        }

        // 2. Downloads
        (allLegacy["downloads_json"] as? String)?.let { raw ->
            val tracks = parseTrackArray(raw)
            if (tracks.isNotEmpty()) {
                db.downloadTrackDao().replaceAll(
                    tracks.mapIndexed { idx, track -> track.toDownloadEntity(idx, now) }
                )
            }
        }

        // 3. Playlists
        (allLegacy["playlists_json"] as? String)?.let { raw ->
            val playlists = parsePlaylistsJson(raw)
            if (playlists.isNotEmpty()) {
                val playlistEntities = playlists.mapIndexed { idx, pl ->
                    LocalPlaylistEntity(
                        id = pl.id,
                        name = pl.name,
                        description = pl.description,
                        coverUrl = pl.coverUrl,
                        isPodcast = pl.isPodcast,
                        position = idx,
                        updatedAtMs = now
                    )
                }
                val trackEntities = playlists.flatMap { pl ->
                    pl.tracks.mapIndexed { trackIdx, track ->
                        track.toPlaylistTrackEntity(pl.id, trackIdx)
                    }
                }
                db.playlistDao().replaceAll(playlistEntities, trackEntities)
            }
        }

        // 4. Recently Played
        (allLegacy["recently_played_tracks"] as? String)?.let { raw ->
            val recent = parseTrackArray(raw)
                .distinctBy { LocalRecommendationEngine.songIdentityKey(it) }
                .take(RECENT_LIMIT)
            if (recent.isNotEmpty()) {
                val entities = recent.mapIndexed { idx, track ->
                    track.toRecentEntity(
                        identityKey = recentIdentityKey(track),
                        playedAtMs = now - idx * 1_000L
                    )
                }
                db.recentTrackDao().replaceAll(entities)
            }
        }

        // 5. Offline Lyrics
        val lyricsEntities = allLegacy.entries.mapNotNull { (key, value) ->
            if (!key.startsWith("offline_lyrics_")) return@mapNotNull null
            val videoId = key.removePrefix("offline_lyrics_")
            val raw = value as? String ?: return@mapNotNull null
            if (videoId.isBlank()) return@mapNotNull null
            val source = runCatching { JSONObject(raw).optString("source", "SimpMusic Offline") }
                .getOrDefault("SimpMusic Offline")
            OfflineLyricsEntity(
                videoId = videoId,
                lyricsJson = raw,
                translatedJson = null,
                source = source,
                updatedAtMs = now
            )
        }
        if (lyricsEntities.isNotEmpty()) {
            db.offlineLyricsDao().upsertAll(lyricsEntities)
        }

        // 6. Offline Palettes
        val paletteEntities = allLegacy.entries.mapNotNull { (key, value) ->
            if (!key.startsWith("offline_palette_")) return@mapNotNull null
            val videoId = key.removePrefix("offline_palette_")
            val raw = value as? String ?: return@mapNotNull null
            if (videoId.isBlank()) return@mapNotNull null
            parsePaletteFromJson(raw)?.toEntity(videoId, now)
        }
        if (paletteEntities.isNotEmpty()) {
            db.offlinePaletteDao().upsertAll(paletteEntities)
        }

        // 7. Lyrics Offsets
        val offsetEntities = allLegacy.entries.mapNotNull { (key, value) ->
            if (!key.startsWith("lyric_offset_")) return@mapNotNull null
            val videoId = key.removePrefix("lyric_offset_")
            val offsetMs = (value as? Number)?.toInt() ?: return@mapNotNull null
            if (videoId.isBlank()) return@mapNotNull null
            LyricsOffsetEntity(videoId = videoId, offsetMs = offsetMs.coerceIn(-120000, 120000))
        }
        if (offsetEntities.isNotEmpty()) {
            db.lyricsOffsetDao().upsertAll(offsetEntities)
        }

        // 8. Listening Stats -> PlaybackEventEntity
        migrateLegacyListeningStats(allLegacy, now)

        // 9. Consolidated Spotify resolution cache + Settings -> DataStore
        val spotResolvedJson = JSONObject()
        allLegacy.forEach { (key, value) ->
            if (key.startsWith("spot_res_") && value is String) {
                spotResolvedJson.put(key, value)
            }
        }

        val updates = mutableMapOf<Preferences.Key<*>, Any?>()
        (allLegacy["recommendation_signals"] as? String)?.let {
            updates[AuraPreferencesStore.Keys.RECOMMENDATION_SIGNALS] = it
        }
        (allLegacy["explore_cover_cache_json"] as? String)?.let {
            updates[AuraPreferencesStore.Keys.EXPLORE_COVER_CACHE_JSON] = it
        }
        if (spotResolvedJson.length() > 0) {
            updates[AuraPreferencesStore.Keys.SPOTIFY_RESOLVED_CACHE_JSON] = spotResolvedJson.toString()
        }
        (allLegacy["setting_app_theme_style"] as? String)?.let {
            updates[AuraPreferencesStore.Keys.APP_THEME_STYLE] = it
        }
        (allLegacy["setting_liquid_glass_enabled"] as? Boolean)?.let {
            updates[AuraPreferencesStore.Keys.LIQUID_GLASS_ENABLED] = it
        }
        (allLegacy["now_playing_style"] as? String)?.let {
            updates[AuraPreferencesStore.Keys.NOW_PLAYING_STYLE] = it
        }
        (allLegacy["setting_lyrics_style"] as? String)?.let {
            updates[AuraPreferencesStore.Keys.LYRICS_STYLE] = it
        }
        (allLegacy["setting_audio_quality"] as? String)?.let {
            updates[AuraPreferencesStore.Keys.AUDIO_QUALITY] = it
        }
        (allLegacy["setting_video_quality"] as? String)?.let {
            updates[AuraPreferencesStore.Keys.VIDEO_QUALITY] = it
        }
        (allLegacy["setting_romanization_languages"] as? String)?.let {
            updates[AuraPreferencesStore.Keys.ROMANIZATION_LANGUAGES] = it
        }
        (allLegacy["setting_normalize_volume"] as? Boolean)?.let {
            updates[AuraPreferencesStore.Keys.NORMALIZE_VOLUME] = it
        }
        (allLegacy["setting_skip_silence"] as? Boolean)?.let {
            updates[AuraPreferencesStore.Keys.SKIP_SILENCE] = it
        }
        (allLegacy["setting_crossfade_sec"] as? Number)?.let {
            updates[AuraPreferencesStore.Keys.CROSSFADE_SEC] = it.toInt()
        }
        (allLegacy["setting_max_cache_mb"] as? Number)?.let {
            updates[AuraPreferencesStore.Keys.MAX_CACHE_MB] = it.toInt()
        }
        (allLegacy["setting_eq_bands"] as? String)?.let {
            updates[AuraPreferencesStore.Keys.EQ_BANDS] = it
        }
        (allLegacy["setting_eq_preamp"] as? Number)?.let {
            updates[AuraPreferencesStore.Keys.EQ_PREAMP] = it.toFloat()
        }
        (allLegacy["setting_eq_preset"] as? String)?.let {
            updates[AuraPreferencesStore.Keys.EQ_PRESET] = it
        }
        (allLegacy["setting_eq_enabled"] as? Boolean)?.let {
            updates[AuraPreferencesStore.Keys.EQ_ENABLED] = it
        }
        (allLegacy["setting_chart_country"] as? String)?.let {
            updates[AuraPreferencesStore.Keys.CHART_COUNTRY] = it
        }
        (allLegacy["setting_auto_download_liked"] as? Boolean)?.let {
            updates[AuraPreferencesStore.Keys.AUTO_DOWNLOAD_LIKED] = it
        }
        (allLegacy["setting_play_video_for_video_track"] as? Boolean)?.let {
            updates[AuraPreferencesStore.Keys.PLAY_VIDEO_FOR_VIDEO_TRACK] = it
        }
        (allLegacy["setting_radio_audio_only"] as? Boolean)?.let {
            updates[AuraPreferencesStore.Keys.RADIO_AUDIO_ONLY] = it
        }
        (allLegacy["setting_audio_reverb_preset"] as? Number)?.let {
            updates[AuraPreferencesStore.Keys.AUDIO_REVERB_PRESET] = it.toInt()
        }
        (allLegacy["setting_save_playback_state"] as? Boolean)?.let {
            updates[AuraPreferencesStore.Keys.SAVE_PLAYBACK_STATE] = it
        }
        (allLegacy["setting_saved_playback_mode"] as? String)?.let {
            updates[AuraPreferencesStore.Keys.SAVED_PLAYBACK_MODE] = it
        }
        (allLegacy["setting_kill_service_on_exit"] as? Boolean)?.let {
            updates[AuraPreferencesStore.Keys.KILL_SERVICE_ON_EXIT] = it
        }
        (allLegacy["setting_sponsorblock_enabled"] as? Boolean)?.let {
            updates[AuraPreferencesStore.Keys.SPONSORBLOCK_ENABLED] = it
        }
        (allLegacy["setting_sponsorblock_categories"] as? String)?.let {
            updates[AuraPreferencesStore.Keys.SPONSORBLOCK_CATEGORIES] = it
        }
        (allLegacy["setting_main_lyrics_provider"] as? String)?.let {
            updates[AuraPreferencesStore.Keys.MAIN_LYRICS_PROVIDER] = it
        }
        (allLegacy["setting_default_lyrics_offset_ms"] as? Number)?.let {
            updates[AuraPreferencesStore.Keys.DEFAULT_LYRICS_OFFSET_MS] = it.toInt()
        }
        (allLegacy["setting_record_listening_history"] as? Boolean)?.let {
            updates[AuraPreferencesStore.Keys.RECORD_LISTENING_HISTORY] = it
        }
        (allLegacy["setting_auto_check_updates"] as? Boolean)?.let {
            updates[AuraPreferencesStore.Keys.AUTO_CHECK_UPDATES] = it
        }
        updates[AuraPreferencesStore.Keys.MIGRATED_TO_ROOM_V1] = true
        prefsStore.editBatch(updates)
    }

    private suspend fun migrateLegacyListeningStats(allLegacy: Map<String, *>, now: Long) {
        val totalMin = (allLegacy["stats_total_min"] as? Number)?.toInt() ?: 0
        val totalPlays = (allLegacy["stats_tracks_played"] as? Number)?.toInt() ?: 0
        val rawTrackPlays = allLegacy["stats_track_plays"] as? String

        val events = mutableListOf<PlaybackEventEntity>()
        if (!rawTrackPlays.isNullOrBlank()) {
            runCatching {
                val playedTracks = JSONObject(rawTrackPlays)
                playedTracks.keys().forEach { videoId ->
                    val row = playedTracks.optJSONObject(videoId) ?: return@forEach
                    val count = row.optInt("count", 0).coerceIn(0, 1_000)
                    if (count <= 0) return@forEach
                    val trackJson = row.optJSONObject("track") ?: return@forEach
                    val track = parseTrackArray(JSONArray().put(trackJson).toString()).firstOrNull()
                        ?: return@forEach
                    val perPlayMs = (track.durationSeconds / 60).coerceAtLeast(3) * 60_000L
                    repeat(count) {
                        events.add(
                            PlaybackEventEntity(
                                videoId = track.videoId.ifBlank { videoId },
                                title = track.title,
                                artist = track.artist,
                                genre = track.genre,
                                artworkUrl = track.artworkUrl,
                                timestampMs = now,
                                hourOfDay = -1, // Unknown hour for legacy aggregated counts
                                durationPlayedMs = perPlayMs,
                                completed = true,
                                album = track.album,
                                durationSeconds = track.durationSeconds
                            )
                        )
                    }
                }
            }
        }

        // Preserve any legacy play/minute count not captured by stats_track_plays
        val remainingPlays = (totalPlays - events.size).coerceAtLeast(0)
        val migratedMin = (events.sumOf { it.durationPlayedMs } / 60_000L).toInt()
        val remainingMin = (totalMin - migratedMin).coerceAtLeast(0)
        if (remainingPlays > 0) {
            val basePerPlayMin = (remainingMin / remainingPlays).coerceAtLeast(0)
            val remainderMin = remainingMin - (basePerPlayMin * remainingPlays)
            repeat(remainingPlays) { idx ->
                val minutes = basePerPlayMin + if (idx == 0) remainderMin else 0
                events.add(
                    PlaybackEventEntity(
                        videoId = "",
                        title = "Migrated Play",
                        artist = "",
                        genre = "",
                        artworkUrl = "",
                        timestampMs = now,
                        hourOfDay = -1,
                        durationPlayedMs = minutes * 60_000L,
                        completed = true
                    )
                )
            }
        } else if (remainingMin > 0 && events.isNotEmpty()) {
            val first = events[0]
            events[0] = first.copy(durationPlayedMs = first.durationPlayedMs + remainingMin * 60_000L)
        } else if (totalMin > 0 && events.isEmpty()) {
            events.add(
                PlaybackEventEntity(
                    videoId = "",
                    title = "Migrated Listening",
                    artist = "",
                    genre = "",
                    artworkUrl = "",
                    timestampMs = now,
                    hourOfDay = -1,
                    durationPlayedMs = totalMin * 60_000L,
                    completed = true
                )
            )
        }

        if (events.isNotEmpty()) {
            db.playbackEventDao().insertAll(events)
        }
    }

    fun loadFavorites(): List<AuraTrack> = runBlocking(Dispatchers.IO) {
        db.favoriteTrackDao().getAll().map { it.toAuraTrack() }
    }

    fun saveFavorites(tracks: List<AuraTrack>) {
        val now = System.currentTimeMillis()
        runBlocking(Dispatchers.IO) {
            db.favoriteTrackDao().replaceAll(
                tracks.mapIndexed { idx, track -> track.toFavoriteEntity(idx, now) }
            )
        }
    }

    fun loadDownloads(): List<AuraTrack> = runBlocking(Dispatchers.IO) {
        db.downloadTrackDao().getAll().map { it.toAuraTrack() }
    }

    fun saveDownloads(tracks: List<AuraTrack>) {
        val now = System.currentTimeMillis()
        runBlocking(Dispatchers.IO) {
            db.downloadTrackDao().replaceAll(
                tracks.mapIndexed { idx, track -> track.toDownloadEntity(idx, now) }
            )
        }
    }

    fun loadPlaylists(): List<AuraPlaylist> = runBlocking(Dispatchers.IO) {
        val playlists = db.playlistDao().getAllPlaylists()
        val tracksByPlaylist = db.playlistDao().getAllPlaylistTracks().groupBy { it.playlistId }
        playlists.map { pl ->
            val cleanDesc = if (pl.description.startsWith("Imported from Spotify", ignoreCase = true)) {
                ""
            } else {
                pl.description
            }
            AuraPlaylist(
                id = pl.id,
                name = pl.name,
                description = cleanDesc,
                coverUrl = pl.coverUrl,
                tracks = tracksByPlaylist[pl.id].orEmpty().map { it.toAuraTrack() },
                isPodcast = pl.isPodcast
            )
        }
    }

    fun savePlaylists(playlists: List<AuraPlaylist>) {
        val now = System.currentTimeMillis()
        runBlocking(Dispatchers.IO) {
            val playlistEntities = playlists.mapIndexed { idx, pl ->
                LocalPlaylistEntity(
                    id = pl.id,
                    name = pl.name,
                    description = pl.description,
                    coverUrl = pl.coverUrl,
                    isPodcast = pl.isPodcast,
                    position = idx,
                    updatedAtMs = now
                )
            }
            val trackEntities = playlists.flatMap { pl ->
                pl.tracks.mapIndexed { idx, track ->
                    track.toPlaylistTrackEntity(pl.id, idx)
                }
            }
            db.playlistDao().replaceAll(playlistEntities, trackEntities)
        }
    }

    fun getLyricsOffsetMs(videoId: String): Int {
        if (videoId.isBlank()) return 0
        return runBlocking(Dispatchers.IO) {
            db.lyricsOffsetDao().getOffsetMs(videoId) ?: 0
        }
    }

    fun setLyricsOffsetMs(videoId: String, offsetMs: Int) {
        if (videoId.isBlank()) return
        runBlocking(Dispatchers.IO) {
            db.lyricsOffsetDao().upsert(
                LyricsOffsetEntity(
                    videoId = videoId,
                    offsetMs = offsetMs.coerceIn(-120000, 120000)
                )
            )
        }
    }

    fun saveOfflineLyrics(videoId: String, lyrics: LyricsData) {
        if (videoId.isBlank()) return
        try {
            val lyricsJson = serializeLyricsData(lyrics).toString()
            val now = System.currentTimeMillis()
            runBlocking(Dispatchers.IO) {
                val existing = db.offlineLyricsDao().getByVideoId(videoId)
                db.offlineLyricsDao().upsert(
                    OfflineLyricsEntity(
                        videoId = videoId,
                        lyricsJson = lyricsJson,
                        translatedJson = existing?.translatedJson,
                        source = lyrics.source,
                        updatedAtMs = now
                    )
                )
            }
        } catch (_: Exception) {
            // Non-critical
        }
    }

    fun loadOfflineLyrics(videoId: String): LyricsData? {
        if (videoId.isBlank()) return null
        val entity = runBlocking(Dispatchers.IO) {
            db.offlineLyricsDao().getByVideoId(videoId)
        } ?: return null
        return parseLyricsData(entity.lyricsJson, videoId)
    }

    fun saveTranslatedLyrics(videoId: String, lyrics: LyricsData) {
        if (videoId.isBlank()) return
        try {
            val translatedJson = serializeLyricsData(lyrics).toString()
            val now = System.currentTimeMillis()
            runBlocking(Dispatchers.IO) {
                val existing = db.offlineLyricsDao().getByVideoId(videoId)
                if (existing != null) {
                    db.offlineLyricsDao().updateTranslation(videoId, translatedJson, now)
                } else {
                    db.offlineLyricsDao().upsert(
                        OfflineLyricsEntity(
                            videoId = videoId,
                            lyricsJson = translatedJson,
                            translatedJson = translatedJson,
                            source = lyrics.source,
                            updatedAtMs = now
                        )
                    )
                }
            }
        } catch (_: Exception) {
            // Non-critical
        }
    }

    fun loadTranslatedLyrics(videoId: String): LyricsData? {
        if (videoId.isBlank()) return null
        val entity = runBlocking(Dispatchers.IO) {
            db.offlineLyricsDao().getByVideoId(videoId)
        } ?: return null
        val raw = entity.translatedJson ?: return null
        return parseLyricsData(raw, videoId)
    }

    fun saveOfflinePalette(videoId: String, palette: AuraPalette) {
        if (videoId.isBlank()) return
        try {
            val entity = palette.toEntity(videoId, System.currentTimeMillis())
            runBlocking(Dispatchers.IO) {
                db.offlinePaletteDao().upsert(entity)
            }
        } catch (_: Exception) {
            // Non-critical
        }
    }

    fun loadOfflinePalette(videoId: String): AuraPalette? {
        if (videoId.isBlank()) return null
        return runBlocking(Dispatchers.IO) {
            db.offlinePaletteDao().getByVideoId(videoId)?.toAuraPalette()
        }
    }

    fun loadAppThemeStyle(): AppThemeStyle = prefsStore.appThemeStyle.value

    fun saveAppThemeStyle(style: AppThemeStyle) {
        prefsStore.editBatch(appThemeStyleUpdates(style))
    }

    fun loadNowPlayingStyle(): NowPlayingStyle =
        if (loadAppThemeStyle() == AppThemeStyle.LIQUID_GLASS) NowPlayingStyle.LIQUID_GLASS else NowPlayingStyle.SPOTIFY

    fun saveNowPlayingStyle(style: NowPlayingStyle) {
        saveAppThemeStyle(if (style == NowPlayingStyle.LIQUID_GLASS) AppThemeStyle.LIQUID_GLASS else AppThemeStyle.DEFAULT)
    }

    fun loadLiquidGlassEnabled(): Boolean =
        loadAppThemeStyle() == AppThemeStyle.LIQUID_GLASS

    fun saveLiquidGlassEnabled(enabled: Boolean) {
        saveAppThemeStyle(if (enabled) AppThemeStyle.LIQUID_GLASS else AppThemeStyle.DEFAULT)
    }

    fun loadLyricsStyle(): LyricsStyle =
        if (loadAppThemeStyle() == AppThemeStyle.LIQUID_GLASS) LyricsStyle.APPLE_MUSIC else LyricsStyle.CLASSIC

    fun saveLyricsStyle(style: LyricsStyle) {
        saveAppThemeStyle(if (style == LyricsStyle.APPLE_MUSIC) AppThemeStyle.LIQUID_GLASS else AppThemeStyle.DEFAULT)
    }

    fun recordRecentlyPlayed(track: AuraTrack) {
        val now = System.currentTimeMillis()
        val key = recentIdentityKey(track)
        runBlocking(Dispatchers.IO) {
            db.recentTrackDao().deleteMatching(
                videoId = track.videoId.ifBlank { track.id },
                identityKey = key
            )
            db.recentTrackDao().upsert(track.toRecentEntity(key, now))
            db.recentTrackDao().trimToLimit(RECENT_LIMIT)
        }
    }

    fun removeRecentlyPlayed(videoId: String, trackKey: String? = null) {
        runBlocking(Dispatchers.IO) {
            db.recentTrackDao().deleteMatching(videoId = videoId, identityKey = trackKey)
        }
    }

    fun loadRecentlyPlayed(): List<AuraTrack> = runBlocking(Dispatchers.IO) {
        db.recentTrackDao().getRecent(RECENT_LIMIT)
            .map { it.toAuraTrack() }
            .distinctBy { LocalRecommendationEngine.songIdentityKey(it) }
    }

    fun recordRecommendationSignal(track: AuraTrack, signal: String) {
        if (track.videoId.isBlank()) return
        runCatching {
            val all = JSONObject(
                prefsStore.getString(AuraPreferencesStore.Keys.RECOMMENDATION_SIGNALS, "{}") ?: "{}"
            )
            val row = all.optJSONObject(track.videoId) ?: JSONObject()
            val key = when (signal) {
                "play" -> "plays"
                "complete" -> "completions"
                "skip" -> "skips"
                else -> return
            }
            row.put(key, row.optInt(key, 0) + 1)
            all.put(track.videoId, row)
            prefsStore.putString(AuraPreferencesStore.Keys.RECOMMENDATION_SIGNALS, all.toString())
        }
    }

    fun loadRecommendationInteractionScores(): Map<String, Float> = runCatching {
        val all = JSONObject(
            prefsStore.getString(AuraPreferencesStore.Keys.RECOMMENDATION_SIGNALS, "{}") ?: "{}"
        )
        all.keys().asSequence().mapNotNull { videoId ->
            val row = all.optJSONObject(videoId) ?: return@mapNotNull null
            val score = row.optInt("plays") * 0.45f +
                row.optInt("completions") * 1.4f -
                row.optInt("skips") * 2.2f
            videoId to score.coerceIn(-8f, 8f)
        }.toMap()
    }.getOrDefault(emptyMap())

    fun recordPlaybackEvent(track: AuraTrack, durationPlayedMs: Long, completed: Boolean) {
        val now = System.currentTimeMillis()
        val hourOfDay = Calendar.getInstance().apply { timeInMillis = now }.get(Calendar.HOUR_OF_DAY)
        val safeDurationMs = durationPlayedMs.coerceAtLeast(0L)
        runBlocking(Dispatchers.IO) {
            db.playbackEventDao().insert(
                PlaybackEventEntity(
                    videoId = track.videoId,
                    title = track.title,
                    artist = track.artist,
                    genre = track.genre,
                    artworkUrl = track.artworkUrl,
                    timestampMs = now,
                    hourOfDay = hourOfDay,
                    durationPlayedMs = safeDurationMs,
                    completed = completed,
                    album = track.album,
                    durationSeconds = track.durationSeconds
                )
            )
        }
    }

    fun queryPlaybackEventsSince(sinceMs: Long): List<PlaybackEventEntity> = runBlocking(Dispatchers.IO) {
        db.playbackEventDao().querySince(sinceMs)
    }

    fun recordTrackPlay(track: AuraTrack): ListeningStats {
        recordRecentlyPlayed(track)
        val estimatedPlayMs = (track.durationSeconds / 60).coerceAtLeast(3) * 60_000L
        recordPlaybackEvent(track, durationPlayedMs = estimatedPlayMs, completed = true)
        return loadListeningStats()
    }

    fun loadListeningStats(): ListeningStats = runBlocking(Dispatchers.IO) {
        val dao = db.playbackEventDao()
        val totalMinutes = (dao.getTotalDurationPlayedMs() / 60_000L).toInt()
        val tracksPlayed = dao.getEventCount()

        val artistCounts = LinkedHashMap<String, Int>()
        dao.getTopArtists(limit = 50).forEach { row ->
            val cleanArtist = row.name.split(",", "ft.").firstOrNull()?.trim().orEmpty()
            if (cleanArtist.isNotBlank()) {
                artistCounts[cleanArtist] = (artistCounts[cleanArtist] ?: 0) + row.playCount
            }
        }
        val topArtists = artistCounts.entries
            .sortedByDescending { it.value }
            .take(5)
            .map { it.key to it.value }

        val topGenres = dao.getTopGenres(limit = 4)
            .map { it.name to it.playCount }

        val topTracks = dao.getTopTracks(limit = 3).map { row ->
            AuraTrack(
                id = if (row.videoId.isNotBlank()) "yt_${row.videoId}" else "track_${row.title.hashCode()}",
                videoId = row.videoId,
                title = row.title,
                artist = row.artist,
                album = row.album,
                durationSeconds = row.durationSeconds,
                artworkUrl = row.artworkUrl,
                genre = row.genre
            ) to row.playCount
        }

        val hourly = dao.getHourlyDistribution()
        val peakHourLabel = if (hourly.isNotEmpty()) {
            val peakHour = hourly.first().hourOfDay
            "${formatHourLabel(peakHour)} – ${formatHourLabel((peakHour + 1) % 24)}"
        } else {
            "Not enough data"
        }

        ListeningStats(
            totalMinutes = totalMinutes,
            tracksPlayed = tracksPlayed,
            topArtists = topArtists,
            topGenres = topGenres,
            topTracks = topTracks,
            peakHourLabel = peakHourLabel
        )
    }

    fun savePlaybackQueue(tracks: List<AuraTrack>) {
        runBlocking(Dispatchers.IO) {
            val entities = tracks.mapIndexed { idx, track ->
                SavedQueueEntity(
                    position = idx,
                    trackJson = serializeTrackArray(listOf(track)).optJSONObject(0)?.toString() ?: "{}"
                )
            }
            db.savedQueueDao().replaceAll(entities)
        }
    }

    fun loadPlaybackQueue(): List<AuraTrack> = runBlocking(Dispatchers.IO) {
        val rows = db.savedQueueDao().getAll()
        if (rows.isEmpty()) return@runBlocking emptyList()
        val arr = JSONArray()
        rows.forEach { row ->
            runCatching { JSONObject(row.trackJson) }.getOrNull()?.let { arr.put(it) }
        }
        parseTrackArray(arr.toString())
    }

    fun saveSearchHistory(query: String) {
        val trimmed = query.trim()
        if (trimmed.isBlank()) return
        val now = System.currentTimeMillis()
        runBlocking(Dispatchers.IO) {
            db.searchHistoryDao().upsert(SearchHistoryEntity(query = trimmed, timestampMs = now))
        }
    }

    fun loadSearchHistory(limit: Int = 30): List<String> = runBlocking(Dispatchers.IO) {
        db.searchHistoryDao().getRecent(limit).map { it.query }
    }

    fun removeSearchHistory(query: String) {
        runBlocking(Dispatchers.IO) {
            db.searchHistoryDao().deleteByQuery(query)
        }
    }

    fun clearSearchHistory() {
        runBlocking(Dispatchers.IO) {
            db.searchHistoryDao().clearAll()
        }
    }

    fun saveSmartDownload(videoId: String, isSmartDownload: Boolean) {
        if (videoId.isBlank()) return
        val now = System.currentTimeMillis()
        runBlocking(Dispatchers.IO) {
            db.smartDownloadDao().upsert(
                SmartDownloadEntity(
                    videoId = videoId,
                    downloadedAtMs = now,
                    isSmartDownload = isSmartDownload
                )
            )
        }
    }

    fun loadSmartDownloads(): List<SmartDownloadEntity> = runBlocking(Dispatchers.IO) {
        db.smartDownloadDao().getAll()
    }

    fun removeSmartDownload(videoId: String) {
        if (videoId.isBlank()) return
        runBlocking(Dispatchers.IO) {
            db.smartDownloadDao().deleteByVideoId(videoId)
        }
    }

    fun cacheSpotifyResolvedTrack(title: String, artist: String, track: AuraTrack) {
        val cleanKey = spotifyCacheKey(title, artist)
        val obj = JSONObject().apply {
            put("id", track.id)
            put("videoId", track.videoId)
            put("title", track.title)
            put("artist", track.artist)
            put("album", track.album)
            put("durationSeconds", track.durationSeconds)
            put("artworkUrl", track.artworkUrl)
            put("source", track.source)
        }
        runCatching {
            val mapJson = JSONObject(
                prefsStore.getString(AuraPreferencesStore.Keys.SPOTIFY_RESOLVED_CACHE_JSON, "{}") ?: "{}"
            )
            mapJson.put(cleanKey, obj.toString())
            prefsStore.putString(AuraPreferencesStore.Keys.SPOTIFY_RESOLVED_CACHE_JSON, mapJson.toString())
        }
    }

    fun getCachedSpotifyResolvedTrack(title: String, artist: String): AuraTrack? {
        val cleanKey = spotifyCacheKey(title, artist)
        val rawMap = prefsStore.getString(AuraPreferencesStore.Keys.SPOTIFY_RESOLVED_CACHE_JSON, "{}") ?: "{}"
        val raw = runCatching { JSONObject(rawMap).optString(cleanKey, "") }.getOrDefault("")
            .ifBlank { legacyPrefs.getString(cleanKey, null) ?: return null }
        return try {
            val o = JSONObject(raw)
            val vid = o.optString("videoId")
            if (vid.isBlank() || vid.startsWith("spotify_pending")) return null
            AuraTrack(
                id = o.optString("id", "yt_$vid"),
                videoId = vid,
                title = o.optString("title", title),
                artist = o.optString("artist", artist),
                album = o.optString("album", "YouTube Music"),
                durationSeconds = o.optInt("durationSeconds", 210),
                artworkUrl = o.optString("artworkUrl"),
                source = o.optString("source", "youtube")
            )
        } catch (_: Exception) {
            null
        }
    }

    // --- SimpMusic Settings & Fidelity Persistence ---
    fun loadAudioQuality(): String =
        prefsStore.getString(AuraPreferencesStore.Keys.AUDIO_QUALITY, "Auto") ?: "Auto"

    fun saveAudioQuality(quality: String) {
        prefsStore.putString(AuraPreferencesStore.Keys.AUDIO_QUALITY, quality)
    }

    fun loadVideoQuality(): String =
        prefsStore.getString(AuraPreferencesStore.Keys.VIDEO_QUALITY, "Auto (1080p)") ?: "Auto (1080p)"

    fun saveVideoQuality(quality: String) {
        prefsStore.putString(AuraPreferencesStore.Keys.VIDEO_QUALITY, quality)
    }

    fun loadRomanizationLanguages(): Set<String> {
        val raw = prefsStore.getString(AuraPreferencesStore.Keys.ROMANIZATION_LANGUAGES, null)
        return raw?.split(",")?.map { it.trim() }?.filter { it.isNotBlank() }?.toSet()
            ?: setOf("Japanese", "Korean", "Chinese")
    }

    fun saveRomanizationLanguages(languages: Set<String>) {
        prefsStore.putString(AuraPreferencesStore.Keys.ROMANIZATION_LANGUAGES, languages.joinToString(","))
    }

    fun loadNormalizeVolume(): Boolean =
        prefsStore.getBoolean(AuraPreferencesStore.Keys.NORMALIZE_VOLUME, true)

    fun saveNormalizeVolume(enabled: Boolean) {
        prefsStore.putBoolean(AuraPreferencesStore.Keys.NORMALIZE_VOLUME, enabled)
    }

    fun loadSkipSilence(): Boolean =
        prefsStore.getBoolean(AuraPreferencesStore.Keys.SKIP_SILENCE, false)

    fun saveSkipSilence(enabled: Boolean) {
        prefsStore.putBoolean(AuraPreferencesStore.Keys.SKIP_SILENCE, enabled)
    }

    fun loadCrossfadeDuration(): Int =
        prefsStore.getInt(AuraPreferencesStore.Keys.CROSSFADE_SEC, 0)

    fun saveCrossfadeDuration(sec: Int) {
        prefsStore.putInt(AuraPreferencesStore.Keys.CROSSFADE_SEC, sec)
    }

    fun loadMaxCacheSize(): Int =
        prefsStore.getInt(AuraPreferencesStore.Keys.MAX_CACHE_MB, 1024)

    fun saveMaxCacheSize(sizeMb: Int) {
        prefsStore.putInt(AuraPreferencesStore.Keys.MAX_CACHE_MB, sizeMb)
    }

    fun loadEqualizerBands(): List<Float> {
        val raw = prefsStore.getString(AuraPreferencesStore.Keys.EQ_BANDS, null)
            ?: return listOf(0f, 0f, 0f, 0f, 0f, 0f, 0f, 0f, 0f, 0f)
        return raw.split(",").mapNotNull { it.toFloatOrNull() }.takeIf { it.size == 10 }
            ?: listOf(0f, 0f, 0f, 0f, 0f, 0f, 0f, 0f, 0f, 0f)
    }

    fun saveEqualizerBands(bands: List<Float>) {
        prefsStore.putString(AuraPreferencesStore.Keys.EQ_BANDS, bands.joinToString(","))
    }

    fun loadEqualizerPreamp(): Float =
        prefsStore.getFloat(AuraPreferencesStore.Keys.EQ_PREAMP, 0f)

    fun saveEqualizerPreamp(preampDb: Float) {
        prefsStore.putFloat(AuraPreferencesStore.Keys.EQ_PREAMP, preampDb)
    }

    fun loadEqualizerPreset(): String =
        prefsStore.getString(AuraPreferencesStore.Keys.EQ_PRESET, "Flat") ?: "Flat"

    fun saveEqualizerPreset(name: String) {
        prefsStore.putString(AuraPreferencesStore.Keys.EQ_PRESET, name)
    }

    fun loadEqualizerEnabled(): Boolean =
        prefsStore.getBoolean(AuraPreferencesStore.Keys.EQ_ENABLED, false)

    fun saveEqualizerEnabled(enabled: Boolean) {
        prefsStore.putBoolean(AuraPreferencesStore.Keys.EQ_ENABLED, enabled)
    }

    fun loadChartCountry(): String =
        prefsStore.getString(AuraPreferencesStore.Keys.CHART_COUNTRY, "US") ?: "US"

    fun saveChartCountry(code: String) {
        prefsStore.putString(AuraPreferencesStore.Keys.CHART_COUNTRY, code)
    }

    fun loadAutoDownloadLikedSongs(): Boolean =
        prefsStore.getBoolean(AuraPreferencesStore.Keys.AUTO_DOWNLOAD_LIKED, false)

    fun saveAutoDownloadLikedSongs(enabled: Boolean) {
        prefsStore.putBoolean(AuraPreferencesStore.Keys.AUTO_DOWNLOAD_LIKED, enabled)
    }

    fun loadPlayVideoForVideoTrack(): Boolean =
        prefsStore.getBoolean(AuraPreferencesStore.Keys.PLAY_VIDEO_FOR_VIDEO_TRACK, false)

    fun savePlayVideoForVideoTrack(enabled: Boolean) {
        prefsStore.putBoolean(AuraPreferencesStore.Keys.PLAY_VIDEO_FOR_VIDEO_TRACK, enabled)
    }

    fun loadRadioAudioOnly(): Boolean =
        prefsStore.getBoolean(AuraPreferencesStore.Keys.RADIO_AUDIO_ONLY, true)

    fun saveRadioAudioOnly(enabled: Boolean) {
        prefsStore.putBoolean(AuraPreferencesStore.Keys.RADIO_AUDIO_ONLY, enabled)
    }

    fun loadAudioReverbPreset(): Int =
        prefsStore.getInt(AuraPreferencesStore.Keys.AUDIO_REVERB_PRESET, 0)

    fun saveAudioReverbPreset(preset: Int) {
        prefsStore.putInt(AuraPreferencesStore.Keys.AUDIO_REVERB_PRESET, preset)
    }

    fun loadSavePlaybackState(): Boolean =
        prefsStore.getBoolean(AuraPreferencesStore.Keys.SAVE_PLAYBACK_STATE, true)

    fun saveSavePlaybackState(enabled: Boolean) {
        prefsStore.putBoolean(AuraPreferencesStore.Keys.SAVE_PLAYBACK_STATE, enabled)
    }

    fun loadSavedPlaybackMode(): String =
        prefsStore.getString(AuraPreferencesStore.Keys.SAVED_PLAYBACK_MODE, "NORMAL") ?: "NORMAL"

    fun saveSavedPlaybackMode(mode: String) {
        prefsStore.putString(AuraPreferencesStore.Keys.SAVED_PLAYBACK_MODE, mode)
    }

    fun loadKillServiceOnExit(): Boolean =
        prefsStore.getBoolean(AuraPreferencesStore.Keys.KILL_SERVICE_ON_EXIT, false)

    fun saveKillServiceOnExit(enabled: Boolean) {
        prefsStore.putBoolean(AuraPreferencesStore.Keys.KILL_SERVICE_ON_EXIT, enabled)
    }

    fun loadSponsorBlockEnabled(): Boolean =
        prefsStore.getBoolean(AuraPreferencesStore.Keys.SPONSORBLOCK_ENABLED, true)

    fun saveSponsorBlockEnabled(enabled: Boolean) {
        prefsStore.putBoolean(AuraPreferencesStore.Keys.SPONSORBLOCK_ENABLED, enabled)
    }

    fun loadSponsorBlockCategories(): Set<String> {
        val raw = prefsStore.getString(AuraPreferencesStore.Keys.SPONSORBLOCK_CATEGORIES, null)
        return raw?.split(",")?.map { it.trim() }?.filter { it.isNotBlank() }?.toSet()
            ?: setOf("sponsor", "selfpromo", "interaction", "intro", "outro", "music_offtopic")
    }

    fun saveSponsorBlockCategories(categories: Set<String>) {
        prefsStore.putString(AuraPreferencesStore.Keys.SPONSORBLOCK_CATEGORIES, categories.joinToString(","))
    }

    fun loadMainLyricsProvider(): String =
        prefsStore.getString(AuraPreferencesStore.Keys.MAIN_LYRICS_PROVIDER, "SimpMusic") ?: "SimpMusic"

    fun saveMainLyricsProvider(provider: String) {
        prefsStore.putString(AuraPreferencesStore.Keys.MAIN_LYRICS_PROVIDER, provider)
    }

    fun loadDefaultLyricsOffsetMs(): Int =
        prefsStore.getInt(AuraPreferencesStore.Keys.DEFAULT_LYRICS_OFFSET_MS, 0)

    fun saveDefaultLyricsOffsetMs(offsetMs: Int) {
        prefsStore.putInt(
            AuraPreferencesStore.Keys.DEFAULT_LYRICS_OFFSET_MS,
            offsetMs.coerceIn(-10000, 10000)
        )
    }

    fun loadRecordListeningHistory(): Boolean =
        prefsStore.getBoolean(AuraPreferencesStore.Keys.RECORD_LISTENING_HISTORY, true)

    fun saveRecordListeningHistory(enabled: Boolean) {
        prefsStore.putBoolean(AuraPreferencesStore.Keys.RECORD_LISTENING_HISTORY, enabled)
    }

    fun clearListeningHistory() {
        runBlocking(Dispatchers.IO) {
            db.recentTrackDao().clearAll()
            db.playbackEventDao().clearAll()
        }
        prefsStore.remove(AuraPreferencesStore.Keys.RECOMMENDATION_SIGNALS)
    }

    fun loadAutoCheckUpdates(): Boolean =
        prefsStore.getBoolean(AuraPreferencesStore.Keys.AUTO_CHECK_UPDATES, true)

    fun saveAutoCheckUpdates(enabled: Boolean) {
        prefsStore.putBoolean(AuraPreferencesStore.Keys.AUTO_CHECK_UPDATES, enabled)
    }

    // --- Tier 1 Preferences (Theme Mode, Accent, Crossfade Album Skip, AutoEq, Push) ---
    fun loadThemeMode(): AppThemeMode {
        val raw = prefsStore.getString(KEY_THEME_MODE, null)
        return AppThemeMode.entries.firstOrNull { it.name == raw } ?: AppThemeMode.DARK
    }

    fun saveThemeMode(mode: AppThemeMode) {
        prefsStore.putString(KEY_THEME_MODE, mode.name)
    }

    fun loadThemeColorSource(): ThemeColorSource {
        val raw = prefsStore.getString(KEY_THEME_COLOR_SOURCE, null)
        return ThemeColorSource.entries.firstOrNull { it.name == raw } ?: ThemeColorSource.DEFAULT
    }

    fun saveThemeColorSource(source: ThemeColorSource) {
        prefsStore.putString(KEY_THEME_COLOR_SOURCE, source.name)
    }

    fun loadCustomAccentArgb(): Int? =
        if (prefsStore.contains(KEY_CUSTOM_ACCENT_ARGB)) {
            prefsStore.getInt(KEY_CUSTOM_ACCENT_ARGB, 0)
        } else {
            null
        }

    fun saveCustomAccentArgb(argb: Int?) {
        if (argb == null) {
            prefsStore.remove(KEY_CUSTOM_ACCENT_ARGB)
        } else {
            prefsStore.putInt(KEY_CUSTOM_ACCENT_ARGB, argb)
        }
    }

    fun loadSkipCrossfadeWithinAlbum(): Boolean =
        prefsStore.getBoolean(KEY_SKIP_CROSSFADE_WITHIN_ALBUM, false)

    fun saveSkipCrossfadeWithinAlbum(enabled: Boolean) {
        prefsStore.putBoolean(KEY_SKIP_CROSSFADE_WITHIN_ALBUM, enabled)
    }

    fun loadActiveAutoEqProfileId(): String? =
        prefsStore.getString(KEY_ACTIVE_AUTO_EQ_PROFILE_ID, null)?.takeIf { it.isNotBlank() }

    fun saveActiveAutoEqProfileId(id: String?) {
        if (id.isNullOrBlank()) {
            prefsStore.remove(KEY_ACTIVE_AUTO_EQ_PROFILE_ID)
        } else {
            prefsStore.putString(KEY_ACTIVE_AUTO_EQ_PROFILE_ID, id)
        }
    }

    fun loadReleaseNotificationsEnabled(): Boolean =
        prefsStore.getBoolean(KEY_RELEASE_NOTIFICATIONS_ENABLED, true)

    fun saveReleaseNotificationsEnabled(enabled: Boolean) {
        prefsStore.putBoolean(KEY_RELEASE_NOTIFICATIONS_ENABLED, enabled)
    }

    // --- Tier 2 Preferences (Speed/Pitch, AI Lyrics, Smart Downloads, Auto-Backup, Integrations) ---
    fun loadPlaybackSpeed(): Float =
        prefsStore.getFloat(KEY_PLAYBACK_SPEED, 1.0f).coerceIn(0.25f, 2.0f)

    fun savePlaybackSpeed(speed: Float) {
        prefsStore.putFloat(KEY_PLAYBACK_SPEED, speed.coerceIn(0.25f, 2.0f))
    }

    fun loadPlaybackPitchSemitones(): Int =
        prefsStore.getInt(KEY_PLAYBACK_PITCH_SEMITONES, 0).coerceIn(-12, 12)

    fun savePlaybackPitchSemitones(semitones: Int) {
        prefsStore.putInt(KEY_PLAYBACK_PITCH_SEMITONES, semitones.coerceIn(-12, 12))
    }

    fun loadAiProvider(): String =
        prefsStore.getString(KEY_AI_PROVIDER, "GEMINI") ?: "GEMINI"

    fun saveAiProvider(provider: String) {
        prefsStore.putString(KEY_AI_PROVIDER, provider)
    }

    fun loadAiApiKey(): String =
        prefsStore.getString(KEY_AI_API_KEY, "") ?: ""

    fun saveAiApiKey(apiKey: String) {
        prefsStore.putString(KEY_AI_API_KEY, apiKey)
    }

    fun loadAiModelId(): String =
        prefsStore.getString(KEY_AI_MODEL_ID, "") ?: ""

    fun saveAiModelId(modelId: String) {
        prefsStore.putString(KEY_AI_MODEL_ID, modelId)
    }

    fun loadAiBaseUrl(): String =
        prefsStore.getString(KEY_AI_BASE_URL, "") ?: ""

    fun saveAiBaseUrl(baseUrl: String) {
        prefsStore.putString(KEY_AI_BASE_URL, baseUrl)
    }

    fun loadAiTargetLanguage(): String =
        prefsStore.getString(KEY_AI_TARGET_LANGUAGE, "English") ?: "English"

    fun saveAiTargetLanguage(language: String) {
        prefsStore.putString(KEY_AI_TARGET_LANGUAGE, language)
    }

    fun loadAiTranslationEnabled(): Boolean =
        prefsStore.getBoolean(KEY_AI_TRANSLATION_ENABLED, false)

    fun saveAiTranslationEnabled(enabled: Boolean) {
        prefsStore.putBoolean(KEY_AI_TRANSLATION_ENABLED, enabled)
    }

    fun loadSmartDownloadsEnabled(): Boolean =
        prefsStore.getBoolean(KEY_SMART_DOWNLOADS_ENABLED, false)

    fun saveSmartDownloadsEnabled(enabled: Boolean) {
        prefsStore.putBoolean(KEY_SMART_DOWNLOADS_ENABLED, enabled)
    }

    fun loadSmartDownloadsLimit(): Int =
        prefsStore.getInt(KEY_SMART_DOWNLOADS_LIMIT, 25).coerceIn(10, 500)

    fun saveSmartDownloadsLimit(limit: Int) {
        prefsStore.putInt(KEY_SMART_DOWNLOADS_LIMIT, limit.coerceIn(10, 500))
    }

    fun loadAutoBackupFrequency(): String =
        prefsStore.getString(KEY_AUTO_BACKUP_FREQUENCY, "OFF") ?: "OFF"

    fun saveAutoBackupFrequency(frequency: String) {
        prefsStore.putString(KEY_AUTO_BACKUP_FREQUENCY, frequency)
    }

    fun loadAutoBackupKeepCount(): Int =
        prefsStore.getInt(KEY_AUTO_BACKUP_KEEP_COUNT, 5).coerceIn(1, 50)

    fun saveAutoBackupKeepCount(keepCount: Int) {
        prefsStore.putInt(KEY_AUTO_BACKUP_KEEP_COUNT, keepCount.coerceIn(1, 50))
    }

    fun loadLastfmSessionKey(): String =
        prefsStore.getString(KEY_LASTFM_SESSION_KEY, "") ?: ""

    fun saveLastfmSessionKey(sessionKey: String) {
        prefsStore.putString(KEY_LASTFM_SESSION_KEY, sessionKey)
    }

    fun loadLastfmApiKey(): String =
        prefsStore.getString(KEY_LASTFM_API_KEY, "") ?: ""

    fun saveLastfmApiKey(apiKey: String) {
        prefsStore.putString(KEY_LASTFM_API_KEY, apiKey)
    }

    fun loadLastfmApiSecret(): String =
        prefsStore.getString(KEY_LASTFM_API_SECRET, "") ?: ""

    fun saveLastfmApiSecret(apiSecret: String) {
        prefsStore.putString(KEY_LASTFM_API_SECRET, apiSecret)
    }

    fun loadLastfmEnabled(): Boolean =
        prefsStore.getBoolean(KEY_LASTFM_ENABLED, false)

    fun saveLastfmEnabled(enabled: Boolean) {
        prefsStore.putBoolean(KEY_LASTFM_ENABLED, enabled)
    }

    fun loadDiscordToken(): String =
        prefsStore.getString(KEY_DISCORD_TOKEN, "") ?: ""

    fun saveDiscordToken(token: String) {
        prefsStore.putString(KEY_DISCORD_TOKEN, token)
    }

    fun loadDiscordRpcEnabled(): Boolean =
        prefsStore.getBoolean(KEY_DISCORD_RPC_ENABLED, false)

    fun saveDiscordRpcEnabled(enabled: Boolean) {
        prefsStore.putBoolean(KEY_DISCORD_RPC_ENABLED, enabled)
    }

    fun loadYtmCookie(): String =
        prefsStore.getString(KEY_YTM_COOKIE, "") ?: ""

    fun saveYtmCookie(cookie: String) {
        prefsStore.putString(KEY_YTM_COOKIE, cookie)
    }

    fun exportBackupJson(): String {
        val favoritesArr = serializeTrackArray(loadFavorites())
        val playlistsArr = JSONArray().apply {
            loadPlaylists().forEach { pl ->
                put(JSONObject().apply {
                    put("id", pl.id)
                    put("name", pl.name)
                    put("description", pl.description)
                    put("coverUrl", pl.coverUrl)
                    put("tracks", serializeTrackArray(pl.tracks))
                    put("isPodcast", pl.isPodcast)
                })
            }
        }
        val recentlyPlayedArr = serializeTrackArray(loadRecentlyPlayed())

        val root = JSONObject().apply {
            put("version", 1)
            put("exportedAt", System.currentTimeMillis())
            put("favorites", favoritesArr)
            put("playlists", playlistsArr)
            put("recentlyPlayed", recentlyPlayedArr)
            put("settings", JSONObject().apply {
                put("appThemeStyle", loadAppThemeStyle().name)
                put("chartCountry", loadChartCountry())
                put("audioQuality", loadAudioQuality())
                put("videoQuality", loadVideoQuality())
                put("normalizeVolume", loadNormalizeVolume())
                put("skipSilence", loadSkipSilence())
                put("crossfadeSec", loadCrossfadeDuration())
                put("maxCacheMb", loadMaxCacheSize())
                put("eqEnabled", loadEqualizerEnabled())
                put("eqPreset", loadEqualizerPreset())
                put("eqPreamp", loadEqualizerPreamp().toDouble())
                put("eqBands", loadEqualizerBands().joinToString(","))
                put("audioReverbPreset", loadAudioReverbPreset())
                put("sponsorBlockEnabled", loadSponsorBlockEnabled())
                put("sponsorBlockCategories", loadSponsorBlockCategories().joinToString(","))
                put("mainLyricsProvider", loadMainLyricsProvider())
                put("defaultLyricsOffsetMs", loadDefaultLyricsOffsetMs())
                put("romanizationLanguages", loadRomanizationLanguages().joinToString(","))
                put("autoDownloadLiked", loadAutoDownloadLikedSongs())
                put("playVideoForVideoTrack", loadPlayVideoForVideoTrack())
                put("radioAudioOnly", loadRadioAudioOnly())
                put("savePlaybackState", loadSavePlaybackState())
                put("killServiceOnExit", loadKillServiceOnExit())
                put("recordListeningHistory", loadRecordListeningHistory())
                put("autoCheckUpdates", loadAutoCheckUpdates())
            })
        }
        return root.toString(2)
    }

    fun importBackupJson(rawJson: String): Boolean {
        return try {
            val root = JSONObject(rawJson)
            root.optJSONArray("favorites")?.let { arr ->
                saveFavorites(parseTrackArray(arr.toString()))
            }
            root.optJSONArray("playlists")?.let { arr ->
                savePlaylists(parsePlaylistsJson(arr.toString()))
            }
            root.optJSONArray("recentlyPlayed")?.let { arr ->
                val tracks = parseTrackArray(arr.toString())
                    .distinctBy { LocalRecommendationEngine.songIdentityKey(it) }
                    .take(RECENT_LIMIT)
                val now = System.currentTimeMillis()
                runBlocking(Dispatchers.IO) {
                    db.recentTrackDao().replaceAll(
                        tracks.mapIndexed { idx, track ->
                            track.toRecentEntity(
                                identityKey = recentIdentityKey(track),
                                playedAtMs = now - idx * 1_000L
                            )
                        }
                    )
                }
            }
            root.optJSONObject("settings")?.let { s ->
                val updates = mutableMapOf<Preferences.Key<*>, Any?>()
                s.optString("appThemeStyle").takeIf { it.isNotBlank() }?.let { rawStyle ->
                    AppThemeStyle.entries.firstOrNull { it.name == rawStyle }?.let { style ->
                        updates.putAll(appThemeStyleUpdates(style))
                    }
                }
                if (s.has("chartCountry")) updates[AuraPreferencesStore.Keys.CHART_COUNTRY] = s.optString("chartCountry", "US")
                if (s.has("audioQuality")) updates[AuraPreferencesStore.Keys.AUDIO_QUALITY] = s.optString("audioQuality", "Auto")
                if (s.has("videoQuality")) updates[AuraPreferencesStore.Keys.VIDEO_QUALITY] = s.optString("videoQuality", "Auto (1080p)")
                if (s.has("normalizeVolume")) updates[AuraPreferencesStore.Keys.NORMALIZE_VOLUME] = s.optBoolean("normalizeVolume", true)
                if (s.has("skipSilence")) updates[AuraPreferencesStore.Keys.SKIP_SILENCE] = s.optBoolean("skipSilence", false)
                if (s.has("crossfadeSec")) updates[AuraPreferencesStore.Keys.CROSSFADE_SEC] = s.optInt("crossfadeSec", 0)
                if (s.has("maxCacheMb")) updates[AuraPreferencesStore.Keys.MAX_CACHE_MB] = s.optInt("maxCacheMb", 1024)
                if (s.has("eqEnabled")) updates[AuraPreferencesStore.Keys.EQ_ENABLED] = s.optBoolean("eqEnabled", false)
                if (s.has("eqPreset")) updates[AuraPreferencesStore.Keys.EQ_PRESET] = s.optString("eqPreset", "Flat")
                if (s.has("eqPreamp")) updates[AuraPreferencesStore.Keys.EQ_PREAMP] = s.optDouble("eqPreamp", 0.0).toFloat()
                if (s.has("eqBands")) updates[AuraPreferencesStore.Keys.EQ_BANDS] = s.optString("eqBands")
                if (s.has("audioReverbPreset")) updates[AuraPreferencesStore.Keys.AUDIO_REVERB_PRESET] = s.optInt("audioReverbPreset", 0)
                if (s.has("sponsorBlockEnabled")) updates[AuraPreferencesStore.Keys.SPONSORBLOCK_ENABLED] = s.optBoolean("sponsorBlockEnabled", true)
                if (s.has("sponsorBlockCategories")) updates[AuraPreferencesStore.Keys.SPONSORBLOCK_CATEGORIES] = s.optString("sponsorBlockCategories")
                if (s.has("mainLyricsProvider")) updates[AuraPreferencesStore.Keys.MAIN_LYRICS_PROVIDER] = s.optString("mainLyricsProvider", "SimpMusic")
                if (s.has("defaultLyricsOffsetMs")) updates[AuraPreferencesStore.Keys.DEFAULT_LYRICS_OFFSET_MS] = s.optInt("defaultLyricsOffsetMs", 0)
                if (s.has("romanizationLanguages")) updates[AuraPreferencesStore.Keys.ROMANIZATION_LANGUAGES] = s.optString("romanizationLanguages")
                if (s.has("autoDownloadLiked")) updates[AuraPreferencesStore.Keys.AUTO_DOWNLOAD_LIKED] = s.optBoolean("autoDownloadLiked", false)
                if (s.has("playVideoForVideoTrack")) updates[AuraPreferencesStore.Keys.PLAY_VIDEO_FOR_VIDEO_TRACK] = s.optBoolean("playVideoForVideoTrack", false)
                if (s.has("radioAudioOnly")) updates[AuraPreferencesStore.Keys.RADIO_AUDIO_ONLY] = s.optBoolean("radioAudioOnly", true)
                if (s.has("savePlaybackState")) updates[AuraPreferencesStore.Keys.SAVE_PLAYBACK_STATE] = s.optBoolean("savePlaybackState", true)
                if (s.has("killServiceOnExit")) updates[AuraPreferencesStore.Keys.KILL_SERVICE_ON_EXIT] = s.optBoolean("killServiceOnExit", false)
                if (s.has("recordListeningHistory")) updates[AuraPreferencesStore.Keys.RECORD_LISTENING_HISTORY] = s.optBoolean("recordListeningHistory", true)
                if (s.has("autoCheckUpdates")) updates[AuraPreferencesStore.Keys.AUTO_CHECK_UPDATES] = s.optBoolean("autoCheckUpdates", true)
                prefsStore.editBatch(updates)
            }
            true
        } catch (_: Exception) {
            false
        }
    }

    // --- Explore Category Fan-Out Covers Cache ---
    fun loadExploreCoverCache(): Map<String, List<String>> {
        val raw = prefsStore.getString(AuraPreferencesStore.Keys.EXPLORE_COVER_CACHE_JSON, null)
            ?: return emptyMap()
        return try {
            val obj = JSONObject(raw)
            val map = mutableMapOf<String, List<String>>()
            val keys = obj.keys()
            while (keys.hasNext()) {
                val key = keys.next()
                val arr = obj.optJSONArray(key) ?: continue
                val urls = mutableListOf<String>()
                for (i in 0 until arr.length()) {
                    val u = arr.optString(i)
                    if (!u.isNullOrBlank()) urls.add(u)
                }
                if (urls.isNotEmpty()) {
                    map[key] = urls
                }
            }
            map
        } catch (_: Exception) {
            emptyMap()
        }
    }

    fun saveExploreCoverCache(cache: Map<String, List<String>>) {
        try {
            val obj = JSONObject()
            cache.forEach { (category, urls) ->
                val arr = JSONArray()
                urls.forEach { arr.put(it) }
                obj.put(category, arr)
            }
            prefsStore.putString(AuraPreferencesStore.Keys.EXPLORE_COVER_CACHE_JSON, obj.toString())
        } catch (_: Exception) {
            // Non-critical
        }
    }

    private fun appThemeStyleUpdates(style: AppThemeStyle): Map<Preferences.Key<*>, Any?> {
        val isGlass = style == AppThemeStyle.LIQUID_GLASS
        return mapOf(
            Pair(AuraPreferencesStore.Keys.APP_THEME_STYLE, style.name),
            Pair(AuraPreferencesStore.Keys.LIQUID_GLASS_ENABLED, isGlass),
            Pair(
                AuraPreferencesStore.Keys.NOW_PLAYING_STYLE,
                if (isGlass) NowPlayingStyle.LIQUID_GLASS.name else NowPlayingStyle.SPOTIFY.name
            ),
            Pair(
                AuraPreferencesStore.Keys.LYRICS_STYLE,
                if (isGlass) LyricsStyle.APPLE_MUSIC.name else LyricsStyle.CLASSIC.name
            )
        )
    }

    private fun recentIdentityKey(track: AuraTrack): String =
        LocalRecommendationEngine.songIdentityKey(track)
            .ifBlank { track.videoId.ifBlank { track.id } }

    private fun spotifyCacheKey(title: String, artist: String): String =
        "spot_res_" + "${title.trim().lowercase()}_${artist.trim().lowercase()}".replace(Regex("[^a-z0-9_]"), "_")

    private fun formatHourLabel(hour: Int): String {
        val h = ((hour % 24) + 24) % 24
        return when {
            h == 0 -> "12:00 AM"
            h < 12 -> "$h:00 AM"
            h == 12 -> "12:00 PM"
            else -> "${h - 12}:00 PM"
        }
    }

    private fun serializeLyricsData(lyrics: LyricsData): JSONObject = JSONObject().apply {
        put("videoId", lyrics.videoId)
        put("plainLyrics", lyrics.plainLyrics)
        put("source", lyrics.source)
        put("hasRomanization", lyrics.hasRomanization)
        val linesArr = JSONArray()
        lyrics.syncedLines.forEach { line ->
            linesArr.put(JSONObject().apply {
                put("timeSec", line.timeSec.toDouble())
                put("endTimeSec", line.endTimeSec.toDouble())
                put("text", line.text)
                if (line.romanizedText != null) {
                    put("romanizedText", line.romanizedText)
                }
            })
        }
        put("syncedLines", linesArr)
    }

    private fun parseLyricsData(raw: String, fallbackVideoId: String): LyricsData? = try {
        val obj = JSONObject(raw)
        val linesArr = obj.optJSONArray("syncedLines") ?: JSONArray()
        val lines = mutableListOf<LyricLine>()
        for (i in 0 until linesArr.length()) {
            val lObj = linesArr.optJSONObject(i) ?: continue
            lines.add(
                LyricLine(
                    timeSec = lObj.optDouble("timeSec", 0.0).toFloat(),
                    endTimeSec = lObj.optDouble("endTimeSec", 0.0).toFloat(),
                    text = lObj.optString("text", ""),
                    romanizedText = if (lObj.has("romanizedText")) lObj.getString("romanizedText") else null
                )
            )
        }
        LyricsData(
            videoId = obj.optString("videoId", fallbackVideoId),
            plainLyrics = obj.optString("plainLyrics", ""),
            syncedLines = lines,
            source = obj.optString("source", "SimpMusic Offline"),
            hasRomanization = obj.optBoolean("hasRomanization", false)
        )
    } catch (_: Exception) {
        null
    }

    private fun parsePaletteFromJson(raw: String): AuraPalette? = try {
        val obj = JSONObject(raw)
        val primaryColor = Color(obj.getInt("primary"))
        AuraPalette(
            primary = primaryColor,
            secondary = Color(obj.getInt("secondary")),
            backgroundTop = Color(obj.getInt("backgroundTop")),
            backgroundBottom = Color(obj.getInt("backgroundBottom")),
            surfaceGlass = Color(obj.getInt("surfaceGlass")),
            appleMusicBackdrop = if (obj.has("appleMusicBackdrop")) {
                Color(obj.getInt("appleMusicBackdrop"))
            } else {
                primaryColor
            }
        )
    } catch (_: Exception) {
        null
    }

    private fun parsePlaylistsJson(raw: String): List<AuraPlaylist> = try {
        val arr = JSONArray(raw)
        val list = mutableListOf<AuraPlaylist>()
        for (i in 0 until arr.length()) {
            val obj = arr.optJSONObject(i) ?: continue
            val tracksArr = obj.optJSONArray("tracks") ?: JSONArray()
            val rawDesc = obj.optString("description")
            val cleanDesc = if (rawDesc.startsWith("Imported from Spotify", ignoreCase = true)) "" else rawDesc
            list.add(
                AuraPlaylist(
                    id = obj.optString("id"),
                    name = obj.optString("name"),
                    description = cleanDesc,
                    coverUrl = obj.optString("coverUrl"),
                    tracks = parseTrackArray(tracksArr.toString()),
                    isPodcast = obj.optBoolean("isPodcast", false)
                )
            )
        }
        list
    } catch (_: Exception) {
        emptyList()
    }

    private fun serializeTrackArray(tracks: List<AuraTrack>): JSONArray {
        val arr = JSONArray()
        tracks.forEach { t ->
            arr.put(JSONObject().apply {
                put("id", t.id)
                put("videoId", t.videoId)
                put("title", t.title)
                put("artist", t.artist)
                put("album", t.album)
                put("durationSeconds", t.durationSeconds)
                put("artworkUrl", t.artworkUrl)
                put("genre", t.genre)
                put("source", t.source)
                put("isDownloaded", t.isDownloaded)
                put("localFilePath", t.localFilePath ?: "")
                put("isPodcast", t.isPodcast)
                t.musicVideoType?.let { put("musicVideoType", it) }
                t.viewCount?.let { put("viewCount", it) }
            })
        }
        return arr
    }

    private fun parseTrackArray(rawJson: String): List<AuraTrack> {
        return try {
            val arr = JSONArray(rawJson)
            val out = mutableListOf<AuraTrack>()
            for (i in 0 until arr.length()) {
                val o = arr.optJSONObject(i) ?: continue
                val vid = o.optString("videoId")
                val title = o.optString("title", "Unknown Track")
                if (title.isBlank() && vid.isBlank()) continue
                val trackId = o.optString(
                    "id",
                    if (vid.isNotBlank()) "yt_$vid" else "spot_${System.currentTimeMillis()}_$i"
                )
                out.add(
                    AuraTrack(
                        id = trackId,
                        videoId = vid,
                        title = title,
                        artist = o.optString("artist", "Unknown Artist"),
                        album = o.optString("album", "YouTube Music"),
                        durationSeconds = o.optInt("durationSeconds", 210),
                        artworkUrl = o.optString("artworkUrl"),
                        source = o.optString("source", if (vid.startsWith("spotify_pending")) "spotify" else "youtube"),
                        genre = o.optString("genre", "Pop"),
                        isDownloaded = o.optBoolean("isDownloaded", false),
                        localFilePath = o.optString("localFilePath").takeIf { it.isNotBlank() },
                        isPodcast = o.optBoolean("isPodcast", false),
                        musicVideoType = o.optString("musicVideoType").takeIf { it.isNotBlank() },
                        viewCount = if (o.has("viewCount")) o.optLong("viewCount") else null
                    )
                )
            }
            out
        } catch (_: Exception) {
            emptyList()
        }
    }

    private companion object {
        const val LEGACY_PREFS_NAME = "aura_music_storage"
        const val RECENT_LIMIT = 30
        val migrationLock = Any()

        val KEY_THEME_MODE = stringPreferencesKey("setting_theme_mode")
        val KEY_THEME_COLOR_SOURCE = stringPreferencesKey("setting_theme_color_source")
        val KEY_CUSTOM_ACCENT_ARGB = intPreferencesKey("setting_custom_accent_argb")
        val KEY_SKIP_CROSSFADE_WITHIN_ALBUM = booleanPreferencesKey("setting_skip_crossfade_within_album")
        val KEY_ACTIVE_AUTO_EQ_PROFILE_ID = stringPreferencesKey("setting_active_auto_eq_profile_id")
        val KEY_RELEASE_NOTIFICATIONS_ENABLED = booleanPreferencesKey("setting_release_notifications_enabled")

        // Tier 2 Preferences
        val KEY_PLAYBACK_SPEED = floatPreferencesKey("setting_playback_speed")
        val KEY_PLAYBACK_PITCH_SEMITONES = intPreferencesKey("setting_playback_pitch_semitones")
        val KEY_AI_PROVIDER = stringPreferencesKey("setting_ai_provider")
        val KEY_AI_API_KEY = stringPreferencesKey("setting_ai_api_key")
        val KEY_AI_MODEL_ID = stringPreferencesKey("setting_ai_model_id")
        val KEY_AI_BASE_URL = stringPreferencesKey("setting_ai_base_url")
        val KEY_AI_TARGET_LANGUAGE = stringPreferencesKey("setting_ai_target_language")
        val KEY_AI_TRANSLATION_ENABLED = booleanPreferencesKey("setting_ai_translation_enabled")
        val KEY_SMART_DOWNLOADS_ENABLED = booleanPreferencesKey("setting_smart_downloads_enabled")
        val KEY_SMART_DOWNLOADS_LIMIT = intPreferencesKey("setting_smart_downloads_limit")
        val KEY_AUTO_BACKUP_FREQUENCY = stringPreferencesKey("setting_auto_backup_frequency")
        val KEY_AUTO_BACKUP_KEEP_COUNT = intPreferencesKey("setting_auto_backup_keep_count")
        val KEY_LASTFM_SESSION_KEY = stringPreferencesKey("setting_lastfm_session_key")
        val KEY_LASTFM_API_KEY = stringPreferencesKey("setting_lastfm_api_key")
        val KEY_LASTFM_API_SECRET = stringPreferencesKey("setting_lastfm_api_secret")
        val KEY_LASTFM_ENABLED = booleanPreferencesKey("setting_lastfm_enabled")
        val KEY_DISCORD_TOKEN = stringPreferencesKey("setting_discord_token")
        val KEY_DISCORD_RPC_ENABLED = booleanPreferencesKey("setting_discord_rpc_enabled")
        val KEY_YTM_COOKIE = stringPreferencesKey("setting_ytm_cookie")

        @Volatile
        var processMigrated = false
    }
}
