package com.example.soundboard.audio

import kotlinx.coroutines.flow.MutableStateFlow

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
