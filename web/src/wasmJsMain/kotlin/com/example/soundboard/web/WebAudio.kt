package com.example.soundboard.web

import com.example.soundboard.audio.Player
import com.example.soundboard.audio.Recorder
import com.example.soundboard.audio.Speaker
import com.example.soundboard.data.FileStore
import com.example.soundboard.data.toUint8Array
import kotlin.js.Promise
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.await
import kotlinx.coroutines.launch

/**
 * [Player] on the Web Audio API: each clip is decoded into memory when it's loaded (like
 * SoundPool on Android), so a tap plays it at once. Playback is exclusive, as on Android:
 * starting a clip stops whatever was playing.
 */
class WebAudioPlayer(private val files: FileStore, private val scope: CoroutineScope) : Player {

    private val clips = mutableMapOf<String, JsAny>()
    private var playing: JsAny? = null

    override fun load(key: String, path: String) {
        if (key in clips) return
        scope.launch {
            val bytes = files.read(path) ?: return@launch
            // A file the browser can't decode (a format it doesn't support) just stays silent.
            runCatching { clips[key] = decodeAudio(bytes.toUint8Array()).await<JsAny>() }
        }
    }

    override fun play(key: String, volume: Float) {
        stopPlaying()
        val clip = clips[key] ?: return
        playing = playClip(clip, volume.coerceIn(0f, 1f).toDouble())
    }

    override fun unload(key: String) {
        clips -= key
    }

    override fun clear() {
        clips.clear()
    }

    override fun release() {
        stopPlaying()
        clips.clear()
    }

    private fun stopPlaying() {
        playing?.let(::stopClip)
        playing = null
    }
}

/** [Speaker] on the browser's speech synthesis, with whatever voice the browser defaults to. */
class WebSpeaker : Speaker {
    override fun speak(text: String) = speakText(text)
    override fun stop() = stopSpeaking()
    override fun shutdown() = stopSpeaking()
}

/** Stands in until recording arrives on the web: it never starts, so the app says it couldn't record. */
class UnavailableRecorder : Recorder {
    override fun start(path: String): Boolean = false
    override fun stop(): Boolean = false
    override fun cancel() {}
}

// One AudioContext for the page. Browsers start it suspended until the user interacts, so
// playing resumes it; a tile tap is that interaction.
private fun decodeAudio(bytes: JsAny): Promise<JsAny> = js(
    """(() => {
        const context = window.soundboardAudio || (window.soundboardAudio = new AudioContext());
        return context.decodeAudioData(bytes.buffer);
    })()"""
)

private fun playClip(clip: JsAny, volume: Double): JsAny = js(
    """(() => {
        const context = window.soundboardAudio || (window.soundboardAudio = new AudioContext());
        if (context.state === 'suspended') context.resume();
        const source = context.createBufferSource();
        source.buffer = clip;
        const gain = context.createGain();
        gain.gain.value = volume;
        source.connect(gain).connect(context.destination);
        source.start();
        return source;
    })()"""
)

private fun stopClip(source: JsAny): Unit = js("{ try { source.stop(); } catch (e) {} }")

private fun speakText(text: String): Unit = js(
    """{
        speechSynthesis.cancel();
        speechSynthesis.speak(new SpeechSynthesisUtterance(text));
    }"""
)

private fun stopSpeaking(): Unit = js("{ speechSynthesis.cancel(); }")
