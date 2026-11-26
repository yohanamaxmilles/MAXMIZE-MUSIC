package com.example.ui.viewmodel

import android.content.IntentSender
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.AppContainer
import com.example.data.dj.DjProfiles
import com.example.data.local.db.CustomDjProfileEntity
import com.example.data.local.db.GenreRuleOverrideEntity
import com.example.data.local.db.MetadataUndoEntity
import com.example.data.local.prefs.UserPreferences
import com.example.data.metadata.ArtworkFetchResult
import com.example.data.metadata.MetadataEditor
import com.example.data.metadata.MetadataSaveResult
import com.example.data.metadata.SmartTrackFixSuggestion
import com.example.data.model.AccentPreset
import com.example.data.model.AlbumSummary
import com.example.data.model.ArtistSummary
import com.example.data.model.ArtworkSearchCandidate
import com.example.data.model.AutomixConfig
import com.example.data.model.AutomixUiState
import com.example.data.model.DjProfile
import com.example.data.model.EqualizerUiState
import com.example.data.model.FolderSummary
import com.example.data.model.FontChoice
import com.example.data.model.GenreSummary
import com.example.data.model.LibraryCategory
import com.example.data.model.LibraryStats
import com.example.data.model.MetadataHealthReport
import com.example.data.model.MixHistoryEntry
import com.example.data.model.PlaybackUiState
import com.example.data.model.Playlist
import com.example.data.model.PlaylistWithTracks
import com.example.data.model.ScanState
import com.example.data.model.SmartPlaylistType
import com.example.data.model.SortOption
import com.example.data.model.SortOrder
import com.example.data.model.ThemeMode
import com.example.data.model.Track
import com.example.data.model.TrackAnalysis
import com.example.data.model.UiDensity
import com.example.ui.screens.DjSectionTab
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.util.UUID

data class PendingWriteEdit(
    val track: Track,
    val title: String,
    val artist: String,
    val album: String,
    val genre: String,
    val year: Int?,
    val artworkUri: String?,
)

data class SearchResults(
    val query: String = "",
    val songs: List<Track> = emptyList(),
    val artists: List<ArtistSummary> = emptyList(),
    val albums: List<AlbumSummary> = emptyList(),
    val genres: List<GenreSummary> = emptyList(),
    val playlists: List<Playlist> = emptyList(),
    val folders: List<FolderSummary> = emptyList(),
) {
    val isEmpty: Boolean
        get() = songs.isEmpty() && artists.isEmpty() && albums.isEmpty() &&
            genres.isEmpty() && playlists.isEmpty() && folders.isEmpty()
}

class MaxmizeViewModel(val container: AppContainer) : ViewModel() {

    private val musicRepo = container.musicRepository
    private val prefsRepo = container.prefsRepo
    private val scanner = container.audioScanner
    private val lyricsRepo = container.lyricsRepository
    private val analysisEngine = container.audioAnalysisEngine
    private val automixRuntime = container.automixRuntime
    private val metadataEditor = container.metadataEditor
    private val artworkRepo = container.artworkRepository
    private val djConfigDao = container.database.djConfigDao()
    private val backupRepo = container.backupRepository
    val playbackController = container.playbackController
    val audioEffectsManager = container.audioEffectsManager

    val preferences: StateFlow<UserPreferences> = prefsRepo.preferencesFlow.stateIn(
        scope = viewModelScope,
        started = SharingStarted.Eagerly,
        initialValue = UserPreferences(),
    )

    val tracks: StateFlow<List<Track>> = musicRepo.tracksFlow.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = emptyList(),
    )

    val sortedTracks: StateFlow<List<Track>> = combine(
        tracks,
        preferences.map { it.sortOption to it.sortOrder }.distinctUntilChanged(),
    ) { list, (sortOption, sortOrder) ->
        musicRepo.sortTracks(list, sortOption, sortOrder)
    }.flowOn(Dispatchers.Default).stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = emptyList(),
    )

    val albums: StateFlow<List<AlbumSummary>> = musicRepo.albumsFlow.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = emptyList(),
    )

    val artists: StateFlow<List<ArtistSummary>> = musicRepo.artistsFlow.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = emptyList(),
    )

    val genres: StateFlow<List<GenreSummary>> = musicRepo.genresFlow.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = emptyList(),
    )

    val folders: StateFlow<List<FolderSummary>> = musicRepo.foldersFlow.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = emptyList(),
    )

    val excludedFolders: StateFlow<List<String>> = musicRepo.excludedFoldersFlow.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = emptyList(),
    )

    val playlists: StateFlow<List<Playlist>> = musicRepo.playlistsFlow.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = emptyList(),
    )

    val searchHistory: StateFlow<List<String>> = musicRepo.searchHistoryFlow.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = emptyList(),
    )

    val scanState: StateFlow<ScanState> = scanner.scanState
    val playbackState: StateFlow<PlaybackUiState> = playbackController.uiState
    val playbackPositionMs: StateFlow<Long> = playbackController.positionMsFlow
    val equalizerState: StateFlow<EqualizerUiState> = audioEffectsManager.uiState
    val automixState: StateFlow<AutomixUiState> = automixRuntime.uiState

    val trackAnalyses: StateFlow<Map<String, TrackAnalysis>> = analysisEngine.analysesFlow.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = emptyMap(),
    )

    val mixHistory: StateFlow<List<MixHistoryEntry>> = automixRuntime.mixHistoryFlow.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = emptyList(),
    )

    val customDjProfiles: StateFlow<List<DjProfile>> = djConfigDao.observeCustomProfiles()
        .map { DjProfiles.allProfiles(it) }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = DjProfiles.BUILT_IN_PROFILES,
        )

    val genreRules: StateFlow<List<GenreRuleOverrideEntity>> = djConfigDao.observeGenreRules().stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = emptyList(),
    )

    val undoHistory: StateFlow<List<MetadataUndoEntity>> = metadataEditor.undoHistoryFlow.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = emptyList(),
    )

    val failedArtworkCount: StateFlow<Int> = artworkRepo.failedEntriesFlow
        .map { it.size }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = 0,
        )

    @OptIn(FlowPreview::class)
    val metadataHealth: StateFlow<MetadataHealthReport> = tracks
        .debounce(150)
        .map { MetadataEditor.analyzeLibraryHealth(it) }
        .flowOn(Dispatchers.Default)
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = MetadataHealthReport(),
        )

    @OptIn(FlowPreview::class)
    val libraryStats: StateFlow<LibraryStats> = combine(tracks, trackAnalyses) { list, analyses ->
        list to analyses
    }
        .debounce(150)
        .map { (list, analyses) ->
            LibraryStats(
                totalTracks = list.size,
                totalDurationMs = list.sumOf { it.durationMs },
                totalSizeBytes = list.sumOf { it.fileSizeBytes },
                totalPlays = list.sumOf { it.playCount },
                totalSkips = list.sumOf { it.skipCount },
                favoriteCount = list.count { it.isFavorite },
                analyzedCount = list.count { analyses.containsKey(it.id) },
                topGenres = list.groupBy { it.genre.ifBlank { "Unknown" } }
                    .map { (g, t) -> g to t.size }
                    .sortedByDescending { it.second }
                    .take(6),
                topArtists = list.groupBy { it.artist.ifBlank { "Unknown Artist" } }
                    .map { (a, t) -> a to t.sumOf { tr -> tr.playCount.coerceAtLeast(1) } }
                    .sortedByDescending { it.second }
                    .take(6),
                topTracks = list.filter { it.playCount > 0 }.sortedByDescending { it.playCount }.take(5),
                formatBreakdown = list.groupBy { it.formatLabel }
                    .map { (f, t) -> f to t.size }
                    .sortedByDescending { it.second },
            )
        }
        .flowOn(Dispatchers.Default)
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = LibraryStats(),
        )

    val smartPlaylistCounts: StateFlow<Map<SmartPlaylistType, Int>> = combine(tracks, trackAnalyses) { all, analyses ->
        var recentlyPlayed = 0
        var mostPlayed = 0
        var favorites = 0
        var topRated = 0
        var highEnergy = 0
        var chillVibes = 0
        var recentlySkipped = 0
        for (track in all) {
            if (track.lastPlayedAt > 0L) recentlyPlayed++
            if (track.playCount > 0) mostPlayed++
            if (track.isFavorite) favorites++
            if (track.rating >= 4) topRated++
            if (track.skipCount > 0) recentlySkipped++
            val a = analyses[track.id]
            val g = track.genre
            if ((a != null && (a.energy >= 0.58f || a.bpm >= 118f)) ||
                g.contains("dance", true) ||
                g.contains("edm", true) ||
                g.contains("hip", true) ||
                g.contains("pop", true)
            ) {
                highEnergy++
            }
            if ((a != null && a.energy <= 0.58f) ||
                g.contains("jazz", true) ||
                g.contains("acoustic", true) ||
                g.contains("ambient", true) ||
                g.contains("chill", true) ||
                g.contains("r&b", true)
            ) {
                chillVibes++
            }
        }
        mapOf(
            SmartPlaylistType.RECENTLY_PLAYED to minOf(recentlyPlayed, 50),
            SmartPlaylistType.MOST_PLAYED to minOf(mostPlayed, 50),
            SmartPlaylistType.RECENTLY_ADDED to minOf(all.size, 50),
            SmartPlaylistType.FAVORITES to favorites,
            SmartPlaylistType.TOP_RATED to topRated,
            SmartPlaylistType.HIGH_ENERGY to highEnergy,
            SmartPlaylistType.CHILL_VIBES to chillVibes,
            SmartPlaylistType.RECENTLY_SKIPPED to minOf(recentlySkipped, 50),
        )
    }.flowOn(Dispatchers.Default).stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = emptyMap(),
    )

    private val _selectedLibraryCategory = MutableStateFlow<LibraryCategory?>(null)
    val selectedLibraryCategory: StateFlow<LibraryCategory?> = _selectedLibraryCategory.asStateFlow()

    private val _selectedDjTab = MutableStateFlow(DjSectionTab.HOME)
    val selectedDjTab: StateFlow<DjSectionTab> = _selectedDjTab.asStateFlow()

    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()

    val searchResults: StateFlow<SearchResults> = combine(
        _searchQuery,
        tracks,
        artists,
        albums,
        genres,
    ) { query, allTracks, allArtists, allAlbums, allGenres ->
        val q = query.trim().lowercase()
        if (q.isEmpty()) {
            SearchResults()
        } else {
            SearchResults(
                query = query,
                songs = allTracks.filter {
                    it.title.lowercase().contains(q) ||
                        it.artist.lowercase().contains(q) ||
                        it.album.lowercase().contains(q)
                },
                artists = allArtists.filter { it.artistName.lowercase().contains(q) },
                albums = allAlbums.filter {
                    it.albumName.lowercase().contains(q) || it.artistName.lowercase().contains(q)
                },
                genres = allGenres.filter { it.genreName.lowercase().contains(q) },
            )
        }
    }.combine(combine(playlists, folders) { p, f -> p to f }) { base, (allPlaylists, allFolders) ->
        val q = base.query.trim().lowercase()
        if (q.isEmpty()) base else base.copy(
            playlists = allPlaylists.filter {
                it.name.lowercase().contains(q) || it.description.lowercase().contains(q)
            },
            folders = allFolders.filter {
                it.folderName.lowercase().contains(q) || it.folderPath.lowercase().contains(q)
            },
        )
    }.flowOn(Dispatchers.Default).stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = SearchResults(),
    )

    private val _lyricsBatchStatus = MutableStateFlow<String?>(null)
    val lyricsBatchStatus: StateFlow<String?> = _lyricsBatchStatus.asStateFlow()

    private val _artworkCandidates = MutableStateFlow<List<ArtworkSearchCandidate>>(emptyList())
    val artworkCandidates: StateFlow<List<ArtworkSearchCandidate>> = _artworkCandidates.asStateFlow()

    private val _isSearchingArtwork = MutableStateFlow(false)
    val isSearchingArtwork: StateFlow<Boolean> = _isSearchingArtwork.asStateFlow()

    private val _artworkBatchStatus = MutableStateFlow<String?>(null)
    val artworkBatchStatus: StateFlow<String?> = _artworkBatchStatus.asStateFlow()

    private var lyricsBatchJob: Job? = null
    private var artworkSearchJob: Job? = null
    private var artworkBatchJob: Job? = null
    private var analysisBatchJob: Job? = null

    private val _snackbarMessages = MutableSharedFlow<String>(extraBufferCapacity = 8)
    val snackbarMessages: SharedFlow<String> = _snackbarMessages.asSharedFlow()

    private val _pendingWriteIntent = MutableSharedFlow<Pair<IntentSender, PendingWriteEdit>>(extraBufferCapacity = 2)
    val pendingWriteIntent: SharedFlow<Pair<IntentSender, PendingWriteEdit>> = _pendingWriteIntent.asSharedFlow()

    init {
        scanner.startWatchingMediaStore()
    }

    override fun onCleared() {
        scanner.stopWatchingMediaStore()
        super.onCleared()
    }

    fun hasAudioPermission(): Boolean = scanner.hasAudioPermission()

    fun startMediaStoreWatcher() {
        scanner.startWatchingMediaStore()
    }

    fun scanLibrary(forceRefresh: Boolean = false) {
        viewModelScope.launch {
            val count = scanner.scanLibrary(forceMetadataRefresh = forceRefresh)
            _snackbarMessages.tryEmit(
                if (count == 0) "No local audio files found" else "Scanned $count tracks"
            )
            if (count > 0 && prefsRepo.getOnce().autoFixArtworkOnScan) {
                batchFixMissingArtwork(silentIfNone = true)
            }
        }
    }

    fun autoScanIfEnabled() {
        viewModelScope.launch {
            if (prefsRepo.getOnce().autoScanOnLaunch) {
                val count = scanner.scanLibrary(forceMetadataRefresh = false)
                if (count > 0 && prefsRepo.getOnce().autoFixArtworkOnScan) {
                    batchFixMissingArtwork(silentIfNone = true)
                }
            }
        }
    }

    fun importSafFolder(uri: Uri) {
        viewModelScope.launch {
            val count = scanner.importSafSource(uri, isTree = true)
            _snackbarMessages.tryEmit("Imported folder ($count total tracks indexed)")
        }
    }

    fun importSafFile(uri: Uri) {
        viewModelScope.launch {
            val count = scanner.importSafSource(uri, isTree = false)
            _snackbarMessages.tryEmit("Imported audio file ($count total tracks indexed)")
        }
    }

    fun selectLibraryCategory(category: LibraryCategory?) {
        _selectedLibraryCategory.value = category
    }

    fun selectDjTab(tab: DjSectionTab) {
        _selectedDjTab.value = tab
    }

    fun playPlaylist(playlistId: Long, shuffle: Boolean = false) {
        viewModelScope.launch {
            val tracks = musicRepo.observePlaylistWithTracks(playlistId).first()?.tracks.orEmpty()
            if (tracks.isNotEmpty()) {
                playbackController.playTracks(tracks, 0, shuffle)
            } else {
                _snackbarMessages.tryEmit("Playlist is empty")
            }
        }
    }

    fun smartPlaylistTracks(type: SmartPlaylistType): List<Track> {
        val all = tracks.value
        val analyses = trackAnalyses.value
        return when (type) {
            SmartPlaylistType.RECENTLY_PLAYED ->
                all.filter { it.lastPlayedAt > 0L }.sortedByDescending { it.lastPlayedAt }.take(50)
            SmartPlaylistType.MOST_PLAYED ->
                all.filter { it.playCount > 0 }.sortedByDescending { it.playCount }.take(50)
            SmartPlaylistType.RECENTLY_ADDED ->
                all.sortedByDescending { it.dateAddedSec }.take(50)
            SmartPlaylistType.FAVORITES ->
                all.filter { it.isFavorite }
            SmartPlaylistType.TOP_RATED ->
                all.filter { it.rating >= 4 }
                    .sortedWith(compareByDescending<Track> { it.rating }.thenByDescending { it.playCount })
            SmartPlaylistType.HIGH_ENERGY ->
                all.filter {
                    val a = analyses[it.id]
                    (a != null && (a.energy >= 0.58f || a.bpm >= 118f)) ||
                        it.genre.contains("dance", true) ||
                        it.genre.contains("edm", true) ||
                        it.genre.contains("hip", true) ||
                        it.genre.contains("pop", true)
                }.sortedByDescending { analyses[it.id]?.energy ?: 0.5f }
            SmartPlaylistType.CHILL_VIBES ->
                all.filter {
                    val a = analyses[it.id]
                    (a != null && a.energy <= 0.58f) ||
                        it.genre.contains("jazz", true) ||
                        it.genre.contains("acoustic", true) ||
                        it.genre.contains("ambient", true) ||
                        it.genre.contains("chill", true) ||
                        it.genre.contains("r&b", true)
                }.sortedBy { analyses[it.id]?.energy ?: 0.5f }
            SmartPlaylistType.RECENTLY_SKIPPED ->
                all.filter { it.skipCount > 0 }
                    .sortedWith(compareByDescending<Track> { it.lastSkippedAt }.thenByDescending { it.skipCount })
                    .take(50)
        }
    }

    fun setSort(option: SortOption, order: SortOrder) {
        viewModelScope.launch { prefsRepo.setSort(option, order) }
    }

    fun toggleFavorite(trackId: String) {
        viewModelScope.launch { musicRepo.toggleFavorite(trackId) }
    }

    fun setTrackRating(trackId: String, rating: Int) {
        viewModelScope.launch {
            musicRepo.setTrackRating(trackId, rating)
            _snackbarMessages.tryEmit("Rated track $rating/5")
        }
    }

    fun observePlaylistWithTracks(playlistId: Long): Flow<PlaylistWithTracks?> =
        musicRepo.observePlaylistWithTracks(playlistId)

    fun createPlaylist(name: String, description: String = "", trackIds: List<String> = emptyList()) {
        viewModelScope.launch {
            musicRepo.createPlaylist(name, description, trackIds)
            _snackbarMessages.tryEmit("Created playlist \"$name\"")
        }
    }

    fun renameAndDescribePlaylist(playlistId: Long, name: String, description: String) {
        viewModelScope.launch {
            musicRepo.renameAndDescribePlaylist(playlistId, name, description)
            _snackbarMessages.tryEmit("Updated playlist")
        }
    }

    fun deletePlaylist(playlistId: Long) {
        viewModelScope.launch {
            musicRepo.deletePlaylist(playlistId)
            _snackbarMessages.tryEmit("Playlist deleted")
        }
    }

    fun addTracksToPlaylist(playlistId: Long, trackIds: List<String>) {
        viewModelScope.launch {
            musicRepo.addTracksToPlaylist(playlistId, trackIds)
            _snackbarMessages.tryEmit("Added ${trackIds.size} track(s) to playlist")
        }
    }

    fun removeTrackFromPlaylist(playlistId: Long, trackId: String) {
        viewModelScope.launch {
            musicRepo.removeTrackFromPlaylist(playlistId, trackId)
        }
    }

    fun reorderPlaylistTracks(playlistId: Long, orderedIds: List<String>) {
        viewModelScope.launch {
            musicRepo.reorderPlaylistTracks(playlistId, orderedIds)
        }
    }

    fun excludeFolder(folderPath: String) {
        viewModelScope.launch {
            musicRepo.excludeFolder(folderPath)
            scanner.scanLibrary(false)
            _snackbarMessages.tryEmit("Excluded folder")
        }
    }

    fun includeFolder(folderPath: String) {
        viewModelScope.launch {
            musicRepo.includeFolder(folderPath)
            scanner.scanLibrary(false)
            _snackbarMessages.tryEmit("Restored folder")
        }
    }

    fun updateSearchQuery(query: String) {
        _searchQuery.value = query
    }

    fun commitSearchQuery(query: String) {
        viewModelScope.launch { musicRepo.addSearchQuery(query) }
    }

    fun deleteSearchHistoryItem(query: String) {
        viewModelScope.launch { musicRepo.deleteSearchQuery(query) }
    }

    fun clearSearchHistory() {
        viewModelScope.launch { musicRepo.clearSearchHistory() }
    }

    // --- Lyrics Settings & Batch Library Actions ---

    fun setAutoDownloadLyrics(enabled: Boolean) {
        viewModelScope.launch { prefsRepo.setAutoDownloadLyrics(enabled) }
    }

    fun setLyricsWifiOnly(wifiOnly: Boolean) {
        viewModelScope.launch { prefsRepo.setLyricsWifiOnly(wifiOnly) }
    }

    fun setDownloadLyricsOnPlayStart(enabled: Boolean) {
        viewModelScope.launch { prefsRepo.setDownloadLyricsOnPlayStart(enabled) }
    }

    fun setMusixmatchApiKey(key: String) {
        viewModelScope.launch {
            prefsRepo.setMusixmatchApiKey(key)
            _snackbarMessages.tryEmit("Updated Musixmatch API key")
        }
    }

    fun batchFetchMissingLyrics() {
        val list = tracks.value
        if (list.isEmpty()) {
            _snackbarMessages.tryEmit("Scan your music library first")
            return
        }
        lyricsBatchJob?.cancel()
        lyricsBatchJob = viewModelScope.launch {
            _lyricsBatchStatus.value = "Preparing..."
            val found = lyricsRepo.batchFetchMissingLyrics(list) { done, total, count ->
                _lyricsBatchStatus.value = "Fetched $count found ($done/$total)"
            }
            _lyricsBatchStatus.value = null
            _snackbarMessages.tryEmit("Lyrics fetch complete ($found downloaded)")
        }
    }

    fun clearLyricsCache() {
        viewModelScope.launch {
            lyricsRepo.clearAllLyricsCache()
            _snackbarMessages.tryEmit("Cleared cached lyrics")
        }
    }

    // --- Smart Artwork & Metadata Health Engine ---

    fun searchArtworkForTrack(track: Track) {
        artworkSearchJob?.cancel()
        _isSearchingArtwork.value = true
        _artworkCandidates.value = emptyList()
        artworkSearchJob = viewModelScope.launch {
            val found = artworkRepo.searchCandidates(track)
            _artworkCandidates.value = found
            _isSearchingArtwork.value = false
        }
    }

    fun clearArtworkCandidates() {
        artworkSearchJob?.cancel()
        _artworkCandidates.value = emptyList()
        _isSearchingArtwork.value = false
    }

    fun applyArtworkCandidate(
        track: Track,
        candidate: ArtworkSearchCandidate,
        onApplied: ((String) -> Unit)? = null,
    ) {
        viewModelScope.launch {
            val localUri = artworkRepo.applyCandidateForTrack(track, candidate)
            if (localUri != null) {
                onApplied?.invoke(localUri)
                _snackbarMessages.tryEmit("Applied verified artwork (${candidate.provider})")
            } else {
                _snackbarMessages.tryEmit("Failed to download artwork image")
            }
        }
    }

    fun autoFixArtworkForTrack(track: Track) {
        smartAutoFixTrack(track)
    }

    fun smartAutoFixTrack(track: Track) {
        viewModelScope.launch {
            _snackbarMessages.tryEmit("Searching tags & cover for \"${track.title}\"...")
            val result = artworkRepo.smartAutoFixAndEmbed(
                track = track,
                metadataEditor = metadataEditor,
                allowMediumAutoApply = true,
            )
            when (result) {
                is MetadataSaveResult.SavedToAudioFileAndDb -> {
                    _snackbarMessages.tryEmit(
                        if (result.embeddedCover) "Fixed tags & embedded cover into audio file"
                        else "Fixed song tags & embedded into audio file"
                    )
                }
                is MetadataSaveResult.SavedToMediaStoreAndDb,
                is MetadataSaveResult.SavedToDbOnly -> {
                    _snackbarMessages.tryEmit("Fixed song info & cover in Maxmize library")
                }
                is MetadataSaveResult.NeedsWriteConsent -> {
                    val suggestion = artworkRepo.resolveSmartTrackFix(track, allowNetwork = true)
                    _pendingWriteIntent.tryEmit(
                        result.intentSender to PendingWriteEdit(
                            track = track,
                            title = suggestion.title,
                            artist = suggestion.artist,
                            album = suggestion.album,
                            genre = suggestion.genre,
                            year = suggestion.year,
                            artworkUri = suggestion.localArtworkUri ?: track.customArtworkUri,
                        )
                    )
                }
                null -> {
                    val candidates = artworkRepo.searchCandidates(track)
                    if (candidates.isNotEmpty()) {
                        _artworkCandidates.value = candidates
                        _snackbarMessages.tryEmit("Found candidates — open Edit Metadata to pick")
                    } else {
                        _snackbarMessages.tryEmit("No confident online match found for \"${track.title}\"")
                    }
                }
            }
        }
    }

    suspend fun previewSmartAutoFix(track: Track): SmartTrackFixSuggestion {
        return artworkRepo.resolveSmartTrackFix(track, allowNetwork = true)
    }

    fun batchFixMissingArtwork(silentIfNone: Boolean = false) {
        val list = tracks.value
        if (list.isEmpty()) {
            if (!silentIfNone) _snackbarMessages.tryEmit("Scan your music library first")
            return
        }
        artworkBatchJob?.cancel()
        artworkBatchJob = viewModelScope.launch {
            _artworkBatchStatus.value = "Checking tags & covers..."
            val applied = artworkRepo.batchFixMissingArtwork(list, metadataEditor) { done, total, count ->
                if (total > 0) {
                    _artworkBatchStatus.value = "Fixed $count ($done/$total)"
                }
            }
            _artworkBatchStatus.value = null
            if (!silentIfNone || applied > 0) {
                _snackbarMessages.tryEmit(
                    if (applied > 0) "Fixed tags & embedded covers for $applied tracks"
                    else "No missing tags or confident covers needed"
                )
            }
        }
    }

    fun retryFailedArtwork() {
        val list = tracks.value
        artworkBatchJob?.cancel()
        artworkBatchJob = viewModelScope.launch {
            _artworkBatchStatus.value = "Retrying..."
            val applied = artworkRepo.retryFailedArtwork(list) { done, total, count ->
                if (total > 0) {
                    _artworkBatchStatus.value = "Retried $done/$total ($count fixed)"
                }
            }
            _artworkBatchStatus.value = null
            _snackbarMessages.tryEmit("Retry complete ($applied fixed)")
        }
    }

    fun clearArtworkCache() {
        viewModelScope.launch {
            artworkRepo.clearArtworkCache()
            _snackbarMessages.tryEmit("Cleared artwork cache")
        }
    }

    fun undoLastEditForTrack(trackId: String) {
        viewModelScope.launch {
            val ok = metadataEditor.undoLastEditForTrack(trackId)
            _snackbarMessages.tryEmit(if (ok) "Reverted metadata edit" else "Nothing to undo")
        }
    }

    fun undoMetadataEntry(entry: MetadataUndoEntity) {
        viewModelScope.launch {
            metadataEditor.undoEntry(entry)
            _snackbarMessages.tryEmit("Reverted metadata change")
        }
    }

    // --- Automix & DJ Intelligence Engine ---

    fun startAutomix(seedTrackId: String? = null) {
        if (tracks.value.isEmpty()) {
            _snackbarMessages.tryEmit("Add local audio files before starting Automix")
            return
        }
        playbackController.startAutomix(seedTrackId)
    }

    fun stopAutomix() {
        playbackController.stopAutomix()
    }

    fun toggleAutomix() {
        if (automixState.value.isActive) stopAutomix() else startAutomix()
    }

    fun updateAutomixConfig(transform: (AutomixConfig) -> AutomixConfig) {
        viewModelScope.launch {
            prefsRepo.updateAutomixConfig(transform)
            // Service observes preferencesFlow and refreshes DJ queue asynchronously without audio interruption
        }
    }

    fun applyDjProfile(profile: DjProfile) {
        viewModelScope.launch {
            prefsRepo.updateAutomixConfig { current ->
                DjProfiles.applyProfile(current, profile)
            }
            _snackbarMessages.tryEmit("Applied DJ profile: ${profile.name}")
        }
    }

    fun saveCustomDjProfile(name: String, description: String) {
        val cleanName = name.trim().ifBlank { "Custom DJ Profile" }
        viewModelScope.launch {
            val cfg = preferences.value.automixConfig
            val id = "custom_${UUID.randomUUID().toString().take(8)}"
            val profile = DjProfile(
                id = id,
                name = cleanName,
                description = description.trim().ifBlank { "Saved custom Automix configuration" },
                isBuiltIn = false,
                mixMode = cfg.mode,
                bpmMin = cfg.bpmMin,
                bpmMax = cfg.bpmMax,
                preferredGenres = if (cfg.genrePreference == "All Genres") emptyList() else listOf(cfg.genrePreference),
                strictGenreMatching = cfg.strictGenreMatching,
                harmonicMixLevel = cfg.harmonicMixLevel,
                energyProgression = cfg.energyProgression,
                transitionStyle = cfg.transitionStyle,
                transitionDurationSec = cfg.transitionDurationSec,
                keyLockEnabled = cfg.keyLockEnabled,
                phraseMatchEnabled = cfg.phraseMatchEnabled,
                bpmWeight = cfg.bpmWeight,
                harmonicWeight = cfg.harmonicWeight,
                energyWeight = cfg.energyWeight,
                genreWeight = cfg.genreWeight,
                openFormatBridging = cfg.openFormatBridging,
            )
            djConfigDao.upsertProfile(with(DjProfiles) { profile.toEntity() })
            prefsRepo.updateAutomixConfig { it.copy(activeProfileId = id) }
            _snackbarMessages.tryEmit("Saved custom profile \"$cleanName\"")
        }
    }

    fun deleteCustomDjProfile(profileId: String) {
        viewModelScope.launch {
            djConfigDao.deleteProfile(profileId)
            if (preferences.value.automixConfig.activeProfileId == profileId) {
                prefsRepo.updateAutomixConfig { it.copy(activeProfileId = DjProfiles.BUILT_IN_PROFILES.first().id) }
            }
            _snackbarMessages.tryEmit("Deleted custom DJ profile")
        }
    }

    fun upsertGenreRule(genreA: String, genreB: String, allowed: Boolean) {
        val a = genreA.trim()
        val b = genreB.trim()
        if (a.isBlank() || b.isBlank()) return
        viewModelScope.launch {
            djConfigDao.upsertGenreRule(
                GenreRuleOverrideEntity(
                    genreA = a,
                    genreB = b,
                    allowed = allowed,
                )
            )
            _snackbarMessages.tryEmit(
                if (allowed) "Allowed transition: $a <-> $b" else "Blocked transition: $a <-> $b"
            )
        }
    }

    fun deleteGenreRule(genreA: String, genreB: String) {
        viewModelScope.launch {
            djConfigDao.deleteGenreRule(genreA, genreB)
        }
    }

    fun analyzeLibraryBatch() {
        val all = tracks.value
        if (all.isEmpty()) {
            _snackbarMessages.tryEmit("No tracks to analyze")
            return
        }
        analysisBatchJob?.cancel()
        analysisBatchJob = viewModelScope.launch {
            automixRuntime.updateState {
                it.copy(isAnalyzing = true, analyzedCount = 0, totalTrackCount = all.size)
            }
            val newlyAnalyzed = analysisEngine.analyzeLibraryBatch(all) { done, total ->
                automixRuntime.updateState {
                    it.copy(isAnalyzing = true, analyzedCount = done, totalTrackCount = total)
                }
            }
            automixRuntime.updateState {
                it.copy(isAnalyzing = false, analyzedCount = trackAnalyses.value.size, totalTrackCount = all.size)
            }
            playbackController.recomputeDjQueue()
            _snackbarMessages.tryEmit(
                if (newlyAnalyzed > 0) "Analyzed BPM, Key, Genre & Energy for $newlyAnalyzed tracks"
                else "All ${all.size} tracks already analyzed"
            )
        }
    }

    fun correctTrackAnalysis(track: Track, bpm: Float, musicalKey: String) {
        viewModelScope.launch {
            analysisEngine.updateManualCorrection(track, bpm, musicalKey)
            playbackController.recomputeDjQueue()
            _snackbarMessages.tryEmit("Updated ${track.title} (${bpm.toInt()} BPM · $musicalKey)")
        }
    }

    fun clearMixHistory() {
        viewModelScope.launch {
            container.database.mixHistoryDao().clearHistory()
            _snackbarMessages.tryEmit("Cleared mix history")
        }
    }

    // --- Metadata Editing & Settings ---

    fun saveTrackMetadata(
        track: Track,
        title: String,
        artist: String,
        album: String,
        genre: String,
        year: Int?,
        artworkUri: String?,
    ) {
        viewModelScope.launch {
            when (val res = metadataEditor.saveMetadataEdit(track, title, artist, album, genre, year, artworkUri)) {
                is MetadataSaveResult.SavedToAudioFileAndDb -> {
                    _snackbarMessages.tryEmit(
                        if (res.embeddedCover) "Embedded tags & cover into audio file"
                        else "Embedded tags into audio file"
                    )
                }
                is MetadataSaveResult.SavedToMediaStoreAndDb -> {
                    _snackbarMessages.tryEmit("Updated audio file and library metadata")
                }
                is MetadataSaveResult.SavedToDbOnly -> {
                    _snackbarMessages.tryEmit("Saved metadata changes to Maxmize library")
                }
                is MetadataSaveResult.NeedsWriteConsent -> {
                    _pendingWriteIntent.tryEmit(
                        res.intentSender to PendingWriteEdit(track, title, artist, album, genre, year, artworkUri)
                    )
                }
            }
        }
    }

    fun completePendingMediaStoreWrite(pending: PendingWriteEdit) {
        viewModelScope.launch {
            val ok = metadataEditor.commitPendingMediaStoreUpdate(
                track = pending.track,
                title = pending.title,
                artist = pending.artist,
                album = pending.album,
                genre = pending.genre,
                year = pending.year,
                artworkUri = pending.artworkUri,
            )
            _snackbarMessages.tryEmit(
                if (ok) "Embedded tags & cover into audio file"
                else "Saved changes in Maxmize library"
            )
        }
    }

    fun setThemeMode(mode: ThemeMode) {
        viewModelScope.launch { prefsRepo.setThemeMode(mode) }
    }

    fun setAccentPreset(preset: AccentPreset) {
        viewModelScope.launch { prefsRepo.setAccentPreset(preset) }
    }

    fun setFontChoice(choice: FontChoice) {
        viewModelScope.launch { prefsRepo.setFontChoice(choice) }
    }

    fun setFontScale(scale: Float) {
        viewModelScope.launch { prefsRepo.setFontScale(scale) }
    }

    fun setUiDensity(density: UiDensity) {
        viewModelScope.launch { prefsRepo.setUiDensity(density) }
    }

    fun setGaplessEnabled(enabled: Boolean) {
        viewModelScope.launch { prefsRepo.setGaplessEnabled(enabled) }
    }

    fun setSkipSilenceEnabled(enabled: Boolean) {
        playbackController.setSkipSilenceEnabled(enabled)
    }

    fun setLoudnessNormalizationEnabled(enabled: Boolean) {
        playbackController.setLoudnessNormalizationEnabled(enabled)
    }

    fun setAutoDownloadArtwork(enabled: Boolean) {
        viewModelScope.launch { prefsRepo.setAutoDownloadArtwork(enabled) }
    }

    fun setArtworkWifiOnly(wifiOnly: Boolean) {
        viewModelScope.launch { prefsRepo.setArtworkWifiOnly(wifiOnly) }
    }

    fun setAutoFixArtworkOnScan(enabled: Boolean) {
        viewModelScope.launch { prefsRepo.setAutoFixArtworkOnScan(enabled) }
    }

    fun setMinDurationSec(seconds: Int) {
        viewModelScope.launch {
            prefsRepo.setMinDurationSec(seconds)
            scanner.scanLibrary(false)
        }
    }

    fun setAutoScanOnLaunch(enabled: Boolean) {
        viewModelScope.launch { prefsRepo.setAutoScanOnLaunch(enabled) }
    }

    fun setResumePausedOnReopen(enabled: Boolean) {
        viewModelScope.launch { prefsRepo.setResumePausedOnReopen(enabled) }
    }

    fun setShowNotificationSeekButtons(enabled: Boolean) {
        viewModelScope.launch { prefsRepo.setShowNotificationSeekButtons(enabled) }
    }

    fun exportBackup(uri: Uri) {
        viewModelScope.launch {
            backupRepo.exportBackupToUri(uri)
                .onSuccess { count -> _snackbarMessages.tryEmit("Exported backup ($count playlists)") }
                .onFailure { _snackbarMessages.tryEmit("Export failed: ${it.localizedMessage}") }
        }
    }

    fun importBackup(uri: Uri) {
        viewModelScope.launch {
            backupRepo.importBackupFromUri(uri)
                .onSuccess { msg -> _snackbarMessages.tryEmit(msg) }
                .onFailure { _snackbarMessages.tryEmit("Import failed: ${it.localizedMessage}") }
        }
    }

    fun notifyMessage(msg: String) {
        _snackbarMessages.tryEmit(msg)
    }

    class Factory(private val container: AppContainer) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            return MaxmizeViewModel(container) as T
        }
    }
}
