package com.example.soundboard.audio

import android.content.Context
import android.media.MediaRecorder
import android.os.Build
import com.example.soundboard.data.FileSystemStore

/**
 * Wraps [MediaRecorder] for short voice clips, encoded as AAC in an MP4
 * container. [SoundPlayer] picks a playback path by file size alone, not
 * format, so a recorded clip needs no conversion before it can be assigned
 * to a tile exactly like an imported file.
 */
class AudioRecorder(private val context: Context, private val files: FileSystemStore) : Recorder {
    private var active: MediaRecorder? = null

    override fun start(path: String): Boolean {
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
        } else {
            recorder.release()
        }
        return started
    }

    /** False also covers MediaRecorder's own "stop failed" case — too little audio was captured to finalize the file. */
    override fun stop(): Boolean {
        val recorder = active ?: return false
        active = null
        return runCatching { recorder.stop() }
            .also { recorder.release() }
            .isSuccess
    }

    override fun cancel() {
        active?.let { recorder ->
            runCatching { recorder.stop() }
            recorder.release()
        }
        active = null
    }

    @Suppress("DEPRECATION")
    private fun newRecorder(): MediaRecorder =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) MediaRecorder(context) else MediaRecorder()
}
