package com.example.data.model

import androidx.compose.runtime.Immutable

@Immutable
data class Track(
    val id: String,
    val mediaStoreId: Long?,
    val contentUri: String,
    val title: String,
    val artist: String,
    val album: String,
    val albumArtist: String,
    val genre: String,
    val durationMs: Long,
    val trackNumber: Int,
    val year: Int,
    val mimeType: String,
    val filePath: String,
    val folderPath: String,
    val folderName: String,
    val fileSizeBytes: Long,
    val dateAddedSec: Long,
    val albumId: Long?,
    val artworkUri: String?,
    val customArtworkUri: String?,
    val playCount: Int = 0,
    val lastPlayedAt: Long = 0L,
    val isFavorite: Boolean = false,
    val isAvailable: Boolean = true,
    val sampleRateHz: Int = 0,
    val bitrateKbps: Int = 0,
    val dateModifiedSec: Long = 0L,
    val rating: Int = 0,
    val skipCount: Int = 0,
    val lastSkippedAt: Long = 0L,
) {
    val displayArtwork: String?
        get() = customArtworkUri ?: artworkUri ?: contentUri

    val formatLabel: String
        get() = when {
            mimeType.contains("flac", ignoreCase = true) -> "FLAC"
            mimeType.contains("wav", ignoreCase = true) || mimeType.contains("x-wav", ignoreCase = true) -> "WAV"
            mimeType.contains("opus", ignoreCase = true) -> "OPUS"
            mimeType.contains("ogg", ignoreCase = true) -> "OGG"
            mimeType.contains("mp4", ignoreCase = true) || mimeType.contains("m4a", ignoreCase = true) -> "M4A"
            mimeType.contains("aac", ignoreCase = true) -> "AAC"
            mimeType.contains("wma", ignoreCase = true) -> "WMA"
            mimeType.contains("mpeg", ignoreCase = true) || filePath.endsWith(".mp3", ignoreCase = true) -> "MP3"
            else -> filePath.substringAfterLast('.', "AUDIO").uppercase().take(5)
        }
}
