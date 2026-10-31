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

    /**
     * The voices this device has for Spanish (#331): null until the device has said what it has,
     * then the list, which is empty when it has none. Unlike [voices] the difference matters:
     * the board warns when this is empty, and mustn't while it's only not known yet.
     */
    val spanishVoices: StateFlow<List<SpeechVoice>?>

    /**
     * Speaks from now on in [settings]'s voice, speed and pitch ([SpeechSettings.spanishVoiceId]
     * for Spanish). A voice it doesn't have means the default.
     */
    fun configure(settings: SpeechSettings)

    /**
     * Speaks [text], interrupting whatever it was already saying — mirrors [Player]'s exclusive playback.
     *
     * [language] is a language tag such as "es"; null is the device's own language. A language the
     * device has no voice for is not spoken at all, in some other language's voice: callers check
     * [spanishVoices] first and say so, since a silent tap must always have an explanation.
     */
    fun speak(text: String, language: String? = null)
    fun stop()
    fun shutdown()
}
