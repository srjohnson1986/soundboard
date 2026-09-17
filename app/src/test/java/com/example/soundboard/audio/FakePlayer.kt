package com.example.soundboard.audio

import java.io.File

/** Records calls instead of touching real audio. Loads and plays are treated as instant. */
class FakePlayer : Player {
    val loaded = mutableListOf<String>()
    val played = mutableListOf<Pair<String, Float>>()
    val unloaded = mutableListOf<String>()
    var cleared = false
    var released = false

    override fun load(key: String, file: File) {
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
