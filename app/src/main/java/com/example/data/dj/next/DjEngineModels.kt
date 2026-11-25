package com.example.data.dj.next

/**
 * Results are explicit about what was measured. Null means the signal has not
 * been analysed by a capable detector; it is never replaced with a guess.
 */
enum class DjConfidence { LOW, MEDIUM, HIGH }

enum class DjSectionType { INTRO, LOW_ENERGY, BUILD, HIGH_ENERGY, BREAKDOWN, OUTRO, UNKNOWN }

data class DjSection(
    val startMs: Long,
    val endMs: Long,
    val type: DjSectionType,
    val energy: Float,
)

data class DjAudioAnalysis(
    val trackId: String,
    val durationMs: Long,
    val bpm: Float?,
    val beatIntervalMs: Long?,
    val firstBeatOffsetMs: Long?,
    val beatTimesMs: List<Long>,
    val phraseMarkersMs: List<Long>,
    val energy: Float,
    val rmsDb: Float,
    val bpmConfidence: Float,
    val bpmConfidenceBand: DjConfidence,
    val sections: List<DjSection>,
    val musicalKey: String? = null,
    val camelotKey: String? = null,
    val vocalProbability: Float? = null,
    val genre: String? = null,
    val notes: List<String> = emptyList(),
) {
    val hasReliableTempo: Boolean
        get() = bpm != null && bpmConfidence >= 0.62f
}

data class DjTrackProfile(
    val trackId: String,
    val title: String,
    val artist: String,
    val durationMs: Long,
    val bpm: Float? = null,
    val bpmConfidence: Float = 0f,
    val camelotKey: String? = null,
    val genreFamily: String? = null,
    val energy: Float = 0.5f,
    val introEndMs: Long? = null,
    val outroStartMs: Long? = null,
    val isExplicit: Boolean = false,
)

data class DjCandidateScore(
    val track: DjTrackProfile,
    val totalScore: Float,
    val tempoScore: Float,
    val harmonicScore: Float,
    val genreScore: Float,
    val energyScore: Float,
    val reason: String,
)

data class DjTransitionPlan(
    val outgoingTrackId: String,
    val incomingTrackId: String,
    val startOutgoingAtMs: Long,
    val startIncomingAtMs: Long,
    val durationMs: Long,
    val bars: Int,
    val bpm: Float?,
    val harmonicCompatible: Boolean?,
    val explanation: String,
)

data class DjMixFrame(
    val outgoingGain: Float,
    val incomingGain: Float,
    val outgoingLowGain: Float,
    val incomingLowGain: Float,
    val outgoingHighGain: Float,
    val incomingHighGain: Float,
)
