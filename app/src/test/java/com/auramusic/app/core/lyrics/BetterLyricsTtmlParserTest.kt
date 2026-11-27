package com.auramusic.app.core.lyrics

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Pure-JVM JUnit4 tests for [BetterLyricsProvider] TTML & YouTube timedtext XML parsing,
 * and [AiLyricsTranslator] JSON response parsing and serialization round-trip.
 */
class BetterLyricsTtmlParserTest {

    @Test
    fun parseTtmlTimestampSec_supportsMmSsHhMmSsAndSsMsFormats() {
        assertEquals(72.345f, BetterLyricsProvider.parseTtmlTimestampSec("01:12.345")!!, 0.001f)
        assertEquals(3723.5f, BetterLyricsProvider.parseTtmlTimestampSec("01:02:03.500")!!, 0.001f)
        assertEquals(12.5f, BetterLyricsProvider.parseTtmlTimestampSec("12.50")!!, 0.001f)
        assertEquals(4.125f, BetterLyricsProvider.parseTtmlTimestampSec("4.125s")!!, 0.001f)
        assertEquals(1.5f, BetterLyricsProvider.parseTtmlTimestampSec("1500ms")!!, 0.001f)
        assertNull(BetterLyricsProvider.parseTtmlTimestampSec(""))
        assertNull(BetterLyricsProvider.parseTtmlTimestampSec("invalid"))
    }

    @Test
    fun parseTtmlLyrics_parsesParagraphsAndWordLevelSpansAcrossTimestampFormats() {
        val ttml = """
            <?xml version="1.0" encoding="UTF-8"?>
            <tt xmlns="http://www.w3.org/ns/ttml" xmlns:ttm="http://www.w3.org/ns/ttml#metadata">
              <body>
                <div>
                  <p begin="00:05.100" end="00:08.400">
                    <span begin="00:05.100" end="00:05.600">Hello</span>
                    <span begin="00:05.600" end="00:06.400">world</span>
                    <span begin="00:06.400" end="00:08.400">again</span>
                  </p>
                  <p begin="00:01:02.250" end="00:01:05.000">
                    <span begin="00:01:02.250" end="00:01:03.000">Mid</span><span begin="00:01:03.000" end="00:01:03.800">night</span>
                    <span begin="00:01:03.800" end="00:01:05.000">glow</span>
                  </p>
                  <p begin="70.5" end="74.25">We&#39;re shining &amp; bright</p>
                </div>
              </body>
            </tt>
        """.trimIndent()

        val parsed = BetterLyricsProvider.parseTtmlLyrics(ttml, "vid_ttml")
        assertNotNull(parsed)
        val data = parsed!!
        assertEquals("vid_ttml", data.videoId)
        assertEquals("BetterLyrics (TTML)", data.source)
        assertEquals(3, data.syncedLines.size)

        val line0 = data.syncedLines[0]
        assertEquals(5.1f, line0.timeSec, 0.001f)
        assertEquals(8.4f, line0.endTimeSec, 0.001f)
        assertEquals("Hello world again", line0.text)
        assertEquals(3, line0.words.size)
        assertEquals("Hello", line0.words[0].word)
        assertEquals(5.1f, line0.words[0].startTimeSec, 0.001f)
        assertEquals(5.6f, line0.words[0].endTimeSec, 0.001f)

        val line1 = data.syncedLines[1]
        assertEquals(62.25f, line1.timeSec, 0.001f)
        assertEquals(65.0f, line1.endTimeSec, 0.001f)
        assertEquals("Midnight glow", line1.text)
        assertEquals(3, line1.words.size)
        assertEquals("Mid", line1.words[0].word)
        assertEquals("night", line1.words[1].word)

        val line2 = data.syncedLines[2]
        assertEquals(70.5f, line2.timeSec, 0.001f)
        assertEquals(74.25f, line2.endTimeSec, 0.001f)
        assertEquals("We're shining & bright", line2.text)
        assertEquals(4, line2.words.size)
    }

    @Test
    fun parseTtmlLyrics_returnsNullForEmptyOrNonTtmlXml() {
        assertNull(BetterLyricsProvider.parseTtmlLyrics("", "vid1"))
        assertNull(BetterLyricsProvider.parseTtmlLyrics("<tt><body><div/></body></tt>", "vid1"))
    }

    @Test
    fun parseYouTubeTimedTextXml_parsesStandardTranscriptAndSkipsMusicNoise() {
        val xml = """
            <?xml version="1.0" encoding="utf-8" ?>
            <transcript>
              <text start="1.2" dur="2.0">[Music]</text>
              <text start="4.5" dur="3.1">First caption&#39;s line</text>
              <text start="8.0" dur="2.5">Second &amp; final</text>
            </transcript>
        """.trimIndent()

        val parsed = BetterLyricsProvider.parseYouTubeTimedTextXml(xml, "yt123")
        assertNotNull(parsed)
        val data = parsed!!
        assertEquals("YouTube Captions", data.source)
        assertEquals(2, data.syncedLines.size)
        assertEquals("First caption's line", data.syncedLines[0].text)
        assertEquals(4.5f, data.syncedLines[0].timeSec, 0.001f)
        assertEquals(7.6f, data.syncedLines[0].endTimeSec, 0.001f)
        assertEquals("Second & final", data.syncedLines[1].text)
    }

    @Test
    fun parseTranslationResponse_parsesPlainAndMarkdownFencedJson() {
        val plainJson = """{"translations": {"0": "Hello world", "2": "Good night"}}"""
        val plainResult = AiLyricsTranslator.parseTranslationResponse(plainJson)
        assertEquals(mapOf(0 to "Hello world", 2 to "Good night"), plainResult)

        val fencedJson = """
            Here is your translation:
            ```json
            {
              "translations": {
                "0": "Bonjour le monde",
                "1": "Sous les étoiles"
              }
            }
            ```
        """.trimIndent()
        val fencedResult = AiLyricsTranslator.parseTranslationResponse(fencedJson)
        assertEquals(mapOf(0 to "Bonjour le monde", 1 to "Sous les étoiles"), fencedResult)

        val flatJson = """{"0": "Line zero", "1": "Line one"}"""
        assertEquals(mapOf(0 to "Line zero", 1 to "Line one"), AiLyricsTranslator.parseTranslationResponse(flatJson))

        assertTrue(AiLyricsTranslator.parseTranslationResponse("not json").isEmpty())
    }

    @Test
    fun serializeAndDeserializeTranslations_roundTripsCleanly() {
        val original = mapOf(
            0 to "First translated line",
            3 to "Fourth line with \"quotes\" & symbols",
            10 to "Tenth line"
        )
        val serialized = AiLyricsTranslator.serializeTranslations(original)
        val restored = AiLyricsTranslator.deserializeTranslations(serialized)
        assertEquals(original, restored)
        assertTrue(AiLyricsTranslator.deserializeTranslations("").isEmpty())
    }
}
