package com.example.soundboard.audio

import android.content.Context
import android.media.MediaCodec
import android.media.MediaExtractor
import android.media.MediaFormat
import android.media.MediaMuxer
import android.media.MediaRecorder
import android.os.Build
import com.example.soundboard.data.FileSystemStore
import java.io.File
import java.nio.ByteBuffer

/**
 * Wraps [MediaRecorder] for short voice clips, encoded as AAC in an MP4
 * container. [SoundPlayer] picks a playback path by file size alone, not
 * format, so a recorded clip needs no conversion before it can be assigned
 * to a tile exactly like an imported file.
 */
class AudioRecorder(private val context: Context, private val files: FileSystemStore) : Recorder {
    private var active: MediaRecorder? = null
    private var activeFile: File? = null

    override val fileExtension: String = "m4a"

    override suspend fun start(path: String): Boolean {
        cancel()
        val file = files.file(path).apply { parentFile?.mkdirs() }
        val recorder = newRecorder()
        val started = runCatching {
            recorder.apply {
                setAudioSource(MediaRecorder.AudioSource.MIC)
                setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
                setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
                setOutputFile(file.absolutePath)
                prepare()
                start()
            }
        }.isSuccess
        if (started) {
            active = recorder
            activeFile = file
        } else {
            recorder.release()
        }
        return started
    }

    /** False also covers MediaRecorder's own "stop failed" case — too little audio was captured to finalize the file. */
    override suspend fun stop(trimEndMillis: Int): Boolean {
        val recorder = active ?: return false
        val file = activeFile
        active = null
        activeFile = null
        val ok = runCatching { recorder.stop() }
            .also { recorder.release() }
            .isSuccess
        if (ok && file != null && trimEndMillis > 0) trimEnd(file, trimEndMillis * 1_000L)
        return ok
    }

    /**
     * Rewrites [file] without its last [trimUs] microseconds, copying the encoded audio as it
     * is (no re-encoding, so no loss). Leaves the file whole when the recording is too short to
     * spare that much, or if anything goes wrong: an untrimmed clip beats none.
     */
    private fun trimEnd(file: File, trimUs: Long) {
        val trimmed = File(file.path + ".trimmed")
        runCatching {
            val extractor = MediaExtractor()
            try {
                extractor.setDataSource(file.path)
                val format = extractor.getTrackFormat(0)
                val endUs = format.getLong(MediaFormat.KEY_DURATION) - trimUs
                if (endUs < MIN_KEPT_US) return@runCatching
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
        }
        trimmed.delete()
    }

    override fun cancel() {
        active?.let { recorder ->
            runCatching { recorder.stop() }
            recorder.release()
        }
        active = null
        activeFile = null
    }

    @Suppress("DEPRECATION")
    private fun newRecorder(): MediaRecorder =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) MediaRecorder(context) else MediaRecorder()

    private companion object {
        /** Never trim a recording to less than this. */
        const val MIN_KEPT_US = 300_000L
        const val DEFAULT_SAMPLE_BUFFER_BYTES = 256 * 1024
    }
}
