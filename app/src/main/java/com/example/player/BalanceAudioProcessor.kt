package com.example.player

import androidx.annotation.OptIn
import androidx.media3.common.C
import androidx.media3.common.audio.AudioProcessor
import androidx.media3.common.audio.BaseAudioProcessor
import androidx.media3.common.util.UnstableApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.nio.ByteBuffer
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sqrt

/**
 * Publishes real-time 12-band PCM audio spectrum amplitudes [0f..1f] directly from ExoPlayer's
 * decoded audio stream without requiring dangerous RECORD_AUDIO permissions.
 */
object RealtimeSpectrumBus {
    private val _bands = MutableStateFlow(List(12) { 0.06f })
    val bands: StateFlow<List<Float>> = _bands.asStateFlow()

    @Volatile
    private var lastPublishMs: Long = 0L

    private val smoothed = FloatArray(12) { 0.06f }
    private val bandFreqs = doubleArrayOf(
        55.0, 95.0, 150.0, 240.0, 380.0, 620.0,
        1000.0, 1600.0, 2500.0, 4000.0, 6400.0, 10000.0,
    )

    fun hasSubscribers(): Boolean = _bands.subscriptionCount.value > 0

    fun publishFrame(monoWindow: FloatArray, count: Int, sampleRate: Int) {
        if (_bands.subscriptionCount.value == 0 || count < 64 || sampleRate <= 0) return
        val now = android.os.SystemClock.elapsedRealtime()
        if (now - lastPublishMs < 40L) return
        lastPublishMs = now

        val n = minOf(count, monoWindow.size)
        for (b in 0 until 12) {
            val freq = bandFreqs[b]
            if (freq >= sampleRate * 0.45) {
                smoothed[b] = (smoothed[b] * 0.72f).coerceAtLeast(0.05f)
                continue
            }
            val omega = (2.0 * PI * freq) / sampleRate
            val coeff = 2.0 * cos(omega)
            var q1 = 0.0
            var q2 = 0.0
            for (i in 0 until n) {
                val q0 = coeff * q1 - q2 + monoWindow[i]
                q2 = q1
                q1 = q0
            }
            val mag = sqrt((q1 * q1 + q2 * q2 - q1 * q2 * coeff).coerceAtLeast(0.0))
            // Normalize Goertzel magnitude relative to window length & gentle log-like compression
            val norm = ((mag / (n * 0.16)).toFloat()).coerceIn(0f, 1.35f)
            val target = sqrt(norm.coerceIn(0f, 1f)).coerceIn(0.05f, 1.0f)
            smoothed[b] = if (target > smoothed[b]) {
                smoothed[b] * 0.35f + target * 0.65f
            } else {
                smoothed[b] * 0.74f + target * 0.26f
            }.coerceIn(0.05f, 1.0f)
        }
        _bands.value = smoothed.toList()
    }

    fun reset() {
        for (i in 0 until 12) smoothed[i] = 0.06f
        _bands.value = List(12) { 0.06f }
    }
}

@OptIn(UnstableApi::class)
class BalanceAudioProcessor : BaseAudioProcessor() {

    @Volatile
    private var balance: Float = 0.0f // -1.0f = full left, 0.0f = center, +1.0f = full right

    @Volatile
    private var normalizationGain: Float = 1.0f

    @Volatile
    var emitSpectrum: Boolean = true

    private val spectrumWindow = FloatArray(256)
    private var windowFill = 0
    private var configuredSampleRate = 44100

    fun setBalance(value: Float) {
        balance = value.coerceIn(-1.0f, 1.0f)
    }

    fun getBalance(): Float = balance

    fun setNormalizationGain(gain: Float) {
        normalizationGain = gain.coerceIn(0.35f, 1.65f)
    }

    override fun onConfigure(inputAudioFormat: AudioProcessor.AudioFormat): AudioProcessor.AudioFormat {
        if (inputAudioFormat.channelCount != 2 || inputAudioFormat.encoding != C.ENCODING_PCM_16BIT) {
            return AudioProcessor.AudioFormat.NOT_SET
        }
        configuredSampleRate = inputAudioFormat.sampleRate.coerceAtLeast(8000)
        return inputAudioFormat
    }

    override fun isActive(): Boolean {
        return super.isActive()
    }

    override fun queueInput(inputBuffer: ByteBuffer) {
        val remaining = inputBuffer.remaining()
        if (remaining == 0) return

        val currentBalance = balance
        val normGain = normalizationGain
        val leftGain = (if (currentBalance > 0f) (1f - currentBalance).coerceIn(0f, 1f) else 1f) * normGain
        val rightGain = (if (currentBalance < 0f) (1f + currentBalance).coerceIn(0f, 1f) else 1f) * normGain
        val shouldEmit = emitSpectrum && RealtimeSpectrumBus.hasSubscribers()

        val outputBuffer = replaceOutputBuffer(remaining)
        while (inputBuffer.remaining() >= 4) {
            val leftSample = inputBuffer.short
            val rightSample = inputBuffer.short
            val adjustedLeft = (leftSample * leftGain).toInt()
                .coerceIn(Short.MIN_VALUE.toInt(), Short.MAX_VALUE.toInt()).toShort()
            val adjustedRight = (rightSample * rightGain).toInt()
                .coerceIn(Short.MIN_VALUE.toInt(), Short.MAX_VALUE.toInt()).toShort()
            outputBuffer.putShort(adjustedLeft)
            outputBuffer.putShort(adjustedRight)

            if (shouldEmit) {
                spectrumWindow[windowFill++] = ((adjustedLeft + adjustedRight) * 0.5f) / 32768f
                if (windowFill >= spectrumWindow.size) {
                    RealtimeSpectrumBus.publishFrame(spectrumWindow, windowFill, configuredSampleRate)
                    windowFill = 0
                }
            }
        }
        while (inputBuffer.hasRemaining()) {
            outputBuffer.put(inputBuffer.get())
        }
        outputBuffer.flip()
    }
}
