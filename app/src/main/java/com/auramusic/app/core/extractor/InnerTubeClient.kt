package com.auramusic.app.core.extractor

import android.util.Log
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.TimeUnit

internal object InnerTubeClient {
    const val TAG = "SimpMusicExtractor"
    const val USER_AGENT =
        "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/131.0.0.0 Safari/537.36"
    private const val YTM_ORIGIN = "https://music.youtube.com"
    private const val YTM_REFERER = "https://music.youtube.com/"
    private val JSON_MEDIA_TYPE = "application/json".toMediaType()

    enum class ClientType(val clientName: String, val clientVersion: String) {
        WEB_REMIX("WEB_REMIX", "1.20250312.01.00"),
        WEB("WEB", "2.20250312.04.00"),
        ANDROID("ANDROID_MUSIC", "7.29.52")
    }

    @Volatile
    var contentCountry: String = "US"

    fun effectiveGl(): String =
        contentCountry.takeIf { it.isNotBlank() && !it.equals("ZZ", ignoreCase = true) } ?: "US"

    val httpClient: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .connectTimeout(10, TimeUnit.SECONDS)
            .readTimeout(15, TimeUnit.SECONDS)
            .followRedirects(true)
            .followSslRedirects(true)
            .build()
    }

    val fastVerifyClient: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .connectTimeout(4, TimeUnit.SECONDS)
            .readTimeout(4, TimeUnit.SECONDS)
            .followRedirects(true)
            .followSslRedirects(true)
            .build()
    }

    fun buildContext(
        clientType: ClientType = ClientType.WEB_REMIX,
        gl: String = effectiveGl(),
        hl: String = "en"
    ): JSONObject = JSONObject().apply {
        put("client", JSONObject().apply {
            put("clientName", clientType.clientName)
            put("clientVersion", clientType.clientVersion)
            put("hl", hl)
            put("gl", gl)
        })
    }

    fun postInnerTube(
        endpoint: String,
        clientType: ClientType = ClientType.WEB_REMIX,
        gl: String = effectiveGl(),
        hl: String = "en",
        includeReferer: Boolean = true,
        configurePayload: JSONObject.() -> Unit = {}
    ): JSONObject? {
        val payload = JSONObject().apply {
            put("context", buildContext(clientType, gl, hl))
            configurePayload()
        }
        val reqBuilder = Request.Builder()
            .url("$YTM_ORIGIN/youtubei/v1/$endpoint?prettyPrint=false")
            .header("User-Agent", USER_AGENT)
            .header("Origin", YTM_ORIGIN)
        if (includeReferer) {
            reqBuilder.header("Referer", YTM_REFERER)
        }
        val req = reqBuilder
            .post(payload.toString().toRequestBody(JSON_MEDIA_TYPE))
            .build()

        return try {
            httpClient.newCall(req).execute().use { resp ->
                if (!resp.isSuccessful) return null
                val body = resp.body?.string()?.ifBlank { "{}" } ?: "{}"
                JSONObject(body)
            }
        } catch (e: Exception) {
            Log.w(TAG, "InnerTube $endpoint request error: ${e.message}")
            null
        }
    }
}

internal fun findRenderer(value: Any?, rendererName: String): JSONObject? = when (value) {
    is JSONObject -> {
        value.optJSONObject(rendererName)
            ?: value.keys().asSequence().mapNotNull { key -> findRenderer(value.opt(key), rendererName) }.firstOrNull()
    }
    is JSONArray -> (0 until value.length()).asSequence()
        .mapNotNull { index -> findRenderer(value.opt(index), rendererName) }
        .firstOrNull()
    else -> null
}

internal fun JSONArray?.joinRunsText(separator: String = "", skipBullet: Boolean = false): String {
    if (this == null || length() == 0) return ""
    val parts = ArrayList<String>(length())
    for (i in 0 until length()) {
        val raw = optJSONObject(i)?.optString("text").orEmpty()
        val text = if (skipBullet) raw.trim() else raw
        if (skipBullet) {
            if (text.isNotEmpty() && text != "•") parts.add(text)
        } else {
            parts.add(text)
        }
    }
    return parts.joinToString(separator).trim()
}

internal fun JSONArray?.lastThumbnailUrl(): String? {
    if (this == null || length() == 0) return null
    return optJSONObject(length() - 1)?.optString("url")?.takeIf { it.isNotBlank() }
}

internal fun JSONObject.extractMusicThumbnailUrl(): String? {
    val thumbContainer = optJSONObject("thumbnail") ?: optJSONObject("thumbnailRenderer")
    val thumbs = thumbContainer?.optJSONObject("musicThumbnailRenderer")
        ?.optJSONObject("thumbnail")
        ?.optJSONArray("thumbnails")
        ?: thumbContainer?.optJSONObject("croppedSquareThumbnailRenderer")
            ?.optJSONObject("thumbnail")
            ?.optJSONArray("thumbnails")
    return thumbs.lastThumbnailUrl()
}

internal fun JSONObject.optMusicVideoType(): String? =
    optJSONObject("watchEndpointMusicSupportedConfigs")
        ?.optJSONObject("watchEndpointMusicConfig")
        ?.optString("musicVideoType")
        ?.takeIf { it.startsWith("MUSIC_VIDEO_TYPE_") }

internal fun innerTubeMusicVideoType(item: JSONObject): String? {
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

internal fun parseDurationText(text: String): Int {
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

internal fun parseCompactViewCount(metadata: List<String>): Long? {
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

internal fun stripBrowseIdPrefixes(rawId: String): String {
    var cleanId = rawId.trim()
    val prefixes = listOf("album_", "yt_album_", "pl_", "track_", "pod_", "misc_", "yt_")
    while (prefixes.any { cleanId.startsWith(it) }) {
        for (prefix in prefixes) {
            cleanId = cleanId.removePrefix(prefix)
        }
        cleanId = cleanId.trim()
    }
    return cleanId
}

internal fun normalizeYtmBrowseId(cleanId: String, isPodcastShow: Boolean = false): String = when {
    isPodcastShow -> cleanId
    cleanId.startsWith("VL") ||
        cleanId.startsWith("MPRE") ||
        cleanId.startsWith("FEmusic_") ||
        cleanId.startsWith("MPSP") ||
        cleanId.startsWith("MP_") -> cleanId
    cleanId.startsWith("OLAK5uy_") || cleanId.startsWith("PL") || cleanId.startsWith("RD") -> "VL$cleanId"
    else -> if (cleanId.startsWith("VL")) cleanId else "VL$cleanId"
}
