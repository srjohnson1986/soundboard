package com.example.soundboard.audio

import android.content.Context
import android.speech.tts.TextToSpeech
import java.util.Locale

/**
 * Wraps Android's on-device [TextToSpeech] engine. Initialization is async, so a [speak]
 * call that arrives before the engine is ready is remembered and fired once it is —
 * only the most recent one, since speaking is exclusive like [SoundPlayer].
 */
class TtsSpeaker(context: Context) : Speaker {

    private var ready = false
    private var pendingText: String? = null

    private val tts: TextToSpeech = TextToSpeech(context.applicationContext) { status ->
        ready = status == TextToSpeech.SUCCESS
        if (ready) pendingText?.let(::speakNow)
        pendingText = null
    }

    override fun speak(text: String) {
        if (text.isBlank()) return
        if (ready) speakNow(text) else pendingText = text
    }

    private fun speakNow(text: String) {
        tts.language = Locale.getDefault()
        tts.speak(text, TextToSpeech.QUEUE_FLUSH, null, null)
    }

    override fun stop() {
        pendingText = null
        tts.stop()
    }

    override fun shutdown() {
        tts.stop()
        tts.shutdown()
    }
}
