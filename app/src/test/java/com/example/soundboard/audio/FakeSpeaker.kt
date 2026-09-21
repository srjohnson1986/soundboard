package com.example.soundboard.audio

/** Records calls instead of touching a real TTS engine. */
class FakeSpeaker : Speaker {
    val spoken = mutableListOf<String>()
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
