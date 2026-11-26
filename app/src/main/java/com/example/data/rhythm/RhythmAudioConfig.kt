package com.example.data.rhythm

/**
 * Preprocessing contract for the preliminary MAXMIZE MUSIC Rhythm Intelligence model.
 *
 * The input to [RhythmFeatureExtractor] must already be mono PCM at 22,050 Hz.
 * Resampling is deliberately kept outside this class until the Android resampler
 * is verified against the project's Colab reference package.
 */
object RhythmAudioConfig {
    const val SAMPLE_RATE = 22_050
    const val DURATION_SECONDS = 8
    const val INPUT_SAMPLES = SAMPLE_RATE * DURATION_SECONDS

    const val FFT_SIZE = 2_048
    const val HOP_LENGTH = 512
    const val MEL_BANDS = 128
    const val EXPECTED_FRAMES = 345

    const val POWER = 2.0
    const val DB_FLOOR = -80.0
    const val DB_CEILING = 0.0
    const val DB_AMIN = 1.0e-10

    const val MODEL_OUTPUTS = 25
    const val DEFAULT_PREDICTION_THRESHOLD = 0.5f

    const val DATASET_NAME = "WaivOps EDM-HSE"
    const val DATASET_DOI = "10.5281/zenodo.13769544"
    const val DATASET_LICENSE = "CC BY 4.0"
}
