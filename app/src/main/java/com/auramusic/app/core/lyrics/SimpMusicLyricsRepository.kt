package com.auramusic.app.core.lyrics

import com.auramusic.app.core.extractor.SimpMusicExtractorEngine
import com.auramusic.app.domain.model.LyricLine
import com.auramusic.app.domain.model.LyricWord
import com.auramusic.app.domain.model.LyricsData
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.Request
import org.json.JSONArray
import org.json.JSONObject
import java.net.URLEncoder
import java.util.concurrent.ConcurrentHashMap

object SimpMusicLyricsRepository {
    private data class CachedLyricsEntry(
        val data: LyricsData,
        val languages: Set<String>
    )

    private val lyricsCache = ConcurrentHashMap<String, CachedLyricsEntry>()

    @Volatile
    private var currentEnabledLanguages: Set<String> = LyricsRomanizer.DEFAULT_LANGUAGES

    fun clearCache() {
        lyricsCache.clear()
    }

    fun setEnabledRomanizationLanguages(languages: Set<String>) {
        currentEnabledLanguages = languages.toSet()
    }

    fun applyRomanization(
        lyrics: LyricsData,
        enabledLanguages: Set<String> = currentEnabledLanguages
    ): LyricsData {
        if (lyrics.syncedLines.isEmpty()) {
            return lyrics.copy(hasRomanization = false)
        }
        val updatedLines = lyrics.syncedLines.map { line ->
            line.copy(romanizedText = LyricsRomanizer.romanizeLine(line.text, enabledLanguages))
        }
        return lyrics.copy(
            syncedLines = updatedLines,
            hasRomanization = updatedLines.any { !it.romanizedText.isNullOrBlank() }
        )
    }

    suspend fun getLyrics(
        videoId: String,
        title: String,
        artist: String,
        durationSeconds: Int,
        preferredProvider: String = "SimpMusic"
    ): LyricsData =
        withContext(Dispatchers.IO) {
            val activeLanguages = currentEnabledLanguages
            val cacheKey = "${videoId}_$preferredProvider"
            lyricsCache[cacheKey]?.let { cached ->
                if (cached.languages == activeLanguages) {
                    return@withContext cached.data
                }
                val updated = applyRomanization(cached.data, activeLanguages)
                lyricsCache[cacheKey] = CachedLyricsEntry(updated, activeLanguages)
                return@withContext updated
            }

            if (preferredProvider.equals("LRCLIB", ignoreCase = true)) {
                // 1. Primary when user selects LRCLIB: Direct LRCLIB API
                fetchFromLrcLib(videoId, title, artist, durationSeconds, activeLanguages)?.let { data ->
                    lyricsCache[cacheKey] = CachedLyricsEntry(data, activeLanguages)
                    return@withContext data
                }
                // 2. Fallback: Official SimpMusic Lyrics API
                fetchFromSimpMusicApi(videoId, activeLanguages)?.let { data ->
                    lyricsCache[cacheKey] = CachedLyricsEntry(data, activeLanguages)
                    return@withContext data
                }
            } else {
                // 1. Primary: Official SimpMusic Lyrics API (https://api-lyrics.simpmusic.org/v1/{videoId})
                fetchFromSimpMusicApi(videoId, activeLanguages)?.let { data ->
                    lyricsCache[cacheKey] = CachedLyricsEntry(data, activeLanguages)
                    return@withContext data
                }
                // 2. Fallback: Direct LRCLIB API (https://lrclib.net/api/search)
                fetchFromLrcLib(videoId, title, artist, durationSeconds, activeLanguages)?.let { data ->
                    lyricsCache[cacheKey] = CachedLyricsEntry(data, activeLanguages)
                    return@withContext data
                }
            }

            LyricsData(
                videoId = videoId,
                plainLyrics = "Instrumental or lyrics not yet indexed for $title — $artist.",
                syncedLines = emptyList(),
                source = "None"
            )
        }

    private fun fetchFromSimpMusicApi(
        videoId: String,
        enabledLanguages: Set<String>
    ): LyricsData? {
        return try {
            val req = Request.Builder()
                .url("https://api-lyrics.simpmusic.org/v1/$videoId")
                .header("Accept", "application/json")
                .header("User-Agent", "SimpMusicLyrics/1.0.0")
                .build()

            SimpMusicExtractorEngine.httpClient.newCall(req).execute().use { resp ->
                if (!resp.isSuccessful) return null
                val root = JSONObject(resp.body?.string() ?: return null)
                if (!root.optBoolean("success", false)) return null
                val dataArr = root.optJSONArray("data") ?: return null
                if (dataArr.length() == 0) return null

                // Pick item with richSyncLyrics first, otherwise syncedLyrics
                var bestObj: JSONObject = dataArr.getJSONObject(0)
                for (i in 0 until dataArr.length()) {
                    val item = dataArr.getJSONObject(i)
                    if (item.optString("richSyncLyrics").isNotBlank()) {
                        bestObj = item
                        break
                    }
                }

                val richLrc = unescapeLyricsHtml(bestObj.optString("richSyncLyrics"))
                val syncedLrc = unescapeLyricsHtml(bestObj.optString("syncedLyrics"))
                val plain = unescapeLyricsHtml(bestObj.optString("plainLyric"))

                val rawToParse = if (richLrc.isNotBlank()) richLrc else syncedLrc
                val lines = if (rawToParse.isNotBlank()) {
                    parseLrcWithRichWords(rawToParse, enabledLanguages)
                } else {
                    emptyList()
                }
                val hasRomanized = lines.any { !it.romanizedText.isNullOrBlank() }

                LyricsData(
                    videoId = videoId,
                    plainLyrics = plain.ifBlank { lines.joinToString("\n") { it.text } },
                    syncedLines = lines,
                    source = if (richLrc.isNotBlank()) "SimpMusic RichSync (Word-by-Word)" else "SimpMusic Synced LRC",
                    hasRomanization = hasRomanized
                )
            }
        } catch (e: Exception) {
            null
        }
    }

    private fun fetchFromLrcLib(
        videoId: String,
        title: String,
        artist: String,
        durationSeconds: Int,
        enabledLanguages: Set<String>
    ): LyricsData? {
        return try {
            val cleanTitle = normalizeLyricsTitle(title, artist)
            val cleanArtist = normalizeLyricsArtist(artist)
            if (cleanTitle.isBlank() || cleanArtist.isBlank()) return null

            val params = linkedMapOf(
                "track_name" to cleanTitle,
                "artist_name" to cleanArtist
            ).apply {
                if (durationSeconds > 0) put("duration", durationSeconds.toString())
            }
            // LRCLIB's signature endpoint uses track + artist + duration for precise matching.
            fetchLrcLibRecord("https://lrclib.net/api/get?${encodeQuery(params)}")?.let { record ->
                return record.toLyricsData(videoId, "LRCLIB Exact Match", enabledLanguages)
            }

            // Search with structured metadata first. Some YouTube uploads use channel names or
            // decorated titles, so also retry the normalized title without trusting the uploader.
            val queries = listOf(
                mapOf("track_name" to cleanTitle, "artist_name" to cleanArtist),
                mapOf("track_name" to cleanTitle),
                mapOf("q" to "$cleanArtist $cleanTitle"),
                mapOf("q" to cleanTitle)
            ).distinct()

            val candidates = queries.flatMap { queryParams ->
                fetchLrcLibSearch(queryParams)
            }.distinctBy { it.optString("id").ifBlank { it.optString("trackName") + it.optString("artistName") } }

            val chosen = candidates
                .map { it to lyricsMatchScore(it, cleanTitle, cleanArtist, durationSeconds) }
                .filter { (_, score) -> score > 0 }
                .maxByOrNull { (_, score) -> score }
                ?.first ?: return null

            chosen.toLyricsData(videoId, "LRCLIB Search", enabledLanguages)
        } catch (e: Exception) {
            null
        }
    }

    private fun fetchLrcLibRecord(url: String): JSONObject? = try {
        val req = Request.Builder().url(url).header("User-Agent", "AuraMusicKotlin/1.0.0").build()
        SimpMusicExtractorEngine.httpClient.newCall(req).execute().use { resp ->
            if (!resp.isSuccessful) null else JSONObject(resp.body?.string() ?: return null)
        }
    } catch (_: Exception) {
        null
    }

    private fun fetchLrcLibSearch(params: Map<String, String>): List<JSONObject> = try {
        val req = Request.Builder()
            .url("https://lrclib.net/api/search?${encodeQuery(params)}")
            .header("User-Agent", "AuraMusicKotlin/1.0.0")
            .build()
        SimpMusicExtractorEngine.httpClient.newCall(req).execute().use { resp ->
            if (!resp.isSuccessful) return emptyList()
            val array = JSONArray(resp.body?.string() ?: return emptyList())
            List(array.length()) { index -> array.getJSONObject(index) }
        }
    } catch (_: Exception) {
        emptyList()
    }

    private fun JSONObject.toLyricsData(
        videoId: String,
        sourceName: String,
        enabledLanguages: Set<String>
    ): LyricsData? {
        val synced = unescapeLyricsHtml(optString("syncedLyrics").takeUnless { it == "null" }.orEmpty())
        val plain = unescapeLyricsHtml(optString("plainLyrics").takeUnless { it == "null" }.orEmpty())
        val lines = if (synced.isNotBlank()) parseLrcWithRichWords(synced, enabledLanguages) else emptyList()
        if (lines.isEmpty() && plain.isBlank()) return null
        return LyricsData(
            videoId = videoId,
            plainLyrics = plain.ifBlank { lines.joinToString("\n") { it.text } },
            syncedLines = lines,
            source = sourceName,
            hasRomanization = lines.any { !it.romanizedText.isNullOrBlank() }
        )
    }

    private fun normalizeLyricsTitle(raw: String, artist: String): String {
        var title = raw
            .replace(Regex("(?i)\\[[^]]*(official|music video|lyrics?|audio|visuali[sz]er|4k|hd|mv)[^]]*]"), " ")
            .replace(Regex("(?i)\\([^)]*(official|music video|lyrics?|audio|visuali[sz]er|4k|hd|mv|from\\s+|soundtrack)[^)]*\\)"), " ")
            .replace(Regex("(?i)\\b(official\\s+(music\\s+)?video|official\\s+audio|official\\s+mv|lyrics?\\s+video|lyrics?|audio|visuali[sz]er|4k|hd|mv|m/v)\\b"), " ")
        if (title.contains(" - ")) {
            val prefix = title.substringBefore(" - ").trim()
            val suffix = title.substringAfter(" - ").trim()
            if (suffix.isNotBlank()) {
                val artistTokens = normalizeLyricsArtist(artist).lowercase().split(Regex("[\\s,]+")).filter { it.length >= 2 }
                val prefixTokens = prefix.lowercase().split(Regex("[\\s,]+")).filter { it.length >= 2 }
                if (prefixTokens.any { artistTokens.contains(it) }) {
                    title = suffix
                }
            }
        }
        normalizeLyricsArtist(artist).split(",", "&", " feat. ", " ft. ").map(String::trim)
            .filter { it.length >= 3 }
            .forEach { creditedName ->
                title = title.replace(
                    Regex("(?i)\\s*[-|•:]\\s*${Regex.escape(creditedName)}\\s*$"),
                    ""
                )
            }
        return title
            .replace(Regex("\\s+[-|•:]\\s*$"), "")
            .replace(Regex("\\s+"), " ")
            .trim(' ', '-', '|', '•', ':')
    }

    private fun normalizeLyricsArtist(raw: String): String = raw
        .replace(Regex("(?i)\\s*[-|•:]\\s*(official|topic|vevo|music|channel).*$"), "")
        .replace(Regex("(?i)\\s+(ft\\.?|feat\\.?|featuring)\\s+.*$"), "")
        .replace(Regex("\\s+"), " ")
        .trim(' ', '-', '|', '•', ':')

    private fun encodeQuery(params: Map<String, String>): String = params.entries.joinToString("&") {
        "${URLEncoder.encode(it.key, "UTF-8")}=${URLEncoder.encode(it.value, "UTF-8")}"
    }

    private fun lyricsMatchScore(candidate: JSONObject, title: String, artist: String, duration: Int): Int {
        val candidateTitle = normalizeForLyricsMatch(candidate.optString("trackName").ifBlank { candidate.optString("name") })
        val candidateArtist = normalizeForLyricsMatch(candidate.optString("artistName"))
        val expectedTitle = normalizeForLyricsMatch(title)
        val expectedArtist = normalizeForLyricsMatch(artist)
        if (candidateTitle.isBlank() || expectedTitle.isBlank()) return 0

        var score = when {
            candidateTitle == expectedTitle -> 100
            candidateTitle.contains(expectedTitle) || expectedTitle.contains(candidateTitle) -> 55
            else -> 0
        }
        if (score == 0) return 0
        if (candidateArtist == expectedArtist) score += 35
        else if (candidateArtist.contains(expectedArtist) || expectedArtist.contains(candidateArtist)) score += 18

        val candidateDuration = candidate.optInt("duration", 0)
        if (duration > 0 && candidateDuration > 0) {
            val delta = kotlin.math.abs(candidateDuration - duration)
            if (delta <= 2) score += 25 else if (delta <= 8) score += 8 else score -= 20
        }
        if (candidate.optString("syncedLyrics").isNotBlank()) score += 5
        return score
    }

    private fun normalizeForLyricsMatch(text: String): String = text
        .lowercase()
        .replace(Regex("(?i)\\b(official|music|video|audio|lyrics?|visuali[sz]er|feat|featuring|ft)\\b"), " ")
        .replace(Regex("[^\\p{L}\\p{N}]"), "")

    private fun parseLrcWithRichWords(
        lrcText: String,
        enabledLanguages: Set<String> = currentEnabledLanguages
    ): List<LyricLine> {
        val lineRegex = Regex("^\\[(\\d{1,2}):(\\d{2})(?:\\.(\\d{1,3}))?](.*)$")
        val wordTagRegex = Regex("<(\\d{1,2}):(\\d{2})(?:\\.(\\d{1,3}))?>\\s*([^<]+)")

        data class RawLine(
            val startSec: Float,
            val rawContent: String
        )

        val rawLines = lrcText.lines().mapNotNull { line ->
            val match = lineRegex.find(line.trim()) ?: return@mapNotNull null
            val min = match.groupValues[1].toIntOrNull() ?: 0
            val sec = match.groupValues[2].toIntOrNull() ?: 0
            val msStr = match.groupValues[3].padEnd(3, '0')
            val ms = msStr.toIntOrNull() ?: 0
            val totalSec = min * 60f + sec + (ms / 1000f)
            RawLine(totalSec, match.groupValues[4].trim())
        }.sortedBy { it.startSec }

        if (rawLines.isEmpty()) return emptyList()

        return rawLines.mapIndexedNotNull { index, raw ->
            val nextLineSec = rawLines.getOrNull(index + 1)?.startSec ?: (raw.startSec + 4.5f)
            val lineEndSec = nextLineSec.coerceAtLeast(raw.startSec + 0.6f)

            val wordMatches = wordTagRegex.findAll(raw.rawContent).toList()
            val words = mutableListOf<LyricWord>()
            val cleanText: String

            if (wordMatches.isNotEmpty()) {
                val parsedWords = wordMatches.map { wm ->
                    val wMin = wm.groupValues[1].toIntOrNull() ?: 0
                    val wSec = wm.groupValues[2].toIntOrNull() ?: 0
                    val wMs = wm.groupValues[3].padEnd(3, '0').toIntOrNull() ?: 0
                    val wStart = wMin * 60f + wSec + (wMs / 1000f)
                    wStart to unescapeLyricsHtml(wm.groupValues[4].trim())
                }.filter { it.second.isNotEmpty() }

                for (wIdx in parsedWords.indices) {
                    val (wStart, wText) = parsedWords[wIdx]
                    val wEnd = parsedWords.getOrNull(wIdx + 1)?.first ?: lineEndSec
                    words.add(
                        LyricWord(
                            word = wText,
                            startTimeSec = wStart,
                            endTimeSec = wEnd.coerceAtLeast(wStart + 0.12f)
                        )
                    )
                }
                cleanText = parsedWords.joinToString(" ") { it.second }
            } else {
                val rawNoTags = raw.rawContent.replace(Regex("<[^>]+>"), "").trim()
                cleanText = unescapeLyricsHtml(rawNoTags)
                if (cleanText.isNotEmpty()) {
                    val splitTokens = cleanText.split(Regex("\\s+")).filter { it.isNotBlank() }
                    val durationSpan = (lineEndSec - raw.startSec).coerceAtLeast(0.5f)
                    val step = durationSpan / splitTokens.size.coerceAtLeast(1)
                    splitTokens.forEachIndexed { tIdx, token ->
                        val wStart = raw.startSec + tIdx * step
                        words.add(
                            LyricWord(
                                word = token,
                                startTimeSec = wStart,
                                endTimeSec = wStart + step
                            )
                        )
                    }
                }
            }

            if (cleanText.isEmpty()) return@mapIndexedNotNull null

            LyricLine(
                timeSec = raw.startSec,
                endTimeSec = lineEndSec,
                text = cleanText,
                words = words,
                romanizedText = LyricsRomanizer.romanizeLine(cleanText, enabledLanguages)
            )
        }
    }

    /**
     * Unescapes HTML entities commonly found in lyrics databases (e.g. &#x27; for apostrophe, &quot;, &amp;, etc.)
     */
    fun unescapeLyricsHtml(text: String): String {
        if (!text.contains("&")) return text
        var res = text
        // 1. Hexadecimal numeric entities (e.g. &#x27; -> ', &#x2F; -> /)
        res = Regex("&#x([0-9a-fA-F]+);").replace(res) { match ->
            val code = match.groupValues[1].toIntOrNull(16)
            if (code != null) code.toChar().toString() else match.value
        }
        // 2. Decimal numeric entities (e.g. &#39; -> ', &#8217; -> ’, &#8216; -> ‘)
        res = Regex("&#([0-9]+);").replace(res) { match ->
            val code = match.groupValues[1].toIntOrNull()
            if (code != null) code.toChar().toString() else match.value
        }
        // 3. Standard named XML/HTML entities
        res = res
            .replace("&quot;", "\"")
            .replace("&apos;", "'")
            .replace("&amp;", "&")
            .replace("&lt;", "<")
            .replace("&gt;", ">")
            .replace("&nbsp;", " ")
            .replace("&hellip;", "…")
            .replace("&mdash;", "—")
            .replace("&ndash;", "–")
            .replace("&rsquo;", "’")
            .replace("&lsquo;", "‘")
            .replace("&rdquo;", "”")
            .replace("&ldquo;", "“")
        return res
    }
}
