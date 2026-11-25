package com.example

import com.example.data.dj.v2.AudioContentKind
import com.example.data.dj.v2.DjTrackProfileFactory
import com.example.data.model.Track
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class DjTrackProfileFactoryTest {

    private fun track(title: String, genre: String, duration: Long = 240_000L) = Track(
        id = "id-$title",
        mediaStoreId = 1L,
        contentUri = "content://media/audio/1",
        title = title,
        artist = "Test Artist",
        album = "Test Album",
        albumArtist = "Test Artist",
        genre = genre,
        durationMs = duration,
        trackNumber = 1,
        year = 2025,
        mimeType = "audio/mpeg",
        filePath = "/Music/test.mp3",
        folderPath = "/Music",
        folderName = "Music",
        fileSizeBytes = 5_000_000L,
        dateAddedSec = 1L,
        albumId = 1L,
        artworkUri = null,
        customArtworkUri = null,
    )

    @Test
    fun `podcast metadata is never classified as a music track`() {
        val result = DjTrackProfileFactory.from(
            track("Podcast Episode 12", "Podcast", duration = 1_800_000L),
            null,
        )
        assertEquals(AudioContentKind.PODCAST, result.contentKind)
        assertNull(result.bpm)
        assertNull(result.camelotCode)
    }

    @Test
    fun `genre metadata identifies music but does not invent BPM or key`() {
        val result = DjTrackProfileFactory.from(track("Progressive Track", "Progressive House"), null)
        assertEquals(AudioContentKind.MUSIC, result.contentKind)
        assertEquals("Progressive House", result.subgenre)
        assertNull(result.bpm)
        assertNull(result.camelotCode)
        assertNull(result.energy)
    }

    @Test
    fun `unverified long audio remains unknown and has no musical defaults`() {
        val result = DjTrackProfileFactory.from(track("Long Recording", "", 2_400_000L), null)
        assertEquals(AudioContentKind.UNKNOWN, result.contentKind)
        assertNull(result.bpm)
        assertNull(result.camelotCode)
        assertNull(result.energy)
    }
}
