package com.auramusic.app.core.extractor

import android.util.Log
import com.auramusic.app.data.AuraStorageRepository
import com.auramusic.app.domain.model.AuraPlaylist
import com.auramusic.app.domain.model.AuraTrack
import com.auramusic.app.domain.model.isPendingResolution
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.Request
import org.json.JSONArray
import org.json.JSONObject
import java.net.URLEncoder
import java.util.concurrent.ConcurrentHashMap

internal object SpotifyPlaylistImporter {
    private const val TAG = InnerTubeClient.TAG
    private const val MAX_IMPORT_COUNT = 5000
    private const val DEFAULT_PLAYLIST_QUERY_HASH =
        "21ea11a6ac75a5f3e01fd365d29c18e0a4a8b7dc09f89bbb5d73910190c6acaf"

    private val spotifyResolvedMemoryCache = ConcurrentHashMap<String, AuraTrack>()
    private var storageRepository: AuraStorageRepository? = null

    fun setStorageRepository(storage: AuraStorageRepository) {
        storageRepository = storage
    }

    fun getCachedSpotifyResolvedTrack(title: String, artist: String): AuraTrack? {
        val key = "${title.trim().lowercase()}_${artist.trim().lowercase()}"
        spotifyResolvedMemoryCache[key]?.let { return it }
        return storageRepository?.getCachedSpotifyResolvedTrack(title, artist)?.also {
            spotifyResolvedMemoryCache[key] = it
        }
    }

    fun cacheSpotifyResolvedTrack(title: String, artist: String, track: AuraTrack) {
        val key = "${title.trim().lowercase()}_${artist.trim().lowercase()}"
        spotifyResolvedMemoryCache[key] = track
        storageRepository?.cacheSpotifyResolvedTrack(title, artist, track)
    }

    /**
     * Lazy On-Demand Resolver: Matches a Spotify track to a YouTube Music track.
     * Takes ~200-350ms, checks persistent local cache first, and saves to storage.
     */
    suspend fun resolveSpotifyPendingTrack(track: AuraTrack): AuraTrack = withContext(Dispatchers.IO) {
        if (!track.isPendingResolution) return@withContext track

        val cached = getCachedSpotifyResolvedTrack(track.title, track.artist)
        if (cached != null && !cached.isPendingResolution) {
            return@withContext cached.copy(
                id = track.id,
                album = track.album,
                artworkUrl = if (track.artworkUrl.isNotBlank() && !track.artworkUrl.contains("default")) {
                    track.artworkUrl
                } else {
                    cached.artworkUrl
                }
            )
        }

        try {
            val query = "${track.title} ${track.artist}".trim()
            val match = YtmBrowseParser.searchYouTubeMusic(query, "songs").firstOrNull()
            if (match != null) {
                val resolved = track.copy(
                    videoId = match.videoId,
                    durationSeconds = if (match.durationSeconds > 0) match.durationSeconds else track.durationSeconds,
                    artworkUrl = if (track.artworkUrl.isNotBlank() && !track.artworkUrl.contains("default")) {
                        track.artworkUrl
                    } else {
                        match.artworkUrl
                    },
                    source = "youtube"
                )
                cacheSpotifyResolvedTrack(track.title, track.artist, resolved)
                return@withContext resolved
            }
        } catch (e: Exception) {
            Log.w(TAG, "Failed to resolve Spotify track '${track.title}': ${e.message}")
        }

        track
    }

    /**
     * Direct Client-First Spotify Public Playlist Importer:
     * Fetches `https://open.spotify.com/embed/playlist/{playlistId}`, extracts `__NEXT_DATA__` tracklist,
     * paginates via Pathfinder GraphQL when an anonymous token is present, and creates lazy tracks.
     */
    suspend fun fetchSpotifyPlaylistDirect(
        playlistUrl: String,
        onProgress: (resolved: Int, total: Int) -> Unit = { _, _ -> }
    ): AuraPlaylist? = withContext(Dispatchers.IO) {
        val playlistId = Regex("playlist[/:]?([a-zA-Z0-9]+)").find(playlistUrl)?.groupValues?.getOrNull(1)
            ?: return@withContext null

        val embedUrl = "https://open.spotify.com/embed/playlist/$playlistId"
        val req = Request.Builder()
            .url(embedUrl)
            .header("User-Agent", InnerTubeClient.USER_AGENT)
            .build()

        val html = InnerTubeClient.httpClient.newCall(req).execute().use { resp ->
            if (!resp.isSuccessful) return@withContext null
            resp.body?.string() ?: return@withContext null
        }

        val nextDataJson = Regex("<script id=\"__NEXT_DATA__\" type=\"application/json\">(.*?)</script>", RegexOption.DOT_MATCHES_ALL)
            .find(html)?.groupValues?.getOrNull(1) ?: return@withContext null

        val root = JSONObject(nextDataJson)
        val pageProps = root.optJSONObject("props")?.optJSONObject("pageProps")
        val stateObj = pageProps?.optJSONObject("state")
        val entity = stateObj?.optJSONObject("data")?.optJSONObject("entity") ?: return@withContext null
        val accessToken = stateObj?.optJSONObject("settings")?.optJSONObject("session")?.optString("accessToken", "") ?: ""

        var playlistName = entity.optString("name", "Spotify Imported Playlist")
        var coverUrl = extractEmbedCoverUrl(entity, html)
        val resolvedTracks = mutableListOf<AuraTrack>()

        // Primary Path: Spotify Pathfinder GraphQL API pagination
        if (accessToken.isNotBlank()) {
            try {
                var queryHash = DEFAULT_PLAYLIST_QUERY_HASH
                var offset = 0
                val pageSize = 100
                var totalToFetch = MAX_IMPORT_COUNT
                var hashRefreshed = false

                while (offset < totalToFetch && resolvedTracks.size < MAX_IMPORT_COUNT) {
                    val opName = if (offset == 0) "fetchPlaylist" else "fetchPlaylistContents"
                    val variablesJson = JSONObject().apply {
                        put("uri", "spotify:playlist:$playlistId")
                        put("offset", offset)
                        put("limit", pageSize)
                        put("enableWatchFeedEntrypoint", false)
                    }.toString()
                    val extensionsJson = JSONObject().apply {
                        put("persistedQuery", JSONObject().apply {
                            put("version", 1)
                            put("sha256Hash", queryHash)
                        })
                    }.toString()

                    val pathfinderUrl = "https://api-partner.spotify.com/pathfinder/v1/query" +
                        "?operationName=$opName" +
                        "&variables=${URLEncoder.encode(variablesJson, "UTF-8")}" +
                        "&extensions=${URLEncoder.encode(extensionsJson, "UTF-8")}"

                    val pfReq = Request.Builder()
                        .url(pathfinderUrl)
                        .header("User-Agent", InnerTubeClient.USER_AGENT)
                        .header("Authorization", "Bearer $accessToken")
                        .header("App-Platform", "WebPlayer")
                        .header("Origin", "https://open.spotify.com")
                        .header("Referer", "https://open.spotify.com/")
                        .build()

                    val pfBody = InnerTubeClient.httpClient.newCall(pfReq).execute().use { pfResp ->
                        pfResp.body?.string() ?: ""
                    }

                    if (pfBody.contains("PersistedQueryNotFound") && !hashRefreshed) {
                        hashRefreshed = true
                        val refreshedHash = fetchDynamicSpotifyPlaylistQueryHash(playlistId)
                        if (!refreshedHash.isNullOrBlank()) {
                            queryHash = refreshedHash
                            continue
                        } else {
                            break
                        }
                    }

                    val pfRoot = JSONObject(pfBody)
                    val playlistV2 = pfRoot.optJSONObject("data")?.optJSONObject("playlistV2") ?: break
                    if (offset == 0) {
                        val v2Name = playlistV2.optString("name").trim()
                        if (v2Name.isNotEmpty()) playlistName = v2Name
                        if (coverUrl.isBlank()) {
                            val imgSources = playlistV2.optJSONObject("images")
                                ?.optJSONArray("items")
                                ?.optJSONObject(0)
                                ?.optJSONArray("sources")
                            if (imgSources != null && imgSources.length() > 0) {
                                coverUrl = imgSources.optJSONObject(0)?.optString("url") ?: ""
                            }
                        }
                    }

                    val contentObj = playlistV2.optJSONObject("content") ?: break
                    val totalCount = contentObj.optInt("totalCount", 0)
                    if (totalCount > 0) {
                        totalToFetch = minOf(totalCount, MAX_IMPORT_COUNT)
                    }
                    val itemsArr = contentObj.optJSONArray("items") ?: break
                    if (itemsArr.length() == 0) break

                    for (idx in 0 until itemsArr.length()) {
                        if (resolvedTracks.size >= MAX_IMPORT_COUNT) break
                        val itemData = itemsArr.optJSONObject(idx)
                            ?.optJSONObject("itemV2")
                            ?.optJSONObject("data") ?: continue

                        val trackTitle = itemData.optString("name").trim()
                        if (trackTitle.isEmpty()) continue

                        val artistsArr = itemData.optJSONObject("artists")?.optJSONArray("items")
                        val artistNames = mutableListOf<String>()
                        if (artistsArr != null) {
                            for (a in 0 until artistsArr.length()) {
                                val aName = artistsArr.optJSONObject(a)
                                    ?.optJSONObject("profile")
                                    ?.optString("name")
                                    ?.trim() ?: ""
                                if (aName.isNotEmpty()) artistNames.add(aName)
                            }
                        }
                        val artistStr = artistNames.joinToString(", ").ifBlank { "Unknown Artist" }

                        val albumObj = itemData.optJSONObject("albumOfTrack")
                        val trackAlbumName = albumObj?.optString("name")?.trim()?.takeIf { it.isNotEmpty() } ?: playlistName
                        val trackCoverUrl = extractBestCoverSource(albumObj?.optJSONObject("coverArt")?.optJSONArray("sources"))

                        val durationMs = itemData.optJSONObject("trackDuration")?.optLong("totalMilliseconds", 210000L) ?: 210000L
                        val durationSec = (durationMs / 1000L).toInt().coerceAtLeast(1)
                        val isExplicit = itemData.optJSONObject("contentRating")?.optString("label") == "EXPLICIT"
                        val trackIndex = resolvedTracks.size

                        resolvedTracks.add(
                            buildLazyOrCachedTrack(
                                playlistId = playlistId,
                                index = trackIndex,
                                title = trackTitle,
                                artist = artistStr,
                                album = trackAlbumName,
                                durationSec = durationSec,
                                artworkUrl = trackCoverUrl.ifBlank { coverUrl },
                                isExplicit = isExplicit,
                                applyFullCachedOverrides = true
                            )
                        )
                        onProgress(resolvedTracks.size, totalToFetch)
                    }

                    offset += itemsArr.length()
                    if (itemsArr.length() < pageSize) break
                }
            } catch (e: Exception) {
                Log.w(TAG, "Spotify Pathfinder pagination fallback: ${e.message}")
            }
        }

        // Fallback Path: Embed trackList
        if (resolvedTracks.isEmpty()) {
            val trackListArray = entity.optJSONArray("trackList") ?: JSONArray()
            val totalToImport = minOf(trackListArray.length(), MAX_IMPORT_COUNT)
            for (i in 0 until totalToImport) {
                val item = trackListArray.optJSONObject(i) ?: continue
                val title = item.optString("title").trim()
                val subtitle = item.optString("subtitle").replace("\u00A0", " ").trim()
                if (title.isEmpty()) continue
                val durationMs = item.optLong("duration", 210000L)
                val durationSec = (durationMs / 1000L).toInt().coerceAtLeast(1)

                resolvedTracks.add(
                    buildLazyOrCachedTrack(
                        playlistId = playlistId,
                        index = i,
                        title = title,
                        artist = subtitle,
                        album = playlistName,
                        durationSec = durationSec,
                        artworkUrl = "",
                        isExplicit = false,
                        applyFullCachedOverrides = false
                    )
                )
                onProgress(i + 1, totalToImport)
            }
        }

        AuraPlaylist(
            id = "spotify_$playlistId",
            name = playlistName,
            description = "",
            coverUrl = coverUrl.ifBlank { resolvedTracks.firstOrNull()?.highResArtworkUrl ?: "" },
            tracks = resolvedTracks
        )
    }

    private fun buildLazyOrCachedTrack(
        playlistId: String,
        index: Int,
        title: String,
        artist: String,
        album: String,
        durationSec: Int,
        artworkUrl: String,
        isExplicit: Boolean,
        applyFullCachedOverrides: Boolean
    ): AuraTrack {
        val trackId = "spot_${playlistId}_$index"
        val cached = getCachedSpotifyResolvedTrack(title, artist)
        if (cached != null && !cached.isPendingResolution) {
            return if (applyFullCachedOverrides) {
                cached.copy(
                    id = trackId,
                    album = album,
                    durationSeconds = durationSec,
                    artworkUrl = artworkUrl.ifBlank { cached.artworkUrl },
                    isExplicit = isExplicit
                )
            } else {
                cached.copy(id = trackId, album = album)
            }
        }
        return AuraTrack(
            id = trackId,
            videoId = "spotify_pending_${playlistId}_$index",
            title = title,
            artist = artist,
            album = album,
            durationSeconds = durationSec,
            artworkUrl = artworkUrl,
            isExplicit = isExplicit,
            source = "spotify"
        )
    }

    private fun extractBestCoverSource(coverSources: JSONArray?): String {
        if (coverSources == null || coverSources.length() == 0) return ""
        var bestUrl = ""
        var bestWidth = -1
        for (c in 0 until coverSources.length()) {
            val src = coverSources.optJSONObject(c) ?: continue
            val url = src.optString("url")
            val w = src.optInt("width", 0)
            if (url.isNotBlank() && w >= bestWidth) {
                bestWidth = w
                bestUrl = url
            }
        }
        return bestUrl
    }

    private fun extractEmbedCoverUrl(entity: JSONObject, html: String): String {
        val visualImages = entity.optJSONObject("visualIdentity")?.optJSONArray("image")
        if (visualImages != null && visualImages.length() > 0) {
            for (idx in visualImages.length() - 1 downTo 0) {
                val candidate = visualImages.optJSONObject(idx)?.optString("url").orEmpty()
                if (candidate.isNotBlank()) return candidate
            }
        }

        val attributes = entity.optJSONArray("attributes")
        if (attributes != null) {
            for (a in 0 until attributes.length()) {
                val attr = attributes.optJSONObject(a) ?: continue
                val key = attr.optString("key")
                val value = attr.optString("value")
                if ((key == "image_url" || key == "header_image_url_desktop") && value.isNotBlank()) {
                    return value
                }
            }
        }

        val coverSources = entity.optJSONObject("coverArt")?.optJSONArray("sources")
        if (coverSources != null && coverSources.length() > 0) {
            val url = coverSources.optJSONObject(0)?.optString("url").orEmpty()
            if (url.isNotBlank()) return url
        }

        val preloadMatch = Regex("<link rel=\"preload\" as=\"image\" href=\"([^\"]+)\"").find(html)
        if (preloadMatch != null) return preloadMatch.groupValues[1]

        val ogImageMatch = Regex("<meta property=\"og:image\" content=\"([^\"]+)\"").find(html)
        if (ogImageMatch != null) return ogImageMatch.groupValues[1]

        return ""
    }

    private fun fetchDynamicSpotifyPlaylistQueryHash(playlistId: String): String? {
        return try {
            val pageReq = Request.Builder()
                .url("https://open.spotify.com/playlist/$playlistId")
                .header("User-Agent", InnerTubeClient.USER_AGENT)
                .build()
            val pageHtml = InnerTubeClient.httpClient.newCall(pageReq).execute().use { it.body?.string() ?: "" }
            val scriptUrl = Regex("""https://open\.spotifycdn\.com/cdn/build/web-player/web-player\.[a-f0-9]+\.js""")
                .find(pageHtml)?.value ?: return null
            val jsReq = Request.Builder()
                .url(scriptUrl)
                .header("User-Agent", InnerTubeClient.USER_AGENT)
                .build()
            val jsContent = InnerTubeClient.httpClient.newCall(jsReq).execute().use { it.body?.string() ?: "" }
            Regex(""""fetchPlaylist"\s*,\s*"query"\s*,\s*"([a-f0-9]{64})"""")
                .find(jsContent)?.groupValues?.getOrNull(1)
        } catch (_: Exception) {
            null
        }
    }
}
