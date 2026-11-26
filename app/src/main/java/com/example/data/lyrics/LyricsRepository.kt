package com.example.data.lyrics

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.net.Uri
import com.example.data.local.db.LyricsDao
import com.example.data.local.prefs.UserPreferencesRepository
import com.example.data.model.LyricsDocument
import com.example.data.model.LyricsError
import com.example.data.model.LyricsLookupOutcome
import com.example.data.model.LyricsSearchCandidate
import com.example.data.model.LyricsSource
import com.example.data.model.Track
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import java.util.concurrent.ConcurrentHashMap

/**
 * Coordinates local lyrics sources, online providers, Room persistence,
 * network policy checks, and in-flight request deduplication.
 */
class LyricsRepository(
    private val context: Context,
    lyricsDao: LyricsDao,
    private val prefsRepo: UserPreferencesRepository,
) {
    private val localDataSource = LocalLyricsDataSource(context, lyricsDao)
    private val httpClient = defaultLyricsHttpClient()
    private val lrcLibProvider = LrcLibLyricsProvider(httpClient)

    @Volatile
    private var cachedApiKey: String = ""
    private val musixmatchProvider = MusixmatchLyricsProvider({ cachedApiKey }, httpClient)
    private val fallbackProvider = CleanQueryFallbackProvider(lrcLibProvider)

    private val inFlightLookups = ConcurrentHashMap<String, Deferred<LyricsLookupOutcome>>()

    suspend fun getLyricsForTrack(
        track: Track,
        allowOnlineLookup: Boolean = true,
    ): LyricsLookupOutcome = withContext(Dispatchers.IO) {
        val requestKey = "${track.id}:$allowOnlineLookup"
        val existing = inFlightLookups[requestKey]
        if (existing != null && existing.isActive) {
            return@withContext existing.await()
        }

        val deferred = CompletableDeferred<LyricsLookupOutcome>()
        val raceWinner = inFlightLookups.putIfAbsent(requestKey, deferred)
        if (raceWinner != null && raceWinner.isActive) {
            return@withContext raceWinner.await()
        }

        try {
            val outcome = resolveLyricsForTrack(track, allowOnlineLookup)
            deferred.complete(outcome)
            outcome
        } catch (t: Throwable) {
            deferred.completeExceptionally(t)
            throw t
        } finally {
            inFlightLookups.remove(requestKey, deferred)
        }
    }

    private suspend fun resolveLyricsForTrack(
        track: Track,
        allowOnlineLookup: Boolean,
    ): LyricsLookupOutcome {
        // 1. Cached / manual / previously downloaded document in Room
        localDataSource.getCachedDocument(track.id)?.let { cachedDoc ->
            return LyricsLookupOutcome.Found(cachedDoc)
        }

        // 2. Embedded audio tag or sibling .lrc/.txt file
        localDataSource.getEmbeddedOrSidecarDocument(track)?.let { localDoc ->
            return LyricsLookupOutcome.Found(localDoc)
        }

        // 3. Online providers (when enabled and network conditions allow)
        val prefs = prefsRepo.getOnce()
        cachedApiKey = prefs.musixmatchApiKey

        if (!allowOnlineLookup || !prefs.autoDownloadLyrics) {
            return LyricsLookupOutcome.Empty(
                trackId = track.id,
                reason = if (!prefs.autoDownloadLyrics) "Auto-download is turned off in Settings." else null,
            )
        }

        if (!hasAnyNetwork()) {
            return LyricsLookupOutcome.Failure(
                trackId = track.id,
                error = LyricsError.Offline,
            )
        }

        if (prefs.lyricsWifiOnly && !isOnWifi()) {
            return LyricsLookupOutcome.Failure(
                trackId = track.id,
                error = LyricsError.WifiOnlyBlocked,
            )
        }

        currentCoroutineContext().ensureActive()
        val chainOutcome = fetchFromProviderChain(track)
        currentCoroutineContext().ensureActive()

        return when (chainOutcome) {
            is ChainOutcome.Found -> {
                val cand = chainOutcome.candidate
                val chosenRaw = cand.syncedLyrics?.takeIf { it.isNotBlank() } ?: cand.plainLyrics.orEmpty()
                if (chosenRaw.isBlank()) {
                    LyricsLookupOutcome.Empty(track.id)
                } else {
                    val parsed = LrcParser.parse(chosenRaw)
                    val source = mapProviderToSource(cand.provider)
                    val doc = LyricsDocument(
                        trackId = track.id,
                        lines = parsed.lines,
                        rawText = chosenRaw,
                        isSynced = parsed.isSynced,
                        hasWordSync = parsed.hasWordSync,
                        source = source,
                        providerName = cand.provider,
                    )
                    localDataSource.saveDocument(doc)
                    LyricsLookupOutcome.Found(doc)
                }
            }
            ChainOutcome.NotFound -> LyricsLookupOutcome.Empty(track.id)
            is ChainOutcome.Error -> LyricsLookupOutcome.Failure(
                trackId = track.id,
                error = LyricsError.NetworkFailure(chainOutcome.message),
            )
        }
    }

    suspend fun refreshLyricsForTrack(track: Track): LyricsLookupOutcome = withContext(Dispatchers.IO) {
        localDataSource.deleteLyrics(track.id)
        localDataSource.getEmbeddedOrSidecarDocument(track)?.let { localDoc ->
            return@withContext LyricsLookupOutcome.Found(localDoc)
        }

        val prefs = prefsRepo.getOnce()
        cachedApiKey = prefs.musixmatchApiKey

        if (!hasAnyNetwork()) {
            return@withContext LyricsLookupOutcome.Failure(track.id, LyricsError.Offline)
        }
        if (prefs.lyricsWifiOnly && !isOnWifi()) {
            return@withContext LyricsLookupOutcome.Failure(track.id, LyricsError.WifiOnlyBlocked)
        }

        when (val chainOutcome = fetchFromProviderChain(track)) {
            is ChainOutcome.Found -> {
                val cand = chainOutcome.candidate
                val chosenRaw = cand.syncedLyrics?.takeIf { it.isNotBlank() } ?: cand.plainLyrics.orEmpty()
                if (chosenRaw.isBlank()) {
                    LyricsLookupOutcome.Empty(track.id)
                } else {
                    val parsed = LrcParser.parse(chosenRaw)
                    val doc = LyricsDocument(
                        trackId = track.id,
                        lines = parsed.lines,
                        rawText = chosenRaw,
                        isSynced = parsed.isSynced,
                        hasWordSync = parsed.hasWordSync,
                        source = mapProviderToSource(cand.provider),
                        providerName = cand.provider,
                    )
                    localDataSource.saveDocument(doc)
                    LyricsLookupOutcome.Found(doc)
                }
            }
            ChainOutcome.NotFound -> LyricsLookupOutcome.Empty(track.id)
            is ChainOutcome.Error -> LyricsLookupOutcome.Failure(
                trackId = track.id,
                error = LyricsError.NetworkFailure(chainOutcome.message),
            )
        }
    }

    suspend fun searchOnlineCandidates(
        track: Track,
        query: String?,
    ): Result<List<LyricsSearchCandidate>> = withContext(Dispatchers.IO) {
        val prefs = prefsRepo.getOnce()
        cachedApiKey = prefs.musixmatchApiKey
        if (!hasAnyNetwork()) {
            return@withContext Result.failure(IllegalStateException("Offline: connect to the internet to search lyrics."))
        }

        val lrcOutcome = lrcLibProvider.search(track, queryOverride = query)
        val mxmOutcome = if (cachedApiKey.isNotBlank() && query.isNullOrBlank()) {
            musixmatchProvider.search(track, null)
        } else {
            LyricsSearchOutcome.Success(emptyList())
        }

        val combined = buildList {
            if (lrcOutcome is LyricsSearchOutcome.Success) addAll(lrcOutcome.candidates)
            if (mxmOutcome is LyricsSearchOutcome.Success) addAll(mxmOutcome.candidates)
        }.sortedByDescending { it.matchScore }

        if (combined.isEmpty() && lrcOutcome is LyricsSearchOutcome.Error) {
            return@withContext Result.failure(IllegalStateException(lrcOutcome.message))
        }

        Result.success(combined)
    }

    suspend fun applySearchCandidate(
        trackId: String,
        candidate: LyricsSearchCandidate,
    ): LyricsDocument = withContext(Dispatchers.IO) {
        val rawText = candidate.syncedLyrics?.takeIf { it.isNotBlank() } ?: candidate.plainLyrics.orEmpty()
        val parsed = LrcParser.parse(rawText)
        val doc = LyricsDocument(
            trackId = trackId,
            lines = parsed.lines,
            rawText = rawText,
            isSynced = parsed.isSynced,
            hasWordSync = parsed.hasWordSync,
            source = mapProviderToSource(candidate.provider),
            providerName = candidate.provider,
        )
        localDataSource.saveDocument(doc)
        doc
    }

    suspend fun updateSyncOffset(
        trackId: String,
        offsetMs: Long,
        fallbackDocument: LyricsDocument? = null,
    ) = localDataSource.updateSyncOffset(trackId, offsetMs, fallbackDocument)

    suspend fun saveManualLyrics(trackId: String, rawText: String): LyricsDocument? = withContext(Dispatchers.IO) {
        val trimmed = rawText.trim()
        if (trimmed.isEmpty()) {
            localDataSource.deleteLyrics(trackId)
            return@withContext null
        }
        val parsed = LrcParser.parse(trimmed)
        val doc = LyricsDocument(
            trackId = trackId,
            lines = parsed.lines,
            rawText = trimmed,
            isSynced = parsed.isSynced,
            hasWordSync = parsed.hasWordSync,
            source = LyricsSource.MANUAL_OR_IMPORTED,
            providerName = "Manual",
        )
        localDataSource.saveDocument(doc)
        doc
    }

    suspend fun importLyricsFromUri(trackId: String, uri: Uri): LyricsDocument? = withContext(Dispatchers.IO) {
        val rawText = localDataSource.readUriText(uri) ?: return@withContext null
        val parsed = LrcParser.parse(rawText)
        val doc = LyricsDocument(
            trackId = trackId,
            lines = parsed.lines,
            rawText = rawText.trim(),
            isSynced = parsed.isSynced,
            hasWordSync = parsed.hasWordSync,
            source = LyricsSource.MANUAL_OR_IMPORTED,
            providerName = "Imported .LRC",
        )
        localDataSource.saveDocument(doc)
        doc
    }

    suspend fun clearManualLyrics(trackId: String) {
        localDataSource.deleteLyrics(trackId)
    }

    suspend fun batchFetchMissingLyrics(
        tracks: List<Track>,
        onProgress: (done: Int, total: Int, found: Int) -> Unit,
    ): Int = withContext(Dispatchers.IO) {
        val prefs = prefsRepo.getOnce()
        if (!hasAnyNetwork() || (prefs.lyricsWifiOnly && !isOnWifi())) return@withContext 0
        cachedApiKey = prefs.musixmatchApiKey

        val cachedIds = localDataSource.getCachedTrackIds()
        val missing = tracks.filter { it.id !in cachedIds }
        var foundCount = 0
        missing.forEachIndexed { idx, track ->
            currentCoroutineContext().ensureActive()
            val outcome = getLyricsForTrack(track, allowOnlineLookup = true)
            if (outcome is LyricsLookupOutcome.Found) foundCount++
            onProgress(idx + 1, missing.size, foundCount)
        }
        foundCount
    }

    suspend fun clearAllLyricsCache() {
        localDataSource.clearAll()
    }

    private sealed class ChainOutcome {
        data class Found(val candidate: LyricsSearchCandidate) : ChainOutcome()
        data object NotFound : ChainOutcome()
        data class Error(val message: String) : ChainOutcome()
    }

    private suspend fun fetchFromProviderChain(track: Track): ChainOutcome {
        val providers = buildList {
            add(lrcLibProvider)
            if (cachedApiKey.isNotBlank()) add(musixmatchProvider)
            add(fallbackProvider)
        }

        var lastError: String? = null
        for (provider in providers) {
            currentCoroutineContext().ensureActive()
            when (val res = provider.fetchBestMatch(track)) {
                is LyricsProviderResult.Found -> return ChainOutcome.Found(res.candidate)
                is LyricsProviderResult.Error -> lastError = res.message
                LyricsProviderResult.NotFound -> Unit
            }
        }
        return if (lastError != null) ChainOutcome.Error(lastError) else ChainOutcome.NotFound
    }

    private fun mapProviderToSource(provider: String): LyricsSource = when (provider) {
        "LRCLIB" -> LyricsSource.ONLINE_LRCLIB
        "Musixmatch" -> LyricsSource.ONLINE_MUSIXMATCH
        else -> LyricsSource.ONLINE_FALLBACK
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
}
