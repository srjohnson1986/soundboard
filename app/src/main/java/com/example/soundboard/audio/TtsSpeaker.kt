package com.example.soundboard.audio

import android.content.Context
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import java.util.Locale
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Wraps Android's on-device [TextToSpeech] engine. Initialization is async, so a [speak]
 * call that arrives before the engine is ready is remembered and fired once it is —
 * only the most recent one, since speaking is exclusive like [SoundPlayer].
 *
 * [available] is false if the engine doesn't start, or if the last thing it was asked to say
 * failed (e.g. no voice data for the language and no network), until something is spoken again.
 */
class TtsSpeaker(context: Context) : Speaker {

    @Volatile private var ready = false
    private var pendingText: String? = null

    private val _available = MutableStateFlow<Boolean?>(null)
    override val available: StateFlow<Boolean?> = _available.asStateFlow()

    private val tts: TextToSpeech = TextToSpeech(context.applicationContext) { status ->
        ready = status == TextToSpeech.SUCCESS
        _available.value = ready
        if (ready) pendingText?.let(::speakNow)
        pendingText = null
    }

    init {
        tts.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
            override fun onStart(utteranceId: String?) {
                _available.value = true
            }

            override fun onDone(utteranceId: String?) {}

            @Deprecated("Deprecated in Java")
            override fun onError(utteranceId: String?) {
                _available.value = false
            }

            override fun onError(utteranceId: String?, errorCode: Int) {
                _available.value = false
            }
        })
    }

    override fun speak(text: String) {
        if (text.isBlank()) return
        if (ready) speakNow(text) else pendingText = text
    }

    private fun speakNow(text: String) {
        tts.language = Locale.getDefault()
        // An utterance id, so the progress listener hears how it went.
        tts.speak(text, TextToSpeech.QUEUE_FLUSH, null, UTTERANCE_ID)
    }

    /** For tests: whether the engine has started up. */
    internal val isReady: Boolean get() = ready

    /** For tests: whether it's speaking now. */
    internal val isSpeaking: Boolean get() = tts.isSpeaking

    /** For tests: whether the device has a speech engine at all. */
    internal val hasEngine: Boolean get() = tts.engines.isNotEmpty()

    override fun stop() {
        pendingText = null
        tts.stop()
    }

    override fun shutdown() {
        tts.stop()
        tts.shutdown()
    }

    private companion object {
        const val UTTERANCE_ID = "soundboard"
    }
}
