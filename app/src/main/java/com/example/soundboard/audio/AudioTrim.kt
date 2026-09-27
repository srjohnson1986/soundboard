package com.example.soundboard.audio

import android.media.MediaCodec
import android.media.MediaExtractor
import android.media.MediaFormat
import android.media.MediaMuxer
import java.io.File
import java.nio.ByteBuffer

/** Never trim a recording to less than this. */
internal const val MIN_KEPT_US = 300_000L

private const val DEFAULT_SAMPLE_BUFFER_BYTES = 256 * 1024

/**
 * Rewrites [file], an MPEG-4 audio file, without its last [trimUs] microseconds, copying the
 * encoded audio as it is (no re-encoding, so no loss). Leaves the file whole when the
 * recording is too short to spare that much, or if anything goes wrong: an untrimmed clip
 * beats none. Returns whether it trimmed. [AudioRecorder] uses it on Stop (#204).
 */
internal fun trimAudioEnd(file: File, trimUs: Long): Boolean {
    val trimmed = File(file.path + ".trimmed")
    val done = runCatching {
        val extractor = MediaExtractor()
        try {
            extractor.setDataSource(file.path)
            val format = extractor.getTrackFormat(0)
            val endUs = format.getLong(MediaFormat.KEY_DURATION) - trimUs
            if (endUs < MIN_KEPT_US) return@runCatching false
            extractor.selectTrack(0)
            val muxer = MediaMuxer(trimmed.path, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4)
            try {
                val track = muxer.addTrack(format)
                muxer.start()
                val bufferSize = if (format.containsKey(MediaFormat.KEY_MAX_INPUT_SIZE)) {
                    format.getInteger(MediaFormat.KEY_MAX_INPUT_SIZE)
                } else {
                    DEFAULT_SAMPLE_BUFFER_BYTES
                }
                val buffer = ByteBuffer.allocate(bufferSize)
                val info = MediaCodec.BufferInfo()
                while (true) {
                    val size = extractor.readSampleData(buffer, 0)
                    if (size < 0 || extractor.sampleTime > endUs) break
                    val keyFrame = extractor.sampleFlags and MediaExtractor.SAMPLE_FLAG_SYNC != 0
                    info.set(0, size, extractor.sampleTime, if (keyFrame) MediaCodec.BUFFER_FLAG_KEY_FRAME else 0)
                    muxer.writeSampleData(track, buffer, info)
                    extractor.advance()
                }
                muxer.stop()
            } finally {
                muxer.release()
            }
        } finally {
            extractor.release()
        }
        check(trimmed.renameTo(file)) { "couldn't replace ${file.name}" }
        true
    }.getOrDefault(false)
    trimmed.delete()
    return done
}
