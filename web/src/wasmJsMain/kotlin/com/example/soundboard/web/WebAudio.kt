package com.example.soundboard.web

import com.example.soundboard.audio.Player
import com.example.soundboard.audio.Recorder
import com.example.soundboard.audio.Speaker
import com.example.soundboard.data.FileStore
import com.example.soundboard.data.toUint8Array
import com.example.soundboard.data.uint8ArrayToByteArray
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

    /** Whether [key] has finished loading and decoding; for tests. */
    internal fun isLoaded(key: String): Boolean = key in clips

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

/**
 * [Recorder] on the browser's MediaRecorder. Starting asks for the microphone (the browser
 * prompts the first time; saying no means recording doesn't start). Chrome and Firefox record
 * Opus in WebM and Safari AAC in MP4; the Android app plays both, so recordings made here
 * survive a backup to the phone.
 */
class WebRecorder(private val files: FileStore) : Recorder {

    private val mimeType: String = supportedRecordingType()

    override val fileExtension: String = if (mimeType.startsWith("audio/mp4")) "m4a" else "webm"

    private var active: JsAny? = null
    private var activePath: String? = null

    override suspend fun start(path: String): Boolean {
        cancel()
        val recording = runCatching { startRecording(mimeType).await<JsAny?>() }.getOrNull() ?: return false
        active = recording
        activePath = path
        return true
    }

    override suspend fun stop(): Boolean {
        val recording = active ?: return false
        val path = activePath ?: return false
        active = null
        activePath = null
        val bytes = runCatching { finishRecording(recording).await<JsAny>().uint8ArrayToByteArray() }.getOrNull()
        if (bytes == null || bytes.isEmpty()) return false
        files.write(path, bytes)
        return true
    }

    override fun cancel() {
        active?.let(::abandonRecording)
        active = null
        activePath = null
    }
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

/** The first recording format the browser supports, or "" to let it choose. */
private fun supportedRecordingType(): String = js(
    """(() => {
        if (typeof MediaRecorder === 'undefined') return '';
        for (const type of ['audio/webm;codecs=opus', 'audio/webm', 'audio/mp4']) {
            if (MediaRecorder.isTypeSupported(type)) return type;
        }
        return '';
    })()"""
)

/** Asks for the microphone and starts recording; resolves to the recording, or null if it couldn't start. */
private fun startRecording(mimeType: String): Promise<JsAny?> = js(
    """(async () => {
        try {
            const stream = await navigator.mediaDevices.getUserMedia({ audio: true });
            const recorder = new MediaRecorder(stream, mimeType ? { mimeType: mimeType } : undefined);
            const recording = { stream: stream, recorder: recorder, chunks: [] };
            recorder.ondataavailable = event => { if (event.data.size > 0) recording.chunks.push(event.data); };
            recorder.start();
            return recording;
        } catch (e) {
            return null;
        }
    })()"""
)

/** Stops the recording and resolves to everything it captured. */
private fun finishRecording(recording: JsAny): Promise<JsAny> = js(
    """new Promise((resolve, reject) => {
        const recorder = recording.recorder;
        recorder.onstop = async () => {
            recording.stream.getTracks().forEach(track => track.stop());
            const blob = new Blob(recording.chunks, { type: recorder.mimeType });
            resolve(new Uint8Array(await blob.arrayBuffer()));
        };
        recorder.onerror = event => reject(event.error);
        recorder.stop();
    })"""
)

private fun abandonRecording(recording: JsAny): Unit = js(
    """{
        recording.recorder.onstop = null;
        try { recording.recorder.stop(); } catch (e) {}
        recording.stream.getTracks().forEach(track => track.stop());
    }"""
)
