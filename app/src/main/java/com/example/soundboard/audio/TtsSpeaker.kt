package com.example.soundboard.audio

import android.content.Context
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import android.speech.tts.Voice
import com.example.soundboard.model.SpeechSettings
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

    private val _voices = MutableStateFlow<List<SpeechVoice>>(emptyList())
    override val voices: StateFlow<List<SpeechVoice>> = _voices.asStateFlow()

    @Volatile private var settings = SpeechSettings()

    private val tts: TextToSpeech = TextToSpeech(context.applicationContext) { status ->
        ready = status == TextToSpeech.SUCCESS
        _available.value = ready
        if (ready) _voices.value = offlineVoices().map { (voice, name) -> SpeechVoice(voice.name, name) }
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

    override fun configure(settings: SpeechSettings) {
        this.settings = settings
    }

    private fun speakNow(text: String) {
        val settings = settings
        val voice = settings.voiceId?.let { id -> offlineVoices().firstOrNull { it.first.name == id }?.first }
        if (voice != null) tts.voice = voice else tts.language = Locale.getDefault()
        tts.setSpeechRate(settings.ratePercent / 100f)
        tts.setPitch(settings.pitchPercent / 100f)
        // An utterance id, so the progress listener hears how it went.
        tts.speak(text, TextToSpeech.QUEUE_FLUSH, null, UTTERANCE_ID)
    }

    /**
     * The installed voices for the device's language that work without a network, since the
     * board mustn't go quiet offline, each with a name to show. Android names voices by id
     * ("en-us-x-iob-local"), so they're numbered within each accent instead.
     */
    private fun offlineVoices(): List<Pair<Voice, String>> {
        val language = Locale.getDefault().language
        return runCatching { tts.voices }.getOrNull().orEmpty()
            .filter { voice ->
                voice.locale.language == language &&
                    !voice.isNetworkConnectionRequired &&
                    TextToSpeech.Engine.KEY_FEATURE_NOT_INSTALLED !in voice.features.orEmpty()
            }
            .sortedWith(compareBy({ it.locale.displayName }, { it.name }))
            .groupBy { it.locale }
            .flatMap { (locale, voices) -> voices.mapIndexed { i, voice -> voice to "${locale.displayName}, voice ${i + 1}" } }
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
