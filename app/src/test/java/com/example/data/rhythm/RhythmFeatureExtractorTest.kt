package com.example.data.rhythm

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class RhythmFeatureExtractorTest {

    private val extractor = RhythmFeatureExtractor()

    @Test
    fun silenceProducesExpectedTensorShapeAndFiniteValues() {
        val features = extractor.extract(FloatArray(RhythmAudioConfig.INPUT_SAMPLES))

        assertEquals(RhythmAudioConfig.MEL_BANDS, features.size)
        features.forEach { row ->
            assertEquals(RhythmAudioConfig.EXPECTED_FRAMES, row.size)
            row.forEach { value ->
                assertTrue(value.isFinite())
                assertTrue(value in 0f..1f)
            }
        }
    }

    @Test
    fun silenceMatchesLibrosaPowerToDbReferenceBehavior() {
        val features = extractor.extract(FloatArray(RhythmAudioConfig.INPUT_SAMPLES))

        // With ref=max and amin=1e-10, an all-zero power spectrogram maps to 0 dB.
        // The project's (dB + 80) / 80 normalization therefore produces 1.0.
        features.forEach { row ->
            row.forEach { value -> assertEquals(1f, value, 1e-6f) }
        }
    }

    @Test
    fun shortInputIsPaddedAndLongInputIsTruncated() {
        val short = extractor.extract(floatArrayOf(0.25f, -0.25f))
        val exact = extractor.extract(
            FloatArray(RhythmAudioConfig.INPUT_SAMPLES).also {
                it[0] = 0.25f
                it[1] = -0.25f
            }
        )
        val long = extractor.extract(
            FloatArray(RhythmAudioConfig.INPUT_SAMPLES + 100) { index ->
                if (index < RhythmAudioConfig.INPUT_SAMPLES) {
                    if (index == 0) 0.25f else if (index == 1) -0.25f else 0f
                } else {
                    0.9f
                }
            }
        )

        for (mel in 0 until RhythmAudioConfig.MEL_BANDS) {
            for (frame in 0 until RhythmAudioConfig.EXPECTED_FRAMES) {
                assertEquals(exact[mel][frame], short[mel][frame], 1e-6f)
                assertEquals(exact[mel][frame], long[mel][frame], 1e-6f)
            }
        }
    }
}
