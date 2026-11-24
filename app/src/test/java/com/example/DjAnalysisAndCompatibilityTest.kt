package com.example

import com.example.data.dj.AudioAnalysisEngine
import com.example.data.dj.HarmonicCompatibility
import com.example.data.model.AutomixConfig
import com.example.data.model.MixMode
import com.example.data.model.MoodFilter
import com.example.data.model.Track
import com.example.data.model.TrackAnalysis
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.sin

class DjAnalysisAndCompatibilityTest {

    private fun track(
        id: String,
        title: String,
        genre: String = "House",
    ) = Track(
        id = id,
        mediaStoreId = id.hashCode().toLong(),
        contentUri = "content://media/external/audio/media/${id.hashCode()}",
        title = title,
        artist = "Artist $id",
        album = "Album $id",
        albumArtist = "Artist $id",
        genre = genre,
        durationMs = 210_000L,
        trackNumber = 1,
        year = 2024,
        mimeType = "audio/mpeg",
        filePath = "/storage/emulated/0/Music/$id.mp3",
        folderPath = "/storage/emulated/0/Music",
        folderName = "Music",
        fileSizeBytes = 6_000_000L,
        dateAddedSec = 1_700_000_000L,
        albumId = 1L,
        artworkUri = null,
        customArtworkUri = null,
    )

    private fun analysis(
        trackId: String,
        bpm: Float,
        key: String,
        energy: Float = 0.65f,
    ) = TrackAnalysis(
        trackId = trackId,
        bpm = bpm,
        bpmConfidence = 0.9f,
        tempoStability = 0.9f,
        musicalKey = key,
        camelotCode = HarmonicCompatibility.camelotForKey(key),
        keyConfidence = 0.85f,
        energy = energy,
        rmsDb = -11f,
        loudnessLufs = -11.7f,
        dynamicRangeDb = 8f,
        onsetDensity = 2.5f,
        firstBeatMs = 0L,
        beatIntervalMs = (60_000f / bpm).toLong(),
        introEndMs = 12_000L,
        outroStartMs = 190_000L,
        durationMs = 210_000L,
    )

    @Test
    fun `harmonicScore follows Camelot wheel relationships`() {
        assertEquals(100, HarmonicCompatibility.harmonicScore("8A", "8A"))
        assertEquals(94, HarmonicCompatibility.harmonicScore("8A", "8B"))
        assertEquals(90, HarmonicCompatibility.harmonicScore("8A", "9A"))
        assertEquals(90, HarmonicCompatibility.harmonicScore("12A", "1A")) // Wrap-around 12 -> 1
        assertEquals(76, HarmonicCompatibility.harmonicScore("8A", "7B"))
        assertTrue(HarmonicCompatibility.harmonicScore("8A", "2A") < 35)
    }

    @Test
    fun `computeBeatmatchRatio handles direct and half-time or double-time alignment`() {
        val direct = HarmonicCompatibility.computeBeatmatchRatio(124f, 120f)
        assertEquals(124f / 120f, direct, 0.001f)

        val doubleTime = HarmonicCompatibility.computeBeatmatchRatio(140f, 70f)
        assertEquals(1.0f, doubleTime, 0.001f)

        val halfTime = HarmonicCompatibility.computeBeatmatchRatio(70f, 140f)
        assertEquals(1.0f, halfTime, 0.001f)
    }

    @Test
    fun `analyzePcmSamples extracts accurate tempo energy and key from synthetic PCM`() {
        val sampleRate = 22_050
        val seconds = 12
        val totalSamples = sampleRate * seconds
        val samples = FloatArray(totalSamples)

        // 120 BPM beat pulses (every 0.5s) + A minor triad frequencies (A3=220Hz, C4=261.63Hz, E4=329.63Hz)
        val beatSamples = sampleRate / 2 // 120 BPM = 2 beats/sec
        for (i in 0 until totalSamples) {
            val phaseInBeat = i % beatSamples
            val envelope = if (phaseInBeat < sampleRate / 12) 0.9f else 0.18f
            val t = i.toDouble() / sampleRate
            val tone = (0.45 * sin(2.0 * PI * 220.0 * t) +
                0.35 * sin(2.0 * PI * 261.63 * t) +
                0.25 * sin(2.0 * PI * 329.63 * t)).toFloat()
            samples[i] = (tone * envelope).coerceIn(-1f, 1f)
        }

        val result = AudioAnalysisEngine.analyzePcmSamples(
            trackId = "synth_120_amin",
            samples = samples,
            sampleRate = sampleRate,
            durationMs = seconds * 1000L,
        )

        assertTrue("Expected extracted BPM near 120, got ${result.bpm}", abs(result.bpm - 120f) <= 4f)
        assertTrue(result.beatIntervalMs in 475L..525L)
        assertTrue(result.energy in 0.1f..1.0f)
        assertTrue(result.loudnessLufs < 0f && result.loudnessLufs > -40f)
        assertEquals("A min", result.musicalKey)
        assertEquals("8A", result.camelotCode)
    }

    @Test
    fun `selectSmartQueue prioritizes harmonic and BPM match while avoiding recent repeats`() {
        val current = track("t0", "Starter")
        val harmonicBest = track("t1", "Harmonic Match")
        val clashTrack = track("t2", "Clashing Track")
        val recentTrack = track("t3", "Already Played")

        val analyses = mapOf(
            current.id to analysis(current.id, bpm = 124f, key = "8A", energy = 0.7f),
            harmonicBest.id to analysis(harmonicBest.id, bpm = 125f, key = "9A", energy = 0.72f),
            clashTrack.id to analysis(clashTrack.id, bpm = 158f, key = "2B", energy = 0.25f),
            recentTrack.id to analysis(recentTrack.id, bpm = 124f, key = "8A", energy = 0.7f),
        )

        val config = AutomixConfig(
            enabled = true,
            mode = MixMode.DJ_MODE,
            bpmMin = 100,
            bpmMax = 140,
            bpmMatchEnabled = true,
            keyMatchingEnabled = true,
            autoSelectMood = true,
            mood = MoodFilter.ENERGETIC,
            avoidRepeats = true,
        )

        val queue = HarmonicCompatibility.selectSmartQueue(
            currentTrack = current,
            allTracks = listOf(current, clashTrack, recentTrack, harmonicBest),
            analyses = analyses,
            recentTrackIds = setOf(recentTrack.id),
            config = config,
            count = 5,
        )

        assertFalse(queue.any { it.track.id == current.id })
        assertFalse(queue.any { it.track.id == recentTrack.id })
        assertEquals(harmonicBest.id, queue.first().track.id)
        assertTrue(queue.first().compatibilityScore >= 85)
    }

    @Test
    fun `GenreEngine allows same family and rejects incompatible pairs under strict mode`() {
        val bongoTrack = track("bf", "Bongo Jam", genre = "Bongo Flava")
        val afroTrack = track("ab", "Afro Vibe", genre = "Afrobeats")
        val popTrack = track("pop", "Radio Pop", genre = "Pop")
        val hardTechnoTrack = track("ht", "Warehouse 145", genre = "Hard Techno")

        val strictCfg = AutomixConfig(strictGenreMatching = true, openFormatBridging = false)
        val sameFamily = com.example.data.dj.GenreEngine.evaluateCompatibility(
            bongoTrack, null, afroTrack, null, strictCfg,
        )
        assertTrue(sameFamily.allowed)
        assertTrue(sameFamily.score >= 85)

        val clashStrict = com.example.data.dj.GenreEngine.evaluateCompatibility(
            popTrack, null, hardTechnoTrack, null, strictCfg,
        )
        assertFalse(clashStrict.allowed)

        val clashBridge = com.example.data.dj.GenreEngine.evaluateCompatibility(
            popTrack, null, hardTechnoTrack, null, strictCfg.copy(openFormatBridging = true),
        )
        assertTrue(clashBridge.allowed)

        val customAllow = listOf(
            com.example.data.local.db.GenreRuleOverrideEntity("Pop", "Hard Techno", allowed = true)
        )
        val overridden = com.example.data.dj.GenreEngine.evaluateCompatibility(
            popTrack, null, hardTechnoTrack, null, strictCfg, customAllow,
        )
        assertTrue(overridden.allowed)
    }

    @Test
    fun `HarmonicCompatibility enforces strict harmonic matching and reports empty reason`() {
        val current = track("c1", "Starter", genre = "Pop")
        val incompatible = track("i1", "Industrial Slam", genre = "Hard Techno")
        val clashKeyPop = track("p2", "Clashing Key Pop", genre = "Pop")

        val analyses = mapOf(
            current.id to analysis(current.id, bpm = 120f, key = "A min", energy = 0.55f),
            incompatible.id to analysis(incompatible.id, bpm = 120f, key = "A min", energy = 0.85f),
            clashKeyPop.id to analysis(clashKeyPop.id, bpm = 120f, key = "F# maj", energy = 0.55f),
        )

        val strictBoth = AutomixConfig(
            bpmMin = 100,
            bpmMax = 140,
            genrePreference = "All Genres",
            autoSelectMood = false,
            strictGenreMatching = true,
            openFormatBridging = false,
            keyMatchingEnabled = true,
            harmonicMixLevel = com.example.data.model.HarmonicMixLevel.STRICT,
        )

        val result = HarmonicCompatibility.selectSmartQueueWithStatus(
            currentTrack = current,
            allTracks = listOf(current, incompatible, clashKeyPop),
            analyses = analyses,
            recentTrackIds = emptySet(),
            config = strictBoth,
        )
        assertTrue(result.queue.isEmpty())
        assertTrue(!result.noSuitableTrackReason.isNullOrBlank())

        val flexibleRes = HarmonicCompatibility.selectSmartQueueWithStatus(
            currentTrack = current,
            allTracks = listOf(current, clashKeyPop),
            analyses = analyses,
            recentTrackIds = emptySet(),
            config = strictBoth.copy(harmonicMixLevel = com.example.data.model.HarmonicMixLevel.FLEXIBLE),
        )
        assertEquals(1, flexibleRes.queue.size)
        assertEquals(clashKeyPop.id, flexibleRes.queue.first().track.id)
    }
}
