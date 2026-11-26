package com.example.data.rhythm

import android.content.Context
import android.media.MediaCodec
import android.media.MediaExtractor
import android.media.MediaFormat
import android.net.Uri
import java.nio.ByteOrder
import kotlin.math.ceil

/**
 * Decodes the first eight seconds of an audio Uri to mono Float PCM at 22,050 Hz.
 *
 * This is a basic Android decoder path. Its resampling is linear and has not yet
 * been measured for feature parity with the project's librosa/soxr_hq pipeline.
 * Treat predictions from arbitrary music files as experimental.
 */
object RhythmAudioDecoder {

    fun decodeFirstEightSeconds(context: Context, uri: Uri): FloatArray {
        val extractor = MediaExtractor()
        var codec: MediaCodec? = null

        try {
            extractor.setDataSource(context, uri, null)

            val audioTrack = (0 until extractor.trackCount).firstOrNull { index ->
                extractor.getTrackFormat(index).getString(MediaFormat.KEY_MIME)
                    ?.startsWith("audio/") == true
            } ?: error("The selected file has no supported audio track.")

            extractor.selectTrack(audioTrack)
            val inputFormat = extractor.getTrackFormat(audioTrack)
            val mime = inputFormat.getString(MediaFormat.KEY_MIME)
                ?: error("Audio format is missing its MIME type.")

            codec = MediaCodec.createDecoderByType(mime)
            codec.configure(inputFormat, null, null, 0)
            codec.start()

            var sampleRate = if (inputFormat.containsKey(MediaFormat.KEY_SAMPLE_RATE)) {
                inputFormat.getInteger(MediaFormat.KEY_SAMPLE_RATE)
            } else {
                RhythmAudioConfig.SAMPLE_RATE
            }
            var channelCount = if (inputFormat.containsKey(MediaFormat.KEY_CHANNEL_COUNT)) {
                inputFormat.getInteger(MediaFormat.KEY_CHANNEL_COUNT)
            } else {
                1
            }
            var pcmEncoding = MediaFormat.ENCODING_PCM_16BIT

            val monoSamples = ArrayList<Float>(sampleRate * RhythmAudioConfig.DURATION_SECONDS)
            var inputEnded = false
            var outputEnded = false

            while (!outputEnded && monoSamples.size < sampleRate * RhythmAudioConfig.DURATION_SECONDS) {
                if (!inputEnded) {
                    val inputIndex = codec.dequeueInputBuffer(TIMEOUT_US)
                    if (inputIndex >= 0) {
                        val inputBuffer = codec.getInputBuffer(inputIndex)
                            ?: error("Decoder input buffer is unavailable.")
                        val sampleSize = extractor.readSampleData(inputBuffer, 0)

                        if (sampleSize < 0) {
                            codec.queueInputBuffer(
                                inputIndex,
                                0,
                                0,
                                0L,
                                MediaCodec.BUFFER_FLAG_END_OF_STREAM
                            )
                            inputEnded = true
                        } else {
                            codec.queueInputBuffer(
                                inputIndex,
                                0,
                                sampleSize,
                                extractor.sampleTime.coerceAtLeast(0L),
                                0
                            )
                            extractor.advance()
                        }
                    }
                }

                val info = MediaCodec.BufferInfo()
                val outputIndex = codec.dequeueOutputBuffer(info, TIMEOUT_US)

                when {
                    outputIndex == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED -> {
                        val outputFormat = codec.outputFormat
                        if (outputFormat.containsKey(MediaFormat.KEY_SAMPLE_RATE)) {
                            sampleRate = outputFormat.getInteger(MediaFormat.KEY_SAMPLE_RATE)
                        }
                        if (outputFormat.containsKey(MediaFormat.KEY_CHANNEL_COUNT)) {
                            channelCount = outputFormat.getInteger(MediaFormat.KEY_CHANNEL_COUNT)
                        }
                        if (outputFormat.containsKey(MediaFormat.KEY_PCM_ENCODING)) {
                            pcmEncoding = outputFormat.getInteger(MediaFormat.KEY_PCM_ENCODING)
                        }
                    }

                    outputIndex >= 0 -> {
                        val outputBuffer = codec.getOutputBuffer(outputIndex)
                        if (outputBuffer != null && info.size > 0 &&
                            info.flags and MediaCodec.BUFFER_FLAG_CODEC_CONFIG == 0
                        ) {
                            outputBuffer.position(info.offset)
                            outputBuffer.limit(info.offset + info.size)
                            val pcm = outputBuffer.slice().order(ByteOrder.LITTLE_ENDIAN)
                            appendMonoSamples(
                                target = monoSamples,
                                pcm = pcm,
                                encoding = pcmEncoding,
                                channels = channelCount,
                                maxSamples = sampleRate * RhythmAudioConfig.DURATION_SECONDS
                            )
                        }

                        if (info.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM != 0) {
                            outputEnded = true
                        }
                        codec.releaseOutputBuffer(outputIndex, false)
                    }
                }
            }

            check(monoSamples.isNotEmpty()) {
                "The audio decoder did not return any PCM samples."
            }
            return resampleLinear(monoSamples, sampleRate, RhythmAudioConfig.SAMPLE_RATE)
        } finally {
            runCatching { codec?.stop() }
            runCatching { codec?.release() }
            runCatching { extractor.release() }
        }
    }

    private fun appendMonoSamples(
        target: MutableList<Float>,
        pcm: java.nio.ByteBuffer,
        encoding: Int,
        channels: Int,
        maxSamples: Int
    ) {
        require(channels > 0) { "Invalid audio channel count: $channels." }

        val bytesPerSample = when (encoding) {
            MediaFormat.ENCODING_PCM_16BIT -> 2
            MediaFormat.ENCODING_PCM_FLOAT -> 4
            MediaFormat.ENCODING_PCM_8BIT -> 1
            else -> error("Unsupported decoded PCM encoding: $encoding.")
        }

        val frameBytes = bytesPerSample * channels
        val frames = pcm.remaining() / frameBytes

        repeat(frames) {
            if (target.size >= maxSamples) return
            var sum = 0f
            repeat(channels) {
                val value = when (encoding) {
                    MediaFormat.ENCODING_PCM_16BIT -> pcm.short / 32768f
                    MediaFormat.ENCODING_PCM_FLOAT -> pcm.float
                    MediaFormat.ENCODING_PCM_8BIT -> ((pcm.get().toInt() and 0xFF) - 128) / 128f
                    else -> 0f
                }
                sum += value
            }
            target.add((sum / channels).coerceIn(-1f, 1f))
        }
    }

    private fun resampleLinear(
        source: List<Float>,
        sourceRate: Int,
        targetRate: Int
    ): FloatArray {
        require(sourceRate > 0 && targetRate > 0) { "Invalid audio sample rate." }

        val outputSize = ceil(source.size.toDouble() * targetRate / sourceRate)
            .toInt()
            .coerceAtMost(RhythmAudioConfig.INPUT_SAMPLES)

        return FloatArray(outputSize) { index ->
            val sourcePosition = index.toDouble() * sourceRate / targetRate
            val left = sourcePosition.toInt().coerceIn(0, source.lastIndex)
            val right = (left + 1).coerceAtMost(source.lastIndex)
            val fraction = (sourcePosition - left).toFloat()
            (source[left] * (1f - fraction) + source[right] * fraction)
                .coerceIn(-1f, 1f)
        }
    }

    private const val TIMEOUT_US = 10_000L
}
