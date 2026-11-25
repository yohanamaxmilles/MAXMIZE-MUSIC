package com.example

import com.example.data.dj.v2.AudioContentKind
import com.example.data.dj.v2.DjDecisionConfig
import com.example.data.dj.v2.DjDecisionEngine
import com.example.data.dj.v2.DjTrackProfile
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class DjGenreRuleOverrideTest {
    private fun profile(id: String, genre: String, family: String) = DjTrackProfile(
        trackId = id,
        durationMs = 240_000L,
        contentKind = AudioContentKind.MUSIC,
        contentConfidence = 0.95f,
        primaryGenre = family,
        subgenre = genre,
        genreFamily = family,
        genreConfidence = 0.95f,
        bpm = 128f,
        bpmConfidence = 0.95f,
        musicalKey = "A min",
        camelotCode = "8A",
        keyConfidence = 0.95f,
        energy = 0.70f,
        energyConfidence = 0.90f,
    )

    @Test
    fun customAllowOverridesStrictGenreMatching() {
        val result = DjDecisionEngine().rankNextTracks(
            current = profile("house", "Progressive House", "House"),
            candidates = listOf(profile("techno", "Hard Techno", "Techno")),
            recentTrackIds = emptySet(),
            config = DjDecisionConfig(
                strictGenreMatching = true,
                customGenreRules = mapOf("hard techno|progressive house" to true),
            ),
        )
        assertEquals(listOf("techno"), result.rankedCandidates.map { it.trackId })
    }

    @Test
    fun customBlockOverridesFlexibleGenreMatching() {
        val result = DjDecisionEngine().rankNextTracks(
            current = profile("house", "Progressive House", "House"),
            candidates = listOf(profile("techno", "Hard Techno", "Techno")),
            recentTrackIds = emptySet(),
            config = DjDecisionConfig(
                strictGenreMatching = false,
                customGenreRules = mapOf("hard techno|progressive house" to false),
            ),
        )
        assertTrue(result.rankedCandidates.isEmpty())
        assertTrue(result.rejectedCandidates.single().reason.contains("custom genre rule"))
    }
}
