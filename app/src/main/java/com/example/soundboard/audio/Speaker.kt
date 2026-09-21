package com.example.soundboard.audio

/** Synthesizes text aloud, for pads that speak their label instead of playing a recorded sound. */
interface Speaker {
    /** Speaks [text], interrupting whatever it was already saying — mirrors [Player]'s exclusive playback. */
    fun speak(text: String)
    fun stop()
    fun shutdown()
}
