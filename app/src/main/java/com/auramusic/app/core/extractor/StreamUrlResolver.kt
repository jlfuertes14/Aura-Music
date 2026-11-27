package com.auramusic.app.core.extractor

import android.util.Log
import com.auramusic.app.domain.model.ResolvedVideoStream
import dev.maxrave.pipepipe.extractor.NewPipe as PipePipe
import dev.maxrave.pipepipe.extractor.ServiceList as PipePipeServiceList
import dev.maxrave.pipepipe.extractor.downloader.CancellableCall as PipePipeCancellableCall
import dev.maxrave.pipepipe.extractor.downloader.Downloader as PipePipeDownloader
import dev.maxrave.pipepipe.extractor.downloader.Request as PipePipeRequest
import dev.maxrave.pipepipe.extractor.downloader.Response as PipePipeResponse
import dev.maxrave.pipepipe.extractor.exceptions.ReCaptchaException as PipePipeReCaptchaException
import dev.maxrave.pipepipe.extractor.services.youtube.YoutubeApiDecoder
import dev.maxrave.pipepipe.extractor.stream.StreamInfo as PipePipeStreamInfo
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import org.schabi.newpipe.extractor.NewPipe as BraveNewPipe
import org.schabi.newpipe.extractor.ServiceList as BraveServiceList
import org.schabi.newpipe.extractor.downloader.Downloader as BraveDownloader
import org.schabi.newpipe.extractor.downloader.Request as BraveRequest
import org.schabi.newpipe.extractor.downloader.Response as BraveResponse
import org.schabi.newpipe.extractor.exceptions.ReCaptchaException as BraveReCaptchaException
import org.schabi.newpipe.extractor.localization.ContentCountry
import org.schabi.newpipe.extractor.localization.Localization
import org.schabi.newpipe.extractor.stream.StreamInfo as BraveStreamInfo
import java.io.IOException
import java.util.concurrent.ConcurrentHashMap

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

internal object StreamUrlResolver {
    private const val TAG = InnerTubeClient.TAG
    private val backgroundScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    // Tier 0 In-memory Resolved Stream Cache (videoId|quality -> ResolvedStream)
    private val streamCache = ConcurrentHashMap<String, ResolvedStream>()
    // Tier 0 In-memory Resolved Video Stream Cache (videoId|videoQuality|audioQuality -> ResolvedVideoStream)
    private val videoStreamCache = ConcurrentHashMap<String, ResolvedVideoStream>()
    private val loudnessCache = ConcurrentHashMap<String, Float>()

    private val XTAGS_REGEX = Regex("[?&]xtags=([^&]+)")

    @Volatile
    private var isInitialized = false

    fun getTrackLoudnessDb(videoId: String): Float? = loudnessCache[videoId]

    private fun cacheLoudnessFromPlayerResponse(bodyStr: String) {
        if (!bodyStr.contains("loudnessDb")) return
        try {
            val json = JSONObject(bodyStr)
            val vid = json.optJSONObject("videoDetails")?.optString("videoId").orEmpty()
            val loudness = json.optJSONObject("playerConfig")
                ?.optJSONObject("audioConfig")
                ?.optDouble("loudnessDb", Double.NaN)
                ?: Double.NaN
            if (vid.isNotBlank() && !loudness.isNaN()) {
                loudnessCache[vid] = loudness.toFloat()
            }
        } catch (_: Exception) {
            // Ignore malformed or non-JSON responses
        }
    }

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
                            .header("User-Agent", InnerTubeClient.USER_AGENT)
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
                        val bodyStr = rawBytes.toString(Charsets.UTF_8)
                        if (request.url.encodedPath.contains("youtubei/v1/player")) {
                            cacheLoudnessFromPlayerResponse(bodyStr)
                        }
                        return PipePipeResponse(
                            code,
                            message,
                            headers.toMultimap(),
                            bodyStr,
                            rawBytes,
                            request.url.toString()
                        )
                    }

                    override fun execute(request: PipePipeRequest): PipePipeResponse {
                        InnerTubeClient.httpClient.newCall(buildOkHttpReq(request)).execute().use { resp ->
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
                        val call = InnerTubeClient.httpClient.newCall(buildOkHttpReq(request))
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
                                .header("User-Agent", InnerTubeClient.USER_AGENT)
                            request.headers().forEach { (key, values) ->
                                builder.removeHeader(key)
                                values.forEach { v -> builder.addHeader(key, v) }
                            }
                            val bodyBytes = request.dataToSend()
                            val reqBody = bodyBytes?.toRequestBody(null, 0, bodyBytes.size)
                            builder.method(request.httpMethod(), reqBody)

                            InnerTubeClient.httpClient.newCall(builder.build()).execute().use { resp ->
                                if (resp.code == 429) {
                                    throw BraveReCaptchaException("reCaptcha Challenge requested", request.url())
                                }
                                val bodyStr = resp.body?.string() ?: ""
                                if (resp.request.url.encodedPath.contains("youtubei/v1/player")) {
                                    cacheLoudnessFromPlayerResponse(bodyStr)
                                }
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

    private fun audioCacheKey(videoId: String, audioQuality: String): String = "$videoId|$audioQuality"

    private fun videoCacheKey(videoId: String, videoQuality: String, audioQuality: String): String =
        "$videoId|$videoQuality|$audioQuality"

    fun getCachedStream(videoId: String, audioQuality: String = "Auto"): ResolvedStream? {
        val key = audioCacheKey(videoId, audioQuality)
        val cached = streamCache[key] ?: return null
        if (cached.expiresAtMs > System.currentTimeMillis() + 60_000L) {
            return cached
        }
        streamCache.remove(key)
        return null
    }

    fun invalidateStream(videoId: String) {
        val prefix = "$videoId|"
        streamCache.keys.filter { it == videoId || it.startsWith(prefix) }.forEach { streamCache.remove(it) }
        videoStreamCache.keys.filter { it == videoId || it.startsWith(prefix) }.forEach { videoStreamCache.remove(it) }
    }

    fun prefetchStream(videoId: String, audioQuality: String = "Auto") {
        if (videoId.isBlank() || getCachedStream(videoId, audioQuality) != null) return
        backgroundScope.launch {
            try {
                resolveStream(videoId, audioQuality)
            } catch (e: Exception) {
                Log.d(TAG, "Prefetch skipped for $videoId: ${e.message}")
            }
        }
    }

    fun clearStreamCache() {
        streamCache.clear()
        videoStreamCache.clear()
    }

    /**
     * SimpMusic is403Url equivalent: verifies the stream URL responds with HTTP 200 or 206
     * and returns the final redirected URL if any.
     */
    private fun verifyPlayableUrl(candidateUrl: String): String? {
        return try {
            val req = Request.Builder()
                .url(candidateUrl)
                .header("User-Agent", InnerTubeClient.USER_AGENT)
                .header("Range", "bytes=0-1024")
                .get()
                .build()
            InnerTubeClient.fastVerifyClient.newCall(req).execute().use { resp ->
                if (resp.code in 200..299) {
                    resp.request.url.toString()
                } else {
                    null
                }
            }
        } catch (_: Exception) {
            null
        }
    }

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
            acont == "descriptive" -> 99
            lang == pref && (acont == null || acont == "original") -> 0
            acont == null || acont == "original" -> 1
            lang == pref -> 2
            else -> 10
        }
    }

    private enum class AudioQualityMode {
        AUTO, HIGH_OPUS, HIGH_AAC, MEDIUM, LOW
    }

    private fun parseAudioQualityMode(audioQuality: String): AudioQualityMode = when {
        audioQuality.contains("Opus", ignoreCase = true) -> AudioQualityMode.HIGH_OPUS
        audioQuality.contains("AAC", ignoreCase = true) -> AudioQualityMode.HIGH_AAC
        audioQuality.contains("Medium", ignoreCase = true) -> AudioQualityMode.MEDIUM
        audioQuality.contains("Low", ignoreCase = true) -> AudioQualityMode.LOW
        else -> AudioQualityMode.AUTO
    }

    private fun isM4aStream(suffix: String?, mime: String?): Boolean =
        suffix.equals("m4a", ignoreCase = true) ||
            mime?.contains("mp4", ignoreCase = true) == true ||
            mime?.contains("aac", ignoreCase = true) == true

    private fun isOpusStream(suffix: String?, mime: String?): Boolean =
        suffix.equals("webm", ignoreCase = true) ||
            suffix.equals("opus", ignoreCase = true) ||
            mime?.contains("opus", ignoreCase = true) == true

    private fun <T> sortAudioStreamsForQuality(
        streams: List<T>,
        audioQuality: String,
        urlOf: (T) -> String?,
        suffixOf: (T) -> String?,
        mimeOf: (T) -> String?,
        bitrateOf: (T) -> Int
    ): List<T> {
        val mode = parseAudioQualityMode(audioQuality)
        val comparator = Comparator<T> { a, b ->
            val rankA = audioTrackRank(urlOf(a).orEmpty(), "en")
            val rankB = audioTrackRank(urlOf(b).orEmpty(), "en")
            if (rankA != rankB) return@Comparator rankA.compareTo(rankB)

            val brA = bitrateOf(a)
            val brB = bitrateOf(b)
            val m4aA = isM4aStream(suffixOf(a), mimeOf(a))
            val m4aB = isM4aStream(suffixOf(b), mimeOf(b))
            val opusA = isOpusStream(suffixOf(a), mimeOf(a))
            val opusB = isOpusStream(suffixOf(b), mimeOf(b))

            when (mode) {
                AudioQualityMode.HIGH_OPUS -> {
                    if (opusA != opusB) return@Comparator if (opusA) -1 else 1
                    brB.compareTo(brA)
                }
                AudioQualityMode.HIGH_AAC -> {
                    if (m4aA != m4aB) return@Comparator if (m4aA) -1 else 1
                    brB.compareTo(brA)
                }
                AudioQualityMode.MEDIUM -> {
                    val inBandA = brA in 110..175
                    val inBandB = brB in 110..175
                    if (inBandA != inBandB) return@Comparator if (inBandA) -1 else 1
                    if (inBandA) {
                        val distA = kotlin.math.abs(brA - 144)
                        val distB = kotlin.math.abs(brB - 144)
                        if (distA != distB) return@Comparator distA.compareTo(distB)
                        return@Comparator m4aB.compareTo(m4aA)
                    }
                    val belowCapA = brA in 1..192
                    val belowCapB = brB in 1..192
                    if (belowCapA != belowCapB) return@Comparator if (belowCapA) -1 else 1
                    val distA = kotlin.math.abs((if (brA > 0) brA else 999) - 144)
                    val distB = kotlin.math.abs((if (brB > 0) brB else 999) - 144)
                    distA.compareTo(distB)
                }
                AudioQualityMode.LOW -> {
                    val inBandA = brA in 40..105
                    val inBandB = brB in 40..105
                    if (inBandA != inBandB) return@Comparator if (inBandA) -1 else 1
                    val effA = if (brA > 0) brA else Int.MAX_VALUE
                    val effB = if (brB > 0) brB else Int.MAX_VALUE
                    effA.compareTo(effB)
                }
                AudioQualityMode.AUTO -> {
                    if (m4aA != m4aB) return@Comparator if (m4aA) -1 else 1
                    brB.compareTo(brA)
                }
            }
        }
        return streams.filter { !urlOf(it).isNullOrBlank() }.sortedWith(comparator)
    }

    private fun <T> resolveAudioFromStreams(
        videoId: String,
        streams: List<T>?,
        durationSeconds: Int,
        sourceTier: String,
        audioQuality: String,
        urlOf: (T) -> String?,
        suffixOf: (T) -> String?,
        mimeOf: (T) -> String?,
        bitrateOf: (T) -> Int
    ): ResolvedStream? {
        if (streams.isNullOrEmpty()) return null
        val sorted = sortAudioStreamsForQuality(streams, audioQuality, urlOf, suffixOf, mimeOf, bitrateOf)
        for (selected in sorted) {
            val rawUrl = urlOf(selected) ?: continue
            val verifiedUrl = verifyPlayableUrl(rawUrl) ?: continue
            val suffix = suffixOf(selected)
            val mime = mimeOf(selected)
            val codec = if (isM4aStream(suffix, mime)) "AAC" else "OPUS"
            val resolved = ResolvedStream(
                videoId = videoId,
                streamUrl = verifiedUrl,
                mimeType = mime ?: "audio/mp4",
                bitrateKbps = bitrateOf(selected).takeIf { it > 0 } ?: 160,
                codec = codec,
                loudnessDb = loudnessCache[videoId] ?: 0f,
                durationSeconds = durationSeconds.coerceAtLeast(0),
                sourceTier = sourceTier
            )
            streamCache[audioCacheKey(videoId, audioQuality)] = resolved
            Log.i(TAG, "$sourceTier resolved $videoId [$audioQuality] (${resolved.codec} ${resolved.bitrateKbps}kbps, loudness=${resolved.loudnessDb}dB)")
            return resolved
        }
        return null
    }

    private fun targetVideoHeight(videoQuality: String): Int = when {
        videoQuality.startsWith("360") -> 360
        videoQuality.startsWith("480") -> 480
        videoQuality.startsWith("720") -> 720
        videoQuality.startsWith("1080") -> 1080
        else -> 1080
    }

    private fun <T> sortVideoStreamsForQuality(
        streams: List<T>,
        videoQuality: String,
        urlOf: (T) -> String?,
        heightOf: (T) -> Int
    ): List<T> {
        val maxTargetHeight = targetVideoHeight(videoQuality)
        return streams
            .filter { !urlOf(it).isNullOrBlank() && heightOf(it) > 0 }
            .sortedWith(Comparator { a, b ->
                val hA = heightOf(a)
                val hB = heightOf(b)
                val withinA = hA <= maxTargetHeight
                val withinB = hB <= maxTargetHeight
                if (withinA != withinB) return@Comparator if (withinA) -1 else 1
                if (withinA) hB.compareTo(hA) else hA.compareTo(hB)
            })
    }

    private fun <V, A> resolveVideoFromLists(
        videoId: String,
        videoOnlyStreams: List<V>?,
        progressiveStreams: List<V>?,
        audioStreams: List<A>?,
        durationSeconds: Int,
        tierLabel: String,
        videoQuality: String,
        audioQuality: String,
        videoUrlOf: (V) -> String?,
        videoHeightOf: (V) -> Int,
        videoResolutionOf: (V) -> String?,
        audioUrlOf: (A) -> String?,
        audioSuffixOf: (A) -> String?,
        audioMimeOf: (A) -> String?,
        audioBitrateOf: (A) -> Int
    ): ResolvedVideoStream? {
        if (!videoOnlyStreams.isNullOrEmpty() && !audioStreams.isNullOrEmpty()) {
            val sortedVideos = sortVideoStreamsForQuality(videoOnlyStreams, videoQuality, videoUrlOf, videoHeightOf)
            val sortedAudios = sortAudioStreamsForQuality(audioStreams, audioQuality, audioUrlOf, audioSuffixOf, audioMimeOf, audioBitrateOf)

            var verifiedAudioUrl: String? = null
            for (audio in sortedAudios.take(4)) {
                val candidate = audioUrlOf(audio) ?: continue
                verifiedAudioUrl = verifyPlayableUrl(candidate)
                if (verifiedAudioUrl != null) break
            }

            if (verifiedAudioUrl != null) {
                for (video in sortedVideos.take(4)) {
                    val rawVideoUrl = videoUrlOf(video) ?: continue
                    val verifiedVideo = verifyPlayableUrl(rawVideoUrl) ?: continue
                    val h = videoHeightOf(video)
                    val resolved = ResolvedVideoStream(
                        videoId = videoId,
                        streamUrl = verifiedVideo,
                        quality = if (h >= 720) "${h}p HD" else "${h}p",
                        durationSeconds = durationSeconds.coerceAtLeast(0),
                        audioUrl = verifiedAudioUrl
                    )
                    videoStreamCache[videoCacheKey(videoId, videoQuality, audioQuality)] = resolved
                    Log.i(TAG, "$tierLabel resolved video+audio for $videoId (${resolved.quality}, pref=$videoQuality, audio=$audioQuality)")
                    return resolved
                }
            }
        }

        if (!progressiveStreams.isNullOrEmpty()) {
            val sorted = sortVideoStreamsForQuality(progressiveStreams, videoQuality, videoUrlOf, videoHeightOf)
            for (stream in sorted) {
                val rawUrl = videoUrlOf(stream) ?: continue
                val verifiedUrl = verifyPlayableUrl(rawUrl) ?: continue
                val resolved = ResolvedVideoStream(
                    videoId = videoId,
                    streamUrl = verifiedUrl,
                    quality = videoResolutionOf(stream) ?: "${videoHeightOf(stream)}p",
                    durationSeconds = durationSeconds.coerceAtLeast(0),
                    audioUrl = null
                )
                videoStreamCache[videoCacheKey(videoId, videoQuality, audioQuality)] = resolved
                Log.i(TAG, "$tierLabel resolved progressive video for $videoId (${resolved.quality}, pref=$videoQuality)")
                return resolved
            }
        }
        return null
    }

    /**
     * Multi-tier SimpMusic stream extraction:
     * Tier 0: Memory cache (0ms)
     * Tier 1: PipePipeExtractor (SimpMusic official engine + api.pipepipe.dev cipher)
     * Tier 2: BraveNewPipe Extractor (SmartTube engine)
     * Tier 3: Fast pre-warmed Invidious/Piped local=true proxy stream (~230ms)
     */
    suspend fun resolveStream(
        videoId: String,
        audioQuality: String = "Auto"
    ): ResolvedStream = withContext(Dispatchers.IO) {
        initExtractors()

        getCachedStream(videoId, audioQuality)?.let { return@withContext it }

        // Tier 1: PipePipeExtractor (SimpMusic)
        try {
            YoutubeApiDecoder.setLocalDecoder(null)
            val url = "https://www.youtube.com/watch?v=$videoId"
            val streamInfo = PipePipeStreamInfo.getInfo(PipePipeServiceList.YouTube, url)
            resolveAudioFromStreams(
                videoId = videoId,
                streams = streamInfo.audioStreams,
                durationSeconds = streamInfo.duration.toInt(),
                sourceTier = "PipePipeExtractor (SimpMusic Native)",
                audioQuality = audioQuality,
                urlOf = { it.content },
                suffixOf = { it.format?.suffix },
                mimeOf = { it.format?.mimeType },
                bitrateOf = { it.averageBitrate }
            )?.let { return@withContext it }
        } catch (e: Exception) {
            Log.w(TAG, "Tier 1 PipePipeExtractor notice for $videoId: ${e.message}")
        }

        // Tier 2: BraveNewPipe Extractor
        try {
            val url = "https://www.youtube.com/watch?v=$videoId"
            val streamInfo = BraveStreamInfo.getInfo(BraveServiceList.YouTube, url)
            resolveAudioFromStreams(
                videoId = videoId,
                streams = streamInfo.audioStreams,
                durationSeconds = streamInfo.duration.toInt(),
                sourceTier = "NewPipeExtractor (SmartTube Native)",
                audioQuality = audioQuality,
                urlOf = { it.content },
                suffixOf = { it.format?.suffix },
                mimeOf = { it.format?.mimeType },
                bitrateOf = { it.averageBitrate }
            )?.let { return@withContext it }
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
                    loudnessDb = loudnessCache[videoId] ?: 0f,
                    sourceTier = "Direct Edge Stream (Pre-Warmed)"
                )
                streamCache[audioCacheKey(videoId, audioQuality)] = resolved
                Log.i(TAG, "Tier 3 Direct Edge Stream resolved $videoId -> $finalUrl")
                return@withContext resolved
            }
        }

        throw IllegalStateException("Unable to resolve playable audio stream for $videoId")
    }

    /**
     * Resolves a video stream for video playback mode honoring user video & audio quality preferences.
     */
    suspend fun resolveVideoStream(
        videoId: String,
        videoQuality: String = "Auto (1080p)",
        audioQuality: String = "Auto"
    ): ResolvedVideoStream? = withContext(Dispatchers.IO) {
        initExtractors()
        videoStreamCache[videoCacheKey(videoId, videoQuality, audioQuality)]?.let { return@withContext it }

        // Tier 1: PipePipeExtractor
        try {
            YoutubeApiDecoder.setLocalDecoder(null)
            val url = "https://www.youtube.com/watch?v=$videoId"
            val streamInfo = PipePipeStreamInfo.getInfo(PipePipeServiceList.YouTube, url)
            resolveVideoFromLists(
                videoId = videoId,
                videoOnlyStreams = streamInfo.videoOnlyStreams,
                progressiveStreams = streamInfo.videoStreams,
                audioStreams = streamInfo.audioStreams,
                durationSeconds = streamInfo.duration.toInt(),
                tierLabel = "Tier 1 PipePipe",
                videoQuality = videoQuality,
                audioQuality = audioQuality,
                videoUrlOf = { it.content },
                videoHeightOf = { it.height },
                videoResolutionOf = { it.resolution },
                audioUrlOf = { it.content },
                audioSuffixOf = { it.format?.suffix },
                audioMimeOf = { it.format?.mimeType },
                audioBitrateOf = { it.averageBitrate }
            )?.let { return@withContext it }
        } catch (e: Exception) {
            Log.w(TAG, "Tier 1 PipePipe video error for $videoId: ${e.message}")
        }

        // Tier 2: BraveNewPipe
        try {
            val url = "https://www.youtube.com/watch?v=$videoId"
            val streamInfo = BraveStreamInfo.getInfo(BraveServiceList.YouTube, url)
            resolveVideoFromLists(
                videoId = videoId,
                videoOnlyStreams = streamInfo.videoOnlyStreams,
                progressiveStreams = streamInfo.videoStreams,
                audioStreams = streamInfo.audioStreams,
                durationSeconds = streamInfo.duration.toInt(),
                tierLabel = "Tier 2 BraveNewPipe",
                videoQuality = videoQuality,
                audioQuality = audioQuality,
                videoUrlOf = { it.content },
                videoHeightOf = { it.height },
                videoResolutionOf = { it.resolution },
                audioUrlOf = { it.content },
                audioSuffixOf = { it.format?.suffix },
                audioMimeOf = { it.format?.mimeType },
                audioBitrateOf = { it.averageBitrate }
            )?.let { return@withContext it }
        } catch (e: Exception) {
            Log.w(TAG, "Tier 2 BraveNewPipe video error for $videoId: ${e.message}")
        }

        null
    }
}
