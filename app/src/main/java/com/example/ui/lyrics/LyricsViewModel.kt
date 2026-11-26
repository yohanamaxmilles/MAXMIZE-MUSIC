package com.example.ui.lyrics

import android.net.Uri
import android.os.SystemClock
import androidx.compose.runtime.Immutable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.AppContainer
import com.example.data.lyrics.LrcFormatter
import com.example.data.model.LyricLine
import com.example.data.model.LyricsDocument
import com.example.data.model.LyricsError
import com.example.data.model.LyricsLookupOutcome
import com.example.data.model.LyricsSearchCandidate
import com.example.data.model.Track
import com.example.player.PlaybackClockSnapshot
import com.example.player.PlaybackController
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

@Immutable
sealed class LyricsScreenUiState {
    data object Idle : LyricsScreenUiState()

    data class Loading(val track: Track) : LyricsScreenUiState()

    data class Content(
        val track: Track,
        val document: LyricsDocument,
        val activeLineIndex: Int,
        val activeWordIndex: Int,
        val playbackPositionMs: Long = 0L,
        val durationMs: Long,
        val isPlaying: Boolean,
        val isBoundToActiveTrack: Boolean,
        val isAutoFollowEnabled: Boolean,
    ) : LyricsScreenUiState()

    data class Empty(
        val track: Track,
        val reason: String? = null,
    ) : LyricsScreenUiState()

    data class Error(
        val track: Track,
        val message: String,
        val isOffline: Boolean,
    ) : LyricsScreenUiState()
}

@Immutable
data class LyricsSearchUiState(
    val isSearching: Boolean = false,
    val candidates: List<LyricsSearchCandidate> = emptyList(),
    val errorMessage: String? = null,
)

class LyricsViewModel(container: AppContainer) : ViewModel() {

    companion object {
        private const val AUTO_FOLLOW_RESUME_DELAY_MS = 4_500L
    }

    private val lyricsRepo = container.lyricsRepository
    private val prefsRepo = container.prefsRepo
    val playbackController = container.playbackController

    private val _overrideTrack = MutableStateFlow<Track?>(null)
    private val _loadedTrack = MutableStateFlow<Track?>(null)
    private val _loadedOutcome = MutableStateFlow<LyricsLookupOutcome?>(null)
    private val _isLoading = MutableStateFlow(false)
    private val _isAutoFollowEnabled = MutableStateFlow(true)
    private val _isLyricsScreenActive = MutableStateFlow(false)
    private val _searchState = MutableStateFlow(LyricsSearchUiState())
    val searchState: StateFlow<LyricsSearchUiState> = _searchState.asStateFlow()

    private val _playbackPositionMs = MutableStateFlow(0L)
    val playbackPositionMs: StateFlow<Long> = _playbackPositionMs.asStateFlow()

    private val _activeIndices = MutableStateFlow(-1 to -1)

    private val _statusMessages = MutableSharedFlow<String>(extraBufferCapacity = 8)
    val statusMessages: SharedFlow<String> = _statusMessages.asSharedFlow()

    val fontSizeSp: StateFlow<Float> = prefsRepo.preferencesFlow
        .map { it.lyricsFontSizeSp }
        .distinctUntilChanged()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 22f)

    private var loadJob: Job? = null
    private var searchJob: Job? = null
    private var autoFollowResumeJob: Job? = null
    private var syncTickerJob: Job? = null

    val targetTrack: StateFlow<Track?> = combine(
        _overrideTrack,
        playbackController.uiState.map { it.currentTrack }.distinctUntilChanged { a, b ->
            a?.id == b?.id && a?.title == b?.title && a?.artist == b?.artist
        },
    ) { overrideTrack, activeTrack ->
        overrideTrack ?: activeTrack
    }.stateIn(viewModelScope, SharingStarted.Eagerly, null)

    private data class PlaybackMetaSnapshot(
        val currentTrackId: String?,
        val durationMs: Long,
        val isPlaying: Boolean,
    )

    private val playbackMetaFlow = playbackController.uiState
        .map { ui ->
            PlaybackMetaSnapshot(
                currentTrackId = ui.currentTrack?.id,
                durationMs = ui.durationMs,
                isPlaying = ui.isPlaying,
            )
        }
        .distinctUntilChanged()

    private data class LoadSnapshot(
        val track: Track?,
        val isLoading: Boolean,
        val outcome: LyricsLookupOutcome?,
        val isAutoFollow: Boolean,
    )

    private val loadSnapshotFlow = combine(
        targetTrack,
        _isLoading,
        _loadedOutcome,
        _isAutoFollowEnabled,
    ) { track, loading, outcome, autoFollow ->
        LoadSnapshot(track, loading, outcome, autoFollow)
    }

    val uiState: StateFlow<LyricsScreenUiState> = combine(
        loadSnapshotFlow,
        playbackMetaFlow,
        _activeIndices,
    ) { load, playback, indices ->
        val track = load.track ?: return@combine LyricsScreenUiState.Idle
        if (load.isLoading) {
            return@combine LyricsScreenUiState.Loading(track)
        }
        val outcome = load.outcome
        if (outcome == null || outcomeTrackId(outcome) != track.id) {
            return@combine LyricsScreenUiState.Loading(track)
        }

        when (outcome) {
            is LyricsLookupOutcome.Found -> {
                val doc = outcome.document
                val isBound = playback.currentTrackId == track.id
                val durMs = if (isBound && playback.durationMs > 0L) playback.durationMs else track.durationMs
                val activeLineIdx = if (doc.isSynced && isBound) indices.first else -1
                val activeWordIdx = if (doc.hasWordSync && isBound && activeLineIdx in doc.lines.indices) {
                    indices.second
                } else {
                    -1
                }
                LyricsScreenUiState.Content(
                    track = track,
                    document = doc,
                    activeLineIndex = activeLineIdx,
                    activeWordIndex = activeWordIdx,
                    playbackPositionMs = 0L,
                    durationMs = durMs,
                    isPlaying = isBound && playback.isPlaying,
                    isBoundToActiveTrack = isBound,
                    isAutoFollowEnabled = load.isAutoFollow,
                )
            }
            is LyricsLookupOutcome.Empty -> LyricsScreenUiState.Empty(
                track = track,
                reason = outcome.reason,
            )
            is LyricsLookupOutcome.Failure -> {
                val (msg, offline) = when (val err = outcome.error) {
                    LyricsError.Offline -> "Offline — connect to the internet to fetch lyrics." to true
                    LyricsError.WifiOnlyBlocked -> "Wi-Fi only lyrics downloads enabled in Settings." to true
                    is LyricsError.NetworkFailure -> err.detail to false
                }
                LyricsScreenUiState.Error(
                    track = track,
                    message = msg,
                    isOffline = offline,
                )
            }
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), LyricsScreenUiState.Idle)

    init {
        // React immediately to track changes: cancel old requests, reset scroll/follow, and fetch new lyrics.
        viewModelScope.launch {
            targetTrack
                .distinctUntilChanged { a, b ->
                    a?.id == b?.id && a?.title == b?.title && a?.artist == b?.artist
                }
                .collect { track ->
                    loadJob?.cancel()
                    autoFollowResumeJob?.cancel()
                    _isAutoFollowEnabled.value = true
                    _searchState.value = LyricsSearchUiState()
                    _activeIndices.value = -1 to -1
                    if (track == null) {
                        _loadedTrack.value = null
                        _loadedOutcome.value = null
                        _isLoading.value = false
                    } else {
                        val prefs = prefsRepo.getOnce()
                        val allowOnline = if (_overrideTrack.value != null) {
                            prefs.autoDownloadLyrics
                        } else {
                            prefs.autoDownloadLyrics && prefs.downloadLyricsOnPlayStart
                        }
                        fetchLyrics(track = track, allowOnlineLookup = allowOnline, forceReload = false)
                    }
                }
        }

        // High-precision monotonic clock interpolation while LyricsScreen is visible.
        viewModelScope.launch {
            combine(
                _isLyricsScreenActive,
                playbackController.clockFlow,
                _loadedOutcome,
                targetTrack,
            ) { visible, clock, outcome, track ->
                SyncLoopParams(visible, clock, outcome, track)
            }.collect { params ->
                syncTickerJob?.cancel()
                val doc = (params.outcome as? LyricsLookupOutcome.Found)?.document
                val track = params.track
                val isBound = track != null && doc != null &&
                    doc.trackId == track.id && params.clock.trackId == track.id

                updatePositionAndIndices(params.clock, doc.takeIf { isBound })

                if (params.visible && isBound && params.clock.isPlaying) {
                    val cadenceMs = if (doc?.hasWordSync == true) 36L else 48L
                    syncTickerJob = viewModelScope.launch {
                        while (isActive) {
                            updatePositionAndIndices(params.clock, doc)
                            delay(cadenceMs)
                        }
                    }
                }
            }
        }
    }

    private data class SyncLoopParams(
        val visible: Boolean,
        val clock: PlaybackClockSnapshot,
        val outcome: LyricsLookupOutcome?,
        val track: Track?,
    )

    private fun updatePositionAndIndices(clock: PlaybackClockSnapshot, doc: LyricsDocument?) {
        val posMs = if (doc != null || clock.trackId == targetTrack.value?.id) {
            PlaybackController.computeInterpolatedPositionMs(clock, SystemClock.elapsedRealtime())
        } else {
            0L
        }
        if (_playbackPositionMs.value != posMs) {
            _playbackPositionMs.value = posMs
        }
        val nextPair = if (doc != null && doc.isSynced) {
            val lineIdx = LyricsSynchronizer.findActiveLineIndex(doc.lines, posMs, doc.syncOffsetMs)
            val wordIdx = if (doc.hasWordSync && lineIdx in doc.lines.indices) {
                LyricsSynchronizer.findActiveWordIndex(
                    doc.lines[lineIdx].words,
                    posMs,
                    doc.syncOffsetMs,
                )
            } else {
                -1
            }
            lineIdx to wordIdx
        } else {
            -1 to -1
        }
        if (_activeIndices.value != nextPair) {
            _activeIndices.value = nextPair
        }
    }

    fun setLyricsActive(active: Boolean) {
        _isLyricsScreenActive.value = active
    }

    /**
     * Opens Lyrics bound to the currently playing track (if any), clearing any manual preview override.
     */
    fun openForCurrentPlayback() {
        _overrideTrack.value = null
        val active = playbackController.uiState.value.currentTrack ?: return
        val currentOutcome = _loadedOutcome.value
        val alreadyLoadedForTrack = currentOutcome != null && outcomeTrackId(currentOutcome) == active.id
        if (!alreadyLoadedForTrack && !_isLoading.value) {
            viewModelScope.launch {
                val allowOnline = prefsRepo.getOnce().autoDownloadLyrics
                fetchLyrics(active, allowOnlineLookup = allowOnline, forceReload = true)
            }
        }
    }

    /**
     * Opens Lyrics for a specific track (e.g., from Library menu). If it matches the active track,
     * stays reactively bound to live playback transitions.
     */
    fun openForTrack(track: Track) {
        val activeId = playbackController.uiState.value.currentTrack?.id
        if (track.id == activeId) {
            openForCurrentPlayback()
        } else {
            _overrideTrack.value = track
        }
    }

    fun clearTrackOverride() {
        _overrideTrack.value = null
    }

    fun retry() {
        val track = targetTrack.value ?: return
        viewModelScope.launch {
            fetchLyrics(track = track, allowOnlineLookup = true, forceReload = true)
        }
    }

    fun refresh() {
        val track = targetTrack.value ?: return
        loadJob?.cancel()
        _loadedTrack.value = track
        _isLoading.value = true
        loadJob = viewModelScope.launch {
            val outcome = lyricsRepo.refreshLyricsForTrack(track)
            if (targetTrack.value?.id == track.id) {
                _loadedOutcome.value = outcome
                _isLoading.value = false
                when (outcome) {
                    is LyricsLookupOutcome.Found -> _statusMessages.tryEmit("Lyrics refreshed (${outcome.document.source.badgeLabel})")
                    is LyricsLookupOutcome.Empty -> _statusMessages.tryEmit("No matching lyrics found")
                    is LyricsLookupOutcome.Failure -> _statusMessages.tryEmit("Refresh failed")
                }
            }
        }
    }

    fun onUserScrolledManually() {
        if (_isAutoFollowEnabled.value) {
            _isAutoFollowEnabled.value = false
        }
        autoFollowResumeJob?.cancel()
        autoFollowResumeJob = viewModelScope.launch {
            delay(AUTO_FOLLOW_RESUME_DELAY_MS)
            _isAutoFollowEnabled.value = true
        }
    }

    fun resumeAutoFollow() {
        autoFollowResumeJob?.cancel()
        _isAutoFollowEnabled.value = true
    }

    fun onLyricLineTapped(line: LyricLine) {
        val ts = line.timestampMs ?: return
        val content = uiState.value as? LyricsScreenUiState.Content ?: return
        val seekMs = LyricsSynchronizer.computeSeekPositionMs(ts, content.document.syncOffsetMs)
        if (content.isBoundToActiveTrack) {
            playbackController.seekTo(seekMs)
        } else {
            playbackController.playTracks(listOf(content.track), 0)
            playbackController.seekTo(seekMs)
            _overrideTrack.value = null
        }
        resumeAutoFollow()
    }

    fun searchOnline(query: String?) {
        val track = targetTrack.value ?: return
        searchJob?.cancel()
        _searchState.value = _searchState.value.copy(isSearching = true, errorMessage = null)
        searchJob = viewModelScope.launch {
            lyricsRepo.searchOnlineCandidates(track, query)
                .onSuccess { list ->
                    _searchState.value = LyricsSearchUiState(
                        isSearching = false,
                        candidates = list,
                        errorMessage = null,
                    )
                }
                .onFailure { err ->
                    _searchState.value = LyricsSearchUiState(
                        isSearching = false,
                        candidates = emptyList(),
                        errorMessage = err.localizedMessage ?: "Search failed",
                    )
                }
        }
    }

    fun applyCandidate(candidate: LyricsSearchCandidate) {
        val track = targetTrack.value ?: return
        viewModelScope.launch {
            val doc = lyricsRepo.applySearchCandidate(track.id, candidate)
            if (targetTrack.value?.id == track.id) {
                _loadedOutcome.value = LyricsLookupOutcome.Found(doc)
                _isAutoFollowEnabled.value = true
            }
            _statusMessages.tryEmit("Applied lyrics from ${candidate.provider}")
        }
    }

    fun adjustSyncOffset(deltaMs: Long) {
        val content = uiState.value as? LyricsScreenUiState.Content ?: return
        val nextOffset = (content.document.syncOffsetMs + deltaMs).coerceIn(-15_000L, 15_000L)
        viewModelScope.launch {
            lyricsRepo.updateSyncOffset(content.track.id, nextOffset, fallbackDocument = content.document)
            if (targetTrack.value?.id == content.track.id) {
                _loadedOutcome.value = LyricsLookupOutcome.Found(
                    content.document.copy(syncOffsetMs = nextOffset)
                )
            }
        }
    }

    fun resetSyncOffset() {
        val content = uiState.value as? LyricsScreenUiState.Content ?: return
        viewModelScope.launch {
            lyricsRepo.updateSyncOffset(content.track.id, 0L, fallbackDocument = content.document)
            if (targetTrack.value?.id == content.track.id) {
                _loadedOutcome.value = LyricsLookupOutcome.Found(
                    content.document.copy(syncOffsetMs = 0L)
                )
            }
            _statusMessages.tryEmit("Lyrics sync offset reset")
        }
    }

    fun saveManualLyrics(rawText: String) {
        val track = targetTrack.value ?: return
        viewModelScope.launch {
            val doc = lyricsRepo.saveManualLyrics(track.id, rawText)
            if (targetTrack.value?.id == track.id) {
                _loadedOutcome.value = if (doc != null) {
                    LyricsLookupOutcome.Found(doc)
                } else {
                    LyricsLookupOutcome.Empty(track.id)
                }
            }
            _statusMessages.tryEmit(if (doc != null) "Lyrics saved" else "Lyrics cleared")
        }
    }

    fun saveSyncedLines(lines: List<LyricLine>) {
        val track = targetTrack.value ?: return
        val formatted = LrcFormatter.formatLrc(lines)
        if (formatted.isBlank()) {
            _statusMessages.tryEmit("Add or stamp at least one lyric line first")
            return
        }
        viewModelScope.launch {
            val doc = lyricsRepo.saveManualLyrics(track.id, formatted)
            if (targetTrack.value?.id == track.id && doc != null) {
                _loadedOutcome.value = LyricsLookupOutcome.Found(doc)
                _isAutoFollowEnabled.value = true
            }
            _statusMessages.tryEmit("Synced lyrics saved")
        }
    }

    fun importLyricsUri(uri: Uri) {
        val track = targetTrack.value ?: return
        viewModelScope.launch {
            val doc = lyricsRepo.importLyricsFromUri(track.id, uri)
            if (targetTrack.value?.id == track.id) {
                if (doc != null) {
                    _loadedOutcome.value = LyricsLookupOutcome.Found(doc)
                    _isAutoFollowEnabled.value = true
                    _statusMessages.tryEmit("Imported lyrics file")
                } else {
                    _statusMessages.tryEmit("Selected file was empty or unreadable")
                }
            }
        }
    }

    fun clearManualLyrics() {
        val track = targetTrack.value ?: return
        viewModelScope.launch {
            lyricsRepo.clearManualLyrics(track.id)
            if (targetTrack.value?.id == track.id) {
                fetchLyrics(track = track, allowOnlineLookup = false, forceReload = true)
            }
            _statusMessages.tryEmit("Removed custom lyrics")
        }
    }

    fun onCacheCleared() {
        val track = targetTrack.value ?: return
        viewModelScope.launch {
            fetchLyrics(track = track, allowOnlineLookup = prefsRepo.getOnce().autoDownloadLyrics, forceReload = true)
        }
    }

    fun setFontSize(sizeSp: Float) {
        viewModelScope.launch { prefsRepo.setLyricsFontSizeSp(sizeSp.coerceIn(16f, 34f)) }
    }

    private fun fetchLyrics(
        track: Track,
        allowOnlineLookup: Boolean,
        forceReload: Boolean,
    ) {
        if (!forceReload) {
            val existing = _loadedOutcome.value
            if (_loadedTrack.value?.id == track.id && existing != null && outcomeTrackId(existing) == track.id) {
                return
            }
        }

        loadJob?.cancel()
        _loadedTrack.value = track
        _isLoading.value = true
        loadJob = viewModelScope.launch {
            val outcome = lyricsRepo.getLyricsForTrack(track, allowOnlineLookup = allowOnlineLookup)
            if (targetTrack.value?.id == track.id) {
                _loadedOutcome.value = outcome
                _isLoading.value = false
            }
        }
    }

    private fun outcomeTrackId(outcome: LyricsLookupOutcome): String = when (outcome) {
        is LyricsLookupOutcome.Found -> outcome.document.trackId
        is LyricsLookupOutcome.Empty -> outcome.trackId
        is LyricsLookupOutcome.Failure -> outcome.trackId
    }

    class Factory(private val container: AppContainer) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            return LyricsViewModel(container) as T
        }
    }
}
