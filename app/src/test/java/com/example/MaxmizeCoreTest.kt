package com.example

import com.example.data.lyrics.LrcParser
import com.example.player.BalanceAudioProcessor
import com.example.ui.components.formatDurationSummary
import com.example.ui.components.formatFileSize
import com.example.ui.components.formatTimecode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MaxmizeCoreTest {

    @Test
    fun `lrc parser sorts multi-timestamp chorus lines chronologically`() {
        val raw = """
            [ti:Maxmize Test]
            [00:10.50][00:45.00]Chorus line
            [00:02.00]Intro line
        """.trimIndent()
        val (lines, isSynced) = LrcParser.parse(raw)
        assertTrue(isSynced)
        assertEquals(3, lines.size)
        assertEquals(2000L, lines[0].timestampMs)
        assertEquals("Intro line", lines[0].text)
        assertEquals(10500L, lines[1].timestampMs)
        assertEquals(45000L, lines[2].timestampMs)
    }

    @Test
    fun `lrc parser handles plain text lyrics without timestamps`() {
        val raw = "First line\nSecond line"
        val (lines, isSynced) = LrcParser.parse(raw)
        assertFalse(isSynced)
        assertEquals(2, lines.size)
        assertEquals("First line", lines[0].text)
    }

    @Test
    fun `formatters produce accurate timecodes and file sizes`() {
        assertEquals("00:00", formatTimecode(0L))
        assertEquals("03:42", formatTimecode(222_000L))
        assertEquals("1:02:05", formatTimecode(3_725_000L))
        assertEquals("1h 5m", formatDurationSummary(3_900_000L))
        assertEquals("5.0 MB", formatFileSize(5L * 1024L * 1024L))
    }

    @Test
    fun `balance audio processor clamps stereo balance range`() {
        val processor = BalanceAudioProcessor()
        processor.setBalance(-2.5f)
        assertEquals(-1.0f, processor.getBalance(), 0.001f)
        processor.setBalance(1.8f)
        assertEquals(1.0f, processor.getBalance(), 0.001f)
        processor.setBalance(0.25f)
        assertEquals(0.25f, processor.getBalance(), 0.001f)
    }

    @Test
    fun `artwork match validator prefers studio match over live or remix mismatch`() {
        val tr = com.example.data.model.Track(
            id = "a1",
            mediaStoreId = 1L,
            contentUri = "content://media/external/audio/media/1",
            title = "Last Last",
            artist = "Burna Boy",
            album = "Love, Damini",
            albumArtist = "Burna Boy",
            genre = "Afrobeats",
            durationMs = 172_000L,
            trackNumber = 3,
            year = 2022,
            mimeType = "audio/mpeg",
            filePath = "/storage/emulated/0/Music/03 - Burna Boy - Last Last.mp3",
            folderPath = "/storage/emulated/0/Music",
            folderName = "Music",
            fileSizeBytes = 4_000_000L,
            dateAddedSec = 1_700_000_000L,
            albumId = 99L,
            artworkUri = null,
            customArtworkUri = null,
        )
        val exact = com.example.data.metadata.ArtworkMatchValidator.scoreCandidate(
            track = tr,
            candTitle = "Last Last",
            candArtist = "Burna Boy",
            candAlbum = "Love, Damini",
            candYear = 2022,
            candDurationMs = 172_000L,
        )
        val wrongVersion = com.example.data.metadata.ArtworkMatchValidator.scoreCandidate(
            track = tr,
            candTitle = "Last Last (Slowed Remix Live)",
            candArtist = "Tribute Band",
            candAlbum = "Unknown Cover",
            candYear = 2024,
            candDurationMs = 240_000L,
        )
        assertTrue("Expected high artwork score >= 85, got $exact", exact >= 85)
        assertTrue("Expected wrong version score < 50, got $wrongVersion", wrongVersion < 50)

        val suggested = com.example.data.metadata.MetadataEditor.suggestTagsFromFilename(tr)
        assertEquals("Burna Boy", suggested?.artist)
        assertEquals("Last Last", suggested?.title)
    }

    @Test
    fun `metadata health report detects missing fields and duplicate tracks`() {
        fun mk(id: String, title: String, artist: String, album: String, genre: String, year: Int, art: String?) =
            com.example.data.model.Track(
                id = id,
                mediaStoreId = id.hashCode().toLong(),
                contentUri = "content://media/external/audio/media/${id.hashCode()}",
                title = title,
                artist = artist,
                album = album,
                albumArtist = artist,
                genre = genre,
                durationMs = 180_000L,
                trackNumber = 1,
                year = year,
                mimeType = "audio/mpeg",
                filePath = "/Music/$id.mp3",
                folderPath = "/Music",
                folderName = "Music",
                fileSizeBytes = 3_000_000L,
                dateAddedSec = 100L,
                albumId = 1L,
                artworkUri = art,
                customArtworkUri = null,
            )
        val t1 = mk("1", "Essence", "Wizkid", "Made in Lagos", "Afrobeats", 2020, "content://art/1")
        val t2 = mk("2", "Essence (Official Audio)", "Wizkid feat. Tems", "Unknown Album", "Unknown Genre", 0, null)
        val report = com.example.data.metadata.MetadataEditor.analyzeLibraryHealth(listOf(t1, t2))
        assertEquals(1, report.missingAlbumTracks.size)
        assertEquals(1, report.missingGenreTracks.size)
        assertEquals(1, report.missingArtworkTracks.size)
        assertEquals(1, report.missingYearTracks.size)
        assertEquals(1, report.duplicateGroups.size)
    }
}
