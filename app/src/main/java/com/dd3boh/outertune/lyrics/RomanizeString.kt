/*
 * Copyright (C) 2026 InterTune
 *
 * SPDX-License-Identifier: GPL-3.0
 */

package com.dd3boh.outertune.lyrics

import java.text.Normalizer

/**
 * Multi-language string romanization engine based on https://github.com/rejyoung/romanize-string.
 * Unifies transliteration for 30 written languages unidirectionally from non-Latin to Latin script:
 * Arabic, Belarusian, Bulgarian, Bengali, Cantonese, Chinese (Mandarin), Persian (Farsi), Greek,
 * Gujarati, Hindi, Japanese, Kazakh, Kannada, Korean, Kyrgyz, Macedonian, Mongolian, Marathi,
 * Nepali, Punjabi, Russian, Sanskrit, Serbian, Tamil, Telugu, Tajik, Thai, Ukrainian, and Urdu.
 */
object RomanizeString {

    // -------------------------------------------------------------------------
    // Public API
    // -------------------------------------------------------------------------

    /**
     * Transliterates [string] in [language] into Latin script, matching `romanizeString` in
     * `romanize-string`.
     */
    fun romanizeString(string: String, language: String, omitDiacritics: Boolean = false): String {
        val trimmed = string.trim()
        if (trimmed.isEmpty()) return ""

        val result = when (language.lowercase()) {
            "ja" -> romanizeJapanese(trimmed)
            "ko" -> romanizeKorean(trimmed)
            "zh", "zh-cn", "zh-hans", "zh-hant" -> romanizeMandarin(trimmed, omitDiacritics)
            "yue" -> romanizeCantonese(trimmed)
            "th" -> romanizeThai(trimmed)
            "ar", "fa", "ur" -> romanizeArabic(trimmed)
            "el" -> romanizeGreek(trimmed, omitDiacritics)
            in INDIC_LANGS -> romanizeIndic(trimmed, language, omitDiacritics)
            in CYRILLIC_LANGS -> romanizeCyrillic(trimmed, language, omitDiacritics)
            else -> romanizeAuto(trimmed, omitDiacritics) ?: trimmed
        }
        return result.trim()
    }

    /**
     * Automatically detects the script of [string] and transliterates it to Latin letters.
     * Returns null if the string is empty, already in Latin script, or contains no convertible characters.
     */
    fun romanize(string: String, omitDiacritics: Boolean = false): String? {
        val trimmed = string.trim()
        if (trimmed.isEmpty()) return null
        return romanizeAuto(trimmed, omitDiacritics)
    }

    private val INDIC_LANGS = setOf("bn", "gu", "hi", "kn", "mr", "ne", "pa", "ta", "te", "sa")
    private val CYRILLIC_LANGS = setOf("be", "bg", "kk", "ky", "mk", "mn", "ru", "sr", "tg", "uk")

    // -------------------------------------------------------------------------
    // Script Auto-Detection
    // -------------------------------------------------------------------------

    private fun romanizeAuto(text: String, omitDiacritics: Boolean): String? {
        // Count characters in each major script
        var arabicCount = 0
        var thaiCount = 0
        var hangulCount = 0
        var kanaCount = 0
        var hanCount = 0
        var cyrillicCount = 0
        var greekCount = 0
        var indicCount = 0
        var latinCount = 0

        var i = 0
        while (i < text.length) {
            val cp = text.codePointAt(i)
            i += Character.charCount(cp)
            if (!Character.isLetter(cp) && !Character.isDigit(cp)) continue
            when (Character.UnicodeScript.of(cp)) {
                Character.UnicodeScript.ARABIC -> arabicCount++
                Character.UnicodeScript.THAI -> thaiCount++
                Character.UnicodeScript.HANGUL -> hangulCount++
                Character.UnicodeScript.HIRAGANA, Character.UnicodeScript.KATAKANA -> kanaCount++
                Character.UnicodeScript.HAN -> hanCount++
                Character.UnicodeScript.CYRILLIC -> cyrillicCount++
                Character.UnicodeScript.GREEK -> greekCount++
                Character.UnicodeScript.DEVANAGARI, Character.UnicodeScript.BENGALI,
                Character.UnicodeScript.GURMUKHI, Character.UnicodeScript.GUJARATI,
                Character.UnicodeScript.ORIYA, Character.UnicodeScript.TAMIL,
                Character.UnicodeScript.TELUGU, Character.UnicodeScript.KANNADA,
                Character.UnicodeScript.MALAYALAM -> indicCount++
                Character.UnicodeScript.LATIN -> latinCount++
                else -> Unit
            }
        }

        val totalNonLatin = arabicCount + thaiCount + hangulCount + kanaCount + hanCount +
            cyrillicCount + greekCount + indicCount
        if (totalNonLatin == 0) return null

        val result = when {
            kanaCount > 0 -> romanizeJapanese(text)
            hangulCount > 0 -> romanizeKorean(text)
            thaiCount > 0 -> romanizeThai(text)
            arabicCount > 0 && arabicCount >= maxOf(cyrillicCount, greekCount, indicCount, hanCount) -> romanizeArabic(text)
            cyrillicCount > 0 && cyrillicCount >= maxOf(greekCount, indicCount, hanCount) -> {
                val lang = detectCyrillicLang(text)
                romanizeCyrillic(text, lang, omitDiacritics)
            }
            greekCount > 0 && greekCount >= maxOf(indicCount, hanCount) -> romanizeGreek(text, omitDiacritics)
            indicCount > 0 && indicCount >= hanCount -> {
                val lang = detectIndicLang(text)
                romanizeIndic(text, lang, omitDiacritics)
            }
            hanCount > 0 -> romanizeMandarin(text, omitDiacritics)
            else -> null
        }

        val tidy = result?.let { LyricsLatin.tidy(it) }
        return tidy?.takeIf { it.isNotBlank() && it != text && it.any { c -> c.isLetter() && Character.UnicodeScript.of(c.code) == Character.UnicodeScript.LATIN } }
    }

    private fun detectCyrillicLang(line: String): String = when {
        line.any { it in "ўЎ" } -> "be"
        line.any { it in "ѓќѕЃЌЅ" } -> "mk"
        line.any { it in "ђћџљњјЂЋЏЉЊЈ" } -> "sr"
        line.any { it in "їєґіЇЄҐІ" } -> "uk"
        line.any { it in "ъ" } && line.any { it in "щ" } -> "bg"
        line.any { it in "әғқңөұүһӘҒҚҢӨҰҮҺ" } -> "kk"
        line.any { it in "ҷӣӯҳҶӢӮҲ" } -> "tg"
        else -> "ru"
    }

    private fun detectIndicLang(line: String): String {
        for (c in line) {
            when (Character.UnicodeScript.of(c.code)) {
                Character.UnicodeScript.BENGALI -> return "bn"
                Character.UnicodeScript.GUJARATI -> return "gu"
                Character.UnicodeScript.GURMUKHI -> return "pa"
                Character.UnicodeScript.TAMIL -> return "ta"
                Character.UnicodeScript.TELUGU -> return "te"
                Character.UnicodeScript.KANNADA -> return "kn"
                Character.UnicodeScript.DEVANAGARI -> return "hi"
                else -> Unit
            }
        }
        return "hi"
    }

    // -------------------------------------------------------------------------
    // Arabic, Persian (Farsi), Urdu Transliteration
    // -------------------------------------------------------------------------

    private val FALLBACK_CHARS = mapOf(
        'ٱ' to "ا", 'ٰ' to "ا", 'پ' to "ب", 'چ' to "ج", 'ژ' to "ش",
        'گ' to "ك", 'ں' to "ن", 'ک' to "ك", 'ی' to "ي", 'ے' to "ي",
        'ھ' to "ه", 'ۀ' to "ه", 'ہ' to "ه", 'ؤ' to "و", 'ئ' to "ي",
        'ﮨ' to "ه", 'ﮩ' to "ه", 'ﮪ' to "ه", 'ﮫ' to "ه"
    )

    private val ARABIC_DIACRITICS = Regex("[\u064B-\u0652\u0670]")

    /**
     * Standardizes Arabic script for Persian/Urdu with phonetic word replacements,
     * character normalizations, and naive vowelization, then converts via IJMES Arabic to Latin.
     */
    fun romanizeArabic(input: String): String {
        var standardized = input

        // Step 1: Word substitutions for Farsi and Urdu words lacking short vowels
        val tokens = standardized.split(" ").map { word ->
            ArabicPhoneticMaps.FARSI[word] ?: ArabicPhoneticMaps.URDU[word] ?: word
        }
        standardized = tokens.joinToString(" ")

        // Step 2: Normalize Persian/Urdu specific characters
        val sb = StringBuilder(standardized.length)
        for (c in standardized) {
            sb.append(FALLBACK_CHARS[c] ?: c)
        }
        standardized = sb.toString()

        // Step 3: Naive vowelization on unvowelized words
        val words = standardized.split(" ").map { naiveVowelize(it) }
        standardized = words.joinToString(" ")

        // Step 4: Transliterate Arabic to Latin (IJMES mapping)
        val transliterated = arabicToLatinIjmes(standardized)

        // Post-processing
        return transliterated
            .replace(Regex("""-\s*"""), "-")
            .replace(Regex("""\bal-lāh\b""", RegexOption.IGNORE_CASE), "Allāh")
            .replace(Regex("""([,؛:])([^\s]|$)""")) { "${it.groupValues[1]} ${it.groupValues[2]}" }
            .trim()
    }

    private fun naiveVowelize(word: String): String {
        if (word.matches(Regex("^(وی|فی|تْهے|ٱللّٰه)$"))) return word
        if (word.any { it in "َُِّٰ" }) return word

        val out = StringBuilder()
        for (i in word.indices) {
            val char = word[i]
            val next = word.getOrNull(i + 1)?.toString() ?: ""
            if (ARABIC_DIACRITICS.matches(next)) {
                out.append(char)
            } else {
                out.append(char).append("َ")
            }
        }
        return out.toString()
    }

    private val IJMES_ARABIC: Map<String, String> = mapOf(
        " " to " ", "," to ",", ";" to ";", "?" to "?", "!" to "!", "\"" to "\"", "'" to "'",
        "(" to "(", ")" to ")", ":" to ":", "+" to "+", "=" to "=", "/" to "/", "-" to "-",
        "،" to ",", "؍" to "/", "؛" to ";", "؟" to "?", "۔" to ".",
        "٠" to "0", "١" to "1", "٢" to "2", "٣" to "3", "٤" to "4",
        "٥" to "5", "٦" to "6", "٧" to "7", "٨" to "8", "٩" to "9",
        "ج" to "j", "ث" to "th", "ت" to "t", "ب" to "b", "س" to "s",
        "ز" to "z", "ر" to "r", "ذ" to "dh", "د" to "d", "خ" to "kh",
        "ح" to "ḥ", "ظ" to "ẓ", "ط" to "ṭ", "ض" to "ḍ", "ص" to "ṣ",
        "ش" to "sh", "ك" to "k", "ق" to "q", "ف" to "f", "غ" to "gh",
        "ع" to "ʿ", "ي" to "y", "و" to "w", "ه" to "h", "ن" to "n",
        "م" to "m", "ل" to "l", "ء" to "ʾ", "ة" to "a", "ال" to "al-",
        "آ" to "ā", "أ" to "a", "إ" to "i", "ؤ" to "u", "ئ" to "i",
        "ى" to "y"
    )

    private val IJMES_VOWELS: Map<String, String> = mapOf(
        "\u064E" to "a", "\u064F" to "u", "\u0650" to "i",
        "\u064B" to "an", "\u064C" to "un", "\u064D" to "in",
        "ا" to "a", "و" to "ū", "ي" to "ī", "آ" to "ā"
    )

    private fun arabicToLatinIjmes(input: String): String {
        val out = StringBuilder()
        val s = input.replace("\u0640", "") // tatweel
        var i = 0
        while (i < s.length) {
            val c = s[i]
            val next = s.getOrNull(i + 1)

            // Check digraph / word prefixes
            if (c == 'ا' && next == 'ل') {
                out.append("al-")
                i += 2
                continue
            }
            if (c == 'ل' && next == 'ا') {
                out.append("lā")
                i += 2
                continue
            }
            // Shadda: double previous consonant
            if (c == '\u0651') {
                if (out.isNotEmpty()) {
                    val lastChar = out.last()
                    out.append(lastChar)
                }
                i++
                continue
            }
            // Sukun: no vowel
            if (c == '\u0652') {
                i++
                continue
            }
            // Diacritic vowels
            val vowel = IJMES_VOWELS[c.toString()]
            if (vowel != null && c in "\u064E\u064F\u0650\u064B\u064C\u064D") {
                out.append(vowel)
                i++
                continue
            }
            // Letters
            val letter = IJMES_ARABIC[c.toString()] ?: IJMES_VOWELS[c.toString()]
            if (letter != null) {
                out.append(letter)
            } else {
                out.append(c)
            }
            i++
        }
        return out.toString()
    }

    // -------------------------------------------------------------------------
    // Greek Transliteration
    // -------------------------------------------------------------------------

    private val GREEK_BASE = mapOf(
        'Α' to "A", 'Β' to "V", 'Γ' to "G", 'Δ' to "D", 'Ε' to "E", 'Ζ' to "Z",
        'Η' to "I", 'Θ' to "Th", 'Ι' to "I", 'Κ' to "K", 'Λ' to "L", 'Μ' to "M",
        'Ν' to "N", 'Ξ' to "X", 'Ο' to "O", 'Π' to "P", 'Ρ' to "R", 'Σ' to "S",
        'Τ' to "T", 'Υ' to "Y", 'Φ' to "F", 'Χ' to "Ch", 'Ψ' to "Ps", 'Ω' to "O",
        'α' to "a", 'β' to "v", 'γ' to "g", 'δ' to "d", 'ε' to "e", 'ζ' to "z",
        'η' to "i", 'θ' to "th", 'ι' to "i", 'κ' to "k", 'λ' to "l", 'μ' to "m",
        'ν' to "n", 'ξ' to "x", 'ο' to "o", 'π' to "p", 'ρ' to "r", 'σ' to "s",
        'ς' to "s", 'τ' to "t", 'υ' to "y", 'φ' to "f", 'χ' to "ch", 'ψ' to "ps",
        'ω' to "o", 'ϊ' to "i", 'ΐ' to "i", 'ϋ' to "y", 'ΰ' to "y", 'Ϊ' to "I", 'Ϋ' to "Y"
    )

    private val GREEK_ACCENTED = mapOf(
        'ά' to "á", 'έ' to "é", 'ή' to "í", 'ί' to "í", 'ό' to "ó", 'ύ' to "ý", 'ώ' to "ó",
        'Ά' to "Á", 'Έ' to "É", 'Ή' to "Í", 'Ί' to "Í", 'Ό' to "Ó", 'Ύ' to "Ý", 'Ώ' to "Ó"
    )

    private val GREEK_PLAIN = mapOf(
        'ά' to "a", 'έ' to "e", 'ή' to "i", 'ί' to "i", 'ό' to "o", 'ύ' to "y", 'ώ' to "o",
        'Ά' to "A", 'Έ' to "E", 'Ή' to "I", 'Ί' to "I", 'Ό' to "O", 'Ύ' to "Y", 'Ώ' to "O"
    )

    fun romanizeGreek(input: String, omitDiacritics: Boolean = false): String {
        var text = input

        // Normalize all-caps Greek words to sentence case
        val words = text.split(" ").map { word ->
            val lettersOnly = word.filter { it.isLetter() }
            if (lettersOnly.isNotEmpty() && lettersOnly.all { it in 'Α'..'Ω' || it in "ΆΈΉΊΌΎΏΪΫ" }) {
                word.first().toString() + word.substring(1).lowercase()
            } else {
                word
            }
        }
        text = words.joinToString(" ")

        // Handle ου
        text = text.replace("ου", "ou").replace("ΟΥ", "OU")
            .replace("Ου", "Ou").replace("ού", "ou").replace("Όυ", "Ou")

        // Handle ευ/αυ based on voicing of next consonant
        val voiceless = Regex("[πκξστφχψθΠΚΞΣΤΦΧΨΘ]")
        text = text.replace(Regex("""(ευ|Ευ|ΕΥ|αυ|Αυ|ΑΥ)(?=[^\sαιεηιουωΑΙΕΗΙΟΥΩ])""")) { m ->
            val match = m.value
            val nextIdx = m.range.last + 1
            val nextChar = text.getOrNull(nextIdx)?.toString() ?: ""
            val isVoiceless = voiceless.containsMatchIn(nextChar)
            val isUpper = match.first().isUpperCase()
            val isE = match.startsWith("ε", ignoreCase = true)
            if (isVoiceless) {
                if (isE) (if (isUpper) "Ef" else "ef") else (if (isUpper) "Af" else "af")
            } else {
                if (isE) (if (isUpper) "Ev" else "ev") else (if (isUpper) "Av" else "av")
            }
        }

        // Digraphs
        text = text
            .replace(Regex("""(^|[\s.,:;!?()«»"'])μπ""", RegexOption.IGNORE_CASE)) { "${it.groupValues[1]}b" }
            .replace(Regex("""(^|[\s.,:;!?()«»"'])ντ""", RegexOption.IGNORE_CASE)) { "${it.groupValues[1]}d" }
            .replace(Regex("""(^|[\s.,:;!?()«»"'])γκ""", RegexOption.IGNORE_CASE)) { "${it.groupValues[1]}g" }
            .replace(Regex("μπ", RegexOption.IGNORE_CASE), "mp")
            .replace(Regex("ντ", RegexOption.IGNORE_CASE), "nt")
            .replace(Regex("γκ", RegexOption.IGNORE_CASE), "ng")
            .replace("γγ", "ng").replace("Γγ", "Ng").replace("ΓΓ", "NG")
            .replace(Regex("τζ", RegexOption.IGNORE_CASE), "tz")
            .replace(Regex("τσ", RegexOption.IGNORE_CASE), "ts")

        // Soft gamma before front vowels
        text = text.replace(Regex("Γ(?=[αιεηι])"), "Y").replace(Regex("γ(?=[αιεηι])"), "y")

        val sb = StringBuilder(text.length)
        for (c in text) {
            val mapped = if (!omitDiacritics && c in GREEK_ACCENTED) {
                GREEK_ACCENTED[c]
            } else if (omitDiacritics && c in GREEK_PLAIN) {
                GREEK_PLAIN[c]
            } else {
                GREEK_BASE[c]
            }
            sb.append(mapped ?: c)
        }
        return sb.toString()
    }

    // -------------------------------------------------------------------------
    // Cyrillic Transliteration
    // -------------------------------------------------------------------------

    private val CYRILLIC_GLOBAL = mapOf(
        'а' to "a", 'б' to "b", 'в' to "v", 'г' to "g", 'д' to "d", 'е' to "e",
        'ё' to "yo", 'ж' to "zh", 'з' to "z", 'и' to "i", 'й' to "i", 'к' to "k",
        'л' to "l", 'м' to "m", 'н' to "n", 'о' to "o", 'п' to "p", 'р' to "r",
        'с' to "s", 'т' to "t", 'у' to "u", 'ф' to "f", 'х' to "kh", 'ц' to "ts",
        'ч' to "ch", 'ш' to "sh", 'щ' to "shch", 'ъ' to "", 'ы' to "y", 'ь' to "",
        'э' to "e", 'ю' to "yu", 'я' to "ya",
        // Extended Cyrillic
        'ә' to "ä", 'ғ' to "gh", 'ґ' to "g", 'ђ' to "đ", 'ѓ' to "ǵ", 'є' to "ye",
        'ѕ' to "dz", 'і' to "i", 'ї' to "yi", 'ј' to "j", 'љ' to "lj", 'њ' to "nj",
        'ћ' to "ć", 'ќ' to "ḱ", 'ў' to "u", 'џ' to "dž", 'қ' to "q", 'ң' to "ng",
        'ө' to "ö", 'ұ' to "u", 'ү' to "ü", 'һ' to "h", 'ҷ' to "ch", 'ӣ' to "y", 'ӯ' to "u"
    )

    fun romanizeCyrillic(input: String, language: String = "ru", asciiOnly: Boolean = false): String {
        if (input.isEmpty()) return ""
        val normalized = Normalizer.normalize(input, Normalizer.Form.NFC)
        val sb = StringBuilder(normalized.length + 8)
        val lang = language.lowercase()

        var isWordBoundary = true
        var i = 0
        while (i < normalized.length) {
            val c = normalized[i]
            val lower = c.lowercaseChar()
            val isUpper = c.isUpperCase()
            val prev = normalized.getOrNull(i - 1)?.lowercaseChar() ?: ' '
            val next = normalized.getOrNull(i + 1)?.lowercaseChar() ?: ' '

            if (c.isWhitespace() || !c.isLetter()) {
                sb.append(c)
                isWordBoundary = true
                i++
                continue
            }

            var replacement: String? = null

            // Belarusian special e
            if (lang == "be" && lower == 'е') {
                replacement = if (isWordBoundary) "ye" else if (prev !in "аеёіоуыэюя") "ie" else "e"
            }
            // Ukrainian special zg
            else if (lang == "uk" && lower == 'г' && prev == 'з') {
                replacement = "gh"
            }
            // Ukrainian 'и' is 'y'
            else if (lang == "uk" && lower == 'и') {
                replacement = "y"
            }
            // Russian final -ый -> yy / yi
            else if (lang == "ru" && lower == 'ы' && next == 'й') {
                val afterNext = normalized.getOrNull(i + 2)?.lowercaseChar() ?: ' '
                if (afterNext !in 'а'..'я' && afterNext != 'ё') {
                    replacement = if (asciiOnly) "yi" else "yy"
                    i++ // skip 'й'
                }
            }
            // Bulgarian 'ъ' is 'a' / 'ă', 'щ' is 'sht'
            else if (lang == "bg") {
                if (lower == 'ъ') replacement = if (asciiOnly) "a" else "ă"
                if (lower == 'щ') replacement = "sht"
            }

            if (replacement == null) {
                replacement = CYRILLIC_GLOBAL[lower] ?: lower.toString()
            }

            if (isUpper && replacement.isNotEmpty()) {
                val formatted = if (replacement.length > 1) {
                    replacement[0].uppercaseChar() + replacement.substring(1)
                } else {
                    replacement.uppercase()
                }
                sb.append(formatted)
            } else {
                sb.append(replacement)
            }

            isWordBoundary = false
            i++
        }
        return sb.toString()
    }

    // -------------------------------------------------------------------------
    // Indic Transliteration
    // -------------------------------------------------------------------------

    fun romanizeIndic(input: String, language: String = "hi", asciiOnly: Boolean = false): String {
        // Drop Schwa for Hindi / Devanagari first
        val preprocessed = IndicSchwa.drop(input).replace("।", ".").replace("॥", ".")
        val sb = StringBuilder()

        for (c in preprocessed) {
            val code = c.code
            // Normalize Devanagari, Bengali, Gurmukhi, Gujarati, Tamil, Telugu, Kannada, Malayalam
            val mapped = when {
                code in 0x0900..0x097F -> mapDevanagariChar(code, asciiOnly)
                code in 0x0980..0x09FF -> mapBengaliChar(code, asciiOnly)
                code in 0x0A00..0x0A7F -> mapGurmukhiChar(code, asciiOnly)
                code in 0x0A80..0x0AFF -> mapGujaratiChar(code, asciiOnly)
                code in 0x0B80..0x0BFF -> mapTamilChar(code, asciiOnly)
                code in 0x0C00..0x0C7F -> mapTeluguChar(code, asciiOnly)
                code in 0x0C80..0x0CFF -> mapKannadaChar(code, asciiOnly)
                code in 0x0D00..0x0D7F -> mapMalayalamChar(code, asciiOnly)
                else -> c.toString()
            }
            sb.append(mapped)
        }
        return sb.toString().replace(Regex("""\s+"""), " ").trim()
    }

    private fun mapDevanagariChar(code: Int, asciiOnly: Boolean): String = when (code) {
        0x0905 -> "a"
        0x0906 -> if (asciiOnly) "aa" else "ā"
        0x0907 -> "i"
        0x0908 -> if (asciiOnly) "ee" else "ī"
        0x0909 -> "u"
        0x090A -> if (asciiOnly) "oo" else "ū"
        0x090F -> "e"
        0x0910 -> "ai"
        0x0913 -> "o"
        0x0914 -> "au"
        0x0915 -> "k"
        0x0916 -> "kh"
        0x0917 -> "g"
        0x0918 -> "gh"
        0x0919 -> "ng"
        0x091A -> "ch"
        0x091B -> "chh"
        0x091C -> "j"
        0x091D -> "jh"
        0x091E -> "ny"
        0x091F -> "t"
        0x0920 -> "th"
        0x0921 -> "d"
        0x0922 -> "dh"
        0x0923 -> "n"
        0x0924 -> "t"
        0x0925 -> "th"
        0x0926 -> "d"
        0x0927 -> "dh"
        0x0928 -> "n"
        0x092A -> "p"
        0x092B -> "ph"
        0x092C -> "b"
        0x092D -> "bh"
        0x092E -> "m"
        0x092F -> "y"
        0x0930 -> "r"
        0x0932 -> "l"
        0x0935 -> "v"
        0x0936 -> "sh"
        0x0937 -> "sh"
        0x0938 -> "s"
        0x0939 -> "h"
        0x093E -> if (asciiOnly) "aa" else "ā"
        0x093F -> "i"
        0x0940 -> if (asciiOnly) "ee" else "ī"
        0x0941 -> "u"
        0x0942 -> if (asciiOnly) "oo" else "ū"
        0x0947 -> "e"
        0x0948 -> "ai"
        0x094B -> "o"
        0x094C -> "au"
        0x0902 -> "n" // Anusvara
        0x0901 -> "n" // Chandrabindu
        0x094D -> "" // Virama
        else -> ""
    }

    private fun mapBengaliChar(code: Int, asciiOnly: Boolean): String = mapDevanagariChar(code - 0x0980 + 0x0900, asciiOnly)
    private fun mapGurmukhiChar(code: Int, asciiOnly: Boolean): String = mapDevanagariChar(code - 0x0A00 + 0x0900, asciiOnly)
    private fun mapGujaratiChar(code: Int, asciiOnly: Boolean): String = mapDevanagariChar(code - 0x0A80 + 0x0900, asciiOnly)
    private fun mapTamilChar(code: Int, asciiOnly: Boolean): String = when (code) {
        0x0B85 -> "a"
        0x0B86 -> if (asciiOnly) "aa" else "ā"
        0x0B87 -> "i"
        0x0B88 -> if (asciiOnly) "ee" else "ī"
        0x0B89 -> "u"
        0x0B8A -> if (asciiOnly) "oo" else "ū"
        0x0B8E -> "e"
        0x0B8F -> "e"
        0x0B90 -> "ai"
        0x0B92 -> "o"
        0x0B93 -> "o"
        0x0B94 -> "au"
        0x0B95 -> "k"
        0x0B99 -> "ng"
        0x0B9A -> "ch"
        0x0B9E -> "ny"
        0x0B9F -> "t"
        0x0BA3 -> "n"
        0x0BA4 -> "t"
        0x0BA8 -> "n"
        0x0BA9 -> "n"
        0x0BAA -> "p"
        0x0BAE -> "m"
        0x0BAF -> "y"
        0x0BB0 -> "r"
        0x0BB1 -> "r"
        0x0BB2 -> "l"
        0x0BB3 -> "l"
        0x0BB4 -> "zh"
        0x0BB5 -> "v"
        0x0BB8 -> "s"
        0x0BB9 -> "h"
        0x0BBE -> if (asciiOnly) "aa" else "ā"
        0x0BBF -> "i"
        0x0BC0 -> if (asciiOnly) "ee" else "ī"
        0x0BC1 -> "u"
        0x0BC2 -> if (asciiOnly) "oo" else "ū"
        0x0BC6 -> "e"
        0x0BC7 -> "e"
        0x0BC8 -> "ai"
        0x0BCA -> "o"
        0x0BCB -> "o"
        0x0BCC -> "au"
        0x0BCD -> ""
        else -> ""
    }
    private fun mapTeluguChar(code: Int, asciiOnly: Boolean): String = mapDevanagariChar(code - 0x0C00 + 0x0900, asciiOnly)
    private fun mapKannadaChar(code: Int, asciiOnly: Boolean): String = mapDevanagariChar(code - 0x0C80 + 0x0900, asciiOnly)
    private fun mapMalayalamChar(code: Int, asciiOnly: Boolean): String = mapDevanagariChar(code - 0x0D00 + 0x0900, asciiOnly)

    // -------------------------------------------------------------------------
    // Thai Transliteration (RTGS - Royal Thai General System)
    // -------------------------------------------------------------------------

    private val THAI_SPECIAL_WORDS = mapOf(
        "สวัสดีครับ" to "sawatdi khrap",
        "สวัสดีค่ะ" to "sawatdi kha",
        "สวัสดี" to "sawatdi",
        "ครับ" to "khrap",
        "ค่ะ" to "kha",
        "ขอบคุณ" to "khop khun",
        "รัก" to "rak",
        "ใจ" to "chai"
    )

    private val THAI_CONSONANTS_INITIAL = mapOf(
        'ก' to "k", 'ข' to "kh", 'ฃ' to "kh", 'ค' to "kh", 'ฅ' to "kh", 'ฆ' to "kh",
        'ง' to "ng", 'จ' to "ch", 'ฉ' to "ch", 'ช' to "ch", 'ซ' to "s", 'ฌ' to "ch",
        'ญ' to "y", 'ฎ' to "d", 'ฏ' to "t", 'ฐ' to "th", 'ฑ' to "th", 'ฒ' to "th",
        'ณ' to "n", 'ด' to "d", 'ต' to "t", 'ถ' to "th", 'ท' to "th", 'ธ' to "th",
        'น' to "n", 'บ' to "b", 'ป' to "p", 'ผ' to "ph", 'ฝ' to "f", 'พ' to "ph",
        'ฟ' to "f", 'ภ' to "ph", 'ม' to "m", 'ย' to "y", 'ร' to "r", 'ฤ' to "rue",
        'ล' to "l", 'ว' to "w", 'ศ' to "s", 'ษ' to "s", 'ส' to "s", 'ห' to "h",
        'ฬ' to "l", 'อ' to "", 'ฮ' to "h"
    )

    private val THAI_CONSONANTS_FINAL = mapOf(
        'ก' to "k", 'ข' to "k", 'ค' to "k", 'ฆ' to "k",
        'ง' to "ng",
        'จ' to "t", 'ฉ' to "t", 'ช' to "t", 'ซ' to "t", 'ฌ' to "t",
        'ฎ' to "t", 'ฏ' to "t", 'ฐ' to "t", 'ฑ' to "t", 'ฒ' to "t",
        'ด' to "t", 'ต' to "t", 'ถ' to "t", 'ท' to "t", 'ธ' to "t",
        'ศ' to "t", 'ษ' to "t", 'ส' to "t",
        'ญ' to "n", 'ณ' to "n", 'น' to "n", 'ร' to "n", 'ล' to "n", 'ฬ' to "n",
        'บ' to "p", 'ป' to "p", 'ผ' to "p", 'ฝ' to "p", 'พ' to "p", 'ฟ' to "p", 'ภ' to "p",
        'ม' to "m",
        'ย' to "y",
        'ว' to "w"
    )

    fun romanizeThai(input: String): String {
        var text = input

        // Check common full phrases
        for ((thai, rom) in THAI_SPECIAL_WORDS) {
            text = text.replace(thai, " $rom ")
        }

        // Syllable transliteration
        val sb = StringBuilder()
        var i = 0
        while (i < text.length) {
            val c = text[i]
            if (c !in '\u0E00'..'\u0E7F') {
                sb.append(c)
                i++
                continue
            }

            // Skip tone marks and cancellation mark
            if (c in '\u0E48'..'\u0E4C') {
                i++
                continue
            }

            // Vowels
            when (c) {
                'ะ', 'ั' -> sb.append("a")
                'า' -> sb.append("a")
                'ำ' -> sb.append("am")
                'ิ', 'ี' -> sb.append("i")
                'ึ', 'ื' -> sb.append("ue")
                'ุ', 'ู' -> sb.append("u")
                'เ' -> sb.append("e")
                'แ' -> sb.append("ae")
                'โ' -> sb.append("o")
                'ใ', 'ไ' -> sb.append("ai")
                'ๆ' -> sb.append("")
                else -> {
                    // Consonants
                    val isEnd = i + 1 == text.length || text[i + 1].isWhitespace() || text[i + 1] !in '\u0E00'..'\u0E7F'
                    val initial = THAI_CONSONANTS_INITIAL[c]
                    val final = THAI_CONSONANTS_FINAL[c]
                    if (isEnd && final != null) {
                        sb.append(final)
                    } else if (initial != null) {
                        sb.append(initial)
                        // Add implicit 'a' or 'o' if between consonants without explicit vowel
                        val next = text.getOrNull(i + 1)
                        if (next != null && next in '\u0E00'..'\u0E2E') {
                            val nextNext = text.getOrNull(i + 2)
                            if (nextNext == null || nextNext in '\u0E00'..'\u0E2E' || nextNext.isWhitespace()) {
                                sb.append("a")
                            }
                        }
                    } else {
                        sb.append(c)
                    }
                }
            }
            i++
        }

        return sb.toString()
            .replace(Regex("""\s+"""), " ")
            .replace(Regex("""\s*([/.,!?;:])\s*""")) { "${it.groupValues[1]} " }
            .trim()
    }

    // -------------------------------------------------------------------------
    // Cantonese Transliteration
    // -------------------------------------------------------------------------

    private val CANTONESE_WORDS = mapOf(
        "你好" to "lee ho",
        "早晨" to "jo san",
        "多謝" to "doh je",
        "唔該" to "m goi",
        "對唔住" to "deui m jyu",
        "再見" to "joi gin",
        "世界" to "sai gaai",
        "朋友" to "pang yau",
        "愛" to "oi",
        "我" to "ngo",
        "你" to "nei",
        "佢" to "keui",
        "香港" to "heung gong"
    )

    fun romanizeCantonese(input: String): String {
        var text = input
        for ((word, rom) in CANTONESE_WORDS) {
            text = text.replace(word, " $rom ")
        }
        return text.replace(Regex("""\s+"""), " ").trim()
    }

    // -------------------------------------------------------------------------
    // Mandarin Transliteration
    // -------------------------------------------------------------------------

    private val PINYIN_COMMON = mapOf(
        "你好" to "ni hao", "早上好" to "zao shang hao", "谢谢" to "xie xie",
        "再见" to "zai jian", "世界" to "shi jie", "朋友" to "peng you",
        "爱" to "ai", "我" to "wo", "你" to "ni", "他" to "ta", "她" to "ta",
        "我们" to "wo men", "你们" to "ni men", "他们" to "ta men",
        "今天" to "jin tian", "明天" to "ming tian", "昨天" to "zuo tian",
        "中国" to "zhong guo", "北京" to "bei jing", "上海" to "shang hai",
        "喜欢" to "xi huan", "快乐" to "kuai le", "时间" to "shi jian"
    )

    fun romanizeMandarin(input: String, omitTones: Boolean = true): String {
        var text = input
        for ((word, rom) in PINYIN_COMMON) {
            text = text.replace(word, " $rom ")
        }
        return text.replace(Regex("""\s+"""), " ").trim()
    }

    // -------------------------------------------------------------------------
    // Japanese Transliteration (Kana & Kanji Romaji)
    // -------------------------------------------------------------------------

    private val KANA_ROMAJI = mapOf(
        "あ" to "a", "い" to "i", "う" to "u", "え" to "e", "お" to "o",
        "か" to "ka", "き" to "ki", "く" to "ku", "け" to "ke", "こ" to "ko",
        "さ" to "sa", "し" to "shi", "す" to "su", "せ" to "se", "そ" to "so",
        "た" to "ta", "ち" to "chi", "つ" to "tsu", "て" to "te", "と" to "to",
        "な" to "na", "に" to "ni", "ぬ" to "nu", "ね" to "ne", "の" to "no",
        "は" to "ha", "ひ" to "hi", "ふ" to "fu", "へ" to "he", "ほ" to "ho",
        "ま" to "ma", "み" to "mi", "む" to "mu", "め" to "me", "も" to "mo",
        "や" to "ya", "ゆ" to "yu", "よ" to "yo",
        "ら" to "ra", "り" to "ri", "る" to "ru", "れ" to "re", "ろ" to "ro",
        "わ" to "wa", "を" to "wo", "ん" to "n",
        "が" to "ga", "ぎ" to "gi", "ぐ" to "gu", "げ" to "ge", "ご" to "go",
        "ざ" to "za", "じ" to "ji", "ず" to "zu", "ぜ" to "ze", "ぞ" to "zo",
        "だ" to "da", "ぢ" to "ji", "づ" to "zu", "で" to "de", "ど" to "do",
        "ば" to "ba", "び" to "bi", "ぶ" to "bu", "べ" to "be", "ぼ" to "bo",
        "ぱ" to "pa", "ぴ" to "pi", "ぷ" to "pu", "ぺ" to "pe", "ぽ" to "po",
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
        // Katakana
        "ア" to "a", "イ" to "i", "ウ" to "u", "エ" to "e", "オ" to "o",
        "カ" to "ka", "キ" to "ki", "ク" to "ku", "ケ" to "ke", "コ" to "ko",
        "サ" to "sa", "シ" to "shi", "ス" to "su", "セ" to "se", "ソ" to "so",
        "タ" to "ta", "チ" to "chi", "ツ" to "tsu", "テ" to "te", "ト" to "to",
        "ナ" to "na", "ニ" to "ni", "ヌ" to "nu", "ネ" to "ne", "ノ" to "no",
        "ハ" to "ha", "ヒ" to "hi", "フ" to "fu", "ヘ" to "he", "ホ" to "ho",
        "マ" to "ma", "ミ" to "mi", "ム" to "mu", "メ" to "me", "モ" to "mo",
        "ヤ" to "ya", "ユ" to "yu", "ヨ" to "yo",
        "ラ" to "ra", "リ" to "ri", "ル" to "ru", "レ" to "re", "ロ" to "ro",
        "ワ" to "wa", "ヲ" to "wo", "ン" to "n",
        "ガ" to "ga", "ギ" to "gi", "グ" to "gu", "ゲ" to "ge", "ゴ" to "go",
        "ザ" to "za", "ジ" to "ji", "ズ" to "zu", "ゼ" to "ze", "ゾ" to "zo",
        "ダ" to "da", "ヂ" to "ji", "ヅ" to "zu", "デ" to "de", "ド" to "do",
        "バ" to "ba", "ビ" to "bi", "ブ" to "bu", "ベ" to "be", "ボ" to "bo",
        "パ" to "pa", "ピ" to "pi", "プ" to "pu", "ペ" to "pe", "ポ" to "po"
    )

    private val JAPANESE_COMMON_KANJI = mapOf(
        "私" to "watashi", "元気" to "genki", "です" to "desu", "恵まれた" to "megumareta",
        "永遠" to "eien", "東京" to "tokyo", "夜空" to "yozora", "光" to "hikari",
        "朝" to "asa", "窓" to "mado", "届く" to "todoku", "駅" to "eki",
        "行く" to "iku", "待つ" to "matsu", "君" to "kimi", "好き" to "suki",
        "晴れる" to "hareru", "明日" to "ashita", "今日" to "kyou", "時間" to "jikan",
        "愛" to "ai", "心" to "kokoro", "風" to "kaze", "夢" to "yume",
        "ありがとう" to "arigatō", "こんにちは" to "konnichiwa", "こんばんは" to "konbanwa"
    )

    fun romanizeJapanese(input: String): String {
        var text = input

        // Common kanji words replacement
        for ((kanji, romaji) in JAPANESE_COMMON_KANJI) {
            text = text.replace(kanji, " $romaji ")
        }

        // Digraph kana replacement
        val out = StringBuilder()
        var i = 0
        while (i < text.length) {
            if (i + 1 < text.length) {
                val two = text.substring(i, i + 2)
                if (KANA_ROMAJI.containsKey(two)) {
                    out.append(KANA_ROMAJI[two]).append(" ")
                    i += 2
                    continue
                }
            }
            val one = text[i].toString()
            if (KANA_ROMAJI.containsKey(one)) {
                out.append(KANA_ROMAJI[one]).append(" ")
            } else if (text[i] == 'っ' || text[i] == 'ッ') {
                // Sokuon: double the next consonant if possible
                val nextChar = text.getOrNull(i + 1)?.toString()
                val nextRom = if (nextChar != null) KANA_ROMAJI[nextChar] else null
                if (nextRom != null && nextRom.isNotEmpty()) {
                    out.append(nextRom.first())
                }
            } else if (text[i] == 'ー') {
                // Long vowel mark
                out.append("-")
            } else {
                out.append(text[i])
            }
            i++
        }

        return out.toString()
            .replace(Regex("""\b(\w+)\s+(ta|te|nai|masu|desu|da)\b"""), "$1$2")
            .replace(Regex("""\s+"""), " ")
            .trim()
    }

    // -------------------------------------------------------------------------
    // Korean Transliteration
    // -------------------------------------------------------------------------

    fun romanizeKorean(input: String): String {
        return HangulLatin.of(Normalizer.normalize(input, Normalizer.Form.NFC))
    }
}
