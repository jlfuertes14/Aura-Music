package com.auramusic.app.core.lyrics

import com.auramusic.app.core.extractor.SimpMusicExtractorEngine
import com.auramusic.app.domain.model.LyricLine
import com.auramusic.app.domain.model.LyricWord
import com.auramusic.app.domain.model.LyricsData
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import org.w3c.dom.Element
import org.w3c.dom.Node
import org.xml.sax.InputSource
import java.io.StringReader
import java.net.URLEncoder
import javax.xml.parsers.DocumentBuilderFactory

/**
 * Fetches rich word-synced TTML lyrics from BetterLyrics (`lyrics-api.boidu.dev`)
 * and timedtext XML captions from YouTube as a fallback lyrics provider.
 */
object BetterLyricsProvider {
    private const val BETTER_LYRICS_URL = "https://lyrics-api.boidu.dev/getLyrics"
    private const val YT_PLAYER_URL = "https://www.youtube.com/youtubei/v1/player?prettyPrint=false"
    private const val USER_AGENT =
        "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/131.0.0.0 Safari/537.36"
    private val JSON_MEDIA_TYPE = "application/json; charset=utf-8".toMediaType()

    suspend fun fetchBetterLyrics(
        videoId: String,
        title: String,
        artist: String,
        durationSeconds: Int,
        album: String = ""
    ): LyricsData? = withContext(Dispatchers.IO) {
        val cleanTitle = title.trim()
        val cleanArtist = artist.trim()
        if (cleanTitle.isBlank() || cleanArtist.isBlank()) return@withContext null

        val params = linkedMapOf(
            "s" to cleanTitle,
            "a" to cleanArtist
        ).apply {
            if (durationSeconds > 0) put("d", durationSeconds.toString())
            val cleanAlbum = album.trim()
            if (cleanAlbum.isNotBlank() && !cleanAlbum.equals("YouTube Music", ignoreCase = true)) {
                put("al", cleanAlbum)
            }
        }
        val query = params.entries.joinToString("&") {
            "${URLEncoder.encode(it.key, "UTF-8")}=${URLEncoder.encode(it.value, "UTF-8")}"
        }

        return@withContext try {
            val req = Request.Builder()
                .url("$BETTER_LYRICS_URL?$query")
                .header("Accept", "application/json, application/ttml+xml, text/xml, */*")
                .header("User-Agent", "AuraMusicKotlin/1.0.0")
                .build()

            SimpMusicExtractorEngine.httpClient.newCall(req).execute().use { resp ->
                if (!resp.isSuccessful) return@use null
                val rawBody = resp.body?.string()?.trim().orEmpty()
                if (rawBody.isBlank()) return@use null

                val ttmlXml = extractTtmlFromResponse(rawBody) ?: return@use null
                parseTtmlLyrics(ttmlXml, videoId)
            }
        } catch (_: Exception) {
            null
        }
    }

    private fun extractTtmlFromResponse(rawBody: String): String? {
        if (rawBody.startsWith("<")) return rawBody
        if (!rawBody.startsWith("{")) return null
        return try {
            val root = JSONObject(rawBody)
            val direct = root.optString("ttml").takeIf { it.isNotBlank() }
                ?: root.optString("lyrics").takeIf { it.trimStart().startsWith("<") }
                ?: root.optJSONObject("data")?.let { dataObj ->
                    dataObj.optString("ttml").takeIf { it.isNotBlank() }
                        ?: dataObj.optString("lyrics").takeIf { it.trimStart().startsWith("<") }
                }
            direct
        } catch (_: Exception) {
            null
        }
    }

    /**
     * Parses a TTML XML document into [LyricsData] with line and word-level timestamps.
     * Supports `<p begin="..." end="...">` and `<span begin="..." end="...">` elements
     * across `mm:ss.mmm`, `hh:mm:ss.mmm`, `ss.ms`, `s`, and `ms` timestamp formats.
     */
    internal fun parseTtmlLyrics(ttmlXml: String, videoId: String): LyricsData? {
        val trimmed = ttmlXml.trim().trimStart('\uFEFF')
        if (trimmed.isBlank() || !trimmed.contains("<")) return null

        return try {
            val doc = newSafeDocumentBuilder().parse(InputSource(StringReader(trimmed)))
            val allElements = doc.getElementsByTagName("*")
            val pElements = ArrayList<Element>()
            for (i in 0 until allElements.length) {
                val node = allElements.item(i) as? Element ?: continue
                if (localTagName(node) == "p") {
                    pElements.add(node)
                }
            }
            if (pElements.isEmpty()) return null

            data class RawParagraph(
                val startSec: Float,
                val explicitEndSec: Float?,
                val text: String,
                val words: List<LyricWord>
            )

            val rawParagraphs = ArrayList<RawParagraph>(pElements.size)
            for (p in pElements) {
                val spanNodes = collectTimedSpans(p)
                val pBegin = parseTtmlTimestampSec(p.getAttribute("begin"))
                    ?: spanNodes.firstOrNull()?.startSec
                    ?: continue
                val pDur = parseTtmlTimestampSec(p.getAttribute("dur"))
                val pEnd = parseTtmlTimestampSec(p.getAttribute("end"))
                    ?: pDur?.let { pBegin + it }
                    ?: spanNodes.lastOrNull()?.endSec

                val textBuilder = StringBuilder()
                appendVisibleText(p, textBuilder)
                val cleanLineText = normalizeSpaces(
                    SimpMusicLyricsRepository.unescapeLyricsHtml(textBuilder.toString())
                )
                if (cleanLineText.isBlank()) continue

                val lineEndEstimate = (pEnd ?: (pBegin + 4.5f)).coerceAtLeast(pBegin + 0.2f)
                val words = if (spanNodes.isNotEmpty()) {
                    spanNodes.mapIndexedNotNull { idx, span ->
                        val cleanedWord = normalizeSpaces(
                            SimpMusicLyricsRepository.unescapeLyricsHtml(span.text)
                        )
                        if (cleanedWord.isBlank()) return@mapIndexedNotNull null
                        val wEnd = span.endSec
                            ?: spanNodes.getOrNull(idx + 1)?.startSec
                            ?: lineEndEstimate
                        LyricWord(
                            word = cleanedWord,
                            startTimeSec = span.startSec,
                            endTimeSec = wEnd.coerceAtLeast(span.startSec + 0.05f)
                        )
                    }
                } else {
                    synthesizeEvenWords(cleanLineText, pBegin, lineEndEstimate)
                }

                rawParagraphs.add(
                    RawParagraph(
                        startSec = pBegin,
                        explicitEndSec = pEnd,
                        text = cleanLineText,
                        words = words
                    )
                )
            }

            if (rawParagraphs.isEmpty()) return null

            val sorted = rawParagraphs.sortedBy { it.startSec }
            val lines = sorted.mapIndexed { idx, para ->
                val nextStart = sorted.getOrNull(idx + 1)?.startSec
                val resolvedEnd = (para.explicitEndSec ?: nextStart ?: (para.startSec + 4.5f))
                    .coerceAtLeast(para.startSec + 0.2f)
                LyricLine(
                    timeSec = para.startSec,
                    endTimeSec = resolvedEnd,
                    text = para.text,
                    words = para.words
                )
            }

            LyricsData(
                videoId = videoId,
                plainLyrics = lines.joinToString("\n") { it.text },
                syncedLines = lines,
                source = "BetterLyrics (TTML)",
                hasRomanization = false
            )
        } catch (_: Exception) {
            null
        }
    }

    private data class TimedSpan(
        val startSec: Float,
        val endSec: Float?,
        val text: String
    )

    private fun collectTimedSpans(element: Element): List<TimedSpan> {
        val result = ArrayList<TimedSpan>()
        val children = element.childNodes
        for (i in 0 until children.length) {
            val child = children.item(i) as? Element ?: continue
            if (localTagName(child) == "span") {
                val nested = collectTimedSpans(child)
                if (nested.isNotEmpty()) {
                    result.addAll(nested)
                } else {
                    val start = parseTtmlTimestampSec(child.getAttribute("begin"))
                    if (start != null) {
                        val dur = parseTtmlTimestampSec(child.getAttribute("dur"))
                        val end = parseTtmlTimestampSec(child.getAttribute("end"))
                            ?: dur?.let { start + it }
                        val sb = StringBuilder()
                        appendVisibleText(child, sb)
                        val spanText = sb.toString()
                        if (spanText.isNotBlank()) {
                            result.add(TimedSpan(startSec = start, endSec = end, text = spanText))
                        }
                    }
                }
            } else {
                result.addAll(collectTimedSpans(child))
            }
        }
        return result
    }

    private fun appendVisibleText(node: Node, out: StringBuilder) {
        when (node.nodeType) {
            Node.TEXT_NODE, Node.CDATA_SECTION_NODE -> {
                out.append(node.nodeValue.orEmpty())
            }
            Node.ELEMENT_NODE -> {
                val el = node as Element
                if (localTagName(el) == "br") {
                    out.append(' ')
                    return
                }
                val children = el.childNodes
                for (i in 0 until children.length) {
                    appendVisibleText(children.item(i), out)
                }
            }
        }
    }

    /**
     * Parses TTML / caption timestamps into seconds.
     * Supports `hh:mm:ss.mmm`, `mm:ss.mmm`, `ss.ms`, `hh:mm:ss`, `mm:ss`, `12.5s`, and `1500ms`.
     */
    internal fun parseTtmlTimestampSec(raw: String?): Float? {
        val value = raw?.trim()?.takeIf { it.isNotEmpty() } ?: return null
        if (value.endsWith("ms", ignoreCase = true)) {
            return value.dropLast(2).trim().toFloatOrNull()?.div(1000f)
        }
        if (value.endsWith("s", ignoreCase = true)) {
            return value.dropLast(1).trim().toFloatOrNull()
        }
        val parts = value.split(":")
        return when (parts.size) {
            3 -> {
                val hours = parts[0].toIntOrNull() ?: return null
                val mins = parts[1].toIntOrNull() ?: return null
                val secs = parts[2].toFloatOrNull() ?: return null
                hours * 3600f + mins * 60f + secs
            }
            2 -> {
                val mins = parts[0].toIntOrNull() ?: return null
                val secs = parts[1].toFloatOrNull() ?: return null
                mins * 60f + secs
            }
            1 -> parts[0].toFloatOrNull()
            else -> null
        }
    }

    /**
     * Fetches YouTube timedtext XML captions for [videoId] as a fallback lyrics provider.
     */
    suspend fun fetchYouTubeCaptions(videoId: String): LyricsData? = withContext(Dispatchers.IO) {
        if (videoId.isBlank()) return@withContext null

        val captionTrackUrl = resolveCaptionTrackUrl(videoId)
            ?: "https://www.youtube.com/api/timedtext?v=${URLEncoder.encode(videoId, "UTF-8")}&lang=en"

        return@withContext try {
            val req = Request.Builder()
                .url(captionTrackUrl)
                .header("User-Agent", USER_AGENT)
                .build()

            SimpMusicExtractorEngine.httpClient.newCall(req).execute().use { resp ->
                if (!resp.isSuccessful) return@use null
                val xml = resp.body?.string().orEmpty()
                parseYouTubeTimedTextXml(xml, videoId)
            }
        } catch (_: Exception) {
            null
        }
    }

    private fun resolveCaptionTrackUrl(videoId: String): String? {
        return try {
            val payload = JSONObject().apply {
                put("context", JSONObject().apply {
                    put("client", JSONObject().apply {
                        put("clientName", "WEB")
                        put("clientVersion", "2.20250312.04.00")
                        put("hl", "en")
                        put("gl", SimpMusicExtractorEngine.effectiveGl())
                    })
                })
                put("videoId", videoId)
            }
            val req = Request.Builder()
                .url(YT_PLAYER_URL)
                .header("User-Agent", USER_AGENT)
                .header("Origin", "https://www.youtube.com")
                .header("Referer", "https://www.youtube.com/watch?v=$videoId")
                .post(payload.toString().toRequestBody(JSON_MEDIA_TYPE))
                .build()

            SimpMusicExtractorEngine.httpClient.newCall(req).execute().use { resp ->
                if (!resp.isSuccessful) return@use null
                val root = JSONObject(resp.body?.string() ?: return@use null)
                val tracks = root.optJSONObject("captions")
                    ?.optJSONObject("playerCaptionsTracklistRenderer")
                    ?.optJSONArray("captionTracks")
                    ?: return@use null
                if (tracks.length() == 0) return@use null

                var chosen: JSONObject? = null
                var bestScore = Int.MIN_VALUE
                for (i in 0 until tracks.length()) {
                    val track = tracks.optJSONObject(i) ?: continue
                    val baseUrl = track.optString("baseUrl")
                    if (baseUrl.isBlank()) continue
                    val lang = track.optString("languageCode")
                    val kind = track.optString("kind")
                    var score = 0
                    if (kind != "asr") score += 100
                    if (lang.startsWith("en", ignoreCase = true)) score += 25
                    if (score > bestScore) {
                        bestScore = score
                        chosen = track
                    }
                }
                chosen?.optString("baseUrl")?.takeIf { it.isNotBlank() }
            }
        } catch (_: Exception) {
            null
        }
    }

    /**
     * Parses YouTube timedtext XML (`<transcript><text start="..." dur="...">` or srv3 `<p t="..." d="...">`).
     */
    internal fun parseYouTubeTimedTextXml(xml: String, videoId: String): LyricsData? {
        val trimmed = xml.trim().trimStart('\uFEFF')
        if (trimmed.isBlank() || !trimmed.contains("<")) return null

        return try {
            val doc = newSafeDocumentBuilder().parse(InputSource(StringReader(trimmed)))
            val lines = ArrayList<LyricLine>()

            val textNodes = doc.getElementsByTagName("text")
            if (textNodes.length > 0) {
                for (i in 0 until textNodes.length) {
                    val el = textNodes.item(i) as? Element ?: continue
                    val startSec = el.getAttribute("start").toFloatOrNull() ?: continue
                    val durSec = el.getAttribute("dur").toFloatOrNull() ?: 3.5f
                    val sb = StringBuilder()
                    appendVisibleText(el, sb)
                    val clean = cleanCaptionLine(sb.toString()) ?: continue
                    val endSec = (startSec + durSec).coerceAtLeast(startSec + 0.3f)
                    lines.add(
                        LyricLine(
                            timeSec = startSec,
                            endTimeSec = endSec,
                            text = clean,
                            words = synthesizeEvenWords(clean, startSec, endSec)
                        )
                    )
                }
            } else {
                val pNodes = doc.getElementsByTagName("p")
                for (i in 0 until pNodes.length) {
                    val el = pNodes.item(i) as? Element ?: continue
                    val startMs = el.getAttribute("t").toFloatOrNull() ?: continue
                    val durMs = el.getAttribute("d").toFloatOrNull() ?: 3500f
                    val startSec = startMs / 1000f
                    val sb = StringBuilder()
                    appendVisibleText(el, sb)
                    val clean = cleanCaptionLine(sb.toString()) ?: continue
                    val endSec = ((startMs + durMs) / 1000f).coerceAtLeast(startSec + 0.3f)
                    lines.add(
                        LyricLine(
                            timeSec = startSec,
                            endTimeSec = endSec,
                            text = clean,
                            words = synthesizeEvenWords(clean, startSec, endSec)
                        )
                    )
                }
            }

            val sorted = lines.sortedBy { it.timeSec }
            if (sorted.isEmpty()) return null

            LyricsData(
                videoId = videoId,
                plainLyrics = sorted.joinToString("\n") { it.text },
                syncedLines = sorted,
                source = "YouTube Captions",
                hasRomanization = false
            )
        } catch (_: Exception) {
            null
        }
    }

    private fun cleanCaptionLine(raw: String): String? {
        val unescaped = SimpMusicLyricsRepository.unescapeLyricsHtml(raw)
        val normalized = normalizeSpaces(unescaped.replace('\n', ' '))
        if (normalized.isBlank()) return null
        if (normalized.matches(Regex("^\\[(music|applause|laughter|cheering|instrumental)]$", RegexOption.IGNORE_CASE))) {
            return null
        }
        return normalized
    }

    private fun synthesizeEvenWords(text: String, startSec: Float, endSec: Float): List<LyricWord> {
        val tokens = text.split(Regex("\\s+")).filter { it.isNotBlank() }
        if (tokens.isEmpty()) return emptyList()
        val span = (endSec - startSec).coerceAtLeast(0.3f)
        val step = span / tokens.size
        return tokens.mapIndexed { idx, token ->
            val wStart = startSec + idx * step
            LyricWord(
                word = token,
                startTimeSec = wStart,
                endTimeSec = (wStart + step).coerceAtLeast(wStart + 0.05f)
            )
        }
    }

    private fun localTagName(element: Element): String =
        element.tagName.substringAfter(':').lowercase()

    private fun normalizeSpaces(text: String): String =
        text.replace(Regex("\\s+"), " ").trim()

    private fun newSafeDocumentBuilder() = DocumentBuilderFactory.newInstance().apply {
        isNamespaceAware = false
        runCatching { setFeature("http://apache.org/xml/features/disallow-doctype-decl", true) }
        runCatching { setFeature("http://xml.org/sax/features/external-general-entities", false) }
        runCatching { setFeature("http://xml.org/sax/features/external-parameter-entities", false) }
    }.newDocumentBuilder()
}
