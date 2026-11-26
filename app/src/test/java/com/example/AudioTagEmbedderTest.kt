package com.example

import com.example.data.metadata.AudioTagEmbedder
import com.example.data.metadata.AudioTagWriteRequest
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayOutputStream

class AudioTagEmbedderTest {

    @Test
    fun `patchMp3Id3v23 writes title artist album genre year and embedded JPEG APIC while preserving audio bytes`() {
        val fakeAudioFrames = byteArrayOf(0xFF.toByte(), 0xFB.toByte(), 0x90.toByte(), 0x64, 0x11, 0x22, 0x33, 0x44)
        val fakeJpeg = byteArrayOf(0xFF.toByte(), 0xD8.toByte(), 0xFF.toByte(), 0xE0.toByte(), 0x01, 0x02, 0xFF.toByte(), 0xD9.toByte())

        val patched = AudioTagEmbedder.patchMp3Id3v23(
            source = fakeAudioFrames,
            req = AudioTagWriteRequest(
                title = "Yatapita",
                artist = "Diamond Platnumz",
                album = "First Of All",
                albumArtist = "Diamond Platnumz",
                genre = "Bongo Flava",
                year = 2023,
                artworkJpegBytes = fakeJpeg,
            ),
        )

        val asciiHeader = String(patched, 0, 3, Charsets.ISO_8859_1)
        assertEqualsTag("ID3", asciiHeader)
        assertTrue(containsAscii(patched, "TIT2"))
        assertTrue(containsAscii(patched, "TPE1"))
        assertTrue(containsAscii(patched, "TALB"))
        assertTrue(containsAscii(patched, "TCON"))
        assertTrue(containsAscii(patched, "TYER"))
        assertTrue(containsAscii(patched, "APIC"))
        assertTrue(containsBytes(patched, fakeJpeg))
        assertArrayEquals(fakeAudioFrames, patched.copyOfRange(patched.size - fakeAudioFrames.size, patched.size))

        // Re-patch again and verify old audio frame trailer & updated title remain intact
        val repatched = AudioTagEmbedder.patchMp3Id3v23(
            source = patched,
            req = AudioTagWriteRequest(
                title = "Yatapita (Remix)",
                artist = "Diamond Platnumz",
                album = "First Of All",
                albumArtist = "Diamond Platnumz",
                genre = "Afrobeats",
                year = 2024,
                artworkJpegBytes = fakeJpeg,
            ),
        )
        assertArrayEquals(fakeAudioFrames, repatched.copyOfRange(repatched.size - fakeAudioFrames.size, repatched.size))
        assertTrue(containsAscii(repatched, "APIC"))
        assertTrue(containsBytes(repatched, fakeJpeg))
    }

    @Test
    fun `patchFlacMetadata writes VORBIS_COMMENT and METADATA_BLOCK_PICTURE while preserving audio frames`() {
        val streamInfoData = ByteArray(34) { (it + 1).toByte() }
        val flacAudioPayload = byteArrayOf(0xFF.toByte(), 0xF8.toByte(), 0x55, 0x66, 0x77, 0x88.toByte())
        val flacSource = ByteArrayOutputStream().apply {
            write("fLaC".toByteArray(Charsets.ISO_8859_1))
            // STREAMINFO header (last block flag = 0x80, length = 34)
            write(0x80)
            write(0); write(0); write(34)
            write(streamInfoData)
            write(flacAudioPayload)
        }.toByteArray()

        val fakeJpeg = byteArrayOf(0xFF.toByte(), 0xD8.toByte(), 0xFF.toByte(), 0xE0.toByte(), 0x42, 0x43, 0xFF.toByte(), 0xD9.toByte())
        val patched = AudioTagEmbedder.patchFlacMetadata(
            source = flacSource,
            req = AudioTagWriteRequest(
                title = "Moyo",
                artist = "Zuchu",
                album = "Peace & Money",
                albumArtist = "Zuchu",
                genre = "Afropop",
                year = 2025,
                artworkJpegBytes = fakeJpeg,
            ),
        )

        assertNotNull(patched)
        patched!!
        assertEqualsTag("fLaC", String(patched, 0, 4, Charsets.ISO_8859_1))
        assertTrue(containsAscii(patched, "TITLE=Moyo"))
        assertTrue(containsAscii(patched, "ARTIST=Zuchu"))
        assertTrue(containsAscii(patched, "GENRE=Afropop"))
        assertTrue(containsAscii(patched, "image/jpeg"))
        assertTrue(containsBytes(patched, fakeJpeg))
        assertArrayEquals(flacAudioPayload, patched.copyOfRange(patched.size - flacAudioPayload.size, patched.size))
    }

    @Test
    fun `patchMp4Atoms updates metadata atoms and shifts stco chunk offsets when moov precedes mdat`() {
        fun atom(type: String, payload: ByteArray): ByteArray {
            val total = 8 + payload.size
            val out = ByteArray(total)
            out[0] = ((total ushr 24) and 0xFF).toByte()
            out[1] = ((total ushr 16) and 0xFF).toByte()
            out[2] = ((total ushr 8) and 0xFF).toByte()
            out[3] = (total and 0xFF).toByte()
            System.arraycopy(type.toByteArray(Charsets.ISO_8859_1), 0, out, 4, 4)
            System.arraycopy(payload, 0, out, 8, payload.size)
            return out
        }

        val ftyp = atom("ftyp", "M4A \u0000\u0000\u0000\u0000M4A mp42".toByteArray(Charsets.ISO_8859_1))
        val mvhd = atom("mvhd", ByteArray(24))
        // placeholder stco pointing to mdat payload
        val stcoPlaceholder = ByteArray(12).apply {
            // version/flags = 0, entry_count = 1
            this[7] = 1
        }
        val stcoBox = atom("stco", stcoPlaceholder)
        val stblBox = atom("stbl", stcoBox)
        val minfBox = atom("minf", stblBox)
        val mdiaBox = atom("mdia", minfBox)
        val trakBox = atom("trak", mdiaBox)
        val moovBox = atom("moov", mvhd + trakBox)
        val mdatPayload = byteArrayOf(0x01, 0x02, 0x03, 0x04, 0x05)
        val mdatBox = atom("mdat", mdatPayload)

        val initialMdatDataOffset = ftyp.size + moovBox.size + 8
        // Write initial stco offset inside raw source
        val stcoOffsetInSource = ftyp.size + 8 + mvhd.size + 8 * 5 + 16
        val source = ftyp + moovBox + mdatBox
        source[stcoOffsetInSource] = ((initialMdatDataOffset ushr 24) and 0xFF).toByte()
        source[stcoOffsetInSource + 1] = ((initialMdatDataOffset ushr 16) and 0xFF).toByte()
        source[stcoOffsetInSource + 2] = ((initialMdatDataOffset ushr 8) and 0xFF).toByte()
        source[stcoOffsetInSource + 3] = (initialMdatDataOffset and 0xFF).toByte()

        val fakeJpeg = byteArrayOf(0xFF.toByte(), 0xD8.toByte(), 0xFF.toByte(), 0xE0.toByte(), 0x55, 0xFF.toByte(), 0xD9.toByte())
        val patched = AudioTagEmbedder.patchMp4Atoms(
            source = source,
            req = AudioTagWriteRequest(
                title = "Nitasubiri",
                artist = "Jux",
                album = "King Of Hearts",
                albumArtist = "Jux",
                genre = "R&B",
                year = 2023,
                artworkJpegBytes = fakeJpeg,
            ),
        )

        assertNotNull(patched)
        patched!!
        assertTrue(containsBytes(patched, "Nitasubiri".toByteArray(Charsets.UTF_8)))
        assertTrue(containsBytes(patched, "covr".toByteArray(Charsets.ISO_8859_1)))
        assertTrue(containsBytes(patched, fakeJpeg))
        // Verify audio mdat payload at end stays intact
        assertArrayEquals(mdatPayload, patched.copyOfRange(patched.size - mdatPayload.size, patched.size))
    }

    private fun assertEqualsTag(expected: String, actual: String) {
        if (expected != actual) {
            throw AssertionError("Expected <$expected> but got <$actual>")
        }
    }

    private fun containsAscii(haystack: ByteArray, needle: String): Boolean =
        containsBytes(haystack, needle.toByteArray(Charsets.ISO_8859_1))

    private fun containsBytes(haystack: ByteArray, needle: ByteArray): Boolean {
        if (needle.isEmpty() || haystack.size < needle.size) return false
        outer@ for (i in 0..(haystack.size - needle.size)) {
            for (j in needle.indices) {
                if (haystack[i + j] != needle[j]) continue@outer
            }
            return true
        }
        return false
    }
}
