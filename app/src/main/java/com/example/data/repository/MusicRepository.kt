package com.example.data.repository

import com.example.data.local.db.ExcludedFolderEntity
import com.example.data.local.db.ExclusionDao
import com.example.data.local.db.MetadataOverrideEntity
import com.example.data.local.db.PlaylistDao
import com.example.data.local.db.PlaylistEntity
import com.example.data.local.db.SearchHistoryDao
import com.example.data.local.db.SearchHistoryEntity
import com.example.data.local.db.TrackDao
import com.example.data.local.db.TrackEntity
import com.example.data.model.AlbumSummary
import com.example.data.model.ArtistSummary
import com.example.data.model.FolderSummary
import com.example.data.model.GenreSummary
import com.example.data.model.Playlist
import com.example.data.model.PlaylistWithTracks
import com.example.data.model.SortOption
import com.example.data.model.SortOrder
import com.example.data.model.Track
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map

class MusicRepository(
    private val trackDao: TrackDao,
    private val playlistDao: PlaylistDao,
    private val searchHistoryDao: SearchHistoryDao,
    private val exclusionDao: ExclusionDao,
) {
    val tracksFlow: Flow<List<Track>> = combine(
        trackDao.observeAvailableTracks(),
        trackDao.observeMetadataOverrides(),
    ) { entities, overrides ->
        val overrideMap = overrides.associateBy { it.trackId }
        entities.map { entity -> entity.toDomain(overrideMap[entity.id]) }
    }

    val playlistsFlow: Flow<List<Playlist>> = combine(
        playlistDao.observePlaylists(),
        playlistDao.observeAllPlaylistTrackRefs(),
        tracksFlow,
    ) { playlists, refs, tracks ->
        val trackMap = tracks.associateBy { it.id }
        val refsByPlaylist = refs.groupBy { it.playlistId }
        playlists.map { p ->
            val pTracks = refsByPlaylist[p.id].orEmpty()
                .sortedBy { it.position }
                .mapNotNull { trackMap[it.trackId] }
            Playlist(
                id = p.id,
                name = p.name,
                description = p.description,
                customCoverUri = p.customCoverUri,
                createdAt = p.createdAt,
                updatedAt = p.updatedAt,
                trackCount = pTracks.size,
                totalDurationMs = pTracks.sumOf { it.durationMs },
                artworkUris = pTracks.mapNotNull { it.displayArtwork }.distinct().take(4),
            )
        }
    }

    val albumsFlow: Flow<List<AlbumSummary>> = tracksFlow.map { tracks ->
        tracks.groupBy { albumKey(it) }
            .map { (key, albumTracks) ->
                val first = albumTracks.first()
                AlbumSummary(
                    key = key,
                    albumName = first.album,
                    artistName = first.albumArtist.ifBlank { first.artist },
                    year = albumTracks.maxOfOrNull { it.year } ?: 0,
                    trackCount = albumTracks.size,
                    totalDurationMs = albumTracks.sumOf { it.durationMs },
                    artworkUri = albumTracks.firstNotNullOfOrNull { it.customArtworkUri ?: it.artworkUri }
                        ?: first.displayArtwork,
                    lastPlayedAt = albumTracks.maxOfOrNull { it.lastPlayedAt } ?: 0L,
                )
            }
            .sortedBy { it.albumName.lowercase() }
    }

    val artistsFlow: Flow<List<ArtistSummary>> = tracksFlow.map { tracks ->
        tracks.groupBy { it.artist }
            .map { (artistName, artistTracks) ->
                ArtistSummary(
                    artistName = artistName,
                    albumCount = artistTracks.map { it.album.lowercase() }.distinct().size,
                    trackCount = artistTracks.size,
                    totalDurationMs = artistTracks.sumOf { it.durationMs },
                    artworkUri = artistTracks.firstNotNullOfOrNull { it.customArtworkUri ?: it.artworkUri }
                        ?: artistTracks.firstOrNull()?.displayArtwork,
                )
            }
            .sortedBy { it.artistName.lowercase() }
    }

    val genresFlow: Flow<List<GenreSummary>> = tracksFlow.map { tracks ->
        tracks.groupBy { it.genre }
            .map { (genreName, genreTracks) ->
                GenreSummary(
                    genreName = genreName,
                    trackCount = genreTracks.size,
                    totalDurationMs = genreTracks.sumOf { it.durationMs },
                    artworkUri = genreTracks.firstNotNullOfOrNull { it.displayArtwork },
                )
            }
            .sortedBy { it.genreName.lowercase() }
    }

    val foldersFlow: Flow<List<FolderSummary>> = combine(
        tracksFlow,
        exclusionDao.observeExcludedFolders(),
    ) { tracks, excluded ->
        val excludedSet = excluded.map { it.folderPath }.toSet()
        tracks.groupBy { it.folderPath }
            .map { (path, folderTracks) ->
                FolderSummary(
                    folderPath = path,
                    folderName = folderTracks.firstOrNull()?.folderName ?: path.substringAfterLast('/'),
                    trackCount = folderTracks.size,
                    totalDurationMs = folderTracks.sumOf { it.durationMs },
                    isExcluded = path in excludedSet,
                )
            }
            .sortedBy { it.folderName.lowercase() }
    }

    val excludedFoldersFlow: Flow<List<String>> = exclusionDao.observeExcludedFolders()
        .map { list -> list.map { it.folderPath } }

    val searchHistoryFlow: Flow<List<String>> = searchHistoryDao.observeHistory()
        .map { list -> list.map { it.query } }

    fun observePlaylistWithTracks(playlistId: Long): Flow<PlaylistWithTracks?> = combine(
        playlistDao.observePlaylistById(playlistId),
        playlistDao.observePlaylistTrackRefs(playlistId),
        tracksFlow,
    ) { playlistEntity, refs, tracks ->
        playlistEntity ?: return@combine null
        val trackMap = tracks.associateBy { it.id }
        val pTracks = refs.sortedBy { it.position }.mapNotNull { trackMap[it.trackId] }
        PlaylistWithTracks(
            playlist = Playlist(
                id = playlistEntity.id,
                name = playlistEntity.name,
                description = playlistEntity.description,
                customCoverUri = playlistEntity.customCoverUri,
                createdAt = playlistEntity.createdAt,
                updatedAt = playlistEntity.updatedAt,
                trackCount = pTracks.size,
                totalDurationMs = pTracks.sumOf { it.durationMs },
                artworkUris = pTracks.mapNotNull { it.displayArtwork }.distinct().take(4),
            ),
            tracks = pTracks,
        )
    }

    suspend fun toggleFavorite(trackId: String) {
        val current = trackDao.getTrackById(trackId) ?: return
        trackDao.setFavorite(trackId, !current.isFavorite)
    }

    suspend fun setTrackRating(trackId: String, rating: Int) {
        trackDao.setRating(trackId, rating.coerceIn(0, 5))
    }

    suspend fun recordTrackPlayed(trackId: String) {
        trackDao.recordPlay(trackId, System.currentTimeMillis())
    }

    suspend fun recordTrackSkipped(trackId: String) {
        trackDao.recordSkip(trackId, System.currentTimeMillis())
    }

    suspend fun createPlaylist(name: String, description: String = "", initialTrackIds: List<String> = emptyList()): Long {
        val cleanName = name.trim().ifBlank { "Untitled Playlist" }
        val id = playlistDao.insertPlaylist(
            PlaylistEntity(
                name = cleanName,
                description = description.trim(),
            )
        )
        if (initialTrackIds.isNotEmpty()) {
            playlistDao.replacePlaylistTracks(id, initialTrackIds)
        }
        return id
    }

    suspend fun renameAndDescribePlaylist(playlistId: Long, name: String, description: String) {
        val existing = playlistDao.getPlaylistById(playlistId) ?: return
        playlistDao.updatePlaylist(
            existing.copy(
                name = name.trim().ifBlank { existing.name },
                description = description.trim(),
                updatedAt = System.currentTimeMillis(),
            )
        )
    }

    suspend fun deletePlaylist(playlistId: Long) {
        playlistDao.deletePlaylistById(playlistId)
    }

    suspend fun addTracksToPlaylist(playlistId: Long, newTrackIds: List<String>) {
        val currentIds = playlistDao.getPlaylistTrackRefsOnce(playlistId)
            .sortedBy { it.position }
            .map { it.trackId }
        val merged = (currentIds + newTrackIds).distinct()
        playlistDao.replacePlaylistTracks(playlistId, merged)
    }

    suspend fun removeTrackFromPlaylist(playlistId: Long, trackId: String) {
        val currentIds = playlistDao.getPlaylistTrackRefsOnce(playlistId)
            .sortedBy { it.position }
            .map { it.trackId }
            .filterNot { it == trackId }
        playlistDao.replacePlaylistTracks(playlistId, currentIds)
    }

    suspend fun reorderPlaylistTracks(playlistId: Long, orderedTrackIds: List<String>) {
        playlistDao.replacePlaylistTracks(playlistId, orderedTrackIds)
    }

    suspend fun excludeFolder(folderPath: String) {
        exclusionDao.excludeFolder(ExcludedFolderEntity(folderPath))
    }

    suspend fun includeFolder(folderPath: String) {
        exclusionDao.includeFolder(folderPath)
    }

    suspend fun addSearchQuery(query: String) {
        val clean = query.trim()
        if (clean.isNotEmpty()) {
            searchHistoryDao.upsertQuery(SearchHistoryEntity(query = clean))
        }
    }

    suspend fun deleteSearchQuery(query: String) {
        searchHistoryDao.deleteQuery(query)
    }

    suspend fun clearSearchHistory() {
        searchHistoryDao.clearAll()
    }

    fun sortTracks(tracks: List<Track>, option: SortOption, order: SortOrder): List<Track> {
        val comparator: Comparator<Track> = when (option) {
            SortOption.TITLE -> compareBy<Track, String>(String.CASE_INSENSITIVE_ORDER) { it.title }
            SortOption.ARTIST -> compareBy<Track, String>(String.CASE_INSENSITIVE_ORDER) { it.artist }
            SortOption.ALBUM -> compareBy<Track, String>(String.CASE_INSENSITIVE_ORDER) { it.album }
                .thenBy { it.trackNumber }
            SortOption.DURATION -> compareBy { it.durationMs }
            SortOption.DATE_ADDED -> compareBy { it.dateAddedSec }
        }
        val sorted = tracks.sortedWith(comparator)
        return if (order == SortOrder.DESCENDING) sorted.reversed() else sorted
    }

    companion object {
        fun albumKey(track: Track): String =
            "${track.album.trim().lowercase()}::${track.albumArtist.ifBlank { track.artist }.trim().lowercase()}"
    }

    private fun TrackEntity.toDomain(override: MetadataOverrideEntity?): Track = Track(
        id = id,
        mediaStoreId = mediaStoreId,
        contentUri = contentUri,
        title = override?.title?.takeIf { it.isNotBlank() } ?: title,
        artist = override?.artist?.takeIf { it.isNotBlank() } ?: artist,
        album = override?.album?.takeIf { it.isNotBlank() } ?: album,
        albumArtist = override?.albumArtist?.takeIf { it.isNotBlank() } ?: albumArtist,
        genre = override?.genre?.takeIf { it.isNotBlank() } ?: genre,
        durationMs = durationMs,
        trackNumber = trackNumber,
        year = override?.year ?: year,
        mimeType = mimeType,
        filePath = filePath,
        folderPath = folderPath,
        folderName = folderName,
        fileSizeBytes = fileSizeBytes,
        dateAddedSec = dateAddedSec,
        albumId = albumId,
        artworkUri = artworkUri,
        customArtworkUri = override?.customArtworkUri,
        playCount = playCount,
        lastPlayedAt = lastPlayedAt,
        isFavorite = isFavorite,
        isAvailable = isAvailable,
        sampleRateHz = sampleRateHz,
        bitrateKbps = bitrateKbps,
        dateModifiedSec = dateModifiedSec,
        rating = rating,
        skipCount = skipCount,
        lastSkippedAt = lastSkippedAt,
    )
}
