package com.example.data.metadata

import android.content.ContentValues
import android.content.Context
import android.content.IntentSender
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.media.MediaScannerConnection
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import com.example.data.local.db.MetadataOverrideEntity
import com.example.data.local.db.MetadataUndoEntity
import com.example.data.local.db.TrackDao
import com.example.data.lyrics.TrackMatchValidator
import com.example.data.model.DuplicateTrackGroup
import com.example.data.model.MetadataHealthReport
import com.example.data.model.Track
import com.example.ui.components.invalidateArtworkCaches
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream

sealed class MetadataSaveResult {
    data class SavedToAudioFileAndDb(val embeddedCover: Boolean) : MetadataSaveResult()
    data object SavedToMediaStoreAndDb : MetadataSaveResult()
    data object SavedToDbOnly : MetadataSaveResult()
    data class NeedsWriteConsent(val intentSender: IntentSender) : MetadataSaveResult()
}

data class SuggestedTags(
    val title: String,
    val artist: String,
)

class MetadataEditor(
    private val context: Context,
    private val trackDao: TrackDao,
) {
    val undoHistoryFlow: Flow<List<MetadataUndoEntity>> = trackDao.observeUndoHistory()

    suspend fun copyArtworkToInternalStorage(trackId: String, sourceUri: Uri): String? =
        withContext(Dispatchers.IO) {
            runCatching {
                val coversDir = File(context.filesDir, "covers").apply { mkdirs() }
                val safeId = trackId.replace(Regex("[^a-zA-Z0-9_-]"), "_")
                val destFile = File(coversDir, "${safeId}_${System.currentTimeMillis()}.jpg")
                context.contentResolver.openInputStream(sourceUri)?.use { input ->
                    val decoded = BitmapFactory.decodeStream(input) ?: return@withContext null
                    val scaled = scaleBitmapIfNeeded(decoded, 1024)
                    FileOutputStream(destFile).use { out ->
                        scaled.compress(Bitmap.CompressFormat.JPEG, 90, out)
                    }
                    if (scaled !== decoded) decoded.recycle()
                } ?: return@withContext null
                Uri.fromFile(destFile).toString()
            }.getOrNull()
        }

    suspend fun saveMetadataEdit(
        track: Track,
        newTitle: String,
        newArtist: String,
        newAlbum: String,
        newGenre: String,
        newYear: Int?,
        newArtworkUri: String?,
    ): MetadataSaveResult = withContext(Dispatchers.IO) {
        val cleanTitle = newTitle.trim().ifBlank { track.title }
        val cleanArtist = newArtist.trim().ifBlank { track.artist }
        val cleanAlbum = newAlbum.trim().ifBlank { track.album }
        val cleanGenre = newGenre.trim().ifBlank { track.genre }
        val resolvedArtworkUri = newArtworkUri ?: track.customArtworkUri

        // Persist undo snapshot before applying edit
        trackDao.insertMetadataUndo(
            MetadataUndoEntity(
                trackId = track.id,
                prevTitle = track.title,
                prevArtist = track.artist,
                prevAlbum = track.album,
                prevAlbumArtist = track.albumArtist,
                prevGenre = track.genre,
                prevYear = track.year,
                prevCustomArtworkUri = track.customArtworkUri,
            )
        )

        trackDao.upsertMetadataOverride(
            MetadataOverrideEntity(
                trackId = track.id,
                title = cleanTitle,
                artist = cleanArtist,
                album = cleanAlbum,
                albumArtist = cleanArtist,
                genre = cleanGenre,
                year = newYear,
                customArtworkUri = resolvedArtworkUri,
            )
        )

        // Update cached TrackEntity immediately so library reflects new tags & verified cover
        trackDao.getTrackById(track.id)?.let { entity ->
            trackDao.upsertTracks(
                listOf(
                    entity.copy(
                        title = cleanTitle,
                        artist = cleanArtist,
                        album = cleanAlbum,
                        albumArtist = cleanArtist,
                        genre = cleanGenre,
                        year = newYear ?: entity.year,
                        artworkUri = resolvedArtworkUri ?: entity.artworkUri,
                    )
                )
            )
        }

        val uri = Uri.parse(track.contentUri)
        val jpegBytes = AudioTagEmbedder.loadNormalizedJpegBytes(context, resolvedArtworkUri)
        val tagReq = AudioTagWriteRequest(
            title = cleanTitle,
            artist = cleanArtist,
            album = cleanAlbum,
            albumArtist = cleanArtist,
            genre = cleanGenre,
            year = newYear,
            artworkJpegBytes = jpegBytes,
        )

        val embedResult = AudioTagEmbedder.embedIntoAudioFile(
            context = context,
            contentUri = uri,
            filePath = track.filePath,
            mimeType = track.mimeType,
            request = tagReq,
        )

        invalidateArtworkCaches(context, track.contentUri, track.artworkUri, resolvedArtworkUri)

        when (embedResult) {
            is TagEmbedResult.NeedsConsent -> {
                return@withContext MetadataSaveResult.NeedsWriteConsent(embedResult.intentSender)
            }
            is TagEmbedResult.Embedded -> {
                updateMediaStoreColumns(track, uri, cleanTitle, cleanArtist, cleanAlbum, newYear)
                triggerMediaRescan(track.filePath)
                return@withContext MetadataSaveResult.SavedToAudioFileAndDb(
                    embeddedCover = jpegBytes != null && jpegBytes.isNotEmpty(),
                )
            }
            is TagEmbedResult.UnsupportedOrFailed -> {
                if (track.mediaStoreId == null) {
                    return@withContext MetadataSaveResult.SavedToDbOnly
                }
                return@withContext try {
                    val updatedRows = updateMediaStoreColumns(track, uri, cleanTitle, cleanArtist, cleanAlbum, newYear)
                    if (updatedRows > 0) {
                        MetadataSaveResult.SavedToMediaStoreAndDb
                    } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                        val pendingIntent = MediaStore.createWriteRequest(context.contentResolver, listOf(uri))
                        MetadataSaveResult.NeedsWriteConsent(pendingIntent.intentSender)
                    } else {
                        MetadataSaveResult.SavedToDbOnly
                    }
                } catch (sec: SecurityException) {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                        val pendingIntent = MediaStore.createWriteRequest(context.contentResolver, listOf(uri))
                        MetadataSaveResult.NeedsWriteConsent(pendingIntent.intentSender)
                    } else {
                        MetadataSaveResult.SavedToDbOnly
                    }
                } catch (_: Throwable) {
                    MetadataSaveResult.SavedToDbOnly
                }
            }
        }
    }

    suspend fun undoLastEditForTrack(trackId: String): Boolean = withContext(Dispatchers.IO) {
        val latest = trackDao.getLatestUndoForTrack(trackId) ?: return@withContext false
        undoEntry(latest)
        true
    }

    suspend fun undoEntry(entry: MetadataUndoEntity) = withContext(Dispatchers.IO) {
        trackDao.upsertMetadataOverride(
            MetadataOverrideEntity(
                trackId = entry.trackId,
                title = entry.prevTitle,
                artist = entry.prevArtist,
                album = entry.prevAlbum,
                albumArtist = entry.prevAlbumArtist ?: entry.prevArtist,
                genre = entry.prevGenre,
                year = entry.prevYear?.takeIf { it > 0 },
                customArtworkUri = entry.prevCustomArtworkUri,
            )
        )
        trackDao.deleteUndoById(entry.id)
        invalidateArtworkCaches(context)
    }

    suspend fun commitPendingMediaStoreUpdate(
        track: Track,
        title: String,
        artist: String,
        album: String,
        genre: String,
        year: Int?,
        artworkUri: String?,
    ): Boolean = withContext(Dispatchers.IO) {
        val uri = Uri.parse(track.contentUri)
        val cleanTitle = title.trim().ifBlank { track.title }
        val cleanArtist = artist.trim().ifBlank { track.artist }
        val cleanAlbum = album.trim().ifBlank { track.album }
        val cleanGenre = genre.trim().ifBlank { track.genre }
        val resolvedArt = artworkUri ?: track.customArtworkUri

        val jpegBytes = AudioTagEmbedder.loadNormalizedJpegBytes(context, resolvedArt)
        val embedRes = AudioTagEmbedder.embedIntoAudioFile(
            context = context,
            contentUri = uri,
            filePath = track.filePath,
            mimeType = track.mimeType,
            request = AudioTagWriteRequest(
                title = cleanTitle,
                artist = cleanArtist,
                album = cleanAlbum,
                albumArtist = cleanArtist,
                genre = cleanGenre,
                year = year,
                artworkJpegBytes = jpegBytes,
            ),
        )
        if (track.mediaStoreId != null) {
            runCatching { updateMediaStoreColumns(track, uri, cleanTitle, cleanArtist, cleanAlbum, year) }
        }
        triggerMediaRescan(track.filePath)
        invalidateArtworkCaches(context, track.contentUri, track.artworkUri, resolvedArt)
        embedRes is TagEmbedResult.Embedded
    }

    private fun updateMediaStoreColumns(
        track: Track,
        uri: Uri,
        title: String,
        artist: String,
        album: String,
        year: Int?,
    ): Int {
        if (track.mediaStoreId == null) return 0
        val values = ContentValues().apply {
            put(MediaStore.Audio.Media.TITLE, title)
            put(MediaStore.Audio.Media.ARTIST, artist)
            put(MediaStore.Audio.Media.ALBUM, album)
            if (year != null && year > 0) {
                put(MediaStore.Audio.Media.YEAR, year)
            }
        }
        return context.contentResolver.update(uri, values, null, null)
    }

    private fun triggerMediaRescan(filePath: String) {
        if (filePath.isBlank() || filePath.startsWith("/SAF/")) return
        runCatching {
            MediaScannerConnection.scanFile(context, arrayOf(filePath), null, null)
        }
    }

    private fun scaleBitmapIfNeeded(bitmap: Bitmap, maxDim: Int): Bitmap {
        val w = bitmap.width
        val h = bitmap.height
        if (w <= maxDim && h <= maxDim) return bitmap
        val ratio = minOf(maxDim.toFloat() / w, maxDim.toFloat() / h)
        return Bitmap.createScaledBitmap(bitmap, (w * ratio).toInt(), (h * ratio).toInt(), true)
    }

    companion object {
        fun suggestTagsFromFilename(track: Track): SuggestedTags? {
            val baseName = track.filePath.substringAfterLast('/').substringBeforeLast('.').ifBlank { track.title }
            val withSpaces = if (!baseName.contains(' ') && baseName.contains('_')) {
                baseName.replace('_', ' ')
            } else baseName
            val strippedTrackNo = withSpaces.replace(Regex("""^\d{1,3}[\s._-]+"""), "").trim()
            val parts = strippedTrackNo.split(Regex("""\s*[-–—]\s+"""), limit = 2)
                .let { split ->
                    if (split.size == 2) split else strippedTrackNo.split(Regex("""\s+[-–—]\s*"""), limit = 2)
                }
            return if (parts.size == 2 && parts[0].isNotBlank() && parts[1].isNotBlank()) {
                val artist = TrackMatchValidator.cleanArtistName(parts[0]).ifBlank { parts[0].trim() }
                val title = TrackMatchValidator.cleanTrackTitle(parts[1]).ifBlank { parts[1].trim() }
                SuggestedTags(title = title, artist = artist)
            } else {
                val cleaned = TrackMatchValidator.cleanTrackTitle(strippedTrackNo)
                if (cleaned.isNotBlank() && cleaned != track.title) {
                    SuggestedTags(
                        title = cleaned,
                        artist = TrackMatchValidator.cleanArtistName(track.artist).ifBlank { track.artist },
                    )
                } else null
            }
        }

        fun analyzeLibraryHealth(tracks: List<Track>): MetadataHealthReport {
            val missingArtist = tracks.filter {
                it.artist.isBlank() || it.artist.equals("Unknown Artist", ignoreCase = true) ||
                    it.artist.equals("<unknown>", ignoreCase = true)
            }
            val missingAlbum = tracks.filter {
                it.album.isBlank() || it.album.equals("Unknown Album", ignoreCase = true) ||
                    it.album.equals("<unknown>", ignoreCase = true)
            }
            val missingGenre = tracks.filter {
                it.genre.isBlank() || it.genre.equals("Unknown Genre", ignoreCase = true) ||
                    it.genre.equals("Unknown", ignoreCase = true)
            }
            val missingArtwork = tracks.filter {
                it.customArtworkUri.isNullOrBlank() && it.artworkUri.isNullOrBlank()
            }
            val missingYear = tracks.filter { it.year <= 0 }

            val dupGroups = tracks
                .groupBy { t ->
                    val normTitle = TrackMatchValidator.normalizeTokens(TrackMatchValidator.cleanTrackTitle(t.title))
                        .sorted().joinToString(" ")
                    val normArtist = TrackMatchValidator.normalizeTokens(TrackMatchValidator.cleanArtistName(t.artist))
                        .sorted().joinToString(" ")
                    "$normTitle|$normArtist"
                }
                .filter { (key, list) -> key != "|" && list.size >= 2 }
                .map { (key, list) ->
                    val rep = list.first()
                    DuplicateTrackGroup(
                        key = key,
                        title = rep.title,
                        artist = rep.artist,
                        tracks = list,
                    )
                }

            return MetadataHealthReport(
                missingArtistTracks = missingArtist,
                missingAlbumTracks = missingAlbum,
                missingGenreTracks = missingGenre,
                missingArtworkTracks = missingArtwork,
                missingYearTracks = missingYear,
                duplicateGroups = dupGroups,
            )
        }
    }
}

