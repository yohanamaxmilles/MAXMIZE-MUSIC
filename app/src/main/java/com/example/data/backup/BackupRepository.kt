package com.example.data.backup

import android.content.Context
import android.net.Uri
import com.example.data.local.db.ExcludedFolderEntity
import com.example.data.local.db.ExclusionDao
import com.example.data.local.db.LyricsDao
import com.example.data.local.db.LyricsEntity
import com.example.data.local.db.MetadataOverrideEntity
import com.example.data.local.db.PlaylistDao
import com.example.data.local.db.PlaylistEntity
import com.example.data.local.db.TrackDao
import com.example.data.local.db.TrackEntity
import com.example.data.local.prefs.UserPreferencesRepository
import com.example.data.model.AccentPreset
import com.example.data.model.AppBackupData
import com.example.data.model.BackupLyrics
import com.example.data.model.BackupMetadataOverride
import com.example.data.model.BackupPlaylist
import com.example.data.model.BackupPreferences
import com.example.data.model.BackupTrackRef
import com.example.data.model.LyricsSource
import com.example.data.model.ThemeMode
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

class BackupRepository(
    private val context: Context,
    private val trackDao: TrackDao,
    private val playlistDao: PlaylistDao,
    private val exclusionDao: ExclusionDao,
    private val lyricsDao: LyricsDao,
    private val prefsRepo: UserPreferencesRepository,
) {
    private val json = Json {
        prettyPrint = true
        ignoreUnknownKeys = true
    }

    suspend fun exportBackupToUri(uri: Uri): Result<Int> = withContext(Dispatchers.IO) {
        runCatching {
            val tracksById = trackDao.getAllTracksOnce().associateBy { it.id }
            val playlists = playlistDao.getPlaylistsOnce().map { playlist ->
                val refs = playlistDao.getPlaylistTrackRefsOnce(playlist.id)
                val trackRefs = refs.mapNotNull { ref ->
                    tracksById[ref.trackId]?.toBackupRef()
                }
                BackupPlaylist(
                    name = playlist.name,
                    description = playlist.description,
                    createdAt = playlist.createdAt,
                    tracks = trackRefs,
                )
            }
            val favorites = tracksById.values.filter { it.isFavorite }.map { it.toBackupRef() }
            val excludedFolders = exclusionDao.getExcludedFoldersOnce().map { it.folderPath }
            val lyrics = lyricsDao.getAllLyricsOnce().mapNotNull { entity ->
                tracksById[entity.trackId]?.toBackupRef()?.let { ref ->
                    BackupLyrics(ref, entity.rawText, entity.isSynced)
                }
            }
            val overrides = trackDao.getAllOverridesOnce().mapNotNull { ov ->
                tracksById[ov.trackId]?.toBackupRef()?.let { ref ->
                    BackupMetadataOverride(
                        trackRef = ref,
                        title = ov.title,
                        artist = ov.artist,
                        album = ov.album,
                        genre = ov.genre,
                        year = ov.year,
                    )
                }
            }
            val prefs = prefsRepo.getOnce()
            val backup = AppBackupData(
                exportedAt = System.currentTimeMillis(),
                playlists = playlists,
                favorites = favorites,
                excludedFolders = excludedFolders,
                lyrics = lyrics,
                metadataOverrides = overrides,
                preferences = BackupPreferences(
                    themeMode = prefs.themeMode.name,
                    accentPreset = prefs.accentPreset.name,
                    eqEnabled = prefs.eqEnabled,
                    eqPreset = prefs.eqPreset,
                    eqBandLevels = prefs.eqBandLevelsMb,
                    bassBoost = prefs.bassBoostStrength,
                    virtualizer = prefs.virtualizerStrength,
                    playbackSpeed = prefs.playbackSpeed,
                    audioBalance = prefs.audioBalance,
                    crossfadeSeconds = prefs.crossfadeSeconds,
                    gaplessEnabled = prefs.gaplessEnabled,
                    minDurationSec = prefs.minDurationSec,
                    autoScanOnLaunch = prefs.autoScanOnLaunch,
                    resumePausedOnReopen = prefs.resumePausedOnReopen,
                ),
            )
            val payload = json.encodeToString(backup)
            context.contentResolver.openOutputStream(uri)?.use { out ->
                out.write(payload.toByteArray(Charsets.UTF_8))
            } ?: error("Unable to open destination file")
            playlists.size
        }
    }

    suspend fun importBackupFromUri(uri: Uri): Result<String> = withContext(Dispatchers.IO) {
        runCatching {
            val raw = context.contentResolver.openInputStream(uri)?.use { stream ->
                stream.bufferedReader().readText()
            } ?: error("Unable to read backup file")
            val backup = json.decodeFromString<AppBackupData>(raw)
            val allTracks = trackDao.getAllTracksOnce()

            var restoredPlaylists = 0
            for (bp in backup.playlists) {
                val matchedIds = bp.tracks.mapNotNull { ref -> findMatchingTrackId(ref, allTracks) }
                val newId = playlistDao.insertPlaylist(
                    PlaylistEntity(
                        name = bp.name,
                        description = bp.description,
                        createdAt = bp.createdAt,
                        updatedAt = System.currentTimeMillis(),
                    )
                )
                if (matchedIds.isNotEmpty()) {
                    playlistDao.replacePlaylistTracks(newId, matchedIds)
                }
                restoredPlaylists++
            }

            var restoredFavorites = 0
            for (favRef in backup.favorites) {
                val id = findMatchingTrackId(favRef, allTracks)
                if (id != null) {
                    trackDao.setFavorite(id, true)
                    restoredFavorites++
                }
            }

            for (folder in backup.excludedFolders) {
                exclusionDao.excludeFolder(ExcludedFolderEntity(folderPath = folder))
            }

            for (lyric in backup.lyrics) {
                val id = findMatchingTrackId(lyric.trackRef, allTracks)
                if (id != null) {
                    lyricsDao.upsertLyrics(
                        LyricsEntity(
                            trackId = id,
                            rawText = lyric.rawText,
                            isSynced = lyric.isSynced,
                            source = LyricsSource.MANUAL_OR_IMPORTED.name,
                        )
                    )
                }
            }

            for (ov in backup.metadataOverrides) {
                val id = findMatchingTrackId(ov.trackRef, allTracks)
                if (id != null) {
                    trackDao.upsertMetadataOverride(
                        MetadataOverrideEntity(
                            trackId = id,
                            title = ov.title,
                            artist = ov.artist,
                            album = ov.album,
                            albumArtist = ov.artist,
                            genre = ov.genre,
                            year = ov.year,
                            customArtworkUri = null,
                        )
                    )
                }
            }

            backup.preferences?.let { p ->
                runCatching { ThemeMode.valueOf(p.themeMode) }.getOrNull()?.let { prefsRepo.setThemeMode(it) }
                runCatching { AccentPreset.valueOf(p.accentPreset) }.getOrNull()?.let { prefsRepo.setAccentPreset(it) }
                prefsRepo.setEqSettings(p.eqEnabled, p.eqPreset, p.eqBandLevels, p.bassBoost, p.virtualizer)
                prefsRepo.setPlaybackSpeed(p.playbackSpeed)
                prefsRepo.setAudioBalance(p.audioBalance)
                prefsRepo.setCrossfadeSeconds(p.crossfadeSeconds)
                prefsRepo.setGaplessEnabled(p.gaplessEnabled)
                prefsRepo.setMinDurationSec(p.minDurationSec)
                prefsRepo.setAutoScanOnLaunch(p.autoScanOnLaunch)
                prefsRepo.setResumePausedOnReopen(p.resumePausedOnReopen)
            }

            "Restored $restoredPlaylists playlists & $restoredFavorites favorites"
        }
    }

    private fun findMatchingTrackId(ref: BackupTrackRef, tracks: List<TrackEntity>): String? {
        return tracks.firstOrNull { it.filePath.equals(ref.filePath, ignoreCase = true) }?.id
            ?: tracks.firstOrNull {
                it.title.equals(ref.title, ignoreCase = true) &&
                    it.artist.equals(ref.artist, ignoreCase = true)
            }?.id
    }

    private fun TrackEntity.toBackupRef(): BackupTrackRef =
        BackupTrackRef(
            filePath = filePath,
            title = title,
            artist = artist,
            album = album,
        )
}
