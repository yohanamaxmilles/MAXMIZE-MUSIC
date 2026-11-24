package com.example.data.model

import androidx.compose.runtime.Immutable

@Immutable
data class Playlist(
    val id: Long,
    val name: String,
    val description: String,
    val customCoverUri: String?,
    val createdAt: Long,
    val updatedAt: Long,
    val trackCount: Int = 0,
    val totalDurationMs: Long = 0L,
    val artworkUris: List<String> = emptyList(),
)

@Immutable
data class PlaylistWithTracks(
    val playlist: Playlist,
    val tracks: List<Track>,
)

@Immutable
data class AlbumSummary(
    val key: String,
    val albumName: String,
    val artistName: String,
    val year: Int,
    val trackCount: Int,
    val totalDurationMs: Long,
    val artworkUri: String?,
    val lastPlayedAt: Long = 0L,
)

@Immutable
data class ArtistSummary(
    val artistName: String,
    val albumCount: Int,
    val trackCount: Int,
    val totalDurationMs: Long,
    val artworkUri: String?,
)

@Immutable
data class GenreSummary(
    val genreName: String,
    val trackCount: Int,
    val totalDurationMs: Long,
    val artworkUri: String?,
)

@Immutable
data class FolderSummary(
    val folderPath: String,
    val folderName: String,
    val trackCount: Int,
    val totalDurationMs: Long,
    val isExcluded: Boolean = false,
)
