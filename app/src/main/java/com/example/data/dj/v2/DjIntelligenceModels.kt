package com.example.data.dj.v2

/** Evidence-aware contracts. Missing measurements stay null, never guessed. */
enum class DjSessionPhase(val targetEnergyDelta: Float) {
    OPENING(0.04f), WARM_UP(0.07f), BUILD_UP(0.12f), PEAK_TIME(0.0f),
    BREATHER(-0.16f), COOL_DOWN(-0.10f), CLOSING(-0.12f), FLOW(0.0f),
}
enum class AudioContentKind { MUSIC, PODCAST, SPOKEN_WORD, AUDIOBOOK, SOUND_EFFECT, UNKNOWN }
enum class MusicalSectionType { INTRO, OUTRO, VERSE, CHORUS, VOCAL, INSTRUMENTAL, BUILD_UP, BREAKDOWN, DROP, SILENCE }

data class MusicalSection(
    val type: MusicalSectionType,
    val startMs: Long,
    val endMs: Long,
    val confidence: Float,
) {
    init {
        require(startMs >= 0L && endMs > startMs)
        require(confidence in 0f..1f)
    }
}

data class DjTrackProfile(
    val trackId: String,
    val artistName: String? = null,
    val durationMs: Long,
    val contentKind: AudioContentKind,
    val contentConfidence: Float,
    val primaryGenre: String?,
    val subgenre: String?,
    val genreFamily: String?,
    val genreConfidence: Float,
    val bpm: Float?,
    val bpmConfidence: Float,
    val musicalKey: String?,
    val camelotCode: String?,
    val keyConfidence: Float,
    val energy: Float?,
    val energyConfidence: Float,
    val moodTags: Set<String> = emptySet(),
    val phraseBoundariesMs: List<Long> = emptyList(),
    val sections: List<MusicalSection> = emptyList(),
    val firstBeatMs: Long? = null,
    val beatIntervalMs: Long? = null,
    val beatsPerBar: Int = 4,
    val analysisVersion: Int = 2,
    val analyzedAtEpochMs: Long = 0L,
) {
    init {
        require(trackId.isNotBlank() && durationMs >= 0L)
        require(contentConfidence in 0f..1f && genreConfidence in 0f..1f)
        require(bpmConfidence in 0f..1f && keyConfidence in 0f..1f)
        require(energyConfidence in 0f..1f)
        require(energy == null || energy in 0f..1f)
        require(beatsPerBar in 1..12)
    }

    val hasReliableTempo: Boolean
        get() = bpm != null && bpm in 60f..200f && bpmConfidence >= 0.60f

    val hasReliableKey: Boolean
        get() = !camelotCode.isNullOrBlank() && keyConfidence >= 0.60f

    val hasReliableGenre: Boolean
        get() = !subgenre.isNullOrBlank() && subgenre != "Unknown" && genreConfidence >= 0.60f
}

data class DjDecisionConfig(
    val strictGenreMatching: Boolean = true,
    val allowOpenFormatBridging: Boolean = false,
    val requireReliableTempo: Boolean = true,
    val requireReliableGenre: Boolean = true,
    val requireReliableKey: Boolean = false,
    val maxTempoAdjustment: Float = 0.08f,
    val minTrackDurationMs: Long = 45_000L,
    val maxUnknownContentDurationMs: Long = 12L * 60L * 1000L,
    val targetEnergyDelta: Float? = null,
    val sessionPhase: DjSessionPhase = DjSessionPhase.FLOW,
    val bpmWeight: Float = 0.28f,
    val keyWeight: Float = 0.24f,
    val genreWeight: Float = 0.30f,
    val energyWeight: Float = 0.18f,
) {
    init {
        require(maxTempoAdjustment in 0f..0.20f)
        require(minTrackDurationMs > 0L && maxUnknownContentDurationMs >= minTrackDurationMs)
        require(targetEnergyDelta == null || targetEnergyDelta in -1f..1f)
        require(listOf(bpmWeight, keyWeight, genreWeight, energyWeight).all { it >= 0f })
        require(bpmWeight + keyWeight + genreWeight + energyWeight > 0f)
    }
}

data class DjCandidateScore(
    val trackId: String,
    val totalScore: Int,
    val bpmScore: Int?,
    val keyScore: Int?,
    val genreScore: Int?,
    val energyScore: Int?,
    val tempoRatio: Float?,
    val reasons: List<String>,
)

data class DjRejection(val trackId: String, val reason: String)
data class DjRankingResult(
    val rankedCandidates: List<DjCandidateScore>,
    val rejectedCandidates: List<DjRejection>,
)

enum class DjTransitionCurve { EQUAL_POWER, BASS_SWAP, VOCAL_AWARE_EQ, PHRASE_CUT }

data class DjTransitionConfig(
    val maxTempoAdjustment: Float = 0.08f,
    val bars: Int = 8,
    val beatsPerBar: Int = 4,
    val curve: DjTransitionCurve = DjTransitionCurve.EQUAL_POWER,
    val keyLockEnabled: Boolean = true,
) {
    init {
        require(maxTempoAdjustment in 0f..0.20f)
        require(bars in 1..32 && beatsPerBar in 1..12)
    }
}

data class DjTransitionPlan(
    val outgoingStartMs: Long,
    val incomingStartMs: Long,
    val durationMs: Long,
    val incomingPlaybackRate: Float,
    val preservePitch: Boolean,
    val curve: DjTransitionCurve,
    val explanation: String,
)

sealed interface DjTransitionPlanningResult {
    data class Ready(val plan: DjTransitionPlan) : DjTransitionPlanningResult
    data class Unavailable(val reason: String) : DjTransitionPlanningResult
}
