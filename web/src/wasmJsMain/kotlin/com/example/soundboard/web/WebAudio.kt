package com.example.soundboard.web

import com.example.soundboard.audio.Player
import com.example.soundboard.audio.Recorder
import com.example.soundboard.audio.Speaker
import com.example.soundboard.audio.SpeechVoice
import com.example.soundboard.data.FileStore
import com.example.soundboard.data.toUint8Array
import com.example.soundboard.data.uint8ArrayToByteArray
import com.example.soundboard.model.SpeechSettings
import kotlin.js.Promise
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.await
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json

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
    /** A browser without speech synthesis can't speak at all; otherwise it can (#248). */
    override val available: StateFlow<Boolean?> = MutableStateFlow(speechSupported())

    private val _voices = MutableStateFlow<List<SpeechVoice>>(emptyList())
    override val voices: StateFlow<List<SpeechVoice>> = _voices.asStateFlow()

    private var settings = SpeechSettings()

    init {
        if (speechSupported()) {
            // Browsers load their voices after the page, and say so with voiceschanged.
            refreshVoices()
            onVoicesChanged { refreshVoices() }
        }
    }

    private fun refreshVoices() {
        _voices.value = Json.decodeFromString<List<List<String>>>(voicesJson()).map { (id, name) -> SpeechVoice(id, name) }
    }

    override fun configure(settings: SpeechSettings) {
        this.settings = settings
    }

    override fun speak(text: String) =
        speakText(text, settings.ratePercent / 100.0, settings.pitchPercent / 100.0, settings.voiceId.orEmpty())
    override fun stop() = stopSpeaking()
    override fun shutdown() = stopSpeaking()
}

/**
 * [Recorder] on the browser's MediaRecorder. Starting asks for the microphone (the browser
 * prompts the first time; saying no means recording doesn't start). Browsers record in
 * different formats (WebM in Chrome and Firefox, MP4 in Safari), so stopping decodes the
 * recording, trims its end (#204), and saves it as a WAV file: mono, 24 kHz, 16-bit, which
 * every browser and the Android app play.
 */
class WebRecorder(private val files: FileStore) : Recorder {

    private val mimeType: String = supportedRecordingType()

    override val fileExtension: String = "wav"

    private var active: JsAny? = null
    private var activePath: String? = null

    override suspend fun start(path: String): Boolean {
        cancel()
        val recording = runCatching { startRecording(mimeType).await<JsAny?>() }.getOrNull() ?: return false
        active = recording
        activePath = path
        return true
    }

    override suspend fun stop(trimEndMillis: Int): Boolean {
        val recording = active ?: return false
        val path = activePath ?: return false
        active = null
        activePath = null
        val bytes = runCatching {
            trimmedWav(finishRecording(recording).await(), trimEndMillis / 1000.0)
        }.getOrNull()
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

/**
 * [audio] (a Web Audio AudioBuffer) as a WAV file: mono, 24 kHz, 16-bit, without its last
 * [trimSeconds] unless that would leave less than 0.3 s. Separate from recording so a test
 * can trim a known buffer.
 */
internal suspend fun trimmedWav(audio: JsAny, trimSeconds: Double): ByteArray =
    encodeWav(audio, trimSeconds).await<JsAny>().uint8ArrayToByteArray()

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

private fun speakText(text: String, rate: Double, pitch: Double, voiceUri: String): Unit = js(
    """{
        speechSynthesis.cancel();
        const utterance = new SpeechSynthesisUtterance(text);
        utterance.rate = rate;
        utterance.pitch = pitch;
        const voice = voiceUri ? speechSynthesis.getVoices().find(v => v.voiceURI === voiceUri) : null;
        if (voice) utterance.voice = voice;
        speechSynthesis.speak(utterance);
    }"""
)

/** The browser's voices for the page's language, as a JSON list of [voiceURI, name] pairs. */
private fun voicesJson(): String = js(
    """(() => {
        const language = (navigator.language || 'en').split('-')[0].toLowerCase();
        return JSON.stringify(speechSynthesis.getVoices()
            .filter(v => v.lang.toLowerCase().startsWith(language))
            .map(v => [v.voiceURI, v.name]));
    })()"""
)

private fun onVoicesChanged(callback: () -> Unit): Unit = js("{ speechSynthesis.addEventListener('voiceschanged', () => callback()); }")

private fun stopSpeaking(): Unit = js("{ speechSynthesis.cancel(); }")

private fun speechSupported(): Boolean = js("typeof speechSynthesis !== 'undefined'")

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

/** Stops the recording and resolves to it decoded, as an AudioBuffer. */
private fun finishRecording(recording: JsAny): Promise<JsAny> = js(
    """new Promise((resolve, reject) => {
        const recorder = recording.recorder;
        recorder.onstop = async () => {
            try {
                recording.stream.getTracks().forEach(track => track.stop());
                const blob = new Blob(recording.chunks, { type: recorder.mimeType });
                const context = window.soundboardAudio || (window.soundboardAudio = new AudioContext());
                resolve(await context.decodeAudioData(await blob.arrayBuffer()));
            } catch (e) {
                reject(e);
            }
        };
        recorder.onerror = event => reject(event.error);
        recorder.stop();
    })"""
)

/** See [trimmedWav]; resolves to the WAV file's bytes. OfflineAudioContext does the mixing down, resampling and cutting. */
private fun encodeWav(decoded: JsAny, trimSeconds: Double): Promise<JsAny> = js(
    """(async () => {
        const keepSeconds = decoded.duration - trimSeconds >= 0.3 ? decoded.duration - trimSeconds : decoded.duration;
        const rate = 24000;
        const offline = new OfflineAudioContext(1, Math.max(1, Math.round(keepSeconds * rate)), rate);
        const source = offline.createBufferSource();
        source.buffer = decoded;
        source.connect(offline.destination);
        source.start();
        const samples = (await offline.startRendering()).getChannelData(0);
        const wav = new DataView(new ArrayBuffer(44 + samples.length * 2));
        const text = (offset, value) => { for (let i = 0; i < value.length; i++) wav.setUint8(offset + i, value.charCodeAt(i)); };
        text(0, 'RIFF'); wav.setUint32(4, 36 + samples.length * 2, true); text(8, 'WAVE');
        text(12, 'fmt '); wav.setUint32(16, 16, true); wav.setUint16(20, 1, true); wav.setUint16(22, 1, true);
        wav.setUint32(24, rate, true); wav.setUint32(28, rate * 2, true); wav.setUint16(32, 2, true); wav.setUint16(34, 16, true);
        text(36, 'data'); wav.setUint32(40, samples.length * 2, true);
        for (let i = 0; i < samples.length; i++) {
            const sample = Math.max(-1, Math.min(1, samples[i]));
            wav.setInt16(44 + i * 2, sample < 0 ? sample * 0x8000 : sample * 0x7fff, true);
        }
        return new Uint8Array(wav.buffer);
    })()"""
)

private fun abandonRecording(recording: JsAny): Unit = js(
    """{
        recording.recorder.onstop = null;
        try { recording.recorder.stop(); } catch (e) {}
        recording.stream.getTracks().forEach(track => track.stop());
    }"""
)
