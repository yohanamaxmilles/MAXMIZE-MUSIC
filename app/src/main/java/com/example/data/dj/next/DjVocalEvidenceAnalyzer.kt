package com.example.data.dj.next

import kotlin.math.max

enum class DjVocalKind { SINGING_CANDIDATE, SPEECH_CANDIDATE, UNKNOWN }
enum class DjVocalConfidence { LOW, MEDIUM, HIGH }

data class DjVocalModelFrame(
    val startMs: Long,
    val durationMs: Long,
    val classScores: Map<String, Float>,
)

data class DjVocalSegment(
    val startMs: Long,
    val endMs: Long,
    val kind: DjVocalKind,
    val peakProbability: Float,
)

data class DjVocalAnalysis(
    val averageVocalProbability: Float?,
    val peakVocalProbability: Float?,
    val vocalActiveRatio: Float?,
    val averageSpeechProbability: Float?,
    val confidence: DjVocalConfidence,
    val segments: List<DjVocalSegment>,
    val notes: List<String>,
)

/**
 * Interprets frame scores from an approved audio classifier such as YAMNet.
 * This class does not run a model or infer vocals from energy/frequency alone.
 * Every positive result is explicitly a candidate, not verified lyric detection.
 */
object DjVocalEvidenceAnalyzer {
    private val singingLabels = setOf(
        "singing", "choir", "yodeling", "chant", "mantra", "child singing",
        "synthetic singing", "rapping", "humming", "vocal music", "a capella",
    )
    private val speechLabels = setOf(
        "speech", "conversation", "narration", "child speech", "male speech",
        "female speech", "whispering",
    )

    fun analyze(
        frames: List<DjVocalModelFrame>,
        threshold: Float = 0.55f,
    ): DjVocalAnalysis {
        if (frames.isEmpty()) {
            return DjVocalAnalysis(
                averageVocalProbability = null,
                peakVocalProbability = null,
                vocalActiveRatio = null,
                averageSpeechProbability = null,
                confidence = DjVocalConfidence.LOW,
                segments = emptyList(),
                notes = listOf("No classifier frames were supplied. Vocal status is unknown."),
            )
        }

        val safeThreshold = threshold.coerceIn(0.05f, 0.99f)
        val scored = frames.sortedBy { it.startMs }.map { frame ->
            val scores = frame.classScores
            val singing = scores.entries
                .filter { normalize(it.key) in singingLabels }
                .maxOfOrNull { it.value.takeIf { score -> score.isFinite() }?.coerceIn(0f, 1f) ?: 0f } ?: 0f
            val speech = scores.entries
                .filter { normalize(it.key) in speechLabels }
                .maxOfOrNull { it.value.takeIf(Float::isFinite)?.coerceIn(0f, 1f) ?: 0f } ?: 0f
            val kind = when {
                singing >= safeThreshold -> DjVocalKind.SINGING_CANDIDATE
                speech >= safeThreshold -> DjVocalKind.SPEECH_CANDIDATE
                else -> DjVocalKind.UNKNOWN
            }
            ScoredFrame(frame, singing, speech, kind)
        }

        val average = scored.map { it.singing }.average().toFloat()
        val peak = scored.maxOf { it.singing }
        val activeRatio = scored.count { it.singing >= safeThreshold }.toFloat() / scored.size
        val speechAverage = scored.map { it.speech }.average().toFloat()
        val confidence = when {
            peak >= 0.85f && activeRatio >= 0.35f -> DjVocalConfidence.HIGH
            peak >= safeThreshold && activeRatio >= 0.15f -> DjVocalConfidence.MEDIUM
            else -> DjVocalConfidence.LOW
        }

        val segments = mutableListOf<DjVocalSegment>()
        var activeKind: DjVocalKind? = null
        var segmentStart = 0L
        var segmentEnd = 0L
        var segmentPeak = 0f
        for (item in scored) {
            if (item.kind == DjVocalKind.UNKNOWN) {
                if (activeKind != null) {
                    segments += DjVocalSegment(segmentStart, segmentEnd, activeKind, segmentPeak)
                    activeKind = null
                }
                continue
            }
            val end = item.frame.startMs + item.frame.durationMs.coerceAtLeast(0L)
            if (activeKind == item.kind && item.frame.startMs <= segmentEnd + 250L) {
                segmentEnd = max(segmentEnd, end)
                segmentPeak = max(segmentPeak, if (item.kind == DjVocalKind.SINGING_CANDIDATE) item.singing else item.speech)
            } else {
                if (activeKind != null) {
                    segments += DjVocalSegment(segmentStart, segmentEnd, activeKind, segmentPeak)
                }
                activeKind = item.kind
                segmentStart = item.frame.startMs
                segmentEnd = end
                segmentPeak = if (item.kind == DjVocalKind.SINGING_CANDIDATE) item.singing else item.speech
            }
        }
        if (activeKind != null) {
            segments += DjVocalSegment(segmentStart, segmentEnd, activeKind, segmentPeak)
        }

        return DjVocalAnalysis(
            averageVocalProbability = average,
            peakVocalProbability = peak,
            vocalActiveRatio = activeRatio,
            averageSpeechProbability = speechAverage,
            confidence = confidence,
            segments = segments,
            notes = listOf(
                "Classifier labels are broad sound-event evidence, not verified lyrics or exact vocal boundaries.",
                "No instrumental label is inferred when vocal scores are low.",
            ),
        )
    }

    private fun normalize(label: String): String =
        label.trim().lowercase().replace('_', ' ').replace('-', ' ')
            .replace(Regex("\\s+"), " ")

    private data class ScoredFrame(
        val frame: DjVocalModelFrame,
        val singing: Float,
        val speech: Float,
        val kind: DjVocalKind,
    )
}
