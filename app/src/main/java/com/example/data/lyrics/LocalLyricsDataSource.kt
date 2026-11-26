package com.example.data.lyrics

import android.content.Context
import android.net.Uri
import com.example.data.local.db.LyricsDao
import com.example.data.local.db.LyricsEntity
import com.example.data.model.LyricsDocument
import com.example.data.model.LyricsSource
import com.example.data.model.Track
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.charset.Charset

/**
 * Local storage data source for lyrics:
 * 1. Room cache (`lyrics_cache`)
 * 2. Embedded audio metadata tags (ID3v2 USLT, Vorbis/FLAC LYRICS, MP4 ©lyr)
 * 3. Sibling `.lrc` / `.txt` files next to audio files
 * 4. SAF URI `.lrc` / `.txt` imports
 */
class LocalLyricsDataSource(
    private val context: Context,
    private val lyricsDao: LyricsDao,
) {

    suspend fun getCachedDocument(trackId: String): LyricsDocument? = withContext(Dispatchers.IO) {
        val cached = lyricsDao.getLyricsOnce(trackId) ?: return@withContext null
        if (cached.rawText.isBlank()) return@withContext null
        val parsed = LrcParser.parse(cached.rawText)
        val source = runCatching { LyricsSource.valueOf(cached.source) }
            .getOrDefault(LyricsSource.MANUAL_OR_IMPORTED)
        LyricsDocument(
            trackId = trackId,
            lines = parsed.lines,
            rawText = cached.rawText,
            isSynced = parsed.isSynced,
            hasWordSync = parsed.hasWordSync,
            source = source,
            syncOffsetMs = cached.syncOffsetMs,
            providerName = cached.provider,
        )
    }

    suspend fun getEmbeddedOrSidecarDocument(track: Track): LyricsDocument? = withContext(Dispatchers.IO) {
        val embeddedText = extractEmbeddedLyrics(Uri.parse(track.contentUri))
        if (!embeddedText.isNullOrBlank()) {
            val parsed = LrcParser.parse(embeddedText)
            return@withContext LyricsDocument(
                trackId = track.id,
                lines = parsed.lines,
                rawText = embeddedText,
                isSynced = parsed.isSynced,
                hasWordSync = parsed.hasWordSync,
                source = LyricsSource.EMBEDDED_TAG,
                providerName = "Embedded Tag",
            )
        }

        val sidecarText = readSidecarLrc(track.filePath)
        if (!sidecarText.isNullOrBlank()) {
            val parsed = LrcParser.parse(sidecarText)
            return@withContext LyricsDocument(
                trackId = track.id,
                lines = parsed.lines,
                rawText = sidecarText,
                isSynced = parsed.isSynced,
                hasWordSync = parsed.hasWordSync,
                source = LyricsSource.SIDECAR_LRC,
                providerName = "Local .lrc",
            )
        }

        null
    }

    suspend fun saveDocument(document: LyricsDocument) = withContext(Dispatchers.IO) {
        lyricsDao.upsertLyrics(
            LyricsEntity(
                trackId = document.trackId,
                rawText = document.rawText,
                isSynced = document.isSynced,
                source = document.source.name,
                syncOffsetMs = document.syncOffsetMs,
                provider = document.providerName,
                hasWordSync = document.hasWordSync,
            )
        )
    }

    suspend fun updateSyncOffset(
        trackId: String,
        offsetMs: Long,
        fallbackDocument: LyricsDocument?,
    ) = withContext(Dispatchers.IO) {
        val clamped = offsetMs.coerceIn(-15_000L, 15_000L)
        val existing = lyricsDao.getLyricsOnce(trackId)
        if (existing != null) {
            lyricsDao.updateSyncOffset(trackId, clamped)
        } else if (fallbackDocument != null && fallbackDocument.rawText.isNotBlank()) {
            saveDocument(fallbackDocument.copy(syncOffsetMs = clamped))
        }
    }

    suspend fun deleteLyrics(trackId: String) = withContext(Dispatchers.IO) {
        lyricsDao.deleteLyrics(trackId)
    }

    suspend fun clearAll() = withContext(Dispatchers.IO) {
        lyricsDao.clearAllLyrics()
    }

    suspend fun getCachedTrackIds(): Set<String> = withContext(Dispatchers.IO) {
        lyricsDao.getCachedTrackIds().toSet()
    }

    suspend fun readUriText(uri: Uri): String? = withContext(Dispatchers.IO) {
        runCatching {
            context.contentResolver.openInputStream(uri)?.use { stream ->
                stream.bufferedReader().readText()
            }
        }.getOrNull()?.takeIf { it.isNotBlank() }
    }

    private fun readSidecarLrc(filePath: String): String? {
        if (filePath.isBlank() || filePath.startsWith("/SAF/")) return null
        return runCatching {
            val audioFile = File(filePath)
            val baseName = audioFile.nameWithoutExtension
            val parent = audioFile.parentFile ?: return null
            val candidates = listOf(
                File(parent, "$baseName.lrc"),
                File(parent, "$baseName.LRC"),
                File(parent, "$baseName.txt"),
            )
            candidates.firstOrNull { it.exists() && it.canRead() }?.readText()
        }.getOrNull()
    }

    private fun extractEmbeddedLyrics(uri: Uri): String? {
        return runCatching {
            context.contentResolver.openInputStream(uri)?.use { stream ->
                val header = ByteArray(256 * 1024)
                val bytesRead = stream.read(header)
                if (bytesRead <= 10) return null
                val slice = header.copyOf(bytesRead)
                parseId3v2Uslt(slice) ?: parseVorbisOrMp4Lyrics(slice)
            }
        }.getOrNull()
    }

    private fun parseId3v2Uslt(bytes: ByteArray): String? {
        if (bytes.size < 10 || bytes[0] != 'I'.code.toByte() || bytes[1] != 'D'.code.toByte() || bytes[2] != '3'.code.toByte()) {
            return null
        }
        val majorVersion = bytes[3].toInt() and 0xFF
        val tagSize = ((bytes[6].toInt() and 0x7F) shl 21) or
            ((bytes[7].toInt() and 0x7F) shl 14) or
            ((bytes[8].toInt() and 0x7F) shl 7) or
            (bytes[9].toInt() and 0x7F)
        val limit = minOf(bytes.size, 10 + tagSize)
        var offset = 10
        while (offset + 10 <= limit) {
            val frameId = String(bytes, offset, 4, Charsets.ISO_8859_1)
            if (frameId.all { it == '\u0000' }) break
            val frameSize = if (majorVersion >= 4) {
                ((bytes[offset + 4].toInt() and 0x7F) shl 21) or
                    ((bytes[offset + 5].toInt() and 0x7F) shl 14) or
                    ((bytes[offset + 6].toInt() and 0x7F) shl 7) or
                    (bytes[offset + 7].toInt() and 0x7F)
            } else {
                ByteBuffer.wrap(bytes, offset + 4, 4).order(ByteOrder.BIG_ENDIAN).int
            }
            if (frameSize <= 0 || offset + 10 + frameSize > limit) break
            if (frameId == "USLT" && frameSize > 4) {
                val payloadStart = offset + 10
                val encodingByte = bytes[payloadStart].toInt() and 0xFF
                val charset: Charset = when (encodingByte) {
                    1 -> Charsets.UTF_16
                    2 -> Charsets.UTF_16BE
                    3 -> Charsets.UTF_8
                    else -> Charsets.ISO_8859_1
                }
                var cursor = payloadStart + 4
                val payloadEnd = payloadStart + frameSize
                if (encodingByte == 1 || encodingByte == 2) {
                    while (cursor + 1 < payloadEnd) {
                        if (bytes[cursor].toInt() == 0 && bytes[cursor + 1].toInt() == 0) {
                            cursor += 2
                            break
                        }
                        cursor += 2
                    }
                } else {
                    while (cursor < payloadEnd) {
                        if (bytes[cursor].toInt() == 0) {
                            cursor += 1
                            break
                        }
                        cursor++
                    }
                }
                if (cursor < payloadEnd) {
                    val text = String(bytes, cursor, payloadEnd - cursor, charset)
                        .trim('\u0000', ' ', '\r', '\n')
                    if (text.isNotBlank()) return text
                }
            }
            offset += 10 + frameSize
        }
        return null
    }

    private fun parseVorbisOrMp4Lyrics(bytes: ByteArray): String? {
        val text = String(bytes, Charsets.UTF_8)
        for (marker in listOf("LYRICS=", "UNSYNCEDLYRICS=", "lyrics=")) {
            val idx = text.indexOf(marker, ignoreCase = false)
            if (idx >= 0) {
                val start = idx + marker.length
                val end = text.indexOf('\u0000', start).takeIf { it > start } ?: minOf(text.length, start + 8000)
                val extracted = text.substring(start, end)
                    .takeWhile { it.code >= 9 }
                    .trim()
                if (extracted.length > 8) return extracted
            }
        }
        return null
    }
}
