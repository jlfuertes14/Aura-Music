package com.auramusic.app.core.integrations

import java.net.URI
import java.net.URLDecoder

sealed class AuraDeepLink {
    data class Track(val videoId: String) : AuraDeepLink()
    data class Playlist(val playlistId: String) : AuraDeepLink()
    data class Search(val query: String) : AuraDeepLink()
}

object DeepLinkParser {
    private val URL_REGEX = Regex("""https?://[^\s<>"'\[\]{}|\\^`]+""", RegexOption.IGNORE_CASE)
    private val YOUTUBE_HOSTS = setOf(
        "youtube.com",
        "www.youtube.com",
        "m.youtube.com",
        "music.youtube.com",
        "youtu.be",
        "www.youtu.be"
    )

    fun parse(rawTextOrUri: String?): AuraDeepLink? {
        val raw = rawTextOrUri?.trim()?.takeIf { it.isNotBlank() } ?: return null
        val candidateUrl = extractCandidateUrl(raw) ?: return null

        val uri = runCatching { URI(candidateUrl) }.getOrNull() ?: return null
        val scheme = uri.scheme?.lowercase().orEmpty()
        val host = uri.host?.lowercase().orEmpty()
        val pathSegments = uri.path
            ?.split('/')
            ?.filter { it.isNotBlank() }
            .orEmpty()
        val queryParams = parseQueryParams(uri.rawQuery)

        if (scheme == "auramusic" || scheme == "aura") {
            return when (host) {
                "track" -> pathSegments.firstOrNull()?.takeIf { it.isNotBlank() }?.let { AuraDeepLink.Track(it) }
                "playlist" -> pathSegments.firstOrNull()?.takeIf { it.isNotBlank() }?.let { AuraDeepLink.Playlist(it) }
                "search" -> queryParams["q"]?.takeIf { it.isNotBlank() }?.let { AuraDeepLink.Search(it) }
                else -> null
            }
        }

        if (host !in YOUTUBE_HOSTS) return null

        if (host == "youtu.be" || host == "www.youtu.be") {
            val id = pathSegments.firstOrNull()?.takeIf { it.isNotBlank() } ?: return null
            return AuraDeepLink.Track(id)
        }

        val firstSeg = pathSegments.firstOrNull()?.lowercase().orEmpty()
        val secondSeg = pathSegments.getOrNull(1)?.takeIf { it.isNotBlank() }

        return when (firstSeg) {
            "watch" -> {
                val videoId = queryParams["v"]?.takeIf { it.isNotBlank() }
                if (videoId != null) {
                    AuraDeepLink.Track(videoId)
                } else {
                    queryParams["list"]?.takeIf { it.isNotBlank() }?.let { AuraDeepLink.Playlist(it) }
                }
            }
            "playlist" -> {
                queryParams["list"]?.takeIf { it.isNotBlank() }?.let { AuraDeepLink.Playlist(it) }
            }
            "shorts", "live", "embed" -> {
                secondSeg?.let { AuraDeepLink.Track(it) }
            }
            "browse" -> {
                secondSeg?.let { AuraDeepLink.Playlist(it.removePrefix("VL")) }
            }
            "search" -> {
                queryParams["q"]?.takeIf { it.isNotBlank() }?.let { AuraDeepLink.Search(it) }
            }
            "results" -> {
                queryParams["search_query"]?.takeIf { it.isNotBlank() }?.let { AuraDeepLink.Search(it) }
            }
            else -> {
                queryParams["v"]?.takeIf { it.isNotBlank() }?.let { AuraDeepLink.Track(it) }
                    ?: queryParams["list"]?.takeIf { it.isNotBlank() }?.let { AuraDeepLink.Playlist(it) }
            }
        }
    }

    private fun extractCandidateUrl(raw: String): String? {
        if (raw.startsWith("auramusic://", ignoreCase = true) ||
            raw.startsWith("aura://", ignoreCase = true)
        ) {
            return raw
        }
        val matched = URL_REGEX.find(raw)?.value ?: if (raw.contains("://")) raw else return null
        return matched.trimEnd('.', ',', ';', '!', '?', ')', ']', '"', '\'')
    }

    private fun parseQueryParams(rawQuery: String?): Map<String, String> {
        if (rawQuery.isNullOrBlank()) return emptyMap()
        val map = LinkedHashMap<String, String>()
        for (pair in rawQuery.split('&')) {
            if (pair.isBlank()) continue
            val idx = pair.indexOf('=')
            val key = if (idx >= 0) decodeComponent(pair.substring(0, idx)) else decodeComponent(pair)
            val value = if (idx >= 0) decodeComponent(pair.substring(idx + 1)) else ""
            if (key.isNotBlank() && !map.containsKey(key)) {
                map[key] = value
            }
        }
        return map
    }

    private fun decodeComponent(value: String): String =
        runCatching { URLDecoder.decode(value, "UTF-8") }.getOrDefault(value)
}
