package com.example.data.dj.v2

import com.example.data.dj.GenreEngine
import com.example.data.model.Track
import com.example.data.model.TrackAnalysis

/**
 * Adapts the current library records into the v2 decision model without fabricating
 * musical structure. Existing intro/outro estimates are deliberately not promoted
 * to verified cue points.
 */
object DjTrackProfileFactory {

    private val spokenMarkers = listOf(
        "podcast", "audiobook", "audio book", "interview", "speech", "spoken word",
        "talk show", "radio show", "lecture", "sermon", "news bulletin",
    )

    fun from(track: Track, analysis: TrackAnalysis?): DjTrackProfile {
        val content = classifyContent(track, analysis)
        val genre = GenreEngine.classifyTrack(track, analysis)
        val bpmReliable = analysis?.takeIf {
            it.bpmConfidence >= 0.60f && it.bpm in 60f..200f
        }
        val keyReliable = analysis?.takeIf {
            it.keyConfidence >= 0.60f && it.camelotCode.matches(Regex("^(?:[1-9]|1[0-2])[AB]$"))
        }
        val genreReliable = genre.takeIf {
            it.confidence >= 0.60f && it.subgenre.isNotBlank() && it.subgenre != "Unknown"
        }
        val energyReliable = analysis?.takeIf {
            it.energy in 0f..1f && it.bpmConfidence >= 0.40f
        }

        return DjTrackProfile(
            trackId = track.id,
            durationMs = track.durationMs,
            contentKind = content.first,
            contentConfidence = content.second,
            primaryGenre = genreReliable?.primaryGenre,
            subgenre = genreReliable?.subgenre,
            genreFamily = genreReliable?.family?.displayName,
            genreConfidence = genreReliable?.confidence ?: 0f,
            bpm = bpmReliable?.bpm,
            bpmConfidence = analysis?.bpmConfidence ?: 0f,
            musicalKey = keyReliable?.musicalKey,
            camelotCode = keyReliable?.camelotCode,
            keyConfidence = analysis?.keyConfidence ?: 0f,
            energy = energyReliable?.energy,
            energyConfidence = if (energyReliable != null) 0.65f else 0f,
            firstBeatMs = analysis?.takeIf { it.downbeatConfidence >= 0.60f }?.firstBeatMs,
            beatIntervalMs = bpmReliable?.beatIntervalMs,
            beatsPerBar = 4,
            analysisVersion = analysis?.analysisVersion ?: 0,
        )
    }

    private fun classifyContent(
        track: Track,
        analysis: TrackAnalysis?,
    ): Pair<AudioContentKind, Float> {
        val mime = track.mimeType.lowercase()
        if (!mime.startsWith("audio/")) return AudioContentKind.UNKNOWN to 0.1f

        val descriptors = listOf(track.title, track.album, track.genre)
            .joinToString(" ").lowercase()
        if (descriptors.contains("audiobook") || descriptors.contains("audio book")) {
            return AudioContentKind.AUDIOBOOK to 0.98f
        }
        if (descriptors.contains("podcast")) return AudioContentKind.PODCAST to 0.98f
        if (descriptors.contains("spoken word") || descriptors.contains("speech") ||
            descriptors.contains("interview") || descriptors.contains("talk show") ||
            descriptors.contains("lecture") || descriptors.contains("radio show")
        ) return AudioContentKind.SPOKEN_WORD to 0.90f

        val knownGenre = GenreEngine.canonicalize(track.genre) != null
        val metadataComplete = track.title.isNotBlank() && track.artist.isNotBlank()
        val analysisSupportsMusic = analysis != null &&
            analysis.bpmConfidence >= 0.60f && analysis.bpm in 60f..200f &&
            analysis.onsetDensity >= 0.8f

        if (knownGenre && metadataComplete && track.durationMs in 45_000L..900_000L) {
            return AudioContentKind.MUSIC to 0.90f
        }
        if (analysisSupportsMusic && metadataComplete && track.durationMs in 45_000L..900_000L) {
            return AudioContentKind.MUSIC to 0.72f
        }
        return AudioContentKind.UNKNOWN to 0.35f
    }
}
