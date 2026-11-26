package com.example

import com.example.data.dj.ml.EdmSubgenre
import com.example.data.dj.ml.EdmSubgenrePrediction
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class EdmAudioIntelligenceContractTest {
    @Test
    fun taxonomy_separates_midtempo_techno_dnb_and_african_styles() {
        assertEquals("BASS", EdmSubgenre.MIDTEMPO_BASS.family.name)
        assertEquals("TECHNO", EdmSubgenre.PEAK_TIME_TECHNO.family.name)
        assertEquals("BASS", EdmSubgenre.DRUM_AND_BASS.family.name)
        assertEquals("AFRICAN_ELECTRONIC", EdmSubgenre.AMAPIANO.family.name)
        assertEquals("AFRICAN_ELECTRONIC", EdmSubgenre.THREE_STEP.family.name)
    }

    @Test
    fun low_confidence_prediction_is_not_accepted() {
        val prediction = EdmSubgenrePrediction(
            subgenre = EdmSubgenre.MIDTEMPO_BASS,
            confidence = 0.42f,
            modelVersion = "untrained-contract-test",
        )
        assertFalse(prediction.isAccepted)
    }

    @Test
    fun high_confidence_prediction_is_accepted() {
        val prediction = EdmSubgenrePrediction(
            subgenre = EdmSubgenre.AMAPIANO,
            confidence = 0.91f,
            modelVersion = "test-model-1",
        )
        assertTrue(prediction.isAccepted)
    }

    @Test
    fun abstention_stays_explicit() {
        val prediction = EdmSubgenrePrediction(
            subgenre = null,
            confidence = 0.18f,
            modelVersion = "test-model-1",
        )
        assertFalse(prediction.isAccepted)
    }
}
