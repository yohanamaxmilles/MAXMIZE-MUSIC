package com.example.player

import android.media.audiofx.BassBoost
import android.media.audiofx.Equalizer
import android.media.audiofx.Virtualizer
import com.example.data.local.prefs.UserPreferencesRepository
import com.example.data.model.EqBandState
import com.example.data.model.EqualizerUiState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class AudioEffectsManager(
    private val prefsRepo: UserPreferencesRepository,
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    private var equalizer: Equalizer? = null
    private var bassBoost: BassBoost? = null
    private var virtualizer: Virtualizer? = null
    private var currentSessionId: Int = 0

    private val _uiState = MutableStateFlow(EqualizerUiState())
    val uiState: StateFlow<EqualizerUiState> = _uiState.asStateFlow()

    init {
        scope.launch {
            val prefs = prefsRepo.getOnce()
            _uiState.value = _uiState.value.copy(
                isEnabled = prefs.eqEnabled,
                currentPresetName = prefs.eqPreset,
                bassBoostStrength = prefs.bassBoostStrength,
                virtualizerStrength = prefs.virtualizerStrength,
            )
        }
    }

    fun attachToAudioSession(audioSessionId: Int) {
        if (audioSessionId <= 0 || audioSessionId == currentSessionId) return
        releaseEffects()
        currentSessionId = audioSessionId

        scope.launch {
            val prefs = prefsRepo.getOnce()
            var eqSupported = false
            var bassSupported = false
            var virtSupported = false
            var minMb = -1500
            var maxMb = 1500
            val bands = mutableListOf<EqBandState>()
            var failureMessage: String? = null

            runCatching {
                val eq = Equalizer(0, audioSessionId)
                equalizer = eq
                eqSupported = true
                val range = eq.bandLevelRange
                minMb = range[0].toInt()
                maxMb = range[1].toInt()
                val numBands = eq.numberOfBands.toInt()
                val savedLevels = prefs.eqBandLevelsMb
                val targetLevels = if (savedLevels.size == numBands) {
                    savedLevels
                } else {
                    computePresetLevels(prefs.eqPreset, numBands, minMb, maxMb)
                }

                for (i in 0 until numBands) {
                    val level = targetLevels.getOrElse(i) { 0 }.coerceIn(minMb, maxMb)
                    eq.setBandLevel(i.toShort(), level.toShort())
                    bands.add(
                        EqBandState(
                            index = i,
                            centerFreqHz = eq.getCenterFreq(i.toShort()) / 1000,
                            levelMillibels = level,
                        )
                    )
                }
                eq.enabled = prefs.eqEnabled
            }.onFailure {
                failureMessage = "Hardware equalizer unavailable on this audio output."
            }

            runCatching {
                val bb = BassBoost(0, audioSessionId)
                bassBoost = bb
                bassSupported = bb.strengthSupported
                if (bassSupported) {
                    bb.setStrength(prefs.bassBoostStrength.coerceIn(0, 1000).toShort())
                }
                bb.enabled = prefs.eqEnabled && prefs.bassBoostStrength > 0
            }

            runCatching {
                val virt = Virtualizer(0, audioSessionId)
                virtualizer = virt
                virtSupported = virt.strengthSupported
                if (virtSupported) {
                    virt.setStrength(prefs.virtualizerStrength.coerceIn(0, 1000).toShort())
                }
                virt.enabled = prefs.eqEnabled && prefs.virtualizerStrength > 0
            }

            _uiState.value = _uiState.value.copy(
                isSupported = eqSupported,
                isBassBoostSupported = bassSupported,
                isVirtualizerSupported = virtSupported,
                isEnabled = prefs.eqEnabled,
                minLevelMb = minMb,
                maxLevelMb = maxMb,
                bands = bands,
                currentPresetName = prefs.eqPreset,
                bassBoostStrength = prefs.bassBoostStrength,
                virtualizerStrength = prefs.virtualizerStrength,
                unavailableReason = failureMessage,
            )
        }
    }

    fun setEnabled(enabled: Boolean) {
        runCatching { equalizer?.enabled = enabled }
        runCatching { bassBoost?.enabled = enabled && _uiState.value.bassBoostStrength > 0 }
        runCatching { virtualizer?.enabled = enabled && _uiState.value.virtualizerStrength > 0 }
        _uiState.value = _uiState.value.copy(isEnabled = enabled)
        persistCurrent()
    }

    fun setBandLevel(bandIndex: Int, levelMb: Int) {
        val current = _uiState.value
        val clamped = levelMb.coerceIn(current.minLevelMb, current.maxLevelMb)
        runCatching { equalizer?.setBandLevel(bandIndex.toShort(), clamped.toShort()) }
        val updatedBands = current.bands.map {
            if (it.index == bandIndex) it.copy(levelMillibels = clamped) else it
        }
        _uiState.value = current.copy(
            bands = updatedBands,
            currentPresetName = "Custom",
        )
        persistCurrent()
    }

    fun applyPreset(presetName: String) {
        val current = _uiState.value
        val numBands = current.bands.size.takeIf { it > 0 } ?: 5
        val levels = computePresetLevels(presetName, numBands, current.minLevelMb, current.maxLevelMb)
        levels.forEachIndexed { idx, mb ->
            runCatching { equalizer?.setBandLevel(idx.toShort(), mb.toShort()) }
        }
        val updatedBands = current.bands.mapIndexed { idx, band ->
            band.copy(levelMillibels = levels.getOrElse(idx) { 0 })
        }
        _uiState.value = current.copy(
            bands = updatedBands,
            currentPresetName = presetName,
        )
        persistCurrent()
    }

    fun setBassBoost(strength: Int) {
        val clamped = strength.coerceIn(0, 1000)
        runCatching {
            bassBoost?.let { bb ->
                if (bb.strengthSupported) bb.setStrength(clamped.toShort())
                bb.enabled = _uiState.value.isEnabled && clamped > 0
            }
        }
        _uiState.value = _uiState.value.copy(bassBoostStrength = clamped)
        persistCurrent()
    }

    fun setVirtualizer(strength: Int) {
        val clamped = strength.coerceIn(0, 1000)
        runCatching {
            virtualizer?.let { virt ->
                if (virt.strengthSupported) virt.setStrength(clamped.toShort())
                virt.enabled = _uiState.value.isEnabled && clamped > 0
            }
        }
        _uiState.value = _uiState.value.copy(virtualizerStrength = clamped)
        persistCurrent()
    }

    fun reset() {
        applyPreset("Flat")
        setBassBoost(0)
        setVirtualizer(0)
    }

    fun releaseEffects() {
        runCatching { equalizer?.release() }
        runCatching { bassBoost?.release() }
        runCatching { virtualizer?.release() }
        equalizer = null
        bassBoost = null
        virtualizer = null
        currentSessionId = 0
    }

    private fun persistCurrent() {
        val state = _uiState.value
        scope.launch {
            prefsRepo.setEqSettings(
                enabled = state.isEnabled,
                preset = state.currentPresetName,
                bandLevelsMb = state.bands.map { it.levelMillibels },
                bassBoost = state.bassBoostStrength,
                virtualizer = state.virtualizerStrength,
            )
        }
    }

    private fun computePresetLevels(
        preset: String,
        bandCount: Int,
        minMb: Int,
        maxMb: Int,
    ): List<Int> {
        // Normalized 5-band curves (-1.0..+1.0 scaled to 60% of hardware range for clean headroom)
        val normalized5 = when (preset) {
            "Normal" -> listOf(0.15f, 0.0f, 0.0f, 0.1f, 0.15f)
            "Flat" -> listOf(0f, 0f, 0f, 0f, 0f)
            "Rock" -> listOf(0.55f, 0.25f, -0.15f, 0.3f, 0.6f)
            "Pop" -> listOf(-0.15f, 0.3f, 0.5f, 0.3f, -0.15f)
            "Jazz" -> listOf(0.4f, 0.15f, -0.2f, 0.25f, 0.45f)
            "Classical" -> listOf(0.5f, 0.25f, -0.1f, 0.35f, 0.5f)
            "Dance" -> listOf(0.65f, 0.45f, 0.1f, 0.0f, 0.4f)
            "Electronic" -> listOf(0.6f, 0.35f, -0.1f, 0.35f, 0.65f)
            "Hip-Hop" -> listOf(0.7f, 0.45f, 0.0f, 0.15f, 0.3f)
            "Acoustic" -> listOf(0.35f, 0.15f, 0.2f, 0.35f, 0.25f)
            else -> listOf(0f, 0f, 0f, 0f, 0f)
        }
        val halfSpan = minOf(kotlin.math.abs(minMb), kotlin.math.abs(maxMb)) * 0.65f
        return List(bandCount) { idx ->
            val t = if (bandCount <= 1) 0f else idx.toFloat() / (bandCount - 1) * 4f
            val low = t.toInt().coerceIn(0, 4)
            val high = (low + 1).coerceIn(0, 4)
            val frac = t - low
            val interp = normalized5[low] + (normalized5[high] - normalized5[low]) * frac
            (interp * halfSpan).toInt().coerceIn(minMb, maxMb)
        }
    }
}
