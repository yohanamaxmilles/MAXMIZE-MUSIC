package com.example.player

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.annotation.OptIn
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.PlaybackParameters
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.DefaultRenderersFactory
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.analytics.AnalyticsListener
import androidx.media3.exoplayer.audio.AudioSink
import androidx.media3.exoplayer.audio.DefaultAudioSink
import androidx.media3.session.DefaultMediaNotificationProvider
import androidx.media3.session.LibraryResult
import androidx.media3.session.MediaLibraryService
import androidx.media3.session.MediaSession
import androidx.media3.session.SessionCommand
import androidx.media3.session.SessionError
import androidx.media3.session.SessionResult
import com.example.MainActivity
import com.example.MaxmizeApplication
import com.example.R
import com.example.data.dj.DjProfiles
import com.example.data.dj.GenreEngine
import com.example.data.dj.HarmonicCompatibility
import com.example.data.dj.v2.AudioContentKind
import com.example.data.dj.v2.DjDecisionConfig
import com.example.data.dj.v2.DjDecisionEngine
import com.example.data.dj.v2.DjSessionArcPlanner
import com.example.data.dj.v2.DjSessionPhase
import com.example.data.dj.v2.DjTrackProfileFactory
import com.example.data.dj.v2.DjTransitionConfig
import com.example.data.dj.v2.DjTransitionCurve
import com.example.data.dj.v2.DjTransitionPlan
import com.example.data.dj.v2.DjTransitionPlanner
import com.example.data.dj.v2.DjTransitionPlanningResult
import com.example.data.local.db.CustomDjProfileEntity
import com.example.data.local.db.GenreRuleOverrideEntity
import com.example.data.local.db.MixHistoryEntity
import com.example.data.local.db.PlaybackSnapshotEntity
import com.example.data.model.AutomixConfig
import com.example.data.model.AutomixTechnique
import com.example.data.model.DjOperatingMode
import com.example.data.model.AutomixUiState
import com.example.data.model.DeckState
import com.example.data.model.DjQueuedTrack
import com.example.data.model.Track
import com.example.data.model.TrackAnalysis
import com.example.data.model.TransitionStyle
import com.example.player.djvoice.DjVoiceDirector
import com.example.widget.MaxmizeWidgetProvider
import com.google.common.collect.ImmutableList
import com.google.common.util.concurrent.Futures
import com.google.common.util.concurrent.ListenableFuture
import com.google.common.util.concurrent.SettableFuture
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.roundToInt
import java.util.Locale

@OptIn(UnstableApi::class)
class MaxmizeMediaLibraryService : MediaLibraryService() {

    companion object {
        const val ACTION_WIDGET_PLAY_PAUSE = "com.example.action.WIDGET_PLAY_PAUSE"
        const val ACTION_WIDGET_NEXT = "com.example.action.WIDGET_NEXT"
        const val ACTION_WIDGET_PREV = "com.example.action.WIDGET_PREV"

        const val CMD_SET_BALANCE = "com.example.cmd.SET_BALANCE"
        const val CMD_SET_CROSSFADE = "com.example.cmd.SET_CROSSFADE"
        const val CMD_START_SLEEP_TIMER = "com.example.cmd.START_SLEEP_TIMER"
        const val CMD_STOP_AFTER_TRACK = "com.example.cmd.STOP_AFTER_TRACK"
        const val CMD_CANCEL_SLEEP_TIMER = "com.example.cmd.CANCEL_SLEEP_TIMER"
        const val CMD_GET_STATE_EXTRAS = "com.example.cmd.GET_STATE_EXTRAS"

        const val CMD_START_AUTOMIX = "com.example.cmd.START_AUTOMIX"
        const val CMD_STOP_AUTOMIX = "com.example.cmd.STOP_AUTOMIX"
        const val CMD_SKIP_AUTOMIX_NEXT = "com.example.cmd.SKIP_AUTOMIX_NEXT"
        const val CMD_RECOMPUTE_DJ_QUEUE = "com.example.cmd.RECOMPUTE_DJ_QUEUE"
        const val CMD_MOVE_DJ_QUEUE = "com.example.cmd.MOVE_DJ_QUEUE"
        const val CMD_REMOVE_DJ_QUEUE = "com.example.cmd.REMOVE_DJ_QUEUE"
        const val CMD_PLAY_DJ_TRACK_NOW = "com.example.cmd.PLAY_DJ_TRACK_NOW"

        const val ARG_FLOAT = "arg_float"
        const val ARG_INT = "arg_int"
        const val ARG_INT_2 = "arg_int_2"
        const val ARG_LONG = "arg_long"
        const val ARG_TRACK_ID = "arg_track_id"

        const val EXTRA_SLEEP_ACTIVE = "sleep_active"
        const val EXTRA_SLEEP_REMAINING_MS = "sleep_remaining_ms"
        const val EXTRA_SLEEP_AFTER_TRACK = "sleep_after_track"
        const val EXTRA_BALANCE = "balance"
        const val EXTRA_CROSSFADE = "crossfade"
        const val EXTRA_AUDIO_SESSION_ID = "audio_session_id"
        const val EXTRA_ACTIVE_MEDIA_ID = "active_media_id"
        const val EXTRA_ACTIVE_DECK = "active_deck"

        private const val ROOT_ID = "maxmize_root"
        private const val NODE_ALL_SONGS = "node_all_songs"
        private const val NODE_ALBUMS = "node_albums"
        private const val NODE_ARTISTS = "node_artists"
        private const val NODE_PLAYLISTS = "node_playlists"
        private const val NODE_FAVORITES = "node_favorites"
        private const val NODE_RECENT = "node_recent"
    }

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    private lateinit var deckA: ExoPlayer
    private lateinit var deckB: ExoPlayer
    private var activeDeckIndex: Int = 0
    private val activePlayer: ExoPlayer get() = if (activeDeckIndex == 0) deckA else deckB
    private val standbyPlayer: ExoPlayer get() = if (activeDeckIndex == 0) deckB else deckA

    private lateinit var audioAttributes: AudioAttributes
    private var mediaLibrarySession: MediaLibrarySession? = null
    private val balanceProcessorA = BalanceAudioProcessor()
    private val balanceProcessorB = BalanceAudioProcessor()
    private val djProcessorA = DjDeckAudioProcessor()
    private val djProcessorB = DjDeckAudioProcessor()
    private val activeDjProcessor: DjDeckAudioProcessor get() = if (activeDeckIndex == 0) djProcessorA else djProcessorB
    private val standbyDjProcessor: DjDeckAudioProcessor get() = if (activeDeckIndex == 0) djProcessorB else djProcessorA

    private var crossfadeSeconds: Int = 0
    private var currentPlaybackSpeed: Float = 1.0f
    private var gaplessEnabled: Boolean = true
    private var skipSilenceEnabled: Boolean = false
    private var loudnessNormalizationEnabled: Boolean = false
    private var automixConfig: AutomixConfig = AutomixConfig()

    private var sleepTimerJob: Job? = null
    private var sleepActive: Boolean = false
    private var sleepRemainingMs: Long = 0L
    private var stopAfterCurrentTrack: Boolean = false
    private var lastRecordedTrackId: String? = null
    private var monitorJob: Job? = null
    private var snapshotJob: Job? = null
    private var djQueueJob: Job? = null
    private var recomputeDebounceJob: Job? = null
    private var tempoEaseJob: Job? = null

    // Dual-Deck Automix runtime state (background-independent)
    private val djQueueMutex = Mutex()
    private val djDecisionEngine = DjDecisionEngine()
    private val djTransitionPlanner = DjTransitionPlanner()
    private var activeTransitionPlan: DjTransitionPlan? = null
    private var djVoiceDirector: DjVoiceDirector? = null
    private var speechDuckActive: Boolean = false
    private var djAnnouncementCounter: Int = 0
    private var automixActive: Boolean = false
    private var automixStartTimeMs: Long = 0L
    private var activeSessionPhase: DjSessionPhase = DjSessionPhase.OPENING
    private var isMixingTransition: Boolean = false
    private var transitionStartElapsedMs: Long = 0L
    private var transitionDurationActiveMs: Long = 8000L
    private var preloadedTrack: Track? = null
    private var activeTrackAnalysis: TrackAnalysis? = null
    private var incomingTrackAnalysis: TrackAnalysis? = null
    private var noSuitableTrackReason: String? = null
    private val djQueue: MutableList<DjQueuedTrack> = mutableListOf()
    private val recentDjHistoryIds: ArrayDeque<String> = ArrayDeque()
    private var allTracksCache: Map<String, Track> = emptyMap()
    private var analysesCache: Map<String, TrackAnalysis> = emptyMap()
    private var customProfilesCache: List<CustomDjProfileEntity> = emptyList()
    private var customRulesCache: List<GenreRuleOverrideEntity> = emptyList()

    private val container by lazy { (application as MaxmizeApplication).container }

    override fun onCreate() {
        super.onCreate()

        audioAttributes = AudioAttributes.Builder()
            .setContentType(C.AUDIO_CONTENT_TYPE_MUSIC)
            .setUsage(C.USAGE_MEDIA)
            .build()

        balanceProcessorA.emitSpectrum = true
        balanceProcessorB.emitSpectrum = false
        djVoiceDirector = DjVoiceDirector(
            context = applicationContext,
            locale = Locale.ENGLISH,
            onSpeechStarted = {
                serviceScope.launch {
                    speechDuckActive = true
                    updateSpeechDuck()
                }
            },
            onSpeechFinished = {
                serviceScope.launch {
                    speechDuckActive = false
                    updateSpeechDuck()
                }
            },
        )

        deckA = buildDeckPlayer(balanceProcessorA, djProcessorA, handleFocus = true)
        deckB = buildDeckPlayer(balanceProcessorB, djProcessorB, handleFocus = false)

        attachDeckListeners(deckIndex = 0, player = deckA)
        attachDeckListeners(deckIndex = 1, player = deckB)

        serviceScope.launch {
            container.musicRepository.tracksFlow.collect { list ->
                allTracksCache = list.associateBy { it.id }
                publishAutomixRuntime()
            }
        }
        serviceScope.launch {
            container.audioAnalysisEngine.analysesFlow.collect { map ->
                analysesCache = map
                updateProcessorNormalization()
                publishAutomixRuntime()
            }
        }
        serviceScope.launch {
            container.database.djConfigDao().observeCustomProfiles().collect { profiles ->
                customProfilesCache = profiles
                publishAutomixRuntime()
            }
        }
        serviceScope.launch {
            container.database.djConfigDao().observeGenreRules().collect { rules ->
                customRulesCache = rules
                if (automixActive) scheduleLiveDjQueueRefresh()
            }
        }

        serviceScope.launch {
            var initialized = false
            var prevDjConfig = AutomixConfig()
            container.prefsRepo.preferencesFlow.collect { prefs ->
                gaplessEnabled = prefs.gaplessEnabled
                deckA.pauseAtEndOfMediaItems = !prefs.gaplessEnabled
                deckB.pauseAtEndOfMediaItems = !prefs.gaplessEnabled
                skipSilenceEnabled = prefs.skipSilenceEnabled
                deckA.skipSilenceEnabled = prefs.skipSilenceEnabled
                deckB.skipSilenceEnabled = prefs.skipSilenceEnabled
                loudnessNormalizationEnabled = prefs.loudnessNormalizationEnabled
                crossfadeSeconds = prefs.crossfadeSeconds
                currentPlaybackSpeed = prefs.playbackSpeed
                automixConfig = prefs.automixConfig
                updateProcessorNormalization()

                if (!initialized) {
                    initialized = true
                    prevDjConfig = prefs.automixConfig
                    balanceProcessorA.setBalance(prefs.audioBalance)
                    balanceProcessorB.setBalance(prefs.audioBalance)
                    deckA.playbackParameters = PlaybackParameters(prefs.playbackSpeed)
                    deckB.playbackParameters = PlaybackParameters(prefs.playbackSpeed)
                } else {
                    val djRelevantChanged = prevDjConfig.copy(enabled = false) != prefs.automixConfig.copy(enabled = false)
                    prevDjConfig = prefs.automixConfig
                    if (djRelevantChanged && automixActive) {
                        scheduleLiveDjQueueRefresh()
                    }
                    publishAutomixRuntime()
                }
            }
        }

        val notificationProvider = DefaultMediaNotificationProvider.Builder(this)
            .build()
        notificationProvider.setSmallIcon(R.drawable.ic_launcher_foreground)
        setMediaNotificationProvider(notificationProvider)

        val sessionActivityIntent = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_SINGLE_TOP
            },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

        mediaLibrarySession = MediaLibrarySession.Builder(this, activePlayer, LibrarySessionCallback())
            .setSessionActivity(sessionActivityIntent)
            .build()

        startBackgroundMonitors()
    }

    private fun buildDeckPlayer(
        balanceProcessor: BalanceAudioProcessor,
        djProcessor: DjDeckAudioProcessor,
        handleFocus: Boolean,
    ): ExoPlayer {
        val renderersFactory = object : DefaultRenderersFactory(this) {
            override fun buildAudioSink(
                context: Context,
                enableFloatOutput: Boolean,
                enableAudioTrackPlaybackParams: Boolean,
            ): AudioSink {
                return DefaultAudioSink.Builder(context)
                    .setAudioProcessors(arrayOf(balanceProcessor, djProcessor))
                    .setEnableFloatOutput(enableFloatOutput)
                    .setEnableAudioTrackPlaybackParams(enableAudioTrackPlaybackParams)
                    .build()
            }
        }
        return ExoPlayer.Builder(this, renderersFactory)
            .setAudioAttributes(audioAttributes, handleFocus)
            .setHandleAudioBecomingNoisy(true)
            .setPauseAtEndOfMediaItems(false)
            .build()
    }

    private fun attachDeckListeners(deckIndex: Int, player: ExoPlayer) {
        player.addAnalyticsListener(object : AnalyticsListener {
            override fun onAudioSessionIdChanged(
                eventTime: AnalyticsListener.EventTime,
                audioSessionId: Int,
            ) {
                if (deckIndex == activeDeckIndex) {
                    container.audioEffectsManager.attachToAudioSession(audioSessionId)
                }
            }
        })

        player.addListener(object : Player.Listener {
            override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
                if (deckIndex != activeDeckIndex) return
                if (stopAfterCurrentTrack && reason == Player.MEDIA_ITEM_TRANSITION_REASON_AUTO) {
                    player.pause()
                    cancelSleepTimer()
                }
                if (!isMixingTransition) {
                    player.volume = computeAutoGainMultiplier(mediaItem?.mediaId)
                }
                updateProcessorNormalization()
                lastRecordedTrackId = null
                updateWidgetFromPlayer()
                persistSnapshotNow()
                if (automixActive) {
                    val id = mediaItem?.mediaId
                    val tr = id?.let { allTracksCache[it] }
                    if (tr != null) {
                        serviceScope.launch {
                            activeTrackAnalysis = container.audioAnalysisEngine.getAnalysisForTrack(tr)
                            recomputeDjQueueInternal(tr)
                        }
                    }
                }
                publishAutomixRuntime()
            }

            override fun onIsPlayingChanged(isPlaying: Boolean) {
                if (deckIndex != activeDeckIndex) return
                if (!isPlaying && !isMixingTransition) {
                    RealtimeSpectrumBus.reset()
                }
                updateWidgetFromPlayer()
                if (!isPlaying) {
                    persistSnapshotNow()
                }
                publishAutomixRuntime()
            }

            override fun onPlaybackStateChanged(playbackState: Int) {
                if (deckIndex != activeDeckIndex) return
                if (playbackState == Player.STATE_ENDED) {
                    if (stopAfterCurrentTrack) {
                        cancelSleepTimer()
                    } else if (automixActive && !isMixingTransition && djQueue.isNotEmpty()) {
                        serviceScope.launch {
                            triggerImmediateTransitionToNext(durationMs = 1500L)
                        }
                    }
                }
            }

            override fun onShuffleModeEnabledChanged(shuffleModeEnabled: Boolean) {
                if (deckIndex == activeDeckIndex) persistSnapshotNow()
            }

            override fun onRepeatModeChanged(repeatMode: Int) {
                if (deckIndex == activeDeckIndex) persistSnapshotNow()
            }
        })
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val player = activePlayer
        when (intent?.action) {
            ACTION_WIDGET_PLAY_PAUSE -> {
                if (player.isPlaying) player.pause() else {
                    if (player.mediaItemCount == 0) {
                        serviceScope.launch {
                            restorePersistedQueueIntoPlayer()
                            if (activePlayer.mediaItemCount > 0) activePlayer.play()
                        }
                    } else {
                        player.play()
                    }
                }
            }
            ACTION_WIDGET_NEXT -> {
                if (automixActive && djQueue.isNotEmpty()) {
                    serviceScope.launch { triggerImmediateTransitionToNext(durationMs = 3000L) }
                } else if (player.hasNextMediaItem()) {
                    player.seekToNextMediaItem()
                }
            }
            ACTION_WIDGET_PREV -> {
                if (player.currentPosition > 3000L || !player.hasPreviousMediaItem()) {
                    player.seekTo(0L)
                } else {
                    player.seekToPreviousMediaItem()
                }
            }
        }
        return super.onStartCommand(intent, flags, startId)
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaLibrarySession? {
        return mediaLibrarySession
    }

    override fun onTaskRemoved(rootIntent: Intent?) {
        persistSnapshotNow()
        if (!activePlayer.playWhenReady || activePlayer.mediaItemCount == 0) {
            stopSelf()
        }
    }

    override fun onDestroy() {
        persistSnapshotNow()
        monitorJob?.cancel()
        snapshotJob?.cancel()
        sleepTimerJob?.cancel()
        djQueueJob?.cancel()
        recomputeDebounceJob?.cancel()
        tempoEaseJob?.cancel()
        RealtimeSpectrumBus.reset()
        djVoiceDirector?.close()
        djVoiceDirector = null
        container.audioEffectsManager.releaseEffects()
        mediaLibrarySession?.release()
        mediaLibrarySession = null
        deckA.release()
        deckB.release()
        serviceScope.cancel()
        super.onDestroy()
    }

    private fun startBackgroundMonitors() {
        monitorJob = serviceScope.launch {
            while (isActive) {
                val player = activePlayer
                if (player.isPlaying) {
                    val dur = player.duration.coerceAtLeast(0L)
                    val pos = player.currentPosition.coerceAtLeast(0L)
                    val currentId = player.currentMediaItem?.mediaId
                    if (currentId != null && currentId != lastRecordedTrackId) {
                        val threshold = if (dur in 1..30_000L) (dur * 0.4f).toLong() else 15_000L
                        if (pos >= threshold) {
                            lastRecordedTrackId = currentId
                            launch(Dispatchers.IO) {
                                container.musicRepository.recordTrackPlayed(currentId)
                            }
                        }
                    }

                    if (automixActive) {
                        stepAutomixEngine(player, pos, dur)
                    } else {
                        stepStandardCrossfade(player, pos, dur)
                    }
                } else if (!automixActive && !isMixingTransition && player.volume != 1.0f) {
                    player.volume = 1.0f
                }

                if (automixActive) {
                    publishAutomixRuntime()
                }
                delay(if (isMixingTransition) 60L else 180L)
            }
        }

        snapshotJob = serviceScope.launch {
            while (isActive) {
                delay(5000L)
                if (activePlayer.isPlaying && activePlayer.mediaItemCount > 0) {
                    persistSnapshotNow()
                }
            }
        }
    }

    private fun stepStandardCrossfade(player: ExoPlayer, pos: Long, dur: Long) {
        val cfMs = crossfadeSeconds * 1000L
        if (cfMs > 0 && dur > cfMs * 2 && player.hasNextMediaItem()) {
            val remaining = dur - pos
            if (remaining in 1..cfMs) {
                val ratio = (remaining.toFloat() / cfMs.toFloat()).coerceIn(0.05f, 1.0f)
                player.volume = ratio
            } else if (pos < cfMs && player.volume < 1.0f) {
                val rampIn = (pos.toFloat() / cfMs.toFloat()).coerceIn(0.15f, 1.0f)
                player.volume = rampIn
            } else if (player.volume != 1.0f) {
                player.volume = 1.0f
            }
        } else if (player.volume != 1.0f) {
            player.volume = 1.0f
        }
    }

    private suspend fun stepAutomixEngine(player: ExoPlayer, pos: Long, dur: Long) {
        if (dur <= 0L) return

        // Check mix duration limit
        val elapsedMin = ((System.currentTimeMillis() - automixStartTimeMs) / 60_000L).toInt()
        if (elapsedMin >= automixConfig.mixDurationMinutes && !isMixingTransition) {
            if (dur - pos <= 500L) {
                stopAutomixInternal()
                player.pause()
            }
            return
        }

        val nextEntry = djQueue.firstOrNull()
        val transMs = (automixConfig.transitionDurationSec * 1000L).coerceIn(2000L, 16_000L)

        // Ensure analysis exists for active track
        val currentId = player.currentMediaItem?.mediaId
        if (currentId != null && activeTrackAnalysis?.trackId != currentId) {
            val tr = allTracksCache[currentId]
            if (tr != null) {
                activeTrackAnalysis = container.audioAnalysisEngine.getAnalysisForTrack(tr)
            }
        }

        // Calculate beat-aligned / phrase-aligned cue-out point
        val rawCueOut = activeTrackAnalysis?.outroStartMs?.takeIf {
            it in (dur / 2)..(dur - 3500L)
        } ?: (dur - transMs - 600L).coerceAtLeast(dur / 2)

        val fallbackCueOutMs = if (automixConfig.phraseMatchEnabled ||
            automixConfig.technique == AutomixTechnique.PHRASE_MATCH
        ) {
            alignToPhraseOrBeat(rawCueOut, activeTrackAnalysis)
        } else {
            alignToNearestBeat(rawCueOut, activeTrackAnalysis)
        }
        val cueOutMs = activeTransitionPlan?.outgoingStartMs ?: fallbackCueOutMs

        // Preload standby deck ~12 seconds prior to cue-out
        if (!isMixingTransition && nextEntry != null && pos >= (cueOutMs - 12_000L).coerceAtLeast(0L)) {
            if (preloadedTrack?.id != nextEntry.track.id) {
                preloadStandbyDeck(nextEntry.track)
            }
        }

        // Start simultaneous dual-deck transition
        if (!isMixingTransition && nextEntry != null && pos >= cueOutMs && preloadedTrack?.id == nextEntry.track.id) {
            beginDualDeckTransition(activeTransitionPlan?.durationMs ?: transMs)
        }

        // Advance active transition volume & EQ/gain curves
        if (isMixingTransition) {
            val elapsed = (System.currentTimeMillis() - transitionStartElapsedMs).coerceAtLeast(0L)
            val progress = (elapsed.toFloat() / transitionDurationActiveMs.toFloat()).coerceIn(0f, 1f)
            applyTransitionVolumeCurves(progress)

            if (progress >= 1.0f) {
                completeDualDeckSwap()
            }
        }
    }

    private fun alignToNearestBeat(targetMs: Long, analysis: TrackAnalysis?): Long {
        if (analysis == null || analysis.beatIntervalMs <= 0L || analysis.bpmConfidence < 0.60f || analysis.downbeatConfidence < 0.60f) return targetMs
        val offset = (targetMs - analysis.firstBeatMs).coerceAtLeast(0L)
        val beatIndex = (offset.toDouble() / analysis.beatIntervalMs).toLong()
        return (analysis.firstBeatMs + beatIndex * analysis.beatIntervalMs).coerceAtLeast(0L)
    }

    private fun alignToPhraseOrBeat(targetMs: Long, analysis: TrackAnalysis?): Long {
        if (analysis == null || analysis.beatIntervalMs <= 0L || analysis.bpmConfidence < 0.60f || analysis.downbeatConfidence < 0.60f) return targetMs
        val phraseMs = analysis.phraseLengthMs.takeIf { it > 0L }
            ?: (analysis.beatIntervalMs * 16L)
        val offset = (targetMs - analysis.firstBeatMs).coerceAtLeast(0L)
        val phraseIndex = (offset.toDouble() / phraseMs).toLong()
        return (analysis.firstBeatMs + phraseIndex * phraseMs).coerceAtLeast(0L)
    }

    private suspend fun preloadStandbyDeck(nextTrack: Track) {
        val inAnalysis = container.audioAnalysisEngine.getAnalysisForTrack(nextTrack)
        val outBpm = activeTrackAnalysis?.takeIf { it.bpmConfidence >= 0.60f && it.bpm in 60f..200f }?.bpm
        val inBpm = inAnalysis.takeIf { it.bpmConfidence >= 0.60f && it.bpm in 60f..200f }?.bpm
        if (automixConfig.bpmMatchEnabled && (outBpm == null || inBpm == null)) {
            preloadedTrack = null
            incomingTrackAnalysis = null
            activeTransitionPlan = null
            return
        }

        val outgoingId = activePlayer.currentMediaItem?.mediaId
        val outgoingTrack = outgoingId?.let { allTracksCache[it] }
        val outgoingProfile = outgoingTrack?.let {
            DjTrackProfileFactory.from(it, activeTrackAnalysis)
        }
        val incomingProfile = DjTrackProfileFactory.from(nextTrack, inAnalysis)
        activeTransitionPlan = if (outgoingProfile != null) {
            val outgoingBeatMs = outgoingProfile.beatIntervalMs ?: 0L
            val bars = if (outgoingBeatMs > 0L) {
                ((automixConfig.transitionDurationSec * 1000L).toDouble() /
                    (outgoingBeatMs * 4L).toDouble()).roundToInt().coerceIn(1, 16)
            } else 4
            val curve = when (automixConfig.transitionStyle) {
                TransitionStyle.BASS_SWAP -> DjTransitionCurve.BASS_SWAP
                TransitionStyle.EQ_FILTER -> DjTransitionCurve.VOCAL_AWARE_EQ
                TransitionStyle.BEAT_CUT -> DjTransitionCurve.PHRASE_CUT
                TransitionStyle.INTRO_OUTRO, TransitionStyle.SMOOTH_FADE -> DjTransitionCurve.EQUAL_POWER
            }
            val maxAdjustment = when (automixConfig.harmonicMixLevel) {
                com.example.data.model.HarmonicMixLevel.STRICT -> 0.04f
                com.example.data.model.HarmonicMixLevel.BALANCED -> 0.08f
                com.example.data.model.HarmonicMixLevel.FLEXIBLE -> 0.12f
            }
            (djTransitionPlanner.plan(
                outgoing = outgoingProfile,
                incoming = incomingProfile,
                outgoingPositionMs = activePlayer.currentPosition,
                config = DjTransitionConfig(
                    maxTempoAdjustment = maxAdjustment,
                    bars = bars,
                    curve = curve,
                    keyLockEnabled = automixConfig.keyLockEnabled,
                ),
            ) as? DjTransitionPlanningResult.Ready)?.plan
        } else null

        val tempoRatio = activeTransitionPlan?.incomingPlaybackRate
            ?: if (automixConfig.bpmMatchEnabled && outBpm != null && inBpm != null) {
                HarmonicCompatibility.computeBeatmatchRatio(outBpm, inBpm)
            } else 1.0f
        preloadedTrack = nextTrack
        incomingTrackAnalysis = inAnalysis

        val sb = standbyPlayer
        standbyDjProcessor.resetEq()
        sb.stop()
        sb.setAudioAttributes(audioAttributes, false) // never steal focus from outgoing active deck
        sb.setMediaItem(nextTrack.toMediaItem())
        sb.prepare()
        sb.pause()
        sb.volume = 0f

        val introSeek = (activeTransitionPlan?.incomingStartMs ?: inAnalysis.firstBeatMs)
            .coerceIn(0L, (nextTrack.durationMs / 4).coerceAtLeast(0L))
        if (introSeek > 0L) sb.seekTo(introSeek)

        val pitch = if (automixConfig.keyLockEnabled) 1.0f else tempoRatio
        sb.playbackParameters = PlaybackParameters(tempoRatio, pitch)
    }

    private suspend fun triggerImmediateTransitionToNext(durationMs: Long) {
        val nextEntry = djQueue.firstOrNull() ?: return
        if (preloadedTrack?.id != nextEntry.track.id) {
            preloadStandbyDeck(nextEntry.track)
        }
        if (!isMixingTransition) {
            beginDualDeckTransition((activeTransitionPlan?.durationMs ?: durationMs).coerceIn(800L, 32_000L))
        }
    }

    private fun beginDualDeckTransition(durationMs: Long) {
        val incoming = preloadedTrack ?: return
        isMixingTransition = true
        transitionDurationActiveMs = durationMs
        transitionStartElapsedMs = System.currentTimeMillis()
        standbyPlayer.volume = 0f
        standbyPlayer.play()
        djAnnouncementCounter++
        if (djAnnouncementCounter % 3 == 0) {
            djVoiceDirector?.announceTrack(incoming.title, incoming.artist)
        }
        publishAutomixRuntime(statusOverride = "Mixing into ${incoming.title}...")
    }

    private fun applyTransitionVolumeCurves(p: Float) {
        val outGain = computeAutoGainMultiplier(activePlayer.currentMediaItem?.mediaId)
        val inGain = computeAutoGainMultiplier(preloadedTrack?.id)

        val (rawOut, rawIn) = when (effectiveTransitionStyle()) {
            TransitionStyle.SMOOTH_FADE -> {
                val angle = p * (PI / 2.0)
                cos(angle).toFloat() to sin(angle).toFloat()
            }
            TransitionStyle.BASS_SWAP -> {
                val o = if (p < 0.52f) 1f - p * 0.2f else ((1f - p) / 0.48f).coerceIn(0f, 1f)
                val i = if (p < 0.48f) (p / 0.48f) * 0.65f else (0.65f + ((p - 0.48f) / 0.52f) * 0.35f)
                o to i
            }
            TransitionStyle.EQ_FILTER -> {
                (1f - p).pow(1.35f) to p.pow(0.75f)
            }
            TransitionStyle.INTRO_OUTRO -> {
                (1f - p * 0.92f).coerceIn(0f, 1f) to (p * 1.12f).coerceIn(0f, 1f)
            }
            TransitionStyle.BEAT_CUT -> {
                val o = if (p < 0.84f) 1f else ((1f - p) / 0.16f).coerceIn(0f, 1f)
                val i = if (p < 0.68f) 0f else ((p - 0.68f) / 0.32f).coerceIn(0f, 1f)
                o to i
            }
        }

        val speechGain = if (speechDuckActive) 0.62f else 1f
        activePlayer.volume = (rawOut * outGain * speechGain).coerceIn(0f, 1f)
        standbyPlayer.volume = (rawIn * inGain * speechGain).coerceIn(0f, 1f)
        applyTransitionEq(p)
    }

    private fun updateSpeechDuck() {
        if (isMixingTransition) {
            val progress = ((System.currentTimeMillis() - transitionStartElapsedMs).toFloat() /
                transitionDurationActiveMs.toFloat()).coerceIn(0f, 1f)
            applyTransitionVolumeCurves(progress)
        } else {
            val trackId = activePlayer.currentMediaItem?.mediaId
            val speechGain = if (speechDuckActive) 0.62f else 1f
            activePlayer.volume = (computeAutoGainMultiplier(trackId) * speechGain).coerceIn(0f, 1f)
        }
    }

    private fun effectiveTransitionStyle(): TransitionStyle {
        return when (activeTransitionPlan?.curve) {
            DjTransitionCurve.BASS_SWAP -> TransitionStyle.BASS_SWAP
            DjTransitionCurve.VOCAL_AWARE_EQ -> TransitionStyle.EQ_FILTER
            DjTransitionCurve.PHRASE_CUT -> TransitionStyle.BEAT_CUT
            DjTransitionCurve.EQUAL_POWER -> TransitionStyle.SMOOTH_FADE
            null -> automixConfig.transitionStyle
        }
    }

    private fun applyTransitionEq(progress: Float) {
        val p = progress.coerceIn(0f, 1f)
        when (effectiveTransitionStyle()) {
            TransitionStyle.BASS_SWAP -> {
                activeDjProcessor.setEq(lowDb = -12f * p, midDb = 0f, highDb = 0f)
                standbyDjProcessor.setEq(lowDb = -12f * (1f - p), midDb = 0f, highDb = 0f)
            }
            TransitionStyle.EQ_FILTER -> {
                activeDjProcessor.setEq(lowDb = -3f * p, midDb = -6f * p, highDb = -2f * p)
                standbyDjProcessor.setEq(lowDb = -3f * (1f - p), midDb = -6f * (1f - p), highDb = -2f * (1f - p))
            }
            TransitionStyle.INTRO_OUTRO -> {
                activeDjProcessor.setEq(lowDb = -7f * p, midDb = -1.5f * p, highDb = 0f)
                standbyDjProcessor.setEq(lowDb = -7f * (1f - p), midDb = -1.5f * (1f - p), highDb = 0f)
            }
            TransitionStyle.SMOOTH_FADE, TransitionStyle.BEAT_CUT -> {
                activeDjProcessor.resetEq()
                standbyDjProcessor.resetEq()
            }
        }
    }

    private fun completeDualDeckSwap() {
        val outgoing = activePlayer
        val incoming = standbyPlayer
        val outId = outgoing.currentMediaItem?.mediaId.orEmpty()
        val outTrack = allTracksCache[outId]
        val inTrack = preloadedTrack
        val inEntry = djQueue.firstOrNull { it.track.id == inTrack?.id }

        // Stop outgoing deck cleanly
        outgoing.pause()
        outgoing.stop()
        outgoing.clearMediaItems()
        outgoing.setAudioAttributes(audioAttributes, false)

        // Flip active deck & attach session + audio focus + PCM spectrum tap
        activeDeckIndex = 1 - activeDeckIndex
        djProcessorA.resetEq()
        djProcessorB.resetEq()
        balanceProcessorA.emitSpectrum = (activeDeckIndex == 0)
        balanceProcessorB.emitSpectrum = (activeDeckIndex == 1)
        incoming.setAudioAttributes(audioAttributes, true)
        incoming.volume = computeAutoGainMultiplier(inTrack?.id) * if (speechDuckActive) 0.62f else 1f
        updateProcessorNormalization()
        mediaLibrarySession?.player = incoming
        container.audioEffectsManager.attachToAudioSession(incoming.audioSessionId)

        isMixingTransition = false
        preloadedTrack = null
        activeTransitionPlan = null
        lastRecordedTrackId = null

        val prevAnalysis = activeTrackAnalysis
        activeTrackAnalysis = incomingTrackAnalysis
        incomingTrackAnalysis = null

        // Smoothly return incoming deck tempo to 1.0x after transition blend
        easeTempoToNatural(incoming)

        // Record history & replenish queue
        if (outTrack != null && inTrack != null) {
            recordRecentDjTrack(outTrack.id)
            recordRecentDjTrack(inTrack.id)
            djQueue.removeAll { it.track.id == inTrack.id }

            serviceScope.launch(Dispatchers.IO) {
                container.database.mixHistoryDao().insertMixHistory(
                    MixHistoryEntity(
                        fromTrackId = outTrack.id,
                        fromTitle = outTrack.title,
                        fromArtist = outTrack.artist,
                        toTrackId = inTrack.id,
                        toTitle = inTrack.title,
                        toArtist = inTrack.artist,
                        mixMode = automixConfig.mode.name,
                        transitionStyle = automixConfig.transitionStyle.name,
                        bpmFrom = prevAnalysis?.takeIf { it.bpmConfidence >= 0.60f }?.bpm ?: 0f,
                        bpmTo = activeTrackAnalysis?.takeIf { it.bpmConfidence >= 0.60f }?.bpm ?: 0f,
                        camelotFrom = prevAnalysis?.takeIf { it.keyConfidence >= 0.60f }?.camelotCode ?: "--",
                        camelotTo = activeTrackAnalysis?.takeIf { it.keyConfidence >= 0.60f }?.camelotCode ?: "--",
                        compatibilityScore = inEntry?.compatibilityScore ?: 80,
                    )
                )
            }
        }

        if (inTrack != null) {
            serviceScope.launch { recomputeDjQueueInternal(inTrack) }
        }

        updateWidgetFromPlayer()
        persistSnapshotNow()
        publishAutomixRuntime()
    }

    private fun easeTempoToNatural(player: ExoPlayer) {
        tempoEaseJob?.cancel()
        val startRate = player.playbackParameters.speed
        val startPitch = player.playbackParameters.pitch
        if (kotlin.math.abs(startRate - 1.0f) < 0.01f && kotlin.math.abs(startPitch - 1.0f) < 0.01f) return
        tempoEaseJob = serviceScope.launch {
            val steps = 12
            for (s in 1..steps) {
                delay(220L)
                if (player != activePlayer || !player.isPlaying) break
                val t = s.toFloat() / steps
                val rate = startRate + (1.0f - startRate) * t
                val pitch = startPitch + (1.0f - startPitch) * t
                player.playbackParameters = PlaybackParameters(rate, pitch)
            }
            if (player == activePlayer) {
                player.playbackParameters = PlaybackParameters(1.0f, 1.0f)
            }
        }
    }

    private fun computeAutoGainMultiplier(trackId: String?): Float {
        if (!automixConfig.autoGainEnabled || trackId == null) return 1.0f
        val lufs = analysesCache[trackId]?.loudnessLufs ?: activeTrackAnalysis?.takeIf { it.trackId == trackId }?.loudnessLufs
            ?: return 1.0f
        val deltaDb = (-14.0f - lufs).coerceIn(-6.0f, 4.0f)
        val linear = 10.0.pow((deltaDb / 20.0)).toFloat()
        return linear.coerceIn(0.55f, 1.0f)
    }

    private fun updateProcessorNormalization() {
        val activeId = activePlayer.currentMediaItem?.mediaId
        val standbyId = standbyPlayer.currentMediaItem?.mediaId
        val gainActive = if (loudnessNormalizationEnabled && activeId != null) {
            val lufs = analysesCache[activeId]?.loudnessLufs ?: -14f
            10.0.pow(((-14.0f - lufs).coerceIn(-6.0f, 5.0f)) / 20.0).toFloat()
        } else 1.0f
        val gainStandby = if (loudnessNormalizationEnabled && standbyId != null) {
            val lufs = analysesCache[standbyId]?.loudnessLufs ?: -14f
            10.0.pow(((-14.0f - lufs).coerceIn(-6.0f, 5.0f)) / 20.0).toFloat()
        } else 1.0f

        if (activeDeckIndex == 0) {
            balanceProcessorA.setNormalizationGain(gainActive)
            balanceProcessorB.setNormalizationGain(gainStandby)
        } else {
            balanceProcessorB.setNormalizationGain(gainActive)
            balanceProcessorA.setNormalizationGain(gainStandby)
        }
    }

    private fun startAutomixInternal(seedTrackId: String?) {
        djQueueJob?.cancel()
        djQueueJob = serviceScope.launch {
            val tracks = container.musicRepository.tracksFlow.first()
            allTracksCache = tracks.associateBy { it.id }
            if (tracks.isEmpty()) return@launch

            val currentMediaId = activePlayer.currentMediaItem?.mediaId
            val seedTrack = seedTrackId?.let { id -> tracks.firstOrNull { it.id == id } }
                ?: currentMediaId?.let { id -> tracks.firstOrNull { it.id == id } }
                ?: tracks.first()

            automixActive = true
            automixStartTimeMs = System.currentTimeMillis()
            activeSessionPhase = DjSessionPhase.OPENING
            djAnnouncementCounter = 0
            isMixingTransition = false
            preloadedTrack = null
            recentDjHistoryIds.clear()
            recordRecentDjTrack(seedTrack.id)

            container.prefsRepo.updateAutomixConfig { it.copy(enabled = true) }

            // Ensure seed track is loaded and playing on activePlayer
            if (activePlayer.currentMediaItem?.mediaId != seedTrack.id || !activePlayer.isPlaying) {
                activePlayer.setMediaItems(listOf(seedTrack.toMediaItem()), 0, 0L)
                activePlayer.prepare()
                activePlayer.play()
            }

            activeTrackAnalysis = container.audioAnalysisEngine.getAnalysisForTrack(seedTrack)
            activePlayer.volume = computeAutoGainMultiplier(seedTrack.id)
            djVoiceDirector?.welcome()
            recomputeDjQueueInternal(seedTrack)
            publishAutomixRuntime()
        }
    }

    private fun stopAutomixInternal() {
        automixActive = false
        isMixingTransition = false
        preloadedTrack = null
        activeTransitionPlan = null
        djQueueJob?.cancel()
        tempoEaseJob?.cancel()
        standbyPlayer.pause()
        standbyPlayer.stop()
        standbyPlayer.clearMediaItems()
        djVoiceDirector?.farewell()
        activePlayer.volume = 1.0f
        djProcessorA.resetEq()
        djProcessorB.resetEq()
        activePlayer.playbackParameters = PlaybackParameters(currentPlaybackSpeed)
        serviceScope.launch {
            container.prefsRepo.updateAutomixConfig { it.copy(enabled = false) }
        }
        publishAutomixRuntime()
    }

    private fun scheduleLiveDjQueueRefresh() {
        recomputeDebounceJob?.cancel()
        recomputeDebounceJob = serviceScope.launch {
            delay(280L)
            val currentTrack = activePlayer.currentMediaItem?.mediaId?.let { allTracksCache[it] } ?: return@launch
            recomputeDjQueueInternal(currentTrack)
        }
    }

    private suspend fun recomputeDjQueueInternal(pivotTrack: Track) {
        val tracks = withContext(Dispatchers.IO) { container.musicRepository.tracksFlow.first() }
        allTracksCache = tracks.associateBy { it.id }
        val pool = if (automixConfig.sourcePlaylistId != null) {
            val plTracks = container.musicRepository.observePlaylistWithTracks(automixConfig.sourcePlaylistId!!).first()?.tracks
            if (!plTracks.isNullOrEmpty()) plTracks else tracks
        } else if (!automixConfig.sourceFolderPath.isNullOrBlank()) {
            tracks.filter { it.folderPath == automixConfig.sourceFolderPath }.ifEmpty { tracks }
        } else {
            tracks
        }

        withContext(Dispatchers.IO) {
            val unanalyzed = pool.filter { it.id !in analysesCache }.take(16)
            for (t in unanalyzed) {
                val res = container.audioAnalysisEngine.getAnalysisForTrack(t)
                analysesCache = analysesCache + (t.id to res)
            }
        }

        val currentAnalysis = analysesCache[pivotTrack.id]
            ?: container.audioAnalysisEngine.getAnalysisForTrack(pivotTrack).also {
                analysesCache = analysesCache + (pivotTrack.id to it)
            }
        val currentProfile = DjTrackProfileFactory.from(pivotTrack, currentAnalysis)
        val profilesById = pool.associate { track ->
            track.id to DjTrackProfileFactory.from(track, analysesCache[track.id])
        }
        val sessionProgress = if (automixConfig.mixDurationMinutes > 0) {
            ((System.currentTimeMillis() - automixStartTimeMs).toFloat() /
                (automixConfig.mixDurationMinutes * 60_000f)).coerceIn(0f, 1f)
        } else 0f
        val plannedSessionPhase = when (automixConfig.energyProgression) {
            com.example.data.model.EnergyProgression.BUILD_UP -> DjSessionArcPlanner.phaseFor(sessionProgress)
            com.example.data.model.EnergyProgression.PEAK_HOUR -> DjSessionPhase.PEAK_TIME
            com.example.data.model.EnergyProgression.STEADY -> DjSessionPhase.FLOW
            com.example.data.model.EnergyProgression.CHILL_DOWN -> DjSessionPhase.COOL_DOWN
            com.example.data.model.EnergyProgression.WAVE -> {
                val waveProgress = (sessionProgress * 2f).let { if (it <= 1f) it else 2f - it }
                DjSessionArcPlanner.phaseFor(waveProgress)
            }
        }
        val recentEnergyValues = recentDjHistoryIds.mapNotNull { id ->
            analysesCache[id]?.takeIf { it.bpmConfidence >= 0.40f }?.energy
        }.takeLast(5)
        val recentAverageEnergy = recentEnergyValues.takeIf { it.isNotEmpty() }?.average()?.toFloat()
        val sessionPhase = if (automixConfig.operatingMode == DjOperatingMode.AUTOMIX) {
            DjSessionPhase.FLOW
        } else {
            if (activeSessionPhase == DjSessionPhase.FLOW || recentAverageEnergy == null ||
                !DjSessionArcPlanner.shouldHoldPhase(
                    current = activeSessionPhase,
                    recentAverageEnergy = recentAverageEnergy,
                    targetEnergy = activeSessionPhase.targetEnergy,
                )
            ) {
                activeSessionPhase = plannedSessionPhase
            }
            activeSessionPhase
        }
        val techniqueWeights = when (automixConfig.technique) {
            AutomixTechnique.BEAT_SYNC -> listOf(0.65f, 0.10f, 0.15f, 0.10f)
            AutomixTechnique.HARMONIC -> listOf(0.20f, 0.55f, 0.15f, 0.10f)
            AutomixTechnique.PHRASE_MATCH -> listOf(0.45f, 0.10f, 0.20f, 0.25f)
            AutomixTechnique.ENERGY_FLOW -> listOf(0.15f, 0.10f, 0.20f, 0.55f)
            AutomixTechnique.GENRE_MATCH -> listOf(0.15f, 0.10f, 0.60f, 0.15f)
            AutomixTechnique.HUMAN_STYLE -> listOf(
                automixConfig.bpmWeight, automixConfig.harmonicWeight,
                automixConfig.genreWeight, automixConfig.energyWeight,
            )
        }
        val maxTempoAdjustment = when {
            automixConfig.technique == AutomixTechnique.BEAT_SYNC -> 0.04f
            automixConfig.harmonicMixLevel == com.example.data.model.HarmonicMixLevel.STRICT -> 0.04f
            automixConfig.harmonicMixLevel == com.example.data.model.HarmonicMixLevel.BALANCED -> 0.08f
            else -> 0.12f
        }
        val decisionConfig = DjDecisionConfig(
            strictGenreMatching = automixConfig.strictGenreMatching,
            allowOpenFormatBridging = automixConfig.openFormatBridging,
            requireReliableTempo = automixConfig.bpmMatchEnabled ||
                automixConfig.technique == AutomixTechnique.BEAT_SYNC ||
                automixConfig.technique == AutomixTechnique.PHRASE_MATCH,
            requireReliableGenre = automixConfig.strictGenreMatching ||
                automixConfig.technique == AutomixTechnique.GENRE_MATCH,
            requireReliableKey = automixConfig.technique == AutomixTechnique.HARMONIC ||
                (automixConfig.keyMatchingEnabled &&
                    automixConfig.harmonicMixLevel == com.example.data.model.HarmonicMixLevel.STRICT),
            requirePhraseStructure = automixConfig.technique == AutomixTechnique.PHRASE_MATCH,
            maxTempoAdjustment = maxTempoAdjustment,
            minTrackDurationMs = 45_000L,
            maxUnknownContentDurationMs = 12L * 60L * 1000L,
            sessionPhase = sessionPhase,
            bpmWeight = techniqueWeights[0],
            keyWeight = techniqueWeights[1],
            genreWeight = techniqueWeights[2],
            energyWeight = techniqueWeights[3],
        )
        val eligibleTrackIds = pool.filter { candidate ->
            if (candidate.id == pivotTrack.id) return@filter false
            if (automixConfig.genrePreference != "All Genres") {
                val candidateGenre = GenreEngine.classifyTrack(candidate, analysesCache[candidate.id])
                if (!candidateGenre.subgenre.equals(automixConfig.genrePreference, ignoreCase = true) &&
                    !candidateGenre.primaryGenre.equals(automixConfig.genrePreference, ignoreCase = true) &&
                    !candidate.genre.equals(automixConfig.genrePreference, ignoreCase = true)
                ) return@filter false
            }
            val candidateAnalysis = analysesCache[candidate.id]
            if (automixConfig.autoSelectMood && candidateAnalysis != null) {
                when (automixConfig.mood) {
                    com.example.data.model.MoodFilter.CHILL -> if (candidateAnalysis.energy > 0.68f) return@filter false
                    com.example.data.model.MoodFilter.ENERGETIC -> if (candidateAnalysis.energy < 0.35f) return@filter false
                    com.example.data.model.MoodFilter.PARTY -> if (candidateAnalysis.energy < 0.48f) return@filter false
                }
            }
            if (customRulesCache.isNotEmpty()) {
                val legacyGate = HarmonicCompatibility.evaluateTransition(
                    current = pivotTrack,
                    currentAnalysis = currentAnalysis,
                    candidate = candidate,
                    candidateAnalysis = candidateAnalysis,
                    config = automixConfig,
                    customRules = customRulesCache,
                )
                if (!legacyGate.allowed) return@filter false
            }
            true
        }.map { it.id }.toSet()
        val eligibleProfiles = profilesById.values.filter { profile ->
            profile.trackId in eligibleTrackIds &&
                (profile.bpm == null ||
                    profile.bpm in automixConfig.bpmMin.toFloat()..automixConfig.bpmMax.toFloat())
        }
        val ranking = djDecisionEngine.rankNextTracks(
            current = currentProfile,
            candidates = eligibleProfiles,
            recentTrackIds = if (automixConfig.avoidRepeats) recentDjHistoryIds.toSet() else emptySet(),
            config = decisionConfig,
            count = 15,
            recentArtistNames = recentDjHistoryIds.mapNotNull { allTracksCache[it]?.artist }
                .filter { it.isNotBlank() && !it.equals("Unknown Artist", ignoreCase = true) }
                .takeLast(5)
                .toSet(),
        )
        val rankedQueue = ranking.rankedCandidates.mapNotNull { score ->
            val track = allTracksCache[score.trackId] ?: return@mapNotNull null
            DjQueuedTrack(
                track = track,
                analysis = analysesCache[track.id],
                compatibilityScore = score.totalScore,
                reason = score.reasons.joinToString(" · "),
            )
        }
        val rejectionSummary = ranking.rejectedCandidates
            .groupingBy { it.reason }
            .eachCount()
            .entries
            .sortedByDescending { it.value }
            .take(2)
            .joinToString("; ") { "${it.key} (${it.value})" }
        val noTrackReason = when {
            currentProfile.contentKind != AudioContentKind.MUSIC ->
                "Current track is not verified music. Select a music track to start Intelligent DJ."
            rankedQueue.isEmpty() -> rejectionSummary.ifBlank {
                "No tracks meet the current tempo, genre, and content safety rules."
            }
            else -> null
        }
        djQueueMutex.withLock {
            val previousFirstId = djQueue.firstOrNull()?.track?.id
            djQueue.clear()
            djQueue.addAll(rankedQueue)
            noSuitableTrackReason = noTrackReason
            if (!isMixingTransition && previousFirstId != rankedQueue.firstOrNull()?.track?.id) {
                preloadedTrack = null
                activeTransitionPlan = null
            }
        }
        publishAutomixRuntime()
    }

    private fun recordRecentDjTrack(id: String) {
        if (id.isBlank()) return
        recentDjHistoryIds.remove(id)
        recentDjHistoryIds.addLast(id)
        while (recentDjHistoryIds.size > 25) {
            recentDjHistoryIds.removeFirst()
        }
    }

    private fun publishAutomixRuntime(statusOverride: String? = null) {
        val elapsedMin = if (automixActive) {
            ((System.currentTimeMillis() - automixStartTimeMs) / 60_000L).toInt()
        } else 0
        val remainingMin = (automixConfig.mixDurationMinutes - elapsedMin).coerceAtLeast(0)

        val deckATrack = deckA.currentMediaItem?.mediaId?.let { allTracksCache[it] }
        val deckBTrack = deckB.currentMediaItem?.mediaId?.let { allTracksCache[it] }
        val aAnalysis = deckATrack?.let { analysesCache[it.id] ?: activeTrackAnalysis?.takeIf { a -> a.trackId == it.id } }
        val bAnalysis = deckBTrack?.let { analysesCache[it.id] ?: incomingTrackAnalysis?.takeIf { a -> a.trackId == it.id } }

        val aGenre = deckATrack?.let { GenreEngine.classifyTrack(it, aAnalysis).subgenre } ?: ""
        val bGenre = deckBTrack?.let { GenreEngine.classifyTrack(it, bAnalysis).subgenre } ?: ""

        val deckAState = DeckState(
            deckName = "Deck A",
            track = deckATrack,
            isActiveForeground = activeDeckIndex == 0,
            isPlaying = deckA.isPlaying,
            positionMs = deckA.currentPosition.coerceAtLeast(0L),
            durationMs = deckA.duration.takeIf { it > 0L } ?: deckATrack?.durationMs ?: 0L,
            volume = deckA.volume,
            bpm = aAnalysis?.bpm ?: 0f,
            camelotCode = aAnalysis?.camelotCode ?: "--",
            musicalKey = aAnalysis?.musicalKey ?: "--",
            genreLabel = aGenre,
            playbackRate = deckA.playbackParameters.speed,
        )
        val deckBState = DeckState(
            deckName = "Deck B",
            track = deckBTrack,
            isActiveForeground = activeDeckIndex == 1,
            isPlaying = deckB.isPlaying,
            positionMs = deckB.currentPosition.coerceAtLeast(0L),
            durationMs = deckB.duration.takeIf { it > 0L } ?: deckBTrack?.durationMs ?: 0L,
            volume = deckB.volume,
            bpm = bAnalysis?.bpm ?: 0f,
            camelotCode = bAnalysis?.camelotCode ?: "--",
            musicalKey = bAnalysis?.musicalKey ?: "--",
            genreLabel = bGenre,
            playbackRate = deckB.playbackParameters.speed,
        )

        val elapsedTrans = if (isMixingTransition) {
            ((System.currentTimeMillis() - transitionStartElapsedMs).toFloat() / transitionDurationActiveMs.toFloat())
                .coerceIn(0f, 1f)
        } else 0f

        val queueSnapshot = djQueue.toList()
        val nextScore = queueSnapshot.firstOrNull()?.compatibilityScore ?: 0
        val profile = DjProfiles.findProfile(automixConfig.activeProfileId, customProfilesCache)
        val status = statusOverride ?: when {
            isMixingTransition -> "Mixing into ${preloadedTrack?.title ?: "next track"}..."
            automixActive && noSuitableTrackReason != null -> noSuitableTrackReason!!
            automixActive -> "${profile.name} · ${automixConfig.energyProgression.label}"
            else -> "Ready to mix your local library offline"
        }

        container.automixRuntime.setState(
            AutomixUiState(
                isActive = automixActive,
                isMixingTransition = isMixingTransition,
                transitionProgress = elapsedTrans,
                activeDeckIndex = activeDeckIndex,
                deckA = deckAState,
                deckB = deckBState,
                djQueue = queueSnapshot,
                currentCompatibilityScore = nextScore,
                statusText = status,
                noSuitableTrackReason = if (automixActive) noSuitableTrackReason else null,
                activeProfileName = profile.name,
                analyzedCount = analysesCache.size,
                totalTrackCount = allTracksCache.size,
                remainingMixMinutes = remainingMin,
            )
        )
    }

    private fun startSleepTimer(durationMs: Long) {
        sleepTimerJob?.cancel()
        stopAfterCurrentTrack = false
        sleepActive = true
        sleepRemainingMs = durationMs
        sleepTimerJob = serviceScope.launch {
            while (sleepRemainingMs > 0L && isActive) {
                delay(1000L)
                sleepRemainingMs = (sleepRemainingMs - 1000L).coerceAtLeast(0L)
            }
            if (isActive && sleepActive) {
                activePlayer.pause()
                if (isMixingTransition) standbyPlayer.pause()
                sleepActive = false
                sleepRemainingMs = 0L
            }
        }
    }

    private fun enableStopAfterCurrentTrack() {
        sleepTimerJob?.cancel()
        sleepActive = true
        stopAfterCurrentTrack = true
        sleepRemainingMs = 0L
    }

    private fun cancelSleepTimer() {
        sleepTimerJob?.cancel()
        sleepActive = false
        stopAfterCurrentTrack = false
        sleepRemainingMs = 0L
    }

    private fun persistSnapshotNow() {
        val player = activePlayer
        val count = player.mediaItemCount
        val ids = (0 until count).mapNotNull { player.getMediaItemAt(it).mediaId.takeIf(String::isNotBlank) }
        val index = player.currentMediaItemIndex.coerceAtLeast(0)
        val pos = player.currentPosition.coerceAtLeast(0L)
        val shuffle = player.shuffleModeEnabled
        val repeat = when (player.repeatMode) {
            Player.REPEAT_MODE_ALL -> "ALL"
            Player.REPEAT_MODE_ONE -> "ONE"
            else -> "OFF"
        }
        serviceScope.launch(Dispatchers.IO) {
            container.database.playbackStateDao().replaceQueue(ids)
            container.database.playbackStateDao().saveSnapshot(
                PlaybackSnapshotEntity(
                    id = 1,
                    currentIndex = index,
                    positionMs = pos,
                    shuffleEnabled = shuffle,
                    repeatMode = repeat,
                )
            )
        }
    }

    private suspend fun restorePersistedQueueIntoPlayer(): MediaSession.MediaItemsWithStartPosition {
        val dao = container.database.playbackStateDao()
        val queueIds = dao.getQueueOnce().map { it.trackId }
        val snapshot = dao.getSnapshotOnce()
        val tracks = container.musicRepository.tracksFlow.first().associateBy { it.id }
        val mediaItems = queueIds.mapNotNull { id -> tracks[id]?.toMediaItem() }
        val startIndex = (snapshot?.currentIndex ?: 0).coerceIn(0, (mediaItems.size - 1).coerceAtLeast(0))
        val startPos = (snapshot?.positionMs ?: 0L).coerceAtLeast(0L)

        val player = activePlayer
        if (mediaItems.isNotEmpty() && player.mediaItemCount == 0) {
            player.setMediaItems(mediaItems, startIndex, startPos)
            player.shuffleModeEnabled = snapshot?.shuffleEnabled ?: false
            player.repeatMode = when (snapshot?.repeatMode) {
                "ALL" -> Player.REPEAT_MODE_ALL
                "ONE" -> Player.REPEAT_MODE_ONE
                else -> Player.REPEAT_MODE_OFF
            }
            player.prepare()
            player.pause()
        }
        return MediaSession.MediaItemsWithStartPosition(mediaItems, startIndex, startPos)
    }

    private fun updateWidgetFromPlayer() {
        val meta = activePlayer.currentMediaItem?.mediaMetadata
        MaxmizeWidgetProvider.updateWidgetState(
            context = this,
            title = meta?.title?.toString(),
            artist = meta?.artist?.toString(),
            isPlaying = activePlayer.isPlaying,
            artworkUri = meta?.artworkUri?.toString(),
        )
    }

    private inner class LibrarySessionCallback : MediaLibrarySession.Callback {

        override fun onConnect(
            session: MediaSession,
            controller: MediaSession.ControllerInfo,
        ): MediaSession.ConnectionResult {
            val sessionCommands = MediaSession.ConnectionResult.DEFAULT_SESSION_AND_LIBRARY_COMMANDS
                .buildUpon()
                .add(SessionCommand(CMD_SET_BALANCE, Bundle.EMPTY))
                .add(SessionCommand(CMD_SET_CROSSFADE, Bundle.EMPTY))
                .add(SessionCommand(CMD_START_SLEEP_TIMER, Bundle.EMPTY))
                .add(SessionCommand(CMD_STOP_AFTER_TRACK, Bundle.EMPTY))
                .add(SessionCommand(CMD_CANCEL_SLEEP_TIMER, Bundle.EMPTY))
                .add(SessionCommand(CMD_GET_STATE_EXTRAS, Bundle.EMPTY))
                .add(SessionCommand(CMD_START_AUTOMIX, Bundle.EMPTY))
                .add(SessionCommand(CMD_STOP_AUTOMIX, Bundle.EMPTY))
                .add(SessionCommand(CMD_SKIP_AUTOMIX_NEXT, Bundle.EMPTY))
                .add(SessionCommand(CMD_RECOMPUTE_DJ_QUEUE, Bundle.EMPTY))
                .add(SessionCommand(CMD_MOVE_DJ_QUEUE, Bundle.EMPTY))
                .add(SessionCommand(CMD_REMOVE_DJ_QUEUE, Bundle.EMPTY))
                .add(SessionCommand(CMD_PLAY_DJ_TRACK_NOW, Bundle.EMPTY))
                .build()

            return MediaSession.ConnectionResult.AcceptedResultBuilder(session)
                .setAvailableSessionCommands(sessionCommands)
                .build()
        }

        override fun onCustomCommand(
            session: MediaSession,
            controller: MediaSession.ControllerInfo,
            customCommand: SessionCommand,
            args: Bundle,
        ): ListenableFuture<SessionResult> {
            when (customCommand.customAction) {
                CMD_SET_BALANCE -> {
                    val value = args.getFloat(ARG_FLOAT, 0f)
                    balanceProcessorA.setBalance(value)
                    balanceProcessorB.setBalance(value)
                }
                CMD_SET_CROSSFADE -> {
                    crossfadeSeconds = args.getInt(ARG_INT, 0).coerceIn(0, 12)
                }
                CMD_START_SLEEP_TIMER -> {
                    val durationMs = args.getLong(ARG_LONG, 0L)
                    if (durationMs > 0L) startSleepTimer(durationMs)
                }
                CMD_STOP_AFTER_TRACK -> {
                    enableStopAfterCurrentTrack()
                }
                CMD_CANCEL_SLEEP_TIMER -> {
                    cancelSleepTimer()
                }
                CMD_START_AUTOMIX -> {
                    val seedId = args.getString(ARG_TRACK_ID)
                    startAutomixInternal(seedId)
                }
                CMD_STOP_AUTOMIX -> {
                    stopAutomixInternal()
                }
                CMD_SKIP_AUTOMIX_NEXT -> {
                    if (automixActive && djQueue.isNotEmpty()) {
                        serviceScope.launch { triggerImmediateTransitionToNext(durationMs = 2800L) }
                    }
                }
                CMD_RECOMPUTE_DJ_QUEUE -> {
                    if (automixActive) {
                        val currentTrack = activePlayer.currentMediaItem?.mediaId?.let { allTracksCache[it] }
                        if (currentTrack != null) {
                            serviceScope.launch { recomputeDjQueueInternal(currentTrack) }
                        }
                    }
                }
                CMD_MOVE_DJ_QUEUE -> {
                    val from = args.getInt(ARG_INT, -1)
                    val to = args.getInt(ARG_INT_2, -1)
                    if (from in djQueue.indices && to in djQueue.indices && from != to) {
                        val item = djQueue.removeAt(from)
                        djQueue.add(to, item)
                        preloadedTrack = null
                        publishAutomixRuntime()
                    }
                }
                CMD_REMOVE_DJ_QUEUE -> {
                    val idx = args.getInt(ARG_INT, -1)
                    if (idx in djQueue.indices) {
                        val removed = djQueue.removeAt(idx)
                        if (preloadedTrack?.id == removed.track.id) preloadedTrack = null
                        publishAutomixRuntime()
                    }
                }
                CMD_PLAY_DJ_TRACK_NOW -> {
                    val trackId = args.getString(ARG_TRACK_ID)
                    val target = trackId?.let { allTracksCache[it] }
                    if (target != null && automixActive) {
                        serviceScope.launch {
                            preloadStandbyDeck(target)
                            beginDualDeckTransition(2200L)
                        }
                    }
                }
                CMD_GET_STATE_EXTRAS -> {
                    val out = Bundle().apply {
                        putBoolean(EXTRA_SLEEP_ACTIVE, sleepActive)
                        putLong(EXTRA_SLEEP_REMAINING_MS, sleepRemainingMs)
                        putBoolean(EXTRA_SLEEP_AFTER_TRACK, stopAfterCurrentTrack)
                        putFloat(EXTRA_BALANCE, balanceProcessorA.getBalance())
                        putInt(EXTRA_CROSSFADE, crossfadeSeconds)
                        putInt(EXTRA_AUDIO_SESSION_ID, activePlayer.audioSessionId)
                        putString(EXTRA_ACTIVE_MEDIA_ID, activePlayer.currentMediaItem?.mediaId)
                        putInt(EXTRA_ACTIVE_DECK, activeDeckIndex)
                    }
                    return Futures.immediateFuture(SessionResult(SessionResult.RESULT_SUCCESS, out))
                }
            }
            return Futures.immediateFuture(SessionResult(SessionResult.RESULT_SUCCESS))
        }

        override fun onPlaybackResumption(
            mediaSession: MediaSession,
            controller: MediaSession.ControllerInfo,
        ): ListenableFuture<MediaSession.MediaItemsWithStartPosition> {
            val future = SettableFuture.create<MediaSession.MediaItemsWithStartPosition>()
            serviceScope.launch {
                val restored = runCatching { restorePersistedQueueIntoPlayer() }
                    .getOrDefault(MediaSession.MediaItemsWithStartPosition(emptyList(), 0, 0L))
                future.set(restored)
            }
            return future
        }

        override fun onGetLibraryRoot(
            session: MediaLibrarySession,
            browser: MediaSession.ControllerInfo,
            params: LibraryParams?,
        ): ListenableFuture<LibraryResult<MediaItem>> {
            val root = MediaItem.Builder()
                .setMediaId(ROOT_ID)
                .setMediaMetadata(
                    MediaMetadata.Builder()
                        .setTitle("Maxmize")
                        .setIsBrowsable(true)
                        .setIsPlayable(false)
                        .setMediaType(MediaMetadata.MEDIA_TYPE_FOLDER_MIXED)
                        .build()
                )
                .build()
            return Futures.immediateFuture(LibraryResult.ofItem(root, params))
        }

        override fun onGetChildren(
            session: MediaLibrarySession,
            browser: MediaSession.ControllerInfo,
            parentId: String,
            page: Int,
            pageSize: Int,
            params: LibraryParams?,
        ): ListenableFuture<LibraryResult<ImmutableList<MediaItem>>> {
            val future = SettableFuture.create<LibraryResult<ImmutableList<MediaItem>>>()
            serviceScope.launch {
                val tracks = container.musicRepository.tracksFlow.first()
                val playlists = container.musicRepository.playlistsFlow.first()
                val items = when {
                    parentId == ROOT_ID -> listOf(
                        browsableNode(NODE_ALL_SONGS, "All Songs", MediaMetadata.MEDIA_TYPE_FOLDER_MIXED),
                        browsableNode(NODE_RECENT, "Recently Played", MediaMetadata.MEDIA_TYPE_FOLDER_MIXED),
                        browsableNode(NODE_FAVORITES, "Favorites", MediaMetadata.MEDIA_TYPE_FOLDER_MIXED),
                        browsableNode(NODE_ALBUMS, "Albums", MediaMetadata.MEDIA_TYPE_FOLDER_ALBUMS),
                        browsableNode(NODE_ARTISTS, "Artists", MediaMetadata.MEDIA_TYPE_FOLDER_ARTISTS),
                        browsableNode(NODE_PLAYLISTS, "Playlists", MediaMetadata.MEDIA_TYPE_FOLDER_PLAYLISTS),
                    )
                    parentId == NODE_ALL_SONGS -> tracks.map { it.toMediaItem() }
                    parentId == NODE_FAVORITES -> tracks.filter { it.isFavorite }.map { it.toMediaItem() }
                    parentId == NODE_RECENT -> tracks.filter { it.lastPlayedAt > 0L }
                        .sortedByDescending { it.lastPlayedAt }
                        .map { it.toMediaItem() }
                    parentId == NODE_ALBUMS -> tracks.groupBy { it.album }
                        .keys.sorted()
                        .map { album ->
                            browsableNode("album:$album", album, MediaMetadata.MEDIA_TYPE_ALBUM)
                        }
                    parentId == NODE_ARTISTS -> tracks.groupBy { it.artist }
                        .keys.sorted()
                        .map { artist ->
                            browsableNode("artist:$artist", artist, MediaMetadata.MEDIA_TYPE_ARTIST)
                        }
                    parentId == NODE_PLAYLISTS -> playlists.map { p ->
                        browsableNode("playlist:${p.id}", p.name, MediaMetadata.MEDIA_TYPE_PLAYLIST)
                    }
                    parentId.startsWith("album:") -> {
                        val album = parentId.removePrefix("album:")
                        tracks.filter { it.album == album }.map { it.toMediaItem() }
                    }
                    parentId.startsWith("artist:") -> {
                        val artist = parentId.removePrefix("artist:")
                        tracks.filter { it.artist == artist }.map { it.toMediaItem() }
                    }
                    parentId.startsWith("playlist:") -> {
                        val id = parentId.removePrefix("playlist:").toLongOrNull()
                        if (id != null) {
                            container.musicRepository.observePlaylistWithTracks(id).first()
                                ?.tracks?.map { it.toMediaItem() }.orEmpty()
                        } else emptyList()
                    }
                    else -> emptyList()
                }
                val paged = if (pageSize > 0) {
                    val from = (page * pageSize).coerceAtMost(items.size)
                    val to = (from + pageSize).coerceAtMost(items.size)
                    items.subList(from, to)
                } else items
                future.set(LibraryResult.ofItemList(ImmutableList.copyOf(paged), params))
            }
            return future
        }

        override fun onGetItem(
            session: MediaLibrarySession,
            browser: MediaSession.ControllerInfo,
            mediaId: String,
        ): ListenableFuture<LibraryResult<MediaItem>> {
            val future = SettableFuture.create<LibraryResult<MediaItem>>()
            serviceScope.launch {
                val track = container.musicRepository.tracksFlow.first().firstOrNull { it.id == mediaId }
                if (track != null) {
                    future.set(LibraryResult.ofItem(track.toMediaItem(), null))
                } else {
                    future.set(LibraryResult.ofError(SessionError.ERROR_BAD_VALUE))
                }
            }
            return future
        }

        override fun onGetSearchResult(
            session: MediaLibrarySession,
            browser: MediaSession.ControllerInfo,
            query: String,
            page: Int,
            pageSize: Int,
            params: LibraryParams?,
        ): ListenableFuture<LibraryResult<ImmutableList<MediaItem>>> {
            val future = SettableFuture.create<LibraryResult<ImmutableList<MediaItem>>>()
            serviceScope.launch {
                val q = query.trim().lowercase()
                val matches = container.musicRepository.tracksFlow.first()
                    .filter {
                        it.title.lowercase().contains(q) ||
                            it.artist.lowercase().contains(q) ||
                            it.album.lowercase().contains(q)
                    }
                    .map { it.toMediaItem() }
                future.set(LibraryResult.ofItemList(ImmutableList.copyOf(matches), params))
            }
            return future
        }
    }

    private fun browsableNode(id: String, title: String, mediaType: Int): MediaItem {
        return MediaItem.Builder()
            .setMediaId(id)
            .setMediaMetadata(
                MediaMetadata.Builder()
                    .setTitle(title)
                    .setIsBrowsable(true)
                    .setIsPlayable(false)
                    .setMediaType(mediaType)
                    .build()
            )
            .build()
    }
}

fun Track.toMediaItem(): MediaItem {
    val artUri = customArtworkUri ?: artworkUri
    val metadata = MediaMetadata.Builder()
        .setTitle(title)
        .setArtist(artist)
        .setAlbumTitle(album)
        .setAlbumArtist(albumArtist)
        .setGenre(genre)
        .setTrackNumber(trackNumber)
        .setReleaseYear(year.takeIf { it > 0 })
        .setArtworkUri(artUri?.let(Uri::parse))
        .setIsBrowsable(false)
        .setIsPlayable(true)
        .setMediaType(MediaMetadata.MEDIA_TYPE_MUSIC)
        .build()
    return MediaItem.Builder()
        .setMediaId(id)
        .setUri(Uri.parse(contentUri))
        .setMediaMetadata(metadata)
        .build()
}
