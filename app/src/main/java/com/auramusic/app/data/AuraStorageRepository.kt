package com.auramusic.app.data

import android.content.Context
import android.content.SharedPreferences
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import com.auramusic.app.domain.model.AuraPalette
import com.auramusic.app.domain.model.AuraPlaylist
import com.auramusic.app.domain.model.AuraTrack
import com.auramusic.app.domain.model.LyricLine
import com.auramusic.app.domain.model.LyricsData
import com.auramusic.app.domain.model.ListeningStats
import com.auramusic.app.domain.model.NowPlayingStyle
import com.auramusic.app.core.recommendations.LocalRecommendationEngine
import org.json.JSONArray
import org.json.JSONObject

class AuraStorageRepository(context: Context) {
    private val prefs: SharedPreferences =
        context.applicationContext.getSharedPreferences("aura_music_storage", Context.MODE_PRIVATE)

    fun loadFavorites(): List<AuraTrack> {
        val raw = prefs.getString("favorites_json", null) ?: return emptyList()
        return parseTrackArray(raw)
    }

    fun saveFavorites(tracks: List<AuraTrack>) {
        prefs.edit().putString("favorites_json", serializeTrackArray(tracks).toString()).apply()
    }

    fun loadDownloads(): List<AuraTrack> {
        val raw = prefs.getString("downloads_json", null) ?: return emptyList()
        return parseTrackArray(raw)
    }

    fun saveDownloads(tracks: List<AuraTrack>) {
        prefs.edit().putString("downloads_json", serializeTrackArray(tracks).toString()).apply()
    }

    fun loadPlaylists(): List<AuraPlaylist> {
        val raw = prefs.getString("playlists_json", null) ?: return emptyList()
        return try {
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
        } catch (e: Exception) {
            emptyList()
        }
    }

    fun savePlaylists(playlists: List<AuraPlaylist>) {
        val arr = JSONArray()
        playlists.forEach { pl ->
            arr.put(JSONObject().apply {
                put("id", pl.id)
                put("name", pl.name)
                put("description", pl.description)
                put("coverUrl", pl.coverUrl)
                put("tracks", serializeTrackArray(pl.tracks))
                put("isPodcast", pl.isPodcast)
            })
        }
        prefs.edit().putString("playlists_json", arr.toString()).apply()
    }

    fun getLyricsOffsetMs(videoId: String): Int {
        return prefs.getInt("lyric_offset_$videoId", 0)
    }

    fun setLyricsOffsetMs(videoId: String, offsetMs: Int) {
        prefs.edit().putInt("lyric_offset_$videoId", offsetMs.coerceIn(-120000, 120000)).apply()
    }

    fun saveOfflineLyrics(videoId: String, lyrics: LyricsData) {
        try {
            val json = JSONObject().apply {
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
            prefs.edit().putString("offline_lyrics_$videoId", json.toString()).apply()
        } catch (e: Exception) {
            // Non-critical
        }
    }

    fun loadOfflineLyrics(videoId: String): LyricsData? {
        val raw = prefs.getString("offline_lyrics_$videoId", null) ?: return null
        return try {
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
                videoId = obj.optString("videoId", videoId),
                plainLyrics = obj.optString("plainLyrics", ""),
                syncedLines = lines,
                source = obj.optString("source", "SimpMusic Offline"),
                hasRomanization = obj.optBoolean("hasRomanization", false)
            )
        } catch (e: Exception) {
            null
        }
    }

    fun saveOfflinePalette(videoId: String, palette: AuraPalette) {
        try {
            val json = JSONObject().apply {
                put("primary", palette.primary.toArgb())
                put("secondary", palette.secondary.toArgb())
                put("backgroundTop", palette.backgroundTop.toArgb())
                put("backgroundBottom", palette.backgroundBottom.toArgb())
                put("surfaceGlass", palette.surfaceGlass.toArgb())
            }
            prefs.edit().putString("offline_palette_$videoId", json.toString()).apply()
        } catch (e: Exception) {
            // Non-critical
        }
    }

    fun loadOfflinePalette(videoId: String): AuraPalette? {
        val raw = prefs.getString("offline_palette_$videoId", null) ?: return null
        return try {
            val obj = JSONObject(raw)
            AuraPalette(
                primary = Color(obj.getInt("primary")),
                secondary = Color(obj.getInt("secondary")),
                backgroundTop = Color(obj.getInt("backgroundTop")),
                backgroundBottom = Color(obj.getInt("backgroundBottom")),
                surfaceGlass = Color(obj.getInt("surfaceGlass"))
            )
        } catch (e: Exception) {
            null
        }
    }

    fun loadNowPlayingStyle(): NowPlayingStyle {
        val name = prefs.getString("now_playing_style", NowPlayingStyle.SPOTIFY.name)
        if (name == "APPLE_MUSIC") return NowPlayingStyle.LIQUID_GLASS
        return NowPlayingStyle.entries.firstOrNull { it.name == name } ?: NowPlayingStyle.SPOTIFY
    }

    fun saveNowPlayingStyle(style: NowPlayingStyle) {
        prefs.edit().putString("now_playing_style", style.name).apply()
    }

    fun recordRecentlyPlayed(track: AuraTrack) {
        val current = loadRecentlyPlayed().toMutableList()
        val targetId = track.videoId.ifBlank { track.id }
        val targetKey = LocalRecommendationEngine.songIdentityKey(track)
        current.removeAll {
            (targetId.isNotBlank() && (it.videoId == targetId || it.id == targetId)) ||
            it.videoId == track.videoId ||
            (it.videoId.isBlank() && it.id == track.id) ||
            (targetKey.isNotBlank() && LocalRecommendationEngine.songIdentityKey(it) == targetKey)
        }
        current.add(0, track)
        val capped = current.distinctBy { LocalRecommendationEngine.songIdentityKey(it) }.take(30)
        prefs.edit().putString("recently_played_tracks", serializeTrackArray(capped).toString()).apply()
    }

    fun removeRecentlyPlayed(videoId: String, trackKey: String? = null) {
        val current = loadRecentlyPlayed().toMutableList()
        current.removeAll {
            it.videoId == videoId ||
            it.id == videoId ||
            (trackKey != null && LocalRecommendationEngine.songIdentityKey(it) == trackKey)
        }
        prefs.edit().putString("recently_played_tracks", serializeTrackArray(current).toString()).apply()
    }

    fun loadRecentlyPlayed(): List<AuraTrack> {
        val raw = prefs.getString("recently_played_tracks", null) ?: return emptyList()
        return parseTrackArray(raw).distinctBy { LocalRecommendationEngine.songIdentityKey(it) }
    }

    fun recordRecommendationSignal(track: AuraTrack, signal: String) {
        if (track.videoId.isBlank()) return
        runCatching {
            val all = JSONObject(prefs.getString("recommendation_signals", "{}") ?: "{}")
            val row = all.optJSONObject(track.videoId) ?: JSONObject()
            val key = when (signal) {
                "play" -> "plays"
                "complete" -> "completions"
                "skip" -> "skips"
                else -> return
            }
            row.put(key, row.optInt(key, 0) + 1)
            all.put(track.videoId, row)
            prefs.edit().putString("recommendation_signals", all.toString()).apply()
        }
    }

    fun loadRecommendationInteractionScores(): Map<String, Float> = runCatching {
        val all = JSONObject(prefs.getString("recommendation_signals", "{}") ?: "{}")
        all.keys().asSequence().mapNotNull { videoId ->
            val row = all.optJSONObject(videoId) ?: return@mapNotNull null
            val score = row.optInt("plays") * 0.45f +
                row.optInt("completions") * 1.4f -
                row.optInt("skips") * 2.2f
            videoId to score.coerceIn(-8f, 8f)
        }.toMap()
    }.getOrDefault(emptyMap())

    fun recordTrackPlay(track: AuraTrack): ListeningStats {
        recordRecentlyPlayed(track)
        val totalMin = prefs.getInt("stats_total_min", 0) + (track.durationSeconds / 60).coerceAtLeast(3)
        val totalPlays = prefs.getInt("stats_tracks_played", 0) + 1
        prefs.edit()
            .putInt("stats_total_min", totalMin)
            .putInt("stats_tracks_played", totalPlays)
            .apply()

        if (track.videoId.isNotBlank()) {
            val playedTracks = JSONObject(prefs.getString("stats_track_plays", "{}") ?: "{}")
            val row = playedTracks.optJSONObject(track.videoId) ?: JSONObject()
            row.put("count", row.optInt("count", 0) + 1)
            row.put("track", serializeTrackArray(listOf(track)).optJSONObject(0))
            playedTracks.put(track.videoId, row)
            prefs.edit().putString("stats_track_plays", playedTracks.toString()).apply()
        }

        val artistsJson = JSONObject(prefs.getString("stats_artists", "{}") ?: "{}")
        val cleanArtist = track.artist.split(",", "ft.").firstOrNull()?.trim() ?: track.artist
        artistsJson.put(cleanArtist, artistsJson.optInt(cleanArtist, 0) + 1)
        prefs.edit().putString("stats_artists", artistsJson.toString()).apply()

        val genresJson = JSONObject(prefs.getString("stats_genres", "{}") ?: "{}")
        genresJson.put(track.genre, genresJson.optInt(track.genre, 0) + 1)
        prefs.edit().putString("stats_genres", genresJson.toString()).apply()

        return loadListeningStats()
    }

    fun loadListeningStats(): ListeningStats {
        val totalMin = prefs.getInt("stats_total_min", 0)
        val totalPlays = prefs.getInt("stats_tracks_played", 0)

        val artistsJson = JSONObject(prefs.getString("stats_artists", "{}") ?: "{}")
        val topArtists = artistsJson.keys().asSequence()
            .map { it to artistsJson.optInt(it, 1) }
            .sortedByDescending { it.second }
            .take(5)
            .toList()

        val genresJson = JSONObject(prefs.getString("stats_genres", "{}") ?: "{}")
        val topGenres = genresJson.keys().asSequence()
            .map { it to genresJson.optInt(it, 1) }
            .sortedByDescending { it.second }
            .take(4)
            .toList()

        val topTracks = runCatching {
            val playedTracks = JSONObject(prefs.getString("stats_track_plays", "{}") ?: "{}")
            playedTracks.keys().asSequence().mapNotNull { videoId ->
                val row = playedTracks.optJSONObject(videoId) ?: return@mapNotNull null
                val count = row.optInt("count", 0)
                if (count <= 0) return@mapNotNull null
                val trackJson = row.optJSONObject("track") ?: return@mapNotNull null
                val track = parseTrackArray(JSONArray().put(trackJson).toString()).firstOrNull()
                    ?: return@mapNotNull null
                track to count
            }.sortedByDescending { it.second }.take(3).toList()
        }.getOrDefault(emptyList())

        return ListeningStats(
            totalMinutes = totalMin,
            tracksPlayed = totalPlays,
            topArtists = topArtists,
            topGenres = topGenres,
            topTracks = topTracks,
            peakHourLabel = "10:00 PM – 1:00 AM"
        )
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
                val trackId = o.optString("id", if (vid.isNotBlank()) "yt_$vid" else "spot_${System.currentTimeMillis()}_$i")
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
                        isPodcast = o.optBoolean("isPodcast", false)
                    )
                )
            }
            out
        } catch (e: Exception) {
            emptyList()
        }
    }

    fun cacheSpotifyResolvedTrack(title: String, artist: String, track: AuraTrack) {
        val cleanKey = "spot_res_" + "${title.trim().lowercase()}_${artist.trim().lowercase()}".replace(Regex("[^a-z0-9_]"), "_")
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
        prefs.edit().putString(cleanKey, obj.toString()).apply()
    }

    fun getCachedSpotifyResolvedTrack(title: String, artist: String): AuraTrack? {
        val cleanKey = "spot_res_" + "${title.trim().lowercase()}_${artist.trim().lowercase()}".replace(Regex("[^a-z0-9_]"), "_")
        val raw = prefs.getString(cleanKey, null) ?: return null
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
    fun loadAudioQuality(): String = prefs.getString("setting_audio_quality", "Auto") ?: "Auto"
    fun saveAudioQuality(quality: String) {
        prefs.edit().putString("setting_audio_quality", quality).apply()
    }

    fun loadVideoQuality(): String = prefs.getString("setting_video_quality", "Auto (1080p)") ?: "Auto (1080p)"
    fun saveVideoQuality(quality: String) {
        prefs.edit().putString("setting_video_quality", quality).apply()
    }

    fun loadRomanizationLanguages(): Set<String> {
        val raw = prefs.getString("setting_romanization_languages", null)
        return raw?.split(",")?.map { it.trim() }?.filter { it.isNotBlank() }?.toSet()
            ?: setOf("Japanese", "Korean", "Chinese")
    }
    fun saveRomanizationLanguages(languages: Set<String>) {
        prefs.edit().putString("setting_romanization_languages", languages.joinToString(",")).apply()
    }

    fun loadNormalizeVolume(): Boolean = prefs.getBoolean("setting_normalize_volume", true)
    fun saveNormalizeVolume(enabled: Boolean) {
        prefs.edit().putBoolean("setting_normalize_volume", enabled).apply()
    }

    fun loadSkipSilence(): Boolean = prefs.getBoolean("setting_skip_silence", false)
    fun saveSkipSilence(enabled: Boolean) {
        prefs.edit().putBoolean("setting_skip_silence", enabled).apply()
    }

    fun loadCrossfadeDuration(): Int = prefs.getInt("setting_crossfade_sec", 0)
    fun saveCrossfadeDuration(sec: Int) {
        prefs.edit().putInt("setting_crossfade_sec", sec).apply()
    }

    fun loadMaxCacheSize(): Int = prefs.getInt("setting_max_cache_mb", 1024)
    fun saveMaxCacheSize(sizeMb: Int) {
        prefs.edit().putInt("setting_max_cache_mb", sizeMb).apply()
    }

    fun loadEqualizerBands(): List<Float> {
        val raw = prefs.getString("setting_eq_bands", null) ?: return listOf(0f, 0f, 0f, 0f, 0f, 0f, 0f, 0f, 0f, 0f)
        return raw.split(",").mapNotNull { it.toFloatOrNull() }.takeIf { it.size == 10 }
            ?: listOf(0f, 0f, 0f, 0f, 0f, 0f, 0f, 0f, 0f, 0f)
    }
    fun saveEqualizerBands(bands: List<Float>) {
        prefs.edit().putString("setting_eq_bands", bands.joinToString(",")).apply()
    }

    fun loadEqualizerPreamp(): Float = prefs.getFloat("setting_eq_preamp", 0f)
    fun saveEqualizerPreamp(preampDb: Float) {
        prefs.edit().putFloat("setting_eq_preamp", preampDb).apply()
    }

    fun loadEqualizerPreset(): String = prefs.getString("setting_eq_preset", "Flat") ?: "Flat"
    fun saveEqualizerPreset(name: String) {
        prefs.edit().putString("setting_eq_preset", name).apply()
    }

    fun loadChartCountry(): String = prefs.getString("setting_chart_country", "US") ?: "US"
    fun saveChartCountry(code: String) {
        prefs.edit().putString("setting_chart_country", code).apply()
    }
}
