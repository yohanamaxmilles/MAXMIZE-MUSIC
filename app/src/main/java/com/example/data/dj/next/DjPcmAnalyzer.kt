package com.example.data.dj.next

import kotlin.math.abs
import kotlin.math.ln
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sqrt

/**
 * Offline PCM analyser. Feed decoded mono Float PCM, not compressed MP3/AAC bytes.
 * This first detector measures tempo and energy. It deliberately does not invent
 * musical key, vocal, or genre labels without dedicated spectral/model analysis.
 */
class DjPcmAnalyzer(
    private val windowSize: Int = 1024,
    private val hopSize: Int = 512,
) {
    fun analyze(trackId: String, monoPcm: FloatArray, sampleRate: Int): DjAudioAnalysis {
        require(trackId.isNotBlank()) { "trackId must not be blank" }
        require(sampleRate >= 8_000) { "sampleRate must be at least 8000 Hz" }
        require(windowSize > 0 && hopSize > 0 && hopSize <= windowSize)

        val durationMs = (monoPcm.size * 1000L) / sampleRate
        if (monoPcm.size < windowSize * 8 || monoPcm.all { abs(it) < 1e-6f }) {
            return DjAudioAnalysis(
                trackId = trackId,
                durationMs = durationMs,
                bpm = null,
                beatIntervalMs = null,
                firstBeatOffsetMs = null,
                beatTimesMs = emptyList(),
                phraseMarkersMs = emptyList(),
                energy = 0f,
                rmsDb = -120f,
                bpmConfidence = 0f,
                bpmConfidenceBand = DjConfidence.LOW,
                sections = emptyList(),
                notes = listOf("Insufficient non-silent PCM for tempo analysis."),
            )
        }

        val rmsFrames = ArrayList<Float>()
        var offset = 0
        while (offset + windowSize <= monoPcm.size) {
            var sum = 0.0
            for (i in offset until offset + windowSize) {
                val sample = monoPcm[i].coerceIn(-1f, 1f).toDouble()
                sum += sample * sample
            }
            rmsFrames += sqrt(sum / windowSize).toFloat()
            offset += hopSize
        }

        val meanSquare = monoPcm.fold(0.0) { acc, sample ->
            val value = sample.coerceIn(-1f, 1f).toDouble()
            acc + value * value
        } / monoPcm.size
        val rms = sqrt(meanSquare).toFloat().coerceAtLeast(1e-6f)
        val rmsDb = (20.0 * ln(rms.toDouble()) / ln(10.0)).toFloat()

        val onset = FloatArray(rmsFrames.size)
        for (i in 1 until rmsFrames.size) {
            val rise = (rmsFrames[i] - rmsFrames[i - 1]).coerceAtLeast(0f)
            val localContrast = abs(rmsFrames[i] - rmsFrames[i - 1])
            onset[i] = rise * 0.7f + localContrast * 0.3f
        }

        val frameRate = sampleRate.toFloat() / hopSize
        val tempo = estimateTempo(onset, frameRate)
        val beatMs = tempo.first?.let { (60_000f / it).toLong() }
        val firstBeatMs = if (tempo.first != null && beatMs != null) {
            val searchFrames = min(onset.size, max(1, (frameRate * (60f / tempo.first)).toInt()))
            val strongest = (0 until searchFrames).maxByOrNull { onset[it] } ?: 0
            ((strongest * hopSize * 1000L) / sampleRate).coerceAtMost(beatMs)
        } else null

        val beatTimes = if (beatMs != null && firstBeatMs != null) {
            generateSequence(firstBeatMs) { previous -> previous + beatMs }
                .takeWhile { it < durationMs }
                .toList()
        } else emptyList()

        val phrases = beatTimes.filterIndexed { index, _ -> index > 0 && index % 16 == 0 }
        val energy = ((rmsDb + 48f) / 42f).coerceIn(0f, 1f)
        val sections = buildSections(rmsFrames, sampleRate, hopSize, windowSize, durationMs)

        val confidence = tempo.second
        val band = when {
            confidence >= 0.78f -> DjConfidence.HIGH
            confidence >= 0.62f -> DjConfidence.MEDIUM
            else -> DjConfidence.LOW
        }
        val notes = buildList {
            if (tempo.first == null) add("No stable tempo peak was found.")
            if (band == DjConfidence.LOW) add("Tempo is uncertain. Do not enable automatic beat sync from this result.")
            add("Key, vocals and genre are intentionally unclassified by this PCM pass.")
            add("Section labels are energy-based estimates, not semantic vocal/drop recognition.")
        }

        return DjAudioAnalysis(
            trackId = trackId,
            durationMs = durationMs,
            bpm = tempo.first,
            beatIntervalMs = beatMs,
            firstBeatOffsetMs = firstBeatMs,
            beatTimesMs = beatTimes,
            phraseMarkersMs = phrases,
            energy = energy,
            rmsDb = rmsDb,
            bpmConfidence = confidence,
            bpmConfidenceBand = band,
            sections = sections,
            notes = notes,
        )
    }

    private fun estimateTempo(onset: FloatArray, frameRate: Float): Pair<Float?, Float> {
        if (onset.size < 16) return null to 0f
        val mean = onset.average().toFloat()
        val centered = FloatArray(onset.size) { (onset[it] - mean).coerceAtLeast(0f) }
        val minLag = max(1, (frameRate * 60f / 200f).toInt())
        val maxLag = min(onset.size / 2, (frameRate * 60f / 60f).toInt())
        if (maxLag <= minLag) return null to 0f

        var bestLag = -1
        var best = 0.0
        var second = 0.0
        for (lag in minLag..maxLag) {
            var correlation = 0.0
            var normA = 0.0
            var normB = 0.0
            for (i in lag until centered.size) {
                val a = centered[i].toDouble()
                val b = centered[i - lag].toDouble()
                correlation += a * b
                normA += a * a
                normB += b * b
            }
            val normalized = if (normA > 0.0 && normB > 0.0) {
                correlation / sqrt(normA * normB)
            } else 0.0
            if (normalized > best) {
                second = best
                best = normalized
                bestLag = lag
            } else if (normalized > second) {
                second = normalized
            }
        }
        if (bestLag < 0 || best <= 0.08) return null to 0f
        val bpm = 60f * frameRate / bestLag
        val separation = if (best <= 1e-6) 0f else ((best - second) / best).toFloat()
        val confidence = (best.toFloat() * 0.72f + separation * 0.28f).coerceIn(0f, 1f)
        return bpm.takeIf { it in 60f..200f } to confidence
    }

    private fun buildSections(
        frames: List<Float>,
        sampleRate: Int,
        hopSize: Int,
        windowSize: Int,
        durationMs: Long,
    ): List<DjSection> {
        if (frames.isEmpty()) return emptyList()
        val framesPerSegment = max(1, (sampleRate * 8f / hopSize).toInt())
        val chunks = frames.chunked(framesPerSegment)
        val levels = chunks.map { chunk -> chunk.average().toFloat() }
        val average = levels.average().toFloat().coerceAtLeast(1e-6f)
        return chunks.mapIndexed { index, chunk ->
            val start = (index * framesPerSegment * hopSize * 1000L) / sampleRate
            val rawEnd = ((index * framesPerSegment + chunk.size) * hopSize * 1000L) / sampleRate
            val end = min(durationMs, max(start + 1, rawEnd))
            val level = levels[index]
            val previous = levels.getOrNull(index - 1) ?: level
            val next = levels.getOrNull(index + 1) ?: level
            val type = when {
                index == 0 -> DjSectionType.INTRO
                index == chunks.lastIndex -> DjSectionType.OUTRO
                level < average * 0.62f -> DjSectionType.BREAKDOWN
                level > average * 1.22f && level > previous * 1.12f -> DjSectionType.BUILD
                level > average * 1.12f -> DjSectionType.HIGH_ENERGY
                level < previous * 0.72f && level < next * 0.78f -> DjSectionType.LOW_ENERGY
                else -> DjSectionType.UNKNOWN
            }
            DjSection(start, end, type, (level / average).coerceIn(0f, 2f) / 2f)
        }
    }
}
