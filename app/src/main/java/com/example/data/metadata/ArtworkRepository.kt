package com.example.data.metadata

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.net.Uri
import com.example.data.local.db.ArtworkCacheDao
import com.example.data.local.db.ArtworkCacheEntity
import com.example.data.local.db.MetadataOverrideEntity
import com.example.data.local.db.MetadataUndoEntity
import com.example.data.local.db.TrackDao
import com.example.data.local.prefs.UserPreferencesRepository
import com.example.data.model.ArtworkSearchCandidate
import com.example.data.model.Track
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
    data class Applied(val localUri: String, val candidate: ArtworkSearchCandidate) : ArtworkFetchResult()
    data class NeedsConfirmation(val candidates: List<ArtworkSearchCandidate>) : ArtworkFetchResult()
    data class Failed(val reason: String) : ArtworkFetchResult()
}

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

    suspend fun searchCandidates(track: Track): List<ArtworkSearchCandidate> = withContext(Dispatchers.IO) {
        val collected = mutableListOf<ArtworkSearchCandidate>()
        for (provider in providers) {
            runCatching { provider.search(track) }.getOrNull()?.let { collected.addAll(it) }
        }
        collected
            .distinctBy { it.imageUrl }
            .sortedByDescending { it.confidenceScore }
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
        val highThreshold = 80
        val mediumThreshold = 52

        return@withContext when {
            best.confidenceScore >= highThreshold || (allowMediumAutoApply && best.confidenceScore >= mediumThreshold) -> {
                val localUri = downloadAndCacheImage(track.id, best.imageUrl)
                if (localUri != null) {
                    applyLocalArtwork(track, localUri, best)
                    ArtworkFetchResult.Applied(localUri, best)
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
        applyLocalArtwork(track, localUri, candidate)
        localUri
    }

    suspend fun batchFixMissingArtwork(
        tracks: List<Track>,
        onProgress: (done: Int, total: Int, applied: Int) -> Unit,
    ): Int = withContext(Dispatchers.IO) {
        val missing = tracks.filter { it.customArtworkUri.isNullOrBlank() && it.artworkUri.isNullOrBlank() }
        if (missing.isEmpty()) {
            onProgress(0, 0, 0)
            return@withContext 0
        }
        var appliedCount = 0
        for ((index, track) in missing.withIndex()) {
            currentCoroutineContext().ensureActive()
            val res = fetchAndApplyForTrack(track, allowMediumAutoApply = false)
            if (res is ArtworkFetchResult.Applied) appliedCount++
            onProgress(index + 1, missing.size, appliedCount)
            delay(300L) // Respect API rate limits
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
            val res = fetchAndApplyForTrack(track, allowMediumAutoApply = false)
            if (res is ArtworkFetchResult.Applied) applied++
            onProgress(i + 1, retryTracks.size, applied)
            delay(300L)
        }
        applied
    }

    suspend fun clearArtworkCache() = withContext(Dispatchers.IO) {
        val cacheDir = File(context.filesDir, "artwork_cache")
        if (cacheDir.exists()) {
            cacheDir.deleteRecursively()
        }
        artworkCacheDao.clearAll()
    }

    private suspend fun applyLocalArtwork(
        track: Track,
        localUri: String,
        candidate: ArtworkSearchCandidate,
    ) {
        val existing = trackDao.getMetadataOverride(track.id)
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
                title = existing?.title ?: track.title,
                artist = existing?.artist ?: track.artist,
                album = existing?.album ?: track.album,
                albumArtist = existing?.albumArtist ?: track.albumArtist,
                genre = existing?.genre ?: track.genre,
                year = existing?.year ?: track.year.takeIf { it > 0 },
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
                val dest = File(cacheDir, "${trackId.replace(Regex("[^a-zA-Z0-9_-]"), "_")}.jpg")
                FileOutputStream(dest).use { out ->
                    bmp.compress(Bitmap.CompressFormat.JPEG, 92, out)
                }
                bmp.recycle()
                Uri.fromFile(dest).toString()
            }
        }.getOrNull()
    }

    private fun isWifiConnected(): Boolean {
        val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager ?: return true
        val net = cm.activeNetwork ?: return false
        val caps = cm.getNetworkCapabilities(net) ?: return false
        return caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) ||
            caps.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET)
    }
}
