package com.example

import com.example.data.dj.v2.AudioContentKind
import com.example.data.dj.v2.DjDecisionConfig
import com.example.data.dj.v2.DjDecisionEngine
import com.example.data.dj.v2.DjTrackProfile
import com.example.data.dj.v2.DjTransitionConfig
import com.example.data.dj.v2.DjTransitionCurve
import com.example.data.dj.v2.DjTransitionPlanner
import com.example.data.dj.v2.DjTransitionPlanningResult
import com.example.data.dj.v2.MusicalSection
import com.example.data.dj.v2.MusicalSectionType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DjIntelligenceV2Test {

    private fun profile(
        id: String,
        genre: String = "Progressive House",
        family: String = "House",
        bpm: Float? = 120f,
        key: String? = "8A",
        energy: Float? = 0.65f,
        kind: AudioContentKind = AudioContentKind.MUSIC,
        confidence: Float = 0.95f,
        duration: Long = 240_000L,
        boundaries: List<Long> = listOf(120_000L, 136_000L, 152_000L),
        sections: List<MusicalSection> = listOf(
            MusicalSection(MusicalSectionType.INTRO, 0L, 32_000L, 0.95f),
            MusicalSection(MusicalSectionType.INSTRUMENTAL, 32_000L, 64_000L, 0.90f),
        ),
    ) = DjTrackProfile(
        trackId = id,
        durationMs = duration,
        contentKind = kind,
        contentConfidence = confidence,
        primaryGenre = family,
        subgenre = genre,
        genreFamily = family,
        genreConfidence = 0.95f,
        bpm = bpm,
        bpmConfidence = 0.95f,
        musicalKey = key,
        camelotCode = key,
        keyConfidence = 0.95f,
        energy = energy,
        energyConfidence = 0.90f,
        phraseBoundariesMs = boundaries,
        sections = sections,
        firstBeatMs = 0L,
        beatIntervalMs = bpm?.let { (60_000f / it).toLong() },
    )

    @Test
    fun `tempo ratio checks direct and half time alignment`() {
        assertEquals(120f / 118f, DjDecisionEngine.tempoRatio(120f, 118f)!!, 0.001f)
        assertEquals(1f, DjDecisionEngine.tempoRatio(140f, 70f)!!, 0.001f)
        assertEquals(1f, DjDecisionEngine.tempoRatio(70f, 140f)!!, 0.001f)
        assertEquals(null, DjDecisionEngine.tempoRatio(null, 120f))
    }

    @Test
    fun `strict ranking rejects podcasts and spoken audio`() {
        val current = profile("current")
        val podcast = profile("podcast", kind = AudioContentKind.PODCAST, duration = 1_800_000L)
        val spoken = profile("spoken", kind = AudioContentKind.SPOKEN_WORD)
        val valid = profile("valid")

        val result = DjDecisionEngine().rankNextTracks(
            current, listOf(podcast, spoken, valid), emptySet(),
        )

        assertEquals(listOf("valid"), result.rankedCandidates.map { it.trackId })
        assertTrue(result.rejectedCandidates.any { it.trackId == "podcast" })
        assertTrue(result.rejectedCandidates.any { it.trackId == "spoken" })
    }

    @Test
    fun `strict genre mode rejects unrelated genre families`() {
        val current = profile("house")
        val techno = profile("techno", genre = "Hard Techno", family = "Techno")
        val result = DjDecisionEngine().rankNextTracks(current, listOf(techno), emptySet())
        assertTrue(result.rankedCandidates.isEmpty())
        assertTrue(result.rejectedCandidates.single().reason.contains("Genre"))
    }

    @Test
    fun `missing BPM is rejected instead of receiving a fabricated default`() {
        val current = profile("current", bpm = null)
        val candidate = profile("candidate")
        val result = DjDecisionEngine().rankNextTracks(current, listOf(candidate), emptySet())
        assertTrue(result.rankedCandidates.isEmpty())
        assertTrue(result.rejectedCandidates.single().reason.contains("BPM"))
    }

    @Test
    fun `planner chooses future phrase and instrumental intro cue`() {
        val outgoing = profile("outgoing", boundaries = listOf(120_000L, 136_000L))
        val incoming = profile("incoming", bpm = 118f)
        val result = DjTransitionPlanner().plan(
            outgoing, incoming, outgoingPositionMs = 121_000L,
            config = DjTransitionConfig(bars = 8, beatsPerBar = 4),
        )

        assertTrue(result is DjTransitionPlanningResult.Ready)
        val plan = (result as DjTransitionPlanningResult.Ready).plan
        assertEquals(136_000L, plan.outgoingStartMs)
        assertEquals(0L, plan.incomingStartMs)
        assertEquals(16_000L, plan.durationMs)
        assertEquals(120f / 118f, plan.incomingPlaybackRate, 0.001f)
        assertTrue(plan.preservePitch)
    }

    @Test
    fun `planner refuses to invent missing phrase structure`() {
        val outgoing = profile("outgoing", boundaries = emptyList())
        val incoming = profile("incoming")
        val result = DjTransitionPlanner().plan(outgoing, incoming, 100_000L)
        assertTrue(result is DjTransitionPlanningResult.Unavailable)
    }

    @Test
    fun `planner switches to vocal aware EQ when both decks have overlapping vocals`() {
        val outgoing = profile(
            "outgoing",
            boundaries = listOf(120_000L),
            sections = listOf(MusicalSection(MusicalSectionType.VOCAL, 120_000L, 150_000L, 0.95f)),
        )
        val incoming = profile(
            "incoming",
            sections = listOf(
                MusicalSection(MusicalSectionType.INTRO, 0L, 10_000L, 0.95f),
                MusicalSection(MusicalSectionType.VOCAL, 0L, 20_000L, 0.95f),
            ),
        )
        val result = DjTransitionPlanner().plan(outgoing, incoming, 110_000L)
        assertTrue(result is DjTransitionPlanningResult.Ready)
        assertEquals(
            DjTransitionCurve.VOCAL_AWARE_EQ,
            (result as DjTransitionPlanningResult.Ready).plan.curve,
        )
    }

    @Test
    fun `session arc contains opening build peak breather and closing phases`() {
        assertEquals(
            com.example.data.dj.v2.DjSessionPhase.OPENING,
            com.example.data.dj.v2.DjSessionArcPlanner.phaseFor(0.02f),
        )
        assertEquals(
            com.example.data.dj.v2.DjSessionPhase.BUILD_UP,
            com.example.data.dj.v2.DjSessionArcPlanner.phaseFor(0.35f),
        )
        assertEquals(
            com.example.data.dj.v2.DjSessionPhase.PEAK_TIME,
            com.example.data.dj.v2.DjSessionArcPlanner.phaseFor(0.60f),
        )
        assertEquals(
            com.example.data.dj.v2.DjSessionPhase.BREATHER,
            com.example.data.dj.v2.DjSessionArcPlanner.phaseFor(0.78f),
        )
        assertEquals(
            com.example.data.dj.v2.DjSessionPhase.CLOSING,
            com.example.data.dj.v2.DjSessionArcPlanner.phaseFor(0.98f),
        )
    }
}
