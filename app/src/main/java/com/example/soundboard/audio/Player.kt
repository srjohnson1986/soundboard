package com.example.soundboard.audio

interface Player {
    /** Prepares the sound file at [path] (a [com.example.soundboard.data.FileStore] path) to play as [key]. */
    fun load(key: String, path: String)
    fun play(key: String, volume: Float = 1f)
    fun unload(key: String)
    fun clear()
    fun release()
}
