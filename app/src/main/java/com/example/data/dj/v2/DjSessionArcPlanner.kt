package com.example.data.dj.v2

/**
 * Provides a default musical journey, not a rigid track-count formula.
 * The decision engine remains free to hold a phase, delay a peak, or recover energy
 * based on the actual tracks and listener feedback.
 */
object DjSessionArcPlanner {

    fun phaseFor(progress: Float): DjSessionPhase {
        val p = progress.coerceIn(0f, 1f)
        return when {
            p < 0.08f -> DjSessionPhase.OPENING
            p < 0.24f -> DjSessionPhase.WARM_UP
            p < 0.48f -> DjSessionPhase.BUILD_UP
            p < 0.72f -> DjSessionPhase.PEAK_TIME
            p < 0.82f -> DjSessionPhase.BREATHER
            p < 0.94f -> DjSessionPhase.COOL_DOWN
            else -> DjSessionPhase.CLOSING
        }
    }

    fun shouldHoldPhase(
        current: DjSessionPhase,
        recentAverageEnergy: Float?,
        targetEnergy: Float,
    ): Boolean {
        if (recentAverageEnergy == null || recentAverageEnergy !in 0f..1f) return true
        return when (current) {
            DjSessionPhase.OPENING -> recentAverageEnergy > targetEnergy + 0.12f
            DjSessionPhase.WARM_UP, DjSessionPhase.BUILD_UP ->
                recentAverageEnergy < targetEnergy - 0.12f
            DjSessionPhase.PEAK_TIME -> recentAverageEnergy < targetEnergy - 0.16f
            DjSessionPhase.BREATHER, DjSessionPhase.COOL_DOWN, DjSessionPhase.CLOSING ->
                recentAverageEnergy > targetEnergy + 0.16f
            DjSessionPhase.FLOW -> false
        }
    }
}
