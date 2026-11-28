package com.example.data.dj.next

import kotlin.math.abs

/**
 * Conservative beat-sync gate. A speed correction is only applied when both
 * tracks have reliable tempo and beat cues. Large corrections are rejected
 * rather than silently clamped into a misleading sync.
 */
data class DjBeatSyncPlan(
    val enabled: Boolean,
    val speedRatio: Float,
    val normalizedIncomingBpm: Float?,
    val adjustmentPercent: Float,
    val reason: String,
)

object DjBeatSyncPlanner {
    fun plan(
        outgoing: DjTrackProfile,
        incoming: DjTrackProfile,
        maxAdjustment: Float = 0.08f,
    ): DjBeatSyncPlan {
        val outgoingBpm = outgoing.bpm
            ?.takeIf { it in 45f..220f && outgoing.bpmConfidence >= 0.62f }
        val incomingBpm = incoming.bpm
            ?.takeIf { it in 45f..220f && incoming.bpmConfidence >= 0.62f }
        if (outgoingBpm == null || incomingBpm == null) {
            return disabled("Reliable BPM is missing on one or both decks.")
        }
        if (outgoing.firstBeatMs == null || incoming.firstBeatMs == null) {
            return disabled("A reliable first-beat cue is missing. Keep both decks at native speed.")
        }

        val safeLimit = maxAdjustment.coerceIn(0.01f, 0.12f)
        val candidates = listOf(incomingBpm, incomingBpm * 0.5f, incomingBpm * 2f)
            .filter { it in 45f..220f }
        val matched = candidates.minByOrNull { abs(outgoingBpm / it - 1f) }
            ?: return disabled("No valid half-time or double-time tempo match was found.")
        val ratio = outgoingBpm / matched
        val adjustment = (ratio - 1f) * 100f
        if (abs(ratio - 1f) > safeLimit) {
            return DjBeatSyncPlan(
                enabled = false,
                speedRatio = 1f,
                normalizedIncomingBpm = matched,
                adjustmentPercent = adjustment,
                reason = "Required tempo correction exceeds ${(safeLimit * 100f).toInt()}%. Use a gradual manual transition.",
            )
        }
        return DjBeatSyncPlan(
            enabled = true,
            speedRatio = ratio,
            normalizedIncomingBpm = matched,
            adjustmentPercent = adjustment,
            reason = "Reliable BPM and beat cues are available on both decks.",
        )
    }

    private fun disabled(reason: String) = DjBeatSyncPlan(
        enabled = false,
        speedRatio = 1f,
        normalizedIncomingBpm = null,
        adjustmentPercent = 0f,
        reason = reason,
    )
}
