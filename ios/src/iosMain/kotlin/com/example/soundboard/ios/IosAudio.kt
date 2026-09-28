@file:OptIn(ExperimentalForeignApi::class)

package com.example.soundboard.ios

import com.example.soundboard.audio.MediaVolume
import com.example.soundboard.audio.Player
import com.example.soundboard.audio.Recorder
import com.example.soundboard.audio.Speaker
import com.example.soundboard.audio.SpeechVoice
import com.example.soundboard.model.SpeechSettings
import platform.AVFAudio.AVAudioPlayer
import platform.AVFAudio.AVAudioRecorder
import platform.AVFAudio.AVAudioSession
import platform.AVFAudio.AVAudioSessionCategoryOptionDefaultToSpeaker
import platform.AVFAudio.AVAudioSessionCategoryPlayAndRecord
import platform.AVFAudio.AVEncoderBitRateKey
import platform.AVFAudio.AVFormatIDKey
import platform.AVFAudio.AVNumberOfChannelsKey
import platform.AVFAudio.AVSampleRateKey
import platform.AVFAudio.AVSpeechBoundary
import platform.AVFAudio.AVSpeechSynthesisVoice
import platform.AVFAudio.AVSpeechSynthesizer
import platform.AVFAudio.AVSpeechUtterance
import platform.AVFAudio.AVSpeechUtteranceDefaultSpeechRate
import platform.AVFAudio.AVSpeechUtteranceMaximumSpeechRate
import platform.AVFAudio.AVSpeechUtteranceMinimumSpeechRate
import platform.AVFAudio.outputVolume
import platform.AVFAudio.setActive
import platform.AVFoundation.AVAssetExportPresetAppleM4A
import platform.AVFoundation.AVAssetExportSession
import platform.AVFoundation.AVAssetExportSessionStatusCompleted
import platform.AVFoundation.AVFileTypeAppleM4A
import platform.AVFoundation.AVURLAsset
import platform.AVFoundation.timeRange
import platform.CoreAudioTypes.kAudioFormatMPEG4AAC
import platform.CoreMedia.CMTimeGetSeconds
import platform.CoreMedia.CMTimeMakeWithSeconds
import platform.CoreMedia.CMTimeRangeMake
import platform.Foundation.NSFileManager
import platform.Foundation.NSLocale
import platform.Foundation.NSNotificationCenter
import platform.Foundation.NSOperationQueue
import platform.Foundation.NSURL
import platform.Foundation.currentLocale
import platform.Foundation.languageCode
import platform.UIKit.UIApplicationDidBecomeActiveNotification
import kotlin.coroutines.resume
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.suspendCancellableCoroutine

// iOS's audio and speech behind the shared interfaces (#266), as the web module has the
// browser's and the app module Android's.

/** Plays and records alongside each other, through the speaker, whatever the silent switch says. */
fun configureAudioSession() {
    val session = AVAudioSession.sharedInstance()
    session.setCategory(AVAudioSessionCategoryPlayAndRecord, withOptions = AVAudioSessionCategoryOptionDefaultToSpeaker, error = null)
    session.setActive(true, error = null)
}

/**
 * [Player] on AVAudioPlayer, one per clip, prepared when it's loaded so a tap plays at once.
 * Exclusive like Android's: starting a clip stops whatever was playing. [root] is the file
 * store's directory, which the clips' paths are relative to.
 */
class IosAudioPlayer(private val root: String) : Player {
    private val clips = mutableMapOf<String, AVAudioPlayer>()
    private var playing: AVAudioPlayer? = null

    override fun load(key: String, path: String) {
        if (key in clips) return
        // A file iOS can't decode just stays silent, as on the other platforms.
        val player = AVAudioPlayer(contentsOfURL = NSURL.fileURLWithPath("$root/$path"), error = null)
        player.prepareToPlay()
        clips[key] = player
    }

    override fun play(key: String, volume: Float) {
        playing?.stop()
        val clip = clips[key] ?: return
        clip.currentTime = 0.0
        clip.volume = volume.coerceIn(0f, 1f)
        clip.play()
        playing = clip
    }

    override fun unload(key: String) {
        clips.remove(key)?.stop()
    }

    override fun clear() {
        clips.values.forEach { it.stop() }
        clips.clear()
    }

    override fun release() {
        clear()
        playing = null
    }
}

/**
 * [Recorder] on AVAudioRecorder, in the same format as Android's (AAC in .m4a, 44.1 kHz mono,
 * 96 kbps). Stopping trims the end (#204) by exporting all but the last [stop]'s worth; a
 * recording that would keep less than [MIN_KEPT_SECONDS] is kept whole, as on Android.
 */
class IosRecorder(private val root: String) : Recorder {
    override val fileExtension: String = "m4a"

    private var active: AVAudioRecorder? = null
    private var activePath: String? = null

    override suspend fun start(path: String): Boolean {
        cancel()
        val full = "$root/$path"
        NSFileManager.defaultManager.createDirectoryAtPath(full.substringBeforeLast('/'), withIntermediateDirectories = true, attributes = null, error = null)
        val settings = mapOf<Any?, Any>(
            AVFormatIDKey to kAudioFormatMPEG4AAC.toInt(),
            AVSampleRateKey to 44_100.0,
            AVNumberOfChannelsKey to 1,
            AVEncoderBitRateKey to 96_000
        )
        val recorder = AVAudioRecorder(uRL = NSURL.fileURLWithPath(rawPath(full)), settings = settings, error = null)
        if (!recorder.record()) return false
        active = recorder
        activePath = full
        return true
    }

    override suspend fun stop(trimEndMillis: Int): Boolean {
        val recorder = active ?: return false
        val full = activePath ?: return false
        active = null
        activePath = null
        recorder.stop()
        val raw = rawPath(full)
        val kept = trim(raw, full, trimEndMillis / 1000.0) || moveWhole(raw, full)
        NSFileManager.defaultManager.removeItemAtPath(raw, error = null)
        return kept && NSFileManager.defaultManager.fileExistsAtPath(full)
    }

    override fun cancel() {
        active?.let {
            it.stop()
            it.deleteRecording()
        }
        active = null
        activePath = null
    }

    /** Exports [raw] less its last [trimSeconds] to [out]; false if it's too short to trim or the export fails. */
    private suspend fun trim(raw: String, out: String, trimSeconds: Double): Boolean {
        if (trimSeconds <= 0.0) return false
        val asset = AVURLAsset(uRL = NSURL.fileURLWithPath(raw), options = null)
        val keep = CMTimeGetSeconds(asset.duration) - trimSeconds
        if (keep < MIN_KEPT_SECONDS) return false
        val export = AVAssetExportSession(asset = asset, presetName = AVAssetExportPresetAppleM4A)
        export.outputURL = NSURL.fileURLWithPath(out)
        export.outputFileType = AVFileTypeAppleM4A
        export.timeRange = CMTimeRangeMake(CMTimeMakeWithSeconds(0.0, 600), CMTimeMakeWithSeconds(keep, 600))
        return suspendCancellableCoroutine { continuation ->
            export.exportAsynchronouslyWithCompletionHandler {
                continuation.resume(export.status == AVAssetExportSessionStatusCompleted)
            }
        }
    }

    private fun moveWhole(raw: String, out: String): Boolean {
        NSFileManager.defaultManager.removeItemAtPath(out, error = null)
        return NSFileManager.defaultManager.moveItemAtPath(raw, toPath = out, error = null)
    }

    private fun rawPath(full: String) = "$full.recording.m4a"

    private companion object {
        const val MIN_KEPT_SECONDS = 0.3
    }
}

/**
 * [Speaker] on AVSpeechSynthesizer: the voices for the device's language, with Settings'
 * speed and pitch (#250). Speaking is exclusive, as elsewhere: a new phrase cuts the last off.
 */
class IosSpeaker : Speaker {
    private val synthesizer = AVSpeechSynthesizer()
    private var settings = SpeechSettings()

    override val available: StateFlow<Boolean?> = MutableStateFlow(AVSpeechSynthesisVoice.speechVoices().isNotEmpty())

    override val voices: StateFlow<List<SpeechVoice>> = MutableStateFlow(
        run {
            val language = NSLocale.currentLocale.languageCode ?: "en"
            AVSpeechSynthesisVoice.speechVoices()
                .map { it as AVSpeechSynthesisVoice }
                .filter { it.language.startsWith(language) }
                .map { SpeechVoice(it.identifier, "${it.name} (${it.language})") }
                .sortedBy { it.name }
        }
    )

    override fun configure(settings: SpeechSettings) {
        this.settings = settings
    }

    override fun speak(text: String) {
        if (text.isBlank()) return
        synthesizer.stopSpeakingAtBoundary(AVSpeechBoundary.AVSpeechBoundaryImmediate)
        val utterance = AVSpeechUtterance.speechUtteranceWithString(text)
        utterance.rate = (AVSpeechUtteranceDefaultSpeechRate * settings.ratePercent / 100f)
            .coerceIn(AVSpeechUtteranceMinimumSpeechRate, AVSpeechUtteranceMaximumSpeechRate)
        utterance.pitchMultiplier = (settings.pitchPercent / 100f).coerceIn(0.5f, 2f)
        utterance.voice = settings.voiceId?.let { AVSpeechSynthesisVoice.voiceWithIdentifier(it) }
        synthesizer.speakUtterance(utterance)
    }

    override fun stop() {
        synthesizer.stopSpeakingAtBoundary(AVSpeechBoundary.AVSpeechBoundaryImmediate)
    }

    override fun shutdown() = stop()
}

/**
 * The media volume (#248): checked when the app comes back to the front and on each tap. The
 * silent switch doesn't count, since the app plays through it (see [configureAudioSession]).
 */
class IosMediaVolume : MediaVolume {
    private val _muted = MutableStateFlow(isMuted())
    override val muted: StateFlow<Boolean> = _muted.asStateFlow()

    private val observer = NSNotificationCenter.defaultCenter.addObserverForName(
        UIApplicationDidBecomeActiveNotification, `object` = null, queue = NSOperationQueue.mainQueue
    ) { _ -> refresh() }

    override fun refresh() {
        _muted.value = isMuted()
    }

    override fun release() {
        NSNotificationCenter.defaultCenter.removeObserver(observer)
    }

    private fun isMuted(): Boolean = AVAudioSession.sharedInstance().outputVolume == 0f
}
