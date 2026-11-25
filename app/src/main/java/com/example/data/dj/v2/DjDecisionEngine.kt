package com.example.data.dj.v2

import java.util.Locale
import kotlin.math.abs

/** Deterministic, offline candidate ranking with explicit rejection reasons. */
class DjDecisionEngine {

    fun rankNextTracks(
        current: DjTrackProfile,
        candidates: List<DjTrackProfile>,
        recentTrackIds: Set<String>,
        config: DjDecisionConfig = DjDecisionConfig(),
        count: Int = 8,
        recentArtistNames: Set<String> = emptySet(),
    ): DjRankingResult {
        require(count >= 0)
        val ranked = mutableListOf<DjCandidateScore>()
        val rejectedById = linkedMapOf<String, DjRejection>()

        if (current.contentKind != AudioContentKind.MUSIC || current.contentConfidence < 0.50f) {
            return DjRankingResult(
                rankedCandidates = emptyList(),
                rejectedCandidates = candidates.map {
                    DjRejection(it.trackId, "Current deck is not verified music")
                },
            )
        }

        val remaining = candidates
            .filter { it.trackId != current.trackId && it.trackId !in recentTrackIds }
            .distinctBy { it.trackId }
            .toMutableList()
        val usedTrackIds = recentTrackIds.toMutableSet().apply { add(current.trackId) }
        val artistRotation = recentArtistNames.map { it.trim().lowercase() }
            .filter { it.isNotBlank() }.toMutableSet()
        var pivot = current

        repeat(count) {
            if (remaining.isEmpty()) return@repeat
            val validOptions = mutableListOf<DjCandidateScore>()
            remaining.forEach { candidate ->
                val reason = rejectionReason(pivot, candidate, usedTrackIds, config)
                if (reason != null) {
                    rejectedById.putIfAbsent(candidate.trackId, DjRejection(candidate.trackId, reason))
                } else {
                    rejectedById.remove(candidate.trackId)
                    validOptions += scoreCandidate(pivot, candidate, config, artistRotation)
                }
            }
            val selected = validOptions.maxWithOrNull(
                compareBy<DjCandidateScore> { it.totalScore }.thenByDescending { it.trackId }
            ) ?: return@repeat
            ranked += selected
            val chosenProfile = remaining.firstOrNull { it.trackId == selected.trackId } ?: return@repeat
            remaining.removeAll { it.trackId == chosenProfile.trackId }
            usedTrackIds += chosenProfile.trackId
            chosenProfile.artistName?.trim()?.lowercase()
                ?.takeIf { it.isNotBlank() && it != "unknown artist" }
                ?.let { artistRotation += it }
            pivot = chosenProfile
        }

        remaining.forEach { candidate ->
            if (candidate.trackId !in rejectedById) {
                rejectedById[candidate.trackId] = DjRejection(
                    candidate.trackId,
                    "Not selected in the current best-fit DJ sequence",
                )
            }
        }

        return DjRankingResult(
            rankedCandidates = ranked,
            rejectedCandidates = rejectedById.values.toList(),
        )
    }

    private fun rejectionReason(
        current: DjTrackProfile,
        candidate: DjTrackProfile,
        recentTrackIds: Set<String>,
        config: DjDecisionConfig,
    ): String? {
        if (candidate.trackId == current.trackId) return "Already playing"
        if (candidate.trackId in recentTrackIds) return "Recently played"
        if (candidate.durationMs < config.minTrackDurationMs) return "Track is too short for a DJ set"
        if (config.requirePhraseStructure &&
            (current.phraseBoundariesMs.size < 2 || candidate.phraseBoundariesMs.size < 2)
        ) {
            return "Reliable phrase grid is required for phrase-matched mixing"
        }

        when (candidate.contentKind) {
            AudioContentKind.PODCAST -> return "Podcast excluded from music Automix"
            AudioContentKind.SPOKEN_WORD -> return "Spoken-word audio excluded from music Automix"
            AudioContentKind.AUDIOBOOK -> return "Audiobook excluded from music Automix"
            AudioContentKind.SOUND_EFFECT -> return "Sound effect excluded from music Automix"
            AudioContentKind.UNKNOWN -> {
                if (candidate.durationMs > config.maxUnknownContentDurationMs) {
                    return "Long audio with unverified content type"
                }
                if (candidate.contentConfidence < 0.50f) return "Audio has not been verified as music"
            }
            AudioContentKind.MUSIC -> Unit
        }

        if (config.requireReliableTempo && (!current.hasReliableTempo || !candidate.hasReliableTempo)) {
            return "Reliable BPM analysis is required"
        }
        if (config.requireReliableGenre && (!current.hasReliableGenre || !candidate.hasReliableGenre)) {
            return "Reliable genre analysis is required"
        }
        if (config.requireReliableKey && (!current.hasReliableKey || !candidate.hasReliableKey)) {
            return "Reliable musical-key analysis is required"
        }

        val ratio = tempoRatio(current.bpm, candidate.bpm)
        if (config.requireReliableTempo && ratio == null) return "BPM data is unavailable"
        if (ratio != null && abs(ratio - 1f) > config.maxTempoAdjustment) {
            return "Tempo adjustment exceeds the configured limit"
        }

        if (config.strictGenreMatching && genreCompatibility(current, candidate, config) < 80) {
            return "Genre family is incompatible in Strict mode"
        }
        if (config.strictHarmonicMatching) {
            val harmonic = camelotScore(current.camelotCode, candidate.camelotCode)
                ?: return "Reliable Camelot compatibility is required"
            if (harmonic < 70) return "Harmonic key relationship is too distant"
        }
        return null
    }

    private fun scoreCandidate(
        current: DjTrackProfile,
        candidate: DjTrackProfile,
        config: DjDecisionConfig,
        recentArtistNames: Set<String>,
    ): DjCandidateScore {
        val ratio = tempoRatio(current.bpm, candidate.bpm)
        val bpmScore = ratio?.let { (100f - abs(it - 1f) * 1000f).toInt().coerceIn(0, 100) }
        val keyScore = camelotScore(current.camelotCode, candidate.camelotCode)
        val genreScore = genreCompatibility(current, candidate, config)
        val energyScore = energyCompatibility(
            current.energy,
            candidate.energy,
            config.targetEnergyDelta ?: config.sessionPhase.targetEnergyDelta,
        )
        val artist = candidate.artistName?.trim()?.lowercase().orEmpty()
        val artistPenalty = if (artist.isNotBlank() && artist != "unknown artist" &&
            recentArtistNames.any { it.trim().equals(artist, ignoreCase = true) }
        ) 12 else 0

        val dimensions = listOf(
            bpmScore to config.bpmWeight,
            keyScore to config.keyWeight,
            genreScore to config.genreWeight,
            energyScore to config.energyWeight,
        )
        val available = dimensions.filter { it.first != null && it.second > 0f }
        val totalWeight = available.sumOf { it.second.toDouble() }.toFloat().coerceAtLeast(0.0001f)
        val weightedScore = available.sumOf { (it.first!! * it.second).toDouble() }.toFloat() / totalWeight

        val reasons = buildList {
            if (bpmScore != null) add("Tempo match: $bpmScore/100")
            if (keyScore != null) add("Harmonic match: $keyScore/100")
            add("Genre match: $genreScore/100")
            if (energyScore != null) add("Energy flow: $energyScore/100")
            ratio?.let { add("Incoming tempo rate: " + String.format(Locale.US, "%.3f", it) + "x") }
            if (artistPenalty > 0) add("Recent artist rotation penalty: -$artistPenalty")
        }

        return DjCandidateScore(
            trackId = candidate.trackId,
            totalScore = (weightedScore.toInt() - artistPenalty).coerceIn(0, 100),
            artistPenalty = artistPenalty,
            bpmScore = bpmScore,
            keyScore = keyScore,
            genreScore = genreScore,
            energyScore = energyScore,
            tempoRatio = ratio,
            reasons = reasons,
        )
    }

    companion object {
        /** Direct, half-time and double-time alignment. Null means no valid tempo data. */
        fun tempoRatio(currentBpm: Float?, incomingBpm: Float?): Float? {
            if (currentBpm == null || incomingBpm == null) return null
            if (currentBpm !in 60f..200f || incomingBpm !in 60f..200f) return null
            return listOf(
                currentBpm / incomingBpm,
                currentBpm / (incomingBpm * 0.5f),
                currentBpm / (incomingBpm * 2f),
            ).minByOrNull { abs(it - 1f) }
        }

        fun camelotScore(a: String?, b: String?): Int? {
            val first = parseCamelot(a) ?: return null
            val second = parseCamelot(b) ?: return null
            val rawDistance = abs(first.first - second.first)
            val distance = minOf(rawDistance, 12 - rawDistance)
            return when {
                distance == 0 && first.second == second.second -> 100
                distance == 0 -> 94
                distance == 1 && first.second == second.second -> 90
                distance == 1 -> 76
                distance == 2 && first.second == second.second -> 64
                distance == 2 -> 52
                else -> (45 - distance * 5).coerceAtLeast(15)
            }
        }

        private fun parseCamelot(value: String?): Pair<Int, Char>? {
            val clean = value?.trim()?.uppercase().orEmpty()
            if (clean.length !in 2..3) return null
            val letter = clean.last()
            if (letter != 'A' && letter != 'B') return null
            val number = clean.dropLast(1).toIntOrNull() ?: return null
            return if (number in 1..12) number to letter else null
        }

        private fun genreCompatibility(
            a: DjTrackProfile,
            b: DjTrackProfile,
            config: DjDecisionConfig,
        ): Int {
            if (!a.hasReliableGenre || !b.hasReliableGenre) return 35
            if (a.subgenre.equals(b.subgenre, ignoreCase = true)) return 100
            if (!a.genreFamily.isNullOrBlank() &&
                a.genreFamily.equals(b.genreFamily, ignoreCase = true)
            ) return 88
            return if (config.allowOpenFormatBridging) 62 else 0
        }

        private fun energyCompatibility(current: Float?, incoming: Float?, targetDelta: Float): Int? {
            if (current == null || incoming == null) return null
            val error = abs((incoming - current) - targetDelta)
            return (100f - error * 150f).toInt().coerceIn(0, 100)
        }
    }
}
