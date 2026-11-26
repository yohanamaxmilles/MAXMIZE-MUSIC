package com.example

import com.example.data.lyrics.LrcFormatter
import com.example.data.lyrics.LrcLibLyricsProvider
import com.example.data.lyrics.LrcParser
import com.example.data.lyrics.TrackMatchValidator
import com.example.data.model.LyricLine
import com.example.data.model.LyricsSearchCandidate
import com.example.data.model.Track
import com.example.player.PlaybackClockSnapshot
import com.example.player.PlaybackController
import com.example.ui.lyrics.LyricsSynchronizer
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

        val parsed = LrcParser.parse(raw)
        assertTrue(parsed.isSynced)
        assertFalse(parsed.hasWordSync)
        assertEquals("Midnight City", parsed.titleTag)
        assertEquals("M83", parsed.artistTag)
        assertEquals(500L, parsed.offsetMs)
        assertEquals(2, parsed.lines.size)
        assertEquals(10_500L, parsed.lines[0].timestampMs)
        assertEquals("Waiting in a car", parsed.lines[0].text)
        assertEquals(14_750L, parsed.lines[1].timestampMs)
    }

    @Test
    fun `LrcParser parses hh mm ss and colon centisecond formats`() {
        val raw = """
            [00:05:40]Colon centis
            [01:02:03.500]Over an hour live set
        """.trimIndent()

        val parsed = LrcParser.parse(raw)
        assertTrue(parsed.isSynced)
        assertEquals(2, parsed.lines.size)
        assertEquals(5_400L, parsed.lines[0].timestampMs)
        assertEquals(3_723_500L, parsed.lines[1].timestampMs)
    }

    @Test
    fun `LrcParser parses enhanced word-level timestamps`() {
        val raw = """
            [00:05.00]<00:05.00>Good <00:05.50>vibes <00:06.10>only
        """.trimIndent()

        val parsed = LrcParser.parse(raw)
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

        val parsed = LrcParser.parse(raw)
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

        val parsed = LrcParser.parse(raw)
        assertFalse(parsed.isSynced)
        assertFalse(parsed.hasWordSync)
        assertEquals(3, parsed.lines.size)
        parsed.lines.forEach { line ->
            assertNull(line.timestampMs)
        }
    }

    @Test
    fun `LyricsSynchronizer binary search handles forward backward seeks offsets and thousands of lines`() {
        val lines = (0 until 3_000).map { i ->
            LyricLine(timestampMs = (i + 1) * 1_000L, text = "Line $i")
        }

        // Before first timestamp
        assertEquals(-1, LyricsSynchronizer.findActiveLineIndex(lines, 500L))
        // Exact boundary hits
        assertEquals(0, LyricsSynchronizer.findActiveLineIndex(lines, 1_000L))
        assertEquals(1_499, LyricsSynchronizer.findActiveLineIndex(lines, 1_500_500L))
        // Seek backward
        assertEquals(4, LyricsSynchronizer.findActiveLineIndex(lines, 5_200L))
        // Positive sync offset advances earlier
        assertEquals(5, LyricsSynchronizer.findActiveLineIndex(lines, 5_200L, syncOffsetMs = 900L))
        // Negative sync offset delays
        assertEquals(3, LyricsSynchronizer.findActiveLineIndex(lines, 5_200L, syncOffsetMs = -500L))
        // Seek target consistency
        val tapTarget = LyricsSynchronizer.computeSeekPositionMs(lineTimestampMs = 15_000L, syncOffsetMs = 500L)
        assertEquals(14_500L, tapTarget)
        assertEquals(14, LyricsSynchronizer.findActiveLineIndex(lines, tapTarget, syncOffsetMs = 500L))
        // Beyond last timestamp
        assertEquals(2_999, LyricsSynchronizer.findActiveLineIndex(lines, 9_999_999L))
        // Empty safety
        assertEquals(-1, LyricsSynchronizer.findActiveLineIndex(emptyList(), 5_000L))
    }

    @Test
    fun `LyricsSynchronizer finds active word index via binary search`() {
        val parsed = LrcParser.parse("[00:10.00]<00:10.00>Hello <00:10.60>world <00:11.20>today")
        val words = parsed.lines.first().words
        assertEquals(-1, LyricsSynchronizer.findActiveWordIndex(words, 9_900L))
        assertEquals(0, LyricsSynchronizer.findActiveWordIndex(words, 10_000L))
        assertEquals(1, LyricsSynchronizer.findActiveWordIndex(words, 10_850L))
        assertEquals(2, LyricsSynchronizer.findActiveWordIndex(words, 12_000L))
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

    @Test
    fun `TrackMatchValidator infers artist and clean title from messy filename and unknown artist`() {
        val messy = sampleTrack(
            title = "01 - Diamond_Platnumz - Yatapita (Official Music Video) [320kbps]",
            artist = "<unknown>",
        ).copy(
            filePath = "/storage/emulated/0/Download/01 - Diamond Platnumz - Yatapita (Official Audio).mp3",
        )
        val (inferredArtist, inferredTitle) = TrackMatchValidator.inferArtistAndTitle(messy)
        assertEquals("Diamond Platnumz", inferredArtist)
        assertEquals("Yatapita", inferredTitle)

        val underscoreFile = sampleTrack(
            title = "Harmonize_Single_Again_[HQ]",
            artist = "Unknown Artist",
        ).copy(
            filePath = "/storage/emulated/0/Music/Harmonize_-_Single_Again_(Official_Video).mp3",
        )
        val (artist2, title2) = TrackMatchValidator.inferArtistAndTitle(underscoreFile)
        assertEquals("Harmonize", artist2)
        assertEquals("Single Again", title2)
    }

    @Test
    fun `LrcFormatter formats timestamps and translations for LrcParser roundtrip`() {
        val input = listOf(
            LyricLine(
                timestampMs = 5_240L,
                text = "Niko tayari leo",
                translation = "I am ready today",
            ),
            LyricLine(
                timestampMs = 72_080L,
                text = "Sauti inapanda juu",
            ),
        )
        val formatted = LrcFormatter.formatLrc(input)
        val expected = """
            [00:05.24]Niko tayari leo
            [00:05.24]I am ready today
            [01:12.08]Sauti inapanda juu
        """.trimIndent()
        assertEquals(expected, formatted)

        val reparsed = LrcParser.parse(formatted)
        assertTrue(reparsed.isSynced)
        assertEquals(2, reparsed.lines.size)
        assertEquals(5_240L, reparsed.lines[0].timestampMs)
        assertEquals("I am ready today", reparsed.lines[0].translation)
        assertEquals(72_080L, reparsed.lines[1].timestampMs)
    }

    @Test
    fun `PlaybackController computeInterpolatedPositionMs tracks speed pause and bounds`() {
        val playingClock = PlaybackClockSnapshot(
            trackId = "t1",
            anchorPositionMs = 10_000L,
            anchorRealtimeMs = 1_000L,
            playbackSpeed = 1.5f,
            isPlaying = true,
            durationMs = 20_000L,
        )
        assertEquals(13_000L, PlaybackController.computeInterpolatedPositionMs(playingClock, nowRealtimeMs = 3_000L))
        assertEquals(20_000L, PlaybackController.computeInterpolatedPositionMs(playingClock, nowRealtimeMs = 15_000L))

        val pausedClock = playingClock.copy(isPlaying = false)
        assertEquals(10_000L, PlaybackController.computeInterpolatedPositionMs(pausedClock, nowRealtimeMs = 9_000L))
    }

    @Test
    fun `LrcLibLyricsProvider selectBestAutoCandidate rejects wrong-duration synced edits`() {
        val wrongDurationSynced = LyricsSearchCandidate(
            id = "wrong",
            trackName = "Midnight City",
            artistName = "M83",
            albumName = "Hurry Up",
            durationSec = 265, // 22s intro difference vs 243s track
            hasSyncedLyrics = true,
            syncedLyrics = "[00:25.00]Late intro",
            plainLyrics = null,
            provider = "LRCLIB",
            matchScore = 82,
        )
        val exactDurationSynced = LyricsSearchCandidate(
            id = "exact",
            trackName = "Midnight City",
            artistName = "M83",
            albumName = "Hurry Up",
            durationSec = 244,
            hasSyncedLyrics = true,
            syncedLyrics = "[00:05.00]Right on beat",
            plainLyrics = null,
            provider = "LRCLIB",
            matchScore = 79,
        )
        val picked = LrcLibLyricsProvider.selectBestAutoCandidate(
            candidates = listOf(wrongDurationSynced, exactDurationSynced),
            trackDurationMs = 243_000L,
        )
        assertEquals("exact", picked?.id)

        // When only a 25s-shifted synced edit exists, do not auto-apply out-of-sync timestamps.
        val rejected = LrcLibLyricsProvider.selectBestAutoCandidate(
            candidates = listOf(wrongDurationSynced),
            trackDurationMs = 243_000L,
        )
        assertNull(rejected)
    }
}
