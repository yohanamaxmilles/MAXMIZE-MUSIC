package com.example.data.rhythm

import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.ln
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.sin

/**
 * Builds the model's [128, 345] normalized log-Mel input.
 *
 * IMPORTANT:
 * - Supply mono PCM already resampled to 22,050 Hz.
 * - This class pads/truncates to exactly 8 seconds.
 * - It uses a periodic Hann window, centered STFT with constant zero padding,
 *   Slaney Mel scale and Slaney area normalization.
 * - Android resampling is intentionally a separate, not-yet-approved component.
 *
 * Output layout is [melBand][timeFrame], matching the Colab feature array.
 */
class RhythmFeatureExtractor {

    fun extract(mono22050: FloatArray): Array<FloatArray> {
        val audio = fitLength(mono22050, RhythmAudioConfig.INPUT_SAMPLES)
        val melFilters = createMelFilterBank()
        val window = createPeriodicHannWindow()

        val frameCount = RhythmAudioConfig.EXPECTED_FRAMES
        val melCount = RhythmAudioConfig.MEL_BANDS
        val fftSize = RhythmAudioConfig.FFT_SIZE
        val hop = RhythmAudioConfig.HOP_LENGTH
        val halfSpectrum = fftSize / 2 + 1

        val melPower = Array(melCount) { DoubleArray(frameCount) }
        val real = DoubleArray(fftSize)
        val imaginary = DoubleArray(fftSize)
        val spectrum = DoubleArray(halfSpectrum)

        // librosa's centered STFT pads n_fft / 2 zeros on both sides.
        val centerPadding = fftSize / 2
        var globalMax = 0.0

        for (frame in 0 until frameCount) {
            val frameStart = frame * hop - centerPadding

            for (i in 0 until fftSize) {
                val sourceIndex = frameStart + i
                val sample = if (sourceIndex in audio.indices) audio[sourceIndex].toDouble() else 0.0
                real[i] = sample * window[i]
                imaginary[i] = 0.0
            }

            fftInPlace(real, imaginary)

            for (bin in 0 until halfSpectrum) {
                val re = real[bin]
                val im = imaginary[bin]
                val power = re * re + im * im
                spectrum[bin] = power
            }

            for (mel in 0 until melCount) {
                var sum = 0.0
                val weights = melFilters[mel]
                for (bin in 0 until halfSpectrum) {
                    sum += weights[bin] * spectrum[bin]
                }
                melPower[mel][frame] = sum
                if (sum > globalMax) globalMax = sum
            }
        }

        val reference = max(globalMax, RhythmAudioConfig.DB_AMIN)
        val output = Array(melCount) { FloatArray(frameCount) }

        for (mel in 0 until melCount) {
            for (frame in 0 until frameCount) {
                val power = max(melPower[mel][frame], RhythmAudioConfig.DB_AMIN)
                val db = (10.0 * log10(power / reference))
                    .coerceIn(RhythmAudioConfig.DB_FLOOR, RhythmAudioConfig.DB_CEILING)
                output[mel][frame] = ((db - RhythmAudioConfig.DB_FLOOR) /
                    (RhythmAudioConfig.DB_CEILING - RhythmAudioConfig.DB_FLOOR)).toFloat()
            }
        }

        return output
    }

    private fun fitLength(input: FloatArray, targetLength: Int): FloatArray {
        if (input.size == targetLength) return input
        return FloatArray(targetLength) { index ->
            if (index < input.size) input[index] else 0f
        }
    }

    private fun createPeriodicHannWindow(): DoubleArray {
        val size = RhythmAudioConfig.FFT_SIZE
        return DoubleArray(size) { index ->
            0.5 - 0.5 * cos((2.0 * PI * index) / size)
        }
    }

    /**
     * Slaney Mel filterbank, matching librosa defaults:
     * htk=false, norm="slaney", fmin=0, fmax=sampleRate/2.
     */
    private fun createMelFilterBank(): Array<DoubleArray> {
        val sr = RhythmAudioConfig.SAMPLE_RATE.toDouble()
        val nFft = RhythmAudioConfig.FFT_SIZE
        val nMels = RhythmAudioConfig.MEL_BANDS
        val fMin = 0.0
        val fMax = sr / 2.0
        val melMin = hzToSlaneyMel(fMin)
        val melMax = hzToSlaneyMel(fMax)

        val melPoints = DoubleArray(nMels + 2) { i ->
            slaneyMelToHz(melMin + (melMax - melMin) * i / (nMels + 1))
        }

        val filters = Array(nMels) { DoubleArray(nFft / 2 + 1) }
        for (mel in 0 until nMels) {
            val lower = melPoints[mel]
            val center = melPoints[mel + 1]
            val upper = melPoints[mel + 2]
            val lowerWidth = max(center - lower, 1.0e-12)
            val upperWidth = max(upper - center, 1.0e-12)
            val enorm = 2.0 / max(upper - lower, 1.0e-12)

            for (bin in 0..nFft / 2) {
                val frequency = bin * sr / nFft
                val lowerSlope = (frequency - lower) / lowerWidth
                val upperSlope = (upper - frequency) / upperWidth
                val triangular = max(0.0, min(lowerSlope, upperSlope))
                filters[mel][bin] = triangular * enorm
            }
        }
        return filters
    }

    private fun hzToSlaneyMel(frequency: Double): Double {
        val linearSpacing = 200.0 / 3.0
        val minLogHz = 1_000.0
        val minLogMel = minLogHz / linearSpacing
        val logStep = ln(6.4) / 27.0
        return if (frequency < minLogHz) {
            frequency / linearSpacing
        } else {
            minLogMel + ln(frequency / minLogHz) / logStep
        }
    }

    private fun slaneyMelToHz(mel: Double): Double {
        val linearSpacing = 200.0 / 3.0
        val minLogHz = 1_000.0
        val minLogMel = minLogHz / linearSpacing
        val logStep = ln(6.4) / 27.0
        return if (mel < minLogMel) {
            mel * linearSpacing
        } else {
            minLogHz * exp(logStep * (mel - minLogMel))
        }
    }

    /** In-place radix-2 Cooley-Tukey FFT. */
    private fun fftInPlace(real: DoubleArray, imaginary: DoubleArray) {
        val n = real.size
        var j = 0
        for (i in 1 until n) {
            var bit = n shr 1
            while ((j and bit) != 0) {
                j = j xor bit
                bit = bit shr 1
            }
            j = j xor bit
            if (i < j) {
                val realTemp = real[i]
                real[i] = real[j]
                real[j] = realTemp

                val imaginaryTemp = imaginary[i]
                imaginary[i] = imaginary[j]
                imaginary[j] = imaginaryTemp
            }
        }

        var length = 2
        while (length <= n) {
            val angle = -2.0 * PI / length
            val wLengthReal = cos(angle)
            val wLengthImaginary = sin(angle)
            var start = 0

            while (start < n) {
                var wReal = 1.0
                var wImaginary = 0.0
                val half = length / 2

                for (offset in 0 until half) {
                    val even = start + offset
                    val odd = even + half

                    val oddReal = real[odd] * wReal - imaginary[odd] * wImaginary
                    val oddImaginary = real[odd] * wImaginary + imaginary[odd] * wReal

                    real[odd] = real[even] - oddReal
                    imaginary[odd] = imaginary[even] - oddImaginary
                    real[even] += oddReal
                    imaginary[even] += oddImaginary

                    val nextWReal = wReal * wLengthReal - wImaginary * wLengthImaginary
                    wImaginary = wReal * wLengthImaginary + wImaginary * wLengthReal
                    wReal = nextWReal
                }
                start += length
            }
            length = length shl 1
        }
    }

    private fun log10(value: Double): Double = ln(value) / ln(10.0)
}
