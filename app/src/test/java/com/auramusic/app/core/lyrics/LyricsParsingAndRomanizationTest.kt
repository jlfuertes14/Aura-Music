package com.auramusic.app.core.lyrics

import com.auramusic.app.domain.model.LyricLine
import com.auramusic.app.domain.model.LyricWord
import com.auramusic.app.domain.model.LyricsData
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Pure JVM tests for the public API surface of [SimpMusicLyricsRepository] and lyrics domain models.
 * Note: LRC parsing (`parseLrcWithRichWords`) and romanization (`romanizeIfNonLatin`, `romanizeKana`)
 * are currently `private` in `SimpMusicLyricsRepository`, so only accessible `public` APIs are called here.
 */
class LyricsParsingAndRomanizationTest {

    @Test
    fun unescapeLyricsHtml_returnsPlainTextUnchangedWhenNoAmpersand() {
        val plain = "Just a normal lyric line without entities!"
        assertEquals(plain, SimpMusicLyricsRepository.unescapeLyricsHtml(plain))
        assertEquals("", SimpMusicLyricsRepository.unescapeLyricsHtml(""))
    }

    @Test
    fun unescapeLyricsHtml_decodesNamedXmlAndHtmlEntities() {
        assertEquals("\"Hello\"", SimpMusicLyricsRepository.unescapeLyricsHtml("&quot;Hello&quot;"))
        assertEquals("It's fine", SimpMusicLyricsRepository.unescapeLyricsHtml("It&apos;s fine"))
        assertEquals("You & Me", SimpMusicLyricsRepository.unescapeLyricsHtml("You &amp; Me"))
        assertEquals("<verse> & </verse>", SimpMusicLyricsRepository.unescapeLyricsHtml("&lt;verse&gt; &amp; &lt;/verse&gt;"))
        assertEquals("wait… — now – then", SimpMusicLyricsRepository.unescapeLyricsHtml("wait&hellip; &mdash; now &ndash; then"))
        assertEquals("‘single’ “double” space ", SimpMusicLyricsRepository.unescapeLyricsHtml("&lsquo;single&rsquo; &ldquo;double&rdquo; space&nbsp;"))
    }

    @Test
    fun unescapeLyricsHtml_decodesDecimalNumericEntities() {
        assertEquals("Don't stop", SimpMusicLyricsRepository.unescapeLyricsHtml("Don&#39;t stop"))
        assertEquals("’right‘", SimpMusicLyricsRepository.unescapeLyricsHtml("&#8217;right&#8216;"))
        assertEquals("A", SimpMusicLyricsRepository.unescapeLyricsHtml("&#65;"))
    }

    @Test
    fun unescapeLyricsHtml_decodesHexNumericEntitiesCaseInsensitively() {
        assertEquals("I'm here / now", SimpMusicLyricsRepository.unescapeLyricsHtml("I&#x27;m here &#x2F; now"))
        assertEquals("‘hi’", SimpMusicLyricsRepository.unescapeLyricsHtml("&#x2018;hi&#x2019;"))
        assertEquals("<", SimpMusicLyricsRepository.unescapeLyricsHtml("&#x3c;"))
    }

    @Test
    fun unescapeLyricsHtml_decodesMixedEntitiesInLrcContent() {
        val raw = "[00:12.34] I&#x27;m singing &ldquo;Stars&rdquo; &amp; waiting&#8230;"
        val expected = "[00:12.34] I'm singing “Stars” & waiting…"
        assertEquals(expected, SimpMusicLyricsRepository.unescapeLyricsHtml(raw))
    }

    @Test
    fun unescapeLyricsHtml_preservesMalformedOrUnknownEntities() {
        val input = "Rock & Roll &unknown; &#notanumber;"
        assertEquals(input, SimpMusicLyricsRepository.unescapeLyricsHtml(input))
    }

    @Test
    fun clearCache_executesSafelyWithoutAndroidRuntime() {
        SimpMusicLyricsRepository.clearCache()
    }

    @Test
    fun lyricsModels_preserveWordsLinesAndRomanizationMetadata() {
        val words = listOf(
            LyricWord(word = "안녕", startTimeSec = 1.0f, endTimeSec = 1.5f),
            LyricWord(word = "하세요", startTimeSec = 1.5f, endTimeSec = 2.2f)
        )
        val line = LyricLine(
            timeSec = 1.0f,
            endTimeSec = 2.5f,
            text = "안녕하세요",
            words = words,
            romanizedText = "annyeonghaseyo"
        )
        val plainLine = LyricLine(timeSec = 3.0f, endTimeSec = 5.0f, text = "Hello")

        val data = LyricsData(
            videoId = "vid1",
            plainLyrics = "안녕하세요\nHello",
            syncedLines = listOf(line, plainLine),
            hasRomanization = true
        )

        assertEquals("SimpMusic RichSync", data.source)
        assertTrue(data.hasRomanization)
        assertEquals(2, data.syncedLines.size)
        assertEquals("annyeonghaseyo", data.syncedLines[0].romanizedText)
        assertNull(data.syncedLines[1].romanizedText)
        assertFalse(data.syncedLines[0].words.isEmpty())
    }
}
