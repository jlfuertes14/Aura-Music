package com.auramusic.app.core.lyrics

import com.atilika.kuromoji.ipadic.Tokenizer
import net.sourceforge.pinyin4j.PinyinHelper
import net.sourceforge.pinyin4j.format.HanyuPinyinCaseType
import net.sourceforge.pinyin4j.format.HanyuPinyinOutputFormat
import net.sourceforge.pinyin4j.format.HanyuPinyinToneType
import net.sourceforge.pinyin4j.format.HanyuPinyinVCharType
import java.text.Normalizer

/**
 * Fast, on-device line-level romanizer supporting all 12 Aura lyric romanization languages:
 * Japanese, Korean, Chinese, Hindi, Punjabi, Russian, Ukrainian, Serbian, Bulgarian,
 * Belarusian, Kyrgyz, and Macedonian.
 */
object LyricsRomanizer {

    val ALL_LANGUAGES: Set<String> = linkedSetOf(
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

    val DEFAULT_LANGUAGES: Set<String> = setOf("Japanese", "Korean", "Chinese")

    private val CYRILLIC_LANGUAGE_PRIORITY = listOf(
        "Russian",
        "Ukrainian",
        "Serbian",
        "Bulgarian",
        "Belarusian",
        "Kyrgyz",
        "Macedonian"
    )

    /** Kuromoji is initialized lazily on first Japanese line, never during app startup. */
    private val japaneseTokenizer: Tokenizer by lazy(LazyThreadSafetyMode.SYNCHRONIZED) { Tokenizer() }

    private val pinyinFormat: HanyuPinyinOutputFormat by lazy {
        HanyuPinyinOutputFormat().apply {
            caseType = HanyuPinyinCaseType.LOWERCASE
            toneType = HanyuPinyinToneType.WITH_TONE_MARK
            vCharType = HanyuPinyinVCharType.WITH_U_UNICODE
        }
    }

    /**
     * Romanizes a single lyric [text] line according to [enabledLanguages].
     * Returns `null` if [text] is blank, already Latin, or its script's language is not enabled.
     */
    fun romanizeLine(
        text: String,
        enabledLanguages: Set<String> = DEFAULT_LANGUAGES
    ): String? {
        if (text.isBlank() || enabledLanguages.isEmpty()) return null

        // 1. Japanese: presence of Hiragana or Katakana reliably identifies Japanese over Chinese.
        if (text.any(::isJapaneseKana)) {
            if ("Japanese" !in enabledLanguages) return null
            return romanizeJapanese(text)
        }

        // 2. Chinese: Hanzi without Japanese Kana is treated as Chinese Pinyin.
        if (text.any(::isHanIdeograph)) {
            if ("Chinese" !in enabledLanguages) return null
            return romanizeChinese(text)
        }

        // 3. Korean: Hangul syllables / compatibility jamo.
        if (text.any(::isHangul)) {
            if ("Korean" !in enabledLanguages) return null
            return romanizeKorean(text)
        }

        // 4. Hindi: Devanagari abugida.
        if (text.any(::isDevanagari)) {
            if ("Hindi" !in enabledLanguages) return null
            return romanizeDevanagari(text)
        }

        // 5. Punjabi: Gurmukhi abugida.
        if (text.any(::isGurmukhi)) {
            if ("Punjabi" !in enabledLanguages) return null
            return romanizeGurmukhi(text)
        }

        // 6. Cyrillic: language-aware mapping across the 7 supported Cyrillic languages.
        if (text.any(::isCyrillic)) {
            val enabledCyrillic = enabledLanguages.filterTo(LinkedHashSet()) { it in CYRILLIC_LANGUAGE_PRIORITY }
            if (enabledCyrillic.isEmpty()) return null
            val detectedLang = detectCyrillicLanguage(text, enabledCyrillic) ?: return null
            return romanizeCyrillic(text, detectedLang)
        }

        return null
    }

    // --- Script Detection ---

    private fun isJapaneseKana(ch: Char): Boolean {
        val code = ch.code
        return code in 0x3041..0x3096 || // Hiragana letters
            code in 0x30A1..0x30FA || // Katakana letters (excludes middle dot U+30FB)
            code == 0x30FC || // Katakana-Hiragana prolonged sound mark
            code in 0x31F0..0x31FF || // Katakana Phonetic Extensions
            code in 0xFF66..0xFF9F // Halfwidth Katakana
    }

    private fun isHanIdeograph(ch: Char): Boolean {
        val code = ch.code
        return code in 0x4E00..0x9FFF ||
            code in 0x3400..0x4DBF ||
            code in 0xF900..0xFAFF
    }

    private fun isHangul(ch: Char): Boolean {
        val code = ch.code
        return code in 0xAC00..0xD7A3 ||
            code in 0x3131..0x3163 ||
            code in 0x1100..0x11FF
    }

    private fun isDevanagari(ch: Char): Boolean = ch.code in 0x0900..0x097F

    private fun isGurmukhi(ch: Char): Boolean = ch.code in 0x0A00..0x0A7F

    private fun isCyrillic(ch: Char): Boolean {
        val code = ch.code
        return code in 0x0400..0x04FF || code in 0x0500..0x052F
    }

    // --- Japanese (Kuromoji + Hepburn Romaji) ---

    private fun romanizeJapanese(text: String): String? {
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

    /** Kana -> Hepburn romaji, including contracted sounds, doubled consonants, and long vowels. */
    internal fun romanizeKana(text: String): String {
        val kana = Normalizer.normalize(text, Normalizer.Form.NFKC)
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

    // --- Chinese (Pinyin4j with Tone Marks) ---

    private fun romanizeChinese(text: String): String? {
        val out = StringBuilder()
        var lastWasPinyin = false
        var convertedAny = false

        for (ch in text) {
            if (isHanIdeograph(ch)) {
                val pinyin = runCatching {
                    PinyinHelper.toHanyuPinyinStringArray(ch, pinyinFormat)?.firstOrNull()
                }.getOrNull()?.takeIf { it.isNotBlank() }

                if (pinyin != null) {
                    if (out.isNotEmpty() && !out.last().isWhitespace() && !isOpeningPunctuation(out.last())) {
                        out.append(' ')
                    }
                    out.append(normalizePinyinToneMarks(pinyin))
                    lastWasPinyin = true
                    convertedAny = true
                } else {
                    out.append(ch)
                    lastWasPinyin = false
                }
            } else if (ch.isWhitespace()) {
                if (out.isNotEmpty() && !out.last().isWhitespace()) {
                    out.append(' ')
                }
                lastWasPinyin = false
            } else {
                val mappedPunct = CJK_PUNCTUATION_MAP[ch]
                if (mappedPunct != null) {
                    out.append(mappedPunct)
                    lastWasPinyin = false
                } else {
                    if (lastWasPinyin && (ch.isLetterOrDigit())) {
                        out.append(' ')
                    }
                    out.append(ch)
                    lastWasPinyin = false
                }
            }
        }

        val result = out.toString().trim()
        return result.takeIf { convertedAny && it.isNotBlank() && it != text }
    }

    private fun isOpeningPunctuation(ch: Char): Boolean =
        ch in charArrayOf('(', '[', '{', '"', '\'', '“', '‘', '（', '【', '《', '「', '『')

    /**
     * Pinyin4j emits breve diacritics (ă, ĕ, ĭ, ŏ, ŭ) for 3rd tone instead of standard
     * Hanyu Pinyin carons (ǎ, ě, ǐ, ǒ, ǔ). Normalize them to standard Unicode Pinyin.
     */
    private fun normalizePinyinToneMarks(pinyin: String): String {
        val sb = StringBuilder(pinyin.length)
        for (ch in pinyin) {
            sb.append(
                when (ch) {
                    '\u0103' -> '\u01CE' // ă -> ǎ
                    '\u0115' -> '\u011B' // ĕ -> ě
                    '\u012D' -> '\u01D0' // ĭ -> ǐ
                    '\u014F' -> '\u01D2' // ŏ -> ǒ
                    '\u016D' -> '\u01D4' // ŭ -> ǔ
                    else -> ch
                }
            )
        }
        return sb.toString()
    }

    // --- Korean (Hangul Unicode Decomposition -> Revised Romanization) ---

    private fun romanizeKorean(text: String): String? {
        var convertedAny = false
        val result = buildString {
            text.forEach { ch ->
                val code = ch.code
                if (code in 0xAC00..0xD7A3) {
                    convertedAny = true
                    val index = code - 0xAC00
                    val initial = index / (21 * 28)
                    val medial = (index % (21 * 28)) / 28
                    val finalIndex = index % 28
                    append(HANGUL_INITIALS[initial])
                    append(HANGUL_MEDIALS[medial])
                    append(HANGUL_FINALS[finalIndex])
                } else {
                    val jamo = HANGUL_COMPAT_JAMO[ch]
                    if (jamo != null) {
                        convertedAny = true
                        append(jamo)
                    } else {
                        append(ch)
                    }
                }
            }
        }
        return result.takeIf { convertedAny && it.isNotBlank() && it != text }
    }

    // --- Hindi (Devanagari Abugida) ---

    private fun romanizeDevanagari(text: String): String? {
        var convertedAny = false
        val out = StringBuilder()
        var i = 0
        val len = text.length

        while (i < len) {
            val ch = text[i]
            if (!isDevanagari(ch)) {
                out.append(ch)
                i++
                continue
            }

            convertedAny = true

            // Independent vowels
            val indepVowel = DEVANAGARI_VOWELS[ch]
            if (indepVowel != null) {
                out.append(indepVowel)
                i++
                continue
            }

            // Consonants (with optional nukta, virama, or matra)
            val baseConsonant = DEVANAGARI_CONSONANTS[ch]
            if (baseConsonant != null) {
                var nextIdx = i + 1
                var consonant = baseConsonant

                if (nextIdx < len && text[nextIdx] == '\u093C') { // Nukta
                    consonant = DEVANAGARI_NUKTA_CONSONANTS[ch] ?: baseConsonant
                    nextIdx++
                }

                if (nextIdx < len && text[nextIdx] == '\u094D') { // Virama (halant)
                    out.append(consonant)
                    i = nextIdx + 1
                    continue
                }

                val matra = if (nextIdx < len) DEVANAGARI_MATRAS[text[nextIdx]] else null
                if (matra != null) {
                    out.append(consonant).append(matra)
                    i = nextIdx + 1
                    continue
                }

                // Bare consonant: inherent 'a' unless word-final in a multi-character word
                // (preserving final 'a' when preceded by virama + semivowel like त्र/व्य/त्व).
                val isWordFinal = nextIdx >= len || (!isDevanagariLetterOrSign(text[nextIdx]))
                val hasPrecedingDevanagari = i > 0 && isDevanagariLetterOrSign(text[i - 1])
                val precededByViramaSemivowel = i >= 2 && text[i - 1] == '\u094D' && ch in charArrayOf('य', 'र', 'व', 'ल')
                val omitInherentA = isWordFinal && hasPrecedingDevanagari && !precededByViramaSemivowel
                out.append(consonant)
                if (!omitInherentA) {
                    out.append('a')
                }
                i = nextIdx
                continue
            }

            // Nasalization & modifiers
            when (ch) {
                '\u0902' -> { // Anusvara
                    val nextCons = if (i + 1 < len) text[i + 1] else '\u0000'
                    out.append(if (nextCons in DEVANAGARI_LABIALS) "m" else "n")
                }
                '\u0901' -> out.append("n") // Chandrabindu
                '\u0903' -> out.append("h") // Visarga
                '\u093D' -> Unit // Avagraha
                '\u0964' -> out.append('.') // Danda
                '\u0965' -> out.append("..") // Double danda
                in '\u0966'..'\u096F' -> out.append(('0'.code + (ch.code - 0x0966)).toChar())
                else -> {
                    val strayMatra = DEVANAGARI_MATRAS[ch]
                    if (strayMatra != null) out.append(strayMatra)
                }
            }
            i++
        }

        val result = out.toString()
        return result.takeIf { convertedAny && it.isNotBlank() && it != text }
    }

    private fun isDevanagariLetterOrSign(ch: Char): Boolean {
        val code = ch.code
        return code in 0x0901..0x0963
    }

    // --- Punjabi (Gurmukhi Abugida) ---

    private fun romanizeGurmukhi(text: String): String? {
        var convertedAny = false
        val out = StringBuilder()
        var i = 0
        val len = text.length
        var geminateNext = false

        while (i < len) {
            val ch = text[i]
            if (!isGurmukhi(ch)) {
                geminateNext = false
                out.append(ch)
                i++
                continue
            }

            convertedAny = true

            // Addak doubles the onset of the following consonant
            if (ch == '\u0A71') {
                geminateNext = true
                i++
                continue
            }

            // Independent precomposed vowels
            val indepVowel = GURMUKHI_VOWELS[ch]
            if (indepVowel != null) {
                geminateNext = false
                out.append(indepVowel)
                i++
                continue
            }

            // Vowel bearers (Iri ੲ / Ura ੳ) combined with matra
            if (ch == '\u0A72' || ch == '\u0A73') {
                geminateNext = false
                val nextMatra = if (i + 1 < len) GURMUKHI_MATRAS[text[i + 1]] else null
                if (nextMatra != null) {
                    out.append(nextMatra)
                    i += 2
                } else {
                    out.append(if (ch == '\u0A72') "i" else "u")
                    i++
                }
                continue
            }

            // Consonants (with optional pair bindi / nukta, virama, or matra)
            val baseConsonant = GURMUKHI_CONSONANTS[ch]
            if (baseConsonant != null) {
                var nextIdx = i + 1
                var consonant = baseConsonant

                if (nextIdx < len && text[nextIdx] == '\u0A3C') { // Pair bindi
                    consonant = GURMUKHI_BINDI_CONSONANTS[ch] ?: baseConsonant
                    nextIdx++
                }

                if (geminateNext) {
                    if (consonant.isNotEmpty()) {
                        out.append(consonant[0])
                    }
                    geminateNext = false
                }

                if (nextIdx < len && text[nextIdx] == '\u0A4D') { // Virama
                    out.append(consonant)
                    i = nextIdx + 1
                    continue
                }

                val matra = if (nextIdx < len) GURMUKHI_MATRAS[text[nextIdx]] else null
                if (matra != null) {
                    out.append(consonant).append(matra)
                    i = nextIdx + 1
                    continue
                }

                val isWordFinal = nextIdx >= len || !isGurmukhiLetterOrSign(text[nextIdx])
                val hasPrecedingGurmukhi = i > 0 && isGurmukhiLetterOrSign(text[i - 1])
                val omitInherentA = isWordFinal && hasPrecedingGurmukhi
                out.append(consonant)
                if (!omitInherentA) {
                    out.append('a')
                }
                i = nextIdx
                continue
            }

            geminateNext = false
            when (ch) {
                '\u0A02', '\u0A70' -> { // Bindi / Tippi
                    val nextCons = if (i + 1 < len) text[i + 1] else '\u0000'
                    out.append(if (nextCons in GURMUKHI_LABIALS) "m" else "n")
                }
                '\u0A03' -> out.append("h") // Visarga
                in '\u0A66'..'\u0A6F' -> out.append(('0'.code + (ch.code - 0x0A66)).toChar())
                else -> {
                    val strayMatra = GURMUKHI_MATRAS[ch]
                    if (strayMatra != null) out.append(strayMatra)
                }
            }
            i++
        }

        val result = out.toString()
        return result.takeIf { convertedAny && it.isNotBlank() && it != text }
    }

    private fun isGurmukhiLetterOrSign(ch: Char): Boolean {
        val code = ch.code
        return code in 0x0A01..0x0A5E || code in 0x0A70..0x0A74
    }

    // --- 7 Cyrillic Languages ---

    private fun detectCyrillicLanguage(text: String, enabledCyrillic: Set<String>): String? {
        var hasUkrainianUnique = false
        var hasBelarusianUnique = false
        var hasI = false
        var hasSerbianUnique = false
        var hasMacedonianUnique = false
        var hasSouthSlavicShared = false
        var hasKyrgyzUnique = false
        var hasEastSlavicShared = false
        var hasHardSign = false

        for (ch in text) {
            when (ch.lowercaseChar()) {
                'ї', 'є', 'ґ' -> hasUkrainianUnique = true
                'ў' -> hasBelarusianUnique = true
                'і' -> hasI = true
                'ђ', 'ћ' -> hasSerbianUnique = true
                'ѓ', 'ќ', 'ѕ' -> hasMacedonianUnique = true
                'ј', 'љ', 'њ', 'џ' -> hasSouthSlavicShared = true
                'ң', 'ө', 'ү' -> hasKyrgyzUnique = true
                'ы', 'э', 'ё' -> hasEastSlavicShared = true
                'ъ' -> hasHardSign = true
            }
        }

        return when {
            hasUkrainianUnique -> "Ukrainian".takeIf { it in enabledCyrillic }
            hasBelarusianUnique -> "Belarusian".takeIf { it in enabledCyrillic }
            hasSerbianUnique -> "Serbian".takeIf { it in enabledCyrillic }
            hasMacedonianUnique -> "Macedonian".takeIf { it in enabledCyrillic }
            hasKyrgyzUnique -> "Kyrgyz".takeIf { it in enabledCyrillic }
            hasI -> firstEnabled(enabledCyrillic, "Ukrainian", "Belarusian")
            hasSouthSlavicShared -> firstEnabled(enabledCyrillic, "Serbian", "Macedonian")
            hasEastSlavicShared -> firstEnabled(enabledCyrillic, "Russian", "Belarusian", "Kyrgyz")
            hasHardSign -> firstEnabled(enabledCyrillic, "Russian", "Bulgarian", "Kyrgyz")
            else -> CYRILLIC_LANGUAGE_PRIORITY.firstOrNull { it in enabledCyrillic }
        }
    }

    private fun firstEnabled(enabled: Set<String>, vararg candidates: String): String? =
        candidates.firstOrNull { it in enabled }

    private fun romanizeCyrillic(text: String, language: String): String? {
        val table = when (language) {
            "Ukrainian" -> UKRAINIAN_CYRILLIC_MAP
            "Belarusian" -> BELARUSIAN_CYRILLIC_MAP
            "Serbian" -> SERBIAN_CYRILLIC_MAP
            "Macedonian" -> MACEDONIAN_CYRILLIC_MAP
            "Bulgarian" -> BULGARIAN_CYRILLIC_MAP
            "Kyrgyz" -> KYRGYZ_CYRILLIC_MAP
            else -> RUSSIAN_CYRILLIC_MAP
        }

        var convertedAny = false
        val out = StringBuilder()

        for (i in text.indices) {
            val ch = text[i]
            val lower = ch.lowercaseChar()
            val mapped = if (language == "Ukrainian") {
                ukrainianContextualMapping(text, i, lower) ?: table[lower]
            } else {
                table[lower]
            }

            if (mapped == null) {
                out.append(ch)
                continue
            }

            convertedAny = true
            if (mapped.isEmpty()) continue

            if (ch.isUpperCase()) {
                val nextUpper = i + 1 < text.length && text[i + 1].isLetter() && text[i + 1].isUpperCase()
                if (nextUpper) {
                    out.append(mapped.uppercase())
                } else {
                    out.append(mapped.replaceFirstChar { it.uppercaseChar() })
                }
            } else {
                out.append(mapped)
            }
        }

        val result = out.toString()
        return result.takeIf { convertedAny && it.isNotBlank() && it != text }
    }

    private fun ukrainianContextualMapping(text: String, index: Int, lower: Char): String? {
        val isWordStart = index == 0 || !text[index - 1].isLetter()
        return when (lower) {
            'ї' -> if (isWordStart) "yi" else "i"
            'є' -> if (isWordStart) "ye" else "ie"
            'й' -> if (isWordStart) "y" else "i"
            'ю' -> if (isWordStart) "yu" else "iu"
            'я' -> if (isWordStart) "ya" else "ia"
            else -> null
        }
    }

    // --- Lookup Tables ---

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

    private val CJK_PUNCTUATION_MAP = mapOf(
        '，' to ", ",
        '。' to ".",
        '！' to "!",
        '？' to "?",
        '、' to ", ",
        '；' to "; ",
        '：' to ": ",
        '（' to "(",
        '）' to ")",
        '【' to "[",
        '】' to "]",
        '《' to "\"",
        '》' to "\"",
        '「' to "\"",
        '」' to "\"",
        '『' to "\"",
        '』' to "\"",
        '“' to "\"",
        '”' to "\"",
        '‘' to "'",
        '’' to "'"
    )

    private val HANGUL_INITIALS = arrayOf(
        "g", "kk", "n", "d", "tt", "r", "m", "b", "pp", "s",
        "ss", "", "j", "jj", "ch", "k", "t", "p", "h"
    )

    private val HANGUL_MEDIALS = arrayOf(
        "a", "ae", "ya", "yae", "eo", "e", "yeo", "ye", "o", "wa",
        "wae", "oe", "yo", "u", "wo", "we", "wi", "yu", "eu", "ui", "i"
    )

    private val HANGUL_FINALS = arrayOf(
        "", "k", "k", "ks", "n", "nj", "nh", "t", "l", "lg", "lm",
        "lb", "ls", "lt", "lp", "lh", "m", "p", "bs", "t", "t", "ng",
        "t", "t", "k", "t", "p", "t"
    )

    private val HANGUL_COMPAT_JAMO = mapOf(
        'ㄱ' to "g", 'ㄲ' to "kk", 'ㄴ' to "n", 'ㄷ' to "d", 'ㄸ' to "tt",
        'ㄹ' to "r", 'ㅁ' to "m", 'ㅂ' to "b", 'ㅃ' to "pp", 'ㅅ' to "s",
        'ㅆ' to "ss", 'ㅇ' to "", 'ㅈ' to "j", 'ㅉ' to "jj", 'ㅊ' to "ch",
        'ㅋ' to "k", 'ㅌ' to "t", 'ㅍ' to "p", 'ㅎ' to "h",
        'ㅏ' to "a", 'ㅐ' to "ae", 'ㅑ' to "ya", 'ㅒ' to "yae", 'ㅓ' to "eo",
        'ㅔ' to "e", 'ㅕ' to "yeo", 'ㅖ' to "ye", 'ㅗ' to "o", 'ㅘ' to "wa",
        'ㅙ' to "wae", 'ㅚ' to "oe", 'ㅛ' to "yo", 'ㅜ' to "u", 'ㅝ' to "wo",
        'ㅞ' to "we", 'ㅟ' to "wi", 'ㅠ' to "yu", 'ㅡ' to "eu", 'ㅢ' to "ui", 'ㅣ' to "i"
    )

    private val DEVANAGARI_VOWELS = mapOf(
        'अ' to "a", 'आ' to "aa", 'इ' to "i", 'ई' to "ee",
        'उ' to "u", 'ऊ' to "oo", 'ऋ' to "ri", 'ॠ' to "ri",
        'ऌ' to "li", 'ए' to "e", 'ऐ' to "ai", 'ओ' to "o", 'औ' to "au",
        'ऍ' to "e", 'ऑ' to "o", 'ॲ' to "a"
    )

    private val DEVANAGARI_CONSONANTS = mapOf(
        'क' to "k", 'ख' to "kh", 'ग' to "g", 'घ' to "gh", 'ङ' to "ng",
        'च' to "ch", 'छ' to "chh", 'ज' to "j", 'झ' to "jh", 'ञ' to "ny",
        'ट' to "t", 'ठ' to "th", 'ड' to "d", 'ढ' to "dh", 'ण' to "n",
        'त' to "t", 'थ' to "th", 'द' to "d", 'ध' to "dh", 'न' to "n",
        'प' to "p", 'फ' to "ph", 'ब' to "b", 'भ' to "bh", 'म' to "m",
        'य' to "y", 'र' to "r", 'ल' to "l", 'व' to "v",
        'श' to "sh", 'ष' to "sh", 'स' to "s", 'ह' to "h",
        'ळ' to "l", 'ऩ' to "n", 'ऱ' to "r", 'ऴ' to "l"
    )

    private val DEVANAGARI_NUKTA_CONSONANTS = mapOf(
        'क' to "q", 'ख' to "kh", 'ग' to "gh", 'ज' to "z",
        'ड' to "r", 'ढ' to "rh", 'फ' to "f", 'य' to "y"
    )

    private val DEVANAGARI_MATRAS = mapOf(
        'ा' to "aa", 'ि' to "i", 'ी' to "ee", 'ु' to "u", 'ू' to "oo",
        'ृ' to "ri", 'ॄ' to "ri", 'ॅ' to "e", 'े' to "e", 'ै' to "ai",
        'ॉ' to "o", 'ो' to "o", 'ौ' to "au", 'ॢ' to "li", 'ॣ' to "li"
    )

    private val DEVANAGARI_LABIALS = setOf('प', 'फ', 'ब', 'भ', 'म')

    private val GURMUKHI_VOWELS = mapOf(
        'ਅ' to "a", 'ਆ' to "aa", 'ਇ' to "i", 'ਈ' to "ee",
        'ਉ' to "u", 'ਊ' to "oo", 'ਏ' to "e", 'ਐ' to "ai",
        'ਓ' to "o", 'ਔ' to "au"
    )

    private val GURMUKHI_CONSONANTS = mapOf(
        'ਕ' to "k", 'ਖ' to "kh", 'ਗ' to "g", 'ਘ' to "gh", 'ਙ' to "ng",
        'ਚ' to "ch", 'ਛ' to "chh", 'ਜ' to "j", 'ਝ' to "jh", 'ਞ' to "ny",
        'ਟ' to "t", 'ਠ' to "th", 'ਡ' to "d", 'ਢ' to "dh", 'ਣ' to "n",
        'ਤ' to "t", 'ਥ' to "th", 'ਦ' to "d", 'ਧ' to "dh", 'ਨ' to "n",
        'ਪ' to "p", 'ਫ' to "ph", 'ਬ' to "b", 'ਭ' to "bh", 'ਮ' to "m",
        'ਯ' to "y", 'ਰ' to "r", 'ਲ' to "l", 'ਵ' to "v", 'ੜ' to "r",
        'ਸ' to "s", 'ਹ' to "h"
    )

    private val GURMUKHI_BINDI_CONSONANTS = mapOf(
        'ਸ' to "sh", 'ਖ' to "kh", 'ਗ' to "gh", 'ਜ' to "z", 'ਫ' to "f", 'ਲ' to "l"
    )

    private val GURMUKHI_MATRAS = mapOf(
        'ਾ' to "aa", 'ਿ' to "i", 'ੀ' to "ee", 'ੁ' to "u", 'ੂ' to "oo",
        'ੇ' to "e", 'ੈ' to "ai", 'ੋ' to "o", 'ੌ' to "au"
    )

    private val GURMUKHI_LABIALS = setOf('ਪ', 'ਫ', 'ਬ', 'ਭ', 'ਮ')

    private val RUSSIAN_CYRILLIC_MAP = mapOf(
        'а' to "a", 'б' to "b", 'в' to "v", 'г' to "g", 'д' to "d",
        'е' to "e", 'ё' to "yo", 'ж' to "zh", 'з' to "z", 'и' to "i",
        'й' to "y", 'к' to "k", 'л' to "l", 'м' to "m", 'н' to "n",
        'о' to "o", 'п' to "p", 'р' to "r", 'с' to "s", 'т' to "t",
        'у' to "u", 'ф' to "f", 'х' to "kh", 'ц' to "ts", 'ч' to "ch",
        'ш' to "sh", 'щ' to "shch", 'ъ' to "", 'ы' to "y", 'ь' to "",
        'э' to "e", 'ю' to "yu", 'я' to "ya"
    )

    private val UKRAINIAN_CYRILLIC_MAP = mapOf(
        'а' to "a", 'б' to "b", 'в' to "v", 'г' to "h", 'ґ' to "g",
        'д' to "d", 'е' to "e", 'є' to "ye", 'ж' to "zh", 'з' to "z",
        'и' to "y", 'і' to "i", 'ї' to "yi", 'й' to "y", 'к' to "k",
        'л' to "l", 'м' to "m", 'н' to "n", 'о' to "o", 'п' to "p",
        'р' to "r", 'с' to "s", 'т' to "t", 'у' to "u", 'ф' to "f",
        'х' to "kh", 'ц' to "ts", 'ч' to "ch", 'ш' to "sh", 'щ' to "shch",
        'ь' to "", 'ю' to "yu", 'я' to "ya"
    )

    private val BELARUSIAN_CYRILLIC_MAP = mapOf(
        'а' to "a", 'б' to "b", 'в' to "v", 'г' to "h", 'ґ' to "g",
        'д' to "d", 'е' to "e", 'ё' to "yo", 'ж' to "zh", 'з' to "z",
        'і' to "i", 'й' to "y", 'к' to "k", 'л' to "l", 'м' to "m",
        'н' to "n", 'о' to "o", 'п' to "p", 'р' to "r", 'с' to "s",
        'т' to "t", 'у' to "u", 'ў' to "u", 'ф' to "f", 'х' to "kh",
        'ц' to "ts", 'ч' to "ch", 'ш' to "sh", 'ы' to "y", 'ь' to "",
        'э' to "e", 'ю' to "yu", 'я' to "ya"
    )

    private val SERBIAN_CYRILLIC_MAP = mapOf(
        'а' to "a", 'б' to "b", 'в' to "v", 'г' to "g", 'д' to "d",
        'ђ' to "đ", 'е' to "e", 'ж' to "ž", 'з' to "z", 'и' to "i",
        'ј' to "j", 'к' to "k", 'л' to "l", 'љ' to "lj", 'м' to "m",
        'н' to "n", 'њ' to "nj", 'о' to "o", 'п' to "p", 'р' to "r",
        'с' to "s", 'т' to "t", 'ћ' to "ć", 'у' to "u", 'ф' to "f",
        'х' to "h", 'ц' to "c", 'ч' to "č", 'џ' to "dž", 'ш' to "š"
    )

    private val MACEDONIAN_CYRILLIC_MAP = mapOf(
        'а' to "a", 'б' to "b", 'в' to "v", 'г' to "g", 'д' to "d",
        'ѓ' to "gj", 'е' to "e", 'ж' to "ž", 'з' to "z", 'ѕ' to "dz",
        'и' to "i", 'ј' to "j", 'к' to "k", 'л' to "l", 'љ' to "lj",
        'м' to "m", 'н' to "n", 'њ' to "nj", 'о' to "o", 'п' to "p",
        'р' to "r", 'с' to "s", 'т' to "t", 'ќ' to "kj", 'у' to "u",
        'ф' to "f", 'х' to "h", 'ц' to "c", 'ч' to "č", 'џ' to "dž",
        'ш' to "š"
    )

    private val BULGARIAN_CYRILLIC_MAP = mapOf(
        'а' to "a", 'б' to "b", 'в' to "v", 'г' to "g", 'д' to "d",
        'е' to "e", 'ж' to "zh", 'з' to "z", 'и' to "i", 'й' to "y",
        'к' to "k", 'л' to "l", 'м' to "m", 'н' to "n", 'о' to "o",
        'п' to "p", 'р' to "r", 'с' to "s", 'т' to "t", 'у' to "u",
        'ф' to "f", 'х' to "h", 'ц' to "ts", 'ч' to "ch", 'ш' to "sh",
        'щ' to "sht", 'ъ' to "a", 'ь' to "y", 'ю' to "yu", 'я' to "ya"
    )

    private val KYRGYZ_CYRILLIC_MAP = mapOf(
        'а' to "a", 'б' to "b", 'в' to "v", 'г' to "g", 'д' to "d",
        'е' to "e", 'ё' to "yo", 'ж' to "zh", 'з' to "z", 'и' to "i",
        'й' to "y", 'к' to "k", 'л' to "l", 'м' to "m", 'н' to "n",
        'ң' to "ng", 'о' to "o", 'ө' to "ö", 'п' to "p", 'р' to "r",
        'с' to "s", 'т' to "t", 'у' to "u", 'ү' to "ü", 'ф' to "f",
        'х' to "kh", 'ц' to "ts", 'ч' to "ch", 'ш' to "sh", 'щ' to "shch",
        'ъ' to "", 'ы' to "y", 'ь' to "", 'э' to "e", 'ю' to "yu",
        'я' to "ya"
    )
}
