package com.example.data.lyrics

import com.example.data.model.Track
import java.text.Normalizer
import kotlin.math.abs

/**
 * Shared track metadata normalization and search candidate matching validator.
 * Used across lyrics lookup, artwork lookup, and smart metadata cleanup.
 */
object TrackMatchValidator {

    private val NOISE_BRACKETS = Regex(
        """\s*[\[(](official\s*(music\s*|lyric\s*)?(video|audio|hd|4k)|audio|lyric(s)?(\s*video)?|hd|4k|hq|visualizer|mp3|320\s*kbps|128\s*kbps|kbps|m4a|flac|www\.[^\])]+)[\])]""",
        RegexOption.IGNORE_CASE,
    )
    private val NOISE_SUFFIX = Regex(
        """\s*[-–—]\s*(official(\s+(music\s+)?(video|audio))?|audio|lyrics?|visualizer|mp3)\s*$""",
        RegexOption.IGNORE_CASE,
    )
    private val LEADING_TRACK_NO = Regex("""^\d{1,3}[\s._-]+(?=\S)""")
    private val URL_TAG = Regex("""\s*\b(www\.[a-z0-9.-]+\.[a-z]{2,}|@[a-z0-9_.-]+)\b""", RegexOption.IGNORE_CASE)

    fun cleanTrackTitle(raw: String): String {
        val withSpaces = if (!raw.contains(' ') && raw.contains('_')) raw.replace('_', ' ') else raw
        return withSpaces
            .replace(LEADING_TRACK_NO, "")
            .replace(NOISE_BRACKETS, "")
            .replace(URL_TAG, "")
            .replace(Regex("""\s+(feat\.|ft\.|featuring)\s+.*$""", RegexOption.IGNORE_CASE), "")
            .replace(NOISE_SUFFIX, "")
            .replace(Regex("""\s+"""), " ")
            .trim(' ', '-', '_')
    }

    fun cleanArtistName(raw: String): String {
        val trimmed = raw.trim()
        if (trimmed.equals("Unknown Artist", ignoreCase = true) ||
            trimmed.equals("<unknown>", ignoreCase = true) ||
            trimmed.equals("Unknown", ignoreCase = true)
        ) {
            return ""
        }
        val withSpaces = if (!trimmed.contains(' ') && trimmed.contains('_')) trimmed.replace('_', ' ') else trimmed
        return withSpaces
            .replace(URL_TAG, "")
            .replace(Regex("""\s*(,|&|x|feat\.|ft\.|featuring)\s+.*$""", RegexOption.IGNORE_CASE), "")
            .replace(Regex("""\s+"""), " ")
            .trim()
    }

    fun inferArtistAndTitle(track: Track): Pair<String, String> {
        val explicitArtist = cleanArtistName(track.artist)
        val rawTitle = track.title.trim()
            .let { if (!it.contains(' ') && it.contains('_')) it.replace('_', ' ') else it }
            .replace(LEADING_TRACK_NO, "")
            .trim()
        val fileBase = track.filePath.substringAfterLast('/').substringBeforeLast('.').trim()
            .let { if (!it.contains(' ') && it.contains('_')) it.replace('_', ' ') else it }
            .replace(LEADING_TRACK_NO, "")
            .trim()

        if (explicitArtist.isBlank()) {
            val sourceWithDash = when {
                Regex("""\s+[-–—]\s+""").containsMatchIn(rawTitle) -> rawTitle
                Regex("""\s+[-–—]\s+""").containsMatchIn(fileBase) -> fileBase
                else -> ""
            }
            if (sourceWithDash.isNotBlank()) {
                val parts = sourceWithDash.split(Regex("""\s+[-–—]\s+"""), limit = 2)
                if (parts.size == 2) {
                    val inferredArtist = cleanArtistName(parts[0])
                    val inferredTitle = cleanTrackTitle(parts[1])
                    if (inferredArtist.isNotBlank() && inferredTitle.isNotBlank()) {
                        return inferredArtist to inferredTitle
                    }
                }
            }
        }

        val cleanedTitle = cleanTrackTitle(rawTitle).ifBlank { cleanTrackTitle(fileBase) }
        return explicitArtist to cleanedTitle
    }

    private val VERSION_MARKERS = listOf(
        "live", "remix", "acoustic", "instrumental", "karaoke", "sped up", "slowed", "radio edit", "acapella"
    )

    fun stripDiacritics(input: String): String {
        val norm = Normalizer.normalize(input, Normalizer.Form.NFD)
        return norm.replace(Regex("""\p{InCombiningDiacriticalMarks}+"""), "")
    }

    fun normalizeTokens(text: String): Set<String> {
        return stripDiacritics(text).lowercase()
            .replace(Regex("""[^a-z0-9\s]"""), " ")
            .split(Regex("""\s+"""))
            .filter { it.length > 1 }
            .toSet()
    }

    fun scoreCandidate(
        track: Track,
        candTitle: String,
        candArtist: String,
        candAlbum: String,
        candDurationSec: Int,
        hasSynced: Boolean,
    ): Int {
        val (inferredArtist, inferredTitle) = inferArtistAndTitle(track)
        val cleanTitle = inferredTitle.ifBlank { cleanTrackTitle(track.title) }
        val cleanCandTitle = cleanTrackTitle(candTitle)
        if (cleanTitle.isBlank() || cleanCandTitle.isBlank()) return 0

        var score = 0
        val titleTokensA = normalizeTokens(cleanTitle)
        val titleTokensB = normalizeTokens(cleanCandTitle)
        if (stripDiacritics(cleanTitle).equals(stripDiacritics(cleanCandTitle), ignoreCase = true)) {
            score += 48
        } else if (titleTokensA.isNotEmpty() && titleTokensB.isNotEmpty()) {
            val overlap = titleTokensA.intersect(titleTokensB).size.toFloat()
            val union = titleTokensA.union(titleTokensB).size.coerceAtLeast(1).toFloat()
            score += ((overlap / union) * 42f).toInt()
        }

        val cleanArtist = inferredArtist
        val cleanCandArtist = cleanArtistName(candArtist)
        if (cleanArtist.isNotBlank() && cleanCandArtist.isNotBlank()) {
            val artistA = normalizeTokens(cleanArtist)
            val artistB = normalizeTokens(cleanCandArtist)
            if (stripDiacritics(cleanArtist).equals(stripDiacritics(cleanCandArtist), ignoreCase = true)) {
                score += 32
            } else if (artistA.isNotEmpty() && artistB.isNotEmpty()) {
                val overlap = artistA.intersect(artistB).size.toFloat()
                val union = artistA.union(artistB).size.coerceAtLeast(1).toFloat()
                score += ((overlap / union) * 28f).toInt()
            }
        } else if (cleanArtist.isBlank()) {
            score += 10
        }

        val trackDurSec = (track.durationMs / 1000L).toInt()
        if (trackDurSec > 0 && candDurationSec > 0) {
            val diff = abs(trackDurSec - candDurationSec)
            score += when {
                diff <= 2 -> 22
                diff <= 4 -> 15
                diff <= 8 -> 5
                diff <= 15 -> -14
                else -> -32
            }
        }

        if (track.album.isNotBlank() && !track.album.equals("Unknown Album", ignoreCase = true) &&
            stripDiacritics(candAlbum).equals(stripDiacritics(track.album), ignoreCase = true)
        ) {
            score += 6
        }

        val lowTrack = stripDiacritics(track.title).lowercase()
        val lowCand = stripDiacritics(candTitle).lowercase()
        for (marker in VERSION_MARKERS) {
            if (lowTrack.contains(marker) != lowCand.contains(marker)) {
                score -= 22
                break
            }
        }

        if (hasSynced && score >= 40) score += 8
        return score.coerceIn(0, 100)
    }
}
