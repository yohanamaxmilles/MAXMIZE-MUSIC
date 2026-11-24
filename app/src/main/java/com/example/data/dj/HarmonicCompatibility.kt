package com.example.data.dj

import com.example.data.local.db.GenreRuleOverrideEntity
import com.example.data.model.AutomixConfig
import com.example.data.model.DjQueuedTrack
import com.example.data.model.EnergyProgression
import com.example.data.model.HarmonicMixLevel
import com.example.data.model.MoodFilter
import com.example.data.model.Track
import com.example.data.model.TrackAnalysis
import kotlin.math.abs

data class SmartQueueResult(
    val queue: List<DjQueuedTrack>,
    val noSuitableTrackReason: String? = null,
)

data class TransitionEvaluation(
    val allowed: Boolean,
    val score: Int,
    val reason: String,
)

object HarmonicCompatibility {

    private val KEY_TO_CAMELOT = mapOf(
        "A min" to "8A", "C maj" to "8B",
        "E min" to "9A", "G maj" to "9B",
        "B min" to "10A", "D maj" to "10B",
        "F# min" to "11A", "A maj" to "11B",
        "C# min" to "12A", "E maj" to "12B",
        "G# min" to "1A", "B maj" to "1B",
        "D# min" to "2A", "F# maj" to "2B",
        "A# min" to "3A", "C# maj" to "3B",
        "F min" to "4A", "G# maj" to "4B",
        "C min" to "5A", "D# maj" to "5B",
        "G min" to "6A", "A# maj" to "6B",
        "D min" to "7A", "F maj" to "7B",
    )

    val ALL_KEYS: List<String> = KEY_TO_CAMELOT.keys.toList()

    fun camelotForKey(musicalKey: String): String =
        KEY_TO_CAMELOT[musicalKey] ?: "8A"

    fun parseCamelot(code: String): Pair<Int, Char>? {
        val clean = code.trim().uppercase()
        if (clean.length < 2) return null
        val letter = clean.last()
        if (letter != 'A' && letter != 'B') return null
        val num = clean.dropLast(1).toIntOrNull() ?: return null
        if (num !in 1..12) return null
        return num to letter
    }

    fun harmonicScore(camelotA: String, camelotB: String): Int {
        val a = parseCamelot(camelotA) ?: return 50
        val b = parseCamelot(camelotB) ?: return 50
        val rawDiff = abs(a.first - b.first)
        val ringDist = minOf(rawDiff, 12 - rawDiff)
        val sameMode = a.second == b.second

        return when {
            ringDist == 0 && sameMode -> 100
            ringDist == 0 && !sameMode -> 94 // Relative major/minor
            ringDist == 1 && sameMode -> 90  // Adjacent Camelot (+/-1)
            ringDist == 1 && !sameMode -> 76 // Diagonal energy boost
            ringDist == 2 && sameMode -> 64
            ringDist == 2 && !sameMode -> 52
            else -> (45 - ringDist * 5).coerceAtLeast(15)
        }
    }

    /**
     * Computes effective tempo ratio needed to bring incoming BPM into alignment with current BPM,
     * checking direct ratio as well as half-time (0.5x) and double-time (2.0x) equivalents.
     */
    fun computeBeatmatchRatio(currentBpm: Float, incomingBpm: Float): Float {
        if (currentBpm <= 1f || incomingBpm <= 1f) return 1.0f
        val direct = currentBpm / incomingBpm
        val half = currentBpm / (incomingBpm * 0.5f)
        val doubleT = currentBpm / (incomingBpm * 2.0f)
        val best = listOf(direct, half, doubleT).minByOrNull { abs(it - 1.0f) } ?: 1.0f
        return best.coerceIn(0.88f, 1.12f)
    }

    fun bpmCompatibilityScore(bpmA: Float, bpmB: Float): Int {
        if (bpmA <= 1f || bpmB <= 1f) return 55
        val ratio = computeBeatmatchRatio(bpmA, bpmB)
        val pctDiff = abs(ratio - 1.0f) * 100f
        return when {
            pctDiff <= 1.5f -> 100
            pctDiff <= 3.5f -> 92
            pctDiff <= 6.0f -> 80
            pctDiff <= 10.0f -> 64
            else -> (55f - pctDiff * 2.5f).toInt().coerceAtLeast(10)
        }
    }

    fun evaluateTransition(
        current: Track,
        currentAnalysis: TrackAnalysis?,
        candidate: Track,
        candidateAnalysis: TrackAnalysis?,
        config: AutomixConfig,
        customRules: List<GenreRuleOverrideEntity> = emptyList(),
        stepIndex: Int = 0,
        recentArtistHistory: List<String> = emptyList(),
    ): TransitionEvaluation {
        val genreResult = GenreEngine.evaluateCompatibility(
            current = current,
            currentAnalysis = currentAnalysis,
            candidate = candidate,
            candidateAnalysis = candidateAnalysis,
            config = config,
            customRules = customRules,
        )
        if (!genreResult.allowed) {
            return TransitionEvaluation(
                allowed = false,
                score = 0,
                reason = genreResult.reason,
            )
        }

        val bpmA = currentAnalysis?.bpm ?: 120f
        val bpmB = candidateAnalysis?.bpm ?: 120f
        val keyA = currentAnalysis?.camelotCode ?: "8A"
        val keyB = candidateAnalysis?.camelotCode ?: "8A"
        val energyA = currentAnalysis?.energy ?: 0.55f
        val energyB = candidateAnalysis?.energy ?: 0.55f

        val rawHarmonic = harmonicScore(keyA, keyB)
        if (config.keyMatchingEnabled &&
            config.harmonicMixLevel == HarmonicMixLevel.STRICT &&
            (currentAnalysis?.keyConfidence ?: 0f) >= 0.35f &&
            (candidateAnalysis?.keyConfidence ?: 0f) >= 0.35f &&
            rawHarmonic < 88
        ) {
            return TransitionEvaluation(
                allowed = false,
                score = 0,
                reason = "Key clash ($keyA -> $keyB) under Strict Harmonic Mixing",
            )
        }

        val bpmScore = if (config.bpmMatchEnabled) bpmCompatibilityScore(bpmA, bpmB) else 78
        val keyScore = if (config.keyMatchingEnabled) {
            when (config.harmonicMixLevel) {
                HarmonicMixLevel.STRICT -> rawHarmonic
                HarmonicMixLevel.BALANCED -> rawHarmonic
                HarmonicMixLevel.FLEXIBLE -> (rawHarmonic * 0.65f + 35f).toInt().coerceIn(45, 100)
            }
        } else 78

        val energyDiff = energyB - energyA
        val energyScore = when (config.energyProgression) {
            EnergyProgression.BUILD_UP -> when {
                energyDiff in 0.01f..0.16f -> 100
                energyDiff in -0.05f..0.24f -> 82
                energyDiff > 0.24f -> 64
                else -> (62f + energyDiff * 90f).toInt().coerceIn(25, 62)
            }
            EnergyProgression.PEAK_HOUR -> (energyB * 100f).toInt().coerceIn(30, 100)
            EnergyProgression.STEADY -> ((1f - abs(energyDiff) * 1.4f) * 100f).toInt().coerceIn(30, 100)
            EnergyProgression.CHILL_DOWN -> when {
                energyDiff in -0.18f..-0.01f -> 100
                energyDiff in -0.28f..0.05f -> 82
                else -> (68f - energyDiff * 90f).toInt().coerceIn(20, 68)
            }
            EnergyProgression.WAVE -> {
                val wantRise = (stepIndex % 5) in 0..2
                if (wantRise) {
                    if (energyDiff >= -0.03f) 92 else 58
                } else {
                    if (energyDiff <= 0.03f) 92 else 58
                }
            }
        }

        // Normalize configured scoring weights
        val wBpm = config.bpmWeight.coerceAtLeast(0.01f)
        val wKey = config.harmonicWeight.coerceAtLeast(0.01f)
        val wEnergy = config.energyWeight.coerceAtLeast(0.01f)
        val wGenre = config.genreWeight.coerceAtLeast(0.01f)
        val wSum = wBpm + wKey + wEnergy + wGenre

        var combined = (
            (bpmScore * (wBpm / wSum)) +
                (keyScore * (wKey / wSum)) +
                (energyScore * (wEnergy / wSum)) +
                (genreResult.score * (wGenre / wSum))
            ).toInt()

        // Slight phrase alignment & artist diversity adjustments
        if (config.phraseMatchEnabled && currentAnalysis != null && candidateAnalysis != null) {
            if (currentAnalysis.phraseLengthMs > 0L && candidateAnalysis.phraseLengthMs > 0L) {
                combined += 3
            }
        }
        val artistNorm = candidate.artist.trim().lowercase()
        if (artistNorm.isNotBlank() && artistNorm != "unknown artist") {
            val recentIdx = recentArtistHistory.indexOfFirst { it.equals(artistNorm, ignoreCase = true) }
            if (recentIdx == 0) combined -= 16
            else if (recentIdx in 1..2) combined -= 8
        }
        if (candidate.rating >= 4) combined += 3
        if (candidate.skipCount >= 3) combined -= 8

        val finalScore = combined.coerceIn(0, 100)
        val genreClass = GenreEngine.classifyTrack(candidate, candidateAnalysis)
        val reason = buildString {
            append(keyB)
            append(" · ")
            append("${bpmB.toInt()} BPM")
            if (genreClass.subgenre != "Unknown") {
                append(" · ")
                append(genreClass.subgenre)
            }
            if (keyScore >= 90) append(" · Harmonic")
            else if (bpmScore >= 92) append(" · Tempo Sync")
        }

        return TransitionEvaluation(allowed = true, score = finalScore, reason = reason)
    }

    fun compatibilityScore(
        current: Track,
        currentAnalysis: TrackAnalysis?,
        candidate: Track,
        candidateAnalysis: TrackAnalysis?,
        config: AutomixConfig,
        customRules: List<GenreRuleOverrideEntity> = emptyList(),
    ): Pair<Int, String> {
        val eval = evaluateTransition(
            current = current,
            currentAnalysis = currentAnalysis,
            candidate = candidate,
            candidateAnalysis = candidateAnalysis,
            config = config,
            customRules = customRules,
        )
        return eval.score to eval.reason
    }

    fun selectSmartQueueWithStatus(
        currentTrack: Track,
        allTracks: List<Track>,
        analyses: Map<String, TrackAnalysis>,
        recentTrackIds: Set<String>,
        config: AutomixConfig,
        count: Int = 12,
        customRules: List<GenreRuleOverrideEntity> = emptyList(),
    ): SmartQueueResult {
        val currentAnalysis = analyses[currentTrack.id]
        val currentClass = GenreEngine.classifyTrack(currentTrack, currentAnalysis)

        val candidates = allTracks.filter { candidate ->
            if (candidate.id == currentTrack.id) return@filter false
            if (config.avoidRepeats && candidate.id in recentTrackIds) return@filter false
            if (config.genrePreference != "All Genres") {
                val candClass = GenreEngine.classifyTrack(candidate, analyses[candidate.id])
                if (!candidate.genre.equals(config.genrePreference, ignoreCase = true) &&
                    !candClass.subgenre.equals(config.genrePreference, ignoreCase = true) &&
                    !candClass.primaryGenre.equals(config.genrePreference, ignoreCase = true)
                ) {
                    return@filter false
                }
            }
            val ca = analyses[candidate.id]
            if (ca != null && (ca.bpm < config.bpmMin || ca.bpm > config.bpmMax)) {
                return@filter false
            }
            if (config.autoSelectMood && ca != null) {
                when (config.mood) {
                    MoodFilter.CHILL -> if (ca.energy > 0.68f) return@filter false
                    MoodFilter.ENERGETIC -> if (ca.energy < 0.35f) return@filter false
                    MoodFilter.PARTY -> if (ca.energy < 0.48f) return@filter false
                }
            }
            true
        }

        // Strict genre & strict harmonic never silently inject incompatible tracks!
        val pool = if (candidates.isNotEmpty() || config.strictGenreMatching) {
            candidates
        } else {
            allTracks.filter { it.id != currentTrack.id && (!config.avoidRepeats || it.id !in recentTrackIds) }
                .ifEmpty { allTracks.filter { it.id != currentTrack.id } }
        }

        if (pool.isEmpty()) {
            return SmartQueueResult(
                queue = emptyList(),
                noSuitableTrackReason = "No tracks match ${config.bpmMin}–${config.bpmMax} BPM / mood filter for ${currentClass.subgenre}. Widen BPM range or disable Strict Genre Matching.",
            )
        }

        val ordered = mutableListOf<DjQueuedTrack>()
        val remaining = pool.toMutableList()
        var pivot = currentTrack
        val recentArtists = ArrayDeque<String>().apply {
            if (currentTrack.artist.isNotBlank()) addFirst(currentTrack.artist)
        }
        val depth = config.lookAheadCount.coerceIn(1, 5)
        var firstStepBlockedReason: String? = null

        repeat(minOf(count, remaining.size)) { stepIdx ->
            val pivotAnalysis = analyses[pivot.id]
            val validStep1 = remaining.mapNotNull { cand ->
                val ca = analyses[cand.id]
                val eval = evaluateTransition(
                    current = pivot,
                    currentAnalysis = pivotAnalysis,
                    candidate = cand,
                    candidateAnalysis = ca,
                    config = config,
                    customRules = customRules,
                    stepIndex = stepIdx,
                    recentArtistHistory = recentArtists.toList(),
                )
                if (!eval.allowed) {
                    if (firstStepBlockedReason == null) firstStepBlockedReason = eval.reason
                    null
                } else {
                    cand to eval
                }
            }

            if (validStep1.isEmpty()) {
                return@repeat
            }

            // Multi-track look-ahead evaluation across top 4 branching choices
            val bestPair = if (depth > 1 && remaining.size > 1) {
                validStep1
                    .sortedByDescending { it.second.score }
                    .take(4)
                    .maxByOrNull { (cand, eval) ->
                        val nextRem = remaining.filter { it.id != cand.id }
                        val futureScore = lookAheadChainScore(
                            pivot = cand,
                            remaining = nextRem,
                            analyses = analyses,
                            config = config,
                            customRules = customRules,
                            depthLeft = depth - 1,
                            stepIdx = stepIdx + 1,
                        )
                        eval.score + futureScore * 0.45f
                    }
            } else {
                validStep1.maxByOrNull { it.second.score }
            } ?: return@repeat

            val (chosen, chosenEval) = bestPair
            ordered.add(
                DjQueuedTrack(
                    track = chosen,
                    analysis = analyses[chosen.id],
                    compatibilityScore = chosenEval.score,
                    reason = chosenEval.reason,
                )
            )
            remaining.removeAll { it.id == chosen.id }
            pivot = chosen
            if (chosen.artist.isNotBlank()) {
                recentArtists.addFirst(chosen.artist)
                while (recentArtists.size > 4) recentArtists.removeLast()
            }
        }

        val noMatchMsg = if (ordered.isEmpty()) {
            firstStepBlockedReason
                ?: "No compatible tracks found for ${currentClass.subgenre}. Enable Open Format Bridging or adjust DJ Intelligence settings."
        } else null

        return SmartQueueResult(queue = ordered, noSuitableTrackReason = noMatchMsg)
    }

    private fun lookAheadChainScore(
        pivot: Track,
        remaining: List<Track>,
        analyses: Map<String, TrackAnalysis>,
        config: AutomixConfig,
        customRules: List<GenreRuleOverrideEntity>,
        depthLeft: Int,
        stepIdx: Int,
    ): Float {
        if (depthLeft <= 0 || remaining.isEmpty()) return 0f
        val pivotAnalysis = analyses[pivot.id]
        val bestNext = remaining
            .take(18)
            .mapNotNull { cand ->
                val eval = evaluateTransition(
                    current = pivot,
                    currentAnalysis = pivotAnalysis,
                    candidate = cand,
                    candidateAnalysis = analyses[cand.id],
                    config = config,
                    customRules = customRules,
                    stepIndex = stepIdx,
                )
                if (eval.allowed) cand to eval.score else null
            }
            .maxByOrNull { it.second } ?: return -25f

        val nextScore = bestNext.second.toFloat()
        if (depthLeft == 1) return nextScore
        val rest = remaining.filter { it.id != bestNext.first.id }
        return nextScore + 0.55f * lookAheadChainScore(
            pivot = bestNext.first,
            remaining = rest,
            analyses = analyses,
            config = config,
            customRules = customRules,
            depthLeft = depthLeft - 1,
            stepIdx = stepIdx + 1,
        )
    }

    fun selectSmartQueue(
        currentTrack: Track,
        allTracks: List<Track>,
        analyses: Map<String, TrackAnalysis>,
        recentTrackIds: Set<String>,
        config: AutomixConfig,
        count: Int = 12,
        customRules: List<GenreRuleOverrideEntity> = emptyList(),
    ): List<DjQueuedTrack> = selectSmartQueueWithStatus(
        currentTrack = currentTrack,
        allTracks = allTracks,
        analyses = analyses,
        recentTrackIds = recentTrackIds,
        config = config,
        count = count,
        customRules = customRules,
    ).queue
}
