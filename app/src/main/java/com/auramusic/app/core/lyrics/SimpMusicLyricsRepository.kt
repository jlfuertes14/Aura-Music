package com.auramusic.app.core.lyrics

import com.auramusic.app.core.extractor.SimpMusicExtractorEngine
import com.auramusic.app.domain.model.LyricLine
import com.auramusic.app.domain.model.LyricWord
import com.auramusic.app.domain.model.LyricsData
import com.atilika.kuromoji.ipadic.Tokenizer
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.Request
import org.json.JSONArray
import org.json.JSONObject
import java.net.URLEncoder
import java.util.concurrent.ConcurrentHashMap

object SimpMusicLyricsRepository {
    private val lyricsCache = ConcurrentHashMap<String, LyricsData>()

    suspend fun getLyrics(videoId: String, title: String, artist: String, durationSeconds: Int): LyricsData =
        withContext(Dispatchers.IO) {
            lyricsCache[videoId]?.let { return@withContext it }

            // 1. Primary: Official SimpMusic Lyrics API (https://api-lyrics.simpmusic.org/v1/{videoId})
            fetchFromSimpMusicApi(videoId)?.let { data ->
                lyricsCache[videoId] = data
                return@withContext data
            }

            // 2. Fallback: Direct LRCLIB API (https://lrclib.net/api/search)
            fetchFromLrcLib(videoId, title, artist, durationSeconds)?.let { data ->
                lyricsCache[videoId] = data
                return@withContext data
            }

            LyricsData(
                videoId = videoId,
                plainLyrics = "Instrumental or lyrics not yet indexed for $title — $artist.",
                syncedLines = emptyList(),
                source = "None"
            )
        }

    private fun fetchFromSimpMusicApi(videoId: String): LyricsData? {
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
                val lines = if (rawToParse.isNotBlank()) parseLrcWithRichWords(rawToParse) else emptyList()
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

    private fun fetchFromLrcLib(videoId: String, title: String, artist: String, durationSeconds: Int): LyricsData? {
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
                return record.toLyricsData(videoId, "LRCLIB Exact Match")
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

            chosen.toLyricsData(videoId, "LRCLIB Search")
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

    private fun JSONObject.toLyricsData(videoId: String, sourceName: String): LyricsData? {
        val synced = unescapeLyricsHtml(optString("syncedLyrics").takeUnless { it == "null" }.orEmpty())
        val plain = unescapeLyricsHtml(optString("plainLyrics").takeUnless { it == "null" }.orEmpty())
        val lines = if (synced.isNotBlank()) parseLrcWithRichWords(synced) else emptyList()
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

    private fun parseLrcWithRichWords(lrcText: String): List<LyricLine> {
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
                romanizedText = romanizeIfNonLatin(cleanText)
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

    /** Kuromoji is initialized on the lyrics IO dispatcher, not during app startup. */
    private val japaneseTokenizer: Tokenizer by lazy(LazyThreadSafetyMode.SYNCHRONIZED) { Tokenizer() }

    private fun romanizeIfNonLatin(text: String): String? {
        if (text.isBlank()) return null

        // Kana reliably identifies Japanese text. Han-only lines are ambiguous with Chinese,
        // so leave those alone rather than assigning them Japanese readings without context.
        val hasJapanese = text.any(::isJapaneseKana)
        if (hasJapanese) {
            val reading = runCatching {
                buildString {
                    japaneseTokenizer.tokenize(text).forEach { token ->
                        val tokenReading = token.reading
                        // Unknown words and Latin fragments have no dictionary reading.
                        append(if (tokenReading.isNullOrBlank() || tokenReading == "*") token.surface else tokenReading)
                    }
                }
            }.getOrNull() ?: text

            return romanizeKana(reading).takeIf { it.isNotBlank() && it != text }
        }

        // Keep the existing Hangul romanization behavior.
        val initials = arrayOf(
            "g", "kk", "n", "d", "tt", "r", "m", "b", "pp", "s",
            "ss", "", "j", "jj", "ch", "k", "t", "p", "h"
        )
        val medials = arrayOf(
            "a", "ae", "ya", "yae", "eo", "e", "yeo", "ye", "o", "wa",
            "wae", "oe", "yo", "u", "wo", "we", "wi", "yu", "eu", "ui", "i"
        )
        val finals = arrayOf(
            "", "k", "k", "ks", "n", "nj", "nh", "t", "l", "lg", "lm",
            "lb", "ls", "lt", "lp", "lh", "m", "p", "bs", "t", "t", "ng",
            "t", "t", "k", "t", "p", "t"
        )
        var hasHangul = false
        val hangul = buildString {
            text.forEach { ch ->
                val code = ch.code
                if (code in 0xAC00..0xD7A3) {
                    hasHangul = true
                    val index = code - 0xAC00
                    val initial = index / (21 * 28)
                    val medial = (index % (21 * 28)) / 28
                    val finalIndex = index % 28
                    append(initials[initial]).append(medials[medial]).append(finals[finalIndex])
                } else {
                    append(ch)
                }
            }
        }
        return hangul.takeIf { hasHangul }
    }

    private fun isJapaneseKana(ch: Char): Boolean {
        val code = ch.code
        return code in 0x3040..0x30FF
    }

    /** Kana → Hepburn romaji, including contracted sounds, doubled consonants and long vowels. */
    private fun romanizeKana(text: String): String {
        val kana = java.text.Normalizer.normalize(text, java.text.Normalizer.Form.NFKC)
            .map { ch -> if (ch in 'ァ'..'ヶ') (ch.code - 0x60).toChar() else ch }
            .joinToString("")
        val output = StringBuilder()
        var index = 0
        while (index < kana.length) {
            val ch = kana[index]
            if (ch == 'っ') {
                val next = romanizeKanaUnit(kana, index + 1)
                if (!next.isNullOrEmpty() && next[0] !in "aiueo") output.append(next[0])
                index++
                continue
            }
            if (ch == 'ー') {
                output.lastOrNull()?.takeIf { it in "aiueo" }?.let(output::append)
                index++
                continue
            }

            val pair = if (index + 1 < kana.length) kana.substring(index, index + 2) else ""
            val contracted = YOUON[pair]
            if (contracted != null) {
                output.append(contracted)
                index += 2
                continue
            }
            output.append(KANA_MAP[ch] ?: ch)
            index++
        }
        return output.toString()
    }

    private fun romanizeKanaUnit(kana: String, index: Int): String? {
        if (index >= kana.length) return null
        val pair = if (index + 1 < kana.length) kana.substring(index, index + 2) else ""
        return YOUON[pair] ?: KANA_MAP[kana[index]]
    }

    private val YOUON = mapOf(
        "きゃ" to "kya", "きゅ" to "kyu", "きょ" to "kyo",
        "しゃ" to "sha", "しゅ" to "shu", "しょ" to "sho",
        "ちゃ" to "cha", "ちゅ" to "chu", "ちょ" to "cho",
        "にゃ" to "nya", "にゅ" to "nyu", "にょ" to "nyo",
        "ひゃ" to "hya", "ひゅ" to "hyu", "ひょ" to "hyo",
        "みゃ" to "mya", "みゅ" to "myu", "みょ" to "myo",
        "りゃ" to "rya", "りゅ" to "ryu", "りょ" to "ryo",
        "ぎゃ" to "gya", "ぎゅ" to "gyu", "ぎょ" to "gyo",
        "じゃ" to "ja", "じゅ" to "ju", "じょ" to "jo",
        "びゃ" to "bya", "びゅ" to "byu", "びょ" to "byo",
        "ぴゃ" to "pya", "ぴゅ" to "pyu", "ぴょ" to "pyo",
        "ふぁ" to "fa", "ふぃ" to "fi", "ふぇ" to "fe", "ふぉ" to "fo",
        "うぃ" to "wi", "うぇ" to "we", "てぃ" to "ti", "でぃ" to "di",
        "とぅ" to "tu", "どぅ" to "du", "ゔぁ" to "va", "ゔぃ" to "vi",
        "ゔぇ" to "ve", "ゔぉ" to "vo", "しぇ" to "she", "ちぇ" to "che", "じぇ" to "je"
    )

    private val KANA_MAP = mapOf(
        'あ' to "a", 'い' to "i", 'う' to "u", 'え' to "e", 'お' to "o",
        'か' to "ka", 'き' to "ki", 'く' to "ku", 'け' to "ke", 'こ' to "ko",
        'さ' to "sa", 'し' to "shi", 'す' to "su", 'せ' to "se", 'そ' to "so",
        'た' to "ta", 'ち' to "chi", 'つ' to "tsu", 'て' to "te", 'と' to "to",
        'な' to "na", 'に' to "ni", 'ぬ' to "nu", 'ね' to "ne", 'の' to "no",
        'は' to "ha", 'ひ' to "hi", 'ふ' to "fu", 'へ' to "he", 'ほ' to "ho",
        'ま' to "ma", 'み' to "mi", 'む' to "mu", 'め' to "me", 'も' to "mo",
        'や' to "ya", 'ゆ' to "yu", 'よ' to "yo", 'ら' to "ra", 'り' to "ri",
        'る' to "ru", 'れ' to "re", 'ろ' to "ro", 'わ' to "wa", 'を' to "o", 'ん' to "n",
        'が' to "ga", 'ぎ' to "gi", 'ぐ' to "gu", 'げ' to "ge", 'ご' to "go",
        'ざ' to "za", 'じ' to "ji", 'ず' to "zu", 'ぜ' to "ze", 'ぞ' to "zo",
        'だ' to "da", 'ぢ' to "ji", 'づ' to "zu", 'で' to "de", 'ど' to "do",
        'ば' to "ba", 'び' to "bi", 'ぶ' to "bu", 'べ' to "be", 'ぼ' to "bo",
        'ぱ' to "pa", 'ぴ' to "pi", 'ぷ' to "pu", 'ぺ' to "pe", 'ぽ' to "po",
        'ぁ' to "a", 'ぃ' to "i", 'ぅ' to "u", 'ぇ' to "e", 'ぉ' to "o",
        'ゃ' to "ya", 'ゅ' to "yu", 'ょ' to "yo", 'ゔ' to "vu", 'ゕ' to "ka", 'ゖ' to "ke"
    )
}
