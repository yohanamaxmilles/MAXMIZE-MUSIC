package com.example.player

import androidx.media3.common.C
import androidx.media3.common.audio.AudioProcessor
import androidx.media3.common.audio.BaseAudioProcessor
import androidx.media3.common.util.UnstableApi
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * Per-deck PCM processor for musical EQ during DJ transitions.
 * Supports stereo PCM16 and keeps independent filter history for left and right channels.
 */
@UnstableApi
class DjDeckAudioProcessor : BaseAudioProcessor() {

    private data class Biquad(
        val b0: Float = 1f,
        val b1: Float = 0f,
        val b2: Float = 0f,
        val a1: Float = 0f,
        val a2: Float = 0f,
    )

    private class ChannelState {
        var x1 = 0f
        var x2 = 0f
        var y1 = 0f
        var y2 = 0f

        fun process(input: Float, c: Biquad): Float {
            val output = c.b0 * input + c.b1 * x1 + c.b2 * x2 -
                c.a1 * y1 - c.a2 * y2
            x2 = x1
            x1 = input
            y2 = y1
            y1 = output
            return output
        }

        fun reset() {
            x1 = 0f
            x2 = 0f
            y1 = 0f
            y2 = 0f
        }
    }

    @Volatile private var sampleRate = 44_100
    @Volatile private var pcmEncoding = C.ENCODING_PCM_16BIT
    @Volatile private var lowGainDb = 0f
    @Volatile private var midGainDb = 0f
    @Volatile private var highGainDb = 0f
    @Volatile private var lowShelf = Biquad()
    @Volatile private var midPeak = Biquad()
    @Volatile private var highShelf = Biquad()

    private val states = Array(2) { Array(3) { ChannelState() } }

    override fun onConfigure(inputAudioFormat: AudioProcessor.AudioFormat): AudioProcessor.AudioFormat {
        if (inputAudioFormat.channelCount != 2 ||
            inputAudioFormat.encoding !in setOf(C.ENCODING_PCM_16BIT, C.ENCODING_PCM_FLOAT)
        ) return AudioProcessor.AudioFormat.NOT_SET

        sampleRate = inputAudioFormat.sampleRate.coerceAtLeast(8_000)
        pcmEncoding = inputAudioFormat.encoding
        rebuildCoefficients()
        return inputAudioFormat
    }

    fun setEq(lowDb: Float, midDb: Float, highDb: Float) {
        lowGainDb = lowDb.coerceIn(-18f, 6f)
        midGainDb = midDb.coerceIn(-18f, 6f)
        highGainDb = highDb.coerceIn(-18f, 6f)
        rebuildCoefficients()
    }

    fun resetEq() = setEq(0f, 0f, 0f)

    private fun rebuildCoefficients() {
        val rate = sampleRate.toFloat()
        lowShelf = shelf(rate, 120f, lowGainDb, true)
        midPeak = peak(rate, 1_000f, midGainDb, 0.9f)
        highShelf = shelf(rate, minOf(8_000f, rate * 0.40f), highGainDb, false)
    }

    override fun queueInput(inputBuffer: ByteBuffer) {
        if (!inputBuffer.hasRemaining()) return
        inputBuffer.order(ByteOrder.LITTLE_ENDIAN)
        val output = replaceOutputBuffer(inputBuffer.remaining()).order(ByteOrder.LITTLE_ENDIAN)
        val low = lowShelf
        val mid = midPeak
        val high = highShelf

        when (pcmEncoding) {
            C.ENCODING_PCM_16BIT -> {
                while (inputBuffer.remaining() >= 4) {
                    for (channel in 0..1) {
                        val input = inputBuffer.short / 32768f
                        val state = states[channel]
                        var value = state[0].process(input, low)
                        value = state[1].process(value, mid)
                        value = state[2].process(value, high)
                        val sample = (value.coerceIn(-1f, 1f) * 32767f).toInt()
                            .coerceIn(Short.MIN_VALUE.toInt(), Short.MAX_VALUE.toInt())
                        output.putShort(sample.toShort())
                    }
                }
            }
            C.ENCODING_PCM_FLOAT -> {
                while (inputBuffer.remaining() >= 8) {
                    for (channel in 0..1) {
                        val input = inputBuffer.float
                        val state = states[channel]
                        var value = state[0].process(input, low)
                        value = state[1].process(value, mid)
                        value = state[2].process(value, high)
                        output.putFloat(value.coerceIn(-1f, 1f))
                    }
                }
            }
        }
        while (inputBuffer.hasRemaining()) output.put(inputBuffer.get())
        output.flip()
    }

    override fun onFlush() {
        states.forEach { channel -> channel.forEach(ChannelState::reset) }
    }

    override fun onReset() {
        resetEq()
        states.forEach { channel -> channel.forEach(ChannelState::reset) }
    }

    private fun peak(rate: Float, frequency: Float, gainDb: Float, q: Float): Biquad {
        val a = 10.0.pow(gainDb / 40.0).toFloat()
        val w = (2.0 * PI * frequency / rate).toFloat()
        val c = cos(w)
        val alpha = sin(w) / (2f * q)
        val a0 = 1f + alpha / a
        return Biquad(
            b0 = (1f + alpha * a) / a0,
            b1 = (-2f * c) / a0,
            b2 = (1f - alpha * a) / a0,
            a1 = (-2f * c) / a0,
            a2 = (1f - alpha / a) / a0,
        )
    }

    private fun shelf(rate: Float, frequency: Float, gainDb: Float, low: Boolean): Biquad {
        val a = 10.0.pow(gainDb / 40.0).toFloat()
        val w = (2.0 * PI * frequency / rate).toFloat()
        val c = cos(w)
        val s = sin(w)
        val alpha = s / 2f * sqrt(2f)
        val beta = 2f * sqrt(a) * alpha

        val b0: Float
        val b1: Float
        val b2: Float
        val a0: Float
        val a1: Float
        val a2: Float

        if (low) {
            b0 = a * ((a + 1f) - (a - 1f) * c + beta)
            b1 = 2f * a * ((a - 1f) - (a + 1f) * c)
            b2 = a * ((a + 1f) - (a - 1f) * c - beta)
            a0 = (a + 1f) + (a - 1f) * c + beta
            a1 = -2f * ((a - 1f) + (a + 1f) * c)
            a2 = (a + 1f) + (a - 1f) * c - beta
        } else {
            b0 = a * ((a + 1f) + (a - 1f) * c + beta)
            b1 = -2f * a * ((a - 1f) + (a + 1f) * c)
            b2 = a * ((a + 1f) + (a - 1f) * c - beta)
            a0 = (a + 1f) - (a - 1f) * c + beta
            a1 = 2f * ((a - 1f) - (a + 1f) * c)
            a2 = (a + 1f) - (a - 1f) * c - beta
        }

        return Biquad(b0 / a0, b1 / a0, b2 / a0, a1 / a0, a2 / a0)
    }
}
