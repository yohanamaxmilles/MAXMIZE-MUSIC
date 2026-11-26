package com.example.data.online

/**
 * Provider-neutral contracts for MAXMIZE MUSIC online catalogues.
 *
 * UI and playback code should depend on these contracts, never on a provider's
 * private API or response format. A provider implementation must use an
 * authorized playback method and must not extract or bypass protected streams.
 */

data class OnlineTrack(
    val id: String,
    val providerId: String,
    val title: String,
    val artistName: String,
    val albumName: String = "",
    val artworkUrl: String? = null,
    val durationMs: Long = 0L,
    val explicit: Boolean = false,
    val permalink: String? = null,
)

data class OnlineArtist(
    val id: String,
    val providerId: String,
    val name: String,
    val artworkUrl: String? = null,
)

data class OnlinePlaylist(
    val id: String,
    val providerId: String,
    val title: String,
    val description: String = "",
    val artworkUrl: String? = null,
    val trackCount: Int? = null,
)

data class OnlineMood(
    val id: String,
    val title: String,
    val description: String = "",
    val artworkUrl: String? = null,
)

data class OnlineSearchResults(
    val tracks: List<OnlineTrack> = emptyList(),
    val artists: List<OnlineArtist> = emptyList(),
    val playlists: List<OnlinePlaylist> = emptyList(),
)

data class OnlinePlaybackSource(
    val uri: String,
    val mimeType: String? = null,
    val expiresAtEpochMs: Long? = null,
)

/**
 * Implementations are added per licensed/authorized music provider.
 * Keep provider-specific authentication and response parsing inside the
 * implementation, not in Compose screens or the DJ engine.
 */
interface OnlineMusicProvider {
    val providerId: String
    val displayName: String

    suspend fun search(query: String): OnlineSearchResults

    suspend fun getFeaturedPlaylists(): List<OnlinePlaylist>

    suspend fun getMoods(): List<OnlineMood>

    suspend fun getPlaylistTracks(playlistId: String): List<OnlineTrack>

    /**
     * Return a playback source only when the provider explicitly permits
     * third-party playback through the returned URI.
     */
    suspend fun getPlaybackSource(track: OnlineTrack): OnlinePlaybackSource
}
