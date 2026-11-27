package com.auramusic.app.core.extractor

import android.util.Log
import com.auramusic.app.domain.model.ArtistAlbum
import com.auramusic.app.domain.model.ArtistCarousel
import com.auramusic.app.domain.model.ArtistCarouselItem
import com.auramusic.app.domain.model.ArtistDetail
import com.auramusic.app.domain.model.ArtistSong
import com.auramusic.app.domain.model.AuraChart
import com.auramusic.app.domain.model.AuraChartArtistItem
import com.auramusic.app.domain.model.AuraChartPlaylistSection
import com.auramusic.app.domain.model.AuraChartPodcastSection
import com.auramusic.app.domain.model.AuraTrack
import com.auramusic.app.domain.model.AuraVideoInfo
import com.auramusic.app.domain.model.MoodBrowseResult
import com.auramusic.app.domain.model.MoodCategory
import com.auramusic.app.domain.model.PlaylistDetail
import com.auramusic.app.domain.model.PlaylistTrackItem
import com.auramusic.app.domain.model.YtmChartSection
import com.auramusic.app.domain.model.YtmCommunityPlaylist
import com.auramusic.app.domain.model.YtmMoodBrowseParams
import com.auramusic.app.domain.model.YtmMoodCategoryItem
import com.auramusic.app.domain.model.YtmMoodCategorySection
import com.auramusic.app.domain.model.YtmMoodItem
import com.auramusic.app.domain.model.YtmMoodItemType
import com.auramusic.app.domain.model.YtmMoodShelf
import com.auramusic.app.domain.model.YtmTopArtist
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import org.schabi.newpipe.extractor.ServiceList as BraveServiceList
import org.schabi.newpipe.extractor.stream.StreamInfoItem
import java.util.Locale
import java.util.concurrent.ConcurrentHashMap
import kotlin.math.ln

internal object YtmBrowseParser {
    private const val TAG = InnerTubeClient.TAG
    private val PLAY_COUNT_REGEX = Regex("""([,\d]+(?:\.\d+)?\s*[KkMmBb]?\s*(?:plays|views))""", RegexOption.IGNORE_CASE)

    private val videoInfoCache = ConcurrentHashMap<String, AuraVideoInfo>()
    private val videoToOfficialAudioCache = ConcurrentHashMap<String, AuraTrack>()

    // -------------------------------------------------------------------------
    // Video / Audio Matching & Normalization
    // -------------------------------------------------------------------------

    fun primaryArtistName(artist: String): String =
        artist
            .replace(Regex("(?i)\\s+(feat(?:uring)?|ft\\.?|with)\\s+.*$"), "")
            .split(Regex("\\s*(?:,|;|&)\\s*"))
            .firstOrNull { it.isNotBlank() }
            ?.trim()
            ?: artist.trim()

    private fun artistVariants(artist: String): List<String> =
        artist
            .split(Regex("(?i)\\s*(?:,|;|&|\\bfeat(?:uring)?\\.?\\b|\\bft\\.?\\b|\\bwith\\b)\\s*"))
            .map { it.trim() }
            .filter { it.isNotBlank() && !it.equals("YouTube Artist", ignoreCase = true) }
            .ifEmpty { listOf(artist.trim()) }

    private fun normalizedVideoMatchText(value: String): String =
        value.lowercase()
            .replace(Regex("(?i)\\b(official|music|video|mv|vevo|topic|hd|4k)\\b"), " ")
            .replace(Regex("[^\\p{L}\\p{N}]+"), " ")
            .trim()

    private fun normalizedVideoWords(value: String): Set<String> {
        val base = normalizedVideoMatchText(value)
            .split(Regex("\\s+"))
            .filter(String::isNotBlank)
        val collapsed = value.lowercase()
            .replace(Regex("(?i)\\b(official|music|video|mv|vevo|topic|hd|4k)\\b"), " ")
            .split(Regex("\\s+"))
            .map { it.replace(Regex("[^\\p{L}\\p{N}]"), "") }
            .filter { it.length >= 2 }
        return (base + collapsed).toSet()
    }

    fun cleanSongTitleForVideoSearch(rawTitle: String, rawArtist: String): String {
        var cleaned = rawTitle
            .replace(Regex("(?i)\\(feat\\..*?\\)|\\[feat\\..*?\\]|\\(ft\\..*?\\)|\\[ft\\..*?\\]"), "")
            .substringBefore("(")
            .substringBefore("[")
            .substringBefore("|")
            .replace(Regex("(?i)\\s+\\b(?:feat(?:uring)?|ft)\\b\\.?\\s+.*$"), "")
            .replace(Regex("(?i)\\b(official|music|video|audio|lyrics?|visualizer|remastered|remaster|hd|hq|4k|mv)\\b"), "")
            .trim()

        if (cleaned.contains(" - ")) {
            val beforeDash = cleaned.substringBefore(" - ").trim()
            val afterDash = cleaned.substringAfter(" - ").trim()
            val artistWords = artistVariants(rawArtist).flatMap { normalizedVideoWords(it) }.toSet()
            val prefixWords = normalizedVideoWords(beforeDash)
            if (afterDash.isNotBlank() && prefixWords.isNotEmpty() && prefixWords.intersect(artistWords).isNotEmpty()) {
                cleaned = afterDash
            }
        }
        return cleaned.trim().ifBlank { rawTitle.trim() }
    }

    private fun officialVideoMatchScore(candidateTitle: String, requestedTitle: String): Float {
        val wanted = normalizedVideoWords(requestedTitle)
        val actual = normalizedVideoWords(candidateTitle)
        if (wanted.isEmpty() || actual.isEmpty()) return 0f
        return wanted.intersect(actual).size.toFloat() / wanted.size
    }

    private fun isOfficialInnerTubeVideoType(type: String?): Boolean =
        type.equals("MUSIC_VIDEO_TYPE_OMV", ignoreCase = true) ||
            type.equals("MUSIC_VIDEO_TYPE_OFFICIAL_SOURCE_MUSIC", ignoreCase = true)

    private fun hasOfficialVideoCue(title: String, artist: String): Boolean =
        Regex("\\b(official|vevo)\\b").containsMatchIn(title) ||
            Regex("\\b(mv|m/v)\\b").containsMatchIn(title) ||
            Regex("\\bmusic\\s+video\\b").containsMatchIn(title) ||
            Regex("\\b(vevo|official)\\b").containsMatchIn(artist)

    private fun isOfficialVideoMatch(
        candidate: AuraTrack,
        requestedTitle: String,
        requestedArtist: String,
        currentVideoId: String
    ): Boolean {
        if (candidate.videoId.isBlank()) return false

        val title = candidate.title.lowercase()
        val artist = candidate.artist.lowercase()
        val hasVideoCue = hasOfficialVideoCue(title, artist)

        if (candidate.videoId == currentVideoId &&
            !isOfficialInnerTubeVideoType(candidate.musicVideoType) &&
            !hasVideoCue
        ) {
            return false
        }

        val lowerRequestedTitle = requestedTitle.lowercase()
        val excludedTerms = listOf(
            "cover", "karaoke", "lyrics", "lyric video", "reaction", "instrumental", "audio", "live", "acoustic",
            "tribute", "fan made", "fanmade", "unofficial", "slowed", "sped up", "nightcore", "audio only", " - topic",
            "8d audio", "dance practice", "dance cover", "tutorial", "guitar lesson", "bass lesson"
        )
        for (term in excludedTerms) {
            if (!lowerRequestedTitle.contains(term) && (title.contains(term) || artist.contains(term))) {
                return false
            }
        }

        val wantedTitleWords = normalizedVideoMatchText(requestedTitle)
            .split(Regex("\\s+")).filter(String::isNotBlank).toSet()
        val foundTitleWords = normalizedVideoWords(candidate.title)
        if (wantedTitleWords.isEmpty() || foundTitleWords.isEmpty()) return false
        val titleMatch = wantedTitleWords.intersect(foundTitleWords).size.toFloat() / wantedTitleWords.size
        if (titleMatch < 0.65f) return false

        val foundArtistWords = normalizedVideoWords(candidate.artist)
        val foundTitleArtistWords = normalizedVideoWords(candidate.title)
        if (foundArtistWords.isEmpty() && foundTitleArtistWords.isEmpty()) return false

        val variants = artistVariants(requestedArtist)
        var uploaderArtistMatch = 0f
        var titleArtistMatch = 0f
        for (variant in variants) {
            val wantedWords = normalizedVideoMatchText(variant)
                .split(Regex("\\s+")).filter(String::isNotBlank).toSet()
            if (wantedWords.isEmpty()) continue
            val upScore = wantedWords.intersect(foundArtistWords).size.toFloat() / wantedWords.size
            val tiScore = wantedWords.intersect(foundTitleArtistWords).size.toFloat() / wantedWords.size
            if (upScore > uploaderArtistMatch) uploaderArtistMatch = upScore
            if (tiScore > titleArtistMatch) titleArtistMatch = tiScore
        }

        val hasArtistPresence = uploaderArtistMatch >= 0.35f || titleArtistMatch >= 0.35f

        when (candidate.musicVideoType?.uppercase()) {
            "MUSIC_VIDEO_TYPE_ATV" -> return false
            "MUSIC_VIDEO_TYPE_OMV", "MUSIC_VIDEO_TYPE_OFFICIAL_SOURCE_MUSIC" ->
                return titleMatch >= 0.65f && hasArtistPresence
            "MUSIC_VIDEO_TYPE_UGC" ->
                return titleMatch >= 0.75f && hasVideoCue && hasArtistPresence
        }

        if (hasVideoCue && titleMatch >= 0.65f && hasArtistPresence) return true
        if (uploaderArtistMatch >= 0.75f && titleMatch >= 0.75f) return true

        return false
    }

    private fun officialVideoCandidateScore(
        candidate: AuraTrack,
        requestedTitle: String,
        rankIndex: Int = 0
    ): Float {
        var score = 0f
        if (isOfficialInnerTubeVideoType(candidate.musicVideoType)) score += 15f
        if (hasOfficialVideoCue(candidate.title.lowercase(), candidate.artist.lowercase())) score += 10f
        score += officialVideoMatchScore(candidate.title, requestedTitle) * 5f
        candidate.viewCount?.takeIf { it > 0 }?.let { views ->
            score += ln(views.toDouble() + 1.0).toFloat()
        }
        score += (20 - rankIndex).coerceAtLeast(0) * 0.25f
        return score
    }

    suspend fun findOfficialMusicVideo(track: AuraTrack): AuraTrack? = withContext(Dispatchers.IO) {
        try {
            val lowerCurrentTitle = track.title.lowercase()
            val lowerCurrentArtist = track.artist.lowercase()
            val isCurrentAlreadyMv = (lowerCurrentTitle.contains("official video") ||
                lowerCurrentTitle.contains("music video") ||
                lowerCurrentTitle.contains("official mv") ||
                lowerCurrentTitle.endsWith("mv") ||
                isOfficialInnerTubeVideoType(track.musicVideoType)) &&
                !lowerCurrentArtist.contains("topic") &&
                !lowerCurrentTitle.contains("audio") &&
                !lowerCurrentTitle.contains("lyric")

            if (isCurrentAlreadyMv) {
                val direct = StreamUrlResolver.resolveVideoStream(track.videoId)
                if (direct != null) {
                    return@withContext track
                }
            }

            val cleanArtist = primaryArtistName(track.artist)
            val cleanTitle = cleanSongTitleForVideoSearch(track.title, track.artist)
            val titleForQuery = cleanTitle.ifBlank { track.title }
            val artistForQuery = cleanArtist.ifBlank { track.artist }
            val fallbackQuery = "$titleForQuery $artistForQuery official music video".trim()
            val queryVariants = listOf(
                "$titleForQuery $artistForQuery",
                "$artistForQuery $titleForQuery official music video",
                "$titleForQuery $artistForQuery official music video"
            ).map { it.trim() }.distinct()

            val candidatesById = linkedMapOf<String, AuraTrack>()
            val candidateRankIndices = mutableMapOf<String, Int>()
            var globalRank = 0
            queryVariants.map { searchQuery ->
                async { searchYouTubeMusic(searchQuery, filter = "videos", prefetchTopResult = false) }
            }.awaitAll().flatten().forEach { result ->
                if (!candidateRankIndices.containsKey(result.videoId)) {
                    candidateRankIndices[result.videoId] = globalRank++
                }
                val existing = candidatesById[result.videoId]
                if (existing == null) {
                    candidatesById[result.videoId] = result
                } else if ((existing.musicVideoType == null && result.musicVideoType != null) ||
                    (existing.viewCount == null && result.viewCount != null)
                ) {
                    candidatesById[result.videoId] = existing.copy(
                        musicVideoType = result.musicVideoType ?: existing.musicVideoType,
                        viewCount = result.viewCount ?: existing.viewCount
                    )
                }
            }

            var candidate = candidatesById.values
                .filter { isOfficialVideoMatch(it, titleForQuery, track.artist, track.videoId) }
                .maxByOrNull { officialVideoCandidateScore(it, titleForQuery, candidateRankIndices[it.videoId] ?: 20) }

            if (candidate == null) {
                try {
                    StreamUrlResolver.initExtractors()
                    val extractor = BraveServiceList.YouTube.getSearchExtractor(fallbackQuery)
                    extractor.fetchPage()
                    val items = extractor.initialPage?.items ?: emptyList()
                    val ytItem = items.filterIsInstance<StreamInfoItem>().firstOrNull { item ->
                        val vidId = item.url?.substringAfter("v=")?.substringBefore("&") ?: ""
                        val fallbackTrack = AuraTrack(
                            id = "yt_$vidId",
                            videoId = vidId,
                            title = item.name.orEmpty(),
                            artist = item.uploaderName?.replace(" - Topic", "")?.trim().orEmpty()
                        )
                        vidId.isNotBlank() && isOfficialVideoMatch(fallbackTrack, titleForQuery, track.artist, track.videoId)
                    }
                    if (ytItem != null) {
                        val vidId = ytItem.url?.substringAfter("v=")?.substringBefore("&") ?: ""
                        candidate = AuraTrack(
                            id = "yt_$vidId",
                            videoId = vidId,
                            title = ytItem.name ?: "Official Music Video",
                            artist = ytItem.uploaderName?.replace(" - Topic", "")?.trim() ?: cleanArtist,
                            album = "Official Music Video",
                            durationSeconds = ytItem.duration.toInt().coerceAtLeast(180),
                            artworkUrl = ytItem.thumbnails?.lastOrNull()?.url ?: "https://img.youtube.com/vi/$vidId/hqdefault.jpg"
                        )
                    }
                } catch (e: Exception) {
                    Log.w(TAG, "Direct YouTube MV search notice: ${e.message}")
                }
            }

            if (candidate != null) {
                val candidateStream = StreamUrlResolver.resolveVideoStream(candidate.videoId)
                if (candidateStream != null) {
                    return@withContext candidate
                }
            }

            null
        } catch (e: Exception) {
            Log.w(TAG, "findOfficialMusicVideo notice: ${e.message}")
            null
        }
    }

    fun isVideoThumbnailUrl(url: String): Boolean {
        val clean = url.trim()
        if (clean.isBlank()) return true
        return (clean.contains("ytimg.com") || clean.contains("youtube.com")) && clean.contains("/vi/")
    }

    suspend fun findOfficialAudioTrack(track: AuraTrack): AuraTrack? = withContext(Dispatchers.IO) {
        try {
            if (!isVideoThumbnailUrl(track.artworkUrl) &&
                (track.artworkUrl.contains("googleusercontent.com") ||
                    track.artworkUrl.contains("ggpht.com") ||
                    track.artworkUrl.contains("i.scdn.co"))
            ) {
                return@withContext track
            }
            if (track.videoId.isNotBlank()) {
                videoToOfficialAudioCache[track.videoId]?.let { return@withContext it }
            }

            val cleanArtist = primaryArtistName(track.artist).ifBlank { track.artist }
            val cleanTitle = cleanSongTitleForVideoSearch(track.title, track.artist).ifBlank { track.title }
            val query = "$cleanTitle $cleanArtist".trim()
            if (query.isBlank()) return@withContext null

            val results = searchYouTubeMusic(query, filter = "songs", prefetchTopResult = false)
            val squareCandidates = results.filter { candidate ->
                !isVideoThumbnailUrl(candidate.artworkUrl) &&
                    (candidate.artworkUrl.contains("googleusercontent.com") ||
                        candidate.artworkUrl.contains("ggpht.com"))
            }

            val best = squareCandidates.maxByOrNull { candidate ->
                var score = officialVideoMatchScore(candidate.title, cleanTitle) * 6f
                if (candidate.musicVideoType?.equals("MUSIC_VIDEO_TYPE_ATV", ignoreCase = true) == true) {
                    score += 10f
                }
                val wantedArtistWords = normalizedVideoMatchText(cleanArtist)
                    .split(Regex("\\s+")).filter(String::isNotBlank).toSet()
                val foundArtistWords = normalizedVideoWords(candidate.artist)
                if (wantedArtistWords.isNotEmpty() && foundArtistWords.isNotEmpty()) {
                    score += (wantedArtistWords.intersect(foundArtistWords).size.toFloat() / wantedArtistWords.size) * 5f
                }
                score
            } ?: results.firstOrNull { !isVideoThumbnailUrl(it.artworkUrl) }

            if (best != null && track.videoId.isNotBlank()) {
                videoToOfficialAudioCache[track.videoId] = best
            }
            best
        } catch (e: Exception) {
            Log.w(TAG, "findOfficialAudioTrack notice: ${e.message}")
            null
        }
    }

    suspend fun fetchLivePerformancesAndVideos(track: AuraTrack): List<AuraTrack> = withContext(Dispatchers.IO) {
        try {
            val cleanArtist = track.artist.split(",", "&", "feat.", "ft.", "/").firstOrNull()?.trim() ?: track.artist
            val cleanTitle = track.title.substringBefore("(").substringBefore("-").trim()
            val query = "$cleanArtist $cleanTitle live"
            val liveResults = searchYouTubeMusic(query, filter = "videos")

            val mvResults = if (liveResults.size < 4) {
                searchYouTubeMusic("$cleanArtist $cleanTitle music video", filter = "videos")
            } else {
                emptyList()
            }

            (liveResults + mvResults)
                .distinctBy { it.videoId }
                .filter { it.videoId != track.videoId }
                .take(12)
        } catch (e: Exception) {
            Log.w(TAG, "Error fetching live performances: ${e.message}")
            emptyList()
        }
    }

    // -------------------------------------------------------------------------
    // Search, Radio / Up-Next, Suggestions & Video Info
    // -------------------------------------------------------------------------

    suspend fun searchYouTubeMusic(
        query: String,
        filter: String = "songs",
        prefetchTopResult: Boolean = true
    ): List<AuraTrack> = withContext(Dispatchers.IO) {
        if (query.isBlank()) return@withContext emptyList()
        val normFilter = filter.lowercase().trim()
        val effectiveQuery = when (normFilter) {
            "videos" -> if (query.contains("video", ignoreCase = true)) query else "$query music video"
            "playlists" -> if (query.contains("playlist", ignoreCase = true)) query else "$query playlist"
            "albums" -> if (query.contains("album", ignoreCase = true)) query else "$query album"
            else -> query
        }
        try {
            val params = when (normFilter) {
                "songs" -> "EgWKAQIIAWoKEAkQBRAKEAMQBA=="
                "videos" -> "EgWKAQIQAWoKEAkQChAFEAMQBA=="
                "playlists" -> "EgWKAQIIAWoKEAkQBRAKEAMQBA=="
                "albums" -> "EgWKAQIYAWoKEAkQChAFEAMQBA=="
                "artists" -> "EgWKAQIgAWoKEAkQChAFEAMQBA=="
                else -> null
            }

            val tracks = mutableListOf<AuraTrack>()
            val defaultVideoType = if (normFilter == "videos") "MUSIC_VIDEO_TYPE_OMV" else null
            InnerTubeClient.postInnerTube("search") {
                put("query", effectiveQuery)
                if (params != null) put("params", params)
            }?.let { root ->
                parseYtmSearchTracks(root, tracks, defaultVideoType)
            }

            if (tracks.isEmpty() && normFilter != "all") {
                InnerTubeClient.postInnerTube("search") {
                    put("query", effectiveQuery)
                }?.let { root ->
                    parseYtmSearchTracks(root, tracks, defaultVideoType)
                }
            }

            if (tracks.isNotEmpty()) {
                if (prefetchTopResult) StreamUrlResolver.prefetchStream(tracks.first().videoId)
                return@withContext tracks.distinctBy { it.videoId }
            }
        } catch (e: Exception) {
            Log.w(TAG, "WEB_REMIX search fallback to NewPipe: ${e.message}")
        }

        try {
            StreamUrlResolver.initExtractors()
            val extractor = BraveServiceList.YouTube.getSearchExtractor(effectiveQuery)
            extractor.fetchPage()
            val items = extractor.initialPage?.items ?: emptyList()
            val results = items.filterIsInstance<StreamInfoItem>().take(20).mapNotNull { item ->
                val vidId = item.url?.substringAfter("v=")?.substringBefore("&") ?: return@mapNotNull null
                if (vidId.isBlank()) return@mapNotNull null
                AuraTrack(
                    id = "yt_$vidId",
                    videoId = vidId,
                    title = item.name ?: "Unknown Title",
                    artist = (item.uploaderName ?: "YouTube Artist")
                        .replace(" - Topic", "")
                        .trim(),
                    album = "YouTube Music",
                    durationSeconds = item.duration.toInt().coerceAtLeast(180),
                    artworkUrl = item.thumbnails?.lastOrNull()?.url
                        ?: "https://img.youtube.com/vi/$vidId/hqdefault.jpg"
                )
            }
            if (prefetchTopResult) results.firstOrNull()?.let { StreamUrlResolver.prefetchStream(it.videoId) }
            results.distinctBy { it.videoId }
        } catch (e: Exception) {
            Log.e(TAG, "Search failed: ${e.message}")
            emptyList()
        }
    }

    suspend fun fetchRadioQueue(videoId: String, audioOnly: Boolean = true): List<AuraTrack> = withContext(Dispatchers.IO) {
        if (videoId.isBlank()) return@withContext emptyList()
        try {
            val queue = mutableListOf<AuraTrack>()
            val root = InnerTubeClient.postInnerTube("next", includeReferer = false) {
                put("videoId", videoId)
                put("playlistId", "RDAMVM$videoId")
                put("isAudioOnly", audioOnly)
            }
            val tabs = root?.optJSONObject("contents")
                ?.optJSONObject("singleColumnMusicWatchNextResultsRenderer")
                ?.optJSONObject("tabbedRenderer")
                ?.optJSONObject("watchNextTabbedResultsRenderer")
                ?.optJSONArray("tabs")

            val playlistItems = tabs?.optJSONObject(0)
                ?.optJSONObject("tabRenderer")
                ?.optJSONObject("content")
                ?.optJSONObject("musicQueueRenderer")
                ?.optJSONObject("content")
                ?.optJSONObject("playlistPanelRenderer")
                ?.optJSONArray("contents")

            if (playlistItems != null) {
                for (i in 0 until playlistItems.length()) {
                    val rowObj = playlistItems.optJSONObject(i) ?: continue
                    val renderer = rowObj.optJSONObject("playlistPanelVideoRenderer")
                        ?: rowObj.optJSONObject("playlistPanelVideoWrapperRenderer")
                            ?.optJSONObject("primaryRenderer")
                            ?.optJSONObject("playlistPanelVideoRenderer")
                        ?: continue
                    val itemVidId = renderer.optString("videoId")
                    if (itemVidId.isBlank() || itemVidId == videoId) continue

                    val title = renderer.optJSONObject("title")
                        ?.optJSONArray("runs")?.optJSONObject(0)?.optString("text") ?: continue
                    val artist = renderer.optJSONObject("shortBylineText")
                        ?.optJSONArray("runs")?.optJSONObject(0)?.optString("text")
                        ?.replace(" - Topic", "") ?: "YouTube Artist"
                    val lengthText = renderer.optJSONObject("lengthText")
                        ?.optJSONArray("runs")?.optJSONObject(0)?.optString("text") ?: "3:30"
                    val thumbUrl = renderer.optJSONObject("thumbnail")?.optJSONArray("thumbnails").lastThumbnailUrl()
                        ?: "https://img.youtube.com/vi/$itemVidId/hqdefault.jpg"

                    queue.add(
                        AuraTrack(
                            id = "yt_$itemVidId",
                            videoId = itemVidId,
                            title = title,
                            artist = artist,
                            durationSeconds = parseDurationText(lengthText),
                            artworkUrl = thumbUrl
                        )
                    )
                }
            }
            queue.firstOrNull()?.let { StreamUrlResolver.prefetchStream(it.videoId) }
            queue
        } catch (e: Exception) {
            Log.w(TAG, "Radio queue fetch warning: ${e.message}")
            emptyList()
        }
    }

    suspend fun fetchVideoInfo(videoId: String): AuraVideoInfo? = withContext(Dispatchers.IO) {
        if (videoId.isBlank()) return@withContext null
        videoInfoCache[videoId]?.let { return@withContext it }
        runCatching {
            val root = InnerTubeClient.postInnerTube(
                endpoint = "next",
                clientType = InnerTubeClient.ClientType.WEB,
                gl = "US",
                includeReferer = false
            ) {
                put("videoId", videoId)
            } ?: return@runCatching null

            val results = root.optJSONObject("contents")
                ?.optJSONObject("twoColumnWatchNextResults")
                ?.optJSONObject("results")
                ?.optJSONObject("results")
                ?.optJSONArray("contents")
            var secondary: JSONObject? = null
            var primary: JSONObject? = null
            if (results != null) {
                for (i in 0 until results.length()) {
                    val item = results.optJSONObject(i) ?: continue
                    item.optJSONObject("videoSecondaryInfoRenderer")?.let { secondary = it }
                    item.optJSONObject("videoPrimaryInfoRenderer")?.let { primary = it }
                }
            }
            findRenderer(root, "videoSecondaryInfoRenderer")?.let { secondary = it }
            findRenderer(root, "videoPrimaryInfoRenderer")?.let { primary = it }
            if (secondary == null && primary == null) return@runCatching null

            val owner = secondary?.optJSONObject("owner")?.optJSONObject("videoOwnerRenderer")
            val thumbnailSet = owner?.optJSONObject("thumbnail")?.optJSONArray("thumbnails")
            val thumbnail = thumbnailSet?.let { thumbs ->
                (thumbs.length() - 1 downTo 0).firstNotNullOfOrNull { index ->
                    thumbs.optJSONObject(index)?.optString("url")?.takeIf(String::isNotBlank)
                }
            }?.replace(Regex("=s\\d+.*$"), "=s960")
            val author = owner?.optJSONObject("title")?.optJSONArray("runs")
                ?.optJSONObject(0)?.optString("text").orEmpty()
                .replace(" - Topic", "")
            val info = AuraVideoInfo(
                videoId = videoId,
                author = author,
                authorId = owner?.optJSONObject("navigationEndpoint")
                    ?.optJSONObject("browseEndpoint")?.optString("browseId"),
                authorThumbnail = thumbnail,
                description = secondary?.optJSONObject("attributedDescription")
                    ?.optString("content").orEmpty(),
                subscribers = owner?.optJSONObject("subscriberCountText")
                    ?.optString("simpleText").orEmpty(),
                uploadDate = primary?.optJSONObject("dateText")?.optString("simpleText").orEmpty()
            )
            if (info.description.isNotBlank() || info.author.isNotBlank() || info.uploadDate.isNotBlank()) {
                videoInfoCache[videoId] = info
                info
            } else {
                null
            }
        }.getOrNull()
    }

    suspend fun fetchSearchSuggestions(query: String): List<String> = withContext(Dispatchers.IO) {
        if (query.isBlank()) return@withContext emptyList()
        val suggestions = mutableListOf<String>()
        try {
            val root = InnerTubeClient.postInnerTube("music/get_search_suggestions", gl = "US") {
                put("input", query.trim())
            }
            val contents = root?.optJSONArray("contents") ?: return@withContext emptyList()
            for (i in 0 until contents.length()) {
                val section = contents.optJSONObject(i)
                    ?.optJSONObject("searchSuggestionsSectionRenderer") ?: continue
                val items = section.optJSONArray("contents") ?: continue
                for (j in 0 until items.length()) {
                    val renderer = items.optJSONObject(j)
                        ?.optJSONObject("searchSuggestionRenderer") ?: continue
                    val text = renderer.optJSONObject("suggestion")?.optJSONArray("runs").joinRunsText()
                    if (text.isNotBlank() && suggestions.none { it.equals(text, ignoreCase = true) }) {
                        suggestions.add(text)
                    }
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "fetchSearchSuggestions failed: ${e.message}")
        }
        suggestions.take(5)
    }

    // -------------------------------------------------------------------------
    // Home, Moods, Genres & Charts
    // -------------------------------------------------------------------------

    suspend fun fetchHomeChartsAndMoods(): Pair<List<YtmChartSection>, List<MoodCategory>> =
        withContext(Dispatchers.IO) {
            val moods = listOf(
                MoodCategory("Late Night Vibes", "late night R&B chill hits official audio", 0xFF8B5CF6),
                MoodCategory("Deep Focus", "ambient electronic focus study beats", 0xFF06B6D4),
                MoodCategory("Workout Rush", "high energy phonk workout gym hits", 0xFFEC4899),
                MoodCategory("Golden Hour", "indie pop sunset chill tracks", 0xFFF59E0B),
                MoodCategory("K-Pop Radar", "top kpop hits 2025 official audio", 0xFF10B981),
                MoodCategory("J-Pop & Anime", "yoasobi ado kenshi yonezu official audio", 0xFF6366F1),
                MoodCategory("90s & 00s Throwback", "2000s r&b pop classics official audio", 0xFFEF4444),
                MoodCategory("Acoustic Unplugged", "acoustic stripped cover sessions", 0xFF14B8A6)
            )

            val trendingDeferred = async { searchYouTubeMusic("Global Top 50 Pop Hits 2025", "songs") }
            val rnbDeferred = async { searchYouTubeMusic("The Weeknd SZA Drake R&B Hits", "songs") }
            val electronicDeferred = async { searchYouTubeMusic("Fred again Daft Punk Kaytranada", "songs") }

            val trending = trendingDeferred.await().ifEmpty { DEFAULT_SEED_TRACKS }
            val rnb = rnbDeferred.await()
            val electronic = electronicDeferred.await()

            val sections = buildList {
                add(
                    YtmChartSection(
                        id = "global_pulse",
                        title = "Global Pulse Charts",
                        subtitle = "Real-time YouTube Music WEB_REMIX streams",
                        tracks = trending.take(10)
                    )
                )
                if (rnb.isNotEmpty()) {
                    add(
                        YtmChartSection(
                            id = "midnight_velvet",
                            title = "Midnight R&B & Soul",
                            subtitle = "Curated for Warm Obsidian acoustics",
                            tracks = rnb.take(10)
                        )
                    )
                }
                if (electronic.isNotEmpty()) {
                    add(
                        YtmChartSection(
                            id = "club_acoustics",
                            title = "Electronic & Future Garage",
                            subtitle = "High-dynamic range spatial selections",
                            tracks = electronic.take(10)
                        )
                    )
                }
            }

            Pair(sections, moods)
        }

    suspend fun fetchYtmMoodBrowse(category: String): MoodBrowseResult = withContext(Dispatchers.IO) {
        val params = YtmMoodBrowseParams.PARAMS_MAP[category]
        val fallbackBg = YtmMoodBrowseParams.DEFAULT_BACKGROUNDS[category]
        if (params == null) {
            return@withContext MoodBrowseResult(fallbackBg, emptyList())
        }

        try {
            val root = InnerTubeClient.postInnerTube("browse") {
                put("browseId", "FEmusic_home")
                put("params", params)
            }
            if (root != null) {
                val bgThumbs = root.optJSONObject("background")
                    ?.optJSONObject("musicThumbnailRenderer")
                    ?.optJSONObject("thumbnail")
                    ?.optJSONArray("thumbnails")
                val bgUrl = fallbackBg ?: bgThumbs.lastThumbnailUrl()

                val parsedTracks = mutableListOf<AuraTrack>()
                val parsedAlbums = mutableListOf<AuraTrack>()
                val parsedShelves = mutableListOf<YtmMoodShelf>()

                val sections = extractSingleColSections(root)
                if (sections != null) {
                    for (s in 0 until sections.length()) {
                        val sec = sections.optJSONObject(s) ?: continue
                        val shelf = sec.optJSONObject("musicCarouselShelfRenderer")
                            ?: sec.optJSONObject("gridRenderer")
                            ?: continue
                        val parsedShelf = parseCarouselOrGridShelf(shelf, category, parsedTracks, parsedAlbums)
                        if (parsedShelf != null && parsedShelf.items.isNotEmpty()) {
                            parsedShelves.add(parsedShelf)
                        }
                    }
                }

                val finalTracks = if (parsedTracks.isNotEmpty()) {
                    parsedTracks.distinctBy { it.videoId }
                } else {
                    searchYouTubeMusic("$category songs official audio", "songs").take(30)
                }

                if (parsedShelves.isEmpty() && finalTracks.isNotEmpty()) {
                    parsedShelves.add(
                        YtmMoodShelf(
                            title = "$category Picks",
                            subtitle = "Popular in $category",
                            is4RowGrid = true,
                            items = finalTracks.take(16).map {
                                YtmMoodItem(
                                    id = it.id,
                                    title = it.title,
                                    subtitle = it.artist,
                                    artworkUrl = it.highResArtworkUrl,
                                    type = YtmMoodItemType.TRACK,
                                    track = it
                                )
                            }
                        )
                    )
                }

                return@withContext MoodBrowseResult(
                    backgroundUrl = bgUrl,
                    tracks = finalTracks,
                    albums = parsedAlbums.distinctBy { it.id },
                    shelves = parsedShelves
                )
            }
        } catch (e: Exception) {
            Log.e(TAG, "fetchYtmMoodBrowse failed for $category: ${e.message}")
        }

        MoodBrowseResult(
            backgroundUrl = fallbackBg,
            tracks = searchYouTubeMusic("$category songs official audio", "songs").take(30)
        )
    }

    private fun parseCarouselOrGridShelf(
        shelf: JSONObject,
        defaultCategory: String,
        outTracks: MutableList<AuraTrack>? = null,
        outAlbums: MutableList<AuraTrack>? = null
    ): YtmMoodShelf? {
        val header = shelf.optJSONObject("header")?.optJSONObject("musicCarouselShelfBasicHeaderRenderer")
            ?: shelf.optJSONObject("header")?.optJSONObject("gridHeaderRenderer")
        val shelfTitle = header?.optJSONObject("title")?.optJSONArray("runs")?.optJSONObject(0)?.optString("text")?.trim().orEmpty()
        val shelfSubtitle = header?.optJSONObject("strapline")?.optJSONArray("runs")?.optJSONObject(0)?.optString("text")?.trim()
            ?: header?.optJSONObject("subtitle")?.optJSONArray("runs")?.optJSONObject(0)?.optString("text")?.trim()
        val numItemsPerColumn = shelf.optString("numItemsPerColumn").ifBlank {
            header?.optString("numItemsPerColumn").orEmpty()
        }
        val is4RowGrid = numItemsPerColumn == "4"
        val contents = shelf.optJSONArray("contents") ?: shelf.optJSONArray("items") ?: return null
        val shelfItems = mutableListOf<YtmMoodItem>()

        for (i in 0 until contents.length()) {
            val item = contents.optJSONObject(i) ?: continue
            val resItem = item.optJSONObject("musicResponsiveListItemRenderer")
            if (resItem != null) {
                val flexCols = resItem.optJSONArray("flexColumns") ?: continue
                val firstRun = flexCols.optJSONObject(0)
                    ?.optJSONObject("musicResponsiveListItemFlexColumnRenderer")
                    ?.optJSONObject("text")
                    ?.optJSONArray("runs")
                    ?.optJSONObject(0)
                val songTitle = firstRun?.optString("text") ?: continue
                val vidId = resItem.optJSONObject("playlistItemData")?.optString("videoId")
                    ?.takeIf { it.isNotBlank() }
                    ?: resItem.optJSONObject("overlay")
                        ?.optJSONObject("musicItemThumbnailOverlayRenderer")
                        ?.optJSONObject("content")
                        ?.optJSONObject("musicPlayButtonRenderer")
                        ?.optJSONObject("playNavigationEndpoint")
                        ?.optJSONObject("watchEndpoint")
                        ?.optString("videoId")
                        ?.takeIf { it.isNotBlank() }
                    ?: continue

                val subRuns = flexCols.optJSONObject(1)
                    ?.optJSONObject("musicResponsiveListItemFlexColumnRenderer")
                    ?.optJSONObject("text")
                    ?.optJSONArray("runs")
                var songArtist = "YouTube Artist"
                var songAlbum = "YouTube Music"
                if (subRuns != null) {
                    val texts = mutableListOf<String>()
                    for (r in 0 until subRuns.length()) {
                        val t = subRuns.optJSONObject(r)?.optString("text")?.trim().orEmpty()
                        if (t.isNotEmpty() && t != "•" && t != "&") texts.add(t)
                    }
                    if (texts.isNotEmpty()) {
                        songArtist = if (texts[0].equals("Song", ignoreCase = true) || texts[0].equals("Video", ignoreCase = true)) {
                            texts.getOrNull(1) ?: "YouTube Artist"
                        } else texts[0]
                        if (texts.size > 2) {
                            songAlbum = texts[1]
                        }
                    }
                }
                val thumb = resItem.extractMusicThumbnailUrl() ?: "https://img.youtube.com/vi/$vidId/hqdefault.jpg"

                val track = AuraTrack(
                    id = "yt_$vidId",
                    videoId = vidId,
                    title = songTitle,
                    artist = songArtist,
                    album = songAlbum,
                    artworkUrl = thumb,
                    genre = defaultCategory
                )
                outTracks?.add(track)
                shelfItems.add(
                    YtmMoodItem(
                        id = "track_$vidId",
                        title = songTitle,
                        subtitle = songArtist,
                        artworkUrl = thumb,
                        type = YtmMoodItemType.TRACK,
                        track = track
                    )
                )
            }
            val twoRow = item.optJSONObject("musicTwoRowItemRenderer")
            if (twoRow != null) {
                val cardTitle = twoRow.optJSONObject("title")
                    ?.optJSONArray("runs")?.optJSONObject(0)?.optString("text") ?: continue
                val subText = twoRow.optJSONObject("subtitle")?.optJSONArray("runs")
                    .joinRunsText().ifBlank { "YouTube Music" }
                val thumb = twoRow.extractMusicThumbnailUrl() ?: ""
                val navWatch = twoRow.optJSONObject("navigationEndpoint")?.optJSONObject("watchEndpoint")
                val navBrowse = twoRow.optJSONObject("navigationEndpoint")?.optJSONObject("browseEndpoint")
                val vidId = navWatch?.optString("videoId")?.takeIf { it.isNotBlank() }
                val browseId = navBrowse?.optString("browseId")?.takeIf { it.isNotBlank() }
                    ?: navWatch?.optString("playlistId")?.takeIf { it.isNotBlank() }

                if (vidId != null) {
                    val isVideo = shelfTitle.contains("video", ignoreCase = true) ||
                        subText.contains("video", ignoreCase = true) ||
                        subText.contains("views", ignoreCase = true)
                    val track = AuraTrack(
                        id = "yt_$vidId",
                        videoId = vidId,
                        title = cardTitle,
                        artist = subText,
                        album = defaultCategory,
                        artworkUrl = thumb,
                        genre = defaultCategory,
                        musicVideoType = if (isVideo) "MUSIC_VIDEO_TYPE_OMV" else null
                    )
                    outTracks?.add(track)
                    shelfItems.add(
                        YtmMoodItem(
                            id = "yt_$vidId",
                            title = cardTitle,
                            subtitle = subText,
                            artworkUrl = thumb,
                            type = if (isVideo) YtmMoodItemType.VIDEO else YtmMoodItemType.TRACK,
                            track = track
                        )
                    )
                } else if (browseId != null) {
                    if (browseId.startsWith("VL") || browseId.startsWith("RDCLAK") || browseId.startsWith("PL")) {
                        val playlist = YtmCommunityPlaylist(
                            id = browseId.removePrefix("VL"),
                            title = cardTitle,
                            author = subText,
                            artworkUrl = thumb
                        )
                        shelfItems.add(
                            YtmMoodItem(
                                id = "pl_$browseId",
                                title = cardTitle,
                                subtitle = subText,
                                artworkUrl = thumb,
                                type = YtmMoodItemType.PLAYLIST,
                                playlist = playlist
                            )
                        )
                    } else if (browseId.startsWith("MPRE") || browseId.startsWith("FEmusic_library_album")) {
                        val albumTrack = AuraTrack(
                            id = "yt_album_$browseId",
                            videoId = browseId,
                            title = cardTitle,
                            artist = subText,
                            album = cardTitle,
                            artworkUrl = thumb,
                            genre = defaultCategory
                        )
                        outAlbums?.add(albumTrack)
                        shelfItems.add(
                            YtmMoodItem(
                                id = "album_$browseId",
                                title = cardTitle,
                                subtitle = subText,
                                artworkUrl = thumb,
                                type = YtmMoodItemType.ALBUM,
                                track = albumTrack
                            )
                        )
                    } else {
                        val idPrefix = if (browseId.startsWith("MPSP")) "pod_" else "misc_"
                        val playlist = YtmCommunityPlaylist(
                            id = browseId,
                            title = cardTitle,
                            author = subText,
                            artworkUrl = thumb
                        )
                        shelfItems.add(
                            YtmMoodItem(
                                id = "$idPrefix$browseId",
                                title = cardTitle,
                                subtitle = subText,
                                artworkUrl = thumb,
                                type = YtmMoodItemType.PLAYLIST,
                                playlist = playlist
                            )
                        )
                    }
                }
            }
        }
        if (shelfItems.isEmpty()) return null
        return YtmMoodShelf(
            title = shelfTitle.ifBlank { if (is4RowGrid) "Quick picks" else "$defaultCategory Highlights" },
            subtitle = shelfSubtitle,
            is4RowGrid = is4RowGrid,
            items = shelfItems
        )
    }

    suspend fun fetchMoodCategoryDetails(
        browseId: String = "FEmusic_moods_and_genres_category",
        params: String? = null,
        title: String
    ): List<YtmMoodShelf> = withContext(Dispatchers.IO) {
        try {
            val root = InnerTubeClient.postInnerTube("browse") {
                put("browseId", browseId)
                if (!params.isNullOrBlank()) put("params", params)
            }
            if (root != null) {
                val parsedShelves = mutableListOf<YtmMoodShelf>()
                val sections = extractSingleColSections(root)
                if (sections != null) {
                    for (s in 0 until sections.length()) {
                        val sec = sections.optJSONObject(s) ?: continue
                        val shelfObj = sec.optJSONObject("musicCarouselShelfRenderer")
                            ?: sec.optJSONObject("gridRenderer")
                            ?: continue
                        val parsedShelf = parseCarouselOrGridShelf(shelfObj, title)
                        if (parsedShelf != null && parsedShelf.items.isNotEmpty()) {
                            parsedShelves.add(parsedShelf)
                        }
                    }
                }
                if (parsedShelves.isNotEmpty()) {
                    return@withContext parsedShelves
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "fetchMoodCategoryDetails failed for $title: ${e.message}")
        }

        try {
            val fallbackTracks = searchYouTubeMusic("$title songs official audio", "songs").take(30)
            if (fallbackTracks.isNotEmpty()) {
                val shelfItems = fallbackTracks.map { track ->
                    YtmMoodItem(
                        id = "track_${track.videoId}",
                        title = track.title,
                        subtitle = track.artist,
                        artworkUrl = track.artworkUrl,
                        type = YtmMoodItemType.TRACK,
                        track = track
                    )
                }
                listOf(
                    YtmMoodShelf(
                        title = "$title Highlights",
                        subtitle = "Recommended for you",
                        is4RowGrid = true,
                        items = shelfItems
                    )
                )
            } else {
                emptyList()
            }
        } catch (_: Exception) {
            emptyList()
        }
    }

    suspend fun fetchMoodAndGenresCategories(): List<YtmMoodCategorySection> = withContext(Dispatchers.IO) {
        try {
            val root = InnerTubeClient.postInnerTube("browse") {
                put("browseId", "FEmusic_moods_and_genres")
            } ?: return@withContext emptyList()

            val sections = extractSingleColSections(root) ?: return@withContext emptyList()
            val resultSections = mutableListOf<YtmMoodCategorySection>()
            for (s in 0 until sections.length()) {
                val sec = sections.optJSONObject(s) ?: continue
                val grid = sec.optJSONObject("gridRenderer") ?: continue
                val secTitle = grid.optJSONObject("header")
                    ?.optJSONObject("gridHeaderRenderer")
                    ?.optJSONObject("title")
                    ?.optJSONArray("runs")
                    ?.optJSONObject(0)
                    ?.optString("text")
                    ?.trim().orEmpty()

                val itemsArray = grid.optJSONArray("items") ?: continue
                val categoryItems = mutableListOf<YtmMoodCategoryItem>()
                for (i in 0 until itemsArray.length()) {
                    val btn = itemsArray.optJSONObject(i)?.optJSONObject("musicNavigationButtonRenderer") ?: continue
                    val btnText = btn.optJSONObject("buttonText")?.optJSONArray("runs")?.optJSONObject(0)?.optString("text")?.trim().orEmpty()
                    val color = btn.optJSONObject("solid")?.optLong("leftStripeColor") ?: 0xFF1DB954
                    val endpoint = btn.optJSONObject("clickCommand")?.optJSONObject("browseEndpoint")
                    val browseId = endpoint?.optString("browseId") ?: "FEmusic_moods_and_genres_category"
                    val params = endpoint?.optString("params").orEmpty()
                    if (btnText.isNotBlank()) {
                        categoryItems.add(
                            YtmMoodCategoryItem(
                                title = btnText,
                                params = params,
                                stripeColor = color,
                                browseId = browseId
                            )
                        )
                    }
                }
                if (categoryItems.isNotEmpty()) {
                    resultSections.add(YtmMoodCategorySection(title = secTitle, items = categoryItems))
                }
            }
            if (resultSections.isNotEmpty()) {
                return@withContext resultSections
            }
        } catch (e: Exception) {
            Log.e(TAG, "fetchMoodAndGenresCategories failed: ${e.message}")
        }
        emptyList()
    }

    suspend fun fetchHomeChartData(countryCode: String = "US"): AuraChart = withContext(Dispatchers.IO) {
        val playlistSections = mutableListOf<AuraChartPlaylistSection>()
        var topArtistsList = mutableListOf<AuraChartArtistItem>()
        var podcastSection: AuraChartPodcastSection? = null

        try {
            val effectiveGl = if (countryCode.equals("ZZ", ignoreCase = true)) "US" else countryCode
            val root = InnerTubeClient.postInnerTube("browse", gl = effectiveGl) {
                put("browseId", "FEmusic_charts")
                put("formData", JSONObject().apply {
                    put("selectedValues", JSONArray().apply { put(countryCode) })
                })
            }
            val sectionContents = root?.let { extractSingleColSections(it) }

            if (sectionContents != null) {
                for (i in 0 until sectionContents.length()) {
                    val shelf = sectionContents.optJSONObject(i)?.optJSONObject("musicCarouselShelfRenderer") ?: continue
                    val contents = shelf.optJSONArray("contents") ?: continue
                    if (contents.length() == 0) continue

                    val shelfTitle = shelf.optJSONObject("header")
                        ?.optJSONObject("musicCarouselShelfBasicHeaderRenderer")
                        ?.optJSONObject("title")
                        ?.optJSONArray("runs")
                        ?.optJSONObject(0)
                        ?.optString("text") ?: ""

                    val firstItem = contents.optJSONObject(0)
                    val pageType = firstItem?.optJSONObject("musicResponsiveListItemRenderer")
                        ?.optJSONObject("navigationEndpoint")
                        ?.optJSONObject("browseEndpoint")
                        ?.optJSONObject("browseEndpointContextSupportedConfigs")
                        ?.optJSONObject("browseEndpointContextMusicConfig")
                        ?.optString("pageType") ?: ""

                    if (pageType == "MUSIC_PAGE_TYPE_ARTIST" || shelfTitle.contains("Top artists", ignoreCase = true)) {
                        val artists = parseChartRankItems(contents, fallbackPrefix = "artist_")
                        if (artists.isNotEmpty()) topArtistsList = artists
                    } else if (pageType == "MUSIC_PAGE_TYPE_PODCAST_SHOW_DETAIL_PAGE" || shelfTitle.contains("podcast", ignoreCase = true)) {
                        val shows = parseChartRankItems(contents, fallbackPrefix = "podcast_")
                        if (shows.isNotEmpty()) {
                            podcastSection = AuraChartPodcastSection(
                                title = shelfTitle.ifBlank { "Weekly top podcast shows" },
                                shows = shows
                            )
                        }
                    } else {
                        val playlists = mutableListOf<YtmCommunityPlaylist>()
                        for (j in 0 until contents.length()) {
                            val item = contents.optJSONObject(j)?.optJSONObject("musicTwoRowItemRenderer") ?: continue
                            val plId = item.optJSONObject("navigationEndpoint")
                                ?.optJSONObject("browseEndpoint")
                                ?.optString("browseId") ?: continue
                            val plTitle = item.optJSONObject("title")
                                ?.optJSONArray("runs")
                                ?.optJSONObject(0)
                                ?.optString("text") ?: continue
                            val author = item.optJSONObject("subtitle")?.optJSONArray("runs").joinRunsText(" ")
                            val thumb = item.extractMusicThumbnailUrl() ?: ""
                            playlists.add(YtmCommunityPlaylist(id = plId, title = plTitle, author = author, trackCount = "", artworkUrl = thumb))
                        }
                        if (playlists.isNotEmpty()) {
                            playlistSections.add(AuraChartPlaylistSection(title = shelfTitle, items = playlists))
                        }
                    }
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "Error fetching live Chart Data for $countryCode: ${e.message}")
        }

        AuraChart(
            countryCode = countryCode,
            playlists = playlistSections,
            topArtists = topArtistsList,
            podcasts = podcastSection
        )
    }

    private fun parseChartRankItems(contents: JSONArray, fallbackPrefix: String): MutableList<AuraChartArtistItem> {
        val result = mutableListOf<AuraChartArtistItem>()
        for (j in 0 until contents.length()) {
            val item = contents.optJSONObject(j)?.optJSONObject("musicResponsiveListItemRenderer") ?: continue
            val runs0 = item.optJSONArray("flexColumns")?.optJSONObject(0)
                ?.optJSONObject("musicResponsiveListItemFlexColumnRenderer")
                ?.optJSONObject("text")?.optJSONArray("runs")
            val name = runs0?.optJSONObject(0)?.optString("text") ?: continue

            val runs1 = item.optJSONArray("flexColumns")?.optJSONObject(1)
                ?.optJSONObject("musicResponsiveListItemFlexColumnRenderer")
                ?.optJSONObject("text")?.optJSONArray("runs")
            val subs = runs1?.optJSONObject(0)?.optString("text") ?: ""
            val thumb = item.extractMusicThumbnailUrl() ?: ""
            val itemId = item.optJSONObject("navigationEndpoint")
                ?.optJSONObject("browseEndpoint")
                ?.optString("browseId")
                ?: runs0.optJSONObject(0)?.optJSONObject("navigationEndpoint")
                    ?.optJSONObject("browseEndpoint")
                    ?.optString("browseId")
                ?: "$fallbackPrefix$j"

            if (name.isNotBlank()) {
                result.add(AuraChartArtistItem(id = itemId, rank = "${j + 1}", name = name, subscribers = subs, avatarUrl = thumb))
            }
        }
        return result
    }

    // -------------------------------------------------------------------------
    // Albums, Playlists & Podcasts
    // -------------------------------------------------------------------------

    suspend fun findAlbumBrowseId(albumName: String, artistName: String): String? = withContext(Dispatchers.IO) {
        if (albumName.isBlank()) return@withContext null
        try {
            val query = "$albumName $artistName".trim()
            val root = InnerTubeClient.postInnerTube("search", gl = "US") {
                put("query", query)
                put("params", "EgWKAQIYAWoKEAkQChAFEAMQBA==")
            }
            val tabs = root?.optJSONObject("contents")
                ?.optJSONObject("tabbedSearchResultsRenderer")
                ?.optJSONArray("tabs")
            val secContents = tabs?.optJSONObject(0)
                ?.optJSONObject("tabRenderer")?.optJSONObject("content")
                ?.optJSONObject("sectionListRenderer")?.optJSONArray("contents")
            val shelfContents = secContents?.optJSONObject(0)
                ?.optJSONObject("musicShelfRenderer")?.optJSONArray("contents")
            val firstItem = shelfContents?.optJSONObject(0)
                ?.optJSONObject("musicResponsiveListItemRenderer")
            val browseId = firstItem?.optJSONObject("navigationEndpoint")
                ?.optJSONObject("browseEndpoint")?.optString("browseId")
            if (!browseId.isNullOrBlank()) {
                return@withContext browseId
            }
        } catch (e: Exception) {
            Log.w(TAG, "Error searching album browseId for $albumName: ${e.message}")
        }
        null
    }

    suspend fun fetchTopArtists(): List<YtmTopArtist> = withContext(Dispatchers.IO) {
        val liveChart = fetchHomeChartData(InnerTubeClient.contentCountry)
        if (liveChart.topArtists.isNotEmpty()) {
            return@withContext liveChart.topArtists.map {
                YtmTopArtist(id = it.id, name = it.name, subscribers = it.subscribers, avatarUrl = it.avatarUrl)
            }
        }

        listOf(
            YtmTopArtist("taylor_swift", "Taylor Swift", "63.5M subscribers", "https://yt3.googleusercontent.com/RCpTA6EXJQyjVFDosWOKa2SMmqkua_lA9mHPDWWciLwgqpZLz9fS3xYq9R0_L9kF=s400"),
            YtmTopArtist("bruno_mars", "Bruno Mars", "44.6M subscribers", "https://lh3.googleusercontent.com/hnefGBrazRhn4Z92bdSZBUENl40ONjRiVDsmZKZh-WZ2iCKE-W_YyJtZ_fL4w=s400"),
            YtmTopArtist("the_weeknd", "The Weeknd", "36.2M subscribers", "https://lh3.googleusercontent.com/f0uIq9s7qQYV4f_4zB1=s400"),
            YtmTopArtist("billie_eilish", "Billie Eilish", "52.4M subscribers", "https://lh3.googleusercontent.com/zV8Q8k=s400"),
            YtmTopArtist("drake", "Drake", "30.1M subscribers", "https://lh3.googleusercontent.com/dK0M_Fh5uQ6s4z7=s400"),
            YtmTopArtist("sza", "SZA", "8.9M subscribers", "https://lh3.googleusercontent.com/k9_a1=s400"),
            YtmTopArtist("sabrina_carpenter", "Sabrina Carpenter", "10.5M subscribers", "https://lh3.googleusercontent.com/sc_b2=s400")
        )
    }

    suspend fun fetchCommunityPlaylists(query: String = "Top Hits 2025"): List<YtmCommunityPlaylist> = withContext(Dispatchers.IO) {
        try {
            val root = InnerTubeClient.postInnerTube("search") {
                put("query", query)
                put("params", "EgeKAQQoAEABahIQBBAKEAUQDhADEAkQFRAQEBE=")
            }
            val playlists = mutableListOf<YtmCommunityPlaylist>()
            val tabs = root?.optJSONObject("contents")
                ?.optJSONObject("tabbedSearchResultsRenderer")
                ?.optJSONArray("tabs")
            val sectionContents = tabs?.optJSONObject(0)
                ?.optJSONObject("tabRenderer")
                ?.optJSONObject("content")
                ?.optJSONObject("sectionListRenderer")
                ?.optJSONArray("contents")

            val shelf = sectionContents?.optJSONObject(0)?.optJSONObject("musicShelfRenderer")
            val items = shelf?.optJSONArray("contents")
            if (items != null) {
                for (i in 0 until items.length()) {
                    val item = items.optJSONObject(i)?.optJSONObject("musicResponsiveListItemRenderer") ?: continue
                    val flexCols = item.optJSONArray("flexColumns") ?: continue
                    val title = flexCols.optJSONObject(0)
                        ?.optJSONObject("musicResponsiveListItemFlexColumnRenderer")
                        ?.optJSONObject("text")
                        ?.optJSONArray("runs")
                        ?.optJSONObject(0)
                        ?.optString("text") ?: ""

                    val flexCol1Runs = flexCols.optJSONObject(1)
                        ?.optJSONObject("musicResponsiveListItemFlexColumnRenderer")
                        ?.optJSONObject("text")
                        ?.optJSONArray("runs")

                    var author = "Community Playlist"
                    var count = "Playlist"
                    if (flexCol1Runs != null) {
                        val textList = mutableListOf<String>()
                        for (r in 0 until flexCol1Runs.length()) {
                            val t = flexCol1Runs.optJSONObject(r)?.optString("text")?.trim() ?: ""
                            if (t.isNotEmpty() && t != "•") textList.add(t)
                        }
                        if (textList.isNotEmpty()) {
                            if (textList[0].equals("Playlist", ignoreCase = true)) {
                                author = textList.getOrNull(1) ?: "Community Playlist"
                                count = textList.getOrNull(2) ?: "Playlist"
                            } else {
                                author = textList[0]
                                count = textList.getOrNull(1) ?: "Playlist"
                            }
                        }
                    }

                    val thumb = item.extractMusicThumbnailUrl() ?: ""
                    val id = item.optJSONObject("navigationEndpoint")
                        ?.optJSONObject("browseEndpoint")
                        ?.optString("browseId")
                        ?: flexCols.optJSONObject(0)
                            ?.optJSONObject("musicResponsiveListItemFlexColumnRenderer")
                            ?.optJSONObject("text")?.optJSONArray("runs")?.optJSONObject(0)
                            ?.optJSONObject("navigationEndpoint")?.optJSONObject("browseEndpoint")?.optString("browseId")
                        ?: "playlist_$i"

                    if (title.isNotBlank()) {
                        playlists.add(
                            YtmCommunityPlaylist(
                                id = id,
                                title = title,
                                author = author,
                                trackCount = count,
                                artworkUrl = thumb
                            )
                        )
                    }
                }
            }

            if (playlists.isNotEmpty()) return@withContext playlists
        } catch (e: Exception) {
            Log.w(TAG, "Error fetching live community playlists: ${e.message}")
        }

        listOf(
            YtmCommunityPlaylist("VLPL4fGSI1pDJn5kI81J1fYWK5eZRl1zJ5kM", "Global Pop Trends 2025", "Billboard Charts", "50 tracks", "https://img.youtube.com/vi/fcnDmrtj6Sk/hqdefault.jpg"),
            YtmCommunityPlaylist("RDCLAK5uy_kmPRjFGN7YstCHdnuf39O75UPhGmbutFc", "Today's Top 50 Hits", "Global Top 50", "50 tracks", "https://img.youtube.com/vi/vRjaGgDsWSo/hqdefault.jpg"),
            YtmCommunityPlaylist("RDCLAK5uy_kPX6d-r5p_3M_W7Qv_U1tE-0_k1u3q-8", "Late Night Vibes & Chill", "Aura Acoustics", "65 tracks", "https://img.youtube.com/vi/vSA7yMrTETg/hqdefault.jpg")
        )
    }

    suspend fun fetchPlaylistTracks(browseId: String): List<AuraTrack> = withContext(Dispatchers.IO) {
        try {
            val cleanId = stripBrowseIdPrefixes(browseId)
            val actualBrowseId = normalizeYtmBrowseId(cleanId)
            val root = InnerTubeClient.postInnerTube("browse", gl = "US") {
                put("browseId", actualBrowseId)
            }
            val tracks = mutableListOf<AuraTrack>()
            val contents = root?.optJSONObject("contents")
                ?.optJSONObject("twoColumnBrowseResultsRenderer")
                ?.optJSONObject("secondaryContents")
                ?.optJSONObject("sectionListRenderer")
                ?.optJSONArray("contents")

            val playlistShelf = contents?.optJSONObject(0)?.optJSONObject("musicPlaylistShelfRenderer")
            val items = playlistShelf?.optJSONArray("contents")
            if (items != null) {
                for (i in 0 until items.length()) {
                    val item = items.optJSONObject(i)?.optJSONObject("musicResponsiveListItemRenderer") ?: continue
                    val title = item.optJSONArray("flexColumns")
                        ?.optJSONObject(0)
                        ?.optJSONObject("musicResponsiveListItemFlexColumnRenderer")
                        ?.optJSONObject("text")
                        ?.optJSONArray("runs")
                        ?.optJSONObject(0)
                        ?.optString("text") ?: continue

                    val artist = item.optJSONArray("flexColumns")
                        ?.optJSONObject(1)
                        ?.optJSONObject("musicResponsiveListItemFlexColumnRenderer")
                        ?.optJSONObject("text")
                        ?.optJSONArray("runs")
                        ?.optJSONObject(0)
                        ?.optString("text") ?: "YouTube Music Artist"

                    val vidId = item.optJSONObject("playlistItemData")?.optString("videoId")
                        ?: item.optJSONObject("navigationEndpoint")
                            ?.optJSONObject("watchEndpoint")
                            ?.optString("videoId") ?: continue

                    val thumb = item.extractMusicThumbnailUrl() ?: "https://img.youtube.com/vi/$vidId/hqdefault.jpg"
                    tracks.add(
                        AuraTrack(
                            id = "yt_$vidId",
                            videoId = vidId,
                            title = title,
                            artist = artist,
                            album = "YouTube Music Playlist",
                            durationSeconds = 210,
                            artworkUrl = thumb
                        )
                    )
                }
            }
            if (tracks.isNotEmpty()) return@withContext tracks
        } catch (e: Exception) {
            Log.w(TAG, "Error fetching playlist tracks: ${e.message}")
        }
        emptyList()
    }

    suspend fun fetchPlaylistDetail(playlist: YtmCommunityPlaylist): PlaylistDetail = withContext(Dispatchers.IO) {
        val rawId = stripBrowseIdPrefixes(playlist.id)
        val isPodcastShow = rawId.startsWith("MPSP") || rawId.startsWith("MP_") || rawId.contains("podcast", ignoreCase = true)
        try {
            val actualBrowseId = normalizeYtmBrowseId(rawId, isPodcastShow = isPodcastShow)
            val root = InnerTubeClient.postInnerTube("browse", gl = "US") {
                put("browseId", actualBrowseId)
            }
            if (root != null) {
                val contentsObj = root.optJSONObject("contents")
                val twoCol = contentsObj?.optJSONObject("twoColumnBrowseResultsRenderer")
                val singleCol = contentsObj?.optJSONObject("singleColumnBrowseResultsRenderer")

                var bannerUrl = playlist.artworkUrl
                var title = playlist.title
                var authorAndMeta = playlist.author
                var totalViews = ""
                var description = ""
                var authorAvatarUrl = ""

                val tab0Contents = (twoCol ?: singleCol)?.optJSONArray("tabs")?.optJSONObject(0)
                    ?.optJSONObject("tabRenderer")?.optJSONObject("content")
                    ?.optJSONObject("sectionListRenderer")?.optJSONArray("contents")
                    ?: singleCol?.optJSONObject("sectionListRenderer")?.optJSONArray("contents")

                var headerRenderer: JSONObject? = null
                if (tab0Contents != null) {
                    for (s in 0 until tab0Contents.length()) {
                        val candidate = tab0Contents.optJSONObject(s)?.optJSONObject("musicResponsiveHeaderRenderer")
                            ?: tab0Contents.optJSONObject(s)?.optJSONObject("musicDetailHeaderRenderer")
                        if (candidate != null) {
                            headerRenderer = candidate
                            break
                        }
                    }
                }
                if (headerRenderer == null) {
                    headerRenderer = root.optJSONObject("header")?.optJSONObject("musicResponsiveHeaderRenderer")
                        ?: root.optJSONObject("header")?.optJSONObject("musicDetailHeaderRenderer")
                }

                if (headerRenderer != null) {
                    val titleRun = headerRenderer.optJSONObject("title")?.optJSONArray("runs")?.optJSONObject(0)?.optString("text")
                    if (!titleRun.isNullOrBlank()) title = titleRun

                    val strapline = headerRenderer.optJSONObject("straplineTextOne")?.optJSONArray("runs")?.optJSONObject(0)?.optString("text")?.trim() ?: ""
                    val authorThumbArr = headerRenderer.optJSONObject("straplineThumbnail")
                        ?.optJSONObject("musicThumbnailRenderer")
                        ?.optJSONObject("thumbnail")
                        ?.optJSONArray("thumbnails")
                    authorThumbArr.lastThumbnailUrl()?.let { authorAvatarUrl = it }

                    val descRuns = headerRenderer.optJSONObject("description")
                        ?.optJSONObject("musicDescriptionShelfRenderer")
                        ?.optJSONObject("description")
                        ?.optJSONArray("runs")
                    if (descRuns != null) {
                        description = descRuns.joinRunsText()
                    }

                    val metaParts = mutableListOf<String>()
                    if (strapline.isNotEmpty()) metaParts.add(strapline)

                    listOf("subtitle", "secondSubtitle").forEach { field ->
                        val runs = headerRenderer.optJSONObject(field)?.optJSONArray("runs")
                        if (runs != null) {
                            for (s in 0 until runs.length()) {
                                val t = runs.optJSONObject(s)?.optString("text")?.trim() ?: ""
                                if (t.isNotEmpty() && t != "•") {
                                    metaParts.add(t)
                                    val pv = PLAY_COUNT_REGEX.find(t)?.value
                                    if (!pv.isNullOrBlank() && totalViews.isBlank()) {
                                        totalViews = pv
                                    }
                                }
                            }
                        }
                    }

                    if (metaParts.isNotEmpty()) {
                        authorAndMeta = metaParts.distinct().joinToString(" • ")
                    }

                    headerRenderer.extractMusicThumbnailUrl()?.let { bannerUrl = it }
                }

                var items: JSONArray? = null
                var hostShelf: JSONObject? = null
                val secContents = twoCol?.optJSONObject("secondaryContents")
                    ?.optJSONObject("sectionListRenderer")
                    ?.optJSONArray("contents")

                fun findShelfIn(array: JSONArray?) {
                    if (array == null) return
                    for (s in 0 until array.length()) {
                        val sec = array.optJSONObject(s) ?: continue
                        val shelf = sec.optJSONObject("musicShelfRenderer")
                            ?: sec.optJSONObject("musicPlaylistShelfRenderer")
                        if (shelf != null) {
                            hostShelf = shelf
                            items = shelf.optJSONArray("contents")
                            if (items != null && items!!.length() > 0) break
                        }
                    }
                }
                findShelfIn(secContents)
                if (items == null) findShelfIn(tab0Contents)

                val playlistTracks = mutableListOf<PlaylistTrackItem>()
                var sumPlaysMillion = 0.0
                var hasPlayCounts = false
                var hasMultiRowItems = false

                if (items != null) {
                    for (i in 0 until items!!.length()) {
                        val rawItem = items!!.optJSONObject(i) ?: continue
                        val multiRow = rawItem.optJSONObject("musicMultiRowListItemRenderer")
                        val item = rawItem.optJSONObject("musicResponsiveListItemRenderer")

                        if (multiRow != null) {
                            hasMultiRowItems = true
                            parseMultiRowPodcastItem(
                                multiRow = multiRow,
                                fallbackArtist = playlist.author.ifBlank { title },
                                albumTitle = title,
                                rank = playlistTracks.size + 1,
                                markAsPodcast = false
                            )?.let { playlistTracks.add(it) }
                        } else if (item != null) {
                            val parsed = parseResponsivePlaylistTrackItem(
                                item = item,
                                fallbackArtist = playlist.author.ifBlank { "Artist" },
                                albumTitle = title,
                                rank = playlistTracks.size + 1
                            )
                            if (parsed != null) {
                                playlistTracks.add(parsed.trackItem)
                                if (parsed.hasPlayCount) hasPlayCounts = true
                                sumPlaysMillion += parsed.playsMillion
                            }
                        }
                    }
                }

                var continuationToken = hostShelf?.optJSONArray("continuations")
                    ?.optJSONObject(0)
                    ?.optJSONObject("nextContinuationData")
                    ?.optString("continuation")
                var continuationPages = 0

                while (!continuationToken.isNullOrBlank() && continuationPages < 5 && playlistTracks.size < 600) {
                    continuationPages++
                    try {
                        val contRoot = InnerTubeClient.postInnerTube("browse", gl = "US") {
                            put("continuation", continuationToken)
                        } ?: break
                        val contShelf = contRoot.optJSONObject("continuationContents")?.optJSONObject("musicShelfContinuation")
                            ?: contRoot.optJSONObject("continuationContents")?.optJSONObject("musicPlaylistShelfContinuation")
                        val contItems = contShelf?.optJSONArray("contents")
                        if (contItems != null) {
                            for (ci in 0 until contItems.length()) {
                                val rawItem = contItems.optJSONObject(ci) ?: continue
                                val multiRow = rawItem.optJSONObject("musicMultiRowListItemRenderer") ?: continue
                                parseMultiRowPodcastItem(
                                    multiRow = multiRow,
                                    fallbackArtist = playlist.author.ifBlank { title },
                                    albumTitle = title,
                                    rank = playlistTracks.size + 1,
                                    markAsPodcast = true
                                )?.let { playlistTracks.add(it) }
                            }
                        }
                        continuationToken = contShelf?.optJSONArray("continuations")
                            ?.optJSONObject(0)
                            ?.optJSONObject("nextContinuationData")
                            ?.optString("continuation")
                    } catch (e: Exception) {
                        Log.w(TAG, "Error fetching continuation: ${e.message}")
                        break
                    }
                }

                if (totalViews.isBlank() && hasPlayCounts && sumPlaysMillion > 0) {
                    totalViews = when {
                        sumPlaysMillion >= 1000.0 -> String.format(Locale.US, "%.1fB total plays", sumPlaysMillion / 1000.0)
                        sumPlaysMillion >= 1.0 -> String.format(Locale.US, "%.1fM total plays", sumPlaysMillion)
                        else -> "${(sumPlaysMillion * 1000).toInt()}K total plays"
                    }
                }

                if (playlistTracks.isNotEmpty()) {
                    val finalIsPodcast = isPodcastShow || hasMultiRowItems
                    val finalTracks = if (finalIsPodcast) {
                        playlistTracks.map { it.copy(track = it.track.copy(isPodcast = true)) }
                    } else {
                        playlistTracks
                    }

                    return@withContext PlaylistDetail(
                        id = playlist.id,
                        title = title,
                        author = authorAndMeta,
                        trackCount = "${finalTracks.size} tracks",
                        bannerUrl = bannerUrl,
                        tracks = finalTracks,
                        totalViews = totalViews,
                        description = description,
                        authorAvatarUrl = authorAvatarUrl,
                        isPodcast = finalIsPodcast
                    )
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "Error fetching playlist detail browse: ${e.message}")
        }

        val fallbackTracks = fetchPlaylistTracks(playlist.id)
        if (fallbackTracks.isNotEmpty()) {
            return@withContext PlaylistDetail(
                id = playlist.id,
                title = playlist.title,
                author = playlist.author,
                trackCount = "${fallbackTracks.size} tracks",
                bannerUrl = playlist.artworkUrl,
                tracks = fallbackTracks.mapIndexed { idx, trk ->
                    PlaylistTrackItem(rank = idx + 1, track = trk, viewCount = "Popular Track")
                },
                totalViews = ""
            )
        }

        try {
            val searchQuery = "${playlist.title} ${playlist.author}".trim().ifBlank { playlist.title }
            val searchResults = searchYouTubeMusic(searchQuery, "songs").take(30)
            if (searchResults.isNotEmpty()) {
                return@withContext PlaylistDetail(
                    id = playlist.id,
                    title = playlist.title,
                    author = playlist.author.ifBlank { "YouTube Music" },
                    trackCount = "${searchResults.size} tracks",
                    bannerUrl = playlist.artworkUrl.ifBlank { searchResults.firstOrNull()?.artworkUrl ?: "" },
                    tracks = searchResults.mapIndexed { idx, trk ->
                        PlaylistTrackItem(rank = idx + 1, track = trk, viewCount = "Track ${idx + 1}")
                    },
                    totalViews = ""
                )
            }
        } catch (e: Exception) {
            Log.w(TAG, "Search fallback for playlist detail failed: ${e.message}")
        }

        PlaylistDetail(
            id = playlist.id,
            title = playlist.title,
            author = playlist.author,
            trackCount = "0 tracks",
            bannerUrl = playlist.artworkUrl,
            tracks = emptyList(),
            totalViews = ""
        )
    }

    private fun parseMultiRowPodcastItem(
        multiRow: JSONObject,
        fallbackArtist: String,
        albumTitle: String,
        rank: Int,
        markAsPodcast: Boolean
    ): PlaylistTrackItem? {
        val itemTitle = multiRow.optJSONObject("title")?.optJSONArray("runs")?.optJSONObject(0)?.optString("text")
            ?: return null
        var vidId = multiRow.optJSONObject("onTap")?.optJSONObject("watchEndpoint")?.optString("videoId") ?: ""
        if (vidId.isBlank()) {
            vidId = multiRow.optJSONObject("navigationEndpoint")?.optJSONObject("watchEndpoint")?.optString("videoId") ?: ""
        }
        if (vidId.isBlank()) return null

        val thumb = multiRow.extractMusicThumbnailUrl() ?: "https://img.youtube.com/vi/$vidId/hqdefault.jpg"
        val subRuns = multiRow.optJSONObject("subtitle")?.optJSONArray("runs")
        val subParts = mutableListOf<String>()
        if (subRuns != null) {
            for (s in 0 until subRuns.length()) {
                val t = subRuns.optJSONObject(s)?.optString("text")?.trim() ?: ""
                if (t.isNotEmpty() && t != "•") subParts.add(t)
            }
        }

        val durRuns = multiRow.optJSONObject("playbackProgress")
            ?.optJSONObject("musicPlaybackProgressRenderer")
            ?.optJSONObject("durationText")
            ?.optJSONArray("runs")
        var durText = ""
        if (durRuns != null) {
            for (d in 0 until durRuns.length()) {
                val t = durRuns.optJSONObject(d)?.optString("text")?.trim() ?: ""
                if (t.isNotEmpty()) {
                    durText = t
                    break
                }
            }
        }
        if (durText.isNotEmpty()) subParts.add(durText)

        val epDesc = multiRow.optJSONObject("description")?.optJSONArray("runs").joinRunsText()
        return PlaylistTrackItem(
            rank = rank,
            track = AuraTrack(
                id = "yt_$vidId",
                videoId = vidId,
                title = itemTitle,
                artist = fallbackArtist,
                album = albumTitle,
                durationSeconds = parseDurationText(durText),
                artworkUrl = thumb,
                isPodcast = markAsPodcast
            ),
            viewCount = subParts.distinct().joinToString(" • ").ifBlank { "Podcast Episode" },
            description = epDesc
        )
    }

    private data class ParsedPlaylistRow(
        val trackItem: PlaylistTrackItem,
        val hasPlayCount: Boolean,
        val playsMillion: Double
    )

    private fun parseResponsivePlaylistTrackItem(
        item: JSONObject,
        fallbackArtist: String,
        albumTitle: String,
        rank: Int
    ): ParsedPlaylistRow? {
        val flexCols = item.optJSONArray("flexColumns") ?: return null
        val itemTitle = flexCols.optJSONObject(0)
            ?.optJSONObject("musicResponsiveListItemFlexColumnRenderer")
            ?.optJSONObject("text")
            ?.optJSONArray("runs")
            ?.optJSONObject(0)
            ?.optString("text") ?: return null

        var artist = ""
        if (flexCols.length() > 1) {
            artist = flexCols.optJSONObject(1)
                ?.optJSONObject("musicResponsiveListItemFlexColumnRenderer")
                ?.optJSONObject("text")
                ?.optJSONArray("runs")
                .joinRunsText(" ", skipBullet = true)
        }
        if (artist.isBlank()) artist = fallbackArtist

        var songViews = ""
        var hasPlayCount = false
        for (c in 1 until flexCols.length()) {
            val colRuns = flexCols.optJSONObject(c)
                ?.optJSONObject("musicResponsiveListItemFlexColumnRenderer")
                ?.optJSONObject("text")
                ?.optJSONArray("runs")
            if (colRuns != null) {
                for (r in 0 until colRuns.length()) {
                    val txt = colRuns.optJSONObject(r)?.optString("text")?.trim() ?: ""
                    val pv = PLAY_COUNT_REGEX.find(txt)?.value
                    if (!pv.isNullOrBlank()) {
                        songViews = pv
                        hasPlayCount = true
                        break
                    }
                }
            }
            if (songViews.isNotEmpty()) break
        }

        val durationText = item.optJSONArray("fixedColumns")?.optJSONObject(0)
            ?.optJSONObject("musicResponsiveListItemFixedColumnRenderer")
            ?.optJSONObject("text")
            ?.optJSONArray("runs")
            ?.optJSONObject(0)
            ?.optString("text") ?: ""

        var vidId = item.optJSONObject("playlistItemData")?.optString("videoId") ?: ""
        if (vidId.isBlank()) {
            vidId = item.optJSONObject("overlay")
                ?.optJSONObject("musicItemThumbnailOverlayRenderer")
                ?.optJSONObject("content")
                ?.optJSONObject("musicPlayButtonRenderer")
                ?.optJSONObject("playNavigationEndpoint")
                ?.optJSONObject("watchEndpoint")
                ?.optString("videoId") ?: ""
        }
        if (vidId.isBlank()) {
            vidId = item.optJSONObject("navigationEndpoint")?.optJSONObject("watchEndpoint")?.optString("videoId") ?: ""
        }
        if (vidId.isBlank()) {
            vidId = flexCols.optJSONObject(0)
                ?.optJSONObject("musicResponsiveListItemFlexColumnRenderer")
                ?.optJSONObject("text")
                ?.optJSONArray("runs")
                ?.optJSONObject(0)
                ?.optJSONObject("navigationEndpoint")
                ?.optJSONObject("watchEndpoint")
                ?.optString("videoId") ?: ""
        }
        if (vidId.isBlank()) return null

        val thumb = item.extractMusicThumbnailUrl() ?: "https://img.youtube.com/vi/$vidId/hqdefault.jpg"
        var playsMillion = 0.0
        if (songViews.isNotEmpty()) {
            val match = Regex("""([\d\.]+)\s*([KkMmBb])?""").find(songViews)
            if (match != null) {
                val num = match.groupValues[1].toDoubleOrNull() ?: 0.0
                val unit = match.groupValues.getOrNull(2)?.uppercase() ?: ""
                val mult = when (unit) {
                    "K" -> 0.001
                    "M" -> 1.0
                    "B" -> 1000.0
                    else -> 0.000001
                }
                playsMillion = num * mult
            }
        }

        val finalTrackViews = when {
            songViews.isNotBlank() -> songViews
            durationText.isNotBlank() -> durationText
            else -> "Popular Track"
        }

        return ParsedPlaylistRow(
            trackItem = PlaylistTrackItem(
                rank = rank,
                track = AuraTrack(
                    id = "yt_$vidId",
                    videoId = vidId,
                    title = itemTitle,
                    artist = artist.replace(" - Topic", "").trim(),
                    album = albumTitle,
                    durationSeconds = parseDurationText(durationText),
                    artworkUrl = thumb
                ),
                viewCount = finalTrackViews
            ),
            hasPlayCount = hasPlayCount,
            playsMillion = playsMillion
        )
    }

    // -------------------------------------------------------------------------
    // Artists Search & Detail
    // -------------------------------------------------------------------------

    suspend fun searchArtists(artistName: String): List<YtmTopArtist> = withContext(Dispatchers.IO) {
        if (artistName.isBlank()) return@withContext emptyList()
        val artists = mutableListOf<YtmTopArtist>()
        try {
            val root = InnerTubeClient.postInnerTube("search", gl = "US") {
                put("query", artistName)
                put("params", "EgWKAQIgAWoKEAkQChAFEAMQBA%3D%3D")
            }
            val tabs = root?.optJSONObject("contents")
                ?.optJSONObject("tabbedSearchResultsRenderer")
                ?.optJSONArray("tabs")

            if (tabs != null) {
                for (t in 0 until tabs.length()) {
                    val sectionContents = tabs.optJSONObject(t)
                        ?.optJSONObject("tabRenderer")
                        ?.optJSONObject("content")
                        ?.optJSONObject("sectionListRenderer")
                        ?.optJSONArray("contents") ?: continue

                    for (s in 0 until sectionContents.length()) {
                        val secObj = sectionContents.optJSONObject(s) ?: continue

                        val card = secObj.optJSONObject("musicCardShelfRenderer")
                        if (card != null) {
                            val browseId = card.optJSONObject("onTap")?.optJSONObject("browseEndpoint")?.optString("browseId")
                                ?: card.optJSONObject("title")?.optJSONArray("runs")?.optJSONObject(0)?.optJSONObject("navigationEndpoint")?.optJSONObject("browseEndpoint")?.optString("browseId")
                                ?: ""
                            val name = card.optJSONObject("title")?.optJSONArray("runs")?.optJSONObject(0)?.optString("text") ?: artistName
                            val subInfo = card.optJSONObject("subtitle")?.optJSONArray("runs").joinRunsText()
                            val thumb = card.extractMusicThumbnailUrl() ?: ""
                            if (browseId.isNotBlank()) {
                                artists.add(YtmTopArtist(id = browseId, name = name, subscribers = subInfo, avatarUrl = thumb))
                            }
                        }

                        val shelfItems = secObj.optJSONObject("musicShelfRenderer")?.optJSONArray("contents")
                            ?: secObj.optJSONObject("itemSectionRenderer")?.optJSONArray("contents")
                            ?: continue

                        for (i in 0 until shelfItems.length()) {
                            val item = shelfItems.optJSONObject(i)
                                ?.optJSONObject("musicResponsiveListItemRenderer") ?: continue
                            val flexCols = item.optJSONArray("flexColumns") ?: continue
                            var browseId = item.optJSONObject("navigationEndpoint")
                                ?.optJSONObject("browseEndpoint")
                                ?.optString("browseId") ?: ""

                            if (browseId.isBlank() && flexCols.length() > 0) {
                                browseId = flexCols.optJSONObject(0)
                                    ?.optJSONObject("musicResponsiveListItemFlexColumnRenderer")
                                    ?.optJSONObject("text")
                                    ?.optJSONArray("runs")
                                    ?.optJSONObject(0)
                                    ?.optJSONObject("navigationEndpoint")
                                    ?.optJSONObject("browseEndpoint")
                                    ?.optString("browseId") ?: ""
                            }
                            if (browseId.isBlank()) continue

                            val name = flexCols.optJSONObject(0)
                                ?.optJSONObject("musicResponsiveListItemFlexColumnRenderer")
                                ?.optJSONObject("text")
                                ?.optJSONArray("runs")
                                ?.optJSONObject(0)
                                ?.optString("text") ?: artistName

                            val subInfo = if (flexCols.length() > 1) {
                                flexCols.optJSONObject(1)
                                    ?.optJSONObject("musicResponsiveListItemFlexColumnRenderer")
                                    ?.optJSONObject("text")
                                    ?.optJSONArray("runs")
                                    .joinRunsText()
                            } else ""

                            val thumb = item.extractMusicThumbnailUrl() ?: ""
                            artists.add(
                                YtmTopArtist(
                                    id = browseId,
                                    name = name,
                                    subscribers = subInfo,
                                    avatarUrl = thumb
                                )
                            )
                        }
                    }
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "Search artists error: ${e.message}")
        }
        artists.distinctBy { it.id }.sortedWith(
            compareByDescending<YtmTopArtist> { it.name == artistName }
                .thenByDescending { it.name.equals(artistName, ignoreCase = true) }
        )
    }

    suspend fun searchArtist(artistName: String): YtmTopArtist? = searchArtists(artistName).firstOrNull()

    suspend fun fetchArtistDetail(artist: YtmTopArtist): ArtistDetail = withContext(Dispatchers.IO) {
        var browseId = artist.id
        var bannerUrl = artist.avatarUrl
        var subsCount = artist.subscribers
        var artistDescription = ""

        if (browseId.isBlank()) {
            val resolved = searchArtist(artist.name)
            if (resolved != null && resolved.id.isNotBlank()) {
                browseId = resolved.id
                if (bannerUrl.isBlank()) bannerUrl = resolved.avatarUrl
                if (subsCount.isBlank()) subsCount = resolved.subscribers
            }
        }

        val topSongs = mutableListOf<ArtistSong>()
        val albums = mutableListOf<ArtistAlbum>()
        val carousels = mutableListOf<ArtistCarousel>()

        if (browseId.isNotBlank()) {
            try {
                val root = InnerTubeClient.postInnerTube("browse") {
                    put("browseId", browseId)
                }
                if (root != null) {
                    val header = root.optJSONObject("header")?.optJSONObject("musicImmersiveHeaderRenderer")
                        ?: root.optJSONObject("header")?.optJSONObject("musicVisualHeaderRenderer")
                        ?: root.optJSONObject("header")?.optJSONObject("musicHeaderRenderer")

                    val bannerThumbnails = header?.optJSONObject("thumbnail")
                        ?.optJSONObject("musicThumbnailRenderer")
                        ?.optJSONObject("thumbnail")
                        ?.optJSONArray("thumbnails")
                        ?: header?.optJSONObject("foregroundThumbnail")
                            ?.optJSONObject("musicThumbnailRenderer")
                            ?.optJSONObject("thumbnail")
                            ?.optJSONArray("thumbnails")

                    bannerThumbnails.lastThumbnailUrl()?.let { bannerUrl = it }

                    val parsedSubs = header?.optJSONObject("subscriptionButton")
                        ?.optJSONObject("subscribeButtonRenderer")
                        ?.optJSONObject("subscriberCountText")
                        ?.optJSONArray("runs")
                        ?.optJSONObject(0)
                        ?.optString("text")
                        ?: header?.optJSONObject("subscriberCountText")
                            ?.optJSONArray("runs")
                            ?.optJSONObject(0)
                            ?.optString("text")

                    if (!parsedSubs.isNullOrBlank()) {
                        val clean = parsedSubs.trim()
                        subsCount = if (clean.contains("sub", ignoreCase = true) || clean.contains("audience", ignoreCase = true)) {
                            clean
                        } else {
                            "$clean subscribers"
                        }
                    }

                    artistDescription = header?.optJSONObject("description")?.let { desc ->
                        val runs = desc.optJSONArray("runs")
                        if (runs != null) runs.joinRunsText() else desc.optString("simpleText", "")
                    }.orEmpty()

                    val contents = extractSingleColSections(root)
                    if (contents != null) {
                        for (i in 0 until contents.length()) {
                            val shelfObj = contents.optJSONObject(i) ?: continue

                            val shelf = shelfObj.optJSONObject("musicShelfRenderer")
                            if (shelf != null) {
                                val shelfTitle = shelf.optJSONObject("title")
                                    ?.optJSONArray("runs")
                                    ?.optJSONObject(0)
                                    ?.optString("text") ?: ""

                                if (shelfTitle.contains("Top songs", ignoreCase = true) || shelfTitle.contains("Songs", ignoreCase = true)) {
                                    val moreBrowseId = shelf.optJSONObject("bottomEndpoint")
                                        ?.optJSONObject("browseEndpoint")
                                        ?.optString("browseId")
                                        ?: shelf.optJSONObject("title")
                                            ?.optJSONArray("runs")
                                            ?.optJSONObject(0)
                                            ?.optJSONObject("navigationEndpoint")
                                            ?.optJSONObject("browseEndpoint")
                                            ?.optString("browseId") ?: ""
                                    val moreParams = shelf.optJSONObject("bottomEndpoint")
                                        ?.optJSONObject("browseEndpoint")
                                        ?.optString("params")
                                        ?: shelf.optJSONObject("title")
                                            ?.optJSONArray("runs")
                                            ?.optJSONObject(0)
                                            ?.optJSONObject("navigationEndpoint")
                                            ?.optJSONObject("browseEndpoint")
                                            ?.optString("params") ?: ""

                                    if (moreBrowseId.isNotBlank()) {
                                        try {
                                            val plRoot = InnerTubeClient.postInnerTube("browse", gl = "US") {
                                                put("browseId", moreBrowseId)
                                                if (moreParams.isNotBlank()) put("params", moreParams)
                                            }
                                            val plContents = plRoot?.let { extractSingleColSections(it) }
                                                ?.optJSONObject(0)
                                                ?.optJSONObject("musicPlaylistShelfRenderer")
                                                ?.optJSONArray("contents")
                                            parseArtistSongItems(plContents, maxCount = 30, albumColIndex = 3, artistName = artist.name, out = topSongs)
                                        } catch (e: Exception) {
                                            Log.d(TAG, "Error fetching full top songs playlist: ${e.message}")
                                        }
                                    }

                                    if (topSongs.isEmpty()) {
                                        parseArtistSongItems(shelf.optJSONArray("contents"), maxCount = 20, albumColIndex = 1, artistName = artist.name, out = topSongs)
                                    }
                                }
                            }

                            val carousel = shelfObj.optJSONObject("musicCarouselShelfRenderer")
                            if (carousel != null) {
                                parseArtistCarousel(carousel, albums, carousels)
                            }
                        }
                    }
                }
            } catch (e: Exception) {
                Log.w(TAG, "Error fetching artist channel browse: ${e.message}")
            }
        }

        if (topSongs.isEmpty()) {
            val searched = searchYouTubeMusic("${artist.name} official songs", "songs").take(25)
            searched.forEachIndexed { idx, track ->
                topSongs.add(ArtistSong(rank = idx + 1, track = track, playCount = ""))
            }
        }

        if (albums.isEmpty()) {
            try {
                val albumSearch = searchYouTubeMusic("${artist.name} album official", "albums").ifEmpty {
                    searchYouTubeMusic("${artist.name} discography", "songs")
                }
                albumSearch.take(8).forEach { trk ->
                    albums.add(
                        ArtistAlbum(
                            id = trk.videoId,
                            title = trk.album.ifBlank { trk.title },
                            year = "Album",
                            artworkUrl = trk.artworkUrl,
                            type = "Album"
                        )
                    )
                }
            } catch (e: Exception) {
                Log.d(TAG, "Fallback albums search error: ${e.message}")
            }
        }

        ArtistDetail(
            id = browseId.ifBlank { artist.id },
            name = artist.name,
            subscribers = subsCount,
            bannerUrl = bannerUrl,
            topSongs = topSongs,
            albums = albums,
            carousels = carousels,
            description = artistDescription
        )
    }

    private fun parseArtistSongItems(
        items: JSONArray?,
        maxCount: Int,
        albumColIndex: Int,
        artistName: String,
        out: MutableList<ArtistSong>
    ) {
        if (items == null || items.length() == 0) return
        for (j in 0 until minOf(items.length(), maxCount)) {
            val item = items.optJSONObject(j)?.optJSONObject("musicResponsiveListItemRenderer") ?: continue
            val flexCols = item.optJSONArray("flexColumns") ?: continue
            val title = flexCols.optJSONObject(0)
                ?.optJSONObject("musicResponsiveListItemFlexColumnRenderer")
                ?.optJSONObject("text")
                ?.optJSONArray("runs")
                ?.optJSONObject(0)
                ?.optString("text") ?: continue

            val album = if (flexCols.length() > albumColIndex) {
                flexCols.optJSONObject(albumColIndex)?.optJSONObject("musicResponsiveListItemFlexColumnRenderer")
                    ?.optJSONObject("text")?.optJSONArray("runs")?.optJSONObject(0)?.optString("text") ?: artistName
            } else artistName

            val playCount = if (flexCols.length() > 2) {
                flexCols.optJSONObject(2)
                    ?.optJSONObject("musicResponsiveListItemFlexColumnRenderer")
                    ?.optJSONObject("text")
                    ?.optJSONArray("runs")
                    ?.optJSONObject(0)
                    ?.optString("text") ?: ""
            } else ""

            val vidId = item.optJSONObject("playlistItemData")?.optString("videoId")
                ?: item.optJSONObject("navigationEndpoint")
                    ?.optJSONObject("watchEndpoint")
                    ?.optString("videoId") ?: continue

            val thumb = item.extractMusicThumbnailUrl() ?: "https://img.youtube.com/vi/$vidId/hqdefault.jpg"
            out.add(
                ArtistSong(
                    rank = out.size + 1,
                    track = AuraTrack(
                        id = "yt_$vidId",
                        videoId = vidId,
                        title = title,
                        artist = artistName,
                        album = album,
                        durationSeconds = 210,
                        artworkUrl = thumb
                    ),
                    playCount = playCount
                )
            )
        }
    }

    private fun parseArtistCarousel(
        carousel: JSONObject,
        albums: MutableList<ArtistAlbum>,
        carousels: MutableList<ArtistCarousel>
    ) {
        val headerTitle = carousel.optJSONObject("header")
            ?.optJSONObject("musicCarouselShelfBasicHeaderRenderer")
            ?.optJSONObject("title")
            ?.optJSONArray("runs")
            ?.optJSONObject(0)
            ?.optString("text") ?: ""

        val carouselItems = mutableListOf<ArtistCarouselItem>()
        val contentsArr = carousel.optJSONArray("contents")
        if (contentsArr != null && headerTitle.isNotBlank()) {
            for (k in 0 until minOf(contentsArr.length(), 15)) {
                val twoRow = contentsArr.optJSONObject(k)?.optJSONObject("musicTwoRowItemRenderer") ?: continue
                val itemTitle = twoRow.optJSONObject("title")
                    ?.optJSONArray("runs")
                    ?.optJSONObject(0)
                    ?.optString("text") ?: continue

                val subtitle = twoRow.optJSONObject("subtitle")?.optJSONArray("runs")
                    .joinRunsText(" • ", skipBullet = true)

                val nav = twoRow.optJSONObject("navigationEndpoint")
                val vid = nav?.optJSONObject("watchEndpoint")?.optString("videoId") ?: ""
                val bid = nav?.optJSONObject("browseEndpoint")?.optString("browseId") ?: ""
                val pageType = nav?.optJSONObject("browseEndpoint")
                    ?.optJSONObject("browseEndpointContextSupportedConfigs")
                    ?.optJSONObject("browseEndpointContextMusicConfig")
                    ?.optString("pageType") ?: ""

                val endpointType = when {
                    vid.isNotBlank() -> "video"
                    pageType == "MUSIC_PAGE_TYPE_ARTIST" -> "artist"
                    pageType == "MUSIC_PAGE_TYPE_PLAYLIST" -> "playlist"
                    else -> "album"
                }

                val thumb = twoRow.extractMusicThumbnailUrl() ?: ""
                val itemId = if (vid.isNotBlank()) vid else if (bid.isNotBlank()) bid else "item_${carouselItems.size}"

                carouselItems.add(
                    ArtistCarouselItem(
                        id = itemId,
                        title = itemTitle,
                        subtitle = subtitle,
                        artworkUrl = thumb,
                        endpointType = endpointType,
                        videoId = vid,
                        browseId = bid
                    )
                )

                if (headerTitle.contains("Albums", ignoreCase = true) ||
                    headerTitle.contains("Singles", ignoreCase = true) ||
                    headerTitle.contains("EPs", ignoreCase = true)
                ) {
                    albums.add(
                        ArtistAlbum(
                            id = itemId,
                            title = itemTitle,
                            year = subtitle.ifBlank { if (headerTitle.contains("Single", ignoreCase = true)) "Single" else "Album" },
                            artworkUrl = thumb,
                            type = if (headerTitle.contains("Single", ignoreCase = true)) "Single" else "Album"
                        )
                    )
                }
            }

            if (carouselItems.isNotEmpty()) {
                carousels.add(ArtistCarousel(title = headerTitle, items = carouselItems))
            }
        }
    }

    suspend fun fetchArtistByName(
        artistName: String,
        songTitle: String = "",
        fallbackArtwork: String = ""
    ): ArtistDetail = withContext(Dispatchers.IO) {
        var resolvedBrowseId: String? = null
        if (songTitle.isNotBlank()) {
            resolvedBrowseId = resolveArtistBrowseId(artistName, songTitle)
        }

        val resolved = if (!resolvedBrowseId.isNullOrBlank()) {
            YtmTopArtist(
                id = resolvedBrowseId,
                name = artistName,
                subscribers = "",
                avatarUrl = fallbackArtwork
            )
        } else {
            searchArtist(artistName)
        }

        val targetArtist = resolved ?: YtmTopArtist(
            id = "",
            name = artistName,
            subscribers = "",
            avatarUrl = fallbackArtwork
        )
        fetchArtistDetail(targetArtist)
    }

    suspend fun resolveArtistBrowseId(artistName: String, songTitle: String): String? = withContext(Dispatchers.IO) {
        if (artistName.isBlank() || songTitle.isBlank()) return@withContext null
        try {
            val root = InnerTubeClient.postInnerTube("search", gl = "US") {
                put("query", "$artistName $songTitle")
            } ?: return@withContext null
            val tabs = root.optJSONObject("contents")
                ?.optJSONObject("tabbedSearchResultsRenderer")
                ?.optJSONArray("tabs") ?: return@withContext null

            for (t in 0 until tabs.length()) {
                val sec = tabs.optJSONObject(t)
                    ?.optJSONObject("tabRenderer")
                    ?.optJSONObject("content")
                    ?.optJSONObject("sectionListRenderer")
                    ?.optJSONArray("contents") ?: continue

                for (s in 0 until sec.length()) {
                    val secObj = sec.optJSONObject(s) ?: continue
                    val shelfItems = secObj.optJSONObject("musicShelfRenderer")?.optJSONArray("contents")
                        ?: secObj.optJSONObject("itemSectionRenderer")?.optJSONArray("contents")
                        ?: continue

                    for (i in 0 until shelfItems.length()) {
                        val r = shelfItems.optJSONObject(i)?.optJSONObject("musicResponsiveListItemRenderer") ?: continue
                        val flex = r.optJSONArray("flexColumns") ?: continue
                        if (flex.length() > 1) {
                            val runs = flex.optJSONObject(1)
                                ?.optJSONObject("musicResponsiveListItemFlexColumnRenderer")
                                ?.optJSONObject("text")
                                ?.optJSONArray("runs") ?: continue

                            for (ri in 0 until runs.length()) {
                                val run = runs.optJSONObject(ri) ?: continue
                                val runText = run.optString("text").trim()
                                val nav = run.optJSONObject("navigationEndpoint")?.optJSONObject("browseEndpoint")
                                val pageType = nav?.optJSONObject("browseEndpointContextSupportedConfigs")
                                    ?.optJSONObject("browseEndpointContextMusicConfig")
                                    ?.optString("pageType") ?: ""
                                val bId = nav?.optString("browseId") ?: ""

                                if ((pageType == "MUSIC_PAGE_TYPE_ARTIST" || bId.startsWith("UC")) && bId.isNotBlank()) {
                                    if (runText.equals(artistName.trim(), ignoreCase = true)) {
                                        return@withContext bId
                                    }
                                }
                            }
                        }
                    }
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "Resolve artist browseId error: ${e.message}")
        }
        null
    }

    // -------------------------------------------------------------------------
    // Shared YTM Search Parser & Helpers
    // -------------------------------------------------------------------------

    private fun extractSingleColSections(root: JSONObject): JSONArray? =
        root.optJSONObject("contents")
            ?.optJSONObject("singleColumnBrowseResultsRenderer")
            ?.optJSONArray("tabs")
            ?.optJSONObject(0)
            ?.optJSONObject("tabRenderer")
            ?.optJSONObject("content")
            ?.optJSONObject("sectionListRenderer")
            ?.optJSONArray("contents")

    private fun parseYtmSearchTracks(
        root: JSONObject,
        out: MutableList<AuraTrack>,
        defaultMusicVideoType: String? = null
    ) {
        val tabs = root.optJSONObject("contents")
            ?.optJSONObject("tabbedSearchResultsRenderer")
            ?.optJSONArray("tabs") ?: return

        for (t in 0 until tabs.length()) {
            val sectionContents = tabs.optJSONObject(t)
                ?.optJSONObject("tabRenderer")
                ?.optJSONObject("content")
                ?.optJSONObject("sectionListRenderer")
                ?.optJSONArray("contents") ?: continue

            for (s in 0 until sectionContents.length()) {
                val secObj = sectionContents.optJSONObject(s) ?: continue
                val shelfItems = secObj.optJSONObject("musicShelfRenderer")?.optJSONArray("contents")
                    ?: secObj.optJSONObject("itemSectionRenderer")?.optJSONArray("contents")
                    ?: secObj.optJSONObject("musicCardShelfRenderer")?.optJSONArray("contents")
                    ?: continue

                for (i in 0 until shelfItems.length()) {
                    val item = shelfItems.optJSONObject(i)
                        ?.optJSONObject("musicResponsiveListItemRenderer") ?: continue

                    val flexColumns = item.optJSONArray("flexColumns") ?: continue
                    val firstRun = flexColumns.optJSONObject(0)
                        ?.optJSONObject("musicResponsiveListItemFlexColumnRenderer")
                        ?.optJSONObject("text")
                        ?.optJSONArray("runs")
                        ?.optJSONObject(0)

                    val videoId = item.optJSONObject("playlistItemData")?.optString("videoId")?.takeIf { it.isNotBlank() }
                        ?: item.optJSONObject("overlay")
                            ?.optJSONObject("musicItemThumbnailOverlayRenderer")
                            ?.optJSONObject("content")
                            ?.optJSONObject("musicPlayButtonRenderer")
                            ?.optJSONObject("playNavigationEndpoint")
                            ?.optJSONObject("watchEndpoint")
                            ?.optString("videoId")
                            ?.takeIf { it.isNotBlank() }
                        ?: item.optJSONObject("navigationEndpoint")
                            ?.optJSONObject("watchEndpoint")
                            ?.optString("videoId")
                            ?.takeIf { it.isNotBlank() }
                        ?: firstRun?.optJSONObject("navigationEndpoint")
                            ?.optJSONObject("watchEndpoint")
                            ?.optString("videoId")
                            ?.takeIf { it.isNotBlank() }
                        ?: item.optJSONObject("navigationEndpoint")
                            ?.optJSONObject("browseEndpoint")
                            ?.optString("browseId")
                            ?.takeIf { it.isNotBlank() }
                        ?: firstRun?.optJSONObject("navigationEndpoint")
                            ?.optJSONObject("browseEndpoint")
                            ?.optString("browseId")
                            ?.takeIf { it.isNotBlank() }
                        ?: continue

                    val title = firstRun?.optString("text") ?: continue

                    val subtitleRuns = flexColumns.optJSONObject(1)
                        ?.optJSONObject("musicResponsiveListItemFlexColumnRenderer")
                        ?.optJSONObject("text")
                        ?.optJSONArray("runs")

                    var artist = "YouTube Artist"
                    var album = "YouTube Music"
                    var durationSec = 210
                    var viewCount: Long? = null

                    if (subtitleRuns != null) {
                        val texts = mutableListOf<String>()
                        for (r in 0 until subtitleRuns.length()) {
                            val txt = subtitleRuns.optJSONObject(r)?.optString("text")?.trim() ?: ""
                            if (txt.isNotEmpty() && txt != "•" && txt != "&") {
                                texts.add(txt)
                            }
                        }
                        val isAlbumOrSingle = texts.isNotEmpty() && (
                            texts[0].equals("Album", ignoreCase = true) ||
                                texts[0].equals("EP", ignoreCase = true) ||
                                texts[0].equals("Single", ignoreCase = true)
                            )
                        if (texts.isNotEmpty()) {
                            artist = if (texts[0].equals("Song", ignoreCase = true) || texts[0].equals("Video", ignoreCase = true) || isAlbumOrSingle) {
                                texts.getOrNull(1) ?: "YouTube Artist"
                            } else {
                                texts[0]
                            }
                        }
                        viewCount = parseCompactViewCount(texts)
                        texts.firstOrNull { it.matches(Regex("^\\d+:\\d{2}(:\\d{2})?$")) }?.let {
                            durationSec = parseDurationText(it)
                        }
                        if (isAlbumOrSingle || videoId.startsWith("MPREb_") || videoId.startsWith("OLAK5uy_")) {
                            album = title
                        } else {
                            val albumCandidate = texts.drop(1).firstOrNull { candidate ->
                                !candidate.equals(artist, ignoreCase = true) &&
                                    !candidate.equals("Song", ignoreCase = true) &&
                                    !candidate.equals("Video", ignoreCase = true) &&
                                    !candidate.matches(Regex("^\\d+:\\d{2}(:\\d{2})?$")) &&
                                    !candidate.endsWith("views", ignoreCase = true) &&
                                    !candidate.endsWith("plays", ignoreCase = true)
                            }
                            if (albumCandidate != null) {
                                album = albumCandidate
                            }
                        }
                    }

                    val rawThumb = item.extractMusicThumbnailUrl()
                        ?: "https://img.youtube.com/vi/$videoId/hqdefault.jpg"

                    out.add(
                        AuraTrack(
                            id = "yt_$videoId",
                            videoId = videoId,
                            title = title,
                            artist = artist.replace(" - Topic", "").trim(),
                            album = album,
                            durationSeconds = durationSec,
                            artworkUrl = rawThumb,
                            musicVideoType = innerTubeMusicVideoType(item) ?: defaultMusicVideoType,
                            viewCount = viewCount
                        )
                    )
                }
            }
        }
    }

    val DEFAULT_SEED_TRACKS = listOf(
        AuraTrack(
            id = "curated-paradise",
            videoId = "4tijiFGhBN8",
            title = "PARADISE",
            artist = "Chase Atlantic",
            album = "BEAUTY IN DEATH (Deluxe Edition)",
            durationSeconds = 256,
            artworkUrl = "https://i.ytimg.com/vi/4tijiFGhBN8/hq720.jpg",
            genre = "Alt R&B",
            hasLyricsBadge = true
        ),
        AuraTrack(
            id = "curated-blinding-lights",
            videoId = "4NRXx6U8ABQ",
            title = "Blinding Lights",
            artist = "The Weeknd",
            album = "After Hours",
            durationSeconds = 200,
            artworkUrl = "https://i.ytimg.com/vi/4NRXx6U8ABQ/hq720.jpg",
            genre = "Synthwave",
            hasLyricsBadge = true
        ),
        AuraTrack(
            id = "curated-birds-of-a-feather",
            videoId = "d5gf9dXbPi0",
            title = "BIRDS OF A FEATHER",
            artist = "Billie Eilish",
            album = "HIT ME HARD AND SOFT",
            durationSeconds = 196,
            artworkUrl = "https://i.ytimg.com/vi/d5gf9dXbPi0/hq720.jpg",
            genre = "Alt Pop",
            isExplicit = true
        ),
        AuraTrack(
            id = "curated-fein",
            videoId = "B9synWjqBn8",
            title = "FE!N",
            artist = "Travis Scott",
            album = "UTOPIA",
            durationSeconds = 191,
            artworkUrl = "https://i.ytimg.com/vi/B9synWjqBn8/hq720.jpg",
            genre = "Hip Hop",
            isExplicit = true
        ),
        AuraTrack(
            id = "curated-505",
            videoId = "qU9mHegkTc4",
            title = "505",
            artist = "Arctic Monkeys",
            album = "Favourite Worst Nightmare",
            durationSeconds = 253,
            artworkUrl = "https://i.ytimg.com/vi/qU9mHegkTc4/hq720.jpg",
            genre = "Indie Rock",
            hasLyricsBadge = true
        ),
        AuraTrack(
            id = "curated-starboy",
            videoId = "34Na4j8AVgA",
            title = "Starboy",
            artist = "The Weeknd ft. Daft Punk",
            album = "Starboy",
            durationSeconds = 230,
            artworkUrl = "https://i.ytimg.com/vi/34Na4j8AVgA/hq720.jpg",
            genre = "R&B / Pop",
            isExplicit = true
        ),
        AuraTrack(
            id = "curated-snooze",
            videoId = "Sv5yCzPCkv8",
            title = "Snooze",
            artist = "SZA",
            album = "SOS",
            durationSeconds = 201,
            artworkUrl = "https://i.ytimg.com/vi/Sv5yCzPCkv8/hq720.jpg",
            genre = "R&B",
            hasLyricsBadge = true
        ),
        AuraTrack(
            id = "curated-chemical",
            videoId = "4D4bB6n0qI4",
            title = "Chemical",
            artist = "Post Malone",
            album = "AUSTIN",
            durationSeconds = 184,
            artworkUrl = "https://i.ytimg.com/vi/4D4bB6n0qI4/hq720.jpg",
            genre = "Pop",
            hasLyricsBadge = true
        )
    )
}
