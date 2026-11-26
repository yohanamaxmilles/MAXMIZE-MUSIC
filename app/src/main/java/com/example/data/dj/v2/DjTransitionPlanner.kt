package com.example.data.dj.v2

import kotlin.math.abs

/**
 * Builds a phrase-aware transition plan. The audio service will execute the plan;
 * this planner never claims that analysis or DSP has happened when evidence is absent.
 */
class DjTransitionPlanner {

    fun plan(
        outgoing: DjTrackProfile,
        incoming: DjTrackProfile,
        outgoingPositionMs: Long,
        config: DjTransitionConfig = DjTransitionConfig(),
    ): DjTransitionPlanningResult {
        if (outgoing.contentKind != AudioContentKind.MUSIC ||
            incoming.contentKind != AudioContentKind.MUSIC
        ) {
            return DjTransitionPlanningResult.Unavailable("Both decks must contain verified music")
        }
        if (!outgoing.hasReliableTempo || !incoming.hasReliableTempo) {
            return DjTransitionPlanningResult.Unavailable("Reliable BPM and beat-grid analysis is required")
        }
        if (outgoing.beatIntervalMs == null || incoming.beatIntervalMs == null ||
            outgoing.beatIntervalMs <= 0L || incoming.beatIntervalMs <= 0L
        ) {
            return DjTransitionPlanningResult.Unavailable("Beat interval is missing")
        }

        val rate = DjDecisionEngine.tempoRatio(outgoing.bpm, incoming.bpm)
            ?: return DjTransitionPlanningResult.Unavailable("Tempo alignment is unavailable")
        if (abs(rate - 1f) > config.maxTempoAdjustment) {
            return DjTransitionPlanningResult.Unavailable("Safe tempo range exceeded")
        }

        val outgoingBoundary = outgoing.phraseBoundariesMs
            .filter { it > outgoingPositionMs && it < outgoing.durationMs }
            .minOrNull()
            ?: return DjTransitionPlanningResult.Unavailable("No future analyzed phrase boundary on outgoing track")

        val introSection = incoming.sections
            .filter { it.type == MusicalSectionType.INTRO || it.type == MusicalSectionType.INSTRUMENTAL }
            .filter { it.confidence >= 0.60f }
            .minByOrNull { it.startMs }
        val analyzedIntroCue = introSection?.let { section ->
            incoming.firstBeatMs?.takeIf { it >= section.startMs && it < section.endMs }
                ?: section.startMs
        }
        val incomingCue = analyzedIntroCue ?: incoming.firstBeatMs
            ?: return DjTransitionPlanningResult.Unavailable("No reliable incoming downbeat cue")

        val durationMs = (outgoing.beatIntervalMs * config.bars * config.beatsPerBar)
            .coerceAtLeast(2_000L)
            .coerceAtMost(32_000L)

        if (outgoingBoundary + durationMs > outgoing.durationMs) {
            return DjTransitionPlanningResult.Unavailable("Outgoing track has insufficient audio after the phrase boundary")
        }
        if (incomingCue + durationMs > incoming.durationMs) {
            return DjTransitionPlanningResult.Unavailable("Incoming track has insufficient intro audio")
        }

        val vocalOverlap = hasHighConfidenceVocalOverlap(
            outgoing, incoming, outgoingBoundary, incomingCue, durationMs,
        )
        val curve = if (vocalOverlap && config.curve == DjTransitionCurve.EQUAL_POWER) {
            DjTransitionCurve.VOCAL_AWARE_EQ
        } else config.curve

        return DjTransitionPlanningResult.Ready(
            DjTransitionPlan(
                outgoingStartMs = outgoingBoundary,
                incomingStartMs = incomingCue,
                durationMs = durationMs,
                incomingPlaybackRate = rate,
                preservePitch = config.keyLockEnabled,
                curve = curve,
                explanation = if (analyzedIntroCue != null) {
                    "Phrase-aligned transition using analyzed beat grids and a detected intro cue"
                } else {
                    "Phrase and intro aligned. Incoming vocal structure is unknown, so vocal overlap is not guaranteed to be avoided."
                },
            )
        )
    }

    private fun hasHighConfidenceVocalOverlap(
        outgoing: DjTrackProfile,
        incoming: DjTrackProfile,
        outgoingStartMs: Long,
        incomingStartMs: Long,
        durationMs: Long,
    ): Boolean {
        val outgoingVocal = outgoing.sections.any {
            it.type == MusicalSectionType.VOCAL && it.confidence >= 0.70f &&
                overlaps(it.startMs, it.endMs, outgoingStartMs, outgoingStartMs + durationMs)
        }
        val incomingVocal = incoming.sections.any {
            it.type == MusicalSectionType.VOCAL && it.confidence >= 0.70f &&
                overlaps(it.startMs, it.endMs, incomingStartMs, incomingStartMs + durationMs)
        }
        return outgoingVocal && incomingVocal
    }

    private fun overlaps(aStart: Long, aEnd: Long, bStart: Long, bEnd: Long): Boolean =
        aStart < bEnd && bStart < aEnd
}
