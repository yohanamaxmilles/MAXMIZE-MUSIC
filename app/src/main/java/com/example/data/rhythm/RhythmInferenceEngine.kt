package com.example.data.rhythm

import android.content.Context
import org.tensorflow.lite.Interpreter
import org.tensorflow.lite.support.common.FileUtil
import java.io.Closeable
import java.nio.MappedByteBuffer

/**
 * Runs the preliminary MAXMIZE MUSIC 25-output drum-presence model.
 *
 * Input must come from RhythmFeatureExtractor and have shape [128][345].
 * The model predicts instrument presence over the complete 8-second segment.
 * It does not return individual hit timestamps.
 *
 * The caller must pass classLabels in the exact output order used during
 * training. The labels are deliberately not guessed or hard-coded here.
 */
class RhythmInferenceEngine(
    context: Context,
    classLabels: List<String>,
    modelAssetPath: String = DEFAULT_MODEL_ASSET,
    numThreads: Int = DEFAULT_NUM_THREADS,
    threshold: Float = RhythmAudioConfig.DEFAULT_PREDICTION_THRESHOLD
) : Closeable {

    private val labels: List<String> = classLabels.toList()
    private val interpreter: Interpreter
    private val predictionThreshold: Float = threshold

    init {
        require(labels.size == RhythmAudioConfig.MODEL_OUTPUTS) {
            "Expected exactly ${RhythmAudioConfig.MODEL_OUTPUTS} class labels, got ${labels.size}."
        }
        require(labels.all { it.isNotBlank() }) { "Class labels must not be blank." }
        require(labels.distinct().size == labels.size) { "Class labels must be unique." }
        require(threshold in 0f..1f) { "Threshold must be between 0 and 1." }
        require(numThreads >= 1) { "numThreads must be at least 1." }

        val modelBuffer: MappedByteBuffer =
            FileUtil.loadMappedFile(context.applicationContext, modelAssetPath)

        interpreter = Interpreter(
            modelBuffer,
            Interpreter.Options().setNumThreads(numThreads)
        )

        validateModelTensors()
    }

    /**
     * Predicts all 25 instrument-presence probabilities.
     *
     * @param features normalized log-Mel features shaped [128][345].
     * @return one result per model output, kept in the original training order.
     *
     * Call from a background dispatcher/thread, not the Android main thread.
     */
    @Synchronized
    fun predict(features: Array<FloatArray>): List<DrumPrediction> {
        validateFeatures(features)

        // TensorFlow Lite input tensor: [batch=1][mel=128][frames=345][channel=1].
        val input = Array(1) {
            Array(RhythmAudioConfig.MEL_BANDS) {
                Array(RhythmAudioConfig.EXPECTED_FRAMES) { FloatArray(1) }
            }
        }

        for (mel in 0 until RhythmAudioConfig.MEL_BANDS) {
            for (frame in 0 until RhythmAudioConfig.EXPECTED_FRAMES) {
                input[0][mel][frame][0] = features[mel][frame]
            }
        }

        // The trained model has 25 sigmoid probability outputs.
        val output = Array(1) { FloatArray(RhythmAudioConfig.MODEL_OUTPUTS) }
        interpreter.run(input, output)

        return labels.indices.map { index ->
            val probability = output[0][index]
            require(probability.isFinite()) {
                "Model returned a non-finite value for output $index."
            }

            val safeProbability = probability.coerceIn(0f, 1f)
            DrumPrediction(
                classIndex = index,
                instrument = labels[index],
                probability = safeProbability,
                detected = safeProbability >= predictionThreshold
            )
        }
    }

    /** Returns the current input and output tensor dimensions for diagnostics. */
    @Synchronized
    fun tensorShapes(): RhythmTensorShapes = RhythmTensorShapes(
        input = interpreter.getInputTensor(0).shape().toList(),
        output = interpreter.getOutputTensor(0).shape().toList()
    )

    private fun validateFeatures(features: Array<FloatArray>) {
        require(features.size == RhythmAudioConfig.MEL_BANDS) {
            "Expected ${RhythmAudioConfig.MEL_BANDS} Mel bands, got ${features.size}."
        }

        features.forEachIndexed { mel, row ->
            require(row.size == RhythmAudioConfig.EXPECTED_FRAMES) {
                "Mel band $mel must contain ${RhythmAudioConfig.EXPECTED_FRAMES} frames, got ${row.size}."
            }
            row.forEachIndexed { frame, value ->
                require(value.isFinite() && value in 0f..1f) {
                    "Invalid feature value at Mel band $mel, frame $frame: $value. Expected a finite value from 0 to 1."
                }
            }
        }
    }

    private fun validateModelTensors() {
        val inputShape = interpreter.getInputTensor(0).shape().toList()
        val outputShape = interpreter.getOutputTensor(0).shape().toList()

        val expectedInput = listOf(
            1,
            RhythmAudioConfig.MEL_BANDS,
            RhythmAudioConfig.EXPECTED_FRAMES,
            1
        )
        val expectedOutput = listOf(1, RhythmAudioConfig.MODEL_OUTPUTS)

        check(inputShape == expectedInput) {
            "Unexpected model input shape $inputShape. Expected $expectedInput."
        }
        check(outputShape == expectedOutput) {
            "Unexpected model output shape $outputShape. Expected $expectedOutput."
        }
    }

    @Synchronized
    override fun close() {
        interpreter.close()
    }

    companion object {
        const val DEFAULT_MODEL_ASSET = "models/rhythm_instrument_model_float32.tflite"
        const val DEFAULT_NUM_THREADS = 2
    }
}

data class DrumPrediction(
    val classIndex: Int,
    val instrument: String,
    /** Model confidence from 0.0 to 1.0. */
    val probability: Float,
    /** True when probability meets the configured threshold. */
    val detected: Boolean
)

data class RhythmTensorShapes(
    val input: List<Int>,
    val output: List<Int>
)
