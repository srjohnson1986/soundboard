package com.example.soundboard.audio

/** Records calls instead of touching a real microphone. [stopSucceeds] scripts a failed stop. */
class FakeRecorder : Recorder {
    var startedPath: String? = null
        private set
    var cancelled = false
        private set
    var stopSucceeds = true

    override fun start(path: String): Boolean {
        startedPath = path
        cancelled = false
        return true
    }

    override fun stop(): Boolean {
        startedPath = null
        return stopSucceeds
    }

    override fun cancel() {
        startedPath = null
        cancelled = true
    }
}
