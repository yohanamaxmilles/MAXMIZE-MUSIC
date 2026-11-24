package com.example.data.local.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface TrackDao {
    @Query("SELECT * FROM tracks WHERE isAvailable = 1 ORDER BY title COLLATE NOCASE ASC")
    fun observeAvailableTracks(): Flow<List<TrackEntity>>

    @Query("SELECT * FROM tracks ORDER BY title COLLATE NOCASE ASC")
    suspend fun getAllTracksOnce(): List<TrackEntity>

    @Query("SELECT * FROM tracks WHERE id = :id LIMIT 1")
    suspend fun getTrackById(id: String): TrackEntity?

    @Query("SELECT * FROM tracks WHERE id IN (:ids)")
    suspend fun getTracksByIds(ids: List<String>): List<TrackEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertTracks(tracks: List<TrackEntity>)

    @Query("UPDATE tracks SET isAvailable = 0 WHERE id NOT IN (:activeIds)")
    suspend fun markUnavailableExcept(activeIds: List<String>)

    @Query("UPDATE tracks SET isAvailable = 0")
    suspend fun markAllUnavailable()

    @Query("DELETE FROM tracks WHERE isAvailable = 0")
    suspend fun deleteUnavailableTracks()

    @Query("UPDATE tracks SET isFavorite = :isFavorite WHERE id = :trackId")
    suspend fun setFavorite(trackId: String, isFavorite: Boolean)

    @Query("UPDATE tracks SET rating = :rating WHERE id = :trackId")
    suspend fun setRating(trackId: String, rating: Int)

    @Query("UPDATE tracks SET playCount = playCount + 1, lastPlayedAt = :playedAt WHERE id = :trackId")
    suspend fun recordPlay(trackId: String, playedAt: Long)

    @Query("UPDATE tracks SET skipCount = skipCount + 1, lastSkippedAt = :skippedAt WHERE id = :trackId")
    suspend fun recordSkip(trackId: String, skippedAt: Long)

    @Query("SELECT * FROM metadata_overrides")
    fun observeMetadataOverrides(): Flow<List<MetadataOverrideEntity>>

    @Query("SELECT * FROM metadata_overrides WHERE trackId = :trackId LIMIT 1")
    suspend fun getMetadataOverride(trackId: String): MetadataOverrideEntity?

    @Query("SELECT * FROM metadata_overrides")
    suspend fun getAllOverridesOnce(): List<MetadataOverrideEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertMetadataOverride(override: MetadataOverrideEntity)

    @Query("DELETE FROM metadata_overrides WHERE trackId = :trackId")
    suspend fun deleteMetadataOverride(trackId: String)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertMetadataUndo(undo: MetadataUndoEntity)

    @Query("SELECT * FROM metadata_undo_history WHERE trackId = :trackId ORDER BY savedAt DESC LIMIT 1")
    suspend fun getLatestUndoForTrack(trackId: String): MetadataUndoEntity?

    @Query("SELECT * FROM metadata_undo_history ORDER BY savedAt DESC LIMIT 30")
    fun observeUndoHistory(): Flow<List<MetadataUndoEntity>>

    @Query("DELETE FROM metadata_undo_history WHERE id = :id")
    suspend fun deleteUndoById(id: Long)
}

@Dao
interface ArtworkCacheDao {
    @Query("SELECT * FROM artwork_cache")
    fun observeAllCache(): Flow<List<ArtworkCacheEntity>>

    @Query("SELECT * FROM artwork_cache WHERE albumKey = :albumKey LIMIT 1")
    suspend fun getByAlbumKey(albumKey: String): ArtworkCacheEntity?

    @Query("SELECT * FROM artwork_cache")
    suspend fun getAllOnce(): List<ArtworkCacheEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(entity: ArtworkCacheEntity)

    @Query("DELETE FROM artwork_cache")
    suspend fun clearAll()
}

@Dao
interface DjConfigDao {
    @Query("SELECT * FROM custom_dj_profiles ORDER BY updatedAt DESC")
    fun observeCustomProfiles(): Flow<List<CustomDjProfileEntity>>

    @Query("SELECT * FROM custom_dj_profiles ORDER BY updatedAt DESC")
    suspend fun getCustomProfilesOnce(): List<CustomDjProfileEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertProfile(profile: CustomDjProfileEntity)

    @Query("DELETE FROM custom_dj_profiles WHERE id = :id")
    suspend fun deleteProfile(id: String)

    @Query("SELECT * FROM genre_rule_overrides ORDER BY updatedAt DESC")
    fun observeGenreRules(): Flow<List<GenreRuleOverrideEntity>>

    @Query("SELECT * FROM genre_rule_overrides")
    suspend fun getGenreRulesOnce(): List<GenreRuleOverrideEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertGenreRule(rule: GenreRuleOverrideEntity)

    @Query("DELETE FROM genre_rule_overrides WHERE genreA = :genreA AND genreB = :genreB")
    suspend fun deleteGenreRule(genreA: String, genreB: String)
}

@Dao
interface PlaylistDao {
    @Query("SELECT * FROM playlists ORDER BY updatedAt DESC")
    fun observePlaylists(): Flow<List<PlaylistEntity>>

    @Query("SELECT * FROM playlists ORDER BY updatedAt DESC")
    suspend fun getPlaylistsOnce(): List<PlaylistEntity>

    @Query("SELECT * FROM playlists WHERE id = :playlistId LIMIT 1")
    fun observePlaylistById(playlistId: Long): Flow<PlaylistEntity?>

    @Query("SELECT * FROM playlists WHERE id = :playlistId LIMIT 1")
    suspend fun getPlaylistById(playlistId: Long): PlaylistEntity?

    @Query("SELECT * FROM playlist_tracks ORDER BY playlistId ASC, position ASC")
    fun observeAllPlaylistTrackRefs(): Flow<List<PlaylistTrackCrossRef>>

    @Query("SELECT * FROM playlist_tracks WHERE playlistId = :playlistId ORDER BY position ASC")
    fun observePlaylistTrackRefs(playlistId: Long): Flow<List<PlaylistTrackCrossRef>>

    @Query("SELECT * FROM playlist_tracks WHERE playlistId = :playlistId ORDER BY position ASC")
    suspend fun getPlaylistTrackRefsOnce(playlistId: Long): List<PlaylistTrackCrossRef>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertPlaylist(playlist: PlaylistEntity): Long

    @Update
    suspend fun updatePlaylist(playlist: PlaylistEntity)

    @Query("DELETE FROM playlists WHERE id = :playlistId")
    suspend fun deletePlaylistById(playlistId: Long)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertPlaylistTrackRefs(refs: List<PlaylistTrackCrossRef>)

    @Query("DELETE FROM playlist_tracks WHERE playlistId = :playlistId")
    suspend fun clearPlaylistTracks(playlistId: Long)

    @Transaction
    suspend fun replacePlaylistTracks(playlistId: Long, trackIds: List<String>) {
        clearPlaylistTracks(playlistId)
        val now = System.currentTimeMillis()
        val refs = trackIds.mapIndexed { index, trackId ->
            PlaylistTrackCrossRef(
                playlistId = playlistId,
                trackId = trackId,
                position = index,
                addedAt = now,
            )
        }
        if (refs.isNotEmpty()) {
            insertPlaylistTrackRefs(refs)
        }
        val playlist = getPlaylistById(playlistId)
        if (playlist != null) {
            updatePlaylist(playlist.copy(updatedAt = now))
        }
    }
}

@Dao
interface PlaybackStateDao {
    @Query("SELECT * FROM queue_items ORDER BY position ASC")
    suspend fun getQueueOnce(): List<QueueItemEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertQueueItems(items: List<QueueItemEntity>)

    @Query("DELETE FROM queue_items")
    suspend fun clearQueue()

    @Transaction
    suspend fun replaceQueue(trackIds: List<String>) {
        clearQueue()
        val items = trackIds.mapIndexed { idx, id -> QueueItemEntity(position = idx, trackId = id) }
        if (items.isNotEmpty()) {
            insertQueueItems(items)
        }
    }

    @Query("SELECT * FROM playback_snapshot WHERE id = 1 LIMIT 1")
    suspend fun getSnapshotOnce(): PlaybackSnapshotEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun saveSnapshot(snapshot: PlaybackSnapshotEntity)
}

@Dao
interface SearchHistoryDao {
    @Query("SELECT * FROM search_history ORDER BY searchedAt DESC LIMIT 15")
    fun observeHistory(): Flow<List<SearchHistoryEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertQuery(entry: SearchHistoryEntity)

    @Query("DELETE FROM search_history WHERE query = :query")
    suspend fun deleteQuery(query: String)

    @Query("DELETE FROM search_history")
    suspend fun clearAll()
}

@Dao
interface LyricsDao {
    @Query("SELECT * FROM lyrics_cache WHERE trackId = :trackId LIMIT 1")
    fun observeLyrics(trackId: String): Flow<LyricsEntity?>

    @Query("SELECT * FROM lyrics_cache WHERE trackId = :trackId LIMIT 1")
    suspend fun getLyricsOnce(trackId: String): LyricsEntity?

    @Query("SELECT * FROM lyrics_cache")
    suspend fun getAllLyricsOnce(): List<LyricsEntity>

    @Query("SELECT trackId FROM lyrics_cache")
    suspend fun getCachedTrackIds(): List<String>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertLyrics(lyrics: LyricsEntity)

    @Query("UPDATE lyrics_cache SET syncOffsetMs = :offsetMs WHERE trackId = :trackId")
    suspend fun updateSyncOffset(trackId: String, offsetMs: Long)

    @Query("DELETE FROM lyrics_cache WHERE trackId = :trackId")
    suspend fun deleteLyrics(trackId: String)

    @Query("DELETE FROM lyrics_cache")
    suspend fun clearAllLyrics()
}

@Dao
interface AnalysisDao {
    @Query("SELECT * FROM track_analysis")
    fun observeAllAnalyses(): Flow<List<TrackAnalysisEntity>>

    @Query("SELECT * FROM track_analysis")
    suspend fun getAllAnalysesOnce(): List<TrackAnalysisEntity>

    @Query("SELECT * FROM track_analysis WHERE trackId = :trackId LIMIT 1")
    suspend fun getAnalysisOnce(trackId: String): TrackAnalysisEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAnalysis(analysis: TrackAnalysisEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAnalyses(analyses: List<TrackAnalysisEntity>)
}

@Dao
interface MixHistoryDao {
    @Query("SELECT * FROM mix_history ORDER BY mixedAt DESC LIMIT 100")
    fun observeMixHistory(): Flow<List<MixHistoryEntity>>

    @Query("SELECT * FROM mix_history ORDER BY mixedAt DESC LIMIT 50")
    suspend fun getRecentHistoryOnce(): List<MixHistoryEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertMixHistory(entry: MixHistoryEntity)

    @Query("DELETE FROM mix_history")
    suspend fun clearHistory()
}

@Dao
interface ExclusionDao {
    @Query("SELECT * FROM excluded_folders ORDER BY folderPath ASC")
    fun observeExcludedFolders(): Flow<List<ExcludedFolderEntity>>

    @Query("SELECT * FROM excluded_folders")
    suspend fun getExcludedFoldersOnce(): List<ExcludedFolderEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun excludeFolder(entity: ExcludedFolderEntity)

    @Query("DELETE FROM excluded_folders WHERE folderPath = :folderPath")
    suspend fun includeFolder(folderPath: String)

    @Query("SELECT * FROM saf_sources ORDER BY addedAt DESC")
    fun observeSafSources(): Flow<List<SafSourceEntity>>

    @Query("SELECT * FROM saf_sources")
    suspend fun getSafSourcesOnce(): List<SafSourceEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertSafSource(entity: SafSourceEntity)

    @Query("DELETE FROM saf_sources WHERE treeUri = :uri")
    suspend fun removeSafSource(uri: String)
}
