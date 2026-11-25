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
import kotlin.math.sin

class AudioAnalysisEngine(
    private val context: Context,
    private val analysisDao: AnalysisDao,
) {
    val analysesFlow: Flow<Map<String, TrackAnalysis>> = analysisDao.observeAllAnalyses()
        .map { list -> list.associate { it.trackId to it.toDomain() } }

    suspend fun getAnalysisForTrack(track: Track): TrackAnalysis = withContext(Dispatchers.IO) {
        val existing = analysisDao.getAnalysisOnce(track.id)
        if (existing != null && (existing.userCorrected || existing.analysisVersion >= 3)) {
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
            existing == null || (!existing.userCorrected && existing.analysisVersion < 3)
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
            analysisVersion = 3,
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
                    sampleOffsetMs = decoded.sampleOffsetMs,
                    outroOffsetMs = decoded.outroOffsetMs,
                )
            }
        }
        val safeDur = track.durationMs.coerceAtLeast(60_000L)
        val fallbackBpm = 0f
        val beatMs = 0L
        val phraseBeats = 0L
        val partial = TrackAnalysis(
            trackId = track.id,
            bpm = fallbackBpm,
            bpmConfidence = 0.0f,
            tempoStability = 0.0f,
            musicalKey = "Unknown",
            camelotCode = "--",
            keyConfidence = 0.0f,
            energy = 0.0f,
            rmsDb = -60.0f,
            loudnessLufs = -60.0f,
            dynamicRangeDb = 0.0f,
            onsetDensity = 0.0f,
            firstBeatMs = 0L,
            beatIntervalMs = beatMs,
            introEndMs = 0L,
            outroStartMs = 0L,
            durationMs = safeDur,
            userCorrected = false,
            phraseLengthMs = beatMs * phraseBeats,
            danceability = 0.0f,
            rhythmSignature = "Unknown",
            downbeatConfidence = 0.0f,
            spectralCentroidHz = 0f,
            analysisVersion = 2,
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
        val sampleOffsetMs: Long,
        val outroOffsetMs: Long,
    )

    private data class DecodedSegment(
        val samples: FloatArray,
        val sampleRate: Int,
        val offsetMs: Long,
    )

    private fun decodePcmWindows(uri: Uri, durationMs: Long): DecodedAudioWindow? {
        val windowMs = 24_000L
        val intro = decodeSegment(uri, 0L, windowMs)
        val middleStart = if (durationMs > 60_000L) {
            (durationMs / 2L - windowMs / 2L).coerceAtLeast(0L)
        } else 0L
        val middle = if (middleStart == 0L) intro else decodeSegment(uri, middleStart, windowMs)
        val outroStart = (durationMs - windowMs).coerceAtLeast(0L)
        val outro = if (outroStart == 0L) intro else decodeSegment(uri, outroStart, windowMs)
        val analysisSegment = middle ?: intro ?: outro ?: return null
        if (analysisSegment.samples.size < analysisSegment.sampleRate * 2) return null

        return DecodedAudioWindow(
            samples = analysisSegment.samples,
            sampleRate = analysisSegment.sampleRate,
            introRms = intro?.let { rmsWindows(it.samples, it.sampleRate) } ?: FloatArray(0),
            outroRms = outro?.let { rmsWindows(it.samples, it.sampleRate) } ?: FloatArray(0),
            sampleOffsetMs = analysisSegment.offsetMs,
            outroOffsetMs = outro?.offsetMs ?: 0L,
        )
    }

    private fun decodeSegment(uri: Uri, requestedStartMs: Long, windowMs: Long): DecodedSegment? {
        val extractor = MediaExtractor()
        var codec: MediaCodec? = null
        return try {
            extractor.setDataSource(context, uri, null)
            var audioTrackIndex = -1
            var format: MediaFormat? = null
            for (index in 0 until extractor.trackCount) {
                val candidate = extractor.getTrackFormat(index)
                val mime = candidate.getString(MediaFormat.KEY_MIME).orEmpty()
                if (mime.startsWith("audio/")) {
                    audioTrackIndex = index
                    format = candidate
                    break
                }
            }
            if (audioTrackIndex < 0 || format == null) return null
            extractor.selectTrack(audioTrackIndex)
            if (requestedStartMs > 0L) {
                extractor.seekTo(requestedStartMs * 1_000L, MediaExtractor.SEEK_TO_CLOSEST_SYNC)
            }

            val mime = format.getString(MediaFormat.KEY_MIME) ?: return null
            val sampleRate = format.getInteger(MediaFormat.KEY_SAMPLE_RATE).coerceAtLeast(8_000)
            val channelCount = format.getInteger(MediaFormat.KEY_CHANNEL_COUNT).coerceAtLeast(1)
            val targetSamples = (sampleRate * (windowMs / 1_000L)).coerceIn(sampleRate.toLong() * 4L, 1_100_000L).toInt()

            codec = MediaCodec.createDecoderByType(mime)
            codec.configure(format, null, null, 0)
            codec.start()

            val collected = FloatArray(targetSamples)
            var collectedCount = 0
            var firstOutputOffsetMs: Long? = null
            val bufferInfo = MediaCodec.BufferInfo()
            var inputEos = false
            var outputEos = false
            var safetyCounter = 0

            while (!outputEos && collectedCount < targetSamples && safetyCounter < 3000) {
                safetyCounter++
                if (!inputEos) {
                    val inputIndex = codec.dequeueInputBuffer(5_000L)
                    if (inputIndex >= 0) {
                        val inputBuffer = codec.getInputBuffer(inputIndex)
                        if (inputBuffer == null) {
                            codec.queueInputBuffer(inputIndex, 0, 0, 0L, MediaCodec.BUFFER_FLAG_END_OF_STREAM)
                            inputEos = true
                        } else {
                            inputBuffer.clear()
                            val size = extractor.readSampleData(inputBuffer, 0)
                            if (size < 0) {
                                codec.queueInputBuffer(inputIndex, 0, 0, 0L, MediaCodec.BUFFER_FLAG_END_OF_STREAM)
                                inputEos = true
                            } else {
                                codec.queueInputBuffer(inputIndex, 0, size, extractor.sampleTime, 0)
                                extractor.advance()
                            }
                        }
                    }
                }

                val outputIndex = codec.dequeueOutputBuffer(bufferInfo, 5_000L)
                if (outputIndex >= 0) {
                    val outputBuffer = codec.getOutputBuffer(outputIndex)
                    if (outputBuffer != null && bufferInfo.size > 0) {
                        firstOutputOffsetMs = firstOutputOffsetMs ?: (bufferInfo.presentationTimeUs / 1_000L)
                        outputBuffer.position(bufferInfo.offset)
                        outputBuffer.limit(bufferInfo.offset + bufferInfo.size)
                        val pcm = outputBuffer.order(ByteOrder.LITTLE_ENDIAN).asShortBuffer()
                        val totalShorts = pcm.remaining()
                        var index = 0
                        while (index + channelCount <= totalShorts && collectedCount < targetSamples) {
                            var mono = 0f
                            for (channel in 0 until channelCount) {
                                mono += pcm.get(index + channel) / 32768f
                            }
                            collected[collectedCount++] = mono / channelCount
                            index += channelCount
                        }
                    }
                    codec.releaseOutputBuffer(outputIndex, false)
                    if ((bufferInfo.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0) {
                        outputEos = true
                    }
                }
            }

            if (collectedCount < sampleRate * 2) return null
            DecodedSegment(
                samples = collected.copyOf(collectedCount),
                sampleRate = sampleRate,
                offsetMs = firstOutputOffsetMs ?: requestedStartMs,
            )
        } catch (_: Throwable) {
            null
        } finally {
            runCatching { codec?.stop() }
            runCatching { codec?.release() }
            runCatching { extractor.release() }
        }
    }

    private fun rmsWindows(samples: FloatArray, sampleRate: Int): FloatArray {
        val windowSize = sampleRate.coerceAtLeast(1)
        if (samples.isEmpty()) return FloatArray(0)
        val count = (samples.size + windowSize - 1) / windowSize
        return FloatArray(count) { window ->
            val start = window * windowSize
            val end = minOf(start + windowSize, samples.size)
            var squareSum = 0.0
            for (index in start until end) {
                val sample = samples[index].toDouble()
                squareSum += sample * sample
            }
            sqrt(squareSum / (end - start).coerceAtLeast(1)).toFloat()
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
            sampleOffsetMs: Long = 0L,
            outroOffsetMs: Long = 0L,
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
            val (bpm, rawBpmConfidence, _) = estimateBpmFromOnsets(onsetEnv, envRateHz, trackHint?.genre.orEmpty())
            val tempoStability = estimateTempoStability(onsetEnv, envRateHz, bpm)
            val bpmConf = if (bpm > 0f) {
                rawBpmConfidence * (0.65f + 0.35f * tempoStability)
            } else 0f
            val beatIntervalMs = if (bpm > 0f) {
                (60_000f / bpm).roundToInt().toLong().coerceAtLeast(250L)
            } else 0L

            val framesPerBeat = if (beatIntervalMs > 0L) {
                ((beatIntervalMs / 1000f) * envRateHz).roundToInt().coerceAtLeast(1)
            } else 1
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
            val firstBeatMs = if (beatIntervalMs > 0L && bpmConf >= 0.45f) {
                val localPhaseMs = ((bestPhase / envRateHz) * 1000f).roundToInt().toLong()
                (sampleOffsetMs + localPhaseMs) % beatIntervalMs
            } else 0L
            val avgPhaseScore = (totalPhaseScore / framesPerBeat).coerceAtLeast(1e-5f)
            val downbeatConfidence = if (beatIntervalMs > 0L) {
                ((bestPhaseScore / avgPhaseScore - 1f) * 0.45f + bpmConf * 0.55f).coerceIn(0f, 0.98f)
            } else 0f

            val phraseBeats = if (bpm <= 0f) 0L else if (bpm >= 115f) 32L else 16L
            val phraseLengthMs = beatIntervalMs * phraseBeats

            val tempoGroove = if (bpm > 0f) exp(-0.5 * ((bpm - 116.0) / 28.0).pow(2)).toFloat() else 0f
            val danceability = (bpmConf * 0.42f + tempoGroove * 0.36f + normOnset * 0.22f)
                .coerceIn(0.12f, 0.98f)

            val (musicalKey, keyConfidence) = estimateMusicalKey(samples, sampleRate)
            val camelotCode = if (keyConfidence >= 0.45f) HarmonicCompatibility.camelotForKey(musicalKey) else "--"

            val introEndMs = estimateIntroEnd(introRms)
            val outroStartMs = estimateOutroStart(outroRms, outroOffsetMs, safeDur)

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
                analysisVersion = 2,
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

        private fun estimateIntroEnd(windowRms: FloatArray): Long {
            if (windowRms.size < 8) return 0L
            val early = windowRms.take(4).average().toFloat()
            if (early < 0.002f) return 0L
            val threshold = max(early * 1.8f, early + 0.012f)
            for (index in 4 until windowRms.size - 2) {
                val sustained = (windowRms[index] + windowRms[index + 1] + windowRms[index + 2]) / 3f
                if (sustained >= threshold) return index * 1_000L
            }
            return 0L
        }

        private fun estimateOutroStart(
            windowRms: FloatArray,
            windowOffsetMs: Long,
            durationMs: Long,
        ): Long {
            if (windowRms.size < 8 || windowOffsetMs < 0L) return 0L
            val body = windowRms.take(windowRms.size / 2).sorted()
            if (body.isEmpty()) return 0L
            val baseline = body[body.size / 2]
            if (baseline < 0.005f) return 0L
            val threshold = baseline * 0.42f
            val startIndex = (windowRms.size - 14).coerceAtLeast(3)
            for (index in startIndex until windowRms.size - 3) {
                val sustained = (windowRms[index] + windowRms[index + 1] + windowRms[index + 2] + windowRms[index + 3]) / 4f
                val cue = windowOffsetMs + index * 1_000L
                if (sustained <= threshold && cue < durationMs - 3_000L) return cue
            }
            return 0L
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

        private fun estimateTempoStability(
            onsetEnv: FloatArray,
            envRateHz: Float,
            referenceBpm: Float,
        ): Float {
            if (referenceBpm !in 55f..200f || onsetEnv.size < (envRateHz * 8f).toInt()) return 0f
            val midpoint = onsetEnv.size / 2
            val windows = listOf(
                onsetEnv.copyOfRange(0, midpoint),
                onsetEnv.copyOfRange(midpoint, onsetEnv.size),
            )
            val localTempos = windows.mapNotNull { window ->
                val (localBpm, confidence, _) = estimateBpmFromOnsets(window, envRateHz)
                localBpm.takeIf { it > 0f && confidence >= 0.30f }
            }
            if (localTempos.size < 2) return 0f
            val matches = localTempos.count { localBpm ->
                val equivalents = listOf(referenceBpm, referenceBpm * 0.5f, referenceBpm * 2f)
                    .filter { it in 55f..200f }
                equivalents.any { target ->
                    abs(localBpm - target) <= max(2.5f, target * 0.035f)
                }
            }
            return matches.toFloat() / localTempos.size.toFloat()
        }

        private fun estimateBpmFromOnsets(
            onsetEnv: FloatArray,
            envRateHz: Float,
            genreHint: String = "",
        ): Triple<Float, Float, Float> {
            val minBpm = 55
            val maxBpm = 200
            if (onsetEnv.size < (envRateHz * 4f).roundToInt() ||
                (onsetEnv.maxOrNull() ?: 0f) < 1e-5f
            ) return Triple(0f, 0f, 0f)

            val mean = onsetEnv.average().toFloat()
            val centered = FloatArray(onsetEnv.size) { onsetEnv[it] - mean }
            val scores = FloatArray(maxBpm - minBpm + 1)
            var bestIndex = -1
            var bestScore = -1f

            val knownGenre = GenreEngine.canonicalize(genreHint)
            val priorBpm = knownGenre?.let { genrePriorBpm(it.canonical) }

            for (bpm in minBpm..maxBpm) {
                val lagFloat = (60f / bpm) * envRateHz
                val lag = kotlin.math.floor(lagFloat).toInt().coerceAtLeast(1)
                if (lag + 1 >= centered.size / 2) continue
                val fraction = lagFloat - lag
                val limit = centered.size - lag - 1
                var dot = 0.0
                var energyA = 0.0
                var energyB = 0.0
                for (i in 0 until limit) {
                    val a = centered[i].toDouble()
                    val b0 = centered[i + lag].toDouble()
                    val b1 = centered[i + lag + 1].toDouble()
                    val b = b0 * (1.0 - fraction) + b1 * fraction
                    dot += a * b
                    energyA += a * a
                    energyB += b * b
                }
                val denominator = sqrt(energyA * energyB).coerceAtLeast(1e-12)
                val correlation = (dot / denominator).toFloat().coerceIn(-1f, 1f)
                val priorWeight = if (priorBpm != null) {
                    exp(-0.5 * ((bpm - priorBpm) / 28.0).pow(2)).toFloat() * 0.10f
                } else 0f
                val score = correlation + priorWeight
                scores[bpm - minBpm] = score
                if (score > bestScore) {
                    bestScore = score
                    bestIndex = bpm - minBpm
                }
            }

            if (bestIndex < 0 || bestScore <= 0.03f) return Triple(0f, 0f, 0f)

            val bestBpm = (bestIndex + minBpm).toFloat()
            var secondBest = -1f
            for (i in scores.indices) {
                val bpm = i + minBpm
                if (kotlin.math.abs(bpm - bestBpm) <= 3f) continue
                if (scores[i] > secondBest) secondBest = scores[i]
            }
            val meanScore = scores.filter { it > -0.5f }.average().toFloat()
            val prominence = ((bestScore - meanScore) / (abs(meanScore) + 0.08f)).coerceIn(0f, 4f)
            val separation = ((bestScore - secondBest) / (abs(bestScore) + 0.08f)).coerceIn(0f, 1f)
            val octaveAmbiguity = listOf(bestBpm * 0.5f, bestBpm * 2f)
                .filter { it in minBpm.toFloat()..maxBpm.toFloat() }
                .mapNotNull { candidate ->
                    val index = candidate.roundToInt() - minBpm
                    scores.getOrNull(index)
                }
                .maxOrNull() ?: -1f
            val ambiguityPenalty = if (octaveAmbiguity > bestScore * 0.90f) 0.35f else 0f
            val confidence = (
                prominence * 0.28f + separation * 0.72f - ambiguityPenalty
            ).coerceIn(0f, 0.98f)
            if (confidence < 0.35f) return Triple(0f, 0f, 0f)

            val tempoStability = confidence
            return Triple(bestBpm, confidence, tempoStability)
        }

        private fun estimateMusicalKey(
            samples: FloatArray,
            sampleRate: Int,
        ): Pair<String, Float> {
            val fftSize = 4096
            val hopSize = 2048
            if (sampleRate < 8_000 || samples.size < sampleRate * 2) return "Unknown" to 0f

            val real = DoubleArray(fftSize)
            val imag = DoubleArray(fftSize)
            val chroma = DoubleArray(12)
            var frameCount = 0
            val targetFrames = 48
            val frameStride = ((samples.size - fftSize).coerceAtLeast(0) / (targetFrames - 1))
                .coerceAtLeast(hopSize)
            var offset = 0

            while (offset + fftSize <= samples.size && frameCount < targetFrames) {
                for (i in 0 until fftSize) {
                    val hann = 0.5 - 0.5 * cos(2.0 * PI * i / (fftSize - 1))
                    real[i] = samples[offset + i].toDouble() * hann
                    imag[i] = 0.0
                }
                fftInPlace(real, imag)

                for (bin in 2 until fftSize / 2) {
                    val frequency = bin.toDouble() * sampleRate / fftSize
                    if (frequency !in 55.0..5_000.0) continue
                    val midiFloat = 69.0 + 12.0 * (kotlin.math.ln(frequency / 440.0) / kotlin.math.ln(2.0))
                    val nearestMidi = midiFloat.roundToInt()
                    if (nearestMidi !in 36..96) continue
                    val cents = (midiFloat - nearestMidi) * 100.0
                    val tuningWeight = exp(-0.5 * (cents / 35.0).pow(2))
                    val magnitude = sqrt(real[bin] * real[bin] + imag[bin] * imag[bin])
                    chroma[Math.floorMod(nearestMidi, 12)] += magnitude * tuningWeight
                }
                frameCount++
                offset += frameStride
            }

            if (frameCount < 4 || chroma.sum() < 1e-8) return "Unknown" to 0f

            val total = chroma.sum()
            val normalized = FloatArray(12) { (chroma[it] / total).toFloat() }
            var bestKey = "Unknown"
            var bestCorr = -1f
            var secondCorr = -1f

            for (root in 0 until 12) {
                val major = correlateCircular(normalized, MAJOR_PROFILE, root)
                if (major > bestCorr) {
                    secondCorr = bestCorr
                    bestCorr = major
                    bestKey = "${PITCH_NAMES[root]} maj"
                } else if (major > secondCorr) {
                    secondCorr = major
                }

                val minor = correlateCircular(normalized, MINOR_PROFILE, root)
                if (minor > bestCorr) {
                    secondCorr = bestCorr
                    bestCorr = minor
                    bestKey = "${PITCH_NAMES[root]} min"
                } else if (minor > secondCorr) {
                    secondCorr = minor
                }
            }

            val margin = (bestCorr - secondCorr).coerceAtLeast(0f)
            val confidence = (margin * 5.0f).coerceIn(0f, 0.98f)
            return if (confidence >= 0.45f) bestKey to confidence else "Unknown" to confidence
        }

        /** In-place radix-2 Cooley-Tukey FFT. */
        private fun fftInPlace(real: DoubleArray, imag: DoubleArray) {
            val n = real.size
            var j = 0
            for (i in 1 until n) {
                var bit = n shr 1
                while ((j and bit) != 0) {
                    j = j xor bit
                    bit = bit shr 1
                }
                j = j xor bit
                if (i < j) {
                    val realTemp = real[i]
                    real[i] = real[j]
                    real[j] = realTemp
                    val imagTemp = imag[i]
                    imag[i] = imag[j]
                    imag[j] = imagTemp
                }
            }

            var length = 2
            while (length <= n) {
                val angle = -2.0 * PI / length
                val wLengthReal = cos(angle)
                val wLengthImag = sin(angle)
                var start = 0
                while (start < n) {
                    var wReal = 1.0
                    var wImag = 0.0
                    for (k in 0 until length / 2) {
                        val even = start + k
                        val odd = even + length / 2
                        val oddReal = real[odd] * wReal - imag[odd] * wImag
                        val oddImag = real[odd] * wImag + imag[odd] * wReal
                        real[odd] = real[even] - oddReal
                        imag[odd] = imag[even] - oddImag
                        real[even] += oddReal
                        imag[even] += oddImag
                        val nextWReal = wReal * wLengthReal - wImag * wLengthImag
                        wImag = wReal * wLengthImag + wImag * wLengthReal
                        wReal = nextWReal
                    }
                    start += length
                }
                length = length shl 1
            }
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

        private fun genrePriorBpm(genre: String): Float? {
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
                else -> null
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
    analysisVersion = analysisVersion,
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
    analysisVersion = analysisVersion,
)
