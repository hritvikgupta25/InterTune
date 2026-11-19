/*
 * Copyright (C) 2026 InterTune
 *
 * SPDX-License-Identifier: GPL-3.0
 */

package com.dd3boh.outertune.lyrics

import org.akanework.gramophone.logic.utils.SemanticLyrics
import org.akanework.gramophone.logic.utils.SemanticLyrics.LyricLine
import org.akanework.gramophone.logic.utils.SemanticLyrics.SyncedLyrics
import org.akanework.gramophone.logic.utils.SemanticLyrics.UnsyncedLyrics
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Tests for [LyricsRomanizer] and [RomanizeString].
 * Verifies romanization for scripts not supported by the inbuilt [LyricsLatin] (such as Arabic,
 * Thai, Persian, Urdu), multi-language romanization from romanize-string, and automatic paired line
 * injection for both synced and unsynced lyrics.
 */
class LyricsRomanizerTest {

    @Test
    fun `arabic words and phrases romanize to latin script`() {
        val habibi = RomanizeString.romanize("حبيبي")
        assertNotNull(habibi)
        assertTrue("Expected latin letters in: $habibi", habibi!!.any { it in 'a'..'z' || it in 'A'..'Z' })

        val salam = RomanizeString.romanize("السلام عليكم")
        assertNotNull(salam)
        assertTrue(salam!!.any { it in 'a'..'z' || it in 'A'..'Z' })

        val allah = RomanizeString.romanize("ٱللّٰه")
        assertNotNull(allah)
        assertTrue(allah!!.contains("llah", ignoreCase = true))
    }

    @Test
    fun `farsi and urdu phonetic mappings work properly`() {
        // Farsi
        val pedar = RomanizeString.romanizeString("پدر", "fa")
        assertEquals("pedar", pedar.lowercase())

        // Urdu
        val ghar = RomanizeString.romanizeString("گھر", "ur")
        assertEquals("ghar", ghar.lowercase())
    }

    @Test
    fun `thai romanizes to latin script via RTGS`() {
        val sawasdee = RomanizeString.romanize("สวัสดี")
        assertNotNull(sawasdee)
        assertTrue(sawasdee!!.startsWith("sa", ignoreCase = true))

        val sawasdeeKrup = RomanizeString.romanize("สวัสดีครับ")
        assertNotNull(sawasdeeKrup)
        assertTrue(sawasdeeKrup!!.any { it in 'a'..'z' || it in 'A'..'Z' })
    }

    @Test
    fun `greek romanizes properly with diphthongs and digraphs`() {
        val kalimera = RomanizeString.romanize("Καλημέρα")
        assertNotNull(kalimera)
        assertTrue(kalimera!!.contains("kalimer", ignoreCase = true))

        val ouranos = RomanizeString.romanize("ουρανός")
        assertNotNull(ouranos)
        assertTrue(ouranos!!.startsWith("ouran", ignoreCase = true))
    }

    @Test
    fun `cyrillic languages romanize according to rules`() {
        val privet = RomanizeString.romanize("Привет мир")
        assertNotNull(privet)
        assertTrue(privet!!.contains("Privet", ignoreCase = true) || privet.contains("mir", ignoreCase = true))

        val kyiv = RomanizeString.romanizeString("Київ", "uk")
        assertEquals("Kyiv", kyiv)

        val bulgaria = RomanizeString.romanizeString("България", "bg")
        assertEquals("Balgariya", bulgaria)
    }

    @Test
    fun `indic scripts romanize with schwa and inherent vowels`() {
        val namaste = RomanizeString.romanize("नमस्ते")
        assertNotNull(namaste)
        assertTrue(namaste!!.contains("namast", ignoreCase = true))

        val vanakkam = RomanizeString.romanize("வணக்கம்")
        assertNotNull(vanakkam)
        assertTrue(vanakkam!!.contains("vana", ignoreCase = true))
    }

    @Test
    fun `cjk scripts romanize cleanly`() {
        // Mandarin
        val chinese = RomanizeString.romanizeString("早上好", "zh")
        assertTrue(chinese.contains("zao", ignoreCase = true))

        // Cantonese
        val cantonese = RomanizeString.romanizeString("早晨", "yue")
        assertTrue(cantonese.contains("zou", ignoreCase = true))

        // Japanese
        val japanese = RomanizeString.romanize("朝の光が窓に届く")
        assertNotNull(japanese)
        assertTrue(japanese!!.any { it in 'a'..'z' || it in 'A'..'Z' })

        // Korean
        val korean = RomanizeString.romanize("안녕하세요")
        assertNotNull(korean)
        assertTrue(korean!!.any { it in 'a'..'z' || it in 'A'..'Z' })
    }

    @Test
    fun `latin and english text is not romanized`() {
        assertNull(RomanizeString.romanize("Hello world, this is English."))
        assertNull(RomanizeString.romanize("12345 !@#$%"))
        assertNull(RomanizeString.romanize(""))
    }

    @Test
    fun `LyricsRomanizer romanizeLine delegates appropriately`() {
        // Line in Arabic (unsupported by LyricsLatin) should produce romanized text
        val arabic = LyricsRomanizer.romanizeLine("حبيبي")
        assertNotNull(arabic)
        assertTrue(arabic!!.any { it in 'a'..'z' || it in 'A'..'Z' })

        // English text should return null (no romanization needed)
        val english = LyricsRomanizer.romanizeLine("I will always love you")
        assertNull(english)
    }

    @Test
    fun `LyricsRomanizer romanizes synced lyrics with paired lines`() {
        val input = SyncedLyrics(
            listOf(
                LyricLine(text = "حبيبي", start = 1000uL, end = 3000uL),
                LyricLine(text = "Hello world", start = 3000uL, end = 5000uL),
                LyricLine(text = "สวัสดีครับ", start = 5000uL, end = 7000uL),
            )
        )

        val romanized = LyricsRomanizer.romanize(input)
        assertTrue(romanized is SyncedLyrics)
        val lines = (romanized as SyncedLyrics).text

        // Arabic gets a translated pair, English doesn't, Thai gets a translated pair: 3 original + 2 romanized = 5
        assertEquals(5, lines.size)

        // Line 0: original Arabic
        assertEquals("حبيبي", lines[0].text)
        assertFalse(lines[0].isTranslated)

        // Line 1: romanized Arabic paired at same time
        assertTrue(lines[1].isTranslated)
        assertEquals(1000uL, lines[1].start)
        assertEquals(3000uL, lines[1].end)
        assertTrue(lines[1].text.any { it in 'a'..'z' || it in 'A'..'Z' })

        // Line 2: English line untouched
        assertEquals("Hello world", lines[2].text)
        assertFalse(lines[2].isTranslated)

        // Line 3: original Thai
        assertEquals("สวัสดีครับ", lines[3].text)
        assertFalse(lines[3].isTranslated)

        // Line 4: romanized Thai
        assertTrue(lines[4].isTranslated)
        assertEquals(5000uL, lines[4].start)
        assertEquals(7000uL, lines[4].end)
        assertTrue(lines[4].text.any { it in 'a'..'z' || it in 'A'..'Z' })
    }

    @Test
    fun `LyricsRomanizer does not duplicate synced lyrics already containing translations`() {
        val input = SyncedLyrics(
            listOf(
                LyricLine(text = "حبيبي", start = 1000uL, end = 3000uL),
                LyricLine(text = "My love", start = 1000uL, end = 3000uL, isTranslated = true),
            )
        )

        val romanized = LyricsRomanizer.romanize(input)
        assertEquals(input, romanized)
    }

    @Test
    fun `LyricsRomanizer romanizes unsynced lyrics with paired lines`() {
        val input = UnsyncedLyrics(
            listOf(
                "حبيبي" to null,
                "Just an English line" to null,
                "Καλημέρα" to null,
            )
        )

        val romanized = LyricsRomanizer.romanize(input)
        assertTrue(romanized is UnsyncedLyrics)
        val lines = (romanized as UnsyncedLyrics).unsyncedText

        // 3 original + 2 romanized = 5
        assertEquals(5, lines.size)
        assertEquals("حبيبي", lines[0].first)
        assertTrue(lines[1].first.any { it in 'a'..'z' || it in 'A'..'Z' })
        assertEquals("Just an English line", lines[2].first)
        assertEquals("Καλημέρα", lines[3].first)
        assertTrue(lines[4].first.any { it in 'a'..'z' || it in 'A'..'Z' })
    }
}
