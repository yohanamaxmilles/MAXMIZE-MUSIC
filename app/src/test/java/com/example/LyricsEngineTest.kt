package com.example

import com.example.data.lyrics.LyricsRepository
import com.example.data.lyrics.TrackMatchValidator
import com.example.data.model.Track
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class LyricsEngineTest {

    private fun sampleTrack(
        title: String = "Midnight City",
        artist: String = "M83",
        album: String = "Hurry Up, We're Dreaming",
        durationMs: Long = 243_000L,
    ) = Track(
        id = "track_1",
        mediaStoreId = 1L,
        contentUri = "content://media/external/audio/media/1",
        title = title,
        artist = artist,
        album = album,
        albumArtist = artist,
        genre = "Synthpop",
        durationMs = durationMs,
        trackNumber = 1,
        year = 2011,
        mimeType = "audio/mpeg",
        filePath = "/storage/emulated/0/Music/midnight.mp3",
        folderPath = "/storage/emulated/0/Music",
        folderName = "Music",
        fileSizeBytes = 5_000_000L,
        dateAddedSec = 1_700_000_000L,
        albumId = 10L,
        artworkUri = null,
        customArtworkUri = null,
    )

    @Test
    fun `LrcParser parses synced timestamps with metadata and offset`() {
        val raw = """
            [ti:Midnight City]
            [ar:M83]
            [offset:500]
            [00:10.00]Waiting in a car
            [00:14.25]Waiting for a ride in the dark
        """.trimIndent()

        val parsed = LyricsRepository.LrcParser.parse(raw)
        assertTrue(parsed.isSynced)
        assertFalse(parsed.hasWordSync)
        assertEquals(2, parsed.lines.size)
        assertEquals(10_500L, parsed.lines[0].timestampMs)
        assertEquals("Waiting in a car", parsed.lines[0].text)
        assertEquals(14_750L, parsed.lines[1].timestampMs)
    }

    @Test
    fun `LrcParser parses enhanced word-level timestamps`() {
        val raw = """
            [00:05.00]<00:05.00>Good <00:05.50>vibes <00:06.10>only
        """.trimIndent()

        val parsed = LyricsRepository.LrcParser.parse(raw)
        assertTrue(parsed.isSynced)
        assertTrue(parsed.hasWordSync)
        assertEquals(1, parsed.lines.size)
        assertEquals(3, parsed.lines[0].words.size)
        assertEquals(5_000L, parsed.lines[0].words[0].startMs)
        assertEquals(5_500L, parsed.lines[0].words[1].startMs)
        assertEquals("only", parsed.lines[0].words[2].text)
    }

    @Test
    fun `LrcParser merges dual-language translation lines sharing timestamp`() {
        val raw = """
            [00:12.00]Usiku wa leo ni wetu
            [00:12.00]Tonight belongs to us
        """.trimIndent()

        val parsed = LyricsRepository.LrcParser.parse(raw)
        assertTrue(parsed.isSynced)
        assertEquals(1, parsed.lines.size)
        assertEquals("Usiku wa leo ni wetu", parsed.lines[0].text)
        assertEquals("Tonight belongs to us", parsed.lines[0].translation)
    }

    @Test
    fun `LrcParser never fabricates timestamps for plain unsynced lyrics`() {
        val raw = """
            Line one without time
            Line two without time
            Line three without time
        """.trimIndent()

        val parsed = LyricsRepository.LrcParser.parse(raw)
        assertFalse(parsed.isSynced)
        assertFalse(parsed.hasWordSync)
        assertEquals(3, parsed.lines.size)
        parsed.lines.forEach { line ->
            assertNull(line.timestampMs)
        }
    }

    @Test
    fun `TrackMatchValidator scores exact match high and mismatched duration artist low`() {
        val track = sampleTrack(title = "Midnight City (Official Video)", artist = "M83 feat. Zola")

        val exactScore = TrackMatchValidator.scoreCandidate(
            track = track,
            candTitle = "Midnight City",
            candArtist = "M83",
            candAlbum = "Hurry Up, We're Dreaming",
            candDurationSec = 243,
            hasSynced = true,
        )
        val wrongScore = TrackMatchValidator.scoreCandidate(
            track = track,
            candTitle = "Totally Different Song",
            candArtist = "Other Artist",
            candAlbum = "Other Album",
            candDurationSec = 110,
            hasSynced = false,
        )

        assertTrue("Expected high score for exact cleaned match, got $exactScore", exactScore >= 90)
        assertTrue("Expected low score for mismatch, got $wrongScore", wrongScore < 30)
    }

    @Test
    fun `TrackMatchValidator normalizes diacritics and penalizes karaoke or live mismatch`() {
        val track = sampleTrack(title = "Beyoncé Nights", artist = "Café Tacvba", durationMs = 200_000L)
        val cleanScore = TrackMatchValidator.scoreCandidate(
            track = track,
            candTitle = "Beyonce Nights",
            candArtist = "Cafe Tacvba",
            candAlbum = "Hurry Up, We're Dreaming",
            candDurationSec = 200,
            hasSynced = true,
        )
        val karaokeScore = TrackMatchValidator.scoreCandidate(
            track = track,
            candTitle = "Beyonce Nights (Karaoke Version)",
            candArtist = "Cafe Tacvba",
            candAlbum = "Karaoke Hits",
            candDurationSec = 200,
            hasSynced = true,
        )
        assertTrue("Expected accented title match >= 85, got $cleanScore", cleanScore >= 85)
        assertTrue("Expected clean studio match ($cleanScore) > karaoke ($karaokeScore)", cleanScore - karaokeScore >= 18)
    }
}
