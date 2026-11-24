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
import java.text.Normalizer
import java.util.concurrent.TimeUnit
import kotlin.math.abs

interface LyricsProviderAdapter {
    val providerName: String
    suspend fun findBestMatch(track: Track): LyricsSearchCandidate?
    suspend fun search(track: Track, queryOverride: String? = null): List<LyricsSearchCandidate>
}

object TrackMatchValidator {

    fun cleanTrackTitle(raw: String): String {
        return raw
            .replace(
                Regex(
                    """\s*[\[(](official\s*(music\s*|lyric\s*)?(video|audio)|audio|lyric(s)?(\s*video)?|hd|4k|hq|visualizer|mp3)[\])]""",
                    RegexOption.IGNORE_CASE,
                ),
                "",
            )
            .replace(Regex("""\s+(feat\.|ft\.|featuring)\s+.*$""", RegexOption.IGNORE_CASE), "")
            .replace(Regex("""\s*-\s*(official|audio|lyrics).*$""", RegexOption.IGNORE_CASE), "")
            .replace(Regex("""\s+"""), " ")
            .trim()
    }

    fun cleanArtistName(raw: String): String {
        val cleaned = raw
            .replace(Regex("""\s*(,|&|x|feat\.|ft\.|featuring)\s+.*$""", RegexOption.IGNORE_CASE), "")
            .replace(Regex("""\s+"""), " ")
            .trim()
        return if (cleaned.equals("Unknown Artist", ignoreCase = true)) "" else cleaned
    }

    private val VERSION_MARKERS = listOf(
        "live", "remix", "acoustic", "instrumental", "karaoke", "sped up", "slowed", "radio edit", "acapella"
    )

    fun stripDiacritics(input: String): String {
        val norm = Normalizer.normalize(input, Normalizer.Form.NFD)
        return norm.replace(Regex("""\p{InCombiningDiacriticalMarks}+"""), "")
    }

    fun normalizeTokens(text: String): Set<String> {
        return stripDiacritics(text).lowercase()
            .replace(Regex("""[^a-z0-9\s]"""), " ")
            .split(Regex("""\s+"""))
            .filter { it.length > 1 }
            .toSet()
    }

    fun scoreCandidate(
        track: Track,
        candTitle: String,
        candArtist: String,
        candAlbum: String,
        candDurationSec: Int,
        hasSynced: Boolean,
    ): Int {
        val cleanTitle = cleanTrackTitle(track.title)
        val cleanCandTitle = cleanTrackTitle(candTitle)
        if (cleanTitle.isBlank() || cleanCandTitle.isBlank()) return 0

        var score = 0
        val titleTokensA = normalizeTokens(cleanTitle)
        val titleTokensB = normalizeTokens(cleanCandTitle)
        if (stripDiacritics(cleanTitle).equals(stripDiacritics(cleanCandTitle), ignoreCase = true)) {
            score += 48
        } else if (titleTokensA.isNotEmpty() && titleTokensB.isNotEmpty()) {
            val overlap = titleTokensA.intersect(titleTokensB).size.toFloat()
            val union = titleTokensA.union(titleTokensB).size.coerceAtLeast(1).toFloat()
            score += ((overlap / union) * 42f).toInt()
        }

        val cleanArtist = cleanArtistName(track.artist)
        val cleanCandArtist = cleanArtistName(candArtist)
        if (cleanArtist.isNotBlank() && cleanCandArtist.isNotBlank()) {
            val artistA = normalizeTokens(cleanArtist)
            val artistB = normalizeTokens(cleanCandArtist)
            if (stripDiacritics(cleanArtist).equals(stripDiacritics(cleanCandArtist), ignoreCase = true)) {
                score += 32
            } else if (artistA.isNotEmpty() && artistB.isNotEmpty()) {
                val overlap = artistA.intersect(artistB).size.toFloat()
                val union = artistA.union(artistB).size.coerceAtLeast(1).toFloat()
                score += ((overlap / union) * 28f).toInt()
            }
        } else if (cleanArtist.isBlank()) {
            score += 12
        }

        val trackDurSec = (track.durationMs / 1000L).toInt()
        if (trackDurSec > 0 && candDurationSec > 0) {
            val diff = abs(trackDurSec - candDurationSec)
            score += when {
                diff <= 2 -> 20
                diff <= 5 -> 14
                diff <= 10 -> 6
                diff <= 20 -> -8
                else -> -25
            }
        }

        if (track.album.isNotBlank() && !track.album.equals("Unknown Album", ignoreCase = true) &&
            stripDiacritics(candAlbum).equals(stripDiacritics(track.album), ignoreCase = true)
        ) {
            score += 6
        }

        // Reject or penalize mismatched alternate versions (e.g., studio track vs live/karaoke/sped up)
        val lowTrack = stripDiacritics(track.title).lowercase()
        val lowCand = stripDiacritics(candTitle).lowercase()
        for (marker in VERSION_MARKERS) {
            if (lowTrack.contains(marker) != lowCand.contains(marker)) {
                score -= 22
                break
            }
        }

        if (hasSynced) score += 5
        return score.coerceIn(0, 100)
    }
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

class LrcLibProviderAdapter(
    private val client: OkHttpClient = defaultLyricsHttpClient(),
) : LyricsProviderAdapter {
    override val providerName: String = "LRCLIB"

    private val json = Json { ignoreUnknownKeys = true }

    override suspend fun findBestMatch(track: Track): LyricsSearchCandidate? = withContext(Dispatchers.IO) {
        val cleanTitle = TrackMatchValidator.cleanTrackTitle(track.title)
        val cleanArtist = TrackMatchValidator.cleanArtistName(track.artist)
        if (cleanTitle.isBlank()) return@withContext null

        // 1. Exact endpoint lookup when artist & duration are known
        if (cleanArtist.isNotBlank()) {
            val durSec = (track.durationMs / 1000L).toInt()
            val urlBuilder = "https://lrclib.net/api/get".toHttpUrl().newBuilder()
                .addQueryParameter("track_name", cleanTitle)
                .addQueryParameter("artist_name", cleanArtist)
            if (track.album.isNotBlank() && !track.album.equals("Unknown Album", ignoreCase = true)) {
                urlBuilder.addQueryParameter("album_name", track.album)
            }
            if (durSec > 0) {
                urlBuilder.addQueryParameter("duration", durSec.toString())
            }
            val exactRecord = executeSingleGet(urlBuilder.build().toString())
            if (exactRecord != null && !exactRecord.instrumental &&
                (!exactRecord.syncedLyrics.isNullOrBlank() || !exactRecord.plainLyrics.isNullOrBlank())
            ) {
                val cand = exactRecord.toCandidate(track, providerName)
                if (cand.matchScore >= 55) return@withContext cand
            }
        }

        // 2. Fallback to search endpoint & strict validation
        val candidates = search(track, queryOverride = null)
        candidates.firstOrNull { it.matchScore >= 58 }
    }

    override suspend fun search(track: Track, queryOverride: String?): List<LyricsSearchCandidate> = withContext(Dispatchers.IO) {
        val cleanTitle = TrackMatchValidator.cleanTrackTitle(track.title)
        val cleanArtist = TrackMatchValidator.cleanArtistName(track.artist)
        val urlBuilder = "https://lrclib.net/api/search".toHttpUrl().newBuilder()
        if (!queryOverride.isNullOrBlank()) {
            urlBuilder.addQueryParameter("q", queryOverride.trim())
        } else if (cleanArtist.isNotBlank()) {
            urlBuilder.addQueryParameter("track_name", cleanTitle)
            urlBuilder.addQueryParameter("artist_name", cleanArtist)
        } else {
            urlBuilder.addQueryParameter("q", cleanTitle)
        }

        val req = Request.Builder()
            .url(urlBuilder.build())
            .header("User-Agent", "MaxmizeMusic/1.0 (Android Offline Player)")
            .build()

        val body = runCatching {
            client.newCall(req).execute().use { resp ->
                if (!resp.isSuccessful) return@use null
                resp.body?.string()
            }
        }.getOrNull() ?: return@withContext emptyList()

        val records = runCatching { json.decodeFromString<List<LrcLibRecord>>(body) }.getOrNull().orEmpty()
        records
            .filter { !it.instrumental && (!it.syncedLyrics.isNullOrBlank() || !it.plainLyrics.isNullOrBlank()) }
            .map { it.toCandidate(track, providerName) }
            .sortedByDescending { it.matchScore }
    }

    private fun executeSingleGet(url: String): LrcLibRecord? {
        val req = Request.Builder()
            .url(url)
            .header("User-Agent", "MaxmizeMusic/1.0 (Android Offline Player)")
            .build()
        return runCatching {
            client.newCall(req).execute().use { resp ->
                if (!resp.isSuccessful) return@use null
                val body = resp.body?.string() ?: return@use null
                json.decodeFromString<LrcLibRecord>(body)
            }
        }.getOrNull()
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
 * Authorized Musixmatch REST adapter. Only active when a licensed user-supplied Musixmatch API key
 * is configured in Settings -> Lyrics Engine. Never scrapes or bypasses licensing.
 */
class MusixmatchProviderAdapter(
    private val apiKeyProvider: () -> String,
    private val client: OkHttpClient = defaultLyricsHttpClient(),
) : LyricsProviderAdapter {
    override val providerName: String = "Musixmatch"
    private val json = Json { ignoreUnknownKeys = true }

    @Serializable
    private data class MxmLyricsResponse(
        val message: MxmMessage? = null,
    )

    @Serializable
    private data class MxmMessage(
        val header: MxmHeader? = null,
        val body: MxmBody? = null,
    )

    @Serializable
    private data class MxmHeader(
        @SerialName("status_code") val statusCode: Int = 0,
    )

    @Serializable
    private data class MxmBody(
        val lyrics: MxmLyricsObj? = null,
        val subtitle: MxmSubtitleObj? = null,
    )

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

    override suspend fun findBestMatch(track: Track): LyricsSearchCandidate? = withContext(Dispatchers.IO) {
        val apiKey = apiKeyProvider().trim()
        if (apiKey.isBlank()) return@withContext null

        val cleanTitle = TrackMatchValidator.cleanTrackTitle(track.title)
        val cleanArtist = TrackMatchValidator.cleanArtistName(track.artist)
        if (cleanTitle.isBlank()) return@withContext null

        // Try synced subtitle first if licensed on key, then fallback to plain matcher.lyrics.get
        val syncedBody = queryEndpoint("matcher.subtitle.get", apiKey, cleanTitle, cleanArtist, "lrc")
            ?.message?.takeIf { it.header?.statusCode == 200 }?.body?.subtitle?.subtitleBody
        if (!syncedBody.isNullOrBlank()) {
            return@withContext LyricsSearchCandidate(
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
        }

        val plainBody = queryEndpoint("matcher.lyrics.get", apiKey, cleanTitle, cleanArtist, null)
            ?.message?.takeIf { it.header?.statusCode == 200 }?.body?.lyrics?.lyricsBody
        if (!plainBody.isNullOrBlank()) {
            return@withContext LyricsSearchCandidate(
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
        }
        null
    }

    override suspend fun search(track: Track, queryOverride: String?): List<LyricsSearchCandidate> {
        val best = findBestMatch(track)
        return if (best != null) listOf(best) else emptyList()
    }

    private fun queryEndpoint(
        endpoint: String,
        apiKey: String,
        title: String,
        artist: String,
        subtitleFormat: String?,
    ): MxmLyricsResponse? {
        val urlBuilder = "https://api.musixmatch.com/ws/1.1/$endpoint".toHttpUrl().newBuilder()
            .addQueryParameter("apikey", apiKey)
            .addQueryParameter("q_track", title)
        if (artist.isNotBlank()) urlBuilder.addQueryParameter("q_artist", artist)
        if (subtitleFormat != null) urlBuilder.addQueryParameter("subtitle_format", subtitleFormat)

        val req = Request.Builder().url(urlBuilder.build()).build()
        val body = runCatching {
            client.newCall(req).execute().use { resp ->
                if (!resp.isSuccessful) return@use null
                resp.body?.string()
            }
        }.getOrNull() ?: return null
        return runCatching { json.decodeFromString<MxmLyricsResponse>(body) }.getOrNull()
    }
}

/**
 * Fallback normalized-query adapter for local filenames with dashes or missing artist tags.
 */
class CleanQueryFallbackAdapter(
    private val primarySearch: LrcLibProviderAdapter,
) : LyricsProviderAdapter {
    override val providerName: String = "LRCLIB Fallback"

    override suspend fun findBestMatch(track: Track): LyricsSearchCandidate? {
        // Handle "Artist - Title" stored inside track.title when artist tag is Unknown Artist
        val parts = track.title.split(" - ", limit = 2)
        val query = if (parts.size == 2) {
            "${TrackMatchValidator.cleanTrackTitle(parts[0])} ${TrackMatchValidator.cleanTrackTitle(parts[1])}"
        } else {
            TrackMatchValidator.cleanTrackTitle(track.title)
        }
        if (query.isBlank()) return null
        val candidates = primarySearch.search(track, queryOverride = query)
        return candidates.firstOrNull { it.matchScore >= 60 }
    }

    override suspend fun search(track: Track, queryOverride: String?): List<LyricsSearchCandidate> =
        primarySearch.search(track, queryOverride)
}

fun defaultLyricsHttpClient(): OkHttpClient = OkHttpClient.Builder()
    .connectTimeout(8, TimeUnit.SECONDS)
    .readTimeout(10, TimeUnit.SECONDS)
    .writeTimeout(8, TimeUnit.SECONDS)
    .build()
