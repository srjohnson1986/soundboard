package com.example.soundboard.audio

import com.example.soundboard.model.SpeechSettings
import kotlinx.coroutines.flow.StateFlow

/** A voice the device can speak in: the platform's [id] for it and a [name] to show. */
data class SpeechVoice(val id: String, val name: String)

/** Synthesizes text aloud, for pads that speak their label instead of playing a recorded sound. */
interface Speaker {
    /**
     * Whether speech works here: null while the engine is still starting, then true or false.
     * False means speaking tiles are silent, which the board says (#248).
     */
    val available: StateFlow<Boolean?>

    /** The voices this device offers for its language, for Settings to choose from; empty until known. */
    val voices: StateFlow<List<SpeechVoice>>

    /** Speaks from now on in [settings]'s voice, speed and pitch. A voice it doesn't have means the default. */
    fun configure(settings: SpeechSettings)

    /** Speaks [text], interrupting whatever it was already saying — mirrors [Player]'s exclusive playback. */
    fun speak(text: String)
    fun stop()
    fun shutdown()
}
