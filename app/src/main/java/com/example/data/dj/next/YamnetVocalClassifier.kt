package com.example.data.dj.next

import android.content.Context
import com.google.mediapipe.tasks.audio.audioclassifier.AudioClassifier
import com.google.mediapipe.tasks.audio.audioclassifier.AudioClassifier.AudioClassifierOptions
import com.google.mediapipe.tasks.audio.core.RunningMode
import com.google.mediapipe.tasks.components.containers.AudioData
import com.google.mediapipe.tasks.components.containers.AudioData.AudioDataFormat
import com.google.mediapipe.tasks.core.BaseOptions
import kotlin.math.floor

/**
 * On-device YAMNet inference. Audio is resampled to 16 kHz mono before MediaPipe.
 * Results are acoustic-event candidates only; this is not lyric transcription.
 */
class YamnetVocalClassifier(context: Context) : AutoCloseable {
    private val appContext = context.applicationContext
    @Volatile private var classifier: AudioClassifier? = null
    private val lock = Any()

    fun analyze(
        samples: FloatArray,
        sampleRate: Int,
        absoluteOffsetMs: Long = 0L,
    ): List<DjVocalModelFrame> {
        if (samples.isEmpty() || sampleRate <= 0) return emptyList()
        val mono16k = resampleLinear(samples, sampleRate, TARGET_SAMPLE_RATE)
        if (mono16k.size < MIN_WINDOW_SAMPLES) return emptyList()

        return synchronized(lock) {
            val activeClassifier = classifier ?: createClassifier().also { classifier = it }
            val format = AudioDataFormat.builder()
                .setSampleRate(TARGET_SAMPLE_RATE.toFloat())
                .build()
            val audioData = AudioData.create(format, mono16k.size)
            audioData.load(mono16k)
            val result = activeClassifier.classify(audioData)
            result.classificationResults().mapNotNull { window ->
                val categories = window.classifications().firstOrNull()?.categories().orEmpty()
                if (categories.isEmpty()) return@mapNotNull null
                val scores = categories.associate { category ->
                    category.categoryName() to category.score()
                }
                DjVocalModelFrame(
                    startMs = absoluteOffsetMs + window.timestampMs().toLong(),
                    durationMs = WINDOW_DURATION_MS,
                    classScores = scores,
                )
            }
        }
    }

    private fun createClassifier(): AudioClassifier {
        val baseOptions = BaseOptions.builder()
            .setModelAssetPath(MODEL_ASSET)
            .build()
        val options = AudioClassifierOptions.builder()
            .setBaseOptions(baseOptions)
            .setRunningMode(RunningMode.AUDIO_CLIPS)
            .setMaxResults(-1)
            .build()
        return AudioClassifier.createFromOptions(appContext, options)
    }

    override fun close() {
        synchronized(lock) {
            classifier?.close()
            classifier = null
        }
    }

    private fun resampleLinear(input: FloatArray, inputRate: Int, outputRate: Int): FloatArray {
        if (inputRate == outputRate) return input
        val outputSize = floor(input.size.toDouble() * outputRate / inputRate)
            .toInt().coerceAtLeast(0)
        if (outputSize == 0) return FloatArray(0)
        val output = FloatArray(outputSize)
        val ratio = inputRate.toDouble() / outputRate
        for (i in output.indices) {
            val sourcePosition = i * ratio
            val left = sourcePosition.toInt().coerceIn(0, input.lastIndex)
            val right = (left + 1).coerceAtMost(input.lastIndex)
            val fraction = (sourcePosition - left).toFloat()
            output[i] = input[left] + (input[right] - input[left]) * fraction
        }
        return output
    }

    companion object {
        private const val MODEL_ASSET = "yamnet.tflite"
        private const val TARGET_SAMPLE_RATE = 16_000
        private const val MIN_WINDOW_SAMPLES = 15_600
        private const val WINDOW_DURATION_MS = 975L
    }
}
