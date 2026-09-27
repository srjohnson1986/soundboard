package com.example.soundboard.audio

import android.media.MediaCodec
import android.media.MediaCodecInfo
import android.media.MediaExtractor
import android.media.MediaFormat
import android.media.MediaMetadataRetriever
import android.media.MediaMuxer
import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.PI
import kotlin.math.min
import kotlin.math.sin

/** Audio files for the audio tests, made on the device: a tone, [durationMs] long. */
object TestAudioFiles {
    private const val SAMPLE_RATE = 44_100
    private const val TIMEOUT_US = 10_000L

    private fun tone(index: Int): Short = (sin(2 * PI * 440 * index / SAMPLE_RATE) * 8_000).toInt().toShort()

    /** An AAC .m4a, as MediaRecorder makes: encoded with MediaCodec, written with MediaMuxer. */
    fun writeAacM4a(file: File, durationMs: Int) {
        val format = MediaFormat.createAudioFormat(MediaFormat.MIMETYPE_AUDIO_AAC, SAMPLE_RATE, 1).apply {
            setInteger(MediaFormat.KEY_AAC_PROFILE, MediaCodecInfo.CodecProfileLevel.AACObjectLC)
            setInteger(MediaFormat.KEY_BIT_RATE, 64_000)
            setInteger(MediaFormat.KEY_MAX_INPUT_SIZE, 16_384)
        }
        val codec = MediaCodec.createEncoderByType(MediaFormat.MIMETYPE_AUDIO_AAC)
        codec.configure(format, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE)
        codec.start()
        val muxer = MediaMuxer(file.path, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4)
        try {
            val totalSamples = SAMPLE_RATE * durationMs / 1000
            var fed = 0
            var inputDone = false
            var track = -1
            val info = MediaCodec.BufferInfo()
            while (true) {
                if (!inputDone) {
                    val index = codec.dequeueInputBuffer(TIMEOUT_US)
                    if (index >= 0) {
                        val buffer = codec.getInputBuffer(index)!!.order(ByteOrder.LITTLE_ENDIAN)
                        buffer.clear()
                        val count = min(min(buffer.remaining() / 2, 1024), totalSamples - fed)
                        val timeUs = fed * 1_000_000L / SAMPLE_RATE
                        if (count <= 0) {
                            codec.queueInputBuffer(index, 0, 0, timeUs, MediaCodec.BUFFER_FLAG_END_OF_STREAM)
                            inputDone = true
                        } else {
                            repeat(count) { buffer.putShort(tone(fed + it)) }
                            codec.queueInputBuffer(index, 0, count * 2, timeUs, 0)
                            fed += count
                        }
                    }
                }
                val index = codec.dequeueOutputBuffer(info, TIMEOUT_US)
                if (index == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED) {
                    track = muxer.addTrack(codec.outputFormat)
                    muxer.start()
                } else if (index >= 0) {
                    if (info.flags and MediaCodec.BUFFER_FLAG_CODEC_CONFIG != 0) info.size = 0
                    if (info.size > 0) muxer.writeSampleData(track, codec.getOutputBuffer(index)!!, info)
                    codec.releaseOutputBuffer(index, false)
                    if (info.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM != 0) break
                }
            }
            muxer.stop()
        } finally {
            muxer.release()
            codec.stop()
            codec.release()
        }
    }

    /** A 16-bit mono PCM .wav, which SoundPool and MediaPlayer both play. */
    fun writeWav(file: File, durationMs: Int) {
        val samples = SAMPLE_RATE * durationMs / 1000
        val data = ByteBuffer.allocate(44 + samples * 2).order(ByteOrder.LITTLE_ENDIAN)
        data.put("RIFF".toByteArray()).putInt(36 + samples * 2).put("WAVE".toByteArray())
        data.put("fmt ".toByteArray()).putInt(16).putShort(1).putShort(1).putInt(SAMPLE_RATE).putInt(SAMPLE_RATE * 2).putShort(2).putShort(16)
        data.put("data".toByteArray()).putInt(samples * 2)
        repeat(samples) { data.putShort(tone(it)) }
        file.parentFile?.mkdirs()
        file.writeBytes(data.array())
    }

    /** The duration the file's audio track says it has, as the trim reads it. */
    fun trackDurationUs(file: File): Long {
        val extractor = MediaExtractor()
        try {
            extractor.setDataSource(file.path)
            return extractor.getTrackFormat(0).getLong(MediaFormat.KEY_DURATION)
        } finally {
            extractor.release()
        }
    }

    /** How long one AAC frame (1024 samples) of the file lasts: the trim cuts in steps of it. */
    fun aacFrameMs(file: File): Long {
        val extractor = MediaExtractor()
        try {
            extractor.setDataSource(file.path)
            return 1024_000L / extractor.getTrackFormat(0).getInteger(MediaFormat.KEY_SAMPLE_RATE)
        } finally {
            extractor.release()
        }
    }

    /** The duration a player sees, read independently of the trim's own code. */
    fun playbackDurationMs(file: File): Long {
        val retriever = MediaMetadataRetriever()
        try {
            retriever.setDataSource(file.path)
            return retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)!!.toLong()
        } finally {
            retriever.release()
        }
    }
}
