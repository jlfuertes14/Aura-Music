package com.auramusic.app.core.lyrics

import com.auramusic.app.domain.model.LyricLine
import com.auramusic.app.domain.model.LyricsData
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class LyricsRomanizerTest {

    @Test
    fun languageConstants_containAllTwelveSupportedKeys() {
        val expectedAll = setOf(
            "Japanese",
            "Korean",
            "Chinese",
            "Hindi",
            "Punjabi",
            "Russian",
            "Ukrainian",
            "Serbian",
            "Bulgarian",
            "Belarusian",
            "Kyrgyz",
            "Macedonian"
        )
        assertEquals(expectedAll, LyricsRomanizer.ALL_LANGUAGES)
        assertEquals(setOf("Japanese", "Korean", "Chinese"), LyricsRomanizer.DEFAULT_LANGUAGES)
    }

    @Test
    fun romanizeLine_chinesePinyinWithToneMarks() {
        assertEquals("wǒ ài nǐ", LyricsRomanizer.romanizeLine("我爱你", setOf("Chinese")))

        val phrase = LyricsRomanizer.romanizeLine("你好世界", setOf("Chinese"))
        assertNotNull(phrase)
        assertTrue(phrase!!.contains(" "))
        assertTrue(phrase.any { it in "āáǎàēéěèīíǐìōóǒòūúǔùǖǘǚǜ" })
    }

    @Test
    fun romanizeLine_japaneseWithKanaDistinguishedFromChinese() {
        // Line with Kana is Japanese even if Chinese is also enabled
        val result = LyricsRomanizer.romanizeLine("ありがとう", setOf("Japanese", "Chinese"))
        assertEquals("arigatou", result)

        // Kana line returns null when Japanese is disabled, even if Chinese is enabled
        assertNull(LyricsRomanizer.romanizeLine("桜が咲く", setOf("Chinese")))
    }

    @Test
    fun romanizeLine_koreanHangulRevisedRomanization() {
        val result = LyricsRomanizer.romanizeLine("안녕하세요", setOf("Korean"))
        assertEquals("annyeonghaseyo", result)
    }

    @Test
    fun romanizeLine_russianAndUkrainianCyrillic() {
        val russian = LyricsRomanizer.romanizeLine("Привет, мир!", setOf("Russian"))
        assertEquals("Privet, mir!", russian)

        val ukrainian = LyricsRomanizer.romanizeLine("Київ", setOf("Ukrainian"))
        assertEquals("Kyiv", ukrainian)

        val ukrainianH = LyricsRomanizer.romanizeLine("Привіт, світе!", setOf("Ukrainian"))
        assertEquals("Pryvit, svite!", ukrainianH)
    }

    @Test
    fun romanizeLine_otherCyrillicLanguagesRespectEnabledKeys() {
        assertEquals("Shtastie", LyricsRomanizer.romanizeLine("Щастие", setOf("Bulgarian")))
        assertEquals("Đorđe", LyricsRomanizer.romanizeLine("Ђорђе", setOf("Serbian")))
        assertEquals("Kukja", LyricsRomanizer.romanizeLine("Куќа", setOf("Macedonian")))
        assertEquals("Slova", LyricsRomanizer.romanizeLine("Слова", setOf("Belarusian")))
        assertEquals("Kyrgyz", LyricsRomanizer.romanizeLine("Кыргыз", setOf("Kyrgyz")))
    }

    @Test
    fun romanizeLine_hindiDevanagari() {
        val result = LyricsRomanizer.romanizeLine("नमस्ते दिल", setOf("Hindi"))
        assertEquals("namaste dil", result)
    }

    @Test
    fun romanizeLine_punjabiGurmukhi() {
        val result = LyricsRomanizer.romanizeLine("ਸਤ ਸ੍ਰੀ ਅਕਾਲ", setOf("Punjabi"))
        assertEquals("sat sree akaal", result)

        val geminated = LyricsRomanizer.romanizeLine("ਮਿੱਤਰ", setOf("Punjabi"))
        assertEquals("mittar", geminated)
    }

    @Test
    fun romanizeLine_latinPassThroughReturnsNull() {
        assertNull(LyricsRomanizer.romanizeLine("Never gonna give you up", LyricsRomanizer.ALL_LANGUAGES))
        assertNull(LyricsRomanizer.romanizeLine("   ", LyricsRomanizer.ALL_LANGUAGES))
        assertNull(LyricsRomanizer.romanizeLine("12345 !?", LyricsRomanizer.ALL_LANGUAGES))
    }

    @Test
    fun romanizeLine_disabledLanguageOptOutReturnsNull() {
        assertNull(LyricsRomanizer.romanizeLine("你好世界", emptySet()))
        assertNull(LyricsRomanizer.romanizeLine("你好世界", setOf("Japanese", "Korean")))
        assertNull(LyricsRomanizer.romanizeLine("안녕하세요", setOf("Japanese", "Chinese")))
        assertNull(LyricsRomanizer.romanizeLine("Привет", setOf("Japanese", "Korean", "Chinese")))
        assertNull(LyricsRomanizer.romanizeLine("Київ", setOf("Russian")))
        assertNull(LyricsRomanizer.romanizeLine("नमस्ते", setOf("Punjabi")))
        assertNull(LyricsRomanizer.romanizeLine("ਸਤ ਸ੍ਰੀ ਅਕਾਲ", setOf("Hindi")))
    }

    @Test
    fun repository_applyRomanizationUpdatesLinesAndMetadata() {
        val raw = LyricsData(
            videoId = "vid_multi",
            plainLyrics = "你好\nПривет\nHello",
            syncedLines = listOf(
                LyricLine(0f, 2f, "你好"),
                LyricLine(2f, 4f, "Привет"),
                LyricLine(4f, 6f, "Hello")
            ),
            hasRomanization = false
        )

        val chineseOnly = SimpMusicLyricsRepository.applyRomanization(raw, setOf("Chinese"))
        assertTrue(chineseOnly.hasRomanization)
        assertNotNull(chineseOnly.syncedLines[0].romanizedText)
        assertNull(chineseOnly.syncedLines[1].romanizedText)
        assertNull(chineseOnly.syncedLines[2].romanizedText)

        val none = SimpMusicLyricsRepository.applyRomanization(chineseOnly, emptySet())
        assertFalse(none.hasRomanization)
        assertTrue(none.syncedLines.all { it.romanizedText == null })
    }
}
