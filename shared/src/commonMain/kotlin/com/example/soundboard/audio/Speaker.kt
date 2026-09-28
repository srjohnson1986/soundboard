package com.example.soundboard.audio

import kotlinx.coroutines.flow.StateFlow

/** Synthesizes text aloud, for pads that speak their label instead of playing a recorded sound. */
interface Speaker {
    /**
     * Whether speech works here: null while the engine is still starting, then true or false.
     * False means speaking tiles are silent, which the board says (#248).
     */
    val available: StateFlow<Boolean?>

    /** Speaks [text], interrupting whatever it was already saying — mirrors [Player]'s exclusive playback. */
    fun speak(text: String)
    fun stop()
    fun shutdown()
}
