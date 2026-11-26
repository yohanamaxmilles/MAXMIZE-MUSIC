package com.example.data.metadata

import android.app.RecoverableSecurityException
import android.content.Context
import android.content.IntentSender
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Build
import android.os.ParcelFileDescriptor
import android.provider.MediaStore
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.charset.Charset

data class AudioTagWriteRequest(
    val title: String,
    val artist: String,
    val album: String,
    val albumArtist: String,
    val genre: String,
    val year: Int?,
    val artworkJpegBytes: ByteArray? = null,
)

sealed class TagEmbedResult {
    data object Embedded : TagEmbedResult()
    data class NeedsConsent(val intentSender: IntentSender) : TagEmbedResult()
    data class UnsupportedOrFailed(val reason: String) : TagEmbedResult()
}

/**
 * Pure-Kotlin audio tag & cover art embedder supporting MP3 (ID3v2.3), M4A/MP4 (moov.udta.meta.ilst),
 * and FLAC (Vorbis Comment + METADATA_BLOCK_PICTURE). Writes via a verified temp file before updating
 * the target descriptor so audio data is never left half-written.
 */
object AudioTagEmbedder {

    fun loadNormalizedJpegBytes(context: Context, artworkUriString: String?, maxDim: Int = 800): ByteArray? {
        if (artworkUriString.isNullOrBlank()) return null
        return runCatching {
            val uri = Uri.parse(artworkUriString)
            val stream = if (uri.scheme == "file" || uri.scheme.isNullOrBlank()) {
                val path = uri.path ?: return null
                val f = File(path)
                if (!f.exists() || !f.canRead()) return null
                FileInputStream(f)
            } else {
                context.contentResolver.openInputStream(uri) ?: return null
            }
            stream.use { input ->
                val bmp = BitmapFactory.decodeStream(input) ?: return null
                val w = bmp.width
                val h = bmp.height
                val scaled = if (w > maxDim || h > maxDim) {
                    val ratio = minOf(maxDim.toFloat() / w, maxDim.toFloat() / h)
                    Bitmap.createScaledBitmap(bmp, (w * ratio).toInt().coerceAtLeast(1), (h * ratio).toInt().coerceAtLeast(1), true)
                } else {
                    bmp
                }
                val out = ByteArrayOutputStream()
                scaled.compress(Bitmap.CompressFormat.JPEG, 90, out)
                if (scaled !== bmp) scaled.recycle()
                bmp.recycle()
                out.toByteArray()
            }
        }.getOrNull()
    }

    fun embedIntoAudioFile(
        context: Context,
        contentUri: Uri,
        filePath: String,
        mimeType: String,
        request: AudioTagWriteRequest,
    ): TagEmbedResult {
        val ext = filePath.substringAfterLast('.', "").lowercase()
        val format = detectFormat(ext, mimeType)

        // 1. Try direct File r/w first if path is accessible (fast on legacy/app-owned storage)
        if (filePath.isNotBlank() && !filePath.startsWith("/SAF/")) {
            val directFile = File(filePath)
            if (directFile.exists() && directFile.canRead() && directFile.canWrite()) {
                val written = runCatching {
                    val sourceBytes = directFile.readBytes()
                    val patched = patchAudioBytes(sourceBytes, format, request) ?: return@runCatching false
                    val tmp = File(context.cacheDir, "tag_tmp_${System.nanoTime()}")
                    try {
                        tmp.writeBytes(patched)
                        if (tmp.length() < patched.size) return@runCatching false
                        FileOutputStream(directFile, false).use { out ->
                            tmp.inputStream().use { inp -> inp.copyTo(out) }
                            out.fd.sync()
                        }
                        true
                    } finally {
                        tmp.delete()
                    }
                }.getOrDefault(false)
                if (written) return TagEmbedResult.Embedded
            }
        }

        // 2. Write through MediaStore / SAF ParcelFileDescriptor ("rw")
        return try {
            val pfd: ParcelFileDescriptor = context.contentResolver.openFileDescriptor(contentUri, "rw")
                ?: return TagEmbedResult.UnsupportedOrFailed("Cannot open audio file for writing")
            pfd.use { descriptor ->
                val sourceBytes = FileInputStream(descriptor.fileDescriptor).use { it.readBytes() }
                val actualFormat = detectFormatFromHeader(sourceBytes, format)
                val patched = patchAudioBytes(sourceBytes, actualFormat, request)
                    ?: return TagEmbedResult.UnsupportedOrFailed("Unsupported audio container for tag embedding")

                val tmp = File(context.cacheDir, "tag_fd_${System.nanoTime()}")
                try {
                    tmp.writeBytes(patched)
                    FileOutputStream(descriptor.fileDescriptor).use { out ->
                        out.channel.position(0)
                        out.channel.truncate(0)
                        tmp.inputStream().use { inp -> inp.copyTo(out) }
                        out.flush()
                        runCatching { descriptor.fileDescriptor.sync() }
                    }
                } finally {
                    tmp.delete()
                }
            }
            TagEmbedResult.Embedded
        } catch (sec: SecurityException) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                val pending = MediaStore.createWriteRequest(context.contentResolver, listOf(contentUri))
                TagEmbedResult.NeedsConsent(pending.intentSender)
            } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q && sec is RecoverableSecurityException) {
                TagEmbedResult.NeedsConsent(sec.userAction.actionIntent.intentSender)
            } else {
                TagEmbedResult.UnsupportedOrFailed("Write permission denied")
            }
        } catch (t: Throwable) {
            TagEmbedResult.UnsupportedOrFailed(t.localizedMessage ?: "Failed to embed tags")
        }
    }

    internal enum class ContainerFormat { MP3, MP4, FLAC, OTHER }

    private fun detectFormat(ext: String, mime: String): ContainerFormat = when {
        ext == "mp3" || mime.contains("mpeg", ignoreCase = true) -> ContainerFormat.MP3
        ext in setOf("m4a", "mp4", "aac", "alac") || mime.contains("mp4", ignoreCase = true) || mime.contains("m4a", ignoreCase = true) -> ContainerFormat.MP4
        ext == "flac" || mime.contains("flac", ignoreCase = true) -> ContainerFormat.FLAC
        else -> ContainerFormat.OTHER
    }

    private fun detectFormatFromHeader(bytes: ByteArray, hint: ContainerFormat): ContainerFormat {
        if (bytes.size >= 4) {
            if (bytes[0] == 'I'.code.toByte() && bytes[1] == 'D'.code.toByte() && bytes[2] == '3'.code.toByte()) {
                return ContainerFormat.MP3
            }
            if (bytes[0] == 'f'.code.toByte() && bytes[1] == 'L'.code.toByte() &&
                bytes[2] == 'a'.code.toByte() && bytes[3] == 'C'.code.toByte()
            ) {
                return ContainerFormat.FLAC
            }
            if (bytes.size >= 8 && bytes[4] == 'f'.code.toByte() && bytes[5] == 't'.code.toByte() &&
                bytes[6] == 'y'.code.toByte() && bytes[7] == 'p'.code.toByte()
            ) {
                return ContainerFormat.MP4
            }
        }
        return hint
    }

    internal fun patchAudioBytes(
        source: ByteArray,
        formatHint: ContainerFormat,
        request: AudioTagWriteRequest,
    ): ByteArray? {
        val format = detectFormatFromHeader(source, formatHint)
        return when (format) {
            ContainerFormat.MP3 -> patchMp3Id3v23(source, request)
            ContainerFormat.MP4 -> patchMp4Atoms(source, request)
            ContainerFormat.FLAC -> patchFlacMetadata(source, request)
            ContainerFormat.OTHER -> null
        }
    }

    // =========================================================================
    // MP3 ID3v2.3 Writer (preserves non-overwritten frames such as USLT/TRCK)
    // =========================================================================

    private val OVERWRITTEN_ID3_FRAMES = setOf("TIT2", "TPE1", "TPE2", "TALB", "TCON", "TYER", "TDRC", "APIC")

    internal fun patchMp3Id3v23(source: ByteArray, req: AudioTagWriteRequest): ByteArray {
        val preservedFrames = mutableListOf<ByteArray>()
        var audioOffset = 0

        if (source.size >= 10 &&
            source[0] == 'I'.code.toByte() &&
            source[1] == 'D'.code.toByte() &&
            source[2] == '3'.code.toByte()
        ) {
            val major = source[3].toInt() and 0xFF
            val flags = source[5].toInt() and 0xFF
            val tagSize = decodeSynchsafe(source, 6)
            val footerSize = if ((flags and 0x10) != 0) 10 else 0
            audioOffset = (10 + tagSize + footerSize).coerceAtMost(source.size)

            if (major == 3 || major == 4) {
                var pos = 10
                if ((flags and 0x40) != 0 && pos + 4 <= audioOffset) {
                    val extSize = if (major == 4) decodeSynchsafe(source, pos) else readIntBE(source, pos)
                    pos += extSize.coerceAtLeast(4)
                }
                val limit = (10 + tagSize).coerceAtMost(source.size)
                while (pos + 10 <= limit) {
                    val idBytes = source.copyOfRange(pos, pos + 4)
                    if (idBytes.all { it == 0.toByte() }) break
                    val id = String(idBytes, Charsets.ISO_8859_1)
                    val frameSize = if (major == 4) decodeSynchsafe(source, pos + 4) else readIntBE(source, pos + 4)
                    if (frameSize <= 0 || pos + 10 + frameSize > limit) break
                    val shouldKeep = id !in OVERWRITTEN_ID3_FRAMES && id.all { it in 'A'..'Z' || it in '0'..'9' }
                    if (shouldKeep) {
                        val payload = source.copyOfRange(pos + 10, pos + 10 + frameSize)
                        val frameFlags = source.copyOfRange(pos + 8, pos + 10)
                        preservedFrames.add(buildId3v23RawFrame(id, payload, frameFlags))
                    }
                    pos += 10 + frameSize
                }
            }
        }

        val frameOut = ByteArrayOutputStream()
        fun writeTextFrame(id: String, value: String) {
            if (value.isBlank()) return
            val encoded = value.toByteArray(Charset.forName("UTF-16LE"))
            val payload = ByteArrayOutputStream(1 + 2 + encoded.size).apply {
                write(0x01) // UTF-16 with BOM
                write(0xFF)
                write(0xFE)
                write(encoded)
            }.toByteArray()
            frameOut.write(buildId3v23RawFrame(id, payload))
        }

        writeTextFrame("TIT2", req.title)
        writeTextFrame("TPE1", req.artist)
        writeTextFrame("TPE2", req.albumArtist.ifBlank { req.artist })
        writeTextFrame("TALB", req.album)
        if (req.genre.isNotBlank() && !req.genre.equals("Unknown Genre", ignoreCase = true)) {
            writeTextFrame("TCON", req.genre)
        }
        if (req.year != null && req.year > 0) {
            writeTextFrame("TYER", req.year.toString())
        }
        if (req.artworkJpegBytes != null && req.artworkJpegBytes.isNotEmpty()) {
            val mime = "image/jpeg".toByteArray(Charsets.ISO_8859_1)
            val apicPayload = ByteArrayOutputStream(mime.size + 4 + req.artworkJpegBytes.size).apply {
                write(0x00) // ISO-8859-1 description
                write(mime)
                write(0x00) // Null terminator
                write(0x03) // Cover (front)
                write(0x00) // Empty description terminator
                write(req.artworkJpegBytes)
            }.toByteArray()
            frameOut.write(buildId3v23RawFrame("APIC", apicPayload))
        }
        for (kept in preservedFrames) {
            frameOut.write(kept)
        }

        val framesBytes = frameOut.toByteArray()
        val padding = ByteArray(128) // Clean padding tail for fast future edits
        val totalTagBodySize = framesBytes.size + padding.size

        val out = ByteArrayOutputStream(10 + totalTagBodySize + (source.size - audioOffset))
        out.write('I'.code)
        out.write('D'.code)
        out.write('3'.code)
        out.write(3) // ID3v2.3
        out.write(0)
        out.write(0) // Flags
        out.write(encodeSynchsafe(totalTagBodySize))
        out.write(framesBytes)
        out.write(padding)
        out.write(source, audioOffset, source.size - audioOffset)
        return out.toByteArray()
    }

    private fun buildId3v23RawFrame(
        frameId: String,
        payload: ByteArray,
        flags: ByteArray = byteArrayOf(0, 0),
    ): ByteArray {
        val out = ByteArray(10 + payload.size)
        val idBytes = frameId.toByteArray(Charsets.ISO_8859_1)
        System.arraycopy(idBytes, 0, out, 0, 4)
        writeIntBE(out, 4, payload.size)
        out[8] = flags[0]
        out[9] = flags[1]
        System.arraycopy(payload, 0, out, 10, payload.size)
        return out
    }

    // =========================================================================
    // MP4 / M4A Atom Writer (updates moov.udta.meta.ilst & shifts stco/co64)
    // =========================================================================

    private data class Mp4Box(
        val type: String,
        val start: Int,
        val headerSize: Int,
        val end: Int,
    )

    private val MP4_REPLACED_ITEMS = setOf("©nam", "©ART", "aART", "©alb", "©gen", "gnre", "©day", "covr")

    internal fun patchMp4Atoms(source: ByteArray, req: AudioTagWriteRequest): ByteArray? {
        val topBoxes = parseMp4Children(source, 0, source.size)
        val moov = topBoxes.firstOrNull { it.type == "moov" } ?: return null
        val mdat = topBoxes.firstOrNull { it.type == "mdat" }

        val moovChildren = parseMp4Children(source, moov.start + moov.headerSize, moov.end)
        val oldUdta = moovChildren.firstOrNull { it.type == "udta" }
        val otherMoovChildren = ByteArrayOutputStream()
        for (child in moovChildren) {
            if (child.type != "udta") {
                otherMoovChildren.write(source, child.start, child.end - child.start)
            }
        }

        // Preserve any non-overwritten udta children and non-overwritten ilst items
        val keptUdtaChildren = ByteArrayOutputStream()
        val keptIlstItems = ByteArrayOutputStream()
        if (oldUdta != null) {
            val udtaChildren = parseMp4Children(source, oldUdta.start + oldUdta.headerSize, oldUdta.end)
            for (uChild in udtaChildren) {
                if (uChild.type != "meta") {
                    keptUdtaChildren.write(source, uChild.start, uChild.end - uChild.start)
                } else {
                    val metaPayloadStart = uChild.start + uChild.headerSize + 4 // skip version/flags
                    if (metaPayloadStart < uChild.end) {
                        val metaChildren = parseMp4Children(source, metaPayloadStart, uChild.end)
                        for (mChild in metaChildren) {
                            if (mChild.type != "ilst") {
                                keptUdtaChildren // no-op; we rebuild meta below
                            } else {
                                val ilstItems = parseMp4Children(source, mChild.start + mChild.headerSize, mChild.end)
                                for (item in ilstItems) {
                                    if (item.type !in MP4_REPLACED_ITEMS) {
                                        keptIlstItems.write(source, item.start, item.end - item.start)
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        val ilstBody = ByteArrayOutputStream()
        fun writeTextAtom(atomType: String, value: String) {
            if (value.isBlank()) return
            ilstBody.write(buildMp4IlstDataItem(atomType, dataType = 1, data = value.toByteArray(Charsets.UTF_8)))
        }
        writeTextAtom("©nam", req.title)
        writeTextAtom("©ART", req.artist)
        writeTextAtom("aART", req.albumArtist.ifBlank { req.artist })
        writeTextAtom("©alb", req.album)
        if (req.genre.isNotBlank() && !req.genre.equals("Unknown Genre", ignoreCase = true)) {
            writeTextAtom("©gen", req.genre)
        }
        if (req.year != null && req.year > 0) {
            writeTextAtom("©day", req.year.toString())
        }
        if (req.artworkJpegBytes != null && req.artworkJpegBytes.isNotEmpty()) {
            ilstBody.write(buildMp4IlstDataItem("covr", dataType = 13, data = req.artworkJpegBytes))
        }
        ilstBody.write(keptIlstItems.toByteArray())

        val hdlrPayload = ByteArray(25).apply {
            // version/flags (4) + pre_defined (4) + handler_type 'mdir' (4) + manufacturer 'appl' (4) + ...
            "mdir".toByteArray(Charsets.ISO_8859_1).copyInto(this, 8)
            "appl".toByteArray(Charsets.ISO_8859_1).copyInto(this, 12)
        }
        val hdlrBox = buildMp4Box("hdlr", hdlrPayload)
        val ilstBox = buildMp4Box("ilst", ilstBody.toByteArray())
        val metaFullPayload = ByteArrayOutputStream().apply {
            write(ByteArray(4)) // version + flags
            write(hdlrBox)
            write(ilstBox)
        }.toByteArray()
        val metaBox = buildMp4Box("meta", metaFullPayload)
        val udtaPayload = ByteArrayOutputStream().apply {
            write(keptUdtaChildren.toByteArray())
            write(metaBox)
        }.toByteArray()
        val newUdtaBox = buildMp4Box("udta", udtaPayload)

        val newMoovPayload = ByteArrayOutputStream().apply {
            write(otherMoovChildren.toByteArray())
            write(newUdtaBox)
        }.toByteArray()
        var newMoovBox = buildMp4Box("moov", newMoovPayload)

        // If moov appears before mdat, adjust all stco/co64 chunk offsets by delta
        val sizeDelta = newMoovBox.size - (moov.end - moov.start)
        if (mdat != null && moov.start < mdat.start && sizeDelta != 0) {
            newMoovBox = shiftMp4ChunkOffsets(newMoovBox, sizeDelta)
        }

        val out = ByteArrayOutputStream(source.size + sizeDelta.coerceAtLeast(0))
        out.write(source, 0, moov.start)
        out.write(newMoovBox)
        out.write(source, moov.end, source.size - moov.end)
        return out.toByteArray()
    }

    private fun buildMp4IlstDataItem(atomType: String, dataType: Int, data: ByteArray): ByteArray {
        val dataPayload = ByteArrayOutputStream(8 + data.size).apply {
            write(0) // version
            write((dataType shr 16) and 0xFF)
            write((dataType shr 8) and 0xFF)
            write(dataType and 0xFF)
            write(ByteArray(4)) // locale
            write(data)
        }.toByteArray()
        val dataBox = buildMp4Box("data", dataPayload)
        return buildMp4Box(atomType, dataBox)
    }

    private fun buildMp4Box(type: String, payload: ByteArray): ByteArray {
        val total = 8 + payload.size
        val out = ByteArray(total)
        writeIntBE(out, 0, total)
        val typeBytes = type.toByteArray(Charsets.ISO_8859_1)
        System.arraycopy(typeBytes, 0, out, 4, 4)
        System.arraycopy(payload, 0, out, 8, payload.size)
        return out
    }

    private fun parseMp4Children(bytes: ByteArray, from: Int, to: Int): List<Mp4Box> {
        val list = mutableListOf<Mp4Box>()
        var pos = from
        while (pos + 8 <= to) {
            val rawSize = readIntBE(bytes, pos).toLong() and 0xFFFFFFFFL
            val type = String(bytes, pos + 4, 4, Charsets.ISO_8859_1)
            val headerSize: Int
            val totalSize: Long
            if (rawSize == 1L && pos + 16 <= to) {
                headerSize = 16
                totalSize = ByteBuffer.wrap(bytes, pos + 8, 8).order(ByteOrder.BIG_ENDIAN).long
            } else {
                headerSize = 8
                totalSize = if (rawSize == 0L) (to - pos).toLong() else rawSize
            }
            if (totalSize < headerSize || pos + totalSize > to) break
            val end = (pos + totalSize).toInt()
            list.add(Mp4Box(type, pos, headerSize, end))
            pos = end
        }
        return list
    }

    private fun shiftMp4ChunkOffsets(moovBox: ByteArray, delta: Int): ByteArray {
        val result = moovBox.copyOf()
        val containerBoxes = setOf("moov", "trak", "mdia", "minf", "stbl", "edts")
        fun walk(from: Int, to: Int) {
            for (box in parseMp4Children(result, from, to)) {
                when (box.type) {
                    in containerBoxes -> walk(box.start + box.headerSize, box.end)
                    "stco" -> {
                        val base = box.start + box.headerSize
                        if (base + 8 <= box.end) {
                            val count = readIntBE(result, base + 4)
                            for (i in 0 until count) {
                                val offPos = base + 8 + i * 4
                                if (offPos + 4 <= box.end) {
                                    val oldOff = readIntBE(result, offPos)
                                    writeIntBE(result, offPos, oldOff + delta)
                                }
                            }
                        }
                    }
                    "co64" -> {
                        val base = box.start + box.headerSize
                        if (base + 8 <= box.end) {
                            val count = readIntBE(result, base + 4)
                            val bb = ByteBuffer.wrap(result).order(ByteOrder.BIG_ENDIAN)
                            for (i in 0 until count) {
                                val offPos = base + 8 + i * 8
                                if (offPos + 8 <= box.end) {
                                    val oldOff = bb.getLong(offPos)
                                    bb.putLong(offPos, oldOff + delta.toLong())
                                }
                            }
                        }
                    }
                }
            }
        }
        walk(8, result.size)
        return result
    }

    // =========================================================================
    // FLAC Metadata Block Writer (Vorbis Comment + METADATA_BLOCK_PICTURE)
    // =========================================================================

    private val FLAC_REPLACED_VORBIS_KEYS = setOf("TITLE", "ARTIST", "ALBUMARTIST", "ALBUM", "GENRE", "DATE", "YEAR")

    internal fun patchFlacMetadata(source: ByteArray, req: AudioTagWriteRequest): ByteArray? {
        if (source.size < 8 ||
            source[0] != 'f'.code.toByte() || source[1] != 'L'.code.toByte() ||
            source[2] != 'a'.code.toByte() || source[3] != 'C'.code.toByte()
        ) return null

        val keptBlocks = mutableListOf<Pair<Int, ByteArray>>()
        val keptVorbisComments = mutableListOf<String>()
        var vendor = "MaxmizeAudioTagger"
        var pos = 4
        while (pos + 4 <= source.size) {
            val headerByte = source[pos].toInt() and 0xFF
            val isLast = (headerByte and 0x80) != 0
            val blockType = headerByte and 0x7F
            val length = ((source[pos + 1].toInt() and 0xFF) shl 16) or
                ((source[pos + 2].toInt() and 0xFF) shl 8) or
                (source[pos + 3].toInt() and 0xFF)
            pos += 4
            if (pos + length > source.size) return null
            val payload = source.copyOfRange(pos, pos + length)
            pos += length

            when (blockType) {
                4 -> { // VORBIS_COMMENT
                    val parsed = parseVorbisComments(payload)
                    if (parsed.first.isNotBlank()) vendor = parsed.first
                    for (entry in parsed.second) {
                        val key = entry.substringBefore('=').uppercase()
                        if (key !in FLAC_REPLACED_VORBIS_KEYS) {
                            keptVorbisComments.add(entry)
                        }
                    }
                }
                6 -> { // PICTURE (replace when artworkJpegBytes is provided, otherwise keep)
                    if (req.artworkJpegBytes == null || req.artworkJpegBytes.isEmpty()) {
                        keptBlocks.add(blockType to payload)
                    }
                }
                else -> keptBlocks.add(blockType to payload)
            }
            if (isLast) break
        }
        val audioFrameStart = pos

        val vorbisList = buildList {
            add("TITLE=${req.title}")
            add("ARTIST=${req.artist}")
            add("ALBUMARTIST=${req.albumArtist.ifBlank { req.artist }}")
            add("ALBUM=${req.album}")
            if (req.genre.isNotBlank() && !req.genre.equals("Unknown Genre", ignoreCase = true)) {
                add("GENRE=${req.genre}")
            }
            if (req.year != null && req.year > 0) {
                add("DATE=${req.year}")
            }
            addAll(keptVorbisComments)
        }
        val vorbisPayload = buildVorbisCommentPayload(vendor, vorbisList)
        val outBlocks = mutableListOf<Pair<Int, ByteArray>>()
        outBlocks.addAll(keptBlocks)
        outBlocks.add(4 to vorbisPayload)
        if (req.artworkJpegBytes != null && req.artworkJpegBytes.isNotEmpty()) {
            outBlocks.add(6 to buildFlacPicturePayload(req.artworkJpegBytes))
        }

        val out = ByteArrayOutputStream()
        out.write("fLaC".toByteArray(Charsets.ISO_8859_1))
        outBlocks.forEachIndexed { index, (type, payload) ->
            val isLastFlag = if (index == outBlocks.lastIndex) 0x80 else 0x00
            out.write(isLastFlag or (type and 0x7F))
            out.write((payload.size shr 16) and 0xFF)
            out.write((payload.size shr 8) and 0xFF)
            out.write(payload.size and 0xFF)
            out.write(payload)
        }
        out.write(source, audioFrameStart, source.size - audioFrameStart)
        return out.toByteArray()
    }

    private fun parseVorbisComments(payload: ByteArray): Pair<String, List<String>> {
        if (payload.size < 8) return "" to emptyList()
        val bb = ByteBuffer.wrap(payload).order(ByteOrder.LITTLE_ENDIAN)
        val vendorLen = bb.int
        if (vendorLen < 0 || 4 + vendorLen + 4 > payload.size) return "" to emptyList()
        val vendor = String(payload, 4, vendorLen, Charsets.UTF_8)
        bb.position(4 + vendorLen)
        val count = bb.int
        val comments = mutableListOf<String>()
        var cursor = bb.position()
        for (i in 0 until count) {
            if (cursor + 4 > payload.size) break
            val len = ByteBuffer.wrap(payload, cursor, 4).order(ByteOrder.LITTLE_ENDIAN).int
            cursor += 4
            if (len < 0 || cursor + len > payload.size) break
            comments.add(String(payload, cursor, len, Charsets.UTF_8))
            cursor += len
        }
        return vendor to comments
    }

    private fun buildVorbisCommentPayload(vendor: String, comments: List<String>): ByteArray {
        val vendorBytes = vendor.toByteArray(Charsets.UTF_8)
        val out = ByteArrayOutputStream()
        fun writeIntLE(v: Int) {
            val b = ByteBuffer.allocate(4).order(ByteOrder.LITTLE_ENDIAN).putInt(v).array()
            out.write(b)
        }
        writeIntLE(vendorBytes.size)
        out.write(vendorBytes)
        writeIntLE(comments.size)
        for (c in comments) {
            val cb = c.toByteArray(Charsets.UTF_8)
            writeIntLE(cb.size)
            out.write(cb)
        }
        return out.toByteArray()
    }

    private fun buildFlacPicturePayload(jpegBytes: ByteArray): ByteArray {
        val mimeBytes = "image/jpeg".toByteArray(Charsets.ISO_8859_1)
        val dims = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        runCatching { BitmapFactory.decodeByteArray(jpegBytes, 0, jpegBytes.size, dims) }
        val width = dims.outWidth.coerceAtLeast(0)
        val height = dims.outHeight.coerceAtLeast(0)
        val out = ByteArrayOutputStream()
        fun writeBE(v: Int) {
            val b = ByteArray(4)
            writeIntBE(b, 0, v)
            out.write(b)
        }
        writeBE(3) // Picture type: Cover (front)
        writeBE(mimeBytes.size)
        out.write(mimeBytes)
        writeBE(0) // description length
        writeBE(width)
        writeBE(height)
        writeBE(24) // color depth
        writeBE(0) // indexed colors
        writeBE(jpegBytes.size)
        out.write(jpegBytes)
        return out.toByteArray()
    }

    private fun decodeSynchsafe(bytes: ByteArray, offset: Int): Int {
        return ((bytes[offset].toInt() and 0x7F) shl 21) or
            ((bytes[offset + 1].toInt() and 0x7F) shl 14) or
            ((bytes[offset + 2].toInt() and 0x7F) shl 7) or
            (bytes[offset + 3].toInt() and 0x7F)
    }

    private fun encodeSynchsafe(value: Int): ByteArray = byteArrayOf(
        ((value shr 21) and 0x7F).toByte(),
        ((value shr 14) and 0x7F).toByte(),
        ((value shr 7) and 0x7F).toByte(),
        (value and 0x7F).toByte(),
    )

    private fun readIntBE(bytes: ByteArray, offset: Int): Int {
        return ((bytes[offset].toInt() and 0xFF) shl 24) or
            ((bytes[offset + 1].toInt() and 0xFF) shl 16) or
            ((bytes[offset + 2].toInt() and 0xFF) shl 8) or
            (bytes[offset + 3].toInt() and 0xFF)
    }

    private fun writeIntBE(bytes: ByteArray, offset: Int, value: Int) {
        bytes[offset] = ((value ushr 24) and 0xFF).toByte()
        bytes[offset + 1] = ((value ushr 16) and 0xFF).toByte()
        bytes[offset + 2] = ((value ushr 8) and 0xFF).toByte()
        bytes[offset + 3] = (value and 0xFF).toByte()
    }
}
