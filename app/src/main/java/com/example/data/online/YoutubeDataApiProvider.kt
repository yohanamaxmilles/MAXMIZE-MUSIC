package com.example.data.online

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.IOException

/**
 * Official YouTube Data API v3 catalogue adapter.
 *
 * Discovery only. YouTube playback must use an approved YouTube player
 * experience. This adapter never resolves or extracts media stream URLs.
 *
 * Supply the API key through secure runtime configuration or a backend.
 * Never commit a real API key to source control.
 */
class YoutubeDataApiProvider(
    private val apiKey: String,
    private val httpClient: OkHttpClient = OkHttpClient(),
    private val json: Json = Json { ignoreUnknownKeys = true },
) : OnlineMusicProvider {

    override val providerId: String = PROVIDER_ID
    override val displayName: String = "YouTube"
    override val supportsInAppAudioPlayback: Boolean = false
    override val supportsDjProcessing: Boolean = false

    override suspend fun search(query: String): OnlineSearchResults {
        val normalizedQuery = query.trim()
        if (normalizedQuery.isEmpty()) return OnlineSearchResults()
        require(apiKey.isNotBlank()) { "YouTube Data API key is not configured." }

        return withContext(Dispatchers.IO) {
            val url = BASE_URL.toHttpUrl().newBuilder()
                .addPathSegment("search")
                .addQueryParameter("part", "snippet")
                .addQueryParameter("type", "video")
                .addQueryParameter("videoCategoryId", MUSIC_CATEGORY_ID)
                .addQueryParameter("maxResults", MAX_RESULTS.toString())
                .addQueryParameter("q", normalizedQuery)
                .addQueryParameter("key", apiKey)
                .build()

            val request = Request.Builder()
                .url(url)
                .header("Accept", "application/json")
                .get()
                .build()

            httpClient.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    throw IOException("YouTube catalogue request failed with HTTP ${response.code}.")
                }
                val body = response.body?.string()
                    ?: throw IOException("YouTube catalogue returned an empty response.")
                parseSearchResults(body)
            }
        }
    }

    override suspend fun getFeaturedPlaylists(): List<OnlinePlaylist> = emptyList()
    override suspend fun getMoods(): List<OnlineMood> = emptyList()
    override suspend fun getPlaylistTracks(playlistId: String): List<OnlineTrack> = emptyList()

    override suspend fun getPlaybackSource(track: OnlineTrack): OnlinePlaybackSource {
        throw UnsupportedOperationException(
            "YouTube catalogue results require the approved YouTube player. " +
                "Direct audio playback in MAXMIZE is not provided by this adapter."
        )
    }

    private fun parseSearchResults(responseBody: String): OnlineSearchResults {
        val root = json.parseToJsonElement(responseBody).jsonObject
        val items = root["items"]?.jsonArray ?: return OnlineSearchResults()
        val tracks = items.mapNotNull { element ->
            val item = element.jsonObject
            val videoId = item["id"]?.jsonObject?.get("videoId")
                ?.jsonPrimitive?.content ?: return@mapNotNull null
            val snippet = item["snippet"]?.jsonObject ?: return@mapNotNull null
            val title = snippet["title"]?.jsonPrimitive?.content ?: return@mapNotNull null
            val artist = snippet["channelTitle"]?.jsonPrimitive?.content.orEmpty()
            val thumbnails = snippet["thumbnails"]?.jsonObject
            val artwork = thumbnails?.get("high")?.jsonObject?.get("url")
                ?: thumbnails?.get("medium")?.jsonObject?.get("url")
                ?: thumbnails?.get("default")?.jsonObject?.get("url")

            OnlineTrack(
                id = videoId,
                providerId = PROVIDER_ID,
                title = title,
                artistName = artist,
                artworkUrl = artwork?.jsonPrimitive?.content,
                permalink = "https://www.youtube.com/watch?v=$videoId",
            )
        }
        return OnlineSearchResults(tracks = tracks)
    }

    private companion object {
        const val PROVIDER_ID = "youtube-data-api"
        const val BASE_URL = "https://www.googleapis.com/youtube/v3"
        const val MUSIC_CATEGORY_ID = "10"
        const val MAX_RESULTS = 25
    }
}
