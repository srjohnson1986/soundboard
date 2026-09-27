package com.example.soundboard.audio

/** Records calls instead of touching real audio. */
class FakePlayer : Player {
    val loaded = mutableListOf<String>()
    val playedKeys = mutableListOf<String>()

    override fun load(key: String, path: String) {
        loaded += key
    }

    override fun play(key: String, volume: Float) {
        playedKeys += key
    }

    override fun unload(key: String) {
        loaded -= key
    }

    override fun clear() {
        loaded.clear()
    }

    override fun release() {}
}

/** Records calls instead of touching a microphone; every recording succeeds. */
class FakeRecorder : Recorder {
    var lastTrimEndMillis: Int? = null
        private set

    override val fileExtension: String = "m4a"
    override suspend fun start(path: String): Boolean = true
    override suspend fun stop(trimEndMillis: Int): Boolean {
        lastTrimEndMillis = trimEndMillis
        return true
    }
    override fun cancel() {}
}

/** Records what it was asked to say. */
class FakeSpeaker : Speaker {
    val spoken = mutableListOf<String>()

    override fun speak(text: String) {
        spoken += text
    }

    override fun stop() {}
    override fun shutdown() {}
}
