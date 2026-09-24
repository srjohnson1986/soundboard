package com.example.soundboard.audio

import java.io.File

/** Records calls instead of touching a real microphone. [stopSucceeds] scripts a failed stop. */
class FakeRecorder : Recorder {
    var startedFile: File? = null
        private set
    var cancelled = false
        private set
    var stopSucceeds = true

    override fun start(file: File): Boolean {
        startedFile = file
        cancelled = false
        return true
    }

    override fun stop(): Boolean {
        startedFile = null
        return stopSucceeds
    }

    override fun cancel() {
        startedFile = null
        cancelled = true
    }
}
