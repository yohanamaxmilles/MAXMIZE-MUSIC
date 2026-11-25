package com.example.data.dj.next

import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * Independent DJ decision and transition layer. It consumes measured profiles
 * and does not depend on the legacy DJ engine's queue or scoring algorithms.
 */
class DjIntelligenceEngine {
    fun rankNextTracks(
        current: DjTrackProfile,
        candidates: List<DjTrackProfile>,
        recentlyPlayedIds: Set<String> = emptySet(),
        targetEnergy: Float? = null,
        allowGenreBridging: Boolean = true,
    ): List<DjCandidateScore> {
        return candidates.asSequence()
            .filter { it.trackId != current.trackId && it.durationMs > 0L }
            .filterNot { it.trackId in recentlyPlayedIds }
            .mapNotNull { candidate ->
                val tempo = tempoScore(current.bpm, candidate.bpm, current.bpmConfidence, candidate.bpmConfidence)
                val harmonic = harmonicScore(current.camelotKey, candidate.camelotKey)
                val genre = genreScore(current.genreFamily, candidate.genreFamily, allowGenreBridging)
                val desiredEnergy = targetEnergy ?: current.energy
                val energy = (1f - abs(candidate.energy - desiredEnergy).coerceIn(0f, 1f))
                val total = tempo * 0.38f + harmonic * 0.25f + genre * 0.17f + energy * 0.20f
                if (total < 0.30f) null else DjCandidateScore(
                    track = candidate,
                    totalScore = total,
                    tempoScore = tempo,
                    harmonicScore = harmonic,
                    genreScore = genre,
                    energyScore = energy,
                    reason = buildReason(tempo, harmonic, genre, energy),
                )
            }
            .sortedByDescending { it.totalScore }
            .toList()
    }

    fun planTransition(
        outgoing: DjTrackProfile,
        incoming: DjTrackProfile,
        bars: Int = 16,
    ): DjTransitionPlan {
        require(outgoing.durationMs > 0L && incoming.durationMs > 0L)
        val safeBars = bars.coerceIn(4, 64)
        val bpm = outgoing.bpm?.takeIf { outgoing.bpmConfidence >= 0.62f }
        val duration = if (bpm != null) {
            (safeBars * 4 * 60_000f / bpm).toLong().coerceIn(4_000L, 64_000L)
        } else 16_000L
        val outgoingStart = (outgoing.outroStartMs ?: (outgoing.durationMs - duration))
            .coerceIn(0L, outgoing.durationMs)
        val incomingStart = (incoming.firstBeatMs ?: 0L).coerceIn(0L, incoming.durationMs)
        val compatible = if (outgoing.camelotKey != null && incoming.camelotKey != null) {
            camelotDistance(outgoing.camelotKey, incoming.camelotKey) <= 1
        } else null
        return DjTransitionPlan(
            outgoingTrackId = outgoing.trackId,
            incomingTrackId = incoming.trackId,
            startOutgoingAtMs = outgoingStart,
            startIncomingAtMs = incomingStart,
            durationMs = min(duration, outgoing.durationMs - outgoingStart).coerceAtLeast(1_000L),
            bars = safeBars,
            bpm = bpm,
            harmonicCompatible = compatible,
            explanation = when {
                bpm == null -> "Tempo confidence is low. Use a manual or gradual fade, not automatic beat sync."
                compatible == false -> "Keys are not adjacent on the Camelot wheel. Prefer a low-overlap transition."
                else -> "Phrase-length transition planned. Confirm beatgrid alignment before full automation."
            },
        )
    }

    private fun tempoScore(a: Float?, b: Float?, confidenceA: Float, confidenceB: Float): Float {
        if (a == null || b == null || confidenceA < 0.62f || confidenceB < 0.62f) return 0.45f
        val ratio = b / a
        val adjusted = when {
            ratio in 0.94f..1.06f -> ratio
            ratio * 2f in 0.94f..1.06f -> ratio * 2f
            ratio / 2f in 0.94f..1.06f -> ratio / 2f
            else -> ratio
        }
        val deviation = abs(adjusted - 1f)
        return (1f - deviation / 0.16f).coerceIn(0f, 1f)
    }

    private fun harmonicScore(a: String?, b: String?): Float {
        if (a.isNullOrBlank() || b.isNullOrBlank()) return 0.5f
        val distance = camelotDistance(a, b)
        return when (distance) {
            0 -> 1f
            1 -> 0.9f
            else -> (0.8f - (distance - 1) * 0.16f).coerceIn(0f, 0.1f)
        }
    }

    private fun camelotDistance(a: String, b: String): Int {
        val left = parseCamelot(a) ?: return 99
        val right = parseCamelot(b) ?: return 99
        val numberDistance = min(abs(left.first - right.first), 12 - abs(left.first - right.first))
        val modeDistance = if (left.second == right.second) 0 else 1
        return numberDistance + modeDistance
    }

    private fun parseCamelot(value: String): Pair<Int, Char>? {
        val normalized = value.trim().uppercase()
        if (normalized.length !in 2..3) return null
        val mode = normalized.last()
        val number = normalized.dropLast(1).toIntOrNull() ?: return null
        return if (number in 1..12 && (mode == 'A' || mode == 'B')) number to mode else null
    }

    private fun genreScore(a: String?, b: String?, allowBridging: Boolean): Float {
        if (a.isNullOrBlank() || b.isNullOrBlank()) return 0.5f
        if (a.equals(b, ignoreCase = true)) return 1f
        return if (allowBridging) 0.62f else 0.25f
    }

    private fun buildReason(tempo: Float, harmonic: Float, genre: Float, energy: Float): String {
        val reasons = buildList {
            if (tempo >= 0.85f) add("tempo match")
            if (harmonic >= 0.85f) add("harmonic match")
            if (genre >= 0.8f) add("genre match")
            if (energy >= 0.85f) add("energy continuity")
        }
        return reasons.takeIf { it.isNotEmpty() }?.joinToString(", ") ?: "best available measured compatibility"
    }
}

/** Equal-power crossfade with a controlled low-frequency handover. */
object DjMixAutomation {
    fun frame(progress: Float): DjMixFrame {
        val p = progress.coerceIn(0f, 1f)
        val outgoing = cosEqualPower(p)
        val incoming = sinEqualPower(p)
        val bassHandover = smoothStep((p - 0.35f) / 0.30f)
        return DjMixFrame(
            outgoingGain = outgoing,
            incomingGain = incoming,
            outgoingLowGain = 1f - bassHandover,
            incomingLowGain = bassHandover,
            outgoingHighGain = outgoing,
            incomingHighGain = incoming,
        )
    }

    private fun smoothStep(value: Float): Float {
        val x = value.coerceIn(0f, 1f)
        return x * x * (3f - 2f * x)
    }

    private fun cosEqualPower(progress: Float): Float =
        kotlin.math.cos(progress * (Math.PI.toFloat() / 2f)).coerceIn(0f, 1f)

    private fun sinEqualPower(progress: Float): Float =
        sin(progress * (Math.PI.toFloat() / 2f)).coerceIn(0f, 1f)
}
