package com.example.data.dj.next

import com.example.data.model.Track
import com.example.data.model.TrackAnalysis

/**
 * Boundary adapter for the app's persisted library metadata.
 *
 * It transfers already stored measurements into the new engine's input model.
 * It does not call the legacy ranking, queue selection, or transition logic.
 */
object DjTrackProfileFactory {
    fun fromLibrary(track: Track, analysis: TrackAnalysis?): DjTrackProfile {
        val reliableTempo = analysis?.takeIf {
            it.bpm > 0f && it.bpmConfidence >= 0.62f
        }
        val reliableKey = analysis?.takeIf {
            it.keyConfidence >= 0.48f &&
                it.camelotCode.isNotBlank() &&
                it.camelotCode != "--"
        }
        val duration = track.durationMs.takeIf { it > 0L }
            ?: analysis?.durationMs?.coerceAtLeast(0L)
            ?: 0L
        val genre = analysis?.subgenre?.takeIf { it.isNotBlank() && !it.equals("Unknown", true) }
            ?: analysis?.primaryGenre?.takeIf { it.isNotBlank() && !it.equals("Unknown", true) }
            ?: track.genre.takeIf { it.isNotBlank() && !it.equals("Unknown", true) }

        return DjTrackProfile(
            trackId = track.id,
            title = track.title,
            artist = track.artist,
            durationMs = duration,
            bpm = reliableTempo?.bpm,
            bpmConfidence = reliableTempo?.bpmConfidence ?: 0f,
            camelotKey = reliableKey?.camelotCode,
            genreFamily = genre,
            energy = analysis?.energy?.coerceIn(0f, 1f) ?: 0.5f,
            introEndMs = analysis?.introEndMs?.takeIf { it > 0L && it < duration },
            firstBeatMs = analysis?.firstBeatMs?.takeIf { it >= 0L && it < duration },
            outroStartMs = analysis?.outroStartMs?.takeIf { it > 0L && it < duration },
        )
    }
}
