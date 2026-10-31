package com.example.soundboard.audio

import com.example.soundboard.model.SpeechSettings
import com.example.soundboard.translate.Translator
import com.example.soundboard.translate.TranslatorStatus
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/** Records calls instead of touching real audio. Loads and plays are treated as instant. */
class FakePlayer : Player {
    val loaded = mutableListOf<String>()
    val played = mutableListOf<Pair<String, Float>>()

    /** Just the keys from [played], in order — for tests that don't care about volume. */
    val playedKeys: List<String> get() = played.map { it.first }
    val unloaded = mutableListOf<String>()
    var cleared = false
    var released = false

    override fun load(key: String, path: String) {
        loaded += key
    }

    override fun play(key: String, volume: Float) {
        played += key to volume
    }

    override fun unload(key: String) {
        loaded -= key
        unloaded += key
    }

    override fun clear() {
        loaded.clear()
        cleared = true
    }

    override fun release() {
        released = true
    }
}

/** Records calls instead of touching a real microphone. [stopSucceeds] scripts a failed stop. */
class FakeRecorder : Recorder {
    var startedPath: String? = null
        private set
    var cancelled = false
        private set
    var stopSucceeds = true

    override val fileExtension: String = "m4a"

    override suspend fun start(path: String): Boolean {
        startedPath = path
        cancelled = false
        return true
    }

    /** The trim the last [stop] was asked for. */
    var lastTrimEndMillis: Int? = null
        private set

    override suspend fun stop(trimEndMillis: Int): Boolean {
        lastTrimEndMillis = trimEndMillis
        startedPath = null
        return stopSucceeds
    }

    override fun cancel() {
        startedPath = null
        cancelled = true
    }
}

/** Records calls instead of touching a real TTS engine. */
class FakeSpeaker : Speaker {
    val spoken = mutableListOf<String>()

    /** Each thing spoken with the language it was asked for in (null: the device's own). */
    val spokenIn = mutableListOf<Pair<String, String?>>()
    override val available = MutableStateFlow<Boolean?>(true)
    override val voices = MutableStateFlow<List<SpeechVoice>>(emptyList())

    /** A device with one Spanish voice; a test sets it to null (not known yet) or empty (none). */
    override val spanishVoices = MutableStateFlow<List<SpeechVoice>?>(listOf(SpeechVoice("es-1", "Spanish voice 1")))

    /** The settings it was last told to speak with. */
    var configured: SpeechSettings? = null
        private set

    override fun configure(settings: SpeechSettings) {
        configured = settings
    }
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

/** A media volume a test can turn off; counts the checks it's asked for. */
class FakeMediaVolume : MediaVolume {
    override val muted = MutableStateFlow(false)
    var refreshes = 0
        private set

    override fun refresh() {
        refreshes++
    }
}

/** What a [FakeSpeaker] is expected to have said, each with its language (null: the device's own); `listOf(text to null)` would infer the wrong type. */
fun spokenOf(vararg said: Pair<String, String?>): List<Pair<String, String?>> = said.toList()

/**
 * A [Translator] a test scripts: [status] to start from, what each text translates to, and whether
 * a translation or the download fails. Translations are `"[es] <text>"` unless [translations] has the text.
 */
class FakeTranslator(initial: TranslatorStatus = TranslatorStatus.Ready) : Translator {
    override val status = MutableStateFlow<TranslatorStatus>(initial)
    val translations = mutableMapOf<String, String>()

    /** Everything it was asked to translate, in order. */
    val translated = mutableListOf<String>()
    var prepared = 0
        private set

    /** Set to make every translation fail with this message. */
    var failWith: String? = null

    /** Set to make [prepare] (and a translation that needs the model) fail with this message. */
    var downloadFails: String? = null

    override suspend fun prepare(target: String) {
        ensureModel()
    }

    private fun ensureModel(): Boolean {
        if (status.value == TranslatorStatus.Ready) return true
        prepared++
        downloadFails?.let {
            status.value = TranslatorStatus.Failed(it)
            return false
        }
        status.value = TranslatorStatus.Ready
        return true
    }

    override suspend fun translate(text: String, target: String, from: String): String? {
        if (status.value is TranslatorStatus.Unavailable || !ensureModel()) return null
        translated += text
        failWith?.let {
            status.value = TranslatorStatus.Failed(it)
            return null
        }
        return translations[text] ?: "[$target] $text"
    }
}

/**
 * A [Translator] that holds each translation until the test releases it with its Spanish, to see
 * the board and the progress while it is working.
 */
class GateTranslator(initial: TranslatorStatus = TranslatorStatus.Ready) : Translator {
    override val status: StateFlow<TranslatorStatus> = MutableStateFlow(initial)
    private val waiting = ArrayDeque<CompletableDeferred<String>>()
    private val released = ArrayDeque<String>()

    override suspend fun prepare(target: String) = Unit

    override suspend fun translate(text: String, target: String, from: String): String? {
        val early = released.removeFirstOrNull()
        if (early != null) return early
        val gate = CompletableDeferred<String>()
        waiting.addLast(gate)
        return gate.await()
    }

    /** Lets the translation being held (or the next one) finish with [spanish]. */
    fun release(spanish: String) {
        val gate = waiting.removeFirstOrNull()
        if (gate != null) gate.complete(spanish) else released.addLast(spanish)
    }
}
