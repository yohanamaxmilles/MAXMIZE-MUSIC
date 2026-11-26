package com.example.data.rhythm

import android.content.Context

/**
 * Creates the Rhythm Analyzer with the exact class-label order used by training.
 *
 * This factory keeps application code from accidentally passing a reordered
 * or incomplete label list to RhythmAnalyzer.
 */
object RhythmAnalyzerFactory {

    fun create(
        context: Context,
        modelAssetPath: String = RhythmInferenceEngine.DEFAULT_MODEL_ASSET,
        numThreads: Int = RhythmInferenceEngine.DEFAULT_NUM_THREADS,
        threshold: Float = RhythmAudioConfig.DEFAULT_PREDICTION_THRESHOLD
    ): RhythmAnalyzer {
        return RhythmAnalyzer(
            context = context.applicationContext,
            classLabels = RhythmInstrumentLabels.ALL,
            modelAssetPath = modelAssetPath,
            numThreads = numThreads,
            threshold = threshold
        )
    }
}
