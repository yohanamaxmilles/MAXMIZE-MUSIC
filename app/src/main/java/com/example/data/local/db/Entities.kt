package com.example.data.local.db

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(tableName = "tracks")
data class TrackEntity(
    @PrimaryKey val id: String,
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
)

@Entity(tableName = "metadata_overrides")
data class MetadataOverrideEntity(
    @PrimaryKey val trackId: String,
    val title: String?,
    val artist: String?,
    val album: String?,
    val albumArtist: String?,
    val genre: String?,
    val year: Int?,
    val customArtworkUri: String?,
    val updatedAt: Long = System.currentTimeMillis(),
)

@Entity(tableName = "metadata_undo_history")
data class MetadataUndoEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0L,
    val trackId: String,
    val prevTitle: String?,
    val prevArtist: String?,
    val prevAlbum: String?,
    val prevAlbumArtist: String?,
    val prevGenre: String?,
    val prevYear: Int?,
    val prevCustomArtworkUri: String?,
    val savedAt: Long = System.currentTimeMillis(),
)

@Entity(tableName = "artwork_cache")
data class ArtworkCacheEntity(
    @PrimaryKey val albumKey: String,
    val trackId: String,
    val localFileUri: String,
    val provider: String,
    val confidenceScore: Int,
    val failedAttempts: Int = 0,
    val updatedAt: Long = System.currentTimeMillis(),
)

@Entity(tableName = "playlists")
data class PlaylistEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0L,
    val name: String,
    val description: String = "",
    val customCoverUri: String? = null,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis(),
)

@Entity(
    tableName = "playlist_tracks",
    primaryKeys = ["playlistId", "position"],
    foreignKeys = [
        ForeignKey(
            entity = PlaylistEntity::class,
            parentColumns = ["id"],
            childColumns = ["playlistId"],
            onDelete = ForeignKey.CASCADE,
        )
    ],
    indices = [Index("playlistId"), Index("trackId")],
)
data class PlaylistTrackCrossRef(
    val playlistId: Long,
    val trackId: String,
    val position: Int,
    val addedAt: Long = System.currentTimeMillis(),
)

@Entity(tableName = "queue_items")
data class QueueItemEntity(
    @PrimaryKey val position: Int,
    val trackId: String,
)

@Entity(tableName = "playback_snapshot")
data class PlaybackSnapshotEntity(
    @PrimaryKey val id: Int = 1,
    val currentIndex: Int = 0,
    val positionMs: Long = 0L,
    val shuffleEnabled: Boolean = false,
    val repeatMode: String = "OFF",
    val updatedAt: Long = System.currentTimeMillis(),
)

@Entity(tableName = "search_history")
data class SearchHistoryEntity(
    @PrimaryKey val query: String,
    val searchedAt: Long = System.currentTimeMillis(),
)

@Entity(tableName = "lyrics_cache")
data class LyricsEntity(
    @PrimaryKey val trackId: String,
    val rawText: String,
    val isSynced: Boolean,
    val source: String,
    val syncOffsetMs: Long = 0L,
    val provider: String = "",
    val language: String = "",
    val hasWordSync: Boolean = false,
    val updatedAt: Long = System.currentTimeMillis(),
)

@Entity(tableName = "track_analysis")
data class TrackAnalysisEntity(
    @PrimaryKey val trackId: String,
    val bpm: Float,
    val bpmConfidence: Float,
    val tempoStability: Float,
    val musicalKey: String,
    val camelotCode: String,
    val keyConfidence: Float,
    val energy: Float,
    val rmsDb: Float,
    val loudnessLufs: Float,
    val dynamicRangeDb: Float,
    val onsetDensity: Float,
    val firstBeatMs: Long,
    val beatIntervalMs: Long,
    val introEndMs: Long,
    val outroStartMs: Long,
    val durationMs: Long,
    val userCorrected: Boolean = false,
    val analyzedAt: Long = System.currentTimeMillis(),
    val primaryGenre: String = "Unknown",
    val secondaryGenre: String = "",
    val subgenre: String = "",
    val genreConfidence: Float = 0.25f,
    val danceability: Float = 0.5f,
    val rhythmSignature: String = "4/4",
    val phraseLengthMs: Long = 0L,
    val downbeatConfidence: Float = 0.5f,
    val spectralCentroidHz: Float = 1500f,
    val analysisVersion: Int = 0,
)

@Entity(tableName = "custom_dj_profiles")
data class CustomDjProfileEntity(
    @PrimaryKey val id: String,
    val name: String,
    val description: String,
    val mixMode: String,
    val bpmMin: Int,
    val bpmMax: Int,
    val preferredGenresCsv: String,
    val strictGenreMatching: Boolean,
    val harmonicMixLevel: String,
    val energyProgression: String,
    val transitionStyle: String,
    val transitionDurationSec: Int,
    val keyLockEnabled: Boolean,
    val phraseMatchEnabled: Boolean,
    val bpmWeight: Float,
    val harmonicWeight: Float,
    val energyWeight: Float,
    val genreWeight: Float,
    val openFormatBridging: Boolean,
    val updatedAt: Long = System.currentTimeMillis(),
)

@Entity(
    tableName = "genre_rule_overrides",
    primaryKeys = ["genreA", "genreB"],
)
data class GenreRuleOverrideEntity(
    val genreA: String,
    val genreB: String,
    val allowed: Boolean,
    val updatedAt: Long = System.currentTimeMillis(),
)

@Entity(tableName = "mix_history")
data class MixHistoryEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0L,
    val fromTrackId: String,
    val fromTitle: String,
    val fromArtist: String,
    val toTrackId: String,
    val toTitle: String,
    val toArtist: String,
    val mixMode: String,
    val transitionStyle: String,
    val bpmFrom: Float,
    val bpmTo: Float,
    val camelotFrom: String,
    val camelotTo: String,
    val compatibilityScore: Int,
    val mixedAt: Long = System.currentTimeMillis(),
)

@Entity(tableName = "excluded_folders")
data class ExcludedFolderEntity(
    @PrimaryKey val folderPath: String,
    val excludedAt: Long = System.currentTimeMillis(),
)

@Entity(tableName = "saf_sources")
data class SafSourceEntity(
    @PrimaryKey val treeUri: String,
    val isTree: Boolean,
    val displayName: String,
    val addedAt: Long = System.currentTimeMillis(),
)
