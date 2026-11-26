package com.example.data.lyrics

import com.example.data.model.LyricsSearchCandidate
import com.example.data.model.Track
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.IOException
import java.util.concurrent.TimeUnit
import kotlin.math.abs

sealed class LyricsProviderResult {
    data class Found(val candidate: LyricsSearchCandidate) : LyricsProviderResult()
    data object NotFound : LyricsProviderResult()
    data class Error(val message: String) : LyricsProviderResult()
}

sealed class LyricsSearchOutcome {
    data class Success(val candidates: List<LyricsSearchCandidate>) : LyricsSearchOutcome()
    data class Error(val message: String) : LyricsSearchOutcome()
}

interface LyricsProvider {
    val providerName: String
    suspend fun fetchBestMatch(track: Track): LyricsProviderResult
    suspend fun search(track: Track, queryOverride: String? = null): LyricsSearchOutcome
}

@Serializable
private data class LrcLibRecord(
    val id: Long = 0L,
    val trackName: String = "",
    val artistName: String = "",
    val albumName: String = "",
    val duration: Double = 0.0,
    val instrumental: Boolean = false,
    val plainLyrics: String? = null,
    val syncedLyrics: String? = null,
)

class LrcLibLyricsProvider(
    private val client: OkHttpClient = defaultLyricsHttpClient(),
) : LyricsProvider {
    override val providerName: String = "LRCLIB"

    private val json = Json { ignoreUnknownKeys = true }

    override suspend fun fetchBestMatch(track: Track): LyricsProviderResult = withContext(Dispatchers.IO) {
        val (cleanArtist, cleanTitle) = TrackMatchValidator.inferArtistAndTitle(track)
        if (cleanTitle.isBlank()) return@withContext LyricsProviderResult.NotFound
        val durSec = (track.durationMs / 1000L).toInt()

        var encounteredError: String? = null

        if (cleanArtist.isNotBlank()) {
            val urlBuilder = "https://lrclib.net/api/get".toHttpUrl().newBuilder()
                .addQueryParameter("track_name", cleanTitle)
                .addQueryParameter("artist_name", cleanArtist)
            if (track.album.isNotBlank() && !track.album.equals("Unknown Album", ignoreCase = true)) {
                urlBuilder.addQueryParameter("album_name", track.album)
            }
            if (durSec > 0) {
                urlBuilder.addQueryParameter("duration", durSec.toString())
            }
            when (val exactResult = executeSingleGet(urlBuilder.build().toString())) {
                is HttpResult.Success -> {
                    val record = exactResult.value
                    if (!record.instrumental && (!record.syncedLyrics.isNullOrBlank() || !record.plainLyrics.isNullOrBlank())) {
                        val cand = record.toCandidate(track, providerName)
                        val durCloseEnough = durSec <= 0 || cand.durationSec <= 0 || abs(durSec - cand.durationSec) <= 6
                        if (cand.matchScore >= 60 && cand.hasSyncedLyrics && durCloseEnough) {
                            return@withContext LyricsProviderResult.Found(cand)
                        }
                    }
                }
                is HttpResult.Error -> encounteredError = exactResult.message
                HttpResult.NotFound -> Unit
            }
        }

        when (val searchOutcome = search(track, queryOverride = null)) {
            is LyricsSearchOutcome.Success -> {
                val best = selectBestAutoCandidate(searchOutcome.candidates, track.durationMs)
                if (best != null) LyricsProviderResult.Found(best) else LyricsProviderResult.NotFound
            }
            is LyricsSearchOutcome.Error -> LyricsProviderResult.Error(encounteredError ?: searchOutcome.message)
        }
    }

    internal companion object {
        fun selectBestAutoCandidate(
            candidates: List<LyricsSearchCandidate>,
            trackDurationMs: Long,
        ): LyricsSearchCandidate? {
            if (candidates.isEmpty()) return null
            val durSec = (trackDurationMs / 1000L).toInt()
            if (durSec <= 0) {
                return candidates.firstOrNull { it.matchScore >= 58 }
            }

            fun durationDelta(cand: LyricsSearchCandidate): Int =
                if (cand.durationSec > 0) abs(durSec - cand.durationSec) else 0

            val bestSynced = candidates
                .filter { cand ->
                    if (!cand.hasSyncedLyrics || cand.matchScore < 58) return@filter false
                    val delta = durationDelta(cand)
                    cand.durationSec <= 0 || delta <= 6 || (delta <= 8 && cand.matchScore >= 82)
                }
                .maxWithOrNull(
                    compareBy<LyricsSearchCandidate> { it.matchScore }
                        .thenByDescending { -durationDelta(it) }
                )
            if (bestSynced != null) return bestSynced

            return candidates
                .filter { cand ->
                    !cand.hasSyncedLyrics &&
                        cand.matchScore >= 64 &&
                        (cand.durationSec <= 0 || durationDelta(cand) <= 18)
                }
                .maxByOrNull { it.matchScore }
        }
    }

    override suspend fun search(track: Track, queryOverride: String?): LyricsSearchOutcome = withContext(Dispatchers.IO) {
        val (cleanArtist, cleanTitle) = TrackMatchValidator.inferArtistAndTitle(track)
        val urlBuilder = "https://lrclib.net/api/search".toHttpUrl().newBuilder()
        if (!queryOverride.isNullOrBlank()) {
            urlBuilder.addQueryParameter("q", queryOverride.trim())
        } else if (cleanArtist.isNotBlank()) {
            urlBuilder.addQueryParameter("track_name", cleanTitle)
            urlBuilder.addQueryParameter("artist_name", cleanArtist)
        } else {
            if (cleanTitle.isBlank()) return@withContext LyricsSearchOutcome.Success(emptyList())
            urlBuilder.addQueryParameter("q", cleanTitle)
        }

        val req = Request.Builder()
            .url(urlBuilder.build())
            .header("User-Agent", "MaxmizeMusic/1.0 (Android Offline Player)")
            .build()

        val body = try {
            client.newCall(req).execute().use { resp ->
                when {
                    resp.code == 404 -> return@withContext LyricsSearchOutcome.Success(emptyList())
                    !resp.isSuccessful -> return@withContext LyricsSearchOutcome.Error("LRCLIB returned HTTP ${resp.code}")
                    else -> resp.body?.string().orEmpty()
                }
            }
        } catch (io: IOException) {
            return@withContext LyricsSearchOutcome.Error(io.localizedMessage ?: "Network error contacting LRCLIB")
        }

        val records = runCatching { json.decodeFromString<List<LrcLibRecord>>(body) }
            .getOrElse {
                return@withContext LyricsSearchOutcome.Error("Invalid response from LRCLIB")
            }

        val candidates = records
            .filter { !it.instrumental && (!it.syncedLyrics.isNullOrBlank() || !it.plainLyrics.isNullOrBlank()) }
            .map { it.toCandidate(track, providerName) }
            .sortedWith(
                compareByDescending<LyricsSearchCandidate> { it.hasSyncedLyrics }
                    .thenByDescending { it.matchScore }
            )
        LyricsSearchOutcome.Success(candidates)
    }

    private sealed class HttpResult<out T> {
        data class Success<T>(val value: T) : HttpResult<T>()
        data object NotFound : HttpResult<Nothing>()
        data class Error(val message: String) : HttpResult<Nothing>()
    }

    private fun executeSingleGet(url: String): HttpResult<LrcLibRecord> {
        val req = Request.Builder()
            .url(url)
            .header("User-Agent", "MaxmizeMusic/1.0 (Android Offline Player)")
            .build()
        return try {
            client.newCall(req).execute().use { resp ->
                when {
                    resp.code == 404 -> HttpResult.NotFound
                    !resp.isSuccessful -> HttpResult.Error("LRCLIB returned HTTP ${resp.code}")
                    else -> {
                        val body = resp.body?.string() ?: return HttpResult.NotFound
                        val parsed = runCatching { json.decodeFromString<LrcLibRecord>(body) }.getOrNull()
                            ?: return HttpResult.NotFound
                        HttpResult.Success(parsed)
                    }
                }
            }
        } catch (io: IOException) {
            HttpResult.Error(io.localizedMessage ?: "Network error contacting LRCLIB")
        }
    }

    private fun LrcLibRecord.toCandidate(track: Track, provider: String): LyricsSearchCandidate {
        val hasSynced = !syncedLyrics.isNullOrBlank()
        val durSec = duration.toInt()
        val score = TrackMatchValidator.scoreCandidate(
            track = track,
            candTitle = trackName,
            candArtist = artistName,
            candAlbum = albumName,
            candDurationSec = durSec,
            hasSynced = hasSynced,
        )
        return LyricsSearchCandidate(
            id = "lrclib_$id",
            trackName = trackName,
            artistName = artistName,
            albumName = albumName,
            durationSec = durSec,
            hasSyncedLyrics = hasSynced,
            syncedLyrics = syncedLyrics,
            plainLyrics = plainLyrics,
            provider = provider,
            matchScore = score,
        )
    }
}

/**
 * Authorized Musixmatch REST adapter. Active only when the user supplies a licensed API key in Settings.
 */
class MusixmatchLyricsProvider(
    private val apiKeyProvider: () -> String,
    private val client: OkHttpClient = defaultLyricsHttpClient(),
) : LyricsProvider {
    override val providerName: String = "Musixmatch"
    private val json = Json { ignoreUnknownKeys = true }

    @Serializable
    private data class MxmLyricsResponse(val message: MxmMessage? = null)

    @Serializable
    private data class MxmMessage(val header: MxmHeader? = null, val body: MxmBody? = null)

    @Serializable
    private data class MxmHeader(@SerialName("status_code") val statusCode: Int = 0)

    @Serializable
    private data class MxmBody(val lyrics: MxmLyricsObj? = null, val subtitle: MxmSubtitleObj? = null)

    @Serializable
    private data class MxmLyricsObj(
        @SerialName("lyrics_id") val lyricsId: Long = 0L,
        @SerialName("lyrics_body") val lyricsBody: String = "",
    )

    @Serializable
    private data class MxmSubtitleObj(
        @SerialName("subtitle_id") val subtitleId: Long = 0L,
        @SerialName("subtitle_body") val subtitleBody: String = "",
    )

    override suspend fun fetchBestMatch(track: Track): LyricsProviderResult = withContext(Dispatchers.IO) {
        val apiKey = apiKeyProvider().trim()
        if (apiKey.isBlank()) return@withContext LyricsProviderResult.NotFound

        val (inferredArtist, inferredTitle) = TrackMatchValidator.inferArtistAndTitle(track)
        val cleanTitle = inferredTitle.ifBlank { TrackMatchValidator.cleanTrackTitle(track.title) }
        val cleanArtist = inferredArtist
        if (cleanTitle.isBlank()) return@withContext LyricsProviderResult.NotFound

        val syncedResp = queryEndpoint("matcher.subtitle.get", apiKey, cleanTitle, cleanArtist, "lrc")
        if (syncedResp is MxmResult.Error) {
            return@withContext LyricsProviderResult.Error(syncedResp.message)
        }
        val syncedBody = (syncedResp as? MxmResult.Success)?.response?.message
            ?.takeIf { it.header?.statusCode == 200 }?.body?.subtitle?.subtitleBody
        if (!syncedBody.isNullOrBlank()) {
            return@withContext LyricsProviderResult.Found(
                LyricsSearchCandidate(
                    id = "mxm_sub_${track.id}",
                    trackName = track.title,
                    artistName = track.artist,
                    albumName = track.album,
                    durationSec = (track.durationMs / 1000L).toInt(),
                    hasSyncedLyrics = true,
                    syncedLyrics = syncedBody,
                    plainLyrics = null,
                    provider = providerName,
                    matchScore = 88,
                )
            )
        }

        val plainResp = queryEndpoint("matcher.lyrics.get", apiKey, cleanTitle, cleanArtist, null)
        if (plainResp is MxmResult.Error) {
            return@withContext LyricsProviderResult.Error(plainResp.message)
        }
        val plainBody = (plainResp as? MxmResult.Success)?.response?.message
            ?.takeIf { it.header?.statusCode == 200 }?.body?.lyrics?.lyricsBody
        if (!plainBody.isNullOrBlank()) {
            return@withContext LyricsProviderResult.Found(
                LyricsSearchCandidate(
                    id = "mxm_lyr_${track.id}",
                    trackName = track.title,
                    artistName = track.artist,
                    albumName = track.album,
                    durationSec = (track.durationMs / 1000L).toInt(),
                    hasSyncedLyrics = false,
                    syncedLyrics = null,
                    plainLyrics = plainBody,
                    provider = providerName,
                    matchScore = 76,
                )
            )
        }

        LyricsProviderResult.NotFound
    }

    override suspend fun search(track: Track, queryOverride: String?): LyricsSearchOutcome {
        return when (val best = fetchBestMatch(track)) {
            is LyricsProviderResult.Found -> LyricsSearchOutcome.Success(listOf(best.candidate))
            LyricsProviderResult.NotFound -> LyricsSearchOutcome.Success(emptyList())
            is LyricsProviderResult.Error -> LyricsSearchOutcome.Error(best.message)
        }
    }

    private sealed class MxmResult {
        data class Success(val response: MxmLyricsResponse?) : MxmResult()
        data class Error(val message: String) : MxmResult()
    }

    private fun queryEndpoint(
        endpoint: String,
        apiKey: String,
        title: String,
        artist: String,
        subtitleFormat: String?,
    ): MxmResult {
        val urlBuilder = "https://api.musixmatch.com/ws/1.1/$endpoint".toHttpUrl().newBuilder()
            .addQueryParameter("apikey", apiKey)
            .addQueryParameter("q_track", title)
        if (artist.isNotBlank()) urlBuilder.addQueryParameter("q_artist", artist)
        if (subtitleFormat != null) urlBuilder.addQueryParameter("subtitle_format", subtitleFormat)

        val req = Request.Builder().url(urlBuilder.build()).build()
        return try {
            client.newCall(req).execute().use { resp ->
                if (!resp.isSuccessful) {
                    MxmResult.Error("Musixmatch returned HTTP ${resp.code}")
                } else {
                    val body = resp.body?.string() ?: return MxmResult.Success(null)
                    MxmResult.Success(runCatching { json.decodeFromString<MxmLyricsResponse>(body) }.getOrNull())
                }
            }
        } catch (io: IOException) {
            MxmResult.Error(io.localizedMessage ?: "Network error contacting Musixmatch")
        }
    }
}

class CleanQueryFallbackProvider(
    private val primaryProvider: LrcLibLyricsProvider,
) : LyricsProvider {
    override val providerName: String = "LRCLIB Fallback"

    override suspend fun fetchBestMatch(track: Track): LyricsProviderResult {
        val (inferredArtist, inferredTitle) = TrackMatchValidator.inferArtistAndTitle(track)
        val query = listOf(inferredArtist, inferredTitle).filter { it.isNotBlank() }.joinToString(" ").trim()
        if (query.isBlank()) return LyricsProviderResult.NotFound
        return when (val outcome = primaryProvider.search(track, queryOverride = query)) {
            is LyricsSearchOutcome.Success -> {
                val best = LrcLibLyricsProvider.selectBestAutoCandidate(outcome.candidates, track.durationMs)
                if (best != null) LyricsProviderResult.Found(best) else LyricsProviderResult.NotFound
            }
            is LyricsSearchOutcome.Error -> LyricsProviderResult.Error(outcome.message)
        }
    }

    override suspend fun search(track: Track, queryOverride: String?): LyricsSearchOutcome =
        primaryProvider.search(track, queryOverride)
}

fun defaultLyricsHttpClient(): OkHttpClient = OkHttpClient.Builder()
    .connectTimeout(8, TimeUnit.SECONDS)
    .readTimeout(10, TimeUnit.SECONDS)
    .writeTimeout(8, TimeUnit.SECONDS)
    .build()
