package com.example.player

import android.content.ComponentName
import android.content.Context
import android.os.Build
import android.os.Bundle
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.PlaybackParameters
import androidx.media3.common.Player
import androidx.media3.session.MediaController
import androidx.media3.session.SessionCommand
import androidx.media3.session.SessionToken
import com.example.data.local.db.PlaybackStateDao
import com.example.data.local.prefs.UserPreferencesRepository
import com.example.data.model.PlaybackUiState
import com.example.data.model.RepeatMode
import com.example.data.model.SleepTimerState
import com.example.data.model.Track
import com.example.data.repository.MusicRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.guava.await
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

class PlaybackController(
    private val context: Context,
    private val musicRepository: MusicRepository,
    private val playbackStateDao: PlaybackStateDao,
    private val prefsRepo: UserPreferencesRepository,
    private val automixRuntime: AutomixRuntime,
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    private var controller: MediaController? = null
    private var allTracksById: Map<String, Track> = emptyMap()
    private var positionTickerJob: Job? = null
    private var hasAttemptedRestore = false

    private val _uiState = MutableStateFlow(PlaybackUiState())
    val uiState: StateFlow<PlaybackUiState> = _uiState.asStateFlow()

    init {
        scope.launch {
            musicRepository.tracksFlow.collect { tracks ->
                allTracksById = tracks.associateBy { it.id }
                syncStateFromController()
            }
        }
        scope.launch {
            prefsRepo.preferencesFlow.collect { prefs ->
                _uiState.value = _uiState.value.copy(
                    playbackSpeed = prefs.playbackSpeed,
                    audioBalance = prefs.audioBalance,
                    crossfadeSeconds = prefs.crossfadeSeconds,
                    skipSilenceEnabled = prefs.skipSilenceEnabled,
                    loudnessNormalizationEnabled = prefs.loudnessNormalizationEnabled,
                )
            }
        }
        connect()
    }

    fun connect() {
        if (controller != null) return
        if (Build.FINGERPRINT.equals("robolectric", ignoreCase = true)) {
            return
        }
        scope.launch {
            runCatching {
                val token = SessionToken(context, ComponentName(context, MaxmizeMediaLibraryService::class.java))
                val mc = MediaController.Builder(context, token).buildAsync().await()
                controller = mc
                mc.addListener(playerListener)
                _uiState.value = _uiState.value.copy(isServiceConnected = true)
                if (!hasAttemptedRestore) {
                    hasAttemptedRestore = true
                    restoreSavedQueueIfEmpty(mc)
                }
                syncStateFromController()
                startPositionTicker()
            }
        }
    }

    private suspend fun restoreSavedQueueIfEmpty(mc: MediaController) {
        if (mc.mediaItemCount > 0) return
        val prefs = prefsRepo.getOnce()
        if (!prefs.resumePausedOnReopen) return

        val queueIds = playbackStateDao.getQueueOnce().map { it.trackId }
        if (queueIds.isEmpty()) return

        val tracks = musicRepository.tracksFlow.first().associateBy { it.id }
        val restoredTracks = queueIds.mapNotNull { tracks[it] }
        if (restoredTracks.isEmpty()) return

        val snapshot = playbackStateDao.getSnapshotOnce()
        val index = (snapshot?.currentIndex ?: 0).coerceIn(0, restoredTracks.lastIndex)
        val pos = (snapshot?.positionMs ?: 0L).coerceAtLeast(0L)

        mc.setMediaItems(restoredTracks.map { it.toMediaItem() }, index, pos)
        mc.shuffleModeEnabled = snapshot?.shuffleEnabled ?: false
        mc.repeatMode = when (snapshot?.repeatMode) {
            "ALL" -> Player.REPEAT_MODE_ALL
            "ONE" -> Player.REPEAT_MODE_ONE
            else -> Player.REPEAT_MODE_OFF
        }
        mc.prepare()
        mc.pause()
    }

    private val playerListener = object : Player.Listener {
        override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
            syncStateFromController()
        }

        override fun onMediaMetadataChanged(mediaMetadata: MediaMetadata) {
            syncStateFromController()
        }

        override fun onPlaybackStateChanged(playbackState: Int) {
            syncStateFromController()
        }

        override fun onIsPlayingChanged(isPlaying: Boolean) {
            syncStateFromController()
        }

        override fun onEvents(player: Player, events: Player.Events) {
            syncStateFromController()
        }
    }

    private fun startPositionTicker() {
        positionTickerJob?.cancel()
        positionTickerJob = scope.launch {
            while (isActive) {
                val mc = controller
                if (mc != null) {
                    val pos = mc.currentPosition.coerceAtLeast(0L)
                    val dur = mc.duration.takeIf { it > 0L } ?: _uiState.value.currentTrack?.durationMs ?: 0L
                    _uiState.value = _uiState.value.copy(
                        positionMs = pos,
                        durationMs = dur,
                    )
                    pollServiceExtras(mc)
                }
                delay(200L)
            }
        }
    }

    private suspend fun pollServiceExtras(mc: MediaController) {
        runCatching {
            val res = mc.sendCustomCommand(
                SessionCommand(MaxmizeMediaLibraryService.CMD_GET_STATE_EXTRAS, Bundle.EMPTY),
                Bundle.EMPTY,
            ).await()
            val extras = res.extras
            val serviceMediaId = extras.getString(MaxmizeMediaLibraryService.EXTRA_ACTIVE_MEDIA_ID)
            if (serviceMediaId != null && serviceMediaId != _uiState.value.activeMediaId) {
                syncStateFromController()
            }
            _uiState.value = _uiState.value.copy(
                sleepTimer = SleepTimerState(
                    isActive = extras.getBoolean(MaxmizeMediaLibraryService.EXTRA_SLEEP_ACTIVE, false),
                    remainingMs = extras.getLong(MaxmizeMediaLibraryService.EXTRA_SLEEP_REMAINING_MS, 0L),
                    stopAfterCurrentTrack = extras.getBoolean(MaxmizeMediaLibraryService.EXTRA_SLEEP_AFTER_TRACK, false),
                ),
                audioSessionId = extras.getInt(MaxmizeMediaLibraryService.EXTRA_AUDIO_SESSION_ID, 0),
            )
        }
    }

    private fun syncStateFromController() {
        val mc = controller ?: return
        val queue = (0 until mc.mediaItemCount).mapNotNull { idx ->
            val item = mc.getMediaItemAt(idx)
            allTracksById[item.mediaId] ?: item.toFallbackTrack()
        }
        val currentIdx = mc.currentMediaItemIndex.takeIf { it in queue.indices } ?: -1
        val activeItem = mc.currentMediaItem
        val currentTrack = queue.getOrNull(currentIdx)
            ?: activeItem?.mediaId?.let { allTracksById[it] }
            ?: activeItem?.toFallbackTrack()
        val repeatMode = when (mc.repeatMode) {
            Player.REPEAT_MODE_ALL -> RepeatMode.ALL
            Player.REPEAT_MODE_ONE -> RepeatMode.ONE
            else -> RepeatMode.OFF
        }
        val dur = mc.duration.takeIf { it > 0L } ?: currentTrack?.durationMs ?: 0L
        _uiState.value = _uiState.value.copy(
            activeMediaId = currentTrack?.id ?: activeItem?.mediaId,
            currentTrack = currentTrack,
            queue = queue,
            currentIndex = currentIdx,
            isPlaying = mc.isPlaying,
            positionMs = mc.currentPosition.coerceAtLeast(0L),
            durationMs = dur,
            shuffleEnabled = mc.shuffleModeEnabled,
            repeatMode = repeatMode,
        )
    }

    fun playTracks(tracks: List<Track>, startIndex: Int = 0, shuffle: Boolean = false) {
        if (tracks.isEmpty()) return
        val mc = controller ?: run {
            connect()
            return
        }
        if (automixRuntime.uiState.value.isActive) {
            stopAutomix()
        }
        val safeIdx = startIndex.coerceIn(0, tracks.lastIndex)
        mc.shuffleModeEnabled = shuffle
        mc.setMediaItems(tracks.map { it.toMediaItem() }, safeIdx, 0L)
        mc.prepare()
        mc.play()
        syncStateFromController()
    }

    fun playTrackAtQueueIndex(index: Int) {
        val mc = controller ?: return
        if (index in 0 until mc.mediaItemCount) {
            mc.seekTo(index, 0L)
            mc.play()
            syncStateFromController()
        }
    }

    fun togglePlayPause() {
        val mc = controller ?: run {
            connect()
            return
        }
        if (mc.isPlaying) {
            mc.pause()
        } else {
            if (mc.mediaItemCount == 0 && allTracksById.isNotEmpty()) {
                playTracks(allTracksById.values.toList(), 0)
            } else {
                mc.play()
            }
        }
    }

    fun seekTo(positionMs: Long) {
        val mc = controller ?: return
        mc.seekTo(positionMs.coerceAtLeast(0L))
        _uiState.value = _uiState.value.copy(positionMs = positionMs.coerceAtLeast(0L))
    }

    fun seekForward(deltaMs: Long = 10_000L) {
        val mc = controller ?: return
        val target = (mc.currentPosition + deltaMs).coerceAtMost(_uiState.value.durationMs.coerceAtLeast(0L))
        seekTo(target)
    }

    fun seekBackward(deltaMs: Long = 10_000L) {
        val mc = controller ?: return
        val target = (mc.currentPosition - deltaMs).coerceAtLeast(0L)
        seekTo(target)
    }

    fun skipNext() {
        val mc = controller ?: return
        val activeId = mc.currentMediaItem?.mediaId
        val pos = mc.currentPosition
        val dur = mc.duration.takeIf { it > 0L } ?: _uiState.value.currentTrack?.durationMs ?: 0L
        if (activeId != null && dur > 15_000L && pos in 750L..((dur * 0.35f).toLong())) {
            scope.launch(Dispatchers.IO) {
                musicRepository.recordTrackSkipped(activeId)
            }
        }
        if (automixRuntime.uiState.value.isActive) {
            skipAutomixNext()
            return
        }
        if (mc.hasNextMediaItem()) {
            mc.seekToNextMediaItem()
        }
    }

    fun skipPrevious() {
        val mc = controller ?: return
        if (mc.currentPosition > 3000L || !mc.hasPreviousMediaItem()) {
            mc.seekTo(0L)
        } else {
            mc.seekToPreviousMediaItem()
        }
    }

    fun toggleShuffle() {
        val mc = controller ?: return
        mc.shuffleModeEnabled = !mc.shuffleModeEnabled
    }

    fun cycleRepeat() {
        val mc = controller ?: return
        mc.repeatMode = when (mc.repeatMode) {
            Player.REPEAT_MODE_OFF -> Player.REPEAT_MODE_ALL
            Player.REPEAT_MODE_ALL -> Player.REPEAT_MODE_ONE
            else -> Player.REPEAT_MODE_OFF
        }
    }

    fun playNext(track: Track) {
        val mc = controller ?: return
        if (mc.mediaItemCount == 0) {
            playTracks(listOf(track), 0)
        } else {
            val insertIdx = (mc.currentMediaItemIndex + 1).coerceAtMost(mc.mediaItemCount)
            mc.addMediaItem(insertIdx, track.toMediaItem())
            syncStateFromController()
        }
    }

    fun addToQueue(track: Track) {
        val mc = controller ?: return
        if (mc.mediaItemCount == 0) {
            playTracks(listOf(track), 0)
        } else {
            mc.addMediaItem(track.toMediaItem())
            syncStateFromController()
        }
    }

    fun removeFromQueue(index: Int) {
        val mc = controller ?: return
        if (index in 0 until mc.mediaItemCount) {
            mc.removeMediaItem(index)
            syncStateFromController()
        }
    }

    fun moveQueueItem(fromIndex: Int, toIndex: Int) {
        val mc = controller ?: return
        if (fromIndex in 0 until mc.mediaItemCount && toIndex in 0 until mc.mediaItemCount) {
            mc.moveMediaItem(fromIndex, toIndex)
            syncStateFromController()
        }
    }

    fun clearQueue() {
        val mc = controller ?: return
        if (automixRuntime.uiState.value.isActive) {
            stopAutomix()
        }
        mc.stop()
        mc.clearMediaItems()
        scope.launch(Dispatchers.IO) {
            playbackStateDao.clearQueue()
        }
        syncStateFromController()
    }

    fun setPlaybackSpeed(speed: Float) {
        val clamped = speed.coerceIn(0.5f, 2.0f)
        controller?.playbackParameters = PlaybackParameters(clamped)
        scope.launch { prefsRepo.setPlaybackSpeed(clamped) }
    }

    fun setAudioBalance(balance: Float) {
        val clamped = balance.coerceIn(-1.0f, 1.0f)
        val args = Bundle().apply { putFloat(MaxmizeMediaLibraryService.ARG_FLOAT, clamped) }
        controller?.sendCustomCommand(
            SessionCommand(MaxmizeMediaLibraryService.CMD_SET_BALANCE, Bundle.EMPTY),
            args,
        )
        scope.launch { prefsRepo.setAudioBalance(clamped) }
    }

    fun setCrossfadeSeconds(seconds: Int) {
        val clamped = seconds.coerceIn(0, 12)
        val args = Bundle().apply { putInt(MaxmizeMediaLibraryService.ARG_INT, clamped) }
        controller?.sendCustomCommand(
            SessionCommand(MaxmizeMediaLibraryService.CMD_SET_CROSSFADE, Bundle.EMPTY),
            args,
        )
        scope.launch { prefsRepo.setCrossfadeSeconds(clamped) }
    }

    fun setSkipSilenceEnabled(enabled: Boolean) {
        scope.launch { prefsRepo.setSkipSilenceEnabled(enabled) }
    }

    fun setLoudnessNormalizationEnabled(enabled: Boolean) {
        scope.launch { prefsRepo.setLoudnessNormalizationEnabled(enabled) }
    }

    fun startSleepTimer(minutes: Int) {
        val args = Bundle().apply { putLong(MaxmizeMediaLibraryService.ARG_LONG, minutes * 60_000L) }
        controller?.sendCustomCommand(
            SessionCommand(MaxmizeMediaLibraryService.CMD_START_SLEEP_TIMER, Bundle.EMPTY),
            args,
        )
    }

    fun stopAfterCurrentTrack() {
        controller?.sendCustomCommand(
            SessionCommand(MaxmizeMediaLibraryService.CMD_STOP_AFTER_TRACK, Bundle.EMPTY),
            Bundle.EMPTY,
        )
    }

    fun cancelSleepTimer() {
        controller?.sendCustomCommand(
            SessionCommand(MaxmizeMediaLibraryService.CMD_CANCEL_SLEEP_TIMER, Bundle.EMPTY),
            Bundle.EMPTY,
        )
    }

    // --- Automix / Dual-Deck DJ Commands ---

    fun previewDjVoice() {
        controller?.sendCustomCommand(
            SessionCommand(MaxmizeMediaLibraryService.CMD_PREVIEW_DJ_VOICE, Bundle.EMPTY),
            Bundle.EMPTY,
        )
    }

    fun startAutomix(seedTrackId: String? = null) {
        val args = Bundle().apply {
            if (seedTrackId != null) putString(MaxmizeMediaLibraryService.ARG_TRACK_ID, seedTrackId)
        }
        controller?.sendCustomCommand(
            SessionCommand(MaxmizeMediaLibraryService.CMD_START_AUTOMIX, Bundle.EMPTY),
            args,
        )
    }

    fun stopAutomix() {
        controller?.sendCustomCommand(
            SessionCommand(MaxmizeMediaLibraryService.CMD_STOP_AUTOMIX, Bundle.EMPTY),
            Bundle.EMPTY,
        )
    }

    fun skipAutomixNext() {
        controller?.sendCustomCommand(
            SessionCommand(MaxmizeMediaLibraryService.CMD_SKIP_AUTOMIX_NEXT, Bundle.EMPTY),
            Bundle.EMPTY,
        )
    }

    fun recomputeDjQueue() {
        controller?.sendCustomCommand(
            SessionCommand(MaxmizeMediaLibraryService.CMD_RECOMPUTE_DJ_QUEUE, Bundle.EMPTY),
            Bundle.EMPTY,
        )
    }

    fun moveDjQueueItem(fromIndex: Int, toIndex: Int) {
        val args = Bundle().apply {
            putInt(MaxmizeMediaLibraryService.ARG_INT, fromIndex)
            putInt(MaxmizeMediaLibraryService.ARG_INT_2, toIndex)
        }
        controller?.sendCustomCommand(
            SessionCommand(MaxmizeMediaLibraryService.CMD_MOVE_DJ_QUEUE, Bundle.EMPTY),
            args,
        )
    }

    fun removeDjQueueItem(index: Int) {
        val args = Bundle().apply { putInt(MaxmizeMediaLibraryService.ARG_INT, index) }
        controller?.sendCustomCommand(
            SessionCommand(MaxmizeMediaLibraryService.CMD_REMOVE_DJ_QUEUE, Bundle.EMPTY),
            args,
        )
    }

    fun playDjQueueTrackNow(trackId: String) {
        val args = Bundle().apply { putString(MaxmizeMediaLibraryService.ARG_TRACK_ID, trackId) }
        controller?.sendCustomCommand(
            SessionCommand(MaxmizeMediaLibraryService.CMD_PLAY_DJ_TRACK_NOW, Bundle.EMPTY),
            args,
        )
    }

    private fun MediaItem.toFallbackTrack(): Track {
        val meta = mediaMetadata
        return Track(
            id = mediaId,
            mediaStoreId = null,
            contentUri = localConfiguration?.uri?.toString().orEmpty(),
            title = meta.title?.toString() ?: "Untitled Track",
            artist = meta.artist?.toString() ?: "Unknown Artist",
            album = meta.albumTitle?.toString() ?: "Unknown Album",
            albumArtist = meta.albumArtist?.toString() ?: "",
            genre = meta.genre?.toString() ?: "",
            durationMs = 0L,
            trackNumber = meta.trackNumber ?: 0,
            year = meta.releaseYear ?: 0,
            mimeType = "audio/*",
            filePath = "",
            folderPath = "",
            folderName = "",
            fileSizeBytes = 0L,
            dateAddedSec = 0L,
            albumId = null,
            artworkUri = meta.artworkUri?.toString(),
            customArtworkUri = null,
        )
    }
}
