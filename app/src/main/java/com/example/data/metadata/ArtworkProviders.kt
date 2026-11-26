package com.example.data.metadata

import com.example.data.lyrics.TrackMatchValidator
import com.example.data.model.ArtworkSearchCandidate
import com.example.data.model.Track
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import java.util.concurrent.TimeUnit
import kotlin.math.abs

interface ArtworkProviderAdapter {
    val providerName: String
    suspend fun search(track: Track): List<ArtworkSearchCandidate>
}

object ArtworkMatchValidator {

    private val VERSION_MARKERS = listOf(
        "live", "remix", "acoustic", "instrumental", "karaoke", "sped up", "slowed", "radio edit",
    )

    fun scoreCandidate(
        track: Track,
        candTitle: String,
        candArtist: String,
        candAlbum: String,
        candYear: Int?,
        candDurationMs: Long?,
    ): Int {
        val (inferredArtist, inferredTitle) = TrackMatchValidator.inferArtistAndTitle(track)
        val cleanTitle = inferredTitle.ifBlank { TrackMatchValidator.cleanTrackTitle(track.title) }
        val cleanCandTitle = TrackMatchValidator.cleanTrackTitle(candTitle)
        if (cleanTitle.isBlank() && cleanCandTitle.isBlank()) return 0

        var score = 0
        val titleA = TrackMatchValidator.normalizeTokens(cleanTitle)
        val titleB = TrackMatchValidator.normalizeTokens(cleanCandTitle)
        if (cleanTitle.equals(cleanCandTitle, ignoreCase = true)) {
            score += 46
        } else if (titleA.isNotEmpty() && titleB.isNotEmpty()) {
            val inter = titleA.intersect(titleB).size.toFloat()
            val union = titleA.union(titleB).size.coerceAtLeast(1).toFloat()
            score += ((inter / union) * 40f).toInt()
        }

        val cleanArtist = inferredArtist
        val cleanCandArtist = TrackMatchValidator.cleanArtistName(candArtist)
        if (cleanArtist.isNotBlank() && cleanCandArtist.isNotBlank()) {
            val artA = TrackMatchValidator.normalizeTokens(cleanArtist)
            val artB = TrackMatchValidator.normalizeTokens(cleanCandArtist)
            if (cleanArtist.equals(cleanCandArtist, ignoreCase = true)) {
                score += 34
            } else if (artA.isNotEmpty() && artB.isNotEmpty()) {
                val inter = artA.intersect(artB).size.toFloat()
                val union = artA.union(artB).size.coerceAtLeast(1).toFloat()
                score += ((inter / union) * 30f).toInt()
            }
        } else if (cleanArtist.isBlank()) {
            score += 8
        }

        if (track.album.isNotBlank() && !track.album.equals("Unknown Album", ignoreCase = true)) {
            if (candAlbum.equals(track.album, ignoreCase = true)) {
                score += 10
            } else {
                val albA = TrackMatchValidator.normalizeTokens(track.album)
                val albB = TrackMatchValidator.normalizeTokens(candAlbum)
                if (albA.isNotEmpty() && albB.isNotEmpty() && albA.intersect(albB).isNotEmpty()) {
                    score += 5
                }
            }
        }

        if (track.year > 0 && candYear != null && candYear > 0) {
            if (abs(track.year - candYear) <= 1) score += 5
        }

        if (track.durationMs > 0L && candDurationMs != null && candDurationMs > 0L) {
            val diffSec = abs(track.durationMs - candDurationMs) / 1000L
            score += when {
                diffSec <= 4 -> 8
                diffSec <= 12 -> 3
                diffSec >= 35 -> -18
                else -> 0
            }
        }

        // Penalize remix / live / instrumental mismatch
        val lowTrack = track.title.lowercase()
        val lowCand = candTitle.lowercase()
        for (marker in VERSION_MARKERS) {
            val inTrack = lowTrack.contains(marker)
            val inCand = lowCand.contains(marker)
            if (inTrack != inCand) {
                score -= 18
                break
            }
        }

        return score.coerceIn(0, 100)
    }
}

@Serializable
private data class ItunesSearchResponse(
    val results: List<ItunesTrackRecord> = emptyList(),
)

@Serializable
private data class ItunesTrackRecord(
    val trackId: Long = 0L,
    val trackName: String = "",
    val artistName: String = "",
    val collectionName: String = "",
    val artworkUrl100: String = "",
    val releaseDate: String = "",
    val trackTimeMillis: Long = 0L,
    val primaryGenreName: String = "",
)

class ItunesArtworkAdapter(
    private val client: OkHttpClient = defaultArtworkHttpClient(),
) : ArtworkProviderAdapter {
    override val providerName: String = "iTunes Search"
    private val json = Json { ignoreUnknownKeys = true }

    override suspend fun search(track: Track): List<ArtworkSearchCandidate> = withContext(Dispatchers.IO) {
        val (inferredArtist, inferredTitle) = TrackMatchValidator.inferArtistAndTitle(track)
        val term = listOf(inferredArtist, inferredTitle).filter { it.isNotBlank() }.joinToString(" ").trim()
        if (term.isBlank()) return@withContext emptyList()

        val primary = queryItunes(track, term)
        if (primary.isNotEmpty() || inferredArtist.isBlank()) {
            return@withContext primary
        }
        // Retry with title-only if combined artist+title search yielded nothing
        if (inferredTitle.isNotBlank()) queryItunes(track, inferredTitle) else emptyList()
    }

    private fun queryItunes(track: Track, term: String): List<ArtworkSearchCandidate> {
        val url = "https://itunes.apple.com/search".toHttpUrl().newBuilder()
            .addQueryParameter("term", term)
            .addQueryParameter("entity", "song")
            .addQueryParameter("limit", "8")
            .build()

        val req = Request.Builder()
            .url(url)
            .header("User-Agent", "MaxmizeMusic/2.0 (Android Offline Player)")
            .build()

        val body = runCatching {
            client.newCall(req).execute().use { resp ->
                if (!resp.isSuccessful) return@use null
                resp.body?.string()
            }
        }.getOrNull() ?: return emptyList()

        val decoded = runCatching { json.decodeFromString<ItunesSearchResponse>(body) }.getOrNull()
            ?: return emptyList()

        return decoded.results.mapNotNull { rec ->
            if (rec.artworkUrl100.isBlank()) return@mapNotNull null
            val hiResUrl = rec.artworkUrl100
                .replace("100x100bb", "600x600bb")
                .replace("100x100", "600x600")
            val year = rec.releaseDate.take(4).toIntOrNull()
            val score = ArtworkMatchValidator.scoreCandidate(
                track = track,
                candTitle = rec.trackName,
                candArtist = rec.artistName,
                candAlbum = rec.collectionName,
                candYear = year,
                candDurationMs = rec.trackTimeMillis,
            )
            ArtworkSearchCandidate(
                id = "itunes_${rec.trackId}",
                imageUrl = hiResUrl,
                title = rec.trackName,
                artist = rec.artistName,
                album = rec.collectionName,
                releaseYear = year,
                provider = providerName,
                confidenceScore = score,
                genre = rec.primaryGenreName,
            )
        }.sortedByDescending { it.confidenceScore }
    }
}

@Serializable
private data class MbRecordingSearchResponse(
    val recordings: List<MbRecording> = emptyList(),
)

@Serializable
private data class MbRecording(
    val id: String = "",
    val title: String = "",
    val length: Long? = null,
    @SerialName("artist-credit") val artistCredit: List<MbArtistCredit> = emptyList(),
    val releases: List<MbRelease> = emptyList(),
)

@Serializable
private data class MbArtistCredit(
    val name: String = "",
)

@Serializable
private data class MbRelease(
    val id: String = "",
    val title: String = "",
    val date: String? = null,
)

class MusicBrainzCoverArtAdapter(
    private val client: OkHttpClient = defaultArtworkHttpClient(),
) : ArtworkProviderAdapter {
    override val providerName: String = "Cover Art Archive"
    private val json = Json { ignoreUnknownKeys = true }
    @Volatile
    private var lastRequestMs: Long = 0L

    override suspend fun search(track: Track): List<ArtworkSearchCandidate> = withContext(Dispatchers.IO) {
        val (cleanArtist, cleanTitle) = TrackMatchValidator.inferArtistAndTitle(track)
        if (cleanTitle.isBlank()) return@withContext emptyList()

        val now = System.currentTimeMillis()
        val wait = 1050L - (now - lastRequestMs)
        if (wait > 0) delay(wait)
        lastRequestMs = System.currentTimeMillis()

        val query = buildString {
            append("recording:\"$cleanTitle\"")
            if (cleanArtist.isNotBlank()) append(" AND artist:\"$cleanArtist\"")
        }
        val url = "https://musicbrainz.org/ws/2/recording".toHttpUrl().newBuilder()
            .addQueryParameter("query", query)
            .addQueryParameter("fmt", "json")
            .addQueryParameter("limit", "4")
            .build()

        val req = Request.Builder()
            .url(url)
            .header("User-Agent", "MaxmizeMusic/2.0 (Android Offline Player)")
            .build()

        val body = runCatching {
            client.newCall(req).execute().use { resp ->
                if (!resp.isSuccessful) return@use null
                resp.body?.string()
            }
        }.getOrNull() ?: return@withContext emptyList()

        val decoded = runCatching { json.decodeFromString<MbRecordingSearchResponse>(body) }.getOrNull()
            ?: return@withContext emptyList()

        val out = mutableListOf<ArtworkSearchCandidate>()
        for (rec in decoded.recordings) {
            val artist = rec.artistCredit.joinToString(", ") { it.name }.ifBlank { track.artist }
            val release = rec.releases.firstOrNull { it.id.isNotBlank() } ?: continue
            val year = release.date?.take(4)?.toIntOrNull()
            val score = ArtworkMatchValidator.scoreCandidate(
                track = track,
                candTitle = rec.title,
                candArtist = artist,
                candAlbum = release.title,
                candYear = year,
                candDurationMs = rec.length,
            )
            if (score < 45) continue

            val caaUrl = "https://coverartarchive.org/release/${release.id}/front-500"
            if (verifyCoverExists(caaUrl)) {
                out.add(
                    ArtworkSearchCandidate(
                        id = "mb_${release.id}",
                        imageUrl = caaUrl,
                        title = rec.title,
                        artist = artist,
                        album = release.title,
                        releaseYear = year,
                        provider = providerName,
                        confidenceScore = score,
                    )
                )
            }
        }
        out.sortedByDescending { it.confidenceScore }
    }

    private fun verifyCoverExists(url: String): Boolean {
        val headReq = Request.Builder()
            .url(url)
            .head()
            .header("User-Agent", "MaxmizeMusic/2.0 (Android Offline Player)")
            .build()
        return runCatching {
            client.newCall(headReq).execute().use { it.isSuccessful }
        }.getOrDefault(false)
    }
}

fun defaultArtworkHttpClient(): OkHttpClient = OkHttpClient.Builder()
    .connectTimeout(8, TimeUnit.SECONDS)
    .readTimeout(12, TimeUnit.SECONDS)
    .followRedirects(true)
    .build()
