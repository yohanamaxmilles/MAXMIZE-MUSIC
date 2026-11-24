package com.example.data.lyrics

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.net.Uri
import com.example.data.local.db.LyricsDao
import com.example.data.local.db.LyricsEntity
import com.example.data.local.prefs.UserPreferencesRepository
import com.example.data.model.LyricLine
import com.example.data.model.LyricWord
import com.example.data.model.LyricsResult
import com.example.data.model.LyricsSearchCandidate
import com.example.data.model.LyricsSource
import com.example.data.model.Track
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.charset.Charset

class LyricsRepository(
    private val context: Context,
    private val lyricsDao: LyricsDao,
    private val prefsRepo: UserPreferencesRepository,
) {
    private val httpClient = defaultLyricsHttpClient()
    private val lrcLibAdapter = LrcLibProviderAdapter(httpClient)
    private var cachedApiKey: String = ""
    private val musixmatchAdapter = MusixmatchProviderAdapter({ cachedApiKey }, httpClient)
    private val fallbackAdapter = CleanQueryFallbackAdapter(lrcLibAdapter)

    suspend fun getLyricsForTrack(
        track: Track,
        allowOnlineLookup: Boolean = true,
    ): LyricsResult = withContext(Dispatchers.IO) {
        // 1. Local Room lyrics database (manual, imported, or cached provider download)
        val cached = lyricsDao.getLyricsOnce(track.id)
        if (cached != null && cached.rawText.isNotBlank()) {
            val parsed = LrcParser.parse(cached.rawText)
            val source = runCatching { LyricsSource.valueOf(cached.source) }
                .getOrDefault(LyricsSource.MANUAL_OR_IMPORTED)
            return@withContext LyricsResult(
                trackId = track.id,
                lines = parsed.lines,
                rawText = cached.rawText,
                isSynced = parsed.isSynced,
                hasWordSync = parsed.hasWordSync,
                source = source,
                syncOffsetMs = cached.syncOffsetMs,
                providerName = cached.provider,
            )
        }

        // 2. Embedded audio metadata lyrics (ID3v2 USLT, Vorbis/FLAC LYRICS, MP4 ©lyr)
        val embeddedText = extractEmbeddedLyrics(Uri.parse(track.contentUri))
        if (!embeddedText.isNullOrBlank()) {
            val parsed = LrcParser.parse(embeddedText)
            return@withContext LyricsResult(
                trackId = track.id,
                lines = parsed.lines,
                rawText = embeddedText,
                isSynced = parsed.isSynced,
                hasWordSync = parsed.hasWordSync,
                source = LyricsSource.EMBEDDED_TAG,
                providerName = "Embedded Tag",
            )
        }

        // 3. Sibling local .lrc / .txt sidecar file
        val sidecarText = readSidecarLrc(track.filePath)
        if (!sidecarText.isNullOrBlank()) {
            val parsed = LrcParser.parse(sidecarText)
            return@withContext LyricsResult(
                trackId = track.id,
                lines = parsed.lines,
                rawText = sidecarText,
                isSynced = parsed.isSynced,
                hasWordSync = parsed.hasWordSync,
                source = LyricsSource.SIDECAR_LRC,
                providerName = "Local .lrc",
            )
        }

        // 4. Online providers (LRCLIB -> Musixmatch -> Fallback) when policy allows
        val prefs = prefsRepo.getOnce()
        cachedApiKey = prefs.musixmatchApiKey
        if (allowOnlineLookup && prefs.autoDownloadLyrics && canFetchOnline(prefs.lyricsWifiOnly)) {
            currentCoroutineContext().ensureActive()
            val candidate = fetchFromProviderChain(track)
            currentCoroutineContext().ensureActive()
            if (candidate != null) {
                val chosenRaw = candidate.syncedLyrics?.takeIf { it.isNotBlank() }
                    ?: candidate.plainLyrics.orEmpty()
                if (chosenRaw.isNotBlank()) {
                    val parsed = LrcParser.parse(chosenRaw)
                    val source = when (candidate.provider) {
                        "LRCLIB" -> LyricsSource.ONLINE_LRCLIB
                        "Musixmatch" -> LyricsSource.ONLINE_MUSIXMATCH
                        else -> LyricsSource.ONLINE_FALLBACK
                    }
                    lyricsDao.upsertLyrics(
                        LyricsEntity(
                            trackId = track.id,
                            rawText = chosenRaw,
                            isSynced = parsed.isSynced,
                            source = source.name,
                            provider = candidate.provider,
                            hasWordSync = parsed.hasWordSync,
                        )
                    )
                    return@withContext LyricsResult(
                        trackId = track.id,
                        lines = parsed.lines,
                        rawText = chosenRaw,
                        isSynced = parsed.isSynced,
                        hasWordSync = parsed.hasWordSync,
                        source = source,
                        providerName = candidate.provider,
                    )
                }
            }
        }

        val offlineHint = when {
            !allowOnlineLookup -> null
            !prefs.autoDownloadLyrics -> "Auto-download is disabled in Settings."
            !hasAnyNetwork() -> "Offline: no internet connection for online lyrics lookup."
            prefs.lyricsWifiOnly && !isOnWifi() -> "Waiting for Wi-Fi (Wi-Fi only lyrics downloads enabled)."
            else -> null
        }

        LyricsResult(
            trackId = track.id,
            source = LyricsSource.NONE,
            statusNote = offlineHint,
        )
    }

    suspend fun refreshLyricsForTrack(track: Track): LyricsResult = withContext(Dispatchers.IO) {
        lyricsDao.deleteLyrics(track.id)
        getLyricsForTrack(track, allowOnlineLookup = true)
    }

    suspend fun searchOnlineCandidates(track: Track, query: String?): List<LyricsSearchCandidate> = withContext(Dispatchers.IO) {
        val prefs = prefsRepo.getOnce()
        cachedApiKey = prefs.musixmatchApiKey
        if (!hasAnyNetwork()) return@withContext emptyList()

        val lrcResults = lrcLibAdapter.search(track, queryOverride = query)
        val mxmResults = if (cachedApiKey.isNotBlank() && query.isNullOrBlank()) {
            musixmatchAdapter.search(track, null)
        } else emptyList()

        (lrcResults + mxmResults).sortedByDescending { it.matchScore }
    }

    suspend fun applySearchCandidate(trackId: String, candidate: LyricsSearchCandidate): LyricsResult = withContext(Dispatchers.IO) {
        val rawText = candidate.syncedLyrics?.takeIf { it.isNotBlank() }
            ?: candidate.plainLyrics.orEmpty()
        val parsed = LrcParser.parse(rawText)
        val source = when (candidate.provider) {
            "LRCLIB" -> LyricsSource.ONLINE_LRCLIB
            "Musixmatch" -> LyricsSource.ONLINE_MUSIXMATCH
            else -> LyricsSource.ONLINE_FALLBACK
        }
        lyricsDao.upsertLyrics(
            LyricsEntity(
                trackId = trackId,
                rawText = rawText,
                isSynced = parsed.isSynced,
                source = source.name,
                provider = candidate.provider,
                hasWordSync = parsed.hasWordSync,
            )
        )
        LyricsResult(
            trackId = trackId,
            lines = parsed.lines,
            rawText = rawText,
            isSynced = parsed.isSynced,
            hasWordSync = parsed.hasWordSync,
            source = source,
            providerName = candidate.provider,
        )
    }

    suspend fun updateSyncOffset(trackId: String, offsetMs: Long) = withContext(Dispatchers.IO) {
        val existing = lyricsDao.getLyricsOnce(trackId)
        if (existing != null) {
            lyricsDao.updateSyncOffset(trackId, offsetMs.coerceIn(-15_000L, 15_000L))
        }
    }

    suspend fun batchFetchMissingLyrics(
        tracks: List<Track>,
        onProgress: (done: Int, total: Int, found: Int) -> Unit,
    ): Int = withContext(Dispatchers.IO) {
        val prefs = prefsRepo.getOnce()
        if (!canFetchOnline(prefs.lyricsWifiOnly)) return@withContext 0
        cachedApiKey = prefs.musixmatchApiKey

        val cachedIds = lyricsDao.getCachedTrackIds().toSet()
        val missing = tracks.filter { it.id !in cachedIds }
        var foundCount = 0
        missing.forEachIndexed { idx, track ->
            currentCoroutineContext().ensureActive()
            val res = getLyricsForTrack(track, allowOnlineLookup = true)
            if (res.source != LyricsSource.NONE) foundCount++
            onProgress(idx + 1, missing.size, foundCount)
        }
        foundCount
    }

    suspend fun clearAllLyricsCache() = withContext(Dispatchers.IO) {
        lyricsDao.clearAllLyrics()
    }

    suspend fun saveManualLyrics(trackId: String, rawText: String) = withContext(Dispatchers.IO) {
        val trimmed = rawText.trim()
        if (trimmed.isEmpty()) {
            lyricsDao.deleteLyrics(trackId)
        } else {
            val parsed = LrcParser.parse(trimmed)
            lyricsDao.upsertLyrics(
                LyricsEntity(
                    trackId = trackId,
                    rawText = trimmed,
                    isSynced = parsed.isSynced,
                    source = LyricsSource.MANUAL_OR_IMPORTED.name,
                    provider = "Manual",
                    hasWordSync = parsed.hasWordSync,
                )
            )
        }
    }

    suspend fun importLyricsFromUri(trackId: String, uri: Uri): LyricsResult = withContext(Dispatchers.IO) {
        val rawText = context.contentResolver.openInputStream(uri)?.use { stream ->
            stream.bufferedReader().readText()
        }.orEmpty()
        if (rawText.isNotBlank()) {
            saveManualLyrics(trackId, rawText)
        }
        val parsed = LrcParser.parse(rawText)
        LyricsResult(
            trackId = trackId,
            lines = parsed.lines,
            rawText = rawText,
            isSynced = parsed.isSynced,
            hasWordSync = parsed.hasWordSync,
            source = if (rawText.isNotBlank()) LyricsSource.MANUAL_OR_IMPORTED else LyricsSource.NONE,
            providerName = if (rawText.isNotBlank()) "Imported .LRC" else "",
        )
    }

    suspend fun clearManualLyrics(trackId: String) = withContext(Dispatchers.IO) {
        lyricsDao.deleteLyrics(trackId)
    }

    private suspend fun fetchFromProviderChain(track: Track): LyricsSearchCandidate? {
        val adapters = buildList {
            add(lrcLibAdapter)
            if (cachedApiKey.isNotBlank()) add(musixmatchAdapter)
            add(fallbackAdapter)
        }
        for (adapter in adapters) {
            currentCoroutineContext().ensureActive()
            val candidate = runCatching { adapter.findBestMatch(track) }.getOrNull()
            if (candidate != null) return candidate
        }
        return null
    }

    private fun canFetchOnline(wifiOnly: Boolean): Boolean {
        return if (wifiOnly) isOnWifi() else hasAnyNetwork()
    }

    private fun hasAnyNetwork(): Boolean {
        val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager ?: return false
        val network = cm.activeNetwork ?: return false
        val caps = cm.getNetworkCapabilities(network) ?: return false
        return caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
    }

    private fun isOnWifi(): Boolean {
        val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager ?: return false
        val network = cm.activeNetwork ?: return false
        val caps = cm.getNetworkCapabilities(network) ?: return false
        return caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) ||
            caps.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET)
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

    data class ParsedLyrics(
        val lines: List<LyricLine>,
        val isSynced: Boolean,
        val hasWordSync: Boolean,
    )

    object LrcParser {
        private val timestampRegex = Regex("""\[(\d{1,3}):(\d{2})(?:[.:](\d{1,3}))?]""")
        private val wordTimestampRegex = Regex("""<(\d{1,3}):(\d{2})(?:[.:](\d{1,3}))?>([^<]*)""")
        private val offsetTagRegex = Regex("""^\[offset:\s*([+-]?\d+)\s*]$""", RegexOption.IGNORE_CASE)
        private val metadataTagRegex = Regex("""^\[(ti|ar|al|au|by|offset|re|ve|length):.*]$""", RegexOption.IGNORE_CASE)

        fun parse(rawText: String): ParsedLyrics {
            val syncedEntries = mutableListOf<LyricLine>()
            val plainLines = mutableListOf<LyricLine>()
            var globalOffsetMs = 0L
            var hasWordSync = false

            for (rawLine in rawText.lines()) {
                val line = rawLine.trim()
                if (line.isEmpty()) continue

                val offsetMatch = offsetTagRegex.matchEntire(line)
                if (offsetMatch != null) {
                    globalOffsetMs = offsetMatch.groupValues[1].toLongOrNull() ?: 0L
                    continue
                }
                if (metadataTagRegex.matches(line)) continue

                val matches = timestampRegex.findAll(line).toList()
                if (matches.isNotEmpty()) {
                    val rawContent = timestampRegex.replace(line, "").trim()
                    val words = parseEnhancedWords(rawContent, globalOffsetMs)
                    if (words.isNotEmpty()) hasWordSync = true
                    val cleanText = if (words.isNotEmpty()) {
                        words.joinToString("") { it.text }.trim().ifBlank { "···" }
                    } else {
                        rawContent.ifBlank { "···" }
                    }
                    for (m in matches) {
                        val totalMs = parseTimestampMs(
                            minStr = m.groupValues[1],
                            secStr = m.groupValues[2],
                            fracStr = m.groupValues[3],
                        ) + globalOffsetMs
                        syncedEntries.add(
                            LyricLine(
                                timestampMs = totalMs.coerceAtLeast(0L),
                                text = cleanText,
                                words = words,
                            )
                        )
                    }
                } else {
                    plainLines.add(LyricLine(timestampMs = null, text = line))
                }
            }

            if (syncedEntries.isEmpty()) {
                // Never invent synchronized timestamps for plain lyrics
                return ParsedLyrics(lines = plainLines, isSynced = false, hasWordSync = false)
            }

            // Merge translation lines sharing identical timestamps
            val sorted = syncedEntries.sortedBy { it.timestampMs ?: 0L }
            val merged = mutableListOf<LyricLine>()
            for (entry in sorted) {
                val last = merged.lastOrNull()
                if (last != null && last.timestampMs == entry.timestampMs &&
                    last.translation == null && entry.text != last.text && entry.text != "···"
                ) {
                    merged[merged.lastIndex] = last.copy(translation = entry.text)
                } else {
                    merged.add(entry)
                }
            }

            return ParsedLyrics(lines = merged, isSynced = true, hasWordSync = hasWordSync)
        }

        private fun parseEnhancedWords(content: String, offsetMs: Long): List<LyricWord> {
            val wordMatches = wordTimestampRegex.findAll(content).toList()
            if (wordMatches.isEmpty()) return emptyList()
            return wordMatches.mapNotNull { wm ->
                val wordText = wm.groupValues[4]
                if (wordText.isEmpty()) return@mapNotNull null
                val ts = parseTimestampMs(wm.groupValues[1], wm.groupValues[2], wm.groupValues[3]) + offsetMs
                LyricWord(startMs = ts.coerceAtLeast(0L), text = wordText)
            }
        }

        private fun parseTimestampMs(minStr: String, secStr: String, fracStr: String): Long {
            val min = minStr.toLongOrNull() ?: 0L
            val sec = secStr.toLongOrNull() ?: 0L
            val msPart = when (fracStr.length) {
                1 -> (fracStr.toLongOrNull() ?: 0L) * 100L
                2 -> (fracStr.toLongOrNull() ?: 0L) * 10L
                3 -> fracStr.toLongOrNull() ?: 0L
                else -> 0L
            }
            return min * 60_000L + sec * 1000L + msPart
        }
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
                    val text = String(bytes, cursor, payloadEnd - cursor, charset).trim('\u0000', ' ', '\r', '\n')
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
