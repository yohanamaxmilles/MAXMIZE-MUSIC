package com.example.data.dj.ml

/**
 * Product-owned EDM taxonomy. Labels describe musical subgenres, not predictions.
 * Predictions must come from a validated model or trusted user metadata.
 */
enum class EdmSubgenre(
    val displayName: String,
    val family: EdmFamily,
    val aliases: Set<String> = emptySet(),
) {
    PROGRESSIVE_HOUSE("Progressive House", EdmFamily.HOUSE, setOf("prog house")),
    FUTURE_HOUSE("Future House", EdmFamily.HOUSE),
    DEEP_HOUSE("Deep House", EdmFamily.HOUSE),
    TECH_HOUSE("Tech House", EdmFamily.HOUSE),
    AFRO_HOUSE("Afro House", EdmFamily.HOUSE),

    TECHNO("Techno", EdmFamily.TECHNO),
    MELODIC_TECHNO("Melodic Techno", EdmFamily.TECHNO),
    PEAK_TIME_TECHNO("Peak-Time Techno", EdmFamily.TECHNO, setOf("peak time")),
    HARD_TECHNO("Hard Techno", EdmFamily.TECHNO),
    MINIMAL_TECHNO("Minimal Techno", EdmFamily.TECHNO),

    MIDTEMPO_BASS("Midtempo Bass", EdmFamily.BASS, setOf("midtempo", "rezz-style midtempo")),
    MELODIC_DUBSTEP("Melodic Dubstep", EdmFamily.BASS),
    DUBSTEP("Dubstep", EdmFamily.BASS),
    FUTURE_BASS("Future Bass", EdmFamily.BASS),
    DRUM_AND_BASS("Drum & Bass", EdmFamily.BASS, setOf("dnb", "drum and bass")),
    BASS_HOUSE("Bass House", EdmFamily.BASS),

    TRANCE("Trance", EdmFamily.TRANCE),
    PSYTRANCE("Psytrance", EdmFamily.TRANCE),

    AMAPIANO("Amapiano", EdmFamily.AFRICAN_ELECTRONIC),
    THREE_STEP("3-Step", EdmFamily.AFRICAN_ELECTRONIC, setOf("three step", "afro 3-step")),
    AFRO_TECH("Afro Tech", EdmFamily.AFRICAN_ELECTRONIC),
}

enum class EdmFamily { HOUSE, TECHNO, BASS, TRANCE, AFRICAN_ELECTRONIC }

/** Normalized, model-independent features extracted from decoded audio windows. */
data class EdmAudioFeatureVector(
    val sampleRateHz: Int,
    val windowDurationMs: Int,
    val logMelBands: FloatArray,
    val spectralCentroidHz: Float,
    val spectralRolloffHz: Float,
    val spectralFlux: Float,
    val lowBandEnergyRatio: Float,
    val midBandEnergyRatio: Float,
    val highBandEnergyRatio: Float,
    val onsetDensityPerSecond: Float,
    val beatPeriodMs: Float?,
    val pulseStrength: Float,
    val harmonicPercussiveRatio: Float,
    val vocalActivityRatio: Float,
) {
    init {
        require(sampleRateHz >= 8_000)
        require(windowDurationMs in 1_000..30_000)
        require(logMelBands.isNotEmpty())
        require(logMelBands.all { it.isFinite() })
        require(listOf(
            spectralCentroidHz, spectralRolloffHz, spectralFlux,
            lowBandEnergyRatio, midBandEnergyRatio, highBandEnergyRatio,
            onsetDensityPerSecond, pulseStrength, harmonicPercussiveRatio,
            vocalActivityRatio,
        ).all { it.isFinite() && it >= 0f })
        require(beatPeriodMs == null || (beatPeriodMs.isFinite() && beatPeriodMs > 0f))
        require(vocalActivityRatio <= 1f && pulseStrength <= 1f)
    }
}

/** A model prediction includes calibrated confidence and explicit abstention. */
data class EdmSubgenrePrediction(
    val subgenre: EdmSubgenre?,
    val confidence: Float,
    val modelVersion: String,
    val evidence: Set<String> = emptySet(),
) {
    init {
        require(confidence in 0f..1f)
        require(modelVersion.isNotBlank())
        require(subgenre != null || confidence < 0.60f) {
            "An abstaining prediction must stay below the acceptance threshold"
        }
    }

    val isAccepted: Boolean
        get() = subgenre != null && confidence >= 0.60f
}

/**
 * Inference boundary. Android DSP extraction and the trained model implementation
 * plug into this contract without tying the app to a paid cloud API or a specific ML runtime.
 */
interface EdmSubgenreClassifier {
    val modelVersion: String
    suspend fun classify(windows: List<EdmAudioFeatureVector>): EdmSubgenrePrediction
}
