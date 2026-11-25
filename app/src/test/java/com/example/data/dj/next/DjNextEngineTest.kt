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
        assertEquals(32, result.beatTimesMs.indexOf(result.phraseMarkersMs.first()))
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
    fun vocalEvidenceProducesCandidatesWithoutClaimingVerifiedLyrics() {
        val result = DjVocalEvidenceAnalyzer.analyze(
            listOf(
                DjVocalModelFrame(0L, 1_000L, mapOf("Music" to 0.8f, "Singing" to 0.91f)),
                DjVocalModelFrame(1_000L, 1_000L, mapOf("Singing" to 0.88f)),
                DjVocalModelFrame(2_000L, 1_000L, mapOf("Instrumental music" to 0.9f)),
                DjVocalModelFrame(3_000L, 1_000L, mapOf("Narration, monologue" to 0.82f)),
            ),
        )
        assertEquals(0.91f, result.peakVocalProbability!!, 0.001f)
        assertEquals(0.5f, result.vocalActiveRatio!!, 0.001f)
        assertEquals(DjVocalConfidence.HIGH, result.confidence)
        assertEquals(2, result.segments.size)
        assertEquals(DjVocalKind.SINGING_CANDIDATE, result.segments.first().kind)
        assertEquals(DjVocalKind.SPEECH_CANDIDATE, result.segments.last().kind)
        assertTrue(result.notes.any { it.contains("not verified lyrics") })
    }

    @Test
    fun missingVocalModelScoresRemainUnknown() {
        val result = DjVocalEvidenceAnalyzer.analyze(emptyList())
        assertNull(result.averageVocalProbability)
        assertNull(result.peakVocalProbability)
        assertTrue(result.segments.isEmpty())
        assertEquals(DjVocalConfidence.LOW, result.confidence)
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
    fun beatSyncRequiresReliableTempoAndBeatCues() {
        val outgoing = DjTrackProfile(
            "out", "Out", "Artist", 240_000,
            bpm = 128f, bpmConfidence = 0.9f, firstBeatMs = 420L,
        )
        val incoming = DjTrackProfile(
            "in", "In", "Artist", 240_000,
            bpm = 64f, bpmConfidence = 0.9f, firstBeatMs = 850L,
        )
        val plan = DjBeatSyncPlanner.plan(outgoing, incoming)
        assertTrue(plan.enabled)
        assertEquals(1f, plan.speedRatio, 0.001f)
        assertEquals(128f, plan.normalizedIncomingBpm!!, 0.001f)

        val closeTempo = DjBeatSyncPlanner.plan(
            outgoing,
            incoming.copy(bpm = 125f, firstBeatMs = 850L),
        )
        assertTrue(closeTempo.enabled)
        assertEquals(128f / 125f, closeTempo.speedRatio, 0.001f)

        val uncertain = DjBeatSyncPlanner.plan(
            outgoing,
            incoming.copy(bpmConfidence = 0.2f),
        )
        assertTrue(!uncertain.enabled)
        assertEquals(1f, uncertain.speedRatio, 0.001f)

        val largeAdjustment = DjBeatSyncPlanner.plan(
            outgoing,
            incoming.copy(bpm = 100f, firstBeatMs = 850L),
        )
        assertTrue(!largeAdjustment.enabled)
        assertEquals(1f, largeAdjustment.speedRatio, 0.001f)
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
    @Test
    fun transitionCuesAndCrossfadeStayInsideBothTrackBoundaries() {
        val outgoing = DjTrackProfile(
            trackId = "out-boundary",
            title = "Outgoing",
            artist = "Artist",
            durationMs = 180_000L,
            bpm = 124f,
            bpmConfidence = 0.9f,
            outroStartMs = 179_500L,
        )
        val incoming = DjTrackProfile(
            trackId = "in-boundary",
            title = "Incoming",
            artist = "Artist",
            durationMs = 150_000L,
            bpm = 124f,
            bpmConfidence = 0.9f,
            firstBeatMs = 149_800L,
        )

        val plan = DjIntelligenceEngine().planTransition(outgoing, incoming, bars = 8)

        assertTrue(plan.durationMs > 0L)
        assertTrue(plan.startOutgoingAtMs >= 0L)
        assertTrue(plan.startIncomingAtMs >= 0L)
        assertTrue(plan.startOutgoingAtMs + plan.durationMs <= outgoing.durationMs)
        assertTrue(plan.startIncomingAtMs + plan.durationMs <= incoming.durationMs)
        assertTrue(plan.explanation.contains("moved earlier"))
    }

    @Test
    fun bassSwapEqMovesLowBandBetweenDecksWithoutChangingMidOrHigh() {
        val start = DjEqAutomation.bassSwap(0f)
        val middle = DjEqAutomation.bassSwap(0.5f)
        val end = DjEqAutomation.bassSwap(1f)

        assertEquals(1f, start.outgoingLow, 0.001f)
        assertEquals(0.1f, start.incomingLow, 0.001f)
        assertTrue(middle.outgoingLow < start.outgoingLow)
        assertTrue(middle.incomingLow > start.incomingLow)
        assertEquals(1f, end.outgoingMid, 0.001f)
        assertEquals(1f, end.incomingHigh, 0.001f)
        assertEquals(0.1f, end.outgoingLow, 0.001f)
        assertEquals(1f, end.incomingLow, 0.001f)
    }

    @Test
    fun eqFilterAutomationIsBoundedAndRestoresIncomingDeck() {
        val start = DjEqAutomation.eqFilter(0f)
        val middle = DjEqAutomation.eqFilter(0.5f)
        val end = DjEqAutomation.eqFilter(1f)

        assertTrue(middle.outgoingLow in 0f..1f)
        assertTrue(middle.outgoingMid in 0f..1f)
        assertTrue(middle.outgoingHigh in 0f..1f)
        assertTrue(middle.incomingLow in 0f..1f)
        assertTrue(middle.incomingMid in 0f..1f)
        assertTrue(middle.outgoingLow < start.outgoingLow)
        assertTrue(middle.outgoingMid < start.outgoingMid)
        assertTrue(middle.incomingLow > start.incomingLow)
        assertTrue(middle.incomingMid > start.incomingMid)
        assertEquals(1f, end.incomingLow, 0.001f)
        assertEquals(1f, end.incomingMid, 0.001f)
        assertEquals(1f, end.incomingHigh, 0.001f)
    }

}
