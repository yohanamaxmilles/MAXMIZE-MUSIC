package com.example.data.lyrics

import com.example.data.model.LyricLine
import com.example.data.model.LyricWord
import java.util.Locale

data class ParsedLyrics(
    val lines: List<LyricLine>,
    val isSynced: Boolean,
    val hasWordSync: Boolean,
    val offsetMs: Long = 0L,
    val titleTag: String? = null,
    val artistTag: String? = null,
    val albumTag: String? = null,
)

/**
 * Dedicated LRC and plain-text lyrics parser.
 * Supports:
 * - [mm:ss.xx], [mm:ss.xxx], [mm:ss:xx], [hh:mm:ss.xx], [hh:mm:ss.xxx]
 * - Multiple timestamps per line (e.g., [00:10.50][00:45.00]Chorus)
 * - Enhanced word-level LRC (<mm:ss.xx>word)
 * - [offset:+/-ms] and standard metadata tags ([ti:], [ar:], [al:], etc.)
 * - Dual-language translation merging for identical timestamps
 * - Plain unsynced lyrics without inventing synthetic timestamps
 */
object LrcParser {

    // Matches [mm:ss], [mm:ss.xx], [mm:ss:xx], [hh:mm:ss.xx], [hh:mm:ss.xxx]
    private val lineTimestampRegex = Regex(
        """\[(\d{1,3}):(\d{2})(?::(\d{2}))?(?:\.(\d{1,3}))?]"""
    )
    private val wordTimestampRegex = Regex(
        """<(\d{1,3}):(\d{2})(?::(\d{2}))?(?:\.(\d{1,3}))?>([^<]*)"""
    )
    private val offsetTagRegex = Regex("""^\[offset:\s*([+-]?\d+)\s*]$""", RegexOption.IGNORE_CASE)
    private val titleTagRegex = Regex("""^\[ti:\s*(.*?)\s*]$""", RegexOption.IGNORE_CASE)
    private val artistTagRegex = Regex("""^\[ar:\s*(.*?)\s*]$""", RegexOption.IGNORE_CASE)
    private val albumTagRegex = Regex("""^\[al:\s*(.*?)\s*]$""", RegexOption.IGNORE_CASE)
    private val otherMetadataTagRegex = Regex(
        """^\[(ti|ar|al|au|by|offset|re|ve|length|tool|la):.*]$""",
        RegexOption.IGNORE_CASE,
    )

    fun parse(rawText: String): ParsedLyrics {
        val syncedEntries = mutableListOf<LyricLine>()
        val plainLines = mutableListOf<LyricLine>()
        var globalOffsetMs = 0L
        var hasWordSync = false
        var titleTag: String? = null
        var artistTag: String? = null
        var albumTag: String? = null

        val normalizedInput = rawText.removePrefix("\uFEFF")
        for (rawLine in normalizedInput.lines()) {
            val line = rawLine.trim()
            if (line.isEmpty()) continue

            offsetTagRegex.matchEntire(line)?.let {
                globalOffsetMs = it.groupValues[1].toLongOrNull() ?: 0L
                return@let
            }?.also { continue }

            titleTagRegex.matchEntire(line)?.let {
                titleTag = it.groupValues[1].takeIf { v -> v.isNotBlank() }
                return@let
            }?.also { continue }

            artistTagRegex.matchEntire(line)?.let {
                artistTag = it.groupValues[1].takeIf { v -> v.isNotBlank() }
                return@let
            }?.also { continue }

            albumTagRegex.matchEntire(line)?.let {
                albumTag = it.groupValues[1].takeIf { v -> v.isNotBlank() }
                return@let
            }?.also { continue }

            if (otherMetadataTagRegex.matches(line)) continue

            val matches = lineTimestampRegex.findAll(line).toList()
            if (matches.isNotEmpty()) {
                val rawContent = lineTimestampRegex.replace(line, "").trim()
                val words = parseEnhancedWords(rawContent, globalOffsetMs)
                if (words.isNotEmpty()) hasWordSync = true
                val cleanText = if (words.isNotEmpty()) {
                    words.joinToString("") { it.text }.trim().ifBlank { "···" }
                } else {
                    rawContent.ifBlank { "···" }
                }

                for (match in matches) {
                    val baseMs = parseTimestampGroups(
                        g1 = match.groupValues[1],
                        g2 = match.groupValues[2],
                        g3 = match.groupValues[3],
                        g4 = match.groupValues[4],
                    )
                    val totalMs = (baseMs + globalOffsetMs).coerceAtLeast(0L)
                    syncedEntries.add(
                        LyricLine(
                            timestampMs = totalMs,
                            text = cleanText,
                            words = words,
                        )
                    )
                }
            } else {
                plainLines.add(LyricLine(timestampMs = null, text = line))
            }
        }

        if (syncedEntries.isEmpty()) {
            return ParsedLyrics(
                lines = plainLines,
                isSynced = false,
                hasWordSync = false,
                offsetMs = globalOffsetMs,
                titleTag = titleTag,
                artistTag = artistTag,
                albumTag = albumTag,
            )
        }

        val sorted = syncedEntries.sortedBy { it.timestampMs ?: 0L }
        val merged = mutableListOf<LyricLine>()
        for (entry in sorted) {
            val last = merged.lastOrNull()
            if (last != null &&
                last.timestampMs == entry.timestampMs &&
                last.translation == null &&
                entry.text != last.text &&
                entry.text != "···"
            ) {
                merged[merged.lastIndex] = last.copy(translation = entry.text)
            } else {
                merged.add(entry)
            }
        }

        return ParsedLyrics(
            lines = merged,
            isSynced = true,
            hasWordSync = hasWordSync,
            offsetMs = globalOffsetMs,
            titleTag = titleTag,
            artistTag = artistTag,
            albumTag = albumTag,
        )
    }

    private fun parseEnhancedWords(content: String, offsetMs: Long): List<LyricWord> {
        val wordMatches = wordTimestampRegex.findAll(content).toList()
        if (wordMatches.isEmpty()) return emptyList()
        return wordMatches.mapNotNull { wm ->
            val wordText = wm.groupValues[5]
            if (wordText.isEmpty()) return@mapNotNull null
            val ts = parseTimestampGroups(
                g1 = wm.groupValues[1],
                g2 = wm.groupValues[2],
                g3 = wm.groupValues[3],
                g4 = wm.groupValues[4],
            ) + offsetMs
            LyricWord(startMs = ts.coerceAtLeast(0L), text = wordText)
        }
    }

    private fun parseTimestampGroups(
        g1: String,
        g2: String,
        g3: String,
        g4: String,
    ): Long {
        val (hours, mins, secs, fracStr) = when {
            g3.isNotEmpty() && g4.isNotEmpty() -> listOf(g1, g2, g3, g4)
            g3.isNotEmpty() -> listOf("0", g1, g2, g3) // [mm:ss:xx]
            else -> listOf("0", g1, g2, g4) // [mm:ss] or [mm:ss.xx]
        }
        val h = hours.toLongOrNull() ?: 0L
        val m = mins.toLongOrNull() ?: 0L
        val s = secs.toLongOrNull() ?: 0L
        val msPart = when (fracStr.length) {
            1 -> (fracStr.toLongOrNull() ?: 0L) * 100L
            2 -> (fracStr.toLongOrNull() ?: 0L) * 10L
            3 -> fracStr.toLongOrNull() ?: 0L
            else -> 0L
        }
        return h * 3_600_000L + m * 60_000L + s * 1000L + msPart
    }
}

object LrcFormatter {
    fun formatTimestamp(ms: Long): String {
        val safe = ms.coerceAtLeast(0L)
        val mins = safe / 60_000L
        val secs = (safe % 60_000L) / 1000L
        val centis = (safe % 1000L) / 10L
        return String.format(Locale.US, "[%02d:%02d.%02d]", mins, secs, centis)
    }

    fun formatLrc(lines: List<LyricLine>): String {
        if (lines.isEmpty()) return ""
        return buildString {
            lines.forEachIndexed { idx, line ->
                val ts = line.timestampMs
                if (ts != null) {
                    val stamp = formatTimestamp(ts)
                    append(stamp).append(line.text)
                    if (!line.translation.isNullOrBlank()) {
                        append('\n').append(stamp).append(line.translation)
                    }
                } else {
                    append(line.text)
                }
                if (idx < lines.lastIndex) append('\n')
            }
        }
    }
}
