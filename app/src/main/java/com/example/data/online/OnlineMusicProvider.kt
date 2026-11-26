package com.example.data.online

/**
 * Provider-neutral contracts for MAXMIZE MUSIC online catalogues.
 *
 * UI and playback code depend on these contracts, never on a provider's
 * private API or response format. Providers expose only capabilities
 * explicitly allowed by their developer terms and content licences.
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

interface OnlineMusicProvider {
    val providerId: String
    val displayName: String

    /** True only when the provider authorizes direct in-app audio playback. */
    val supportsInAppAudioPlayback: Boolean
        get() = false

    /** True only when the provider's licence permits DJ processing/mixing. */
    val supportsDjProcessing: Boolean
        get() = false

    suspend fun search(query: String): OnlineSearchResults
    suspend fun getFeaturedPlaylists(): List<OnlinePlaylist>
    suspend fun getMoods(): List<OnlineMood>
    suspend fun getPlaylistTracks(playlistId: String): List<OnlineTrack>

    /**
     * Return a direct audio source only when the provider explicitly permits
     * third-party playback through that URI. Discovery-only providers must
     * leave this unsupported.
     */
    suspend fun getPlaybackSource(track: OnlineTrack): OnlinePlaybackSource
}
