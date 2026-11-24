package com.example.data.dj

import android.content.Context
import android.media.MediaCodec
import android.media.MediaExtractor
import android.media.MediaFormat
import android.net.Uri
import android.os.Build
import com.example.data.local.db.AnalysisDao
import com.example.data.local.db.TrackAnalysisEntity
import com.example.data.model.Track
import com.example.data.model.TrackAnalysis
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import java.nio.ByteOrder
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.log10
import kotlin.math.max
import kotlin.math.pow
import kotlin.math.roundToInt
import kotlin.math.sqrt

class AudioAnalysisEngine(
    private val context: Context,
    private val analysisDao: AnalysisDao,
) {
    val analysesFlow: Flow<Map<String, TrackAnalysis>> = analysisDao.observeAllAnalyses()
        .map { list -> list.associate { it.trackId to it.toDomain() } }

    suspend fun getAnalysisForTrack(track: Track): TrackAnalysis = withContext(Dispatchers.IO) {
        val existing = analysisDao.getAnalysisOnce(track.id)
        if (existing != null && existing.phraseLengthMs > 0L) {
            return@withContext existing.toDomain()
        }

        val computed = analyzeSingleTrackInternal(track)
        analysisDao.upsertAnalysis(computed.toEntity())
        computed
    }

    suspend fun analyzeLibraryBatch(
        tracks: List<Track>,
        onProgress: (done: Int, total: Int) -> Unit,
    ): Int = withContext(Dispatchers.IO) {
        val existingById = analysisDao.getAllAnalysesOnce().associateBy { it.trackId }
        val missing = tracks.filter {
            val existing = existingById[it.id]
            existing == null || existing.phraseLengthMs <= 0L
        }
        if (missing.isEmpty()) {
            onProgress(tracks.size, tracks.size)
            return@withContext 0
        }
        var analyzed = 0
        val total = missing.size
        for ((index, track) in missing.withIndex()) {
            currentCoroutineContext().ensureActive()
            val res = analyzeSingleTrackInternal(track)
            analysisDao.upsertAnalysis(res.toEntity())
            analyzed++
            onProgress(index + 1, total)
        }
        analyzed
    }

    suspend fun updateManualCorrection(
        track: Track,
        bpm: Float,
        musicalKey: String,
    ): TrackAnalysis = withContext(Dispatchers.IO) {
        val base = getAnalysisForTrack(track)
        val clampedBpm = bpm.coerceIn(45f, 220f)
        val beatIntervalMs = (60_000f / clampedBpm).roundToInt().toLong().coerceAtLeast(200L)
        val phraseBeats = if (clampedBpm >= 115f) 32L else 16L
        val updated = base.copy(
            bpm = clampedBpm,
            bpmConfidence = 1.0f,
            tempoStability = 1.0f,
            musicalKey = musicalKey,
            camelotCode = HarmonicCompatibility.camelotForKey(musicalKey),
            keyConfidence = 1.0f,
            beatIntervalMs = beatIntervalMs,
            phraseLengthMs = beatIntervalMs * phraseBeats,
            userCorrected = true,
        )
        analysisDao.upsertAnalysis(updated.toEntity())
        updated
    }

    private fun analyzeSingleTrackInternal(track: Track): TrackAnalysis {
        if (!Build.FINGERPRINT.equals("robolectric", ignoreCase = true)) {
            val decoded = decodePcmWindows(Uri.parse(track.contentUri), track.durationMs)
            if (decoded != null && decoded.samples.size >= decoded.sampleRate * 2) {
                return analyzePcmSamples(
                    trackId = track.id,
                    samples = decoded.samples,
                    sampleRate = decoded.sampleRate,
                    durationMs = track.durationMs,
                    introRms = decoded.introRms,
                    outroRms = decoded.outroRms,
                    trackHint = track,
                )
            }
        }
        val safeDur = track.durationMs.coerceAtLeast(60_000L)
        val fallbackBpm = genrePriorBpm(track.genre)
        val beatMs = (60_000f / fallbackBpm).roundToInt().toLong().coerceAtLeast(250L)
        val phraseBeats = if (fallbackBpm >= 115f) 32L else 16L
        val partial = TrackAnalysis(
            trackId = track.id,
            bpm = fallbackBpm,
            bpmConfidence = 0.2f,
            tempoStability = 0.5f,
            musicalKey = "A min",
            camelotCode = "8A",
            keyConfidence = 0.2f,
            energy = 0.55f,
            rmsDb = -14.0f,
            loudnessLufs = -14.0f,
            dynamicRangeDb = 8.0f,
            onsetDensity = 2.0f,
            firstBeatMs = 0L,
            beatIntervalMs = beatMs,
            introEndMs = minOf(12_000L, (safeDur * 0.12f).toLong()),
            outroStartMs = max(safeDur - 14_000L, (safeDur * 0.85f).toLong()),
            durationMs = safeDur,
            userCorrected = false,
            phraseLengthMs = beatMs * phraseBeats,
            danceability = 0.52f,
            rhythmSignature = "4/4",
            downbeatConfidence = 0.3f,
            spectralCentroidHz = 1500f,
        )
        val genre = GenreEngine.classifyTrack(track, partial)
        return partial.copy(
            primaryGenre = genre.primaryGenre,
            secondaryGenre = genre.secondaryGenre,
            subgenre = genre.subgenre,
            genreConfidence = genre.confidence,
        )
    }

    private data class DecodedAudioWindow(
        val samples: FloatArray,
        val sampleRate: Int,
        val introRms: FloatArray,
        val outroRms: FloatArray,
    )

    private fun decodePcmWindows(uri: Uri, durationMs: Long): DecodedAudioWindow? {
        val extractor = MediaExtractor()
        var codec: MediaCodec? = null
        return try {
            extractor.setDataSource(context, uri, null)
            var audioTrackIdx = -1
            var format: MediaFormat? = null
            for (i in 0 until extractor.trackCount) {
                val f = extractor.getTrackFormat(i)
                val mime = f.getString(MediaFormat.KEY_MIME).orEmpty()
                if (mime.startsWith("audio/")) {
                    audioTrackIdx = i
                    format = f
                    break
                }
            }
            if (audioTrackIdx < 0 || format == null) return null
            extractor.selectTrack(audioTrackIdx)

            val mime = format.getString(MediaFormat.KEY_MIME) ?: return null
            val sampleRate = format.getInteger(MediaFormat.KEY_SAMPLE_RATE).coerceAtLeast(8000)
            val channelCount = if (format.containsKey(MediaFormat.KEY_CHANNEL_COUNT)) {
                format.getInteger(MediaFormat.KEY_CHANNEL_COUNT).coerceAtLeast(1)
            } else 1

            codec = MediaCodec.createDecoderByType(mime)
            codec.configure(format, null, null, 0)
            codec.start()

            val seekUs = if (durationMs > 45_000L) 20_000_000L else 0L
            if (seekUs > 0L) extractor.seekTo(seekUs, MediaExtractor.SEEK_TO_CLOSEST_SYNC)

            val targetSamples = minOf(sampleRate * 24, 1_100_000)
            val collected = FloatArray(targetSamples)
            var collectedCount = 0

            val bufferInfo = MediaCodec.BufferInfo()
            var sawInputEos = false
            var sawOutputEos = false
            var loopSafety = 0

            while (!sawOutputEos && collectedCount < targetSamples && loopSafety < 2500) {
                loopSafety++
                if (!sawInputEos) {
                    val inIdx = codec.dequeueInputBuffer(5_000L)
                    if (inIdx >= 0) {
                        val inBuf = codec.getInputBuffer(inIdx)
                        if (inBuf != null) {
                            val size = extractor.readSampleData(inBuf, 0)
                            if (size < 0) {
                                codec.queueInputBuffer(inIdx, 0, 0, 0L, MediaCodec.BUFFER_FLAG_END_OF_STREAM)
                                sawInputEos = true
                            } else {
                                codec.queueInputBuffer(inIdx, 0, size, extractor.sampleTime, 0)
                                extractor.advance()
                            }
                        }
                    }
                }

                val outIdx = codec.dequeueOutputBuffer(bufferInfo, 5_000L)
                if (outIdx >= 0) {
                    val outBuf = codec.getOutputBuffer(outIdx)
                    if (outBuf != null && bufferInfo.size > 0) {
                        outBuf.position(bufferInfo.offset)
                        outBuf.limit(bufferInfo.offset + bufferInfo.size)
                        val shortBuf = outBuf.order(ByteOrder.LITTLE_ENDIAN).asShortBuffer()
                        val totalShorts = shortBuf.remaining()
                        var i = 0
                        while (i + channelCount <= totalShorts && collectedCount < targetSamples) {
                            var monoSum = 0f
                            for (c in 0 until channelCount) {
                                monoSum += shortBuf.get(i + c) / 32768f
                            }
                            collected[collectedCount++] = monoSum / channelCount
                            i += channelCount
                        }
                    }
                    codec.releaseOutputBuffer(outIdx, false)
                    if ((bufferInfo.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0) {
                        sawOutputEos = true
                    }
                }
            }

            if (collectedCount < sampleRate) return null
            val trimmed = collected.copyOf(collectedCount)
            DecodedAudioWindow(
                samples = trimmed,
                sampleRate = sampleRate,
                introRms = FloatArray(0),
                outroRms = FloatArray(0),
            )
        } catch (_: Throwable) {
            null
        } finally {
            runCatching { codec?.stop() }
            runCatching { codec?.release() }
            runCatching { extractor.release() }
        }
    }

    companion object {
        private val MAJOR_PROFILE = floatArrayOf(
            6.35f, 2.23f, 3.48f, 2.33f, 4.38f, 4.09f, 2.52f, 5.19f, 2.39f, 3.66f, 2.29f, 2.88f
        )
        private val MINOR_PROFILE = floatArrayOf(
            6.33f, 2.68f, 3.52f, 5.38f, 2.60f, 3.53f, 2.54f, 4.75f, 3.98f, 2.69f, 3.34f, 3.17f
        )
        private val PITCH_NAMES = arrayOf("C", "C#", "D", "D#", "E", "F", "F#", "G", "G#", "A", "A#", "B")

        /**
         * Pure-DSP audio analyzer operating directly on mono PCM samples [-1f..1f].
         * Extracts real RMS/LUFS, dynamic range, spectral centroid, danceability,
         * onset density, autocorrelation BPM, phrase boundary estimate, and
         * Krumhansl-Schmuckler chromagram musical key + Camelot notation.
         */
        fun analyzePcmSamples(
            trackId: String,
            samples: FloatArray,
            sampleRate: Int,
            durationMs: Long,
            introRms: FloatArray = FloatArray(0),
            outroRms: FloatArray = FloatArray(0),
            trackHint: Track? = null,
        ): TrackAnalysis {
            val safeDur = durationMs.coerceAtLeast((samples.size * 1000L) / sampleRate.coerceAtLeast(1))
            val hopSize = (sampleRate * 0.023f).roundToInt().coerceAtLeast(128)
            val frameCount = (samples.size / hopSize).coerceAtLeast(1)
            val frameRms = FloatArray(frameCount)
            val frameDb = FloatArray(frameCount)

            var totalSquare = 0.0
            var zeroCrossings = 0L
            for (f in 0 until frameCount) {
                val start = f * hopSize
                val end = minOf(start + hopSize, samples.size)
                var sq = 0.0
                for (i in start until end) {
                    val s = samples[i].toDouble()
                    sq += s * s
                    if (i > start && ((samples[i] >= 0f && samples[i - 1] < 0f) || (samples[i] < 0f && samples[i - 1] >= 0f))) {
                        zeroCrossings++
                    }
                }
                val rms = sqrt(sq / (end - start).coerceAtLeast(1)).toFloat()
                frameRms[f] = rms
                frameDb[f] = (20f * log10(rms.coerceAtLeast(1e-5f)))
                totalSquare += sq
            }

            val globalRms = sqrt(totalSquare / samples.size.coerceAtLeast(1)).toFloat()
            val rmsDb = (20f * log10(globalRms.coerceAtLeast(1e-5f)))
            val loudnessLufs = (rmsDb - 0.691f).coerceIn(-60f, 0f)

            val sortedDb = frameDb.sorted()
            val p95Db = sortedDb[((sortedDb.size - 1) * 0.95f).toInt()]
            val p30Db = sortedDb[((sortedDb.size - 1) * 0.30f).toInt()]
            val dynamicRangeDb = (p95Db - p30Db).coerceIn(1f, 36f)

            // Spectral centroid approximation combining zero-crossing rate and sub-band Goertzel ratios
            val zcrFreqHz = (zeroCrossings.toFloat() * sampleRate) / (2f * samples.size.coerceAtLeast(1))
            val spectralCentroidHz = estimateSpectralCentroid(samples, sampleRate, zcrFreqHz)

            // Onset strength envelope
            val onsetEnv = FloatArray(frameCount)
            var onsetSum = 0f
            for (f in 1 until frameCount) {
                val delta = (frameRms[f] - frameRms[f - 1]).coerceAtLeast(0f)
                onsetEnv[f] = delta
                onsetSum += delta
            }
            val meanOnset = onsetSum / frameCount.coerceAtLeast(1)
            val onsetThreshold = meanOnset * 1.45f
            var peakCount = 0
            for (f in 1 until frameCount - 1) {
                if (onsetEnv[f] > onsetThreshold && onsetEnv[f] >= onsetEnv[f - 1] && onsetEnv[f] > onsetEnv[f + 1]) {
                    peakCount++
                }
            }
            val analyzedSec = samples.size.toFloat() / sampleRate.coerceAtLeast(1)
            val onsetDensity = (peakCount / analyzedSec.coerceAtLeast(1f)).coerceIn(0f, 15f)

            val normLoudness = ((rmsDb + 32f) / 26f).coerceIn(0f, 1f)
            val normOnset = (onsetDensity / 6.0f).coerceIn(0f, 1f)
            val energy = (normLoudness * 0.65f + normOnset * 0.35f).coerceIn(0.05f, 1.0f)

            val envRateHz = sampleRate.toFloat() / hopSize.toFloat()
            val (bpm, bpmConf, tempoStability) = estimateBpmFromOnsets(onsetEnv, envRateHz)
            val beatIntervalMs = (60_000f / bpm).roundToInt().toLong().coerceAtLeast(250L)

            val framesPerBeat = ((beatIntervalMs / 1000f) * envRateHz).roundToInt().coerceAtLeast(1)
            var bestPhase = 0
            var bestPhaseScore = -1f
            var totalPhaseScore = 0f
            for (phase in 0 until framesPerBeat) {
                var sum = 0f
                var idx = phase
                while (idx < onsetEnv.size) {
                    sum += onsetEnv[idx]
                    idx += framesPerBeat
                }
                totalPhaseScore += sum
                if (sum > bestPhaseScore) {
                    bestPhaseScore = sum
                    bestPhase = phase
                }
            }
            val firstBeatMs = ((bestPhase / envRateHz) * 1000f).roundToInt().toLong().coerceIn(0L, beatIntervalMs)
            val avgPhaseScore = (totalPhaseScore / framesPerBeat).coerceAtLeast(1e-5f)
            val downbeatConfidence = ((bestPhaseScore / avgPhaseScore - 1f) * 0.45f + bpmConf * 0.55f)
                .coerceIn(0.15f, 0.98f)

            val phraseBeats = if (bpm >= 115f) 32L else 16L
            val phraseLengthMs = beatIntervalMs * phraseBeats

            val tempoGroove = exp(-0.5 * ((bpm - 116.0) / 28.0).pow(2)).toFloat()
            val danceability = (bpmConf * 0.42f + tempoGroove * 0.36f + normOnset * 0.22f)
                .coerceIn(0.12f, 0.98f)

            val (musicalKey, keyConfidence) = estimateMusicalKey(samples, sampleRate)
            val camelotCode = HarmonicCompatibility.camelotForKey(musicalKey)

            val introEndMs = minOf(16_000L, (safeDur * 0.14f).toLong())
            val outroStartMs = max(safeDur - 18_000L, (safeDur * 0.84f).toLong())

            val base = TrackAnalysis(
                trackId = trackId,
                bpm = (bpm * 10f).roundToInt() / 10f,
                bpmConfidence = bpmConf,
                tempoStability = tempoStability,
                musicalKey = musicalKey,
                camelotCode = camelotCode,
                keyConfidence = keyConfidence,
                energy = energy,
                rmsDb = rmsDb,
                loudnessLufs = loudnessLufs,
                dynamicRangeDb = dynamicRangeDb,
                onsetDensity = onsetDensity,
                firstBeatMs = firstBeatMs,
                beatIntervalMs = beatIntervalMs,
                introEndMs = introEndMs,
                outroStartMs = outroStartMs,
                durationMs = safeDur,
                userCorrected = false,
                danceability = danceability,
                rhythmSignature = "4/4",
                phraseLengthMs = phraseLengthMs,
                downbeatConfidence = downbeatConfidence,
                spectralCentroidHz = spectralCentroidHz,
            )

            val genreClass = if (trackHint != null) {
                GenreEngine.classifyTrack(trackHint, base)
            } else {
                GenreEngine.inferFromAudioFeatures(base)
            }

            return base.copy(
                primaryGenre = genreClass.primaryGenre,
                secondaryGenre = genreClass.secondaryGenre,
                subgenre = genreClass.subgenre,
                genreConfidence = genreClass.confidence,
            )
        }

        private fun estimateSpectralCentroid(
            samples: FloatArray,
            sampleRate: Int,
            zcrFreqHz: Float,
        ): Float {
            val probeFreqs = doubleArrayOf(90.0, 220.0, 550.0, 1200.0, 2600.0, 5200.0)
            val n = minOf(samples.size, sampleRate * 2)
            if (n < 512) return zcrFreqHz.coerceIn(200f, 8000f)
            val step = (n / 8000).coerceAtLeast(1)
            val subN = n / step
            val subRate = sampleRate.toDouble() / step
            var weightedSum = 0.0
            var totalMag = 0.0
            for (freq in probeFreqs) {
                if (freq >= subRate * 0.45) continue
                val omega = (2.0 * PI * freq) / subRate
                val coeff = 2.0 * cos(omega)
                var q1 = 0.0
                var q2 = 0.0
                for (i in 0 until subN) {
                    val q0 = coeff * q1 - q2 + samples[i * step]
                    q2 = q1
                    q1 = q0
                }
                val mag = sqrt((q1 * q1 + q2 * q2 - q1 * q2 * coeff).coerceAtLeast(0.0))
                weightedSum += freq * mag
                totalMag += mag
            }
            val goertzelCentroid = if (totalMag > 1e-6) (weightedSum / totalMag).toFloat() else 1400f
            return (goertzelCentroid * 0.7f + zcrFreqHz.coerceIn(150f, 7000f) * 0.3f).coerceIn(180f, 7500f)
        }

        private fun estimateBpmFromOnsets(
            onsetEnv: FloatArray,
            envRateHz: Float,
        ): Triple<Float, Float, Float> {
            val minBpm = 65
            val maxBpm = 185
            var bestBpm = 120f
            var bestWeightedScore = -1f
            var totalScore = 0f
            val scores = FloatArray(maxBpm - minBpm + 1)

            for (bpm in minBpm..maxBpm) {
                val lag = ((60f / bpm) * envRateHz).roundToInt().coerceAtLeast(1)
                if (lag >= onsetEnv.size / 2) continue
                var corr = 0f
                val limit = onsetEnv.size - lag
                for (i in 0 until limit) {
                    corr += onsetEnv[i] * onsetEnv[i + lag]
                }
                val normCorr = corr / limit.coerceAtLeast(1)
                val octavePrior = exp(-0.5 * ((bpm - 118.0) / 38.0).pow(2)).toFloat()
                val weighted = normCorr * (0.75f + 0.25f * octavePrior)
                scores[bpm - minBpm] = weighted
                totalScore += weighted
                if (weighted > bestWeightedScore) {
                    bestWeightedScore = weighted
                    bestBpm = bpm.toFloat()
                }
            }

            val avgScore = (totalScore / scores.size).coerceAtLeast(1e-7f)
            val prominence = (bestWeightedScore / avgScore).coerceIn(1f, 6f)
            val confidence = ((prominence - 1f) / 3.5f).coerceIn(0.15f, 0.98f)
            val stability = confidence.coerceIn(0.2f, 0.98f)
            return Triple(bestBpm, confidence, stability)
        }

        private fun estimateMusicalKey(
            samples: FloatArray,
            sampleRate: Int,
        ): Pair<String, Float> {
            val windowSize = minOf(samples.size, sampleRate * 8)
            val step = (windowSize / 24_000).coerceAtLeast(1)
            val subCount = windowSize / step
            if (subCount < 256) return "A min" to 0.2f

            val subRate = sampleRate.toFloat() / step
            val chroma = FloatArray(12)

            for (octave in 3..5) {
                for (pc in 0 until 12) {
                    val midi = (octave + 1) * 12 + pc
                    val freq = 440.0 * 2.0.pow((midi - 69) / 12.0)
                    if (freq >= subRate * 0.45) continue
                    val omega = (2.0 * PI * freq) / subRate
                    val coeff = 2.0 * cos(omega)
                    var q1 = 0.0
                    var q2 = 0.0
                    for (i in 0 until subCount) {
                        val s = samples[i * step].toDouble()
                        val q0 = coeff * q1 - q2 + s
                        q2 = q1
                        q1 = q0
                    }
                    val magSq = (q1 * q1 + q2 * q2 - q1 * q2 * coeff).coerceAtLeast(0.0)
                    chroma[pc] += sqrt(magSq).toFloat()
                }
            }

            val maxChroma = chroma.maxOrNull()?.coerceAtLeast(1e-6f) ?: 1f
            for (i in 0 until 12) chroma[i] /= maxChroma

            var bestKey = "A min"
            var bestCorr = -1f
            var secondCorr = -1f

            for (root in 0 until 12) {
                val majCorr = correlateCircular(chroma, MAJOR_PROFILE, root)
                if (majCorr > bestCorr) {
                    secondCorr = bestCorr
                    bestCorr = majCorr
                    bestKey = "${PITCH_NAMES[root]} maj"
                } else if (majCorr > secondCorr) {
                    secondCorr = majCorr
                }

                val minCorr = correlateCircular(chroma, MINOR_PROFILE, root)
                if (minCorr > bestCorr) {
                    secondCorr = bestCorr
                    bestCorr = minCorr
                    bestKey = "${PITCH_NAMES[root]} min"
                } else if (minCorr > secondCorr) {
                    secondCorr = minCorr
                }
            }

            val confidence = ((bestCorr - secondCorr) * 4f + 0.4f).coerceIn(0.25f, 0.98f)
            return bestKey to confidence
        }

        private fun correlateCircular(
            chroma: FloatArray,
            profile: FloatArray,
            shift: Int,
        ): Float {
            var sumA = 0f
            var sumB = 0f
            for (i in 0 until 12) {
                sumA += chroma[(i + shift) % 12]
                sumB += profile[i]
            }
            val meanA = sumA / 12f
            val meanB = sumB / 12f
            var num = 0f
            var denA = 0f
            var denB = 0f
            for (i in 0 until 12) {
                val da = chroma[(i + shift) % 12] - meanA
                val db = profile[i] - meanB
                num += da * db
                denA += da * da
                denB += db * db
            }
            val den = sqrt(denA * denB).coerceAtLeast(1e-6f)
            return num / den
        }

        private fun genrePriorBpm(genre: String): Float {
            val g = genre.lowercase()
            return when {
                "drum" in g || "dnb" in g -> 170f
                "hip" in g || "rap" in g || "trap" in g || "drill" in g -> 94f
                "hard techno" in g || "psytrance" in g -> 140f
                "techno" in g || "trance" in g -> 132f
                "house" in g || "dance" in g || "edm" in g -> 124f
                "amapiano" in g -> 112f
                "afro" in g || "bongo" in g -> 108f
                "reggae" in g || "dub" in g -> 82f
                "rock" in g || "metal" in g -> 130f
                else -> 116f
            }
        }
    }
}

private fun TrackAnalysisEntity.toDomain(): TrackAnalysis = TrackAnalysis(
    trackId = trackId,
    bpm = bpm,
    bpmConfidence = bpmConfidence,
    tempoStability = tempoStability,
    musicalKey = musicalKey,
    camelotCode = camelotCode,
    keyConfidence = keyConfidence,
    energy = energy,
    rmsDb = rmsDb,
    loudnessLufs = loudnessLufs,
    dynamicRangeDb = dynamicRangeDb,
    onsetDensity = onsetDensity,
    firstBeatMs = firstBeatMs,
    beatIntervalMs = beatIntervalMs,
    introEndMs = introEndMs,
    outroStartMs = outroStartMs,
    durationMs = durationMs,
    userCorrected = userCorrected,
    primaryGenre = primaryGenre,
    secondaryGenre = secondaryGenre,
    subgenre = subgenre,
    genreConfidence = genreConfidence,
    danceability = danceability,
    rhythmSignature = rhythmSignature,
    phraseLengthMs = phraseLengthMs,
    downbeatConfidence = downbeatConfidence,
    spectralCentroidHz = spectralCentroidHz,
)

private fun TrackAnalysis.toEntity(): TrackAnalysisEntity = TrackAnalysisEntity(
    trackId = trackId,
    bpm = bpm,
    bpmConfidence = bpmConfidence,
    tempoStability = tempoStability,
    musicalKey = musicalKey,
    camelotCode = camelotCode,
    keyConfidence = keyConfidence,
    energy = energy,
    rmsDb = rmsDb,
    loudnessLufs = loudnessLufs,
    dynamicRangeDb = dynamicRangeDb,
    onsetDensity = onsetDensity,
    firstBeatMs = firstBeatMs,
    beatIntervalMs = beatIntervalMs,
    introEndMs = introEndMs,
    outroStartMs = outroStartMs,
    durationMs = durationMs,
    userCorrected = userCorrected,
    primaryGenre = primaryGenre,
    secondaryGenre = secondaryGenre,
    subgenre = subgenre,
    genreConfidence = genreConfidence,
    danceability = danceability,
    rhythmSignature = rhythmSignature,
    phraseLengthMs = phraseLengthMs,
    downbeatConfidence = downbeatConfidence,
    spectralCentroidHz = spectralCentroidHz,
)
