package com.example.data.rhythm

import android.content.Context
import java.io.Closeable

/**
 * End-to-end entry point for the preliminary Rhythm Intelligence model.
 *
 * Input is mono Float PCM already resampled to 22,050 Hz.
 * The analyzer extracts the 128 x 345 log-Mel tensor and runs the TFLite model.
 *
 * Audio decoding and high-quality resampling are intentionally outside this class.
 * Do not pass stereo interleaved samples or audio at another sample rate.
 */
class RhythmAnalyzer(
    context: Context,
    classLabels: List<String>,
    modelAssetPath: String = RhythmInferenceEngine.DEFAULT_MODEL_ASSET,
    numThreads: Int = RhythmInferenceEngine.DEFAULT_NUM_THREADS,
    private val threshold: Float = RhythmAudioConfig.DEFAULT_PREDICTION_THRESHOLD
) : Closeable {

    private val featureExtractor = RhythmFeatureExtractor()
    private val inferenceEngine = RhythmInferenceEngine(
        context = context,
        classLabels = classLabels,
        modelAssetPath = modelAssetPath,
        numThreads = numThreads,
        threshold = threshold
    )

    /**
     * Analyze one 8-second segment.
     *
     * Short clips are zero-padded and long clips are truncated to 8 seconds,
     * matching the current training preprocessing contract.
     *
     * Run this method on Dispatchers.Default or another background thread.
     */
    fun analyze(mono22050: FloatArray): RhythmAnalysisResult {
        require(mono22050.isNotEmpty()) { "Audio input must not be empty." }
        require(mono22050.all { it.isFinite() }) { "Audio contains NaN or infinite samples." }

        val features = featureExtractor.extract(mono22050)
        val predictions = inferenceEngine.predict(features)

        return RhythmAnalysisResult(
            predictions = predictions,
            detectedInstruments = predictions.filter { it.detected },
            inputSampleRateHz = RhythmAudioConfig.SAMPLE_RATE,
            analyzedDurationSeconds = RhythmAudioConfig.DURATION_SECONDS,
            threshold = threshold
        )
    }

    override fun close() {
        inferenceEngine.close()
    }
}

data class RhythmAnalysisResult(
    /** All 25 outputs in the exact class-label order used during training. */
    val predictions: List<DrumPrediction>,
    /** Only outputs whose probability meets the configured threshold. */
    val detectedInstruments: List<DrumPrediction>,
    val inputSampleRateHz: Int,
    val analyzedDurationSeconds: Int,
    val threshold: Float
)
