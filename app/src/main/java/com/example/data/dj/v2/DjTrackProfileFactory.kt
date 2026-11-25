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
            it.energy in 0f..1f && it.analysisVersion >= 2
        }
        val moodTags = buildSet {
            if (energyReliable != null) {
                if (energyReliable.energy >= 0.72f) add("high-energy")
                if (energyReliable.energy <= 0.32f) add("low-energy")
                if (energyReliable.danceability >= 0.70f) add("dance-driven")
                if (energyReliable.dynamicRangeDb >= 14f) add("dynamic")
            }
        }

        val phraseBoundaries = analysis?.takeIf {
            it.bpmConfidence >= 0.75f &&
                it.tempoStability >= 0.65f &&
                it.downbeatConfidence >= 0.65f &&
                it.phraseLengthMs > 0L
        }?.let { a ->
            generateSequence(a.firstBeatMs + a.phraseLengthMs) { previous ->
                previous + a.phraseLengthMs
            }.takeWhile { it < track.durationMs }.toList()
        }.orEmpty()
        val sections = buildList {
            analysis?.takeIf { it.introEndMs > 0L && it.introEndMs < track.durationMs }?.let {
                add(MusicalSection(MusicalSectionType.INTRO, 0L, it.introEndMs, 0.65f))
            }
            analysis?.takeIf { it.outroStartMs > 0L && it.outroStartMs < track.durationMs }?.let {
                add(MusicalSection(MusicalSectionType.OUTRO, it.outroStartMs, track.durationMs, 0.65f))
            }
        }

        return DjTrackProfile(
            trackId = track.id,
            artistName = track.artist,
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
            energyConfidence = if (energyReliable != null) 0.82f else 0f,
            moodTags = moodTags,
            phraseBoundariesMs = phraseBoundaries,
            sections = sections,
            firstBeatMs = analysis?.takeIf { it.downbeatConfidence >= 0.60f }?.firstBeatMs,
            beatIntervalMs = bpmReliable?.beatIntervalMs,
            beatsPerBar = 4,
            analysisVersion = analysis?.analysisVersion ?: 0,
        )
    }

    private fun containsMarker(text: String, marker: String): Boolean =
        Regex("(^|[^a-z0-9])" + Regex.escape(marker) + "([^a-z0-9]|$)")
            .containsMatchIn(text)

    private fun classifyContent(
        track: Track,
        analysis: TrackAnalysis?,
    ): Pair<AudioContentKind, Float> {
        val mime = track.mimeType.lowercase()
        if (!mime.startsWith("audio/")) return AudioContentKind.UNKNOWN to 0.1f

        val descriptors = listOf(track.title, track.album, track.genre, track.folderPath)
            .joinToString(" ").lowercase()
        if (listOf("sound effect", "sound effects", "sfx", "notification tone", "ringtone", "alarm tone")
                .any { marker -> containsMarker(descriptors, marker) }
        ) return AudioContentKind.SOUND_EFFECT to 0.96f
        if (containsMarker(descriptors, "audiobook") || containsMarker(descriptors, "audio book")) {
            return AudioContentKind.AUDIOBOOK to 0.98f
        }
        if (containsMarker(descriptors, "podcast")) return AudioContentKind.PODCAST to 0.98f
        if (spokenMarkers.any { marker -> containsMarker(descriptors, marker) }) {
            return AudioContentKind.SPOKEN_WORD to 0.90f
        }

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
