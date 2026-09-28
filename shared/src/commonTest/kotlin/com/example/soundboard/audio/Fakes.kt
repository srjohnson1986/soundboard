package com.example.soundboard.audio

import kotlinx.coroutines.flow.MutableStateFlow

/** Records calls instead of touching real audio. Loads and plays are treated as instant. */
class FakePlayer : Player {
    val loaded = mutableListOf<String>()
    val played = mutableListOf<Pair<String, Float>>()

    /** Just the keys from [played], in order — for tests that don't care about volume. */
    val playedKeys: List<String> get() = played.map { it.first }
    val unloaded = mutableListOf<String>()
    var cleared = false
    var released = false

    override fun load(key: String, path: String) {
        loaded += key
    }

    override fun play(key: String, volume: Float) {
        played += key to volume
    }

    override fun unload(key: String) {
        loaded -= key
        unloaded += key
    }

    override fun clear() {
        loaded.clear()
        cleared = true
    }

    override fun release() {
        released = true
    }
}

/** Records calls instead of touching a real microphone. [stopSucceeds] scripts a failed stop. */
class FakeRecorder : Recorder {
    var startedPath: String? = null
        private set
    var cancelled = false
        private set
    var stopSucceeds = true

    override val fileExtension: String = "m4a"

    override suspend fun start(path: String): Boolean {
        startedPath = path
        cancelled = false
        return true
    }

    /** The trim the last [stop] was asked for. */
    var lastTrimEndMillis: Int? = null
        private set

    override suspend fun stop(trimEndMillis: Int): Boolean {
        lastTrimEndMillis = trimEndMillis
        startedPath = null
        return stopSucceeds
    }

    override fun cancel() {
        startedPath = null
        cancelled = true
    }
}

/** Records calls instead of touching a real TTS engine. */
class FakeSpeaker : Speaker {
    val spoken = mutableListOf<String>()
    override val available = MutableStateFlow<Boolean?>(true)
    var stopped = false
    var shutdown = false

    override fun speak(text: String) {
        spoken += text
    }

    override fun stop() {
        stopped = true
    }

    override fun shutdown() {
        shutdown = true
    }
}

/** A media volume a test can turn off; counts the checks it's asked for. */
class FakeMediaVolume : MediaVolume {
    override val muted = MutableStateFlow(false)
    var refreshes = 0
        private set

    override fun refresh() {
        refreshes++
    }
}
