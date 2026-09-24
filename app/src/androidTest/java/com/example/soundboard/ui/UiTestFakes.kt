package com.example.soundboard.ui

import com.example.soundboard.audio.Player
import com.example.soundboard.audio.Recorder
import com.example.soundboard.audio.Speaker
import java.io.File

internal class FakePlayer : Player {
    val played = mutableListOf<String>()
    override fun load(key: String, file: File) {}
    override fun play(key: String, volume: Float) {
        played += key
    }
    override fun unload(key: String) {}
    override fun clear() {}
    override fun release() {}
}

internal class FakeRecorder : Recorder {
    override fun start(file: File) = true
    override fun stop() = true
    override fun cancel() {}
}

internal class FakeSpeaker : Speaker {
    val spoken = mutableListOf<String>()
    override fun speak(text: String) {
        spoken += text
    }
    override fun stop() {}
    override fun shutdown() {}
}
