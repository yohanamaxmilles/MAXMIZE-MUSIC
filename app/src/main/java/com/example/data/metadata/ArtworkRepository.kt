package com.example.data.metadata

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.media.MediaMetadataRetriever
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.net.Uri
import com.example.data.local.db.ArtworkCacheDao
import com.example.data.local.db.ArtworkCacheEntity
import com.example.data.local.db.MetadataOverrideEntity
import com.example.data.local.db.MetadataUndoEntity
import com.example.data.local.db.TrackDao
import com.example.data.local.prefs.UserPreferencesRepository
import com.example.data.lyrics.TrackMatchValidator
import com.example.data.model.ArtworkSearchCandidate
import com.example.data.model.Track
import com.example.ui.components.invalidateArtworkCaches
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.FileOutputStream

sealed class ArtworkFetchResult {
    data class Applied(
        val localUri: String,
        val candidate: ArtworkSearchCandidate,
        val embeddedToFile: Boolean = false,
    ) : ArtworkFetchResult()
    data class NeedsConfirmation(val candidates: List<ArtworkSearchCandidate>) : ArtworkFetchResult()
    data class Failed(val reason: String) : ArtworkFetchResult()
}

data class SmartTrackFixSuggestion(
    val title: String,
    val artist: String,
    val album: String,
    val genre: String,
    val year: Int?,
    val localArtworkUri: String?,
    val provider: String,
    val confidenceScore: Int,
)

class ArtworkRepository(
    private val context: Context,
    private val artworkCacheDao: ArtworkCacheDao,
    private val trackDao: TrackDao,
    private val prefsRepo: UserPreferencesRepository,
    private val client: OkHttpClient = defaultArtworkHttpClient(),
) {
    private val providers: List<ArtworkProviderAdapter> = listOf(
        ItunesArtworkAdapter(client),
        MusicBrainzCoverArtAdapter(client),
    )

    val failedEntriesFlow: Flow<List<ArtworkCacheEntity>> = artworkCacheDao.observeAllCache()
        .map { list -> list.filter { it.failedAttempts > 0 && it.localFileUri.isBlank() } }
    val allCacheEntriesFlow: Flow<List<ArtworkCacheEntity>> = artworkCacheDao.observeAllCache()

    private fun cacheKeyFor(track: Track): String {
        val cleanAlbum = track.album.trim()
        return if (cleanAlbum.isNotBlank() && !cleanAlbum.equals("Unknown Album", ignoreCase = true)) {
            "${cleanAlbum.lowercase()}::${track.albumArtist.ifBlank { track.artist }.trim().lowercase()}"
        } else {
            "track:${track.id}"
        }
    }

    /**
     * Checks whether a track actually has a readable artwork image on disk or embedded in audio tags,
     * rather than blindly trusting MediaStore's placeholder albumart/<albumId> URI.
     */
    fun hasVerifiableArtwork(track: Track): Boolean {
        val custom = track.customArtworkUri?.takeIf { it.isNotBlank() }
        if (custom != null && canDecodeImageUri(custom)) return true
        val uriStr = track.artworkUri?.takeIf { it.isNotBlank() }
        if (uriStr != null && canDecodeImageUri(uriStr)) return true
        if (track.contentUri.isNotBlank()) {
            val hasEmbedded = runCatching {
                val retriever = MediaMetadataRetriever()
                try {
                    retriever.setDataSource(context, Uri.parse(track.contentUri))
                    retriever.embeddedPicture?.isNotEmpty() == true
                } finally {
                    runCatching { retriever.release() }
                }
            }.getOrDefault(false)
            if (hasEmbedded) return true
        }
        return false
    }

    private fun canDecodeImageUri(uriString: String): Boolean {
        return runCatching {
            val uri = Uri.parse(uriString)
            val stream = if (uri.scheme == "file") {
                val f = File(uri.path ?: return false)
                if (!f.exists() || !f.canRead()) return false
                f.inputStream()
            } else {
                context.contentResolver.openInputStream(uri) ?: return false
            }
            stream.use { input ->
                val opts = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                BitmapFactory.decodeStream(input, null, opts)
                opts.outWidth > 0 && opts.outHeight > 0
            }
        }.getOrDefault(false)
    }

    suspend fun searchCandidates(track: Track): List<ArtworkSearchCandidate> = withContext(Dispatchers.IO) {
        val collected = mutableListOf<ArtworkSearchCandidate>()
        for (provider in providers) {
            runCatching { provider.search(track) }.getOrNull()?.let { collected.addAll(it) }
        }
        collected
            .distinctBy { it.imageUrl }
            .sortedByDescending { it.confidenceScore }
    }

    /**
     * Resolves clean Title, Artist, Album, Genre, Year + downloaded cover art for the Edit Sheet
     * or one-tap Smart Auto-Fix, even when existing tags are missing or messy.
     */
    suspend fun resolveSmartTrackFix(
        track: Track,
        allowNetwork: Boolean = true,
    ): SmartTrackFixSuggestion = withContext(Dispatchers.IO) {
        val (inferredArtist, inferredTitle) = TrackMatchValidator.inferArtistAndTitle(track)
        var resolvedTitle = inferredTitle.ifBlank { track.title }
        var resolvedArtist = inferredArtist.ifBlank {
            MetadataEditor.suggestTagsFromFilename(track)?.artist.orEmpty()
                .ifBlank { track.artist }
        }
        var resolvedAlbum = track.album
        var resolvedGenre = track.genre
        var resolvedYear: Int? = track.year.takeIf { it > 0 }
        var localArtUri: String? = track.customArtworkUri?.takeIf { canDecodeImageUri(it) }
        var providerName = "Filename Parser"
        var confidence = if (inferredArtist.isNotBlank()) 65 else 45

        if (allowNetwork && hasNetworkConnection()) {
            val candidates = searchCandidates(
                track.copy(
                    title = resolvedTitle.ifBlank { track.title },
                    artist = resolvedArtist,
                )
            )
            val best = candidates.firstOrNull { it.confidenceScore >= 50 }
            if (best != null) {
                providerName = best.provider
                confidence = best.confidenceScore
                if (best.title.isNotBlank() && (isMessyOrMissingTitle(track) || best.confidenceScore >= 72)) {
                    resolvedTitle = best.title.trim()
                }
                if (best.artist.isNotBlank() && (isMissingArtist(resolvedArtist) || best.confidenceScore >= 70)) {
                    resolvedArtist = best.artist.trim()
                }
                if (best.album.isNotBlank() && (isMissingAlbum(resolvedAlbum) || best.confidenceScore >= 78)) {
                    resolvedAlbum = best.album.trim()
                }
                if (best.genre.isNotBlank() && isMissingGenre(resolvedGenre)) {
                    resolvedGenre = best.genre.trim()
                }
                if ((best.releaseYear ?: 0) > 0 && (resolvedYear == null || resolvedYear <= 0)) {
                    resolvedYear = best.releaseYear
                }
                if (localArtUri == null || !hasVerifiableArtwork(track)) {
                    downloadAndCacheImage(track.id, best.imageUrl)?.let { downloaded ->
                        localArtUri = downloaded
                    }
                }
            }
        }

        SmartTrackFixSuggestion(
            title = resolvedTitle.ifBlank { track.title },
            artist = resolvedArtist.ifBlank { track.artist },
            album = resolvedAlbum.ifBlank { track.album },
            genre = resolvedGenre.ifBlank { track.genre },
            year = resolvedYear,
            localArtworkUri = localArtUri,
            provider = providerName,
            confidenceScore = confidence,
        )
    }

    suspend fun smartAutoFixAndEmbed(
        track: Track,
        metadataEditor: MetadataEditor,
        allowMediumAutoApply: Boolean = true,
    ): MetadataSaveResult? = withContext(Dispatchers.IO) {
        val suggestion = resolveSmartTrackFix(track, allowNetwork = true)
        val minScore = if (allowMediumAutoApply) 50 else 72
        val metadataChanged = suggestion.title != track.title ||
            suggestion.artist != track.artist ||
            suggestion.album != track.album ||
            suggestion.genre != track.genre ||
            (suggestion.year != null && suggestion.year != track.year)
        val hasNewArt = !suggestion.localArtworkUri.isNullOrBlank() && suggestion.localArtworkUri != track.customArtworkUri

        if (!metadataChanged && !hasNewArt) return@withContext null
        if (suggestion.confidenceScore < minScore && !metadataChanged) return@withContext null

        metadataEditor.saveMetadataEdit(
            track = track,
            newTitle = suggestion.title,
            newArtist = suggestion.artist,
            newAlbum = suggestion.album,
            newGenre = suggestion.genre,
            newYear = suggestion.year,
            newArtworkUri = suggestion.localArtworkUri ?: track.customArtworkUri,
        )
    }

    suspend fun fetchAndApplyForTrack(
        track: Track,
        allowMediumAutoApply: Boolean = false,
    ): ArtworkFetchResult = withContext(Dispatchers.IO) {
        val prefs = prefsRepo.getOnce()
        if (prefs.artworkWifiOnly && !isWifiConnected()) {
            return@withContext ArtworkFetchResult.Failed("Waiting for Wi-Fi connection")
        }

        val candidates = searchCandidates(track)
        if (candidates.isEmpty()) {
            markFailed(track, "No matching artwork found")
            return@withContext ArtworkFetchResult.Failed("No matching artwork found")
        }

        val best = candidates.first()
        val highThreshold = 76
        val mediumThreshold = 50

        return@withContext when {
            best.confidenceScore >= highThreshold || (allowMediumAutoApply && best.confidenceScore >= mediumThreshold) -> {
                val localUri = downloadAndCacheImage(track.id, best.imageUrl)
                if (localUri != null) {
                    val embedded = applyLocalArtworkAndEmbed(track, localUri, best)
                    ArtworkFetchResult.Applied(localUri, best, embedded)
                } else {
                    markFailed(track, "Image download or validation failed")
                    ArtworkFetchResult.Failed("Downloaded image was corrupt or too small")
                }
            }
            best.confidenceScore >= mediumThreshold -> {
                ArtworkFetchResult.NeedsConfirmation(candidates.filter { it.confidenceScore >= mediumThreshold })
            }
            else -> {
                markFailed(track, "Low match confidence (${best.confidenceScore}%)")
                ArtworkFetchResult.Failed("Low match confidence (${best.confidenceScore}%) — skipped to avoid wrong cover")
            }
        }
    }

    suspend fun applyCandidateForTrack(
        track: Track,
        candidate: ArtworkSearchCandidate,
    ): String? = withContext(Dispatchers.IO) {
        val localUri = downloadAndCacheImage(track.id, candidate.imageUrl) ?: return@withContext null
        applyLocalArtworkAndEmbed(track, localUri, candidate)
        localUri
    }

    suspend fun batchFixMissingArtwork(
        tracks: List<Track>,
        metadataEditor: MetadataEditor? = null,
        onProgress: (done: Int, total: Int, applied: Int) -> Unit,
    ): Int = withContext(Dispatchers.IO) {
        val missing = tracks.filter { tr ->
            !hasVerifiableArtwork(tr) ||
                isMissingArtist(tr.artist) ||
                isMissingAlbum(tr.album) ||
                isMissingGenre(tr.genre)
        }
        if (missing.isEmpty()) {
            onProgress(0, 0, 0)
            return@withContext 0
        }
        var appliedCount = 0
        for ((index, track) in missing.withIndex()) {
            currentCoroutineContext().ensureActive()
            val fixed = if (metadataEditor != null) {
                smartAutoFixAndEmbed(track, metadataEditor, allowMediumAutoApply = true) != null
            } else {
                fetchAndApplyForTrack(track, allowMediumAutoApply = false) is ArtworkFetchResult.Applied
            }
            if (fixed) appliedCount++
            onProgress(index + 1, missing.size, appliedCount)
            delay(280L) // Respect API rate limits
        }
        appliedCount
    }

    suspend fun retryFailedArtwork(
        allTracks: List<Track>,
        onProgress: (done: Int, total: Int, applied: Int) -> Unit,
    ): Int = withContext(Dispatchers.IO) {
        val failedIds = artworkCacheDao.getAllOnce()
            .filter { it.failedAttempts > 0 && it.localFileUri.isBlank() }
            .map { it.trackId }
            .toSet()
        val retryTracks = allTracks.filter { it.id in failedIds }
        if (retryTracks.isEmpty()) {
            onProgress(0, 0, 0)
            return@withContext 0
        }
        var applied = 0
        for ((i, track) in retryTracks.withIndex()) {
            currentCoroutineContext().ensureActive()
            val res = fetchAndApplyForTrack(track, allowMediumAutoApply = true)
            if (res is ArtworkFetchResult.Applied) applied++
            onProgress(i + 1, retryTracks.size, applied)
            delay(280L)
        }
        applied
    }

    suspend fun clearArtworkCache() = withContext(Dispatchers.IO) {
        val cacheDir = File(context.filesDir, "artwork_cache")
        if (cacheDir.exists()) {
            cacheDir.deleteRecursively()
        }
        artworkCacheDao.clearAll()
        invalidateArtworkCaches(context)
    }

    private suspend fun applyLocalArtworkAndEmbed(
        track: Track,
        localUri: String,
        candidate: ArtworkSearchCandidate,
    ): Boolean {
        val existing = trackDao.getMetadataOverride(track.id)
        val cleanArtist = (existing?.artist ?: track.artist).let { cur ->
            if (isMissingArtist(cur) && candidate.artist.isNotBlank()) candidate.artist else cur
        }
        val cleanTitle = (existing?.title ?: track.title).let { cur ->
            if (isMessyOrMissingTitle(track) && candidate.title.isNotBlank()) candidate.title else cur
        }
        val cleanAlbum = (existing?.album ?: track.album).let { cur ->
            if (isMissingAlbum(cur) && candidate.album.isNotBlank()) candidate.album else cur
        }
        val cleanGenre = (existing?.genre ?: track.genre).let { cur ->
            if (isMissingGenre(cur) && candidate.genre.isNotBlank()) candidate.genre else cur
        }
        val cleanYear = (existing?.year ?: track.year.takeIf { it > 0 })
            ?: candidate.releaseYear?.takeIf { it > 0 }

        trackDao.insertMetadataUndo(
            MetadataUndoEntity(
                trackId = track.id,
                prevTitle = existing?.title ?: track.title,
                prevArtist = existing?.artist ?: track.artist,
                prevAlbum = existing?.album ?: track.album,
                prevAlbumArtist = existing?.albumArtist ?: track.albumArtist,
                prevGenre = existing?.genre ?: track.genre,
                prevYear = existing?.year ?: track.year,
                prevCustomArtworkUri = existing?.customArtworkUri ?: track.customArtworkUri,
            )
        )
        trackDao.upsertMetadataOverride(
            MetadataOverrideEntity(
                trackId = track.id,
                title = cleanTitle,
                artist = cleanArtist,
                album = cleanAlbum,
                albumArtist = existing?.albumArtist ?: cleanArtist,
                genre = cleanGenre,
                year = cleanYear,
                customArtworkUri = localUri,
            )
        )
        artworkCacheDao.upsert(
            ArtworkCacheEntity(
                albumKey = cacheKeyFor(track),
                trackId = track.id,
                localFileUri = localUri,
                provider = candidate.provider,
                confidenceScore = candidate.confidenceScore,
                failedAttempts = 0,
                updatedAt = System.currentTimeMillis(),
            )
        )

        val jpegBytes = AudioTagEmbedder.loadNormalizedJpegBytes(context, localUri)
        val embedRes = AudioTagEmbedder.embedIntoAudioFile(
            context = context,
            contentUri = Uri.parse(track.contentUri),
            filePath = track.filePath,
            mimeType = track.mimeType,
            request = AudioTagWriteRequest(
                title = cleanTitle,
                artist = cleanArtist,
                album = cleanAlbum,
                albumArtist = cleanArtist,
                genre = cleanGenre,
                year = cleanYear,
                artworkJpegBytes = jpegBytes,
            ),
        )
        invalidateArtworkCaches(context, track.contentUri, track.artworkUri, localUri)
        return embedRes is TagEmbedResult.Embedded
    }

    private suspend fun markFailed(track: Track, reason: String) {
        val key = cacheKeyFor(track)
        val existing = artworkCacheDao.getByAlbumKey(key)
        artworkCacheDao.upsert(
            ArtworkCacheEntity(
                albumKey = key,
                trackId = track.id,
                localFileUri = "",
                provider = reason,
                confidenceScore = 0,
                failedAttempts = (existing?.failedAttempts ?: 0) + 1,
                updatedAt = System.currentTimeMillis(),
            )
        )
    }

    private fun downloadAndCacheImage(trackId: String, imageUrl: String): String? {
        val req = Request.Builder()
            .url(imageUrl)
            .header("User-Agent", "MaxmizeMusic/2.0 (Android Offline Player)")
            .build()
        return runCatching {
            client.newCall(req).execute().use { resp ->
                if (!resp.isSuccessful) return@use null
                val bytes = resp.body?.bytes() ?: return@use null
                if (bytes.size < 1024) return@use null
                val bmp = BitmapFactory.decodeByteArray(bytes, 0, bytes.size) ?: return@use null
                if (bmp.width < 160 || bmp.height < 160) {
                    bmp.recycle()
                    return@use null
                }
                val cacheDir = File(context.filesDir, "artwork_cache").apply { mkdirs() }
                val safeId = trackId.replace(Regex("[^a-zA-Z0-9_-]"), "_")
                val dest = File(cacheDir, "${safeId}_${System.currentTimeMillis()}.jpg")
                FileOutputStream(dest).use { out ->
                    bmp.compress(Bitmap.CompressFormat.JPEG, 92, out)
                }
                bmp.recycle()
                Uri.fromFile(dest).toString()
            }
        }.getOrNull()
    }

    private fun hasNetworkConnection(): Boolean {
        val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager ?: return true
        val net = cm.activeNetwork ?: return false
        val caps = cm.getNetworkCapabilities(net) ?: return false
        return caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
    }

    private fun isWifiConnected(): Boolean {
        val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager ?: return true
        val net = cm.activeNetwork ?: return false
        val caps = cm.getNetworkCapabilities(net) ?: return false
        return caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) ||
            caps.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET)
    }

    private fun isMissingArtist(artist: String): Boolean =
        artist.isBlank() || artist.equals("Unknown Artist", ignoreCase = true) || artist.equals("<unknown>", ignoreCase = true)

    private fun isMissingAlbum(album: String): Boolean =
        album.isBlank() || album.equals("Unknown Album", ignoreCase = true) || album.equals("<unknown>", ignoreCase = true)

    private fun isMissingGenre(genre: String): Boolean =
        genre.isBlank() || genre.equals("Unknown Genre", ignoreCase = true) || genre.equals("Unknown", ignoreCase = true)

    private fun isMessyOrMissingTitle(track: Track): Boolean =
        track.title.contains(" - ") || track.title.contains('_') || track.title.contains("official", ignoreCase = true) ||
            track.title.equals("Untitled Track", ignoreCase = true)
}

