package com.example.soundboard.audio

import java.io.File

interface Player {
    fun load(key: String, file: File)
    fun play(key: String, volume: Float = 1f)
    fun unload(key: String)
    fun clear()
    fun release()
}
