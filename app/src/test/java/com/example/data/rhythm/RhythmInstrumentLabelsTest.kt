package com.example.data.rhythm

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class RhythmInstrumentLabelsTest {

    @Test
    fun containsExactlyTwentyFiveUniqueLabels() {
        assertEquals(RhythmAudioConfig.MODEL_OUTPUTS, RhythmInstrumentLabels.ALL.size)
        assertEquals(RhythmInstrumentLabels.ALL.size, RhythmInstrumentLabels.ALL.distinct().size)
        assertTrue(RhythmInstrumentLabels.ALL.all { it.isNotBlank() })
    }

    @Test
    fun preservesTheTrainingOutputOrder() {
        assertEquals(
            listOf(
                "One_Shot_SFX",
                "Stick",
                "Kick",
                "Rimshot",
                "Snare_Big",
                "Clap",
                "Snare_Small",
                "Simple_Low_Tom",
                "Closed_Hat",
                "Simple_High_Tom",
                "Accent_Hat",
                "Low_Tom",
                "Open_Hat",
                "Mid_Tom",
                "High_Tom",
                "Tambourine",
                "Reversed_Rise",
                "Mute_Drumsynth_Conga",
                "High_Drumsynth_Conga",
                "Low_Drumsynth_Conga",
                "Cabasa",
                "Maracas",
                "Claves",
                "High_Wood_Block",
                "Low_Wood_Block"
            ),
            RhythmInstrumentLabels.ALL
        )
    }
}
