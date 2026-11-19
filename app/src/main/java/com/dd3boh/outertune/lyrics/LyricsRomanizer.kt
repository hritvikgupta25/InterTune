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
import org.akanework.gramophone.logic.utils.SpeakerEntity

/**
 * Romanization feature orchestrator.
 * Automatically romanizes lyrics fetched in non-Latin scripts (such as Arabic, Thai, Persian, Urdu,
 * Greek, Cyrillic, Indic, Japanese, Korean, Chinese, etc.) using [RomanizeString], prioritizing the
 * app's current inbuilt Latin Script feature ([LyricsLatin]) when it produces a Latin version.
 */
object LyricsRomanizer {

    /**
     * Romanizes a single [line] into Latin script.
     *
     * First checks if the app's current inbuilt Latin Script feature ([LyricsLatin]) provides a Latin
     * version. If not (for instance scripts classified as other like Arabic, Thai, Urdu, or when ICU
     * is unavailable), uses [RomanizeString].
     */
    fun romanizeLine(
        line: String,
        songScripts: SongScripts = SongScripts(),
        tools: LatinTools? = latinTools(),
    ): String? {
        val trimmed = line.trim()
        if (trimmed.isEmpty()) return null

        // 1. Check if the app's current inbuilt Latin Script feature romanizes it
        val inbuilt = if (tools != null) LyricsLatin.latinOf(trimmed, songScripts, tools) else null
        if (inbuilt != null && inbuilt.isNotBlank() && inbuilt != trimmed) {
            return inbuilt
        }

        // 2. Fall back to multi-language romanization engine (romanize-string)
        val romanized = RomanizeString.romanize(trimmed)
        if (romanized != null && romanized.isNotBlank() && romanized != trimmed) {
            return romanized
        }

        return null
    }

    /**
     * Romanizes [lyrics] automatically by generating paired romanized lines marked as
     * [LyricLine.isTranslated] for synced lyrics or subtitle lines for unsynced lyrics.
     * If the lyrics already contain translated/romanized lines, they are returned untouched.
     */
    fun romanize(
        lyrics: SemanticLyrics,
        tools: LatinTools? = latinTools(),
    ): SemanticLyrics {
        return when (lyrics) {
            is SyncedLyrics -> romanizeSynced(lyrics, tools)
            is UnsyncedLyrics -> romanizeUnsynced(lyrics, tools)
        }
    }

    private fun romanizeSynced(lyrics: SyncedLyrics, tools: LatinTools?): SemanticLyrics {
        // If the lyrics already have translated lines from the provider, don't duplicate
        if (lyrics.text.any { it.isTranslated }) {
            return lyrics
        }

        val allLines = lyrics.text.map { it.text }
        val songScripts = LyricsLatin.songScripts(allLines)
        val out = ArrayList<LyricLine>(lyrics.text.size * 2)
        var addedAny = false

        for (item in lyrics.text) {
            out.add(item)
            val rom = romanizeLine(item.text, songScripts, tools)
            if (rom != null && rom != item.text) {
                out.add(
                    LyricLine(
                        text = rom,
                        start = item.start,
                        end = item.end,
                        words = null,
                        speaker = item.speaker,
                        isTranslated = true,
                    )
                )
                addedAny = true
            }
        }

        return if (addedAny) SyncedLyrics(out) else lyrics
    }

    private fun romanizeUnsynced(lyrics: UnsyncedLyrics, tools: LatinTools?): SemanticLyrics {
        val allLines = lyrics.unsyncedText.map { it.first }
        val songScripts = LyricsLatin.songScripts(allLines)
        val out = ArrayList<Pair<String, SpeakerEntity?>>(lyrics.unsyncedText.size * 2)
        var addedAny = false

        for ((text, speaker) in lyrics.unsyncedText) {
            out.add(text to speaker)
            val rom = romanizeLine(text, songScripts, tools)
            if (rom != null && rom != text) {
                out.add(rom to speaker)
                addedAny = true
            }
        }

        return if (addedAny) UnsyncedLyrics(out) else lyrics
    }
}
