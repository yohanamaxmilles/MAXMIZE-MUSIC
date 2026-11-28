package com.example.data.dj.next

enum class DjSessionPhase(
    val label: String,
    val targetEnergy: Float,
) {
    OPENING("Opening", 0.38f),
    WARM_UP("Warm-up", 0.52f),
    BUILD("Build", 0.68f),
    PEAK("Peak", 0.90f),
    BREATHER("Breather", 0.62f),
    SECOND_PEAK("Second peak", 0.86f),
    CLOSING("Closing", 0.42f),
}

data class DjSessionSelection(
    val position: Int,
    val phase: DjSessionPhase,
    val profile: DjTrackProfile,
    val compatibility: DjCandidateScore,
)

/**
 * Plans a coherent session arc. Each selection is re-ranked against the
 * previously selected track, so the plan evolves instead of sorting once.
 */
class DjSessionPlanner(
    private val intelligence: DjIntelligenceEngine = DjIntelligenceEngine(),
) {
    fun plan(
        startingTrack: DjTrackProfile,
        library: List<DjTrackProfile>,
        requestedTrackCount: Int,
        recentlyPlayedIds: Set<String> = emptySet(),
    ): List<DjSessionSelection> {
        val count = requestedTrackCount.coerceIn(1, 100)
        val phases = sessionPhases(count)
        val remaining = library
            .filter { it.trackId != startingTrack.trackId && it.trackId !in recentlyPlayedIds }
            .associateBy { it.trackId }
            .toMutableMap()
        val used = recentlyPlayedIds.toMutableSet().apply { add(startingTrack.trackId) }
        var pivot = startingTrack
        val result = mutableListOf<DjSessionSelection>()

        phases.forEachIndexed { index, phase ->
            val ranked = intelligence.rankNextTracks(
                current = pivot,
                candidates = remaining.values.toList(),
                recentlyPlayedIds = used,
                targetEnergy = phase.targetEnergy,
            )
            val best = ranked.firstOrNull() ?: return@forEachIndexed
            result += DjSessionSelection(
                position = index + 1,
                phase = phase,
                profile = best.track,
                compatibility = best,
            )
            used += best.track.trackId
            remaining.remove(best.track.trackId)
            pivot = best.track
        }
        return result
    }

    private fun sessionPhases(count: Int): List<DjSessionPhase> {
        val arc = listOf(
            DjSessionPhase.OPENING,
            DjSessionPhase.WARM_UP,
            DjSessionPhase.BUILD,
            DjSessionPhase.PEAK,
            DjSessionPhase.BREATHER,
            DjSessionPhase.SECOND_PEAK,
            DjSessionPhase.CLOSING,
        )
        if (count <= arc.size) return arc.take(count)
        return List(count) { index ->
            when (index) {
                0 -> DjSessionPhase.OPENING
                count - 1 -> DjSessionPhase.CLOSING
                else -> arc[1 + (((index - 1) * (arc.size - 2)) / (count - 2).coerceAtLeast(1))]
            }
        }
    }
}
