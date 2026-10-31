package com.example.soundboard.audio

import com.example.soundboard.model.SpeechSettings
import kotlinx.coroutines.flow.MutableStateFlow

/** Records calls instead of touching a real TTS engine. */
class FakeSpeaker : Speaker {
    val spoken = mutableListOf<String>()

    /** Each thing spoken with the language it was asked for in (null: the device's own). */
    val spokenIn = mutableListOf<Pair<String, String?>>()
    override val available = MutableStateFlow<Boolean?>(true)
    override val voices = MutableStateFlow<List<SpeechVoice>>(emptyList())

    /** A device with one Spanish voice; a test sets it to null (not known yet) or empty (none). */
    override val spanishVoices = MutableStateFlow<List<SpeechVoice>?>(listOf(SpeechVoice("es-1", "Spanish voice 1")))

    override fun configure(settings: SpeechSettings) {}
    var stopped = false
    var shutdown = false

    override fun speak(text: String, language: String?) {
        spoken += text
        spokenIn += text to language
    }

    override fun stop() {
        stopped = true
    }

    override fun shutdown() {
        shutdown = true
    }
}
