package com.example.data.dj.next

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.PI
import kotlin.math.sin

class DjNextEngineTest {
    @Test
    fun silenceProducesNoInventedTempoOrKey() {
        val result = DjPcmAnalyzer().analyze("silence", FloatArray(22_050 * 5), 22_050)
        assertNull(result.bpm)
        assertNull(result.musicalKey)
        assertNull(result.vocalProbability)
        assertEquals(DjConfidence.LOW, result.bpmConfidenceBand)
    }

    @Test
    fun pulseTrainFindsTempoAndBeatGrid() {
        val rate = 22_050
        val seconds = 24
        val pcm = FloatArray(rate * seconds)
        val beatLength = rate / 2
        for (i in pcm.indices) {
            val phase = i % beatLength
            val envelope = if (phase < rate / 10) 0.9f else 0.12f
            val tone = sin(2.0 * PI * 110.0 * i / rate).toFloat()
            pcm[i] = tone * envelope
        }
        val result = DjPcmAnalyzer().analyze("pulse-120", pcm, rate)
        assertNotNull(result.bpm)
        assertTrue("BPM was ${result.bpm}", kotlin.math.abs(result.bpm!! - 120f) < 5f)
        assertTrue(result.beatTimesMs.size > 30)
        assertTrue(result.phraseMarkersMs.isNotEmpty())
    }

    @Test
    fun lowToHighEnergyJumpIsMarkedAsDropCandidate() {
        val rate = 22_050
        val seconds = 32
        val pcm = FloatArray(rate * seconds)
        val segmentAmplitude = floatArrayOf(0.42f, 0.035f, 0.78f, 0.40f)
        for (i in pcm.indices) {
            val segment = (i / (rate * 8)).coerceIn(0, 3)
            pcm[i] = segmentAmplitude[segment] *
                sin(2.0 * PI * 110.0 * i / rate).toFloat()
        }

        val result = DjPcmAnalyzer().analyze("energy-jump", pcm, rate)
        assertTrue(result.sections.any { it.type == DjSectionType.DROP_CANDIDATE })
        assertTrue(result.notes.any { it.contains("energy jumps only") })
    }

    @Test
    fun candidateRankingAvoidsRecentTracksAndPrefersGoodTempo() {
        val engine = DjIntelligenceEngine()
        val current = DjTrackProfile("a", "A", "Artist", 240_000, 124f, 0.9f, "8A", "House", 0.65f)
        val good = DjTrackProfile("b", "B", "Artist", 240_000, 125f, 0.9f, "9A", "House", 0.68f)
        val poor = DjTrackProfile("c", "C", "Artist", 240_000, 158f, 0.9f, "2B", "Hard Techno", 0.2f)
        val recent = DjTrackProfile("d", "D", "Artist", 240_000, 124f, 0.9f, "8A", "House", 0.65f)
        val ranked = engine.rankNextTracks(current, listOf(poor, recent, good), setOf("d"))
        assertEquals("b", ranked.first().track.trackId)
        assertTrue(ranked.none { it.track.trackId == "d" })
    }

    @Test
    fun mixFrameHandsBassFromOutgoingToIncoming() {
        val start = DjMixAutomation.frame(0f)
        val middle = DjMixAutomation.frame(0.5f)
        val end = DjMixAutomation.frame(1f)
        assertEquals(1f, start.outgoingLowGain, 0.001f)
        assertEquals(0f, start.incomingLowGain, 0.001f)
        assertEquals(0f, end.outgoingLowGain, 0.001f)
        assertEquals(1f, end.incomingLowGain, 0.001f)
        assertTrue(middle.incomingGain > 0f)
    }

    @Test
    fun sessionPlannerBuildsEnergyArcWithoutRepeatingTracks() {
        val start = DjTrackProfile("start", "Start", "Artist", 240_000, 124f, 0.9f, "8A", "House", 0.35f)
        val tracks = (1..10).map { index ->
            DjTrackProfile(
                trackId = "track-$index",
                title = "Track $index",
                artist = "Artist",
                durationMs = 240_000,
                bpm = 124f + (index % 3),
                bpmConfidence = 0.9f,
                camelotKey = if (index % 2 == 0) "8A" else "9A",
                genreFamily = "House",
                energy = index / 10f,
            )
        }
        val planned = DjSessionPlanner().plan(start, tracks, 7)
        assertEquals(7, planned.size)
        assertEquals(DjSessionPhase.OPENING, planned.first().phase)
        assertEquals(DjSessionPhase.CLOSING, planned.last().phase)
        assertEquals(planned.size, planned.map { it.profile.trackId }.distinct().size)
    }
}
