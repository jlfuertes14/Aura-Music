package com.auramusic.app.core.extractor

import android.util.Log
import com.auramusic.app.domain.model.ArtistDetail
import com.auramusic.app.domain.model.ArtistSong
import com.auramusic.app.domain.model.AuraPlaylist
import com.auramusic.app.domain.model.AuraTrack
import com.auramusic.app.domain.model.AuraVideoInfo
import com.auramusic.app.domain.model.AuraChart
import com.auramusic.app.domain.model.AuraChartPlaylistSection
import com.auramusic.app.domain.model.AuraChartArtistItem
import com.auramusic.app.domain.model.AuraChartPodcastSection
import com.auramusic.app.domain.model.MoodBrowseResult
import com.auramusic.app.domain.model.MoodCategory
import com.auramusic.app.domain.model.YtmMoodBrowseParams
import com.auramusic.app.domain.model.YtmMoodShelf
import com.auramusic.app.domain.model.YtmMoodItem
import com.auramusic.app.domain.model.YtmMoodItemType
import com.auramusic.app.domain.model.PlaylistDetail
import com.auramusic.app.domain.model.PlaylistTrackItem
import com.auramusic.app.domain.model.ResolvedVideoStream
import com.auramusic.app.domain.model.YtmChartSection
import com.auramusic.app.domain.model.YtmCommunityPlaylist
import com.auramusic.app.domain.model.YtmTopArtist
import dev.maxrave.pipepipe.extractor.NewPipe as PipePipe
import dev.maxrave.pipepipe.extractor.ServiceList as PipePipeServiceList
import dev.maxrave.pipepipe.extractor.downloader.CancellableCall as PipePipeCancellableCall
import dev.maxrave.pipepipe.extractor.downloader.Downloader as PipePipeDownloader
import dev.maxrave.pipepipe.extractor.downloader.Request as PipePipeRequest
import dev.maxrave.pipepipe.extractor.downloader.Response as PipePipeResponse
import dev.maxrave.pipepipe.extractor.exceptions.ReCaptchaException as PipePipeReCaptchaException
import dev.maxrave.pipepipe.extractor.services.youtube.YoutubeApiDecoder
import dev.maxrave.pipepipe.extractor.stream.AudioStream as PipePipeAudioStream
import dev.maxrave.pipepipe.extractor.stream.StreamInfo as PipePipeStreamInfo
import com.auramusic.app.domain.model.isPendingResolution
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import org.schabi.newpipe.extractor.NewPipe as BraveNewPipe
import org.schabi.newpipe.extractor.ServiceList as BraveServiceList
import org.schabi.newpipe.extractor.downloader.Downloader as BraveDownloader
import org.schabi.newpipe.extractor.downloader.Request as BraveRequest
import org.schabi.newpipe.extractor.downloader.Response as BraveResponse
import org.schabi.newpipe.extractor.exceptions.ReCaptchaException as BraveReCaptchaException
import org.schabi.newpipe.extractor.localization.ContentCountry
import org.schabi.newpipe.extractor.localization.Localization
import org.schabi.newpipe.extractor.stream.AudioStream as BraveAudioStream
import org.schabi.newpipe.extractor.stream.StreamInfo as BraveStreamInfo
import org.schabi.newpipe.extractor.stream.StreamInfoItem
import java.io.IOException
import kotlin.math.ln
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit

data class ResolvedStream(
    val videoId: String,
    val streamUrl: String,
    val mimeType: String = "audio/mp4",
    val bitrateKbps: Int = 160,
    val codec: String = "AAC",
    val loudnessDb: Float = 0f,
    val durationSeconds: Int = 0,
    val sourceTier: String = "PipePipeExtractor",
    val expiresAtMs: Long = System.currentTimeMillis() + 5 * 3600 * 1000L
)

object SimpMusicExtractorEngine {
    private const val TAG = "SimpMusicExtractor"
    private const val USER_AGENT =
        "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/131.0.0.0 Safari/537.36"

    private val backgroundScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    // Tier 0 In-memory Resolved Stream Cache (videoId -> ResolvedStream)
    private val streamCache = ConcurrentHashMap<String, ResolvedStream>()
    // Tier 0 In-memory Resolved Video Stream Cache (videoId -> ResolvedVideoStream)
    private val videoStreamCache = ConcurrentHashMap<String, ResolvedVideoStream>()
    private val videoInfoCache = ConcurrentHashMap<String, AuraVideoInfo>()

    val httpClient: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .connectTimeout(10, TimeUnit.SECONDS)
            .readTimeout(15, TimeUnit.SECONDS)
            .followRedirects(true)
            .followSslRedirects(true)
            .build()
    }

    private val fastVerifyClient: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .connectTimeout(4, TimeUnit.SECONDS)
            .readTimeout(4, TimeUnit.SECONDS)
            .followRedirects(true)
            .followSslRedirects(true)
            .build()
    }

    @Volatile
    private var isInitialized = false

    fun initExtractors() {
        if (isInitialized) return
        synchronized(this) {
            if (isInitialized) return
            try {
                // 1. Initialize SimpMusic PipePipeExtractor (uses api.pipepipe.dev remote decoder when localDecoder is null)
                YoutubeApiDecoder.setLocalDecoder(null)
                PipePipe.init(object : PipePipeDownloader() {
                    private fun buildOkHttpReq(request: PipePipeRequest): Request {
                        val builder = Request.Builder()
                            .url(request.url())
                            .header("User-Agent", USER_AGENT)
                        request.headers().forEach { (key, values) ->
                            builder.removeHeader(key)
                            values.forEach { v -> builder.addHeader(key, v) }
                        }
                        val bodyBytes = request.dataToSend()
                        val reqBody = bodyBytes?.toRequestBody(null, 0, bodyBytes.size)
                        builder.method(request.httpMethod(), reqBody)
                        return builder.build()
                    }

                    private fun okhttp3.Response.toPipePipeResponse(): PipePipeResponse {
                        val rawBytes = body?.bytes() ?: ByteArray(0)
                        return PipePipeResponse(
                            code,
                            message,
                            headers.toMultimap(),
                            rawBytes.toString(Charsets.UTF_8),
                            rawBytes,
                            request.url.toString()
                        )
                    }

                    override fun execute(request: PipePipeRequest): PipePipeResponse {
                        httpClient.newCall(buildOkHttpReq(request)).execute().use { resp ->
                            if (resp.code == 429) {
                                throw PipePipeReCaptchaException("reCaptcha Challenge requested", request.url())
                            }
                            return resp.toPipePipeResponse()
                        }
                    }

                    override fun executeAsync(
                        request: PipePipeRequest,
                        callback: AsyncCallback?
                    ): PipePipeCancellableCall {
                        val call = httpClient.newCall(buildOkHttpReq(request))
                        val cancellable = PipePipeCancellableCall(call)
                        call.enqueue(object : okhttp3.Callback {
                            override fun onFailure(call: okhttp3.Call, e: IOException) {
                                cancellable.setFinished()
                                callback?.onError(e)
                            }

                            override fun onResponse(call: okhttp3.Call, response: okhttp3.Response) {
                                try {
                                    if (response.code == 429) {
                                        response.close()
                                        callback?.onError(
                                            PipePipeReCaptchaException("reCaptcha Challenge requested", request.url())
                                        )
                                        return
                                    }
                                    callback?.onSuccess(response.toPipePipeResponse())
                                } catch (e: Exception) {
                                    callback?.onError(e)
                                } finally {
                                    cancellable.setFinished()
                                }
                            }
                        })
                        return cancellable
                    }
                })
            } catch (e: Exception) {
                Log.w(TAG, "PipePipeExtractor init warning: ${e.message}")
            }

            try {
                // 2. Initialize SmartTube / BraveNewPipe Extractor
                BraveNewPipe.init(
                    object : BraveDownloader() {
                        override fun execute(request: BraveRequest): BraveResponse {
                            val builder = Request.Builder()
                                .url(request.url())
                                .header("User-Agent", USER_AGENT)
                            request.headers().forEach { (key, values) ->
                                builder.removeHeader(key)
                                values.forEach { v -> builder.addHeader(key, v) }
                            }
                            val bodyBytes = request.dataToSend()
                            val reqBody = bodyBytes?.toRequestBody(null, 0, bodyBytes.size)
                            builder.method(request.httpMethod(), reqBody)

                            httpClient.newCall(builder.build()).execute().use { resp ->
                                if (resp.code == 429) {
                                    throw BraveReCaptchaException("reCaptcha Challenge requested", request.url())
                                }
                                val bodyStr = resp.body?.string() ?: ""
                                return BraveResponse(
                                    resp.code,
                                    resp.message,
                                    resp.headers.toMultimap(),
                                    bodyStr,
                                    resp.request.url.toString()
                                )
                            }
                        }
                    },
                    Localization.DEFAULT,
                    ContentCountry.DEFAULT
                )
            } catch (e: Exception) {
                Log.w(TAG, "BraveNewPipe init warning: ${e.message}")
            }
            isInitialized = true
        }
    }

    fun getCachedStream(videoId: String): ResolvedStream? {
        val cached = streamCache[videoId] ?: return null
        if (cached.expiresAtMs > System.currentTimeMillis() + 60_000L) {
            return cached
        }
        streamCache.remove(videoId)
        return null
    }

    fun invalidateStream(videoId: String) {
        streamCache.remove(videoId)
    }

    fun prefetchStream(videoId: String) {
        if (videoId.isBlank() || getCachedStream(videoId) != null) return
        backgroundScope.launch {
            try {
                resolveStream(videoId)
            } catch (e: Exception) {
                Log.d(TAG, "Prefetch skipped for $videoId: ${e.message}")
            }
        }
    }

    /**
     * SimpMusic is403Url equivalent: verifies the stream URL responds with HTTP 200 or 206
     * and returns the final redirected URL if any.
     */
    private fun verifyPlayableUrl(candidateUrl: String): String? {
        return try {
            val req = Request.Builder()
                .url(candidateUrl)
                .header("User-Agent", USER_AGENT)
                .header("Range", "bytes=0-1024")
                .get()
                .build()
            fastVerifyClient.newCall(req).execute().use { resp ->
                if (resp.code in 200..299) {
                    resp.request.url.toString()
                } else {
                    null
                }
            }
        } catch (e: Exception) {
            null
        }
    }

    private val XTAGS_REGEX = Regex("[?&]xtags=([^&]+)")

    /**
     * Ranks audio stream URLs to guarantee the original language (English for US podcasts/charts)
     * is strictly chosen over foreign language dubbed audio tracks (e.g. Brazilian Portuguese, Spanish, etc.).
     *
     * In YouTube videos with multi-track audio (podcasts, MrBeast, etc.), all language tracks share
     * identical itags and bitrates. The track identity is encoded in the googlevideo URL's `xtags`:
     * - `acont=original:lang=en-US` (original track)
     * - `acont=dubbed-auto:lang=pt-BR` (auto-dubbed Portuguese)
     * Single-track music tracks carry no `xtags` and rank cleanly as original (rank 1).
     */
    internal fun audioTrackRank(url: String, preferredLanguage: String = "en"): Int {
        val tags = XTAGS_REGEX
            .find(url)
            ?.groupValues
            ?.getOrNull(1)
            ?.replace("%3D", "=", ignoreCase = true)
            ?.replace("%3A", ":", ignoreCase = true)
            ?.split(':')
            ?.associate {
                val eq = it.indexOf('=')
                if (eq != -1) it.substring(0, eq) to it.substring(eq + 1) else it to ""
            }
            ?: emptyMap()

        val acont = tags["acont"]?.lowercase()
        val lang = tags["lang"]?.substringBefore('-')?.lowercase()
        val pref = preferredLanguage.substringBefore('-').lowercase()

        return when {
            // "descriptive" is audio narration for the visually impaired, never primary
            acont == "descriptive" -> 99
            // 1. Matches preferred language and is the original track
            lang == pref && (acont == null || acont == "original") -> 0
            // 2. Original audio track (e.g. US top chart podcast)
            acont == null || acont == "original" -> 1
            // 3. Dubbed in preferred language (e.g. English)
            lang == pref -> 2
            // 4. Any other language dub (e.g. Portuguese pt-BR, Spanish, etc.)
            else -> 10
        }
    }

    fun clearStreamCache() {
        streamCache.clear()
        videoStreamCache.clear()
    }

    /**
     * Multi-tier SimpMusic stream extraction:
     * Tier 0: Memory cache (0ms)
     * Tier 1: PipePipeExtractor (SimpMusic official engine + api.pipepipe.dev cipher)
     * Tier 2: BraveNewPipe Extractor (SmartTube engine)
     * Tier 3: Fast pre-warmed Invidious/Piped local=true proxy stream (~230ms)
     */
    suspend fun resolveStream(videoId: String): ResolvedStream = withContext(Dispatchers.IO) {
        initExtractors()

        getCachedStream(videoId)?.let { return@withContext it }

        // Tier 1: PipePipeExtractor (SimpMusic)
        try {
            YoutubeApiDecoder.setLocalDecoder(null)
            val url = "https://www.youtube.com/watch?v=$videoId"
            val streamInfo = PipePipeStreamInfo.getInfo(PipePipeServiceList.YouTube, url)
            val audioStreams = streamInfo.audioStreams
            if (!audioStreams.isNullOrEmpty()) {
                val sortedStreams = audioStreams.sortedWith(
                    compareBy<PipePipeAudioStream> { audioTrackRank(it.content, "en") }
                        .thenByDescending { it.format?.suffix?.equals("m4a", ignoreCase = true) == true }
                        .thenByDescending { it.averageBitrate }
                )
                for (selected in sortedStreams) {
                    val rawUrl = selected.content
                    val verifiedUrl = verifyPlayableUrl(rawUrl)
                    if (verifiedUrl != null) {
                        val resolved = ResolvedStream(
                            videoId = videoId,
                            streamUrl = verifiedUrl,
                            mimeType = selected.format?.mimeType ?: "audio/mp4",
                            bitrateKbps = selected.averageBitrate.takeIf { it > 0 } ?: 160,
                            codec = if (selected.format?.suffix == "m4a") "AAC" else "OPUS",
                            durationSeconds = streamInfo.duration.toInt().coerceAtLeast(0),
                            sourceTier = "PipePipeExtractor (SimpMusic Native)"
                        )
                        streamCache[videoId] = resolved
                        Log.i(TAG, "Tier 1 PipePipeExtractor resolved $videoId (${resolved.bitrateKbps}kbps, rank=${audioTrackRank(rawUrl, "en")})")
                        return@withContext resolved
                    }
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "Tier 1 PipePipeExtractor notice for $videoId: ${e.message}")
        }

        // Tier 2: BraveNewPipe Extractor
        try {
            val url = "https://www.youtube.com/watch?v=$videoId"
            val streamInfo = BraveStreamInfo.getInfo(BraveServiceList.YouTube, url)
            val audioStreams = streamInfo.audioStreams
            if (!audioStreams.isNullOrEmpty()) {
                val sortedStreams = audioStreams.sortedWith(
                    compareBy<BraveAudioStream> { audioTrackRank(it.content, "en") }
                        .thenByDescending { it.format?.suffix?.equals("m4a", ignoreCase = true) == true }
                        .thenByDescending { it.averageBitrate }
                )
                for (selected in sortedStreams) {
                    val verifiedUrl = verifyPlayableUrl(selected.content)
                    if (verifiedUrl != null) {
                        val resolved = ResolvedStream(
                            videoId = videoId,
                            streamUrl = verifiedUrl,
                            mimeType = selected.format?.mimeType ?: "audio/mp4",
                            bitrateKbps = selected.averageBitrate.takeIf { it > 0 } ?: 160,
                            codec = if (selected.format?.suffix == "m4a") "AAC" else "OPUS",
                            durationSeconds = streamInfo.duration.toInt().coerceAtLeast(0),
                            sourceTier = "NewPipeExtractor (SmartTube Native)"
                        )
                        streamCache[videoId] = resolved
                        Log.i(TAG, "Tier 2 BraveNewPipe resolved $videoId (${resolved.bitrateKbps}kbps, rank=${audioTrackRank(selected.content, "en")})")
                        return@withContext resolved
                    }
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "Tier 2 BraveNewPipe notice for $videoId: ${e.message}")
        }

        // Tier 3: Pre-warmed direct companion proxy (follows 302 redirect once so ExoPlayer starts in ~230ms)
        val proxyCandidates = listOf(
            "https://yt.chocolatemoo53.com/latest_version?id=$videoId&itag=140&local=true",
            "https://inv.nadeko.net/latest_version?id=$videoId&itag=140&local=true",
            "https://invidious.nerdvpn.de/latest_version?id=$videoId&itag=140&local=true"
        )
        for (candidate in proxyCandidates) {
            val finalUrl = verifyPlayableUrl(candidate)
            if (finalUrl != null) {
                val resolved = ResolvedStream(
                    videoId = videoId,
                    streamUrl = finalUrl,
                    mimeType = "audio/mp4",
                    bitrateKbps = 130,
                    codec = "AAC",
                    sourceTier = "Direct Edge Stream (Pre-Warmed)"
                )
                streamCache[videoId] = resolved
                Log.i(TAG, "Tier 3 Direct Edge Stream resolved $videoId -> $finalUrl")
                return@withContext resolved
            }
        }

        throw IllegalStateException("Unable to resolve playable audio stream for $videoId")
    }

    /**
     * Resolves a video stream for video playback mode.
     * Prioritizes high-definition 1080p/720p video paired with high-bitrate audio,
     * with graceful fallback to progressive muxed MP4 streams.
     */
    suspend fun resolveVideoStream(videoId: String): ResolvedVideoStream? = withContext(Dispatchers.IO) {
        initExtractors()
        videoStreamCache[videoId]?.let { return@withContext it }

        // Tier 1: PipePipeExtractor
        try {
            YoutubeApiDecoder.setLocalDecoder(null)
            val url = "https://www.youtube.com/watch?v=$videoId"
            val streamInfo = PipePipeStreamInfo.getInfo(PipePipeServiceList.YouTube, url)

            // Step 1: Check for high-definition video-only stream (1080p / 720p) + crisp audio stream
            val videoOnly = streamInfo.videoOnlyStreams
            val audioStreams = streamInfo.audioStreams
            if (!videoOnly.isNullOrEmpty() && !audioStreams.isNullOrEmpty()) {
                val hdVideo = videoOnly
                    .filter { it.height >= 720 && !it.content.isNullOrBlank() }
                    .maxByOrNull { it.height }
                val bestAudio = audioStreams
                    .filter { !it.content.isNullOrBlank() }
                    .sortedWith(
                        compareBy<PipePipeAudioStream> { audioTrackRank(it.content, "en") }
                            .thenByDescending { it.averageBitrate }
                    ).firstOrNull() ?: audioStreams.firstOrNull()

                if (hdVideo != null && bestAudio != null) {
                    val verifiedVideo = verifyPlayableUrl(hdVideo.content)
                    val verifiedAudio = verifyPlayableUrl(bestAudio.content)
                    if (verifiedVideo != null && verifiedAudio != null) {
                        val resolved = ResolvedVideoStream(
                            videoId = videoId,
                            streamUrl = verifiedVideo,
                            quality = "${hdVideo.height}p HD",
                            durationSeconds = streamInfo.duration.toInt().coerceAtLeast(0),
                            audioUrl = verifiedAudio
                        )
                        videoStreamCache[videoId] = resolved
                        Log.i(TAG, "Tier 1 PipePipe resolved HD video+audio for $videoId (${resolved.quality})")
                        return@withContext resolved
                    }
                }
            }

            // Step 2: Fallback to progressive muxed video streams (720p / 360p)
            val videoStreams = streamInfo.videoStreams
            if (!videoStreams.isNullOrEmpty()) {
                val sorted = videoStreams.filter { !it.content.isNullOrBlank() }.sortedByDescending { it.height }
                for (stream in sorted) {
                    val rawUrl = stream.content
                    val verifiedUrl = verifyPlayableUrl(rawUrl)
                    if (verifiedUrl != null) {
                        val resolved = ResolvedVideoStream(
                            videoId = videoId,
                            streamUrl = verifiedUrl,
                            quality = stream.resolution ?: "${stream.height}p",
                            durationSeconds = streamInfo.duration.toInt().coerceAtLeast(0),
                            audioUrl = null
                        )
                        videoStreamCache[videoId] = resolved
                        Log.i(TAG, "Tier 1 PipePipe resolved progressive video for $videoId (${resolved.quality})")
                        return@withContext resolved
                    }
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "Tier 1 PipePipe video error for $videoId: ${e.message}")
        }

        // Tier 2: BraveNewPipe
        try {
            val url = "https://www.youtube.com/watch?v=$videoId"
            val streamInfo = BraveStreamInfo.getInfo(BraveServiceList.YouTube, url)

            // Step 1: Check for high-definition video-only stream (1080p / 720p) + crisp audio stream
            val videoOnly = streamInfo.videoOnlyStreams
            val audioStreams = streamInfo.audioStreams
            if (!videoOnly.isNullOrEmpty() && !audioStreams.isNullOrEmpty()) {
                val hdVideo = videoOnly
                    .filter { it.height >= 720 && !it.content.isNullOrBlank() }
                    .maxByOrNull { it.height }
                val bestAudio = audioStreams
                    .filter { !it.content.isNullOrBlank() }
                    .sortedWith(
                        compareBy<BraveAudioStream> { audioTrackRank(it.content, "en") }
                            .thenByDescending { it.averageBitrate }
                    ).firstOrNull() ?: audioStreams.firstOrNull()

                if (hdVideo != null && bestAudio != null) {
                    val verifiedVideo = verifyPlayableUrl(hdVideo.content)
                    val verifiedAudio = verifyPlayableUrl(bestAudio.content)
                    if (verifiedVideo != null && verifiedAudio != null) {
                        val resolved = ResolvedVideoStream(
                            videoId = videoId,
                            streamUrl = verifiedVideo,
                            quality = "${hdVideo.height}p HD",
                            durationSeconds = streamInfo.duration.toInt().coerceAtLeast(0),
                            audioUrl = verifiedAudio
                        )
                        videoStreamCache[videoId] = resolved
                        Log.i(TAG, "Tier 2 BraveNewPipe resolved HD video+audio for $videoId (${resolved.quality})")
                        return@withContext resolved
                    }
                }
            }

            // Step 2: Fallback to progressive muxed video streams
            val videoStreams = streamInfo.videoStreams
            if (!videoStreams.isNullOrEmpty()) {
                val sorted = videoStreams.filter { !it.content.isNullOrBlank() }.sortedByDescending { it.height }
                for (stream in sorted) {
                    val rawUrl = stream.content
                    val verifiedUrl = verifyPlayableUrl(rawUrl)
                    if (verifiedUrl != null) {
                        val resolved = ResolvedVideoStream(
                            videoId = videoId,
                            streamUrl = verifiedUrl,
                            quality = stream.resolution ?: "${stream.height}p",
                            durationSeconds = streamInfo.duration.toInt().coerceAtLeast(0),
                            audioUrl = null
                        )
                        videoStreamCache[videoId] = resolved
                        Log.i(TAG, "Tier 2 BraveNewPipe resolved progressive video for $videoId (${resolved.quality})")
                        return@withContext resolved
                    }
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "Tier 2 BraveNewPipe video error for $videoId: ${e.message}")
        }

        null
    }

    /**
     * Resolves an authentic official music video for a track.
     * Searches YouTube Music and YouTube for verified official music videos / MVs.
     * Strictly filters out static audio tracks (e.g. YouTube Topic uploads) so cover art
     * is NEVER maximized as a fake video.
     * Returns null if no authentic official video stream is available.
     */
    private fun primaryArtistName(artist: String): String =
        artist
            .replace(Regex("(?i)\\s+(feat(?:uring)?|ft\\.?|with)\\s+.*$"), "")
            .trim()

    private fun normalizedVideoMatchText(value: String): String =
        value.lowercase()
            .replace(Regex("(?i)\\b(official|music|video|mv|vevo|topic|hd|4k)\\b"), " ")
            .replace(Regex("[^\\p{L}\\p{N}]+"), " ")
            .trim()

    private fun officialVideoMatchScore(candidateTitle: String, requestedTitle: String): Float {
        val wanted = normalizedVideoMatchText(requestedTitle).split(Regex("\\s+")).filter(String::isNotBlank).toSet()
        val actual = normalizedVideoMatchText(candidateTitle).split(Regex("\\s+")).filter(String::isNotBlank).toSet()
        if (wanted.isEmpty() || actual.isEmpty()) return 0f
        return wanted.intersect(actual).size.toFloat() / wanted.size
    }

    /** Reads YouTube's type from the possible watch-endpoint locations used by YTM search rows. */
    private fun JSONObject.optMusicVideoType(): String? =
        optJSONObject("watchEndpointMusicSupportedConfigs")
            ?.optJSONObject("watchEndpointMusicConfig")
            ?.optString("musicVideoType")
            ?.takeIf { it.startsWith("MUSIC_VIDEO_TYPE_") }

    private fun innerTubeMusicVideoType(item: JSONObject): String? {
        val navigationType = item.optJSONObject("navigationEndpoint")
            ?.optJSONObject("watchEndpoint")
            ?.optMusicVideoType()
        val overlayType = item.optJSONObject("overlay")
            ?.optJSONObject("musicItemThumbnailOverlayRenderer")
            ?.optJSONObject("content")
            ?.optJSONObject("musicPlayButtonRenderer")
            ?.optJSONObject("playNavigationEndpoint")
            ?.optMusicVideoType()
        val titleRunType = item.optJSONArray("flexColumns")
            ?.optJSONObject(0)
            ?.optJSONObject("musicResponsiveListItemFlexColumnRenderer")
            ?.optJSONObject("text")
            ?.optJSONArray("runs")
            ?.optJSONObject(0)
            ?.optJSONObject("navigationEndpoint")
            ?.optJSONObject("watchEndpoint")
            ?.optMusicVideoType()
        return navigationType ?: overlayType ?: titleRunType
    }

    private fun isOfficialInnerTubeVideoType(type: String?): Boolean =
        type.equals("MUSIC_VIDEO_TYPE_OMV", ignoreCase = true) ||
            type.equals("MUSIC_VIDEO_TYPE_OFFICIAL_SOURCE_MUSIC", ignoreCase = true)

    private fun hasOfficialVideoCue(title: String, artist: String): Boolean =
        Regex("\\b(official|vevo)\\b").containsMatchIn(title) ||
            Regex("\\b(mv|m/v)\\b").containsMatchIn(title) ||
            Regex("\\bmusic\\s+video\\b").containsMatchIn(title) ||
            Regex("\\b(vevo|official)\\b").containsMatchIn(artist)

    private fun isTrustedVideoDistributor(artist: String): Boolean = Regex(
        "\\b(netflix|vevo|sony music|universal music|warner records|republic records|atlantic records|hybe labels|smtown|jyp entertainment|yg entertainment|big hit music|bighit music|kq entertainment|starship entertainment)\\b"
    ).containsMatchIn(artist)

    private fun isOfficialVideoMatch(
        candidate: AuraTrack,
        requestedTitle: String,
        requestedArtist: String,
        currentVideoId: String
    ): Boolean {
        if (candidate.videoId.isBlank() || candidate.videoId == currentVideoId) return false

        val title = candidate.title.lowercase()
        val artist = candidate.artist.lowercase()

        val excludedTerms = listOf(
            "cover", "karaoke", "lyrics", "lyric video", "reaction", "instrumental", "audio", "live", "acoustic",
            "tribute", "fan made", "fanmade", "unofficial", "slowed", "sped up", "nightcore", "audio only", " - topic"
        )
        if (excludedTerms.any { title.contains(it) || artist.contains(it) }) return false

        val wantedTitleWords = normalizedVideoMatchText(requestedTitle)
            .split(Regex("\\s+")).filter(String::isNotBlank).toSet()
        val foundTitleWords = normalizedVideoMatchText(candidate.title)
            .split(Regex("\\s+")).filter(String::isNotBlank).toSet()
        if (wantedTitleWords.isEmpty() || foundTitleWords.isEmpty()) return false
        val titleMatch = wantedTitleWords.intersect(foundTitleWords).size.toFloat() / wantedTitleWords.size
        if (titleMatch < 0.8f) return false

        val wantedArtistWords = normalizedVideoMatchText(primaryArtistName(requestedArtist))
            .split(Regex("\\s+")).filter(String::isNotBlank).toSet()
        val foundArtistWords = normalizedVideoMatchText(candidate.artist)
            .split(Regex("\\s+")).filter(String::isNotBlank).toSet()
        val foundTitleArtistWords = normalizedVideoMatchText(candidate.title)
            .split(Regex("\\s+")).filter(String::isNotBlank).toSet()
        if (wantedArtistWords.isEmpty() || foundArtistWords.isEmpty()) return false
        val uploaderArtistMatch = wantedArtistWords.intersect(foundArtistWords).size.toFloat() / wantedArtistWords.size
        val titleArtistMatch = wantedArtistWords.intersect(foundTitleArtistWords).size.toFloat() / wantedArtistWords.size
        val hasVideoCue = hasOfficialVideoCue(title, artist)
        val trustedDistributor = isTrustedVideoDistributor(artist)

        // InnerTube's classification is useful evidence, but it is not enough by itself: unrelated
        // uploads can be classified OMV, while licensed studio uploads can appear as UGC.
        when (candidate.musicVideoType?.uppercase()) {
            "MUSIC_VIDEO_TYPE_ATV" -> return false
            "MUSIC_VIDEO_TYPE_UGC" -> return hasVideoCue && trustedDistributor &&
                titleMatch >= 0.8f && titleArtistMatch >= 0.6f
            "MUSIC_VIDEO_TYPE_OMV", "MUSIC_VIDEO_TYPE_OFFICIAL_SOURCE_MUSIC" ->
                return titleMatch >= 0.8f &&
                    (uploaderArtistMatch >= 0.6f || (trustedDistributor && titleArtistMatch >= 0.6f))
        }

        // Some search rows omit the type. In that case use explicit video wording and require
        // either the artist as uploader or a recognized distributor with the artist in the title.
        if (!hasVideoCue || titleMatch < 0.8f) return false
        return uploaderArtistMatch >= 0.6f || (trustedDistributor && titleArtistMatch >= 0.6f)
    }

    private fun officialVideoCandidateScore(candidate: AuraTrack, requestedTitle: String): Float =
        (if (isOfficialInnerTubeVideoType(candidate.musicVideoType)) 10f else 0f) +
            officialVideoMatchScore(candidate.title, requestedTitle) +
            // Views only break close matches; they never turn a rejected result into an eligible one.
            (candidate.viewCount?.takeIf { it > 0 }?.let { ln(it.toFloat() + 1f) / 100f } ?: 0f)

    suspend fun findOfficialMusicVideo(track: AuraTrack): AuraTrack? = withContext(Dispatchers.IO) {
        try {
            // If the current track is already explicitly an official music video (and not a Topic audio upload)
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
                val direct = resolveVideoStream(track.videoId)
                if (direct != null) {
                    return@withContext track
                }
            }

            // Clean artist and title for YouTube search
            val cleanArtist = primaryArtistName(track.artist)
            val cleanTitle = track.title
                .replace(Regex("(?i)\\(feat\\..*?\\)|\\[feat\\..*?\\]"), "")
                .replace(Regex("(?i)\\b(official|music|video|audio|lyrics?|visualizer|remastered|remaster|hd|hq|4k)\\b"), "")
                .substringBefore("(")
                .substringBefore("[")
                .trim()

            val titleForQuery = cleanTitle.ifBlank { track.title }
            val artistForQuery = cleanArtist.ifBlank { track.artist }
            val fallbackQuery = "$titleForQuery $artistForQuery official music video".trim()
            val queryVariants = listOf(
                "$titleForQuery $artistForQuery",
                "$artistForQuery $titleForQuery official music video",
                "$titleForQuery $artistForQuery official music video"
            ).map { it.trim() }.distinct()

            // Search InnerTube's video results with both natural and explicit queries. The YTM
            // client often returns the official result for the natural query but ranks it lower
            // when extra "official music video" terms are forced into the query.
            val candidatesById = linkedMapOf<String, AuraTrack>()
            queryVariants.map { searchQuery ->
                async { searchYouTubeMusic(searchQuery, filter = "videos", prefetchTopResult = false) }
            }.awaitAll().flatten().forEach { result ->
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
                .filter { isOfficialVideoMatch(it, titleForQuery, artistForQuery, track.videoId) }
                .maxByOrNull { officialVideoCandidateScore(it, titleForQuery) }

            // 2. Direct YouTube fallback search via NewPipe extractor if YouTube Music didn't find an MV
            if (candidate == null) {
                try {
                    initExtractors()
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
                        vidId.isNotBlank() && isOfficialVideoMatch(fallbackTrack, titleForQuery, artistForQuery, track.videoId)
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
                val candidateStream = resolveVideoStream(candidate.videoId)
                if (candidateStream != null) {
                    return@withContext candidate
                }
            }

            // NEVER fallback to track.videoId if it's an audio song, preventing static cover art playback in video player
            null
        } catch (e: Exception) {
            Log.w(TAG, "findOfficialMusicVideo notice: ${e.message}")
            null
        }
    }

    /**
     * Searches YouTube Music for live performances, concert footage, and official music videos for a track.
     */
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

    /**
     * Direct YouTube Music WEB_REMIX search (`https://music.youtube.com/youtubei/v1/search`)
     * with NewPipeExtractor fallback.
     */
    suspend fun searchYouTubeMusic(
        query: String,
        filter: String = "songs",
        prefetchTopResult: Boolean = true
    ): List<AuraTrack> =
        withContext(Dispatchers.IO) {
            if (query.isBlank()) return@withContext emptyList()
            val normFilter = filter.lowercase().trim()
            val effectiveQuery = when (normFilter) {
                "videos" -> if (query.contains("video", ignoreCase = true)) query else "$query music video"
                "playlists" -> if (query.contains("playlist", ignoreCase = true)) query else "$query playlist"
                "albums" -> if (query.contains("album", ignoreCase = true)) query else "$query album"
                else -> query
            }
            try {
                // Valid Base64 InnerTube params for returning playable Song & Video items
                val params = when (normFilter) {
                    "songs" -> "EgWKAQIIAWoKEAkQBRAKEAMQBA=="
                    "videos" -> "EgWKAQIQAWoKEAkQChAFEAMQBA=="
                    "playlists" -> "EgWKAQIIAWoKEAkQBRAKEAMQBA=="
                    "albums" -> "EgWKAQIYAWoKEAkQChAFEAMQBA=="
                    "artists" -> "EgWKAQIgAWoKEAkQChAFEAMQBA=="
                    else -> null // "all"
                }
                val payload = JSONObject().apply {
                    put("context", JSONObject().apply {
                        put("client", JSONObject().apply {
                            put("clientName", "WEB_REMIX")
                            put("clientVersion", "1.20250312.01.00")
                            put("hl", "en")
                            put("gl", "US")
                        })
                    })
                    put("query", effectiveQuery)
                    if (params != null) {
                        put("params", params)
                    }
                }

                val req = Request.Builder()
                    .url("https://music.youtube.com/youtubei/v1/search?prettyPrint=false")
                    .header("User-Agent", USER_AGENT)
                    .header("Origin", "https://music.youtube.com")
                    .header("Referer", "https://music.youtube.com/")
                    .post(payload.toString().toRequestBody("application/json".toMediaType()))
                    .build()

                val tracks = mutableListOf<AuraTrack>()
                httpClient.newCall(req).execute().use { resp ->
                    if (resp.isSuccessful) {
                        val bodyStr = resp.body?.string() ?: ""
                        parseYtmSearchTracks(JSONObject(bodyStr), tracks)
                    }
                }

                // If "videos" filter with strict param returned empty, retry with un-parameterized video query
                if (tracks.isEmpty() && normFilter != "all") {
                    val retryPayload = JSONObject().apply {
                        put("context", JSONObject().apply {
                            put("client", JSONObject().apply {
                                put("clientName", "WEB_REMIX")
                                put("clientVersion", "1.20250312.01.00")
                                put("hl", "en")
                                put("gl", "US")
                            })
                        })
                        put("query", effectiveQuery)
                    }
                    val retryReq = Request.Builder()
                        .url("https://music.youtube.com/youtubei/v1/search?prettyPrint=false")
                        .header("User-Agent", USER_AGENT)
                        .header("Origin", "https://music.youtube.com")
                        .header("Referer", "https://music.youtube.com/")
                        .post(retryPayload.toString().toRequestBody("application/json".toMediaType()))
                        .build()
                    httpClient.newCall(retryReq).execute().use { resp ->
                        if (resp.isSuccessful) {
                            val bodyStr = resp.body?.string() ?: ""
                            parseYtmSearchTracks(JSONObject(bodyStr), tracks)
                        }
                    }
                }

                if (tracks.isNotEmpty()) {
                    // Proactively pre-warm the top search result for instant 0ms playback when tapped!
                    if (prefetchTopResult) prefetchStream(tracks.first().videoId)
                    return@withContext tracks.distinctBy { it.videoId }
                }
            } catch (e: Exception) {
                Log.w(TAG, "WEB_REMIX search fallback to NewPipe: ${e.message}")
            }

            // Fallback to BraveNewPipe search
            try {
                initExtractors()
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
                if (prefetchTopResult) results.firstOrNull()?.let { prefetchStream(it.videoId) }
                results.distinctBy { it.videoId }
            } catch (e: Exception) {
                Log.e(TAG, "Search failed: ${e.message}")
                emptyList()
            }
        }

    /**
     * Fetches SimpMusic endless Radio / Automix queue via `youtubei/v1/next` (`RDAMVM{videoId}`).
     */
    suspend fun fetchRadioQueue(videoId: String): List<AuraTrack> = withContext(Dispatchers.IO) {
        if (videoId.isBlank()) return@withContext emptyList()
        try {
            val payload = JSONObject().apply {
                put("context", JSONObject().apply {
                    put("client", JSONObject().apply {
                        put("clientName", "WEB_REMIX")
                        put("clientVersion", "1.20250312.01.00")
                        put("hl", "en")
                        put("gl", "US")
                    })
                })
                put("videoId", videoId)
                put("playlistId", "RDAMVM$videoId")
                put("isAudioOnly", true)
            }

            val req = Request.Builder()
                .url("https://music.youtube.com/youtubei/v1/next?prettyPrint=false")
                .header("User-Agent", USER_AGENT)
                .header("Origin", "https://music.youtube.com")
                .post(payload.toString().toRequestBody("application/json".toMediaType()))
                .build()

            val queue = mutableListOf<AuraTrack>()
            httpClient.newCall(req).execute().use { resp ->
                if (resp.isSuccessful) {
                    val root = JSONObject(resp.body?.string() ?: "{}")
                    val tabs = root.optJSONObject("contents")
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
                            val renderer = playlistItems.optJSONObject(i)
                                ?.optJSONObject("playlistPanelVideoRenderer") ?: continue
                            val itemVidId = renderer.optString("videoId")
                            if (itemVidId.isBlank() || itemVidId == videoId) continue

                            val title = renderer.optJSONObject("title")
                                ?.optJSONArray("runs")?.optJSONObject(0)?.optString("text") ?: continue
                            val artist = renderer.optJSONObject("shortBylineText")
                                ?.optJSONArray("runs")?.optJSONObject(0)?.optString("text")
                                ?.replace(" - Topic", "") ?: "YouTube Artist"
                            val lengthText = renderer.optJSONObject("lengthText")
                                ?.optJSONArray("runs")?.optJSONObject(0)?.optString("text") ?: "3:30"
                            val thumbs = renderer.optJSONObject("thumbnail")?.optJSONArray("thumbnails")
                            val thumbUrl = thumbs?.optJSONObject(thumbs.length() - 1)?.optString("url")
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
                }
            }
            queue.firstOrNull()?.let { prefetchStream(it.videoId) }
            queue
        } catch (e: Exception) {
            Log.w(TAG, "Radio queue fetch warning: ${e.message}")
            emptyList()
        }
    }

    /** Fetches video owner and description fields from the YouTube NEXT response used by
     * SimpMusic for its now-playing information card. */
    suspend fun fetchVideoInfo(videoId: String): AuraVideoInfo? = withContext(Dispatchers.IO) {
        if (videoId.isBlank()) return@withContext null
        videoInfoCache[videoId]?.let { return@withContext it }
        runCatching {
            val payload = JSONObject().apply {
                put("context", JSONObject().apply {
                    put("client", JSONObject().apply {
                        // SimpMusic's getSongInfo uses WEB. WEB_REMIX can return a different
                        // page shape, which does not reliably include the secondary renderer.
                        put("clientName", "WEB")
                        put("clientVersion", "2.20250312.04.00")
                        put("hl", "en")
                        put("gl", "US")
                    })
                })
                put("videoId", videoId)
            }
            val request = Request.Builder()
                .url("https://music.youtube.com/youtubei/v1/next?prettyPrint=false")
                .header("User-Agent", USER_AGENT)
                .header("Origin", "https://music.youtube.com")
                .post(payload.toString().toRequestBody("application/json".toMediaType()))
                .build()

            httpClient.newCall(request).execute().use { response ->
                if (!response.isSuccessful) return@use null
                val root = JSONObject(response.body?.string().orEmpty())
                val contents = root.optJSONObject("contents")
                val results = contents
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
                // Keep compatibility with single-column and nested response variants as well.
                findRenderer(root, "videoSecondaryInfoRenderer")?.let { secondary = it }
                findRenderer(root, "videoPrimaryInfoRenderer")?.let { primary = it }
                if (secondary == null && primary == null) return@use null
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
                // An empty result usually means the request/response shape failed. Do not
                // poison the in-memory cache; a later request can retry after a transient miss.
                if (info.description.isNotBlank() || info.author.isNotBlank() || info.uploadDate.isNotBlank()) {
                    videoInfoCache[videoId] = info
                    info
                } else {
                    null
                }
            }
        }.getOrNull()
    }

    private fun findRenderer(value: Any?, rendererName: String): JSONObject? = when (value) {
        is JSONObject -> {
            value.optJSONObject(rendererName)
                ?: value.keys().asSequence().mapNotNull { key -> findRenderer(value.opt(key), rendererName) }.firstOrNull()
        }
        is JSONArray -> (0 until value.length()).asSequence()
            .mapNotNull { index -> findRenderer(value.opt(index), rendererName) }
            .firstOrNull()
        else -> null
    }

    /**
     * Fetches curated YouTube Music Charts & Moods for the Home screen.
     */
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

    /**
     * Directly calls YouTube Music InnerTube browse API with official mood/moment params
     * (`browseId = "FEmusic_home"`), fetching the official backdrop image, quick picks, and shelves.
     */
    suspend fun fetchYtmMoodBrowse(category: String): MoodBrowseResult = withContext(Dispatchers.IO) {
        val params = YtmMoodBrowseParams.PARAMS_MAP[category]
        val fallbackBg = YtmMoodBrowseParams.DEFAULT_BACKGROUNDS[category]
        if (params == null) {
            return@withContext MoodBrowseResult(fallbackBg, emptyList())
        }

        try {
            val payload = JSONObject().apply {
                put("context", JSONObject().apply {
                    put("client", JSONObject().apply {
                        put("clientName", "WEB_REMIX")
                        put("clientVersion", "1.20250312.01.00")
                        put("hl", "en")
                        put("gl", "US")
                    })
                })
                put("browseId", "FEmusic_home")
                put("params", params)
            }

            val req = Request.Builder()
                .url("https://music.youtube.com/youtubei/v1/browse?prettyPrint=false")
                .header("User-Agent", USER_AGENT)
                .header("Origin", "https://music.youtube.com")
                .header("Referer", "https://music.youtube.com/")
                .post(payload.toString().toRequestBody("application/json".toMediaType()))
                .build()

            httpClient.newCall(req).execute().use { resp ->
                if (resp.isSuccessful) {
                    val bodyStr = resp.body?.string() ?: ""
                    val root = JSONObject(bodyStr)
                    val bgThumbs = root.optJSONObject("background")
                        ?.optJSONObject("musicThumbnailRenderer")
                        ?.optJSONObject("thumbnail")
                        ?.optJSONArray("thumbnails")
                    // Always prefer curated dark night/sunset mood backgrounds over YTM's bright daytime images
                    val bgUrl = fallbackBg ?: bgThumbs?.let {
                        if (it.length() > 0) it.optJSONObject(it.length() - 1)?.optString("url") else null
                    }

                    val parsedTracks = mutableListOf<AuraTrack>()
                    val parsedAlbums = mutableListOf<AuraTrack>()
                    val parsedShelves = mutableListOf<YtmMoodShelf>()

                    val sections = root.optJSONObject("contents")
                        ?.optJSONObject("singleColumnBrowseResultsRenderer")
                        ?.optJSONArray("tabs")
                        ?.optJSONObject(0)
                        ?.optJSONObject("tabRenderer")
                        ?.optJSONObject("content")
                        ?.optJSONObject("sectionListRenderer")
                        ?.optJSONArray("contents")

                    if (sections != null) {
                        for (s in 0 until sections.length()) {
                            val sec = sections.optJSONObject(s) ?: continue
                            val shelf = sec.optJSONObject("musicCarouselShelfRenderer") ?: continue
                            val header = shelf.optJSONObject("header")?.optJSONObject("musicCarouselShelfBasicHeaderRenderer")
                            val shelfTitle = header?.optJSONObject("title")?.optJSONArray("runs")?.optJSONObject(0)?.optString("text")?.trim().orEmpty()
                            val shelfSubtitle = header?.optJSONObject("strapline")?.optJSONArray("runs")?.optJSONObject(0)?.optString("text")?.trim()
                            val numItemsPerColumn = shelf.optString("numItemsPerColumn").ifBlank {
                                header?.optString("numItemsPerColumn").orEmpty()
                            }
                            val is4RowGrid = numItemsPerColumn == "4"
                            val contents = shelf.optJSONArray("contents") ?: continue
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
                                    val thumbs = resItem.optJSONObject("thumbnail")
                                        ?.optJSONObject("musicThumbnailRenderer")
                                        ?.optJSONObject("thumbnail")
                                        ?.optJSONArray("thumbnails")
                                    val thumb = thumbs?.let {
                                        if (it.length() > 0) it.optJSONObject(it.length() - 1)?.optString("url") else null
                                    } ?: "https://img.youtube.com/vi/$vidId/hqdefault.jpg"

                                    val track = AuraTrack(
                                        id = "yt_$vidId",
                                        videoId = vidId,
                                        title = songTitle,
                                        artist = songArtist,
                                        album = songAlbum,
                                        artworkUrl = thumb,
                                        genre = category
                                    )
                                    parsedTracks.add(track)
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
                                    val subRuns = twoRow.optJSONObject("subtitle")?.optJSONArray("runs")
                                    var subText = "YouTube Music"
                                    if (subRuns != null) {
                                        val sBuilder = StringBuilder()
                                        for (r in 0 until subRuns.length()) {
                                            val t = subRuns.optJSONObject(r)?.optString("text").orEmpty()
                                            sBuilder.append(t)
                                        }
                                        if (sBuilder.isNotBlank()) subText = sBuilder.toString().trim()
                                    }
                                    val thumbs = twoRow.optJSONObject("thumbnailRenderer")
                                        ?.optJSONObject("musicThumbnailRenderer")
                                        ?.optJSONObject("thumbnail")
                                        ?.optJSONArray("thumbnails")
                                    val thumb = thumbs?.let {
                                        if (it.length() > 0) it.optJSONObject(it.length() - 1)?.optString("url") else null
                                    } ?: ""
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
                                            album = category,
                                            artworkUrl = thumb,
                                            genre = category,
                                            musicVideoType = if (isVideo) "MUSIC_VIDEO_TYPE_OMV" else null
                                        )
                                        parsedTracks.add(track)
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
                                                genre = category
                                            )
                                            parsedAlbums.add(albumTrack)
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
                                        } else if (browseId.startsWith("MPSP")) {
                                            val podcast = YtmCommunityPlaylist(
                                                id = browseId,
                                                title = cardTitle,
                                                author = subText,
                                                artworkUrl = thumb
                                            )
                                            shelfItems.add(
                                                YtmMoodItem(
                                                    id = "pod_$browseId",
                                                    title = cardTitle,
                                                    subtitle = subText,
                                                    artworkUrl = thumb,
                                                    type = YtmMoodItemType.PLAYLIST,
                                                    playlist = podcast
                                                )
                                            )
                                        } else {
                                            val playlist = YtmCommunityPlaylist(
                                                id = browseId,
                                                title = cardTitle,
                                                author = subText,
                                                artworkUrl = thumb
                                            )
                                            shelfItems.add(
                                                YtmMoodItem(
                                                    id = "misc_$browseId",
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
                            if (shelfItems.isNotEmpty()) {
                                parsedShelves.add(
                                    YtmMoodShelf(
                                        title = shelfTitle.ifBlank { if (is4RowGrid) "Quick picks" else "$category Highlights" },
                                        subtitle = shelfSubtitle,
                                        is4RowGrid = is4RowGrid,
                                        items = shelfItems
                                    )
                                )
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

                    MoodBrowseResult(
                        backgroundUrl = bgUrl,
                        tracks = finalTracks,
                        albums = parsedAlbums.distinctBy { it.id },
                        shelves = parsedShelves
                    )
                } else {
                    MoodBrowseResult(
                        backgroundUrl = fallbackBg,
                        tracks = searchYouTubeMusic("$category songs official audio", "songs").take(30)
                    )
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "fetchYtmMoodBrowse failed for $category: ${e.message}")
            MoodBrowseResult(
                backgroundUrl = fallbackBg,
                tracks = searchYouTubeMusic("$category songs official audio", "songs").take(30)
            )
        }
    }

    /**
     * Fetches YouTube Music Charts (`FEmusic_charts`) for the chosen country (or Global "ZZ").
     * Returns video charts, genres, top artists, and podcasts matching SimpMusic.
     */
    suspend fun fetchHomeChartData(countryCode: String = "US"): AuraChart = withContext(Dispatchers.IO) {
        val playlistSections = mutableListOf<AuraChartPlaylistSection>()
        var topArtistsList = mutableListOf<AuraChartArtistItem>()
        var podcastSection: AuraChartPodcastSection? = null

        try {
            val effectiveGl = if (countryCode.equals("ZZ", ignoreCase = true)) "US" else countryCode
            val payload = JSONObject().apply {
                put("context", JSONObject().apply {
                    put("client", JSONObject().apply {
                        put("clientName", "WEB_REMIX")
                        put("clientVersion", "1.20250312.01.00")
                        put("hl", "en")
                        put("gl", effectiveGl)
                    })
                })
                put("browseId", "FEmusic_charts")
                put("formData", JSONObject().apply {
                    put("selectedValues", JSONArray().apply { put(countryCode) })
                })
            }

            val req = Request.Builder()
                .url("https://music.youtube.com/youtubei/v1/browse?prettyPrint=false")
                .header("User-Agent", USER_AGENT)
                .header("Origin", "https://music.youtube.com")
                .header("Referer", "https://music.youtube.com/")
                .post(payload.toString().toRequestBody("application/json".toMediaType()))
                .build()

            httpClient.newCall(req).execute().use { resp ->
                if (resp.isSuccessful) {
                    val root = JSONObject(resp.body?.string() ?: "{}")
                    val tabs = root.optJSONObject("contents")
                        ?.optJSONObject("singleColumnBrowseResultsRenderer")
                        ?.optJSONArray("tabs")
                    val sectionContents = tabs?.optJSONObject(0)
                        ?.optJSONObject("tabRenderer")
                        ?.optJSONObject("content")
                        ?.optJSONObject("sectionListRenderer")
                        ?.optJSONArray("contents")

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
                                val artists = mutableListOf<AuraChartArtistItem>()
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

                                    val thumbArr = item.optJSONObject("thumbnail")
                                        ?.optJSONObject("musicThumbnailRenderer")
                                        ?.optJSONObject("thumbnail")
                                        ?.optJSONArray("thumbnails")
                                    val thumb = thumbArr?.optJSONObject(thumbArr.length() - 1)?.optString("url") ?: ""
                                    val artistId = item.optJSONObject("navigationEndpoint")
                                        ?.optJSONObject("browseEndpoint")
                                        ?.optString("browseId") ?: "artist_$j"

                                    if (name.isNotBlank()) {
                                        artists.add(AuraChartArtistItem(id = artistId, rank = "${j + 1}", name = name, subscribers = subs, avatarUrl = thumb))
                                    }
                                }
                                if (artists.isNotEmpty()) {
                                    topArtistsList = artists
                                }
                            } else if (pageType == "MUSIC_PAGE_TYPE_PODCAST_SHOW_DETAIL_PAGE" || shelfTitle.contains("podcast", ignoreCase = true)) {
                                val shows = mutableListOf<AuraChartArtistItem>()
                                for (j in 0 until contents.length()) {
                                    val item = contents.optJSONObject(j)?.optJSONObject("musicResponsiveListItemRenderer") ?: continue
                                    val runs0 = item.optJSONArray("flexColumns")?.optJSONObject(0)
                                        ?.optJSONObject("musicResponsiveListItemFlexColumnRenderer")
                                        ?.optJSONObject("text")?.optJSONArray("runs")
                                    val name = runs0?.optJSONObject(0)?.optString("text") ?: continue

                                    val runs1 = item.optJSONArray("flexColumns")?.optJSONObject(1)
                                        ?.optJSONObject("musicResponsiveListItemFlexColumnRenderer")
                                        ?.optJSONObject("text")?.optJSONArray("runs")
                                    val author = runs1?.optJSONObject(0)?.optString("text") ?: ""

                                    val thumbObj = item.optJSONObject("thumbnail")
                                    val thumbArr = thumbObj?.optJSONObject("musicThumbnailRenderer")
                                        ?.optJSONObject("thumbnail")
                                        ?.optJSONArray("thumbnails")
                                        ?: thumbObj?.optJSONObject("croppedSquareThumbnailRenderer")
                                            ?.optJSONObject("thumbnail")
                                            ?.optJSONArray("thumbnails")
                                    val thumb = thumbArr?.optJSONObject(thumbArr.length() - 1)?.optString("url") ?: ""
                                    val podcastId = item.optJSONObject("navigationEndpoint")
                                        ?.optJSONObject("browseEndpoint")
                                        ?.optString("browseId")
                                        ?: runs0?.optJSONObject(0)?.optJSONObject("navigationEndpoint")
                                            ?.optJSONObject("browseEndpoint")
                                            ?.optString("browseId")
                                        ?: "podcast_$j"

                                    if (name.isNotBlank()) {
                                        shows.add(AuraChartArtistItem(id = podcastId, rank = "${j + 1}", name = name, subscribers = author, avatarUrl = thumb))
                                    }
                                }
                                if (shows.isNotEmpty()) {
                                    podcastSection = AuraChartPodcastSection(title = shelfTitle.ifBlank { "Weekly top podcast shows" }, shows = shows)
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
                                    val subRuns = item.optJSONObject("subtitle")?.optJSONArray("runs")
                                    val author = if (subRuns != null) {
                                        (0 until subRuns.length()).joinToString(" ") { subRuns.optJSONObject(it)?.optString("text") ?: "" }.trim()
                                    } else ""

                                    val thumbArr = item.optJSONObject("thumbnailRenderer")
                                        ?.optJSONObject("musicThumbnailRenderer")
                                        ?.optJSONObject("thumbnail")
                                        ?.optJSONArray("thumbnails")
                                    val thumb = thumbArr?.optJSONObject(thumbArr.length() - 1)?.optString("url") ?: ""

                                    playlists.add(YtmCommunityPlaylist(id = plId, title = plTitle, author = author, trackCount = "", artworkUrl = thumb))
                                }
                                if (playlists.isNotEmpty()) {
                                    playlistSections.add(AuraChartPlaylistSection(title = shelfTitle, items = playlists))
                                }
                            }
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

    /**
     * Resolves the YouTube Music album browseId (`MPREb_...`) for an album title and artist.
     */
    suspend fun findAlbumBrowseId(albumName: String, artistName: String): String? = withContext(Dispatchers.IO) {
        if (albumName.isBlank()) return@withContext null
        try {
            val query = "$albumName $artistName".trim()
            val payload = JSONObject().apply {
                put("context", JSONObject().apply {
                    put("client", JSONObject().apply {
                        put("clientName", "WEB_REMIX")
                        put("clientVersion", "1.20250312.01.00")
                        put("hl", "en")
                        put("gl", "US")
                    })
                })
                put("query", query)
                put("params", "EgWKAQIYAWoKEAkQChAFEAMQBA==")
            }
            val req = Request.Builder()
                .url("https://music.youtube.com/youtubei/v1/search?prettyPrint=false")
                .header("User-Agent", USER_AGENT)
                .header("Origin", "https://music.youtube.com")
                .header("Referer", "https://music.youtube.com/")
                .post(payload.toString().toRequestBody("application/json".toMediaType()))
                .build()

            httpClient.newCall(req).execute().use { resp ->
                if (resp.isSuccessful) {
                    val root = JSONObject(resp.body?.string() ?: "{}")
                    val tabs = root.optJSONObject("contents")
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
                }
            }
        } catch (e: Exception) {
            Log.w("SimpMusicExtractor", "Error searching album browseId for $albumName: ${e.message}")
        }
        null
    }

    /**
     * Fetches live Global Top Artists from YouTube Music Charts (`FEmusic_charts`).
     */
    suspend fun fetchTopArtists(): List<YtmTopArtist> = withContext(Dispatchers.IO) {
        val liveChart = fetchHomeChartData("US")
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

    /**
     * Fetches public playlists uploaded by users on YouTube Music (`EgWKAQIQA2oKEAkQBRAKEAMQBA%3D%3D`).
     */
    suspend fun fetchCommunityPlaylists(query: String = "Top Hits 2025"): List<YtmCommunityPlaylist> = withContext(Dispatchers.IO) {
        try {
            val payload = JSONObject().apply {
                put("context", JSONObject().apply {
                    put("client", JSONObject().apply {
                        put("clientName", "WEB_REMIX")
                        put("clientVersion", "1.20250312.01.00")
                        put("hl", "en")
                        put("gl", "US")
                    })
                })
                put("query", query)
                put("params", "EgeKAQQoAEABahIQBBAKEAUQDhADEAkQFRAQEBE=") // Valid Base64 Community Playlists filter
            }

            val req = Request.Builder()
                .url("https://music.youtube.com/youtubei/v1/search?prettyPrint=false")
                .header("User-Agent", USER_AGENT)
                .header("Origin", "https://music.youtube.com")
                .header("Referer", "https://music.youtube.com/")
                .post(payload.toString().toRequestBody("application/json".toMediaType()))
                .build()

            val playlists = mutableListOf<YtmCommunityPlaylist>()
            httpClient.newCall(req).execute().use { resp ->
                if (resp.isSuccessful) {
                    val root = JSONObject(resp.body?.string() ?: "{}")
                    val tabs = root.optJSONObject("contents")
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

                            val thumbArr = item.optJSONObject("thumbnail")
                                ?.optJSONObject("musicThumbnailRenderer")
                                ?.optJSONObject("thumbnail")
                                ?.optJSONArray("thumbnails")
                            val thumb = thumbArr?.optJSONObject(thumbArr.length() - 1)?.optString("url") ?: ""

                            val id = item.optJSONObject("navigationEndpoint")
                                ?.optJSONObject("browseEndpoint")
                                ?.optString("browseId")
                                ?: item.optJSONArray("flexColumns")?.optJSONObject(0)
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

    /**
     * Resolves tracks from any user-uploaded or public YouTube Music playlist.
     */
    suspend fun fetchPlaylistTracks(browseId: String): List<AuraTrack> = withContext(Dispatchers.IO) {
        try {
            val actualBrowseId = if (browseId.startsWith("VL") || browseId.startsWith("FEmusic_")) browseId else "VL$browseId"
            val payload = JSONObject().apply {
                put("context", JSONObject().apply {
                    put("client", JSONObject().apply {
                        put("clientName", "WEB_REMIX")
                        put("clientVersion", "1.20250312.01.00")
                        put("hl", "en")
                        put("gl", "US")
                    })
                })
                put("browseId", actualBrowseId)
            }

            val req = Request.Builder()
                .url("https://music.youtube.com/youtubei/v1/browse?prettyPrint=false")
                .header("User-Agent", USER_AGENT)
                .header("Origin", "https://music.youtube.com")
                .header("Referer", "https://music.youtube.com/")
                .post(payload.toString().toRequestBody("application/json".toMediaType()))
                .build()

            val tracks = mutableListOf<AuraTrack>()
            httpClient.newCall(req).execute().use { resp ->
                if (resp.isSuccessful) {
                    val root = JSONObject(resp.body?.string() ?: "{}")
                    val contents = root.optJSONObject("contents")
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

                            val thumbArr = item.optJSONObject("thumbnail")
                                ?.optJSONObject("musicThumbnailRenderer")
                                ?.optJSONObject("thumbnail")
                                ?.optJSONArray("thumbnails")
                            val thumb = thumbArr?.optJSONObject(thumbArr.length() - 1)?.optString("url")
                                ?: "https://img.youtube.com/vi/$vidId/hqdefault.jpg"

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
                }
            }
            if (tracks.isNotEmpty()) return@withContext tracks
        } catch (e: Exception) {
            Log.w(TAG, "Error fetching playlist tracks: ${e.message}")
        }
        emptyList()
    }

    /**
     * Fetches detailed playlist, album, single, EP, or podcast show overview with banner artwork, creator,
     * total views/plays, description, and ranked track list / episode items.
     */
    suspend fun fetchPlaylistDetail(playlist: YtmCommunityPlaylist): PlaylistDetail = withContext(Dispatchers.IO) {
        val playCountRegex = Regex("""([,\d]+(?:\.\d+)?\s*[KkMmBb]?\s*(?:plays|views))""", RegexOption.IGNORE_CASE)
        val rawId = playlist.id.removePrefix("album_").removePrefix("pl_").removePrefix("track_").removePrefix("yt_album_").removePrefix("pod_").trim()
        val isPodcastShow = rawId.startsWith("MPSP") || rawId.startsWith("MP_") || rawId.contains("podcast", ignoreCase = true)
        try {
            val actualBrowseId = when {
                isPodcastShow -> rawId
                rawId.startsWith("VL") || rawId.startsWith("MPREb_") || rawId.startsWith("FEmusic_") -> rawId
                rawId.startsWith("OLAK5uy_") || rawId.startsWith("PL") || rawId.startsWith("RD") -> "VL$rawId"
                else -> if (rawId.startsWith("VL")) rawId else "VL$rawId"
            }
            val payload = JSONObject().apply {
                put("context", JSONObject().apply {
                    put("client", JSONObject().apply {
                        put("clientName", "WEB_REMIX")
                        put("clientVersion", "1.20250312.01.00")
                        put("hl", "en")
                        put("gl", "US")
                    })
                })
                put("browseId", actualBrowseId)
            }

            val req = Request.Builder()
                .url("https://music.youtube.com/youtubei/v1/browse?prettyPrint=false")
                .header("User-Agent", USER_AGENT)
                .header("Origin", "https://music.youtube.com")
                .header("Referer", "https://music.youtube.com/")
                .post(payload.toString().toRequestBody("application/json".toMediaType()))
                .build()

            httpClient.newCall(req).execute().use { resp ->
                if (resp.isSuccessful) {
                    val root = JSONObject(resp.body?.string() ?: "{}")
                    val contentsObj = root.optJSONObject("contents")
                    val twoCol = contentsObj?.optJSONObject("twoColumnBrowseResultsRenderer")
                    val singleCol = contentsObj?.optJSONObject("singleColumnBrowseResultsRenderer")

                    var bannerUrl = playlist.artworkUrl
                    var title = playlist.title
                    var authorAndMeta = playlist.author
                    var totalViews = ""
                    var description = ""
                    var authorAvatarUrl = ""

                    // 1. Extract header renderer (tabs[0] section 0, or root header)
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
                        if (authorThumbArr != null && authorThumbArr.length() > 0) {
                            authorAvatarUrl = authorThumbArr.optJSONObject(authorThumbArr.length() - 1)?.optString("url") ?: ""
                        }

                        val descRuns = headerRenderer.optJSONObject("description")
                            ?.optJSONObject("musicDescriptionShelfRenderer")
                            ?.optJSONObject("description")
                            ?.optJSONArray("runs")
                        if (descRuns != null) {
                            val sb = StringBuilder()
                            for (d in 0 until descRuns.length()) {
                                sb.append(descRuns.optJSONObject(d)?.optString("text") ?: "")
                            }
                            description = sb.toString().trim()
                        }

                        val metaParts = mutableListOf<String>()
                        if (strapline.isNotEmpty()) {
                            metaParts.add(strapline)
                        }

                        val subRuns = headerRenderer.optJSONObject("subtitle")?.optJSONArray("runs")
                        if (subRuns != null) {
                            for (s in 0 until subRuns.length()) {
                                val t = subRuns.optJSONObject(s)?.optString("text")?.trim() ?: ""
                                if (t.isNotEmpty() && t != "•") {
                                    metaParts.add(t)
                                    val pv = playCountRegex.find(t)?.value
                                    if (!pv.isNullOrBlank() && totalViews.isBlank()) {
                                        totalViews = pv
                                    }
                                }
                            }
                        }

                        val secondSub = headerRenderer.optJSONObject("secondSubtitle")?.optJSONArray("runs")
                        if (secondSub != null) {
                            for (s in 0 until secondSub.length()) {
                                val t = secondSub.optJSONObject(s)?.optString("text")?.trim() ?: ""
                                if (t.isNotEmpty() && t != "•") {
                                    metaParts.add(t)
                                    val pv = playCountRegex.find(t)?.value
                                    if (!pv.isNullOrBlank() && totalViews.isBlank()) {
                                        totalViews = pv
                                    }
                                }
                            }
                        }

                        if (metaParts.isNotEmpty()) {
                            authorAndMeta = metaParts.distinct().joinToString(" • ")
                        }

                        val hThumbObj = headerRenderer.optJSONObject("thumbnail")
                        val thumbArr = hThumbObj?.optJSONObject("musicThumbnailRenderer")
                            ?.optJSONObject("thumbnail")
                            ?.optJSONArray("thumbnails")
                            ?: hThumbObj?.optJSONObject("croppedSquareThumbnailRenderer")
                                ?.optJSONObject("thumbnail")
                                ?.optJSONArray("thumbnails")
                        if (thumbArr != null && thumbArr.length() > 0) {
                            bannerUrl = thumbArr.optJSONObject(thumbArr.length() - 1)?.optString("url") ?: bannerUrl
                        }
                    }

                    // 2. Find tracks shelf (either musicShelfRenderer or musicPlaylistShelfRenderer)
                    var items: JSONArray? = null
                    var hostShelf: JSONObject? = null
                    val secContents = twoCol?.optJSONObject("secondaryContents")
                        ?.optJSONObject("sectionListRenderer")
                        ?.optJSONArray("contents")

                    if (secContents != null) {
                        for (s in 0 until secContents.length()) {
                            val sec = secContents.optJSONObject(s) ?: continue
                            val shelf = sec.optJSONObject("musicShelfRenderer")
                                ?: sec.optJSONObject("musicPlaylistShelfRenderer")
                            if (shelf != null) {
                                hostShelf = shelf
                                items = shelf.optJSONArray("contents")
                                if (items != null && items.length() > 0) break
                            }
                        }
                    }

                    if (items == null && tab0Contents != null) {
                        for (s in 0 until tab0Contents.length()) {
                            val sec = tab0Contents.optJSONObject(s) ?: continue
                            val shelf = sec.optJSONObject("musicShelfRenderer")
                                ?: sec.optJSONObject("musicPlaylistShelfRenderer")
                            if (shelf != null) {
                                hostShelf = shelf
                                items = shelf.optJSONArray("contents")
                                if (items != null && items.length() > 0) break
                            }
                        }
                    }

                    val playlistTracks = mutableListOf<PlaylistTrackItem>()
                    var sumPlaysMillion = 0.0
                    var hasPlayCounts = false
                    var hasMultiRowItems = false

                    if (items != null) {
                        for (i in 0 until items.length()) {
                            val rawItem = items.optJSONObject(i) ?: continue
                            val multiRow = rawItem.optJSONObject("musicMultiRowListItemRenderer")
                            val item = rawItem.optJSONObject("musicResponsiveListItemRenderer")

                            if (multiRow != null) {
                                hasMultiRowItems = true
                                val itemTitle = multiRow.optJSONObject("title")?.optJSONArray("runs")?.optJSONObject(0)?.optString("text") ?: continue
                                var vidId = multiRow.optJSONObject("onTap")?.optJSONObject("watchEndpoint")?.optString("videoId") ?: ""
                                if (vidId.isBlank()) {
                                    vidId = multiRow.optJSONObject("navigationEndpoint")?.optJSONObject("watchEndpoint")?.optString("videoId") ?: ""
                                }
                                if (vidId.isBlank()) continue

                                val thumbObj = multiRow.optJSONObject("thumbnail")
                                val thumbArr = thumbObj?.optJSONObject("musicThumbnailRenderer")
                                    ?.optJSONObject("thumbnail")
                                    ?.optJSONArray("thumbnails")
                                    ?: thumbObj?.optJSONObject("croppedSquareThumbnailRenderer")
                                        ?.optJSONObject("thumbnail")
                                        ?.optJSONArray("thumbnails")
                                val thumb = thumbArr?.optJSONObject(thumbArr.length() - 1)?.optString("url")
                                    ?: "https://img.youtube.com/vi/$vidId/hqdefault.jpg"

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
                                        if (t.isNotEmpty()) { durText = t; break }
                                    }
                                }
                                if (durText.isNotEmpty()) {
                                    subParts.add(durText)
                                }

                                val epDescRuns = multiRow.optJSONObject("description")?.optJSONArray("runs")
                                val epDesc = if (epDescRuns != null) {
                                    val sb = StringBuilder()
                                    for (d in 0 until epDescRuns.length()) {
                                        sb.append(epDescRuns.optJSONObject(d)?.optString("text") ?: "")
                                    }
                                    sb.toString().trim()
                                } else ""

                                playlistTracks.add(
                                    PlaylistTrackItem(
                                        rank = playlistTracks.size + 1,
                                        track = AuraTrack(
                                            id = "yt_$vidId",
                                            videoId = vidId,
                                            title = itemTitle,
                                            artist = playlist.author.ifBlank { title },
                                            album = title,
                                            durationSeconds = parseDurationText(durText),
                                            artworkUrl = thumb
                                        ),
                                        viewCount = subParts.distinct().joinToString(" • ").ifBlank { "Podcast Episode" },
                                        description = epDesc
                                    )
                                )
                            } else if (item != null) {
                                val flexCols = item.optJSONArray("flexColumns") ?: continue
                                val itemTitle = flexCols.optJSONObject(0)
                                    ?.optJSONObject("musicResponsiveListItemFlexColumnRenderer")
                                    ?.optJSONObject("text")
                                    ?.optJSONArray("runs")
                                    ?.optJSONObject(0)
                                    ?.optString("text") ?: continue

                                // Artist name
                                var artist = ""
                                if (flexCols.length() > 1) {
                                    val artistRuns = flexCols.optJSONObject(1)
                                        ?.optJSONObject("musicResponsiveListItemFlexColumnRenderer")
                                        ?.optJSONObject("text")
                                        ?.optJSONArray("runs")
                                    if (artistRuns != null) {
                                        val parts = mutableListOf<String>()
                                        for (a in 0 until artistRuns.length()) {
                                            val t = artistRuns.optJSONObject(a)?.optString("text")?.trim() ?: ""
                                            if (t.isNotEmpty() && t != "•") parts.add(t)
                                        }
                                        artist = parts.joinToString(" ")
                                    }
                                }
                                if (artist.isBlank()) {
                                    artist = playlist.author.ifBlank { "Artist" }
                                }

                                // View count / play count per song
                                var songViews = ""
                                for (c in 1 until flexCols.length()) {
                                    val colRuns = flexCols.optJSONObject(c)
                                        ?.optJSONObject("musicResponsiveListItemFlexColumnRenderer")
                                        ?.optJSONObject("text")
                                        ?.optJSONArray("runs")
                                    if (colRuns != null) {
                                        for (r in 0 until colRuns.length()) {
                                            val txt = colRuns.optJSONObject(r)?.optString("text")?.trim() ?: ""
                                            val pv = playCountRegex.find(txt)?.value
                                            if (!pv.isNullOrBlank()) {
                                                songViews = pv
                                                hasPlayCounts = true
                                                break
                                            }
                                        }
                                    }
                                    if (songViews.isNotEmpty()) break
                                }

                                val fixedCols = item.optJSONArray("fixedColumns")
                                val durationText = fixedCols?.optJSONObject(0)
                                    ?.optJSONObject("musicResponsiveListItemFixedColumnRenderer")
                                    ?.optJSONObject("text")
                                    ?.optJSONArray("runs")
                                    ?.optJSONObject(0)
                                    ?.optString("text") ?: ""

                                // VideoId from multiple potential descriptors
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
                                    vidId = item.optJSONObject("navigationEndpoint")
                                        ?.optJSONObject("watchEndpoint")
                                        ?.optString("videoId") ?: ""
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
                                if (vidId.isBlank()) continue

                                val thumbObj = item.optJSONObject("thumbnail")
                                val thumbArr = thumbObj?.optJSONObject("musicThumbnailRenderer")
                                    ?.optJSONObject("thumbnail")
                                    ?.optJSONArray("thumbnails")
                                    ?: thumbObj?.optJSONObject("croppedSquareThumbnailRenderer")
                                        ?.optJSONObject("thumbnail")
                                        ?.optJSONArray("thumbnails")
                                val thumb = thumbArr?.optJSONObject(thumbArr.length() - 1)?.optString("url")
                                    ?: "https://img.youtube.com/vi/$vidId/hqdefault.jpg"

                                // Sum numeric plays
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
                                        sumPlaysMillion += num * mult
                                    }
                                }

                                val finalTrackViews = if (songViews.isNotBlank()) {
                                    songViews
                                } else if (durationText.isNotBlank()) {
                                    durationText
                                } else {
                                    "Popular Track"
                                }

                                playlistTracks.add(
                                    PlaylistTrackItem(
                                        rank = playlistTracks.size + 1,
                                        track = AuraTrack(
                                            id = "yt_$vidId",
                                            videoId = vidId,
                                            title = itemTitle,
                                            artist = artist.replace(" - Topic", "").trim(),
                                            album = title,
                                            durationSeconds = parseDurationText(durationText),
                                            artworkUrl = thumb
                                        ),
                                        viewCount = finalTrackViews
                                    )
                                )
                            }
                        }
                    }

                    // Paginate continuations for podcasts and extended playlists
                    var continuationToken = hostShelf?.optJSONArray("continuations")
                        ?.optJSONObject(0)
                        ?.optJSONObject("nextContinuationData")
                        ?.optString("continuation")
                    var continuationPages = 0

                    while (!continuationToken.isNullOrBlank() && continuationPages < 5 && playlistTracks.size < 600) {
                        continuationPages++
                        try {
                            val contPayload = JSONObject().apply {
                                put("context", JSONObject().apply {
                                    put("client", JSONObject().apply {
                                        put("clientName", "WEB_REMIX")
                                        put("clientVersion", "1.20250312.01.00")
                                        put("hl", "en")
                                        put("gl", "US")
                                    })
                                })
                                put("continuation", continuationToken)
                            }
                            val contReq = Request.Builder()
                                .url("https://music.youtube.com/youtubei/v1/browse?prettyPrint=false")
                                .header("User-Agent", USER_AGENT)
                                .header("Origin", "https://music.youtube.com")
                                .header("Referer", "https://music.youtube.com/")
                                .post(contPayload.toString().toRequestBody("application/json".toMediaType()))
                                .build()

                            var nextToken: String? = null
                            httpClient.newCall(contReq).execute().use { contResp ->
                                if (contResp.isSuccessful) {
                                    val contRoot = JSONObject(contResp.body?.string() ?: "{}")
                                    val contShelf = contRoot.optJSONObject("continuationContents")?.optJSONObject("musicShelfContinuation")
                                        ?: contRoot.optJSONObject("continuationContents")?.optJSONObject("musicPlaylistShelfContinuation")
                                    val contItems = contShelf?.optJSONArray("contents")
                                    if (contItems != null) {
                                        for (ci in 0 until contItems.length()) {
                                            val rawItem = contItems.optJSONObject(ci) ?: continue
                                            val multiRow = rawItem.optJSONObject("musicMultiRowListItemRenderer")
                                            if (multiRow != null) {
                                                val itemTitle = multiRow.optJSONObject("title")?.optJSONArray("runs")?.optJSONObject(0)?.optString("text") ?: continue
                                                var vidId = multiRow.optJSONObject("onTap")?.optJSONObject("watchEndpoint")?.optString("videoId") ?: ""
                                                if (vidId.isBlank()) {
                                                    vidId = multiRow.optJSONObject("navigationEndpoint")?.optJSONObject("watchEndpoint")?.optString("videoId") ?: ""
                                                }
                                                if (vidId.isBlank()) continue

                                                val thumbObj = multiRow.optJSONObject("thumbnail")
                                                val thumbArr = thumbObj?.optJSONObject("musicThumbnailRenderer")
                                                    ?.optJSONObject("thumbnail")
                                                    ?.optJSONArray("thumbnails")
                                                    ?: thumbObj?.optJSONObject("croppedSquareThumbnailRenderer")
                                                        ?.optJSONObject("thumbnail")
                                                        ?.optJSONArray("thumbnails")
                                                val thumb = thumbArr?.optJSONObject(thumbArr.length() - 1)?.optString("url")
                                                    ?: "https://img.youtube.com/vi/$vidId/hqdefault.jpg"

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
                                                        if (t.isNotEmpty()) { durText = t; break }
                                                    }
                                                }
                                                if (durText.isNotEmpty()) subParts.add(durText)

                                                val epDescRuns = multiRow.optJSONObject("description")?.optJSONArray("runs")
                                                val epDesc = if (epDescRuns != null) {
                                                    val sb = StringBuilder()
                                                    for (d in 0 until epDescRuns.length()) {
                                                        sb.append(epDescRuns.optJSONObject(d)?.optString("text") ?: "")
                                                    }
                                                    sb.toString().trim()
                                                } else ""

                                                playlistTracks.add(
                                                    PlaylistTrackItem(
                                                        rank = playlistTracks.size + 1,
                                                        track = AuraTrack(
                                                            id = "yt_$vidId",
                                                            videoId = vidId,
                                                            title = itemTitle,
                                                            artist = playlist.author.ifBlank { title },
                                                            album = title,
                                                            durationSeconds = parseDurationText(durText),
                                                            artworkUrl = thumb,
                                                            isPodcast = true
                                                        ),
                                                        viewCount = subParts.distinct().joinToString(" • ").ifBlank { "Podcast Episode" },
                                                        description = epDesc
                                                    )
                                                )
                                            }
                                        }
                                    }
                                    nextToken = contShelf?.optJSONArray("continuations")
                                        ?.optJSONObject(0)
                                        ?.optJSONObject("nextContinuationData")
                                        ?.optString("continuation")
                                }
                            }
                            continuationToken = nextToken
                        } catch (e: Exception) {
                            Log.w(TAG, "Error fetching continuation: ${e.message}")
                            break
                        }
                    }

                    if (totalViews.isBlank() && hasPlayCounts && sumPlaysMillion > 0) {
                        totalViews = when {
                            sumPlaysMillion >= 1000.0 -> String.format(java.util.Locale.US, "%.1fB total plays", sumPlaysMillion / 1000.0)
                            sumPlaysMillion >= 1.0 -> String.format(java.util.Locale.US, "%.1fM total plays", sumPlaysMillion)
                            else -> "${(sumPlaysMillion * 1000).toInt()}K total plays"
                        }
                    }

                    if (playlistTracks.isNotEmpty()) {
                        val finalIsPodcast = isPodcastShow || hasMultiRowItems
                        val finalTracks = if (finalIsPodcast) {
                            playlistTracks.map { it.copy(track = it.track.copy(isPodcast = true)) }
                        } else playlistTracks

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
            }
        } catch (e: Exception) {
            Log.w(TAG, "Error fetching playlist detail browse: ${e.message}")
        }

        val fallbackTracks = fetchPlaylistTracks(playlist.id)
        PlaylistDetail(
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

    /**
     * Searches YouTube Music specifically for matching artist channels.
     * Uses WEB_REMIX with artist filter params `EgWKAQIgAWoKEAkQChAFEAMQBA%3D%3D`.
     * Inspects both `musicCardShelfRenderer` (top hero artist) and `musicShelfRenderer` / `itemSectionRenderer`.
     */
    suspend fun searchArtists(artistName: String): List<YtmTopArtist> = withContext(Dispatchers.IO) {
        if (artistName.isBlank()) return@withContext emptyList()
        val artists = mutableListOf<YtmTopArtist>()
        try {
            val payload = JSONObject().apply {
                put("context", JSONObject().apply {
                    put("client", JSONObject().apply {
                        put("clientName", "WEB_REMIX")
                        put("clientVersion", "1.20250312.01.00")
                        put("hl", "en")
                        put("gl", "US")
                    })
                })
                put("query", artistName)
                put("params", "EgWKAQIgAWoKEAkQChAFEAMQBA%3D%3D")
            }

            val req = Request.Builder()
                .url("https://music.youtube.com/youtubei/v1/search?prettyPrint=false")
                .header("User-Agent", USER_AGENT)
                .header("Origin", "https://music.youtube.com")
                .header("Referer", "https://music.youtube.com/")
                .post(payload.toString().toRequestBody("application/json".toMediaType()))
                .build()

            httpClient.newCall(req).execute().use { resp ->
                if (resp.isSuccessful) {
                    val root = JSONObject(resp.body?.string() ?: "{}")
                    val tabs = root.optJSONObject("contents")
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

                                // 1. Check musicCardShelfRenderer (hero artist card)
                                val card = secObj.optJSONObject("musicCardShelfRenderer")
                                if (card != null) {
                                    val browseId = card.optJSONObject("onTap")?.optJSONObject("browseEndpoint")?.optString("browseId")
                                        ?: card.optJSONObject("title")?.optJSONArray("runs")?.optJSONObject(0)?.optJSONObject("navigationEndpoint")?.optJSONObject("browseEndpoint")?.optString("browseId")
                                        ?: ""
                                    val name = card.optJSONObject("title")?.optJSONArray("runs")?.optJSONObject(0)?.optString("text") ?: artistName
                                    val subInfo = card.optJSONObject("subtitle")?.optJSONArray("runs")?.let { runs ->
                                        (0 until runs.length()).joinToString("") { runs.optJSONObject(it)?.optString("text") ?: "" }
                                    } ?: ""
                                    val thumbArr = card.optJSONObject("thumbnail")?.optJSONObject("musicThumbnailRenderer")?.optJSONObject("thumbnail")?.optJSONArray("thumbnails")
                                    val thumb = thumbArr?.optJSONObject(thumbArr.length() - 1)?.optString("url") ?: ""
                                    if (browseId.isNotBlank()) {
                                        artists.add(YtmTopArtist(id = browseId, name = name, subscribers = subInfo, avatarUrl = thumb))
                                    }
                                }

                                // 2. Check musicShelfRenderer or itemSectionRenderer
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

                                    var subInfo = ""
                                    if (flexCols.length() > 1) {
                                        val runs = flexCols.optJSONObject(1)
                                            ?.optJSONObject("musicResponsiveListItemFlexColumnRenderer")
                                            ?.optJSONObject("text")
                                            ?.optJSONArray("runs")
                                        if (runs != null) {
                                            subInfo = (0 until runs.length()).joinToString("") { runs.optJSONObject(it)?.optString("text") ?: "" }
                                        }
                                    }

                                    val thumbArr = item.optJSONObject("thumbnail")
                                        ?.optJSONObject("musicThumbnailRenderer")
                                        ?.optJSONObject("thumbnail")
                                        ?.optJSONArray("thumbnails")
                                    val thumb = thumbArr?.optJSONObject(thumbArr.length() - 1)?.optString("url") ?: ""

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

    /**
     * Fetches detailed artist profile with high-res banner, up to 30 Top Songs with real play counts,
     * and all carousels (Albums, Singles & EPs, Videos, Live performances, Featured on, Playlists, Fans might also like).
     */
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
        val albums = mutableListOf<com.auramusic.app.domain.model.ArtistAlbum>()
        val carousels = mutableListOf<com.auramusic.app.domain.model.ArtistCarousel>()

        if (browseId.isNotBlank()) {
            try {
                val payload = JSONObject().apply {
                    put("context", JSONObject().apply {
                        put("client", JSONObject().apply {
                            put("clientName", "WEB_REMIX")
                            put("clientVersion", "1.20250312.01.00")
                            put("hl", "en")
                            put("gl", "US")
                        })
                    })
                    put("browseId", browseId)
                }

                val req = Request.Builder()
                    .url("https://music.youtube.com/youtubei/v1/browse?prettyPrint=false")
                    .header("User-Agent", USER_AGENT)
                    .header("Origin", "https://music.youtube.com")
                    .header("Referer", "https://music.youtube.com/")
                    .post(payload.toString().toRequestBody("application/json".toMediaType()))
                    .build()

                httpClient.newCall(req).execute().use { resp ->
                    if (resp.isSuccessful) {
                        val root = JSONObject(resp.body?.string() ?: "{}")
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

                        if (bannerThumbnails != null && bannerThumbnails.length() > 0) {
                            bannerUrl = bannerThumbnails.optJSONObject(bannerThumbnails.length() - 1)?.optString("url") ?: bannerUrl
                        }

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
                            if (runs != null) (0 until runs.length()).joinToString("") { i ->
                                runs.optJSONObject(i)?.optString("text").orEmpty()
                            } else desc.optString("simpleText", "")
                        }.orEmpty()

                        val contents = root.optJSONObject("contents")
                            ?.optJSONObject("singleColumnBrowseResultsRenderer")
                            ?.optJSONArray("tabs")
                            ?.optJSONObject(0)
                            ?.optJSONObject("tabRenderer")
                            ?.optJSONObject("content")
                            ?.optJSONObject("sectionListRenderer")
                            ?.optJSONArray("contents")

                        if (contents != null) {
                            for (i in 0 until contents.length()) {
                                val shelfObj = contents.optJSONObject(i) ?: continue

                                // 1. Top Songs shelf
                                val shelf = shelfObj.optJSONObject("musicShelfRenderer")
                                if (shelf != null) {
                                    val shelfTitle = shelf.optJSONObject("title")
                                        ?.optJSONArray("runs")
                                        ?.optJSONObject(0)
                                        ?.optString("text") ?: ""

                                    if (shelfTitle.contains("Top songs", ignoreCase = true) || shelfTitle.contains("Songs", ignoreCase = true)) {
                                        // Check if a full Top Songs playlist endpoint is available (e.g. VLOL...)
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
                                                val plPayload = JSONObject().apply {
                                                    put("context", JSONObject().apply {
                                                        put("client", JSONObject().apply {
                                                            put("clientName", "WEB_REMIX")
                                                            put("clientVersion", "1.20250312.01.00")
                                                            put("hl", "en")
                                                            put("gl", "US")
                                                        })
                                                    })
                                                    put("browseId", moreBrowseId)
                                                    if (moreParams.isNotBlank()) put("params", moreParams)
                                                }
                                                val plReq = Request.Builder()
                                                    .url("https://music.youtube.com/youtubei/v1/browse?prettyPrint=false")
                                                    .header("User-Agent", USER_AGENT)
                                                    .header("Origin", "https://music.youtube.com")
                                                    .header("Referer", "https://music.youtube.com/")
                                                    .post(plPayload.toString().toRequestBody("application/json".toMediaType()))
                                                    .build()

                                                httpClient.newCall(plReq).execute().use { plResp ->
                                                    if (plResp.isSuccessful) {
                                                        val plRoot = JSONObject(plResp.body?.string() ?: "{}")
                                                        val plContents = plRoot.optJSONObject("contents")
                                                            ?.optJSONObject("singleColumnBrowseResultsRenderer")
                                                            ?.optJSONArray("tabs")
                                                            ?.optJSONObject(0)
                                                            ?.optJSONObject("tabRenderer")
                                                            ?.optJSONObject("content")
                                                            ?.optJSONObject("sectionListRenderer")
                                                            ?.optJSONArray("contents")
                                                            ?.optJSONObject(0)
                                                            ?.optJSONObject("musicPlaylistShelfRenderer")
                                                            ?.optJSONArray("contents")

                                                        if (plContents != null && plContents.length() > 0) {
                                                            for (j in 0 until minOf(plContents.length(), 30)) {
                                                                val item = plContents.optJSONObject(j)?.optJSONObject("musicResponsiveListItemRenderer") ?: continue
                                                                val flexCols = item.optJSONArray("flexColumns") ?: continue
                                                                val title = flexCols.optJSONObject(0)
                                                                    ?.optJSONObject("musicResponsiveListItemFlexColumnRenderer")
                                                                    ?.optJSONObject("text")
                                                                    ?.optJSONArray("runs")
                                                                    ?.optJSONObject(0)
                                                                    ?.optString("text") ?: continue

                                                                val album = if (flexCols.length() > 3) {
                                                                    flexCols.optJSONObject(3)?.optJSONObject("musicResponsiveListItemFlexColumnRenderer")
                                                                        ?.optJSONObject("text")?.optJSONArray("runs")?.optJSONObject(0)?.optString("text") ?: artist.name
                                                                } else artist.name

                                                                var playCount = ""
                                                                if (flexCols.length() > 2) {
                                                                    playCount = flexCols.optJSONObject(2)
                                                                        ?.optJSONObject("musicResponsiveListItemFlexColumnRenderer")
                                                                        ?.optJSONObject("text")
                                                                        ?.optJSONArray("runs")
                                                                        ?.optJSONObject(0)
                                                                        ?.optString("text") ?: ""
                                                                }

                                                                val vidId = item.optJSONObject("playlistItemData")?.optString("videoId")
                                                                    ?: item.optJSONObject("navigationEndpoint")
                                                                        ?.optJSONObject("watchEndpoint")
                                                                        ?.optString("videoId") ?: continue

                                                                val thumbArr = item.optJSONObject("thumbnail")
                                                                    ?.optJSONObject("musicThumbnailRenderer")
                                                                    ?.optJSONObject("thumbnail")
                                                                    ?.optJSONArray("thumbnails")
                                                                val thumb = thumbArr?.optJSONObject(thumbArr.length() - 1)?.optString("url")
                                                                    ?: "https://img.youtube.com/vi/$vidId/hqdefault.jpg"

                                                                topSongs.add(
                                                                    ArtistSong(
                                                                        rank = topSongs.size + 1,
                                                                        track = AuraTrack(
                                                                            id = "yt_$vidId",
                                                                            videoId = vidId,
                                                                            title = title,
                                                                            artist = artist.name,
                                                                            album = album,
                                                                            durationSeconds = 210,
                                                                            artworkUrl = thumb
                                                                        ),
                                                                        playCount = playCount
                                                                    )
                                                                )
                                                            }
                                                        }
                                                    }
                                                }
                                            } catch (e: Exception) {
                                                Log.d(TAG, "Error fetching full top songs playlist: ${e.message}")
                                            }
                                        }

                                        // Fallback to the main page top songs shelf items if full playlist was not parsed
                                        if (topSongs.isEmpty()) {
                                            val songItems = shelf.optJSONArray("contents")
                                            if (songItems != null) {
                                                for (j in 0 until minOf(songItems.length(), 20)) {
                                                    val item = songItems.optJSONObject(j)?.optJSONObject("musicResponsiveListItemRenderer") ?: continue
                                                    val flexCols = item.optJSONArray("flexColumns") ?: continue
                                                    val title = flexCols.optJSONObject(0)
                                                        ?.optJSONObject("musicResponsiveListItemFlexColumnRenderer")
                                                        ?.optJSONObject("text")
                                                        ?.optJSONArray("runs")
                                                        ?.optJSONObject(0)
                                                        ?.optString("text") ?: continue

                                                    val album = flexCols.optJSONObject(1)
                                                        ?.optJSONObject("musicResponsiveListItemFlexColumnRenderer")
                                                        ?.optJSONObject("text")
                                                        ?.optJSONArray("runs")
                                                        ?.optJSONObject(0)
                                                        ?.optString("text") ?: artist.name

                                                    var playCount = ""
                                                    if (flexCols.length() > 2) {
                                                        playCount = flexCols.optJSONObject(2)
                                                            ?.optJSONObject("musicResponsiveListItemFlexColumnRenderer")
                                                            ?.optJSONObject("text")
                                                            ?.optJSONArray("runs")
                                                            ?.optJSONObject(0)
                                                            ?.optString("text") ?: ""
                                                    }

                                                    val vidId = item.optJSONObject("playlistItemData")?.optString("videoId")
                                                        ?: item.optJSONObject("navigationEndpoint")
                                                            ?.optJSONObject("watchEndpoint")
                                                            ?.optString("videoId") ?: continue

                                                    val thumbArr = item.optJSONObject("thumbnail")
                                                        ?.optJSONObject("musicThumbnailRenderer")
                                                        ?.optJSONObject("thumbnail")
                                                        ?.optJSONArray("thumbnails")
                                                    val thumb = thumbArr?.optJSONObject(thumbArr.length() - 1)?.optString("url")
                                                        ?: "https://img.youtube.com/vi/$vidId/hqdefault.jpg"

                                                    topSongs.add(
                                                        ArtistSong(
                                                            rank = topSongs.size + 1,
                                                            track = AuraTrack(
                                                                id = "yt_$vidId",
                                                                videoId = vidId,
                                                                title = title,
                                                                artist = artist.name,
                                                                album = album,
                                                                durationSeconds = 210,
                                                                artworkUrl = thumb
                                                            ),
                                                            playCount = playCount
                                                        )
                                                    )
                                                }
                                            }
                                        }
                                    }
                                }

                                // 2. Parse All Carousel Shelves (Albums, Singles & EPs, Videos, Live performances, Featured on, Playlists, Fans might also like)
                                val carousel = shelfObj.optJSONObject("musicCarouselShelfRenderer")
                                if (carousel != null) {
                                    val headerTitle = carousel.optJSONObject("header")
                                        ?.optJSONObject("musicCarouselShelfBasicHeaderRenderer")
                                        ?.optJSONObject("title")
                                        ?.optJSONArray("runs")
                                        ?.optJSONObject(0)
                                        ?.optString("text") ?: ""

                                    val carouselItems = mutableListOf<com.auramusic.app.domain.model.ArtistCarouselItem>()
                                    val contentsArr = carousel.optJSONArray("contents")
                                    if (contentsArr != null && headerTitle.isNotBlank()) {
                                        for (k in 0 until minOf(contentsArr.length(), 15)) {
                                            val twoRow = contentsArr.optJSONObject(k)?.optJSONObject("musicTwoRowItemRenderer") ?: continue
                                            val itemTitle = twoRow.optJSONObject("title")
                                                ?.optJSONArray("runs")
                                                ?.optJSONObject(0)
                                                ?.optString("text") ?: continue

                                            val subRuns = twoRow.optJSONObject("subtitle")?.optJSONArray("runs")
                                            val subtitle = subRuns?.let { runs ->
                                                val parts = mutableListOf<String>()
                                                for (r in 0 until runs.length()) {
                                                    val txt = runs.optJSONObject(r)?.optString("text")?.trim() ?: ""
                                                    if (txt.isNotEmpty() && txt != "•") parts.add(txt)
                                                }
                                                parts.joinToString(" • ")
                                            } ?: ""

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

                                            val thumbArr = twoRow.optJSONObject("thumbnailRenderer")
                                                ?.optJSONObject("musicThumbnailRenderer")
                                                ?.optJSONObject("thumbnail")
                                                ?.optJSONArray("thumbnails")
                                            val thumb = thumbArr?.optJSONObject(thumbArr.length() - 1)?.optString("url") ?: ""

                                            val itemId = if (vid.isNotBlank()) vid else if (bid.isNotBlank()) bid else "item_${carouselItems.size}"

                                            carouselItems.add(
                                                com.auramusic.app.domain.model.ArtistCarouselItem(
                                                    id = itemId,
                                                    title = itemTitle,
                                                    subtitle = subtitle,
                                                    artworkUrl = thumb,
                                                    endpointType = endpointType,
                                                    videoId = vid,
                                                    browseId = bid
                                                )
                                            )

                                            // Also maintain backwards compatibility for albums list
                                            if (headerTitle.contains("Albums", ignoreCase = true) ||
                                                headerTitle.contains("Singles", ignoreCase = true) ||
                                                headerTitle.contains("EPs", ignoreCase = true)
                                            ) {
                                                albums.add(
                                                    com.auramusic.app.domain.model.ArtistAlbum(
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
                                            carousels.add(
                                                com.auramusic.app.domain.model.ArtistCarousel(
                                                    title = headerTitle,
                                                    items = carouselItems
                                                )
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            } catch (e: Exception) {
                Log.w(TAG, "Error fetching artist channel browse: ${e.message}")
            }
        }

        // Fallbacks if browse didn't return top songs
        if (topSongs.isEmpty()) {
            val searched = searchYouTubeMusic("${artist.name} official songs", "songs").take(25)
            searched.forEachIndexed { idx, track ->
                topSongs.add(
                    ArtistSong(
                        rank = idx + 1,
                        track = track,
                        playCount = ""
                    )
                )
            }
        }

        // Fallbacks if albums are empty
        if (albums.isEmpty()) {
            try {
                val albumSearch = searchYouTubeMusic("${artist.name} album official", "albums").ifEmpty {
                    searchYouTubeMusic("${artist.name} discography", "songs")
                }
                albumSearch.take(8).forEachIndexed { idx, trk ->
                    val alb = com.auramusic.app.domain.model.ArtistAlbum(
                        id = trk.videoId,
                        title = trk.album.ifBlank { trk.title },
                        year = "Album",
                        artworkUrl = trk.artworkUrl,
                        type = "Album"
                    )
                    albums.add(alb)
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

    /**
     * Resolves artist profile directly by artist name string, disambiguating with songTitle context if available.
     */
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

    /**
     * Disambiguates an artist channel by querying "$artistName $songTitle" on InnerTube.
     * Prevents collisions between artists sharing the same name (e.g. Japanese "LiSA" vs K-pop "LISA", Japanese "Eve" vs US rapper "Eve").
     */
    suspend fun resolveArtistBrowseId(artistName: String, songTitle: String): String? = withContext(Dispatchers.IO) {
        if (artistName.isBlank() || songTitle.isBlank()) return@withContext null
        try {
            val payload = JSONObject().apply {
                put("context", JSONObject().apply {
                    put("client", JSONObject().apply {
                        put("clientName", "WEB_REMIX")
                        put("clientVersion", "1.20250312.01.00")
                        put("hl", "en")
                        put("gl", "US")
                    })
                })
                put("query", "$artistName $songTitle")
            }

            val req = Request.Builder()
                .url("https://music.youtube.com/youtubei/v1/search?prettyPrint=false")
                .header("User-Agent", USER_AGENT)
                .header("Origin", "https://music.youtube.com")
                .header("Referer", "https://music.youtube.com/")
                .post(payload.toString().toRequestBody("application/json".toMediaType()))
                .build()

            httpClient.newCall(req).execute().use { resp ->
                if (!resp.isSuccessful) return@withContext null
                val root = JSONObject(resp.body?.string() ?: "{}")
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
            }
        } catch (e: Exception) {
            Log.w(TAG, "Resolve artist browseId error: ${e.message}")
        }
        null
    }

    /**
     * Direct Client-First Spotify Public Playlist Importer:
     * Fetches `https://open.spotify.com/embed/playlist/{playlistId}`, extracts `__NEXT_DATA__` tracklist,
     * and resolves matching YouTube Music tracks in parallel without any backend server.
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
            .header("User-Agent", USER_AGENT)
            .build()

        val html = httpClient.newCall(req).execute().use { resp ->
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

        // Multi-tier cover art resolution:
        // 1. visualIdentity.image array (highest resolution)
        var coverUrl = ""
        val visualImages = entity.optJSONObject("visualIdentity")?.optJSONArray("image")
        if (visualImages != null && visualImages.length() > 0) {
            for (idx in visualImages.length() - 1 downTo 0) {
                val candidate = visualImages.optJSONObject(idx)?.optString("url") ?: ""
                if (candidate.isNotBlank()) {
                    coverUrl = candidate
                    break
                }
            }
        }

        // 2. attributes array (image_url or header_image_url_desktop)
        if (coverUrl.isBlank()) {
            val attributes = entity.optJSONArray("attributes")
            if (attributes != null) {
                for (a in 0 until attributes.length()) {
                    val attr = attributes.optJSONObject(a) ?: continue
                    val key = attr.optString("key")
                    val value = attr.optString("value")
                    if ((key == "image_url" || key == "header_image_url_desktop") && value.isNotBlank()) {
                        coverUrl = value
                        break
                    }
                }
            }
        }

        // 3. coverArt sources
        if (coverUrl.isBlank()) {
            val coverSources = entity.optJSONObject("coverArt")?.optJSONArray("sources")
            if (coverSources != null && coverSources.length() > 0) {
                coverUrl = coverSources.optJSONObject(0)?.optString("url") ?: ""
            }
        }

        // 4. HTML meta tags & preload link fallback
        if (coverUrl.isBlank()) {
            val preloadMatch = Regex("<link rel=\"preload\" as=\"image\" href=\"([^\"]+)\"").find(html)
            if (preloadMatch != null) {
                coverUrl = preloadMatch.groupValues[1]
            } else {
                val ogImageMatch = Regex("<meta property=\"og:image\" content=\"([^\"]+)\"").find(html)
                if (ogImageMatch != null) {
                    coverUrl = ogImageMatch.groupValues[1]
                }
            }
        }

        val maxImportCount = 5000
        val resolvedTracks = mutableListOf<AuraTrack>()

        // Primary Path: Use Spotify Pathfinder GraphQL API with the anonymous WebPlayer accessToken
        // to paginate ALL tracks (1000+ songs) AND extract each individual song's 640x640 album artwork!
        if (accessToken.isNotBlank()) {
            try {
                var queryHash = "21ea11a6ac75a5f3e01fd365d29c18e0a4a8b7dc09f89bbb5d73910190c6acaf"
                var offset = 0
                val pageSize = 100
                var totalToFetch = maxImportCount
                var hashRefreshed = false

                while (offset < totalToFetch && resolvedTracks.size < maxImportCount) {
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
                        "&variables=${java.net.URLEncoder.encode(variablesJson, "UTF-8")}" +
                        "&extensions=${java.net.URLEncoder.encode(extensionsJson, "UTF-8")}"

                    val pfReq = Request.Builder()
                        .url(pathfinderUrl)
                        .header("User-Agent", USER_AGENT)
                        .header("Authorization", "Bearer $accessToken")
                        .header("App-Platform", "WebPlayer")
                        .header("Origin", "https://open.spotify.com")
                        .header("Referer", "https://open.spotify.com/")
                        .build()

                    val pfBody = httpClient.newCall(pfReq).execute().use { pfResp ->
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
                        totalToFetch = minOf(totalCount, maxImportCount)
                    }
                    val itemsArr = contentObj.optJSONArray("items") ?: break
                    if (itemsArr.length() == 0) break

                    for (idx in 0 until itemsArr.length()) {
                        if (resolvedTracks.size >= maxImportCount) break
                        val itemData = itemsArr.optJSONObject(idx)
                            ?.optJSONObject("itemV2")
                            ?.optJSONObject("data") ?: continue

                        val trackTitle = itemData.optString("name").trim()
                        if (trackTitle.isEmpty()) continue

                        // Extract all artists for this track
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

                        // Extract this track's own album name and individual 640x640 album artwork
                        val albumObj = itemData.optJSONObject("albumOfTrack")
                        val trackAlbumName = albumObj?.optString("name")?.trim()?.takeIf { it.isNotEmpty() } ?: playlistName
                        var trackCoverUrl = ""
                        val coverSources = albumObj?.optJSONObject("coverArt")?.optJSONArray("sources")
                        if (coverSources != null && coverSources.length() > 0) {
                            var bestWidth = -1
                            for (c in 0 until coverSources.length()) {
                                val src = coverSources.optJSONObject(c) ?: continue
                                val url = src.optString("url")
                                val w = src.optInt("width", 0)
                                if (url.isNotBlank() && w >= bestWidth) {
                                    bestWidth = w
                                    trackCoverUrl = url
                                }
                            }
                        }

                        val durationMs = itemData.optJSONObject("trackDuration")?.optLong("totalMilliseconds", 210000L) ?: 210000L
                        val durationSec = (durationMs / 1000L).toInt().coerceAtLeast(1)
                        val isExplicit = itemData.optJSONObject("contentRating")?.optString("label") == "EXPLICIT"
                        val trackIndex = resolvedTracks.size

                        val cached = getCachedSpotifyResolvedTrack(trackTitle, artistStr)
                        if (cached != null && !cached.isPendingResolution) {
                            resolvedTracks.add(
                                cached.copy(
                                    id = "spot_${playlistId}_$trackIndex",
                                    album = trackAlbumName,
                                    durationSeconds = durationSec,
                                    artworkUrl = trackCoverUrl.ifBlank { cached.artworkUrl },
                                    isExplicit = isExplicit
                                )
                            )
                        } else {
                            resolvedTracks.add(
                                AuraTrack(
                                    id = "spot_${playlistId}_$trackIndex",
                                    videoId = "spotify_pending_${playlistId}_$trackIndex",
                                    title = trackTitle,
                                    artist = artistStr,
                                    album = trackAlbumName,
                                    durationSeconds = durationSec,
                                    artworkUrl = trackCoverUrl.ifBlank { coverUrl },
                                    isExplicit = isExplicit,
                                    source = "spotify"
                                )
                            )
                        }
                        onProgress(resolvedTracks.size, totalToFetch)
                    }

                    offset += itemsArr.length()
                    if (itemsArr.length() < pageSize) break
                }
            } catch (e: Exception) {
                Log.w(TAG, "Spotify Pathfinder pagination fallback: ${e.message}")
            }
        }

        // Fallback Path: If Pathfinder returned 0 tracks, fall back to embed trackList
        if (resolvedTracks.isEmpty()) {
            val trackListArray = entity.optJSONArray("trackList") ?: JSONArray()
            val totalToImport = minOf(trackListArray.length(), maxImportCount)
            for (i in 0 until totalToImport) {
                val item = trackListArray.optJSONObject(i) ?: continue
                val title = item.optString("title").trim()
                val subtitle = item.optString("subtitle").replace("\u00A0", " ").trim()
                if (title.isEmpty()) continue
                val durationMs = item.optLong("duration", 210000L)
                val durationSec = (durationMs / 1000L).toInt().coerceAtLeast(1)

                val cached = getCachedSpotifyResolvedTrack(title, subtitle)
                if (cached != null && !cached.isPendingResolution) {
                    resolvedTracks.add(cached.copy(id = "spot_${playlistId}_$i", album = playlistName))
                } else {
                    resolvedTracks.add(
                        AuraTrack(
                            id = "spot_${playlistId}_$i",
                            videoId = "spotify_pending_${playlistId}_$i",
                            title = title,
                            artist = subtitle,
                            album = playlistName,
                            durationSeconds = durationSec,
                            artworkUrl = "",
                            source = "spotify"
                        )
                    )
                }
                onProgress(i + 1, totalToImport)
            }
        }

        AuraPlaylist(
            id = "spotify_$playlistId",
            name = playlistName,
            description = "Imported from Spotify • ${resolvedTracks.size} tracks",
            coverUrl = coverUrl.ifBlank { resolvedTracks.firstOrNull()?.highResArtworkUrl ?: "" },
            tracks = resolvedTracks
        )
    }

    private fun fetchDynamicSpotifyPlaylistQueryHash(playlistId: String): String? {
        return try {
            val pageReq = Request.Builder()
                .url("https://open.spotify.com/playlist/$playlistId")
                .header("User-Agent", USER_AGENT)
                .build()
            val pageHtml = httpClient.newCall(pageReq).execute().use { it.body?.string() ?: "" }
            val scriptUrl = Regex("""https://open\.spotifycdn\.com/cdn/build/web-player/web-player\.[a-f0-9]+\.js""")
                .find(pageHtml)?.value ?: return null
            val jsReq = Request.Builder()
                .url(scriptUrl)
                .header("User-Agent", USER_AGENT)
                .build()
            val jsContent = httpClient.newCall(jsReq).execute().use { it.body?.string() ?: "" }
            Regex(""""fetchPlaylist"\s*,\s*"query"\s*,\s*"([a-f0-9]{64})"""")
                .find(jsContent)?.groupValues?.getOrNull(1)
        } catch (e: Exception) {
            null
        }
    }

    private val spotifyResolvedMemoryCache = ConcurrentHashMap<String, AuraTrack>()
    private var storageRepository: com.auramusic.app.data.AuraStorageRepository? = null

    fun setStorageRepository(storage: com.auramusic.app.data.AuraStorageRepository) {
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
                artworkUrl = if (track.artworkUrl.isNotBlank() && !track.artworkUrl.contains("default")) track.artworkUrl else cached.artworkUrl
            )
        }

        try {
            val query = "${track.title} ${track.artist}".trim()
            val match = searchYouTubeMusic(query, "songs").firstOrNull()
            if (match != null) {
                val resolved = track.copy(
                    videoId = match.videoId,
                    durationSeconds = if (match.durationSeconds > 0) match.durationSeconds else track.durationSeconds,
                    artworkUrl = if (track.artworkUrl.isNotBlank() && !track.artworkUrl.contains("default")) track.artworkUrl else match.artworkUrl,
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

    private fun parseCompactViewCount(metadata: List<String>): Long? {
        val viewPattern = Regex("([\\d,.]+)\\s*([KMB])?\\s*(?:views|plays)", RegexOption.IGNORE_CASE)
        for (entry in metadata) {
            val match = viewPattern.find(entry) ?: continue
            val amount = match.groupValues[1].replace(",", "").toDoubleOrNull() ?: continue
            val multiplier = when (match.groupValues[2].uppercase()) {
                "K" -> 1_000.0
                "M" -> 1_000_000.0
                "B" -> 1_000_000_000.0
                else -> 1.0
            }
            return (amount * multiplier).toLong().coerceAtLeast(0L)
        }
        return null
    }

    private fun parseYtmSearchTracks(root: JSONObject, out: MutableList<AuraTrack>) {
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

                    val thumbs = item.optJSONObject("thumbnail")
                        ?.optJSONObject("musicThumbnailRenderer")
                        ?.optJSONObject("thumbnail")
                        ?.optJSONArray("thumbnails")
                    val rawThumb = thumbs?.optJSONObject(thumbs.length() - 1)?.optString("url")
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
                            musicVideoType = innerTubeMusicVideoType(item),
                            viewCount = viewCount
                        )
                    )
                }
            }
        }
    }

    private fun parseDurationText(text: String): Int {
        val trimmed = text.trim()
        if (trimmed.contains("hr", ignoreCase = true) || trimmed.contains("min", ignoreCase = true)) {
            var total = 0
            val hrMatch = Regex("""(\d+)\s*(?:hr|hour)""", RegexOption.IGNORE_CASE).find(trimmed)
            if (hrMatch != null) total += (hrMatch.groupValues[1].toIntOrNull() ?: 0) * 3600
            val minMatch = Regex("""(\d+)\s*(?:min|minute)""", RegexOption.IGNORE_CASE).find(trimmed)
            if (minMatch != null) total += (minMatch.groupValues[1].toIntOrNull() ?: 0) * 60
            val secMatch = Regex("""(\d+)\s*(?:sec|second)""", RegexOption.IGNORE_CASE).find(trimmed)
            if (secMatch != null) total += (secMatch.groupValues[1].toIntOrNull() ?: 0)
            if (total > 0) return total
        }
        val parts = trimmed.split(":").mapNotNull { it.toIntOrNull() }
        return when (parts.size) {
            2 -> parts[0] * 60 + parts[1]
            3 -> parts[0] * 3600 + parts[1] * 60 + parts[2]
            else -> 210
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
