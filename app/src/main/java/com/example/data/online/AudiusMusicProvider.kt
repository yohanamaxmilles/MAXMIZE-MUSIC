package com.example.data.online

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.IOException

/**
 * Audius read-only catalogue and public-track streaming adapter.
 *
 * The API key is free to create and is safe for client-side use according
 * to Audius developer documentation. No bearer token or user login is used.
 * DJ processing stays disabled because streaming permission does not grant
 * separate rights for tempo/key manipulation or DJ redistribution.
 */
class AudiusMusicProvider(
    private val apiKey: String,
    private val httpClient: OkHttpClient = OkHttpClient(),
    private val json: Json = Json { ignoreUnknownKeys = true },
) : OnlineMusicProvider {

    override val providerId: String = PROVIDER_ID
    override val displayName: String = "Audius"
    override val supportsInAppAudioPlayback: Boolean = true
    override val supportsDjProcessing: Boolean = false

    override suspend fun search(query: String): OnlineSearchResults {
        val normalizedQuery = query.trim()
        if (normalizedQuery.isEmpty()) return OnlineSearchResults()
        require(apiKey.isNotBlank()) { "Add your free Audius API key to search music." }

        return withContext(Dispatchers.IO) {
            val url = BASE_URL.toHttpUrl().newBuilder()
                .addPathSegment("tracks")
                .addPathSegment("search")
                .addQueryParameter("query", normalizedQuery)
                .addQueryParameter("limit", MAX_RESULTS.toString())
                .build()

            val request = Request.Builder()
                .url(url)
                .header("Accept", "application/json")
                .header("x-api-key", apiKey.trim())
                .get()
                .build()

            httpClient.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    throw IOException(
                        when (response.code) {
                            401, 403 -> "Audius rejected the API key. Check the key and try again."
                            429 -> "Audius request limit reached. Wait a moment and try again."
                            else -> "Audius search failed (HTTP ${response.code})."
                        }
                    )
                }
                val body = response.body?.string()
                    ?: throw IOException("Audius returned an empty response.")
                parseSearchResults(body)
            }
        }
    }

    override suspend fun getFeaturedPlaylists(): List<OnlinePlaylist> = emptyList()
    override suspend fun getMoods(): List<OnlineMood> = emptyList()
    override suspend fun getPlaylistTracks(playlistId: String): List<OnlineTrack> = emptyList()

    override suspend fun getPlaybackSource(track: OnlineTrack): OnlinePlaybackSource {
        require(track.providerId == providerId) { "This track belongs to another music provider." }
        require(apiKey.isNotBlank()) { "Add your free Audius API key before playing music." }
        return OnlinePlaybackSource(
            uri = BASE_URL.toHttpUrl().newBuilder()
                .addPathSegment("tracks")
                .addPathSegment(track.id)
                .addPathSegment("stream")
                .addQueryParameter("app_name", APP_NAME)
                .build()
                .toString(),
        )
    }

    private fun parseSearchResults(responseBody: String): OnlineSearchResults {
        val root = json.parseToJsonElement(responseBody).jsonObject
        val items = root["data"]?.jsonArray ?: return OnlineSearchResults()
        val tracks = items.mapNotNull { element ->
            val item = element.jsonObject
            if ((item["is_stream_gated"] as? JsonPrimitive)?.booleanOrNull == true) {
                return@mapNotNull null
            }

            val id = (item["id"] as? JsonPrimitive)?.content
                ?: return@mapNotNull null
            val title = (item["title"] as? JsonPrimitive)?.content
                ?: return@mapNotNull null
            val user = item["user"] as? JsonObject
            val artist = (user?.get("name") as? JsonPrimitive)?.content
                ?: (user?.get("handle") as? JsonPrimitive)?.content
                ?: "Unknown artist"
            val artwork = item["artwork"] as? JsonObject
            val artworkUrl = (artwork?.get("480x480") as? JsonPrimitive)?.content
                ?: (artwork?.get("150x150") as? JsonPrimitive)?.content
                ?: (artwork?.get("1000x1000") as? JsonPrimitive)?.content
            val durationSeconds = (item["duration"] as? JsonPrimitive)?.doubleOrNull ?: 0.0

            OnlineTrack(
                id = id,
                providerId = PROVIDER_ID,
                title = title,
                artistName = artist,
                albumName = ((item["album"] as? JsonObject)?.get("title") as? JsonPrimitive)?.content.orEmpty(),
                artworkUrl = artworkUrl,
                durationMs = (durationSeconds * 1000.0).toLong(),
                explicit = (item["explicit"] as? JsonPrimitive)?.booleanOrNull ?: false,
                permalink = (item["permalink"] as? JsonPrimitive)?.content,
            )
        }
        return OnlineSearchResults(tracks = tracks)
    }

    private companion object {
        const val PROVIDER_ID = "audius"
        const val BASE_URL = "https://api.audius.co/v1"
        const val APP_NAME = "MAXMIZE_MUSIC"
        const val MAX_RESULTS = 25
    }
}
