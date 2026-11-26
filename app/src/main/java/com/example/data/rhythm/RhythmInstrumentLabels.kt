package com.example.data.rhythm

/**
 * Output labels for the preliminary MAXMIZE MUSIC rhythm instrument model.
 *
 * Keep this order unchanged. Each index maps to the same index in the
 * model's 25-value output tensor.
 */
object RhythmInstrumentLabels {
    val ALL: List<String> = listOf(
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
    )
}
