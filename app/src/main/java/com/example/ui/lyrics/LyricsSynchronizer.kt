package com.example.ui.lyrics

import com.example.data.model.LyricLine
import com.example.data.model.LyricWord

/**
 * Pure, O(log N) binary-search synchronization engine for timestamped lyrics.
 * Handles large lyric files (thousands of lines), forward/backward seeks,
 * pause/resume, sync offsets, and enhanced word-level timestamps without frame lag.
 */
object LyricsSynchronizer {

    /**
     * Returns the index of the currently active line in [lines] at [positionMs] + [syncOffsetMs],
     * or -1 if playback is before the first timestamped line or no synced lines exist.
     *
     * Assumes synced [lines] are ordered chronologically by [LyricLine.timestampMs] (guaranteed
     * by [com.example.data.lyrics.LrcParser]). Falls back safely to linear scan only if
     * non-monotonic or interspersed null timestamps are passed directly by callers.
     */
    fun findActiveLineIndex(
        lines: List<LyricLine>,
        positionMs: Long,
        syncOffsetMs: Long = 0L,
    ): Int {
        if (lines.isEmpty()) return -1
        val effectivePosMs = positionMs + syncOffsetMs

        // Fast path: verify monotonic non-null timestamps on boundaries, binary search in O(log N)
        val firstTs = lines.first().timestampMs
        val lastTs = lines.last().timestampMs
        if (firstTs != null && lastTs != null && firstTs <= lastTs) {
            if (effectivePosMs < firstTs) return -1
            if (effectivePosMs >= lastTs) return lines.lastIndex

            var low = 0
            var high = lines.lastIndex
            var answer = -1

            while (low <= high) {
                val mid = (low + high) ushr 1
                val midTs = lines[mid].timestampMs
                if (midTs == null) {
                    // Unexpected null inside synced block -> safe fallback
                    return findActiveLineIndexLinear(lines, effectivePosMs)
                }
                if (midTs <= effectivePosMs) {
                    answer = mid
                    low = mid + 1
                } else {
                    high = mid - 1
                }
            }
            return answer
        }

        return findActiveLineIndexLinear(lines, effectivePosMs)
    }

    private fun findActiveLineIndexLinear(lines: List<LyricLine>, effectivePosMs: Long): Int {
        var active = -1
        for (i in lines.indices) {
            val ts = lines[i].timestampMs ?: continue
            if (ts <= effectivePosMs) {
                active = i
            }
        }
        return active
    }

    /**
     * Returns the index of the active word inside [words] using O(log N) binary search,
     * or -1 if before the first word timestamp or [words] is empty.
     */
    fun findActiveWordIndex(
        words: List<LyricWord>,
        positionMs: Long,
        syncOffsetMs: Long = 0L,
    ): Int {
        if (words.isEmpty()) return -1
        val effectivePosMs = positionMs + syncOffsetMs
        if (effectivePosMs < words.first().startMs) return -1
        if (effectivePosMs >= words.last().startMs) return words.lastIndex

        var low = 0
        var high = words.lastIndex
        var answer = -1
        while (low <= high) {
            val mid = (low + high) ushr 1
            if (words[mid].startMs <= effectivePosMs) {
                answer = mid
                low = mid + 1
            } else {
                high = mid - 1
            }
        }
        return answer
    }

    /**
     * Computes the exact player seek position so that tapping a line at [lineTimestampMs]
     * lands right on that line after accounting for [syncOffsetMs].
     */
    fun computeSeekPositionMs(
        lineTimestampMs: Long,
        syncOffsetMs: Long = 0L,
    ): Long = (lineTimestampMs - syncOffsetMs).coerceAtLeast(0L)
}
